package ua.edu.chnu.awards.auth.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.entity.OneTimeToken;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.auth.event.EmailChangeRequested;
import ua.edu.chnu.awards.auth.event.EmailChanged;
import ua.edu.chnu.awards.auth.security.AuthorizationRevoker;
import ua.edu.chnu.awards.common.EmailUtils;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.ClientRequest;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.config.AuthProperties;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.service.UserNotFoundException;
import ua.edu.chnu.awards.user.service.UserProfileService;

import lombok.RequiredArgsConstructor;

/**
 * Change of the sign-in address: requested with the current password, confirmed from the new mailbox. The
 * confirmation signs the user out everywhere, because every session and token carries the old address.
 */
@Service
@RequiredArgsConstructor
public class EmailChangeService {

    static final String REQUEST_KEY_PREFIX = "auth:email-change:";
    static final Duration LINK_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final OneTimeTokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService attempts;
    private final AuthorizationRevoker authorizations;
    private final RequestThrottle throttle;
    private final AuthProperties properties;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    /**
     * Sends a confirmation link to the new address and cancels older links. A wrong password counts as a failed
     * sign-in; the failure that locks the account also signs it out, and that survives the refusal.
     *
     * @param userId          the caller
     * @param newEmail        the requested address
     * @param currentPassword the caller's password
     * @throws ApiProblemException 403 {@code password-mismatch}, 422 {@code institutional-email-required} or
     *                             {@code validation-failed} (unchanged address), 409 {@code email-taken},
     *                             429 {@code too-many-requests}
     */
    @Transactional(noRollbackFor = ApiProblemException.class)
    public void request(long userId, String newEmail, String currentPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            if (attempts.recordFailure(user.getEmailAddress(), ClientRequest.current().ip())) {
                tokens.invalidate(user, TokenPurpose.EMAIL_CHANGE);
                authorizations.revokeAll(user);
            }
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "password-mismatch", "The password is wrong");
        }
        String email = EmailUtils.normalize(newEmail);
        requireAvailable(user, email);
        if (!throttle.claim(REQUEST_KEY_PREFIX + userId, properties.resendInterval())) {
            throw new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests",
                "A confirmation link was sent recently; try again in a minute");
        }
        tokens.invalidate(user, TokenPurpose.EMAIL_CHANGE);
        String raw = tokens.issue(user, TokenPurpose.EMAIL_CHANGE, LINK_TTL, email);
        audit.record(AuditAction.EMAIL_CHANGE_REQUESTED, UserProfileService.AUDIT_ENTITY, userId, userId,
            Map.of("newEmail", email));
        events.publishEvent(new EmailChangeRequested(email, user.getEmailAddress(), user.getFirstName(),
            properties.link("/confirm-email-change", raw), properties.frontendUrl() + "/forgot-password"));
    }

    /**
     * Moves the account to the address of a confirmation link and signs it out everywhere.
     *
     * @param rawToken token from the link
     * @return the new sign-in address
     * @throws ApiProblemException 410 {@code token-invalid}, 409 {@code account-not-active} or
     *                             {@code email-taken}; the account keeps its address
     */
    @Transactional
    public String confirm(String rawToken) {
        OneTimeToken token = tokens.redeem(rawToken, TokenPurpose.EMAIL_CHANGE)
            .orElseThrow(OneTimeTokenService::gone);
        User user = token.getUser();
        if (!user.isActive()) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "account-not-active", "The account is not active");
        }
        String oldEmail = user.getEmailAddress();
        String newEmail = token.getNewEmailAddress();
        if (userRepository.existsByEmailAddressIgnoreCase(newEmail)) {
            throw taken(null);
        }
        user.setEmailAddress(newEmail);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            user.setEmailAddress(oldEmail);
            throw taken(e);
        }
        authorizations.revokeAll(user.getId(), oldEmail);
        tokens.invalidate(user, TokenPurpose.EMAIL_CHANGE);
        tokens.invalidate(user, TokenPurpose.PASSWORD_RESET);
        audit.record(AuditAction.EMAIL_CHANGED, UserProfileService.AUDIT_ENTITY, user.getId(), user.getId(),
            Map.of("oldEmail", oldEmail, "newEmail", newEmail));
        events.publishEvent(new EmailChanged(oldEmail, newEmail, user.getFirstName()));
        return newEmail;
    }

    private void requireAvailable(User user, String email) {
        if (!properties.isInstitutional(email)) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "institutional-email-required",
                "Only institutional addresses can be used to sign in");
        }
        if (email.equalsIgnoreCase(user.getEmailAddress())) {
            throw ApiProblemException.validationFailed("The address is the current one",
                List.of(new FieldViolation("newEmail", "unchanged", "This is already your sign-in address")));
        }
        if (userRepository.existsByEmailAddressIgnoreCase(email)) {
            throw taken(null);
        }
    }

    private static ApiProblemException taken(Throwable cause) {
        return new ApiProblemException(HttpStatus.CONFLICT, "email-taken",
            "An account with this address already exists", cause);
    }
}

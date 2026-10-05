package ua.edu.chnu.awards.auth.service;

import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.dto.EmailChangeResponse;
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

import lombok.RequiredArgsConstructor;

/**
 * Change of the sign-in address: requested with the current password, confirmed from the new mailbox. The
 * confirmation signs the user out everywhere, because every session and token carries the old address, and
 * leaves the previous address a 24-hour "this was not me" link that moves the account back.
 */
@Service
@RequiredArgsConstructor
public class EmailChangeService {

    /** Redis key prefix of the one-request-a-minute marker, followed by the user id. */
    public static final String REQUEST_KEY_PREFIX = "auth:email-change:";

    private final UserRepository userRepository;
    private final OneTimeTokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService attempts;
    private final AuthorizationRevoker authorizations;
    private final RequestThrottle throttle;
    private final EmailAddressRules addressRules;
    private final AuthProperties properties;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    /**
     * Sends a confirmation link to the new address and cancels older links. A wrong password counts as a failed
     * sign-in; the failure that locks the account also signs it out, and that survives the refusal. The minute
     * claimed by the request is given back when the request does not commit.
     *
     * @param userId          the caller
     * @param newEmail        the requested address
     * @param currentPassword the caller's password
     * @throws ApiProblemException 423 {@code account-locked} while the address is locked after failed sign-ins,
     *                             403 {@code password-mismatch}, 422 {@code institutional-email-required} or
     *                             {@code validation-failed} (unchanged address), 409 {@code email-taken},
     *                             429 {@code too-many-requests}
     */
    @Transactional(noRollbackFor = ApiProblemException.class)
    public void request(long userId, String newEmail, String currentPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (attempts.isLocked(user.getEmailAddress())) {
            throw new ApiProblemException(HttpStatus.LOCKED, "account-locked",
                "The account is locked after failed sign-ins; try again later");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            if (attempts.recordFailure(user.getEmailAddress(), ClientRequest.current().ip())) {
                authorizations.revokeAll(user);
            }
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "password-mismatch", "The password is wrong");
        }
        String email = EmailUtils.normalize(newEmail);
        requireAvailable(user, email);
        if (!throttle.claimForTransaction(REQUEST_KEY_PREFIX + userId, properties.resendInterval())) {
            throw new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests",
                "A confirmation link was sent recently; try again in a minute");
        }
        tokens.invalidate(user, TokenPurpose.EMAIL_CHANGE);
        String raw = tokens.issue(user, TokenPurpose.EMAIL_CHANGE, properties.emailChangeTtl(), email);
        audit.record(AuditAction.EMAIL_CHANGE_REQUESTED, AuditEntityConstants.USER, userId, userId,
            Map.of("newEmail", email));
        events.publishEvent(new EmailChangeRequested(email, user.getEmailAddress(), user.getFirstName(),
            properties.link("/confirm-email-change", raw), properties.frontendUrl() + "/forgot-password"));
    }

    /**
     * Moves the account to the address of a confirmation link and signs it out everywhere. Password reset and
     * address links stop working; "this was not me" links already mailed, and a new one sent to the old address
     * with the notice, are bound to the old address, so using one moves the account back. While the current
     * address is locked after failed sign-ins the account stays where it is.
     *
     * @param rawToken token from the link
     * @return the account and its new sign-in address
     * @throws ApiProblemException 410 {@code token-invalid} (also while the account is locked), 409
     *                             {@code account-not-active} or {@code email-taken}; the account keeps its
     *                             address
     */
    @Transactional
    public EmailChangeResponse confirm(String rawToken) {
        OneTimeToken token = tokens.redeem(rawToken, TokenPurpose.EMAIL_CHANGE)
            .orElseThrow(OneTimeTokenService::gone);
        User user = token.getUser();
        if (!user.isActive()) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "account-not-active", "The account is not active");
        }
        String oldEmail = user.getEmailAddress();
        if (attempts.isLocked(oldEmail)) {
            throw OneTimeTokenService.gone();
        }
        String newEmail = token.getNewEmailAddress();
        if (userRepository.existsByEmailAddressIgnoreCase(newEmail)) {
            throw addressRules.taken(null);
        }
        user.setEmailAddress(newEmail);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            user.setEmailAddress(oldEmail);
            throw addressRules.taken(e);
        }
        authorizations.revokeAll(user.getId(), oldEmail);
        tokens.invalidate(user, TokenPurpose.EMAIL_CHANGE);
        tokens.invalidate(user, TokenPurpose.PASSWORD_RESET);
        tokens.bindAddress(user, TokenPurpose.SECURITY_REVOKE, oldEmail);
        String revoke = tokens.issue(user, TokenPurpose.SECURITY_REVOKE, properties.securityRevokeTtl(), oldEmail);
        audit.record(AuditAction.EMAIL_CHANGED, AuditEntityConstants.USER, user.getId(), user.getId(),
            Map.of("oldEmail", oldEmail, "newEmail", newEmail));
        events.publishEvent(new EmailChanged(oldEmail, newEmail, user.getFirstName(),
            properties.link("/security/not-me", revoke)));
        return new EmailChangeResponse(user.getId(), newEmail);
    }

    private void requireAvailable(User user, String email) {
        addressRules.requireInstitutional(email, "Only institutional addresses can be used to sign in");
        if (email.equalsIgnoreCase(user.getEmailAddress())) {
            throw ApiProblemException.validationFailed("The address is the current one",
                List.of(new FieldViolation("newEmail", "unchanged", "This is already your sign-in address")));
        }
        if (userRepository.existsByEmailAddressIgnoreCase(email)) {
            throw addressRules.taken(null);
        }
    }
}

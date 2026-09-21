package ua.edu.chnu.awards.auth.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.auth.event.PasswordResetRequested;
import ua.edu.chnu.awards.auth.security.AuthorizationRevoker;
import ua.edu.chnu.awards.common.EmailUtils;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.config.AuthProperties;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Password reset by email link. Requests never reveal whether an address is known; a confirmed reset signs the
 * user out everywhere.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final String REQUEST_KEY_PREFIX = "auth:reset:";

    private final UserRepository userRepository;
    private final OneTimeTokenService tokens;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final AuthorizationRevoker authorizations;
    private final RequestThrottle throttle;
    private final AuthProperties properties;
    private final AuditService audit;

    /**
     * Sends a reset link to an active account, at most once per interval per address. Unknown, pending and
     * disabled addresses are silently ignored.
     *
     * @param email the address
     */
    @Transactional
    public void request(String email) {
        String normalized = EmailUtils.normalize(email);
        if (!throttle.claim(REQUEST_KEY_PREFIX + normalized, properties.resendInterval())) {
            return;
        }
        userRepository.findByEmailAddressIgnoreCase(normalized)
            .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
            .ifPresent(user -> {
                String raw = tokens.issue(user, TokenPurpose.PASSWORD_RESET, properties.passwordResetTtl());
                String link = properties.link("/reset-password", raw);
                audit.record(AuditAction.PASSWORD_RESET_REQUESTED, user.getId());
                events.publishEvent(new PasswordResetRequested(user.getEmailAddress(), user.getFirstName(), link));
            });
    }

    /**
     * Replaces the password behind a reset token (it must differ from the current one) and signs the user out
     * everywhere.
     *
     * @param rawToken    token from the link
     * @param newPassword the password to set
     */
    @Transactional
    public void confirm(String rawToken, String newPassword) {
        passwordPolicy.require(newPassword);
        User user = tokens.redeemOwner(rawToken, TokenPurpose.PASSWORD_RESET);
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "account-not-active",
                "The account is not active");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "password-same-as-current",
                "The new password must differ from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        tokens.invalidate(user, TokenPurpose.SECURITY_REVOKE);
        authorizations.revokeAll(user);
        audit.record(AuditAction.PASSWORD_RESET, user.getId());
    }
}

package ua.edu.chnu.awards.auth.service;

import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.entity.OneTimeToken;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.auth.event.PasswordResetRequested;
import ua.edu.chnu.awards.auth.security.AuthorizationRevoker;
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
    private final StringRedisTemplate redis;
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
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        Boolean first = redis.opsForValue()
            .setIfAbsent(REQUEST_KEY_PREFIX + normalized, "1", properties.resendInterval());
        if (Boolean.FALSE.equals(first)) {
            return;
        }
        userRepository.findByEmailAddressIgnoreCase(normalized)
            .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
            .ifPresent(user -> {
                String raw = tokens.issue(user, TokenPurpose.PASSWORD_RESET, properties.passwordResetTtl());
                String link = properties.frontendUrl() + "/reset-password?token=" + raw;
                audit.record(AuditAction.PASSWORD_RESET_REQUESTED, user.getId());
                events.publishEvent(new PasswordResetRequested(user.getEmailAddress(), user.getFirstName(), link));
            });
    }

    /**
     * Replaces the password behind a reset token and revokes every authorization of the user.
     *
     * @param rawToken    token from the link
     * @param newPassword the password to set
     */
    @Transactional
    public void confirm(String rawToken, String newPassword) {
        String problem = passwordPolicy.problem(newPassword);
        if (!problem.isEmpty()) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "password-" + problem,
                "The password does not meet the policy (" + problem + ")");
        }
        User user = tokens.redeem(rawToken, TokenPurpose.PASSWORD_RESET)
            .map(OneTimeToken::getUser)
            .orElseThrow(() -> new ApiProblemException(HttpStatus.GONE, "token-invalid",
                "The reset link is invalid, expired or already used"));
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "account-not-active",
                "The account is not active");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        authorizations.revokeAll(user.getEmailAddress());
        audit.record(AuditAction.PASSWORD_RESET, user.getId());
    }
}

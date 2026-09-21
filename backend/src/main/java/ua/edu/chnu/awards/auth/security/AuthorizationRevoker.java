package ua.edu.chnu.awards.auth.security;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ua.edu.chnu.awards.config.AuthProperties;
import ua.edu.chnu.awards.user.entity.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Signs a user out everywhere: deletes every authorization (codes, access and refresh tokens) the authorization
 * server holds for the principal, expires the login sessions of the server itself, and records the instant in
 * Redis so that access tokens issued up to that second are refused by the API at once instead of living out
 * their lifetime. The instant is written once the surrounding transaction has committed, so a refresh racing
 * with the deletion cannot mint a token that outlives it. {@code OAuth2AuthorizationService} has no lookup by
 * principal, so the rows are deleted directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorizationRevoker {

    static final String NOT_BEFORE_KEY_PREFIX = "auth:nbf:";
    /** Leeway the resource server grants on {@code exp}; the key must outlive it. */
    static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final JdbcTemplate jdbc;
    private final SessionRegistry sessions;
    private final StringRedisTemplate redis;
    private final AuthProperties properties;
    private final Clock clock;

    /**
     * Deletes all authorizations of the user, expires its login sessions and invalidates its access tokens.
     *
     * @param user the account being signed out
     * @return number of authorizations removed
     */
    public int revokeAll(User user) {
        String principalName = user.getEmailAddress();
        List<SessionInformation> logins = sessions.getAllPrincipals().stream()
            .filter(principal -> principal instanceof UserDetails details
                && details.getUsername().equalsIgnoreCase(principalName))
            .flatMap(principal -> sessions.getAllSessions(principal, false).stream())
            .toList();
        logins.forEach(SessionInformation::expireNow);
        int authorizations = jdbc.update("delete from oauth2_authorization where principal_name = ?", principalName);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    markRevoked(user.getId());
                }
            });
        } else {
            markRevoked(user.getId());
        }
        log.debug("Revoked {} authorizations and {} login sessions of {}", authorizations, logins.size(),
            principalName);
        return authorizations;
    }

    private void markRevoked(Long userId) {
        try {
            redis.opsForValue().set(NOT_BEFORE_KEY_PREFIX + userId,
                String.valueOf(clock.instant().getEpochSecond()),
                properties.client().accessTokenTtl().plus(CLOCK_SKEW));
        } catch (DataAccessException e) {
            log.error("Redis unavailable; access tokens of user {} stay valid until they expire: {}", userId,
                e.getMessage());
        }
    }
}

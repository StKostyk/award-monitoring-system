package ua.edu.chnu.awards.auth.security;

import java.time.Instant;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Refuses access tokens issued up to the second of the user's last sign-out-everywhere (password reset, "this
 * was not me"); both instants have second precision, so a token minted in the same second is refused too.
 * The instant is kept in Redis by {@link AuthorizationRevoker} for the access-token lifetime; without Redis the
 * token is accepted and the outage logged.
 */
@RequiredArgsConstructor
@Slf4j
public final class RevokedTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "Token revoked", null);

    private final StringRedisTemplate redis;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Instant issuedAt = token.getIssuedAt();
        if (issuedAt == null) {
            return OAuth2TokenValidatorResult.success();
        }
        try {
            String notBefore = redis.opsForValue()
                .get(AuthorizationRevoker.NOT_BEFORE_KEY_PREFIX + token.getSubject());
            if (notBefore != null && issuedAt.getEpochSecond() <= Long.parseLong(notBefore)) {
                return OAuth2TokenValidatorResult.failure(REVOKED);
            }
        } catch (DataAccessException e) {
            log.error("Redis unavailable; token revocation not checked: {}", e.getMessage());
        }
        return OAuth2TokenValidatorResult.success();
    }
}

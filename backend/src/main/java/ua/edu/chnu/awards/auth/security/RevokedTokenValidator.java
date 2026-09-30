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
 * Refuses access tokens issued up to the user's last sign-out-everywhere (password reset, "this was not me",
 * address change), compared in milliseconds through the {@code iat_ms} claim so a sign-in right after the
 * revocation works. A token without that claim is compared by its {@code iat} second and refused when minted in
 * the second of the revocation. The instant is kept in Redis by {@link AuthorizationRevoker} for the
 * access-token lifetime; without Redis the token is accepted and the outage logged.
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
            if (notBefore != null && issuedMillis(token, issuedAt) <= Long.parseLong(notBefore)) {
                return OAuth2TokenValidatorResult.failure(REVOKED);
            }
        } catch (DataAccessException e) {
            log.error("Redis unavailable; token revocation not checked: {}", e.getMessage());
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static long issuedMillis(Jwt token, Instant issuedAt) {
        String millis = token.getClaimAsString(TokenClaimsCustomizer.CLAIM_ISSUED_AT_MILLIS);
        return millis == null ? issuedAt.toEpochMilli() : Long.parseLong(millis);
    }
}

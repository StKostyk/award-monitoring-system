package ua.edu.chnu.awards.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;

/**
 * Detects a rotated refresh token being presented again and revokes the whole authorization it belonged to.
 * The hash of every refresh token that was rotated is remembered in Redis for the token lifetime.
 */
public final class RefreshTokenReuseGuard implements AuthenticationProvider {

    static final String KEY_PREFIX = "auth:rotated:";

    private final AuthenticationProvider delegate;
    private final OAuth2AuthorizationService authorizationService;
    private final StringRedisTemplate redis;
    private final Duration remember;

    public RefreshTokenReuseGuard(AuthenticationProvider delegate, OAuth2AuthorizationService authorizationService,
                                  StringRedisTemplate redis, Duration remember) {
        this.delegate = delegate;
        this.authorizationService = authorizationService;
        this.redis = redis;
        this.remember = remember;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        OAuth2RefreshTokenAuthenticationToken request = (OAuth2RefreshTokenAuthenticationToken) authentication;
        String presented = request.getRefreshToken();
        OAuth2Authorization authorization = authorizationService.findByToken(presented, OAuth2TokenType.REFRESH_TOKEN);
        if (authorization == null) {
            revokeFamilyOf(presented);
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        Authentication result = delegate.authenticate(authentication);
        redis.opsForValue().set(KEY_PREFIX + sha256(presented), authorization.getId(), remember);
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }

    private void revokeFamilyOf(String presented) {
        String key = KEY_PREFIX + sha256(presented);
        String authorizationId = redis.opsForValue().get(key);
        if (authorizationId == null) {
            return;
        }
        OAuth2Authorization family = authorizationService.findById(authorizationId);
        if (family != null) {
            authorizationService.remove(family);
        }
        redis.delete(key);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}

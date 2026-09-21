package ua.edu.chnu.awards.auth.security;

import java.time.Duration;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;

import ua.edu.chnu.awards.common.HashUtils;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Guards the refresh grant: a rotated refresh token presented again revokes the whole authorization it belonged
 * to (the hash of every rotated token is remembered in Redis for the token lifetime), and an account whose status no
 * longer allows signing in cannot refresh either.
 */
@Slf4j
public final class RefreshTokenReuseGuard implements AuthenticationProvider {

    static final String KEY_PREFIX = "auth:rotated:";

    private final AuthenticationProvider delegate;
    private final OAuth2AuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final StringRedisTemplate redis;
    private final Duration remember;

    public RefreshTokenReuseGuard(AuthenticationProvider delegate, OAuth2AuthorizationService authorizationService,
                                  UserRepository userRepository, StringRedisTemplate redis, Duration remember) {
        this.delegate = delegate;
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
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
        AccountStatus status = userRepository.findByEmailAddressIgnoreCase(authorization.getPrincipalName())
            .map(User::getAccountStatus)
            .orElse(AccountStatus.DELETED);
        if (!status.canLogIn()) {
            authorizationService.remove(authorization);
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + sha256(presented), authorization.getId(), remember);
        } catch (DataAccessException e) {
            log.error("Redis unavailable; rotated refresh token not remembered for reuse detection: {}",
                e.getMessage());
        }
        return delegate.authenticate(authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }

    private void revokeFamilyOf(String presented) {
        String key = KEY_PREFIX + sha256(presented);
        try {
            String authorizationId = redis.opsForValue().get(key);
            if (authorizationId == null) {
                return;
            }
            OAuth2Authorization family = authorizationService.findById(authorizationId);
            if (family != null) {
                authorizationService.remove(family);
            }
            redis.delete(key);
        } catch (DataAccessException e) {
            log.error("Redis unavailable; refresh token reuse could not be checked: {}", e.getMessage());
        }
    }

    static String sha256(String value) {
        return HashUtils.sha256Hex(value);
    }
}

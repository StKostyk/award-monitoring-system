package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;

import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class RefreshTokenReuseGuardTest {

    private static final String PRESENTED = "refresh-token-value";
    private static final String KEY = RefreshTokenReuseGuard.KEY_PREFIX + RefreshTokenReuseGuard.sha256(PRESENTED);

    private final AuthenticationProvider delegate = mock(AuthenticationProvider.class);
    private final OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Authentication client = mock(Authentication.class);
    private final RefreshTokenReuseGuard guard =
        new RefreshTokenReuseGuard(delegate, authorizationService, userRepository, redis, Duration.ofDays(7));

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(userRepository.findByEmailAddressIgnoreCase("dean@chnu.edu.ua"))
            .thenReturn(Optional.of(User.builder().accountStatus(AccountStatus.ACTIVE).build()));
    }

    @Test
    void ac15_validRefreshIsDelegatedAndTheOldTokenIsRemembered() {
        OAuth2RefreshTokenAuthenticationToken request = new OAuth2RefreshTokenAuthenticationToken(PRESENTED, client,
            null, null);
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorization.getId()).thenReturn("auth-1");
        when(authorization.getPrincipalName()).thenReturn("dean@chnu.edu.ua");
        when(authorizationService.findByToken(PRESENTED, OAuth2TokenType.REFRESH_TOKEN)).thenReturn(authorization);
        Authentication issued = mock(Authentication.class);
        when(delegate.authenticate(request)).thenReturn(issued);

        Authentication result = guard.authenticate(request);

        assertThat(result).isSameAs(issued);
        verify(values).set(KEY, "auth-1", Duration.ofDays(7));
    }

    @Test
    void ac15_reusedTokenRevokesTheAuthorizationItCameFrom() {
        OAuth2RefreshTokenAuthenticationToken request = new OAuth2RefreshTokenAuthenticationToken(PRESENTED, client,
            null, null);
        when(authorizationService.findByToken(PRESENTED, OAuth2TokenType.REFRESH_TOKEN)).thenReturn(null);
        when(values.get(KEY)).thenReturn("auth-1");
        OAuth2Authorization family = mock(OAuth2Authorization.class);
        when(authorizationService.findById("auth-1")).thenReturn(family);

        assertThatThrownBy(() -> guard.authenticate(request))
            .isInstanceOf(OAuth2AuthenticationException.class)
            .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
            .isEqualTo("invalid_grant");
        verify(authorizationService).remove(family);
        verify(redis).delete(KEY);
        verify(delegate, never()).authenticate(any());
    }

    @Test
    void suspendedOrDeletedAccountsCannotRefreshAndLoseTheAuthorization() {
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorization.getPrincipalName()).thenReturn("gone@chnu.edu.ua");
        when(authorizationService.findByToken(PRESENTED, OAuth2TokenType.REFRESH_TOKEN)).thenReturn(authorization);
        when(userRepository.findByEmailAddressIgnoreCase("gone@chnu.edu.ua"))
            .thenReturn(Optional.of(User.builder().accountStatus(AccountStatus.SUSPENDED).build()), Optional.empty());
        OAuth2RefreshTokenAuthenticationToken request = new OAuth2RefreshTokenAuthenticationToken(PRESENTED, client,
            null, null);

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> guard.authenticate(request)).isInstanceOf(OAuth2AuthenticationException.class);
        }
        verify(authorizationService, times(2)).remove(authorization);
        verify(delegate, never()).authenticate(any());
    }

    @Test
    void unknownTokenIsRejectedWithoutSideEffects() {
        OAuth2RefreshTokenAuthenticationToken request = new OAuth2RefreshTokenAuthenticationToken("never-seen", client,
            null, null);
        when(authorizationService.findByToken(eq("never-seen"), any())).thenReturn(null);
        when(values.get(any())).thenReturn(null);

        assertThatThrownBy(() -> guard.authenticate(request)).isInstanceOf(OAuth2AuthenticationException.class);
        verify(authorizationService, never()).remove(any());
    }

    @Test
    void supportsWhatTheDelegateSupports() {
        when(delegate.supports(OAuth2RefreshTokenAuthenticationToken.class)).thenReturn(true);

        assertThat(guard.supports(OAuth2RefreshTokenAuthenticationToken.class)).isTrue();
        assertThat(RefreshTokenReuseGuard.sha256("a")).hasSize(64);
    }
}

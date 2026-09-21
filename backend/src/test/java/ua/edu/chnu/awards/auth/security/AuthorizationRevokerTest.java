package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ua.edu.chnu.awards.config.AuthProperties;
import ua.edu.chnu.awards.user.entity.User;

class AuthorizationRevokerTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SessionRegistryImpl sessions = new SessionRegistryImpl();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final AuthProperties properties = new AuthProperties("http://localhost:8080", "http://localhost:4200",
        List.of(), List.of("chnu.edu.ua"), Duration.ofHours(24), Duration.ofHours(1), Duration.ofHours(24),
        Duration.ofMinutes(1),
        new AuthProperties.Client("award-web", List.of(), List.of(), Duration.ofMinutes(15), Duration.ofDays(7)),
        new AuthProperties.Jwk("", "", ""));
    private final AuthorizationRevoker revoker = new AuthorizationRevoker(jdbc, sessions, redis, properties,
        Clock.fixed(NOW, ZoneOffset.UTC));
    private final User olena = User.builder().id(7L).emailAddress("olena@chnu.edu.ua").build();

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void ac32_deletesEveryAuthorizationAndExpiresEveryLoginSessionOfThePrincipal() {
        UserDetails olenaLogin = org.springframework.security.core.userdetails.User
            .withUsername("olena@chnu.edu.ua").password("x").authorities("ROLE_X").build();
        UserDetails other = org.springframework.security.core.userdetails.User
            .withUsername("other@chnu.edu.ua").password("x").authorities("ROLE_X").build();
        sessions.registerNewSession("s1", olenaLogin);
        sessions.registerNewSession("s2", olenaLogin);
        sessions.registerNewSession("s3", other);
        when(jdbc.update("delete from oauth2_authorization where principal_name = ?", "olena@chnu.edu.ua"))
            .thenReturn(3);

        assertThat(revoker.revokeAll(olena)).isEqualTo(3);

        assertThat(sessions.getSessionInformation("s1").isExpired()).isTrue();
        assertThat(sessions.getSessionInformation("s2").isExpired()).isTrue();
        assertThat(sessions.getSessionInformation("s3").isExpired()).isFalse();
    }

    @Test
    void ac65_recordsTheRevocationInstantSoEarlierAccessTokensAreRefusedImmediately() {
        revoker.revokeAll(olena);

        verify(values).set("auth:nbf:7", String.valueOf(NOW.getEpochSecond()), Duration.ofMinutes(16));
    }

    @Test
    void ac65_insideATransactionTheInstantIsWrittenOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            revoker.revokeAll(olena);
            verify(values, never()).set(anyString(), anyString(), any(Duration.class));

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(values).set("auth:nbf:7", String.valueOf(NOW.getEpochSecond()), Duration.ofMinutes(16));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void ac65_revocationStillHappensWhenRedisIsDown() {
        doThrow(new QueryTimeoutException("down")).when(values).set(anyString(), anyString(), any(Duration.class));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertThat(revoker.revokeAll(olena)).isEqualTo(1);
    }
}

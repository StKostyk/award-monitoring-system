package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.event.AccountLocked;
import ua.edu.chnu.awards.config.ProtectionProperties;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

class LoginAttemptServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final String EMAIL = "dean@chnu.edu.ua";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ProtectionProperties properties = new ProtectionProperties(5, Duration.ofMinutes(15),
        Duration.ofMinutes(30), 20);
    private final LoginAttemptService service = new LoginAttemptService(redis, properties, userRepository,
        userRoleRepository, audit, events, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void ac41_firstFailureStartsTheWindowAndDoesNotLock() {
        when(values.increment("auth:fail:" + EMAIL)).thenReturn(1L);

        assertThat(service.recordFailure(" Dean@chnu.edu.ua\r\n", "203.0.113.7")).isFalse();

        verify(redis).expire("auth:fail:" + EMAIL, Duration.ofMinutes(15));
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(events, audit);
    }

    @Test
    void ac41_ac44_fifthFailureLocksNotifiesAdminsAndAudits() {
        when(values.increment("auth:fail:" + EMAIL)).thenReturn(5L);
        when(userRepository.findByEmailAddressIgnoreCase(EMAIL))
            .thenReturn(Optional.of(User.builder().id(5L).emailAddress(EMAIL).build()));
        when(userRoleRepository.findCurrentEmailsByRole(RoleType.SYSTEM_ADMIN, LocalDate.of(2026, 9, 21)))
            .thenReturn(List.of("admin@chnu.edu.ua"));

        assertThat(service.recordFailure(EMAIL, "203.0.113.7")).isTrue();

        verify(values).set("auth:lock:" + EMAIL, "1", Duration.ofMinutes(30));
        verify(redis).delete("auth:fail:" + EMAIL);
        verify(audit).record(eq(AuditAction.ACCOUNT_LOCKED), eq(5L), any());
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        AccountLocked locked = (AccountLocked) event.getValue();
        assertThat(locked.email()).isEqualTo(EMAIL);
        assertThat(locked.ip()).isEqualTo("203.0.113.7");
        assertThat(locked.at()).isEqualTo(NOW);
        assertThat(locked.lockedFor()).isEqualTo(Duration.ofMinutes(30));
        assertThat(locked.recipients()).containsExactly("admin@chnu.edu.ua");
    }

    @Test
    void ac41_unknownAddressIsLockedSilently() {
        when(values.increment("auth:fail:ghost@chnu.edu.ua")).thenReturn(5L);
        when(userRepository.findByEmailAddressIgnoreCase("ghost@chnu.edu.ua")).thenReturn(Optional.empty());

        assertThat(service.recordFailure("ghost@chnu.edu.ua", "203.0.113.7")).isTrue();

        verify(values).set("auth:lock:ghost@chnu.edu.ua", "1", Duration.ofMinutes(30));
        verifyNoInteractions(events, audit);
    }

    @Test
    void ac41_onlyTheFailureThatReachesTheLimitLocks() {
        when(values.increment("auth:fail:" + EMAIL)).thenReturn(6L);

        assertThat(service.recordFailure(EMAIL, "203.0.113.7")).isFalse();

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void ac41_ac45_lockStateIsReadFromRedisAndResetClearsOnlyTheCounter() {
        when(redis.hasKey("auth:lock:" + EMAIL)).thenReturn(true, false);

        assertThat(service.isLocked("DEAN@chnu.edu.ua")).isTrue();
        assertThat(service.isLocked(EMAIL)).isFalse();

        service.reset(EMAIL);
        verify(redis).delete("auth:fail:" + EMAIL);
        verify(redis, never()).delete("auth:lock:" + EMAIL);
    }

    @Test
    void normalisesTypedAddresses() {
        assertThat(LoginAttemptService.normalize(null)).isEmpty();
        assertThat(LoginAttemptService.normalize("  A\u0000b@X.ua\n")).isEqualTo("ab@x.ua");
        assertThat(LoginAttemptService.normalize("x".repeat(300))).hasSize(254);
    }

    @Test
    void redisOutageFailsOpen() {
        when(redis.hasKey(anyString())).thenThrow(new QueryTimeoutException("down"));
        when(values.increment(anyString())).thenThrow(new QueryTimeoutException("down"));
        when(redis.delete(anyString())).thenThrow(new QueryTimeoutException("down"));

        assertThat(service.isLocked(EMAIL)).isFalse();
        assertThat(service.recordFailure(EMAIL, "203.0.113.7")).isFalse();
        service.reset(EMAIL);

        verifyNoInteractions(events, audit);
    }
}

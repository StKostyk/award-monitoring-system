package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RequestThrottleTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RequestThrottle throttle = new RequestThrottle(redis);

    @Test
    void ac26_ac31_firstClaimWinsAndTheSecondInsideTheIntervalLoses() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("auth:resend:a@chnu.edu.ua", "1", MINUTE)).thenReturn(true, false);

        assertThat(throttle.claim("auth:resend:a@chnu.edu.ua", MINUTE)).isTrue();
        assertThat(throttle.claim("auth:resend:a@chnu.edu.ua", MINUTE)).isFalse();
    }

    @Test
    void redisOutageAllowsTheRequest() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(throttle.claim("auth:reset:a@chnu.edu.ua", MINUTE)).isTrue();
    }

    @Test
    void aReleasedClaimIsDeletedAndARedisOutageIsOnlyLogged() {
        throttle.release("gdpr:export:5");
        verify(redis).delete("gdpr:export:5");

        when(redis.delete("gdpr:export:6")).thenThrow(new RedisConnectionFailureException("down"));
        throttle.release("gdpr:export:6");
        verify(redis).delete("gdpr:export:6");
    }

    @Test
    void ac14_ac34_aClaimOfARequestThatDoesNotCommitIsGivenBack() {
        claimInTransaction(true);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(redis).delete("auth:email-change:12");
    }

    @Test
    void ac14_ac34_aCommittedRequestKeepsItsClaim() {
        claimInTransaction(true);

        complete(TransactionSynchronization.STATUS_COMMITTED);

        verify(redis, never()).delete(anyString());
    }

    @Test
    void ac14_ac34_aRefusedClaimRegistersNothing() {
        claimInTransaction(false);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void claimInTransaction(boolean free) {
        TransactionSynchronizationManager.initSynchronization();
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("auth:email-change:12", "1", MINUTE)).thenReturn(free);

        assertThat(throttle.claimForTransaction("auth:email-change:12", MINUTE)).isEqualTo(free);
    }

    private static void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }
}

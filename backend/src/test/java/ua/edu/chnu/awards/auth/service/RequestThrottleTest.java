package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
}

package ua.edu.chnu.awards.common.limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class FixedWindowCounterTest {

    private static final String PREFIX = "test:rate:";
    private static final long LIMIT = 3;
    private static final Instant LAST_SECOND = Instant.parse("2026-10-05T10:00:59Z");
    private static final long WINDOW = LAST_SECOND.getEpochSecond() / 60;
    private static final Duration KEY_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final FixedWindowCounter counter = new FixedWindowCounter(redis, Clock.fixed(LAST_SECOND, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void eventsUpToTheLimitPassAndOnlyTheFirstSetsTheExpiry() {
        String key = PREFIX + "21:" + WINDOW;
        when(values.increment(key)).thenReturn(1L, LIMIT);

        assertThat(counter.secondsToWait(PREFIX, 21L, LIMIT)).isZero();
        assertThat(counter.secondsToWait(PREFIX, 21L, LIMIT)).isZero();
        verify(redis).expire(key, KEY_TTL);
    }

    @Test
    void anEventOverTheLimitWaitsAtLeastOneSecond() {
        when(values.increment(anyString())).thenReturn(LIMIT + 1);

        assertThat(counter.secondsToWait(PREFIX, "203.0.113.7", LIMIT)).isEqualTo(1);
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void withoutRedisNothingIsLimited() {
        when(values.increment(anyString())).thenThrow(new QueryTimeoutException("down"));

        assertThat(counter.secondsToWait(PREFIX, 21L, 0)).isZero();
    }
}

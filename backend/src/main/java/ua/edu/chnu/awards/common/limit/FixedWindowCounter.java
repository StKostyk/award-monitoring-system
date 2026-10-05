package ua.edu.chnu.awards.common.limit;

import java.time.Clock;
import java.time.Duration;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fixed one-minute windows in Redis: one counter per subject and minute, kept for two minutes. Without Redis
 * nothing is limited.
 */
@Slf4j
@RequiredArgsConstructor
public class FixedWindowCounter {

    private static final long WINDOW_SECONDS = 60;
    private static final Duration KEY_TTL = Duration.ofSeconds(2 * WINDOW_SECONDS);

    private final StringRedisTemplate redis;
    private final Clock clock;

    /**
     * Counts one event of the subject in the current window.
     *
     * @param keyPrefix the prefix of the window keys, e.g. {@code auth:rate:}
     * @param subject   the client address or user the events belong to
     * @param limit     the events allowed in one window
     * @return the seconds until the window ends, at least 1, when the event is over the limit; 0 when it is
     *         allowed or Redis cannot be reached
     */
    public long secondsToWait(String keyPrefix, Object subject, long limit) {
        long now = clock.instant().getEpochSecond();
        long window = now / WINDOW_SECONDS;
        if (count(keyPrefix + subject + ":" + window, keyPrefix) <= limit) {
            return 0;
        }
        return Math.max(1, (window + 1) * WINDOW_SECONDS - now);
    }

    private long count(String key, String keyPrefix) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, KEY_TTL);
            }
            return count == null ? 0 : count;
        } catch (DataAccessException e) {
            log.error("Redis unavailable; {}* is not rate limited: {}", keyPrefix, e.getMessage());
            return 0;
        }
    }
}

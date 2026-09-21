package ua.edu.chnu.awards.auth.service;

import java.time.Duration;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * One-claim-per-interval markers in Redis for emails that must not be sent twice in a row. When Redis is
 * unavailable the request is allowed and the outage logged, like the login counters.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestThrottle {

    private final StringRedisTemplate redis;

    /**
     * Claims the key for the interval.
     *
     * @param key      the marker key
     * @param interval how long the claim lasts
     * @return true when the key was free (or Redis is down), false when a claim is still active
     */
    public boolean claim(String key, Duration interval) {
        try {
            return !Boolean.FALSE.equals(redis.opsForValue().setIfAbsent(key, "1", interval));
        } catch (DataAccessException e) {
            log.error("Redis unavailable; request not throttled: {}", e.getMessage());
            return true;
        }
    }
}

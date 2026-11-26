package ua.edu.chnu.awards.common.limit;

import java.time.Duration;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    /**
     * Claims the key for the interval and gives the claim back when the surrounding transaction does not commit,
     * so a request that failed half-way can be repeated at once. Must be called inside a transaction.
     *
     * @param key      the marker key
     * @param interval how long the claim lasts
     * @return true when the key was free (or Redis is down), false when a claim is still active
     */
    public boolean claimForTransaction(String key, Duration interval) {
        if (!claim(key, interval)) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    release(key);
                }
            }
        });
        return true;
    }

    /**
     * Gives a claim back, for a request that did not complete.
     *
     * @param key the marker key
     */
    public void release(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException e) {
            log.error("Redis unavailable; claim {} not released: {}", key, e.getMessage());
        }
    }
}

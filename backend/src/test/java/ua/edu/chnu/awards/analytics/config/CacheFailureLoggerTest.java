package ua.edu.chnu.awards.analytics.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.RedisConnectionFailureException;

class CacheFailureLoggerTest {

    private final CacheFailureLogger handler = new CacheFailureLogger();
    private final ConcurrentMapCache cache = new ConcurrentMapCache("unit-analytics");
    private final RuntimeException failure = new RedisConnectionFailureException("Redis is down");

    @Test
    void ac1_9_aFailedCacheOperationIsLoggedAndTheCallGoesOn() {
        assertThatCode(() -> {
            handler.handleCacheGetError(failure, cache, "9:2022:2026");
            handler.handleCachePutError(failure, cache, "9:2022:2026", "value");
            handler.handleCacheEvictError(failure, cache, "9:2022:2026");
            handler.handleCacheClearError(failure, cache);
        }).doesNotThrowAnyException();
    }

    @Test
    void ac1_9_theAnalyticsLimitsRefuseNonPositiveValues() {
        assertThatCode(() -> new AnalyticsProperties(5, 500, 10, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new AnalyticsProperties(5, 500, 0, Duration.ofMinutes(5)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

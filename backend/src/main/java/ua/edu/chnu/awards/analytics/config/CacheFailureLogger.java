package ua.edu.chnu.awards.analytics.config;

import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * Logs a failed cache operation and lets the call go on, so an unavailable Redis costs a recomputation, not the
 * request.
 */
@Slf4j
public class CacheFailureLogger implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache {} could not be read for {}; computing without it: {}", cache.getName(), key,
            exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Cache {} could not be written for {}: {}", cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache {} could not be evicted for {}: {}", cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Cache {} could not be cleared: {}", cache.getName(), exception.getMessage());
    }
}

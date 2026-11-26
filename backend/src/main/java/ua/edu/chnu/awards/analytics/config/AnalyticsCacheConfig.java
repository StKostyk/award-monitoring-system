package ua.edu.chnu.awards.analytics.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.analytics.service.UnitAggregates;

/**
 * The Redis cache of the unit aggregates: JSON of one known type, never null values, and a Redis failure falls back
 * to computing.
 */
@Configuration
@EnableCaching
public class AnalyticsCacheConfig implements CachingConfigurer {

    /**
     * The cache manager; only the declared caches exist.
     *
     * @param connections the Redis connections
     * @param mapper      the application's JSON mapper
     * @param properties  the analytics limits, with the time to live
     * @param keyPrefix   prefix of every cache key
     * @return the cache manager
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connections, ObjectMapper mapper,
                                          AnalyticsProperties properties,
                                          @Value("${spring.cache.redis.key-prefix:awardapp:}") String keyPrefix) {
        RedisCacheConfiguration unitAggregates = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(properties.unitCacheTtl())
            .disableCachingNullValues()
            .prefixCacheNameWith(keyPrefix)
            .serializeValuesWith(SerializationPair.fromSerializer(
                new Jackson2JsonRedisSerializer<>(mapper.copy(), UnitFigures.class)));
        return RedisCacheManager.builder(connections)
            .withCacheConfiguration(UnitAggregates.CACHE, unitAggregates)
            .disableCreateOnMissingCache()
            .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheFailureLogger();
    }
}

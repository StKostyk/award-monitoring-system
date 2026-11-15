package ua.edu.chnu.awards.auth.web;

import java.time.Duration;
import java.util.OptionalLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * The number of approved awards shown on the sign-in page. The value is shared through Redis and counted again at
 * most once per time-to-live; when Redis or the database cannot answer, the page shows no number.
 */
@Slf4j
@Component
public class LoginAwardCount {

    static final String KEY = "login:award-count";
    static final String SQL = "select count(*) from awards where status = 'APPROVED'";

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final Duration ttl;

    /**
     * Creates the count reader.
     *
     * @param jdbc  database access for the count
     * @param redis shared store of the last count
     * @param ttl   how long a count is reused
     */
    public LoginAwardCount(JdbcTemplate jdbc, StringRedisTemplate redis,
                           @Value("${app.login.award-count-ttl:PT10M}") Duration ttl) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.ttl = ttl;
    }

    /**
     * Reads the count from Redis, or counts and stores it when no value is held.
     *
     * @return the count, or empty when it cannot be read
     */
    public OptionalLong current() {
        try {
            String cached = redis.opsForValue().get(KEY);
            if (cached != null) {
                return OptionalLong.of(Long.parseLong(cached));
            }
            Long counted = jdbc.queryForObject(SQL, Long.class);
            if (counted == null) {
                return OptionalLong.empty();
            }
            redis.opsForValue().set(KEY, Long.toString(counted), ttl);
            return OptionalLong.of(counted);
        } catch (DataAccessException | NumberFormatException e) {
            log.warn("Approved award count unavailable for the sign-in page: {}", e.getMessage());
            return OptionalLong.empty();
        }
    }
}

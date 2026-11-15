package ua.edu.chnu.awards.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

class LoginAwardCountTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final LoginAwardCount count = new LoginAwardCount(jdbc, redis, TTL);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void ac4_2_a_held_count_is_used_without_a_query() {
        when(values.get(LoginAwardCount.KEY)).thenReturn("886");

        assertThat(count.current()).hasValue(886);
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    void ac4_2_a_missing_count_is_counted_and_held_for_the_ttl() {
        when(jdbc.queryForObject(LoginAwardCount.SQL, Long.class)).thenReturn(886L);

        assertThat(count.current()).hasValue(886);
        verify(values).set(LoginAwardCount.KEY, "886", TTL);
    }

    @Test
    void ac4_2_no_number_when_redis_fails() {
        when(values.get(LoginAwardCount.KEY)).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(count.current()).isEmpty();
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    void ac4_2_no_number_when_the_query_fails() {
        when(jdbc.queryForObject(LoginAwardCount.SQL, Long.class)).thenThrow(new QueryTimeoutException("slow"));

        assertThat(count.current()).isEmpty();
    }

    @Test
    void ac4_2_no_number_for_a_corrupt_held_value() {
        when(values.get(LoginAwardCount.KEY)).thenReturn("many");

        assertThat(count.current()).isEmpty();
    }
}

package ua.edu.chnu.awards;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;

class ApplicationContextIT extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void migrationsApplyOnEmptyDatabase() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer applied = jdbc.queryForObject(
            "select count(*) from flyway_schema_history where success", Integer.class);
        Integer tables = jdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = 'public'", Integer.class);

        assertThat(applied).isPositive();
        assertThat(tables).isGreaterThan(10);
    }

    @Test
    void redisIsReachable() {
        redisTemplate.opsForValue().set("smoke", "ok");

        assertThat(redisTemplate.opsForValue().get("smoke")).isEqualTo("ok");
    }
}

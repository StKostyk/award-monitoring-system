package ua.edu.chnu.awards.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.support.TestUsers;

class LoginPageIT extends AbstractIntegrationTest {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final String INSERT_USER = "insert into users (email_address, first_name, last_name, "
        + "password_hash, account_status, organization_id) values (?, 'Test', 'User', 'x', 'ACTIVE', ?) "
        + "returning user_id";

    @Autowired
    private LoginAwardCount awardCount;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    private long ownerId;

    @BeforeEach
    void setUp() {
        redis.delete(LoginAwardCount.KEY);
        ownerId = Objects.requireNonNull(jdbc.queryForObject(INSERT_USER, Long.class, "it.login.count@chnu.edu.ua",
            TestUsers.DAI_DEPARTMENT_ID));
    }

    @AfterEach
    void tearDown() {
        redis.delete(LoginAwardCount.KEY);
        jdbc.query("select user_id from users where email_address like 'it.login.%'",
            (RowCallbackHandler) row -> ConsentRows.purge(jdbc, row.getLong(1)));
        jdbc.update("delete from awards where user_id = ?", ownerId);
        jdbc.update("delete from users where email_address like 'it.login.%'");
    }

    @Test
    void ac4_2_counts_approved_awards_only() {
        final long before = approvedNow();
        AwardRows.award(jdbc, ownerId).status("APPROVED").insert();
        AwardRows.award(jdbc, ownerId).status("APPROVED").insert();
        AwardRows.award(jdbc, ownerId).status("PENDING").insert();
        AwardRows.award(jdbc, ownerId).status("REJECTED").insert();

        assertThat(awardCount.current()).hasValue(before + 2);
    }

    @Test
    void ac4_2_reuses_the_count_for_ten_minutes() {
        long first = awardCount.current().orElseThrow();
        AwardRows.award(jdbc, ownerId).status("APPROVED").insert();

        assertThat(awardCount.current()).hasValue(first);
        assertThat(redis.getExpire(LoginAwardCount.KEY, TimeUnit.SECONDS))
            .isPositive()
            .isLessThanOrEqualTo(DEFAULT_TTL.toSeconds());
    }

    @Test
    void ac4_2_counts_again_once_the_held_value_is_gone() {
        long first = awardCount.current().orElseThrow();
        AwardRows.award(jdbc, ownerId).status("APPROVED").insert();
        redis.delete(LoginAwardCount.KEY);

        assertThat(awardCount.current()).hasValue(first + 1);
    }

    private long approvedNow() {
        return Objects.requireNonNull(jdbc.queryForObject(LoginAwardCount.SQL, Long.class));
    }
}

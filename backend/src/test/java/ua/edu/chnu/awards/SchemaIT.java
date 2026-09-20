package ua.edu.chnu.awards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;

class SchemaIT extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void ac01_latestMigrationIsApplied() {
        String version = jdbc.queryForObject(
            "select version from flyway_schema_history where success and version is not null "
                + "order by installed_rank desc limit 1", String.class);

        assertThat(version).isEqualTo("014");
    }

    @Test
    void ac02_authTablesExist() {
        List<String> tables = jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public' "
                + "and table_name in ('oauth2_registered_client', 'oauth2_authorization', "
                + "'oauth2_authorization_consent', 'one_time_tokens', 'user_devices') order by table_name",
            String.class);

        assertThat(tables).containsExactly("oauth2_authorization", "oauth2_authorization_consent",
            "oauth2_registered_client", "one_time_tokens", "user_devices");
    }

    @Test
    void ac02_authTablesHaveExpectedConstraints() {
        List<String> constraints = jdbc.queryForList(
            "select constraint_name from information_schema.table_constraints "
                + "where table_schema = 'public' and table_name in ('one_time_tokens', 'user_devices') "
                + "and constraint_type in ('UNIQUE', 'FOREIGN KEY', 'CHECK') order by constraint_name",
            String.class);

        assertThat(constraints).contains("uk_one_time_tokens_hash", "fk_one_time_tokens_user",
            "ck_one_time_tokens_purpose", "uk_user_devices_fingerprint", "fk_user_devices_user");
    }

    @Test
    void ac02_authorizationTokenColumnsUseTextAndTimestamptz() {
        String valueType = jdbc.queryForObject(
            "select data_type from information_schema.columns where table_name = 'oauth2_authorization' "
                + "and column_name = 'refresh_token_value'", String.class);
        String timeType = jdbc.queryForObject(
            "select data_type from information_schema.columns where table_name = 'oauth2_authorization' "
                + "and column_name = 'refresh_token_issued_at'", String.class);

        assertThat(valueType).isEqualTo("text");
        assertThat(timeType).isEqualTo("timestamp with time zone");
    }

    @Test
    void ac05_noSeedUsersOutsideLocalProfile() {
        Integer users = jdbc.queryForObject("select count(*) from users", Integer.class);

        assertThat(users).isZero();
    }
}

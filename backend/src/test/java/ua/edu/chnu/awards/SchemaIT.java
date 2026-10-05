package ua.edu.chnu.awards;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;

class SchemaIT extends AbstractIntegrationTest {

    private static final int OLD_UNIVERSITY_SCORE = 60;
    private static final int OLD_LOCAL_SCORE = 45;
    private static final int NEW_UNIVERSITY_SCORE = 50;
    private static final int NEW_LOCAL_SCORE = 60;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PostgreSQLContainer<?> postgres;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void ac01_latestMigrationIsApplied() throws IOException {
        String latest = Arrays.stream(new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*__*.sql"))
            .map(Resource::getFilename)
            .map(name -> name.substring(1, name.indexOf("__")))
            .max(Comparator.naturalOrder())
            .orElseThrow();

        String version = jdbc.queryForObject(
            "select version from flyway_schema_history where success and version is not null "
                + "order by installed_rank desc limit 1", String.class);

        assertThat(version).isEqualTo(latest);
    }

    @Test
    void ac1_7_awardsExistingBeforeV023GetOneBaselineVersionWithTheirState() {
        String database = "v023_baseline_" + System.nanoTime();
        jdbc.execute("create database " + database);
        try {
            String url = "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database;
            DriverManagerDataSource target = new DriverManagerDataSource(url, postgres.getUsername(),
                postgres.getPassword());
            Flyway.configure().dataSource(target).locations("classpath:db/migration").target("022").load()
                .migrate();
            JdbcTemplate old = new JdbcTemplate(target);
            Long owner = old.queryForObject("insert into users (email_address, first_name, last_name, password_hash, "
                + "organization_id) values ('baseline@chnu.edu.ua', 'Base', 'Line', 'x', 64) returning user_id",
                Long.class);
            old.update("insert into awards (user_id, organization_id, title, award_date, version) "
                + "values (?, 64, 'Letter', date '2025-05-01', 4)", owner);

            Flyway.configure().dataSource(target).locations("classpath:db/migration").load().migrate();

            Map<String, Object> baseline = old.queryForMap("select version_number, action, actor_id, "
                + "snapshot->>'title' as title, snapshot->>'awardDate' as award_date, "
                + "snapshot->>'status' as status from award_versions");
            assertThat(baseline).containsEntry("version_number", 4L).containsEntry("action", "BASELINE")
                .containsEntry("actor_id", null).containsEntry("title", "Letter")
                .containsEntry("award_date", "2025-05-01").containsEntry("status", "DRAFT");
        } finally {
            jdbc.execute("drop database " + database + " with (force)");
        }
    }

    @Test
    void ac1_2_requestsSubmittedBeforeV024AreDueThreeDaysAfterTheirSubmission() {
        String database = "v024_deadline_" + System.nanoTime();
        jdbc.execute("create database " + database);
        try {
            String url = "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database;
            DriverManagerDataSource target = new DriverManagerDataSource(url, postgres.getUsername(),
                postgres.getPassword());
            Flyway.configure().dataSource(target).locations("classpath:db/migration").target("023").load()
                .migrate();
            JdbcTemplate old = new JdbcTemplate(target);
            Long owner = old.queryForObject("insert into users (email_address, first_name, last_name, password_hash, "
                + "organization_id) values ('deadline@chnu.edu.ua', 'Dead', 'Line', 'x', 64) returning user_id",
                Long.class);
            Long award = old.queryForObject("insert into awards (user_id, organization_id, title, status, "
                + "category_id, awarding_organization, award_date) values (?, 64, 'Letter', 'PENDING', 13, 'МОН', "
                + "date '2025-05-01') returning award_id", Long.class, owner);
            old.update("insert into award_requests (award_id, submitter_id, current_level, submitted_at) "
                + "values (?, ?, 'FACULTY_SECRETARY', timestamptz '2026-09-28 23:30:00+03')", award, owner);

            Flyway.configure().dataSource(target).locations("classpath:db/migration").load().migrate();

            assertThat(old.queryForObject("select deadline = timestamptz '2026-10-01 23:30:00+03' "
                + "from award_requests", Boolean.class)).isTrue();
        } finally {
            jdbc.execute("drop database " + database + " with (force)");
        }
    }

    @Test
    void ac1_6_v026RescoresSubmittedAwardsAndRemovesOldPasswordHashesFromTheAuditTrail() {
        String database = "v026_review_" + System.nanoTime();
        jdbc.execute("create database " + database);
        try {
            String url = "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database;
            DriverManagerDataSource target = new DriverManagerDataSource(url, postgres.getUsername(),
                postgres.getPassword());
            Flyway.configure().dataSource(target).locations("classpath:db/migration").target("025").load()
                .migrate();
            JdbcTemplate old = new JdbcTemplate(target);
            Long owner = old.queryForObject("insert into users (email_address, first_name, last_name, password_hash, "
                + "organization_id) values ('rescore@chnu.edu.ua', 'Re', 'Score', 'x', 64) returning user_id",
                Long.class);
            String insertAward = "insert into awards (user_id, organization_id, title, status, category_id, "
                + "awarding_organization, award_date, impact_score) values (?, 64, ?, ?, (select min(category_id) "
                + "from award_categories where level = ?), 'ЧНУ', date '2025-05-01', ?)";
            old.update(insertAward, owner, "University", "PENDING", "UNIVERSITY", OLD_UNIVERSITY_SCORE);
            old.update(insertAward, owner, "Local", "APPROVED", "LOCAL", OLD_LOCAL_SCORE);
            old.update(insertAward, owner, "Draft", "DRAFT", "UNIVERSITY", null);
            old.update("insert into audit_logs (user_id, action_type, entity_type, entity_id, old_values, new_values, "
                + "changed_fields) values (?, 'UPDATE', 'users', ?, '{\"first_name\":\"A\",\"password_hash\":\"h1\"}', "
                + "'{\"first_name\":\"B\",\"password_hash\":\"h2\"}', array['first_name','password_hash'])",
                owner, owner);

            Flyway.configure().dataSource(target).locations("classpath:db/migration").load().migrate();

            assertThat(old.queryForList("select title || '=' || coalesce(impact_score::text, '-') from awards "
                + "order by title", String.class))
                .containsExactly("Draft=-", "Local=" + NEW_LOCAL_SCORE, "University=" + NEW_UNIVERSITY_SCORE);
            Map<String, Object> audit = old.queryForMap("select old_values::text as old_values, "
                + "new_values::text as new_values, array_to_string(changed_fields, ',') as changed "
                + "from audit_logs where entity_type = 'users' and action_type = 'UPDATE' and entity_id = ?", owner);
            assertThat(audit).containsEntry("old_values", "{\"first_name\": \"A\"}")
                .containsEntry("new_values", "{\"first_name\": \"B\"}").containsEntry("changed", "first_name");
            old.update("update audit_logs set entity_type = 'changed'");
            assertThat(old.queryForObject("select count(*) from audit_logs where entity_type = 'changed'",
                Long.class)).isZero();
        } finally {
            jdbc.execute("drop database " + database + " with (force)");
        }
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
    void ac46_auditLogsHasMonthlyPartitionsThroughDecember2027() {
        List<String> partitions = jdbc.queryForList(
            "select c.relname from pg_inherits i join pg_class c on c.oid = i.inhrelid "
                + "join pg_class p on p.oid = i.inhparent where p.relname = 'audit_logs' order by c.relname",
            String.class);

        assertThat(partitions).contains("audit_logs_2026_01", "audit_logs_2026_07", "audit_logs_2026_09",
            "audit_logs_2027_01", "audit_logs_2027_12", "audit_logs_default").hasSize(25);
    }

    @Test
    void ac05_noSeedUsersOutsideLocalProfile() {
        Integer users = jdbc.queryForObject(
            "select count(*) from users where email_address in ('admin@chnu.edu.ua', 'rector@chnu.edu.ua')",
            Integer.class);

        assertThat(users).isZero();
    }
}

package ua.edu.chnu.awards.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;

class DashboardLayoutMigrationIT extends AbstractIntegrationTest {

    private static final String INSERT_USER = "insert into users (email_address, first_name, last_name, "
        + "password_hash, account_status, organization_id) values (?, 'Test', 'User', 'x', 'ACTIVE', "
        + "(select min(org_id) from organizations)) returning user_id";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    void ac1_6_hiddenWidgetsMoveToTheMyAwardsLayoutWithTheNewNamesAndTheColumnIsDropped() {
        String database = "v052_layouts_" + System.nanoTime();
        jdbc.execute("create database " + database);
        try {
            String url = "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database;
            DriverManagerDataSource target = new DriverManagerDataSource(url, postgres.getUsername(),
                postgres.getPassword());
            Flyway.configure().dataSource(target).locations("classpath:db/migration").target("051").load().migrate();
            JdbcTemplate old = new JdbcTemplate(target);
            long mapped = seed(old, "it.v052.mapped@chnu.edu.ua", "{RANK,TIMELINE,SUMMARY,BY_LEVEL}");
            long timelineOnly = seed(old, "it.v052.timeline@chnu.edu.ua", "{TIMELINE}");
            long empty = seed(old, "it.v052.empty@chnu.edu.ua", "{}");

            Flyway.configure().dataSource(target).locations("classpath:db/migration").load().migrate();

            assertThat(row(old, mapped)).containsEntry("color_scheme", "SYSTEM").containsEntry("layouts",
                "{\"MY_AWARDS\": {\"order\": [], \"hidden\": [\"KPI\", \"LEVEL_STAIRS\", \"RANK\"]}}");
            assertThat(row(old, timelineOnly)).containsEntry("layouts", "{}");
            assertThat(row(old, empty)).containsEntry("color_scheme", "SYSTEM").containsEntry("layouts", "{}");
            assertThat(old.queryForList("select column_name from information_schema.columns "
                + "where table_name = 'dashboard_preferences'", String.class)).doesNotContain("hidden_widgets");
        } finally {
            jdbc.execute("drop database " + database + " with (force)");
        }
    }

    private static long seed(JdbcTemplate template, String email, String hidden) {
        Long userId = template.queryForObject(INSERT_USER, Long.class, email);
        template.update("insert into dashboard_preferences (user_id, hidden_widgets) values (?, ?::text[])", userId,
            hidden);
        return Objects.requireNonNull(userId);
    }

    private static Map<String, Object> row(JdbcTemplate template, long userId) {
        return template.queryForMap("select color_scheme, layouts::text as layouts from dashboard_preferences "
            + "where user_id = ?", userId);
    }
}

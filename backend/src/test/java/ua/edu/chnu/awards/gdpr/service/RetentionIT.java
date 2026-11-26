package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import ua.edu.chnu.awards.audit.service.AuditPartitionRetention;
import ua.edu.chnu.awards.auth.service.AuthorizationRetention;
import ua.edu.chnu.awards.auth.service.DeviceRetention;
import ua.edu.chnu.awards.auth.service.OneTimeTokenRetention;
import ua.edu.chnu.awards.common.retention.RetentionContext;
import ua.edu.chnu.awards.common.retention.RetentionPolicy;
import ua.edu.chnu.awards.common.retention.RetentionResult;
import ua.edu.chnu.awards.notification.service.NotificationRetention;

class RetentionIT extends AbstractRetentionIT {

    private static final int SMALL_BATCH = 2;
    private static final List<String> FUTURE_PARTITIONS =
        List.of("audit_logs_2031_01", "audit_logs_2031_02", "audit_logs_2031_03", "audit_logs_2031_04");

    @Autowired
    private RetentionJob job;

    @Autowired
    private NotificationRetention notifications;

    @Autowired
    private OneTimeTokenRetention tokens;

    @Autowired
    private AuthorizationRetention authorizations;

    @Autowired
    private DeviceRetention devices;

    @Autowired
    private AuditPartitionRetention partitions;

    @Autowired
    private DataSource dataSource;

    private long userId;

    @BeforeEach
    void createPerson() {
        userId = newPerson("it.retention.");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from oauth2_authorization where principal_name like 'it.retention.%'");
        jdbc.update("delete from notifications where user_id = ?", userId);
        jdbc.update("delete from retention_runs");
        FUTURE_PARTITIONS.forEach(name -> jdbc.execute("drop table if exists " + name));
    }

    @Test
    void ac1_1_retentionRunsChecksItsStatusAndFinishAndIsIndexedByStart() {
        assertThatThrownBy(() -> jdbc.update("insert into retention_runs (status, finished_at) values ('DONE', now())"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into retention_runs (status, finished_at) values "
            + "('RUNNING', now())")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into retention_runs (status) values ('COMPLETED')"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select indexdef from pg_indexes where indexname = 'idx_retention_runs_started'",
            String.class)).contains("started_at DESC");
        assertThat(jdbc.queryForObject("insert into retention_runs default values returning results::text",
            String.class)).isEqualTo("[]");
    }

    @Test
    void ac1_2_ac1_3_aRunClosesTheInterruptedRunAppliesEveryPolicyAndIsAudited() {
        long stale = jdbc.queryForObject("insert into retention_runs (started_at) values (now() - interval '1 day') "
            + "returning run_id", Long.class);

        Optional<Long> run = job.run();

        assertThat(run).isPresent();
        assertThat(jdbc.queryForObject("select status from retention_runs where run_id = ? and finished_at is not null",
            String.class, stale)).isEqualTo("INTERRUPTED");
        assertThat(jdbc.queryForObject("select status from retention_runs where run_id = ?", String.class,
            run.get())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForList("select r ->> 'policy' from retention_runs, jsonb_array_elements(results) r "
            + "where run_id = ?", String.class, run.get()))
            .containsExactly("notifications", "one-time-tokens", "authorizations", "devices", "pending-accounts",
                "former-staff", "consent-records", "audit-partitions", "audit-logs");
        assertThat(jdbc.queryForObject("select new_values ->> 'status' from audit_logs where action_type = "
            + "'RETENTION_RUN_COMPLETED' and entity_type = 'RETENTION' and entity_id = ? and user_id is null",
            String.class, run.get())).isEqualTo("COMPLETED");
    }

    @Test
    void ac1_2_aRunStartedWhileAnotherHoldsTheLockIsSkippedWithoutARow() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("select pg_advisory_lock(hashtext('retention-run'))");
            try {
                assertThat(job.run()).isEmpty();
            } finally {
                statement.execute("select pg_advisory_unlock(hashtext('retention-run'))");
            }
        }

        assertThat(jdbc.queryForObject("select count(*) from retention_runs", Integer.class)).isZero();
    }

    @Test
    void ac1_2_aPolicyDeletesInBatchesOfTheBatchSize() {
        for (int device = 0; device < 5; device++) {
            device("old-" + device, "800 days");
        }

        RetentionResult result = devices.apply(context(Set.of(), SMALL_BATCH));

        assertThat(result).isEqualTo(RetentionResult.completed("devices", 5, 0));
        assertThat(devicesOf()).isEmpty();
    }

    @Test
    void ac1_5_expiredNotificationsGoAndTheOthersStay() {
        notification("gone", "now() - interval '1 minute'");
        notification("kept", "now() + interval '1 minute'");

        assertThat(apply(notifications).affected()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForList("select dedupe_key from notifications where user_id = ?", String.class, userId))
            .containsExactly(key("kept"));
        assertThat(notifications.period()).isEqualTo("P365D");
    }

    @Test
    void ac1_6_tokensUsedOrExpiredLongerAgoThanThePeriodGo() {
        token("used-old", "now() + interval '1 day'", "now() - interval '31 days'");
        token("expired-old", "now() - interval '31 days'", null);
        token("used-recently", "now() - interval '28 days'", "now() - interval '29 days'");
        token("expired-recently", "now() - interval '29 days'", null);

        apply(tokens);

        assertThat(jdbc.queryForList("select token_hash from one_time_tokens where user_id = ?", String.class, userId))
            .containsExactlyInAnyOrder("used-recently-" + userId, "expired-recently-" + userId);
        assertThat(tokens.period()).isEqualTo("P30D");
    }

    @Test
    void ac1_7_authorizationsGoOnlyWhenEveryExpiryIsOlderThanThePeriod() {
        authorization("expired", "now() - interval '2 days'", "now() - interval '2 days'");
        authorization("live-refresh", "now() - interval '2 days'", "now() + interval '20 days'");
        authorization("recent", "now() - interval '2 hours'", null);
        authorization("empty", null, null);

        apply(authorizations);

        assertThat(jdbc.queryForList("select id from oauth2_authorization where principal_name like 'it.retention.%'",
            String.class)).containsExactlyInAnyOrder(authorizationId("live-refresh"), authorizationId("recent"));
    }

    @Test
    void ac1_8_devicesUnusedForThePeriodGo() {
        device("old", "731 days");
        device("recent", "729 days");

        apply(devices);

        assertThat(devicesOf()).containsExactly("recent");
        assertThat(devices.period()).isEqualTo("P730D");
    }

    @Test
    void ac1_9_theCurrentMonthAndThreeAheadGetPartitionsAndExistingOnesAreSkipped() {
        Instant future = Instant.parse("2031-01-15T10:00:00Z");

        RetentionResult first = partitions.apply(new RetentionContext(future, Set.of(), SMALL_BATCH, transactions));
        RetentionResult second = partitions.apply(new RetentionContext(future, Set.of(), SMALL_BATCH, transactions));

        assertThat(first.affected()).isEqualTo(4);
        assertThat(second.affected()).isZero();
        assertThat(jdbc.queryForList("select relname::text from pg_class where relname like 'audit_logs_2031_%' "
            + "and relkind = 'r' order by relname", String.class)).containsExactlyElementsOf(FUTURE_PARTITIONS);
        assertThat(apply(partitions).affected()).isZero();
    }

    private RetentionResult apply(RetentionPolicy policy) {
        return policy.apply(context(Set.of(), 1000));
    }

    private void notification(String name, String expiresAt) {
        jdbc.update("insert into notifications (user_id, type, channel, dedupe_key, sent_at, expires_at) values "
            + "(?, 'AWARD_APPROVED', 'IN_APP', ?, now(), " + expiresAt + ")", userId, key(name));
    }

    private String key(String name) {
        return name + "-" + userId;
    }

    private void token(String name, String expiresAt, String usedAt) {
        jdbc.update("insert into one_time_tokens (token_hash, user_id, purpose, expires_at, used_at) values "
            + "(?, ?, 'EMAIL_VERIFICATION', " + expiresAt + ", " + usedAt + ")", key(name), userId);
    }

    private void authorization(String name, String accessExpiresAt, String refreshExpiresAt) {
        jdbc.update("insert into oauth2_authorization (id, registered_client_id, principal_name, "
            + "authorization_grant_type, access_token_expires_at, refresh_token_expires_at) "
            + "select ?, id, ?, 'authorization_code', " + accessExpiresAt + ", " + refreshExpiresAt
            + " from oauth2_registered_client limit 1", authorizationId(name), "it.retention." + userId);
    }

    private String authorizationId(String name) {
        return "it-retention-" + key(name);
    }

    private void device(String fingerprint, String age) {
        jdbc.update("insert into user_devices (user_id, fingerprint, first_seen_at, last_used_at) values "
            + "(?, ?, now() - interval '" + age + "', now() - interval '" + age + "')", userId, fingerprint);
    }

    private List<String> devicesOf() {
        return jdbc.queryForList("select fingerprint from user_devices where user_id = ?", String.class, userId);
    }
}

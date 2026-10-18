package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.audit.service.AuditLogRetention;
import ua.edu.chnu.awards.common.retention.RetentionContext;
import ua.edu.chnu.awards.common.retention.RetentionResult;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.repository.UserRepository;

class RecordRetentionIT extends AbstractIntegrationTest {

    private static final List<String> OLD_PARTITIONS =
        List.of("audit_logs_2018_01", "audit_logs_2018_02", "audit_logs_2018_03");

    @Autowired
    private ConsentRecordRetention consents;

    @Autowired
    private AuditLogRetention auditLogs;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    private final List<Long> people = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        people.forEach(id -> {
            jdbc.update("delete from erasure_requests where user_id = ?", id);
            ConsentRows.purge(jdbc, id);
        });
        userRepository.deleteAllById(people);
        OLD_PARTITIONS.forEach(name -> jdbc.execute("drop table if exists " + name));
    }

    @Test
    void ac3_5_withdrawnAnalyticsChainsAndErasedAccountsGoAndAcknowledgementsOfLivingAccountsStay() {
        final long withdrawn = person();
        consent(withdrawn, "DATA_PROCESSING", true, "9 years");
        consent(withdrawn, "ANALYTICS", true, "9 years");
        consent(withdrawn, "ANALYTICS", false, "7 years 1 day");
        final long regranted = person();
        consent(regranted, "ANALYTICS", false, "8 years");
        consent(regranted, "ANALYTICS", true, "1 year");
        final long recent = person();
        consent(recent, "ANALYTICS", false, "6 years");
        final long erased = person();
        consent(erased, "DATA_PROCESSING", true, "9 years");
        jdbc.update("insert into erasure_requests (user_id, requested_at, scheduled_for, status, completed_at) "
            + "values (?, now() - interval '8 years', now() - interval '8 years', 'COMPLETED', "
            + "now() - interval '7 years 1 day')", erased);
        final long held = person();
        consent(held, "ANALYTICS", false, "8 years");

        RetentionResult result = consents.apply(context(Set.of(held)));

        assertThat(result.affected()).isEqualTo(3);
        assertThat(result.held()).isOne();
        assertThat(typesOf(withdrawn)).containsExactly("DATA_PROCESSING");
        assertThat(typesOf(regranted)).hasSize(2);
        assertThat(typesOf(recent)).hasSize(1);
        assertThat(typesOf(erased)).isEmpty();
        assertThat(typesOf(held)).hasSize(1);
        assertThat(consents.period()).isEqualTo("P7Y");
    }

    @Test
    void ac3_5_aDirectDeleteStillRaisesAfterAPurge() {
        final long withdrawn = person();
        consent(withdrawn, "DATA_PROCESSING", true, "1 day");
        consents.apply(context(Set.of()));

        assertThatThrownBy(() -> jdbc.update("delete from consent_records where user_id = ?", withdrawn))
            .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbc.queryForObject("select fn_purge_consent_records(now(), '{}')", Integer.class);
            jdbc.update("delete from consent_records where user_id = ?", withdrawn);
        })).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    void ac3_6_expiredPartitionsAreDroppedUnlessTheyHoldARowOfAHeldPerson() {
        final long held = person();
        OLD_PARTITIONS.forEach(name -> jdbc.queryForObject("select fn_create_audit_partition(cast(? as date))",
            Boolean.class, name.substring("audit_logs_".length()).replace('_', '-') + "-01"));
        audit("2018-01-15", null, "awards", "{\"title\": \"Old\"}");
        audit("2018-02-15", held, "awards", "{\"title\": \"Signed in\"}");
        audit("2018-03-15", null, "consent_records", "{\"user_id\": " + held + "}");

        RetentionResult result = auditLogs.apply(context(Set.of(held)));

        assertThat(result.affected()).isOne();
        assertThat(result.held()).isEqualTo(2);
        assertThat(partitions()).containsExactly("audit_logs_2018_02", "audit_logs_2018_03");
        assertThat(partitions("audit_logs_default")).isOne();
        assertThat(jdbc.queryForObject("select new_values -> 'dropped' ->> 0 from audit_logs where action_type = "
            + "'AUDIT_PARTITIONS_DROPPED' order by created_at desc limit 1", String.class))
            .isEqualTo("audit_logs_2018_01");
        assertThatThrownBy(() -> jdbc.update("delete from audit_logs_2018_02"))
            .isInstanceOf(DataAccessException.class).hasMessageContaining("cannot be deleted");
        assertThat(auditLogs.period()).isEqualTo("P7Y");
    }

    private RetentionContext context(Set<Long> held) {
        return new RetentionContext(Instant.now(), held, 1000, transactions);
    }

    private long person() {
        long id = userRepository.save(TestUsers.user("it.record.retention." + UUID.randomUUID() + "@chnu.edu.ua",
            organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow())).getId();
        people.add(id);
        return id;
    }

    private void consent(long userId, String type, boolean granted, String age) {
        jdbc.update("insert into consent_records (user_id, consent_type, consent_version, is_granted, granted_at, "
            + "withdrawn_at, created_at) values (?, ?, '1.0', ?, case when ? then now() - cast(? as interval) end, "
            + "case when ? then null else now() - cast(? as interval) end, now() - cast(? as interval))",
            userId, type, granted, granted, age, granted, age, age);
    }

    private List<String> typesOf(long userId) {
        return jdbc.queryForList("select consent_type from consent_records where user_id = ? order by consent_id",
            String.class, userId);
    }

    private void audit(String day, Long actor, String entityType, String values) {
        jdbc.update("insert into audit_logs (user_id, action_type, entity_type, entity_id, new_values, created_at) "
            + "values (?, 'CREATE', ?, 1, cast(? as jsonb), cast(? as timestamptz))", actor, entityType, values,
            day + "T10:00:00Z");
    }

    private List<String> partitions() {
        return jdbc.queryForList("select relname::text from pg_class where relname like 'audit_logs_2018_%' "
            + "and relkind = 'r' order by relname", String.class);
    }

    private int partitions(String name) {
        return jdbc.queryForObject("select count(*) from pg_class where relname = ?", Integer.class, name);
    }
}

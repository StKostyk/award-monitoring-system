package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.common.retention.RetentionContext;
import ua.edu.chnu.awards.common.retention.RetentionResult;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.repository.UserRepository;

class AccountRetentionIT extends AbstractIntegrationTest {

    private static final String STATUS = "select status from erasure_requests where user_id = ? order by request_id "
        + "desc limit 1";

    @Autowired
    private PendingAccountRetention pendingAccounts;

    @Autowired
    private FormerStaffRetention formerStaff;

    @Autowired
    private ErasureRunner runner;

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
    void deletePeople() {
        people.forEach(id -> {
            jdbc.update("delete from erasure_requests where user_id = ?", id);
            jdbc.update("delete from legal_holds where user_id = ?", id);
            jdbc.update("delete from one_time_tokens where user_id = ?", id);
            ConsentRows.purge(jdbc, id);
        });
        userRepository.deleteAllById(people);
    }

    @Test
    void ac3_1_theStatusChangeTimeIsSetOnAChangeOnlyAndTheOriginIsChecked() {
        long id = person("ACTIVE");
        jdbc.update("update users set status_changed_at = now() - interval '3 days' where user_id = ?", id);

        jdbc.update("update users set account_status = 'ACTIVE', first_name = 'Same' where user_id = ?", id);
        assertThat(ageInDays(id)).isEqualTo(3);
        jdbc.update("update users set account_status = 'RETIRED' where user_id = ?", id);
        assertThat(ageInDays(id)).isZero();

        assertThatThrownBy(() -> jdbc.update("insert into erasure_requests (user_id, requested_at, scheduled_for, "
            + "origin) values (?, now(), now(), 'JOB')", id)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("insert into erasure_requests (user_id, requested_at, scheduled_for) values (?, now(), now())",
            id);
        assertThat(jdbc.queryForObject("select origin from erasure_requests where user_id = ?", String.class, id))
            .isEqualTo("REQUEST");
    }

    @Test
    void ac3_2_oldUnverifiedRegistrationsGetADueRetentionRequestAndHeldOnesAreCounted() {
        final long abandoned = pending("31 days");
        final long recent = pending("29 days");
        final long renewed = pending("40 days");
        jdbc.update("insert into one_time_tokens (token_hash, user_id, purpose, expires_at) values (?, ?, "
            + "'EMAIL_VERIFICATION', now() - interval '1 day')", UUID.randomUUID().toString(), renewed);
        final long held = pending("60 days");
        final long alreadyRequested = pending("60 days");
        jdbc.update("insert into erasure_requests (user_id, requested_at, scheduled_for) values (?, now(), "
            + "now() + interval '30 days')", alreadyRequested);

        RetentionResult result = pendingAccounts.apply(context(Set.of(held), 1));
        final Timestamp ranBy = Timestamp.from(Instant.now());

        assertThat(result.affected()).isOne();
        assertThat(result.held()).isOne();
        assertThat(jdbc.queryForMap("select origin, requested_at = scheduled_for as due_at_once, "
            + "scheduled_for <= ? as due from erasure_requests where user_id = ?", ranBy, abandoned))
            .containsEntry("origin", "RETENTION").containsEntry("due_at_once", true).containsEntry("due", true);
        assertThat(requestsOf(recent)).isZero();
        assertThat(requestsOf(renewed)).isZero();
        assertThat(requestsOf(held)).isZero();
        assertThat(requestsOf(alreadyRequested)).isOne();
        assertThat(pendingAccounts.apply(context(Set.of(held), 1)).affected()).isZero();
        assertThat(pendingAccounts.period()).isEqualTo("P30D");
    }

    @Test
    void ac3_3_retiredAndMemorialAccountsGetARequestSevenYearsAfterTheirStatusChanged() {
        final long retired = former("RETIRED", "7 years 1 day");
        final long memorial = former("MEMORIAL", "8 years");
        final long recentlyRetired = former("RETIRED", "6 years 11 months");
        final long active = former("ACTIVE", "10 years");

        RetentionResult result = formerStaff.apply(context(Set.of(), 1000));

        assertThat(result.affected()).isEqualTo(2);
        assertThat(requestsOf(retired)).isOne();
        assertThat(requestsOf(memorial)).isOne();
        assertThat(requestsOf(recentlyRetired)).isZero();
        assertThat(requestsOf(active)).isZero();
        assertThat(formerStaff.period()).isEqualTo("P7Y");
    }

    @Test
    void ac3_4_aRetentionRequestIsErasedByTheRunWithItsOrigin() {
        final long abandoned = pending("31 days");
        pendingAccounts.apply(context(Set.of(), 1000));

        runner.runDue(Instant.now());

        assertThat(jdbc.queryForObject(STATUS, String.class, abandoned)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select account_status from users where user_id = ?", String.class,
            abandoned)).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("select new_values ->> 'origin' from audit_logs where action_type = "
            + "'ERASURE_COMPLETED' and entity_id = ?", String.class, abandoned)).isEqualTo("retention");
    }

    @Test
    void ac3_4_aRetentionRequestOfAnAccountVerifiedMeanwhileIsCancelled() {
        final long verified = pending("31 days");
        pendingAccounts.apply(context(Set.of(), 1000));
        jdbc.update("update users set account_status = 'ACTIVE' where user_id = ?", verified);

        runner.runDue(Instant.now());

        assertThat(jdbc.queryForObject(STATUS, String.class, verified)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select account_status from users where user_id = ?", String.class,
            verified)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select new_values ->> 'reason' from audit_logs where action_type = "
            + "'ERASURE_CANCELLED' and entity_id = ?", String.class, verified)).isEqualTo("no-longer-applies");
    }

    @Test
    void ac3_4_aHoldPlacedAfterTheFilingPostponesTheRetentionErasure() {
        final long abandoned = pending("31 days");
        pendingAccounts.apply(context(Set.of(), 1000));
        jdbc.update("insert into legal_holds (user_id, reason, placed_by) values (?, 'Court case 3/2026', ?)",
            abandoned, person("ACTIVE"));

        runner.runDue(Instant.now());

        assertThat(jdbc.queryForMap("select status, postponed_at is not null as postponed from erasure_requests "
            + "where user_id = ?", abandoned)).containsEntry("status", "PENDING").containsEntry("postponed", true);
        assertThat(jdbc.queryForObject("select new_values ->> 'origin' from audit_logs where action_type = "
            + "'ERASURE_POSTPONED' and entity_id = ?", String.class, abandoned)).isEqualTo("retention");
    }

    @Test
    void ac3_4_aRetentionRequestOfARegistrationTakenOverMeanwhileIsCancelled() {
        final long taken = pending("31 days");
        pendingAccounts.apply(context(Set.of(), 1000));
        jdbc.update("insert into one_time_tokens (token_hash, user_id, purpose, expires_at) values (?, ?, "
            + "'EMAIL_VERIFICATION', now() + interval '1 day')", UUID.randomUUID().toString(), taken);

        runner.runDue(Instant.now());

        assertThat(jdbc.queryForObject(STATUS, String.class, taken)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select account_status from users where user_id = ?", String.class,
            taken)).isEqualTo("PENDING");
    }

    @Test
    void ac3_4_aPostponedRetentionErasureRunsOnceTheHoldIsLifted() {
        final long abandoned = pending("31 days");
        pendingAccounts.apply(context(Set.of(), 1000));
        jdbc.update("insert into legal_holds (user_id, reason, placed_by) values (?, 'Court case 4/2026', ?)",
            abandoned, person("ACTIVE"));
        runner.runDue(Instant.now());
        jdbc.update("update legal_holds set lifted_at = now(), lifted_by = placed_by, lift_reason = 'Closed' "
            + "where user_id = ?", abandoned);

        runner.runDue(Instant.now());

        assertThat(jdbc.queryForObject(STATUS, String.class, abandoned)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select account_status from users where user_id = ?", String.class,
            abandoned)).isEqualTo("DELETED");
        assertThat(requestsOf(abandoned)).isOne();
    }

    private RetentionContext context(Set<Long> held, int batchSize) {
        return new RetentionContext(Instant.now(), held, batchSize, transactions);
    }

    private long person(String status) {
        long id = userRepository.save(TestUsers.user("it.account.retention." + UUID.randomUUID() + "@chnu.edu.ua",
            organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow())).getId();
        jdbc.update("update users set account_status = ? where user_id = ?", status, id);
        people.add(id);
        return id;
    }

    private long pending(String age) {
        long id = person("PENDING");
        jdbc.update("update users set created_at = now() - cast(? as interval) where user_id = ?", age, id);
        return id;
    }

    private long former(String status, String age) {
        long id = person(status);
        jdbc.update("update users set status_changed_at = now() - cast(? as interval) where user_id = ?", age, id);
        return id;
    }

    private int requestsOf(long userId) {
        return jdbc.queryForObject("select count(*) from erasure_requests where user_id = ?", Integer.class, userId);
    }

    private int ageInDays(long userId) {
        return jdbc.queryForObject("select extract(day from now() - status_changed_at)::int from users "
            + "where user_id = ?", Integer.class, userId);
    }
}

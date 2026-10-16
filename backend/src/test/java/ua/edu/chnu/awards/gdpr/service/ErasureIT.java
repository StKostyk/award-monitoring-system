package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.support.DecisionRows;
import ua.edu.chnu.awards.support.RequestRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class ErasureIT extends AbstractIntegrationTest {

    private static final String CURRENT_NAME = "Erik Person";
    private static final String FORMER_NAME = "Erik Former";
    private static final String IP = "10.20.30.40";
    private static final String AGENT = "Firefox 140";

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

    private long personId;
    private long ownerId;
    private long reviewerId;
    private long approvedId;
    private long draftId;
    private long pendingId;
    private long pendingRequestId;
    private long claimedRequestId;
    private long decidedRequestId;
    private long reviewedNoticeId;
    private long delegationNoticeId;
    private long namesakeNoticeId;
    private LocalDate today;

    @BeforeEach
    void createPeople() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        User person = TestUsers.user("it.erased." + UUID.randomUUID() + "@chnu.edu.ua", department);
        person.setFirstName("Erik");
        person.setLastName("Former");
        personId = userRepository.save(person).getId();
        jdbc.update("update users set last_name = 'Person', last_login_at = now() where user_id = ?", personId);
        ownerId = userRepository.save(TestUsers.user("it.erased.owner." + UUID.randomUUID() + "@chnu.edu.ua",
            department)).getId();
        reviewerId = userRepository.save(TestUsers.user("it.erased.reviewer." + UUID.randomUUID() + "@chnu.edu.ua",
            department)).getId();
        today = LocalDate.now(ZoneId.of("Europe/Kyiv"));
        authorityOfPerson();
        awardsOfPerson();
        workOnOthersAwards();
        tracesOfPerson();
    }

    @AfterEach
    void deletePeople() {
        List<Long> people = List.of(personId, ownerId, reviewerId);
        people.forEach(id -> jdbc.update("delete from awards where user_id = ?", id));
        jdbc.update("delete from erasure_requests where user_id = ?", personId);
        jdbc.update("delete from legal_holds where user_id = ?", personId);
        ConsentRows.purge(jdbc, personId);
        userRepository.deleteAllById(people);
    }

    @Test
    void ac5_1_aDueRequestIsCompletedAndAuditedWithTheCounts() {
        assertThat(runner.runDue(Instant.now())).isOne();

        assertThat(jdbc.queryForObject("select status from erasure_requests where user_id = ?", String.class,
            personId)).isEqualTo("COMPLETED");
        Map<String, Object> counts = jdbc.queryForMap("select new_values ->> 'awardsDeleted' as deleted, "
            + "new_values ->> 'rolesEnded' as roles, new_values ->> 'documentsDeleted' as documents "
            + "from audit_logs where action_type = 'ERASURE_COMPLETED' and entity_id = ?", personId);
        assertThat(counts).containsEntry("deleted", "2").containsEntry("roles", "2").containsEntry("documents", "2");
        assertThat(runner.runDue(Instant.now())).isZero();
    }

    @Test
    void ac5_2_anActiveHoldPostponesTheErasureUntilItIsLifted() {
        final long holdId = jdbc.queryForObject("insert into legal_holds (user_id, reason, placed_by) values (?, "
            + "'Court case 12/2026', ?) returning hold_id", Long.class, personId, ownerId);

        assertThat(runner.runDue(Instant.now())).isZero();
        assertThat(runner.runDue(Instant.now())).isZero();

        assertThat(jdbc.queryForMap("select status, postponed_at is not null as postponed from erasure_requests "
            + "where user_id = ?", personId)).containsEntry("status", "PENDING").containsEntry("postponed", true);
        assertThat(count("select count(*) from audit_logs where action_type = 'ERASURE_POSTPONED' and entity_id = ?",
            personId)).isOne();
        assertThat(count("select count(*) from awards where user_id = ?", personId)).isEqualTo(3);

        jdbc.update("update legal_holds set lifted_at = now(), lifted_by = ?, lift_reason = 'Closed' "
            + "where hold_id = ?", ownerId, holdId);
        assertThat(runner.runDue(Instant.now())).isOne();
    }

    @Test
    void ac5_3_theAccountIsScrubbedSignedOutAndUnauthorisedWithoutNotifications() {
        final int ownerNotices = count("select count(*) from notifications where user_id = ?", ownerId);

        runner.runDue(Instant.now());

        assertThat(jdbc.queryForMap("select account_status, email_address, first_name, last_name, last_login_at "
            + "from users where user_id = ?", personId))
            .containsEntry("account_status", "DELETED")
            .containsEntry("email_address", "erased-" + personId + "@erased.invalid")
            .containsEntry("first_name", "Erased")
            .containsEntry("last_name", "#" + personId)
            .containsEntry("last_login_at", null);
        assertThat(count("select count(*) from one_time_tokens where user_id = ?", personId)).isZero();
        assertThat(count("select count(*) from user_devices where user_id = ?", personId)).isZero();
        assertThat(count("select count(*) from user_roles where user_id = ? "
            + "and (valid_to is null or (valid_to >= valid_from and valid_to >= ?))", personId, today)).isZero();
        assertThat(count("select count(*) from audit_logs where action_type = 'ROLE_REVOKED' and entity_id = ? "
            + "and new_values ->> 'reason' = 'erasure'", personId)).isEqualTo(2);
        assertThat(count("select count(*) from role_delegations where delegate_id = ? and revoked_at is null",
            personId)).isZero();
        assertThat(count("select count(*) from audit_logs where action_type = 'DELEGATION_REVOKED' and entity_id = ? "
            + "and new_values ->> 'reason' = 'erasure'", personId)).isOne();
        assertThat(count("select count(*) from notifications where user_id = ?", ownerId)).isEqualTo(ownerNotices);
    }

    @Test
    void ac5_4_approvedAwardsStayPrivateWithoutDocumentsAndEveryOtherAwardGoes() {
        runner.runDue(Instant.now());

        assertThat(jdbc.queryForList("select award_id from awards where user_id = ?", Long.class, personId))
            .containsExactly(approvedId);
        assertThat(jdbc.queryForObject("select visibility from awards where award_id = ?", String.class,
            approvedId)).isEqualTo("PRIVATE");
        assertThat(count("select count(*) from award_requests where request_id = ?", pendingRequestId)).isZero();
        assertThat(count("select count(*) from documents where award_id in (?, ?, ?)", approvedId, draftId,
            pendingId)).isZero();
        assertThat(count("select count(*) from award_versions where actor_id = ?", personId)).isZero();
    }

    @Test
    void ac5_5_claimsGoBackToTheQueueAndDecisionsOnOtherPeoplesAwardsStay() {
        runner.runDue(Instant.now());

        assertThat(jdbc.queryForMap("select current_reviewer_id, status from award_requests where request_id = ?",
            claimedRequestId)).containsEntry("current_reviewer_id", null).containsEntry("status", "SUBMITTED");
        assertThat(count("select count(*) from review_decisions where request_id = ? and reviewer_id = ?",
            decidedRequestId, personId)).isOne();
    }

    @Test
    void ac5_6_ownNotificationsGoAndNamesInRelatedNotificationsOfOthersBecomeThePseudonym() {
        runner.runDue(Instant.now());

        String pseudonym = "Erased #" + personId;
        assertThat(count("select count(*) from notifications where user_id = ?", personId)).isZero();
        assertThat(count("select count(*) from notification_preferences where user_id = ?", personId)).isZero();
        assertThat(payload(reviewedNoticeId, "reviewer")).isEqualTo(pseudonym);
        assertThat(payload(delegationNoticeId, "otherParty")).isEqualTo(pseudonym);
        assertThat(payload(namesakeNoticeId, "reviewer")).isEqualTo(CURRENT_NAME);
    }

    @Test
    void ac5_7_consentRowsStayWithoutTechnicalIdentifiers() {
        runner.runDue(Instant.now());

        assertThat(jdbc.queryForMap("select ip_address, user_agent from consent_records where user_id = ?",
            personId)).containsEntry("ip_address", null).containsEntry("user_agent", null);
    }

    @Test
    void ac5_9_theTrailIsRedactedIncludingTheRowsOfTheErasureItself() {
        runner.runDue(Instant.now());

        assertThat(jdbc.queryForMap("select ip_address, user_agent, new_values ->> 'email' as email, "
            + "new_values ->> 'devices' as devices from audit_logs where action_type = 'LOGIN_SUCCESS' "
            + "and user_id = ?", personId))
            .containsEntry("ip_address", null).containsEntry("user_agent", null)
            .containsEntry("email", "[erased]").containsEntry("devices", "1");
        assertThat(jdbc.queryForList("select coalesce(old_values, '{}'::jsonb)::text || coalesce(new_values, "
            + "'{}'::jsonb)::text from audit_logs where entity_type = 'users' and entity_id = ?", String.class,
            personId)).isNotEmpty().noneMatch(values -> values.contains("Erik") || values.contains("Former")
            || values.contains("it.erased."));
        assertThat(jdbc.queryForList("select coalesce(old_values, new_values)::text from audit_logs "
            + "where entity_type = 'awards' and entity_id in (?, ?)", String.class, draftId, pendingId))
            .isNotEmpty().allMatch("{\"erased\": true}"::equals);
        assertThat(jdbc.queryForList("select coalesce(old_values, '{}'::jsonb)::text || new_values::text "
            + "from audit_logs where entity_type = 'notifications' and entity_id = ?", String.class, reviewedNoticeId))
            .isNotEmpty().noneMatch(values -> values.contains(CURRENT_NAME));
        assertThat(jdbc.queryForObject("select new_values ->> 'values' from audit_logs where action_type = "
            + "'AUDIT_REDACTED' and entity_id = ?", Integer.class, personId)).isPositive();
        assertThatThrownBy(() -> jdbc.update("update audit_logs set new_values = '{}' where user_id = ?", personId))
            .hasMessageContaining("audit_logs rows are immutable");
    }

    @Test
    void ac5_8_theTrailRefusesDeletesAndEveryUpdateBeyondTheGuardedValues() {
        assertThatThrownBy(() -> jdbc.update("delete from audit_logs where user_id = ?", personId))
            .hasMessageContaining("audit_logs rows cannot be deleted");
        String partition = jdbc.queryForObject("select tableoid::regclass::text from audit_logs where user_id = ? "
            + "limit 1", String.class, personId);
        assertThatThrownBy(() -> jdbc.update("update " + partition + " set user_agent = null where user_id = ?",
            personId)).hasMessageContaining("audit_logs rows are immutable");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbc.queryForList("select set_config('app.audit_redaction', 'on', true)");
            jdbc.update("update audit_logs set action_type = 'LOGOUT' where user_id = ?", personId);
        })).hasMessageContaining("audit_logs rows are immutable");
        Integer redacted = transactions.execute(status -> {
            jdbc.queryForList("select set_config('app.audit_redaction', 'on', true)");
            return jdbc.update("update audit_logs set user_agent = null where user_id = ?", personId);
        });
        assertThat(redacted).isPositive();
    }

    private void authorityOfPerson() {
        jdbc.update("insert into user_roles (user_id, organization_id, role_type, valid_from) values (?, ?, "
            + "'EMPLOYEE', ?), (?, ?, 'FACULTY_SECRETARY', ?)", personId, TestUsers.DAI_DEPARTMENT_ID,
            today.minusDays(30), personId, TestUsers.DAI_DEPARTMENT_ID, today.plusDays(5));
        jdbc.update("insert into role_delegations (delegator_id, delegate_id, organization_id, role_type, valid_from, "
            + "valid_to) values (?, ?, ?, 'FACULTY_SECRETARY', ?, ?)", ownerId, personId, TestUsers.DAI_DEPARTMENT_ID,
            today, today.plusDays(10));
    }

    private void awardsOfPerson() {
        approvedId = AwardRows.award(jdbc, personId).title("Kept").status("APPROVED").insert();
        jdbc.update("update awards set visibility = 'PUBLIC' where award_id = ?", approvedId);
        draftId = AwardRows.award(jdbc, personId).title("Draft").insert();
        pendingId = AwardRows.award(jdbc, personId).title("Pending").status("PENDING").insert();
        pendingRequestId = RequestRows.request(jdbc, pendingId, personId).insert();
        DecisionRows.decision(jdbc, pendingRequestId, reviewerId).comments("Please attach the scan").insert();
        document(approvedId);
        document(pendingId);
    }

    private void workOnOthersAwards() {
        long claimedId = AwardRows.award(jdbc, ownerId).title("Claimed").status("PENDING").insert();
        claimedRequestId = RequestRows.request(jdbc, claimedId, ownerId).status("IN_REVIEW").insert();
        jdbc.update("update award_requests set current_reviewer_id = ? where request_id = ?", personId,
            claimedRequestId);
        long decidedId = AwardRows.award(jdbc, ownerId).title("Decided").status("APPROVED").insert();
        decidedRequestId = RequestRows.request(jdbc, decidedId, ownerId).status("APPROVED").insert();
        DecisionRows.decision(jdbc, decidedRequestId, personId).type("APPROVED").insert();
        jdbc.update("insert into award_versions (award_id, version_number, action, actor_id, snapshot) "
            + "values (?, 1, 'DECIDED', ?, '{}')", decidedId, personId);
        long otherId = AwardRows.award(jdbc, ownerId).title("Other").status("APPROVED").insert();

        reviewedNoticeId = notice(ownerId, "AWARD_APPROVED", decidedId, "{\"title\": \"Decided\", \"reviewer\": \""
            + CURRENT_NAME + "\"}");
        delegationNoticeId = notice(ownerId, "DELEGATION_REVOKED", null, "{\"otherParty\": \"" + FORMER_NAME
            + "\"}");
        namesakeNoticeId = notice(ownerId, "AWARD_APPROVED", otherId, "{\"title\": \"Other\", \"reviewer\": \""
            + CURRENT_NAME + "\"}");
        notice(personId, "AWARD_RETURNED", pendingId, "{\"title\": \"Pending\"}");
        jdbc.update("insert into notification_preferences (user_id, event_type) values (?, 'AWARD_APPROVED')",
            personId);
    }

    private void tracesOfPerson() {
        jdbc.update("insert into consent_records (user_id, consent_type, consent_version, is_granted, granted_at, "
            + "ip_address, user_agent) values (?, 'DATA_PROCESSING', '1.0', true, now(), ?::inet, ?)", personId, IP,
            AGENT);
        jdbc.update("insert into one_time_tokens (token_hash, user_id, purpose, expires_at, new_email_address) "
            + "values (?, ?, 'EMAIL_CHANGE', now() + interval '1 day', 'new@chnu.edu.ua')",
            UUID.randomUUID().toString(), personId);
        jdbc.update("insert into user_devices (user_id, fingerprint, last_ip_address, first_seen_at, last_used_at) "
            + "values (?, 'fp', ?, now(), now())", personId, IP);
        jdbc.update("insert into audit_logs (user_id, action_type, entity_type, entity_id, new_values, ip_address, "
            + "user_agent) values (?, 'LOGIN_SUCCESS', 'AUTHENTICATION', ?, ?::jsonb, ?::inet, ?)", personId,
            personId, "{\"email\": \"erik@chnu.edu.ua\", \"devices\": 1}", IP, AGENT);
        jdbc.update("insert into erasure_requests (user_id, requested_at, scheduled_for) "
            + "values (?, now() - interval '31 days', now() - interval '1 hour')", personId);
    }

    private void document(long awardId) {
        jdbc.update("insert into documents (award_id, file_name, file_type, file_size, storage_bucket, storage_key, "
            + "uploaded_by, document_type) values (?, 'scan.pdf', 'PDF', 10, 'awards', ?, ?, 'CERTIFICATE')", awardId,
            "it-erasure/" + UUID.randomUUID(), personId);
    }

    private long notice(long userId, String type, Long awardId, String payload) {
        return jdbc.queryForObject("insert into notifications (user_id, type, channel, reference_type, reference_id, "
            + "sent_at, payload, dedupe_key) values (?, ?, 'IN_APP', ?, ?, now(), ?::jsonb, ?) "
            + "returning notification_id", Long.class, userId, type, awardId == null ? null : "AWARD", awardId,
            payload, UUID.randomUUID().toString());
    }

    private String payload(long notificationId, String key) {
        return jdbc.queryForObject("select payload ->> ? from notifications where notification_id = ?", String.class,
            key, notificationId);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}

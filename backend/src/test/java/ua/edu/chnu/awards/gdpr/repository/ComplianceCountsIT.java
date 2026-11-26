package ua.edu.chnu.awards.gdpr.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.common.privacy.PrivacyNoticeVersions;
import ua.edu.chnu.awards.gdpr.ComplianceFigures;
import ua.edu.chnu.awards.gdpr.dto.RetentionStatus;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class ComplianceCountsIT extends AbstractIntegrationTest {

    private static final Instant RUN_STARTED = Instant.parse("2099-03-01T02:00:00Z");
    private static final String CONSENT = "insert into consent_records (user_id, consent_type, consent_version,"
        + " is_granted, granted_at, withdrawn_at) values (?, ?, ?, ?, ?, ?)";
    private static final String ERASURE = "insert into erasure_requests (user_id, requested_at, scheduled_for,"
        + " postponed_at) values (?, ?, ?, ?)";

    @Autowired
    private ComplianceFigures figures;

    @Autowired
    private PrivacyNoticeVersions noticeVersions;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private List<User> people;

    @BeforeEach
    void createUsers() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        people = userRepository.saveAll(List.of(TestUsers.user("it.counts.a@chnu.edu.ua", department),
            TestUsers.user("it.counts.b@chnu.edu.ua", department),
            TestUsers.user("it.counts.c@chnu.edu.ua", department)));
    }

    @AfterEach
    void deleteRows() {
        jdbc.update("delete from retention_runs where started_at >= ?", Timestamp.from(RUN_STARTED));
        for (User person : people) {
            jdbc.update("delete from erasure_requests where user_id = ?", person.getId());
            jdbc.update("delete from legal_holds where user_id = ?", person.getId());
            ConsentRows.purge(jdbc, person.getId());
        }
        userRepository.deleteAll(people);
    }

    @Test
    void ac1_10_consentsCountTheLatestRowOfActiveAccountsOnly() {
        Instant now = Instant.now();
        String current = noticeVersions.current().version();
        final ComplianceFigures.Counts before = figures.read(current, now);
        long a = people.get(0).getId();
        long b = people.get(1).getId();
        final long inactive = people.get(2).getId();
        consent(a, "DATA_PROCESSING", "0.9", true);
        consent(a, "DATA_PROCESSING", current, true);
        consent(b, "DATA_PROCESSING", "0.9", true);
        consent(inactive, "DATA_PROCESSING", current, true);
        consent(a, "ANALYTICS", current, true);
        consent(a, "ANALYTICS", current, false);
        consent(b, "ANALYTICS", current, true);
        consent(inactive, "ANALYTICS", current, true);
        jdbc.update("update users set account_status = 'INACTIVE' where user_id = ?", inactive);

        ComplianceFigures.Counts after = figures.read(current, now);

        assertThat(after.activeAccounts()).isEqualTo(jdbc.queryForObject(
            "select count(*) from users where account_status = 'ACTIVE'", Long.class));
        assertThat(before.activeAccounts() - after.activeAccounts()).isEqualTo(1);
        assertThat(after.acknowledged() - before.acknowledged()).isEqualTo(1);
        assertThat(after.analyticsGranted() - before.analyticsGranted()).isEqualTo(1);
    }

    @Test
    void ac1_10_erasureIsOverdueWhenNotPostponedAndScheduledMoreThanADayAgo() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        final ComplianceFigures.Counts before = figures.read("1.0", now);
        erasure(people.get(0).getId(), now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(3)));
        erasure(people.get(1).getId(), now.minus(Duration.ofDays(2)), null);
        erasure(people.get(2).getId(), now.minus(Duration.ofHours(12)), null);

        ComplianceFigures.Counts after = figures.read("1.0", now);

        assertThat(after.erasurePending() - before.erasurePending()).isEqualTo(3);
        assertThat(after.erasurePostponed() - before.erasurePostponed()).isEqualTo(1);
        assertThat(after.erasureOverdue() - before.erasureOverdue()).isEqualTo(1);
    }

    @Test
    void ac1_10_activeHoldsAndTheLatestRunAreRead() {
        Instant now = Instant.now();
        final ComplianceFigures.Counts before = figures.read("1.0", now);
        long officer = people.get(2).getId();
        jdbc.update("insert into legal_holds (user_id, reason, placed_by, placed_at) values (?, 'Case 1', ?, ?)",
            people.get(0).getId(), officer, Timestamp.from(now));
        jdbc.update("insert into legal_holds (user_id, reason, placed_by, placed_at, lifted_by, lifted_at,"
            + " lift_reason) values (?, 'Case 2', ?, ?, ?, ?, 'Closed')", people.get(1).getId(), officer,
            Timestamp.from(now), officer, Timestamp.from(now.plusSeconds(60)));
        jdbc.update("insert into retention_runs (started_at, finished_at, status) values (?, ?, 'PARTIAL')",
            Timestamp.from(RUN_STARTED), Timestamp.from(RUN_STARTED.plusSeconds(5)));

        ComplianceFigures.Counts after = figures.read("1.0", now);

        assertThat(after.activeLegalHolds() - before.activeLegalHolds()).isEqualTo(1);
        assertThat(after.lastRetentionRun()).isEqualTo(new ComplianceFigures.RetentionRun(RetentionStatus.PARTIAL,
            RUN_STARTED, RUN_STARTED.plusSeconds(5)));
    }

    private void consent(long userId, String type, String version, boolean granted) {
        Timestamp at = Timestamp.from(Instant.now());
        jdbc.update(CONSENT, userId, type, version, granted, granted ? at : null, granted ? null : at);
    }

    private void erasure(long userId, Instant scheduledFor, Instant postponedAt) {
        jdbc.update(ERASURE, userId, Timestamp.from(scheduledFor.minus(Duration.ofDays(30))),
            Timestamp.from(scheduledFor), postponedAt == null ? null : Timestamp.from(postponedAt));
    }
}

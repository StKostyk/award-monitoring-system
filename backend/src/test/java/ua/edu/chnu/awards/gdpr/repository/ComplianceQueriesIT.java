package ua.edu.chnu.awards.gdpr.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.common.retention.RetentionResult;
import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.gdpr.dto.ErasureOrigin;
import ua.edu.chnu.awards.gdpr.dto.ErasureRequestEntry;
import ua.edu.chnu.awards.gdpr.dto.ErasureRequestStatus;
import ua.edu.chnu.awards.gdpr.dto.LegalHoldEntry;
import ua.edu.chnu.awards.gdpr.dto.RetentionRunEntry;
import ua.edu.chnu.awards.gdpr.dto.RetentionStatus;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class ComplianceQueriesIT extends AbstractIntegrationTest {

    private static final Instant EARLIER = Instant.parse("2099-01-01T02:00:00Z");
    private static final Instant LATER = Instant.parse("2099-01-02T02:00:00Z");
    private static final String REASON = "Pending court case 12/2026";

    @Autowired
    private ComplianceQueries queries;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private User person;
    private User erased;
    private User officer;

    @BeforeEach
    void createUsers() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        person = userRepository.save(TestUsers.user("it.compliance@chnu.edu.ua", department));
        erased = userRepository.save(TestUsers.user("it.compliance.erased@chnu.edu.ua", department));
        officer = userRepository.save(TestUsers.user("it.compliance.officer@chnu.edu.ua", department));
    }

    @AfterEach
    void deleteRows() {
        jdbc.update("delete from retention_runs where started_at >= ?", Timestamp.from(EARLIER));
        jdbc.update("delete from erasure_requests where user_id in (?, ?)", person.getId(), erased.getId());
        jdbc.update("delete from legal_holds where user_id = ?", person.getId());
        userRepository.deleteAllById(List.of(person.getId(), erased.getId(), officer.getId()));
    }

    @Test
    void ac2_2_runsAreListedNewestFirstWithTheirResults() {
        jdbc.update("insert into retention_runs (started_at, finished_at, status, results) values (?, ?, 'PARTIAL',"
            + " '[{\"policy\":\"notifications\",\"affected\":3,\"held\":0,\"failed\":true,"
            + "\"error\":\"DataAccessException\"}]')", Timestamp.from(EARLIER), Timestamp.from(EARLIER.plusSeconds(5)));
        jdbc.update("insert into retention_runs (started_at) values (?)", Timestamp.from(LATER));

        PageResponse<RetentionRunEntry> page = queries.runs(0, 2);

        assertThat(page.content()).extracting(RetentionRunEntry::startedAt).containsExactly(LATER, EARLIER);
        RetentionRunEntry running = page.content().getFirst();
        assertThat(running.status()).isEqualTo(RetentionStatus.RUNNING);
        assertThat(running.finishedAt()).isNull();
        assertThat(running.results()).isEmpty();
        RetentionRunEntry partial = page.content().get(1);
        assertThat(partial.status()).isEqualTo(RetentionStatus.PARTIAL);
        assertThat(partial.finishedAt()).isEqualTo(EARLIER.plusSeconds(5));
        assertThat(partial.results()).containsExactly(
            new RetentionResult("notifications", 3, 0, true, "DataAccessException"));
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(2);
        assertThat(page.size()).isEqualTo(2);
    }

    @Test
    void ac2_3_requestsOfBothOriginsAreListedNewestFirstAndFilteredByStatus() {
        jdbc.update("insert into erasure_requests (user_id, requested_at, scheduled_for, postponed_at)"
            + " values (?, ?, ?, ?)", person.getId(), Timestamp.from(EARLIER), Timestamp.from(LATER),
            Timestamp.from(LATER));
        jdbc.update("insert into erasure_requests (user_id, requested_at, scheduled_for, origin, status, completed_at)"
            + " values (?, ?, ?, 'RETENTION', 'COMPLETED', ?)", erased.getId(), Timestamp.from(LATER),
            Timestamp.from(LATER), Timestamp.from(LATER.plusSeconds(60)));
        jdbc.update("update users set first_name = 'Erased', last_name = ? where user_id = ?",
            "#" + erased.getId(), erased.getId());

        List<ErasureRequestEntry> all = mine(queries.erasureRequests(null, 0, 100).content());

        assertThat(all).extracting(entry -> entry.person().id()).containsExactly(erased.getId(), person.getId());
        ErasureRequestEntry completed = all.getFirst();
        assertThat(completed.person().name()).isEqualTo("Erased #" + erased.getId());
        assertThat(completed.origin()).isEqualTo(ErasureOrigin.RETENTION);
        assertThat(completed.status()).isEqualTo(ErasureRequestStatus.COMPLETED);
        assertThat(completed.completedAt()).isEqualTo(LATER.plusSeconds(60));
        ErasureRequestEntry pending = all.get(1);
        assertThat(pending.origin()).isEqualTo(ErasureOrigin.REQUEST);
        assertThat(pending.status()).isEqualTo(ErasureRequestStatus.PENDING);
        assertThat(pending.requestedAt()).isEqualTo(EARLIER);
        assertThat(pending.scheduledFor()).isEqualTo(LATER);
        assertThat(pending.postponedAt()).isEqualTo(LATER);
        assertThat(pending.cancelledAt()).isNull();

        assertThat(mine(queries.erasureRequests(ErasureRequestStatus.PENDING, 0, 100).content()))
            .extracting(entry -> entry.person().id()).containsExactly(person.getId());
        assertThat(mine(queries.erasureRequests(ErasureRequestStatus.CANCELLED, 0, 100).content())).isEmpty();
    }

    @Test
    void ac2_4_holdsOfEveryAccountAreListedNewestFirstAndActiveOnesOnRequest() {
        jdbc.update("insert into legal_holds (user_id, reason, placed_by, placed_at, lifted_by, lifted_at,"
            + " lift_reason) values (?, ?, ?, ?, ?, ?, 'Closed')", person.getId(), REASON, officer.getId(),
            Timestamp.from(EARLIER), officer.getId(), Timestamp.from(EARLIER.plusSeconds(60)));
        jdbc.update("insert into legal_holds (user_id, reason, placed_by, placed_at) values (?, ?, ?, ?)",
            person.getId(), REASON, officer.getId(), Timestamp.from(LATER));

        List<LegalHoldEntry> all = holdsOfPerson(queries.legalHolds(false, 0, 100).content());

        assertThat(all).extracting(LegalHoldEntry::placedAt).containsExactly(LATER, EARLIER);
        LegalHoldEntry active = all.getFirst();
        assertThat(active.person().id()).isEqualTo(person.getId());
        assertThat(active.reason()).isEqualTo(REASON);
        assertThat(active.placedBy().id()).isEqualTo(officer.getId());
        assertThat(active.liftedBy()).isNull();
        LegalHoldEntry lifted = all.get(1);
        assertThat(lifted.liftedBy().id()).isEqualTo(officer.getId());
        assertThat(lifted.liftedAt()).isEqualTo(EARLIER.plusSeconds(60));
        assertThat(lifted.liftReason()).isEqualTo("Closed");

        assertThat(holdsOfPerson(queries.legalHolds(true, 0, 100).content()))
            .extracting(LegalHoldEntry::placedAt).containsExactly(LATER);
    }

    @Test
    void ac2_2_aPageBeyondTheEndIsEmptyAndTheSizeIsClamped() {
        PageResponse<LegalHoldEntry> page = queries.legalHolds(false, 1000, 1000);

        assertThat(page.content()).isEmpty();
        assertThat(page.size()).isEqualTo(PageResponse.MAX_SIZE);
        assertThat(page.number()).isEqualTo(1000);
    }

    private List<ErasureRequestEntry> mine(List<ErasureRequestEntry> entries) {
        return entries.stream()
            .filter(entry -> List.of(person.getId(), erased.getId()).contains(entry.person().id()))
            .toList();
    }

    private List<LegalHoldEntry> holdsOfPerson(List<LegalHoldEntry> entries) {
        return entries.stream().filter(entry -> person.getId().equals(entry.person().id())).toList();
    }
}

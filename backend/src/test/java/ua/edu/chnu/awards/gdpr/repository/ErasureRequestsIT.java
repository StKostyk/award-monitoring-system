package ua.edu.chnu.awards.gdpr.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.gdpr.dto.ErasureRequest;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.ErasureRequestEntry;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class ErasureRequestsIT extends AbstractIntegrationTest {

    private static final Duration GRACE_PERIOD = Duration.ofDays(30);
    private static final Duration WARNING_BEFORE = Duration.ofDays(7);
    private static final Instant REQUESTED = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant DUE = REQUESTED.plus(GRACE_PERIOD);
    private static final LocalDate ROLE_DAY = LocalDate.of(1990, 6, 1);

    @Autowired
    private ErasureRequests requests;

    @Autowired
    private PersonalDataQueries queries;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private User person;
    private User colleague;

    @BeforeEach
    void createUsers() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        person = userRepository.save(TestUsers.user("it.erasure@chnu.edu.ua", department));
        colleague = userRepository.save(TestUsers.user("it.erasure.admin@chnu.edu.ua", department));
    }

    @AfterEach
    void deleteUsers() {
        jdbc.update("delete from erasure_requests where user_id in (?, ?)", person.getId(), colleague.getId());
        userRepository.deleteAllById(List.of(person.getId(), colleague.getId()));
    }

    @Test
    void ac4_1_anAccountHasAtMostOnePendingRequest() {
        long first = requests.create(person.getId(), REQUESTED, DUE);

        assertThatThrownBy(() -> requests.create(person.getId(), REQUESTED, DUE))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_erasure_requests_pending");

        assertThat(requests.pending(person.getId())).contains(new ErasureRequest(first, REQUESTED, DUE, false));
        assertThat(requests.cancel(person.getId(), REQUESTED.plusSeconds(60))).contains(first);
        assertThat(requests.cancel(person.getId(), REQUESTED.plusSeconds(60))).isEmpty();
        assertThat(requests.pending(person.getId())).isEmpty();
        requests.create(person.getId(), REQUESTED.plusSeconds(120), DUE);

        assertThat(queries.erasureRequests(person.getId())).extracting(ErasureRequestEntry::status)
            .containsExactly("PENDING", "CANCELLED");
    }

    @Test
    void ac4_1_theStatusAndItsDatesGoTogether() {
        long request = requests.create(person.getId(), REQUESTED, DUE);

        assertThatThrownBy(() -> jdbc.update("update erasure_requests set status = 'CANCELLED' where request_id = ?",
            request)).isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_erasure_requests_dates");
        assertThatThrownBy(() -> jdbc.update("update erasure_requests set status = 'ERASED' where request_id = ?",
            request)).isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_erasure_requests_status");
    }

    @Test
    void ac4_1_changesAreAuditedUnderTheRequestKey() {
        long request = requests.create(person.getId(), REQUESTED, DUE);

        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_type = 'erasure_requests'"
            + " and entity_id = ? and action_type = 'INSERT'", Long.class, request)).isEqualTo(1L);
    }

    @Test
    void ac4_6_onlyUnwarnedRequestsWithinTheWarningPeriodAreDue() {
        long request = requests.create(person.getId(), REQUESTED, DUE);
        Instant early = DUE.minus(WARNING_BEFORE).minusSeconds(60);
        Instant inside = DUE.minus(WARNING_BEFORE).plusSeconds(60);

        assertThat(requests.dueForWarning(early, WARNING_BEFORE)).isEmpty();
        assertThat(requests.dueForWarning(inside, WARNING_BEFORE))
            .containsExactly(new ErasureRequests.Due(request, DUE, "it.erasure@chnu.edu.ua", person.getFirstName()));

        requests.markWarned(request, inside);

        assertThat(requests.dueForWarning(inside, WARNING_BEFORE)).isEmpty();
        assertThat(requests.dueForWarning(DUE.plusSeconds(1), WARNING_BEFORE)).isEmpty();
    }

    @Test
    void ac4_6_aRequestWithLessTimeLeftThanTheWarningPeriodIsNeverWarned() {
        Instant late = DUE.minus(Duration.ofDays(5));
        requests.create(person.getId(), late, DUE);

        assertThat(requests.dueForWarning(late.plusSeconds(60), WARNING_BEFORE)).isEmpty();
    }

    @Test
    void ac4_3_theOnlyActiveSystemAdministratorIsRecognised() {
        assertThat(requests.isOnlyAdministrator(person.getId(), ROLE_DAY)).isFalse();

        grantAdministrator(person);
        assertThat(requests.isOnlyAdministrator(person.getId(), ROLE_DAY)).isTrue();

        grantAdministrator(colleague);
        assertThat(requests.isOnlyAdministrator(person.getId(), ROLE_DAY)).isFalse();

        colleague.setAccountStatus(AccountStatus.SUSPENDED);
        userRepository.save(colleague);
        assertThat(requests.isOnlyAdministrator(person.getId(), ROLE_DAY)).isTrue();
    }

    private void grantAdministrator(User user) {
        jdbc.update("insert into user_roles (user_id, organization_id, role_type, valid_from, valid_to)"
            + " values (?, ?, 'SYSTEM_ADMIN', ?, ?)", user.getId(), TestUsers.UNIVERSITY_ID, ROLE_DAY.minusDays(1),
            ROLE_DAY.plusDays(1));
    }
}

package ua.edu.chnu.awards.gdpr.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.gdpr.dto.LegalHold;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.LegalHoldEntry;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class LegalHoldsIT extends AbstractIntegrationTest {

    private static final String REASON = "Pending court case 12/2026";
    private static final String LIFT_REASON = "Court case closed";
    private static final Instant PLACED = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant LIFTED = Instant.parse("2026-10-10T09:00:00Z");

    @Autowired
    private LegalHolds holds;

    @Autowired
    private PersonalDataQueries queries;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private User person;
    private User officer;

    @BeforeEach
    void createUsers() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        person = userRepository.save(TestUsers.user("it.hold@chnu.edu.ua", department));
        officer = userRepository.save(TestUsers.user("it.hold.officer@chnu.edu.ua", department));
    }

    @AfterEach
    void deleteUsers() {
        jdbc.update("delete from legal_holds where user_id = ?", person.getId());
        userRepository.deleteAllById(List.of(person.getId(), officer.getId()));
    }

    @Test
    void ac3_1_anAccountHasAtMostOneActiveHold() {
        long first = holds.place(person.getId(), REASON, officer.getId(), PLACED);

        assertThatThrownBy(() -> holds.place(person.getId(), REASON, officer.getId(), PLACED))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_legal_holds_active");

        assertThat(holds.lift(first, LIFT_REASON, officer.getId(), LIFTED)).isTrue();
        assertThat(holds.lift(first, LIFT_REASON, officer.getId(), LIFTED)).isFalse();
        holds.place(person.getId(), REASON, officer.getId(), LIFTED.plus(1, ChronoUnit.DAYS));

        assertThat(holds.isActive(person.getId())).isTrue();
        assertThat(holds.of(person.getId())).extracting(LegalHold::active).containsExactly(true, false);
    }

    @Test
    void ac3_1_aLiftNeedsByAtAndReasonTogether() {
        long hold = holds.place(person.getId(), REASON, officer.getId(), PLACED);

        assertThatThrownBy(() -> jdbc.update("update legal_holds set lifted_at = now() where hold_id = ?", hold))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_legal_holds_lifted");
    }

    @Test
    void ac3_3_aHoldIsReadWithItsOfficersAndOnlyForItsAccount() {
        long hold = holds.place(person.getId(), REASON, officer.getId(), PLACED);
        holds.lift(hold, LIFT_REASON, officer.getId(), LIFTED);

        LegalHold read = holds.find(person.getId(), hold).orElseThrow();

        assertThat(read.reason()).isEqualTo(REASON);
        assertThat(read.placedAt()).isEqualTo(PLACED);
        assertThat(read.placedBy().id()).isEqualTo(officer.getId());
        assertThat(read.placedBy().email()).isEqualTo("it.hold.officer@chnu.edu.ua");
        assertThat(read.liftedAt()).isEqualTo(LIFTED);
        assertThat(read.liftedBy().id()).isEqualTo(officer.getId());
        assertThat(read.liftReason()).isEqualTo(LIFT_REASON);
        assertThat(holds.find(officer.getId(), hold)).isEmpty();
        assertThat(holds.lockAccount(person.getId())).contains("ACTIVE");
        assertThat(holds.accountExists(Long.MAX_VALUE)).isFalse();
    }

    @Test
    void ac3_2_theRowAuditKeepsNeitherReason() {
        long hold = holds.place(person.getId(), REASON, officer.getId(), PLACED);
        holds.lift(hold, LIFT_REASON, officer.getId(), LIFTED);

        List<Map<String, Object>> rows = jdbc.queryForList("select action_type, entity_id,"
            + " coalesce(new_values::text, '') || coalesce(old_values::text, '') as snapshot from audit_logs"
            + " where entity_type = 'legal_holds' and entity_id = ? order by log_id", hold);

        assertThat(rows).extracting(row -> row.get("action_type")).containsExactly("INSERT", "UPDATE");
        assertThat(rows).allSatisfy(row -> assertThat((String) row.get("snapshot"))
            .contains("placed_by").doesNotContain(REASON, LIFT_REASON, "\"reason\"", "lift_reason"));
    }

    @Test
    void ac3_7_theExportListsTheHoldsWithTheirDatesOnly() {
        long hold = holds.place(person.getId(), REASON, officer.getId(), PLACED);
        holds.lift(hold, LIFT_REASON, officer.getId(), LIFTED);
        Instant again = LIFTED.plus(1, ChronoUnit.DAYS);
        holds.place(person.getId(), REASON, officer.getId(), again);

        assertThat(queries.legalHolds(person.getId()))
            .containsExactly(new LegalHoldEntry(again, null), new LegalHoldEntry(PLACED, LIFTED));
        assertThat(queries.legalHolds(officer.getId())).isEmpty();
    }
}

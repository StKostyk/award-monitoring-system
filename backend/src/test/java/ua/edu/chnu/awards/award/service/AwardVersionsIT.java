package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ua.edu.chnu.awards.audit.dto.AuditTrailEntry;
import ua.edu.chnu.awards.audit.service.AuditTrailService;
import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.ClientRequest;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

class AwardVersionsIT extends AbstractIntegrationTest {

    private static final String OWNER = "it.versions@chnu.edu.ua";
    private static final String REVIEWER = "it.versions.reviewer@chnu.edu.ua";
    private static final String VERSIONS = "select version_number, action, actor_id, changed_fields::text as changed "
        + "from award_versions where award_id = ? order by version_number";

    @Autowired
    private AwardService awardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditTrailService auditTrail;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private User owner;
    private UUID correlation;

    @BeforeEach
    void setUp() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        owner = userRepository.save(TestUsers.user(OWNER, department));
        correlation = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ClientRequest.CORRELATION_ATTRIBUTE, correlation);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TestUsers.signInAs(owner);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        jdbc.update("delete from awards where user_id = ?", owner.getId());
        List.of(OWNER, REVIEWER).forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac1_1_to_ac1_4_everySaveThatChangesTheDraftIsOneVersionAndFailedSavesAreNone() {
        AwardResponse created = awardService.create(form("Letter", null, null));
        long id = created.id();
        assertThat(created.version()).isEqualTo(1L);

        AwardResponse saved = awardService.update(id, form("Diploma", null, 1L));
        AwardResponse unchanged = awardService.update(id, form("Diploma", null, saved.version()));
        assertThatThrownBy(() -> awardService.update(id, form("Stale", null, 1L)))
            .isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(() -> awardService.update(id, form("Future", LocalDate.now().plusYears(1), 2L)))
            .isInstanceOf(ApiProblemException.class);

        assertThat(unchanged.version()).isEqualTo(2L);
        List<Map<String, Object>> rows = jdbc.queryForList(VERSIONS, id);
        assertThat(rows).extracting(row -> row.get("version_number")).containsExactly(1L, 2L);
        assertThat(rows).extracting(row -> row.get("action")).containsExactly("CREATED", "UPDATED");
        assertThat(rows).extracting(row -> row.get("actor_id")).containsOnly(owner.getId());
        assertThat(rows).extracting(row -> row.get("changed")).containsExactly(null, "{title}");
        String snapshot = jdbc.queryForObject("select snapshot::text from award_versions where award_id = ? "
            + "and version_number = 2", String.class, id);
        assertThat(snapshot).contains("\"title\": \"Diploma\"", "\"organizationId\": 64", "\"status\": \"DRAFT\"");
    }

    @Test
    void ac1_6_triggerRowsOfTheSaveNameTheCallerAndTheRequest() {
        long id = awardService.create(form("Letter", null, null)).id();
        awardService.update(id, form("Diploma", null, 1L));

        List<Map<String, Object>> rows = jdbc.queryForList("select action_type, user_id, correlation_id "
            + "from audit_logs where entity_type = 'awards' and entity_id = ? order by log_id", id);

        assertThat(rows).extracting(row -> row.get("action_type")).containsExactly("INSERT", "UPDATE");
        assertThat(rows).extracting(row -> row.get("user_id")).containsOnly(owner.getId());
        assertThat(rows).extracting(row -> row.get("correlation_id")).containsOnly(correlation);
    }

    @Test
    void ac1_5_deletingADraftDeletesItsVersionsAndLogsTheDeleteUnderTheAward() {
        long id = awardService.create(form("Letter", null, null)).id();

        awardService.delete(id);

        assertThat(jdbc.queryForObject("select count(*) from award_versions where award_id = ?", Long.class, id))
            .isZero();
        Map<String, Object> row = jdbc.queryForMap("select entity_id, user_id from audit_logs "
            + "where entity_type = 'awards' and action_type = 'DELETE' and old_values->>'award_id' = ?",
            Long.toString(id));
        assertThat(row).containsEntry("entity_id", id).containsEntry("user_id", owner.getId());
    }

    @Test
    void ac1_10_aDeleteRowWrittenBeforeV023BelongsToTheDeletedAwardNotToTheOwnersIdNamesake() {
        long id = awardService.create(form("Letter", null, null)).id();
        long deleted = id + 1_000_000;
        jdbc.update("insert into audit_logs (action_type, entity_type, entity_id, old_values) "
            + "values ('DELETE', 'awards', ?, jsonb_build_object('award_id', ?::bigint, 'user_id', ?::bigint))",
            id, deleted, id);

        assertThat(auditTrail.aboutAward(id, 0, 20).getContent())
            .extracting(AuditTrailEntry::action).containsExactly("INSERT");
        assertThat(auditTrail.aboutAward(deleted, 0, 20).getContent()).singleElement()
            .satisfies(row -> assertThat(row.action()).isEqualTo("DELETE"));
    }

    @Test
    void edge_versionsAreUniquePerNumberAndCannotBeChanged() {
        long id = awardService.create(form("Letter", null, null)).id();

        assertThatThrownBy(() -> jdbc.update("insert into award_versions (award_id, version_number, action, "
            + "snapshot) values (?, 1, 'UPDATED', '{}')", id))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update award_versions set action = 'SUBMITTED' where award_id = ?",
            id))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("immutable");
    }

    @Test
    void edge_erasingTheActorClearsItOnTheVersion() {
        long id = awardService.create(form("Letter", null, null)).id();
        User reviewer = userRepository.save(TestUsers.user(REVIEWER, owner.getOrganization()));
        jdbc.update("insert into award_versions (award_id, version_number, action, actor_id, snapshot) "
            + "values (?, 7, 'UPDATED', ?, '{}')", id, reviewer.getId());

        userRepository.delete(reviewer);

        assertThat(jdbc.queryForObject("select actor_id from award_versions where award_id = ? "
            + "and version_number = 7", Long.class, id)).isNull();
    }

    private static AwardForm form(String title, LocalDate date, Long version) {
        return new AwardForm(title, null, null, null, null, null, date, null, version);
    }
}

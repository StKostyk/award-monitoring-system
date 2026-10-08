package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.support.TestWorkflow;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReviewPeriodFT extends AbstractFunctionalTest {

    private static final String DEAN = "ft.period.dean@chnu.edu.ua";
    private static final String SECRETARY = "ft.period.secretary@chnu.edu.ua";
    private static final String OTHER_DEAN = "ft.period.otherdean@chnu.edu.ua";
    private static final String EMPLOYEE = "ft.period.employee@chnu.edu.ua";
    private static final String DEPUTY = "ft.period.deputy@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(DEAN, SECRETARY, OTHER_DEAN, EMPLOYEE, DEPUTY);
    private static final String DAYS = "workingDays";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final long OTHER_FACULTY_ID = 10L;
    private static final int FACULTY_DAYS = 5;
    private static final int DEFAULT_DAYS = 3;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long facultyId;
    private long departmentId;
    private long deanId;

    @BeforeAll
    void createUsers() {
        facultyId = jdbc.queryForObject("select max(f.org_id) from organizations f where f.org_type = 'FACULTY'"
            + " and f.org_id <> ? and exists (select 1 from organizations d where d.parent_org_id = f.org_id"
            + " and d.org_type = 'DEPARTMENT' and d.is_active)", Long.class, OTHER_FACULTY_ID);
        departmentId = jdbc.queryForObject("select min(org_id) from organizations where parent_org_id = ?"
            + " and org_type = 'DEPARTMENT' and is_active", Long.class, facultyId);
        Organization faculty = organizationRepository.findById(facultyId).orElseThrow();
        Organization other = organizationRepository.findById(OTHER_FACULTY_ID).orElseThrow();
        final Organization department = organizationRepository.findById(departmentId).orElseThrow();
        deanId = withRole(DEAN, faculty, RoleType.DEAN, faculty);
        withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        withRole(OTHER_DEAN, other, RoleType.DEAN, other);
        withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        long deputyId = withRole(DEPUTY, department, RoleType.EMPLOYEE, department);
        jdbc.update("insert into role_delegations (delegator_id, delegate_id, organization_id, role_type, valid_from,"
            + " valid_to, reason) values (?, ?, ?, 'DEAN', ?, ?, 'Leave')", deanId, deputyId, facultyId,
            Date.valueOf(LocalDate.now().minusDays(1)), Date.valueOf(LocalDate.now().plusDays(7)));
    }

    @AfterEach
    void resetPeriod() {
        jdbc.update("update organizations set review_working_days = null where org_id = ?", facultyId);
    }

    @AfterAll
    void deleteUsers() {
        String ours = " (select user_id from users where email_address like 'ft.period.%')";
        jdbc.update("delete from role_delegations where delegate_id in" + ours);
        jdbc.update("delete from awards where user_id in" + ours);
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac1_1_aFacultyWithoutItsOwnPeriodAnswersTheDefault() {
        get(tokenOf(DEAN), facultyId).then().statusCode(HttpStatus.OK.value())
            .body("organizationId", equalTo((int) facultyId))
            .body(DAYS, nullValue())
            .body("effectiveWorkingDays", equalTo(DEFAULT_DAYS))
            .body("defaultWorkingDays", equalTo(DEFAULT_DAYS))
            .body("updatable", equalTo(true));
    }

    @Test
    void ac1_2_ac1_3_theDeanSetsAndResetsThePeriodAndEachChangeIsAudited() {
        String dean = tokenOf(DEAN);
        Instant before = Instant.now();

        put(dean, facultyId, FACULTY_DAYS).then().statusCode(HttpStatus.OK.value())
            .body(DAYS, equalTo(FACULTY_DAYS))
            .body("effectiveWorkingDays", equalTo(FACULTY_DAYS));
        put(dean, facultyId, null).then().statusCode(HttpStatus.OK.value())
            .body(DAYS, nullValue())
            .body("effectiveWorkingDays", equalTo(DEFAULT_DAYS));

        List<Map<String, Object>> rows = jdbc.queryForList("select user_id, old_values->>'workingDays' as old_days,"
            + " new_values->>'workingDays' as new_days, new_values->>'effectiveWorkingDays' as effective"
            + " from audit_logs where action_type = 'REVIEW_PERIOD_CHANGED' and entity_type = 'organizations'"
            + " and entity_id = ? and created_at >= ? order by log_id", facultyId, Timestamp.from(before));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("user_id", deanId).containsEntry("old_days", null)
            .containsEntry("new_days", "5").containsEntry("effective", "5");
        assertThat(rows.get(1)).containsEntry("old_days", "5").containsEntry("new_days", null)
            .containsEntry("effective", "3");
    }

    @Test
    void ac1_2_aDelegatedDeanChangesThePeriodOnBehalfOfTheDean() {
        Instant before = Instant.now();

        put(tokenOf(DEPUTY), facultyId, FACULTY_DAYS).then().statusCode(HttpStatus.OK.value());

        assertThat(jdbc.queryForObject("select (new_values->>'delegatorId')::bigint from audit_logs"
            + " where action_type = 'REVIEW_PERIOD_CHANGED' and entity_id = ? and created_at >= ?", Long.class,
            facultyId, Timestamp.from(before))).isEqualTo(deanId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "21", "5.5", "\"five\""})
    void ac1_4_aPeriodOutsideOneToTwentyOrNotAnIntegerIsRefused(String value) {
        as(tokenOf(DEAN)).contentType(ContentType.JSON).body("{\"workingDays\": " + value + "}")
            .put(path(facultyId)).then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("type", equalTo(PROBLEM + "validation-failed"))
            .body("errors.field", contains(DAYS))
            .body("errors.code", contains("range"));
    }

    @Test
    void ac1_5_aFacultySecretaryReadsThePeriodButCannotChangeIt() {
        String secretary = tokenOf(SECRETARY);

        get(secretary, facultyId).then().statusCode(HttpStatus.OK.value()).body("updatable", equalTo(false));
        put(secretary, facultyId, FACULTY_DAYS).then().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void ac1_5_aDeanOfAnotherFacultyFindsNothing() {
        String other = tokenOf(OTHER_DEAN);

        get(other, facultyId).then().statusCode(HttpStatus.NOT_FOUND.value());
        put(other, facultyId, FACULTY_DAYS).then().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void ac1_5_anEmployeeIsRefused() {
        String employee = tokenOf(EMPLOYEE);

        get(employee, facultyId).then().statusCode(HttpStatus.FORBIDDEN.value());
        put(employee, facultyId, FACULTY_DAYS).then().statusCode(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void ac1_5_aDepartmentOrTheUniversityIsNoFaculty() {
        String dean = tokenOf(DEAN);

        get(dean, departmentId).then().statusCode(HttpStatus.NOT_FOUND.value());
        put(dean, 1L, FACULTY_DAYS).then().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void ac1_6_ac1_7_aSubmissionIsDueByThePeriodInForceAndKeepsItsDeadlineAfterAChange() {
        String dean = tokenOf(DEAN);
        put(dean, facultyId, FACULTY_DAYS).then().statusCode(HttpStatus.OK.value());

        long awardId = AwardApi.submitted(tokenOf(EMPLOYEE), Map.of("titleUk", "Відзнака з терміном факультету",
            "awardDate", LocalDate.now(AwardApi.KYIV).minusMonths(2).toString()));
        Map<String, Object> request = jdbc.queryForMap("select submitted_at, deadline from award_requests"
            + " where award_id = ?", awardId);
        Instant submittedAt = ((Timestamp) request.get("submitted_at")).toInstant();
        Instant deadline = ((Timestamp) request.get("deadline")).toInstant();
        put(dean, facultyId, 2).then().statusCode(HttpStatus.OK.value());

        assertThat(deadline).isEqualTo(TestWorkflow.estimator().deadline(submittedAt, FACULTY_DAYS));
        assertThat(jdbc.queryForObject("select deadline from award_requests where award_id = ?", Timestamp.class,
            awardId).toInstant()).isEqualTo(deadline);
    }

    private static String path(long organizationId) {
        return "/api/v1/organizations/" + organizationId + "/review-period";
    }

    private static Response get(String token, long organizationId) {
        return as(token).get(path(organizationId));
    }

    private static Response put(String token, long organizationId, Integer days) {
        Map<String, Object> body = new HashMap<>();
        body.put(DAYS, days);
        return as(token).contentType(ContentType.JSON).body(body).put(path(organizationId));
    }
}

package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.DecisionRows;
import ua.edu.chnu.awards.support.RequestRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AwardStatusFT extends AbstractFunctionalTest {

    private static final String EMPLOYEE = "ft.status.employee@chnu.edu.ua";
    private static final String SECRETARY = "ft.status.secretary@chnu.edu.ua";
    private static final String DEAN = "ft.status.dean@chnu.edu.ua";
    private static final String OUTSIDER = "ft.status.outsider@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(EMPLOYEE, SECRETARY, DEAN, OUTSIDER);
    private static final String AWARDS = "/api/v1/awards";
    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
    private static final long OTHER_FACULTY_ID = 10L;
    private static final long MINISTRY_CATEGORY = 13L;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private String employee;
    private long employeeId;
    private long secretaryId;

    @BeforeAll
    void createUsers() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        employeeId = withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        secretaryId = withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        withRole(DEAN, faculty, RoleType.DEAN, faculty);
        Organization other = organizationRepository.findById(OTHER_FACULTY_ID).orElseThrow();
        withRole(OUTSIDER, other, RoleType.FACULTY_SECRETARY, other);
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from awards where user_id = ?", employeeId);
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @BeforeEach
    void setUp() {
        employee = tokenOf(EMPLOYEE);
        jdbc.update("update user_roles set valid_to = null where user_id = ?", secretaryId);
    }

    @Test
    void ac1_1_ac1_3_ac1_6_aSubmittedAwardIsDueInThreeDaysAndExpectedAfterItsWholePath() {
        long id = submitted("Diploma");

        JsonPath status = as(employee).get(AWARDS + "/" + id + "/status").then().statusCode(200)
            .body("status", equalTo("PENDING"))
            .body("requestStatus", equalTo("SUBMITTED"))
            .body("currentLevel", equalTo("FACULTY_SECRETARY"))
            .body("overdue", equalTo(false))
            .body("delay", nullValue())
            .body("decisions", empty())
            .body("path.level", contains("FACULTY_SECRETARY", "DEAN", "RECTOR_SECRETARY"))
            .body("path.state", contains("CURRENT", "UPCOMING", "UPCOMING"))
            .extract().jsonPath();

        Instant submittedAt = Instant.parse(status.getString("submittedAt"));
        Instant deadline = Instant.parse(status.getString("deadline"));
        assertThat(Duration.between(submittedAt, deadline)).isEqualTo(Duration.ofDays(3));
        assertThat(status.getString("estimatedCompletion"))
            .isEqualTo(LocalDate.ofInstant(deadline, KYIV).plusDays(6).toString());
    }

    @Test
    void ac1_7_ac1_8_draftsAndScopesFollowTheReadRuleOfTheAward() {
        long id = submitted("Certificate");
        long draft = as(employee).contentType(ContentType.JSON).body(Map.of("title", "Draft")).post(AWARDS)
            .then().statusCode(201).extract().jsonPath().getLong("id");
        String dean = tokenOf(DEAN);

        as(employee).get(AWARDS + "/" + draft + "/status").then().statusCode(200)
            .body("status", equalTo("DRAFT"))
            .body("requestStatus", nullValue())
            .body("path", empty());
        as(dean).get(AWARDS + "/" + id + "/status").then().statusCode(200)
            .body("requestStatus", equalTo("SUBMITTED"));
        as(dean).get(AWARDS + "/" + draft + "/status").then().statusCode(404);
        as(tokenOf(OUTSIDER)).get(AWARDS + "/" + id + "/status").then().statusCode(404);
        as(dean).get(AWARDS + "/999999999/status").then().statusCode(404);
        as(dean).get(AWARDS + "/abc/status").then().statusCode(400);
    }

    @Test
    void ac1_5_ac1_14_aReturnedRequestShowsTheCommentAndWaitsForItsOwner() {
        long id = submitted("Returned");
        long request = RequestRows.idOf(jdbc, id);
        DecisionRows.decision(jdbc, request, secretaryId).comments("Додайте номер наказу").insert();
        jdbc.update("update award_requests set status = 'RETURNED' where request_id = ?", request);

        as(employee).get(AWARDS + "/" + id + "/status").then().statusCode(200)
            .body("requestStatus", equalTo("RETURNED"))
            .body("estimatedCompletion", nullValue())
            .body("delay", nullValue())
            .body("decisions[0].decision", equalTo("RETURNED"))
            .body("decisions[0].reviewerId", equalTo((int) secretaryId))
            .body("decisions[0].reviewerName", equalTo("Test User"))
            .body("decisions[0].comments", equalTo("Додайте номер наказу"));
    }

    @Test
    void ac1_4_ac1_9_anOverdueReviewIsExplainedAndANewEstimateGiven() {
        long id = submitted("Late");
        jdbc.update("update award_requests set deadline = now() - interval '2 days' where award_id = ?", id);

        as(employee).get(AWARDS + "/" + id + "/status").then().statusCode(200)
            .body("overdue", equalTo(true))
            .body("delay.reason", equalTo("REVIEW_OVERDUE"))
            .body("delay.since", notNullValue())
            .body("estimatedCompletion", equalTo(LocalDate.now(KYIV).plusDays(9).toString()));
        as(employee).get(AWARDS + "/" + id).then().statusCode(200)
            .body("request.overdue", equalTo(true));
    }

    @Test
    void edge_aRequestWithoutStoredDeadlineAnswersTheDeadlineOfItsEstimate() {
        long id = submitted("Undated");
        jdbc.update("update award_requests set deadline = null, submitted_at = now() - interval '5 days' "
            + "where award_id = ?", id);

        as(employee).get(AWARDS + "/" + id + "/status").then().statusCode(200)
            .body("overdue", equalTo(true))
            .body("deadline", notNullValue());
        as(employee).get(AWARDS + "/" + id).then().statusCode(200)
            .body("request.overdue", equalTo(true))
            .body("request.deadline", notNullValue());
    }

    @Test
    void ac1_9_withoutAFacultySecretaryTheDelayNamesTheMissingReviewer() {
        long id = submitted("Unreviewed");
        jdbc.update("update user_roles set valid_to = current_date - 1, valid_from = current_date - 30 "
            + "where user_id = ?", secretaryId);

        as(employee).get(AWARDS + "/" + id + "/status").then().statusCode(200)
            .body("overdue", equalTo(false))
            .body("delay.reason", equalTo("NO_REVIEWER"))
            .body("delay.since", nullValue());
    }

    @Test
    void ac1_11_theListAndTheDetailCarryTheRequestTiming() {
        long id = submitted("Listed");

        as(employee).get(AWARDS + "/" + id).then().statusCode(200)
            .body("request.deadline", notNullValue())
            .body("request.estimatedCompletion", notNullValue())
            .body("request.overdue", equalTo(false));
        as(employee).queryParam("status", "PENDING").get(AWARDS).then().statusCode(200)
            .body("content.find { it.id == " + id + " }.request.estimatedCompletion", notNullValue());
    }

    @Test
    void ac1_10_theStatusAnswersWithinOneSecondAtThe95thPercentileAmong200Awards() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            long id = AwardRows.award(jdbc, employeeId).title("Load " + i).status("PENDING").insert();
            RequestRows.request(jdbc, id, employeeId).insert();
            ids.add(id);
        }
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            long id = ids.get(i * 5);
            long start = System.nanoTime();
            as(employee).get(AWARDS + "/" + id + "/status").then().statusCode(200);
            times.add(Duration.ofNanos(System.nanoTime() - start).toMillis());
        }

        times.sort(Long::compare);
        assertThat(times.get(37)).isLessThan(1000L);
    }

    private long submitted(String title) {
        long id = as(employee).contentType(ContentType.JSON).body(Map.of("title", title,
                "categoryId", MINISTRY_CATEGORY, "awardingOrganization", "МОН",
                "awardDate", LocalDate.now().minusYears(1).toString()))
            .post(AWARDS).then().statusCode(201).extract().jsonPath().getLong("id");
        as(employee).contentType(ContentType.JSON).body(Map.of("version", 1, "duplicateAcknowledged", true))
            .post(AWARDS + "/" + id + "/submit").then().statusCode(200);
        return id;
    }
}

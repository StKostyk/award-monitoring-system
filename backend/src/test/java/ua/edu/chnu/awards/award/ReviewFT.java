package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReviewFT extends AbstractFunctionalTest {

    private static final String SECRETARY = "ft.review.secretary@chnu.edu.ua";
    private static final String COLLEAGUE = "ft.review.secretary2@chnu.edu.ua";
    private static final String DEAN = "ft.review.dean@chnu.edu.ua";
    private static final String EMPLOYEE = "ft.review.employee@chnu.edu.ua";
    private static final String DEPUTY = "ft.review.deputy@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(SECRETARY, COLLEAGUE, DEAN, EMPLOYEE, DEPUTY);
    private static final String REVIEWS = "/api/v1/reviews";
    private static final String VERSION = "requestVersion";
    private static final String TYPE = "type";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final long FACULTY_ID = 10L;
    private static final long OTHER_FACULTY_ID = 9L;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long secretaryId;
    private long colleagueId;
    private long deanId;
    private long deputyId;

    @BeforeAll
    void createUsers() {
        Organization faculty = organizationRepository.findById(FACULTY_ID).orElseThrow();
        long departmentId = jdbc.queryForObject("select min(org_id) from organizations where parent_org_id = ?"
            + " and org_type = 'DEPARTMENT' and is_active", Long.class, FACULTY_ID);
        final Organization department = organizationRepository.findById(departmentId).orElseThrow();
        secretaryId = withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        colleagueId = withRole(COLLEAGUE, faculty, RoleType.FACULTY_SECRETARY, faculty);
        deanId = withRole(DEAN, faculty, RoleType.DEAN, faculty);
        withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        deputyId = withRole(DEPUTY, department, RoleType.EMPLOYEE, department);
        jdbc.update("insert into role_delegations (delegator_id, delegate_id, organization_id, role_type, valid_from,"
            + " valid_to, reason) values (?, ?, ?, 'FACULTY_SECRETARY', ?, ?, 'Leave')", secretaryId, deputyId,
            FACULTY_ID, Date.valueOf(LocalDate.now().minusDays(1)),
            Date.valueOf(LocalDate.now().plusDays(7)));
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from role_delegations where delegate_id in"
            + " (select user_id from users where email_address like 'ft.review.%')");
        jdbc.update("update award_requests set current_reviewer_id = null where current_reviewer_id in"
            + " (select user_id from users where email_address like 'ft.review.%')");
        jdbc.update("delete from awards where user_id in"
            + " (select user_id from users where email_address like 'ft.review.%')");
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac1_1_ac1_2_theQueueShowsTheOwnLevelAndLowerLevelsOnRequest() {
        long id = submitted("Грамота для черги");
        String secretary = tokenOf(SECRETARY);
        String dean = tokenOf(DEAN);

        as(secretary).get(REVIEWS).then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", hasItem((int) id))
            .body(item(id) + ".level", equalTo("FACULTY_SECRETARY"))
            .body(item(id) + ".status", equalTo("SUBMITTED"))
            .body(item(id) + ".reviewer", nullValue())
            .body(item(id) + ".delegatedFrom", nullValue())
            .body(item(id) + ".documentCount", equalTo(0));
        as(secretary).queryParam("assigned", "me").get(REVIEWS).then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", not(hasItem((int) id)));
        as(dean).get(REVIEWS).then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", not(hasItem((int) id)));
        as(dean).queryParam("level", "FACULTY_SECRETARY").queryParam("organizationId", FACULTY_ID).get(REVIEWS)
            .then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", hasItem((int) id));
    }

    @Test
    void ac1_2_ac1_3_filtersOutsideTheCallersReachAnswer400AndNonApprovers403() {
        String secretary = tokenOf(SECRETARY);
        as(secretary).queryParam("level", "DEAN").get(REVIEWS).then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("parameter", equalTo("level"));
        as(secretary).queryParam("organizationId", OTHER_FACULTY_ID).get(REVIEWS).then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("parameter", equalTo("organizationId"));
        as(tokenOf(EMPLOYEE)).get(REVIEWS).then().statusCode(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void ac1_1_aDelegateSeesTheQueueOnBehalfOfTheDelegator() {
        long id = submitted("Грамота для заступника");

        as(tokenOf(DEPUTY)).get(REVIEWS).then().statusCode(HttpStatus.OK.value())
            .body(item(id) + ".delegatedFrom.id", equalTo((int) secretaryId));
    }

    @Test
    void ac1_4_ac1_5_ac1_7_claimConflictStaleAndRelease() {
        long id = submitted("Грамота для взяття в роботу");
        String secretary = tokenOf(SECRETARY);
        String colleague = tokenOf(COLLEAGUE);
        long version = versionOf(secretary, id);

        claim(secretary, id, Map.of(VERSION, version)).then().statusCode(HttpStatus.OK.value())
            .body("status", equalTo("IN_REVIEW"))
            .body("reviewer.id", equalTo((int) secretaryId))
            .body(VERSION, equalTo((int) version + 1));
        claim(secretary, id, Map.of(VERSION, version)).then().statusCode(HttpStatus.OK.value())
            .body(VERSION, equalTo((int) version + 1));
        claim(colleague, id, Map.of(VERSION, version + 1)).then().statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(PROBLEM + "request-claimed"))
            .body("reviewer.id", equalTo((int) secretaryId));
        as(colleague).queryParam(VERSION, version + 1).delete(reviewer(id)).then()
            .statusCode(HttpStatus.CONFLICT.value());
        as(secretary).queryParam(VERSION, version).delete(reviewer(id)).then()
            .statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(PROBLEM + "request-stale"))
            .body("currentVersion", equalTo((int) version + 1));

        as(secretary).queryParam(VERSION, version + 1).delete(reviewer(id)).then()
            .statusCode(HttpStatus.NO_CONTENT.value());

        assertThat(jdbc.queryForMap("select status, current_reviewer_id from award_requests where award_id = ?",
            id)).containsEntry("status", "SUBMITTED").containsEntry("current_reviewer_id", null);
        assertThat(audited(id, "REVIEW_CLAIMED")).isEqualTo(1);
        assertThat(audited(id, "REVIEW_RELEASED")).isEqualTo(1);
    }

    @Test
    void ac1_6_theDeanTakesARequestOverFromASecretary() {
        long id = submitted("Грамота для перехоплення");
        String secretary = tokenOf(SECRETARY);
        long version = versionOf(secretary, id);
        claim(secretary, id, Map.of(VERSION, version)).then().statusCode(HttpStatus.OK.value());
        String dean = tokenOf(DEAN);

        claim(dean, id, Map.of(VERSION, version + 1)).then().statusCode(HttpStatus.CONFLICT.value());
        claim(tokenOf(COLLEAGUE), id, Map.of(VERSION, version + 1, "takeOver", true)).then()
            .statusCode(HttpStatus.CONFLICT.value());
        claim(dean, id, Map.of(VERSION, version + 1, "takeOver", true)).then().statusCode(HttpStatus.OK.value())
            .body("reviewer.id", equalTo((int) deanId));

        assertThat(jdbc.queryForObject("select (new_values->>'previousReviewerId')::bigint from audit_logs"
            + " where action_type = 'REVIEW_TAKEN_OVER' and entity_id = ?", Long.class, id)).isEqualTo(secretaryId);
    }

    @Test
    void ac1_8_theReviewerHandsTheRequestToAnEligibleColleague() {
        long id = submitted("Грамота для передачі");
        String secretary = tokenOf(SECRETARY);
        long version = versionOf(secretary, id);
        claim(secretary, id, Map.of(VERSION, version)).then().statusCode(HttpStatus.OK.value());

        as(secretary).get("/api/v1/awards/" + id + "/reviewers").then().statusCode(HttpStatus.OK.value())
            .body("id", hasItem((int) colleagueId))
            .body("id", not(hasItem((int) secretaryId)))
            .body("find { it.id == " + deputyId + " }.delegated", equalTo(true));
        claim(secretary, id, Map.of(VERSION, version + 1, "reviewerId", deanId)).then()
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body(TYPE, equalTo(PROBLEM + "reviewer-not-eligible"));
        claim(secretary, id, Map.of(VERSION, version + 1, "reviewerId", colleagueId)).then()
            .statusCode(HttpStatus.OK.value())
            .body("reviewer.id", equalTo((int) colleagueId));

        assertThat(audited(id, "REVIEW_HANDED_OVER")).isEqualTo(1);
    }

    @Test
    void ac1_9_unknownDraftAndOwnAwardsAnswer404() {
        String employee = tokenOf(EMPLOYEE);
        long draft = AwardApi.complete(employee, Map.of("titleUk", "Чернетка", "awardDate", awardDate()));
        String secretary = tokenOf(SECRETARY);

        claim(secretary, draft, Map.of(VERSION, 0)).then().statusCode(HttpStatus.NOT_FOUND.value());
        claim(secretary, Long.MAX_VALUE, Map.of(VERSION, 0)).then().statusCode(HttpStatus.NOT_FOUND.value());
        as(secretary).get("/api/v1/awards/" + draft + "/reviewers").then()
            .statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void ac1_5_twoClaimsAtOnceLeaveOneReviewer() throws Exception {
        long id = submitted("Грамота для одночасного взяття");
        String secretary = tokenOf(SECRETARY);
        String colleague = tokenOf(COLLEAGUE);
        long version = versionOf(secretary, id);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (String token : List.of(secretary, colleague)) {
            calls.add(() -> claim(token, id, Map.of(VERSION, version)).statusCode());
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : pool.invokeAll(calls)) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK.value(), HttpStatus.CONFLICT.value());
        assertThat(audited(id, "REVIEW_CLAIMED")).isEqualTo(1);
    }

    private long submitted(String title) {
        return AwardApi.submitted(tokenOf(EMPLOYEE), Map.of("titleUk", title, "awardDate", awardDate()));
    }

    private static String awardDate() {
        return LocalDate.now(AwardApi.KYIV).minusMonths(2).toString();
    }

    private static long versionOf(String token, long awardId) {
        return as(token).queryParam("size", 100).get(REVIEWS).jsonPath()
            .getLong(item(awardId) + "." + VERSION);
    }

    private static String item(long awardId) {
        return "content.find { it.awardId == " + awardId + " }";
    }

    private static Response claim(String token, long awardId, Map<String, Object> body) {
        return as(token).contentType(ContentType.JSON).body(body).put(reviewer(awardId));
    }

    private static String reviewer(long awardId) {
        return AwardApi.AWARDS + "/" + awardId + "/reviewer";
    }

    private int audited(long awardId, String action) {
        return jdbc.queryForObject("select count(*) from audit_logs where action_type = ? and entity_id = ?",
            Integer.class, action, awardId);
    }
}

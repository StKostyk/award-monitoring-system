package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

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
class WithdrawFT extends AbstractFunctionalTest {

    private static final String SECRETARY = "ft.withdraw.secretary@chnu.edu.ua";
    private static final String DEAN = "ft.withdraw.dean@chnu.edu.ua";
    private static final String OWNER = "ft.withdraw.owner@chnu.edu.ua";
    private static final String OTHER = "ft.withdraw.other@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(SECRETARY, DEAN, OWNER, OTHER);
    private static final String TYPE = "type";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final String REQUEST_STATUS = "request.status";
    private static final String REVIEWS = "/api/v1/reviews";
    private static final long FACULTY_ID = 10L;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    void createUsers() {
        Organization faculty = organizationRepository.findById(FACULTY_ID).orElseThrow();
        long departmentId = jdbc.queryForObject("select min(org_id) from organizations where parent_org_id = ?"
            + " and org_type = 'DEPARTMENT' and is_active", Long.class, FACULTY_ID);
        Organization department = organizationRepository.findById(departmentId).orElseThrow();
        withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        withRole(DEAN, faculty, RoleType.DEAN, faculty);
        withRole(OWNER, department, RoleType.EMPLOYEE, department);
        withRole(OTHER, department, RoleType.EMPLOYEE, department);
    }

    @AfterAll
    void deleteUsers() {
        String ours = " (select user_id from users where email_address like 'ft.withdraw.%')";
        jdbc.update("update award_requests set current_reviewer_id = null where current_reviewer_id in" + ours);
        jdbc.update("delete from review_decisions where reviewer_id in" + ours);
        jdbc.update("delete from awards where user_id in" + ours);
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac3_1_ac3_3_aWithdrawnAwardLeavesTheQueueAndIsSubmittedAgainFromTheStart() {
        long id = submitted("Грамота для відкликання");
        String owner = tokenOf(OWNER);
        long requestId = requestId(id);

        withdraw(owner, id, AwardApi.version(owner, id) - 1).then().statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(PROBLEM + "award-stale"));
        withdraw(owner, id, AwardApi.version(owner, id)).then().statusCode(HttpStatus.OK.value())
            .body("status", equalTo("DRAFT"))
            .body(REQUEST_STATUS, equalTo("WITHDRAWN"));

        assertThat(jdbc.queryForMap("select status, deadline from award_requests where award_id = ?", id))
            .containsEntry("status", "WITHDRAWN").containsEntry("deadline", null);
        assertThat(audited(id)).isEqualTo(1);
        as(tokenOf(SECRETARY)).queryParam("size", 100).get(REVIEWS).then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", not(hasItem((int) id)));
        withdraw(owner, id, AwardApi.version(owner, id)).then().statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(PROBLEM + "award-not-pending"));

        AwardApi.submit(owner, id, AwardApi.version(owner, id), true).then().statusCode(HttpStatus.OK.value())
            .body(REQUEST_STATUS, equalTo("SUBMITTED"))
            .body("request.currentLevel", equalTo("FACULTY_SECRETARY"));
        assertThat(requestId(id)).isEqualTo(requestId);
    }

    @Test
    void ac3_2_aClaimedRequestOrSomeoneElsesAwardIsNotWithdrawn() {
        long id = submitted("Грамота, взята в роботу");
        String owner = tokenOf(OWNER);

        withdraw(tokenOf(OTHER), id, AwardApi.version(owner, id)).then().statusCode(HttpStatus.NOT_FOUND.value());
        claim(tokenOf(SECRETARY), id).then().statusCode(HttpStatus.OK.value());
        withdraw(owner, id, AwardApi.version(owner, id)).then().statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(PROBLEM + "request-claimed"));
        assertThat(audited(id)).isZero();
    }

    @Test
    void ac3_4_ac3_5_aReturnedAwardKeepsItsRequestAndGoesBackToTheReturningLevel() {
        long id = submitted("Грамота для повторного подання");
        String owner = tokenOf(OWNER);
        decide(tokenOf(SECRETARY), id, "ESCALATE", "Потрібне рішення декана");
        decide(tokenOf(DEAN), id, "RETURN", "Додайте номер наказу");

        as(owner).get(AwardApi.AWARDS + "/" + id).then().statusCode(HttpStatus.OK.value())
            .body(REQUEST_STATUS, equalTo("RETURNED"))
            .body("request.returnComment", equalTo("Додайте номер наказу"));
        as(owner).delete(AwardApi.AWARDS + "/" + id).then().statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(PROBLEM + "award-has-request"));

        AwardApi.submit(owner, id, AwardApi.version(owner, id), true).then().statusCode(HttpStatus.OK.value())
            .body(REQUEST_STATUS, equalTo("SUBMITTED"))
            .body("request.currentLevel", equalTo("DEAN"))
            .body("request.returnComment", equalTo(null));
        as(owner).get(AwardApi.AWARDS + "/" + id + "/status").then().statusCode(HttpStatus.OK.value())
            .body("path.state", contains("DONE", "CURRENT", "UPCOMING"))
            .body("decisions.decision", contains("ESCALATED", "RETURNED"));
        as(tokenOf(DEAN)).queryParam("size", 100).get(REVIEWS).then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", hasItem((int) id));
    }

    @Test
    void ac3_2_aWithdrawalAndAClaimAtOnceLeaveOneWinner() throws Exception {
        long id = submitted("Грамота для одночасного відкликання");
        String owner = tokenOf(OWNER);
        String secretary = tokenOf(SECRETARY);
        long version = AwardApi.version(owner, id);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> calls = List.of(() -> withdraw(owner, id, version).statusCode(),
            () -> claim(secretary, id).statusCode());
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : pool.invokeAll(calls)) {
            statuses.add(result.get());
        }
        pool.shutdown();

        String status = jdbc.queryForObject("select status from award_requests where award_id = ?", String.class, id);
        if ("WITHDRAWN".equals(status)) {
            assertThat(statuses).containsExactly(HttpStatus.OK.value(), HttpStatus.NOT_FOUND.value());
            assertThat(audited(id)).isEqualTo(1);
        } else {
            assertThat(status).isEqualTo("IN_REVIEW");
            assertThat(statuses).containsExactly(HttpStatus.CONFLICT.value(), HttpStatus.OK.value());
            assertThat(audited(id)).isZero();
        }
    }

    private long submitted(String title) {
        return AwardApi.submitted(tokenOf(OWNER), Map.of("titleUk", title,
            "awardDate", LocalDate.now(AwardApi.KYIV).minusMonths(2).toString()));
    }

    private long requestId(long awardId) {
        return jdbc.queryForObject("select request_id from award_requests where award_id = ?", Long.class, awardId);
    }

    private long requestVersion(long awardId) {
        return jdbc.queryForObject("select version from award_requests where award_id = ?", Long.class, awardId);
    }

    private static Response withdraw(String token, long awardId, long version) {
        return as(token).contentType(ContentType.JSON).body(Map.of("version", version))
            .post(AwardApi.AWARDS + "/" + awardId + "/withdraw");
    }

    private Response claim(String token, long awardId) {
        return as(token).contentType(ContentType.JSON).body(Map.of("requestVersion", requestVersion(awardId)))
            .put(AwardApi.AWARDS + "/" + awardId + "/reviewer");
    }

    private void decide(String token, long awardId, String decision, String comment) {
        as(token).contentType(ContentType.JSON)
            .body(Map.of("decision", decision, "requestVersion", requestVersion(awardId), "comment", comment))
            .post(AwardApi.AWARDS + "/" + awardId + "/decisions").then().statusCode(HttpStatus.OK.value());
    }

    private int audited(long awardId) {
        return jdbc.queryForObject("select count(*) from audit_logs where action_type = 'AWARD_WITHDRAWN'"
            + " and entity_id = ?", Integer.class, awardId);
    }
}

package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DecisionFT extends AbstractFunctionalTest {

    private static final String SECRETARY = "ft.decision.secretary@chnu.edu.ua";
    private static final String DEAN = "ft.decision.dean@chnu.edu.ua";
    private static final String OFFICE = "ft.decision.office@chnu.edu.ua";
    private static final String EMPLOYEE = "ft.decision.employee@chnu.edu.ua";
    private static final String AUTHOR = "ft.decision.author@chnu.edu.ua";
    private static final String DEPUTY = "ft.decision.deputy@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(SECRETARY, DEAN, OFFICE, EMPLOYEE, AUTHOR, DEPUTY);
    private static final String VERSION = "requestVersion";
    private static final String DECISION = "decision";
    private static final String COMMENT = "comment";
    private static final String TYPE = "type";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final String REQUEST_STATUS = "requestStatus";
    private static final long FACULTY_ID = 10L;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long secretaryId;
    private long deanId;

    @BeforeAll
    void createUsers() {
        Organization faculty = organizationRepository.findById(FACULTY_ID).orElseThrow();
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        long departmentId = jdbc.queryForObject("select min(org_id) from organizations where parent_org_id = ?"
            + " and org_type = 'DEPARTMENT' and is_active", Long.class, FACULTY_ID);
        final Organization department = organizationRepository.findById(departmentId).orElseThrow();
        secretaryId = withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        deanId = withRole(DEAN, faculty, RoleType.DEAN, faculty);
        withRole(OFFICE, university, RoleType.RECTOR_SECRETARY, university);
        withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        withRole(AUTHOR, department, RoleType.EMPLOYEE, department);
        long deputyId = withRole(DEPUTY, department, RoleType.EMPLOYEE, department);
        jdbc.update("insert into role_delegations (delegator_id, delegate_id, organization_id, role_type, valid_from,"
            + " valid_to, reason) values (?, ?, ?, 'FACULTY_SECRETARY', ?, ?, 'Leave')", secretaryId, deputyId,
            FACULTY_ID, Date.valueOf(LocalDate.now().minusDays(1)), Date.valueOf(LocalDate.now().plusDays(7)));
    }

    @AfterAll
    void deleteUsers() {
        String ours = " (select user_id from users where email_address like 'ft.decision.%')";
        jdbc.update("delete from role_delegations where delegate_id in" + ours);
        jdbc.update("update award_requests set current_reviewer_id = null where current_reviewer_id in" + ours);
        jdbc.update("delete from review_decisions where reviewer_id in" + ours);
        jdbc.update("delete from awards where user_id in" + ours);
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac2_1_ac2_2_ac2_6_aNationalAwardClimbsThreeLevelsToApproval() {
        long id = submitted(EMPLOYEE, "Відзнака МОН для трьох рівнів");

        decide(tokenOf(SECRETARY), id, "APPROVE", null).then().statusCode(HttpStatus.OK.value())
            .body("status", equalTo("PENDING"))
            .body(REQUEST_STATUS, equalTo("ESCALATED"))
            .body("level", equalTo("DEAN"));
        decide(tokenOf(DEAN), id, "APPROVE", "Погоджено").then().statusCode(HttpStatus.OK.value())
            .body("level", equalTo("RECTOR_SECRETARY"));
        String office = tokenOf(OFFICE);
        decide(office, id, Map.of(DECISION, "APPROVE", VERSION, version(id), "verified", true)).then()
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body(TYPE, equalTo(PROBLEM + "validation-failed"));
        decide(office, id, "APPROVE", null).then().statusCode(HttpStatus.OK.value())
            .body("status", equalTo("APPROVED"))
            .body(REQUEST_STATUS, equalTo("APPROVED"));

        assertThat(jdbc.queryForList("select level from review_decisions d join award_requests r"
            + " on r.request_id = d.request_id where r.award_id = ? order by d.decided_at", String.class, id))
            .containsExactly("FACULTY_SECRETARY", "DEAN", "RECTOR_SECRETARY");
        assertThat(jdbc.queryForMap("select status, completed_at is not null as done, current_reviewer_id"
            + " from award_requests where award_id = ?", id)).containsEntry("status", "APPROVED")
            .containsEntry("done", true);
        assertThat(audited(id, "REVIEW_DECISION")).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from award_versions where award_id = ? and action = 'DECIDED'",
            Integer.class, id)).isEqualTo(1);
        assertThat(mailpit.latestTextTo(EMPLOYEE, "Нагороду затверджено")).contains("/awards/" + id);
        assertThat(mailpit.latestTextTo(EMPLOYEE, "Нагороду передано далі", 2)).contains("секретарю ректора");
        decide(office, id, "REJECT", "Пізно").then().statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(PROBLEM + "request-closed"));
    }

    @Test
    void ac2_3_ac2_4_ac2_10_returnAndRejectNeedACommentAndReachTheOwner() {
        long returned = submitted(AUTHOR, "Грамота для повернення");
        String secretary = tokenOf(SECRETARY);

        decide(secretary, returned, "RETURN", " ").then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors.field", hasItem(COMMENT));
        decide(secretary, returned, "RETURN", "Додайте номер наказу").then().statusCode(HttpStatus.OK.value())
            .body("status", equalTo("DRAFT"))
            .body(REQUEST_STATUS, equalTo("RETURNED"));
        assertThat(jdbc.queryForMap("select current_reviewer_id, deadline from award_requests where award_id = ?",
            returned)).containsEntry("current_reviewer_id", null).containsEntry("deadline", null);
        assertThat(mailpit.latestTextTo(AUTHOR, "повернуто на доопрацювання"))
            .contains("Коментар: Додайте номер наказу");
        as(tokenOf(AUTHOR)).get(AwardApi.AWARDS + "/" + returned + "/status").then()
            .statusCode(HttpStatus.OK.value())
            .body("status", equalTo("DRAFT"));

        long rejected = submitted(AUTHOR, "Грамота для відхилення");
        decide(secretary, rejected, "REJECT", null).then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value());
        decide(secretary, rejected, "REJECT", "Не відповідає положенню").then().statusCode(HttpStatus.OK.value())
            .body("status", equalTo("REJECTED"));
        assertThat(jdbc.queryForObject("select rejection_reason from award_requests where award_id = ?",
            String.class, rejected)).isEqualTo("Не відповідає положенню");
        assertThat(mailpit.latestTextTo(AUTHOR, "Нагороду відхилено")).contains("Не відповідає положенню");
    }

    @Test
    void ac2_5_ac2_12_anEscalationShowsOnTheStatusPage() {
        long id = submitted(EMPLOYEE, "Відзнака для передачі декану");

        decide(tokenOf(SECRETARY), id, "ESCALATE", "Потрібне рішення декана").then()
            .statusCode(HttpStatus.OK.value())
            .body(REQUEST_STATUS, equalTo("ESCALATED"))
            .body("level", equalTo("DEAN"));

        as(tokenOf(EMPLOYEE)).get(AwardApi.AWARDS + "/" + id + "/status").then().statusCode(HttpStatus.OK.value())
            .body("currentLevel", equalTo("DEAN"))
            .body("path.state", contains("DONE", "CURRENT", "UPCOMING"))
            .body("decisions[0].decision", equalTo("ESCALATED"))
            .body("decisions[0].reviewerId", equalTo((int) secretaryId))
            .body("decisions[0].comments", equalTo("Потрібне рішення декана"));
    }

    @Test
    void ac2_7_aDelegateDecidesOnBehalfOfTheDelegator() {
        long id = submitted(EMPLOYEE, "Відзнака для заступника");

        decide(tokenOf(DEPUTY), id, "APPROVE", null).then().statusCode(HttpStatus.OK.value());

        assertThat(jdbc.queryForObject("select d.delegator_id from review_decisions d join award_requests r"
            + " on r.request_id = d.request_id where r.award_id = ?", Long.class, id)).isEqualTo(secretaryId);
        as(tokenOf(EMPLOYEE)).get(AwardApi.AWARDS + "/" + id + "/status").then().statusCode(HttpStatus.OK.value())
            .body("decisions[0].delegatorId", equalTo((int) secretaryId));
        assertThat(jdbc.queryForObject("select (new_values->>'delegatorId')::bigint from audit_logs"
            + " where action_type = 'REVIEW_DECISION' and entity_id = ?", Long.class, id)).isEqualTo(secretaryId);
    }

    @Test
    void ac2_8_conflictsUnknownDecisionsAndNonReviewersAreRefused() {
        long id = submitted(EMPLOYEE, "Відзнака для конфліктів");
        String dean = tokenOf(DEAN);
        long version = version(id);
        as(dean).contentType(ContentType.JSON).body(Map.of(VERSION, version))
            .put(AwardApi.AWARDS + "/" + id + "/reviewer").then().statusCode(HttpStatus.OK.value());

        decide(tokenOf(SECRETARY), id, "APPROVE", null).then().statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(PROBLEM + "request-claimed"))
            .body("reviewer.id", equalTo((int) deanId));
        decide(dean, id, Map.of(DECISION, "APPROVE", VERSION, version)).then()
            .statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(PROBLEM + "request-stale"));
        decide(dean, id, Map.of(DECISION, "POSTPONE", VERSION, version + 1)).then()
            .statusCode(HttpStatus.BAD_REQUEST.value());
        decide(tokenOf(EMPLOYEE), id, "APPROVE", null).then().statusCode(HttpStatus.FORBIDDEN.value());
        decide(dean, Long.MAX_VALUE, Map.of(DECISION, "APPROVE", VERSION, 0)).then()
            .statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void ac2_8_aReturnedAwardIsNoLongerReviewableAndARepeatedDecisionIsClosed() {
        long returned = submitted(EMPLOYEE, "Відзнака після повернення");
        String secretary = tokenOf(SECRETARY);
        decide(secretary, returned, "RETURN", "Додайте скан").then().statusCode(HttpStatus.OK.value());

        decide(secretary, returned, "APPROVE", null).then().statusCode(HttpStatus.NOT_FOUND.value());

        long rejected = submitted(EMPLOYEE, "Відзнака після відхилення");
        long stale = version(rejected);
        decide(secretary, rejected, "REJECT", "Не стосується університету").then().statusCode(HttpStatus.OK.value());
        decide(secretary, rejected, Map.of(DECISION, "REJECT", COMMENT, "Повторно", VERSION, stale)).then()
            .statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(PROBLEM + "request-closed"));
    }

    private long submitted(String owner, String title) {
        return AwardApi.submitted(tokenOf(owner), Map.of("titleUk", title,
            "awardDate", LocalDate.now(AwardApi.KYIV).minusMonths(2).toString()));
    }

    private long version(long awardId) {
        return jdbc.queryForObject("select version from award_requests where award_id = ?", Long.class, awardId);
    }

    private Response decide(String token, long awardId, String decision, String comment) {
        Map<String, Object> body = new HashMap<>(Map.of(DECISION, decision, VERSION, version(awardId)));
        if (comment != null) {
            body.put(COMMENT, comment);
        }
        return decide(token, awardId, body);
    }

    private static Response decide(String token, long awardId, Map<String, Object> body) {
        return as(token).contentType(ContentType.JSON).body(body)
            .post(AwardApi.AWARDS + "/" + awardId + "/decisions");
    }

    private int audited(long awardId, String action) {
        return jdbc.queryForObject("select count(*) from audit_logs where action_type = ? and entity_id = ?",
            Integer.class, action, awardId);
    }
}

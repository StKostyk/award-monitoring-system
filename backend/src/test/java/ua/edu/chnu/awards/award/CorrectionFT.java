package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
class CorrectionFT extends AbstractFunctionalTest {

    private static final String SECRETARY = "ft.correct.secretary@chnu.edu.ua";
    private static final String COLLEAGUE = "ft.correct.colleague@chnu.edu.ua";
    private static final String DEAN = "ft.correct.dean@chnu.edu.ua";
    private static final String OWNER = "ft.correct.owner@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(SECRETARY, COLLEAGUE, DEAN, OWNER);
    private static final String TYPE = "type";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final String SUBJECT = "Рецензент виправив нагороду";
    private static final long FACULTY_ID = 10L;
    private static final long UNIVERSITY_CATEGORY = 21L;
    private static final long NATIONAL_CATEGORY = AwardApi.MINISTRY_CATEGORY;
    private static final int REASON_MAX = 1000;

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
        final Organization department = organizationRepository.findById(departmentId).orElseThrow();
        withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        withRole(COLLEAGUE, faculty, RoleType.FACULTY_SECRETARY, faculty);
        withRole(DEAN, faculty, RoleType.DEAN, faculty);
        withRole(OWNER, department, RoleType.EMPLOYEE, department);
    }

    @AfterAll
    void deleteUsers() {
        String ours = " (select user_id from users where email_address like 'ft.correct.%')";
        jdbc.update("update award_requests set current_reviewer_id = null where current_reviewer_id in" + ours);
        jdbc.update("delete from review_decisions where reviewer_id in" + ours);
        jdbc.update("delete from awards where user_id in" + ours);
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac3_3_ac3_4_ac3_5_ac3_6_aCorrectionClaimsTheRequestRecordsAVersionAndTellsTheOwner() {
        long id = submitted("Грамота з помилкою в даті");
        final String owner = tokenOf(OWNER);
        String deadline = deadline(id);
        LocalDate date = LocalDate.now(AwardApi.KYIV).minusMonths(3);
        mailpit.clear();

        correct(tokenOf(SECRETARY), id, Map.of("awardDate", date.toString(), "categoryId", NATIONAL_CATEGORY,
            "externalUrl", "https://mon.gov.ua/nakaz"), "Дату взято з наказу").then()
            .statusCode(HttpStatus.OK.value())
            .body("award.awardDate", equalTo(date.toString()))
            .body("award.impactScore", equalTo(80))
            .body("award.request.status", equalTo("IN_REVIEW"))
            .body("award.request.currentLevel", equalTo("FACULTY_SECRETARY"))
            .body("requestVersion", equalTo((int) requestVersion(id)))
            .body("changedFields", contains("awardDate", "categoryId", "impactScore", "externalUrl"));

        assertThat(deadline(id)).isEqualTo(deadline);
        assertThat(jdbc.queryForObject("select u.email_address from award_requests r join users u"
            + " on u.user_id = r.current_reviewer_id where r.award_id = ?", String.class, id)).isEqualTo(SECRETARY);
        assertThat(audited(id, "REVIEW_CLAIMED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select new_values ->> 'reason' from audit_logs where action_type ="
            + " 'AWARD_CORRECTED' and entity_id = ?", String.class, id)).isEqualTo("Дату взято з наказу");
        as(owner).get(AwardApi.AWARDS + "/" + id + "/versions").then().statusCode(HttpStatus.OK.value())
            .body("content[0].action", equalTo("CORRECTED"))
            .body("content[0].comment", equalTo("Дату взято з наказу"))
            .body("content[0].actor.email", equalTo(SECRETARY))
            .body("content[0].changes.field", hasItem("awardDate"));
        as(tokenOf(DEAN)).get(AwardApi.AWARDS + "/" + id + "/versions").then().statusCode(HttpStatus.OK.value())
            .body("content[0].action", equalTo("CORRECTED"));
        assertThat(mailpit.latestTextTo(OWNER, SUBJECT)).contains("Причина: Дату взято з наказу")
            .contains("Reason: Дату взято з наказу").contains("Відзнака міністерства").contains("Ministry Recognition")
            .contains(date.toString()).contains("/awards/" + id);

        decide(tokenOf(SECRETARY), id, "APPROVE").then().statusCode(HttpStatus.OK.value())
            .body("requestStatus", equalTo("ESCALATED"));
    }

    @Test
    void ac3_3_theHolderCorrectsAgainWithoutASecondClaimAndNullClearsAnOptionalField() {
        long id = submitted("Грамота з посиланням");
        String secretary = tokenOf(SECRETARY);
        correct(secretary, id, Map.of("externalUrl", "https://chnu.edu.ua/a"), "Додано посилання").then()
            .statusCode(HttpStatus.OK.value());
        long before = requestVersion(id);
        Map<String, Object> cleared = new HashMap<>();
        cleared.put("externalUrl", null);

        correct(secretary, id, cleared, "Посилання недійсне").then().statusCode(HttpStatus.OK.value())
            .body("award.externalUrl", equalTo(null))
            .body("requestVersion", equalTo((int) before))
            .body("award.titleUk", equalTo("Грамота з посиланням"));
        assertThat(audited(id, "REVIEW_CLAIMED")).isEqualTo(1);
        assertThat(audited(id, "AWARD_CORRECTED")).isEqualTo(2);
    }

    @Test
    void ac3_7_invalidCorrectionsAreRefused() {
        long id = submitted("Грамота для перевірки помилок");
        String secretary = tokenOf(SECRETARY);
        Map<String, Object> cleared = new HashMap<>();
        cleared.put("awardingOrganization", null);

        correct(secretary, id, Map.of("titleUk", "Грамота для перевірки помилок"), "Без змін").then()
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value()).body(TYPE, equalTo(PROBLEM + "no-change"));
        correct(secretary, id, Map.of("titleUk", "Нова назва"), " ").then()
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value()).body("errors[0].field", equalTo("reason"));
        correct(secretary, id, Map.of("titleUk", "Нова назва"), "я".repeat(REASON_MAX + 1)).then()
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value()).body("errors[0].code", equalTo("too-long"));
        correct(secretary, id, Map.of("externalUrl", "ftp://chnu.edu.ua"), "Посилання").then()
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value()).body("errors[0].field", equalTo("externalUrl"));
        correct(secretary, id, cleared, "Організація").then()
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value()).body(TYPE, equalTo(PROBLEM + "award-incomplete"));
        as(secretary).contentType(ContentType.JSON).body(body(id, Map.of("titleUk", "Нова"), "Назва", -1, 0))
            .post(path(id)).then().statusCode(HttpStatus.CONFLICT.value()).body(TYPE, equalTo(PROBLEM + "award-stale"));
        as(secretary).contentType(ContentType.JSON).body(body(id, Map.of("titleUk", "Нова"), "Назва", 0, -1))
            .post(path(id)).then().statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(PROBLEM + "request-stale"));
        assertThat(audited(id, "AWARD_CORRECTED")).isZero();
        assertThat(audited(id, "REVIEW_CLAIMED")).isZero();
    }

    @Test
    void ac3_7_aRequestHeldByAColleagueOrAnAwardOutOfReachIsNotCorrected() {
        long id = submitted("Грамота в роботі колеги");
        claim(tokenOf(COLLEAGUE), id);

        correct(tokenOf(SECRETARY), id, Map.of("titleUk", "Інша назва"), "Назва").then()
            .statusCode(HttpStatus.CONFLICT.value()).body(TYPE, equalTo(PROBLEM + "request-claimed"))
            .body("reviewer.email", equalTo(COLLEAGUE));
        correct(tokenOf(OWNER), id, Map.of("titleUk", "Інша назва"), "Назва").then()
            .statusCode(HttpStatus.FORBIDDEN.value());
        correct(tokenOf(COLLEAGUE), 0L, Map.of("titleUk", "Інша назва"), "Назва").then()
            .statusCode(HttpStatus.NOT_FOUND.value());

        decide(tokenOf(COLLEAGUE), id, "REJECT").then().statusCode(HttpStatus.OK.value());
        correct(tokenOf(COLLEAGUE), id, Map.of("titleUk", "Інша назва"), "Назва").then()
            .statusCode(HttpStatus.NOT_FOUND.value());
        long draft = AwardApi.complete(tokenOf(OWNER), Map.of("titleUk", "Чернетка"));
        correct(tokenOf(SECRETARY), draft, Map.of("titleUk", "Інша назва"), "Назва").then()
            .statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void ac3_7_aCorrectionAndAWithdrawalAtOnceLeaveOneWinner() throws Exception {
        long id = submitted("Грамота для одночасного виправлення");
        String owner = tokenOf(OWNER);
        String secretary = tokenOf(SECRETARY);
        Map<String, Object> correction = body(id, Map.of("titleUk", "Виправлена назва"), "Назва", 0, 0);
        long version = AwardApi.version(owner, id);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> calls = List.of(
            () -> as(secretary).contentType(ContentType.JSON).body(correction).post(path(id)).statusCode(),
            () -> as(owner).contentType(ContentType.JSON).body(Map.of("version", version))
                .post(AwardApi.AWARDS + "/" + id + "/withdraw").statusCode());
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : pool.invokeAll(calls)) {
            statuses.add(result.get());
        }
        pool.shutdown();

        String status = jdbc.queryForObject("select status from award_requests where award_id = ?", String.class, id);
        if ("WITHDRAWN".equals(status)) {
            assertThat(statuses).containsExactly(HttpStatus.NOT_FOUND.value(), HttpStatus.OK.value());
            assertThat(audited(id, "AWARD_CORRECTED")).isZero();
        } else {
            assertThat(status).isEqualTo("IN_REVIEW");
            assertThat(statuses).containsExactly(HttpStatus.OK.value(), HttpStatus.CONFLICT.value());
            assertThat(audited(id, "AWARD_CORRECTED")).isEqualTo(1);
        }
    }

    private long submitted(String title) {
        return AwardApi.submitted(tokenOf(OWNER), Map.of("titleUk", title, "categoryId", UNIVERSITY_CATEGORY,
            "awardDate", LocalDate.now(AwardApi.KYIV).minusMonths(2).toString()));
    }

    private Response correct(String token, long awardId, Map<String, Object> fields, String reason) {
        return as(token).contentType(ContentType.JSON).body(body(awardId, fields, reason, 0, 0)).post(path(awardId));
    }

    private Map<String, Object> body(long awardId, Map<String, Object> fields, String reason, long versionShift,
                                     long requestShift) {
        Map<String, Object> body = new HashMap<>(fields);
        long version = awardId == 0L ? 1L : jdbc.queryForObject("select coalesce(max(version), 1) from awards"
            + " where award_id = ?", Long.class, awardId);
        long request = awardId == 0L ? 0L : jdbc.queryForObject("select coalesce(max(version), 0)"
            + " from award_requests where award_id = ?", Long.class, awardId);
        body.put("version", version + versionShift);
        body.put("requestVersion", request + requestShift);
        body.put("reason", reason);
        return body;
    }

    private static String path(long awardId) {
        return AwardApi.AWARDS + "/" + awardId + "/corrections";
    }

    private long requestVersion(long awardId) {
        return jdbc.queryForObject("select version from award_requests where award_id = ?", Long.class, awardId);
    }

    private String deadline(long awardId) {
        return jdbc.queryForObject("select deadline::text from award_requests where award_id = ?", String.class,
            awardId);
    }

    private void claim(String token, long awardId) {
        as(token).contentType(ContentType.JSON).body(Map.of("requestVersion", requestVersion(awardId)))
            .put(AwardApi.AWARDS + "/" + awardId + "/reviewer").then().statusCode(HttpStatus.OK.value());
    }

    private Response decide(String token, long awardId, String decision) {
        return as(token).contentType(ContentType.JSON)
            .body(Map.of("decision", decision, "requestVersion", requestVersion(awardId), "comment", "Рішення"))
            .post(AwardApi.AWARDS + "/" + awardId + "/decisions");
    }

    private int audited(long awardId, String action) {
        return jdbc.queryForObject("select count(*) from audit_logs where action_type = ? and entity_id = ?",
            Integer.class, action, awardId);
    }
}

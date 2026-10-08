package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

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
class BatchReviewFT extends AbstractFunctionalTest {

    private static final String SECRETARY = "ft.batch.secretary@chnu.edu.ua";
    private static final String OTHER = "ft.batch.other@chnu.edu.ua";
    private static final String OWNER = "ft.batch.owner@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(SECRETARY, OTHER, OWNER);
    private static final String DECISIONS = "/api/v1/reviews/decisions";
    private static final String TEMPLATES = "/api/v1/reviews/templates";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final long FACULTY_ID = 10L;
    private static final long MISSING_AWARD = 987_654_321L;
    private static final int TIMED_ITEMS = 20;
    private static final Duration TIME_LIMIT = Duration.ofSeconds(5);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long otherId;
    private String ownerToken;

    @BeforeAll
    void createUsers() {
        Organization faculty = organizationRepository.findById(FACULTY_ID).orElseThrow();
        long departmentId = jdbc.queryForObject("select min(org_id) from organizations where parent_org_id = ?"
            + " and org_type = 'DEPARTMENT' and is_active", Long.class, FACULTY_ID);
        Organization department = organizationRepository.findById(departmentId).orElseThrow();
        withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        otherId = withRole(OTHER, faculty, RoleType.FACULTY_SECRETARY, faculty);
        withRole(OWNER, department, RoleType.EMPLOYEE, department);
    }

    @AfterAll
    void deleteUsers() {
        String ours = " (select user_id from users where email_address like 'ft.batch.%')";
        jdbc.update("update award_requests set current_reviewer_id = null where current_reviewer_id in" + ours);
        jdbc.update("delete from review_decisions where reviewer_id in" + ours);
        jdbc.update("delete from awards where user_id in" + ours);
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac4_1_ac4_2_ac4_3_aMixedBatchDecidesWhatItCanAndReportsTheRest() {
        long first = submitted("Пакет: перша грамота");
        long claimed = submitted("Пакет: взята іншим");
        long stale = submitted("Пакет: застаріла версія");
        long last = submitted("Пакет: остання грамота");
        jdbc.update("update award_requests set current_reviewer_id = ? where award_id = ?", otherId, claimed);
        String comment = "Додайте скан сертифіката";
        int batchesBefore = batches();

        Response response = batch(tokenOf(SECRETARY), "RETURN", comment, List.of(item(first), item(claimed),
            Map.of("awardId", stale, "requestVersion", version(stale) + 1), Map.of("awardId", MISSING_AWARD,
                "requestVersion", 0), item(last)));

        assertThat(response.jsonPath().getList("awardId", Long.class))
            .containsExactly(first, claimed, stale, MISSING_AWARD, last);
        response.then().statusCode(HttpStatus.OK.value())
            .body("outcome", contains("DONE", "FAILED", "FAILED", "FAILED", "DONE"))
            .body("code", contains(null, "request-claimed", "request-stale", "not-found", null))
            .body("[0].status", equalTo("DRAFT"));

        assertThat(jdbc.queryForList("select award_id from award_requests where status = 'RETURNED' and award_id in"
            + " (?, ?, ?, ?)", Long.class, first, claimed, stale, last)).containsExactlyInAnyOrder(first, last);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'REVIEW_DECISION'"
            + " and entity_id in (?, ?)", Integer.class, first, last)).isEqualTo(2);
        assertThat(batches()).isEqualTo(batchesBefore + 1);
        assertThat(jdbc.queryForMap("select new_values->>'decision' as decision, new_values->>'items' as items,"
            + " new_values->>'done' as done, new_values->>'failed' as failed from audit_logs"
            + " where action_type = 'REVIEW_BATCH' order by log_id desc limit 1"))
            .containsEntry("decision", "RETURN").containsEntry("items", "5")
            .containsEntry("done", "2").containsEntry("failed", "3");
        assertThat(mailpit.latestTextTo(OWNER, "повернуто на доопрацювання")).contains("Коментар: " + comment);
    }

    @Test
    void ac4_2_invalidBatchesAreRefusedAsAWhole() {
        long id = submitted("Пакет: недійсний запит");
        String secretary = tokenOf(SECRETARY);

        batch(secretary, "APPROVE", null, List.of()).then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("type", equalTo(PROBLEM + "invalid-parameter"));
        batch(secretary, "APPROVE", null, List.of(item(id), item(id))).then()
            .statusCode(HttpStatus.BAD_REQUEST.value());
        batch(secretary, "REJECT", " ", List.of(item(id))).then()
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("type", equalTo(PROBLEM + "validation-failed"));
        batch(tokenOf(OWNER), "APPROVE", null, List.of(item(id))).then().statusCode(HttpStatus.FORBIDDEN.value());
        assertThat(jdbc.queryForObject("select status from award_requests where award_id = ?", String.class, id))
            .isEqualTo("SUBMITTED");
    }

    @Test
    void ac4_4_twentyItemsAreDecidedWithinFiveSeconds() {
        List<Map<String, Object>> items = new ArrayList<>();
        IntStream.range(0, TIMED_ITEMS).forEach(index -> items.add(item(submitted("Пакет: швидкість " + index))));
        String secretary = tokenOf(SECRETARY);

        long started = System.nanoTime();
        batch(secretary, "APPROVE", null, items).then().statusCode(HttpStatus.OK.value())
            .body("$", hasSize(TIMED_ITEMS))
            .body("outcome", everyItem(equalTo("DONE")));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(TIME_LIMIT);
    }

    @Test
    void ac4_5_templatesAreListedPerDecisionInTheCallersLanguage() {
        String secretary = tokenOf(SECRETARY);

        as(secretary).header("Accept-Language", "uk").queryParam("decision", "RETURN").get(TEMPLATES).then()
            .statusCode(HttpStatus.OK.value())
            .body("decision", everyItem(equalTo("RETURN")))
            .body("[0].title", equalTo("Немає скану сертифіката"));
        as(secretary).header("Accept-Language", "en").queryParam("decision", "RETURN").get(TEMPLATES).then()
            .statusCode(HttpStatus.OK.value())
            .body("[0].title", equalTo("Certificate scan missing"));
        assertThat(jdbc.queryForList("select decision || ':' || count(*) from review_templates where active"
            + " group by decision order by decision", String.class))
            .containsExactly("APPROVE:1", "ESCALATE:1", "REJECT:2", "RETURN:3");
    }

    private long submitted(String title) {
        if (ownerToken == null) {
            ownerToken = tokenOf(OWNER);
        }
        return AwardApi.submitted(ownerToken, Map.of("titleUk", title,
            "awardDate", LocalDate.now(AwardApi.KYIV).minusMonths(2).toString()));
    }

    private long version(long awardId) {
        return jdbc.queryForObject("select version from award_requests where award_id = ?", Long.class, awardId);
    }

    private Map<String, Object> item(long awardId) {
        return Map.of("awardId", awardId, "requestVersion", version(awardId));
    }

    private int batches() {
        return jdbc.queryForObject("select count(*) from audit_logs where action_type = 'REVIEW_BATCH'",
            Integer.class);
    }

    private static Response batch(String token, String decision, String comment, List<Map<String, Object>> items) {
        Map<String, Object> body = new HashMap<>();
        body.put("decision", decision);
        body.put("comment", comment);
        body.put("items", items);
        return as(token).contentType(ContentType.JSON).body(body).post(DECISIONS);
    }
}

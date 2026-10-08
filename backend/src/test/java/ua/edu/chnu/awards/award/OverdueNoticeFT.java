package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import ua.edu.chnu.awards.award.service.OverdueNotices;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OverdueNoticeFT extends AbstractFunctionalTest {

    private static final String SECRETARY = "ft.overdue.secretary@chnu.edu.ua";
    private static final String DEAN = "ft.overdue.dean@chnu.edu.ua";
    private static final String EMPLOYEE = "ft.overdue.employee@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(SECRETARY, DEAN, EMPLOYEE);
    private static final String SUBJECT = "Прострочені заявки: 1 / Overdue requests: 1";
    private static final String NOTICED = "select overdue_noticed_at, overdue_noticed_level, status, current_level"
        + " from award_requests where award_id = ?";

    @Autowired
    private OverdueNotices notices;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    void createUsers() {
        long facultyId = jdbc.queryForObject("select min(f.org_id) from organizations f where f.org_type = 'FACULTY'"
            + " and f.org_id not in (9, 10) and exists (select 1 from organizations d where d.parent_org_id = f.org_id"
            + " and d.org_type = 'DEPARTMENT' and d.is_active)", Long.class);
        long departmentId = jdbc.queryForObject("select min(org_id) from organizations where parent_org_id = ?"
            + " and org_type = 'DEPARTMENT' and is_active", Long.class, facultyId);
        Organization faculty = organizationRepository.findById(facultyId).orElseThrow();
        Organization department = organizationRepository.findById(departmentId).orElseThrow();
        withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        withRole(DEAN, faculty, RoleType.DEAN, faculty);
        withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
    }

    @AfterAll
    void deleteUsers() {
        String ours = " (select user_id from users where email_address like 'ft.overdue.%')";
        jdbc.update("update award_requests set current_reviewer_id = null where current_reviewer_id in" + ours);
        jdbc.update("delete from review_decisions where reviewer_id in" + ours);
        jdbc.update("delete from awards where user_id in" + ours);
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac2_1_ac2_2_ac2_9_anOverdueRequestIsMarkedOnceAndTheNextLevelGetsOneDigest() {
        mailpit.clear();
        long id = overdue("Грамота з простроченим розглядом");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        notices.run(now);

        Map<String, Object> row = jdbc.queryForMap(NOTICED, id);
        assertThat(((Timestamp) row.get("overdue_noticed_at")).toInstant()).isEqualTo(now);
        assertThat(row).containsEntry("overdue_noticed_level", "FACULTY_SECRETARY")
            .containsEntry("status", "SUBMITTED").containsEntry("current_level", "FACULTY_SECRETARY");
        assertThat(audited(id)).isEqualTo(1);
        String text = mailpit.textMentioning(DEAN, SUBJECT, "Грамота з простроченим розглядом");
        assertThat(text).contains("Грамота з простроченим розглядом", "не взято", "not taken", "/awards/" + id);
        assertThat(mailpit.messagesTo(SECRETARY, SUBJECT)).isEmpty();

        notices.run(now.plusSeconds(3600));

        assertThat(((Timestamp) jdbc.queryForMap(NOTICED, id).get("overdue_noticed_at")).toInstant()).isEqualTo(now);
        assertThat(audited(id)).isEqualTo(1);
    }

    @Test
    void ac2_3_movingToTheNextLevelClearsTheMarkWithTheNewDeadline() {
        long id = overdue("Грамота для передачі після нагадування");
        notices.run(Instant.now());

        as(tokenOf(SECRETARY)).contentType(ContentType.JSON)
            .body(Map.of("decision", "ESCALATE", "requestVersion", version(id), "comment", "Рішення декана"))
            .post(AwardApi.AWARDS + "/" + id + "/decisions").then().statusCode(HttpStatus.OK.value());

        Map<String, Object> row = jdbc.queryForMap(NOTICED, id);
        assertThat(row.get("overdue_noticed_at")).isNull();
        assertThat(row.get("overdue_noticed_level")).isNull();
        assertThat(jdbc.queryForObject("select deadline from award_requests where award_id = ?", Timestamp.class, id)
            .toInstant()).isAfter(Instant.now());
    }

    @Test
    void ac2_4_ac2_5_theQueueAndTheStatusPageCarryTheNotice() {
        long id = overdue("Грамота у фільтрі нижчого рівня");
        long fresh = AwardApi.submitted(tokenOf(EMPLOYEE), fields("Грамота без нагадування"));
        notices.run(Instant.now());

        as(tokenOf(DEAN)).queryParam("level", "FACULTY_SECRETARY").queryParam("noticed", true)
            .get("/api/v1/reviews").then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", hasItem((int) id))
            .body("content.awardId", not(hasItem((int) fresh)))
            .body("content.find { it.awardId == " + id + " }.overdueNoticedAt", not(nullValue()));
        as(tokenOf(EMPLOYEE)).get(AwardApi.AWARDS + "/" + id + "/status").then().statusCode(HttpStatus.OK.value())
            .body("delay.reason", equalTo("REVIEW_OVERDUE"))
            .body("delay.noticedAt", not(nullValue()));
    }

    @Test
    void ac2_6_decisionsAndQueueGaugesAppearAtThePrometheusEndpoint() {
        long id = overdue("Грамота для метрик");
        notices.run(Instant.now());
        as(tokenOf(SECRETARY)).contentType(ContentType.JSON)
            .body(Map.of("decision", "ESCALATE", "requestVersion", version(id), "comment", "Метрики"))
            .post(AwardApi.AWARDS + "/" + id + "/decisions").then().statusCode(HttpStatus.OK.value());

        RestAssured.get("/actuator/prometheus").then().statusCode(HttpStatus.OK.value())
            .body(containsString("awards_review_decisions_total{"))
            .body(containsString("on_time=\"false\""))
            .body(containsString("awards_review_decision_duration_seconds_count{"))
            .body(containsString("awards_review_open{"))
            .body(containsString("awards_review_overdue{"))
            .body(containsString("awards_review_overdue_notices_total"));
    }

    private long overdue(String title) {
        long id = AwardApi.submitted(tokenOf(EMPLOYEE), fields(title));
        jdbc.update("update award_requests set deadline = ? where award_id = ?",
            Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)), id);
        return id;
    }

    private static Map<String, Object> fields(String title) {
        return Map.of("titleUk", title, "awardDate", LocalDate.now(AwardApi.KYIV).minusMonths(2).toString());
    }

    private long version(long awardId) {
        return jdbc.queryForObject("select version from award_requests where award_id = ?", Long.class, awardId);
    }

    private int audited(long awardId) {
        return jdbc.queryForObject("select count(*) from audit_logs where action_type = 'REVIEW_OVERDUE_NOTICED'"
            + " and entity_id = ?", Integer.class, awardId);
    }
}

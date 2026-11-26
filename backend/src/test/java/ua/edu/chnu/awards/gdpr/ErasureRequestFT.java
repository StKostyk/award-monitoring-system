package ua.edu.chnu.awards.gdpr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.auth.service.LoginAttemptService;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.gdpr.service.DataExportService;
import ua.edu.chnu.awards.gdpr.service.ErasureJob;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ErasureRequestFT extends AbstractFunctionalTest {

    private static final String PERSON = "ft.erasure.person@chnu.edu.ua";
    private static final String HELD = "ft.erasure.held@chnu.edu.ua";
    private static final String GUESSER = "ft.erasure.guesser@chnu.edu.ua";
    private static final String OFFICER = "ft.erasure.officer@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(PERSON, HELD, GUESSER, OFFICER);
    private static final String REQUEST = "/api/v1/users/me/erasure-request";
    private static final String REQUESTED_SUBJECT = "Запит на видалення облікового запису / Account erasure requested";
    private static final String WARNING_SUBJECT =
        "Обліковий запис скоро буде видалено / Your account will be erased soon";
    private static final String CANCELLED_SUBJECT = "Видалення скасовано / Account erasure cancelled";
    private static final Duration GRACE_PERIOD = Duration.ofDays(30);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ErasureJob job;

    private long personId;
    private long heldId;

    @BeforeAll
    void createUsers() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        personId = withRole(PERSON, department, RoleType.EMPLOYEE, department);
        heldId = withRole(HELD, department, RoleType.EMPLOYEE, department);
        withRole(GUESSER, department, RoleType.EMPLOYEE, department);
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        long officerId = withRole(OFFICER, university, RoleType.GDPR_OFFICER, university);
        jdbc.update("insert into legal_holds (user_id, reason, placed_by) values (?, ?, ?)", heldId,
            "Pending court case 12/2026", officerId);
    }

    @AfterEach
    void cancelRequests() {
        jdbc.update("update erasure_requests set status = 'CANCELLED', cancelled_at = now()"
            + " where user_id = ? and status = 'PENDING'", personId);
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from erasure_requests where user_id in (?, ?)", personId, heldId);
        jdbc.update("delete from legal_holds where user_id = ?", heldId);
        redis.delete(DataExportService.THROTTLE_KEY_PREFIX + personId);
        ACCOUNTS.forEach(email -> redis.delete(List.of(LoginAttemptService.LOCK_KEY_PREFIX + email,
            LoginAttemptService.FAILURE_KEY_PREFIX + email)));
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac4_2_aRequestIsScheduledAuditedMailedAndShownInThePrivacyCentre() {
        mailpit.clear();
        String token = tokenOf(PERSON);
        Instant before = Instant.now();

        Response created = request(token, DEMO_PASSWORD);

        created.then().statusCode(HttpStatus.CREATED.value())
            .body("id", notNullValue())
            .body("postponed", equalTo(false));
        Instant scheduled = Instant.parse(created.jsonPath().getString("scheduledFor"));
        assertThat(scheduled).isBetween(before.plus(GRACE_PERIOD), Instant.now().plus(GRACE_PERIOD));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'ERASURE_REQUESTED'"
            + " and entity_type = 'GDPR' and entity_id = ? and new_values ->> 'requestId' = ?", Integer.class, personId,
            created.jsonPath().getString("id"))).isEqualTo(1);
        assertThat(mailpit.latestTextTo(PERSON, REQUESTED_SUBJECT)).contains("http://localhost:4200/privacy");

        as(token).get("/api/v1/users/me/privacy").then().statusCode(HttpStatus.OK.value())
            .body("erasure.id", equalTo(created.jsonPath().getInt("id")))
            .body("erasure.scheduledFor", notNullValue());
        request(token, DEMO_PASSWORD).then().statusCode(HttpStatus.CONFLICT.value())
            .body("type", endsWith("erasure-pending"));
    }

    @Test
    void ac4_4_thePendingRequestIsCancelledOnce() {
        mailpit.clear();
        String token = tokenOf(PERSON);
        request(token, DEMO_PASSWORD).then().statusCode(HttpStatus.CREATED.value());

        cancel(token).then().statusCode(HttpStatus.NO_CONTENT.value());
        cancel(token).then().statusCode(HttpStatus.CONFLICT.value()).body("type", endsWith("erasure-not-pending"));

        assertThat(jdbc.queryForObject("select status from erasure_requests where user_id = ?"
            + " order by request_id desc limit 1", String.class, personId)).isEqualTo("CANCELLED");
        assertThat(mailpit.latestTextTo(PERSON, CANCELLED_SUBJECT)).contains("The account stays active");
        as(token).get("/api/v1/users/me/privacy").then().body("erasure", nullValue());
    }

    @Test
    void ac4_3_anActiveLegalHoldRefusesAndTheOfficerSeesNoDate() {
        request(tokenOf(HELD), DEMO_PASSWORD).then().statusCode(HttpStatus.CONFLICT.value())
            .body("type", endsWith("legal-hold"))
            .body("detail", equalTo("The account cannot be erased now"));

        assertThat(jdbc.queryForObject("select count(*) from erasure_requests where user_id = ?", Integer.class,
            heldId)).isZero();
    }

    @Test
    void ac4_5_theOfficerSeesWhenThePendingErasureIsDue() {
        Response created = request(tokenOf(PERSON), DEMO_PASSWORD);
        created.then().statusCode(HttpStatus.CREATED.value());

        as(tokenOf(OFFICER)).get("/api/v1/users/" + personId + "/legal-holds").then()
            .statusCode(HttpStatus.OK.value())
            .body("erasureScheduledFor", equalTo(created.jsonPath().getString("scheduledFor")));
    }

    @Test
    void ac4_3_aWrongPasswordIsRefusedAndALockedAccountAnswers423() {
        String token = tokenOf(GUESSER);

        request(token, "wrong-password").then().statusCode(HttpStatus.FORBIDDEN.value())
            .body("type", endsWith("password-mismatch"));
        assertThat(redis.opsForValue().get(LoginAttemptService.FAILURE_KEY_PREFIX + GUESSER)).isEqualTo("1");

        redis.opsForValue().set(LoginAttemptService.LOCK_KEY_PREFIX + GUESSER, "1", Duration.ofMinutes(1));
        request(token, DEMO_PASSWORD).then().statusCode(HttpStatus.LOCKED.value())
            .body("type", endsWith("account-locked"));
        request(token, "").then().statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void ac4_6_theJobRemindsOnceWithinTheWarningPeriod() {
        mailpit.clear();
        request(tokenOf(PERSON), DEMO_PASSWORD).then().statusCode(HttpStatus.CREATED.value());
        Instant now = Instant.now();
        jdbc.update("update erasure_requests set requested_at = ?, scheduled_for = ?"
            + " where user_id = ? and status = 'PENDING'", utc(now.minus(Duration.ofDays(27))),
            utc(now.plus(Duration.ofDays(3))), personId);

        assertThat(job.run()).isEqualTo(1);
        assertThat(job.run()).isZero();

        assertThat(mailpit.latestTextTo(PERSON, WARNING_SUBJECT)).contains("http://localhost:4200/privacy");
        assertThat(jdbc.queryForObject("select warning_sent_at is not null from erasure_requests"
            + " where user_id = ? and status = 'PENDING'", Boolean.class, personId)).isTrue();
    }

    private static Response request(String token, String password) {
        return as(token).contentType(ContentType.JSON).body(Map.of("password", password)).post(REQUEST);
    }

    private static Response cancel(String token) {
        return as(token).post(REQUEST + "/cancel");
    }

    private static OffsetDateTime utc(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}

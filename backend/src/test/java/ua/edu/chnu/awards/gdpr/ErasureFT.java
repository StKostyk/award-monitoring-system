package ua.edu.chnu.awards.gdpr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.auth.service.LoginAttemptService;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.gdpr.service.ErasureJob;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ErasureFT extends AbstractFunctionalTest {

    private static final String PERSON = "ft.erased.person@chnu.edu.ua";
    private static final String HELD = "ft.erased.held@chnu.edu.ua";
    private static final String REVIEWER = "ft.erased.reviewer@chnu.edu.ua";
    private static final String COLLEAGUE = "ft.erased.colleague@chnu.edu.ua";
    private static final String OFFICER = "ft.erased.officer@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(PERSON, HELD, REVIEWER, COLLEAGUE, OFFICER);
    private static final String REQUEST = "/api/v1/users/me/erasure-request";
    private static final String COMPLETED_SUBJECT = "Обліковий запис видалено / Your account was erased";
    private static final String POSTPONED_SUBJECT = "Видалення відкладено / Account erasure postponed";

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
    private long officerId;

    @BeforeAll
    void createUsers() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        final Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        personId = withRole(PERSON, department, RoleType.EMPLOYEE, department);
        heldId = withRole(HELD, department, RoleType.EMPLOYEE, department);
        withRole(REVIEWER, department, RoleType.FACULTY_SECRETARY, faculty);
        withRole(COLLEAGUE, department, RoleType.EMPLOYEE, department);
        officerId = withRole(OFFICER, university, RoleType.GDPR_OFFICER, university);
    }

    @AfterAll
    void deleteUsers() {
        List<Long> people = new ArrayList<>(List.of(personId, heldId));
        userRepository.findByEmailAddressIgnoreCase(PERSON).map(User::getId).ifPresent(people::add);
        people.forEach(id -> {
            jdbc.update("delete from awards where user_id = ?", id);
            jdbc.update("delete from erasure_requests where user_id = ?", id);
            jdbc.update("delete from legal_holds where user_id = ?", id);
            ConsentRows.purge(jdbc, id);
        });
        ACCOUNTS.forEach(email -> redis.delete(List.of(LoginAttemptService.LOCK_KEY_PREFIX + email,
            LoginAttemptService.FAILURE_KEY_PREFIX + email)));
        userRepository.deleteAllById(people);
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac5_3_ac5_10_ac5_11_anErasedAccountIsSignedOutConfirmedHiddenAndItsAddressFree() {
        long award = AwardRows.award(jdbc, personId).title("Kept letter").status("APPROVED").insert();
        jdbc.update("update awards set visibility = 'UNIVERSITY' where award_id = ?", award);
        String token = tokenOf(PERSON);
        String colleague = tokenOf(COLLEAGUE);
        as(colleague).get("/api/v1/achievements/" + award).then().statusCode(HttpStatus.OK.value());
        request(token).then().statusCode(HttpStatus.CREATED.value());
        makeDue(personId);
        mailpit.clear();

        assertThat(job.run()).isOne();

        as(token).get("/api/v1/users/me/privacy").then().statusCode(HttpStatus.UNAUTHORIZED.value());
        assertThatThrownBy(() -> tokenOf(PERSON)).isNotNull();
        assertThat(mailpit.latestTextTo(PERSON, COMPLETED_SUBJECT)).contains("Hello Test,", "was erased");
        as(tokenOf(REVIEWER)).get("/api/v1/awards/" + award).then().statusCode(HttpStatus.OK.value())
            .body("owner.name", equalTo("Erased #" + personId));
        as(colleague).get("/api/v1/achievements/" + award).then().statusCode(HttpStatus.NOT_FOUND.value());
        RestAssured.given().get("/api/v1/public/achievements/" + award).then()
            .statusCode(HttpStatus.NOT_FOUND.value());
        RestAssured.given().contentType(ContentType.JSON).body(Map.of("email", PERSON, "password", DEMO_PASSWORD,
                "firstName", "Олена", "lastName", "Нова", "organizationId", TestUsers.DAI_DEPARTMENT_ID,
                "privacyNoticeVersion", "1.0"))
            .post("/api/v1/auth/register").then().statusCode(HttpStatus.CREATED.value());
        assertThat(jdbc.queryForObject("select count(*) from users where lower(email_address) = ? and user_id <> ?",
            Integer.class, PERSON, personId)).isOne();
    }

    @Test
    void ac5_2_aHoldPlacedDuringTheGracePeriodPostponesTheErasureWithOneMail() {
        request(tokenOf(HELD)).then().statusCode(HttpStatus.CREATED.value());
        jdbc.update("insert into legal_holds (user_id, reason, placed_by) values (?, ?, ?)", heldId,
            "Pending court case 14/2026", officerId);
        makeDue(heldId);
        mailpit.clear();

        assertThat(job.run()).isZero();
        assertThat(job.run()).isZero();

        assertThat(mailpit.latestTextTo(HELD, POSTPONED_SUBJECT)).contains("legal obligation")
            .doesNotContain("14/2026");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'ERASURE_POSTPONED' "
            + "and entity_id = ?", Integer.class, heldId)).isOne();
        assertThat(jdbc.queryForObject("select account_status from users where user_id = ?", String.class, heldId))
            .isEqualTo("ACTIVE");
    }

    private void makeDue(long userId) {
        jdbc.update("update erasure_requests set requested_at = now() - interval '31 days', "
            + "scheduled_for = now() - interval '1 minute' where user_id = ? and status = 'PENDING'", userId);
    }

    private static Response request(String token) {
        return as(token).contentType(ContentType.JSON).body(Map.of("password", DEMO_PASSWORD)).post(REQUEST);
    }
}

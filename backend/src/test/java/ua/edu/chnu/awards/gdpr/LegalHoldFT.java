package ua.edu.chnu.awards.gdpr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.gdpr.service.DataExportService;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class LegalHoldFT extends AbstractFunctionalTest {

    private static final String PERSON = "ft.hold.person@chnu.edu.ua";
    private static final String OFFICER = "ft.hold.officer@chnu.edu.ua";
    private static final String ADMIN = "ft.hold.admin@chnu.edu.ua";
    private static final String DEAN = "ft.hold.dean@chnu.edu.ua";
    private static final String ERASED = "ft.hold.erased@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(PERSON, OFFICER, ADMIN, DEAN, ERASED);
    private static final String REASON = "Pending court case 12/2026";
    private static final String LIFT_REASON = "Court case closed on appeal";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    private long personId;
    private long officerId;
    private long erasedId;

    @BeforeAll
    void createUsers() {
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        personId = withRole(PERSON, department, RoleType.EMPLOYEE, department);
        officerId = withRole(OFFICER, university, RoleType.GDPR_OFFICER, university);
        withRole(ADMIN, university, RoleType.SYSTEM_ADMIN, university);
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        withRole(DEAN, faculty, RoleType.DEAN, faculty);
        erasedId = withRole(ERASED, department, RoleType.EMPLOYEE, department);
        jdbc.update("update users set account_status = 'DELETED' where user_id = ?", erasedId);
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from legal_holds where user_id in (?, ?)", personId, erasedId);
        redis.delete(DataExportService.THROTTLE_KEY_PREFIX + personId);
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac3_2_theOfficerPlacesAHoldAuditedWithoutItsReason() {
        String token = tokenOf(OFFICER);

        as(token).get(holds(personId)).then().statusCode(HttpStatus.OK.value())
            .body("holds", hasSize(0))
            .body("erasureScheduledFor", nullValue());
        place(token, personId, "too short").then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors[0].field", equalTo("reason"))
            .body("errors[0].code", equalTo("too-short"));
        place(token, personId, REASON).then().statusCode(HttpStatus.CREATED.value())
            .body("id", notNullValue())
            .body("reason", equalTo(REASON))
            .body("placedAt", notNullValue())
            .body("placedBy.id", equalTo((int) officerId))
            .body("liftedAt", nullValue());
        place(token, personId, REASON).then().statusCode(HttpStatus.CONFLICT.value())
            .body("type", endsWith("legal-hold-active"));

        String details = jdbc.queryForObject("select new_values::text from audit_logs where action_type = ?"
            + " and entity_type = 'GDPR' and entity_id = ? and user_id = ?", String.class,
            "LEGAL_HOLD_PLACED", personId, officerId);
        assertThat(details).contains("\"reasonLength\": " + REASON.length()).doesNotContain(REASON);
    }

    @Test
    void ac3_2_erasedAndUnknownAccountsAreRefused() {
        String token = tokenOf(OFFICER);

        place(token, erasedId, REASON).then().statusCode(HttpStatus.CONFLICT.value())
            .body("type", endsWith("account-erased"));
        place(token, Long.MAX_VALUE, REASON).then().statusCode(HttpStatus.NOT_FOUND.value());
        as(token).get(holds(Long.MAX_VALUE)).then().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void ac3_3_theOfficerLiftsTheActiveHoldOnce() {
        String token = tokenOf(OFFICER);
        long holdId = activeHold();

        lift(token, erasedId, holdId, LIFT_REASON).then().statusCode(HttpStatus.NOT_FOUND.value())
            .body("type", endsWith("legal-hold-not-found"));
        lift(token, personId, holdId, LIFT_REASON).then().statusCode(HttpStatus.OK.value())
            .body("id", equalTo((int) holdId))
            .body("liftedAt", notNullValue())
            .body("liftedBy.id", equalTo((int) officerId))
            .body("liftReason", equalTo(LIFT_REASON));
        lift(token, personId, holdId, LIFT_REASON).then().statusCode(HttpStatus.CONFLICT.value())
            .body("type", endsWith("legal-hold-lifted"));

        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'LEGAL_HOLD_LIFTED'"
            + " and entity_id = ?", Integer.class, personId)).isEqualTo(1);
    }

    @Test
    void ac3_4_theHoldsAreListedNewestFirst() {
        String token = tokenOf(OFFICER);
        place(token, personId, REASON).then().statusCode(HttpStatus.CREATED.value());

        as(token).get(holds(personId)).then().statusCode(HttpStatus.OK.value())
            .body("holds", hasSize(2))
            .body("holds[0].liftedAt", nullValue())
            .body("holds[1].liftReason", equalTo(LIFT_REASON));
    }

    @Test
    void ac3_5_otherRolesAndAnonymousCallersAreRefused() {
        long holdId = activeHold();
        for (String email : List.of(ADMIN, DEAN, PERSON)) {
            String token = tokenOf(email);
            as(token).get(holds(personId)).then().statusCode(HttpStatus.FORBIDDEN.value());
            place(token, personId, REASON).then().statusCode(HttpStatus.FORBIDDEN.value());
            lift(token, personId, holdId, LIFT_REASON).then().statusCode(HttpStatus.FORBIDDEN.value());
        }
        RestAssured.given().get(holds(personId)).then().statusCode(HttpStatus.UNAUTHORIZED.value());
        RestAssured.given().contentType(ContentType.JSON).body(Map.of("reason", REASON)).post(holds(personId))
            .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void ac3_7_theExportListsTheHoldDatesOnly() {
        as(tokenOf(PERSON)).get("/api/v1/users/me/export").then().statusCode(HttpStatus.OK.value())
            .body("export_metadata.format_version", equalTo("1.7"))
            .body("dashboard_preferences.hidden_widgets", hasSize(0))
            .body("dashboard_preferences.updated_at", nullValue())
            .body("legal_holds", hasSize(2))
            .body("legal_holds[0].placed_at", notNullValue())
            .body("legal_holds[0].lifted_at", nullValue())
            .body("legal_holds[1].lifted_at", notNullValue())
            .body("legal_holds[1]", not(hasKey("reason")));
    }

    private long activeHold() {
        return Objects.requireNonNull(jdbc.queryForObject(
            "select hold_id from legal_holds where user_id = ? and lifted_at is null", Long.class, personId));
    }

    private static String holds(long userId) {
        return "/api/v1/users/" + userId + "/legal-holds";
    }

    private static Response place(String token, long userId, String reason) {
        return as(token).contentType(ContentType.JSON).body(Map.of("reason", reason)).post(holds(userId));
    }

    private static Response lift(String token, long userId, long holdId, String reason) {
        return as(token).contentType(ContentType.JSON).body(Map.of("reason", reason))
            .post(holds(userId) + "/" + holdId + "/lift");
    }
}

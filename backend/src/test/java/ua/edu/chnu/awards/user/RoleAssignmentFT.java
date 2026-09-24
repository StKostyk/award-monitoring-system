package ua.edu.chnu.awards.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AuthorizationCodeFlow;
import ua.edu.chnu.awards.support.Mailpit;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

/**
 * Every test owns the account it changes, so the order the methods run in cannot matter.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleAssignmentFT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Passw0rd-demo";
    private static final String ADMIN = "ft.roles.admin@chnu.edu.ua";
    private static final String RECTOR = "ft.roles.rector@chnu.edu.ua";
    private static final String DEAN = "ft.roles.dean@chnu.edu.ua";
    private static final String SECRETARY = "ft.roles.secretary@chnu.edu.ua";
    private static final String PROMOTED = "ft.roles.promoted@chnu.edu.ua";
    private static final String SCHEDULED = "ft.roles.scheduled@chnu.edu.ua";
    private static final String SENIOR = "ft.roles.senior@chnu.edu.ua";
    private static final String RULES = "ft.roles.rules@chnu.edu.ua";
    private static final String REVOKED = "ft.roles.revoked@chnu.edu.ua";
    private static final String NEWCOMER = "ft.roles.newcomer@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(ADMIN, RECTOR, DEAN, SECRETARY, PROMOTED, SCHEDULED,
        SENIOR, RULES, REVOKED, NEWCOMER);
    private static final long OTHER_DEPARTMENT_ID = 65L;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${mailpit.api-url}")
    private String mailpitApiUrl;

    private Mailpit mailpit;
    private long adminId;
    private long deanId;
    private long promotedId;
    private long scheduledId;
    private long seniorId;
    private long rulesId;
    private long revokedId;
    private long newcomerId;

    @BeforeAll
    void createUsers() {
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        adminId = withRole(ADMIN, university, RoleType.SYSTEM_ADMIN, university);
        withRole(RECTOR, university, RoleType.RECTOR, university);
        deanId = withRole(DEAN, faculty, RoleType.DEAN, faculty);
        withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        promotedId = withRole(PROMOTED, department, RoleType.EMPLOYEE, department);
        scheduledId = withRole(SCHEDULED, department, RoleType.EMPLOYEE, department);
        seniorId = withRole(SENIOR, faculty, RoleType.FACULTY_SECRETARY, faculty);
        rulesId = withRole(RULES, department, RoleType.EMPLOYEE, department);
        revokedId = withRole(REVOKED, department, RoleType.EMPLOYEE, department);
        newcomerId = userRepository.save(TestUsers.user(NEWCOMER, department)).getId();
    }

    @AfterAll
    void deleteUsers() {
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        mailpit = new Mailpit(mailpitApiUrl);
    }

    @Test
    void ac2_1_ac2_5_ac2_9_theDeanGrantsARoleThatReachesTheNextTokenAndTheHoldersInbox() {
        as(tokenOf(DEAN)).contentType(ContentType.JSON)
            .body(Map.of("role", "FACULTY_SECRETARY", "organizationId", TestUsers.FMI_FACULTY_ID,
                "validTo", LocalDate.now().plusMonths(1).toString()))
            .post("/api/v1/users/" + promotedId + "/roles").then().statusCode(201)
            .body("role", equalTo("FACULTY_SECRETARY"))
            .body("organization.id", equalTo((int) TestUsers.FMI_FACULTY_ID))
            .body("validFrom", equalTo(LocalDate.now().toString()))
            .body("validTo", equalTo(LocalDate.now().plusMonths(1).toString()));

        assertThat(mailpit.latestTextTo(PROMOTED, "Роль призначено")).contains("FACULTY_SECRETARY");
        assertThat(claims(tokenOf(PROMOTED)).get("role_scopes").toString())
            .contains("FACULTY_SECRETARY:" + TestUsers.FMI_FACULTY_ID);
    }

    @Test
    void ac2_1_ac2_2_ac2_3_theAssignmentRulesAreEnforcedEndToEnd() {
        String deanToken = tokenOf(DEAN);

        as(deanToken).contentType(ContentType.JSON)
            .body(Map.of("role", "RECTOR", "organizationId", TestUsers.UNIVERSITY_ID))
            .post("/api/v1/users/" + rulesId + "/roles").then().statusCode(403)
            .contentType("application/problem+json")
            .body("type", equalTo("urn:awards:problem:role-above-level"));

        as(deanToken).contentType(ContentType.JSON)
            .body(Map.of("role", "DEAN", "organizationId", TestUsers.DAI_DEPARTMENT_ID))
            .post("/api/v1/users/" + rulesId + "/roles").then().statusCode(422)
            .body("type", equalTo("urn:awards:problem:role-organization-mismatch"));

        as(deanToken).contentType(ContentType.JSON)
            .body(Map.of("role", "EMPLOYEE", "organizationId", TestUsers.DAI_DEPARTMENT_ID))
            .post("/api/v1/users/" + rulesId + "/roles").then().statusCode(409)
            .body("type", equalTo("urn:awards:problem:role-already-assigned"));

        as(tokenOf(SECRETARY)).contentType(ContentType.JSON)
            .body(Map.of("role", "DEAN", "organizationId", TestUsers.FMI_FACULTY_ID))
            .post("/api/v1/users/" + rulesId + "/roles").then().statusCode(403)
            .body("type", equalTo("urn:awards:problem:role-above-level"));

        as(tokenOf(RULES)).contentType(ContentType.JSON)
            .body(Map.of("role", "EMPLOYEE", "organizationId", TestUsers.DAI_DEPARTMENT_ID))
            .post("/api/v1/users/" + newcomerId + "/roles").then().statusCode(403)
            .body("detail", equalTo("permission user:manage or user:manage:scope is required"));
    }

    @Test
    void ac2_2_theRectorGrantsUniversityRolesButNotSystemOnes() {
        String rectorToken = tokenOf(RECTOR);

        as(rectorToken).contentType(ContentType.JSON)
            .body(Map.of("role", "RECTOR_SECRETARY", "organizationId", TestUsers.UNIVERSITY_ID))
            .post("/api/v1/users/" + seniorId + "/roles").then().statusCode(201)
            .body("role", equalTo("RECTOR_SECRETARY"));

        as(rectorToken).contentType(ContentType.JSON)
            .body(Map.of("role", "SYSTEM_ADMIN", "organizationId", TestUsers.UNIVERSITY_ID))
            .post("/api/v1/users/" + seniorId + "/roles").then().statusCode(403)
            .body("type", equalTo("urn:awards:problem:role-above-level"));

        as(tokenOf(ADMIN)).contentType(ContentType.JSON)
            .body(Map.of("role", "GDPR_OFFICER", "organizationId", TestUsers.UNIVERSITY_ID))
            .post("/api/v1/users/" + seniorId + "/roles").then().statusCode(201)
            .body("role", equalTo("GDPR_OFFICER"));
    }

    @Test
    void ac2_4_ac2_5_revocationEndsTheRoleYesterdayAndTheSessionsAtOnce() {
        String deanToken = tokenOf(DEAN);
        long roleId = as(deanToken).contentType(ContentType.JSON)
            .body(Map.of("role", "FACULTY_SECRETARY", "organizationId", TestUsers.FMI_FACULTY_ID))
            .post("/api/v1/users/" + revokedId + "/roles").then().statusCode(201)
            .extract().jsonPath().getLong("id");
        String holderToken = tokenOf(REVOKED);
        as(holderToken).get("/api/v1/users").then().statusCode(200);

        as(deanToken).delete("/api/v1/users/" + revokedId + "/roles/" + roleId).then().statusCode(204);

        as(holderToken).get("/api/v1/users").then().statusCode(401);
        as(deanToken).delete("/api/v1/users/" + revokedId + "/roles/" + roleId).then().statusCode(409)
            .body("type", equalTo("urn:awards:problem:role-already-revoked"));
        assertThat(jdbc.queryForObject("select valid_to from user_roles where user_role_id = ?",
            java.sql.Date.class, roleId).toLocalDate()).isEqualTo(LocalDate.now().minusDays(1));
        assertThat(jdbc.queryForList("select action_type from audit_logs where entity_type = 'AUTHORIZATION'"
            + " and user_id = ? and action_type in ('ROLE_ASSIGNED', 'ROLE_REVOKED') order by created_at",
            revokedId)).extracting(row -> row.get("action_type"))
            .containsExactly("ROLE_ASSIGNED", "ROLE_REVOKED");
        assertThat(mailpit.latestTextTo(REVOKED, "Роль відкликано")).contains("FACULTY_SECRETARY");
    }

    @Test
    void ac2_4_ac5_aRoleThatHasNotStartedYetCanStillBeTakenBack() {
        String deanToken = tokenOf(DEAN);
        long roleId = as(deanToken).contentType(ContentType.JSON)
            .body(Map.of("role", "FACULTY_SECRETARY", "organizationId", TestUsers.FMI_FACULTY_ID,
                "validFrom", LocalDate.now().plusMonths(1).toString()))
            .post("/api/v1/users/" + scheduledId + "/roles").then().statusCode(201)
            .extract().jsonPath().getLong("id");

        as(deanToken).delete("/api/v1/users/" + scheduledId + "/roles/" + roleId).then().statusCode(204);

        assertThat(jdbc.queryForObject("select valid_to from user_roles where user_role_id = ?",
            java.sql.Date.class, roleId).toLocalDate()).isEqualTo(LocalDate.now().plusMonths(1).minusDays(1));
    }

    @Test
    void ac2_4_nobodyCanTakeBackTheirOwnLastRoleAboveEmployee() {
        long deanRole = jdbc.queryForObject(
            "select user_role_id from user_roles where user_id = ? and role_type = 'DEAN'", Long.class, deanId);
        as(tokenOf(DEAN)).delete("/api/v1/users/" + deanId + "/roles/" + deanRole).then().statusCode(403)
            .body("type", equalTo("urn:awards:problem:role-above-level"));

        long adminRole = jdbc.queryForObject(
            "select user_role_id from user_roles where user_id = ? and role_type = 'SYSTEM_ADMIN'", Long.class,
            adminId);
        as(tokenOf(ADMIN)).delete("/api/v1/users/" + adminId + "/roles/" + adminRole).then().statusCode(403)
            .body("type", equalTo("urn:awards:problem:role-last-own"));
    }

    @Test
    void ac2_6_ac2_7_confirmingAMembershipGrantsTheFirstRoleAndMayCorrectTheDepartment() {
        String secretaryToken = tokenOf(SECRETARY);
        as(secretaryToken).queryParam("unconfirmed", "true").get("/api/v1/users").then().statusCode(200)
            .body("content.email", hasItem(NEWCOMER));
        assertThat(claims(tokenOf(NEWCOMER)).get("permissions")).isEqualTo(List.of());

        as(secretaryToken).contentType(ContentType.JSON)
            .body(Map.of("role", "EMPLOYEE", "organizationId", OTHER_DEPARTMENT_ID, "updateOrganization", true))
            .post("/api/v1/users/" + newcomerId + "/roles").then().statusCode(201)
            .body("organization.id", equalTo((int) OTHER_DEPARTMENT_ID));

        as(secretaryToken).get("/api/v1/users/" + newcomerId).then().statusCode(200)
            .body("membershipConfirmed", equalTo(true))
            .body("organization.id", equalTo((int) OTHER_DEPARTMENT_ID))
            .body("roles[0].role", equalTo("EMPLOYEE"));
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims(tokenOf(NEWCOMER)).get("permissions");
        assertThat(permissions).contains("award:create");
    }

    private long withRole(String email, Organization home, RoleType role, Organization scope) {
        User user = userRepository.save(TestUsers.user(email, home));
        userRoleRepository.save(TestUsers.role(user, role, scope, LocalDate.now().minusDays(1), null));
        return user.getId();
    }

    private static String tokenOf(String email) {
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow();
        return flow.exchange(flow.loginAndGetCode(email, PASSWORD)).jsonPath().getString("access_token");
    }

    private static RequestSpecification as(String token) {
        return RestAssured.given().header("Authorization", "Bearer " + token);
    }

    private static Map<String, Object> claims(String token) {
        return AuthorizationCodeFlow.claimsOf(token);
    }
}

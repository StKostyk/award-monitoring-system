package ua.edu.chnu.awards.delegation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import java.time.LocalDate;
import java.util.HashMap;
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
 * Each test lends through a dean of its own, because one person may not lend the same role twice over the
 * same days; the order the methods run in therefore cannot matter.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DelegationFT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Passw0rd-demo";
    private static final String ADMIN = "ft.deleg.admin@chnu.edu.ua";
    private static final String DEAN_CLAIMS = "ft.deleg.dean1@chnu.edu.ua";
    private static final String DEAN_LEND_ON = "ft.deleg.dean2@chnu.edu.ua";
    private static final String DEAN_EXPIRY = "ft.deleg.dean3@chnu.edu.ua";
    private static final String DEAN_RULES = "ft.deleg.dean4@chnu.edu.ua";
    private static final String DEAN_REVOKE = "ft.deleg.dean5@chnu.edu.ua";
    private static final String DEAN_AUTHORITY = "ft.deleg.dean6@chnu.edu.ua";
    private static final String SECRETARY = "ft.deleg.secretary@chnu.edu.ua";
    private static final String STAND_IN = "ft.deleg.standin@chnu.edu.ua";
    private static final String BORROWER = "ft.deleg.borrower@chnu.edu.ua";
    private static final String EMPLOYEE = "ft.deleg.employee@chnu.edu.ua";
    private static final String BENCH = "ft.deleg.bench@chnu.edu.ua";
    private static final String CASCADE = "ft.deleg.cascade@chnu.edu.ua";
    private static final String OUTSIDER = "ft.deleg.outsider@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(ADMIN, DEAN_CLAIMS, DEAN_LEND_ON, DEAN_EXPIRY,
        DEAN_RULES, DEAN_REVOKE, DEAN_AUTHORITY, SECRETARY, STAND_IN, BORROWER, EMPLOYEE, BENCH, CASCADE,
        OUTSIDER);
    private static final String DELEGATIONS = "/api/v1/delegations";
    private static final String PROBLEM_TYPE = "type";
    private static final String DEAN_ROLE = "DEAN";
    private static final long OTHER_FACULTY_ID = 10L;

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
    private long claimsDeanId;
    private long secretaryId;
    private long standInId;
    private long borrowerId;
    private long employeeId;
    private long benchId;
    private long cascadeId;
    private long outsiderId;

    @BeforeAll
    void createUsers() {
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        withRole(ADMIN, university, RoleType.SYSTEM_ADMIN, university);
        claimsDeanId = withRole(DEAN_CLAIMS, faculty, RoleType.DEAN, faculty);
        List.of(DEAN_LEND_ON, DEAN_EXPIRY, DEAN_RULES, DEAN_REVOKE, DEAN_AUTHORITY)
            .forEach(email -> withRole(email, faculty, RoleType.DEAN, faculty));
        secretaryId = withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        standInId = withRole(STAND_IN, faculty, RoleType.FACULTY_SECRETARY, faculty);
        Organization other = organizationRepository.findById(OTHER_FACULTY_ID).orElseThrow();
        outsiderId = withRole(OUTSIDER, other, RoleType.FACULTY_SECRETARY, other);
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        borrowerId = withRole(BORROWER, department, RoleType.EMPLOYEE, department);
        employeeId = withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        benchId = withRole(BENCH, department, RoleType.EMPLOYEE, department);
        cascadeId = withRole(CASCADE, department, RoleType.EMPLOYEE, department);
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from role_delegations where delegator_id in"
            + " (select user_id from users where email_address like 'ft.deleg.%')");
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        mailpit = new Mailpit(mailpitApiUrl);
    }

    @Test
    void ac3_1_ac3_2_ac3_5_theDeanLendsApprovalAuthorityAndItReachesTheDelegatesToken() {
        long id = as(tokenOf(DEAN_CLAIMS)).contentType(ContentType.JSON)
            .body(delegation(secretaryId, DEAN_ROLE, TestUsers.FMI_FACULTY_ID, LocalDate.now(),
                LocalDate.now().plusDays(14)))
            .post(DELEGATIONS).then().statusCode(201)
            .body("role", equalTo(DEAN_ROLE))
            .body("state", equalTo("active"))
            .body("delegate.id", equalTo((int) secretaryId))
            .body("reason", equalTo("Відпустка"))
            .extract().jsonPath().getLong("id");

        assertThat(mailpit.latestTextTo(SECRETARY, "Делеговано повноваження")).contains(DEAN_ROLE);
        Map<String, Object> claims = claims(tokenOf(SECRETARY));
        assertThat(claims.get("delegations").toString())
            .contains(DEAN_ROLE + ":" + TestUsers.FMI_FACULTY_ID + ":" + claimsDeanId);
        assertThat(claims.get("role_scopes").toString()).contains(DEAN_ROLE + ":" + TestUsers.FMI_FACULTY_ID);
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims.get("permissions");
        assertThat(permissions).contains("award:approve:level2", "award:read:faculty")
            .doesNotContain("user:manage", "user:read:all");
        as(tokenOf(SECRETARY)).get(DELEGATIONS).then().statusCode(200)
            .body("received.id", hasItem((int) id));
        assertThat(jdbc.queryForList("select action_type from audit_logs where entity_type = 'AUTHORIZATION'"
            + " and user_id = ? and action_type = 'DELEGATION_CREATED'", secretaryId)).hasSize(1);
    }

    @Test
    void ac3_2_borrowedAuthorityIsNeitherLentOnNorUsedToManageUsers() {
        as(tokenOf(DEAN_LEND_ON)).contentType(ContentType.JSON)
            .body(delegation(borrowerId, DEAN_ROLE, TestUsers.FMI_FACULTY_ID, LocalDate.now(),
                LocalDate.now().plusDays(3)))
            .post(DELEGATIONS).then().statusCode(201);

        String borrowerToken = tokenOf(BORROWER);
        as(borrowerToken).contentType(ContentType.JSON)
            .body(delegation(employeeId, DEAN_ROLE, TestUsers.FMI_FACULTY_ID, LocalDate.now(),
                LocalDate.now().plusDays(2)))
            .post(DELEGATIONS).then().statusCode(422)
            .body(PROBLEM_TYPE, equalTo("urn:awards:problem:delegation-not-holder"));

        as(borrowerToken).contentType(ContentType.JSON)
            .body(Map.of("role", "FACULTY_SECRETARY", "organizationId", TestUsers.FMI_FACULTY_ID))
            .post("/api/v1/users/" + employeeId + "/roles").then().statusCode(403)
            .body(PROBLEM_TYPE, equalTo("urn:awards:problem:access-denied"));
    }

    @Test
    void ac3_1_thePeriodTheDelegateAndTheCallerAreChecked() {
        String deanToken = tokenOf(DEAN_RULES);
        as(deanToken).contentType(ContentType.JSON)
            .body(delegation(employeeId, DEAN_ROLE, TestUsers.FMI_FACULTY_ID, LocalDate.now(),
                LocalDate.now().plusDays(100)))
            .post(DELEGATIONS).then().statusCode(422)
            .body(PROBLEM_TYPE, equalTo("urn:awards:problem:delegation-period"));

        as(deanToken).contentType(ContentType.JSON)
            .body(delegation(employeeId, "RECTOR", TestUsers.UNIVERSITY_ID, LocalDate.now(),
                LocalDate.now().plusDays(3)))
            .post(DELEGATIONS).then().statusCode(422)
            .body(PROBLEM_TYPE, equalTo("urn:awards:problem:delegation-not-holder"));

        as(deanToken).contentType(ContentType.JSON)
            .body(delegation(outsiderId, DEAN_ROLE, TestUsers.FMI_FACULTY_ID, LocalDate.now(),
                LocalDate.now().plusDays(3)))
            .post(DELEGATIONS).then().statusCode(404);

        as(tokenOf(EMPLOYEE)).contentType(ContentType.JSON)
            .body(delegation(employeeId, DEAN_ROLE, TestUsers.FMI_FACULTY_ID, LocalDate.now(),
                LocalDate.now().plusDays(3)))
            .post(DELEGATIONS).then().statusCode(403)
            .body(PROBLEM_TYPE, equalTo("urn:awards:problem:access-denied"));
    }

    @Test
    void ac3_1_thesameRoleIsNotLentTwiceOverThesamePeriod() {
        String deanToken = tokenOf(DEAN_RULES);
        as(deanToken).contentType(ContentType.JSON)
            .body(delegation(employeeId, DEAN_ROLE, TestUsers.FMI_FACULTY_ID, LocalDate.now().plusDays(40),
                LocalDate.now().plusDays(50)))
            .post(DELEGATIONS).then().statusCode(201)
            .body("state", equalTo("upcoming"));

        as(deanToken).contentType(ContentType.JSON)
            .body(delegation(secretaryId, DEAN_ROLE, TestUsers.FMI_FACULTY_ID, LocalDate.now().plusDays(45),
                LocalDate.now().plusDays(60)))
            .post(DELEGATIONS).then().statusCode(422)
            .body(PROBLEM_TYPE, equalTo("urn:awards:problem:delegation-overlap"));
    }

    @Test
    void ac3_3_ac3_5_anExpiredDelegationLeavesTheTokenButStaysOnThePage() {
        long id = as(tokenOf(DEAN_EXPIRY)).contentType(ContentType.JSON)
            .body(delegation(cascadeId, DEAN_ROLE, TestUsers.FMI_FACULTY_ID, LocalDate.now(),
                LocalDate.now()))
            .post(DELEGATIONS).then().statusCode(201).extract().jsonPath().getLong("id");
        jdbc.update("update role_delegations set valid_from = ?, valid_to = ? where delegation_id = ?",
            java.sql.Date.valueOf(LocalDate.now().minusDays(2)),
            java.sql.Date.valueOf(LocalDate.now().minusDays(1)), id);

        assertThat(claims(tokenOf(CASCADE)).get("delegations")).isEqualTo(List.of());
        as(tokenOf(CASCADE)).queryParam("state", "expired").get(DELEGATIONS).then().statusCode(200)
            .body("received.id", hasItem((int) id));
        as(tokenOf(CASCADE)).queryParam("state", "active").get(DELEGATIONS).then().statusCode(200)
            .body("received", hasSize(0));
    }

    @Test
    void ac3_4_ac3_5_revocationEndsTheBorrowedAuthorityAndTheDelegatesSessions() {
        String deanToken = tokenOf(DEAN_REVOKE);
        long id = as(deanToken).contentType(ContentType.JSON)
            .body(delegation(benchId, DEAN_ROLE, TestUsers.FMI_FACULTY_ID, LocalDate.now(),
                LocalDate.now().plusDays(5)))
            .post(DELEGATIONS).then().statusCode(201).extract().jsonPath().getLong("id");
        String benchToken = tokenOf(BENCH);
        as(benchToken).get(DELEGATIONS).then().statusCode(200);

        as(deanToken).delete(DELEGATIONS + "/" + id).then().statusCode(204);

        as(benchToken).get(DELEGATIONS).then().statusCode(401);
        as(deanToken).delete(DELEGATIONS + "/" + id).then().statusCode(409)
            .body(PROBLEM_TYPE, equalTo("urn:awards:problem:delegation-not-active"));
        assertThat(claims(tokenOf(BENCH)).get("delegations")).isEqualTo(List.of());
        assertThat(jdbc.queryForList("select action_type from audit_logs where entity_type = 'AUTHORIZATION'"
            + " and user_id = ? and action_type = 'DELEGATION_REVOKED'", benchId)).hasSize(1);
        assertThat(mailpit.latestTextTo(BENCH, "Делегування відкликано")).contains(DEAN_ROLE);
    }

    @Test
    void ac3_4_onlyTheDelegatorOrSomebodyAboveThemMayEndIt() {
        long id = as(tokenOf(DEAN_AUTHORITY)).contentType(ContentType.JSON)
            .body(delegation(employeeId, DEAN_ROLE, TestUsers.FMI_FACULTY_ID, LocalDate.now().plusDays(60),
                LocalDate.now().plusDays(70)))
            .post(DELEGATIONS).then().statusCode(201).extract().jsonPath().getLong("id");

        as(tokenOf(SECRETARY)).delete(DELEGATIONS + "/" + id).then().statusCode(404);
        as(tokenOf(ADMIN)).delete(DELEGATIONS + "/" + id).then().statusCode(204);
    }

    @Test
    void ac3_4_ac5_takingTheRoleBackTakesBackWhatItHadLent() {
        String adminToken = tokenOf(ADMIN);
        long standInRole = jdbc.queryForObject(
            "select user_role_id from user_roles where user_id = ? and role_type = 'FACULTY_SECRETARY'",
            Long.class, standInId);
        long id = as(tokenOf(STAND_IN)).contentType(ContentType.JSON)
            .body(delegation(employeeId, "FACULTY_SECRETARY", TestUsers.FMI_FACULTY_ID, LocalDate.now(),
                LocalDate.now().plusDays(5)))
            .post(DELEGATIONS).then().statusCode(201).extract().jsonPath().getLong("id");

        as(adminToken).delete("/api/v1/users/" + standInId + "/roles/" + standInRole).then().statusCode(204);

        as(adminToken).queryParam("delegatorId", standInId).get(DELEGATIONS).then().statusCode(200)
            .body("given.find { it.id == " + id + " }.state", equalTo("revoked"));
    }

    @Test
    void ac3_5_onlyAnAdministratorReadsSomebodyElsesPage() {
        as(tokenOf(SECRETARY)).queryParam("delegatorId", claimsDeanId).get(DELEGATIONS).then().statusCode(403)
            .body(PROBLEM_TYPE, equalTo("urn:awards:problem:access-denied"));
        as(tokenOf(ADMIN)).queryParam("delegatorId", claimsDeanId).get(DELEGATIONS).then().statusCode(200);
    }

    private static Map<String, Object> delegation(long delegateId, String role, long organizationId,
                                                  LocalDate from, LocalDate to) {
        Map<String, Object> body = new HashMap<>();
        body.put("delegateId", delegateId);
        body.put("role", role);
        body.put("organizationId", organizationId);
        body.put("validFrom", from.toString());
        body.put("validTo", to.toString());
        body.put("reason", "Відпустка");
        return body;
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

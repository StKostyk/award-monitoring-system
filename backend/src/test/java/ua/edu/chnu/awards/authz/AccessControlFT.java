package ua.edu.chnu.awards.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AuthorizationCodeFlow;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessControlFT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Passw0rd-demo";
    private static final String DEAN = "ft.scope.dean@chnu.edu.ua";
    private static final String ADMIN = "ft.scope.admin@chnu.edu.ua";
    private static final String MEMBER = "ft.scope.member@chnu.edu.ua";
    private static final String OUTSIDER = "ft.scope.outsider@chnu.edu.ua";
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

    private long deanId;
    private long memberId;
    private long outsiderId;

    @BeforeAll
    void createUsers() {
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        Organization other = organizationRepository.findById(OTHER_FACULTY_ID).orElseThrow();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        User dean = userRepository.save(TestUsers.user(DEAN, faculty));
        userRoleRepository.save(TestUsers.role(dean, RoleType.DEAN, faculty, yesterday, null));
        User admin = userRepository.save(TestUsers.user(ADMIN, university));
        userRoleRepository.save(TestUsers.role(admin, RoleType.SYSTEM_ADMIN, university, yesterday, null));
        User member = userRepository.save(TestUsers.user(MEMBER, department));
        userRoleRepository.save(TestUsers.role(member, RoleType.EMPLOYEE, department, yesterday, null));
        User outsider = userRepository.save(TestUsers.user(OUTSIDER, other));
        deanId = dean.getId();
        memberId = member.getId();
        outsiderId = outsider.getId();
    }

    @AfterAll
    void deleteUsers() {
        List.of(DEAN, ADMIN, MEMBER, OUTSIDER).forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void ac11_ac16_theDeanSeesTheFacultyAndTheAdministratorSeesEverybody() {
        String deanToken = tokenOf(DEAN);
        assertThat(claims(deanToken)).containsEntry("role_scopes", List.of("DEAN:" + TestUsers.FMI_FACULTY_ID));
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims(deanToken).get("permissions");
        assertThat(permissions).contains("user:read:scope", "user:manage:scope").doesNotContain("user:manage");

        as(deanToken).get("/api/v1/users").then().statusCode(200)
            .body("content.email", hasItems(DEAN, MEMBER))
            .body("content.email", not(hasItem(OUTSIDER)))
            .body("content.find { it.email == '" + MEMBER + "' }.membershipConfirmed", equalTo(true))
            .body("content.find { it.email == '" + MEMBER + "' }.roles[0].role", equalTo("EMPLOYEE"));
        as(deanToken).queryParam("organization", TestUsers.DAI_DEPARTMENT_ID).queryParam("role", "EMPLOYEE")
            .get("/api/v1/users").then().statusCode(200)
            .body("content.email", hasItem(MEMBER)).body("content.email", not(hasItem(DEAN)));

        String adminToken = tokenOf(ADMIN);
        as(adminToken).queryParam("q", "ft.scope").queryParam("size", "500").get("/api/v1/users")
            .then().statusCode(200)
            .body("content.email", hasItems(DEAN, ADMIN, MEMBER, OUTSIDER))
            .body("size", equalTo(100));
        as(adminToken).queryParam("organization", 999_999).get("/api/v1/users").then().statusCode(200)
            .body("totalElements", equalTo(0));
    }

    @Test
    void ac15_refusalsAreTypedAndAudited() {
        String memberToken = tokenOf(MEMBER);
        as(memberToken).get("/api/v1/users").then().statusCode(403)
            .contentType("application/problem+json")
            .body("type", equalTo("urn:awards:problem:access-denied"))
            .body("detail", equalTo("permission user:read:all or user:read:scope is required"));

        as(tokenOf(DEAN)).queryParam("organization", OTHER_FACULTY_ID).get("/api/v1/users")
            .then().statusCode(403)
            .body("detail", equalTo("organisation " + OTHER_FACULTY_ID + " is outside your scope"));

        List<Map<String, Object>> rows = jdbc.queryForList(
            "select user_id, new_values->>'path' as path, new_values->>'required' as required,"
                + " host(ip_address) as ip, correlation_id from audit_logs"
                + " where action_type = 'ACCESS_DENIED' and entity_type = 'AUTHORIZATION'"
                + " and user_id in (?, ?) order by created_at", memberId, deanId);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("path", "/api/v1/users")
            .containsEntry("required", "permission user:read:all or user:read:scope is required");
        assertThat(rows.get(0).get("ip")).isNotNull();
        assertThat(rows.get(0).get("correlation_id")).isNotNull();
        assertThat(rows.get(1))
            .containsEntry("required", "organisation " + OTHER_FACULTY_ID + " is outside your scope");
    }

    @Test
    void ac17_aUserOutsideTheScopeIsUnknownToTheDeanButNotToTheAdministrator() {
        as(tokenOf(DEAN)).get("/api/v1/users/" + outsiderId).then().statusCode(404);
        as(tokenOf(DEAN)).get("/api/v1/users/" + memberId).then().statusCode(200)
            .body("email", equalTo(MEMBER))
            .body("roles[0].role", equalTo("EMPLOYEE"))
            .body("roleHistory[0].organization.id", equalTo((int) TestUsers.DAI_DEPARTMENT_ID))
            .body("membershipConfirmed", equalTo(true));
        as(tokenOf(ADMIN)).get("/api/v1/users/" + outsiderId).then().statusCode(200)
            .body("roles", equalTo(List.of()))
            .body("membershipConfirmed", equalTo(false));
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

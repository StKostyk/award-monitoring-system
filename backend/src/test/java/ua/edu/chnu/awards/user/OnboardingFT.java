package ua.edu.chnu.awards.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OnboardingFT extends AbstractFunctionalTest {

    private static final String PREFIX = "ft.onboarding.";
    private static final String EMPLOYEE = PREFIX + "employee@chnu.edu.ua";
    private static final String DEAN = PREFIX + "dean@chnu.edu.ua";
    private static final String ONBOARDING = "/api/v1/users/me/onboarding";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long employeeId;
    private String employee;
    private String dean;

    @BeforeAll
    void createAccounts() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        employeeId = withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        withRole(DEAN, department, RoleType.DEAN, faculty);
        employee = tokenOf(EMPLOYEE);
        dean = tokenOf(DEAN);
    }

    @AfterEach
    void deleteProgress() {
        jdbc.update("delete from dashboard_preferences where user_id in"
            + " (select user_id from users where email_address like '" + PREFIX + "%')");
    }

    @AfterAll
    void deleteAccounts() {
        userRepository.findAll().stream().filter(user -> user.getEmailAddress().startsWith(PREFIX))
            .forEach(userRepository::delete);
    }

    @Test
    void ac9_1_anEmployeeReadsTheFiveStepsNothingDoneAndADeanAlsoTheQueueAndTheDelegation() {
        as(employee).get(ONBOARDING).then().statusCode(HttpStatus.OK.value())
            .header("Cache-Control", equalTo("no-store"))
            .body("steps.key", contains("NOTICE", "PROFILE", "FIRST_AWARD", "VISIBILITY", "NOTIFICATIONS"))
            .body("steps.derived", contains(true, false, true, false, false))
            .body("steps.findAll { it.done }.size()", equalTo(0))
            .body("dismissed", equalTo(false))
            .body("tourSeen", equalTo(false));

        as(dean).get(ONBOARDING).then().statusCode(HttpStatus.OK.value())
            .body("steps.key", contains("NOTICE", "PROFILE", "FIRST_AWARD", "VISIBILITY", "NOTIFICATIONS",
                "REVIEW_QUEUE", "DELEGATE"));
    }

    @Test
    void ac9_3_theSavedProgressIsReadBackWithoutAnAuditRowAndOnlyByItsOwner() {
        long lastLogId = jdbc.queryForObject("select coalesce(max(log_id), 0) from audit_logs", Long.class);

        put(employee, Map.of("done", List.of("PROFILE", "NOTIFICATIONS"), "dismissed", true, "tourSeen", true))
            .statusCode(HttpStatus.OK.value())
            .body("steps.findAll { it.done }.key", contains("PROFILE", "NOTIFICATIONS"));

        as(employee).get(ONBOARDING).then().statusCode(HttpStatus.OK.value())
            .body("steps.findAll { it.done }.key", contains("PROFILE", "NOTIFICATIONS"))
            .body("dismissed", equalTo(true))
            .body("tourSeen", equalTo(true));
        as(dean).get(ONBOARDING).then().statusCode(HttpStatus.OK.value())
            .body("steps.findAll { it.done }.size()", equalTo(0))
            .body("dismissed", equalTo(false));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where log_id > ? and user_id = ?",
            Integer.class, lastLogId, employeeId)).isZero();
    }

    @Test
    void ac9_3_aStepOutsideTheCallersRolesIsAnInvalidParameterAndNothingIsStored() {
        put(employee, Map.of("done", List.of("REVIEW_QUEUE"), "dismissed", true, "tourSeen", false))
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("type", equalTo("urn:awards:problem:invalid-parameter"))
            .body("parameter", equalTo("done"));

        assertThat(jdbc.queryForObject("select count(*) from dashboard_preferences where user_id = ?",
            Integer.class, employeeId)).isZero();
    }

    @Test
    void ac9_6_anAnonymousCallerIsUnauthorisedOnBothMethods() {
        RestAssured.get(ONBOARDING).then().statusCode(HttpStatus.UNAUTHORIZED.value());
        RestAssured.given().contentType(ContentType.JSON).body(Map.of("done", List.of())).put(ONBOARDING)
            .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    private ValidatableResponse put(String token, Map<String, Object> body) {
        return as(token).contentType(ContentType.JSON).body(body).put(ONBOARDING).then();
    }
}

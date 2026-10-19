package ua.edu.chnu.awards.analytics;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonalAnalyticsFT extends AbstractFunctionalTest {

    private static final String ANALYTICS = "/api/v1/analytics/me";
    private static final String CONSENT = "/api/v1/users/me/consents/ANALYTICS";
    private static final String PROBLEM = "urn:awards:problem:invalid-parameter";
    private static final String PREFIX = "ft.analytics.";
    private static final String DOMAIN = "@chnu.edu.ua";
    private static final String OWNER = PREFIX + "employee" + DOMAIN;
    private static final String NO_ROLE = PREFIX + "norole" + DOMAIN;
    private static final long DEPARTMENT_ID = 66L;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long ownerId;

    @BeforeAll
    void createAccounts() {
        Organization department = organizationRepository.findById(DEPARTMENT_ID).orElseThrow();
        for (RoleType role : RoleType.values()) {
            long id = withRole(email(role), department, role, department);
            if (role == RoleType.EMPLOYEE) {
                ownerId = id;
            }
        }
        activeUser(NO_ROLE);
        long colleague = userRepository.findByEmailAddressIgnoreCase(email(RoleType.DEAN)).orElseThrow().getId();
        AwardRows.award(jdbc, colleague).status("APPROVED").awardDate(LocalDate.of(2024, 3, 1)).insert();
        AwardRows.award(jdbc, colleague).status("APPROVED").awardDate(LocalDate.of(2025, 3, 1)).insert();
        AwardRows.award(jdbc, ownerId).status("APPROVED").title("Ministry letter").awardDate(LocalDate.of(2023, 3, 1))
            .insert();
        AwardRows.award(jdbc, ownerId).status("APPROVED").title("State prize").category(12L)
            .awardDate(LocalDate.of(2025, 6, 1)).insert();
        AwardRows.award(jdbc, ownerId).insert();
    }

    @AfterAll
    void deleteAccounts() {
        jdbc.update("delete from awards where user_id in"
            + " (select user_id from users where email_address like '" + PREFIX + "%')");
        userRepository.findAll().stream().filter(user -> user.getEmailAddress().startsWith(PREFIX))
            .forEach(user -> {
                ConsentRows.purge(jdbc, user.getId());
                userRepository.delete(user);
            });
    }

    @Test
    void ac1_1_everyRoleReadsItsOwnAnalytics() {
        for (RoleType role : RoleType.values()) {
            as(tokenOf(email(role))).get(ANALYTICS).then().statusCode(HttpStatus.OK.value())
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        }
    }

    @Test
    void ac1_1_anAccountWithoutARoleOrTokenIsRefused() {
        as(tokenOf(NO_ROLE)).get(ANALYTICS).then().statusCode(HttpStatus.FORBIDDEN.value());
        RestAssured.given().get(ANALYTICS).then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void ac1_2_theResponseCarriesTheCallersCountedAwards() {
        as(tokenOf(OWNER)).queryParam("to", 2025).get(ANALYTICS).then().statusCode(HttpStatus.OK.value())
            .body("period.from", equalTo(2023)).body("period.to", equalTo(2025))
            .body("totals.approved", equalTo(2)).body("totals.highestLevel", equalTo("NATIONAL"))
            .body("totals.firstAwardDate", equalTo("2023-03-01"))
            .body("inProgress.drafts", equalTo(1)).body("inProgress.pending", equalTo(0))
            .body("byYear.year", contains(2023, 2024, 2025)).body("byYear.total", contains(1, 0, 1))
            .body("byYear[0].byLevel.NATIONAL", equalTo(1))
            .body("byLevel", hasSize(1)).body("byCategory.category.id", contains(13, 12))
            .body("timeline.title", contains("State prize", "Ministry letter"))
            .body("timeline[0].category.level", equalTo("NATIONAL")).body("timelineTruncated", equalTo(false));
    }

    @Test
    void ac1_5_invalidYearsNameTheParameter() {
        String token = tokenOf(OWNER);
        Map<String, Map<String, Object>> cases = Map.of(
            "from", Map.of("from", 2024, "to", 2023),
            "to", Map.of("to", 2101));
        cases.forEach((parameter, query) -> as(token).queryParams(query).get(ANALYTICS).then()
            .statusCode(HttpStatus.BAD_REQUEST.value()).body("type", equalTo(PROBLEM))
            .body("parameter", equalTo(parameter)));
        as(token).queryParam("from", 1949).get(ANALYTICS).then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("parameter", equalTo("from"));
        as(token).queryParam("from", "abc").get(ANALYTICS).then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("type", equalTo(PROBLEM)).body("parameter", equalTo("from"));
    }

    @Test
    void ac1_6_ac1_7_theRankFollowsTheConsent() {
        String token = tokenOf(OWNER);
        as(token).get(ANALYTICS).then().body("rank.status", equalTo("CONSENT_REQUIRED"))
            .body("rank.position", nullValue()).body("rank.department", nullValue());

        choose(token, true);
        as(token).get(ANALYTICS).then().statusCode(HttpStatus.OK.value())
            .body("rank.status", equalTo("RANKED")).body("rank.position", equalTo(1))
            .body("rank.groupSize", equalTo(RoleType.values().length)).body("rank.sharedWith", equalTo(1))
            .body("rank.department.code", equalTo("DMA"));

        choose(token, false);
        as(token).get(ANALYTICS).then().body("rank.status", equalTo("CONSENT_REQUIRED"))
            .body("rank.groupSize", nullValue());
    }

    private void choose(String token, boolean granted) {
        as(token).contentType(ContentType.JSON).body(Map.of("granted", granted)).put(CONSENT).then()
            .statusCode(HttpStatus.OK.value());
    }

    private static String email(RoleType role) {
        return PREFIX + Arrays.stream(role.name().split("_")).map(String::toLowerCase)
            .reduce((left, right) -> left + "-" + right).orElseThrow() + DOMAIN;
    }
}

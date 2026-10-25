package ua.edu.chnu.awards.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.oneOf;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import ua.edu.chnu.awards.analytics.service.UnitAggregates;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UniversityAnalyticsFT extends AbstractFunctionalTest {

    private static final String UNIVERSITY = "/api/v1/analytics/university";
    private static final String COMPLIANCE = UNIVERSITY + "/compliance";
    private static final String INVALID = "urn:awards:problem:invalid-parameter";
    private static final String PREFIX = "ft.university.";
    private static final String DOMAIN = "@chnu.edu.ua";
    private static final String RECTOR = PREFIX + "rector" + DOMAIN;
    private static final String RECTOR_SECRETARY = PREFIX + "rector-secretary" + DOMAIN;
    private static final String DEAN = PREFIX + "dean" + DOMAIN;
    private static final String EMPLOYEE = PREFIX + "employee" + DOMAIN;
    private static final String DELEGATE = PREFIX + "delegate" + DOMAIN;
    private static final String GDPR = PREFIX + "gdpr" + DOMAIN;
    private static final String ADMIN = PREFIX + "admin" + DOMAIN;
    private static final long UNIVERSITY_ID = 1L;
    private static final long COLLEGE_ID = 14L;
    private static final long FACULTY_ID = 9L;
    private static final long DEPARTMENT_ID = 64L;
    private static final int CURRENT_YEAR = LocalDate.now(ZoneId.of("Europe/Kyiv")).getYear();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private CacheManager caches;

    @Autowired
    private JdbcTemplate jdbc;

    private long delegateId;

    @BeforeAll
    void createAccounts() {
        Organization university = organizationRepository.findById(UNIVERSITY_ID).orElseThrow();
        Organization faculty = organizationRepository.findById(FACULTY_ID).orElseThrow();
        Organization department = organizationRepository.findById(DEPARTMENT_ID).orElseThrow();
        final long rectorId = withRole(RECTOR, university, RoleType.RECTOR, university);
        withRole(RECTOR_SECRETARY, university, RoleType.RECTOR_SECRETARY, university);
        withRole(DEAN, department, RoleType.DEAN, faculty);
        final long employeeId = withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        delegateId = withRole(DELEGATE, department, RoleType.EMPLOYEE, department);
        withRole(GDPR, university, RoleType.GDPR_OFFICER, university);
        withRole(ADMIN, university, RoleType.SYSTEM_ADMIN, university);
        String award = "insert into awards (user_id, organization_id, title, status, category_id, "
            + "awarding_organization, award_date) values (?, ?, 'Letter', 'APPROVED', 13, 'МОН', ?)";
        jdbc.update(award, employeeId, DEPARTMENT_ID, LocalDate.of(CURRENT_YEAR, 1, 10));
        jdbc.update(award, employeeId, DEPARTMENT_ID, LocalDate.of(CURRENT_YEAR - 6, 1, 10));
        jdbc.update(award, rectorId, UNIVERSITY_ID, LocalDate.of(CURRENT_YEAR - 1, 3, 1));
        Objects.requireNonNull(caches.getCache(UnitAggregates.CACHE)).clear();
    }

    @AfterAll
    void deleteAccounts() {
        String owners = " (select user_id from users where email_address like '" + PREFIX + "%')";
        jdbc.update("delete from role_delegations where delegator_id in" + owners + " or delegate_id in" + owners);
        jdbc.update("delete from awards where user_id in" + owners);
        userRepository.findAll().stream().filter(user -> user.getEmailAddress().startsWith(PREFIX))
            .forEach(user -> {
                ConsentRows.purge(jdbc, user.getId());
                userRepository.delete(user);
            });
        Objects.requireNonNull(caches.getCache(UnitAggregates.CACHE)).clear();
    }

    @Test
    void ac1_2_theRectorAndTheSecretaryReadTheUniversityAndTheCompliance() {
        for (String email : List.of(RECTOR, RECTOR_SECRETARY)) {
            String token = tokenOf(email);
            as(token).get(UNIVERSITY).then().statusCode(HttpStatus.OK.value())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body("unit.id", equalTo((int) UNIVERSITY_ID)).body("unit.type", equalTo("UNIVERSITY"))
                .body("period.to", equalTo(CURRENT_YEAR)).body("previousPeriod", notNullValue())
                .body("partialYear", equalTo(true)).body("generatedAt", notNullValue());
            as(token).get(COMPLIANCE).then().statusCode(HttpStatus.OK.value())
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        }
    }

    @Test
    void ac1_1_ac1_2_otherRolesAndDelegatesOfTheRectorAreRefused() {
        as(tokenOf(RECTOR)).contentType(ContentType.JSON)
            .body(Map.of("delegateId", delegateId, "role", "RECTOR", "organizationId", UNIVERSITY_ID,
                "validFrom", LocalDate.now(ZoneId.of("Europe/Kyiv")).toString(),
                "validTo", LocalDate.now(ZoneId.of("Europe/Kyiv")).plusDays(7).toString(), "reason", "Відрядження"))
            .post("/api/v1/delegations").then().statusCode(HttpStatus.CREATED.value());
        for (String email : List.of(DEAN, EMPLOYEE, GDPR, ADMIN, DELEGATE)) {
            String token = tokenOf(email);
            as(token).get(UNIVERSITY).then().statusCode(HttpStatus.FORBIDDEN.value());
            as(token).get(COMPLIANCE).then().statusCode(HttpStatus.FORBIDDEN.value());
        }
        RestAssured.given().get(UNIVERSITY).then().statusCode(HttpStatus.UNAUTHORIZED.value());
        RestAssured.given().get(COMPLIANCE).then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void ac1_2_invalidYearsNameTheParameter() {
        String rector = tokenOf(RECTOR);
        as(rector).queryParam("from", CURRENT_YEAR + 1).get(UNIVERSITY).then()
            .statusCode(HttpStatus.BAD_REQUEST.value()).body("type", equalTo(INVALID))
            .body("parameter", equalTo("from"));
        as(rector).queryParam("from", "abc").get(UNIVERSITY).then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("parameter", equalTo("from"));
        as(rector).queryParam("to", 2101).get(UNIVERSITY).then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("parameter", equalTo("to"));
    }

    @Test
    void ac1_4_to_ac1_7_theUniversityCarriesUnitsTrendsProjectionAndHighlightsWithoutPeople() {
        String body = as(tokenOf(RECTOR_SECRETARY)).get(UNIVERSITY).then().statusCode(HttpStatus.OK.value())
            .body("units.unit.id", hasItem((int) FACULTY_ID)).body("units.unit.id", hasItem((int) COLLEGE_ID))
            .body("units[-1].unit.id", equalTo((int) UNIVERSITY_ID)).body("units[-1].facultyLevel", equalTo(true))
            .body("units.findAll { !it.facultyLevel }.unit.type", everyItem(oneOf("FACULTY", "COLLEGE")))
            .body("totals.approved.current", notNullValue()).body("byYear", hasSize(5))
            .body("trends.categories", allOf(hasKey("rising"), hasKey("falling")))
            .body("trends.sources", allOf(hasKey("rising"), hasKey("falling")))
            .body("projection.status", equalTo("PROJECTED")).body("projection.year", equalTo(CURRENT_YEAR))
            .body("projection.basis.to", equalTo(CURRENT_YEAR - 1))
            .body("highlights.unit.type", everyItem(oneOf("FACULTY", "COLLEGE")))
            .body("$", not(hasKey("topPerformers")))
            .extract().asString();
        assertThat(body).doesNotContain("userId", "email", PREFIX);
    }

    @Test
    void ac1_10_ac1_11_theComplianceSummaryIsCountsOnlyAndReadLive() {
        String rector = tokenOf(RECTOR);
        int accounts = as(rector).get(COMPLIANCE).then().statusCode(HttpStatus.OK.value())
            .body("status", oneOf("OK", "ATTENTION")).body("privacyNotice.version", notNullValue())
            .body("analyticsConsent.granted", notNullValue()).body("erasure.pending", notNullValue())
            .body("legalHolds.active", notNullValue()).body("reviews.byLevel.level", hasItem("RECTOR"))
            .extract().path("privacyNotice.accounts");
        jdbc.update("update users set account_status = 'INACTIVE' where email_address = ?", EMPLOYEE);

        String body = as(rector).get(COMPLIANCE).then().statusCode(HttpStatus.OK.value())
            .body("privacyNotice.accounts", equalTo(accounts - 1)).body("analyticsConsent.accounts",
                equalTo(accounts - 1))
            .extract().asString();

        assertThat(body).doesNotContain("userId", "email", "\"id\"", "@");
    }
}

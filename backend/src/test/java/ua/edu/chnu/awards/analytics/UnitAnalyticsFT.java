package ua.edu.chnu.awards.analytics;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnitAnalyticsFT extends AbstractFunctionalTest {

    private static final String UNITS = "/api/v1/analytics/units";
    private static final String CONSENT = "/api/v1/users/me/consents/ANALYTICS";
    private static final String INVALID = "urn:awards:problem:invalid-parameter";
    private static final String PREFIX = "ft.units.";
    private static final String DOMAIN = "@chnu.edu.ua";
    private static final String SECRETARY = PREFIX + "secretary" + DOMAIN;
    private static final String DEAN = PREFIX + "dean" + DOMAIN;
    private static final String RECTOR = PREFIX + "rector" + DOMAIN;
    private static final String RECTOR_SECRETARY = PREFIX + "rector-secretary" + DOMAIN;
    private static final String EMPLOYEE = PREFIX + "employee" + DOMAIN;
    private static final String DELEGATE = PREFIX + "delegate" + DOMAIN;
    private static final String GDPR = PREFIX + "gdpr" + DOMAIN;
    private static final String ADMIN = PREFIX + "admin" + DOMAIN;
    private static final String NAMED = PREFIX + "named" + DOMAIN;
    private static final long UNIVERSITY_ID = 1L;
    private static final long COLLEGE_ID = 14L;
    private static final long FACULTY_ID = 10L;
    private static final long DEPARTMENT_ID = 69L;
    private static final long OTHER_FACULTY_ID = 9L;
    private static final long OTHER_DEPARTMENT_ID = 64L;
    private static final int CURRENT_YEAR = LocalDate.now(ZoneId.of("Europe/Kyiv")).getYear();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private CacheManager caches;

    @Autowired
    private JdbcTemplate jdbc;

    private long namedId;
    private long delegateId;

    @BeforeAll
    void createAccounts() {
        Organization university = organizationRepository.findById(UNIVERSITY_ID).orElseThrow();
        Organization faculty = organizationRepository.findById(FACULTY_ID).orElseThrow();
        Organization department = organizationRepository.findById(DEPARTMENT_ID).orElseThrow();
        withRole(SECRETARY, department, RoleType.FACULTY_SECRETARY, faculty);
        withRole(DEAN, department, RoleType.DEAN, faculty);
        withRole(RECTOR, university, RoleType.RECTOR, university);
        withRole(RECTOR_SECRETARY, university, RoleType.RECTOR_SECRETARY, university);
        withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        delegateId = withRole(DELEGATE, department, RoleType.EMPLOYEE, department);
        withRole(GDPR, university, RoleType.GDPR_OFFICER, university);
        withRole(ADMIN, university, RoleType.SYSTEM_ADMIN, university);
        namedId = withRole(NAMED, department, RoleType.EMPLOYEE, department);
        jdbc.update("insert into awards (user_id, organization_id, title, status, category_id, "
            + "awarding_organization, award_date) values (?, ?, 'Letter', 'APPROVED', 13, 'МОН', ?)", namedId,
            DEPARTMENT_ID, LocalDate.of(CURRENT_YEAR, 1, 10));
        AwardRows.award(jdbc, namedId).unit(FACULTY_ID).status("APPROVED")
            .awardDate(LocalDate.of(CURRENT_YEAR - 1, 2, 1)).insert();
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
    void ac1_2_eachRoleListsTheUnitsOfItsOwnScope() {
        as(tokenOf(SECRETARY)).get(UNITS).then().statusCode(HttpStatus.OK.value())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body("type", everyItem(equalTo("DEPARTMENT"))).body("parentId", everyItem(equalTo((int) FACULTY_ID)))
            .body("id", hasItem((int) DEPARTMENT_ID));
        as(tokenOf(DEAN)).get(UNITS).then().statusCode(HttpStatus.OK.value())
            .body("[0].id", equalTo((int) FACULTY_ID)).body("[0].type", equalTo("FACULTY"))
            .body("[0].nameUk", notNullValue()).body("[1].parentId", equalTo((int) FACULTY_ID))
            .body("id", not(hasItem((int) OTHER_FACULTY_ID)));
        as(tokenOf(RECTOR)).get(UNITS).then().statusCode(HttpStatus.OK.value())
            .body("id", hasItems((int) FACULTY_ID, (int) OTHER_FACULTY_ID, (int) OTHER_DEPARTMENT_ID))
            .body("id", not(hasItem((int) UNIVERSITY_ID))).body("id", not(hasItem((int) COLLEGE_ID)))
            .body("type", everyItem(oneOf("FACULTY", "DEPARTMENT")));
        as(tokenOf(RECTOR_SECRETARY)).get(UNITS).then().statusCode(HttpStatus.OK.value())
            .body("id", hasItem((int) OTHER_FACULTY_ID));
    }

    @Test
    void ac1_1_rolesWithoutUnitAnalyticsAndDelegatesAreRefused() {
        as(tokenOf(SECRETARY)).contentType(ContentType.JSON)
            .body(Map.of("delegateId", delegateId, "role", "FACULTY_SECRETARY", "organizationId", FACULTY_ID,
                "validFrom", LocalDate.now(ZoneId.of("Europe/Kyiv")).toString(),
                "validTo", LocalDate.now(ZoneId.of("Europe/Kyiv")).plusDays(7).toString(), "reason", "Відпустка"))
            .post("/api/v1/delegations").then().statusCode(HttpStatus.CREATED.value());
        for (String email : List.of(EMPLOYEE, GDPR, ADMIN, DELEGATE)) {
            as(tokenOf(email)).get(UNITS).then().statusCode(HttpStatus.FORBIDDEN.value());
            as(tokenOf(email)).get(UNITS + "/" + DEPARTMENT_ID).then().statusCode(HttpStatus.FORBIDDEN.value());
        }
        RestAssured.given().get(UNITS).then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void ac1_3_unitsOutsideTheCallersAnalysableUnitsAnswerNotFoundAlike() {
        String secretary = tokenOf(SECRETARY);
        for (long unit : new long[] {FACULTY_ID, UNIVERSITY_ID, OTHER_DEPARTMENT_ID, 999_999L}) {
            as(secretary).get(UNITS + "/" + unit).then().statusCode(HttpStatus.NOT_FOUND.value())
                .body("title", equalTo("Not found")).body("detail", equalTo("Unit " + unit + " is not available"));
        }
        as(secretary).get(UNITS + "/" + DEPARTMENT_ID).then().statusCode(HttpStatus.OK.value());
        as(tokenOf(DEAN)).get(UNITS + "/" + OTHER_FACULTY_ID).then().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void ac1_3_invalidYearsNameTheParameter() {
        String dean = tokenOf(DEAN);
        String path = UNITS + "/" + FACULTY_ID;
        as(dean).queryParam("from", CURRENT_YEAR + 1).get(path).then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("type", equalTo(INVALID)).body("parameter", equalTo("from"));
        as(dean).queryParam("from", CURRENT_YEAR).queryParam("to", CURRENT_YEAR - 5).get(path).then()
            .statusCode(HttpStatus.BAD_REQUEST.value()).body("parameter", equalTo("from"));
        as(dean).queryParam("from", "abc").get(path).then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("type", equalTo(INVALID)).body("parameter", equalTo("from"));
        as(dean).queryParam("to", 2101).get(path).then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("parameter", equalTo("to"));
    }

    @Test
    void ac1_4_ac1_5_theFacultyAnalyticsCarryTheFiguresAndTheComparison() {
        as(tokenOf(DEAN)).get(UNITS + "/" + FACULTY_ID).then().statusCode(HttpStatus.OK.value())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body("unit.id", equalTo((int) FACULTY_ID)).body("unit.type", equalTo("FACULTY"))
            .body("period.from", equalTo(CURRENT_YEAR - 4)).body("period.to", equalTo(CURRENT_YEAR))
            .body("previousPeriod.from", equalTo(CURRENT_YEAR - 9)).body("partialYear", equalTo(true))
            .body("generatedAt", notNullValue())
            .body("totals.approved.current", equalTo(2)).body("totals.awardedPeople.current", equalTo(1))
            .body("totals.personal", equalTo(1)).body("totals.unitAwards", equalTo(1))
            .body("byYear", hasSize(5)).body("byLevel", hasSize(1))
            .body("units.find { it.facultyLevel }.approved.current", equalTo(1))
            .body("units.find { it.unit.id == " + DEPARTMENT_ID + " }.approved.current", equalTo(1))
            .body("bySource.sources[0].name", equalTo("МОН")).body("bySource.otherCount", equalTo(0));
        as(tokenOf(SECRETARY)).get(UNITS + "/" + DEPARTMENT_ID).then().statusCode(HttpStatus.OK.value())
            .body("units", hasSize(0)).body("highlights", hasSize(0)).body("totals.approved.current", equalTo(1));
    }

    @Test
    void ac1_7_aWithdrawnConsentRemovesThePersonAtTheNextRequest() {
        String named = tokenOf(NAMED);
        String dean = tokenOf(DEAN);
        choose(named, true);
        as(dean).get(UNITS + "/" + FACULTY_ID).then().statusCode(HttpStatus.OK.value())
            .body("topPerformers.status", equalTo("LISTED"))
            .body("topPerformers.people.userId", hasItem((int) namedId))
            .body("topPerformers.people.find { it.userId == " + namedId + " }.count", equalTo(1))
            .body("topPerformers.people.find { it.userId == " + namedId + " }.position", nullValue());

        choose(named, false);

        as(dean).get(UNITS + "/" + FACULTY_ID).then().statusCode(HttpStatus.OK.value())
            .body("topPerformers.people.userId", not(hasItem((int) namedId)));
    }

    private void choose(String token, boolean granted) {
        as(token).contentType(ContentType.JSON).body(Map.of("granted", granted)).put(CONSENT).then()
            .statusCode(HttpStatus.OK.value());
    }
}

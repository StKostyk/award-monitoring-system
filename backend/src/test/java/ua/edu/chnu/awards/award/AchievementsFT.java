package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.time.Instant;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;

import ua.edu.chnu.awards.auth.security.RateLimitFilter;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AchievementsFT extends AbstractFunctionalTest {

    private static final String OWNER = "ft.share.owner@chnu.edu.ua";
    private static final String COLLEAGUE = "ft.share.colleague@chnu.edu.ua";
    private static final String SECRETARY = "ft.share.secretary@chnu.edu.ua";
    private static final String DELETED = "ft.share.deleted@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(OWNER, COLLEAGUE, SECRETARY, DELETED);
    private static final String ACHIEVEMENTS = "/api/v1/achievements";
    private static final String PUBLIC_ACHIEVEMENTS = "/api/v1/public/achievements";
    private static final String BURST_IP = "10.98.0.7";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final int YEAR = 1991;
    private static final long OTHER_FACULTY_ID = 10L;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Value("${app.auth.protection.public-requests-per-minute}")
    private int publicRequestsPerMinute;

    private long ownerId;
    private long approvedId;
    private long pendingId;
    private long unitAwardId;

    @BeforeAll
    void createAwards() {
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        Organization other = organizationRepository.findById(OTHER_FACULTY_ID).orElseThrow();
        ownerId = withRole(OWNER, department, RoleType.EMPLOYEE, department);
        withRole(COLLEAGUE, other, RoleType.EMPLOYEE, other);
        long secretaryId = withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        LocalDate date = LocalDate.of(YEAR, 3, 1);
        approvedId = AwardRows.award(jdbc, ownerId).title("Shared letter").status("APPROVED").awardDate(date)
            .insert();
        pendingId = AwardRows.award(jdbc, ownerId).status("PENDING").awardDate(date).insert();
        unitAwardId = AwardRows.award(jdbc, secretaryId).title("Faculty cup").unit(TestUsers.FMI_FACULTY_ID)
            .status("APPROVED").awardDate(date).insert();
        long deletedId = withRole(DELETED, department, RoleType.EMPLOYEE, department);
        long hiddenId = AwardRows.award(jdbc, deletedId).status("APPROVED").awardDate(date).insert();
        jdbc.update("update awards set visibility = 'UNIVERSITY' where award_id = ?", hiddenId);
        jdbc.update("update users set account_status = 'DELETED' where user_id = ?", deletedId);
    }

    @BeforeEach
    void keepPrivate() {
        jdbc.update("update awards set visibility = 'PRIVATE' where award_id = ?", approvedId);
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from awards where user_id in"
            + " (select user_id from users where email_address like 'ft.share.%')");
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac1_2_theOwnerSharesAnAwardOnceWithoutANewVersion() {
        int version = as(tokenOf(OWNER)).get(AwardApi.AWARDS + "/" + approvedId).then()
            .statusCode(HttpStatus.OK.value()).body("visibility", equalTo("PRIVATE")).extract().path("version");
        int versions = versionRows();
        int changes = visibilityChanges();

        share(OWNER, approvedId, "UNIVERSITY").then().statusCode(HttpStatus.OK.value())
            .body("visibility", equalTo("UNIVERSITY")).body("version", equalTo(version));
        share(OWNER, approvedId, "UNIVERSITY").then().statusCode(HttpStatus.OK.value());

        assertThat(visibilityChanges()).isEqualTo(changes + 1);
        assertThat(jdbc.queryForMap("select new_values ->> 'from' as old, new_values ->> 'to' as new "
            + "from audit_logs where action_type = 'AWARD_VISIBILITY_CHANGED' and entity_id = ? "
            + "order by log_id desc limit 1", approvedId))
            .isEqualTo(Map.of("old", "PRIVATE", "new", "UNIVERSITY"));
        assertThat(versionRows()).isEqualTo(versions);
    }

    @Test
    void ac1_4_noOneElseAndNoOtherStatusMayChoose() {
        share(SECRETARY, approvedId, "PUBLIC").then().statusCode(HttpStatus.NOT_FOUND.value());
        share(OWNER, pendingId, "PUBLIC").then().statusCode(HttpStatus.CONFLICT.value())
            .body("type", equalTo(PROBLEM + "visibility-fixed")).body("awardStatus", equalTo("PENDING"));
        share(SECRETARY, unitAwardId, "PUBLIC").then().statusCode(HttpStatus.CONFLICT.value())
            .body("type", equalTo(PROBLEM + "visibility-fixed")).body("awardStatus", nullValue());
        as(tokenOf(OWNER)).contentType(ContentType.JSON).body(Map.of())
            .put(AwardApi.AWARDS + "/" + approvedId + "/visibility").then()
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value()).body("type", equalTo(PROBLEM + "validation-failed"));

        assertThat(jdbc.queryForObject("select visibility from awards where award_id = ?", String.class,
            approvedId)).isEqualTo("PRIVATE");
    }

    @Test
    void ac1_5_ac1_7_aColleagueSeesSharedAwardsUntilTheOwnerTakesThemBack() {
        share(OWNER, approvedId, "PUBLIC").then().statusCode(HttpStatus.OK.value());

        String body = as(tokenOf(COLLEAGUE)).queryParam("year", YEAR).get(ACHIEVEMENTS).then()
            .statusCode(HttpStatus.OK.value())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body("content.awardId", contains((int) unitAwardId, (int) approvedId))
            .body("content[0].recipient.type", equalTo("UNIT"))
            .body("content[0].recipient.personName", nullValue())
            .body("content[1].recipient.unit.id", equalTo((int) TestUsers.DAI_DEPARTMENT_ID))
            .body("content[1].category.level", equalTo("NATIONAL"))
            .extract().asString();
        assertThat(body).doesNotContain(OWNER, "impactScore", "owner", "request");

        share(OWNER, approvedId, "PRIVATE").then().statusCode(HttpStatus.OK.value());
        as(tokenOf(COLLEAGUE)).queryParam("year", YEAR).get(ACHIEVEMENTS).then()
            .body("content.awardId", contains((int) unitAwardId));
    }

    @Test
    void ac1_6_filtersNarrowTheListAndAFacultyIncludesItsDepartments() {
        share(OWNER, approvedId, "UNIVERSITY").then().statusCode(HttpStatus.OK.value());

        list(Map.of("year", YEAR, "unit", TestUsers.FMI_FACULTY_ID))
            .body("content.awardId", contains((int) unitAwardId, (int) approvedId));
        list(Map.of("year", YEAR, "unit", TestUsers.DAI_DEPARTMENT_ID))
            .body("content.awardId", contains((int) approvedId));
        list(Map.of("year", YEAR, "unit", OTHER_FACULTY_ID)).body("content", empty());
        list(Map.of("year", YEAR, "recipient", "UNIT")).body("content.awardId", contains((int) unitAwardId));
        list(Map.of("year", YEAR, "recipient", "PERSON")).body("content.awardId", contains((int) approvedId));
        list(Map.of("year", YEAR, "level", "INTERNATIONAL")).body("content", empty());
        list(Map.of("year", YEAR + 1)).body("content.awardId", not(contains((int) approvedId)));
        as(tokenOf(COLLEAGUE)).queryParam("unit", TestUsers.UNIVERSITY_ID).get(ACHIEVEMENTS).then()
            .statusCode(HttpStatus.NOT_FOUND.value());
        as(tokenOf(COLLEAGUE)).queryParam("year", "nineteen").get(ACHIEVEMENTS).then()
            .statusCode(HttpStatus.BAD_REQUEST.value()).body("type", equalTo(PROBLEM + "invalid-parameter"));
    }

    @Test
    void ac2_1_anAnonymousVisitorSeesPublicAndUnitAwardsOnly() {
        share(OWNER, approvedId, "UNIVERSITY").then().statusCode(HttpStatus.OK.value());
        published(Map.of("year", YEAR)).body("content.awardId", contains((int) unitAwardId));

        share(OWNER, approvedId, "PUBLIC").then().statusCode(HttpStatus.OK.value());
        String body = published(Map.of("year", YEAR))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body("content.awardId", contains((int) unitAwardId, (int) approvedId))
            .body("content[1].recipient.unit.id", equalTo((int) TestUsers.DAI_DEPARTMENT_ID))
            .extract().asString();
        assertThat(body).doesNotContain(OWNER, "impactScore", "owner", "request");
        published(Map.of("year", YEAR, "unit", TestUsers.DAI_DEPARTMENT_ID))
            .body("content.awardId", contains((int) approvedId));
        RestAssured.given().queryParam("unit", TestUsers.UNIVERSITY_ID).get(PUBLIC_ACHIEVEMENTS).then()
            .statusCode(HttpStatus.NOT_FOUND.value());
        RestAssured.given().queryParam("year", "nineteen").get(PUBLIC_ACHIEVEMENTS).then()
            .statusCode(HttpStatus.BAD_REQUEST.value()).body("type", equalTo(PROBLEM + "invalid-parameter"));
    }

    @Test
    void ac2_2_aBrokenTokenReadsThePublicListLikeAnAnonymousVisitor() {
        share(OWNER, approvedId, "PUBLIC").then().statusCode(HttpStatus.OK.value());

        as("not.a.token").queryParam("year", YEAR).get(PUBLIC_ACHIEVEMENTS).then()
            .statusCode(HttpStatus.OK.value()).body("content.awardId", contains((int) unitAwardId, (int) approvedId));
        as("not.a.token").get(ACHIEVEMENTS).then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void ac2_3_aBurstFromOneAddressIsRefusedWhileOthersAndSignInKeepTheirBudget() {
        long window = Instant.now().getEpochSecond() / 60;
        String used = String.valueOf(publicRequestsPerMinute);
        redis.opsForValue().set(RateLimitFilter.PUBLIC_KEY_PREFIX + BURST_IP + ":" + window, used);
        redis.opsForValue().set(RateLimitFilter.PUBLIC_KEY_PREFIX + BURST_IP + ":" + (window + 1), used);

        Response refused = RestAssured.given().header("X-Forwarded-For", BURST_IP).get(PUBLIC_ACHIEVEMENTS);
        refused.then().statusCode(HttpStatus.TOO_MANY_REQUESTS.value())
            .header(HttpHeaders.RETRY_AFTER, notNullValue())
            .contentType("application/problem+json")
            .body("type", equalTo(PROBLEM + "too-many-requests"));
        assertThat(Integer.parseInt(refused.getHeader(HttpHeaders.RETRY_AFTER))).isBetween(1, 60);

        RestAssured.given().header("X-Forwarded-For", "10.98.0.8").get(PUBLIC_ACHIEVEMENTS).then()
            .statusCode(HttpStatus.OK.value());
        RestAssured.given().header("X-Forwarded-For", BURST_IP).accept("text/html").get("/login").then()
            .statusCode(HttpStatus.OK.value());
    }

    @Test
    void ac2_7_reducingToUniversityRemovesTheAwardFromThePublicListAtOnce() {
        share(OWNER, approvedId, "PUBLIC").then().statusCode(HttpStatus.OK.value());
        published(Map.of("year", YEAR)).body("content.awardId", contains((int) unitAwardId, (int) approvedId));

        share(OWNER, approvedId, "UNIVERSITY").then().statusCode(HttpStatus.OK.value());

        published(Map.of("year", YEAR)).body("content.awardId", contains((int) unitAwardId));
        list(Map.of("year", YEAR)).body("content.awardId", contains((int) unitAwardId, (int) approvedId));
    }

    @Test
    void ac1_10_theExportCarriesTheVisibility() {
        share(OWNER, approvedId, "PUBLIC").then().statusCode(HttpStatus.OK.value());

        List<Map<String, Object>> awards = as(tokenOf(OWNER)).get("/api/v1/users/me/export").then()
            .statusCode(HttpStatus.OK.value()).extract().path("awards");

        assertThat(awards).anySatisfy(award -> assertThat(award).containsEntry("award_id", (int) approvedId)
            .containsEntry("visibility", "PUBLIC"));
    }

    private Response share(String email, long awardId, String visibility) {
        return as(tokenOf(email)).contentType(ContentType.JSON).body(Map.of("visibility", visibility))
            .put(AwardApi.AWARDS + "/" + awardId + "/visibility");
    }

    private ValidatableResponse list(Map<String, Object> filters) {
        return as(tokenOf(COLLEAGUE)).queryParams(filters).get(ACHIEVEMENTS).then()
            .statusCode(HttpStatus.OK.value());
    }

    private static ValidatableResponse published(Map<String, Object> filters) {
        return RestAssured.given().queryParams(filters).get(PUBLIC_ACHIEVEMENTS).then()
            .statusCode(HttpStatus.OK.value());
    }

    private int visibilityChanges() {
        return jdbc.queryForObject("select count(*) from audit_logs where action_type = 'AWARD_VISIBILITY_CHANGED' "
            + "and entity_type = 'awards' and entity_id = ? and user_id = ?", Integer.class, approvedId, ownerId);
    }

    private int versionRows() {
        return jdbc.queryForObject("select count(*) from award_versions where award_id = ?", Integer.class,
            approvedId);
    }
}

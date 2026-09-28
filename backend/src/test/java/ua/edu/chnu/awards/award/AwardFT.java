package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
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
class AwardFT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Passw0rd-demo";
    private static final String EMPLOYEE = "ft.award.employee@chnu.edu.ua";
    private static final String DEAN = "ft.award.dean@chnu.edu.ua";
    private static final String SECRETARY = "ft.award.secretary@chnu.edu.ua";
    private static final String OUTSIDER = "ft.award.outsider@chnu.edu.ua";
    private static final String ADMIN = "ft.award.admin@chnu.edu.ua";
    private static final String NEWCOMER = "ft.award.newcomer@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(EMPLOYEE, DEAN, SECRETARY, OUTSIDER, ADMIN, NEWCOMER);
    private static final String AWARDS = "/api/v1/awards";
    private static final String TYPE = "type";
    private static final String VERSION = "version";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final long OTHER_FACULTY_ID = 10L;
    private static final long MINISTRY_CATEGORY = 13L;
    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

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

    private String employee;
    private long employeeId;

    @BeforeAll
    void createUsers() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        employeeId = withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        userRepository.save(TestUsers.user(NEWCOMER, department));
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        withRole(DEAN, faculty, RoleType.DEAN, faculty);
        withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        Organization other = organizationRepository.findById(OTHER_FACULTY_ID).orElseThrow();
        withRole(OUTSIDER, other, RoleType.FACULTY_SECRETARY, other);
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        withRole(ADMIN, university, RoleType.SYSTEM_ADMIN, university);
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from awards where user_id in"
            + " (select user_id from users where email_address like 'ft.award.%')");
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        employee = tokenOf(EMPLOYEE);
    }

    @Test
    void ac1_1_to_ac1_6_aDraftIsCompletedSubmittedOnceAndThenFrozen() {
        Response created = as(employee).contentType(ContentType.JSON)
            .body(Map.of("titleUk", "  Грамота Міністерства освіти і науки  ")).post(AWARDS);
        created.then().statusCode(201)
            .header("Location", endsWith(AWARDS + "/" + created.jsonPath().getLong("id")))
            .body("status", equalTo("DRAFT"))
            .body("titleUk", equalTo("Грамота Міністерства освіти і науки"))
            .body("organization.id", equalTo((int) TestUsers.DAI_DEPARTMENT_ID))
            .body("owner.email", equalTo(EMPLOYEE))
            .body("category", nullValue());
        long id = created.jsonPath().getLong("id");
        long version = created.jsonPath().getLong(VERSION);

        as(employee).contentType(ContentType.JSON).body(Map.of(VERSION, version)).post(AWARDS + "/" + id + "/submit")
            .then().statusCode(422)
            .body(TYPE, equalTo(PROBLEM + "award-incomplete"))
            .body("errors.field", containsInAnyOrder("categoryId", "awardingOrganization", "awardDate"));

        Response updated = as(employee).contentType(ContentType.JSON).body(Map.of(
                "titleUk", "Грамота Міністерства освіти і науки", "categoryId", MINISTRY_CATEGORY,
                "awardingOrganization", "Міністерство освіти і науки України",
                "awardDate", LocalDate.now().minusYears(1).toString(), VERSION, version))
            .put(AWARDS + "/" + id);
        updated.then().statusCode(200).body("category.level", equalTo("NATIONAL"));
        long current = updated.jsonPath().getLong(VERSION);
        assertThat(current).isGreaterThan(version);

        as(employee).contentType(ContentType.JSON).body(Map.of("titleUk", "Інша", VERSION, version))
            .put(AWARDS + "/" + id)
            .then().statusCode(409).body(TYPE, equalTo(PROBLEM + "award-stale"))
            .body("currentVersion", equalTo((int) current));

        as(employee).contentType(ContentType.JSON).body(Map.of(VERSION, current)).post(AWARDS + "/" + id + "/submit")
            .then().statusCode(200)
            .body("status", equalTo("PENDING"))
            .body("impactScore", equalTo(80))
            .body("request.status", equalTo("SUBMITTED"))
            .body("request.currentLevel", equalTo("FACULTY_SECRETARY"));

        as(employee).contentType(ContentType.JSON).body(Map.of(VERSION, current)).post(AWARDS + "/" + id + "/submit")
            .then().statusCode(409).body(TYPE, equalTo(PROBLEM + "award-not-editable"));
        as(employee).contentType(ContentType.JSON).body(Map.of("titleUk", "Інша", VERSION, current))
            .put(AWARDS + "/" + id).then().statusCode(409).body(TYPE, equalTo(PROBLEM + "award-not-editable"));
        as(employee).delete(AWARDS + "/" + id).then().statusCode(409);

        assertThat(jdbc.queryForMap("select status, current_level, submitter_id from award_requests "
            + "where award_id = ?", id)).containsEntry("status", "SUBMITTED")
            .containsEntry("current_level", "FACULTY_SECRETARY").containsEntry("submitter_id", employeeId);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'AWARD_SUBMITTED' "
            + "and entity_type = 'awards' and entity_id = ? and user_id = ?", Integer.class, id, employeeId))
            .isEqualTo(1);
    }

    @Test
    void ac1_1_invalidFieldsAreListedOneByOne() {
        as(employee).contentType(ContentType.JSON).body(Map.of("externalUrl", "javascript:alert(1)",
                "awardDate", LocalDate.now().plusDays(2).toString()))
            .post(AWARDS)
            .then().statusCode(422).body(TYPE, equalTo(PROBLEM + "validation-failed"))
            .body("errors.field", containsInAnyOrder("title", "externalUrl", "awardDate"));
    }

    @Test
    void ac2_1_ac2_2_theDateIsNeitherInTheFutureNorOlderThanFiftyYears() {
        LocalDate today = LocalDate.now(KYIV);

        dated(today.plusDays(1)).then().statusCode(422).body(TYPE, equalTo(PROBLEM + "validation-failed"))
            .body("errors.code", contains("future"));
        dated(today.minusYears(50)).then().statusCode(201);
        dated(today.minusYears(50).minusDays(1)).then().statusCode(422).body("errors.code", contains("too-old"));
    }

    @Test
    void ac2_3_aRecentDateIsAHintThatDoesNotBlockTheSubmission() {
        Response created = dated(LocalDate.now(KYIV).minusDays(7));
        created.then().statusCode(201).body("warnings.code", contains("RECENT_DATE"))
            .body("warnings[0].field", equalTo("awardDate"));

        submit(employee, created.jsonPath().getLong("id"), created.jsonPath().getLong(VERSION), null)
            .then().statusCode(200).body("warnings", hasSize(0));
    }

    @Test
    void ac2_4_ac2_5_aPossibleDuplicateIsSubmittedOnlyWhenAcknowledged() {
        LocalDate date = LocalDate.now(KYIV).minusYears(2).minusDays(17);
        long first = submitted(employee, "Грамота Міністерства освіти і науки", date);
        Response second = as(employee).contentType(ContentType.JSON).body(Map.of(
                "titleUk", "Грамота Міністерства освіти і науки України", "categoryId", MINISTRY_CATEGORY,
                "awardingOrganization", "МОН України", "awardDate", date.toString()))
            .post(AWARDS);
        second.then().statusCode(201)
            .body("warnings.code", contains("POSSIBLE_DUPLICATE"))
            .body("warnings[0].matches.id", contains((int) first))
            .body("warnings[0].matches[0].status", equalTo("PENDING"));
        long id = second.jsonPath().getLong("id");
        long version = second.jsonPath().getLong(VERSION);

        submit(employee, id, version, null).then().statusCode(409)
            .body(TYPE, equalTo(PROBLEM + "award-possible-duplicate"))
            .body("matches.id", contains((int) first));
        as(employee).get(AWARDS + "/" + id).then().statusCode(200).body("status", equalTo("DRAFT"));
        submit(employee, id, version, true).then().statusCode(200).body("status", equalTo("PENDING"));

        assertThat(jdbc.queryForObject("select new_values ->> 'duplicateAcknowledged' from audit_logs "
            + "where action_type = 'AWARD_SUBMITTED' and entity_id = ?", String.class, id)).isEqualTo("true");
        as(tokenOf(SECRETARY)).contentType(ContentType.JSON).body(Map.of(
                "titleUk", "Грамота Міністерства освіти і науки", "awardDate", date.toString()))
            .post(AWARDS).then().statusCode(201).body("warnings", hasSize(0));
    }

    @Test
    void ac1_4_aDraftIsDeletedAndThenUnknown() {
        long id = draft(employee, "Подяка ректора");

        as(employee).delete(AWARDS + "/" + id).then().statusCode(204);
        as(employee).get(AWARDS + "/" + id).then().statusCode(404);
    }

    @Test
    void ac1_2_administratorsAndUnconfirmedAccountsDoNotSubmit() {
        as(tokenOf(ADMIN)).contentType(ContentType.JSON).body(Map.of("title", "x")).post(AWARDS)
            .then().statusCode(403).body(TYPE, equalTo(PROBLEM + "access-denied"));
        as(tokenOf(NEWCOMER)).contentType(ContentType.JSON).body(Map.of("title", "x")).post(AWARDS)
            .then().statusCode(403);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'ACCESS_DENIED' "
            + "and new_values ->> 'path' = ? and user_id = (select user_id from users where email_address = ?)",
            Integer.class, AWARDS, ADMIN)).isPositive();
    }

    @Test
    void ac1_8_submittedAwardsAreReadInsideTheScopeAndDraftsByTheOwnerOnly() {
        long submitted = submitted(employee, "Грамота декана");
        long draft = draft(employee, "Чернетка");
        String dean = tokenOf(DEAN);

        as(dean).get(AWARDS + "/" + submitted).then().statusCode(200).body("owner.email", equalTo(EMPLOYEE));
        as(tokenOf(OUTSIDER)).get(AWARDS + "/" + submitted).then().statusCode(404);
        as(tokenOf(ADMIN)).get(AWARDS + "/" + submitted).then().statusCode(200);
        as(dean).get(AWARDS + "/" + draft).then().statusCode(404);
        as(employee).get(AWARDS + "/" + draft).then().statusCode(200).body("status", equalTo("DRAFT"));
        as(dean).get(AWARDS + "/999999999").then().statusCode(404);
    }

    @Test
    void ac1_7_theListShowsOnlyOwnAwardsWithTheirRequests() {
        long submitted = submitted(employee, "Подяка голови ОДА");
        long draft = draft(employee, "Ще одна чернетка");
        submitted(tokenOf(SECRETARY), "Нагорода секретаря");

        as(employee).get(AWARDS + "?size=500").then().statusCode(200)
            .body("size", equalTo(100))
            .body("content.owner.email.unique()", hasSize(1))
            .body("content.find { it.id == " + submitted + " }.request.status", equalTo("SUBMITTED"))
            .body("content.find { it.id == " + draft + " }.request", nullValue());
        as(employee).get(AWARDS + "?status=DRAFT").then().statusCode(200)
            .body("content.status.unique()", equalTo(List.of("DRAFT")));
    }

    @Test
    void ac1_11_anApproverSubmitsTheirOwnAward() {
        long id = submitted(tokenOf(SECRETARY), "Відзнака секретаря факультету");

        assertThat(jdbc.queryForObject("select current_level from award_requests where award_id = ?", String.class,
            id)).isEqualTo("FACULTY_SECRETARY");
    }

    @Test
    void ac1_6_twoSubmissionsAtOnceCreateOneRequest() throws Exception {
        long id = complete(employee, "Подвійне подання");
        long version = as(employee).get(AWARDS + "/" + id).jsonPath().getLong(VERSION);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            calls.add(() -> as(employee).contentType(ContentType.JSON).body(Map.of(VERSION, version))
                .post(AWARDS + "/" + id + "/submit").statusCode());
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : pool.invokeAll(calls)) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select count(*) from award_requests where award_id = ?", Integer.class,
            id)).isEqualTo(1);
    }

    private long draft(String token, String title) {
        return as(token).contentType(ContentType.JSON).body(Map.of("titleUk", title)).post(AWARDS)
            .then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private long complete(String token, String title) {
        return complete(token, title, LocalDate.now(KYIV).minusMonths(3));
    }

    private long complete(String token, String title, LocalDate date) {
        return as(token).contentType(ContentType.JSON).body(Map.of("titleUk", title,
                "categoryId", MINISTRY_CATEGORY, "awardingOrganization", "МОН України",
                "awardDate", date.toString()))
            .post(AWARDS).then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private long submitted(String token, String title) {
        return submitted(token, title, LocalDate.now(KYIV).minusMonths(3));
    }

    private long submitted(String token, String title, LocalDate date) {
        long id = complete(token, title, date);
        long version = as(token).get(AWARDS + "/" + id).jsonPath().getLong(VERSION);
        submit(token, id, version, null).then().statusCode(200);
        return id;
    }

    private Response submit(String token, long id, long version, Boolean acknowledgeDuplicate) {
        Map<String, Object> body = new HashMap<>();
        body.put(VERSION, version);
        body.put("acknowledgeDuplicate", acknowledgeDuplicate);
        return as(token).contentType(ContentType.JSON).body(body).post(AWARDS + "/" + id + "/submit");
    }

    private Response dated(LocalDate date) {
        return as(employee).contentType(ContentType.JSON).body(Map.of("titleUk", "Подяка " + date,
                "categoryId", MINISTRY_CATEGORY, "awardingOrganization", "МОН України", "awardDate", date.toString()))
            .post(AWARDS);
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
}

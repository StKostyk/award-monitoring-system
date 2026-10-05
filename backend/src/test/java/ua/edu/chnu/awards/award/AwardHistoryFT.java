package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static ua.edu.chnu.awards.common.web.ApiExceptionHandler.TYPE_PREFIX;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.common.web.CorrelationIdFilter;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AwardHistoryFT extends AbstractFunctionalTest {

    private static final String EMPLOYEE = "ft.history.employee@chnu.edu.ua";
    private static final String DEAN = "ft.history.dean@chnu.edu.ua";
    private static final String OUTSIDER = "ft.history.outsider@chnu.edu.ua";
    private static final String ADMIN = "ft.history.admin@chnu.edu.ua";
    private static final String NEWCOMER = "ft.history.newcomer@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(EMPLOYEE, DEAN, OUTSIDER, ADMIN, NEWCOMER);
    private static final String AWARDS = "/api/v1/awards";
    private static final String VERSION = "version";
    private static final long OTHER_FACULTY_ID = 10L;
    private static final long MINISTRY_CATEGORY = 13L;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private String employee;
    private long employeeId;
    private long adminId;
    private long newcomerId;

    @BeforeAll
    void createUsers() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        employeeId = withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        newcomerId = userRepository.save(TestUsers.user(NEWCOMER, department)).getId();
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        withRole(DEAN, faculty, RoleType.DEAN, faculty);
        Organization other = organizationRepository.findById(OTHER_FACULTY_ID).orElseThrow();
        withRole(OUTSIDER, other, RoleType.FACULTY_SECRETARY, other);
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        adminId = withRole(ADMIN, university, RoleType.SYSTEM_ADMIN, university);
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from awards where user_id in"
            + " (select user_id from users where email_address like 'ft.history.%')");
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @BeforeEach
    void setUp() {
        employee = tokenOf(EMPLOYEE);
    }

    @Test
    void ac1_1_to_ac1_3_ac1_8_theOwnerSeesEverySavedVersionWithItsChanges() {
        String correlation = UUID.randomUUID().toString();
        long id = as(employee).header(CorrelationIdFilter.HEADER, correlation).contentType(ContentType.JSON)
            .body(Map.of("title", "Letter")).post(AWARDS).then().statusCode(201)
            .body(VERSION, equalTo(1)).extract().jsonPath().getLong("id");
        String yearAgo = LocalDate.now(AwardApi.KYIV).minusYears(1).toString();
        Map<String, Object> complete = Map.of("title", "Diploma", "categoryId", MINISTRY_CATEGORY,
            "awardingOrganization", "МОН", "awardDate", yearAgo, VERSION, 1);
        as(employee).contentType(ContentType.JSON).body(complete).put(AWARDS + "/" + id).then().statusCode(200)
            .body(VERSION, equalTo(2));
        as(employee).contentType(ContentType.JSON).body(Map.of("title", "Diploma", "categoryId", MINISTRY_CATEGORY,
                "awardingOrganization", "МОН", "awardDate", yearAgo, VERSION, 2))
            .put(AWARDS + "/" + id).then().statusCode(200).body(VERSION, equalTo(2));
        as(employee).contentType(ContentType.JSON).body(complete).put(AWARDS + "/" + id).then().statusCode(409)
            .body("type", equalTo(TYPE_PREFIX + "award-stale"));
        AwardApi.submit(employee, id, 2, true).then().statusCode(200);

        Response versions = as(employee).get(AWARDS + "/" + id + "/versions");

        versions.then().statusCode(200)
            .body("content.number", contains(3, 2, 1))
            .body("content.action", contains("SUBMITTED", "UPDATED", "CREATED"))
            .body("content[0].snapshot.status", equalTo("PENDING"))
            .body("content[0].snapshot.impactScore", equalTo(80))
            .body("content[0].changes.field", contains("status", "impactScore"))
            .body("content[1].changes.field", contains("title", "awardingOrganization", "awardDate", "categoryId"))
            .body("content[1].changes[0].from", equalTo("Letter"))
            .body("content[1].changes[0].to", equalTo("Diploma"))
            .body("content[2].changes", empty())
            .body("content[2].actor.email", equalTo(EMPLOYEE));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_type = 'awards' "
            + "and entity_id = ? and user_id = ? and correlation_id = ?::uuid", Integer.class, id, employeeId,
            correlation)).isEqualTo(1);
    }

    @Test
    void ac1_9_readersInScopeSeeTheAwardFromItsSubmissionAndOthersNothing() {
        long id = submitted("Certificate");
        long draft = as(employee).contentType(ContentType.JSON).body(Map.of("title", "Draft")).post(AWARDS)
            .then().statusCode(201).extract().jsonPath().getLong("id");
        String dean = tokenOf(DEAN);

        as(dean).get(AWARDS + "/" + id + "/versions").then().statusCode(200)
            .body("content.action", contains("SUBMITTED"))
            .body("content[0].changes", empty());
        as(tokenOf(OUTSIDER)).get(AWARDS + "/" + id + "/versions").then().statusCode(404);
        as(dean).get(AWARDS + "/" + draft + "/versions").then().statusCode(404);
        as(dean).get(AWARDS + "/999999999/versions").then().statusCode(404);
    }

    @Test
    void ac1_10_ac1_11_theAuditTrailIsForOversightOnlyAndOutlivesADeletedDraft() {
        long id = submitted("Grant");
        String admin = tokenOf(ADMIN);

        as(admin).get(AWARDS + "/" + id + "/audit-trail").then().statusCode(200)
            .body("content.action", hasItems("INSERT", "UPDATE", "AWARD_SUBMITTED"))
            .body("content.entityType", hasItems("awards", "award_requests"))
            .body("content.find { it.action == 'AWARD_SUBMITTED' }.actorEmail", equalTo(EMPLOYEE))
            .body("content.find { it.action == 'AWARD_SUBMITTED' }.ipAddress", equalTo("127.0.0.1"))
            .body("content.find { it.action == 'INSERT' && it.entityType == 'awards' }.actorId",
                equalTo((int) employeeId));
        as(tokenOf(DEAN)).get(AWARDS + "/" + id + "/audit-trail").then().statusCode(403)
            .body("type", equalTo(TYPE_PREFIX + "access-denied"));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'ACCESS_DENIED' "
            + "and new_values->>'path' like ?", Integer.class, "%/" + id + "/audit-trail")).isPositive();

        long draft = as(employee).contentType(ContentType.JSON).body(Map.of("title", "Draft")).post(AWARDS)
            .then().statusCode(201).extract().jsonPath().getLong("id");
        as(employee).delete(AWARDS + "/" + draft).then().statusCode(204);
        assertThat(jdbc.queryForObject("select count(*) from award_versions where award_id = ?", Integer.class,
            draft)).isZero();
        as(admin).get(AWARDS + "/" + draft + "/audit-trail").then().statusCode(200)
            .body("content[0].action", equalTo("DELETE"))
            .body("content[0].entityId", equalTo((int) draft))
            .body("content[0].actorId", equalTo((int) employeeId));
        as(admin).get(AWARDS + "/999999999/audit-trail").then().statusCode(404);
    }

    @Test
    void ac2_6_ac2_7_anAuditorExportsTheTrailAsCsvAndTheExportIsAudited() {
        long id = submitted("=HYPERLINK(\"http://example.com\")");

        Response export = as(tokenOf(ADMIN)).get(AWARDS + "/" + id + "/audit-trail/export");

        export.then().statusCode(200)
            .contentType("text/csv;charset=UTF-8")
            .header("Content-Disposition", equalTo("attachment; filename=\"award-" + id + "-audit-"
                + LocalDate.now(ZoneId.of("Europe/Kyiv")) + ".csv\""));
        byte[] body = export.asByteArray();
        assertThat(body).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String[] lines = new String(body, StandardCharsets.UTF_8).substring(1).split("\r\n");
        assertThat(lines[0]).startsWith("time;actor_id;actor_email;action;");
        assertThat(lines).anySatisfy(line -> assertThat(line).contains(";AWARD_SUBMITTED;awards;" + id + ";"));
        assertThat(jdbc.queryForObject("select (new_values->>'rows')::int from audit_logs "
            + "where action_type = 'AUDIT_EXPORT' and entity_id = ? and user_id = ?", Integer.class, id, adminId))
            .isEqualTo(lines.length - 1);
        as(tokenOf(DEAN)).get(AWARDS + "/" + id + "/audit-trail/export").then().statusCode(403)
            .body("type", equalTo(TYPE_PREFIX + "access-denied"));
    }

    @Test
    void ac1_6_aRoleAssignmentNamesTheAdministratorInTheTriggerRow() {
        as(tokenOf(ADMIN)).contentType(ContentType.JSON)
            .body(Map.of("role", "FACULTY_SECRETARY", "organizationId", TestUsers.FMI_FACULTY_ID))
            .post("/api/v1/users/" + newcomerId + "/roles").then().statusCode(201);

        assertThat(jdbc.queryForObject("select user_id from audit_logs where entity_type = 'user_roles' "
            + "and action_type = 'INSERT' and new_values->>'user_id' = ? order by log_id desc limit 1", Long.class,
            Long.toString(newcomerId))).isEqualTo(adminId);
    }

    private long submitted(String title) {
        return AwardApi.submitted(employee, Map.of("title", title));
    }
}

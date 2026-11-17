package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.support.TestUsers;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonLinkFT extends AbstractFunctionalTest {

    private static final String SECRETARY = "ft.plink.secretary@chnu.edu.ua";
    private static final String OUTSIDER = "ft.plink.outsider@chnu.edu.ua";
    private static final String MEMBER = "ft.plink.member@chnu.edu.ua";
    private static final long OTHER_FACULTY_ID = 10L;
    private static final long OTHER_DEPARTMENT_ID = 65L;
    private static final String TITLE_UK = "titleUk";
    private static final String PERSON_FIELD = "recipientUserId";
    private static final String FIELD_CODE = "errors.find { it.field == '%s' }.code";

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long secretaryId;
    private long memberId;
    private long outsiderId;

    @BeforeAll
    void createUsers() {
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        Organization other = organizationRepository.findById(OTHER_FACULTY_ID).orElseThrow();
        secretaryId = withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        outsiderId = withRole(OUTSIDER, other, RoleType.FACULTY_SECRETARY, other);
        memberId = withRole(MEMBER, department, RoleType.EMPLOYEE, department);
    }

    @AfterAll
    void deleteUsers() {
        String ours = " (select user_id from users where email_address like 'ft.plink.%')";
        jdbc.update("update award_requests set current_reviewer_id = null where current_reviewer_id in" + ours);
        jdbc.update("delete from awards where user_id in" + ours);
        jdbc.update("delete from awards where user_id in (select user_id from users where added_by_id in" + ours
            + ")");
        jdbc.update("delete from user_roles where user_id in (select user_id from users where added_by_id in" + ours
            + ")");
        jdbc.update("delete from users where added_by_id in" + ours);
        jdbc.update("delete from user_roles where user_id in" + ours);
        jdbc.update("delete from users where email_address like 'ft.plink.%'");
    }

    @Test
    void ac4_2_theAwardsMoveTheMembershipIsConfirmedAndThePersonIsScrubbed() {
        String secretary = tokenOf(SECRETARY);
        long registrantId = activeUser("ft.plink.registrant@chnu.edu.ua").getId();
        jdbc.update("update users set organization_id = ? where user_id = ?", OTHER_DEPARTMENT_ID, registrantId);
        long personId = addPerson(secretary, "Мельник");
        long draftId = AwardApi.complete(secretary, Map.of(TITLE_UK, "Чернетка колеги", PERSON_FIELD, personId));
        long pendingId = AwardApi.submitted(secretary, Map.of(TITLE_UK, "Подана колеги", PERSON_FIELD, personId));

        jdbc.update("update award_requests set current_reviewer_id = ?, status = 'IN_REVIEW' where award_id = ?",
            registrantId, pendingId);

        link(secretary, personId, registrantId, version(personId)).statusCode(HttpStatus.OK.value())
            .body("userId", equalTo((int) registrantId))
            .body("movedAwards", equalTo(2));

        assertThat(jdbc.queryForList("select user_id from awards where award_id in (?, ?)", Long.class, draftId,
            pendingId)).containsOnly(registrantId);
        assertThat(jdbc.queryForMap("select submitter_id, current_reviewer_id, status from award_requests"
            + " where award_id = ?", pendingId)).containsEntry("submitter_id", secretaryId)
            .containsEntry("current_reviewer_id", null).containsEntry("status", "SUBMITTED");
        assertThat(jdbc.queryForList("select role_type || ':' || organization_id from user_roles where user_id = ?",
            String.class, registrantId)).containsExactly("EMPLOYEE:" + TestUsers.DAI_DEPARTMENT_ID);
        assertThat(jdbc.queryForObject("select organization_id from users where user_id = ?", Long.class,
            registrantId)).isEqualTo(TestUsers.DAI_DEPARTMENT_ID);
        Map<String, Object> person = jdbc.queryForMap("select account_status, email_address, last_name from users"
            + " where user_id = ?", personId);
        assertThat(person.get("account_status")).isEqualTo("DELETED");
        assertThat((String) person.get("last_name")).doesNotContain("Мельник");
        assertThat(jdbc.queryForObject("select new_values ->> 'movedAwards' from audit_logs"
            + " where action_type = 'ACCOUNT_LINKED' and entity_id = ?", String.class, personId)).isEqualTo("2");
        link(secretary, personId, registrantId, 0L).statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void ac4_4_anAccountWithARoleTakesTheAwardsWithoutANewRole() {
        String secretary = tokenOf(SECRETARY);
        long personId = addPerson(secretary, "Член");
        AwardApi.complete(secretary, Map.of(TITLE_UK, "Нагорода члена", PERSON_FIELD, personId));
        Integer roles = jdbc.queryForObject("select count(*) from user_roles where user_id = ?", Integer.class,
            memberId);

        link(secretary, personId, memberId, version(personId)).statusCode(HttpStatus.OK.value())
            .body("movedAwards", equalTo(1));

        assertThat(jdbc.queryForObject("select count(*) from user_roles where user_id = ?", Integer.class,
            memberId)).isEqualTo(roles);
        as(tokenOf(MEMBER)).queryParam("size", 50).get(AwardApi.AWARDS).then().statusCode(HttpStatus.OK.value())
            .body("content.titleUk", hasItem("Нагорода члена"));
    }

    @Test
    void ac4_3_refusals() {
        String secretary = tokenOf(SECRETARY);
        long personId = addPerson(secretary, "Відмова");
        long pendingId = activeUser("ft.plink.pending@chnu.edu.ua").getId();
        jdbc.update("update users set account_status = 'PENDING' where user_id = ?", pendingId);
        long version = version(personId);

        link(tokenOf(OUTSIDER), personId, memberId, version).statusCode(HttpStatus.NOT_FOUND.value());
        link(secretary, personId, outsiderId, version).statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body(FIELD_CODE.formatted("userId"), equalTo("not-active"));
        link(secretary, personId, Long.MAX_VALUE, version).statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body(FIELD_CODE.formatted("userId"), equalTo("not-active"));
        link(secretary, personId, pendingId, version).statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body(FIELD_CODE.formatted("userId"), equalTo("not-active"));
        link(secretary, personId, personId, version).statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body(FIELD_CODE.formatted("userId"), equalTo("not-active"));
        link(secretary, personId, memberId, version + 1).statusCode(HttpStatus.CONFLICT.value())
            .body("type", equalTo("urn:awards:problem:version-conflict"));
        jdbc.update("update users set account_status = 'ACTIVE' where user_id = ?", personId);
        link(secretary, personId, memberId, version).statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body(FIELD_CODE.formatted("person"), equalTo("claimed"));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'ACCOUNT_LINKED'"
            + " and entity_id = ?", Integer.class, personId)).isZero();
    }

    private static long addPerson(String token, String lastName) {
        return as(token).contentType(ContentType.JSON)
            .body(Map.of("firstName", "Оксана", "lastName", lastName, "organizationId", TestUsers.DAI_DEPARTMENT_ID))
            .post("/api/v1/people").then().statusCode(HttpStatus.CREATED.value())
            .extract().jsonPath().getLong("id");
    }

    private long version(long personId) {
        return jdbc.queryForObject("select version from users where user_id = ?", Long.class, personId);
    }

    private static ValidatableResponse link(String token, long personId, long userId, long version) {
        Map<String, Object> body = new HashMap<>(Map.of("userId", userId, "version", version));
        return as(token).contentType(ContentType.JSON).body(body).post("/api/v1/people/" + personId + "/link").then();
    }
}

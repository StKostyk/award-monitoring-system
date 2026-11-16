package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static ua.edu.chnu.awards.common.web.ApiExceptionHandler.TYPE_PREFIX;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.notification.InvitationMails;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AuthorizationCodeFlow;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.Mailpit;
import ua.edu.chnu.awards.support.TestUsers;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PeopleFT extends AbstractFunctionalTest {

    private static final String PEOPLE = "/api/v1/people";
    private static final String SECRETARY = "ft.people.secretary@chnu.edu.ua";
    private static final String DEAN = "ft.people.dean@chnu.edu.ua";
    private static final String EMPLOYEE = "ft.people.employee@chnu.edu.ua";
    private static final String OUTSIDER = "ft.people.outsider@chnu.edu.ua";
    private static final long OTHER_FACULTY_ID = 10L;
    private static final String ORGANIZATION = "organizationId";
    private static final String TYPE = "type";

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${mailpit.api-url}")
    private String mailpitApiUrl;

    private Mailpit mailpit;
    private long secretaryId;
    private long otherDepartmentId;

    @BeforeAll
    void createUsers() {
        mailpit = new Mailpit(mailpitApiUrl);
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        secretaryId = withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        withRole(DEAN, faculty, RoleType.DEAN, faculty);
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        Organization other = organizationRepository.findById(OTHER_FACULTY_ID).orElseThrow();
        withRole(OUTSIDER, other, RoleType.FACULTY_SECRETARY, other);
        otherDepartmentId = jdbc.queryForObject("select min(org_id) from organizations"
            + " where parent_org_id = ? and org_type = 'DEPARTMENT'", Long.class, OTHER_FACULTY_ID);
    }

    @BeforeEach
    void clearMail() {
        mailpit.clear();
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from awards where user_id in (select user_id from users where added_by_id in"
            + " (select user_id from users where email_address like 'ft.people.%'))");
        jdbc.update("delete from users where added_by_id in"
            + " (select user_id from users where email_address like 'ft.people.%')");
        jdbc.update("delete from user_roles where user_id in"
            + " (select user_id from users where email_address like 'ft.people.%')");
        jdbc.update("delete from users where email_address like 'ft.people.%'");
    }

    @Test
    void ac1_1_ac1_4_aSecretaryAddsAPersonWhoReceivesTheInvitation() {
        String email = "ft.person.invited@chnu.edu.ua";

        int id = add(tokenOf(SECRETARY), person("Олена", "Коваль", email))
            .statusCode(HttpStatus.CREATED.value())
            .body("status", equalTo("UNCLAIMED"))
            .body("email", equalTo(email))
            .body("addedBy.id", equalTo((int) secretaryId))
            .body("invitedAt", notNullValue())
            .body("organization.id", equalTo((int) TestUsers.DAI_DEPARTMENT_ID))
            .body("awardCount", equalTo(0))
            .extract().path("id");

        assertThat(roles(id)).isZero();
        assertThat(audits("PERSON_ADDED", id)).isEqualTo(1);
        assertThat(audits("INVITATION_SENT", id)).isEqualTo(1);
        String text = mailpit.latestTextTo(email, InvitationMails.SUBJECT.substring(0, 10));
        assertThat(text).contains("Test User додав(ла) вас")
            .contains("gdpr@chnu.edu.ua")
            .contains("/register?email=ft.person.invited%40chnu.edu.ua")
            .contains("6(1)(f)");
    }

    @Test
    void ac1_4_withoutAnAddressNoMailGoesAndThePlaceholderIsHidden() {
        int id = add(tokenOf(DEAN), person("Петро", "Без-Адреси", null))
            .statusCode(HttpStatus.CREATED.value())
            .body("email", nullValue())
            .body("invitedAt", nullValue())
            .extract().path("id");

        assertThat(jdbc.queryForObject("select email_address from users where user_id = ?", String.class, id))
            .startsWith("unclaimed-").endsWith("@unclaimed.invalid");
        assertThat(audits("INVITATION_SENT", id)).isZero();
        invite(tokenOf(DEAN), id).statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(TYPE_PREFIX + "no-address"));
    }

    @Test
    void ac1_2_anAddressOfAnAccountIsTaken() {
        final Integer before = jdbc.queryForObject("select count(*) from users", Integer.class);

        add(tokenOf(SECRETARY), person("Іван", "Зайнятий", EMPLOYEE)).statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(TYPE_PREFIX + "email-taken"));

        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(before);
    }

    @Test
    void ac1_2_twoConcurrentAddsOfOneAddressStoreOneRow() {
        String secretary = tokenOf(SECRETARY);
        String dean = tokenOf(DEAN);
        Map<String, Object> body = person("Богдан", "Одночасний", "ft.person.race@chnu.edu.ua");
        body.put("duplicateAcknowledged", true);

        List<Integer> codes = Stream.of(secretary, dean, secretary, dean).parallel()
            .map(token -> add(token, body).extract().statusCode()).toList();

        assertThat(codes).containsOnlyOnce(HttpStatus.CREATED.value())
            .containsOnly(HttpStatus.CREATED.value(), HttpStatus.CONFLICT.value());
        assertThat(jdbc.queryForObject("select count(*) from users where email_address = ?", Integer.class,
            "ft.person.race@chnu.edu.ua")).isEqualTo(1);
    }

    @Test
    void ac1_3_unitsOutsideTheScopeForeignAddressesAndOtherRolesAreRefused() {
        String secretary = tokenOf(SECRETARY);

        add(secretary, withUnit(person("Анна", "Чужа", null), otherDepartmentId))
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors.find { it.field == 'organizationId' }.code", equalTo("out-of-scope"));
        add(secretary, withUnit(person("Анна", "Факультетська", null), TestUsers.FMI_FACULTY_ID))
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors.find { it.field == 'organizationId' }.code", equalTo("out-of-scope"));
        add(secretary, person("Анна", "Ґмейл", "anna@gmail.com"))
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors.find { it.field == 'email' }.code", equalTo("institutional"));
        add(secretary, person(" ", "x".repeat(101), null)).statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors.field", hasItem("firstName")).body("errors.field", hasItem("lastName"));
        add(tokenOf(EMPLOYEE), person("Анна", "Працівниця", null)).statusCode(HttpStatus.FORBIDDEN.value())
            .body(TYPE, equalTo(TYPE_PREFIX + "access-denied"));
    }

    @Test
    void ac1_5_theInvitationGoesAgainAtMostOncePerInterval() {
        String dean = tokenOf(DEAN);
        String email = "ft.person.again@chnu.edu.ua";
        int id = add(dean, person("Марія", "Повторна", email)).statusCode(HttpStatus.CREATED.value())
            .extract().path("id");

        invite(dean, id).statusCode(HttpStatus.TOO_MANY_REQUESTS.value()).header("Retry-After", notNullValue());
        jdbc.update("update users set invited_at = invited_at - interval '2 hours' where user_id = ?", id);
        invite(dean, id).statusCode(HttpStatus.ACCEPTED.value());

        assertThat(mailpit.latestTextTo(email, "", 2)).contains("/register?email=");
        assertThat(audits("INVITATION_SENT", id)).isEqualTo(2);
        invite(tokenOf(OUTSIDER), id).statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void ac1_6_aChangeAuditsTheFieldsAndAFirstAddressReceivesTheInvitation() {
        String secretary = tokenOf(SECRETARY);
        String email = "ft.person.later@chnu.edu.ua";
        Response added = add(secretary, person("Ольга", "Пізня", null)).extract().response();
        int id = added.path("id");
        int version = added.path("version");

        Map<String, Object> changed = person("Ольга", "Пізніша", email);
        changed.put("version", version);
        final int next = change(secretary, id, changed).statusCode(HttpStatus.OK.value())
            .body("lastName", equalTo("Пізніша"))
            .body("email", equalTo(email))
            .body("invitedAt", notNullValue())
            .extract().path("version");

        assertThat(mailpit.latestTextTo(email, "")).contains("Ольга");
        assertThat(jdbc.queryForObject("select new_values->>'fields' from audit_logs"
            + " where action_type = 'PERSON_CHANGED' and entity_id = ?", String.class, id))
            .contains("lastName").contains("email");
        change(secretary, id, changed).statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(TYPE_PREFIX + "version-conflict"));
        Map<String, Object> withoutAddress = person("Ольга", "Пізніша", null);
        withoutAddress.put("version", next);
        change(secretary, id, withoutAddress).statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors.find { it.field == 'email' }.code", equalTo("required"));
    }

    @Test
    void ac1_7_aPersonWithOnlyDraftsIsRemovedAndTheAddressBecomesFree() {
        String secretary = tokenOf(SECRETARY);
        String email = "ft.person.removed@chnu.edu.ua";
        int id = add(secretary, person("Юрій", "Тимчасовий", email)).extract().path("id");
        AwardRows.award(jdbc, id).titleUk("Чернетка").status("DRAFT").insert();

        as(secretary).delete(PEOPLE + "/" + id).then().statusCode(HttpStatus.NO_CONTENT.value());

        assertThat(jdbc.queryForObject("select account_status from users where user_id = ?", String.class, id))
            .isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("select count(*) from awards where user_id = ?", Integer.class, id)).isZero();
        assertThat(audits("PERSON_REMOVED", id)).isEqualTo(1);
        as(secretary).get(PEOPLE + "/" + id).then().statusCode(HttpStatus.NOT_FOUND.value());
        add(secretary, person("Юрій", "Повернений", email)).statusCode(HttpStatus.CREATED.value());
    }

    @Test
    void ac1_7_aPersonWithAnAwardBeyondDraftsStays() {
        String secretary = tokenOf(SECRETARY);
        int id = add(secretary, person("Софія", "Нагороджена", null)).extract().path("id");
        AwardRows.award(jdbc, id).titleUk("Подяка").status("PENDING").insert();

        as(secretary).delete(PEOPLE + "/" + id).then().statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(TYPE_PREFIX + "person-has-awards"));
        as(secretary).get(PEOPLE + "/" + id).then().statusCode(HttpStatus.OK.value())
            .body("awards.size()", equalTo(1)).body("awardCount", equalTo(1));
    }

    @Test
    void ac1_9_aClaimedPersonIsNoLongerShownButChangesStillAnswerClaimed() {
        String secretary = tokenOf(SECRETARY);
        int id = add(secretary, person("Ірина", "Зареєстрована", "ft.person.claimed@chnu.edu.ua"))
            .extract().path("id");
        AwardRows.award(jdbc, id).titleUk("Власна чернетка").status("DRAFT").insert();
        jdbc.update("update users set account_status = 'ACTIVE' where user_id = ?", id);

        as(secretary).get(PEOPLE + "/" + id).then().statusCode(HttpStatus.NOT_FOUND.value());
        invite(secretary, id).statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(TYPE_PREFIX + "person-claimed"));
    }

    @Test
    void ac1_8_aPersonWithoutAnAccountCannotSignInOrHoldARole() {
        String email = "ft.person.nologin@chnu.edu.ua";
        int id = add(tokenOf(SECRETARY), person("Тарас", "Незареєстрований", email)).extract().path("id");
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow();
        flow.authorize();

        assertThat(flow.submitLogin(email, "any-password-1").getHeader("Location"))
            .endsWith("/login?error=BAD_CREDENTIALS");
        as(tokenOf(SECRETARY)).contentType(ContentType.JSON)
            .body(Map.of("role", "EMPLOYEE", ORGANIZATION, TestUsers.DAI_DEPARTMENT_ID))
            .post("/api/v1/users/" + id + "/roles").then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors.find { it.field == 'userId' }.code", equalTo("unclaimed"));
        mailpit.clear();
        RestAssured.given().contentType(ContentType.JSON).body(Map.of("email", email))
            .post("/api/v1/auth/password-reset/request").then().statusCode(HttpStatus.ACCEPTED.value());
        assertThat(mailpit.messagesTo(email)).isEmpty();
    }

    @Test
    void ac1_9_theListShowsThePeopleOfTheScopeWithMaskedAddresses() {
        add(tokenOf(SECRETARY), person("Василь", "Списковий", "ft.person.listed@chnu.edu.ua"))
            .statusCode(HttpStatus.CREATED.value());

        as(tokenOf(DEAN)).queryParam("q", "Списков").get(PEOPLE).then().statusCode(HttpStatus.OK.value())
            .body("content[0].email", equalTo("f***@chnu.edu.ua"))
            .body("content[0].status", equalTo("UNCLAIMED"));
        as(tokenOf(OUTSIDER)).queryParam("q", "Списков").get(PEOPLE).then().statusCode(HttpStatus.OK.value())
            .body("content.size()", equalTo(0));
        as(tokenOf(EMPLOYEE)).get(PEOPLE).then().statusCode(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void ac1_10_aPersonOfTheSameNameNeedsTheAcknowledgement() {
        String secretary = tokenOf(SECRETARY);
        add(secretary, person("Ірина", "Д'якова", "ft.person.first@chnu.edu.ua"))
            .statusCode(HttpStatus.CREATED.value());

        add(secretary, person(" ірина ", "ДЯКОВА", null)).statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(TYPE_PREFIX + "possible-duplicate"))
            .body("matches[0].email", equalTo("f***@chnu.edu.ua"))
            .body("matches[0].awardCount", equalTo(0));
        Map<String, Object> acknowledged = person("Ірина", "Дякова", null);
        acknowledged.put("duplicateAcknowledged", true);
        add(secretary, acknowledged).statusCode(HttpStatus.CREATED.value());
    }

    private static Map<String, Object> person(String firstName, String lastName, String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("email", email);
        body.put(ORGANIZATION, TestUsers.DAI_DEPARTMENT_ID);
        return body;
    }

    private static Map<String, Object> withUnit(Map<String, Object> body, long organizationId) {
        body.put(ORGANIZATION, organizationId);
        return body;
    }

    private static ValidatableResponse add(String token, Map<String, Object> body) {
        return as(token).contentType(ContentType.JSON).body(body).post(PEOPLE).then();
    }

    private static ValidatableResponse change(String token, int id, Map<String, Object> body) {
        return as(token).contentType(ContentType.JSON).body(body).put(PEOPLE + "/" + id).then();
    }

    private static ValidatableResponse invite(String token, int id) {
        return as(token).post(PEOPLE + "/" + id + "/invitation").then();
    }

    private Integer roles(int id) {
        return jdbc.queryForObject("select count(*) from user_roles where user_id = ?", Integer.class, id);
    }

    private Integer audits(String action, int id) {
        return jdbc.queryForObject("select count(*) from audit_logs where action_type = ? and entity_id = ?",
            Integer.class, action, id);
    }
}

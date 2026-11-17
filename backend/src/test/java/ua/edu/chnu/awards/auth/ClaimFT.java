package ua.edu.chnu.awards.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AuthorizationCodeFlow;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.support.Mailpit;
import ua.edu.chnu.awards.support.TestUsers;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClaimFT extends AbstractFunctionalTest {

    private static final String SECRETARY = "ft.claim.secretary@chnu.edu.ua";
    private static final String PASSWORD = "claim-horse-battery-staple";
    private static final String VERIFY_SUBJECT = "Підтвердження адреси";
    private static final String TITLE_UK = "titleUk";
    private static final String VERSION = "version";
    private static final long OTHER_DEPARTMENT_ID = 65L;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long secretaryId;

    @BeforeAll
    void createSecretary() {
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        secretaryId = withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
    }

    @AfterAll
    void deleteUsers() {
        String ours = " (select user_id from users where email_address like 'ft.claim.%')";
        jdbc.update("update award_requests set current_reviewer_id = null where current_reviewer_id in" + ours);
        jdbc.update("delete from awards where user_id in" + ours);
        jdbc.update("delete from user_roles where user_id in" + ours);
        jdbc.queryForList("select user_id from users where email_address like 'ft.claim.%'", Long.class)
            .forEach(id -> ConsentRows.purge(jdbc, id));
        jdbc.update("delete from users where email_address like 'ft.claim.%' and added_by_id is not null");
        jdbc.update("delete from users where email_address like 'ft.claim.%'");
    }

    @Test
    void ac3_1_ac3_2_ac3_3_thePersonClaimsTheRowAndTakesOverTheAwards() {
        String email = "ft.claim.person@chnu.edu.ua";
        String secretary = tokenOf(SECRETARY);
        long personId = addPerson(secretary, email);
        final long draftId = AwardApi.complete(secretary, Map.of(TITLE_UK, "Чернетка колеги", "recipientUserId",
            personId));
        final long pendingId = AwardApi.submitted(secretary, Map.of(TITLE_UK, "Подана нагорода колеги",
            "recipientUserId", personId));
        final long approvedId = AwardApi.complete(secretary, Map.of(TITLE_UK, "Затверджена нагорода колеги",
            "recipientUserId", personId));
        jdbc.update("update awards set status = 'APPROVED' where award_id = ?", approvedId);
        mailpit.clear();

        register(email, "Інше", "Ім'я", OTHER_DEPARTMENT_ID).then().statusCode(HttpStatus.CREATED.value())
            .body("email", equalTo(email))
            .body("status", equalTo("PENDING"))
            .body("waitingAwards", equalTo(0));
        Map<String, Object> row = jdbc.queryForMap("select user_id, first_name, organization_id, account_status"
            + " from users where email_address = ?", email);
        assertThat(row).containsEntry("user_id", personId).containsEntry("first_name", "Петро")
            .containsEntry("organization_id", TestUsers.DAI_DEPARTMENT_ID).containsEntry("account_status", "PENDING");
        assertThat(jdbc.queryForObject("select new_values ->> 'claim' from audit_logs"
            + " where action_type = 'REGISTRATION_REPLACED' and entity_id = ?", String.class, personId))
            .isEqualTo("true");
        as(secretary).get(AwardApi.AWARDS + "/" + draftId).then().statusCode(HttpStatus.OK.value())
            .body("managedByCaller", equalTo(true));
        register(email, "Ще", "Раз", TestUsers.DAI_DEPARTMENT_ID).then().statusCode(HttpStatus.CONFLICT.value())
            .body("type", equalTo("urn:awards:problem:email-taken"));

        verify(tokenIn(email), PASSWORD).then().statusCode(HttpStatus.OK.value())
            .body("status", equalTo("ACTIVE"))
            .body("waitingAwards", equalTo(3));
        assertThat(jdbc.queryForList("select role_type || ':' || organization_id || ':' || created_by"
            + " from user_roles where user_id = ?", String.class, personId))
            .containsExactly("EMPLOYEE:" + TestUsers.DAI_DEPARTMENT_ID + ":" + secretaryId);
        assertThat(jdbc.queryForObject("select new_values ->> 'awards' from audit_logs"
            + " where action_type = 'ACCOUNT_CLAIMED' and entity_id = ?", String.class, personId)).isEqualTo("3");

        String person = tokenWith(email);
        as(person).queryParam("size", 50).get(AwardApi.AWARDS).then().statusCode(HttpStatus.OK.value())
            .body("content.id", hasItems((int) draftId, (int) pendingId, (int) approvedId));
        as(person).get(AwardApi.AWARDS + "/" + draftId).then().statusCode(HttpStatus.OK.value())
            .body("ownerWithoutAccount", equalTo(false));
        as(person).contentType(ContentType.JSON).body(Map.of("visibility", "UNIVERSITY"))
            .put(AwardApi.AWARDS + "/" + approvedId + "/visibility").then().statusCode(HttpStatus.OK.value());
        as(secretary).contentType(ContentType.JSON).body(Map.of(VERSION, AwardApi.version(person, pendingId)))
            .post(AwardApi.AWARDS + "/" + pendingId + "/withdraw").then()
            .statusCode(HttpStatus.NOT_FOUND.value());
        as(secretary).contentType(ContentType.JSON).body(edit("Змінено секретарем", person, draftId))
            .put(AwardApi.AWARDS + "/" + draftId).then().statusCode(HttpStatus.NOT_FOUND.value());
        as(person).contentType(ContentType.JSON).body(Map.of(VERSION, AwardApi.version(person, pendingId)))
            .post(AwardApi.AWARDS + "/" + pendingId + "/withdraw").then().statusCode(HttpStatus.OK.value())
            .body("status", equalTo("DRAFT"));
        as(person).contentType(ContentType.JSON).body(edit("Змінено власником", person, draftId))
            .put(AwardApi.AWARDS + "/" + draftId).then().statusCode(HttpStatus.OK.value());
        as(person).get("/api/v1/users/me").then().statusCode(HttpStatus.OK.value())
            .body("status", equalTo("ACTIVE"))
            .body("firstName", equalTo("Петро"))
            .body("membershipConfirmed", equalTo(true));
    }

    @Test
    void ac3_4_anExpiredClaimIsStillManagedAndTheNextRegistrationClaimsAgain() {
        String email = "ft.claim.abandoned@chnu.edu.ua";
        String secretary = tokenOf(SECRETARY);
        long personId = addPerson(secretary, email);
        long draftId = AwardApi.complete(secretary, Map.of(TITLE_UK, "Нагорода покинутого", "recipientUserId",
            personId));
        register(email, "Перша", "Спроба", TestUsers.DAI_DEPARTMENT_ID).then().statusCode(HttpStatus.CREATED.value());
        jdbc.update("update one_time_tokens set expires_at = now() - interval '1 hour'"
            + " where purpose = 'EMAIL_VERIFICATION' and user_id = ?", personId);

        as(secretary).get(AwardApi.AWARDS + "/" + draftId).then().statusCode(HttpStatus.OK.value())
            .body("ownerWithoutAccount", equalTo(true))
            .body("managedByCaller", equalTo(true));
        mailpit.clear();
        register(email, "Друга", "Спроба", OTHER_DEPARTMENT_ID).then().statusCode(HttpStatus.CREATED.value())
            .body("status", equalTo("PENDING"));

        assertThat(jdbc.queryForObject("select first_name from users where user_id = ?", String.class, personId))
            .isEqualTo("Петро");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'REGISTRATION_REPLACED'"
            + " and entity_id = ? and new_values ->> 'claim' = 'true'", Integer.class, personId)).isEqualTo(2);
        verify(tokenIn(email), PASSWORD).then().statusCode(HttpStatus.OK.value()).body("waitingAwards", equalTo(1));
    }

    private static long addPerson(String token, String email) {
        return as(token).contentType(ContentType.JSON)
            .body(Map.of("firstName", "Петро", "lastName", "Заявлений", "email", email,
                "organizationId", TestUsers.DAI_DEPARTMENT_ID))
            .post("/api/v1/people").then().statusCode(HttpStatus.CREATED.value())
            .extract().jsonPath().getLong("id");
    }

    private static Map<String, Object> edit(String title, String token, long id) {
        return new HashMap<>(Map.of(TITLE_UK, title, "categoryId", AwardApi.MINISTRY_CATEGORY,
            "awardingOrganization", "МОН України", "awardDate", "2025-05-05", VERSION, AwardApi.version(token, id)));
    }

    private String tokenIn(String email) {
        String link = Mailpit.linkIn(mailpit.latestTextTo(email, VERIFY_SUBJECT));
        return UriComponentsBuilder.fromUri(URI.create(link)).build().getQueryParams().getFirst("token");
    }

    private static String tokenWith(String email) {
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow();
        return flow.exchange(flow.loginAndGetCode(email, PASSWORD)).jsonPath().getString("access_token");
    }

    private static Response register(String email, String firstName, String lastName, long organizationId) {
        return RestAssured.given().contentType(ContentType.JSON)
            .body(Map.of("email", email, "password", PASSWORD, "firstName", firstName, "lastName", lastName,
                "organizationId", organizationId, "privacyNoticeVersion", "1.0"))
            .post("/api/v1/auth/register");
    }

    private static Response verify(String token, String password) {
        return RestAssured.given().contentType(ContentType.JSON).body(Map.of("token", token, "password", password))
            .post("/api/v1/auth/verify-email");
    }
}

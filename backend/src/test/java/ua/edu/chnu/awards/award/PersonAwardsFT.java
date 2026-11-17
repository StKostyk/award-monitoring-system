package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.support.TestUsers;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonAwardsFT extends AbstractFunctionalTest {

    private static final String SECRETARY = "ft.pawards.secretary@chnu.edu.ua";
    private static final String DEAN = "ft.pawards.dean@chnu.edu.ua";
    private static final String OFFICE = "ft.pawards.office@chnu.edu.ua";
    private static final String OUTSIDER = "ft.pawards.outsider@chnu.edu.ua";
    private static final String EMPLOYEE = "ft.pawards.employee@chnu.edu.ua";
    private static final String PERSON_EMAIL = "ft.pawards.person@chnu.edu.ua";
    private static final long OTHER_FACULTY_ID = 10L;
    private static final String TYPE = "type";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final String TITLE_UK = "titleUk";
    private static final String PERSON_FIELD = "recipientUserId";
    private static final String VERSION = "version";

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long secretaryId;
    private long deanId;
    private long personId;
    private long employeeId;

    @BeforeAll
    void createUsers() {
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        final Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        final Organization other = organizationRepository.findById(OTHER_FACULTY_ID).orElseThrow();
        secretaryId = withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        deanId = withRole(DEAN, faculty, RoleType.DEAN, faculty);
        withRole(OFFICE, university, RoleType.RECTOR_SECRETARY, university);
        withRole(OUTSIDER, other, RoleType.FACULTY_SECRETARY, other);
        employeeId = withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
    }

    @BeforeEach
    void addPersonAndClearMail() {
        if (personId == 0) {
            personId = as(tokenOf(SECRETARY)).contentType(ContentType.JSON)
                .body(Map.of("firstName", "Петро", "lastName", "Тестовий", "email", PERSON_EMAIL,
                    "organizationId", TestUsers.DAI_DEPARTMENT_ID))
                .post("/api/v1/people").then().statusCode(HttpStatus.CREATED.value())
                .extract().jsonPath().getLong("id");
        }
        mailpit.clear();
    }

    @AfterAll
    void deleteUsers() {
        String ours = " (select user_id from users where email_address like 'ft.pawards.%')";
        jdbc.update("update award_requests set current_reviewer_id = null where current_reviewer_id in" + ours);
        jdbc.update("delete from review_decisions where reviewer_id in" + ours);
        jdbc.update("delete from awards where user_id in" + ours);
        jdbc.update("delete from user_roles where user_id in" + ours);
        jdbc.update("delete from users where email_address like 'ft.pawards.%'");
    }

    @Test
    void ac2_2_aSecretaryCreatesADraftOfThePersonInThePersonsDepartment() {
        long id = AwardApi.complete(tokenOf(SECRETARY), Map.of(TITLE_UK, "Грамота колеги", PERSON_FIELD, personId));

        as(tokenOf(SECRETARY)).get(AwardApi.AWARDS + "/" + id).then().statusCode(HttpStatus.OK.value())
            .body("owner.id", equalTo((int) personId))
            .body("organization.id", equalTo((int) TestUsers.DAI_DEPARTMENT_ID))
            .body("ownerWithoutAccount", equalTo(true))
            .body("managedByCaller", equalTo(true));
        assertThat(jdbc.queryForObject("select user_id from awards where award_id = ?", Long.class, id))
            .isEqualTo(personId);
    }

    @Test
    void ac2_2_anotherScopeAnAccountOrBothRecipientsAreRefused() {
        create(OUTSIDER, Map.of(TITLE_UK, "Чужа", PERSON_FIELD, personId))
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors.find { it.field == 'recipientUserId' }.code", equalTo("out-of-scope"));
        create(SECRETARY, Map.of(TITLE_UK, "Працівник з обліковим записом", PERSON_FIELD, employeeId))
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors.find { it.field == 'recipientUserId' }.code", equalTo("out-of-scope"));
        create(SECRETARY, Map.of(TITLE_UK, "Обидва", PERSON_FIELD, personId,
            "recipientOrganizationId", TestUsers.DAI_DEPARTMENT_ID))
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors.find { it.field == 'recipientUserId' }.code", equalTo("one-recipient"));
        create(EMPLOYEE, Map.of(TITLE_UK, "Працівник", PERSON_FIELD, personId))
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value());
    }

    @Test
    void ac2_3_theSecretaryEditsSubmitsAndWithdrawsAndOthersStillGet404() {
        String secretary = tokenOf(SECRETARY);
        long id = AwardApi.complete(secretary, Map.of(TITLE_UK, "Подяка колеги", PERSON_FIELD, personId));

        as(tokenOf(OUTSIDER)).get(AwardApi.AWARDS + "/" + id).then().statusCode(HttpStatus.NOT_FOUND.value());
        as(tokenOf(EMPLOYEE)).get(AwardApi.AWARDS + "/" + id).then().statusCode(HttpStatus.NOT_FOUND.value());
        Map<String, Object> form = new HashMap<>(Map.of(TITLE_UK, "Подяка колеги, змінена", "categoryId",
            AwardApi.MINISTRY_CATEGORY, "awardingOrganization", "МОН України", "awardDate",
            LocalDate.now(AwardApi.KYIV).minusYears(1).toString(), VERSION, AwardApi.version(secretary, id)));
        as(secretary).contentType(ContentType.JSON).body(form).put(AwardApi.AWARDS + "/" + id).then()
            .statusCode(HttpStatus.OK.value()).body(TITLE_UK, equalTo("Подяка колеги, змінена"));
        form.put("recipientOrganizationId", TestUsers.DAI_DEPARTMENT_ID);
        form.put(VERSION, AwardApi.version(secretary, id));
        as(secretary).contentType(ContentType.JSON).body(form).put(AwardApi.AWARDS + "/" + id).then()
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors.find { it.field == 'recipientUserId' }.code", equalTo("one-recipient"));

        AwardApi.submit(secretary, id, AwardApi.version(secretary, id), true).then()
            .statusCode(HttpStatus.OK.value()).body("status", equalTo("PENDING"));
        assertThat(jdbc.queryForObject("select submitter_id from award_requests where award_id = ?", Long.class,
            id)).isEqualTo(secretaryId);
        assertThat(jdbc.queryForObject("select user_id from audit_logs where action_type = 'AWARD_SUBMITTED'"
            + " and entity_id = ?", Long.class, id)).isEqualTo(secretaryId);
        assertThat(jdbc.queryForList("select distinct actor_id from award_versions where award_id = ?", Long.class,
            id)).containsExactly(secretaryId);

        as(secretary).contentType(ContentType.JSON).body(Map.of(VERSION, AwardApi.version(secretary, id)))
            .post(AwardApi.AWARDS + "/" + id + "/withdraw").then().statusCode(HttpStatus.OK.value())
            .body("status", equalTo("DRAFT"));
        assertThat(jdbc.queryForObject("select user_id from audit_logs where action_type = 'AWARD_WITHDRAWN'"
            + " and entity_id = ?", Long.class, id)).isEqualTo(secretaryId);
    }

    @Test
    void ac2_3_theSecretaryDeletesTheDraftAndAnOutsiderCannot() {
        long id = AwardApi.complete(tokenOf(SECRETARY), Map.of(TITLE_UK, "Видалити", PERSON_FIELD, personId));

        as(tokenOf(OUTSIDER)).delete(AwardApi.AWARDS + "/" + id).then().statusCode(HttpStatus.NOT_FOUND.value());
        as(tokenOf(SECRETARY)).delete(AwardApi.AWARDS + "/" + id).then()
            .statusCode(HttpStatus.NO_CONTENT.value());
    }

    @Test
    void ac2_4_ac2_5_ac2_6_theSubmitterIsToldAndThePersonIsNot() {
        String secretary = tokenOf(SECRETARY);
        long id = AwardApi.submitted(secretary, Map.of(TITLE_UK, "Відзнака колеги", PERSON_FIELD, personId));

        as(secretary).get("/api/v1/reviews").then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", not(hasItem((int) id)));
        decide(DEAN, id, "RETURN", "Додайте дату").then().statusCode(HttpStatus.OK.value());
        assertThat(mailpit.latestTextTo(SECRETARY, "Нагороду повернуто"))
            .contains("Нагорода колеги Тестовий Петро «Відзнака колеги»")
            .contains("Award of your colleague Тестовий Петро");
        assertThat(jdbc.queryForObject("select payload ->> 'person' from notifications where user_id = ?"
            + " and reference_id = ? and channel = 'IN_APP' and type = 'AWARD_RETURNED'", String.class,
            secretaryId, id))
            .isEqualTo("Тестовий Петро");

        AwardApi.submit(secretary, id, AwardApi.version(secretary, id), true).then()
            .statusCode(HttpStatus.OK.value());
        approveToTheEnd(id);

        assertThat(mailpit.messagesTo(PERSON_EMAIL, "Нагороду")).isEmpty();
        as(secretary).get(AwardApi.AWARDS + "/" + id).then().statusCode(HttpStatus.OK.value())
            .body("status", equalTo("APPROVED")).body("visibility", equalTo("PRIVATE"));
        as(secretary).contentType(ContentType.JSON).body(Map.of("visibility", "PUBLIC"))
            .put(AwardApi.AWARDS + "/" + id + "/visibility").then().statusCode(HttpStatus.NOT_FOUND.value());
        as(tokenOf(EMPLOYEE)).queryParam("year", LocalDate.now(AwardApi.KYIV).minusYears(1).getYear())
            .get("/api/v1/achievements").then().statusCode(HttpStatus.OK.value())
            .body("content.id", not(hasItem((int) id)));
    }

    @Test
    void ac2_3_ac2_4_aResubmissionByAnotherManagerMakesThemTheSubmitter() {
        String secretary = tokenOf(SECRETARY);
        String dean = tokenOf(DEAN);
        long id = AwardApi.submitted(secretary, Map.of(TITLE_UK, "Повторна подача", PERSON_FIELD, personId));
        decide(DEAN, id, "RETURN", "Уточніть назву").then().statusCode(HttpStatus.OK.value());

        AwardApi.submit(dean, id, AwardApi.version(dean, id), true).then().statusCode(HttpStatus.OK.value());

        assertThat(jdbc.queryForObject("select submitter_id from award_requests where award_id = ?", Long.class,
            id)).isEqualTo(deanId);
        as(dean).get("/api/v1/reviews").then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", not(hasItem((int) id)));
    }

    private void approveToTheEnd(long id) {
        for (String reviewer : List.of(DEAN, DEAN, OFFICE)) {
            String status = jdbc.queryForObject("select status from awards where award_id = ?", String.class, id);
            if ("APPROVED".equals(status)) {
                return;
            }
            String level = jdbc.queryForObject("select current_level from award_requests where award_id = ?",
                String.class, id);
            decide("RECTOR_SECRETARY".equals(level) ? OFFICE : reviewer, id, "APPROVE", null).then()
                .statusCode(HttpStatus.OK.value());
        }
    }

    private static ValidatableResponse create(String email, Map<String, Object> fields) {
        Map<String, Object> body = new HashMap<>(fields);
        return as(tokenOf(email)).contentType(ContentType.JSON).body(body).post(AwardApi.AWARDS).then();
    }

    private Response decide(String email, long id, String decision, String comment) {
        Map<String, Object> body = new HashMap<>(Map.of("decision", decision, "requestVersion",
            jdbc.queryForObject("select version from award_requests where award_id = ?", Long.class, id)));
        if (comment != null) {
            body.put("comment", comment);
        }
        return as(tokenOf(email)).contentType(ContentType.JSON).body(body)
            .post(AwardApi.AWARDS + "/" + id + "/decisions");
    }
}

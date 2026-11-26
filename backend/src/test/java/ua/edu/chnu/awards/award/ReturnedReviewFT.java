package ua.edu.chnu.awards.award;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReturnedReviewFT extends AbstractFunctionalTest {

    private static final String SECRETARY = "ft.returned.secretary@chnu.edu.ua";
    private static final String COLLEAGUE = "ft.returned.secretary2@chnu.edu.ua";
    private static final String DEAN = "ft.returned.dean@chnu.edu.ua";
    private static final String EMPLOYEE = "ft.returned.employee@chnu.edu.ua";
    private static final String DEPUTY = "ft.returned.deputy@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(SECRETARY, COLLEAGUE, DEAN, EMPLOYEE, DEPUTY);
    private static final String RETURNED = "/api/v1/reviews/returned";
    private static final String REVIEWS = "/api/v1/reviews";
    private static final long FACULTY_ID = 10L;
    private static final long OTHER_FACULTY_ID = 9L;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long secretaryId;
    private long colleagueId;

    @BeforeAll
    void createUsers() {
        Organization faculty = organizationRepository.findById(FACULTY_ID).orElseThrow();
        long departmentId = jdbc.queryForObject("select min(org_id) from organizations where parent_org_id = ?"
            + " and org_type = 'DEPARTMENT' and is_active", Long.class, FACULTY_ID);
        final Organization department = organizationRepository.findById(departmentId).orElseThrow();
        secretaryId = withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        colleagueId = withRole(COLLEAGUE, faculty, RoleType.FACULTY_SECRETARY, faculty);
        withRole(DEAN, faculty, RoleType.DEAN, faculty);
        withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        long deputyId = withRole(DEPUTY, department, RoleType.EMPLOYEE, department);
        jdbc.update("insert into role_delegations (delegator_id, delegate_id, organization_id, role_type, valid_from,"
            + " valid_to, reason) values (?, ?, ?, 'FACULTY_SECRETARY', ?, ?, 'Leave')", secretaryId, deputyId,
            FACULTY_ID, Date.valueOf(LocalDate.now().minusDays(1)), Date.valueOf(LocalDate.now().plusDays(7)));
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from role_delegations where delegate_id in"
            + " (select user_id from users where email_address like 'ft.returned.%')");
        jdbc.update("update award_requests set current_reviewer_id = null where current_reviewer_id in"
            + " (select user_id from users where email_address like 'ft.returned.%')");
        jdbc.update("delete from review_decisions where reviewer_id in"
            + " (select user_id from users where email_address like 'ft.returned.%')");
        jdbc.update("delete from awards where user_id in"
            + " (select user_id from users where email_address like 'ft.returned.%')");
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac1_aReturnedAwardIsListedWithWhoReturnedItWhenAndWhy() {
        long id = submitted("Грамота на доопрацюванні");

        giveBack(tokenOf(SECRETARY), id, "Додайте номер наказу");

        as(tokenOf(COLLEAGUE)).get(RETURNED).then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", hasItem((int) id))
            .body(item(id) + ".titleUk", equalTo("Грамота на доопрацюванні"))
            .body(item(id) + ".recipient", notNullValue())
            .body(item(id) + ".organization.id", notNullValue())
            .body(item(id) + ".level", equalTo("FACULTY_SECRETARY"))
            .body(item(id) + ".returnedBy.id", equalTo((int) secretaryId))
            .body(item(id) + ".returnedFor", nullValue())
            .body(item(id) + ".returnedAt", notNullValue())
            .body(item(id) + ".comment", equalTo("Додайте номер наказу"))
            .body(item(id) + ".daysWaiting", equalTo(0));
        as(tokenOf(COLLEAGUE)).get(REVIEWS).then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", not(hasItem((int) id)));
    }

    @Test
    void ac2_aResubmittedAwardLeavesTheTabAndTheLatestReturnIsShown() {
        long id = submitted("Грамота, повернута двічі");
        String employee = tokenOf(EMPLOYEE);
        giveBack(tokenOf(SECRETARY), id, "Перша причина");

        AwardApi.submit(employee, id, AwardApi.version(employee, id), null).then()
            .statusCode(HttpStatus.OK.value());

        as(tokenOf(SECRETARY)).get(RETURNED).then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", not(hasItem((int) id)));
        as(tokenOf(SECRETARY)).get(REVIEWS).then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", hasItem((int) id));

        giveBack(tokenOf(COLLEAGUE), id, "Друга причина");

        as(tokenOf(SECRETARY)).get(RETURNED).then().statusCode(HttpStatus.OK.value())
            .body(item(id) + ".comment", equalTo("Друга причина"))
            .body(item(id) + ".returnedBy.id", equalTo((int) colleagueId));
    }

    @Test
    void ac3_theTabFollowsTheScopeAndDelegationOfTheQueue() {
        long id = submitted("Грамота для заступника");
        giveBack(tokenOf(SECRETARY), id, "Уточніть дату");

        as(tokenOf(DEPUTY)).get(RETURNED).then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", hasItem((int) id));
        as(tokenOf(DEAN)).get(RETURNED).then().statusCode(HttpStatus.OK.value())
            .body("content.awardId", not(hasItem((int) id)));
        as(tokenOf(EMPLOYEE)).get(RETURNED).then().statusCode(HttpStatus.FORBIDDEN.value());
        as(tokenOf(SECRETARY)).queryParam("organizationId", OTHER_FACULTY_ID).get(RETURNED).then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("parameter", equalTo("organizationId"));
    }

    @Test
    void ac3_ac4_theCountComesWithAPageOfOne() {
        long first = submitted("Перша повернута");
        long second = submitted("Друга повернута");
        String secretary = tokenOf(SECRETARY);
        giveBack(secretary, first, "Причина один");
        giveBack(secretary, second, "Причина два");

        as(secretary).queryParam("size", 1).get(RETURNED).then().statusCode(HttpStatus.OK.value())
            .body("content", hasSize(1))
            .body("totalElements", greaterThanOrEqualTo(2));
        as(secretary).queryParam("organizationId", FACULTY_ID).get(RETURNED).then()
            .statusCode(HttpStatus.OK.value())
            .body("content.awardId", hasItem((int) second));
    }

    private long submitted(String title) {
        return AwardApi.submitted(tokenOf(EMPLOYEE), Map.of("titleUk", title,
            "awardDate", LocalDate.now(AwardApi.KYIV).minusMonths(2).toString()));
    }

    private void giveBack(String token, long awardId, String comment) {
        long version = jdbc.queryForObject("select version from award_requests where award_id = ?", Long.class,
            awardId);
        Response response = as(token).contentType(ContentType.JSON)
            .body(Map.of("decision", "RETURN", "requestVersion", version, "comment", comment))
            .post(AwardApi.AWARDS + "/" + awardId + "/decisions");
        response.then().statusCode(HttpStatus.OK.value());
    }

    private static String item(long awardId) {
        return "content.find { it.awardId == " + awardId + " }";
    }
}

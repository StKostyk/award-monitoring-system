package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static ua.edu.chnu.awards.common.web.ApiExceptionHandler.TYPE_PREFIX;

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

import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnitAwardFT extends AbstractFunctionalTest {

    private static final String SECRETARY = "ft.unit.secretary@chnu.edu.ua";
    private static final String SECOND_SECRETARY = "ft.unit.secretary2@chnu.edu.ua";
    private static final String DEAN = "ft.unit.dean@chnu.edu.ua";
    private static final String EMPLOYEE = "ft.unit.employee@chnu.edu.ua";
    private static final String OUTSIDER = "ft.unit.outsider@chnu.edu.ua";
    private static final String LEAVING = "ft.unit.leaving@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(SECRETARY, SECOND_SECRETARY, DEAN, EMPLOYEE, OUTSIDER,
        LEAVING);
    private static final String AWARDS = AwardApi.AWARDS;
    private static final String UNITS = AWARDS + "/recipient-units";
    private static final String RECIPIENT = "recipientOrganizationId";
    private static final long OTHER_FACULTY_ID = 10L;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long secretaryId;
    private long leavingId;

    @BeforeAll
    void createUsers() {
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        secretaryId = withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        withRole(SECOND_SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        withRole(DEAN, faculty, RoleType.DEAN, faculty);
        leavingId = withRole(LEAVING, faculty, RoleType.FACULTY_SECRETARY, faculty);
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        Organization other = organizationRepository.findById(OTHER_FACULTY_ID).orElseThrow();
        withRole(OUTSIDER, other, RoleType.FACULTY_SECRETARY, other);
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from awards where user_id in"
            + " (select user_id from users where email_address like 'ft.unit.%')");
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac0_1_aSecretaryListsHerFacultyFirstAndAnEmployeeNothing() {
        as(tokenOf(SECRETARY)).get(UNITS).then().statusCode(HttpStatus.OK.value())
            .body("[0].id", equalTo((int) TestUsers.FMI_FACULTY_ID))
            .body("[0].type", equalTo("FACULTY"))
            .body("id", hasItem((int) TestUsers.DAI_DEPARTMENT_ID))
            .body("id", not(hasItem((int) OTHER_FACULTY_ID)))
            .body("type.unique()", contains("FACULTY", "DEPARTMENT"));
        as(tokenOf(EMPLOYEE)).get(UNITS).then().statusCode(HttpStatus.OK.value()).body("$", empty());
    }

    @Test
    void ac0_3_unitsOutsideTheScopeAreRefusedAndNothingIsSaved() {
        final Integer before = jdbc.queryForObject("select count(*) from awards", Integer.class);

        refused(tokenOf(EMPLOYEE), TestUsers.DAI_DEPARTMENT_ID);
        refused(tokenOf(SECRETARY), OTHER_FACULTY_ID);
        refused(tokenOf(SECRETARY), TestUsers.UNIVERSITY_ID);

        assertThat(jdbc.queryForObject("select count(*) from awards", Integer.class)).isEqualTo(before);
    }

    @Test
    void ac0_2_ac0_4_ac0_7_aDepartmentAwardStaysWithTheDepartmentAndStartsAtTheSecretaries() {
        String secretary = tokenOf(SECRETARY);
        long id = AwardApi.complete(secretary, Map.of("titleUk", "Грамота кафедрі алгебри",
            RECIPIENT, TestUsers.DAI_DEPARTMENT_ID));

        as(secretary).get(AWARDS + "/" + id).then().statusCode(HttpStatus.OK.value())
            .body("recipient.type", equalTo("UNIT"))
            .body("recipient.organization.id", equalTo((int) TestUsers.DAI_DEPARTMENT_ID))
            .body("organization.id", equalTo((int) TestUsers.DAI_DEPARTMENT_ID));
        AwardApi.submit(secretary, id, AwardApi.version(secretary, id), true).then()
            .statusCode(HttpStatus.OK.value())
            .body("recipient.type", equalTo("UNIT"))
            .body("organization.id", equalTo((int) TestUsers.DAI_DEPARTMENT_ID))
            .body("request.currentLevel", equalTo("FACULTY_SECRETARY"));

        Map<String, Object> row = jdbc.queryForMap("select a.user_id, a.organization_id, a.recipient_org_id,"
            + " r.submitter_id from awards a join award_requests r using (award_id) where a.award_id = ?", id);
        assertThat(row).containsEntry("user_id", secretaryId).containsEntry("submitter_id", secretaryId)
            .containsEntry("organization_id", TestUsers.DAI_DEPARTMENT_ID)
            .containsEntry("recipient_org_id", TestUsers.DAI_DEPARTMENT_ID);
        as(secretary).get(AWARDS + "?status=PENDING").then().statusCode(HttpStatus.OK.value())
            .body("content.find { it.id == " + id + " }.recipient.organization.nameUk",
                equalTo(organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow().getNameUk()));
    }

    @Test
    void ac0_6_theOnlySecretaryOfAFacultyStartsHerOwnAwardAtTheDean() {
        String outsider = tokenOf(OUTSIDER);

        long unit = AwardApi.submitted(outsider, Map.of("titleUk", "Подяка факультету", RECIPIENT, OTHER_FACULTY_ID));
        long personal = AwardApi.submitted(outsider, Map.of("titleUk", "Особиста подяка"));

        assertThat(levelOf(unit)).isEqualTo("DEAN");
        assertThat(levelOf(personal)).isEqualTo("DEAN");
    }

    @Test
    void ac0_4_aUnitDraftIsNotSubmittedOnceTheSecretaryRoleIsGone() {
        long id = AwardApi.complete(tokenOf(LEAVING), Map.of("titleUk", "Грамота факультету",
            RECIPIENT, TestUsers.FMI_FACULTY_ID));
        jdbc.update("update user_roles set role_type = 'EMPLOYEE', organization_id = ? where user_id = ?",
            TestUsers.DAI_DEPARTMENT_ID, leavingId);
        String withoutRole = tokenOf(LEAVING);

        AwardApi.submit(withoutRole, id, AwardApi.version(withoutRole, id), true).then()
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("type", endsWith("recipient-out-of-scope"));
        assertThat(jdbc.queryForObject("select status from awards where award_id = ?", String.class, id))
            .isEqualTo("DRAFT");
    }

    @Test
    void ac0_10_aSubmittedUnitAwardIsReadInsideTheScopeOfTheUnit() {
        long id = AwardApi.submitted(tokenOf(SECOND_SECRETARY), Map.of("titleUk", "Відзнака факультету",
            RECIPIENT, TestUsers.FMI_FACULTY_ID));

        as(tokenOf(DEAN)).get(AWARDS + "/" + id).then().statusCode(HttpStatus.OK.value())
            .body("recipient.organization.id", equalTo((int) TestUsers.FMI_FACULTY_ID));
        as(tokenOf(OUTSIDER)).get(AWARDS + "/" + id).then().statusCode(HttpStatus.NOT_FOUND.value());
        as(tokenOf(EMPLOYEE)).get(AWARDS + "/" + id).then().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void ac0_9_theUnitAwardIsInTheExportOfWhoEnteredItOnly() {
        long id = AwardApi.complete(tokenOf(SECOND_SECRETARY), Map.of("titleUk", "Подяка кафедрі",
            RECIPIENT, TestUsers.DAI_DEPARTMENT_ID));

        as(tokenOf(SECOND_SECRETARY)).get("/api/v1/users/me/export").then().statusCode(HttpStatus.OK.value())
            .body("awards.find { it.award_id == " + id + " }.recipient_unit.id",
                equalTo((int) TestUsers.DAI_DEPARTMENT_ID));
        as(tokenOf(EMPLOYEE)).get("/api/v1/users/me/export").then().statusCode(HttpStatus.OK.value())
            .body("awards.find { it.award_id == " + id + " }", nullValue());
    }

    private void refused(String token, long unitId) {
        as(token).contentType(ContentType.JSON).body(Map.of("titleUk", "Чужа нагорода", RECIPIENT, unitId))
            .post(AWARDS).then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("type", equalTo(TYPE_PREFIX + "validation-failed"))
            .body("errors.field", contains(RECIPIENT));
    }

    private String levelOf(long awardId) {
        return jdbc.queryForObject("select current_level from award_requests where award_id = ?", String.class,
            awardId);
    }
}

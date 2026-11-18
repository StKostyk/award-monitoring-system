package ua.edu.chnu.awards.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyAwardsCvFT extends AbstractFunctionalTest {

    private static final String CV = "/api/v1/users/me/awards/cv";
    private static final String AWARDS = "/api/v1/awards";
    private static final String INVALID = "urn:awards:problem:invalid-parameter";
    private static final String PREFIX = "ft.cv.";
    private static final String DOMAIN = "@chnu.edu.ua";
    private static final String OWNER = PREFIX + "owner" + DOMAIN;
    private static final String EMPTY = PREFIX + "empty" + DOMAIN;
    private static final String COLLEAGUE = PREFIX + "colleague" + DOMAIN;
    private static final long DEPARTMENT_ID = 69L;
    private static final String COUNT_EXPORTS = "select count(*) from audit_logs where action_type = "
        + "'ANALYTICS_EXPORTED' and user_id = ?";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long ownerId;
    private long emptyId;
    private long privateAward;

    @BeforeAll
    void createAccounts() {
        Organization department = organizationRepository.findById(DEPARTMENT_ID).orElseThrow();
        ownerId = withRole(OWNER, department, RoleType.EMPLOYEE, department);
        emptyId = withRole(EMPTY, department, RoleType.EMPLOYEE, department);
        AwardRows.award(jdbc, withRole(COLLEAGUE, department, RoleType.EMPLOYEE, department)).status("APPROVED")
            .title("Colleague prize").insert();
        privateAward = AwardRows.award(jdbc, ownerId).status("APPROVED").title("Private letter")
            .awardDate(LocalDate.of(2019, 9, 1)).insert();
        long shared = AwardRows.award(jdbc, ownerId).status("APPROVED").title("State prize").category(12L)
            .awardingOrganization("Cabinet").awardDate(LocalDate.of(2025, 6, 3)).insert();
        jdbc.update("update awards set title_uk = 'Державна премія', visibility = 'PUBLIC' where award_id = ?",
            shared);
        AwardRows.award(jdbc, ownerId).status("PENDING").title("Pending prize").insert();
        AwardRows.award(jdbc, ownerId).status("APPROVED").title("Unit prize").unit(DEPARTMENT_ID).insert();
        AwardRows.award(jdbc, emptyId).status("REJECTED").title("Rejected prize").insert();
    }

    @AfterAll
    void deleteAccounts() {
        jdbc.update("delete from awards where user_id in"
            + " (select user_id from users where email_address like '" + PREFIX + "%')");
        userRepository.findAll().stream().filter(user -> user.getEmailAddress().startsWith(PREFIX))
            .forEach(userRepository::delete);
    }

    @Test
    void ac4_3_ac4_6_theTextListsOnlyTheOwnApprovedPersonalAwardsAndIsAudited() {
        int before = jdbc.queryForObject(COUNT_EXPORTS, Integer.class, ownerId);

        Response response = as(tokenOf(OWNER)).queryParam("format", "text").get(CV);

        response.then().statusCode(HttpStatus.OK.value())
            .header(HttpHeaders.CONTENT_TYPE, startsWith("text/plain"))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header(HttpHeaders.CONTENT_DISPOSITION, equalTo("attachment; filename=\"awards-cv-"
                + LocalDate.now(ZoneId.of("Europe/Kyiv")) + ".txt\""));
        String text = new String(response.asByteArray(), StandardCharsets.UTF_8);
        assertThat(text).startsWith("Нагороди: Test User\n")
            .contains("2025\n- Державна премія — Національний — Cabinet", "2019\n- Private letter")
            .doesNotContain("Pending prize", "Unit prize", "Colleague prize");
        assertThat(text.indexOf("2025")).isLessThan(text.indexOf("2019"));
        assertThat(jdbc.queryForObject(COUNT_EXPORTS, Integer.class, ownerId)).isEqualTo(before + 1);
        Map<String, Object> row = jdbc.queryForMap("select entity_type, entity_id, new_values::text as details "
            + "from audit_logs where action_type = 'ANALYTICS_EXPORTED' and user_id = ? order by log_id desc limit 1",
            ownerId);
        assertThat(row).containsEntry("entity_type", "ANALYTICS").containsEntry("entity_id", ownerId);
        assertThat((String) row.get("details")).contains("\"format\": \"cv-text\"", "\"language\": \"uk\"",
            "\"count\": 2").doesNotContain("Private letter", "State prize");
    }

    @Test
    void ac4_3_thePdfIsInEnglishWhenAsked() throws IOException {
        Response response = as(tokenOf(OWNER)).queryParam("format", "pdf").queryParam("lang", "en").get(CV);

        response.then().statusCode(HttpStatus.OK.value()).header(HttpHeaders.CONTENT_TYPE, "application/pdf");
        try (PDDocument pdf = Loader.loadPDF(response.asByteArray())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Awards: Test User", "State prize",
                "Private letter", "Page 1 of 1");
        }
    }

    @Test
    void ac4_5_withoutApprovedAwardsTheAnswerIsTheHeadingOrOnePage() throws IOException {
        Response text = as(tokenOf(EMPTY)).queryParam("format", "text").queryParam("lang", "en").get(CV);
        text.then().statusCode(HttpStatus.OK.value());
        assertThat(new String(text.asByteArray(), StandardCharsets.UTF_8)).startsWith("Awards: Test User\n")
            .doesNotContain("Rejected prize").hasLineCount(2);

        Response pdf = as(tokenOf(EMPTY)).queryParam("format", "pdf").get(CV);
        pdf.then().statusCode(HttpStatus.OK.value());
        try (PDDocument document = Loader.loadPDF(pdf.asByteArray())) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(document)).contains("Затверджених нагород поки немає.");
        }
    }

    @Test
    void ac4_3_anUnknownFormatOrLanguageNamesTheParameterAndWritesNoRow() {
        String owner = tokenOf(OWNER);
        int before = jdbc.queryForObject(COUNT_EXPORTS, Integer.class, ownerId);

        as(owner).queryParam("format", "docx").get(CV).then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("type", equalTo(INVALID)).body("parameter", equalTo("format"));
        as(owner).get(CV).then().statusCode(HttpStatus.BAD_REQUEST.value()).body("parameter", equalTo("format"));
        as(owner).queryParam("format", "pdf").queryParam("lang", "de").get(CV).then()
            .statusCode(HttpStatus.BAD_REQUEST.value()).body("parameter", equalTo("lang"));
        RestAssured.given().queryParam("format", "pdf").get(CV).then().statusCode(HttpStatus.UNAUTHORIZED.value());
        assertThat(jdbc.queryForObject(COUNT_EXPORTS, Integer.class, ownerId)).isEqualTo(before);
    }

    @Test
    void ac4_2_theOwnListFiltersByVisibilityAndTheAnalyticsCountIt() {
        String owner = tokenOf(OWNER);

        List<Integer> privateIds = as(owner).queryParam("status", "APPROVED").queryParam("visibility", "PRIVATE")
            .get(AWARDS).then().statusCode(HttpStatus.OK.value()).extract().path("content.id");
        List<String> publicTitles = as(owner).queryParam("visibility", "PUBLIC").get(AWARDS).then()
            .statusCode(HttpStatus.OK.value()).extract().path("content.title");

        assertThat(privateIds).containsExactly((int) privateAward);
        assertThat(publicTitles).containsExactly("State prize");
        as(owner).queryParam("visibility", "SECRET").get(AWARDS).then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("parameter", equalTo("visibility"));
        as(owner).get("/api/v1/analytics/me").then().statusCode(HttpStatus.OK.value())
            .body("visibility.publicAwards", equalTo(1)).body("visibility.universityAwards", equalTo(0))
            .body("visibility.privateAwards", equalTo(1)).body("visibility.withDocument", equalTo(0));
    }
}

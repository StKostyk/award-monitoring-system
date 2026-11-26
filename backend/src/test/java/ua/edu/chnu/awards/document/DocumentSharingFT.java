package ua.edu.chnu.awards.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static ua.edu.chnu.awards.common.web.ApiExceptionHandler.TYPE_PREFIX;
import static ua.edu.chnu.awards.support.DocumentApi.DOCUMENTS;
import static ua.edu.chnu.awards.support.DocumentApi.SMALL;
import static ua.edu.chnu.awards.support.DocumentApi.pdf;
import static ua.edu.chnu.awards.support.DocumentApi.upload;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentSharingFT extends AbstractFunctionalTest {

    private static final String OWNER = "ft.docshare.owner@chnu.edu.ua";
    private static final String COLLEAGUE = "ft.docshare.colleague@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(OWNER, COLLEAGUE);
    private static final String ACHIEVEMENTS = "/api/v1/achievements/";
    private static final String SHOWN = "shownToColleagues";
    private static final String TYPE = "type";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    void createUsers() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        withRole(OWNER, department, RoleType.EMPLOYEE, department);
        Organization other = organizationRepository.findById(10L).orElseThrow();
        withRole(COLLEAGUE, other, RoleType.EMPLOYEE, other);
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from awards where user_id in"
            + " (select user_id from users where email_address like 'ft.docshare.%')");
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac1_ac2_ac3_aColleagueSeesAndDownloadsOnlyTheShownDocumentsOfASharedAward() {
        String owner = tokenOf(OWNER);
        long award = AwardApi.complete(owner, Map.of("titleUk", "Документи для колег"));
        long shown = uploaded(owner, award, "диплом.pdf");
        final long hidden = uploaded(owner, award, "чернетка.pdf");
        as(owner).get(AwardApi.AWARDS + "/" + award + "/documents").then().statusCode(HttpStatus.OK.value())
            .body(SHOWN, contains(false, false));
        approve(owner, award);
        String colleague = tokenOf(COLLEAGUE);

        sharing(owner, shown, true).then().statusCode(HttpStatus.CONFLICT.value())
            .body(TYPE, equalTo(TYPE_PREFIX + "sharing-fixed"));
        as(colleague).get(ACHIEVEMENTS + award).then().statusCode(HttpStatus.NOT_FOUND.value());

        share(owner, award, "UNIVERSITY");
        sharing(colleague, shown, true).then().statusCode(HttpStatus.NOT_FOUND.value());
        sharing(owner, shown, true).then().statusCode(HttpStatus.OK.value()).body(SHOWN, equalTo(true));

        as(colleague).get(ACHIEVEMENTS + award).then().statusCode(HttpStatus.OK.value())
            .header("Cache-Control", "no-store")
            .body("achievement.awardId", equalTo((int) award))
            .body("achievement.titleUk", equalTo("Документи для колег"))
            .body("documents.id", contains((int) shown))
            .body("documents[0]", not(hasKey("storageKey")))
            .body("$", not(hasKey("history")));
        assertThat(as(colleague).get(DOCUMENTS + shown).then().statusCode(HttpStatus.OK.value()).extract()
            .asByteArray()).isNotEmpty();
        as(colleague).get(DOCUMENTS + hidden).then().statusCode(HttpStatus.NOT_FOUND.value());
        as(colleague).get(AwardApi.AWARDS + "/" + award + "/documents").then()
            .statusCode(HttpStatus.NOT_FOUND.value());
        RestAssured.given().get(DOCUMENTS + shown).then().statusCode(HttpStatus.UNAUTHORIZED.value());
        assertThat(audit("DOCUMENT_DOWNLOAD", shown, COLLEAGUE, "via")).isEqualTo("colleague");
        assertThat(audit("DOCUMENT_SHARING_CHANGED", shown, OWNER, "to")).isEqualTo("true");
    }

    @Test
    void ac3_goingBackToPrivateHidesTheDocumentsAtOnceAndResharingKeepsThemHidden() {
        String owner = tokenOf(OWNER);
        long award = AwardApi.complete(owner, Map.of("titleUk", "Знову приватна"));
        long document = uploaded(owner, award, "грамота.pdf");
        approve(owner, award);
        share(owner, award, "PUBLIC");
        sharing(owner, document, true).then().statusCode(HttpStatus.OK.value());
        RestAssured.given().get("/api/v1/public/achievements/" + award).then()
            .statusCode(HttpStatus.NOT_FOUND.value());
        String colleague = tokenOf(COLLEAGUE);

        share(owner, award, "UNIVERSITY");
        as(colleague).get(DOCUMENTS + document).then().statusCode(HttpStatus.OK.value());
        share(owner, award, "PRIVATE");

        as(colleague).get(DOCUMENTS + document).then().statusCode(HttpStatus.NOT_FOUND.value());
        as(colleague).get(ACHIEVEMENTS + award).then().statusCode(HttpStatus.NOT_FOUND.value());
        assertThat(audit("DOCUMENT_SHARING_CHANGED", document, OWNER, "to")).isEqualTo("false");
        share(owner, award, "UNIVERSITY");
        as(colleague).get(ACHIEVEMENTS + award).then().statusCode(HttpStatus.OK.value())
            .body("documents", empty());
    }

    private long uploaded(String token, long award, String name) {
        return upload(token, award, name, pdf(SMALL), "CERTIFICATE").then().statusCode(HttpStatus.CREATED.value())
            .extract().jsonPath().getLong("id");
    }

    private void approve(String token, long award) {
        AwardApi.submit(token, award, AwardApi.version(token, award), true).then()
            .statusCode(HttpStatus.OK.value());
        jdbc.update("update awards set status = 'APPROVED' where award_id = ?", award);
    }

    private static void share(String token, long award, String visibility) {
        as(token).contentType(ContentType.JSON).body(Map.of("visibility", visibility))
            .put(AwardApi.AWARDS + "/" + award + "/visibility").then().statusCode(HttpStatus.OK.value());
    }

    private static Response sharing(String token, long document, boolean shown) {
        return as(token).contentType(ContentType.JSON).body(Map.of(SHOWN, shown))
            .put(DOCUMENTS + document + "/sharing");
    }

    private String audit(String action, long document, String email, String key) {
        return jdbc.queryForObject("select new_values ->> ? from audit_logs where action_type = ? and entity_id = ?"
            + " and user_id = (select user_id from users where email_address = ?) order by log_id desc limit 1",
            String.class, key, action, document, email);
    }
}

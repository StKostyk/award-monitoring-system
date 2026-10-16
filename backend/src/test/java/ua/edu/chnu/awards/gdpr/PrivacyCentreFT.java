package ua.edu.chnu.awards.gdpr;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;

import ua.edu.chnu.awards.gdpr.service.DataExportService;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class PrivacyCentreFT extends AbstractFunctionalTest {

    private static final String PERSON = "ft.privacy.centre@chnu.edu.ua";
    private static final String SUMMARY = "/api/v1/users/me/privacy";
    private static final String ACTIVITY = "/api/v1/users/me/activity";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    private User person;

    @BeforeAll
    void createUser() {
        person = activeUser(PERSON);
        long award = AwardRows.award(jdbc, person.getId()).status("APPROVED").insert();
        jdbc.update("update awards set visibility = 'PUBLIC' where award_id = ?", award);
        jdbc.update("insert into notification_preferences (user_id, event_type, email_enabled, in_app_enabled)"
            + " values (?, 'AWARD_RETURNED', false, true)", person.getId());
    }

    @AfterAll
    void deleteUser() {
        jdbc.update("delete from awards where user_id = ?", person.getId());
        jdbc.update("delete from notification_preferences where user_id = ?", person.getId());
        ConsentRows.purge(jdbc, person.getId());
        userRepository.deleteById(person.getId());
        redis.delete(DataExportService.THROTTLE_KEY_PREFIX + person.getId());
    }

    @Test
    void ac21_a_theSummaryCountsWhatIsSharedAndHasNoExportYet() {
        RestAssured.given().get(SUMMARY).then().statusCode(HttpStatus.UNAUTHORIZED.value());

        as(tokenOf(PERSON)).get(SUMMARY).then().statusCode(HttpStatus.OK.value())
            .body("awardsByVisibility.PUBLIC", equalTo(1))
            .body("awardsByVisibility.UNIVERSITY", equalTo(0))
            .body("awardsByVisibility.PRIVATE", equalTo(0))
            .body("documentsShown", equalTo(0))
            .body("emailOffTypes", equalTo(List.of("AWARD_RETURNED")))
            .body("lastExportAt", nullValue());
    }

    @Test
    void ac21_b_anExportIsReportedInTheSummary() {
        String token = tokenOf(PERSON);
        as(token).get("/api/v1/users/me/export").then().statusCode(HttpStatus.OK.value());

        as(token).get(SUMMARY).then().statusCode(HttpStatus.OK.value()).body("lastExportAt", notNullValue());
    }

    @Test
    void ac22_theActivityIsPagedAndCarriesNoValuesOrAddresses() {
        String token = tokenOf(PERSON);
        RestAssured.given().get(ACTIVITY).then().statusCode(HttpStatus.UNAUTHORIZED.value());

        as(token).get(ACTIVITY).then().statusCode(HttpStatus.OK.value())
            .body("size", equalTo(20))
            .body("number", equalTo(0))
            .body("totalElements", greaterThanOrEqualTo(2))
            .body("content.action", hasItem("DATA_EXPORT"))
            .body("content.action", hasItem("LOGIN_SUCCESS"))
            .body("content.byMe", everyItem(equalTo(true)))
            .body("content.action", not(hasItem("INSERT")))
            .body("content", everyItem(not(hasKey("ipAddress"))))
            .body("content", everyItem(not(hasKey("newValues"))));
        as(token).queryParam("size", 500).get(ACTIVITY).then().statusCode(HttpStatus.OK.value())
            .body("size", equalTo(100));
        as(token).queryParam("size", 1).queryParam("page", 1).get(ACTIVITY).then()
            .statusCode(HttpStatus.OK.value()).body("size", equalTo(1)).body("number", equalTo(1));
        as(token).queryParam("page", "first").get(ACTIVITY).then().statusCode(HttpStatus.BAD_REQUEST.value());
    }
}

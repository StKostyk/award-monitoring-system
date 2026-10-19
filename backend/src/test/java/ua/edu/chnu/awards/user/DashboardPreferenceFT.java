package ua.edu.chnu.awards.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardPreferenceFT extends AbstractFunctionalTest {

    private static final String PERSON = "ft.dashboard@chnu.edu.ua";
    private static final String PREFERENCES = "/api/v1/users/me/dashboard-preferences";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long personId;
    private String token;
    private long lastLogId;

    @BeforeAll
    void createUser() {
        personId = activeUser(PERSON).getId();
        token = tokenOf(PERSON);
    }

    @BeforeEach
    void markAuditLog() {
        lastLogId = jdbc.queryForObject("select coalesce(max(log_id), 0) from audit_logs", Long.class);
    }

    @AfterEach
    void deleteRow() {
        jdbc.update("delete from dashboard_preferences where user_id = ?", personId);
    }

    @AfterAll
    void deleteUser() {
        userRepository.findByEmailAddressIgnoreCase(PERSON).ifPresent(userRepository::delete);
    }

    @Test
    void ac2_2_withoutASavedChoiceNothingIsHidden() {
        as(token).get(PREFERENCES).then().statusCode(HttpStatus.OK.value())
            .header("Cache-Control", equalTo("no-store"))
            .body("hiddenWidgets", empty());
    }

    @Test
    void ac2_2_aChoiceIsStoredWithoutDuplicatesAndReadBackWithoutAnAuditRow() {
        put(List.of("BY_LEVEL", "TIMELINE", "TIMELINE")).statusCode(HttpStatus.OK.value())
            .header("Cache-Control", equalTo("no-store"))
            .body("hiddenWidgets", contains("TIMELINE", "BY_LEVEL"));

        as(token).get(PREFERENCES).then().statusCode(HttpStatus.OK.value())
            .body("hiddenWidgets", contains("TIMELINE", "BY_LEVEL"));
        assertThat(jdbc.queryForObject("select array_to_string(hidden_widgets, ',') from dashboard_preferences "
            + "where user_id = ?", String.class, personId)).isEqualTo("TIMELINE,BY_LEVEL");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where log_id > ? and user_id = ?",
            Integer.class, lastLogId, personId)).isZero();
    }

    @Test
    void ac2_2_anEmptyListShowsEveryWidgetAgain() {
        put(List.of("RANK")).statusCode(HttpStatus.OK.value());

        put(List.of()).statusCode(HttpStatus.OK.value()).body("hiddenWidgets", empty());
    }

    @Test
    void ac2_2_anUnknownValueIsAnInvalidParameterNamingTheField() {
        put(List.of("SUMMARY", "WEATHER")).statusCode(HttpStatus.BAD_REQUEST.value())
            .body("type", equalTo("urn:awards:problem:invalid-parameter"))
            .body("parameter", equalTo("hiddenWidgets"));

        assertThat(jdbc.queryForObject("select count(*) from dashboard_preferences where user_id = ?",
            Integer.class, personId)).isZero();
    }

    @Test
    void ac2_2_anAnonymousCallerIsUnauthorised() {
        RestAssured.get(PREFERENCES).then().statusCode(HttpStatus.UNAUTHORIZED.value());
        RestAssured.given().contentType(ContentType.JSON).body(Map.of("hiddenWidgets", List.of())).put(PREFERENCES)
            .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    private ValidatableResponse put(List<String> widgets) {
        return as(token).contentType(ContentType.JSON).body(Map.of("hiddenWidgets", widgets)).put(PREFERENCES)
            .then();
    }
}

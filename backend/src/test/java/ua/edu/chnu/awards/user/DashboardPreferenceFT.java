package ua.edu.chnu.awards.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
    void ac1_1_withoutASavedChoiceTheSchemeIsSystemAndTheLayoutsDefault() {
        as(token).get(PREFERENCES).then().statusCode(HttpStatus.OK.value())
            .header("Cache-Control", equalTo("no-store"))
            .body("colorScheme", equalTo("SYSTEM"))
            .body("layouts.size()", equalTo(0));
    }

    @Test
    void ac1_1_ac1_3_theSchemeAndALayoutAreStoredAndReadBackWithoutAnAuditRow() {
        Map<String, Object> body = Map.of("colorScheme", "DARK", "layouts",
            Map.of("MY_AWARDS", Map.of("order", List.of("RANK", "KPI"), "hidden", List.of("TREND"))));

        put(body).statusCode(HttpStatus.OK.value())
            .header("Cache-Control", equalTo("no-store"))
            .body("colorScheme", equalTo("DARK"))
            .body("layouts.MY_AWARDS.order", contains("RANK", "KPI"));

        as(token).get(PREFERENCES).then().statusCode(HttpStatus.OK.value())
            .body("colorScheme", equalTo("DARK"))
            .body("layouts.MY_AWARDS.order", contains("RANK", "KPI"))
            .body("layouts.MY_AWARDS.hidden", contains("TREND"));
        assertThat(jdbc.queryForObject("select color_scheme from dashboard_preferences where user_id = ?",
            String.class, personId)).isEqualTo("DARK");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where log_id > ? and user_id = ?",
            Integer.class, lastLogId, personId)).isZero();
    }

    @Test
    void ac1_3_anEmptyBodyRestoresTheSystemSchemeAndTheDefaults() {
        put(Map.of("colorScheme", "LIGHT", "layouts", Map.of("HALL_OF_FAME",
            Map.of("order", List.of("TOP"), "hidden", List.of())))).statusCode(HttpStatus.OK.value());

        put(Map.of()).statusCode(HttpStatus.OK.value())
            .body("colorScheme", equalTo("SYSTEM"))
            .body("layouts.size()", equalTo(0));
    }

    @Test
    void ac1_4_anUnknownSchemeIsAnInvalidParameterNamingTheField() {
        put(Map.of("colorScheme", "BLUE")).statusCode(HttpStatus.BAD_REQUEST.value())
            .body("type", equalTo("urn:awards:problem:invalid-parameter"))
            .body("parameter", equalTo("colorScheme"));

        assertNothingStored();
    }

    @Test
    void ac1_4_aWidgetOfAnotherDashboardOrADuplicateNamesTheList() {
        put(Map.of("layouts", Map.of("HALL_OF_FAME", Map.of("order", List.of("KPI", "QUEUE")))))
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("parameter", equalTo("layouts.HALL_OF_FAME.order"));
        put(Map.of("layouts", Map.of("COMMAND_CENTRE", Map.of("hidden", List.of("TOP", "TOP")))))
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("parameter", equalTo("layouts.COMMAND_CENTRE.hidden"));
        put(Map.of("layouts", Map.of("ADMIN", Map.of("order", List.of()))))
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("parameter", equalTo("layouts"));

        assertNothingStored();
    }

    @Test
    void ac1_1_anAnonymousCallerIsUnauthorised() {
        RestAssured.get(PREFERENCES).then().statusCode(HttpStatus.UNAUTHORIZED.value());
        RestAssured.given().contentType(ContentType.JSON).body(Map.of("colorScheme", "DARK")).put(PREFERENCES)
            .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    private void assertNothingStored() {
        assertThat(jdbc.queryForObject("select count(*) from dashboard_preferences where user_id = ?",
            Integer.class, personId)).isZero();
    }

    private ValidatableResponse put(Map<String, Object> body) {
        return as(token).contentType(ContentType.JSON).body(body).put(PREFERENCES).then();
    }
}

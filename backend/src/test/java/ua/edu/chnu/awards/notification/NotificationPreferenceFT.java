package ua.edu.chnu.awards.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;

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
class NotificationPreferenceFT extends AbstractFunctionalTest {

    private static final String PERSON = "ft.preferences@chnu.edu.ua";
    private static final String PREFERENCES = "/api/v1/users/me/notification-preferences";
    private static final String CHANGED = "NOTIFICATION_PREFERENCES_CHANGED";
    private static final int TYPES = 11;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long personId;
    private long lastLogId;

    @BeforeAll
    void createUser() {
        personId = activeUser(PERSON).getId();
    }

    @BeforeEach
    void markAuditLog() {
        lastLogId = jdbc.queryForObject("select coalesce(max(log_id), 0) from audit_logs", Long.class);
    }

    @AfterEach
    void deleteRows() {
        jdbc.update("delete from notification_preferences where user_id = ?", personId);
    }

    @AfterAll
    void deleteUser() {
        userRepository.findByEmailAddressIgnoreCase(PERSON).ifPresent(userRepository::delete);
    }

    @Test
    void ac3_1_withoutStoredRowsEveryTypeIsOnForBothChannels() {
        as(tokenOf(PERSON)).get(PREFERENCES).then().statusCode(HttpStatus.OK.value())
            .header("Cache-Control", equalTo("no-store"))
            .body("preferences", hasSize(TYPES))
            .body("preferences[0].type", equalTo("AWARD_APPROVED"))
            .body("preferences.email", everyItem(equalTo(true)))
            .body("preferences.inApp", everyItem(equalTo(true)));
    }

    @Test
    void ac3_2_listedTypesAreStoredAndTheOthersKeepTheirState() {
        put(List.of(item("ROLE_ASSIGNED", false, true))).statusCode(HttpStatus.OK.value());

        put(List.of(item("AWARD_APPROVED", true, false))).statusCode(HttpStatus.OK.value())
            .body("preferences", hasSize(TYPES))
            .body("preferences.find { it.type == 'AWARD_APPROVED' }.inApp", equalTo(false))
            .body("preferences.find { it.type == 'ROLE_ASSIGNED' }.email", equalTo(false));
        put(List.of(item("AWARD_APPROVED", false, true))).statusCode(HttpStatus.OK.value());

        assertThat(jdbc.queryForList("select event_type || ':' || email_enabled || ':' || in_app_enabled"
            + " from notification_preferences where user_id = ? order by event_type", String.class, personId))
            .containsExactly("AWARD_APPROVED:false:true", "ROLE_ASSIGNED:false:true");
        as(tokenOf(PERSON)).get(PREFERENCES).then()
            .body("preferences.find { it.type == 'AWARD_APPROVED' }.email", equalTo(false));
    }

    @Test
    void ac3_3_aTypeWithoutChannelAnUnknownTypeAndARepeatedTypeAreRefusedAndNothingIsStored() {
        put(List.of(item("AWARD_APPROVED", false, true), item("ROLE_REVOKED", false, false),
                item("AWARD_SUBMITTED", true, true), item("AWARD_APPROVED", true, true)))
            .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("type", equalTo("urn:awards:problem:validation-failed"))
            .body("errors.field", contains("preferences[1]", "preferences[2].type", "preferences[3].type"))
            .body("errors.code", contains("both-off", "invalid", "duplicate"));

        assertThat(storedRows()).isZero();
        assertThat(auditRows()).isZero();
    }

    @Test
    void ac3_4_anEffectiveChangeIsAuditedOnceAndARepeatWritesNoRow() {
        put(List.of(item("REVIEW_OVERDUE", false, true), item("ROLE_ASSIGNED", true, true)))
            .statusCode(HttpStatus.OK.value());
        put(List.of(item("REVIEW_OVERDUE", false, true))).statusCode(HttpStatus.OK.value());

        assertThat(auditRows()).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("select user_id, old_values::text as old_values,"
            + " new_values::text as new_values from audit_logs"
            + " where action_type = ? and entity_id = ? and log_id > ?", CHANGED, personId, lastLogId);
        assertThat(row.get("user_id")).isEqualTo(personId);
        assertThat((String) row.get("old_values")).contains("REVIEW_OVERDUE", "\"email\": true")
            .doesNotContain("ROLE_ASSIGNED");
        assertThat((String) row.get("new_values")).contains("REVIEW_OVERDUE", "\"email\": false")
            .doesNotContain("ROLE_ASSIGNED");
    }

    @Test
    void anonymousCallersAreRefused() {
        RestAssured.get(PREFERENCES).then().statusCode(HttpStatus.UNAUTHORIZED.value());
        RestAssured.given().contentType(ContentType.JSON).body(Map.of("preferences", List.of())).put(PREFERENCES)
            .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    private ValidatableResponse put(List<Map<String, Object>> items) {
        return as(tokenOf(PERSON)).contentType(ContentType.JSON).body(Map.of("preferences", items))
            .put(PREFERENCES).then();
    }

    private long storedRows() {
        return jdbc.queryForObject("select count(*) from notification_preferences where user_id = ?", Long.class,
            personId);
    }

    private long auditRows() {
        return jdbc.queryForObject("select count(*) from audit_logs where action_type = ? and entity_id = ?"
            + " and log_id > ?", Long.class, CHANGED, personId, lastLogId);
    }

    private static Map<String, Object> item(String type, boolean email, boolean inApp) {
        return Map.of("type", type, "email", email, "inApp", inApp);
    }
}

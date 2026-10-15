package ua.edu.chnu.awards.gdpr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;

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
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.gdpr.service.DataExportService;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class ConsentFT extends AbstractFunctionalTest {

    private static final String PERSON = "ft.consents@chnu.edu.ua";
    private static final String CONSENTS = "/api/v1/users/me/consents";

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
    }

    @AfterAll
    void deleteUser() {
        ConsentRows.purge(jdbc, person.getId());
        userRepository.deleteById(person.getId());
        redis.delete(DataExportService.THROTTLE_KEY_PREFIX + person.getId());
    }

    @Test
    void ac12_theNoticeVersionIsOpenToEveryone() {
        RestAssured.given().get("/api/v1/public/privacy-notice").then().statusCode(HttpStatus.OK.value())
            .body("version", equalTo("1.0"))
            .body("publishedOn", equalTo("2026-10-15"))
            .body("versions", hasSize(1))
            .body("versions[0].version", equalTo("1.0"));
    }

    @Test
    void ac15_anAccountWithoutRecordsMustAcknowledge() {
        RestAssured.given().get(CONSENTS).then().statusCode(HttpStatus.UNAUTHORIZED.value());

        as(tokenOf(PERSON)).get(CONSENTS).then().statusCode(HttpStatus.OK.value())
            .body("notice.currentVersion", equalTo("1.0"))
            .body("notice.acknowledgedVersion", nullValue())
            .body("notice.acknowledgementRequired", equalTo(true))
            .body("consents", hasSize(1))
            .body("consents[0].type", equalTo("ANALYTICS"))
            .body("consents[0].granted", equalTo(false))
            .body("history", hasSize(0));
    }

    @Test
    void ac16_theAcknowledgementIsAppendedOnce() {
        String token = tokenOf(PERSON);

        acknowledge(token, "0.9").then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors[0].field", equalTo("version"))
            .body("errors[0].code", equalTo("outdated"));
        acknowledge(token, "1.0").then().statusCode(HttpStatus.OK.value())
            .body("notice.acknowledgedVersion", equalTo("1.0"))
            .body("notice.acknowledgedAt", notNullValue())
            .body("notice.acknowledgementRequired", equalTo(false))
            .body("history", hasSize(1));
        acknowledge(token, "1.0").then().statusCode(HttpStatus.OK.value()).body("history", hasSize(1));

        assertThat(auditCount("PRIVACY_NOTICE_ACKNOWLEDGED")).isEqualTo(1);
    }

    @Test
    void ac17_analyticsIsGrantedAndWithdrawnOnChangeOnly() {
        String token = tokenOf(PERSON);

        choose(token, "ANALYTICS", true).then().statusCode(HttpStatus.OK.value())
            .body("consents[0].granted", equalTo(true))
            .body("consents[0].changedAt", notNullValue());
        choose(token, "ANALYTICS", true).then().statusCode(HttpStatus.OK.value());
        choose(token, "ANALYTICS", false).then().statusCode(HttpStatus.OK.value())
            .body("consents[0].granted", equalTo(false))
            .body("history", hasSize(3))
            .body("history[2].type", equalTo("ANALYTICS"))
            .body("history[2].granted", equalTo(false))
            .body("history[2]", not(hasKey("ipAddress")));
        choose(token, "DATA_PROCESSING", false).then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors[0].field", equalTo("type"))
            .body("errors[0].code", equalTo("not-optional"));
        choose(token, "PUBLIC_VISIBILITY", true).then().statusCode(HttpStatus.BAD_REQUEST.value());
        as(token).contentType(ContentType.JSON).body(Map.of()).put(CONSENTS + "/ANALYTICS").then()
            .statusCode(HttpStatus.BAD_REQUEST.value());

        assertThat(auditCount("CONSENT_GRANTED")).isEqualTo(1);
        assertThat(auditCount("CONSENT_WITHDRAWN")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from consent_records"
            + " where user_id = ? and consent_type = 'ANALYTICS' and not is_granted and withdrawn_at is not null"
            + " and granted_at is null", Integer.class, person.getId())).isEqualTo(1);
    }

    @Test
    void ac1_10_theExportListsTheConsentRecords() {
        as(tokenOf(PERSON)).get("/api/v1/users/me/export").then().statusCode(HttpStatus.OK.value())
            .body("consent_history", hasSize(3))
            .body("consent_history[0].consent_type", equalTo("DATA_PROCESSING"))
            .body("consent_history[2].consent_type", equalTo("ANALYTICS"));
    }

    private Response acknowledge(String token, String version) {
        return as(token).contentType(ContentType.JSON).body(Map.of("version", version))
            .post(CONSENTS + "/notice-acknowledgement");
    }

    private Response choose(String token, String type, boolean granted) {
        return as(token).contentType(ContentType.JSON).body(Map.of("granted", granted)).put(CONSENTS + "/" + type);
    }

    private Integer auditCount(String action) {
        return jdbc.queryForObject("select count(*) from audit_logs where user_id = ? and entity_type = 'GDPR'"
            + " and action_type = ?", Integer.class, person.getId(), action);
    }
}

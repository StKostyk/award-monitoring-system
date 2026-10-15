package ua.edu.chnu.awards.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;

import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationFT extends AbstractFunctionalTest {

    private static final String READER = "ft.notification.reader@chnu.edu.ua";
    private static final String OTHER = "ft.notification.other@chnu.edu.ua";
    private static final String NOTIFICATIONS = "/api/v1/notifications";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final String UNREAD = "/unread-count";
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long readerId;
    private long otherId;

    @BeforeAll
    void createUsers() {
        readerId = activeUser(READER).getId();
        otherId = activeUser(OTHER).getId();
    }

    @AfterEach
    void deleteRows() {
        jdbc.update("delete from notifications where user_id in (?, ?)", readerId, otherId);
    }

    @AfterAll
    void deleteUsers() {
        List.of(READER, OTHER).forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac1_7_theCallerSeesOnlyOwnInAppRowsNewestFirst() {
        long older = insert(readerId, "IN_APP", "AWARD_APPROVED", Duration.ofHours(2));
        long newer = insert(readerId, "IN_APP", "ROLE_ASSIGNED", Duration.ofHours(1));
        insert(readerId, "EMAIL", "AWARD_APPROVED", Duration.ZERO);
        insert(otherId, "IN_APP", "AWARD_REJECTED", Duration.ZERO);

        as(tokenOf(READER)).get(NOTIFICATIONS).then().statusCode(HttpStatus.OK.value())
            .header("Cache-Control", equalTo("no-store"))
            .body("content.id", contains((int) newer, (int) older))
            .body("content[1].type", equalTo("AWARD_APPROVED"))
            .body("content[1].payload.title", equalTo("Certificate"))
            .body("content[1].referenceType", equalTo("AWARD"))
            .body("content[1].read", equalTo(false))
            .body("content[0].referenceType", nullValue())
            .body("size", equalTo(20))
            .body("totalElements", equalTo(2));
    }

    @Test
    void ac1_7_unreadFiltersAndPageSizeIsClamped() {
        long unread = insert(readerId, "IN_APP", "AWARD_APPROVED", Duration.ofHours(1));
        long read = insert(readerId, "IN_APP", "AWARD_RETURNED", Duration.ZERO);
        jdbc.update("update notifications set is_read = true, read_at = now() where notification_id = ?", read);
        String reader = tokenOf(READER);

        as(reader).queryParam("unread", true).get(NOTIFICATIONS).then().statusCode(HttpStatus.OK.value())
            .body("content.id", contains((int) unread));
        as(reader).queryParam("size", 1000).queryParam("page", -1).get(NOTIFICATIONS).then()
            .statusCode(HttpStatus.OK.value())
            .body("size", equalTo(MAX_PAGE_SIZE))
            .body("number", equalTo(0));
        as(reader).queryParam("size", 0).get(NOTIFICATIONS).then().statusCode(HttpStatus.OK.value())
            .body("size", equalTo(1));
        as(reader).queryParam("unread", "maybe").get(NOTIFICATIONS).then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("type", equalTo(PROBLEM + "invalid-parameter"));
    }

    @Test
    void ac1_8_ac1_9_markingReadSetsTheFlagOnceAndCountsDown() {
        long id = insert(readerId, "IN_APP", "AWARD_APPROVED", Duration.ZERO);
        insert(readerId, "EMAIL", "AWARD_APPROVED", Duration.ZERO);
        String reader = tokenOf(READER);
        as(reader).get(NOTIFICATIONS + UNREAD).then().statusCode(HttpStatus.OK.value()).body("unread", equalTo(1));

        as(reader).post(NOTIFICATIONS + "/" + id + "/read").then().statusCode(HttpStatus.NO_CONTENT.value());
        Timestamp firstRead = readAt(id);
        as(reader).post(NOTIFICATIONS + "/" + id + "/read").then().statusCode(HttpStatus.NO_CONTENT.value());

        assertThat(firstRead).isNotNull();
        assertThat(readAt(id)).isEqualTo(firstRead);
        as(reader).get(NOTIFICATIONS + UNREAD).then().body("unread", equalTo(0));
    }

    @Test
    void ac1_9_anotherPersonsAnEmailOrAnUnknownIdIsNotFound() {
        long others = insert(otherId, "IN_APP", "AWARD_APPROVED", Duration.ZERO);
        long email = insert(readerId, "EMAIL", "AWARD_APPROVED", Duration.ZERO);
        String reader = tokenOf(READER);

        for (long id : new long[] {others, email, Long.MAX_VALUE}) {
            as(reader).post(NOTIFICATIONS + "/" + id + "/read").then().statusCode(HttpStatus.NOT_FOUND.value())
                .body("type", equalTo(PROBLEM + "notification-not-found"));
        }
        assertThat(readAt(others)).isNull();
        assertThat(readAt(email)).isNull();
    }

    @Test
    void ac1_10_readAllMarksTheCallersUnreadRowsOnly() {
        insert(readerId, "IN_APP", "AWARD_APPROVED", Duration.ofHours(1));
        insert(readerId, "IN_APP", "ROLE_ASSIGNED", Duration.ZERO);
        long email = insert(readerId, "EMAIL", "AWARD_APPROVED", Duration.ZERO);
        long others = insert(otherId, "IN_APP", "AWARD_APPROVED", Duration.ZERO);
        String reader = tokenOf(READER);

        as(reader).post(NOTIFICATIONS + "/read-all").then().statusCode(HttpStatus.OK.value())
            .body("marked", equalTo(2));
        as(reader).post(NOTIFICATIONS + "/read-all").then().statusCode(HttpStatus.OK.value())
            .body("marked", equalTo(0));
        assertThat(readAt(email)).isNull();
        assertThat(readAt(others)).isNull();
    }

    @Test
    void ac1_11_everyEndpointNeedsAToken() {
        RestAssured.get(NOTIFICATIONS).then().statusCode(HttpStatus.UNAUTHORIZED.value());
        RestAssured.get(NOTIFICATIONS + UNREAD).then().statusCode(HttpStatus.UNAUTHORIZED.value());
        RestAssured.post(NOTIFICATIONS + "/1/read").then().statusCode(HttpStatus.UNAUTHORIZED.value());
        RestAssured.post(NOTIFICATIONS + "/read-all").then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    private long insert(long userId, String channel, String type, Duration age) {
        boolean award = type.startsWith("AWARD_");
        Timestamp sentAt = Timestamp.from(Instant.now().minus(age));
        return jdbc.queryForObject("insert into notifications (user_id, type, channel, payload, dedupe_key,"
                + " reference_type, reference_id, sent_at, expires_at, delivery_status) values (?, ?, ?,"
                + " '{\"title\": \"Certificate\"}'::jsonb, ?, ?, ?, ?, ?, ?) returning notification_id", Long.class,
            userId, type, channel, UUID.randomUUID().toString().replace("-", ""), award ? "AWARD" : null,
            award ? 5L : null, sentAt, sentAt, "EMAIL".equals(channel) ? "SENT" : null);
    }

    private Timestamp readAt(long id) {
        return jdbc.queryForObject("select read_at from notifications where notification_id = ?", Timestamp.class,
            id);
    }
}

package ua.edu.chnu.awards.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.notification.entity.DeliveryStatus;
import ua.edu.chnu.awards.notification.repository.NewNotification;
import ua.edu.chnu.awards.notification.repository.NotificationRepository;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class DeliveryLogIT extends AbstractIntegrationTest {

    private static final String PREFIX = "it.delivery.";
    private static final Duration RETRY_AFTER = Duration.ofMinutes(10);

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private OrganizationRepository organizations;

    private User owner;

    @BeforeEach
    void createOwner() {
        owner = users.save(TestUsers.user(PREFIX + "owner@chnu.edu.ua",
            organizations.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow()));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM notifications WHERE user_id IN (SELECT user_id FROM users WHERE email_address LIKE ?)",
            PREFIX + "%");
        jdbc.update("DELETE FROM users WHERE email_address LIKE ?", PREFIX + "%");
    }

    @Test
    void ac4_2_aClaimWritesASendingRowAndFinishRecordsTheOutcome() {
        Instant now = now();

        assertThat(notifications.claim(row("a", now), now.minus(RETRY_AFTER))).isEqualTo(1);
        assertThat(status("a")).isEqualTo("SENDING");

        notifications.finish(key("a"), DeliveryStatus.SENT, now);

        assertThat(status("a")).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT delivered_at FROM notifications WHERE dedupe_key = ?",
            Timestamp.class, key("a")).toInstant()).isEqualTo(now);
    }

    @Test
    void ac4_3_aSentOrFreshlyClaimedMailIsNotClaimedAgain() {
        Instant now = now();
        notifications.claim(row("sent", now), now.minus(RETRY_AFTER));
        notifications.finish(key("sent"), DeliveryStatus.SENT, now);
        notifications.claim(row("busy", now.minus(Duration.ofMinutes(9))), now.minus(RETRY_AFTER));

        assertThat(notifications.claim(row("sent", now), now.minus(RETRY_AFTER))).isZero();
        assertThat(notifications.claim(row("busy", now), now.minus(RETRY_AFTER))).isZero();
        assertThat(status("sent")).isEqualTo("SENT");
        assertThat(status("busy")).isEqualTo("SENDING");
    }

    @Test
    void ac4_3_aFailedOrStaleClaimIsTakenOver() {
        Instant now = now();
        notifications.claim(row("failed", now.minus(Duration.ofMinutes(1))), now.minus(RETRY_AFTER));
        notifications.finish(key("failed"), DeliveryStatus.FAILED, null);
        notifications.claim(row("stale", now.minus(Duration.ofMinutes(11))), now.minus(RETRY_AFTER));

        assertThat(notifications.claim(row("failed", now), now.minus(RETRY_AFTER))).isEqualTo(1);
        assertThat(notifications.claim(row("stale", now), now.minus(RETRY_AFTER))).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT delivery_status || '@' || sent_at FROM notifications"
            + " WHERE dedupe_key IN (?, ?)", String.class, key("failed"), key("stale")))
            .allMatch(value -> value.startsWith("SENDING@"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE dedupe_key IN (?, ?)"
            + " AND sent_at = ?", Long.class, key("failed"), key("stale"), Timestamp.from(now))).isEqualTo(2);
    }

    @Test
    void ac4_3_ofTwoConcurrentClaimsOnlyOneWins() throws Exception {
        Instant now = now();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> claim = () -> {
            start.await();
            return notifications.claim(row("race", now), now.minus(RETRY_AFTER));
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = List.of(pool.submit(claim), pool.submit(claim));
            start.countDown();

            assertThat(results.get(0).get(10, TimeUnit.SECONDS) + results.get(1).get(10, TimeUnit.SECONDS))
                .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void ac4_1_anInAppRowCannotCarryADeliveryStatusAndAMailRowNeedsOne() {
        long userId = owner.getId();

        assertThat(insertFails(userId, "IN_APP", "'SENT'")).isTrue();
        assertThat(insertFails(userId, "EMAIL", "NULL")).isTrue();
        assertThat(insertFails(userId, "EMAIL", "'DELIVERED'")).isTrue();
        assertThat(insertFails(userId, "EMAIL", "'SENT'")).isFalse();
    }

    private boolean insertFails(long userId, String channel, String status) {
        try {
            jdbc.update("INSERT INTO notifications (user_id, type, channel, dedupe_key, sent_at, delivery_status)"
                + " VALUES (?, 'AWARD_APPROVED', ?, ?, now(), " + status + ")", userId, channel,
                key(channel + status));
            return false;
        } catch (DataIntegrityViolationException e) {
            return true;
        }
    }

    private NewNotification row(String name, Instant at) {
        return new NewNotification(owner.getId(), "AWARD_APPROVED", "EMAIL", "{\"title\":\"Certificate\"}",
            key(name), "AWARD", 1L, at, at.plus(Duration.ofDays(365)));
    }

    private String key(String name) {
        return String.format("%-64s", PREFIX + owner.getId() + "." + name).replace(' ', '0');
    }

    private String status(String name) {
        return jdbc.queryForObject("SELECT delivery_status FROM notifications WHERE dedupe_key = ?", String.class,
            key(name));
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}

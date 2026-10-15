package ua.edu.chnu.awards.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.event.AwardDecided;
import ua.edu.chnu.awards.common.event.PublicationRetry;
import ua.edu.chnu.awards.delegation.event.DelegationRevoked;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.Eventually;
import ua.edu.chnu.awards.support.TestEvents;
import ua.edu.chnu.awards.support.TestMails;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class InAppNotificationsIT extends AbstractIntegrationTest {

    private static final long WAIT_MS = 30_000;
    private static final long FAILED_SEND_MS = 20_000;
    private static final long WAIT_SHORT_MS = 2_000;
    private static final long AWARD_ID = 999_002L;
    private static final String PREFIX = "it.inapp.";
    private static final String DOMAIN = "@chnu.edu.ua";

    @MockitoSpyBean
    private JavaMailSender mailSender;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PublicationRetry retry;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private OrganizationRepository organizations;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM event_publication");
        jdbc.update("DELETE FROM notifications WHERE user_id IN (SELECT user_id FROM users WHERE email_address LIKE ?)",
            PREFIX + "%");
        jdbc.update("DELETE FROM users WHERE email_address LIKE ?", PREFIX + "%");
    }

    @Test
    void ac1_2_aCommittedDecisionWritesOneInAppRowForTheOwner() {
        User owner = account("decided");
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        publish(decided(owner, occurredAt));

        assertThat(Eventually.equalTo(() -> rows(owner), 1L)).isEqualTo(1);
        assertThat(Eventually.equalTo(this::incomplete, 0L)).isZero();
        Map<String, Object> row = jdbc.queryForMap("SELECT type, channel, payload::text AS payload, reference_type,"
            + " reference_id, is_read, sent_at, expires_at, length(dedupe_key) AS key_length FROM notifications"
            + " WHERE user_id = ? AND channel = 'IN_APP'", owner.getId());
        assertThat(row).containsEntry("type", "AWARD_RETURNED").containsEntry("channel", "IN_APP")
            .containsEntry("reference_type", "AWARD").containsEntry("reference_id", AWARD_ID)
            .containsEntry("is_read", false).containsEntry("key_length", 64);
        assertThat((String) row.get("payload")).contains("\"title\": \"Certificate\"", "\"titleUk\": \"Грамота\"",
            "\"reviewer\": \"Ірина Бойко\"", "\"level\": \"FACULTY_SECRETARY\"").doesNotContain("Виправте дату");
        Instant sentAt = ((Timestamp) row.get("sent_at")).toInstant();
        assertThat(((Timestamp) row.get("expires_at")).toInstant())
            .isEqualTo(sentAt.atZone(ZoneOffset.UTC).plusYears(1).toInstant());
    }

    @Test
    void ac1_3_aSecondDeliveryOfTheSameEventWritesNothingAndCompletes() {
        User owner = account("twice");
        AwardDecided event = decided(owner, Instant.now());

        publish(event);
        assertThat(Eventually.equalTo(() -> rows(owner), 1L)).isEqualTo(1);
        publish(event);

        assertThat(Eventually.equalTo(this::incomplete, 0L)).isZero();
        assertThat(rows(owner)).isEqualTo(1);
        assertThat(mailStatus(owner)).isEqualTo("SENT");
    }

    @Test
    void ac1_5_anUnknownOrDeletedRecipientGetsNoRowAndThePublicationCompletes() {
        User deleted = account("deleted");
        jdbc.update("UPDATE users SET account_status = 'DELETED' WHERE user_id = ?", deleted.getId());

        publish(TestEvents.decided().to(Long.MAX_VALUE, "it.inapp.gone" + DOMAIN, "Олена")
            .award(AWARD_ID, "Certificate", "Грамота").at(Instant.now()).build());
        publish(decided(deleted, Instant.now()));

        assertThat(Eventually.equalTo(this::incomplete, 0L)).isZero();
        assertThat(rows(deleted)).isZero();
        verify(mailSender, never())
            .send(ArgumentMatchers.<MimeMessage>argThat(sentTo("it.inapp.gone" + DOMAIN)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ?", Long.class,
            Long.MAX_VALUE)).isZero();
    }

    @Test
    void ac1_6_withTheMailServerDownTheInAppRowIsWrittenAndOnlyTheMailIsRetried() {
        User owner = account("maildown");
        doThrow(new MailSendException("down")).when(mailSender)
            .send(ArgumentMatchers.<MimeMessage>argThat(sentTo(owner.getEmailAddress())));

        long publications = Objects.requireNonNull(transactions.execute(status -> {
            events.publishEvent(decided(owner, Instant.now()));
            return incomplete();
        }));

        assertThat(publications).isEqualTo(2);
        verify(mailSender, timeout(FAILED_SEND_MS).times(3))
            .send(ArgumentMatchers.<MimeMessage>argThat(sentTo(owner.getEmailAddress())));
        assertThat(Eventually.equalTo(() -> rows(owner), 1L)).isEqualTo(1);
        assertThat(Eventually.equalTo(this::incomplete, 1L)).isEqualTo(1);
        assertThat(Eventually.equalTo(() -> mailStatus(owner), "FAILED")).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT listener_id FROM event_publication WHERE completion_date IS NULL",
            String.class)).contains("DecisionMails");

        reset(mailSender);
        jdbc.update("UPDATE event_publication SET publication_date = ?",
            Timestamp.from(Instant.now().minus(Duration.ofMinutes(15))));
        retry.run();

        verify(mailSender, timeout(WAIT_MS))
            .send(ArgumentMatchers.<MimeMessage>argThat(sentTo(owner.getEmailAddress())));
        assertThat(Eventually.equalTo(this::incomplete, 0L)).isZero();
        assertThat(rows(owner)).isEqualTo(1);
        assertThat(mailStatus(owner)).isEqualTo("SENT");
    }

    @Test
    void ac4_2_aSentMailIsLoggedAsSentWithTheMomentTheServerAcceptedIt() {
        User owner = account("logged");
        final Instant before = Instant.now().minusSeconds(1);

        publish(decided(owner, Instant.now()));

        assertThat(Eventually.equalTo(() -> mailStatus(owner), "SENT")).isEqualTo("SENT");
        Map<String, Object> row = jdbc.queryForMap("SELECT type, payload ->> 'title' AS title, delivered_at"
            + " FROM notifications WHERE user_id = ? AND channel = 'EMAIL'", owner.getId());
        assertThat(row).containsEntry("type", "AWARD_RETURNED").containsEntry("title", "Certificate");
        assertThat(((Timestamp) row.get("delivered_at")).toInstant()).isAfter(before);
        assertThat(Eventually.equalTo(this::incomplete, 0L)).isZero();
    }

    @Test
    void ac4_3_aRepeatedDeliveryOfASentMailSendsNothingAndCompletes() {
        User owner = account("f1");
        AwardDecided event = decided(owner, Instant.now().truncatedTo(ChronoUnit.MICROS));
        publish(event);
        assertThat(Eventually.equalTo(() -> mailStatus(owner), "SENT")).isEqualTo("SENT");
        assertThat(Eventually.equalTo(this::incomplete, 0L)).isZero();

        publish(event);

        assertThat(Eventually.equalTo(this::incomplete, 0L)).isZero();
        verify(mailSender, after(WAIT_SHORT_MS).times(1))
            .send(ArgumentMatchers.<MimeMessage>argThat(sentTo(owner.getEmailAddress())));
    }

    @Test
    void ac3_5_eachChannelFollowsTheRecipientsPreferenceForTheType() {
        User mailOff = account("mailoff");
        User inAppOff = account("inappoff");
        prefer(mailOff, false, true);
        prefer(inAppOff, true, false);

        publish(decided(mailOff, Instant.now()));
        publish(decided(inAppOff, Instant.now()));

        verify(mailSender, timeout(WAIT_MS))
            .send(ArgumentMatchers.<MimeMessage>argThat(sentTo(inAppOff.getEmailAddress())));
        assertThat(Eventually.equalTo(() -> rows(mailOff), 1L)).isEqualTo(1);
        assertThat(Eventually.equalTo(this::incomplete, 0L)).isZero();
        assertThat(rows(inAppOff)).isZero();
        assertThat(mailStatus(mailOff)).isNull();
        verify(mailSender, never())
            .send(ArgumentMatchers.<MimeMessage>argThat(sentTo(mailOff.getEmailAddress())));
    }

    @Test
    void edgeCase_aRevokedDelegationWritesOneRowPerRecipientNamingTheOtherSide() {
        User delegate = account("delegate");
        User delegator = account("delegator");

        publish(new DelegationRevoked(List.of(
                new DelegationRevoked.Recipient(delegate.getId(), delegate.getEmailAddress(), "Марія Мартинюк"),
                new DelegationRevoked.Recipient(delegator.getId(), delegator.getEmailAddress(), "Аліна Коваль")),
            RoleType.DEAN, "Faculty", "Факультет", "Аліна Коваль", "Олег Адміненко", LocalDate.of(2026, 10, 14),
            Instant.now()));

        assertThat(Eventually.equalTo(() -> rows(delegate) + rows(delegator), 2L)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT payload ->> 'otherParty' FROM notifications WHERE user_id = ?"
            + " AND channel = 'IN_APP'",
            String.class, delegator.getId())).isEqualTo("Аліна Коваль");
        assertThat(Eventually.equalTo(this::incomplete, 0L)).isZero();
    }

    private User account(String name) {
        return users.save(TestUsers.user(PREFIX + name + DOMAIN,
            organizations.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow()));
    }

    private static AwardDecided decided(User owner, Instant occurredAt) {
        return TestEvents.decided().to(owner.getId(), owner.getEmailAddress(), "Олена")
            .award(AWARD_ID, "Certificate", "Грамота")
            .outcome(RequestStatus.RETURNED, ApprovalLevel.FACULTY_SECRETARY)
            .by("Ірина Бойко", "Виправте дату").at(occurredAt).build();
    }

    private void prefer(User user, boolean email, boolean inApp) {
        jdbc.update("INSERT INTO notification_preferences (user_id, event_type, email_enabled, in_app_enabled)"
            + " VALUES (?, 'AWARD_RETURNED', ?, ?)", user.getId(), email, inApp);
    }

    private void publish(Object event) {
        transactions.executeWithoutResult(status -> events.publishEvent(event));
    }

    private long rows(User user) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND channel = 'IN_APP'",
            Long.class, user.getId());
    }

    private String mailStatus(User user) {
        return jdbc.query("SELECT delivery_status FROM notifications WHERE user_id = ? AND channel = 'EMAIL'",
            (rs, i) -> rs.getString(1), user.getId()).stream().findFirst().orElse(null);
    }

    private long incomplete() {
        return jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE completion_date IS NULL",
            Long.class);
    }

    private static ArgumentMatcher<MimeMessage> sentTo(String to) {
        return message -> message != null && TestMails.recipients(message).contains(to);
    }
}

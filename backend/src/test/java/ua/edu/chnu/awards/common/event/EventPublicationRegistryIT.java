package ua.edu.chnu.awards.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.auth.event.PasswordResetRequested;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.award.event.AwardDecided;
import ua.edu.chnu.awards.award.event.ReviewMeasured;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;

class EventPublicationRegistryIT extends AbstractIntegrationTest {

    private static final long MAIL_WAIT_MS = 30_000;
    private static final long FAILED_SEND_MS = 20_000;
    private static final long QUIET_MS = 2_000;
    private static final String RESET_LINK = "http://localhost:4200/reset-password?token=secret-one-time-token";

    @MockitoSpyBean
    private JavaMailSender mailSender;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private AfterCommit afterCommit;

    @Autowired
    private PublicationRetry retry;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearRegistry() {
        jdbc.update("DELETE FROM event_publication");
    }

    @Test
    void ac1_1_aCommittedEventIsRecordedInTheTransactionAndDeletedOnceTheMailIsSent() {
        String to = "it.registry.commit@chnu.edu.ua";

        long inTransaction = Objects.requireNonNull(transactions.execute(status -> {
            events.publishEvent(decided(to));
            return incomplete();
        }));

        assertThat(inTransaction).isEqualTo(1);
        verify(mailSender, timeout(MAIL_WAIT_MS)).send(ArgumentMatchers.<SimpleMailMessage>argThat(sentTo(to)));
        assertThat(waitFor(this::incomplete, 0L)).isZero();
    }

    @Test
    void ac1_1_aSynchronousListenerCompletesItsPublicationToo() {
        transactions.executeWithoutResult(status -> events.publishEvent(
            new ReviewMeasured(ApprovalLevel.DEAN, ReviewDecisionType.APPROVED, true, Duration.ofHours(2))));

        assertThat(waitFor(this::incomplete, 0L)).isZero();
    }

    @Test
    void ac1_2_aRolledBackEventLeavesNoRowAndSendsNoMail() {
        String to = "it.registry.rollback@chnu.edu.ua";

        transactions.executeWithoutResult(status -> {
            events.publishEvent(decided(to));
            status.setRollbackOnly();
        });

        assertThat(incomplete()).isZero();
        verify(mailSender, after(QUIET_MS).never())
            .send(ArgumentMatchers.<SimpleMailMessage>argThat(sentTo(to)));
    }

    @Test
    void ac1_3_ac1_4_aRefusedMailStaysIncompleteAndTheRetryJobSendsIt() {
        String to = "it.registry.retry@chnu.edu.ua";
        doThrow(new MailSendException("down")).when(mailSender)
            .send(ArgumentMatchers.<SimpleMailMessage>argThat(sentTo(to)));

        transactions.executeWithoutResult(status -> events.publishEvent(decided(to)));

        verify(mailSender, timeout(FAILED_SEND_MS).times(3))
            .send(ArgumentMatchers.<SimpleMailMessage>argThat(sentTo(to)));
        assertThat(waitFor(this::incomplete, 1L)).isEqualTo(1);

        reset(mailSender);
        age(Duration.ofMinutes(5));
        retry.run();
        verify(mailSender, after(QUIET_MS).never()).send(any(SimpleMailMessage.class));
        assertThat(incomplete()).isEqualTo(1);

        age(Duration.ofMinutes(15));
        retry.run();
        verify(mailSender, timeout(MAIL_WAIT_MS)).send(ArgumentMatchers.<SimpleMailMessage>argThat(sentTo(to)));
        assertThat(waitFor(this::incomplete, 0L)).isZero();
    }

    @Test
    void ac1_5_aDayOldPublicationIsNoLongerRetriedAndAMonthOldOneIsDeleted() {
        insertIncomplete(Duration.ofHours(25));
        insertIncomplete(Duration.ofDays(31));

        retry.run();

        verify(mailSender, after(QUIET_MS).never()).send(any(SimpleMailMessage.class));
        assertThat(incomplete()).isEqualTo(1);
    }

    @Test
    void ac1_7_aTransientEventIsNeverStoredAndIsSentAfterTheCommit() {
        String to = "it.registry.transient@chnu.edu.ua";

        long inTransaction = Objects.requireNonNull(transactions.execute(status -> {
            afterCommit.publish(new PasswordResetRequested(to, "Олена", RESET_LINK));
            return incomplete();
        }));

        assertThat(inTransaction).isZero();
        verify(mailSender, timeout(MAIL_WAIT_MS)).send(ArgumentMatchers.<SimpleMailMessage>argThat(sentTo(to)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?",
            Long.class, "%token=%")).isZero();
    }

    @Test
    void ac1_7_aTransientEventIsDroppedOnRollback() {
        String to = "it.registry.transient.rollback@chnu.edu.ua";

        transactions.executeWithoutResult(status -> {
            afterCommit.publish(new PasswordResetRequested(to, "Олена", RESET_LINK));
            status.setRollbackOnly();
        });

        verify(mailSender, after(QUIET_MS).never())
            .send(ArgumentMatchers.<SimpleMailMessage>argThat(sentTo(to)));
        assertThat(incomplete()).isZero();
    }

    private static AwardDecided decided(String to) {
        return new AwardDecided(to, "Олена", 999_001L, "Certificate", "Грамота", RequestStatus.APPROVED,
            ApprovalLevel.FACULTY_SECRETARY, "Ірина Бойко", null);
    }

    private static ArgumentMatcher<SimpleMailMessage> sentTo(String to) {
        return message -> message != null && message.getTo() != null && List.of(message.getTo()).contains(to);
    }

    private long incomplete() {
        return Objects.requireNonNull(jdbc.queryForObject(
            "SELECT count(*) FROM event_publication WHERE completion_date IS NULL", Long.class));
    }

    private void age(Duration age) {
        jdbc.update("UPDATE event_publication SET publication_date = ?",
            Timestamp.from(Instant.now().minus(age)));
    }

    private void insertIncomplete(Duration age) {
        jdbc.update("INSERT INTO event_publication (id, listener_id, event_type, serialized_event, publication_date)"
                + " VALUES (gen_random_uuid(), 'listener', ?, '{}', ?)", AwardDecided.class.getName(),
            Timestamp.from(Instant.now().minus(age)));
    }

    private static <T> T waitFor(Supplier<T> value, T expected) {
        long deadline = System.currentTimeMillis() + MAIL_WAIT_MS;
        T current = value.get();
        while (!expected.equals(current) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return current;
            }
            current = value.get();
        }
        return current;
    }
}

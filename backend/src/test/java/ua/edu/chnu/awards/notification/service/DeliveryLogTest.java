package ua.edu.chnu.awards.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.common.mail.MailNotDeliveredException;
import ua.edu.chnu.awards.notification.entity.DeliveryStatus;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.repository.NewNotification;
import ua.edu.chnu.awards.notification.repository.NotificationRepository;
import ua.edu.chnu.awards.support.TestDeliveries;

class DeliveryLogTest {

    private static final String EMAIL = "olena@chnu.edu.ua";
    private static final String SUBJECT = "Subject";
    private static final String BODY = "Body";
    private static final Instant OCCURRED = Instant.parse("2026-10-14T09:59:00Z");

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DeliveryLog log = TestDeliveries.log(delivery, preferences, repository,
        new DeliveryMetrics(registry));

    @BeforeEach
    void allowEveryChannel() {
        when(preferences.allows(any(), any(), any())).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(1);
    }

    @Test
    void ac4_2_theMailRowIsClaimedBeforeTheSendAndMarkedSentAfterIt() {
        boolean sent = log.send(draft(7L, OCCURRED), EMAIL, SUBJECT, BODY);

        ArgumentCaptor<NewNotification> row = ArgumentCaptor.forClass(NewNotification.class);
        InOrder order = inOrder(repository, delivery);
        order.verify(repository).claim(row.capture(), eq(TestDeliveries.NOW.minus(TestDeliveries.RETRY_AFTER)));
        order.verify(delivery).send(EMAIL, SUBJECT, BODY);
        order.verify(repository).finish(row.getValue().dedupeKey(), DeliveryStatus.SENT, TestDeliveries.NOW);
        assertThat(sent).isTrue();
        assertThat(row.getValue()).extracting(NewNotification::userId, NewNotification::type,
                NewNotification::channel, NewNotification::sentAt)
            .containsExactly(7L, "AWARD_RETURNED", "EMAIL", TestDeliveries.NOW);
        assertThat(row.getValue().dedupeKey())
            .isEqualTo(new NotificationKeys().key(draft(7L, OCCURRED), NotificationChannel.EMAIL));
        assertThat(count("email", "sent")).isEqualTo(1.0);
    }

    @Test
    void ac4_2_aRefusedMailIsMarkedFailedAndTheFailurePropagates() {
        MailNotDeliveredException failure = new MailNotDeliveredException(SUBJECT, List.of(EMAIL), null);
        doThrow(failure).when(delivery).send(EMAIL, SUBJECT, BODY);

        assertThatThrownBy(() -> log.send(draft(7L, OCCURRED), EMAIL, SUBJECT, BODY)).isSameAs(failure);

        verify(repository).finish(anyString(), eq(DeliveryStatus.FAILED), isNull());
        verify(repository, never()).finish(anyString(), eq(DeliveryStatus.SENT), any());
        assertThat(count("email", "failed")).isEqualTo(1.0);
    }

    @Test
    void ac4_3_aMailSentOrBeingSentIsNotSentAgain() {
        when(repository.claim(any(), any())).thenReturn(0);

        boolean sent = log.send(draft(7L, OCCURRED), EMAIL, SUBJECT, BODY);

        assertThat(sent).isFalse();
        verifyNoInteractions(delivery);
        verify(repository, never()).finish(anyString(), any(), any());
        assertThat(count("email", "skipped")).isEqualTo(1.0);
    }

    @Test
    void ac4_4_aTypeSwitchedOffIsCountedAndNothingIsClaimed() {
        when(preferences.allows(7L, NotificationType.AWARD_RETURNED, NotificationChannel.EMAIL)).thenReturn(false);

        assertThat(log.send(draft(7L, OCCURRED), EMAIL, SUBJECT, BODY)).isFalse();

        verifyNoInteractions(delivery);
        verify(repository, never()).claim(any(), any());
        assertThat(count("email", "off")).isEqualTo(1.0);
    }

    @Test
    void ac4_4_anErasedRecipientIsCountedAsSkipped() {
        assertThat(log.send(draft(7L, OCCURRED), null, SUBJECT, BODY)).isFalse();

        verifyNoInteractions(delivery, repository);
        assertThat(count("email", "skipped")).isEqualTo(1.0);
    }

    @Test
    void edgeCase_anEventStoredBeforeItCarriedRecipientAndTimeIsSentWithoutALogRow() {
        assertThat(log.send(draft(7L, null), EMAIL, SUBJECT, BODY)).isTrue();
        assertThat(log.send(draft(null, OCCURRED), EMAIL, SUBJECT, BODY)).isTrue();

        verify(delivery, times(2)).send(EMAIL, SUBJECT, BODY);
        verifyNoInteractions(repository);
        assertThat(count("email", "sent")).isEqualTo(2.0);
    }

    private double count(String channel, String outcome) {
        return registry.get(DeliveryMetrics.DELIVERED).tag("channel", channel).tag("outcome", outcome)
            .tag("type", "AWARD_RETURNED").counter().count();
    }

    private static NotificationDraft draft(Long userId, Instant occurredAt) {
        return new NotificationDraft(userId, NotificationType.AWARD_RETURNED, Map.of("title", "Certificate"),
            "AWARD", 5L, occurredAt);
    }
}

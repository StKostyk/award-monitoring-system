package ua.edu.chnu.awards.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import ua.edu.chnu.awards.common.mail.MailContent;
import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.event.UnitReminderRequested;
import ua.edu.chnu.awards.notification.repository.NotificationRepository;
import ua.edu.chnu.awards.notification.service.DeliveryMetrics;
import ua.edu.chnu.awards.notification.service.NotificationDraft;
import ua.edu.chnu.awards.notification.service.NotificationDrafts;
import ua.edu.chnu.awards.notification.service.NotificationPreferenceService;
import ua.edu.chnu.awards.support.TestDeliveries;

class ReminderMailsTest {

    private static final String SUBJECT = "Нагадування про нагороди / Awards reminder";
    private static final UnitReminderRequested REMINDER = new UnitReminderRequested(8L, "colleague@chnu.edu.ua",
        "Петро", 64L, "Algebra and Informatics", "Кафедра алгебри та інформатики", 2026, "Аліна Секретаренко",
        Instant.parse("2026-11-18T10:00:00Z"));

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final ReminderMails mails = new ReminderMails(TestDeliveries.log(delivery, preferences, repository,
        new DeliveryMetrics(new SimpleMeterRegistry())), new NotificationDrafts());

    @Test
    void ac5_3_theMailNamesTheDepartmentTheYearAndTheSenderInBothLanguages() {
        when(preferences.allows(any(), any(), any())).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(1);
        ArgumentCaptor<MailContent> text = ArgumentCaptor.forClass(MailContent.class);

        mails.onUnitReminderRequested(REMINDER);

        verify(delivery).send(eq("colleague@chnu.edu.ua"), eq(SUBJECT), text.capture());
        assertThat(text.getValue().text()).contains("Вітаємо, Петро!", "Кафедра алгебри та інформатики",
            "Algebra and Informatics", "2026", "Аліна Секретаренко", "Hello Петро");
    }

    @Test
    void ac5_3_aColleagueWhoSwitchedTheMailsOffGetsNone() {
        when(preferences.allows(8L, NotificationType.AWARDS_REMINDER, NotificationChannel.EMAIL)).thenReturn(false);

        mails.onUnitReminderRequested(REMINDER);

        verify(delivery, never()).send(anyString(), anyString(), any(MailContent.class));
    }

    @Test
    void ac5_3_theDraftCarriesOnlyWhatTheSentenceNeeds() {
        NotificationDraft draft = new NotificationDrafts().of(REMINDER);

        assertThat(draft.type()).isEqualTo(NotificationType.AWARDS_REMINDER);
        assertThat(draft.userId()).isEqualTo(8L);
        assertThat(draft.payload()).containsOnlyKeys("unitId", "unit", "unitUk", "year", "sender")
            .containsEntry("unitId", 64L).containsEntry("year", 2026);
    }
}

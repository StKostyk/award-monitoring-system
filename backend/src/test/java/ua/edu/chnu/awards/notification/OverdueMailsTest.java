package ua.edu.chnu.awards.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.event.OverdueNoticed;
import ua.edu.chnu.awards.award.service.AwardLinks;
import ua.edu.chnu.awards.award.service.ReviewMetrics;
import ua.edu.chnu.awards.common.mail.MailContent;
import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.common.mail.MailNotDeliveredException;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.service.NotificationDrafts;
import ua.edu.chnu.awards.notification.service.NotificationPreferenceService;
import ua.edu.chnu.awards.support.TestDeliveries;

class OverdueMailsTest {

    private static final String DEAN = "dean.fmi@chnu.edu.ua";
    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final AwardLinks links = mock(AwardLinks.class);
    private final ReviewMetrics metrics = mock(ReviewMetrics.class);
    private final NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
    private final OverdueMails mails = new OverdueMails(TestDeliveries.log(delivery, preferences),
        new NotificationDrafts(), links, metrics, Clock.system(KYIV));

    @BeforeEach
    void setUp() {
        when(links.awardLink(anyLong())).thenReturn("http://localhost:4200/awards/5");
        when(preferences.allows(any(), any(), any())).thenReturn(true);
    }

    @Test
    void ac2_9_theDigestListsEveryRequestInUkrainianAndEnglish() {
        OverdueNoticed event = digest();

        String body = OverdueMails.body(event, awardId -> "link-" + awardId, KYIV);

        assertThat(OverdueMails.subject(event)).isEqualTo("Прострочені заявки: 2 / Overdue requests: 2");
        assertThat(body).contains("Ірина Бойко", "«Грамота», Анастасія Коваль; рівень: секретар факультету;"
                + " термін: 06.10.2026 12:00; розглядає: не взято", "link-5",
            "\"Certificate\", Анастасія Коваль; level: faculty secretary; deadline: 06.10.2026 12:00;"
                + " reviewer: not taken", "розглядає: Олег Шевчук", "reviewer: Олег Шевчук",
            "link-7")
            .containsSubsequence("Термін розгляду", "---", "The review period");
    }

    @Test
    void ac2_2_ac2_6_aDeliveredDigestIsCounted() {
        mails.onNoticed(digest());

        verify(delivery).send(eq(DEAN), anyString(), any(MailContent.class));
        verify(metrics).noticeSent();
    }

    @Test
    void ac1_9_aRefusedDigestIsNotCountedAndStaysIncomplete() {
        doThrow(new MailNotDeliveredException("s", List.of(DEAN), null))
            .when(delivery).send(eq(DEAN), anyString(), any(MailContent.class));
        OverdueNoticed digest = digest();

        assertThatThrownBy(() -> mails.onNoticed(digest)).isInstanceOf(MailNotDeliveredException.class);

        verify(metrics, never()).noticeSent();
    }

    @Test
    void ac3_5_noDigestAndNoCountIfTheReviewerSwitchedTheseMailsOff() {
        when(preferences.allows(4L, NotificationType.REVIEW_OVERDUE, NotificationChannel.EMAIL)).thenReturn(false);

        mails.onNoticed(digest());

        verifyNoInteractions(delivery, metrics);
    }

    @Test
    void ac2_2_aReviewerWithoutAddressGetsNothing() {
        mails.onNoticed(new OverdueNoticed(4L, null, "Ірина Бойко", digest().requests(), null));

        verifyNoInteractions(delivery, metrics);
    }

    private static OverdueNoticed digest() {
        Instant deadline = Instant.parse("2026-10-06T09:00:00Z");
        return new OverdueNoticed(4L, DEAN, "Ірина Бойко", List.of(
            new OverdueNoticed.Item(5L, "Certificate", "Грамота", "Анастасія Коваль", ApprovalLevel.FACULTY_SECRETARY,
                deadline, null),
            new OverdueNoticed.Item(7L, "Diploma", "Диплом", "Анастасія Коваль", ApprovalLevel.FACULTY_SECRETARY,
                deadline.plusSeconds(3600), "Олег Шевчук")), null);
    }
}

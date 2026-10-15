package ua.edu.chnu.awards.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.award.event.ReviewTakenOver;
import ua.edu.chnu.awards.award.service.AwardLinks;
import ua.edu.chnu.awards.common.mail.MailContent;
import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.service.NotificationDrafts;
import ua.edu.chnu.awards.notification.service.NotificationPreferenceService;
import ua.edu.chnu.awards.support.TestDeliveries;
import ua.edu.chnu.awards.support.TestEvents;

class TakeOverMailsTest {

    @Test
    void ac3_5_noMessageIfTheHolderSwitchedTheseMailsOff() {
        when(preferences.allows(21L, NotificationType.REVIEW_TAKEN_OVER, NotificationChannel.EMAIL)).thenReturn(false);

        mails.onTakenOver(event("secretary@chnu.edu.ua"));

        verify(delivery, never()).send(anyString(), anyString(), any(MailContent.class));
    }

    private static final String LINK = "http://localhost:4200/awards/5";

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final AwardLinks links = mock(AwardLinks.class);
    private final NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
    private final TakeOverMails mails = new TakeOverMails(TestDeliveries.log(delivery, preferences),
        new NotificationDrafts(), links);

    @BeforeEach
    void allowEveryChannel() {
        when(preferences.allows(any(), any(), any())).thenReturn(true);
    }

    @Test
    void ac2_thePreviousHolderGetsOneMessageWithALinkToTheAward() {
        when(links.awardLink(5L)).thenReturn(LINK);

        mails.onTakenOver(event("secretary@chnu.edu.ua"));

        verify(delivery).send("secretary@chnu.edu.ua", TakeOverMails.SUBJECT,
            MailContent.notification(TakeOverMails.body(event("secretary@chnu.edu.ua"), LINK)));
    }

    @Test
    void ac2_anErasedHolderGetsNoMessage() {
        mails.onTakenOver(event(null));

        verify(delivery, never()).send(anyString(), anyString(), any(MailContent.class));
    }

    @Test
    void ac2_theMessageNamesTheReviewerAndTheAwardInBothLanguages() {
        String body = TakeOverMails.body(event("secretary@chnu.edu.ua"), LINK);

        assertThat(body).contains("Аліна Мартинюк взяв(ла) на себе розгляд заявки «Премія»")
            .contains("Аліна Мартинюк took over the review of \"Prize\"")
            .contains("Переглянути нагороду: " + LINK).contains("View the award: " + LINK);
    }

    private static ReviewTakenOver event(String email) {
        return TestEvents.takenOver().to(21L, email, "Ірина").award(5L, "Prize", "Премія").by("Аліна Мартинюк").build();
    }
}

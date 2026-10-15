package ua.edu.chnu.awards.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.event.AwardDecided;
import ua.edu.chnu.awards.award.service.AwardLinks;
import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.service.NotificationDrafts;
import ua.edu.chnu.awards.notification.service.NotificationPreferenceService;
import ua.edu.chnu.awards.support.TestDeliveries;

class DecisionMailsTest {

    private static final String LINK = "http://localhost:4200/awards/5";

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final AwardLinks links = mock(AwardLinks.class);
    private final NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
    private final DecisionMails mails = new DecisionMails(TestDeliveries.log(delivery, preferences),
        new NotificationDrafts(), links);

    @BeforeEach
    void allowEveryChannel() {
        when(preferences.allows(any(), any(), any())).thenReturn(true);
    }

    @Test
    void ac2_10_theOwnerGetsOneMessageWithALinkToTheAward() {
        when(links.awardLink(5L)).thenReturn(LINK);

        mails.onDecided(event(RequestStatus.APPROVED, ApprovalLevel.DEAN, null));

        verify(delivery).send("employee@chnu.edu.ua", "Нагороду затверджено / Award approved",
            DecisionMails.body(event(RequestStatus.APPROVED, ApprovalLevel.DEAN, null), LINK));
    }

    @Test
    void ac2_10_anErasedOwnerGetsNoMessage() {
        mails.onDecided(new AwardDecided(21L, null, "Ірина", 5L, "Prize", "Премія", RequestStatus.APPROVED,
            ApprovalLevel.DEAN, "Аліна Мартинюк", null, null));

        verify(delivery, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void ac3_5_noMessageIfTheOwnerSwitchedTheseMailsOff() {
        when(preferences.allows(21L, NotificationType.AWARD_REJECTED, NotificationChannel.EMAIL)).thenReturn(false);

        mails.onDecided(event(RequestStatus.REJECTED, ApprovalLevel.DEAN, null));
        mails.onDecided(event(RequestStatus.APPROVED, ApprovalLevel.DEAN, null));

        verify(delivery).send(eq("employee@chnu.edu.ua"), eq("Нагороду затверджено / Award approved"), anyString());
        verify(delivery, never()).send(anyString(), eq("Нагороду відхилено / Award rejected"), anyString());
    }

    @Test
    void ac2_10_anApprovalNamesTheReviewerInBothLanguages() {
        String body = DecisionMails.body(event(RequestStatus.APPROVED, ApprovalLevel.DEAN, null), LINK);

        assertThat(body).contains("«Премія»: Аліна Мартинюк затвердив(ла) нагороду.")
            .contains("\"Prize\": Аліна Мартинюк approved the award.")
            .contains("Переглянути нагороду: " + LINK).contains("View the award: " + LINK)
            .doesNotContain("Коментар");
    }

    @Test
    void ac2_10_aPassedOnAwardNamesTheNextLevel() {
        AwardDecided event = event(RequestStatus.ESCALATED, ApprovalLevel.DEAN, null);

        assertThat(DecisionMails.subject(event)).isEqualTo("Нагороду передано далі / Award passed on");
        assertThat(DecisionMails.body(event, LINK)).contains("передав(ла) нагороду декану.")
            .contains("passed the award on to the dean.");
    }

    @Test
    void ac2_10_aReturnCarriesTheComment() {
        AwardDecided event = event(RequestStatus.RETURNED, ApprovalLevel.DEAN, "Додайте номер наказу");

        assertThat(DecisionMails.subject(event))
            .isEqualTo("Нагороду повернуто на доопрацювання / Award returned for changes");
        assertThat(DecisionMails.body(event, LINK)).contains("Коментар: Додайте номер наказу")
            .contains("Comment: Додайте номер наказу").contains("повернув(ла) нагороду на доопрацювання");
    }

    @Test
    void ac2_10_aRejectionCarriesTheComment() {
        AwardDecided event = event(RequestStatus.REJECTED, ApprovalLevel.DEAN, "Не відповідає положенню");

        assertThat(DecisionMails.subject(event)).isEqualTo("Нагороду відхилено / Award rejected");
        assertThat(DecisionMails.body(event, LINK)).contains("відхилив(ла) нагороду.")
            .contains("rejected the award.").contains("Коментар: Не відповідає положенню");
    }

    private static AwardDecided event(RequestStatus outcome, ApprovalLevel level, String comment) {
        return new AwardDecided(21L, "employee@chnu.edu.ua", "Ірина", 5L, "Prize", "Премія", outcome, level,
            "Аліна Мартинюк", comment, null);
    }
}

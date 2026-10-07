package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.event.AwardDecided;
import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.config.AuthProperties;

class DecisionMailsTest {

    private static final String LINK = "http://localhost:4200/awards/5";

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final AuthProperties properties = mock(AuthProperties.class);
    private final DecisionMails mails = new DecisionMails(delivery, properties);

    @Test
    void ac2_10_theOwnerGetsOneMessageWithALinkToTheAward() {
        when(properties.frontendUrl()).thenReturn("http://localhost:4200");

        mails.onDecided(event(RequestStatus.APPROVED, ApprovalLevel.DEAN, null));

        verify(delivery).send("employee@chnu.edu.ua", "Нагороду затверджено / Award approved",
            DecisionMails.body(event(RequestStatus.APPROVED, ApprovalLevel.DEAN, null), LINK));
    }

    @Test
    void ac2_10_anErasedOwnerGetsNoMessage() {
        mails.onDecided(new AwardDecided(null, "Ірина", 5L, "Prize", "Премія", RequestStatus.APPROVED,
            ApprovalLevel.DEAN, "Аліна Мартинюк", null));

        verify(delivery, never()).send(anyString(), anyString(), anyString());
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
        return new AwardDecided("employee@chnu.edu.ua", "Ірина", 5L, "Prize", "Премія", outcome, level,
            "Аліна Мартинюк", comment);
    }
}

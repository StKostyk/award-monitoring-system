package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.award.event.AwardCorrected;
import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.config.AuthProperties;

class CorrectionMailsTest {

    private static final String LINK = "http://localhost:4200/awards/5";

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final AuthProperties properties = mock(AuthProperties.class);
    private final CorrectionMails mails = new CorrectionMails(delivery, properties);

    @Test
    void ac3_5_theOwnerGetsOneMessageWithALinkToTheAward() {
        when(properties.frontendUrl()).thenReturn("http://localhost:4200");

        mails.onCorrected(event("employee@chnu.edu.ua"));

        verify(delivery).send("employee@chnu.edu.ua", CorrectionMails.SUBJECT,
            CorrectionMails.body(event("employee@chnu.edu.ua"), LINK));
    }

    @Test
    void ac3_5_anErasedOwnerGetsNoMessage() {
        mails.onCorrected(event(null));

        verify(delivery, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void ac3_5_theMessageListsOldAndNewValuesTheReasonAndTheReviewerInBothLanguages() {
        String body = CorrectionMails.body(event("employee@chnu.edu.ua"), LINK);

        assertThat(body).contains("Аліна Мартинюк виправив(ла) нагороду «Премія»:")
            .contains("- Категорія: Університетська нагорода → Відзнака міністерства")
            .contains("- Дата нагородження: 2025-05-01 → 2025-06-01")
            .contains("- Посилання: — → https://mon.gov.ua")
            .contains("Причина: Дата з наказу")
            .contains("Аліна Мартинюк corrected the award \"Prize\":")
            .contains("- Category: University Award → Ministry Recognition")
            .contains("- Link: — → https://mon.gov.ua")
            .contains("Reason: Дата з наказу")
            .contains("Переглянути нагороду: " + LINK).contains("View the award: " + LINK);
    }

    private static AwardCorrected event(String email) {
        return new AwardCorrected(email, "Ірина", 5L, "Prize", "Премія", "Аліна Мартинюк", "Дата з наказу", List.of(
            new AwardCorrected.Change("awardDate", "2025-05-01", "2025-06-01", "2025-05-01", "2025-06-01"),
            new AwardCorrected.Change("categoryId", "University Award", "Ministry Recognition",
                "Університетська нагорода", "Відзнака міністерства"),
            new AwardCorrected.Change("externalUrl", null, "https://mon.gov.ua", null, "https://mon.gov.ua")));
    }
}

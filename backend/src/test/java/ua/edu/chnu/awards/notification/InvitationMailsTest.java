package ua.edu.chnu.awards.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.award.event.PersonInvited;
import ua.edu.chnu.awards.award.service.AwardLinks;
import ua.edu.chnu.awards.common.mail.MailDelivery;

class InvitationMailsTest {

    private static final String EMAIL = "olena.koval@chnu.edu.ua";
    private static final String REGISTER = "http://localhost:4200/register?email=olena.koval%40chnu.edu.ua";
    private static final String NOTICE = "http://localhost:4200/privacy-notice";

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final AwardLinks links = mock(AwardLinks.class);
    private final InvitationMails mails = new InvitationMails(delivery, links);

    @Test
    void ac1_4_theInvitationNamesTheSenderTheDepartmentTheRightsAndTheRegistrationLink() {
        String body = InvitationMails.body(event(), REGISTER, NOTICE);

        assertThat(body).contains("Ірина Секретар додав(ла) вас", "«Кафедра алгебри»", "ст. 6(1)(f) GDPR",
            "заперечити", "видалення", "gdpr@chnu.edu.ua", REGISTER, NOTICE);
        assertThat(body).contains("Ірина Секретар added you", "\"Department of Algebra\"", "Art. 6(1)(f)",
            "right to object", "erasure");
    }

    @Test
    void ac1_4_oneMailGoesToThePersonsAddress() {
        when(links.registrationLink(EMAIL)).thenReturn(REGISTER);
        when(links.privacyNoticeLink()).thenReturn(NOTICE);

        mails.onInvited(event());

        verify(delivery).send(eq(EMAIL), eq(InvitationMails.SUBJECT), startsWith("Вітаємо"));
    }

    private static PersonInvited event() {
        return new PersonInvited(7L, EMAIL, "Олена", "Ірина Секретар", "Department of Algebra", "Кафедра алгебри",
            "gdpr@chnu.edu.ua");
    }
}

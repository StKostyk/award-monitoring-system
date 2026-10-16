package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.gdpr.event.DataExported;
import ua.edu.chnu.awards.gdpr.event.ErasureCancelled;
import ua.edu.chnu.awards.gdpr.event.ErasureRequested;
import ua.edu.chnu.awards.gdpr.event.ErasureWarned;

class PrivacyMailsTest {

    private static final String EMAIL = "employee.fmi@chnu.edu.ua";
    private static final Instant DUE = Instant.parse("2026-11-15T08:00:00Z");

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final PrivacyMails mails = new PrivacyMails(delivery, new PrivacyLinks("http://localhost:4200"),
        Clock.fixed(Instant.parse("2026-10-16T08:00:00Z"), ZoneId.of("Europe/Kyiv")));

    @Test
    void ac34_tellsTheAccountWhenWhereFromAndWithWhatTheDataWasExported() {
        mails.onDataExported(new DataExported(EMAIL, "Анастасія",
            Instant.parse("2026-09-30T09:00:00Z"), "10.0.0.7", "Firefox"));

        assertThat(sent("Ваші дані експортовано / Your data was exported")).contains("Вітаємо, Анастасія!",
            "Hello Анастасія,", "2026-09-30T09:00:00Z", "IP: 10.0.0.7", "Браузер / Browser: Firefox", "JSON");
    }

    @Test
    void ac4_2_theRequestMailNamesTheKyivDateWhatIsKeptAndTheCancelLink() {
        mails.onErasureRequested(new ErasureRequested(EMAIL, "Анастасія", DUE));

        assertThat(sent("Запит на видалення облікового запису / Account erasure requested")).contains(
            "Вітаємо, Анастасія!", "15.11.2026 10:00", "under a pseudonym", "seven years",
            "http://localhost:4200/privacy", "If this was not you");
    }

    @Test
    void ac4_6_theWarningMailNamesTheDateAndTheCancelLink() {
        mails.onErasureWarned(new ErasureWarned(EMAIL, "Анастасія", DUE));

        assertThat(sent("Обліковий запис скоро буде видалено / Your account will be erased soon"))
            .contains("15.11.2026 10:00", "http://localhost:4200/privacy");
    }

    @Test
    void ac4_4_theCancelMailConfirmsTheAccountStays() {
        mails.onErasureCancelled(new ErasureCancelled(EMAIL, "Анастасія"));

        assertThat(sent("Видалення скасовано / Account erasure cancelled")).contains("Hello Анастасія,",
            "The account stays active");
    }

    private String sent(String subject) {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(delivery).send(eq(EMAIL), eq(subject), text.capture());
        return text.getValue();
    }
}

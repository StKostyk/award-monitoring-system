package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.gdpr.event.DataExported;

class PrivacyMailsTest {

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final PrivacyMails mails = new PrivacyMails(delivery);

    @Test
    void ac34_tellsTheAccountWhenWhereFromAndWithWhatTheDataWasExported() {
        mails.onDataExported(new DataExported("employee.fmi@chnu.edu.ua", "Анастасія",
            Instant.parse("2026-09-30T09:00:00Z"), "10.0.0.7", "Firefox"));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(delivery).send(eq("employee.fmi@chnu.edu.ua"), eq("Ваші дані експортовано / Your data was exported"),
            text.capture());
        assertThat(text.getValue()).contains("Вітаємо, Анастасія!", "Hello Анастасія,", "2026-09-30T09:00:00Z",
            "IP: 10.0.0.7", "Браузер / Browser: Firefox", "JSON");
    }
}

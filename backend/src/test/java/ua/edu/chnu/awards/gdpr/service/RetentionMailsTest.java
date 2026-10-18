package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.gdpr.event.RetentionRunIncomplete;

class RetentionMailsTest {

    private static final Instant STARTED = Instant.parse("2026-10-18T01:00:00Z");
    private static final List<String> OFFICERS = List.of("officer@chnu.edu.ua", "second@chnu.edu.ua");

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final RetentionMails mails = new RetentionMails(delivery, new PrivacyLinks("http://localhost:4200"),
        Clock.fixed(STARTED, ZoneId.of("Europe/Kyiv")));

    @Test
    void ac1_4_oneMailGoesToEveryOfficerNamingTheRunTheFailedPoliciesAndTheCompliancePage() {
        RetentionRunIncomplete event = new RetentionRunIncomplete(12L, STARTED, "PARTIAL",
            List.of("devices", "one-time-tokens"), OFFICERS);

        mails.onRunIncomplete(event);

        verify(delivery).send(OFFICERS,
            "Збереження даних: запуск № 12 не завершено / Data retention: run 12 did not complete",
            RetentionMails.body(event, "18.10.2026 04:00", "http://localhost:4200/compliance"));
        assertThat(RetentionMails.body(event, "18.10.2026 04:00", "http://localhost:4200/compliance"))
            .contains("Запуск № 12", "18.10.2026 04:00", "Політики з помилкою: devices, one-time-tokens.",
                "Failed policies: devices, one-time-tokens.", "status PARTIAL",
                "http://localhost:4200/compliance");
    }

    @Test
    void ac1_4_anInterruptedRunWithoutFailedPoliciesNamesOnlyItsStatus() {
        RetentionRunIncomplete event = new RetentionRunIncomplete(11L, STARTED, "INTERRUPTED", List.of(), OFFICERS);

        assertThat(RetentionMails.body(event, "18.10.2026 04:00", "http://localhost:4200/compliance"))
            .contains("status INTERRUPTED", "стан INTERRUPTED")
            .doesNotContain("Failed policies", "Політики з помилкою");
    }
}

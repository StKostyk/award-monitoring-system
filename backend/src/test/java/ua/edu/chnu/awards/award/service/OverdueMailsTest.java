package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.event.OverdueNoticed;
import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.config.AuthProperties;

class OverdueMailsTest {

    private static final String DEAN = "dean.fmi@chnu.edu.ua";

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final AuthProperties properties = mock(AuthProperties.class);
    private final ReviewMetrics metrics = mock(ReviewMetrics.class);
    private final OverdueMails mails = new OverdueMails(delivery, properties, metrics);

    @BeforeEach
    void setUp() {
        when(properties.frontendUrl()).thenReturn("http://localhost:4200");
    }

    @Test
    void ac2_9_theDigestListsEveryRequestInUkrainianAndEnglish() {
        OverdueNoticed event = digest();

        String body = OverdueMails.body(event, "http://localhost:4200/awards/");

        assertThat(OverdueMails.subject(event)).isEqualTo("Прострочені заявки: 2 / Overdue requests: 2");
        assertThat(body).contains("Ірина Бойко", "«Грамота», Анастасія Коваль; рівень: секретар факультету;"
                + " термін: 06.10.2026 12:00; розглядає: не взято", "http://localhost:4200/awards/5",
            "\"Certificate\", Анастасія Коваль; level: faculty secretary; deadline: 06.10.2026 12:00;"
                + " reviewer: not taken", "розглядає: Олег Шевчук", "reviewer: Олег Шевчук",
            "http://localhost:4200/awards/7")
            .containsSubsequence("Термін розгляду", "---", "The review period");
    }

    @Test
    void ac2_2_ac2_6_aDeliveredDigestIsCounted() {
        when(delivery.send(eq(DEAN), anyString(), anyString())).thenReturn(true);

        mails.onNoticed(digest());

        verify(metrics).noticeSent();
    }

    @Test
    void ac2_7_aRefusedDigestIsNeitherCountedNorRetried() {
        when(delivery.send(eq(DEAN), anyString(), anyString())).thenReturn(false);

        mails.onNoticed(digest());

        verify(delivery).send(eq(DEAN), anyString(), anyString());
        verify(metrics, never()).noticeSent();
    }

    @Test
    void ac2_2_aReviewerWithoutAddressGetsNothing() {
        mails.onNoticed(new OverdueNoticed(null, "Ірина Бойко", digest().requests()));

        verifyNoInteractions(delivery, metrics);
    }

    private static OverdueNoticed digest() {
        Instant deadline = Instant.parse("2026-10-06T09:00:00Z");
        return new OverdueNoticed(DEAN, "Ірина Бойко", List.of(
            new OverdueNoticed.Item(5L, "Certificate", "Грамота", "Анастасія Коваль", ApprovalLevel.FACULTY_SECRETARY,
                deadline, null),
            new OverdueNoticed.Item(7L, "Diploma", "Диплом", "Анастасія Коваль", ApprovalLevel.FACULTY_SECRETARY,
                deadline.plusSeconds(3600), "Олег Шевчук")));
    }
}

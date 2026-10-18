package ua.edu.chnu.awards.gdpr.service;

import static ua.edu.chnu.awards.common.mail.MailTextHelper.SEPARATOR;

import java.time.Clock;
import java.time.format.DateTimeFormatter;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.gdpr.event.RetentionRunIncomplete;

import lombok.RequiredArgsConstructor;

/**
 * Tells the data protection officers that a retention run did not complete, after the transaction commits.
 */
@Component
@RequiredArgsConstructor
public class RetentionMails {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final MailDelivery delivery;
    private final PrivacyLinks links;
    private final Clock clock;

    /**
     * Sends one mail to the officers naming the run and its failed policies.
     *
     * @param event the committed run
     */
    @Async
    @TransactionalEventListener
    public void onRunIncomplete(RetentionRunIncomplete event) {
        delivery.send(event.recipients(), subject(event.runId()),
            body(event, DATE.withZone(clock.getZone()).format(event.startedAt()), links.compliance()));
    }

    static String subject(long runId) {
        return "Збереження даних: запуск № " + runId + " не завершено / Data retention: run " + runId
            + " did not complete";
    }

    static String body(RetentionRunIncomplete event, String started, String link) {
        String failed = String.join(", ", event.failedPolicies());
        String failedUk = failed.isEmpty() ? "" : "Політики з помилкою: " + failed + ".\n\n";
        String failedEn = failed.isEmpty() ? "" : "Failed policies: " + failed + ".\n\n";
        return "Вітаємо!\n\n"
            + "Запуск № " + event.runId() + " збереження даних, розпочатий " + started + ", не завершено "
            + "(стан " + event.status() + ").\n\n"
            + failedUk
            + "Подробиці на сторінці відповідності: " + link + "\n\n"
            + SEPARATOR
            + "Hello,\n\n"
            + "Data retention run " + event.runId() + ", started " + started + ", did not complete (status "
            + event.status() + ").\n\n"
            + failedEn
            + "Details are on the compliance page: " + link + "\n";
    }
}

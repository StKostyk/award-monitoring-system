package ua.edu.chnu.awards.notification.service;

import java.time.Clock;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.event.EventProperties;
import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.common.mail.MailNotDeliveredException;
import ua.edu.chnu.awards.notification.entity.DeliveryStatus;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.repository.NewNotification;
import ua.edu.chnu.awards.notification.repository.NotificationRepository;
import ua.edu.chnu.awards.notification.service.DeliveryMetrics.Outcome;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the mail of a preference type at most once per event and recipient: the {@code EMAIL} row of the dedupe key
 * is claimed before the send and marked sent or failed after it, so a retried publication does not mail again what
 * the server accepted or is still sending.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryLog {

    private final MailDelivery delivery;
    private final NotificationRows rows;
    private final NotificationRepository notifications;
    private final NotificationPreferenceService preferences;
    private final DeliveryMetrics metrics;
    private final EventProperties events;
    private final Clock clock;

    /**
     * Sends one mail unless the recipient has no address or account, switched the type off for e-mail, or the mail
     * of this event was sent or is being sent. A draft without recipient or event time (events stored before they
     * carried them) is sent without a log row.
     *
     * @param draft   the notification the mail belongs to
     * @param email   the recipient's address, null when erased
     * @param subject the subject
     * @param body    the text
     * @return true when the mail server accepted the mail in this call
     * @throws MailNotDeliveredException when the server did not accept it; the row is marked failed
     */
    public boolean send(NotificationDraft draft, String email, String subject, String body) {
        NotificationType type = draft.type();
        if (email == null) {
            metrics.count(NotificationChannel.EMAIL, type, Outcome.SKIPPED);
            return false;
        }
        if (!preferences.allows(draft.userId(), type, NotificationChannel.EMAIL)) {
            metrics.count(NotificationChannel.EMAIL, type, Outcome.OFF);
            return false;
        }
        if (draft.userId() == null || draft.occurredAt() == null) {
            return deliver(type, null, email, subject, body);
        }
        NewNotification row = rows.row(draft, NotificationChannel.EMAIL);
        if (notifications.claim(row, row.sentAt().minus(events.retryAfter())) == 0) {
            log.info("Mail {} for user {} was sent, is being sent or has no recipient", type, draft.userId());
            metrics.count(NotificationChannel.EMAIL, type, Outcome.SKIPPED);
            return false;
        }
        return deliver(type, row.dedupeKey(), email, subject, body);
    }

    private boolean deliver(NotificationType type, String key, String email, String subject, String body) {
        try {
            delivery.send(email, subject, body);
        } catch (MailNotDeliveredException e) {
            if (key != null) {
                notifications.finish(key, DeliveryStatus.FAILED, null);
            }
            metrics.count(NotificationChannel.EMAIL, type, Outcome.FAILED);
            throw e;
        }
        if (key != null) {
            notifications.finish(key, DeliveryStatus.SENT, clock.instant());
        }
        metrics.count(NotificationChannel.EMAIL, type, Outcome.SENT);
        return true;
    }
}

package ua.edu.chnu.awards.notification.repository;

import java.time.Instant;

/**
 * The values of one notification row to insert.
 *
 * @param userId        the recipient
 * @param type          the notification type name
 * @param channel       the channel name
 * @param payload       the payload as JSON
 * @param dedupeKey     the key of this event, channel and recipient
 * @param referenceType the related entity type, null when none
 * @param referenceId   the related entity, null when none
 * @param sentAt        when it was written
 * @param expiresAt     when it may be deleted
 */
public record NewNotification(long userId, String type, String channel, String payload, String dedupeKey,
                              String referenceType, Long referenceId, Instant sentAt, Instant expiresAt) {
}

package ua.edu.chnu.awards.notification.dto;

import java.time.Instant;
import java.util.Map;

import ua.edu.chnu.awards.notification.entity.Notification;
import ua.edu.chnu.awards.notification.entity.NotificationType;

/**
 * One in-app notification; the client renders its sentence from the type and the payload.
 *
 * @param id            the notification
 * @param type          what it is about
 * @param payload       the values its sentence needs
 * @param referenceType {@code AWARD} for award types, null otherwise
 * @param referenceId   the award, null otherwise
 * @param read          whether it was read
 * @param sentAt        when it was written
 */
public record NotificationResponse(long id, NotificationType type, Map<String, Object> payload,
                                   String referenceType, Long referenceId, boolean read, Instant sentAt) {

    /**
     * The response for one row.
     *
     * @param notification the row
     * @return the response
     */
    public static NotificationResponse of(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getPayload(),
            notification.getReferenceType(), notification.getReferenceId(), notification.isRead(),
            notification.getSentAt());
    }
}

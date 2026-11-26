package ua.edu.chnu.awards.notification.service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import ua.edu.chnu.awards.notification.entity.NotificationType;

/**
 * A notification for one recipient, before it is written.
 *
 * @param userId        the recipient, null in events stored before it was carried
 * @param type          what it is about
 * @param payload       the values its sentence needs
 * @param referenceType {@code AWARD} for award types, null otherwise
 * @param referenceId   the award, null otherwise
 * @param occurredAt    when the event happened, null in events stored before it was carried
 */
public record NotificationDraft(Long userId, NotificationType type, Map<String, Object> payload,
                                String referenceType, Long referenceId, Instant occurredAt) {

    /**
     * Keeps an unmodifiable copy of the payload; its values may be null.
     */
    public NotificationDraft {
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}

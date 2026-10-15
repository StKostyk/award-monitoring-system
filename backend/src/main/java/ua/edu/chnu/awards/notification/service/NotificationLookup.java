package ua.edu.chnu.awards.notification.service;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.notification.entity.Notification;
import ua.edu.chnu.awards.notification.entity.NotificationPreference;
import ua.edu.chnu.awards.notification.repository.NotificationPreferenceRepository;
import ua.edu.chnu.awards.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read access to a person's notifications and stored preferences for other modules, such as the data export.
 */
@Service
@RequiredArgsConstructor
public class NotificationLookup {

    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;

    /**
     * Every notification row of a person, in-app and the delivery log of mails, newest first.
     *
     * @param userId the person
     * @return the rows
     */
    @Transactional(readOnly = true)
    public List<NotificationRecord> notificationsOf(long userId) {
        return notifications.findByUserIdOrderBySentAtDesc(userId).stream().map(NotificationLookup::record).toList();
    }

    /**
     * The preferences a person changed from the default, by type name.
     *
     * @param userId the person
     * @return the stored rows
     */
    @Transactional(readOnly = true)
    public List<PreferenceRecord> preferencesOf(long userId) {
        return preferences.findByUserId(userId).stream()
            .map(NotificationLookup::record)
            .sorted(Comparator.comparing(PreferenceRecord::type))
            .toList();
    }

    private static NotificationRecord record(Notification row) {
        return new NotificationRecord(row.getType().name(), row.getChannel().name(), row.getPayload(), row.isRead(),
            row.getSentAt(), row.getReadAt(), row.getDeliveredAt(),
            row.getDeliveryStatus() == null ? null : row.getDeliveryStatus().name());
    }

    private static PreferenceRecord record(NotificationPreference row) {
        return new PreferenceRecord(row.getType().name(), row.isEmailEnabled(), row.isInAppEnabled());
    }

    /**
     * One notification row.
     *
     * @param type           the notification type
     * @param channel        {@code IN_APP} or {@code EMAIL}
     * @param payload        the values of its sentence
     * @param read           whether an in-app row was read
     * @param sentAt         when it was written or the mail was claimed
     * @param readAt         when it was read, null when not
     * @param deliveredAt    when the mail server accepted the mail, null otherwise
     * @param deliveryStatus {@code SENDING}, {@code SENT} or {@code FAILED} for mails, null for in-app rows
     */
    public record NotificationRecord(String type, String channel, Map<String, Object> payload, boolean read,
                                     Instant sentAt, Instant readAt, Instant deliveredAt, String deliveryStatus) {

        /**
         * Keeps an unmodifiable copy of the payload; its values may be null.
         */
        public NotificationRecord {
            payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }

    /**
     * One stored preference.
     *
     * @param type  the notification type
     * @param email whether mails of the type are sent
     * @param inApp whether in-app notifications of the type are written
     */
    public record PreferenceRecord(String type, boolean email, boolean inApp) {
    }
}

package ua.edu.chnu.awards.notification.service;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.HashUtils;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;

/**
 * Derives the dedupe key of a notification, so a repeated delivery of one event writes nothing.
 */
@Component
public class NotificationKeys {

    /**
     * SHA-256 of type, channel, recipient, reference and event time.
     *
     * @param draft   the notification
     * @param channel the channel it goes out on
     * @return 64 hex characters
     */
    public String key(NotificationDraft draft, NotificationChannel channel) {
        return HashUtils.sha256Hex(String.join("|", draft.type().name(), channel.name(),
            String.valueOf(draft.userId()), String.valueOf(draft.referenceType()),
            String.valueOf(draft.referenceId()), String.valueOf(draft.occurredAt())));
    }
}

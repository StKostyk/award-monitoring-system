package ua.edu.chnu.awards.notification.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.common.retention.RetentionProperties;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.repository.NewNotification;

import lombok.RequiredArgsConstructor;

/**
 * Turns a draft into the row of one channel: payload as JSON, dedupe key, written now, kept for the set period.
 */
@Component
@RequiredArgsConstructor
public class NotificationRows {

    private final NotificationKeys keys;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final RetentionProperties retention;

    /**
     * The row of a draft on a channel.
     *
     * @param draft   the notification, with recipient and event time
     * @param channel the channel
     * @return the values to insert
     */
    public NewNotification row(NotificationDraft draft, NotificationChannel channel) {
        Instant sentAt = clock.instant();
        Instant expiresAt = sentAt.plus(retention.notifications());
        return new NewNotification(draft.userId(), draft.type().name(), channel.name(), json(draft),
            keys.key(draft, channel), draft.referenceType(), draft.referenceId(), sentAt, expiresAt);
    }

    private String json(NotificationDraft draft) {
        try {
            return mapper.writeValueAsString(draft.payload());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Payload of " + draft.type() + " cannot be written", e);
        }
    }
}

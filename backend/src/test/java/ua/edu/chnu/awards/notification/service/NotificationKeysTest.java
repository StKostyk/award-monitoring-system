package ua.edu.chnu.awards.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationType;

class NotificationKeysTest {

    private static final Instant AT = Instant.parse("2026-10-14T10:00:00Z");
    private static final int KEY_LENGTH = 64;

    private final NotificationKeys keys = new NotificationKeys();

    @Test
    void ac1_3_oneEventGivesTheSameKeyEachTime() {
        String key = keys.key(draft(7L, 5L, AT), NotificationChannel.IN_APP);

        assertThat(key).hasSize(KEY_LENGTH).matches("[0-9a-f]+")
            .isEqualTo(keys.key(draft(7L, 5L, AT), NotificationChannel.IN_APP));
    }

    @Test
    void ac1_3_recipientChannelReferenceAndTimeEachChangeTheKey() {
        String key = keys.key(draft(7L, 5L, AT), NotificationChannel.IN_APP);

        assertThat(keys.key(draft(8L, 5L, AT), NotificationChannel.IN_APP)).isNotEqualTo(key);
        assertThat(keys.key(draft(7L, 5L, AT), NotificationChannel.EMAIL)).isNotEqualTo(key);
        assertThat(keys.key(draft(7L, 6L, AT), NotificationChannel.IN_APP)).isNotEqualTo(key);
        assertThat(keys.key(draft(7L, 5L, AT.plusNanos(1000)), NotificationChannel.IN_APP)).isNotEqualTo(key);
    }

    private static NotificationDraft draft(long userId, long awardId, Instant at) {
        return new NotificationDraft(userId, NotificationType.AWARD_RETURNED, Map.of(), "AWARD", awardId, at);
    }
}

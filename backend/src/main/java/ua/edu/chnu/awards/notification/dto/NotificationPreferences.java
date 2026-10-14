package ua.edu.chnu.awards.notification.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Channels per notification type, as read and as changed.
 *
 * @param preferences one entry per type; a request without the list changes nothing
 */
public record NotificationPreferences(List<NotificationPreferenceItem> preferences) {

    /**
     * Keeps an unmodifiable copy of the entries; a missing list is empty.
     *
     * @param preferences one entry per type
     */
    public NotificationPreferences {
        preferences = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNullElse(preferences, List.of())));
    }
}

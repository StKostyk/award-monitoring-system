package ua.edu.chnu.awards.user.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The colour scheme of a person and the arrangement of their dashboards.
 *
 * @param colorScheme {@code SYSTEM}, {@code LIGHT} or {@code DARK}; null in a request means {@code SYSTEM}
 * @param layouts     the arrangement per dashboard name; a dashboard left out keeps its default
 */
public record DashboardPreferences(String colorScheme, Map<String, DashboardLayout> layouts) {

    /**
     * Keeps an unmodifiable copy of the layouts, null values included for the service to treat as the default.
     */
    public DashboardPreferences {
        layouts = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNullElse(layouts, Map.of())));
    }
}

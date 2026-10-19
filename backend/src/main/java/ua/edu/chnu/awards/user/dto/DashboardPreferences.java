package ua.edu.chnu.awards.user.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The widgets a person hid on the personal dashboard.
 *
 * @param hiddenWidgets widget names; the request may hold unknown names, which the service rejects
 */
public record DashboardPreferences(List<String> hiddenWidgets) {

    /**
     * Keeps an unmodifiable copy, null entries included for the service to reject; a missing list means none
     * hidden.
     */
    public DashboardPreferences {
        hiddenWidgets = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNullElse(hiddenWidgets, List.of())));
    }
}

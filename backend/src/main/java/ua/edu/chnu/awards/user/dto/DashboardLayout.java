package ua.edu.chnu.awards.user.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The arrangement of one dashboard.
 *
 * @param order  widget names in display order; widgets left out follow in their default order
 * @param hidden widget names not shown
 */
public record DashboardLayout(List<String> order, List<String> hidden) {

    /**
     * Keeps unmodifiable copies, null entries included for the service to reject; a missing list is empty.
     */
    public DashboardLayout {
        order = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNullElse(order, List.of())));
        hidden = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNullElse(hidden, List.of())));
    }
}

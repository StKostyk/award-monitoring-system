package ua.edu.chnu.awards.user.dto;

import static ua.edu.chnu.awards.user.dto.DashboardWidget.BY_CATEGORY;
import static ua.edu.chnu.awards.user.dto.DashboardWidget.FACT;
import static ua.edu.chnu.awards.user.dto.DashboardWidget.IN_PROGRESS;
import static ua.edu.chnu.awards.user.dto.DashboardWidget.KPI;
import static ua.edu.chnu.awards.user.dto.DashboardWidget.LEVEL_MAP;
import static ua.edu.chnu.awards.user.dto.DashboardWidget.LEVEL_STAIRS;
import static ua.edu.chnu.awards.user.dto.DashboardWidget.QUEUE;
import static ua.edu.chnu.awards.user.dto.DashboardWidget.RACE;
import static ua.edu.chnu.awards.user.dto.DashboardWidget.RANK;
import static ua.edu.chnu.awards.user.dto.DashboardWidget.RECORDS;
import static ua.edu.chnu.awards.user.dto.DashboardWidget.TODAY;
import static ua.edu.chnu.awards.user.dto.DashboardWidget.TOP;
import static ua.edu.chnu.awards.user.dto.DashboardWidget.TREND;
import static ua.edu.chnu.awards.user.dto.DashboardWidget.UNITS;
import static ua.edu.chnu.awards.user.dto.DashboardWidget.VISIBILITY;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A dashboard whose widget order and visibility a person can arrange.
 */
public enum Dashboard {
    MY_AWARDS(List.of(KPI, TREND, LEVEL_STAIRS, IN_PROGRESS, BY_CATEGORY, RANK, VISIBILITY)),
    HALL_OF_FAME(List.of(KPI, TREND, RACE, RECORDS, FACT, TOP, LEVEL_MAP)),
    COMMAND_CENTRE(List.of(TODAY, QUEUE, UNITS, TOP));

    private final List<DashboardWidget> widgets;
    private final Set<String> names;

    Dashboard(List<DashboardWidget> widgets) {
        this.widgets = widgets;
        this.names = widgets.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The widgets of the dashboard.
     *
     * @return the widgets in their default order
     */
    public List<DashboardWidget> widgets() {
        return List.copyOf(widgets);
    }

    /**
     * Whether a widget name belongs to the dashboard.
     *
     * @param name the widget name, may be null
     * @return true for a widget of this dashboard
     */
    public boolean holds(String name) {
        return name != null && names.contains(name);
    }
}

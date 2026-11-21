package ua.edu.chnu.awards.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DashboardTest {

    @Test
    void ac2_4_myAwardsHoldsTheFactsAndTheLatestAwardsInTheirDefaultPlace() {
        assertThat(Dashboard.MY_AWARDS.widgets()).containsExactly(DashboardWidget.KPI, DashboardWidget.FACT,
            DashboardWidget.TREND, DashboardWidget.LEVEL_STAIRS, DashboardWidget.IN_PROGRESS, DashboardWidget.LATEST,
            DashboardWidget.BY_CATEGORY, DashboardWidget.RANK, DashboardWidget.VISIBILITY);
    }

    @Test
    void ac2_4_theOtherDashboardsDoNotHoldTheLatestAwards() {
        assertThat(Dashboard.HALL_OF_FAME.holds(DashboardWidget.LATEST.name())).isFalse();
        assertThat(Dashboard.COMMAND_CENTRE.holds(DashboardWidget.LATEST.name())).isFalse();
        assertThat(Dashboard.MY_AWARDS.holds(DashboardWidget.QUEUE.name())).isFalse();
    }
}

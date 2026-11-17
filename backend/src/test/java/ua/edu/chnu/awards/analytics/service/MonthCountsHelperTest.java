package ua.edu.chnu.awards.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.month;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.months;

import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.analytics.dto.MonthCount;
import ua.edu.chnu.awards.analytics.dto.UnitFigures;

class MonthCountsHelperTest {

    @Test
    void ac2_1_monthsListTheLastYearAndTheYearBeforeWithZerosAfterTheCurrentMonth() {
        UnitFigures figures = months(List.of(month(2025, 1, 4), month(2025, 12, 2), month(2026, 3, 5),
            month(2026, 11, 1), month(2026, 12, 7), month(2024, 6, 9)));

        List<MonthCount> byMonth = MonthCountsHelper.byMonth(figures, 2026, YearMonth.of(2026, 11));

        assertThat(byMonth).hasSize(24);
        assertThat(byMonth.getFirst()).isEqualTo(new MonthCount(2025, 1, 4));
        assertThat(byMonth.get(11)).isEqualTo(new MonthCount(2025, 12, 2));
        assertThat(byMonth.get(12)).isEqualTo(new MonthCount(2026, 1, 0));
        assertThat(byMonth.get(14)).isEqualTo(new MonthCount(2026, 3, 5));
        assertThat(byMonth.get(22)).isEqualTo(new MonthCount(2026, 11, 1));
        assertThat(byMonth.getLast()).isEqualTo(new MonthCount(2026, 12, 0));
        assertThat(byMonth).extracting(MonthCount::count).doesNotContain(9L);
    }

    @Test
    void ac2_1_aPastLastYearKeepsEveryMonth() {
        UnitFigures figures = months(List.of(month(2024, 12, 3), month(2023, 1, 1)));

        List<MonthCount> byMonth = MonthCountsHelper.byMonth(figures, 2024, YearMonth.of(2026, 11));

        assertThat(byMonth).extracting(MonthCount::year).containsOnly(2023, 2024);
        assertThat(byMonth.getFirst()).isEqualTo(new MonthCount(2023, 1, 1));
        assertThat(byMonth.getLast()).isEqualTo(new MonthCount(2024, 12, 3));
    }
}

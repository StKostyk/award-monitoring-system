package ua.edu.chnu.awards.analytics.service;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import ua.edu.chnu.awards.analytics.dto.MonthCount;
import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.award.dto.MonthAwardCount;

/**
 * The monthly series of the cached aggregates: the last year of the period and the year before, month by month.
 */
final class MonthCountsHelper {

    private static final int MONTHS_SHOWN = 24;

    private MonthCountsHelper() {
    }

    /**
     * Every month of the last year and the year before; months after the current one count 0.
     *
     * @param figures  the aggregates
     * @param lastYear the last year of the period
     * @param current  the current Kyiv month
     * @return 24 months in order
     */
    static List<MonthCount> byMonth(UnitFigures figures, int lastYear, YearMonth current) {
        Map<YearMonth, Long> counts = figures.months().stream().collect(Collectors.toMap(
            month -> YearMonth.of(month.year(), month.month()), MonthAwardCount::count, Long::sum));
        return IntStream.range(0, MONTHS_SHOWN).mapToObj(YearMonth.of(lastYear - 1, 1)::plusMonths)
            .map(month -> new MonthCount(month.getYear(), month.getMonthValue(),
                month.isAfter(current) ? 0 : counts.getOrDefault(month, 0L)))
            .toList();
    }
}

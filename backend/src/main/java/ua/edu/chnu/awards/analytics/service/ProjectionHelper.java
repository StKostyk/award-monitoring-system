package ua.edu.chnu.awards.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import ua.edu.chnu.awards.analytics.dto.TrendProjection;
import ua.edu.chnu.awards.analytics.dto.UnitYearCount;
import ua.edu.chnu.awards.award.dto.YearRange;

/**
 * A least-squares line over the yearly totals of the complete years of a period, extended by one year.
 */
final class ProjectionHelper {

    /** The fewest complete years a line is drawn through. */
    static final int MIN_COMPLETE_YEARS = 3;

    private ProjectionHelper() {
    }

    /**
     * The projection of a period.
     *
     * @param byYear      every year of the period in order
     * @param currentYear the current calendar year, whose count is partial
     * @return the line over the years before the current one, or {@link TrendProjection#TOO_FEW_YEARS}
     */
    static TrendProjection of(List<UnitYearCount> byYear, int currentYear) {
        List<UnitYearCount> complete = byYear.stream().filter(year -> year.year() < currentYear).toList();
        if (complete.size() < MIN_COMPLETE_YEARS) {
            return TrendProjection.TOO_FEW_YEARS;
        }
        double meanYear = complete.stream().mapToDouble(UnitYearCount::year).average().orElseThrow();
        double meanTotal = complete.stream().mapToDouble(UnitYearCount::total).average().orElseThrow();
        double covariance = 0;
        double variance = 0;
        for (UnitYearCount year : complete) {
            covariance += (year.year() - meanYear) * (year.total() - meanTotal);
            variance += (year.year() - meanYear) * (year.year() - meanYear);
        }
        double slope = covariance / variance;
        int first = complete.getFirst().year();
        int last = complete.getLast().year();
        long estimate = Math.max(0, Math.round(meanTotal + slope * (last + 1 - meanYear)));
        return new TrendProjection(TrendProjection.Status.PROJECTED, new YearRange(first, last),
            BigDecimal.valueOf(slope).setScale(1, RoundingMode.HALF_UP), last + 1, estimate);
    }
}

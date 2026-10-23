package ua.edu.chnu.awards.analytics.dto;

import java.time.Instant;
import java.util.List;

import ua.edu.chnu.awards.award.dto.YearRange;

/**
 * The approved awards submitted in a faculty or department over a period, compared with the previous period.
 *
 * @param unit           the unit
 * @param period         the calendar years
 * @param previousPeriod the years of the same length before the period
 * @param partialYear    whether the period ends in the current year
 * @param generatedAt    when the aggregates were computed
 * @param totals         totals of the counted awards
 * @param byYear         every year of the period in order
 * @param units          the child departments and the faculty level; empty for a department
 * @param byCategory     highest count first, then by English name
 * @param byLevel        in level order
 * @param bySource       counted awards by awarding organisation
 * @param topPerformers  the people who agreed to be named
 * @param highlights     departments that stand out; empty for a department
 */
public record UnitAnalytics(AnalysableUnit unit, YearRange period, YearRange previousPeriod, boolean partialYear,
                            Instant generatedAt, UnitTotals totals, List<UnitYearCount> byYear,
                            List<UnitComparisonRow> units, List<CategoryComparison> byCategory,
                            List<LevelComparison> byLevel, SourceSummary bySource, TopPerformers topPerformers,
                            List<UnitHighlight> highlights) {

    /**
     * Copies the lists.
     *
     * @param unit           the unit
     * @param period         the calendar years
     * @param previousPeriod the previous period
     * @param partialYear    whether the period ends in the current year
     * @param generatedAt    when the aggregates were computed
     * @param totals         totals of the counted awards
     * @param byYear         every year of the period
     * @param units          the comparison rows
     * @param byCategory     counts per category
     * @param byLevel        counts per level
     * @param bySource       counts per awarding organisation
     * @param topPerformers  the people who agreed to be named
     * @param highlights     departments that stand out
     */
    public UnitAnalytics {
        byYear = List.copyOf(byYear);
        units = List.copyOf(units);
        byCategory = List.copyOf(byCategory);
        byLevel = List.copyOf(byLevel);
        highlights = List.copyOf(highlights);
    }
}

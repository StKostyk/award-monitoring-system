package ua.edu.chnu.awards.analytics.dto;

import java.time.Instant;
import java.util.List;

import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.organization.dto.OrganizationRef;

/**
 * The approved awards submitted in the whole university over a period, compared with the previous period; nobody
 * is named.
 *
 * @param unit           the university
 * @param period         the calendar years
 * @param previousPeriod the years of the same length before the period
 * @param partialYear    whether the period ends in the current year
 * @param generatedAt    when the aggregates were computed
 * @param totals         totals of the counted awards
 * @param byYear         every year of the period in order
 * @param units          the faculties and colleges by Ukrainian name, then the university level
 * @param byCategory     highest count first, then by English name
 * @param byLevel        in level order
 * @param bySource       counted awards by awarding organisation
 * @param trends         the categories and awarding organisations that rose or fell the most
 * @param projection     the line over the complete years
 * @param highlights     faculties and colleges that stand out
 */
public record UniversityAnalytics(OrganizationRef unit, YearRange period, YearRange previousPeriod,
                                  boolean partialYear, Instant generatedAt, UnitTotals totals,
                                  List<UnitYearCount> byYear, List<UnitComparisonRow> units,
                                  List<CategoryComparison> byCategory, List<LevelComparison> byLevel,
                                  SourceSummary bySource, AnalyticsTrends trends, TrendProjection projection,
                                  List<UnitHighlight> highlights) {

    /**
     * Copies the lists.
     *
     * @param unit           the university
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
     * @param trends         rising and falling entries
     * @param projection     the line over the complete years
     * @param highlights     units that stand out
     */
    public UniversityAnalytics {
        byYear = List.copyOf(byYear);
        units = List.copyOf(units);
        byCategory = List.copyOf(byCategory);
        byLevel = List.copyOf(byLevel);
        highlights = List.copyOf(highlights);
    }
}

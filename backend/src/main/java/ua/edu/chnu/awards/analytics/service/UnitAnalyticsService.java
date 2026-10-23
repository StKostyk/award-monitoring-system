package ua.edu.chnu.awards.analytics.service;

import java.time.Clock;
import java.time.Year;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.analytics.dto.AnalysableUnit;
import ua.edu.chnu.awards.analytics.dto.UnitAnalytics;
import ua.edu.chnu.awards.analytics.dto.UnitComparisonRow;
import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.analytics.dto.UnitTotals;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.organization.entity.OrganizationType;

import lombok.RequiredArgsConstructor;

/**
 * The analytics of a faculty or department: approved awards submitted in its subtree over a period of calendar
 * years, compared with the previous period of the same length.
 */
@Service
@RequiredArgsConstructor
public class UnitAnalyticsService {

    private static final int DEFAULT_YEARS = 5;

    private final UnitScope scope;
    private final UnitAggregates aggregates;
    private final UnitComparison comparison;
    private final HighlightRules highlights;
    private final TopPerformersRanking ranking;
    private final Clock clock;

    /**
     * The caller's analysable units.
     *
     * @return each faculty followed by its departments
     */
    @Transactional(readOnly = true)
    public List<AnalysableUnit> units() {
        return scope.list();
    }

    /**
     * The analytics of one unit; the counts may come from the cache, the names and the consent never do.
     *
     * @param unitId the unit
     * @param from   first calendar year, by default four years before the last
     * @param to     last calendar year, by default the current year
     * @return the analytics
     * @throws UnitNotFoundException when the unit is not analysable for the caller
     * @throws ApiProblemException   400 {@code invalid-parameter} naming {@code from} or {@code to}
     */
    @Transactional(readOnly = true)
    public UnitAnalytics of(long unitId, Integer from, Integer to) {
        AnalysableUnit unit = scope.require(unitId);
        int currentYear = Year.now(clock).getValue();
        YearRange period = PeriodHelper.resolve(from, to, currentYear, last -> last - DEFAULT_YEARS + 1);
        YearRange previous = new YearRange(2 * period.from() - period.to() - 1, period.from() - 1);
        UnitFigures figures = aggregates.of(unitId, previous, period);
        UnitTotals totals = UnitFiguresHelper.totals(figures, period, previous);
        boolean faculty = unit.type() == OrganizationType.FACULTY;
        List<UnitComparisonRow> rows = faculty ? comparison.rows(unitId, figures, period, previous) : List.of();
        return new UnitAnalytics(unit, period, previous, period.to() == currentYear, figures.generatedAt(), totals,
            UnitFiguresHelper.byYear(figures, period), rows, UnitFiguresHelper.byCategory(figures, period, previous),
            UnitFiguresHelper.byLevel(figures, period, previous), UnitFiguresHelper.bySource(figures),
            ranking.of(figures, totals.members()), faculty ? highlights.of(rows, totals) : List.of());
    }
}

package ua.edu.chnu.awards.analytics.service;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.analytics.config.ReminderProperties;
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

    private final UnitScope scope;
    private final UnitAggregates aggregates;
    private final UnitComparison comparison;
    private final HighlightRules highlights;
    private final TopPerformersRanking ranking;
    private final Clock clock;
    private final UnitReminderLog reminders;
    private final ReminderProperties reminderProperties;

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
     * The analytics of one unit as the export reads it; the counts may come from the cache, the names and the
     * consent never do.
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
        return analytics(scope.require(unitId), from, to);
    }

    /**
     * The analytics of one unit on screen: an analysable unit, or the faculty of the caller's faculty secretary
     * role, with the last reminder of each department.
     *
     * @param unitId the unit
     * @param from   first calendar year, by default four years before the last
     * @param to     last calendar year, by default the current year
     * @return the analytics
     * @throws UnitNotFoundException when the unit is not viewable for the caller
     * @throws ApiProblemException   400 {@code invalid-parameter} naming {@code from} or {@code to}
     */
    @Transactional(readOnly = true)
    public UnitAnalytics overview(long unitId, Integer from, Integer to) {
        UnitAnalytics analytics = analytics(scope.requireOverview(unitId), from, to);
        List<Long> departments = analytics.units().stream().filter(row -> !row.facultyLevel())
            .map(row -> row.unit().id()).toList();
        Map<Long, Instant> sent = reminders.lastSent(departments,
            Instant.now(clock).minus(reminderProperties.unitInterval()));
        if (sent.isEmpty()) {
            return analytics;
        }
        List<UnitComparisonRow> rows = analytics.units().stream()
            .map(row -> row.facultyLevel() ? row : row.withLastReminderAt(sent.get(row.unit().id()))).toList();
        return new UnitAnalytics(analytics.unit(), analytics.period(), analytics.previousPeriod(),
            analytics.partialYear(), analytics.generatedAt(), analytics.totals(), analytics.byYear(),
            analytics.byMonth(), rows, analytics.byCategory(), analytics.byLevel(), analytics.bySource(),
            analytics.topPerformers(), analytics.highlights());
    }

    private UnitAnalytics analytics(AnalysableUnit unit, Integer from, Integer to) {
        long unitId = unit.id();
        YearMonth now = YearMonth.now(clock);
        int currentYear = now.getYear();
        YearRange period = PeriodHelper.resolve(from, to, currentYear);
        YearRange previous = period.previous();
        UnitFigures figures = aggregates.of(unitId, previous, period);
        UnitTotals totals = UnitFiguresHelper.totals(figures, period, previous);
        boolean faculty = unit.type() == OrganizationType.FACULTY;
        List<UnitComparisonRow> rows = faculty ? comparison.rows(unitId, figures, period, previous) : List.of();
        return new UnitAnalytics(unit, period, previous, period.to() == currentYear, figures.generatedAt(), totals,
            UnitFiguresHelper.byYear(figures, period), MonthCountsHelper.byMonth(figures, period.to(), now), rows,
            UnitFiguresHelper.byCategory(figures, period, previous),
            UnitFiguresHelper.byLevel(figures, period, previous), UnitFiguresHelper.bySource(figures),
            ranking.of(figures, totals.members()), faculty ? highlights.of(rows, totals) : List.of());
    }
}

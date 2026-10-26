package ua.edu.chnu.awards.analytics.service;

import java.time.Clock;
import java.time.Year;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.analytics.dto.UnitComparisonRow;
import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.analytics.dto.UnitTotals;
import ua.edu.chnu.awards.analytics.dto.UnitYearCount;
import ua.edu.chnu.awards.analytics.dto.UniversityAnalytics;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.organization.OrganizationTree;
import ua.edu.chnu.awards.organization.dto.OrganizationRef;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;

import lombok.RequiredArgsConstructor;

/**
 * The analytics of the whole university: approved awards submitted anywhere in it over a period of calendar years,
 * compared with the previous period of the same length, per faculty and college, with trends and a projection.
 */
@Service
@RequiredArgsConstructor
public class UniversityAnalyticsService {

    private static final Set<OrganizationType> COMPARED = EnumSet.of(OrganizationType.FACULTY,
        OrganizationType.COLLEGE);

    private final OrganizationTree tree;
    private final OrganizationLookup organizations;
    private final UnitAggregates aggregates;
    private final UnitComparison comparison;
    private final HighlightRules highlights;
    private final TrendRules trends;
    private final Clock clock;

    /**
     * The analytics of the university; the counts may come from the cache, the names never do.
     *
     * @param from first calendar year, by default four years before the last
     * @param to   last calendar year, by default the current year
     * @return the analytics
     * @throws ApiProblemException 400 {@code invalid-parameter} naming {@code from} or {@code to}
     */
    @Transactional(readOnly = true)
    public UniversityAnalytics of(Integer from, Integer to) {
        int currentYear = Year.now(clock).getValue();
        YearRange period = PeriodHelper.resolve(from, to, currentYear);
        YearRange previous = period.previous();
        Organization university = university();
        UnitFigures figures = aggregates.of(university.getId(), previous, period);
        UnitTotals totals = UnitFiguresHelper.totals(figures, period, previous);
        List<UnitComparisonRow> rows = comparison.rows(university.getId(), COMPARED, figures, period, previous);
        List<UnitYearCount> byYear = UnitFiguresHelper.byYear(figures, period);
        return new UniversityAnalytics(new OrganizationRef(university.getId(), university.getName(),
            university.getNameUk(), university.getCode(), university.getOrgType()), period, previous,
            period.to() == currentYear, figures.generatedAt(), totals, byYear, rows,
            UnitFiguresHelper.byCategory(figures, period, previous), UnitFiguresHelper.byLevel(figures, period,
                previous), UnitFiguresHelper.bySource(figures), trends.of(figures, period, previous),
            ProjectionHelper.of(byYear, currentYear), highlights.ofUniversity(rows, totals));
    }

    private Organization university() {
        return tree.university().flatMap(node -> organizations.findById(node.id()))
            .orElseThrow(() -> new IllegalStateException("The organisation tree has no university"));
    }
}

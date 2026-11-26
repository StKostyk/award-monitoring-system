package ua.edu.chnu.awards.analytics.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.analytics.config.AnalyticsProperties;
import ua.edu.chnu.awards.analytics.dto.UnitComparisonRow;
import ua.edu.chnu.awards.analytics.dto.UnitHighlight;
import ua.edu.chnu.awards.analytics.dto.UnitTotals;

import lombok.RequiredArgsConstructor;

/**
 * The fixed rules naming the departments of a faculty, or the faculties and colleges of the university, that stand
 * out; a unit without counted awards is named for that alone.
 */
@Component
@RequiredArgsConstructor
public class HighlightRules {

    /** The fewest awards in the previous period before a decline or a growth is named. */
    static final long TREND_MINIMUM = 4;
    /**
     * Growth is named at 3 halves of the previous count or more, a decline at 1 half or less, above average at 3
     * halves of the university's awards per member or more.
     */
    static final long GROWTH_HALVES = 3;

    private final AnalyticsProperties properties;

    /**
     * The highlights of the department rows that have members, gaps first, in row order within a rule.
     *
     * @param rows   the comparison rows of the faculty
     * @param totals the faculty totals
     * @return the highlights
     */
    public List<UnitHighlight> of(List<UnitComparisonRow> rows, UnitTotals totals) {
        return collect(rows, totals, false);
    }

    /**
     * The highlights of the faculty and college rows that have members, with the university as the average and
     * {@link UnitHighlight.Type#ABOVE_AVERAGE} added; gaps first, in row order within a rule.
     *
     * @param rows   the comparison rows of the university
     * @param totals the university totals
     * @return the highlights
     */
    public List<UnitHighlight> ofUniversity(List<UnitComparisonRow> rows, UnitTotals totals) {
        return collect(rows, totals, true);
    }

    private List<UnitHighlight> collect(List<UnitComparisonRow> rows, UnitTotals totals, boolean university) {
        List<UnitHighlight> highlights = new ArrayList<>();
        for (UnitComparisonRow row : rows) {
            if (!row.facultyLevel() && row.members() > 0) {
                highlights.addAll(rowHighlights(row, totals, university));
            }
        }
        highlights.sort(Comparator.comparing(UnitHighlight::type));
        return highlights;
    }

    private List<UnitHighlight> rowHighlights(UnitComparisonRow row, UnitTotals totals, boolean university) {
        if (row.approved().current() == 0) {
            return List.of(highlight(UnitHighlight.Type.NO_AWARDS, row));
        }
        List<UnitHighlight> result = new ArrayList<>(trends(row));
        if (row.members() >= properties.minGroupSize() && totals.members() > 0) {
            long rate = row.approved().current() * totals.members() * 2;
            long average = totals.approved().current() * row.members();
            if (rate < average) {
                result.add(highlight(UnitHighlight.Type.BELOW_AVERAGE, row));
            }
            if (university && rate >= average * GROWTH_HALVES) {
                result.add(highlight(UnitHighlight.Type.ABOVE_AVERAGE, row));
            }
        }
        return result;
    }

    private static List<UnitHighlight> trends(UnitComparisonRow row) {
        long current = row.approved().current();
        long previous = row.approved().previous();
        if (previous < TREND_MINIMUM) {
            return List.of();
        }
        if (current * 2 <= previous) {
            return List.of(highlight(UnitHighlight.Type.DECLINE, row));
        }
        return current * 2 >= previous * GROWTH_HALVES ? List.of(highlight(UnitHighlight.Type.GROWTH, row))
            : List.of();
    }

    private static UnitHighlight highlight(UnitHighlight.Type type, UnitComparisonRow row) {
        return new UnitHighlight(type, row.unit(), row.approved().current(), row.approved().previous());
    }
}

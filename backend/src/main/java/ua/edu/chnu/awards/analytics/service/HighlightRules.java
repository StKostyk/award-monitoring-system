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
 * The fixed rules naming the departments of a faculty that stand out; a department without counted awards is named
 * for that alone.
 */
@Component
@RequiredArgsConstructor
public class HighlightRules {

    /** The fewest awards in the previous period before a decline or a growth is named. */
    static final long TREND_MINIMUM = 4;
    /** Growth is named at 3 halves of the previous count or more, a decline at 1 half or less. */
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
        List<UnitHighlight> highlights = new ArrayList<>();
        for (UnitComparisonRow row : rows) {
            if (!row.facultyLevel() && row.members() > 0) {
                highlights.addAll(of(row, totals));
            }
        }
        highlights.sort(Comparator.comparing(UnitHighlight::type));
        return highlights;
    }

    private List<UnitHighlight> of(UnitComparisonRow row, UnitTotals totals) {
        long current = row.approved().current();
        long previous = row.approved().previous();
        if (current == 0) {
            return List.of(highlight(UnitHighlight.Type.NO_AWARDS, row));
        }
        List<UnitHighlight> result = new ArrayList<>();
        if (previous >= TREND_MINIMUM && current * 2 <= previous) {
            result.add(highlight(UnitHighlight.Type.DECLINE, row));
        }
        if (previous >= TREND_MINIMUM && current * 2 >= previous * GROWTH_HALVES) {
            result.add(highlight(UnitHighlight.Type.GROWTH, row));
        }
        if (row.members() >= properties.minGroupSize() && totals.members() > 0
            && current * totals.members() * 2 < totals.approved().current() * row.members()) {
            result.add(highlight(UnitHighlight.Type.BELOW_AVERAGE, row));
        }
        return result;
    }

    private static UnitHighlight highlight(UnitHighlight.Type type, UnitComparisonRow row) {
        return new UnitHighlight(type, row.unit(), row.approved().current(), row.approved().previous());
    }
}

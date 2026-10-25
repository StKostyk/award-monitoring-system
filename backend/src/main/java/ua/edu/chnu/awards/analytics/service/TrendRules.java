package ua.edu.chnu.awards.analytics.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.analytics.dto.AnalyticsTrends;
import ua.edu.chnu.awards.analytics.dto.CategoryTrend;
import ua.edu.chnu.awards.analytics.dto.Figure;
import ua.edu.chnu.awards.analytics.dto.SourceTrend;
import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.award.dto.SourceSpelling;
import ua.edu.chnu.awards.award.dto.YearRange;

/**
 * The categories and awarding organisations that rose or fell the most between the previous period and the period.
 */
@Component
public class TrendRules {

    /** The most entries per direction. */
    static final int LIMIT = 5;
    /** The fewest counted awards over both periods before an entry is listed. */
    static final long MINIMUM = 3;

    /**
     * The trends of a unit.
     *
     * @param figures  the aggregates
     * @param period   the calendar years
     * @param previous the previous period
     * @return up to 5 rising and 5 falling categories and awarding organisations
     */
    public AnalyticsTrends of(UnitFigures figures, YearRange period, YearRange previous) {
        List<CategoryTrend> categories = UnitFiguresHelper.byCategory(figures, period, previous).stream()
            .map(comparison -> CategoryTrend.of(comparison.category(),
                Figure.of(comparison.count(), comparison.previous())))
            .toList();
        return new AnalyticsTrends(
            directions(categories, CategoryTrend::current, CategoryTrend::previous, CategoryTrend::delta,
                trend -> trend.category().name()),
            directions(sources(figures), SourceTrend::current, SourceTrend::previous, SourceTrend::delta,
                SourceTrend::name));
    }

    private static List<SourceTrend> sources(UnitFigures figures) {
        Map<String, long[]> counts = new HashMap<>();
        Map<String, Map<String, Long>> spellings = new HashMap<>();
        add(figures.sources(), 0, counts, spellings);
        add(figures.previousSources(), 1, counts, spellings);
        return counts.entrySet().stream()
            .map(entry -> SourceTrend.of(spelling(spellings.get(entry.getKey())),
                Figure.of(entry.getValue()[0], entry.getValue()[1])))
            .toList();
    }

    private static void add(List<SourceSpelling> sources, int slot, Map<String, long[]> counts,
                            Map<String, Map<String, Long>> spellings) {
        for (SourceSpelling source : sources) {
            counts.computeIfAbsent(source.key(), key -> new long[2])[slot] += source.count();
            spellings.computeIfAbsent(source.key(), key -> new HashMap<>())
                .merge(source.spelling(), source.count(), Long::sum);
        }
    }

    private static String spelling(Map<String, Long> spellings) {
        return spellings.entrySet().stream()
            .min(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .orElseThrow().getKey();
    }

    private static <T> AnalyticsTrends.Directions<T> directions(List<T> entries, Function<T, Long> current,
                                                                 Function<T, Long> previous, Function<T, Long> delta,
                                                                 Function<T, String> name) {
        List<T> listed = entries.stream()
            .filter(entry -> current.apply(entry) + previous.apply(entry) >= MINIMUM)
            .toList();
        return new AnalyticsTrends.Directions<>(
            top(listed.stream().filter(entry -> delta.apply(entry) > 0),
                Comparator.comparing(delta).reversed().thenComparing(name)),
            top(listed.stream().filter(entry -> delta.apply(entry) < 0),
                Comparator.comparing(delta).thenComparing(name)));
    }

    private static <T> List<T> top(Stream<T> entries, Comparator<T> order) {
        return entries.sorted(order).limit(LIMIT).toList();
    }
}

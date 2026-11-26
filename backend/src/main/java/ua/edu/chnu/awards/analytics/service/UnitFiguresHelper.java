package ua.edu.chnu.awards.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import ua.edu.chnu.awards.analytics.dto.CategoryComparison;
import ua.edu.chnu.awards.analytics.dto.Figure;
import ua.edu.chnu.awards.analytics.dto.LevelComparison;
import ua.edu.chnu.awards.analytics.dto.SourceSummary;
import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.analytics.dto.UnitTotals;
import ua.edu.chnu.awards.analytics.dto.UnitYearCount;
import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.MemberCount;
import ua.edu.chnu.awards.award.dto.OwnerAwardCount;
import ua.edu.chnu.awards.award.dto.SourceSpelling;
import ua.edu.chnu.awards.award.dto.UnitAwardCount;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * Folds the cached aggregates of a unit into the figures of the unit analytics, for the whole subtree or a part of
 * it.
 */
final class UnitFiguresHelper {

    /** Every organisation of the subtree. */
    static final LongPredicate EVERY = id -> true;

    private static final int SOURCE_LIMIT = 15;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private UnitFiguresHelper() {
    }

    /**
     * Totals of the subtree.
     *
     * @param figures  the aggregates
     * @param period   the calendar years
     * @param previous the previous period
     * @return the totals
     */
    static UnitTotals totals(UnitFigures figures, YearRange period, YearRange previous) {
        long approved = approved(figures, EVERY, period);
        long members = members(figures, EVERY);
        List<UnitAwardCount> current = within(figures, EVERY, period);
        return new UnitTotals(Figure.of(approved, approved(figures, EVERY, previous)),
            Figure.of(awardedPeople(figures, EVERY, true), awardedPeople(figures, EVERY, false)),
            current.stream().filter(count -> !count.unitAward()).mapToLong(UnitAwardCount::count).sum(),
            current.stream().filter(UnitAwardCount::unitAward).mapToLong(UnitAwardCount::count).sum(),
            verified(figures, EVERY, period), members, perHundred(approved, members));
    }

    /**
     * Counted awards of a part of the subtree in a period.
     *
     * @param figures       the aggregates
     * @param organizations the organisations of the part
     * @param range         the calendar years
     * @return the count
     */
    static long approved(UnitFigures figures, LongPredicate organizations, YearRange range) {
        return within(figures, organizations, range).stream().mapToLong(UnitAwardCount::count).sum();
    }

    /**
     * Counted awards with a verification badge of a part of the subtree in a period.
     *
     * @param figures       the aggregates
     * @param organizations the organisations of the part
     * @param range         the calendar years
     * @return the count
     */
    static long verified(UnitFigures figures, LongPredicate organizations, YearRange range) {
        return within(figures, organizations, range).stream().mapToLong(UnitAwardCount::verified).sum();
    }

    /**
     * Distinct owners of counted personal awards of a part of the subtree.
     *
     * @param figures       the aggregates
     * @param organizations the organisations of the part
     * @param current       true for the period, false for the previous period
     * @return the count
     */
    static long awardedPeople(UnitFigures figures, LongPredicate organizations, boolean current) {
        return figures.owners().stream()
            .filter(owner -> owner.current() == current && organizations.test(owner.organizationId()))
            .mapToLong(OwnerAwardCount::userId).distinct().count();
    }

    /**
     * Active accounts of a part of the subtree.
     *
     * @param figures       the aggregates
     * @param organizations the organisations of the part
     * @return the count
     */
    static long members(UnitFigures figures, LongPredicate organizations) {
        return figures.members().stream().filter(count -> organizations.test(count.organizationId()))
            .mapToLong(MemberCount::members).sum();
    }

    /**
     * Awards per 100 members.
     *
     * @param awards  counted awards
     * @param members active accounts
     * @return one decimal, null without members
     */
    static BigDecimal perHundred(long awards, long members) {
        return members == 0 ? null
            : BigDecimal.valueOf(awards).multiply(HUNDRED).divide(BigDecimal.valueOf(members), 1,
                RoundingMode.HALF_UP);
    }

    /**
     * Counted awards per year of the period, zeros included.
     *
     * @param figures       the aggregates
     * @param organizations the organisations of the part
     * @param period        the calendar years
     * @return one count per year, in order
     */
    static List<Long> yearSeries(UnitFigures figures, LongPredicate organizations, YearRange period) {
        Map<Integer, Long> years = within(figures, organizations, period).stream()
            .collect(Collectors.groupingBy(UnitAwardCount::year, Collectors.summingLong(UnitAwardCount::count)));
        return IntStream.rangeClosed(period.from(), period.to()).mapToObj(year -> years.getOrDefault(year, 0L))
            .toList();
    }

    /**
     * Every year of the period with its personal and unit awards.
     *
     * @param figures the aggregates
     * @param period  the calendar years
     * @return the series
     */
    static List<UnitYearCount> byYear(UnitFigures figures, YearRange period) {
        List<UnitAwardCount> counts = within(figures, EVERY, period);
        return IntStream.rangeClosed(period.from(), period.to()).mapToObj(year -> {
            long personal = counts.stream().filter(count -> count.year() == year && !count.unitAward())
                .mapToLong(UnitAwardCount::count).sum();
            long unitAwards = counts.stream().filter(count -> count.year() == year && count.unitAward())
                .mapToLong(UnitAwardCount::count).sum();
            return new UnitYearCount(year, personal + unitAwards, personal, unitAwards);
        }).toList();
    }

    /**
     * Categories with counted awards in either period, highest current count first, then by English name.
     *
     * @param figures  the aggregates
     * @param period   the calendar years
     * @param previous the previous period
     * @return the series
     */
    static List<CategoryComparison> byCategory(UnitFigures figures, YearRange period, YearRange previous) {
        Map<CategoryRef, long[]> categories = new LinkedHashMap<>();
        figures.counts().forEach(count -> {
            int slot = slot(count.year(), period, previous);
            if (slot >= 0) {
                categories.computeIfAbsent(count.category(), category -> new long[2])[slot] += count.count();
            }
        });
        return categories.entrySet().stream()
            .map(entry -> new CategoryComparison(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
            .sorted(Comparator.comparingLong(CategoryComparison::count).reversed()
                .thenComparing(comparison -> comparison.category().name()))
            .toList();
    }

    /**
     * Levels with counted awards in either period, in level order.
     *
     * @param figures  the aggregates
     * @param period   the calendar years
     * @param previous the previous period
     * @return the series
     */
    static List<LevelComparison> byLevel(UnitFigures figures, YearRange period, YearRange previous) {
        Map<RecognitionLevel, long[]> levels = new EnumMap<>(RecognitionLevel.class);
        figures.counts().forEach(count -> {
            int slot = slot(count.year(), period, previous);
            if (slot >= 0) {
                levels.computeIfAbsent(count.category().level(), level -> new long[2])[slot] += count.count();
            }
        });
        return levels.entrySet().stream()
            .map(entry -> new LevelComparison(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
            .toList();
    }

    /**
     * Awarding organisations grouped by their key under the most frequent spelling, the largest first.
     *
     * @param figures the aggregates
     * @return the largest sources and the count of the rest
     */
    static SourceSummary bySource(UnitFigures figures) {
        Map<String, List<SourceSpelling>> groups = figures.sources().stream()
            .collect(Collectors.groupingBy(SourceSpelling::key));
        List<SourceSummary.Source> sources = groups.values().stream()
            .map(spellings -> new SourceSummary.Source(spellings.stream()
                .sorted(Comparator.comparingLong(SourceSpelling::count).reversed()
                    .thenComparing(SourceSpelling::spelling))
                .findFirst().orElseThrow().spelling(),
                spellings.stream().mapToLong(SourceSpelling::count).sum()))
            .sorted(Comparator.comparingLong(SourceSummary.Source::count).reversed()
                .thenComparing(SourceSummary.Source::name))
            .toList();
        long other = sources.stream().skip(SOURCE_LIMIT).mapToLong(SourceSummary.Source::count).sum();
        return new SourceSummary(sources.stream().limit(SOURCE_LIMIT).toList(), other);
    }

    /**
     * Counted personal awards of the period per owner, with the highest level.
     *
     * @param figures the aggregates
     * @return per owner id
     */
    static Map<Long, OwnerAwardCount> currentOwners(UnitFigures figures) {
        Map<Long, OwnerAwardCount> owners = new HashMap<>();
        figures.owners().stream().filter(OwnerAwardCount::current).forEach(owner -> owners.merge(owner.userId(),
            owner, (left, right) -> new OwnerAwardCount(left.userId(), left.organizationId(), true,
                left.count() + right.count(), left.highestLevel().compareTo(right.highestLevel()) >= 0
                    ? left.highestLevel() : right.highestLevel())));
        return owners;
    }

    /**
     * Whether an organisation is one of a set.
     *
     * @param organizations the set
     * @return the test
     */
    static LongPredicate in(Set<Long> organizations) {
        return organizations::contains;
    }

    private static List<UnitAwardCount> within(UnitFigures figures, LongPredicate organizations, YearRange range) {
        return figures.counts().stream()
            .filter(count -> count.year() >= range.from() && count.year() <= range.to()
                && organizations.test(count.organizationId()))
            .toList();
    }

    private static int slot(int year, YearRange period, YearRange previous) {
        if (year >= period.from() && year <= period.to()) {
            return 0;
        }
        return year >= previous.from() && year <= previous.to() ? 1 : -1;
    }
}

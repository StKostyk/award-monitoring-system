package ua.edu.chnu.awards.analytics.service;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import ua.edu.chnu.awards.analytics.dto.CategoryCount;
import ua.edu.chnu.awards.analytics.dto.LevelCount;
import ua.edu.chnu.awards.analytics.dto.YearCount;
import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.CategoryYearCount;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * Folds the counts per year and category into the series of the personal analytics.
 */
final class AwardCountsHelper {

    private AwardCountsHelper() {
    }

    /**
     * Every year of the period in order, zeros included.
     *
     * @param period the calendar years
     * @param counts counts per year and category
     * @return the series
     */
    static List<YearCount> byYear(YearRange period, List<CategoryYearCount> counts) {
        return IntStream.rangeClosed(period.from(), period.to()).mapToObj(year -> {
            Map<RecognitionLevel, Long> levels = new EnumMap<>(RecognitionLevel.class);
            counts.stream().filter(count -> count.year() == year)
                .forEach(count -> levels.merge(count.category().level(), count.count(), Long::sum));
            return new YearCount(year, levels.values().stream().mapToLong(Long::longValue).sum(), levels);
        }).toList();
    }

    /**
     * Counts per category, highest first, then by English name.
     *
     * @param counts counts per year and category
     * @return the series
     */
    static List<CategoryCount> byCategory(List<CategoryYearCount> counts) {
        Map<CategoryRef, Long> categories = new LinkedHashMap<>();
        counts.forEach(count -> categories.merge(count.category(), count.count(), Long::sum));
        return categories.entrySet().stream().map(entry -> new CategoryCount(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingLong(CategoryCount::count).reversed()
                .thenComparing(count -> count.category().name()))
            .toList();
    }

    /**
     * Non-zero counts per level, in level order.
     *
     * @param counts counts per year and category
     * @return the series
     */
    static List<LevelCount> byLevel(List<CategoryYearCount> counts) {
        Map<RecognitionLevel, Long> levels = new EnumMap<>(RecognitionLevel.class);
        counts.forEach(count -> levels.merge(count.category().level(), count.count(), Long::sum));
        return levels.entrySet().stream().map(entry -> new LevelCount(entry.getKey(), entry.getValue())).toList();
    }
}

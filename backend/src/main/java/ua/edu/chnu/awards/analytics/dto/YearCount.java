package ua.edu.chnu.awards.analytics.dto;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * Counted awards of one calendar year.
 *
 * @param year    the year
 * @param total   number of awards
 * @param byLevel non-zero counts per level, in level order
 */
public record YearCount(int year, long total, Map<RecognitionLevel, Long> byLevel) {

    /**
     * Copies the counts in level order.
     *
     * @param year    the year
     * @param total   number of awards
     * @param byLevel non-zero counts per level
     */
    public YearCount {
        Map<RecognitionLevel, Long> ordered = new EnumMap<>(RecognitionLevel.class);
        ordered.putAll(byLevel);
        byLevel = Collections.unmodifiableMap(ordered);
    }
}

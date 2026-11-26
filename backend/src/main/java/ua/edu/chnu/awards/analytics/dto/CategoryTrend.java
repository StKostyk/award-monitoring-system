package ua.edu.chnu.awards.analytics.dto;

import java.math.BigDecimal;

import ua.edu.chnu.awards.award.dto.CategoryRef;

/**
 * A category whose count changed between the previous period and the period.
 *
 * @param category     the category
 * @param current      counted awards in the period
 * @param previous     counted awards in the previous period
 * @param delta        current minus previous
 * @param deltaPercent the delta in percent of the previous count, one decimal; null when the previous count is 0
 */
public record CategoryTrend(CategoryRef category, long current, long previous, long delta, BigDecimal deltaPercent) {

    /**
     * The trend of a category.
     *
     * @param category the category
     * @param figure   its counts in both periods
     * @return the trend
     */
    public static CategoryTrend of(CategoryRef category, Figure figure) {
        return new CategoryTrend(category, figure.current(), figure.previous(), figure.delta(),
            figure.deltaPercent());
    }
}

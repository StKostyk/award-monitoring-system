package ua.edu.chnu.awards.analytics.dto;

import java.math.BigDecimal;

/**
 * An awarding organisation whose count changed between the previous period and the period.
 *
 * @param name         the most frequent spelling over both periods
 * @param current      counted awards in the period
 * @param previous     counted awards in the previous period
 * @param delta        current minus previous
 * @param deltaPercent the delta in percent of the previous count, one decimal; null when the previous count is 0
 */
public record SourceTrend(String name, long current, long previous, long delta, BigDecimal deltaPercent) {

    /**
     * The trend of an awarding organisation.
     *
     * @param name   the most frequent spelling
     * @param figure its counts in both periods
     * @return the trend
     */
    public static SourceTrend of(String name, Figure figure) {
        return new SourceTrend(name, figure.current(), figure.previous(), figure.delta(), figure.deltaPercent());
    }
}

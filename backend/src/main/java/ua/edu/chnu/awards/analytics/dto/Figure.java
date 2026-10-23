package ua.edu.chnu.awards.analytics.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A count in the period and in the previous period of the same length.
 *
 * @param current      count in the period
 * @param previous     count in the previous period
 * @param delta        current minus previous
 * @param deltaPercent the delta in percent of the previous count, one decimal; null when the previous count is 0
 */
public record Figure(long current, long previous, long delta, BigDecimal deltaPercent) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Derives the delta of two counts.
     *
     * @param current  count in the period
     * @param previous count in the previous period
     * @return the figure
     */
    public static Figure of(long current, long previous) {
        long delta = current - previous;
        BigDecimal percent = previous == 0 ? null
            : BigDecimal.valueOf(delta).multiply(HUNDRED).divide(BigDecimal.valueOf(previous), 1,
                RoundingMode.HALF_UP);
        return new Figure(current, previous, delta, percent);
    }
}

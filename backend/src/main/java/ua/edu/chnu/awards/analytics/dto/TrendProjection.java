package ua.edu.chnu.awards.analytics.dto;

import java.math.BigDecimal;

import ua.edu.chnu.awards.award.dto.YearRange;

/**
 * A least-squares line over the yearly totals of the complete years of a period, extended by one year.
 *
 * @param status   whether there were enough complete years
 * @param basis    the complete years; null with too few
 * @param slope    awards per year, one decimal; null with too few
 * @param year     the year after the basis; null with too few
 * @param estimate the line's value for that year, rounded, never below 0; null with too few
 */
public record TrendProjection(Status status, YearRange basis, BigDecimal slope, Integer year, Long estimate) {

    /** The projection of a period with too few complete years. */
    public static final TrendProjection TOO_FEW_YEARS = new TrendProjection(Status.TOO_FEW_YEARS, null, null, null,
        null);

    /**
     * Whether the line could be drawn.
     */
    public enum Status {
        /** At least 3 complete years. */
        PROJECTED,
        /** Fewer than 3 complete years. */
        TOO_FEW_YEARS
    }
}

package ua.edu.chnu.awards.analytics.service;

import java.util.Map;
import java.util.function.IntUnaryOperator;

import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * Resolves the requested period of calendar years of the analytics.
 */
final class PeriodHelper {

    private static final int DEFAULT_YEARS = 5;
    private static final String FROM = "from";
    private static final String TO = "to";

    private PeriodHelper() {
    }

    /**
     * Checks the requested years and fills in the defaults: the last five years up to the current one.
     *
     * @param from        first calendar year, or null for four years before the last
     * @param to          last calendar year, or null for the current year
     * @param currentYear the current Kyiv year
     * @return the period
     * @throws ApiProblemException 400 {@code invalid-parameter} naming {@code from} or {@code to}
     */
    static YearRange resolve(Integer from, Integer to, int currentYear) {
        return resolve(from, to, currentYear, last -> last - DEFAULT_YEARS + 1);
    }

    /**
     * Checks the requested years and fills in the defaults.
     *
     * @param from         first calendar year, or null
     * @param to           last calendar year, or null for the current year
     * @param currentYear  the current Kyiv year
     * @param defaultFirst the first year when none was given, from the last year
     * @return the period
     * @throws ApiProblemException 400 {@code invalid-parameter} naming {@code from} or {@code to}
     */
    static YearRange resolve(Integer from, Integer to, int currentYear, IntUnaryOperator defaultFirst) {
        YearRange.requireYear(from, FROM);
        YearRange.requireYear(to, TO);
        int last = to == null ? currentYear : to;
        int first = from == null ? defaultFirst.applyAsInt(last) : from;
        if (first > last) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "The first year must not lie after the last year " + last, Map.of("parameter", FROM));
        }
        return new YearRange(first, last);
    }
}

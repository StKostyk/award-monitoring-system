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

    private static final int FIRST_YEAR = 1950;
    private static final int LAST_YEAR = 2100;
    private static final String FROM = "from";
    private static final String TO = "to";

    private PeriodHelper() {
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
        requireYear(from, FROM);
        requireYear(to, TO);
        int last = to == null ? currentYear : to;
        int first = from == null ? defaultFirst.applyAsInt(last) : from;
        if (first > last) {
            throw invalid(FROM, "The first year must not lie after the last year " + last);
        }
        return new YearRange(first, last);
    }

    private static void requireYear(Integer year, String parameter) {
        if (year != null && (year < FIRST_YEAR || year > LAST_YEAR)) {
            throw invalid(parameter, "The year must lie between " + FIRST_YEAR + " and " + LAST_YEAR);
        }
    }

    private static ApiProblemException invalid(String parameter, String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter", detail,
            Map.of("parameter", parameter));
    }
}

package ua.edu.chnu.awards.award.dto;

import java.util.Map;

import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * Calendar years from one to another, both included.
 *
 * @param from first year
 * @param to   last year
 */
public record YearRange(int from, int to) {

    private static final int FIRST_YEAR = 1950;
    private static final int LAST_YEAR = 2100;

    /**
     * Checks a requested calendar year.
     *
     * @param year      the year, or null when none was given
     * @param parameter the query parameter named in the problem
     * @throws ApiProblemException 400 {@code invalid-parameter} for a year outside 1950..2100
     */
    public static void requireYear(Integer year, String parameter) {
        if (year != null && (year < FIRST_YEAR || year > LAST_YEAR)) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "The year must lie between " + FIRST_YEAR + " and " + LAST_YEAR, Map.of("parameter", parameter));
        }
    }

    /**
     * The period of the same length just before this one.
     *
     * @return the previous period
     */
    public YearRange previous() {
        return new YearRange(2 * from - to - 1, from - 1);
    }
}

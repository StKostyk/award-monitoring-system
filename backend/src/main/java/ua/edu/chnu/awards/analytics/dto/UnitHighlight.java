package ua.edu.chnu.awards.analytics.dto;

import ua.edu.chnu.awards.organization.dto.OrganizationRef;

/**
 * A department of a faculty, or a faculty or college of the university, that stands out by a fixed rule.
 *
 * @param type     the rule
 * @param unit     the unit
 * @param current  counted awards in the period
 * @param previous counted awards in the previous period
 */
public record UnitHighlight(Type type, OrganizationRef unit, long current, long previous) {

    /**
     * The rules, gaps first.
     */
    public enum Type {
        /** No counted award in the period. */
        NO_AWARDS,
        /** At least 4 in the previous period and at most half of it now. */
        DECLINE,
        /** Awards per member under half of the faculty's, with at least the minimum group of members. */
        BELOW_AVERAGE,
        /** At least 4 in the previous period and at least 1.5 times it now. */
        GROWTH,
        /**
         * Awards per member at least 1.5 times the university's, with at least the minimum group of members;
         * university analytics only.
         */
        ABOVE_AVERAGE
    }
}

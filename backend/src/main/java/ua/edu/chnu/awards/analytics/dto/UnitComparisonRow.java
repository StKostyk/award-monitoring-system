package ua.edu.chnu.awards.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

import ua.edu.chnu.awards.organization.dto.OrganizationRef;

/**
 * A child department of a faculty, or the faculty level itself.
 *
 * @param unit              the department, or the faculty for the faculty level
 * @param facultyLevel      whether the row holds what was submitted at faculty level
 * @param active            whether the unit is active
 * @param members           active accounts of the row
 * @param approved          counted awards, compared with the previous period
 * @param awardedPeople     distinct owners of counted personal awards in the period
 * @param perHundredMembers counted awards per 100 members, one decimal; null without members
 * @param verified          counted awards with a verification badge in the period
 * @param byYear            counted awards per year of the period, zeros included
 */
public record UnitComparisonRow(OrganizationRef unit, boolean facultyLevel, boolean active, long members,
                                Figure approved, long awardedPeople, BigDecimal perHundredMembers, long verified,
                                List<Long> byYear) {

    /**
     * Copies the series.
     *
     * @param unit              the department, or the faculty
     * @param facultyLevel      whether the row is the faculty level
     * @param active            whether the unit is active
     * @param members           active accounts of the row
     * @param approved          counted awards
     * @param awardedPeople     distinct owners of counted personal awards
     * @param perHundredMembers counted awards per 100 members
     * @param verified          counted awards with a verification badge
     * @param byYear            counted awards per year of the period
     */
    public UnitComparisonRow {
        byYear = List.copyOf(byYear);
    }
}

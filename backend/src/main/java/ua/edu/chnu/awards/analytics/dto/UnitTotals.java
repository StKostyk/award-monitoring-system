package ua.edu.chnu.awards.analytics.dto;

import java.math.BigDecimal;

/**
 * Totals of the counted awards of a unit.
 *
 * @param approved          counted awards, compared with the previous period
 * @param awardedPeople     distinct owners of counted personal awards, compared with the previous period
 * @param personal          counted personal awards in the period
 * @param unitAwards        counted unit awards in the period
 * @param verified          counted awards with a verification badge in the period
 * @param members           active accounts of the unit's subtree
 * @param perHundredMembers counted awards per 100 members, one decimal; null without members
 */
public record UnitTotals(Figure approved, Figure awardedPeople, long personal, long unitAwards, long verified,
                         long members, BigDecimal perHundredMembers) {
}

package ua.edu.chnu.awards.award.dto;

/**
 * Approved awards submitted in one organisation, of one category, in one calendar year, personal or unit awards.
 *
 * @param organizationId the organisation of the submission
 * @param year           calendar year of the award date
 * @param category       the category with its level
 * @param unitAward      whether the awards name a unit as recipient
 * @param count          number of awards
 * @param verified       number of those with a verification badge
 */
public record UnitAwardCount(long organizationId, int year, CategoryRef category, boolean unitAward, long count,
                             long verified) {
}

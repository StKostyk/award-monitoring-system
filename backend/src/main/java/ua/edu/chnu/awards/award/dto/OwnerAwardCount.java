package ua.edu.chnu.awards.award.dto;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * Approved personal awards of one owner submitted in one organisation, in the period or in the previous period.
 *
 * @param userId         the award owner
 * @param organizationId the organisation of the submission
 * @param current        true for the period, false for the previous period
 * @param count          number of awards
 * @param highestLevel   the highest recognition level among them
 */
public record OwnerAwardCount(long userId, long organizationId, boolean current, long count,
                              RecognitionLevel highestLevel) {
}

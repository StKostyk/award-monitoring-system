package ua.edu.chnu.awards.award.dto;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RequestStatus;

/**
 * Where an award stands after a reviewer decision.
 *
 * @param awardId        the award
 * @param status         the award's status
 * @param requestStatus  the request's status
 * @param level          the level the request stands at now
 * @param requestVersion the request's new version
 */
public record DecisionOutcome(long awardId, AwardStatus status, RequestStatus requestStatus, ApprovalLevel level,
                              long requestVersion) {
}

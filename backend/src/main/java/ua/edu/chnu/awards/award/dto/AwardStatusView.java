package ua.edu.chnu.awards.award.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RequestStatus;

/**
 * The review timeline of an award. Every request field is null, and the path and decisions are empty, for an
 * award without a request.
 *
 * @param awardId             the award
 * @param status              award status
 * @param requestStatus       request status
 * @param currentLevel        level the request stands at
 * @param submittedAt         when it was submitted
 * @param deadline            end of the current level's review period
 * @param estimatedCompletion Kyiv date the last level is expected to finish; null when no level is reviewing
 * @param overdue             whether the current level is past its deadline
 * @param completedAt         when the request reached a final status
 * @param rejectionReason     why it was rejected
 * @param delay               why it is late, null when it is not
 * @param path                the approval levels, lowest first
 * @param decisions           reviewer decisions, oldest first
 */
public record AwardStatusView(Long awardId, AwardStatus status, RequestStatus requestStatus,
                              ApprovalLevel currentLevel, Instant submittedAt, Instant deadline,
                              LocalDate estimatedCompletion, boolean overdue, Instant completedAt,
                              String rejectionReason, StatusDelay delay, List<PathStep> path,
                              List<DecisionView> decisions) {

    /**
     * Keeps unmodifiable copies of the path and the decisions.
     *
     * @param awardId             the award
     * @param status              award status
     * @param requestStatus       request status
     * @param currentLevel        level the request stands at
     * @param submittedAt         when it was submitted
     * @param deadline            end of the current level's review period
     * @param estimatedCompletion Kyiv date the last level is expected to finish
     * @param overdue             whether the current level is past its deadline
     * @param completedAt         when the request reached a final status
     * @param rejectionReason     why it was rejected
     * @param delay               why it is late
     * @param path                the approval levels
     * @param decisions           reviewer decisions
     */
    public AwardStatusView {
        path = List.copyOf(path);
        decisions = List.copyOf(decisions);
    }

    /**
     * The view of an award that has no request, such as a draft.
     *
     * @param awardId the award
     * @param status  its status
     * @return the view
     */
    public static AwardStatusView withoutRequest(Long awardId, AwardStatus status) {
        return new AwardStatusView(awardId, status, null, null, null, null, null, false, null, null, null,
            List.of(), List.of());
    }
}

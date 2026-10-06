package ua.edu.chnu.awards.award.service;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * The decision rows of the approval workflow's transition table (ADR-023): what a decision on an open request
 * records, where the request and the award go, and whether the request climbs to the next level.
 */
@Component
public class Transitions {

    /**
     * The step a decision takes from the level a request stands at.
     *
     * @param decision the decision
     * @param current  the level the request stands at
     * @param minimum  the minimum approval level of the award's category
     * @return the step
     * @throws ApiProblemException 409 {@code no-higher-level} for an escalation at the rector
     */
    public Step of(Decision decision, ApprovalLevel current, ApprovalLevel minimum) {
        return switch (decision) {
            case APPROVE -> current.covers(minimum)
                ? new Step(ReviewDecisionType.APPROVED, RequestStatus.APPROVED, AwardStatus.APPROVED, false)
                : new Step(ReviewDecisionType.APPROVED, RequestStatus.ESCALATED, AwardStatus.PENDING, true);
            case ESCALATE -> {
                if (current == ApprovalLevel.RECTOR) {
                    throw new ApiProblemException(HttpStatus.CONFLICT, "no-higher-level",
                        "The rector is the highest level", Map.of("level", current.name()));
                }
                yield new Step(ReviewDecisionType.ESCALATED, RequestStatus.ESCALATED, AwardStatus.PENDING, true);
            }
            case RETURN -> new Step(ReviewDecisionType.RETURNED, RequestStatus.RETURNED, AwardStatus.DRAFT, false);
            case REJECT -> new Step(ReviewDecisionType.REJECTED, RequestStatus.REJECTED, AwardStatus.REJECTED, false);
        };
    }

    /**
     * The level above the given one.
     *
     * @param level a level below the rector
     * @return the next level
     */
    public ApprovalLevel above(ApprovalLevel level) {
        return ApprovalLevel.values()[level.ordinal() + 1];
    }

    /**
     * One transition.
     *
     * @param recorded what the decision row records at the deciding level
     * @param request  the request's new status
     * @param award    the award's new status
     * @param climbs   true when the request moves to the next level not passed over
     */
    public record Step(ReviewDecisionType recorded, RequestStatus request, AwardStatus award, boolean climbs) {

        /**
         * Whether the step ends the review.
         *
         * @return true for an approval or a rejection
         */
        public boolean isFinal() {
            return request == RequestStatus.APPROVED || request == RequestStatus.REJECTED;
        }
    }
}

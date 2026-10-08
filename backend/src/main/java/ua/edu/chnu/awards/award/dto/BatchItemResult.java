package ua.edu.chnu.awards.award.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardStatus;

/**
 * What became of one item of a batch decision.
 *
 * @param awardId the award
 * @param outcome whether the decision was applied
 * @param code    the problem type slug of a failed item
 * @param detail  why the item failed
 * @param status  the award status after a decided item
 * @param level   the level the request stands at after a decided item
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchItemResult(long awardId, Outcome outcome, String code, String detail, AwardStatus status,
                              ApprovalLevel level) {

    /**
     * A decided item.
     *
     * @param outcome the single decision's result
     * @return the result of the item
     */
    public static BatchItemResult done(DecisionOutcome outcome) {
        return new BatchItemResult(outcome.awardId(), Outcome.DONE, null, null, outcome.status(), outcome.level());
    }

    /**
     * A failed item.
     *
     * @param awardId the award
     * @param code    the problem type slug
     * @param detail  why it failed
     * @return the result of the item
     */
    public static BatchItemResult failed(long awardId, String code, String detail) {
        return new BatchItemResult(awardId, Outcome.FAILED, code, detail, null, null);
    }

    /**
     * Whether an item was decided.
     */
    public enum Outcome {
        DONE,
        FAILED
    }
}

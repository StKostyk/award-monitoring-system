package ua.edu.chnu.awards.award.dto;

import java.time.Instant;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.ReviewDecision;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;

/**
 * A reviewer decision as shown to the readers of the award.
 *
 * @param id           decision identifier
 * @param decision     what was decided
 * @param level        the level that decided
 * @param reviewerId   who decided
 * @param reviewerName first and last name of the reviewer
 * @param comments     the reviewer's comment, required for a rejection or a return
 * @param decidedAt    when
 */
public record DecisionView(Long id, ReviewDecisionType decision, ApprovalLevel level, Long reviewerId,
                           String reviewerName, String comments, Instant decidedAt) {

    /**
     * The view of a decision with its reviewer loaded.
     *
     * @param decision the decision
     * @return the view
     */
    public static DecisionView of(ReviewDecision decision) {
        return new DecisionView(decision.getId(), decision.getDecision(), decision.getLevel(),
            decision.getReviewer().getId(), decision.getReviewer().getFullName(), decision.getComments(),
            decision.getDecidedAt());
    }
}

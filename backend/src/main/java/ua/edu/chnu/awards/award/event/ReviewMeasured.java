package ua.edu.chnu.awards.award.event;

import java.time.Duration;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;

/**
 * One reviewer decision as the review metrics count it, recorded once the decision commits.
 *
 * @param level    the deciding level
 * @param decision what the decision row records
 * @param onTime   whether the decision came before the deadline
 * @param duration from the time the request reached the level to the decision
 */
public record ReviewMeasured(ApprovalLevel level, ReviewDecisionType decision, boolean onTime, Duration duration) {
}

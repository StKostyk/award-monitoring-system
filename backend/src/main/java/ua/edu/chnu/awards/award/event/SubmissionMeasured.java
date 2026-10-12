package ua.edu.chnu.awards.award.event;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;

/**
 * One award submission as the workflow metrics count it, recorded once the submission commits.
 *
 * @param level       the level the request starts at
 * @param resubmitted whether the award had been returned or withdrawn before
 */
public record SubmissionMeasured(ApprovalLevel level, boolean resubmitted) {
}

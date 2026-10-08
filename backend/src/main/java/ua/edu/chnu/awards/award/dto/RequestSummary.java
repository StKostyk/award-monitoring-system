package ua.edu.chnu.awards.award.dto;

import java.time.Instant;
import java.time.LocalDate;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;

/**
 * The approval request of a submitted award.
 *
 * @param status              request status
 * @param currentLevel        approval level the request waits at
 * @param submittedAt         when it was submitted
 * @param deadline            end of the current level's review period
 * @param estimatedCompletion Kyiv date the last level is expected to finish; null when no level is reviewing
 * @param overdue             whether the current level is past its deadline
 * @param returnComment       the reviewer's comment of a return while the award waits for resubmission
 */
public record RequestSummary(RequestStatus status, ApprovalLevel currentLevel, Instant submittedAt,
                             Instant deadline, LocalDate estimatedCompletion, boolean overdue,
                             String returnComment) {
}

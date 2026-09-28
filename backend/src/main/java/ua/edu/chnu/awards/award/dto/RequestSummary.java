package ua.edu.chnu.awards.award.dto;

import java.time.Instant;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;

/**
 * The approval request of a submitted award.
 *
 * @param status       request status
 * @param currentLevel approval level the request waits at
 * @param submittedAt  when it was submitted
 */
public record RequestSummary(RequestStatus status, ApprovalLevel currentLevel, Instant submittedAt) {
}

package ua.edu.chnu.awards.award.dto;

import java.time.Instant;
import java.time.LocalDate;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;

/**
 * One level of the approval path of a request.
 *
 * @param level       the approval level
 * @param state       whether the level is done, reviewing now or still ahead
 * @param dueDate     Kyiv date the level is expected to finish; null for a done level and for a request no level
 *                    is reviewing
 * @param completedAt when the level approved or passed the request on; null when it has not
 */
public record PathStep(ApprovalLevel level, StepState state, LocalDate dueDate, Instant completedAt) {
}

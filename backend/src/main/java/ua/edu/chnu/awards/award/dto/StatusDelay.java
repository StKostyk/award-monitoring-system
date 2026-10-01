package ua.edu.chnu.awards.award.dto;

import java.time.Instant;

/**
 * The explanation of a delayed request.
 *
 * @param reason why it is delayed
 * @param since  the missed deadline for {@link DelayReason#REVIEW_OVERDUE}, null otherwise
 */
public record StatusDelay(DelayReason reason, Instant since) {
}

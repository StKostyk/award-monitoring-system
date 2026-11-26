package ua.edu.chnu.awards.award.dto;

import java.time.Instant;

/**
 * The explanation of a delayed request.
 *
 * @param reason    why it is delayed
 * @param since     the missed deadline for {@link DelayReason#REVIEW_OVERDUE}, null otherwise
 * @param noticedAt when the next level was told about the missed deadline, null while not yet
 */
public record StatusDelay(DelayReason reason, Instant since, Instant noticedAt) {
}

package ua.edu.chnu.awards.award.event;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;

/**
 * A reviewer decided on an award; its owner is told once the surrounding transaction commits.
 *
 * @param email     the owner's address
 * @param firstName used in the greeting
 * @param awardId   the award
 * @param title     the award's title, the Ukrainian one when it has no English title
 * @param titleUk   the award's Ukrainian title, the English one when it has none
 * @param outcome   {@code APPROVED}, {@code ESCALATED} (passed on), {@code RETURNED} or {@code REJECTED}
 * @param level     the level the request stands at now
 * @param reviewer  full name of the reviewer
 * @param comment   the reviewer's comment, null when none was given
 */
public record AwardDecided(String email, String firstName, long awardId, String title, String titleUk,
                           RequestStatus outcome, ApprovalLevel level, String reviewer, String comment) {
}

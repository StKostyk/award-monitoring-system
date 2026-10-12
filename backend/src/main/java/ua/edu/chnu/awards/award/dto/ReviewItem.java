package ua.edu.chnu.awards.award.dto;

import java.time.Instant;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.user.dto.OrganizationRef;

/**
 * One request in a reviewer's queue.
 *
 * @param awardId        the award, used in every workflow path
 * @param requestId      its approval request
 * @param requestVersion the version of the request to send back with a claim, release or decision
 * @param title          English title
 * @param titleUk        Ukrainian title
 * @param recipient      the owner or a unit
 * @param owner          who owns the award
 * @param organization   the faculty or department the award belongs to
 * @param category       the category with its recognition level
 * @param level          the level the request waits at
 * @param status         the request status
 * @param reviewer       who claimed it, null while nobody holds it
 * @param submittedAt    when it was submitted
 * @param deadline       when the current level is due
 * @param overdue        whether the deadline has passed
 * @param overdueNoticedAt when the next level was told about the missed deadline, null while not yet
 * @param documentCount  how many documents the award has
 * @param delegatedFrom  the person whose delegation lets the caller review it, null under an own role
 * @param canTakeOver    whether the caller may take the request over from its holder; set only on the item of one award
 * @param canEscalate    whether the caller may escalate the request; set only on the item of one award
 */
@SuppressWarnings("PMD.CommentSize")
public record ReviewItem(Long awardId, Long requestId, Long requestVersion, String title, String titleUk,
                         AwardRecipient recipient, UserRef owner, OrganizationRef organization, CategoryRef category,
                         ApprovalLevel level, RequestStatus status, UserRef reviewer, Instant submittedAt,
                         Instant deadline, boolean overdue, Instant overdueNoticedAt, long documentCount,
                         UserRef delegatedFrom, Boolean canTakeOver, Boolean canEscalate) {

    /**
     * The same item with the flags of what the caller is offered set.
     *
     * @param takeOver whether the caller may take the request over
     * @param escalate whether the caller may escalate it
     * @return the item
     */
    public ReviewItem withOffers(boolean takeOver, boolean escalate) {
        return new ReviewItem(awardId, requestId, requestVersion, title, titleUk, recipient, owner, organization,
            category, level, status, reviewer, submittedAt, deadline, overdue, overdueNoticedAt, documentCount,
            delegatedFrom, takeOver, escalate);
    }
}

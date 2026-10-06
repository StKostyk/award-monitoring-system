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
 * @param documentCount  how many documents the award has
 * @param delegatedFrom  the person whose delegation lets the caller review it, null under an own role
 */
@SuppressWarnings("PMD.CommentSize")
public record ReviewItem(Long awardId, Long requestId, Long requestVersion, String title, String titleUk,
                         AwardRecipient recipient, UserRef owner, OrganizationRef organization, CategoryRef category,
                         ApprovalLevel level, RequestStatus status, UserRef reviewer, Instant submittedAt,
                         Instant deadline, boolean overdue, long documentCount, UserRef delegatedFrom) {
}

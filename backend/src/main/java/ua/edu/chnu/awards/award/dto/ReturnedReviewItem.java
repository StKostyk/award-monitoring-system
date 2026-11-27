package ua.edu.chnu.awards.award.dto;

import java.time.Instant;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.organization.dto.OrganizationRef;

/**
 * An award sent back for rework that its owner has not submitted again, as the in-rework tab of the queue lists it.
 *
 * @param awardId      the award
 * @param requestId    its approval request
 * @param title        English title
 * @param titleUk      Ukrainian title
 * @param recipient    the owner or a unit
 * @param owner        who owns the award
 * @param organization the faculty or department the award belongs to
 * @param category     the category with its recognition level
 * @param level        the level that sent it back
 * @param returnedAt   when it was sent back
 * @param returnedBy   who sent it back
 * @param returnedFor  whose delegated role they acted under, null under an own role
 * @param comment      the reason given
 * @param daysWaiting  whole days since it was sent back
 */
@SuppressWarnings("PMD.CommentSize")
public record ReturnedReviewItem(Long awardId, Long requestId, String title, String titleUk,
                                 AwardRecipient recipient, UserRef owner, OrganizationRef organization,
                                 CategoryRef category, ApprovalLevel level, Instant returnedAt, UserRef returnedBy,
                                 UserRef returnedFor, String comment, long daysWaiting) {
}

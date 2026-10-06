package ua.edu.chnu.awards.award.dto;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;

/**
 * Filters of the reviewer queue.
 *
 * @param assigned       who holds the requests; null for everybody
 * @param level          the level the requests wait at; null for the caller's own levels
 * @param organizationId a faculty or department inside the caller's scopes, with its sub-units
 * @param overdue        true for overdue requests only
 */
public record ReviewQuery(Assignment assigned, ApprovalLevel level, Long organizationId, boolean overdue) {

    /**
     * Who holds a request.
     */
    public enum Assignment {
        ME,
        UNASSIGNED,
        OTHERS
    }
}

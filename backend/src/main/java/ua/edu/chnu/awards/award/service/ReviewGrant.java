package ua.edu.chnu.awards.award.service;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;

/**
 * Approval authority of the caller in one scope, held or borrowed.
 *
 * @param level          the level the role reviews
 * @param organizationId the root of the scope
 * @param delegatorId    who lent the role, null for an own role
 */
public record ReviewGrant(ApprovalLevel level, long organizationId, Long delegatorId) {

    /**
     * Whether the authority is borrowed.
     *
     * @return true for a delegation
     */
    public boolean delegated() {
        return delegatorId != null;
    }
}

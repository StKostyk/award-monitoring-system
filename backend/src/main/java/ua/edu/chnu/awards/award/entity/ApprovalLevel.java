package ua.edu.chnu.awards.award.entity;

import java.util.Arrays;
import java.util.Optional;

import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * Approval levels of the award workflow in the order a request climbs them (`award_requests.current_level`).
 */
public enum ApprovalLevel {
    FACULTY_SECRETARY(RoleType.FACULTY_SECRETARY),
    DEAN(RoleType.DEAN),
    RECTOR_SECRETARY(RoleType.RECTOR_SECRETARY),
    RECTOR(RoleType.RECTOR);

    private final RoleType role;

    ApprovalLevel(RoleType role) {
        this.role = role;
    }

    /**
     * The role whose holders review requests at this level.
     *
     * @return the reviewing role
     */
    public RoleType role() {
        return role;
    }

    /**
     * The level whose requests holders of a role review.
     *
     * @param role any role
     * @return the level, empty for a role that reviews nothing
     */
    public static Optional<ApprovalLevel> of(RoleType role) {
        return Arrays.stream(values()).filter(level -> level.role == role).findFirst();
    }

    /**
     * Whether this level is the given one or above it.
     *
     * @param other another level
     * @return true when this level reviews at least as high
     */
    public boolean covers(ApprovalLevel other) {
        return compareTo(other) >= 0;
    }
}

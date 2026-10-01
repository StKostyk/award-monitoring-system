package ua.edu.chnu.awards.award.entity;

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
}

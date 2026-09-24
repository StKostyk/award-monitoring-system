package ua.edu.chnu.awards.user.service;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * Which level of the organisation tree each role belongs to. An {@code EMPLOYEE} works in a department, a
 * {@code DEAN} leads a faculty or a college, a {@code FACULTY_SECRETARY} serves a faculty or one of its
 * departments, and the university roles apply to the university itself.
 */
@Component
public class RoleOrganizations {

    private static final Map<RoleType, Set<OrganizationType>> ALLOWED = Map.of(
        RoleType.EMPLOYEE, EnumSet.of(OrganizationType.DEPARTMENT),
        RoleType.FACULTY_SECRETARY, EnumSet.of(OrganizationType.FACULTY, OrganizationType.DEPARTMENT),
        RoleType.DEAN, EnumSet.of(OrganizationType.FACULTY, OrganizationType.COLLEGE),
        RoleType.RECTOR_SECRETARY, EnumSet.of(OrganizationType.UNIVERSITY),
        RoleType.RECTOR, EnumSet.of(OrganizationType.UNIVERSITY),
        RoleType.SYSTEM_ADMIN, EnumSet.of(OrganizationType.UNIVERSITY),
        RoleType.GDPR_OFFICER, EnumSet.of(OrganizationType.UNIVERSITY));

    /**
     * Whether the role may be granted at that level of the tree.
     *
     * @param role the role
     * @param type the level of the organisation
     * @return true when the pair fits
     */
    public boolean fits(RoleType role, OrganizationType type) {
        return ALLOWED.getOrDefault(role, Set.of()).contains(type);
    }

    /**
     * Levels a role may be granted at, for the message of a refusal.
     *
     * @param role the role
     * @return the allowed levels
     */
    public Set<OrganizationType> levelsOf(RoleType role) {
        return ALLOWED.getOrDefault(role, Set.of());
    }
}

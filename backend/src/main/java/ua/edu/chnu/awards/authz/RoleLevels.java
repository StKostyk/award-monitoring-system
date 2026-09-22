package ua.edu.chnu.awards.authz;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * The line of authority for granting roles, lowest first: {@code EMPLOYEE}, {@code FACULTY_SECRETARY},
 * {@code DEAN}, {@code RECTOR_SECRETARY}, {@code RECTOR}. A holder may grant roles strictly below their own.
 * {@code SYSTEM_ADMIN} is above everything and is the only role that grants the system roles
 * ({@code SYSTEM_ADMIN}, {@code GDPR_OFFICER}); {@code GDPR_OFFICER} is outside the line and grants nothing.
 */
@Component
public class RoleLevels {

    private static final Map<RoleType, Integer> LINE = Map.of(
        RoleType.EMPLOYEE, 0,
        RoleType.FACULTY_SECRETARY, 1,
        RoleType.DEAN, 2,
        RoleType.RECTOR_SECRETARY, 3,
        RoleType.RECTOR, 4);
    private static final Set<RoleType> SYSTEM = EnumSet.of(RoleType.SYSTEM_ADMIN, RoleType.GDPR_OFFICER);

    /**
     * Position of a line role; system roles have no position.
     *
     * @param role a line role
     * @return 0 for {@code EMPLOYEE} up to 4 for {@code RECTOR}, -1 for system roles
     */
    public int of(RoleType role) {
        return LINE.getOrDefault(role, -1);
    }

    /**
     * Whether the role is a system role rather than a step in the line.
     *
     * @param role the role
     * @return true for {@code SYSTEM_ADMIN} and {@code GDPR_OFFICER}
     */
    public boolean isSystem(RoleType role) {
        return SYSTEM.contains(role);
    }

    /**
     * Whether a holder of {@code holder} may grant {@code target}.
     *
     * @param holder the role held
     * @param target the role to grant
     * @return true when {@code holder} outranks {@code target}
     */
    public boolean above(RoleType holder, RoleType target) {
        if (holder == RoleType.SYSTEM_ADMIN) {
            return true;
        }
        if (isSystem(holder) || isSystem(target)) {
            return false;
        }
        return of(holder) > of(target);
    }
}

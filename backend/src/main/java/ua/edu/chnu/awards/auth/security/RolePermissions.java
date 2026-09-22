package ua.edu.chnu.awards.auth.security;

import static java.util.Map.entry;
import static ua.edu.chnu.awards.user.entity.RoleType.DEAN;
import static ua.edu.chnu.awards.user.entity.RoleType.EMPLOYEE;
import static ua.edu.chnu.awards.user.entity.RoleType.FACULTY_SECRETARY;
import static ua.edu.chnu.awards.user.entity.RoleType.GDPR_OFFICER;
import static ua.edu.chnu.awards.user.entity.RoleType.RECTOR;
import static ua.edu.chnu.awards.user.entity.RoleType.RECTOR_SECRETARY;
import static ua.edu.chnu.awards.user.entity.RoleType.SYSTEM_ADMIN;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * Permission matrix of the authorization design: which roles hold each permission.
 */
@Component
public class RolePermissions {

    private static final Set<RoleType> APPROVERS = EnumSet.of(FACULTY_SECRETARY, DEAN, RECTOR_SECRETARY, RECTOR);
    private static final Set<RoleType> OVERSIGHT = EnumSet.of(SYSTEM_ADMIN, GDPR_OFFICER);

    private static final Map<String, Set<RoleType>> HOLDERS = Map.ofEntries(
        entry("award:read:own", EnumSet.allOf(RoleType.class)),
        entry("award:read:department", union(APPROVERS, OVERSIGHT)),
        entry("award:read:faculty", union(EnumSet.of(DEAN, RECTOR_SECRETARY, RECTOR), OVERSIGHT)),
        entry("award:read:all", union(EnumSet.of(RECTOR_SECRETARY, RECTOR), OVERSIGHT)),
        entry("award:create", union(EnumSet.of(EMPLOYEE), APPROVERS)),
        entry("award:update:own", union(EnumSet.of(EMPLOYEE), APPROVERS)),
        entry("award:approve:level1", APPROVERS),
        entry("award:approve:level2", EnumSet.of(DEAN, RECTOR_SECRETARY, RECTOR)),
        entry("award:approve:level3", EnumSet.of(RECTOR_SECRETARY, RECTOR)),
        entry("award:approve:final", EnumSet.of(RECTOR)),
        entry("user:read:all", OVERSIGHT),
        entry("user:read:scope", APPROVERS),
        entry("user:manage", EnumSet.of(SYSTEM_ADMIN)),
        entry("user:manage:scope", APPROVERS),
        entry("system:configure", EnumSet.of(SYSTEM_ADMIN)),
        entry("data:export", OVERSIGHT),
        entry("consent:manage", EnumSet.of(GDPR_OFFICER)),
        entry("audit:read", OVERSIGHT));

    /**
     * Permissions of one role.
     *
     * @param role the role
     * @return sorted permission set
     */
    public Set<String> of(RoleType role) {
        Set<String> result = new TreeSet<>();
        HOLDERS.forEach((permission, holders) -> {
            if (holders.contains(role)) {
                result.add(permission);
            }
        });
        return Set.copyOf(result);
    }

    /**
     * Union of the permissions of several roles, sorted for stable token claims.
     *
     * @param roles the roles held
     * @return sorted permission list
     */
    public List<String> union(Collection<RoleType> roles) {
        Set<String> all = new TreeSet<>();
        roles.forEach(role -> all.addAll(of(role)));
        return List.copyOf(all);
    }

    private static Set<RoleType> union(Set<RoleType> first, Set<RoleType> second) {
        Set<RoleType> result = EnumSet.copyOf(first);
        result.addAll(second);
        return result;
    }
}

package ua.edu.chnu.awards.analytics.service;

import java.text.Collator;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.analytics.dto.AnalysableUnit;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.RolePermissions;
import ua.edu.chnu.awards.authz.RoleScope;
import ua.edu.chnu.awards.organization.OrganizationTree;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;

import lombok.RequiredArgsConstructor;

/**
 * The faculties and departments the caller may analyse: those covered by a scope of a role the caller holds
 * (never a borrowed one) that grants {@code analytics:read:faculty} (faculties and departments) or
 * {@code analytics:read:department} (departments only).
 */
@Component
@RequiredArgsConstructor
public class UnitScope {

    static final String READ_FACULTY = "analytics:read:faculty";
    static final String READ_DEPARTMENT = "analytics:read:department";

    private static final Collator UKRAINIAN = Collator.getInstance(Locale.forLanguageTag("uk"));

    private final AccessScope access;
    private final RolePermissions permissions;
    private final OrganizationTree tree;
    private final OrganizationLookup organizations;

    /**
     * The caller's active analysable units, each faculty followed by its departments, alphabetically by Ukrainian
     * name.
     *
     * @return the units
     */
    public List<AnalysableUnit> list() {
        List<AnalysableUnit> units = organizations.findAllById(analysable()).stream()
            .filter(Organization::isActive).map(this::unit).toList();
        Set<Long> groupIds = units.stream().map(UnitScope::group).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, String> groupNames = organizations.findAllById(groupIds).stream()
            .collect(Collectors.toMap(Organization::getId, UnitScope::organizationName));
        Comparator<AnalysableUnit> order = Comparator
            .comparing((AnalysableUnit unit) -> groupNames.getOrDefault(group(unit), ""), UKRAINIAN)
            .thenComparing(UnitScope::group, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(unit -> unit.type() != OrganizationType.FACULTY)
            .thenComparing(UnitScope::unitName, UKRAINIAN)
            .thenComparing(AnalysableUnit::id);
        return units.stream().sorted(order).toList();
    }

    /**
     * One analysable unit of the caller, active or not.
     *
     * @param unitId the unit
     * @return the unit
     * @throws UnitNotFoundException when it is unknown, of another type or not analysable for the caller
     */
    public AnalysableUnit require(long unitId) {
        if (!analysable().contains(unitId)) {
            throw new UnitNotFoundException(unitId);
        }
        return organizations.findById(unitId).map(this::unit).orElseThrow(() -> new UnitNotFoundException(unitId));
    }

    private Set<Long> analysable() {
        Set<Long> result = new HashSet<>();
        for (RoleScope scope : access.heldScopes()) {
            Set<String> granted = permissions.of(scope.role());
            boolean faculties = granted.contains(READ_FACULTY);
            if (faculties || granted.contains(READ_DEPARTMENT)) {
                tree.subtree(scope.organizationId()).stream()
                    .filter(id -> tree.node(id).map(OrganizationTree.Node::type)
                        .filter(type -> type == OrganizationType.DEPARTMENT
                            || faculties && type == OrganizationType.FACULTY)
                        .isPresent())
                    .forEach(result::add);
            }
        }
        return result;
    }

    private AnalysableUnit unit(Organization organization) {
        Long parentId = tree.node(organization.getId()).map(OrganizationTree.Node::parentId).orElse(null);
        return new AnalysableUnit(organization.getId(), organization.getName(), organization.getNameUk(),
            organization.getCode(), organization.getOrgType(), parentId);
    }

    static Long group(AnalysableUnit unit) {
        return unit.type() == OrganizationType.FACULTY ? unit.id() : unit.parentId();
    }

    static String unitName(AnalysableUnit unit) {
        return unit.nameUk() == null ? unit.name() : unit.nameUk();
    }

    private static String organizationName(Organization organization) {
        return organization.getNameUk() == null ? organization.getName() : organization.getNameUk();
    }
}

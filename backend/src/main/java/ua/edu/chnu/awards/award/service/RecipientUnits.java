package ua.edu.chnu.awards.award.service;

import java.text.Collator;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.authz.RoleScope;
import ua.edu.chnu.awards.award.dto.UnitRef;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;

import lombok.RequiredArgsConstructor;

/**
 * The faculties and departments the caller may enter awards for: the active ones inside the scope of a faculty
 * secretary or dean role the caller holds or was lent.
 */
@Component
@RequiredArgsConstructor
public class RecipientUnits {

    static final Set<RoleType> ROLES = EnumSet.of(RoleType.FACULTY_SECRETARY, RoleType.DEAN);
    static final Set<OrganizationType> TYPES = EnumSet.of(OrganizationType.FACULTY, OrganizationType.DEPARTMENT);

    private final AccessScope access;
    private final OrganizationTree tree;
    private final OrganizationRepository organizations;

    /**
     * The units, faculties first and each group by Ukrainian name.
     *
     * @return the units, empty for a caller without either role
     */
    public List<UnitRef> list() {
        Set<Long> ids = scopes().stream()
            .flatMap(scope -> tree.subtree(scope.organizationId()).stream())
            .filter(this::isUnit)
            .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return List.of();
        }
        Collator collator = Collator.getInstance(Locale.forLanguageTag("uk"));
        return organizations.findAllById(ids).stream()
            .sorted(Comparator.comparing((Organization unit) -> unit.getOrgType() != OrganizationType.FACULTY)
                .thenComparing(unit -> unit.getNameUk() == null ? unit.getName() : unit.getNameUk(), collator))
            .map(UnitRef::of)
            .toList();
    }

    /**
     * Whether the caller may enter awards for the organisation.
     *
     * @param organizationId the organisation
     * @return true for an active faculty or department inside the scope of a faculty secretary or dean role
     */
    public boolean covers(long organizationId) {
        return isUnit(organizationId)
            && scopes().stream().anyMatch(scope -> tree.covers(scope.organizationId(), organizationId));
    }

    private List<RoleScope> scopes() {
        return access.scopes().stream().filter(scope -> ROLES.contains(scope.role())).toList();
    }

    private boolean isUnit(long organizationId) {
        return tree.node(organizationId).filter(OrganizationTree.Node::active)
            .filter(node -> TYPES.contains(node.type())).isPresent();
    }
}

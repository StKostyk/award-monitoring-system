package ua.edu.chnu.awards.user.service;

import java.time.LocalDate;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.RoleLevels;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.dto.RoleAssignmentRequest;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * The checks a role assignment or revocation must pass, each answering with the typed problem the API
 * documents.
 */
@Component
@RequiredArgsConstructor
public class RoleAssignmentRules {

    static final String PERMISSION_MANAGE_ALL = "user:manage";
    private static final LocalDate OPEN_ENDED = LocalDate.of(9999, 12, 31);

    private final UserRoleRepository userRoleRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleOrganizations roleOrganizations;
    private final RoleLevels levels;
    private final AccessScope access;

    /**
     * Whether the caller may grant or take back the role in the organisation.
     *
     * @param role           the role
     * @param organizationId where it applies
     * @return true for {@code user:manage}, or a held role above it whose scope covers the organisation
     */
    public boolean mayManage(RoleType role, long organizationId) {
        return access.has(PERMISSION_MANAGE_ALL) || access.canManage(role, organizationId);
    }

    /**
     * Refuses a role the caller may not grant or take back there.
     *
     * @param role           the role
     * @param organizationId where it applies
     */
    public void requireAuthority(RoleType role, long organizationId) {
        if (!mayManage(role, organizationId)) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "role-above-level",
                "You may grant only roles below your own inside your organisation; university and system roles "
                    + "are granted by the rector's office or the administrator");
        }
    }

    /**
     * An active organisation by id.
     *
     * @param organizationId the organisation
     * @return the organisation
     */
    public Organization activeOrganization(long organizationId) {
        return organizationRepository.findById(organizationId)
            .filter(Organization::isActive)
            .orElseThrow(() -> new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "organisation-invalid",
                "Choose an active organisation"));
    }

    /**
     * Refuses an organisation whose level of the tree does not fit the role.
     *
     * @param role         the role
     * @param organization where it would apply
     */
    public void requireFit(RoleType role, Organization organization) {
        if (!roleOrganizations.fits(role, organization.getOrgType())) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "role-organization-mismatch",
                role + " applies to " + roleOrganizations.levelsOf(role).stream().map(Enum::name)
                    .sorted().collect(Collectors.joining(" or ")) + ", not to a "
                    + organization.getOrgType());
        }
    }

    /**
     * Refuses an account that is not active.
     *
     * @param target who would receive the role
     * @return the same user
     */
    public User requireActive(User target) {
        if (target.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "user-not-active",
                "Only an active account can hold a role");
        }
        return target;
    }

    /**
     * The first day of the new assignment: today when not given, never in the past, never after its last day.
     *
     * @param request the assignment request
     * @param today   the current day
     * @return the first day
     */
    public LocalDate startDay(RoleAssignmentRequest request, LocalDate today) {
        LocalDate validFrom = request.validFrom() == null ? today : request.validFrom();
        if (validFrom.isBefore(today) || request.validTo() != null && request.validTo().isBefore(validFrom)) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "role-validity",
                "A role starts today or later and ends no earlier than it starts");
        }
        return validFrom;
    }

    /**
     * Refuses a role the user already holds there for part of the period.
     *
     * @param userId         the user
     * @param request        the assignment request
     * @param organizationId where it applies
     * @param validFrom      its first day
     */
    public void requireFree(long userId, RoleAssignmentRequest request, long organizationId, LocalDate validFrom) {
        LocalDate until = request.validTo() == null ? OPEN_ENDED : request.validTo();
        if (!userRoleRepository.findOverlapping(userId, request.role(), organizationId, validFrom, until)
            .isEmpty()) {
            throw alreadyAssigned(null);
        }
    }

    /**
     * Refuses a department correction with any role but {@code EMPLOYEE}, and a stale one: a first
     * confirmation needs no precondition, while moving somebody who has already held a role needs the
     * department the caller saw to still be theirs, so a stale confirmation cannot undo a correction.
     *
     * @param request the assignment request
     * @param target  who would be moved
     */
    public void requireMembershipChange(RoleAssignmentRequest request, User target) {
        if (request.role() != RoleType.EMPLOYEE) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "membership-role-required",
                "The department of a user is corrected only while confirming the membership, with an EMPLOYEE "
                    + "role");
        }
        if (userRoleRepository.existsByUserId(target.getId())
            && !target.getOrganization().getId().equals(request.fromOrganizationId())) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "membership-already-confirmed",
                "The membership has already been confirmed or changed; reload the user");
        }
    }

    /**
     * Refuses to let a caller take back their own last role above {@code EMPLOYEE}.
     *
     * @param actor  who takes the role back
     * @param target who holds it
     * @param role   the assignment
     * @param today  the current day
     */
    public void requireNotOwnLastRole(User actor, User target, UserRole role, LocalDate today) {
        if (!actor.getId().equals(target.getId()) || !aboveEmployee(role.getRoleType())) {
            return;
        }
        boolean another = userRoleRepository.findCurrentByUserId(target.getId(), today).stream()
            .filter(held -> !held.getId().equals(role.getId()))
            .anyMatch(held -> aboveEmployee(held.getRoleType()));
        if (!another) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "role-last-own",
                "You cannot take back your own last role above EMPLOYEE; ask somebody above you");
        }
    }

    /**
     * Refuses an assignment that is already over.
     *
     * @param role  the assignment
     * @param today the current day
     */
    public void requireNotEnded(UserRole role, LocalDate today) {
        if (role.hasEndedBy(today)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "role-already-revoked",
                "The assignment has already ended");
        }
    }

    /**
     * The refusal for a role already held, also raised when the unique index catches two grants at once.
     *
     * @param cause what the database said, null when the check found it first
     * @return the problem to answer with
     */
    public ApiProblemException alreadyAssigned(Throwable cause) {
        return new ApiProblemException(HttpStatus.CONFLICT, "role-already-assigned",
            "The user already holds this role in that organisation", cause);
    }

    private boolean aboveEmployee(RoleType role) {
        return levels.of(role) > 0 || levels.isSystem(role);
    }
}

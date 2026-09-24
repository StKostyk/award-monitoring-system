package ua.edu.chnu.awards.user.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.auth.security.AuthorizationRevoker;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.RoleLevels;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.dto.RoleAssignmentRequest;
import ua.edu.chnu.awards.user.dto.RoleAssignmentResponse;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.mapper.UserProfileMapper;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Grants and takes back roles within the caller's authority: inside the subtree of a role they hold and
 * strictly below its level. Nothing is deleted — a revoked assignment ends on the previous day so the history
 * stays readable — and the former holder is signed out everywhere so the lost permissions stop applying at
 * once.
 */
@Service
@RequiredArgsConstructor
public class RoleAssignmentService {

    static final String PERMISSION_MANAGE_ALL = "user:manage";
    private static final LocalDate OPEN_ENDED = LocalDate.of(9999, 12, 31);

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleOrganizations roleOrganizations;
    private final RoleLevels levels;
    private final AccessScope access;
    private final AuthorizationRevoker revoker;
    private final UserProfileMapper mapper;
    private final RoleChangeRecorder recorder;
    private final Clock clock;

    /**
     * Grants a role to a user.
     *
     * @param userId  who receives the role
     * @param request role, organisation and validity
     * @return the new assignment
     * @throws UserNotFoundException when the user is unknown or outside the caller's scope
     * @throws ApiProblemException   when the caller may not grant it, the organisation does not fit the role,
     *                               the user is not active or the role is already held there
     */
    @Transactional
    public RoleAssignmentResponse assign(long userId, RoleAssignmentRequest request) {
        final User target = requireActive(visible(userId));
        Organization organization = activeOrganization(request.organizationId());
        requireFit(request.role(), organization);
        requireAuthority(request.role(), organization.getId());

        User actor = caller();
        LocalDate today = LocalDate.now(clock);
        LocalDate validFrom = startDay(request, today);
        requireFree(userId, request, organization.getId(), validFrom);
        if (request.updateOrganization()) {
            requireMembershipRole(request.role());
            confirmMembership(actor, target, organization, today);
        }

        UserRole assignment = UserRole.builder()
            .user(target)
            .roleType(request.role())
            .organization(organization)
            .validFrom(validFrom)
            .validTo(request.validTo())
            .createdBy(actor)
            .build();
        try {
            userRoleRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException e) {
            throw alreadyAssigned(e);
        }
        recorder.granted(actor, assignment);
        return mapper.toAssignment(assignment);
    }

    /**
     * Takes a role back: it ends on the previous day and the holder is signed out everywhere.
     *
     * @param userId     who holds the role
     * @param assignment which assignment to end
     * @throws UserNotFoundException when the user or the assignment is unknown, or outside the caller's scope
     * @throws ApiProblemException   when the caller may not take it back, it has already ended, or it is the
     *                               caller's own last role above {@code EMPLOYEE}
     */
    @Transactional
    public void revoke(long userId, long assignment) {
        User target = visible(userId);
        UserRole role = userRoleRepository.findByIdAndUserId(assignment, userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        LocalDate today = LocalDate.now(clock);
        if (role.getValidTo() != null && role.getValidTo().isBefore(today)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "role-already-revoked",
                "The assignment has already ended");
        }
        requireAuthority(role.getRoleType(), role.getOrganization().getId());
        User actor = caller();
        requireNotOwnLastRole(actor, target, role, today);

        role.setValidTo(lastDayOf(role, today));
        recorder.revoked(actor, role);
        revoker.revokeAll(target);
    }

    private void confirmMembership(User actor, User target, Organization department, LocalDate today) {
        if (department.getId().equals(target.getOrganization().getId())) {
            return;
        }
        userRoleRepository.findCurrentByUserId(target.getId(), today).stream()
            .filter(role -> role.getRoleType() == RoleType.EMPLOYEE)
            .filter(role -> !role.getOrganization().getId().equals(department.getId()))
            .filter(role -> mayManage(role.getRoleType(), role.getOrganization().getId()))
            .forEach(role -> {
                role.setValidTo(lastDayOf(role, today));
                recorder.superseded(actor, role);
            });
        target.setOrganization(department);
        revoker.revokeAll(target);
    }

    /**
     * The day an assignment stops applying when it is taken back today: yesterday, or the day before it was due
     * to start when it has not started yet, so the row can never be current and the date check still holds.
     */
    private static LocalDate lastDayOf(UserRole role, LocalDate today) {
        return today.isAfter(role.getValidFrom()) ? today.minusDays(1) : role.getValidFrom().minusDays(1);
    }

    private void requireFit(RoleType role, Organization organization) {
        if (!roleOrganizations.fits(role, organization.getOrgType())) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "role-organization-mismatch",
                role + " applies to " + roleOrganizations.levelsOf(role).stream().map(Enum::name)
                    .sorted().collect(Collectors.joining(" or ")) + ", not to a "
                    + organization.getOrgType());
        }
    }

    private void requireAuthority(RoleType role, long organizationId) {
        if (!mayManage(role, organizationId)) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "role-above-level",
                "You may grant only roles below your own inside your organisation; university and system roles "
                    + "are granted by the rector's office or the administrator");
        }
    }

    private boolean mayManage(RoleType role, long organizationId) {
        return access.has(PERMISSION_MANAGE_ALL) || access.canManage(role, organizationId);
    }

    private static User requireActive(User target) {
        if (target.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "user-not-active",
                "Only an active account can hold a role");
        }
        return target;
    }

    private static LocalDate startDay(RoleAssignmentRequest request, LocalDate today) {
        LocalDate validFrom = request.validFrom() == null ? today : request.validFrom();
        if (validFrom.isBefore(today) || request.validTo() != null && request.validTo().isBefore(validFrom)) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "role-validity",
                "A role starts today or later and ends no earlier than it starts");
        }
        return validFrom;
    }

    private void requireFree(long userId, RoleAssignmentRequest request, long organizationId,
                             LocalDate validFrom) {
        LocalDate until = request.validTo() == null ? OPEN_ENDED : request.validTo();
        if (!userRoleRepository.findOverlapping(userId, request.role(), organizationId, validFrom, until)
            .isEmpty()) {
            throw alreadyAssigned();
        }
    }

    private void requireNotOwnLastRole(User actor, User target, UserRole role, LocalDate today) {
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

    private void requireMembershipRole(RoleType role) {
        if (role != RoleType.EMPLOYEE) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "membership-role-required",
                "The department of a user is corrected only while confirming the membership, with an EMPLOYEE "
                    + "role");
        }
    }

    private boolean aboveEmployee(RoleType role) {
        return levels.of(role) > 0 || levels.isSystem(role);
    }

    private User caller() {
        long id = access.callerId();
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private User visible(long userId) {
        return userRepository.findByIdForUpdate(userId)
            .filter(found -> found.getAccountStatus() != AccountStatus.PENDING)
            .filter(found -> access.readableOrganizations()
                .map(ids -> ids.contains(found.getOrganization().getId())).orElse(true))
            .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private Organization activeOrganization(long organizationId) {
        return organizationRepository.findById(organizationId)
            .filter(Organization::isActive)
            .orElseThrow(() -> new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "organisation-invalid",
                "Choose an active organisation"));
    }

    private static ApiProblemException alreadyAssigned() {
        return alreadyAssigned(null);
    }

    private static ApiProblemException alreadyAssigned(Throwable cause) {
        return new ApiProblemException(HttpStatus.CONFLICT, "role-already-assigned",
            "The user already holds this role in that organisation", cause);
    }

}

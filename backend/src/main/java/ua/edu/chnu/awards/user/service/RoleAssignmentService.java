package ua.edu.chnu.awards.user.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.auth.security.AuthorizationRevoker;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.delegation.service.DelegationService;
import ua.edu.chnu.awards.user.dto.RoleAssignmentRequest;
import ua.edu.chnu.awards.user.dto.RoleAssignmentResponse;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.mapper.UserProfileMapper;
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

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleAssignmentRules rules;
    private final AccessScope access;
    private final AuthorizationRevoker revoker;
    private final UserProfileMapper mapper;
    private final RoleChangeRecorder recorder;
    private final DelegationService delegations;
    private final Clock clock;

    /**
     * Grants a role to a user.
     *
     * @param userId  who receives the role
     * @param request role, organisation and validity
     * @return the new assignment
     * @throws UserNotFoundException when the user is unknown or outside the caller's scope
     * @throws ApiProblemException   when the caller may not grant it, the organisation does not fit the role,
     *                               the user is not active, the role is already held there, or a department
     *                               correction is stale
     */
    @Transactional
    public RoleAssignmentResponse assign(long userId, RoleAssignmentRequest request) {
        final User target = rules.requireActive(visible(userId));
        Organization organization = rules.activeOrganization(request.organizationId());
        rules.requireFit(request.role(), organization);
        rules.requireAuthority(request.role(), organization.getId());

        User actor = caller();
        LocalDate today = LocalDate.now(clock);
        LocalDate validFrom = rules.startDay(request, today);
        rules.requireFree(userId, request, organization.getId(), validFrom);
        boolean moved = false;
        if (request.updateOrganization()) {
            rules.requireMembershipChange(request, target);
            moved = confirmMembership(actor, target, organization, today);
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
            throw rules.alreadyAssigned(e);
        }
        if (moved) {
            delegations.revokeForMove(actor, target);
            revoker.revokeAll(target);
        }
        recorder.granted(actor, assignment);
        return mapper.toAssignment(assignment);
    }

    /**
     * Takes a role back: it ends on the previous day, whatever it had delegated is taken back with it, and
     * the holder is signed out everywhere.
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
        rules.requireNotEnded(role, today);
        rules.requireAuthority(role.getRoleType(), role.getOrganization().getId());
        User actor = caller();
        rules.requireNotOwnLastRole(actor, target, role, today);

        role.setValidTo(lastDayOf(role, today));
        recorder.revoked(actor, role);
        delegations.revokeForRole(actor, role);
        revoker.revokeAll(target);
    }

    private boolean confirmMembership(User actor, User target, Organization department, LocalDate today) {
        if (department.getId().equals(target.getOrganization().getId())) {
            return false;
        }
        userRoleRepository.findCurrentByUserId(target.getId(), today).stream()
            .filter(role -> role.getRoleType() == RoleType.EMPLOYEE)
            .filter(role -> !role.getOrganization().getId().equals(department.getId()))
            .filter(role -> rules.mayManage(role.getRoleType(), role.getOrganization().getId()))
            .forEach(role -> {
                role.setValidTo(lastDayOf(role, today));
                recorder.superseded(actor, role);
            });
        target.setOrganization(department);
        return true;
    }

    /**
     * The day an assignment stops applying when it is taken back today: yesterday, or the day before it was due
     * to start when it has not started yet, so the row can never be current and the date check still holds.
     */
    private static LocalDate lastDayOf(UserRole role, LocalDate today) {
        return today.isAfter(role.getValidFrom()) ? today.minusDays(1) : role.getValidFrom().minusDays(1);
    }

    private User caller() {
        long id = access.callerId();
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private User visible(long userId) {
        return userRepository.findByIdForUpdate(userId)
            .filter(found -> found.getAccountStatus().isListed())
            .filter(found -> access.readableOrganizations()
                .map(ids -> ids.contains(found.getOrganization().getId())).orElse(true))
            .orElseThrow(() -> new UserNotFoundException(userId));
    }
}

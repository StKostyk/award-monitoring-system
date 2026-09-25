package ua.edu.chnu.awards.delegation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.auth.security.AuthorizationRevoker;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.delegation.dto.DelegationListResponse;
import ua.edu.chnu.awards.delegation.dto.DelegationRequest;
import ua.edu.chnu.awards.delegation.dto.DelegationResponse;
import ua.edu.chnu.awards.delegation.entity.DelegationState;
import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.delegation.mapper.DelegationMapper;
import ua.edu.chnu.awards.delegation.repository.RoleDelegationRepository;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.service.UserNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Hands approval authority over for a bounded period and takes it back. Only a role the person holds
 * themselves can be lent, never one they borrowed, and the delegate receives the reading and approving
 * permissions of that role and nothing else.
 */
@Service
@RequiredArgsConstructor
public class DelegationService {

    static final String PERMISSION_MANAGE_ALL = "user:manage";

    private final RoleDelegationRepository delegationRepository;
    private final UserRepository userRepository;
    private final DelegationRules rules;
    private final AccessScope access;
    private final AuthorizationRevoker revoker;
    private final DelegationMapper mapper;
    private final DelegationRecorder recorder;
    private final Clock clock;

    /**
     * Lends an approval role to a colleague.
     *
     * @param request delegate, role, organisation, period and reason
     * @return the new delegation
     * @throws UserNotFoundException when the delegate is unknown or outside the caller's scope
     * @throws ApiProblemException   when the caller does not hold the role, the delegate cannot receive it,
     *                               the period is out of bounds or another delegation already covers part of
     *                               it
     */
    @Transactional
    public DelegationResponse create(DelegationRequest request) {
        LocalDate today = LocalDate.now(clock);
        rules.requirePeriod(request, today);
        Organization organization = rules.activeOrganization(request.organizationId());
        User delegator = caller();
        rules.requireHolder(request.role(), organization.getId());
        User delegate = rules.eligibleDelegate(request.delegateId(), delegator, organization, today);
        rules.requireFreePeriod(delegator.getId(), request, organization.getId());

        RoleDelegation delegation = RoleDelegation.builder()
            .delegator(delegator)
            .delegate(delegate)
            .roleType(request.role())
            .organization(organization)
            .validFrom(request.validFrom())
            .validTo(request.validTo())
            .reason(request.reason())
            .build();
        try {
            delegationRepository.saveAndFlush(delegation);
        } catch (DataIntegrityViolationException e) {
            throw rules.overlap(e);
        }
        recorder.created(delegation);
        return mapper.toResponse(delegation, today);
    }

    /**
     * The delegation page of the caller, or of somebody else for an administrator.
     *
     * @param state       show only delegations in this state, every state when null
     * @param delegatorId whose page to read; the caller's own when null
     * @return delegations given and received
     */
    @Transactional(readOnly = true)
    public DelegationListResponse list(DelegationState state, Long delegatorId) {
        long subject = delegatorId == null ? access.callerId() : delegatorId;
        LocalDate today = LocalDate.now(clock);
        return new DelegationListResponse(
            page(delegationRepository.findByDelegatorId(subject), state, today),
            page(delegationRepository.findByDelegateId(subject), state, today));
    }

    /**
     * Takes borrowed authority back before its last day; the delegate is signed out everywhere.
     *
     * @param delegationId which delegation to end
     * @throws DelegationNotFoundException when the delegation is unknown
     * @throws ApiProblemException         when it has already ended or the caller may not end it
     */
    @Transactional
    public void revoke(long delegationId) {
        RoleDelegation delegation = delegationRepository.findById(delegationId)
            .orElseThrow(() -> new DelegationNotFoundException(delegationId));
        User actor = caller();
        if (!mayRevoke(delegation, actor)) {
            throw new DelegationNotFoundException(delegationId);
        }
        DelegationState state = delegation.stateOn(LocalDate.now(clock));
        if (state == DelegationState.REVOKED || state == DelegationState.EXPIRED) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "delegation-not-active",
                "The delegation has already ended");
        }
        end(delegation, actor);
    }

    /**
     * Takes back what a role had lent, and the authority its holder had borrowed for an organisation they no
     * longer belong to, because the role itself has just been taken back.
     *
     * @param actor      who took the role back
     * @param assignment the role assignment that has just ended
     */
    @Transactional
    public void revokeForRole(User actor, UserRole assignment) {
        LocalDate today = LocalDate.now(clock);
        User holder = assignment.getUser();
        delegationRepository.findStandingByRole(holder.getId(), assignment.getRoleType(),
                assignment.getOrganization().getId(), today)
            .forEach(delegation -> end(delegation, actor));
        rules.borrowedOutsideMembership(holder, today).forEach(delegation -> end(delegation, actor));
    }

    private void end(RoleDelegation delegation, User actor) {
        delegation.setRevokedAt(Instant.now(clock));
        delegation.setRevokedBy(actor);
        recorder.revoked(actor, delegation);
        revoker.revokeAll(delegation.getDelegate());
    }

    private List<DelegationResponse> page(List<RoleDelegation> delegations, DelegationState state,
                                          LocalDate today) {
        return delegations.stream()
            .filter(delegation -> state == null || delegation.stateOn(today) == state)
            .map(delegation -> mapper.toResponse(delegation, today))
            .toList();
    }

    /**
     * Whether the caller may end the delegation: its delegator, an administrator, or somebody who could take
     * the delegator's role back. Anybody else is not told that it exists.
     */
    private boolean mayRevoke(RoleDelegation delegation, User actor) {
        return actor.getId().equals(delegation.getDelegator().getId())
            || access.has(PERMISSION_MANAGE_ALL)
            || access.canManage(delegation.getRoleType(), delegation.getOrganization().getId());
    }

    private User caller() {
        long id = access.callerId();
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }
}

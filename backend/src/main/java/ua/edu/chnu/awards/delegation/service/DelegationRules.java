package ua.edu.chnu.awards.delegation.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.delegation.dto.DelegationRequest;
import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.delegation.repository.RoleDelegationRepository;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;
import ua.edu.chnu.awards.user.service.UserNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * What a delegation has to satisfy before it is written: an approval role the caller holds themselves, a
 * colleague inside the organisation who can receive it, a period of at most 90 days, and no other standing
 * delegation of that role over the same days.
 */
@Component
@RequiredArgsConstructor
public class DelegationRules {

    /** The roles that carry approval authority and can therefore be lent. */
    public static final Set<RoleType> APPROVAL_ROLES = EnumSet.of(RoleType.FACULTY_SECRETARY, RoleType.DEAN,
        RoleType.RECTOR_SECRETARY, RoleType.RECTOR);

    private static final long MAX_DAYS = 90;

    private final RoleDelegationRepository delegationRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final OrganizationRepository organizationRepository;
    private final AccessScope access;

    /**
     * The organisation the role would be lent in.
     *
     * @param organizationId the organisation
     * @return the organisation when it exists and is active
     * @throws ApiProblemException otherwise
     */
    public Organization activeOrganization(long organizationId) {
        return organizationRepository.findById(organizationId)
            .filter(Organization::isActive)
            .orElseThrow(() -> new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "organisation-invalid",
                "Choose an active organisation"));
    }

    /**
     * Checks that the period begins no later than it ends, has not already passed and is short enough.
     *
     * @param request the delegation asked for
     * @param today   the current day
     * @throws ApiProblemException when the period is out of bounds
     */
    public void requirePeriod(DelegationRequest request, LocalDate today) {
        if (request.validTo().isBefore(request.validFrom()) || request.validTo().isBefore(today)
            || ChronoUnit.DAYS.between(request.validFrom(), request.validTo()) > MAX_DAYS) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "delegation-period",
                "A delegation ends no earlier than it begins, not in the past, and lasts at most " + MAX_DAYS
                    + " days");
        }
    }

    /**
     * Checks that the caller holds the approval role in the organisation themselves; a borrowed role is not
     * held and cannot be lent on.
     *
     * @param role           the role to lend
     * @param organizationId where it applies
     * @throws ApiProblemException when the caller does not hold it
     */
    public void requireHolder(RoleType role, long organizationId) {
        boolean held = access.heldScopes().stream()
            .anyMatch(scope -> scope.role() == role && scope.organizationId() == organizationId);
        if (!APPROVAL_ROLES.contains(role) || !held) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "delegation-not-holder",
                "You can delegate only an approval role you hold yourself in that organisation; borrowed "
                    + "authority cannot be passed on");
        }
    }

    /**
     * The colleague who may receive the authority.
     *
     * @param delegateId   who would receive it
     * @param delegator    who lends it
     * @param organization where the lent role applies
     * @param today        the current day
     * @return the delegate
     * @throws UserNotFoundException when the delegate is unknown or outside the caller's scope
     * @throws ApiProblemException   when the delegate cannot hold the borrowed authority
     */
    public User eligibleDelegate(long delegateId, User delegator, Organization organization, LocalDate today) {
        User delegate = userRepository.findById(delegateId)
            .filter(found -> found.getAccountStatus() != AccountStatus.PENDING)
            .filter(found -> access.readableOrganizations()
                .map(ids -> ids.contains(found.getOrganization().getId())).orElse(true))
            .orElseThrow(() -> new UserNotFoundException(delegateId));
        Set<Long> subtree = access.subtreeOf(organization.getId());
        boolean member = userRoleRepository.findCurrentByUserId(delegateId, today).stream()
            .anyMatch(role -> subtree.contains(role.getOrganization().getId()));
        if (delegate.getId().equals(delegator.getId()) || delegate.getAccountStatus() != AccountStatus.ACTIVE
            || !member) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "delegation-bad-delegate",
                "Choose an active colleague other than yourself who holds a role inside that organisation");
        }
        return delegate;
    }

    /**
     * Checks that the caller has not already lent the same role in the same organisation over those days.
     *
     * @param delegatorId    who lends it
     * @param request        the delegation asked for
     * @param organizationId where it applies
     * @throws ApiProblemException when another standing delegation overlaps
     */
    public void requireFreePeriod(long delegatorId, DelegationRequest request, long organizationId) {
        if (!delegationRepository.findOverlapping(delegatorId, request.role(), organizationId,
            request.validFrom(), request.validTo()).isEmpty()) {
            throw overlap(null);
        }
    }

    /**
     * Standing delegations a person has received for an organisation they no longer belong to: authority is
     * lent to a colleague inside the organisation, so losing the last role there ends what was lent.
     *
     * @param holder whose roles have just changed
     * @param today  the current day
     * @return delegations to take back
     */
    public List<RoleDelegation> borrowedOutsideMembership(User holder, LocalDate today) {
        List<RoleDelegation> received = delegationRepository.findStandingByDelegate(holder.getId(), today);
        if (received.isEmpty()) {
            return List.of();
        }
        Set<Long> organizations = userRoleRepository.findCurrentByUserId(holder.getId(), today).stream()
            .map(role -> role.getOrganization().getId())
            .collect(Collectors.toSet());
        return received.stream()
            .filter(delegation -> access.subtreeOf(delegation.getOrganization().getId()).stream()
                .noneMatch(organizations::contains))
            .toList();
    }

    /**
     * The refusal for a period already lent, also raised when the exclusion constraint catches two requests
     * arriving at once.
     *
     * @param cause what the database said, null when the check found it first
     * @return the problem to answer with
     */
    public ApiProblemException overlap(Throwable cause) {
        return new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "delegation-overlap",
            "You have already delegated this role in that organisation for part of the period", cause);
    }
}

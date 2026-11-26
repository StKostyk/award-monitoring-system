package ua.edu.chnu.awards.delegation.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.delegation.repository.RoleDelegationRepository;
import ua.edu.chnu.awards.user.entity.AccountStatus;

import lombok.RequiredArgsConstructor;

/**
 * Reads delegations for other modules.
 */
@Service
@RequiredArgsConstructor
public class DelegationLookup {

    private final RoleDelegationRepository delegations;

    /**
     * The delegations a person borrows authority through on the day.
     *
     * @param delegateId the delegate
     * @param day        the day
     * @return the delegations
     */
    public List<RoleDelegation> findCurrentByDelegateId(Long delegateId, LocalDate day) {
        return delegations.findCurrentByDelegateId(delegateId, day);
    }

    /**
     * Every delegation to a person.
     *
     * @param delegateId the delegate
     * @return the delegations
     */
    public List<RoleDelegation> findByDelegateId(Long delegateId) {
        return delegations.findByDelegateId(delegateId);
    }

    /**
     * Every delegation from a person.
     *
     * @param delegatorId the delegator
     * @return the delegations
     */
    public List<RoleDelegation> findByDelegatorId(Long delegatorId) {
        return delegations.findByDelegatorId(delegatorId);
    }

    /**
     * Whether somebody else borrows the role for one of the organisations on the day.
     *
     * @param role            the role
     * @param organizationIds the organisations
     * @param statuses        account statuses that count
     * @param excludedUserId  the user left out
     * @param day             the day
     * @return true when a delegate exists
     */
    public boolean existsInEffect(RoleType role, Collection<Long> organizationIds, Collection<AccountStatus> statuses,
                                  long excludedUserId, LocalDate day) {
        return delegations.existsInEffect(role, organizationIds, statuses, excludedUserId, day);
    }

    /**
     * The delegations of the role for the organisations in effect on the day.
     *
     * @param role            the role
     * @param organizationIds the organisations
     * @param statuses        account statuses that count
     * @param day             the day
     * @return the delegations
     */
    public List<RoleDelegation> findInEffect(RoleType role, Collection<Long> organizationIds,
                                             Collection<AccountStatus> statuses, LocalDate day) {
        return delegations.findInEffect(role, organizationIds, statuses, day);
    }
}

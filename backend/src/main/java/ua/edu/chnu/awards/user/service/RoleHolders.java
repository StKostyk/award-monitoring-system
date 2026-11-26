package ua.edu.chnu.awards.user.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reads and records role assignments for other modules.
 */
@Service
@RequiredArgsConstructor
public class RoleHolders {

    private final UserRoleRepository roles;

    /**
     * Saves a role assignment.
     *
     * @param role the assignment
     * @return the saved assignment
     */
    public UserRole save(UserRole role) {
        return roles.save(role);
    }

    /**
     * The roles a user holds on the day.
     *
     * @param userId the user
     * @param day    the day
     * @return the current assignments
     */
    public List<UserRole> findCurrentByUserId(Long userId, LocalDate day) {
        return roles.findCurrentByUserId(userId, day);
    }

    /**
     * Every role assignment of a user, past and present.
     *
     * @param userId the user
     * @return the assignments
     */
    public List<UserRole> findHistoryByUserId(Long userId) {
        return roles.findHistoryByUserId(userId);
    }

    /**
     * The addresses of everybody holding the role on the day.
     *
     * @param role the role
     * @param day  the day
     * @return the addresses
     */
    public List<String> findCurrentEmailsByRole(RoleType role, LocalDate day) {
        return roles.findCurrentEmailsByRole(role, day);
    }

    /**
     * Whether somebody else holds the role for one of the organisations on the day.
     *
     * @param role            the role
     * @param organizationIds the organisations
     * @param statuses        account statuses that count
     * @param excludedUserId  the user left out
     * @param day             the day
     * @return true when a holder exists
     */
    public boolean existsHolder(RoleType role, Collection<Long> organizationIds, Collection<AccountStatus> statuses,
                                long excludedUserId, LocalDate day) {
        return roles.existsHolder(role, organizationIds, statuses, excludedUserId, day);
    }

    /**
     * The holders of the role for the organisations on the day.
     *
     * @param role            the role
     * @param organizationIds the organisations
     * @param statuses        account statuses that count
     * @param day             the day
     * @return the holders
     */
    public List<User> findHolders(RoleType role, Collection<Long> organizationIds, Collection<AccountStatus> statuses,
                                  LocalDate day) {
        return roles.findHolders(role, organizationIds, statuses, day);
    }
}

package ua.edu.chnu.awards.award.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.delegation.repository.RoleDelegationRepository;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Whether somebody can review a request at a level: a person other than the award's owner who may sign in holds
 * the level's role today in the award's organisation or in an organisation above it, by assignment or by a
 * delegation in effect.
 */
@Component
@RequiredArgsConstructor
public class ReviewerAvailability {

    private static final Set<AccountStatus> CAN_REVIEW = Arrays.stream(AccountStatus.values())
        .filter(AccountStatus::canLogIn)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(AccountStatus.class)));

    private final UserRoleRepository roles;
    private final RoleDelegationRepository delegations;
    private final OrganizationTree tree;
    private final Clock clock;

    /**
     * Whether the level has a holder whose scope covers the organisation.
     *
     * @param level          the approval level
     * @param organizationId the award's organisation
     * @param ownerId        the award's owner, who never reviews their own award
     * @return true when at least one person may review at that level today
     */
    public boolean hasReviewer(ApprovalLevel level, long organizationId, long ownerId) {
        Set<Long> scopes = tree.ancestors(organizationId);
        if (scopes.isEmpty()) {
            scopes = Set.of(organizationId);
        }
        RoleType role = RoleType.valueOf(level.name());
        LocalDate today = LocalDate.now(clock);
        return roles.existsHolder(role, scopes, CAN_REVIEW, ownerId, today)
            || delegations.existsInEffect(role, scopes, CAN_REVIEW, ownerId, today);
    }
}

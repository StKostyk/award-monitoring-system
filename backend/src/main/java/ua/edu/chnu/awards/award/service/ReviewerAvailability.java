package ua.edu.chnu.awards.award.service;

import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.delegation.repository.RoleDelegationRepository;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Whether somebody can review a request at a level: a person other than the award's owner who may sign in holds
 * the level's role today in the award's organisation or in an organisation above it, by assignment or by a
 * delegation in effect that the owner did not lend.
 */
@Component
@RequiredArgsConstructor
public class ReviewerAvailability {

    static final long NOBODY = 0L;

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
        Set<Long> scopes = scopesOf(organizationId);
        RoleType role = level.role();
        LocalDate today = LocalDate.now(clock);
        return roles.existsHolder(role, scopes, CAN_REVIEW, ownerId, today)
            || delegations.existsInEffect(role, scopes, CAN_REVIEW, ownerId, today);
    }

    /**
     * Whether the person is the only one who could review at the level: the level has a holder for the
     * organisation, but nobody else.
     *
     * @param level          the approval level
     * @param organizationId the award's organisation
     * @param userId         the person, usually the submitter
     * @return true when only that person holds the level
     */
    public boolean isHeldOnlyBy(ApprovalLevel level, long organizationId, long userId) {
        return !hasReviewer(level, organizationId, userId) && hasReviewer(level, organizationId, NOBODY);
    }

    /**
     * Everybody who may review at the level today for the organisation, except the award's owner and submitter,
     * who count neither as reviewers nor as the source of a delegation; a person holding the role and also a
     * delegation of it is listed once, under the own role.
     *
     * @param level          the approval level
     * @param organizationId the award's organisation
     * @param ownerId        the award's owner
     * @param submitterId    who submitted the request
     * @return the reviewers by name
     */
    public List<Candidate> candidates(ApprovalLevel level, long organizationId, long ownerId, long submitterId) {
        Set<Long> scopes = scopesOf(organizationId);
        RoleType role = level.role();
        LocalDate today = LocalDate.now(clock);
        Set<Long> excluded = new HashSet<>(List.of(ownerId, submitterId));
        Map<Long, Candidate> found = new LinkedHashMap<>();
        for (User holder : roles.findHolders(role, scopes, CAN_REVIEW, today)) {
            if (!excluded.contains(holder.getId())) {
                found.put(holder.getId(), Candidate.of(holder, false));
            }
        }
        for (RoleDelegation delegation : delegations.findInEffect(role, scopes, CAN_REVIEW, today)) {
            User delegate = delegation.getDelegate();
            if (!excluded.contains(delegate.getId()) && !excluded.contains(delegation.getDelegator().getId())) {
                found.putIfAbsent(delegate.getId(), Candidate.of(delegate, true));
            }
        }
        Collator collator = Collator.getInstance(Locale.forLanguageTag("uk"));
        return found.values().stream()
            .sorted(Comparator.comparing(Candidate::name, collator))
            .toList();
    }

    /**
     * Whether the person may still review the award at the level or above it today.
     *
     * @param userId         the person, such as the reviewer holding a request
     * @param level          the lowest level that counts
     * @param organizationId the award's organisation
     * @param ownerId        the award's owner
     * @param submitterId    who submitted the request
     * @return true when a role or delegation of the person reaches the award
     */
    public boolean isEligible(long userId, ApprovalLevel level, long organizationId, long ownerId,
                              long submitterId) {
        return Arrays.stream(ApprovalLevel.values())
            .filter(candidateLevel -> candidateLevel.covers(level))
            .anyMatch(candidateLevel -> candidates(candidateLevel, organizationId, ownerId, submitterId).stream()
                .anyMatch(candidate -> candidate.id() == userId));
    }

    private Set<Long> scopesOf(long organizationId) {
        Set<Long> scopes = tree.ancestors(organizationId);
        return scopes.isEmpty() ? Set.of(organizationId) : scopes;
    }

    /**
     * A person who may review.
     *
     * @param id        user identifier
     * @param name      first and last name
     * @param email     address
     * @param delegated true when only a delegation lets them review
     */
    public record Candidate(long id, String name, String email, boolean delegated) {

        static Candidate of(User user, boolean delegated) {
            return new Candidate(user.getId(), user.getFullName(), user.getEmailAddress(), delegated);
        }
    }
}

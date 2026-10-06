package ua.edu.chnu.awards.award.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;

import lombok.RequiredArgsConstructor;

/**
 * Who may review a request: a caller holding a role, their own or borrowed, whose approval level is the request's
 * level or above and whose scope covers the award's organisation, who is neither the award's owner nor the
 * request's submitter, and whose borrowed role was not lent by either of them.
 */
@Component
@RequiredArgsConstructor
public class ReviewerRule {

    private static final Comparator<ReviewGrant> PREFERRED = Comparator.comparing(ReviewGrant::delegated)
        .thenComparing(ReviewGrant::level, Comparator.reverseOrder());

    private final AccessScope access;
    private final OrganizationTree tree;

    /**
     * The caller's approval authority, own roles first.
     *
     * @return one grant per approval role scope, empty for a caller who approves nothing
     */
    public List<ReviewGrant> callerGrants() {
        Stream<ReviewGrant> held = access.heldScopes().stream()
            .flatMap(scope -> ApprovalLevel.of(scope.role()).stream()
                .map(level -> new ReviewGrant(level, scope.organizationId(), null)));
        Stream<ReviewGrant> borrowed = access.delegations().stream()
            .flatMap(scope -> ApprovalLevel.of(scope.role()).stream()
                .map(level -> new ReviewGrant(level, scope.organizationId(), scope.delegatorId())));
        return Stream.concat(held, borrowed).distinct().toList();
    }

    /**
     * The caller's grants that reach the award of a request, at any level.
     *
     * @param request the request
     * @return grants, empty when the caller owns the award or submitted it
     */
    public List<ReviewGrant> grantsOn(AwardRequest request) {
        Award award = request.getAward();
        long callerId = access.callerId();
        long ownerId = award.getOwner().getId();
        long submitterId = request.getSubmitter().getId();
        if (callerId == ownerId || callerId == submitterId) {
            return List.of();
        }
        long organizationId = award.getOrganization().getId();
        return callerGrants().stream()
            .filter(grant -> tree.covers(grant.organizationId(), organizationId))
            .filter(grant -> !grant.delegated()
                || grant.delegatorId() != ownerId && grant.delegatorId() != submitterId)
            .toList();
    }

    /**
     * The authority the caller reviews the request under: an own role before a borrowed one, the highest first.
     *
     * @param request the request
     * @return the grant, empty when the caller may not review it
     */
    public Optional<ReviewGrant> grant(AwardRequest request) {
        return grantsOn(request).stream()
            .filter(grant -> grant.level().covers(request.getCurrentLevel()))
            .min(PREFERRED);
    }

    /**
     * Who lent the role the caller reviews the request under.
     *
     * @param request the request
     * @return the delegator, null for an own role or when the caller may not review it
     */
    public Long delegatorId(AwardRequest request) {
        return grant(request).map(ReviewGrant::delegatorId).orElse(null);
    }

    /**
     * The highest level the caller reviews the request's award at.
     *
     * @param request the request
     * @return the level, empty when no role reaches it
     */
    public Optional<ApprovalLevel> highestLevel(AwardRequest request) {
        return grantsOn(request).stream().map(ReviewGrant::level).max(Comparator.naturalOrder());
    }
}

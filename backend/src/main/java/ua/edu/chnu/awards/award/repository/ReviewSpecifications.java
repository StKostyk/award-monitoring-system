package ua.edu.chnu.awards.award.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.ReviewQuery;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RequestStatus;

/**
 * Query parts of the reviewer queue.
 */
@Component
public class ReviewSpecifications {

    private static final Set<RequestStatus> OPEN = EnumSet.of(RequestStatus.SUBMITTED, RequestStatus.IN_REVIEW,
        RequestStatus.ESCALATED);
    private static final String AWARD = "award";
    private static final String OWNER = "owner";
    private static final String SUBMITTER = "submitter";
    private static final String ID = "id";

    /**
     * Open requests inside one of the reaches, neither owned nor submitted by the caller, matching the filters.
     *
     * @param callerId      the reviewer
     * @param reaches       where the caller reviews; at least one
     * @param query         the filters
     * @param organizations the organisations of the {@code organizationId} filter, null without it
     * @param now           the current time, for the overdue filter
     * @return specification
     */
    public Specification<AwardRequest> queue(long callerId, List<Reach> reaches, ReviewQuery query,
                                             Set<Long> organizations, Instant now) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(OPEN));
            predicates.add(builder.notEqual(owner(root), callerId));
            predicates.add(builder.notEqual(root.get(SUBMITTER).get(ID), callerId));
            predicates.add(builder.or(reaches.stream().map(reach -> reach(root, builder, reach))
                .toArray(Predicate[]::new)));
            if (organizations != null) {
                predicates.add(organization(root).in(organizations));
            }
            if (query.assigned() != null) {
                predicates.add(assigned(root, builder, query.assigned(), callerId));
            }
            if (query.overdue()) {
                predicates.add(builder.lessThan(root.get("deadline"), now));
            }
            if (query.noticed()) {
                predicates.add(builder.isNotNull(root.get("overdueNoticedAt")));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate reach(Root<AwardRequest> root, CriteriaBuilder builder, Reach reach) {
        List<Predicate> parts = new ArrayList<>();
        parts.add(builder.equal(root.get("currentLevel"), reach.level()));
        parts.add(organization(root).in(reach.organizationIds()));
        if (reach.delegatorId() != null) {
            parts.add(builder.notEqual(owner(root), reach.delegatorId()));
            parts.add(builder.notEqual(root.get(SUBMITTER).get(ID), reach.delegatorId()));
        }
        return builder.and(parts.toArray(Predicate[]::new));
    }

    private static Path<Long> owner(Root<AwardRequest> root) {
        return root.get(AWARD).get(OWNER).get(ID);
    }

    private static Path<Long> organization(Root<AwardRequest> root) {
        return root.get(AWARD).get("organization").get(ID);
    }

    private static Predicate assigned(Root<AwardRequest> root, CriteriaBuilder builder,
                                      ReviewQuery.Assignment assigned, long callerId) {
        Path<Object> reviewer = root.get("currentReviewer");
        return switch (assigned) {
            case ME -> builder.equal(reviewer.get(ID), callerId);
            case UNASSIGNED -> builder.isNull(reviewer);
            case OTHERS -> builder.and(builder.isNotNull(reviewer), builder.notEqual(reviewer.get(ID), callerId));
        };
    }

    /**
     * Requests of one level in a set of organisations.
     *
     * @param level           the level the requests wait at
     * @param organizationIds the organisations of the scope
     * @param delegatorId     who lent the role, whose own awards and submissions are left out; null for an own
     *                        role
     */
    public record Reach(ApprovalLevel level, Set<Long> organizationIds, Long delegatorId) {

        /**
         * Keeps a copy of the organisations.
         */
        public Reach {
            organizationIds = Set.copyOf(organizationIds);
        }
    }
}

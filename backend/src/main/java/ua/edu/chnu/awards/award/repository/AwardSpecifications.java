package ua.edu.chnu.awards.award.repository;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.AwardQuery;
import ua.edu.chnu.awards.award.entity.Award;

/**
 * Query parts of the award lists.
 */
@Component
public class AwardSpecifications {

    private static final String AWARD_DATE = "awardDate";

    /**
     * Awards of one owner matching the filters.
     *
     * @param ownerId the owner
     * @param query   the filters
     * @return specification
     */
    public Specification<Award> ownedBy(long ownerId, AwardQuery query) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("owner").get("id"), ownerId));
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }
            if (query.categoryId() != null) {
                predicates.add(builder.equal(root.get("category").get("id"), query.categoryId()));
            }
            if (query.dateFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get(AWARD_DATE), query.dateFrom()));
            }
            if (query.dateTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get(AWARD_DATE), query.dateTo()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}

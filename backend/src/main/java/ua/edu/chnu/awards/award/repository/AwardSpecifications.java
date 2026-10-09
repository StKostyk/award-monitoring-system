package ua.edu.chnu.awards.award.repository;

import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.AchievementQuery;
import ua.edu.chnu.awards.award.dto.AwardQuery;
import ua.edu.chnu.awards.award.dto.RecipientType;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.AwardVisibility;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;

/**
 * Query parts of the award lists.
 */
@Component
public class AwardSpecifications {

    private static final String AWARD_DATE = "awardDate";
    private static final String RECIPIENT_UNIT = "recipientOrganizationId";

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

    /**
     * Approved awards shared at one of the given visibilities, and approved unit awards, matching the filters.
     * Personal awards of a deleted account are left out; a faculty includes its departments.
     *
     * @param visibilities the visibilities of personal awards to include
     * @param query        the filters
     * @return specification
     */
    public Specification<Award> shared(Collection<AwardVisibility> visibilities, AchievementQuery query) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("status"), AwardStatus.APPROVED));
            predicates.add(builder.or(builder.isNotNull(root.get(RECIPIENT_UNIT)), builder.and(
                root.get("visibility").in(visibilities),
                builder.notEqual(root.get("owner").get("accountStatus"), AccountStatus.DELETED))));
            if (query.unit() != null) {
                Join<Award, Organization> unit = root.join("organization");
                Join<Organization, Organization> parent = unit.join("parent", JoinType.LEFT);
                predicates.add(builder.or(builder.equal(unit.get("id"), query.unit()),
                    builder.equal(parent.get("id"), query.unit())));
            }
            if (query.year() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get(AWARD_DATE), Year.of(query.year()).atDay(1)));
                predicates.add(builder.lessThan(root.get(AWARD_DATE), Year.of(query.year() + 1).atDay(1)));
            }
            if (query.level() != null) {
                predicates.add(builder.equal(root.get("category").get("level"), query.level()));
            }
            if (query.recipient() != null) {
                predicates.add(query.recipient() == RecipientType.UNIT ? builder.isNotNull(root.get(RECIPIENT_UNIT))
                    : builder.isNull(root.get(RECIPIENT_UNIT)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}

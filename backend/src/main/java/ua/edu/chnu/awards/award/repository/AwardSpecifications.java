package ua.edu.chnu.awards.award.repository;

import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.AchievementQuery;
import ua.edu.chnu.awards.award.dto.AwardQuery;
import ua.edu.chnu.awards.award.dto.RecipientType;
import ua.edu.chnu.awards.award.dto.SearchTerms;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.AwardVisibility;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.user.entity.AccountStatus;

/**
 * Query parts of the award lists.
 */
@Component
public class AwardSpecifications {

    private static final String AWARD_DATE = "awardDate";
    private static final String OWNER = "owner";
    private static final String RECIPIENT_UNIT = "recipientOrganizationId";
    private static final String SEARCH_VECTOR = "searchVector";
    private static final String SEARCH_TEXT = "searchText";

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
            predicates.add(builder.equal(root.get(OWNER).get("id"), ownerId));
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
     * Personal awards of a deleted account are left out; a faculty includes its departments. A person narrows the
     * list to the personal awards of that account while it is active.
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
                builder.notEqual(root.get(OWNER).get("accountStatus"), AccountStatus.DELETED))));
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
            if (query.person() != null) {
                predicates.add(builder.isNull(root.get(RECIPIENT_UNIT)));
                predicates.add(builder.equal(root.get(OWNER).get("id"), query.person()));
                predicates.add(builder.equal(root.get(OWNER).get("accountStatus"), AccountStatus.ACTIVE));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Awards whose texts hold every search word as a prefix, or, when asked, whose owner's name holds the search
     * text, by full-text rank and then in the given order. The count query of a page carries no order.
     *
     * @param search the search terms, at least one word
     * @param ties   the list order of equally relevant awards
     * @param byName whether the owner's name of a personal award is searched as well
     * @return specification
     */
    public Specification<Award> matching(SearchTerms search, Sort ties, boolean byName) {
        return (root, criteria, builder) -> {
            Expression<Object> vector = root.get(SEARCH_VECTOR);
            Expression<String> tsquery = builder.literal(search.tsquery());
            ordered(criteria, builder.function(AwardSearchFunctions.RANK, Double.class, vector, tsquery), root,
                builder, ties);
            Predicate fullText = holds(builder, AwardSearchFunctions.FULL_TEXT, vector, tsquery);
            if (!byName) {
                return fullText;
            }
            return builder.or(fullText, builder.and(builder.isNull(root.get(RECIPIENT_UNIT)), holds(builder,
                AwardSearchFunctions.OWNER_NAMED, root.get(OWNER).get("id"), builder.literal(search.namePattern()))));
        };
    }

    /**
     * Awards whose titles and awarding organisation are similar to the search words, most similar first and then
     * in the given order; the typo fallback when no award matches the words.
     *
     * @param search the search terms, at least one word
     * @param ties   the list order of equally similar awards
     * @return specification
     */
    public Specification<Award> similar(SearchTerms search, Sort ties) {
        return (root, criteria, builder) -> {
            Expression<String> text = root.get(SEARCH_TEXT);
            Expression<String> words = builder.literal(search.trigram());
            ordered(criteria, builder.function(AwardSearchFunctions.SIMILARITY, Double.class, text, words), root,
                builder, ties);
            return holds(builder, AwardSearchFunctions.TYPO, text, words);
        };
    }

    private static Predicate holds(CriteriaBuilder builder, String function, Expression<?>... arguments) {
        return builder.isTrue(builder.function(function, Boolean.class, arguments));
    }

    private static void ordered(CriteriaQuery<?> criteria, Expression<Double> relevance, Root<Award> root,
                                CriteriaBuilder builder, Sort ties) {
        if (criteria.getResultType() == Long.class || criteria.getResultType() == long.class) {
            return;
        }
        List<Order> orders = new ArrayList<>();
        orders.add(builder.desc(relevance));
        ties.forEach(tie -> orders.add(tie.isAscending() ? builder.asc(root.get(tie.getProperty()))
            : builder.desc(root.get(tie.getProperty()))));
        criteria.orderBy(orders);
    }
}

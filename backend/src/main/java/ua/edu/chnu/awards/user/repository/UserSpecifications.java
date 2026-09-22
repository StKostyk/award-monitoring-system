package ua.edu.chnu.awards.user.repository;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;

/**
 * Query parts of the user directory.
 */
@Component
public class UserSpecifications {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final char ESCAPE = '\\';

    /**
     * Members of the given organisations.
     *
     * @param organizationIds organisation ids; an empty set matches nobody
     * @return specification
     */
    public Specification<User> inOrganizations(Set<Long> organizationIds) {
        return (root, query, builder) -> organizationIds.isEmpty()
            ? builder.disjunction()
            : root.get("organization").get("id").in(organizationIds);
    }

    /**
     * Holders of the role on the given day.
     *
     * @param role the role
     * @param day  the day the assignment must be valid on
     * @return specification
     */
    public Specification<User> holdingRole(RoleType role, LocalDate day) {
        return (root, query, builder) -> {
            Subquery<Long> assignments = query.subquery(Long.class);
            Root<UserRole> assignment = assignments.from(UserRole.class);
            assignments.select(assignment.get("id")).where(
                builder.equal(assignment.get("user"), root),
                builder.equal(assignment.get("roleType"), role),
                builder.lessThanOrEqualTo(assignment.get("validFrom"), day),
                builder.or(assignment.get("validTo").isNull(),
                    builder.greaterThanOrEqualTo(assignment.get("validTo"), day)));
            return builder.exists(assignments);
        };
    }

    /**
     * Users who have never been granted any role.
     *
     * @return specification
     */
    public Specification<User> neverConfirmed() {
        return (root, query, builder) -> {
            Subquery<Long> assignments = query.subquery(Long.class);
            Root<UserRole> assignment = assignments.from(UserRole.class);
            assignments.select(assignment.get("id")).where(builder.equal(assignment.get("user"), root));
            return builder.not(builder.exists(assignments));
        };
    }

    /**
     * Accounts in the status; without one, everything but unverified and deleted accounts.
     *
     * @param status the status, or null
     * @return specification
     */
    public Specification<User> withStatus(AccountStatus status) {
        return (root, query, builder) -> {
            if (status == null) {
                return root.get("accountStatus").in(AccountStatus.PENDING, AccountStatus.DELETED).not();
            }
            Predicate wanted = builder.equal(root.get("accountStatus"), status);
            return status == AccountStatus.PENDING ? builder.disjunction() : wanted;
        };
    }

    /**
     * Name or address containing the fragment, case-insensitively; short fragments match everything.
     *
     * @param fragment typed text
     * @return specification
     */
    public Specification<User> matching(String fragment) {
        String needle = fragment == null ? "" : fragment.trim().toLowerCase(Locale.ROOT);
        if (needle.length() < MIN_QUERY_LENGTH) {
            return null;
        }
        String pattern = "%" + needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, builder) -> builder.or(
            builder.like(builder.lower(root.get("firstName")), pattern, ESCAPE),
            builder.like(builder.lower(root.get("lastName")), pattern, ESCAPE),
            builder.like(builder.lower(builder.concat(builder.concat(root.get("firstName"), " "),
                root.get("lastName"))), pattern, ESCAPE),
            builder.like(builder.lower(root.get("emailAddress")), pattern, ESCAPE));
    }
}

package ua.edu.chnu.awards.user.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ua.edu.chnu.awards.user.entity.User;

/**
 * Access to {@link User} rows.
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /**
     * The JPQL selection of people without an account.
     */
    String WITHOUT_ACCOUNT = """
        select u from User u
        where (u.accountStatus = ua.edu.chnu.awards.user.entity.AccountStatus.UNCLAIMED
               or u.accountStatus = ua.edu.chnu.awards.user.entity.AccountStatus.PENDING and u.addedById is not null)
        """;

    Optional<User> findByEmailAddressIgnoreCase(String emailAddress);

    boolean existsByEmailAddressIgnoreCase(String emailAddress);

    /**
     * Stores the time of a sign-in without touching the optimistic-lock version, so two sign-ins of one person
     * at the same moment do not refuse each other.
     *
     * @param id the user
     * @param at when they signed in
     * @return rows changed
     */
    @Modifying
    @Query("update User u set u.lastLoginAt = :at where u.id = :id")
    int recordLogin(@Param("id") Long id, @Param("at") Instant at);

    /**
     * The user with the row locked until the transaction ends, so two callers changing the same person's roles
     * are serialised and the check for an assignment that already covers the period cannot be overtaken.
     *
     * @param id the user
     * @return the user
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * People without an account of one department: added by a faculty secretary or dean and not yet verified.
     *
     * @param organizationId the department
     * @return the people
     */
    @Query(WITHOUT_ACCOUNT + " and u.organization.id = :organizationId")
    List<User> findWithoutAccount(@Param("organizationId") Long organizationId);

    /**
     * A page of people without an account in the departments, matching a fragment of the name or address.
     *
     * @param organizationIds the departments
     * @param pattern         a lower-case LIKE pattern, {@code %} for all
     * @param pageable        page and sort
     * @return the page
     */
    @Query(WITHOUT_ACCOUNT + """
         and u.organization.id in :organizationIds
         and (lower(u.firstName) like :pattern or lower(u.lastName) like :pattern
              or lower(u.emailAddress) like :pattern)""")
    Page<User> findWithoutAccount(@Param("organizationIds") Collection<Long> organizationIds,
                                  @Param("pattern") String pattern, Pageable pageable);
}

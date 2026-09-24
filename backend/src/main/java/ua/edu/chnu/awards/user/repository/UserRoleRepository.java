package ua.edu.chnu.awards.user.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.UserRole;

/**
 * Access to {@link UserRole} assignments.
 */
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /**
     * Roles of a user that are in effect on the given day, with their organisation loaded.
     *
     * @param userId the user
     * @param day    the day the roles must be valid on
     * @return matching assignments
     */
    @Query("""
        select r from UserRole r join fetch r.organization
        where r.user.id = :userId
          and r.validFrom <= :day
          and (r.validTo is null or r.validTo >= :day)
        """)
    List<UserRole> findCurrentByUserId(@Param("userId") Long userId, @Param("day") LocalDate day);

    /**
     * Every assignment of a user, newest first, with the organisation loaded.
     *
     * @param userId the user
     * @return assignments past, present and future
     */
    @Query("""
        select r from UserRole r join fetch r.organization
        where r.user.id = :userId
        order by r.validFrom desc, r.id desc
        """)
    List<UserRole> findHistoryByUserId(@Param("userId") Long userId);

    /**
     * Current assignments of several users at once, with the organisation loaded.
     *
     * @param userIds the users
     * @param day     the day the roles must be valid on
     * @return matching assignments
     */
    @Query("""
        select r from UserRole r join fetch r.organization
        where r.user.id in :userIds
          and r.validFrom <= :day
          and (r.validTo is null or r.validTo >= :day)
        """)
    List<UserRole> findCurrentByUserIds(@Param("userIds") Collection<Long> userIds, @Param("day") LocalDate day);

    /**
     * Users among the given ones who have ever held a role.
     *
     * @param userIds the users
     * @return ids with at least one assignment
     */
    @Query("select distinct r.user.id from UserRole r where r.user.id in :userIds")
    Set<Long> findEverAssignedUserIds(@Param("userIds") Collection<Long> userIds);

    /**
     * One assignment of a user, with the organisation loaded.
     *
     * @param id     the assignment
     * @param userId the user it must belong to
     * @return the assignment when it belongs to that user
     */
    @Query("select r from UserRole r join fetch r.organization where r.id = :id and r.user.id = :userId")
    Optional<UserRole> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Whether the user has ever been granted a role.
     *
     * @param userId the user
     * @return true when at least one assignment exists
     */
    boolean existsByUserId(Long userId);

    /**
     * Assignments of the same role in the same organisation whose validity overlaps the given period.
     *
     * @param userId         the user
     * @param role           the role
     * @param organizationId where the role would apply
     * @param from           first day of the new period
     * @param to             last day of the new period; {@link LocalDate#MAX} for an open-ended one
     * @return overlapping assignments
     */
    @Query("""
        select r from UserRole r
        where r.user.id = :userId
          and r.roleType = :role
          and r.organization.id = :organizationId
          and r.validFrom <= :to
          and (r.validTo is null or r.validTo >= :from)
        """)
    List<UserRole> findOverlapping(@Param("userId") Long userId, @Param("role") RoleType role,
                                   @Param("organizationId") Long organizationId, @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    /**
     * Addresses of the users holding a role on the given day.
     *
     * @param role the role
     * @param day  the day the assignment must be valid on
     * @return distinct email addresses
     */
    @Query("""
        select distinct r.user.emailAddress from UserRole r
        where r.roleType = :role
          and r.validFrom <= :day
          and (r.validTo is null or r.validTo >= :day)
        """)
    List<String> findCurrentEmailsByRole(@Param("role") RoleType role, @Param("day") LocalDate day);
}

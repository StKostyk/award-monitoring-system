package ua.edu.chnu.awards.user.repository;

import java.time.LocalDate;
import java.util.List;

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

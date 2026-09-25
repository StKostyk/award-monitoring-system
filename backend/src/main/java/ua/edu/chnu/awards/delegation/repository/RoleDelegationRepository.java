package ua.edu.chnu.awards.delegation.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * Access to {@link RoleDelegation} rows.
 */
public interface RoleDelegationRepository extends JpaRepository<RoleDelegation, Long> {

    /**
     * Delegations a user has received that are in effect on the given day and whose delegator still holds the
     * lent role: borrowed authority never outlives the role it came from, however that role ended.
     *
     * @param delegateId who borrowed the authority
     * @param day        the day the delegation must cover
     * @return matching delegations with their organisation and delegator loaded
     */
    @Query("""
        select d from RoleDelegation d join fetch d.organization join fetch d.delegator
        where d.delegate.id = :delegateId
          and d.revokedAt is null
          and d.validFrom <= :day
          and d.validTo >= :day
          and exists (
            select 1 from UserRole r
            where r.user.id = d.delegator.id
              and r.roleType = d.roleType
              and r.organization.id = d.organization.id
              and r.validFrom <= :day
              and (r.validTo is null or r.validTo >= :day)
          )
        """)
    List<RoleDelegation> findCurrentByDelegateId(@Param("delegateId") Long delegateId,
                                                 @Param("day") LocalDate day);

    /**
     * Every delegation a user has given, newest first.
     *
     * @param delegatorId who lent the authority
     * @return delegations past, present and future
     */
    @Query("""
        select d from RoleDelegation d join fetch d.organization join fetch d.delegate join fetch d.delegator
        where d.delegator.id = :delegatorId
        order by d.validFrom desc, d.id desc
        """)
    List<RoleDelegation> findByDelegatorId(@Param("delegatorId") Long delegatorId);

    /**
     * Every delegation a user has received, newest first.
     *
     * @param delegateId who borrowed the authority
     * @return delegations past, present and future
     */
    @Query("""
        select d from RoleDelegation d join fetch d.organization join fetch d.delegate join fetch d.delegator
        where d.delegate.id = :delegateId
        order by d.validFrom desc, d.id desc
        """)
    List<RoleDelegation> findByDelegateId(@Param("delegateId") Long delegateId);

    /**
     * Standing delegations of the same role in the same organisation by the same person whose period overlaps
     * the given one.
     *
     * @param delegatorId    who lends the authority
     * @param role           the role lent
     * @param organizationId where it applies
     * @param from           first day of the new period
     * @param to             last day of the new period
     * @return overlapping delegations that have not been taken back
     */
    @Query("""
        select d from RoleDelegation d
        where d.delegator.id = :delegatorId
          and d.roleType = :role
          and d.organization.id = :organizationId
          and d.revokedAt is null
          and d.validFrom <= :to
          and d.validTo >= :from
        """)
    List<RoleDelegation> findOverlapping(@Param("delegatorId") Long delegatorId, @Param("role") RoleType role,
                                         @Param("organizationId") Long organizationId,
                                         @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Standing delegations of one role of one person that have not ended yet, used when the role itself is
     * taken back.
     *
     * @param delegatorId    who lent the authority
     * @param role           the role lent
     * @param organizationId where it applies
     * @param day            delegations ending before this day are left alone
     * @return delegations to take back with the role
     */
    @Query("""
        select d from RoleDelegation d join fetch d.organization join fetch d.delegate join fetch d.delegator
        where d.delegator.id = :delegatorId
          and d.roleType = :role
          and d.organization.id = :organizationId
          and d.revokedAt is null
          and d.validTo >= :day
        """)
    List<RoleDelegation> findStandingByRole(@Param("delegatorId") Long delegatorId,
                                            @Param("role") RoleType role,
                                            @Param("organizationId") Long organizationId,
                                            @Param("day") LocalDate day);

    /**
     * Standing delegations a user has received that have not ended yet, whatever their delegator's role does.
     *
     * @param delegateId who borrowed the authority
     * @param day        delegations ending before this day are left alone
     * @return delegations to reconsider when the delegate's own roles change
     */
    @Query("""
        select d from RoleDelegation d join fetch d.organization join fetch d.delegate join fetch d.delegator
        where d.delegate.id = :delegateId
          and d.revokedAt is null
          and d.validTo >= :day
        """)
    List<RoleDelegation> findStandingByDelegate(@Param("delegateId") Long delegateId,
                                                @Param("day") LocalDate day);
}

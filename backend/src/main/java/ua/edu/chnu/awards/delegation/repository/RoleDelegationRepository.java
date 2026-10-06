package ua.edu.chnu.awards.delegation.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.user.entity.AccountStatus;
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
     * One delegation with its row locked until the transaction ends, so two revocations arriving at once are
     * applied one after the other and the second finds it already revoked.
     *
     * @param id the delegation
     * @return the delegation
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from RoleDelegation d where d.id = :id")
    Optional<RoleDelegation> findByIdForUpdate(@Param("id") Long id);

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
     * @return delegations to take back with the role, locked so a concurrent revocation cannot end them twice
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select d from RoleDelegation d
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
     * @return delegations to reconsider when the delegate's own roles change, locked so a concurrent revocation
     *         cannot end them twice
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select d from RoleDelegation d
        where d.delegate.id = :delegateId
          and d.revokedAt is null
          and d.validTo >= :day
        """)
    List<RoleDelegation> findStandingByDelegate(@Param("delegateId") Long delegateId,
                                                @Param("day") LocalDate day);

    /**
     * Whether a delegation of a role in one of the given organisations is in effect on the given day for a
     * delegate other than the given user, with an account in one of the given states, while its delegator still
     * holds the role.
     *
     * @param role            the role lent
     * @param organizationIds organisations whose delegations count
     * @param statuses        account states of the delegate that count
     * @param excludedUserId  the user who does not count, such as the owner of the award under review; neither
     *                        as a delegate nor through a delegate acting for them
     * @param day             the day the delegation must cover
     * @return true when at least one delegation matches
     */
    @Query("""
        select count(d) > 0 from RoleDelegation d
        where d.roleType = :role
          and d.organization.id in :organizationIds
          and d.delegate.accountStatus in :statuses
          and d.delegate.id <> :excludedUserId
          and d.delegator.id <> :excludedUserId
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
    boolean existsInEffect(@Param("role") RoleType role,
                           @Param("organizationIds") Collection<Long> organizationIds,
                           @Param("statuses") Collection<AccountStatus> statuses,
                           @Param("excludedUserId") long excludedUserId, @Param("day") LocalDate day);

    /**
     * Delegations of a role in one of the given organisations in effect on the given day for a delegate with an
     * account in one of the given states, while the delegator still holds the role.
     *
     * @param role            the role lent
     * @param organizationIds organisations whose delegations count
     * @param statuses        account states of the delegate that count
     * @param day             the day the delegation must cover
     * @return matching delegations with their delegate loaded
     */
    @Query("""
        select d from RoleDelegation d join fetch d.delegate
        where d.roleType = :role
          and d.organization.id in :organizationIds
          and d.delegate.accountStatus in :statuses
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
    List<RoleDelegation> findInEffect(@Param("role") RoleType role,
                                      @Param("organizationIds") Collection<Long> organizationIds,
                                      @Param("statuses") Collection<AccountStatus> statuses,
                                      @Param("day") LocalDate day);
}

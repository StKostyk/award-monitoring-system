package ua.edu.chnu.awards.delegation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.support.AbstractJpaSliceTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;

class RoleDelegationRepositoryIT extends AbstractJpaSliceTest {

    @Autowired
    private RoleDelegationRepository delegationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final LocalDate today = LocalDate.now();
    private Organization faculty;
    private User dean;
    private User secretary;

    @BeforeEach
    void seed() {
        faculty = entityManager.find(Organization.class, TestUsers.FMI_FACULTY_ID);
        Organization department = entityManager.find(Organization.class, TestUsers.DAI_DEPARTMENT_ID);
        dean = entityManager.persist(TestUsers.user("deleg.dean@chnu.edu.ua", faculty));
        secretary = entityManager.persist(TestUsers.user("deleg.secretary@chnu.edu.ua", department));
        entityManager.persist(TestUsers.role(dean, RoleType.DEAN, faculty, today.minusYears(1), null));
        entityManager.flush();
    }

    @Test
    void ac3_1_theDatabaseRefusesAPeriodLongerThanNinetyDays() {
        RoleDelegation tooLong = delegation(today, today.plusDays(91));

        assertThatThrownBy(() -> {
            entityManager.persist(tooLong);
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class).hasMessageContaining("ck_role_delegations_dates");
    }

    @Test
    void ac3_1_theDatabaseRefusesLendingToOneself() {
        RoleDelegation toSelf = delegation(today, today.plusDays(3));
        toSelf.setDelegate(dean);

        assertThatThrownBy(() -> {
            entityManager.persist(toSelf);
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class).hasMessageContaining("ck_role_delegations_parties");
    }

    @Test
    void ac3_1_theDatabaseRefusesARoleThatCarriesNoApprovalAuthority() {
        RoleDelegation employee = delegation(today, today.plusDays(3));
        employee.setRoleType(RoleType.EMPLOYEE);

        assertThatThrownBy(() -> {
            entityManager.persist(employee);
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class).hasMessageContaining("ck_role_delegations_type");
    }

    @Test
    void ac3_2_aDelegationIsUnreadOnceItsDelegatorNoLongerHoldsTheRole() {
        entityManager.persist(delegation(today, today.plusDays(5)));
        entityManager.flush();
        assertThat(delegationRepository.findCurrentByDelegateId(secretary.getId(), today)).hasSize(1);

        entityManager.getEntityManager()
            .createQuery("update UserRole r set r.validTo = :day where r.user.id = :id")
            .setParameter("day", today.minusDays(1))
            .setParameter("id", dean.getId())
            .executeUpdate();

        assertThat(delegationRepository.findCurrentByDelegateId(secretary.getId(), today)).isEmpty();
    }

    @Test
    void ac3_2_ac3_3_onlyTheDelegationsCoveringTodayAreRead() {
        entityManager.persist(delegation(today.minusDays(1), today.plusDays(5)));
        entityManager.persist(delegation(today.plusDays(10), today.plusDays(20)));
        RoleDelegation over = delegation(today.minusDays(10), today.minusDays(2));
        entityManager.persist(over);
        entityManager.flush();

        assertThat(delegationRepository.findCurrentByDelegateId(secretary.getId(), today)).hasSize(1);
        assertThat(delegationRepository.findByDelegateId(secretary.getId())).hasSize(3);
        assertThat(delegationRepository.findByDelegatorId(dean.getId())).hasSize(3);
    }

    @Test
    void ac3_4_arevokedDelegationIsNeitherCurrentNorOverlapping() {
        RoleDelegation revoked = delegation(today, today.plusDays(5));
        revoked.setRevokedAt(Instant.now());
        revoked.setRevokedBy(dean);
        entityManager.persist(revoked);
        entityManager.flush();

        assertThat(delegationRepository.findCurrentByDelegateId(secretary.getId(), today)).isEmpty();
        assertThat(delegationRepository.findOverlapping(dean.getId(), RoleType.DEAN, faculty.getId(), today,
            today.plusDays(5))).isEmpty();
        assertThat(delegationRepository.findStandingByRole(dean.getId(), RoleType.DEAN, faculty.getId(),
            today)).isEmpty();
    }

    @Test
    void ac3_1_theDatabaseRefusesTwoStandingDelegationsOfOneRoleOverTheSameDays() {
        entityManager.persist(delegation(today, today.plusDays(10)));
        entityManager.flush();
        RoleDelegation overlapping = delegation(today.plusDays(5), today.plusDays(15));
        overlapping.setDelegate(entityManager.persist(
            TestUsers.user("deleg.other@chnu.edu.ua", faculty)));

        assertThatThrownBy(() -> {
            entityManager.persist(overlapping);
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("uk_role_delegations_standing");
    }

    @Test
    void ac3_1_overlapIsJudgedOnTheWholePeriod() {
        entityManager.persist(delegation(today.plusDays(5), today.plusDays(10)));
        entityManager.flush();

        assertThat(delegationRepository.findOverlapping(dean.getId(), RoleType.DEAN, faculty.getId(),
            today.plusDays(9), today.plusDays(12))).hasSize(1);
        assertThat(delegationRepository.findOverlapping(dean.getId(), RoleType.DEAN, faculty.getId(),
            today.plusDays(11), today.plusDays(12))).isEmpty();
        assertThat(delegationRepository.findOverlapping(dean.getId(), RoleType.DEAN, faculty.getId(),
            today, today.plusDays(4))).isEmpty();
    }

    @Test
    void ac3_4_onlyDelegationsThatHaveNotEndedAreTakenBackWithTheRole() {
        entityManager.persist(delegation(today.minusDays(20), today.minusDays(10)));
        entityManager.persist(delegation(today, today.plusDays(10)));
        entityManager.flush();

        assertThat(delegationRepository.findStandingByRole(dean.getId(), RoleType.DEAN, faculty.getId(),
            today)).hasSize(1);
    }

    private RoleDelegation delegation(LocalDate from, LocalDate to) {
        return RoleDelegation.builder().delegator(dean).delegate(secretary).roleType(RoleType.DEAN)
            .organization(faculty).validFrom(from).validTo(to).reason("Відпустка").build();
    }
}

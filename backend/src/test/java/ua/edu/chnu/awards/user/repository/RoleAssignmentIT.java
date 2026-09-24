package ua.edu.chnu.awards.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import ua.edu.chnu.awards.support.AbstractJpaSliceTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;

class RoleAssignmentIT extends AbstractJpaSliceTest {

    private static final LocalDate OPEN_ENDED = LocalDate.of(9999, 12, 31);

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final LocalDate today = LocalDate.now();
    private Organization department;
    private Organization faculty;
    private User holder;

    @BeforeEach
    void seed() {
        department = entityManager.find(Organization.class, TestUsers.DAI_DEPARTMENT_ID);
        faculty = entityManager.find(Organization.class, TestUsers.FMI_FACULTY_ID);
        holder = entityManager.persist(TestUsers.user("roles.holder@chnu.edu.ua", department));
        entityManager.flush();
    }

    @Test
    void ac2_1_theSameOpenEndedRoleCannotBeHeldTwiceInOneOrganisation() {
        entityManager.persist(TestUsers.role(holder, RoleType.EMPLOYEE, department, today.minusYears(1), null));
        entityManager.flush();

        UserRole duplicate = TestUsers.role(holder, RoleType.EMPLOYEE, department, today, null);

        assertThatThrownBy(() -> entityManager.persist(duplicate))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("uk_user_roles_current");
    }

    @Test
    void ac2_1_theSameRoleInAnotherOrganisationIsFine() {
        entityManager.persist(TestUsers.role(holder, RoleType.FACULTY_SECRETARY, department, today, null));
        entityManager.persist(TestUsers.role(holder, RoleType.FACULTY_SECRETARY, faculty, today, null));

        entityManager.flush();

        assertThat(userRoleRepository.findCurrentByUserId(holder.getId(), today)).hasSize(2);
    }

    @Test
    void ac2_4_aRoleRevokedOnTheDayItBeganEndsTheDayBefore() {
        UserRole sameDay = entityManager.persist(TestUsers.role(holder, RoleType.EMPLOYEE, department, today,
            null));
        entityManager.flush();

        sameDay.setValidTo(today.minusDays(1));
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(UserRole.class, sameDay.getId()).getValidTo())
            .isEqualTo(today.minusDays(1));
        assertThat(userRoleRepository.findCurrentByUserId(holder.getId(), today)).isEmpty();
    }

    @Test
    void ac2_1_ac2_4_aRevokedRoleLeavesTheDayFreeForANewAssignment() {
        entityManager.persist(TestUsers.role(holder, RoleType.EMPLOYEE, department, today.minusMonths(2),
            today.minusDays(1)));
        entityManager.flush();

        List<UserRole> overlapping = userRoleRepository.findOverlapping(holder.getId(), RoleType.EMPLOYEE,
            department.getId(), today, OPEN_ENDED);

        assertThat(overlapping).isEmpty();
        entityManager.persist(TestUsers.role(holder, RoleType.EMPLOYEE, department, today, null));
        entityManager.flush();
        assertThat(userRoleRepository.findCurrentByUserId(holder.getId(), today)).hasSize(1);
    }

    @Test
    void ac2_1_anOverlappingPeriodOfTheSameRoleIsFound() {
        entityManager.persist(TestUsers.role(holder, RoleType.EMPLOYEE, department, today,
            today.plusMonths(1)));
        entityManager.flush();

        assertThat(userRoleRepository.findOverlapping(holder.getId(), RoleType.EMPLOYEE, department.getId(),
            today.plusDays(10), today.plusDays(20))).hasSize(1);
        assertThat(userRoleRepository.findOverlapping(holder.getId(), RoleType.EMPLOYEE, department.getId(),
            today.plusMonths(2), OPEN_ENDED)).isEmpty();
        assertThat(userRoleRepository.findOverlapping(holder.getId(), RoleType.FACULTY_SECRETARY,
            department.getId(), today, OPEN_ENDED)).isEmpty();
    }

    @Test
    void ac2_6_anAccountThatNeverHeldARoleIsUnconfirmed() {
        assertThat(userRoleRepository.existsByUserId(holder.getId())).isFalse();

        entityManager.persist(TestUsers.role(holder, RoleType.EMPLOYEE, department, today.minusYears(2),
            today.minusYears(1)));
        entityManager.flush();

        assertThat(userRoleRepository.existsByUserId(holder.getId())).isTrue();
    }

    @Test
    void ac2_4_anAssignmentIsFoundOnlyUnderItsOwnHolder() {
        UserRole role = entityManager.persist(TestUsers.role(holder, RoleType.EMPLOYEE, department, today, null));
        User other = entityManager.persist(TestUsers.user("roles.other@chnu.edu.ua", department));
        entityManager.flush();

        assertThat(userRoleRepository.findByIdAndUserId(role.getId(), holder.getId())).isPresent();
        assertThat(userRoleRepository.findByIdAndUserId(role.getId(), other.getId())).isEmpty();
    }
}

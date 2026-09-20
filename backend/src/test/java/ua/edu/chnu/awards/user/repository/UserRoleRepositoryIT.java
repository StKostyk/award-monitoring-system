package ua.edu.chnu.awards.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import ua.edu.chnu.awards.support.AbstractJpaSliceTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;

class UserRoleRepositoryIT extends AbstractJpaSliceTest {

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void ac04_returnsOnlyRolesValidToday() {
        Organization department = entityManager.find(Organization.class, TestUsers.DAI_DEPARTMENT_ID);
        Organization faculty = entityManager.find(Organization.class, TestUsers.FMI_FACULTY_ID);
        User user = entityManager.persistAndFlush(TestUsers.user("roles@chnu.edu.ua", department));
        LocalDate today = LocalDate.now();

        entityManager.persist(TestUsers.role(user, RoleType.EMPLOYEE, department, today.minusYears(1), null));
        entityManager.persist(TestUsers.role(user, RoleType.DEAN, faculty, today.minusYears(2), today.minusDays(1)));
        entityManager.persist(TestUsers.role(user, RoleType.FACULTY_SECRETARY, faculty, today.plusDays(1), null));
        entityManager.persist(TestUsers.role(user, RoleType.RECTOR, faculty, today.minusDays(3), today));
        entityManager.flush();

        List<UserRole> current = userRoleRepository.findCurrentByUserId(user.getId(), today);

        assertThat(current).extracting(UserRole::getRoleType)
            .containsExactlyInAnyOrder(RoleType.EMPLOYEE, RoleType.RECTOR);
        assertThat(current).allMatch(role -> role.isCurrentOn(today));
    }

    @Test
    void recordsWhoAssignedTheRole() {
        Organization department = entityManager.find(Organization.class, TestUsers.DAI_DEPARTMENT_ID);
        User admin = entityManager.persistAndFlush(TestUsers.user("admin-role@chnu.edu.ua", department));
        User user = entityManager.persistAndFlush(TestUsers.user("assigned@chnu.edu.ua", department));
        UserRole role = TestUsers.role(user, RoleType.EMPLOYEE, department, LocalDate.now(), null);
        role.setCreatedBy(admin);

        UserRole saved = entityManager.persistFlushFind(role);

        assertThat(saved.getCreatedBy().getId()).isEqualTo(admin.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}

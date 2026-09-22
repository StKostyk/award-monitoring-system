package ua.edu.chnu.awards.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import ua.edu.chnu.awards.support.AbstractJpaSliceTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;

class UserDirectoryIT extends AbstractJpaSliceTest {

    private static final long OTHER_FACULTY_ID = 10L;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final UserSpecifications specifications = new UserSpecifications();
    private final LocalDate today = LocalDate.now();
    private User confirmed;
    private User newcomer;
    private User elsewhere;
    private User pending;

    @BeforeEach
    void seed() {
        Organization department = entityManager.find(Organization.class, TestUsers.DAI_DEPARTMENT_ID);
        Organization faculty = entityManager.find(Organization.class, TestUsers.FMI_FACULTY_ID);
        confirmed = entityManager.persist(named(TestUsers.user("dir.confirmed@chnu.edu.ua", department),
            "Олена", "Підтверджена"));
        entityManager.persist(TestUsers.role(confirmed, RoleType.EMPLOYEE, department, today.minusYears(1),
            today.minusDays(1)));
        entityManager.persist(TestUsers.role(confirmed, RoleType.FACULTY_SECRETARY, faculty, today, null));
        newcomer = entityManager.persist(named(TestUsers.user("dir.new@chnu.edu.ua", department), "Ірина", "Нова"));
        Organization other = entityManager.find(Organization.class, OTHER_FACULTY_ID);
        elsewhere = entityManager.persist(named(TestUsers.user("dir.other@chnu.edu.ua", other), "Петро", "Інший"));
        entityManager.persist(TestUsers.role(elsewhere, RoleType.DEAN, other, today, null));
        User unverified = TestUsers.user("dir.pending@chnu.edu.ua", department);
        unverified.setAccountStatus(AccountStatus.PENDING);
        pending = entityManager.persist(unverified);
        entityManager.flush();
    }

    @Test
    void ac16_scopeLimitsTheDirectoryToTheSubtree() {
        List<User> fmi = find(specifications.inOrganizations(Set.of(9L, 64L)),
            specifications.withStatus(null));

        assertThat(fmi).extracting(User::getId).contains(confirmed.getId(), newcomer.getId())
            .doesNotContain(elsewhere.getId(), pending.getId());
        assertThat(find(specifications.inOrganizations(Set.of()))).isEmpty();
    }

    @Test
    void ac16_unverifiedAccountsNeverAppearEvenWhenAskedFor() {
        assertThat(find(specifications.withStatus(null))).extracting(User::getId)
            .doesNotContain(pending.getId());
        assertThat(find(specifications.withStatus(AccountStatus.PENDING))).isEmpty();
        assertThat(find(specifications.withStatus(AccountStatus.ACTIVE))).extracting(User::getId)
            .contains(confirmed.getId(), newcomer.getId());
    }

    @Test
    void ac16_roleAndConfirmationFiltersUseTheAssignments() {
        assertThat(find(specifications.holdingRole(RoleType.FACULTY_SECRETARY, today)))
            .extracting(User::getId).containsExactly(confirmed.getId());
        assertThat(find(specifications.holdingRole(RoleType.EMPLOYEE, today)))
            .extracting(User::getId).doesNotContain(confirmed.getId());
        assertThat(find(specifications.neverConfirmed(), specifications.withStatus(null)))
            .extracting(User::getId).contains(newcomer.getId()).doesNotContain(confirmed.getId());
        assertThat(userRoleRepository.findEverAssignedUserIds(List.of(confirmed.getId(), newcomer.getId())))
            .containsExactly(confirmed.getId());
    }

    @Test
    void ac16_freeTextMatchesNameOrAddressAndEscapesWildcards() {
        assertThat(find(specifications.matching("нова"))).extracting(User::getId)
            .containsExactly(newcomer.getId());
        assertThat(find(specifications.matching("Ірина Нов"))).extracting(User::getId)
            .containsExactly(newcomer.getId());
        assertThat(find(specifications.matching("DIR.OTHER"))).extracting(User::getId)
            .containsExactly(elsewhere.getId());
        assertThat(find(specifications.matching("r.new"))).extracting(User::getId)
            .containsExactly(newcomer.getId());
        assertThat(find(specifications.matching("r.n%"))).isEmpty();
        assertThat(find(specifications.matching("d_r"))).isEmpty();
        assertThat(specifications.matching("н")).isNull();
    }

    @Test
    void ac17_historyIsNewestFirstAndCurrentRolesOfSeveralUsersLoadInOneQuery() {
        assertThat(userRoleRepository.findHistoryByUserId(confirmed.getId()))
            .extracting(role -> role.getRoleType())
            .containsExactly(RoleType.FACULTY_SECRETARY, RoleType.EMPLOYEE);
        assertThat(userRoleRepository.findCurrentByUserIds(List.of(confirmed.getId(), elsewhere.getId()), today))
            .extracting(role -> role.getRoleType())
            .containsExactlyInAnyOrder(RoleType.FACULTY_SECRETARY, RoleType.DEAN);
    }

    @SafeVarargs
    private List<User> find(Specification<User>... parts) {
        return userRepository.findAll(Specification.allOf(parts),
            PageRequest.of(0, 50, Sort.by("lastName", "firstName", "id"))).getContent();
    }

    private static User named(User user, String firstName, String lastName) {
        user.setFirstName(firstName);
        user.setLastName(lastName);
        return user;
    }
}

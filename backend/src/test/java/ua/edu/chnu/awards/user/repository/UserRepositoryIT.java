package ua.edu.chnu.awards.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import ua.edu.chnu.awards.support.AbstractJpaSliceTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;

class UserRepositoryIT extends AbstractJpaSliceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void ac03_findsUserByEmailIgnoringCase() {
        Organization department = entityManager.find(Organization.class, TestUsers.DAI_DEPARTMENT_ID);
        entityManager.persistAndFlush(TestUsers.user("Mixed.Case@chnu.edu.ua", department));

        Optional<User> found = userRepository.findByEmailAddressIgnoreCase("mixed.case@CHNU.EDU.UA");

        assertThat(found).isPresent();
        assertThat(found.get().getEmailAddress()).isEqualTo("Mixed.Case@chnu.edu.ua");
        assertThat(userRepository.existsByEmailAddressIgnoreCase("MIXED.CASE@chnu.edu.ua")).isTrue();
        assertThat(userRepository.existsByEmailAddressIgnoreCase("other@chnu.edu.ua")).isFalse();
    }

    @Test
    void ac01_persistsEveryMappedColumn() {
        Organization department = entityManager.find(Organization.class, TestUsers.DAI_DEPARTMENT_ID);
        User saved = entityManager.persistFlushFind(TestUsers.user("columns@chnu.edu.ua", department));

        assertThat(saved.getId()).isPositive();
        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getOrganization().getId()).isEqualTo(TestUsers.DAI_DEPARTMENT_ID);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isNotNull();
        assertThat(saved.getLastLoginAt()).isNull();
        assertThat(saved.getFullName()).isEqualTo("Test User");
    }

    @Test
    void ac43_recordingASignInKeepsTheVersionSoParallelSignInsDoNotCollide() {
        Organization department = entityManager.find(Organization.class, TestUsers.DAI_DEPARTMENT_ID);
        User saved = entityManager.persistFlushFind(TestUsers.user("signin@chnu.edu.ua", department));
        Instant at = Instant.parse("2026-09-28T09:00:00Z");

        assertThat(userRepository.recordLogin(saved.getId(), at)).isEqualTo(1);
        entityManager.clear();

        User reloaded = entityManager.find(User.class, saved.getId());
        assertThat(reloaded.getLastLoginAt()).isEqualTo(at);
        assertThat(reloaded.getVersion()).isEqualTo(saved.getVersion());
    }

    @Test
    void rejectsDuplicateEmailInAnyLetterCase() {
        Organization department = entityManager.find(Organization.class, TestUsers.DAI_DEPARTMENT_ID);
        entityManager.persistAndFlush(TestUsers.user("case@chnu.edu.ua", department));

        assertThatThrownBy(() -> entityManager.persistAndFlush(TestUsers.user("CASE@chnu.edu.ua", department)))
            .isInstanceOf(jakarta.persistence.PersistenceException.class);
    }

    @Test
    void rejectsDuplicateEmail() {
        Organization department = entityManager.find(Organization.class, TestUsers.DAI_DEPARTMENT_ID);
        entityManager.persistAndFlush(TestUsers.user("dup@chnu.edu.ua", department));

        assertThatThrownBy(() -> entityManager.persistAndFlush(TestUsers.user("dup@chnu.edu.ua", department)))
            .isInstanceOfAny(DataIntegrityViolationException.class,
                jakarta.persistence.PersistenceException.class);
    }
}

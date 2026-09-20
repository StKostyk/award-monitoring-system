package ua.edu.chnu.awards;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.support.ContainersConfiguration;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/seed/local")
@Import(ContainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class DevSeedIT {

    private static final Map<String, RoleType> EXPECTED_ROLES = Map.of(
        "admin@chnu.edu.ua", RoleType.SYSTEM_ADMIN,
        "rector@chnu.edu.ua", RoleType.RECTOR,
        "dean.fmi@chnu.edu.ua", RoleType.DEAN,
        "secretary.fmi@chnu.edu.ua", RoleType.FACULTY_SECRETARY,
        "employee.fmi@chnu.edu.ua", RoleType.EMPLOYEE,
        "pending@chnu.edu.ua", RoleType.EMPLOYEE);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void ac05_seedUsersExistWithRolesAndStatuses() {
        List<User> users = userRepository.findAll();

        assertThat(users).extracting(User::getEmailAddress)
            .containsExactlyInAnyOrderElementsOf(EXPECTED_ROLES.keySet());
        for (User user : users) {
            AccountStatus expected = "pending@chnu.edu.ua".equals(user.getEmailAddress())
                ? AccountStatus.PENDING : AccountStatus.ACTIVE;
            assertThat(user.getAccountStatus()).as(user.getEmailAddress()).isEqualTo(expected);
            List<UserRole> roles = userRoleRepository.findCurrentByUserId(user.getId(), LocalDate.now());
            assertThat(roles).extracting(UserRole::getRoleType)
                .as(user.getEmailAddress())
                .containsExactly(EXPECTED_ROLES.get(user.getEmailAddress()));
        }
    }

    @Test
    void ac05_seedPasswordMatchesDemoPassword() {
        User admin = userRepository.findByEmailAddressIgnoreCase("admin@chnu.edu.ua").orElseThrow();

        assertThat(new BCryptPasswordEncoder().matches("Passw0rd-demo", admin.getPasswordHash())).isTrue();
    }
}

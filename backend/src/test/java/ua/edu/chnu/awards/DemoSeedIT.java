package ua.edu.chnu.awards;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
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

class DemoSeedIT {

    private static final String DEMO_LOCATIONS =
        "spring.flyway.locations=classpath:db/migration,classpath:db/seed/demo";
    private static final String PASSWORD = "Demo-pass-2026";
    private static final String HASH_2Y = "$2y$04$GYoaOAjEWSJ9kqym1YaoReD76IiCdzoR2aUu0HLnALB5KAzDL6G5G";
    private static final Map<String, RoleType> EXPECTED_ROLES = Map.of(
        "admin@demo.example", RoleType.SYSTEM_ADMIN,
        "rector@demo.example", RoleType.RECTOR,
        "dean.fmi@demo.example", RoleType.DEAN,
        "secretary.fmi@demo.example", RoleType.FACULTY_SECRETARY,
        "employee.fmi@demo.example", RoleType.EMPLOYEE);

    @Nested
    @SpringBootTest(properties = {DEMO_LOCATIONS, "DEMO_PASSWORD_HASH=" + HASH_2Y})
    @Import(ContainersConfiguration.class)
    @ActiveProfiles("test")
    @Transactional
    class WithPasswordHash {

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private UserRoleRepository userRoleRepository;

        @Test
        void demoAccountsExistWithRoles() {
            List<User> users = userRepository.findAll();

            assertThat(users).extracting(User::getEmailAddress)
                .containsExactlyInAnyOrderElementsOf(EXPECTED_ROLES.keySet());
            for (User user : users) {
                assertThat(user.getAccountStatus()).as(user.getEmailAddress()).isEqualTo(AccountStatus.ACTIVE);
                List<UserRole> roles = userRoleRepository.findCurrentByUserId(user.getId(), LocalDate.now());
                assertThat(roles).extracting(UserRole::getRoleType)
                    .as(user.getEmailAddress())
                    .containsExactly(EXPECTED_ROLES.get(user.getEmailAddress()));
            }
        }

        @Test
        void demoPasswordComesFromTheConfiguredHash() {
            User admin = userRepository.findByEmailAddressIgnoreCase("admin@demo.example").orElseThrow();

            assertThat(new BCryptPasswordEncoder().matches(PASSWORD, admin.getPasswordHash())).isTrue();
        }
    }

    @Nested
    @SpringBootTest(properties = DEMO_LOCATIONS)
    @Import(ContainersConfiguration.class)
    @ActiveProfiles("test")
    @Transactional
    class WithoutPasswordHash {

        @Autowired
        private UserRepository userRepository;

        @Test
        void noAccountsAreCreated() {
            assertThat(userRepository.count()).isZero();
        }
    }
}

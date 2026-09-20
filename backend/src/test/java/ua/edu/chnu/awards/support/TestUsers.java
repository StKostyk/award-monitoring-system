package ua.edu.chnu.awards.support;

import java.time.LocalDate;

import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;

/**
 * Builders for users and roles used across tests. Organisation ids refer to the seeded university structure.
 */
public final class TestUsers {

    public static final long UNIVERSITY_ID = 1L;
    public static final long FMI_FACULTY_ID = 9L;
    public static final long DAI_DEPARTMENT_ID = 64L;

    private TestUsers() {
    }

    public static User user(String email, Organization organization) {
        return User.builder()
            .emailAddress(email)
            .firstName("Test")
            .lastName("User")
            .passwordHash("$2a$12$aHfrRHPVY2s/0iSzOm5Jkua5t87xeHqTfXWPovs7uM/trJp2mgoTi")
            .accountStatus(AccountStatus.ACTIVE)
            .organization(organization)
            .build();
    }

    public static UserRole role(User user, RoleType type, Organization organization,
                                LocalDate validFrom, LocalDate validTo) {
        return UserRole.builder()
            .user(user)
            .roleType(type)
            .organization(organization)
            .validFrom(validFrom)
            .validTo(validTo)
            .build();
    }
}

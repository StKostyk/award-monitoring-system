package ua.edu.chnu.awards.support;

import java.time.LocalDate;

import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;

/**
 * Builders for users, organisations and roles used across tests. The id constants refer to the seeded
 * university structure; `person` and `organization` build detached entities for unit tests.
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

    public static User person(long id, String email) {
        return person(id, email, "Марія", null);
    }

    public static User person(long id, String email, Organization organization) {
        return person(id, email, "Марія", organization);
    }

    public static User person(long id, String email, String firstName, Organization organization) {
        return User.builder().id(id).emailAddress(email).firstName(firstName).lastName("Мартинюк")
            .accountStatus(AccountStatus.ACTIVE).organization(organization).build();
    }

    public static Organization organization(long id, OrganizationType type) {
        return organization(id, type, "Org " + id);
    }

    public static Organization organization(long id, OrganizationType type, String name) {
        return Organization.builder().id(id).orgType(type).name(name).nameUk(name + " (укр)").code("O" + id)
            .active(true).build();
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

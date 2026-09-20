package ua.edu.chnu.awards.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class UserRoleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Test
    void ac04_openEndedRoleIsCurrentFromItsStartDate() {
        UserRole role = UserRole.builder().roleType(RoleType.EMPLOYEE).validFrom(TODAY).build();

        assertThat(role.isCurrentOn(TODAY)).isTrue();
        assertThat(role.isCurrentOn(TODAY.minusDays(1))).isFalse();
        assertThat(role.isCurrentOn(TODAY.plusYears(5))).isTrue();
    }

    @Test
    void ac04_boundedRoleIsCurrentUntilItsEndDateInclusive() {
        UserRole role = UserRole.builder()
            .roleType(RoleType.DEAN)
            .validFrom(TODAY.minusDays(10))
            .validTo(TODAY)
            .build();

        assertThat(role.isCurrentOn(TODAY)).isTrue();
        assertThat(role.isCurrentOn(TODAY.plusDays(1))).isFalse();
    }

    @Test
    void userStatusHelpersFollowTheStateMachine() {
        User user = User.builder().firstName("Anna").lastName("Kovalenko")
            .accountStatus(AccountStatus.ACTIVE).build();

        assertThat(user.getFullName()).isEqualTo("Anna Kovalenko");
        assertThat(user.isActive()).isTrue();
        user.setAccountStatus(AccountStatus.PENDING);
        assertThat(user.isActive()).isFalse();
        assertThat(AccountStatus.PENDING.canLogIn()).isFalse();
        assertThat(AccountStatus.RETIRED.canLogIn()).isTrue();
        assertThat(AccountStatus.MEMORIAL.canLogIn()).isFalse();
    }
}

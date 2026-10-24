package ua.edu.chnu.awards.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ua.edu.chnu.awards.support.TestUsers.organization;
import static ua.edu.chnu.awards.support.TestUsers.person;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.RoleLevels;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

class RoleLastHolderRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);
    private static final long HOLDER_ID = 2L;
    private static final long ASSIGNMENT_ID = 100L;

    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final RoleAssignmentRules rules = new RoleAssignmentRules(userRoleRepository,
        new OrganizationLookup(mock(OrganizationRepository.class)), new RoleOrganizations(), new RoleLevels(),
        mock(AccessScope.class));
    private final Organization university = organization(1L, OrganizationType.UNIVERSITY, "ChNU");
    private final User holder = person(HOLDER_ID, "holder@chnu.edu.ua", university);

    @Test
    void ac6_1_theLastSystemAdministratorIsRefused() {
        UserRole role = assignment(RoleType.SYSTEM_ADMIN, TODAY.minusYears(1));

        assertThatThrownBy(() -> rules.requireNotLastHolder(holder, role, TODAY))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getType()).isEqualTo("role-last-holder");
            });
    }

    @Test
    void ac6_1_theLastDataProtectionOfficerIsRefused() {
        UserRole role = assignment(RoleType.GDPR_OFFICER, TODAY.minusYears(1));

        assertThatThrownBy(() -> rules.requireNotLastHolder(holder, role, TODAY))
            .isInstanceOfSatisfying(ApiProblemException.class,
                e -> assertThat(e.getType()).isEqualTo("role-last-holder"));
    }

    @Test
    void ac6_2_anotherActiveHolderLetsTheRoleGo() {
        UserRole role = assignment(RoleType.SYSTEM_ADMIN, TODAY.minusYears(1));
        when(userRoleRepository.existsOtherActiveHolder(RoleType.SYSTEM_ADMIN, ASSIGNMENT_ID, TODAY))
            .thenReturn(true);

        assertThatCode(() -> rules.requireNotLastHolder(holder, role, TODAY)).doesNotThrowAnyException();
    }

    @Test
    void ac6_2_aHolderWhoseAccountIsNotActiveDoesNotCount() {
        holder.setAccountStatus(AccountStatus.RETIRED);
        UserRole role = assignment(RoleType.GDPR_OFFICER, TODAY.minusYears(1));

        assertThatCode(() -> rules.requireNotLastHolder(holder, role, TODAY)).doesNotThrowAnyException();
        verify(userRoleRepository, never()).existsOtherActiveHolder(any(), anyLong(), any());
    }

    @Test
    void ac6_2_anAssignmentThatHasNotStartedDoesNotCount() {
        UserRole role = assignment(RoleType.SYSTEM_ADMIN, TODAY.plusDays(3));

        assertThatCode(() -> rules.requireNotLastHolder(holder, role, TODAY)).doesNotThrowAnyException();
    }

    @Test
    void ac6_2_otherRolesAreNotGuarded() {
        UserRole role = assignment(RoleType.RECTOR, TODAY.minusYears(1));

        assertThatCode(() -> rules.requireNotLastHolder(holder, role, TODAY)).doesNotThrowAnyException();
        verify(userRoleRepository, never()).existsOtherActiveHolder(any(), anyLong(), any());
    }

    @Test
    void ac6_3_aGuardedRoleTakesTheErasureLock() {
        when(userRoleRepository.findRoleTypeByIdAndUserId(ASSIGNMENT_ID, HOLDER_ID))
            .thenReturn(Optional.of(RoleType.GDPR_OFFICER));

        rules.serializeWithErasures(HOLDER_ID, ASSIGNMENT_ID);

        verify(userRoleRepository).lockAgainstErasures();
    }

    @Test
    void ac6_3_otherRolesAndUnknownAssignmentsDoNotWait() {
        when(userRoleRepository.findRoleTypeByIdAndUserId(ASSIGNMENT_ID, HOLDER_ID))
            .thenReturn(Optional.of(RoleType.FACULTY_SECRETARY));

        rules.serializeWithErasures(HOLDER_ID, ASSIGNMENT_ID);
        rules.serializeWithErasures(HOLDER_ID, ASSIGNMENT_ID + 1);

        verify(userRoleRepository, never()).lockAgainstErasures();
    }

    private UserRole assignment(RoleType type, LocalDate from) {
        UserRole role = UserRole.builder().user(holder).roleType(type).organization(university).validFrom(from)
            .build();
        role.setId(ASSIGNMENT_ID);
        return role;
    }
}

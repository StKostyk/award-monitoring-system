package ua.edu.chnu.awards.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.auth.security.AuthorizationRevoker;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.RoleLevels;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.delegation.service.DelegationService;
import ua.edu.chnu.awards.user.dto.RoleAssignmentRequest;
import ua.edu.chnu.awards.user.dto.RoleAssignmentResponse;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.mapper.UserProfileMapper;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

class RoleAssignmentServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);
    private static final long FACULTY_ID = 9L;
    private static final long DEPARTMENT_ID = 64L;
    private static final long OTHER_DEPARTMENT_ID = 65L;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final AccessScope access = mock(AccessScope.class);
    private final AuthorizationRevoker revoker = mock(AuthorizationRevoker.class);
    private final RoleChangeRecorder recorder = mock(RoleChangeRecorder.class);
    private final DelegationService delegations = mock(DelegationService.class);

    private final Organization faculty = organization(FACULTY_ID, OrganizationType.FACULTY, "FMI");
    private final Organization department = organization(DEPARTMENT_ID, OrganizationType.DEPARTMENT, "DAI");
    private final Organization other = organization(OTHER_DEPARTMENT_ID, OrganizationType.DEPARTMENT, "DMA");
    private final User dean = person(1L, "dean.fmi@chnu.edu.ua", faculty);
    private final User employee = person(2L, "employee.fmi@chnu.edu.ua", department);

    private RoleAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new RoleAssignmentService(userRepository, userRoleRepository, organizationRepository,
            new RoleOrganizations(), new RoleLevels(), access, revoker, new UserProfileMapper(),
            recorder, delegations, Clock.fixed(Instant.parse("2026-09-22T09:00:00Z"), ZoneId.of("Europe/Kyiv")));
        when(access.callerId()).thenReturn(dean.getId());
        when(access.readableOrganizations()).thenReturn(Optional.of(Set.of(FACULTY_ID, DEPARTMENT_ID,
            OTHER_DEPARTMENT_ID)));
        when(access.canManage(any(), anyLong())).thenReturn(true);
        when(userRepository.findById(dean.getId())).thenReturn(Optional.of(dean));
        when(userRepository.findByIdForUpdate(dean.getId())).thenReturn(Optional.of(dean));
        when(userRepository.findByIdForUpdate(employee.getId())).thenReturn(Optional.of(employee));
        when(organizationRepository.findById(FACULTY_ID)).thenReturn(Optional.of(faculty));
        when(organizationRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(department));
        when(organizationRepository.findById(OTHER_DEPARTMENT_ID)).thenReturn(Optional.of(other));
        when(userRoleRepository.saveAndFlush(any(UserRole.class))).thenAnswer(invocation -> {
            UserRole role = invocation.getArgument(0);
            role.setId(100L);
            return role;
        });
    }

    @Test
    void ac2_1_grantsTheRoleAndRecordsWhoDidIt() {
        RoleAssignmentResponse response = service.assign(employee.getId(),
            new RoleAssignmentRequest(RoleType.FACULTY_SECRETARY, FACULTY_ID, null, null, false));

        assertThat(response.role()).isEqualTo(RoleType.FACULTY_SECRETARY);
        assertThat(response.validFrom()).isEqualTo(TODAY);
        ArgumentCaptor<UserRole> saved = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCreatedBy()).isSameAs(dean);
        assertThat(saved.getValue().getUser()).isSameAs(employee);
    }

    @Test
    void ac2_1_aDefaultedStartDateIsTodayAndAPastOneIsRefused() {
        RoleAssignmentRequest past = new RoleAssignmentRequest(RoleType.EMPLOYEE, DEPARTMENT_ID,
            TODAY.minusDays(1), null, false);

        assertThatThrownBy(() -> service.assign(employee.getId(), past))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(e.getType()).isEqualTo("role-validity");
            });
    }

    @Test
    void ac2_1_anInactiveAccountCannotHoldARole() {
        employee.setAccountStatus(AccountStatus.SUSPENDED);
        RoleAssignmentRequest request = new RoleAssignmentRequest(RoleType.EMPLOYEE, DEPARTMENT_ID, null, null,
            false);

        assertThatThrownBy(() -> service.assign(employee.getId(), request))
            .isInstanceOfSatisfying(ApiProblemException.class,
                e -> assertThat(e.getType()).isEqualTo("user-not-active"));
    }

    @Test
    void ac2_1_aUserOutsideTheCallersScopeIsUnknown() {
        when(access.readableOrganizations()).thenReturn(Optional.of(Set.of(FACULTY_ID)));
        RoleAssignmentRequest request = new RoleAssignmentRequest(RoleType.EMPLOYEE, DEPARTMENT_ID, null, null,
            false);

        assertThatThrownBy(() -> service.assign(employee.getId(), request))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void ac2_1_ac5_aRoleAlreadyHeldThereIsAConflict() {
        when(userRoleRepository.findOverlapping(eq(employee.getId()), eq(RoleType.EMPLOYEE), eq(DEPARTMENT_ID),
            any(), any())).thenReturn(List.of(assignment(employee, RoleType.EMPLOYEE, department, TODAY, null)));
        RoleAssignmentRequest request = new RoleAssignmentRequest(RoleType.EMPLOYEE, DEPARTMENT_ID, null, null,
            false);

        assertThatThrownBy(() -> service.assign(employee.getId(), request))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getType()).isEqualTo("role-already-assigned");
            });
    }

    @Test
    void ac2_1_ac5_aConcurrentGrantLosingTheRaceIsAConflictToo() {
        when(userRoleRepository.saveAndFlush(any(UserRole.class)))
            .thenThrow(new DataIntegrityViolationException("uk_user_roles_current"));
        RoleAssignmentRequest request = new RoleAssignmentRequest(RoleType.EMPLOYEE, DEPARTMENT_ID, null, null,
            false);

        assertThatThrownBy(() -> service.assign(employee.getId(), request))
            .isInstanceOfSatisfying(ApiProblemException.class,
                e -> assertThat(e.getType()).isEqualTo("role-already-assigned"));
    }

    @Test
    void ac2_2_aRoleAtOrAboveTheCallersLevelIsForbidden() {
        when(access.canManage(RoleType.RECTOR, 1L)).thenReturn(false);
        when(access.has("user:manage")).thenReturn(false);
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(
            organization(1L, OrganizationType.UNIVERSITY, "ChNU")));
        RoleAssignmentRequest request = new RoleAssignmentRequest(RoleType.RECTOR, 1L, null, null, false);

        assertThatThrownBy(() -> service.assign(employee.getId(), request))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(e.getType()).isEqualTo("role-above-level");
            });
        verify(userRoleRepository, never()).saveAndFlush(any());
    }

    @Test
    void ac2_2_theAdministratorMayGrantAnythingAnywhere() {
        when(access.canManage(any(), anyLong())).thenReturn(false);
        when(access.has("user:manage")).thenReturn(true);

        service.assign(employee.getId(),
            new RoleAssignmentRequest(RoleType.FACULTY_SECRETARY, FACULTY_ID, null, null, false));

        verify(userRoleRepository).saveAndFlush(any(UserRole.class));
    }

    @Test
    void ac2_3_anOrganisationOfTheWrongLevelIsRefused() {
        RoleAssignmentRequest request = new RoleAssignmentRequest(RoleType.DEAN, DEPARTMENT_ID, null, null, false);

        assertThatThrownBy(() -> service.assign(employee.getId(), request))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(e.getType()).isEqualTo("role-organization-mismatch");
            });
    }

    @Test
    void ac2_3_anInactiveOrganisationIsRefused() {
        Organization closed = organization(70L, OrganizationType.DEPARTMENT, "Closed");
        closed.setActive(false);
        when(organizationRepository.findById(70L)).thenReturn(Optional.of(closed));
        RoleAssignmentRequest request = new RoleAssignmentRequest(RoleType.EMPLOYEE, 70L, null, null, false);

        assertThatThrownBy(() -> service.assign(employee.getId(), request))
            .isInstanceOfSatisfying(ApiProblemException.class,
                e -> assertThat(e.getType()).isEqualTo("organisation-invalid"));
    }

    @Test
    void ac2_4_revocationEndsTheRoleYesterdayAndSignsTheHolderOut() {
        UserRole role = assignment(employee, RoleType.FACULTY_SECRETARY, faculty, TODAY.minusMonths(1), null);
        when(userRoleRepository.findByIdAndUserId(role.getId(), employee.getId())).thenReturn(Optional.of(role));

        service.revoke(employee.getId(), role.getId());

        assertThat(role.getValidTo()).isEqualTo(TODAY.minusDays(1));
        verify(revoker).revokeAll(employee);
        verify(recorder).revoked(dean, role);
    }

    @Test
    void ac2_4_anAssignmentThatHasAlreadyEndedCannotBeRevokedAgain() {
        UserRole role = assignment(employee, RoleType.EMPLOYEE, department, TODAY.minusMonths(2),
            TODAY.minusMonths(1));
        when(userRoleRepository.findByIdAndUserId(role.getId(), employee.getId())).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.revoke(employee.getId(), role.getId()))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getType()).isEqualTo("role-already-revoked");
            });
        verify(revoker, never()).revokeAll(any());
    }

    @Test
    void ac2_4_aCallerCannotTakeBackTheirOwnLastRoleAboveEmployee() {
        UserRole own = assignment(dean, RoleType.DEAN, faculty, TODAY.minusYears(1), null);
        when(userRoleRepository.findByIdAndUserId(own.getId(), dean.getId())).thenReturn(Optional.of(own));
        when(userRoleRepository.findCurrentByUserId(dean.getId(), TODAY)).thenReturn(List.of(own,
            assignment(dean, RoleType.EMPLOYEE, department, TODAY.minusYears(1), null)));

        assertThatThrownBy(() -> service.revoke(dean.getId(), own.getId()))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(e.getType()).isEqualTo("role-last-own");
            });
        assertThat(own.getValidTo()).isNull();
    }

    @Test
    void ac2_4_aCallerMayTakeBackOneOfSeveralOwnRolesAboveEmployee() {
        UserRole own = assignment(dean, RoleType.DEAN, faculty, TODAY.minusYears(1), null);
        UserRole another = assignment(dean, RoleType.FACULTY_SECRETARY, faculty, TODAY.minusYears(1), null);
        another.setId(101L);
        when(userRoleRepository.findByIdAndUserId(own.getId(), dean.getId())).thenReturn(Optional.of(own));
        when(userRoleRepository.findCurrentByUserId(dean.getId(), TODAY)).thenReturn(List.of(own, another));

        service.revoke(dean.getId(), own.getId());

        assertThat(own.getValidTo()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void ac2_5_grantingIsRecordedWithTheActorAndTheNewAssignment() {
        service.assign(employee.getId(),
            new RoleAssignmentRequest(RoleType.FACULTY_SECRETARY, FACULTY_ID, null, TODAY.plusMonths(1), false));

        ArgumentCaptor<UserRole> granted = ArgumentCaptor.forClass(UserRole.class);
        verify(recorder).granted(eq(dean), granted.capture());
        assertThat(granted.getValue().getRoleType()).isEqualTo(RoleType.FACULTY_SECRETARY);
        assertThat(granted.getValue().getValidTo()).isEqualTo(TODAY.plusMonths(1));
    }

    @Test
    void ac2_5_revocationIsRecordedOnceTheLastDayIsSet() {
        UserRole role = assignment(employee, RoleType.FACULTY_SECRETARY, faculty, TODAY.minusMonths(1), null);
        when(userRoleRepository.findByIdAndUserId(role.getId(), employee.getId())).thenReturn(Optional.of(role));

        service.revoke(employee.getId(), role.getId());

        verify(recorder).revoked(dean, role);
        assertThat(role.getValidTo()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void ac2_7_confirmingWithACorrectedDepartmentMovesTheUserAndEndsTheOldRole() {
        UserRole old = assignment(employee, RoleType.EMPLOYEE, department, TODAY.minusYears(1), null);
        when(userRoleRepository.findCurrentByUserId(employee.getId(), TODAY)).thenReturn(List.of(old));

        service.assign(employee.getId(),
            new RoleAssignmentRequest(RoleType.EMPLOYEE, OTHER_DEPARTMENT_ID, null, null, true));

        assertThat(employee.getOrganization()).isSameAs(other);
        assertThat(old.getValidTo()).isEqualTo(TODAY.minusDays(1));
        verify(recorder).superseded(dean, old);
        verify(recorder).granted(eq(dean), any(UserRole.class));
        verify(revoker).revokeAll(employee);
    }

    @Test
    void ac2_7_confirmingTheDepartmentTheUserPickedChangesNothingElse() {
        service.assign(employee.getId(),
            new RoleAssignmentRequest(RoleType.EMPLOYEE, DEPARTMENT_ID, null, null, true));

        assertThat(employee.getOrganization()).isSameAs(department);
        verify(recorder, never()).superseded(any(), any());
        verify(revoker, never()).revokeAll(any());
    }

    @Test
    void ac2_7_aRoleInAnOrganisationTheCallerCannotManageSurvivesTheCorrection() {
        Organization elsewhere = organization(80L, OrganizationType.DEPARTMENT, "Other faculty");
        UserRole outside = assignment(employee, RoleType.EMPLOYEE, elsewhere, TODAY.minusYears(1), null);
        when(userRoleRepository.findCurrentByUserId(employee.getId(), TODAY)).thenReturn(List.of(outside));
        when(access.canManage(RoleType.EMPLOYEE, 80L)).thenReturn(false);

        service.assign(employee.getId(),
            new RoleAssignmentRequest(RoleType.EMPLOYEE, OTHER_DEPARTMENT_ID, null, null, true));

        assertThat(outside.getValidTo()).isNull();
        verify(recorder, never()).superseded(any(), any());
    }

    @Test
    void ac2_4_anAssignmentThatHasNotStartedYetEndsBeforeItWouldHaveBegun() {
        UserRole future = assignment(employee, RoleType.FACULTY_SECRETARY, faculty, TODAY.plusMonths(1), null);
        when(userRoleRepository.findByIdAndUserId(future.getId(), employee.getId()))
            .thenReturn(Optional.of(future));

        service.revoke(employee.getId(), future.getId());

        assertThat(future.getValidTo()).isEqualTo(TODAY.plusMonths(1).minusDays(1));
        verify(revoker).revokeAll(employee);
    }

    @Test
    void ac2_7_onlyTheEmployeeRoleMayCorrectTheDepartment() {
        RoleAssignmentRequest request = new RoleAssignmentRequest(RoleType.FACULTY_SECRETARY, FACULTY_ID, null,
            null, true);

        assertThatThrownBy(() -> service.assign(employee.getId(), request))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(e.getType()).isEqualTo("membership-role-required");
            });
    }

    private static Organization organization(long id, OrganizationType type, String name) {
        return Organization.builder().id(id).orgType(type).name(name).nameUk(name + " (укр)").active(true).build();
    }

    private static User person(long id, String email, Organization organization) {
        return User.builder().id(id).emailAddress(email).firstName("Іван").lastName("Тест")
            .accountStatus(AccountStatus.ACTIVE).organization(organization).build();
    }

    private static UserRole assignment(User user, RoleType role, Organization organization, LocalDate from,
                                       LocalDate to) {
        UserRole assignment = UserRole.builder().user(user).roleType(role).organization(organization)
            .validFrom(from).validTo(to).build();
        assignment.setId(100L);
        return assignment;
    }
}

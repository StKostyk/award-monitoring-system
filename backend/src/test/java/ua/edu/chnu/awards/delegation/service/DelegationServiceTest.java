package ua.edu.chnu.awards.delegation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.auth.security.AuthorizationRevoker;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.RoleScope;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.delegation.dto.DelegationListResponse;
import ua.edu.chnu.awards.delegation.dto.DelegationRequest;
import ua.edu.chnu.awards.delegation.dto.DelegationResponse;
import ua.edu.chnu.awards.delegation.entity.DelegationState;
import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.delegation.mapper.DelegationMapper;
import ua.edu.chnu.awards.delegation.repository.RoleDelegationRepository;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;
import ua.edu.chnu.awards.user.service.UserNotFoundException;

class DelegationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);
    private static final long FACULTY_ID = 9L;
    private static final long DEPARTMENT_ID = 64L;
    private static final long OTHER_FACULTY_ID = 10L;

    private final RoleDelegationRepository delegationRepository = mock(RoleDelegationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final AccessScope access = mock(AccessScope.class);
    private final AuthorizationRevoker revoker = mock(AuthorizationRevoker.class);
    private final DelegationRecorder recorder = mock(DelegationRecorder.class);

    private final Organization faculty = organization(FACULTY_ID, OrganizationType.FACULTY);
    private final Organization department = organization(DEPARTMENT_ID, OrganizationType.DEPARTMENT);
    private final User dean = person(1L, "dean.fmi@chnu.edu.ua", faculty);
    private final User secretary = person(2L, "secretary.fmi@chnu.edu.ua", faculty);

    private DelegationService service;

    @BeforeEach
    void setUp() {
        service = new DelegationService(delegationRepository, userRepository,
            new DelegationRules(delegationRepository, userRepository, userRoleRepository,
                organizationRepository, access),
            access, revoker, new DelegationMapper(), recorder,
            Clock.fixed(Instant.parse("2026-09-24T09:00:00Z"), ZoneId.of("Europe/Kyiv")));
        when(access.callerId()).thenReturn(dean.getId());
        when(access.heldScopes()).thenReturn(List.of(new RoleScope(RoleType.DEAN, FACULTY_ID)));
        when(access.readableOrganizations()).thenReturn(Optional.of(Set.of(FACULTY_ID, DEPARTMENT_ID)));
        when(access.subtreeOf(FACULTY_ID)).thenReturn(Set.of(FACULTY_ID, DEPARTMENT_ID));
        when(userRepository.findById(dean.getId())).thenReturn(Optional.of(dean));
        when(userRepository.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(organizationRepository.findById(FACULTY_ID)).thenReturn(Optional.of(faculty));
        when(userRoleRepository.findCurrentByUserId(anyLong(), any(LocalDate.class))).thenReturn(List.of(
            UserRole.builder().roleType(RoleType.FACULTY_SECRETARY).organization(faculty).build()));
        when(delegationRepository.findOverlapping(anyLong(), any(), anyLong(), any(), any()))
            .thenReturn(List.of());
    }

    @Test
    void ac31_aHeldApprovalRoleIsLentForABoundedPeriod() {
        DelegationResponse response = service.create(request(secretary.getId(), RoleType.DEAN, FACULTY_ID,
            TODAY, TODAY.plusDays(14)));

        assertThat(response.role()).isEqualTo(RoleType.DEAN);
        assertThat(response.delegate().id()).isEqualTo(secretary.getId());
        assertThat(response.delegator().id()).isEqualTo(dean.getId());
        assertThat(response.state()).isEqualTo(DelegationState.ACTIVE);
        assertThat(response.reason()).isEqualTo("Vacation");
        verify(delegationRepository).saveAndFlush(any(RoleDelegation.class));
        verify(recorder).created(any(RoleDelegation.class));
    }

    @Test
    void ac31_aRoleTheCallerDoesNotHoldIsRefused() {
        assertThatThrownBy(() -> service.create(request(secretary.getId(), RoleType.RECTOR, FACULTY_ID, TODAY,
            TODAY.plusDays(7))))
            .isInstanceOf(ApiProblemException.class)
            .extracting("type", "status")
            .containsExactly("delegation-not-holder", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void ac32_borrowedAuthorityCannotBePassedOn() {
        when(access.heldScopes()).thenReturn(List.of(new RoleScope(RoleType.FACULTY_SECRETARY, FACULTY_ID)));

        assertThatThrownBy(() -> service.create(request(secretary.getId(), RoleType.DEAN, FACULTY_ID, TODAY,
            TODAY.plusDays(7))))
            .isInstanceOf(ApiProblemException.class)
            .extracting("type")
            .isEqualTo("delegation-not-holder");
    }

    @Test
    void ac31_onlyApprovalRolesAreLent() {
        when(access.heldScopes()).thenReturn(List.of(new RoleScope(RoleType.EMPLOYEE, DEPARTMENT_ID)));

        assertThatThrownBy(() -> service.create(request(secretary.getId(), RoleType.EMPLOYEE, FACULTY_ID,
            TODAY, TODAY.plusDays(7))))
            .isInstanceOf(ApiProblemException.class)
            .extracting("type")
            .isEqualTo("delegation-not-holder");
    }

    @Test
    void ac31_theEndDateIsBoundedByNinetyDaysAndTheCalendar() {
        assertThat(typeOf(() -> service.create(request(secretary.getId(), RoleType.DEAN, FACULTY_ID, TODAY,
            TODAY.plusDays(91))))).isEqualTo("delegation-period");
        assertThat(typeOf(() -> service.create(request(secretary.getId(), RoleType.DEAN, FACULTY_ID, TODAY,
            TODAY.minusDays(1))))).isEqualTo("delegation-period");
        assertThat(typeOf(() -> service.create(request(secretary.getId(), RoleType.DEAN, FACULTY_ID,
            TODAY.minusDays(30), TODAY.minusDays(20))))).isEqualTo("delegation-period");
        assertThat(service.create(request(secretary.getId(), RoleType.DEAN, FACULTY_ID, TODAY,
            TODAY.plusDays(90))).validTo()).isEqualTo(TODAY.plusDays(90));
    }

    @Test
    void ac31_theDelegateIsAnActiveColleagueInsideTheOrganisation() {
        assertThat(typeOf(() -> service.create(request(dean.getId(), RoleType.DEAN, FACULTY_ID, TODAY,
            TODAY.plusDays(7))))).isEqualTo("delegation-bad-delegate");

        secretary.setAccountStatus(AccountStatus.SUSPENDED);
        assertThat(typeOf(() -> service.create(request(secretary.getId(), RoleType.DEAN, FACULTY_ID, TODAY,
            TODAY.plusDays(7))))).isEqualTo("delegation-bad-delegate");

        secretary.setAccountStatus(AccountStatus.ACTIVE);
        when(userRoleRepository.findCurrentByUserId(anyLong(), any(LocalDate.class))).thenReturn(List.of(
            UserRole.builder().roleType(RoleType.FACULTY_SECRETARY)
                .organization(organization(OTHER_FACULTY_ID, OrganizationType.FACULTY)).build()));
        assertThat(typeOf(() -> service.create(request(secretary.getId(), RoleType.DEAN, FACULTY_ID, TODAY,
            TODAY.plusDays(7))))).isEqualTo("delegation-bad-delegate");
    }

    @Test
    void ac31_anUnknownOrForeignDelegateIsUnknown() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(99L, RoleType.DEAN, FACULTY_ID, TODAY,
            TODAY.plusDays(7))))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void ac31_asecondDelegationOfTheSameRoleOverThePeriodIsRefused() {
        when(delegationRepository.findOverlapping(dean.getId(), RoleType.DEAN, FACULTY_ID, TODAY,
            TODAY.plusDays(7))).thenReturn(List.of(delegation(TODAY, TODAY.plusDays(3))));

        assertThat(typeOf(() -> service.create(request(secretary.getId(), RoleType.DEAN, FACULTY_ID, TODAY,
            TODAY.plusDays(7))))).isEqualTo("delegation-overlap");
    }

    @Test
    void ac35_thePageShowsWhatWasGivenAndReceivedAndFiltersByState() {
        when(delegationRepository.findByDelegatorId(dean.getId())).thenReturn(List.of(
            delegation(TODAY, TODAY.plusDays(3)), delegation(TODAY.plusDays(5), TODAY.plusDays(9))));
        when(delegationRepository.findByDelegateId(dean.getId())).thenReturn(List.of(
            delegation(TODAY.minusDays(9), TODAY.minusDays(5))));

        DelegationListResponse all = service.list(null, null);
        assertThat(all.given()).hasSize(2);
        assertThat(all.received()).hasSize(1);
        assertThat(service.list(DelegationState.ACTIVE, null).given()).hasSize(1);
        assertThat(service.list(DelegationState.UPCOMING, null).given()).hasSize(1);
        assertThat(service.list(DelegationState.EXPIRED, null).received()).hasSize(1);
        assertThat(service.list(DelegationState.EXPIRED, null).given()).isEmpty();
    }

    @Test
    void ac35_anAdministratorReadsSomebodyElsesPage() {
        when(delegationRepository.findByDelegatorId(secretary.getId()))
            .thenReturn(List.of(delegation(TODAY, TODAY.plusDays(3))));

        assertThat(service.list(null, secretary.getId()).given()).hasSize(1);
        verify(delegationRepository, never()).findByDelegatorId(dean.getId());
    }

    @Test
    void ac34_theDelegatorTakesTheAuthorityBackAndTheDelegateIsSignedOut() {
        RoleDelegation delegation = delegation(TODAY, TODAY.plusDays(7));
        when(delegationRepository.findById(5L)).thenReturn(Optional.of(delegation));

        service.revoke(5L);

        assertThat(delegation.getRevokedAt()).isNotNull();
        assertThat(delegation.getRevokedBy()).isEqualTo(dean);
        assertThat(delegation.stateOn(TODAY)).isEqualTo(DelegationState.REVOKED);
        verify(recorder).revoked(dean, delegation);
        verify(revoker).revokeAll(secretary);
    }

    @Test
    void ac34_somebodyWhoCouldTakeTheRoleBackMayEndTheDelegation() {
        RoleDelegation delegation = delegation(TODAY, TODAY.plusDays(7));
        when(delegationRepository.findById(5L)).thenReturn(Optional.of(delegation));
        when(access.callerId()).thenReturn(secretary.getId());
        when(access.canManage(RoleType.DEAN, FACULTY_ID)).thenReturn(true);

        service.revoke(5L);

        verify(recorder).revoked(secretary, delegation);
    }

    @Test
    void ac34_anUnrelatedCallerIsNotEvenToldThatItExists() {
        RoleDelegation expired = delegation(TODAY.minusDays(9), TODAY.minusDays(2));
        when(delegationRepository.findById(5L)).thenReturn(Optional.of(expired));
        when(access.callerId()).thenReturn(secretary.getId());
        when(access.canManage(RoleType.DEAN, FACULTY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.revoke(5L)).isInstanceOf(DelegationNotFoundException.class);
        verify(revoker, never()).revokeAll(any());
    }

    @Test
    void ac34_whatHasAlreadyEndedCannotEndAgain() {
        RoleDelegation revoked = delegation(TODAY, TODAY.plusDays(7));
        revoked.setRevokedAt(Instant.parse("2026-09-23T09:00:00Z"));
        when(delegationRepository.findById(5L)).thenReturn(Optional.of(revoked));
        when(delegationRepository.findById(6L))
            .thenReturn(Optional.of(delegation(TODAY.minusDays(9), TODAY.minusDays(2))));

        assertThat(typeOf(() -> service.revoke(5L))).isEqualTo("delegation-not-active");
        assertThat(typeOf(() -> service.revoke(6L))).isEqualTo("delegation-not-active");
    }

    @Test
    void anUnknownDelegationIsNotFound() {
        when(delegationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(404L)).isInstanceOf(DelegationNotFoundException.class);
    }

    @Test
    void ac34_ac5_losingTheLastRoleInAnOrganisationEndsTheAuthorityBorrowedThere() {
        RoleDelegation received = delegation(TODAY, TODAY.plusDays(7));
        when(delegationRepository.findStandingByDelegate(secretary.getId(), TODAY))
            .thenReturn(List.of(received));
        when(userRoleRepository.findCurrentByUserId(secretary.getId(), TODAY)).thenReturn(List.of());
        when(access.subtreeOf(FACULTY_ID)).thenReturn(Set.of(FACULTY_ID, DEPARTMENT_ID));
        UserRole assignment = UserRole.builder().user(secretary).roleType(RoleType.FACULTY_SECRETARY)
            .organization(faculty).validFrom(TODAY.minusDays(30)).build();

        service.revokeForRole(dean, assignment);

        assertThat(received.getRevokedBy()).isEqualTo(dean);
        verify(revoker).revokeAll(secretary);
    }

    @Test
    void ac34_revokingTheRoleTakesBackWhatItHadLent() {
        RoleDelegation delegation = delegation(TODAY, TODAY.plusDays(7));
        UserRole assignment = UserRole.builder().user(dean).roleType(RoleType.DEAN).organization(faculty)
            .validFrom(TODAY.minusDays(30)).build();
        when(delegationRepository.findStandingByRole(dean.getId(), RoleType.DEAN, FACULTY_ID, TODAY))
            .thenReturn(List.of(delegation));

        service.revokeForRole(secretary, assignment);

        assertThat(delegation.getRevokedBy()).isEqualTo(secretary);
        verify(recorder).revoked(secretary, delegation);
        verify(revoker).revokeAll(secretary);
    }

    private String typeOf(Runnable call) {
        try {
            call.run();
            return "no problem";
        } catch (ApiProblemException e) {
            return e.getType();
        }
    }

    private RoleDelegation delegation(LocalDate from, LocalDate to) {
        return RoleDelegation.builder().id(5L).delegator(dean).delegate(secretary).roleType(RoleType.DEAN)
            .organization(faculty).validFrom(from).validTo(to).createdAt(Instant.parse("2026-09-24T08:00:00Z"))
            .build();
    }

    private static DelegationRequest request(long delegateId, RoleType role, long organizationId,
                                             LocalDate from, LocalDate to) {
        return new DelegationRequest(delegateId, role, organizationId, from, to, "Vacation");
    }

    private static User person(long id, String email, Organization organization) {
        return User.builder().id(id).emailAddress(email).firstName("Test").lastName("User")
            .accountStatus(AccountStatus.ACTIVE).organization(organization).build();
    }

    private static Organization organization(long id, OrganizationType type) {
        return Organization.builder().id(id).name("Org " + id).nameUk("Підрозділ " + id).code("O" + id)
            .orgType(type).active(true).build();
    }
}

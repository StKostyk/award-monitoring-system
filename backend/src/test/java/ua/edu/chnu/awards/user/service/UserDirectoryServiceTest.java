package ua.edu.chnu.awards.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.user.dto.UserDetailResponse;
import ua.edu.chnu.awards.user.dto.UserDirectoryQuery;
import ua.edu.chnu.awards.user.dto.UserSummaryResponse;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.mapper.UserProfileMapper;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;
import ua.edu.chnu.awards.user.repository.UserSpecifications;

class UserDirectoryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final AccessScope access = mock(AccessScope.class);
    private final UserDirectoryService service = new UserDirectoryService(userRepository, userRoleRepository,
        new UserProfileMapper(), new UserSpecifications(), access,
        Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    private final Organization department = Organization.builder().id(64L).name("DAI")
        .orgType(OrganizationType.DEPARTMENT).build();
    private final User member = User.builder().id(5L).emailAddress("m@chnu.edu.ua").firstName("A").lastName("B")
        .organization(department).accountStatus(AccountStatus.ACTIVE).createdAt(Instant.EPOCH).build();

    @Test
    void ac16_pagesAreCappedAndRowsCarryCurrentRolesAndConfirmation() {
        when(access.readableOrganizations()).thenReturn(Optional.of(Set.of(9L, 64L)));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(member), PageRequest.of(0, 100), 1));
        when(userRoleRepository.findCurrentByUserIds(anyCollection(), any())).thenReturn(List.of(
            UserRole.builder().id(3L).user(member).roleType(RoleType.EMPLOYEE).organization(department)
                .validFrom(TODAY).build()));
        when(userRoleRepository.findEverAssignedUserIds(anyCollection())).thenReturn(Set.of(5L));

        Page<UserSummaryResponse> page = service.list(new UserDirectoryQuery(null, null, null, false, null), 0, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.email()).isEqualTo("m@chnu.edu.ua");
            assertThat(row.roles()).singleElement().satisfies(role -> assertThat(role.id()).isEqualTo(3L));
            assertThat(row.membershipConfirmed()).isTrue();
        });
    }

    @Test
    void ac16_anEmptyPageAsksForNoRoles() {
        when(access.readableOrganizations()).thenReturn(Optional.empty());
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.list(new UserDirectoryQuery(64L, null, null, true, "ab"), -1, 0).getContent()).isEmpty();
        verify(userRoleRepository, org.mockito.Mockito.never()).findCurrentByUserIds(anyCollection(), any());
    }

    @Test
    void ac17_detailSplitsCurrentRolesFromHistoryAndHidesUsersOutsideTheScope() {
        when(access.readableOrganizations()).thenReturn(Optional.of(Set.of(9L, 64L)));
        when(userRepository.findById(5L)).thenReturn(Optional.of(member));
        when(userRoleRepository.findHistoryByUserId(5L)).thenReturn(List.of(
            UserRole.builder().id(2L).user(member).roleType(RoleType.FACULTY_SECRETARY).organization(department)
                .validFrom(TODAY.plusDays(1)).build(),
            UserRole.builder().id(1L).user(member).roleType(RoleType.EMPLOYEE).organization(department)
                .validFrom(TODAY.minusYears(1)).validTo(TODAY.minusDays(1)).build()));

        UserDetailResponse detail = service.detail(5L);

        assertThat(detail.roles()).isEmpty();
        assertThat(detail.roleHistory()).extracting(role -> role.id()).containsExactly(2L, 1L);
        assertThat(detail.membershipConfirmed()).isTrue();

        User outsider = User.builder().id(6L).organization(Organization.builder().id(10L).build())
            .accountStatus(AccountStatus.ACTIVE).build();
        when(userRepository.findById(6L)).thenReturn(Optional.of(outsider));
        assertThatThrownBy(() -> service.detail(6L)).isInstanceOf(UserNotFoundException.class);

        User pending = User.builder().id(7L).organization(department).accountStatus(AccountStatus.PENDING).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service.detail(7L)).isInstanceOf(UserNotFoundException.class);
    }
}

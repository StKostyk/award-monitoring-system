package ua.edu.chnu.awards.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.dto.UserProfileResponse;
import ua.edu.chnu.awards.user.dto.UserUpdateRequest;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.mapper.UserProfileMapper;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

class UserProfileServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final Organization faculty = TestUsers.organization(9L, OrganizationType.FACULTY, "Faculty");
    private final Organization department = TestUsers.organization(64L, OrganizationType.DEPARTMENT, "Dept");
    private User user;
    private UserProfileService service;

    @BeforeEach
    void setUp() {
        department.setParent(faculty);
        user = TestUsers.person(5L, "employee.fmi@chnu.edu.ua", "Анастасія", department);
        user.setLastName("Петренко");
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRoleRepository.findCurrentByUserId(anyLong(), any())).thenReturn(List.of());
        service = new UserProfileService(userRepository, userRoleRepository, new UserProfileMapper(),
            Clock.fixed(Instant.parse("2026-09-29T10:00:00Z"), ZoneOffset.UTC), new ProfileNameRules(), audit,
            new TransactionTemplate(transactionManager));
    }

    @Test
    void ac11_theProfileNamesTheFacultyOfTheDepartment() {
        UserProfileResponse profile = service.profileOf(5L);

        assertThat(profile.organization().id()).isEqualTo(64L);
        assertThat(profile.faculty().id()).isEqualTo(9L);
    }

    @Test
    void ac12_ac13_storesTheNewNameAndAuditsOnlyTheChangedField() {
        UserProfileResponse profile = service.update(5L, new UserUpdateRequest(" Анастасія ", "Петренко-Коваль"));

        assertThat(user.getLastName()).isEqualTo("Петренко-Коваль");
        assertThat(profile.lastName()).isEqualTo("Петренко-Коваль");
        verify(userRepository).saveAndFlush(user);
        verify(audit).recordChange(AuditAction.PROFILE_UPDATED, UserProfileService.AUDIT_ENTITY, 5L, 5L,
            Map.of("last_name", "Петренко"), Map.of("last_name", "Петренко-Коваль"));
    }

    @Test
    void ac12_aChangeOfNothingIsAnsweredWithoutAudit() {
        UserProfileResponse profile = service.update(5L, new UserUpdateRequest("Анастасія", "Петренко"));

        assertThat(profile.firstName()).isEqualTo("Анастасія");
        verify(userRepository, never()).saveAndFlush(any());
        verify(audit, never()).recordChange(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ac12_anInvalidNameChangesNothing() {
        assertThatThrownBy(() -> service.update(5L, new UserUpdateRequest(null, "X1")))
            .isInstanceOf(ApiProblemException.class);

        assertThat(user.getLastName()).isEqualTo("Петренко");
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void edge_aConcurrentWriteIsRetriedOnce() {
        when(userRepository.findById(5L)).thenAnswer(invocation ->
            Optional.of(TestUsers.person(5L, "employee.fmi@chnu.edu.ua", "Анастасія", department)));
        when(userRepository.saveAndFlush(any()))
            .thenThrow(new ObjectOptimisticLockingFailureException(User.class, 5L))
            .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.update(5L, new UserUpdateRequest("Олена", null)).firstName()).isEqualTo("Олена");
        verify(userRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void edge_aSecondConflictIsAnswered409() {
        when(userRepository.findById(5L)).thenAnswer(invocation ->
            Optional.of(TestUsers.person(5L, "employee.fmi@chnu.edu.ua", "Анастасія", department)));
        when(userRepository.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(User.class, 5L));

        assertThatThrownBy(() -> service.update(5L, new UserUpdateRequest("Олена", null)))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus().value()).isEqualTo(409);
                assertThat(problem.getType()).isEqualTo("concurrent-update");
            });
    }
}

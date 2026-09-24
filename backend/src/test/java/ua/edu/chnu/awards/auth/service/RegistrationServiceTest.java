package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.dto.RegisterRequest;
import ua.edu.chnu.awards.auth.dto.RegistrationResponse;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.auth.event.VerificationRequested;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.config.AuthProperties;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;
import ua.edu.chnu.awards.user.service.ManualConfirmation;

class RegistrationServiceTest {

    private static final RegisterRequest VALID = new RegisterRequest("New.User@chnu.edu.ua", "correct-horse-battery",
        "Олена", "Нова", 64L);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final OneTimeTokenService tokens = mock(OneTimeTokenService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RequestThrottle throttle = mock(RequestThrottle.class);
    private final AuditService audit = mock(AuditService.class);
    private final AuthProperties properties = new AuthProperties("http://localhost:8080", "http://localhost:4200",
        List.of(), List.of("chnu.edu.ua"), Duration.ofHours(24), Duration.ofHours(1), Duration.ofHours(24),
        Duration.ofMinutes(1),
        new AuthProperties.Client("award-web", List.of(), List.of(), Duration.ofMinutes(15), Duration.ofDays(7)),
        new AuthProperties.Jwk("", "", ""));
    private final Organization department = Organization.builder().id(64L).orgType(OrganizationType.DEPARTMENT)
        .active(true).build();
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(userRepository, userRoleRepository, organizationRepository, tokens,
            new PasswordPolicy(), passwordEncoder, events, throttle, properties, audit, new ManualConfirmation());
        when(passwordEncoder.encode(any())).thenReturn("$2a$12$hash");
        when(organizationRepository.findById(64L)).thenReturn(Optional.of(department));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });
        when(tokens.issue(any(), eq(TokenPurpose.EMAIL_VERIFICATION), eq(Duration.ofHours(24))))
            .thenReturn("raw-token");
    }

    @Test
    void ac21_registersPendingAccountAndRequestsVerificationEmail() {
        RegistrationResponse response = service.register(VALID);

        assertThat(response.status()).isEqualTo(AccountStatus.PENDING);
        assertThat(response.email()).isEqualTo("new.user@chnu.edu.ua");
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(user.capture());
        verify(throttle).claim("auth:resend:new.user@chnu.edu.ua", Duration.ofMinutes(1));
        assertThat(user.getValue().getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(user.getValue().getOrganization()).isSameAs(department);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        VerificationRequested requested = (VerificationRequested) event.getValue();
        assertThat(requested.link()).isEqualTo("http://localhost:4200/verify-email?token=raw-token");
        assertThat(requested.firstName()).isEqualTo("Олена");
    }

    @Test
    void ac2_6_registrationGrantsNoRoleSoSomebodyMustConfirmTheMembership() {
        service.register(VALID);

        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    @Test
    void ac2_6_aProvenMembershipWouldGrantTheEmployeeRoleAtOnce() {
        RegistrationService proven = new RegistrationService(userRepository, userRoleRepository,
            organizationRepository, tokens, new PasswordPolicy(), passwordEncoder, events, throttle, properties,
            audit, (user, where) -> Optional.of(UserRole.builder().user(user).roleType(RoleType.EMPLOYEE)
                .organization(where).validFrom(LocalDate.of(2026, 9, 21)).build()));

        proven.register(VALID);

        ArgumentCaptor<UserRole> role = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(role.capture());
        assertThat(role.getValue().getRoleType()).isEqualTo(RoleType.EMPLOYEE);
        assertThat(role.getValue().getOrganization()).isSameAs(department);
    }

    @Test
    void ac2_8_pendingAccountWhoseLinkHasExpiredIsReplacedByTheNewRegistration() {
        User abandoned = User.builder().id(7L).emailAddress("new.user@chnu.edu.ua").firstName("Стара")
            .lastName("Назва").passwordHash("$2a$12$old").accountStatus(AccountStatus.PENDING)
            .organization(Organization.builder().id(65L).orgType(OrganizationType.DEPARTMENT).active(true).build())
            .build();
        when(userRepository.findByEmailAddressIgnoreCase("new.user@chnu.edu.ua")).thenReturn(Optional.of(abandoned));
        when(tokens.countUsable(abandoned, TokenPurpose.EMAIL_VERIFICATION)).thenReturn(0L);

        RegistrationResponse response = service.register(VALID);

        assertThat(response.status()).isEqualTo(AccountStatus.PENDING);
        assertThat(abandoned.getFirstName()).isEqualTo("Олена");
        assertThat(abandoned.getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(abandoned.getOrganization()).isSameAs(department);
        verify(tokens).invalidate(abandoned, TokenPurpose.EMAIL_VERIFICATION);
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(audit).record(AuditAction.REGISTRATION_REPLACED, 7L, Map.of("organizationId", 64L));
    }

    @Test
    void ac2_8_pendingAccountWithAUsableLinkKeepsTheAddress() {
        User pending = User.builder().id(7L).emailAddress("new.user@chnu.edu.ua")
            .accountStatus(AccountStatus.PENDING).build();
        when(userRepository.findByEmailAddressIgnoreCase("new.user@chnu.edu.ua")).thenReturn(Optional.of(pending));
        when(tokens.countUsable(pending, TokenPurpose.EMAIL_VERIFICATION)).thenReturn(1L);

        assertThatThrownBy(() -> service.register(VALID))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getType()).isEqualTo("email-taken");
            });
    }

    @Test
    void ac22_nonInstitutionalAddressIsRefusedBeforeAnythingIsStored() {
        RegisterRequest request = new RegisterRequest("someone@gmail.com", "correct-horse-battery", "A", "B", 64L);

        assertThatThrownBy(() -> service.register(request))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(e.getType()).isEqualTo("institutional-email-required");
            });
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void ac23_existingAddressIsAConflict() {
        when(userRepository.findByEmailAddressIgnoreCase("new.user@chnu.edu.ua")).thenReturn(Optional.of(
            User.builder().id(7L).accountStatus(AccountStatus.ACTIVE).build()));

        assertThatThrownBy(() -> service.register(VALID))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getType()).isEqualTo("email-taken");
            });
    }

    @Test
    void ac23_concurrentRegistrationLosingTheRaceIsAConflictToo() {
        when(userRepository.saveAndFlush(any(User.class)))
            .thenThrow(new DataIntegrityViolationException("uk_users_email_lower"));

        assertThatThrownBy(() -> service.register(VALID))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getType()).isEqualTo("email-taken"));
    }

    @Test
    void ac24_facultyOrInactiveOrganisationIsRefused() {
        when(organizationRepository.findById(9L)).thenReturn(Optional.of(
            Organization.builder().id(9L).orgType(OrganizationType.FACULTY).active(true).build()));
        when(organizationRepository.findById(65L)).thenReturn(Optional.of(
            Organization.builder().id(65L).orgType(OrganizationType.DEPARTMENT).active(false).build()));

        for (long id : new long[] {9L, 65L, 999L}) {
            RegisterRequest request = new RegisterRequest("x@chnu.edu.ua", "correct-horse-battery", "A", "B", id);
            assertThatThrownBy(() -> service.register(request))
                .isInstanceOfSatisfying(ApiProblemException.class,
                    e -> assertThat(e.getType()).isEqualTo("organisation-invalid"));
        }
    }

    @Test
    void weakPasswordIsRefusedWithTheReason() {
        RegisterRequest request = new RegisterRequest("x@chnu.edu.ua", "password123", "A", "B", 64L);

        assertThatThrownBy(() -> service.register(request))
            .isInstanceOfSatisfying(ApiProblemException.class,
                e -> assertThat(e.getType()).isEqualTo("password-too-common"));
    }

    @Test
    void ac25_validTokenAndTheRegistrationPasswordActivateThePendingAccount() {
        User user = User.builder().id(42L).emailAddress("x@chnu.edu.ua").passwordHash("$2a$12$hash")
            .accountStatus(AccountStatus.PENDING).build();
        when(tokens.peekOwner("raw", TokenPurpose.EMAIL_VERIFICATION)).thenReturn(user);
        when(tokens.redeemOwner("raw", TokenPurpose.EMAIL_VERIFICATION)).thenReturn(user);
        when(passwordEncoder.matches("correct-horse-battery", "$2a$12$hash")).thenReturn(true);

        RegistrationResponse response = service.verify("raw", "correct-horse-battery");

        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(audit).record(AuditAction.EMAIL_VERIFIED, 42L);
    }

    @Test
    void ac25_wrongPasswordIsForbiddenAndLeavesTheTokenUsable() {
        User user = User.builder().id(42L).passwordHash("$2a$12$hash").accountStatus(AccountStatus.PENDING).build();
        when(tokens.peekOwner("raw", TokenPurpose.EMAIL_VERIFICATION)).thenReturn(user);
        when(passwordEncoder.matches("wrong-password-1", "$2a$12$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.verify("raw", "wrong-password-1"))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(e.getType()).isEqualTo("password-mismatch");
            });
        verify(tokens, never()).redeemOwner(any(), any());
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void ac25_ac26_unknownExpiredOrUsedTokenIsGone() {
        when(tokens.peekOwner("raw", TokenPurpose.EMAIL_VERIFICATION))
            .thenThrow(new ApiProblemException(HttpStatus.GONE, "token-invalid", "The link is invalid"));

        assertThatThrownBy(() -> service.verify("raw", "correct-horse-battery"))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE);
                assertThat(e.getType()).isEqualTo("token-invalid");
            });
    }

    @Test
    void ac25_tokenRedeemedConcurrentlyIsGone() {
        User user = User.builder().id(42L).passwordHash("$2a$12$hash").accountStatus(AccountStatus.PENDING).build();
        when(tokens.peekOwner("raw", TokenPurpose.EMAIL_VERIFICATION)).thenReturn(user);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(tokens.redeemOwner("raw", TokenPurpose.EMAIL_VERIFICATION))
            .thenThrow(new ApiProblemException(HttpStatus.GONE, "token-invalid", "The link is invalid"));

        assertThatThrownBy(() -> service.verify("raw", "correct-horse-battery"))
            .isInstanceOfSatisfying(ApiProblemException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE));
    }

    @Test
    void verifyingASuspendedAccountDoesNotReactivateIt() {
        User user = User.builder().id(42L).passwordHash("$2a$12$hash").accountStatus(AccountStatus.SUSPENDED).build();
        when(tokens.peekOwner("raw", TokenPurpose.EMAIL_VERIFICATION)).thenReturn(user);
        when(tokens.redeemOwner("raw", TokenPurpose.EMAIL_VERIFICATION)).thenReturn(user);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.verify("raw", "correct-horse-battery"))
            .isInstanceOfSatisfying(ApiProblemException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void ac26_resendIsThrottledPerAddressAndSilentForUnknownAddresses() {
        when(throttle.claim("auth:resend:x@chnu.edu.ua", Duration.ofMinutes(1))).thenReturn(true, false);
        User pending = User.builder().id(1L).emailAddress("x@chnu.edu.ua").firstName("A")
            .accountStatus(AccountStatus.PENDING).build();
        when(userRepository.findByEmailAddressIgnoreCase("x@chnu.edu.ua")).thenReturn(Optional.of(pending));

        service.resend("x@chnu.edu.ua");
        verify(events).publishEvent(any(VerificationRequested.class));

        assertThatThrownBy(() -> service.resend("x@chnu.edu.ua"))
            .isInstanceOfSatisfying(ApiProblemException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        when(throttle.claim("auth:resend:ghost@chnu.edu.ua", Duration.ofMinutes(1))).thenReturn(true);
        when(userRepository.findByEmailAddressIgnoreCase("ghost@chnu.edu.ua")).thenReturn(Optional.empty());
        service.resend("ghost@chnu.edu.ua");
        verify(events).publishEvent(any(VerificationRequested.class));
    }
}

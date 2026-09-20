package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import ua.edu.chnu.awards.auth.dto.RegisterRequest;
import ua.edu.chnu.awards.auth.dto.RegistrationResponse;
import ua.edu.chnu.awards.auth.entity.OneTimeToken;
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

class RegistrationServiceTest {

    private static final RegisterRequest VALID = new RegisterRequest("New.User@chnu.edu.ua", "correct-horse-battery",
        "Олена", "Нова", 64L);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final OneTimeTokenService tokens = mock(OneTimeTokenService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final AuthProperties properties = new AuthProperties("http://localhost:8080", "http://localhost:4200",
        List.of(), List.of("chnu.edu.ua"), Duration.ofHours(24), Duration.ofMinutes(1),
        new AuthProperties.Client("award-web", List.of(), List.of(), Duration.ofMinutes(15), Duration.ofDays(7)),
        new AuthProperties.Jwk("", "", ""));
    private final Organization department = Organization.builder().id(64L).orgType(OrganizationType.DEPARTMENT)
        .active(true).build();
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(userRepository, userRoleRepository, organizationRepository, tokens,
            new PasswordPolicy(), passwordEncoder, events, redis, properties,
            Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC));
        when(redis.opsForValue()).thenReturn(values);
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
    void ac21_registersPendingEmployeeAndRequestsVerificationEmail() {
        RegistrationResponse response = service.register(VALID);

        assertThat(response.status()).isEqualTo(AccountStatus.PENDING);
        assertThat(response.email()).isEqualTo("new.user@chnu.edu.ua");
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(user.capture());
        verify(values).setIfAbsent("auth:resend:new.user@chnu.edu.ua", "1", Duration.ofMinutes(1));
        assertThat(user.getValue().getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(user.getValue().getOrganization()).isSameAs(department);
        ArgumentCaptor<UserRole> role = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(role.capture());
        assertThat(role.getValue().getRoleType()).isEqualTo(RoleType.EMPLOYEE);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        VerificationRequested requested = (VerificationRequested) event.getValue();
        assertThat(requested.link()).isEqualTo("http://localhost:4200/verify-email?token=raw-token");
        assertThat(requested.firstName()).isEqualTo("Олена");
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
        when(userRepository.existsByEmailAddressIgnoreCase("new.user@chnu.edu.ua")).thenReturn(true);

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
    void ac25_validTokenActivatesThePendingAccount() {
        User user = User.builder().id(42L).emailAddress("x@chnu.edu.ua").accountStatus(AccountStatus.PENDING).build();
        when(tokens.redeem("raw", TokenPurpose.EMAIL_VERIFICATION))
            .thenReturn(Optional.of(OneTimeToken.builder().user(user).build()));

        RegistrationResponse response = service.verify("raw");

        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void ac25_ac26_unknownExpiredOrUsedTokenIsGone() {
        when(tokens.redeem("raw", TokenPurpose.EMAIL_VERIFICATION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("raw"))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE);
                assertThat(e.getType()).isEqualTo("token-invalid");
            });
    }

    @Test
    void verifyingASuspendedAccountDoesNotReactivateIt() {
        User user = User.builder().id(42L).accountStatus(AccountStatus.SUSPENDED).build();
        when(tokens.redeem("raw", TokenPurpose.EMAIL_VERIFICATION))
            .thenReturn(Optional.of(OneTimeToken.builder().user(user).build()));

        assertThatThrownBy(() -> service.verify("raw"))
            .isInstanceOfSatisfying(ApiProblemException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void ac26_resendIsThrottledPerAddressAndSilentForUnknownAddresses() {
        when(values.setIfAbsent("auth:resend:x@chnu.edu.ua", "1", Duration.ofMinutes(1))).thenReturn(true, false);
        when(values.setIfAbsent("auth:resend:new.user@chnu.edu.ua", "1", Duration.ofMinutes(1))).thenReturn(true);
        User pending = User.builder().id(1L).emailAddress("x@chnu.edu.ua").firstName("A")
            .accountStatus(AccountStatus.PENDING).build();
        when(userRepository.findByEmailAddressIgnoreCase("x@chnu.edu.ua")).thenReturn(Optional.of(pending));

        service.resend("x@chnu.edu.ua");
        verify(events).publishEvent(any(VerificationRequested.class));

        assertThatThrownBy(() -> service.resend("x@chnu.edu.ua"))
            .isInstanceOfSatisfying(ApiProblemException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        when(values.setIfAbsent("auth:resend:ghost@chnu.edu.ua", "1", Duration.ofMinutes(1))).thenReturn(true);
        when(userRepository.findByEmailAddressIgnoreCase("ghost@chnu.edu.ua")).thenReturn(Optional.empty());
        service.resend("ghost@chnu.edu.ua");
        verify(events).publishEvent(any(VerificationRequested.class));
    }
}

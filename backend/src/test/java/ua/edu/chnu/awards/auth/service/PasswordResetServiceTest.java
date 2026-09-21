package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.auth.event.PasswordResetRequested;
import ua.edu.chnu.awards.auth.security.AuthorizationRevoker;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.config.AuthProperties;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class PasswordResetServiceTest {

    private static final String EMAIL = "olena@chnu.edu.ua";
    private static final String NEW_PASSWORD = "new-horse-battery";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OneTimeTokenService tokens = mock(OneTimeTokenService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuthorizationRevoker revoker = mock(AuthorizationRevoker.class);
    private final RequestThrottle throttle = mock(RequestThrottle.class);
    private final AuditService audit = mock(AuditService.class);
    private final AuthProperties properties = new AuthProperties("http://localhost:8080", "http://localhost:4200",
        List.of(), List.of("chnu.edu.ua"), Duration.ofHours(24), Duration.ofHours(1), Duration.ofHours(24),
        Duration.ofMinutes(1),
        new AuthProperties.Client("award-web", List.of(), List.of(), Duration.ofMinutes(15), Duration.ofDays(7)),
        new AuthProperties.Jwk("", "", ""));
    private final User active = User.builder().id(7L).emailAddress(EMAIL).firstName("Олена")
        .passwordHash("$2a$12$old").accountStatus(AccountStatus.ACTIVE).build();
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokens, new PasswordPolicy(), passwordEncoder, events,
            revoker, throttle, properties, audit);
        when(throttle.claim(any(), any())).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("$2a$12$new");
    }

    @Test
    void ac31_activeUserGetsAOneHourLinkByEmail() {
        when(userRepository.findByEmailAddressIgnoreCase(EMAIL)).thenReturn(Optional.of(active));
        when(tokens.issue(active, TokenPurpose.PASSWORD_RESET, Duration.ofHours(1))).thenReturn("raw-token");

        service.request(" Olena@chnu.edu.ua ");

        verify(throttle).claim("auth:reset:" + EMAIL, Duration.ofMinutes(1));
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        PasswordResetRequested requested = (PasswordResetRequested) event.getValue();
        assertThat(requested.email()).isEqualTo(EMAIL);
        assertThat(requested.firstName()).isEqualTo("Олена");
        assertThat(requested.link()).isEqualTo("http://localhost:4200/reset-password?token=raw-token");
        verify(audit).record(AuditAction.PASSWORD_RESET_REQUESTED, 7L);
    }

    @Test
    void ac31_unknownAndPendingAddressesAreAcceptedSilently() {
        User pending = User.builder().id(8L).emailAddress("pending@chnu.edu.ua")
            .accountStatus(AccountStatus.PENDING).build();
        when(userRepository.findByEmailAddressIgnoreCase("pending@chnu.edu.ua")).thenReturn(Optional.of(pending));
        when(userRepository.findByEmailAddressIgnoreCase("ghost@chnu.edu.ua")).thenReturn(Optional.empty());

        service.request("pending@chnu.edu.ua");
        service.request("ghost@chnu.edu.ua");

        verifyNoInteractions(tokens, events);
    }

    @Test
    void ac31_secondRequestWithinTheIntervalSendsNothing() {
        when(throttle.claim("auth:reset:" + EMAIL, Duration.ofMinutes(1))).thenReturn(false);

        service.request(EMAIL);

        verifyNoInteractions(userRepository, tokens, events);
    }

    @Test
    void ac32_validTokenReplacesTheHashAndRevokesEveryAuthorization() {
        when(tokens.redeemOwner("raw", TokenPurpose.PASSWORD_RESET)).thenReturn(active);

        service.confirm("raw", NEW_PASSWORD);

        assertThat(active.getPasswordHash()).isEqualTo("$2a$12$new");
        verify(revoker).revokeAll(EMAIL);
        verify(tokens).invalidate(active, TokenPurpose.SECURITY_REVOKE);
        verify(audit).record(AuditAction.PASSWORD_RESET, 7L);
    }

    @Test
    void ac32_expiredOrUsedTokenIsGoneAndNothingChanges() {
        when(tokens.redeemOwner("raw", TokenPurpose.PASSWORD_RESET))
            .thenThrow(new ApiProblemException(HttpStatus.GONE, "token-invalid", "The link is invalid"));

        assertThatThrownBy(() -> service.confirm("raw", NEW_PASSWORD))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE);
                assertThat(e.getType()).isEqualTo("token-invalid");
            });
        verifyNoInteractions(revoker);
    }

    @Test
    void ac32_weakPasswordIsRefusedBeforeTheTokenIsConsumed() {
        assertThatThrownBy(() -> service.confirm("raw", "password123"))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(e.getType()).isEqualTo("password-too-common");
            });
        verify(tokens, never()).redeemOwner(any(), any());
    }

    @Test
    void ac32_suspendedAccountKeepsItsPassword() {
        User suspended = User.builder().id(9L).emailAddress("s@chnu.edu.ua").passwordHash("$2a$12$old")
            .accountStatus(AccountStatus.SUSPENDED).build();
        when(tokens.redeemOwner("raw", TokenPurpose.PASSWORD_RESET)).thenReturn(suspended);

        assertThatThrownBy(() -> service.confirm("raw", NEW_PASSWORD))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getType()).isEqualTo("account-not-active");
            });
        assertThat(suspended.getPasswordHash()).isEqualTo("$2a$12$old");
        verifyNoInteractions(revoker);
    }
}

package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.dto.EmailChangeResponse;
import ua.edu.chnu.awards.auth.entity.OneTimeToken;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.auth.event.EmailChangeRequested;
import ua.edu.chnu.awards.auth.event.EmailChanged;
import ua.edu.chnu.awards.auth.security.AuthorizationRevoker;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.config.AuthProperties;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class EmailChangeServiceTest {

    private static final String OLD = "mover@chnu.edu.ua";
    private static final String NEW = "mover.new@chnu.edu.ua";
    private static final String PASSWORD = "Passw0rd-demo";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OneTimeTokenService tokens = mock(OneTimeTokenService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final AuthorizationRevoker revoker = mock(AuthorizationRevoker.class);
    private final RequestThrottle throttle = mock(RequestThrottle.class);
    private final AuditService audit = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuthProperties properties = new AuthProperties("http://localhost:8080", "http://localhost:4200",
        List.of(), List.of("chnu.edu.ua"), Duration.ofHours(24), Duration.ofHours(1), Duration.ofHours(24),
        Duration.ofHours(1), Duration.ofMinutes(1),
        new AuthProperties.Client("award-web", List.of(), List.of(), Duration.ofMinutes(15), Duration.ofDays(7)),
        new AuthProperties.Jwk("", "", "", ""));
    private final User user = User.builder().id(12L).emailAddress(OLD).firstName("Петро")
        .passwordHash("$2a$12$hash").accountStatus(AccountStatus.ACTIVE).build();
    private EmailChangeService service;

    @BeforeEach
    void setUp() {
        service = new EmailChangeService(userRepository, tokens, passwordEncoder, attempts, revoker, throttle,
            new EmailAddressRules(properties), properties, audit, events);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "$2a$12$hash")).thenReturn(true);
        when(throttle.claimForTransaction(anyString(), any())).thenReturn(true);
        when(tokens.issue(user, TokenPurpose.EMAIL_CHANGE, Duration.ofHours(1), NEW)).thenReturn("raw");
    }

    @Test
    void ac14_aOneHourLinkGoesToTheNewAddressAndOlderLinksStop() {
        service.request(12L, " Mover.New@CHNU.edu.ua ", PASSWORD);

        InOrder order = inOrder(tokens);
        order.verify(tokens).invalidate(user, TokenPurpose.EMAIL_CHANGE);
        order.verify(tokens).issue(user, TokenPurpose.EMAIL_CHANGE, Duration.ofHours(1), NEW);
        verify(throttle).claimForTransaction("auth:email-change:12", Duration.ofMinutes(1));
        verify(audit).record(AuditAction.EMAIL_CHANGE_REQUESTED, AuditEntityConstants.USER, 12L, 12L,
            Map.of("newEmail", NEW));
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new EmailChangeRequested(NEW, OLD, "Петро",
            "http://localhost:4200/confirm-email-change?token=raw", "http://localhost:4200/forgot-password"));
    }

    @Test
    void ac14_aWrongPasswordIs403AndCountsAsAFailedSignIn() {
        assertProblem(() -> service.request(12L, NEW, "wrong"), 403, "password-mismatch");

        verify(attempts).recordFailure(OLD, null);
        verify(revoker, never()).revokeAll(any());
        verifyNoInteractions(tokens, events);
    }

    @Test
    void edge_theFailureThatLocksTheAccountSignsItOutEverywhere() {
        when(attempts.recordFailure(OLD, null)).thenReturn(true);

        assertProblem(() -> service.request(12L, NEW, "wrong"), 403, "password-mismatch");

        verify(revoker).revokeAll(user);
        verifyNoInteractions(events);
    }

    @Test
    void edge_aLockedAddressCannotRequestAChangeNorGuessPasswords() {
        when(attempts.isLocked(OLD)).thenReturn(true);

        assertProblem(() -> service.request(12L, NEW, "wrong"), 423, "account-locked");
        verify(attempts, never()).recordFailure(any(), any());
        verifyNoInteractions(tokens, events, throttle);
    }

    @Test
    void ac14_anotherDomainIs422() {
        assertProblem(() -> service.request(12L, "mover@gmail.com", PASSWORD), 422,
            "institutional-email-required");
        verifyNoInteractions(tokens);
    }

    @Test
    void ac14_theCurrentAddressIs422() {
        assertProblem(() -> service.request(12L, "MOVER@chnu.edu.ua", PASSWORD), 422, "validation-failed");
        verifyNoInteractions(tokens);
    }

    @Test
    void ac14_anAddressInUseIs409() {
        when(userRepository.existsByEmailAddressIgnoreCase(NEW)).thenReturn(true);

        assertProblem(() -> service.request(12L, NEW, PASSWORD), 409, "email-taken");
        verifyNoInteractions(tokens, throttle);
    }

    @Test
    void ac14_aSecondRequestWithinAMinuteIs429() {
        when(throttle.claimForTransaction("auth:email-change:12", Duration.ofMinutes(1))).thenReturn(false);

        assertProblem(() -> service.request(12L, NEW, PASSWORD), 429, "too-many-requests");
        verifyNoInteractions(tokens, events);
    }

    @Test
    void ac15_ac16_theLinkMovesTheAccountAndEndsEverySession() {
        when(tokens.redeem("raw", TokenPurpose.EMAIL_CHANGE)).thenReturn(Optional.of(token()));
        when(tokens.issue(user, TokenPurpose.SECURITY_REVOKE, Duration.ofHours(24), OLD)).thenReturn("back");

        assertThat(service.confirm("raw")).isEqualTo(new EmailChangeResponse(12L, NEW));

        assertThat(user.getEmailAddress()).isEqualTo(NEW);
        InOrder order = inOrder(userRepository, revoker);
        order.verify(userRepository).saveAndFlush(user);
        order.verify(revoker).revokeAll(12L, OLD);
        verify(tokens).invalidate(user, TokenPurpose.EMAIL_CHANGE);
        verify(tokens).invalidate(user, TokenPurpose.PASSWORD_RESET);
        verify(audit).record(AuditAction.EMAIL_CHANGED, AuditEntityConstants.USER, 12L, 12L,
            Map.of("oldEmail", OLD, "newEmail", NEW));
        verify(events).publishEvent(new EmailChanged(OLD, NEW, "Петро",
            "http://localhost:4200/security/not-me?token=back"));
    }

    @Test
    void ac5_notMeLinksOfTheOldAddressStayValidAndMoveTheAccountBack() {
        when(tokens.redeem("raw", TokenPurpose.EMAIL_CHANGE)).thenReturn(Optional.of(token()));

        service.confirm("raw");

        verify(tokens).bindAddress(user, TokenPurpose.SECURITY_REVOKE, OLD);
        verify(tokens).issue(user, TokenPurpose.SECURITY_REVOKE, Duration.ofHours(24), OLD);
        verify(tokens, never()).invalidate(user, TokenPurpose.SECURITY_REVOKE);
    }

    @Test
    void edge_aLockedAddressIsNotMovedAndTheLinkIs410() {
        when(tokens.redeem("raw", TokenPurpose.EMAIL_CHANGE)).thenReturn(Optional.of(token()));
        when(attempts.isLocked(OLD)).thenReturn(true);

        assertProblem(() -> service.confirm("raw"), 410, "token-invalid");
        assertThat(user.getEmailAddress()).isEqualTo(OLD);
        verifyNoInteractions(revoker, events);
    }

    @Test
    void ac15_aUsedOrExpiredLinkIs410() {
        when(tokens.redeem("raw", TokenPurpose.EMAIL_CHANGE)).thenReturn(Optional.empty());

        assertProblem(() -> service.confirm("raw"), 410, "token-invalid");
        assertThat(user.getEmailAddress()).isEqualTo(OLD);
    }

    @Test
    void edge_anAddressTakenSinceTheRequestIs409AndKeepsTheOldOne() {
        when(tokens.redeem("raw", TokenPurpose.EMAIL_CHANGE)).thenReturn(Optional.of(token()));
        when(userRepository.existsByEmailAddressIgnoreCase(NEW)).thenReturn(true);

        assertProblem(() -> service.confirm("raw"), 409, "email-taken");
        assertThat(user.getEmailAddress()).isEqualTo(OLD);
        verifyNoInteractions(revoker, events);
    }

    @Test
    void edge_anAddressTakenAtTheSameMomentIs409() {
        when(tokens.redeem("raw", TokenPurpose.EMAIL_CHANGE)).thenReturn(Optional.of(token()));
        when(userRepository.saveAndFlush(user)).thenThrow(new DataIntegrityViolationException("uk"));

        assertProblem(() -> service.confirm("raw"), 409, "email-taken");
        verifyNoInteractions(revoker, events);
    }

    @Test
    void edge_anAccountThatIsNoLongerActiveIsNotMoved() {
        user.setAccountStatus(AccountStatus.SUSPENDED);
        when(tokens.redeem("raw", TokenPurpose.EMAIL_CHANGE)).thenReturn(Optional.of(token()));

        assertProblem(() -> service.confirm("raw"), 409, "account-not-active");
        assertThat(user.getEmailAddress()).isEqualTo(OLD);
    }

    private OneTimeToken token() {
        return OneTimeToken.builder().user(user).purpose(TokenPurpose.EMAIL_CHANGE).newEmailAddress(NEW).build();
    }

    private static void assertProblem(Runnable call, int status, String type) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiProblemException.class, problem -> {
            assertThat(problem.getStatus().value()).isEqualTo(status);
            assertThat(problem.getType()).isEqualTo(type);
        });
    }
}

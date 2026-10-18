package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.service.LoginAttemptService;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.gdpr.config.ErasureProperties;
import ua.edu.chnu.awards.gdpr.dto.ErasureRequest;
import ua.edu.chnu.awards.gdpr.event.ErasureCancelled;
import ua.edu.chnu.awards.gdpr.event.ErasureRequested;
import ua.edu.chnu.awards.gdpr.event.ErasureWarned;
import ua.edu.chnu.awards.gdpr.repository.ErasureRequests;
import ua.edu.chnu.awards.gdpr.repository.LegalHolds;
import ua.edu.chnu.awards.user.AccountSessions;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.service.UserAccounts;

class ErasureRequestServiceTest {

    private static final long USER_ID = 12L;
    private static final long REQUEST_ID = 4L;
    private static final String EMAIL = "leaver@chnu.edu.ua";
    private static final String PASSWORD = "Passw0rd-demo";
    private static final String HASH = "$2a$12$hash";
    private static final Duration GRACE_PERIOD = Duration.ofDays(30);
    private static final Duration WARNING_BEFORE = Duration.ofDays(7);
    private static final Instant NOW = Instant.parse("2026-10-16T08:00:00Z");
    private static final Instant DUE = NOW.plus(GRACE_PERIOD);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ErasureRequests requests = mock(ErasureRequests.class);
    private final LegalHolds holds = mock(LegalHolds.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final AccountSessions sessions = mock(AccountSessions.class);
    private final AuditService audit = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final User user = User.builder().id(USER_ID).emailAddress(EMAIL).firstName("Петро").passwordHash(HASH)
        .accountStatus(AccountStatus.ACTIVE).build();
    private final ErasureRequestService service = new ErasureRequestService(new UserAccounts(userRepository),
        requests, new ErasureGuard(holds, requests, mock(AccountRetentionRule.class)), passwordEncoder, attempts,
        sessions, new ErasureProperties(GRACE_PERIOD, WARNING_BEFORE), audit, events,
        Clock.fixed(NOW, ZoneId.of("Europe/Kyiv")));

    @BeforeEach
    void setUp() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(requests.create(USER_ID, NOW, DUE)).thenReturn(REQUEST_ID);
    }

    @Test
    void ac4_2_aRequestIsScheduledAfterTheGracePeriodAuditedAndMailed() {
        assertThat(service.request(USER_ID, PASSWORD)).isEqualTo(new ErasureRequest(REQUEST_ID, NOW, DUE, false));

        verify(audit).record(AuditAction.ERASURE_REQUESTED, AuditEntityConstants.GDPR, USER_ID, USER_ID,
            Map.of("requestId", REQUEST_ID, "scheduledFor", DUE.toString()));
        verify(events).publishEvent(new ErasureRequested(EMAIL, "Петро", DUE));
    }

    @Test
    void ac4_3_aLockedAccountIsRefusedBeforeThePasswordIsChecked() {
        when(attempts.isLocked(EMAIL)).thenReturn(true);

        assertProblem(() -> service.request(USER_ID, PASSWORD), HttpStatus.LOCKED, "account-locked");

        verifyNoInteractions(passwordEncoder);
        assertNothingWritten();
    }

    @Test
    void ac4_3_aWrongPasswordCountsAsAFailedSignIn() {
        assertProblem(() -> service.request(USER_ID, "wrong"), HttpStatus.FORBIDDEN, "password-mismatch");

        verify(attempts).recordFailure(EMAIL, null);
        verify(sessions, never()).signOutEverywhere(any());
        assertNothingWritten();
    }

    @Test
    void ac4_3_theFailureThatLocksTheAccountSignsItOut() {
        when(attempts.recordFailure(EMAIL, null)).thenReturn(true);

        assertProblem(() -> service.request(USER_ID, "wrong"), HttpStatus.FORBIDDEN, "password-mismatch");

        verify(sessions).signOutEverywhere(user);
        assertNothingWritten();
    }

    @Test
    void ac4_3_aSecondRequestIsRefusedWhileOneIsPending() {
        when(requests.pending(USER_ID)).thenReturn(Optional.of(new ErasureRequest(1L, NOW, DUE, false)));

        assertProblem(() -> service.request(USER_ID, PASSWORD), HttpStatus.CONFLICT, "erasure-pending");

        assertNothingWritten();
    }

    @Test
    void ac4_3_anActiveLegalHoldRefusesWithoutTheReason() {
        when(holds.isActive(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.request(USER_ID, PASSWORD))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getType()).isEqualTo("legal-hold");
                assertThat(problem.getMessage()).doesNotContainIgnoringCase("hold");
            });

        assertNothingWritten();
    }

    @Test
    void ac4_3_theOnlySystemAdministratorIsRefused() {
        when(requests.isOnlyAdministrator(USER_ID, LocalDate.of(2026, 10, 16))).thenReturn(true);

        assertProblem(() -> service.request(USER_ID, PASSWORD), HttpStatus.CONFLICT, "last-administrator");

        assertNothingWritten();
    }

    @Test
    void ac4_4_aPendingRequestIsCancelledAuditedAndMailed() {
        when(requests.cancel(USER_ID, NOW)).thenReturn(Optional.of(REQUEST_ID));

        service.cancel(USER_ID);

        verify(audit).record(AuditAction.ERASURE_CANCELLED, AuditEntityConstants.GDPR, USER_ID, USER_ID,
            Map.of("requestId", REQUEST_ID));
        verify(events).publishEvent(new ErasureCancelled(EMAIL, "Петро"));
    }

    @Test
    void ac4_4_cancellingWithoutAPendingRequestIsRefused() {
        when(requests.cancel(USER_ID, NOW)).thenReturn(Optional.empty());

        assertProblem(() -> service.cancel(USER_ID), HttpStatus.CONFLICT, "erasure-not-pending");

        verifyNoInteractions(audit, events);
    }

    @Test
    void ac4_6_eachDueRequestIsMarkedAndReminded() {
        ErasureRequests.Due due = new ErasureRequests.Due(REQUEST_ID, DUE, EMAIL, "Петро");
        when(requests.dueForWarning(NOW, WARNING_BEFORE)).thenReturn(List.of(due));

        assertThat(service.warnDue(NOW)).isEqualTo(1);

        verify(requests).markWarned(REQUEST_ID, NOW);
        verify(events).publishEvent(new ErasureWarned(EMAIL, "Петро", DUE));
    }

    private void assertNothingWritten() {
        verify(requests, never()).create(anyLong(), any(), any());
        verifyNoInteractions(audit, events);
    }

    private static void assertProblem(Runnable call, HttpStatus status, String type) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiProblemException.class, problem -> {
            assertThat(problem.getStatus()).isEqualTo(status);
            assertThat(problem.getType()).isEqualTo(type);
        });
    }
}

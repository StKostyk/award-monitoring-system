package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditRedaction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.common.erasure.ErasureContext;
import ua.edu.chnu.awards.common.erasure.ErasureContributor;
import ua.edu.chnu.awards.gdpr.event.ErasureCompleted;
import ua.edu.chnu.awards.gdpr.event.ErasurePostponed;
import ua.edu.chnu.awards.gdpr.repository.ErasureRequests;
import ua.edu.chnu.awards.gdpr.repository.LegalHolds;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

class ErasureRunnerTest {

    private static final Instant NOW = Instant.parse("2026-10-16T00:30:00Z");
    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    private static final long REQUEST_ID = 7L;
    private static final long USER_ID = 42L;
    private static final String EMAIL = "petro@chnu.edu.ua";

    private final ErasureRequests requests = mock(ErasureRequests.class);
    private final LegalHolds holds = mock(LegalHolds.class);
    private final UserAccounts users = mock(UserAccounts.class);
    private final AuditService audit = mock(AuditService.class);
    private final AuditRedaction redaction = mock(AuditRedaction.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final List<String> calls = new ArrayList<>();
    private final User user = User.builder().id(USER_ID).emailAddress(EMAIL).firstName("Петро").lastName("Іваненко")
        .build();

    private ErasureRunner runner(ErasureContributor... contributors) {
        return new ErasureRunner(requests, new ErasureGuard(holds, requests), users, List.of(contributors), audit,
            redaction, events, new TransactionTemplate(transactionManager), new ErasureMetrics(meters),
            Clock.fixed(NOW, KYIV));
    }

    @BeforeEach
    void dueRequest() {
        when(requests.due(NOW)).thenReturn(List.of(REQUEST_ID));
        when(requests.lockDue(REQUEST_ID, NOW)).thenReturn(Optional.of(new ErasureRequests.Locked(REQUEST_ID,
            USER_ID)));
        when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(redaction.formerNames(USER_ID)).thenReturn(List.of("Петро Іваненко", "Петро Коваль"));
    }

    @Test
    void ac5_1_everyContributorRunsInItsOrderAndTheErasureIsAuditedRedactedAndCompleted() {
        int erased = runner(contributor(90, "account"), contributor(10, "auth"), contributor(40, "awards"))
            .runDue(NOW);

        assertThat(erased).isOne();
        assertThat(calls).containsExactly("auth", "awards", "account");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        InOrder order = inOrder(requests, audit, redaction, events);
        order.verify(requests).serialize();
        order.verify(audit).record(eq(AuditAction.ERASURE_COMPLETED), eq(AuditEntityConstants.GDPR), eq(USER_ID),
            eq(USER_ID), details.capture());
        order.verify(redaction).redactSubject(eq(USER_ID), any(), any());
        order.verify(requests).markCompleted(REQUEST_ID, NOW);
        order.verify(events).publishEvent(new ErasureCompleted(EMAIL, "Петро"));
        assertThat(details.getValue()).containsEntry("requestId", REQUEST_ID).containsEntry("auth", 1)
            .containsEntry("account", 1);
        assertThat(meters.counter(ErasureMetrics.RUNS, "outcome", "completed").count()).isEqualTo(1.0);
    }

    @Test
    void ac5_1_theContextCarriesTheCapturedAddressTheDayAndEveryNameTheAccountHeld() {
        List<ErasureContext> seen = new ArrayList<>();
        ErasureContributor capture = mock(ErasureContributor.class);
        doAnswer(invocation -> seen.add(invocation.getArgument(0))).when(capture).erase(any());

        runner(capture).runDue(NOW);

        ErasureContext context = seen.getFirst();
        assertThat(context.userId()).isEqualTo(USER_ID);
        assertThat(context.emailAddress()).isEqualTo(EMAIL);
        assertThat(context.day()).isEqualTo(TODAY);
        assertThat(context.names()).containsExactly("Петро Іваненко", "Петро Коваль");
        assertThat(context.pseudonym()).isEqualTo("Erased #42");
        assertThat(context.erasedEmailAddress()).isEqualTo("erased-42@erased.invalid");
    }

    @Test
    void ac5_2_anActiveLegalHoldPostponesOnceWithAMailAndErasesNothing() {
        when(holds.isActive(USER_ID)).thenReturn(true);
        when(requests.markPostponed(REQUEST_ID, NOW)).thenReturn(true, false);
        ErasureRunner runner = runner(contributor(10, "auth"));

        assertThat(runner.runDue(NOW)).isZero();
        assertThat(runner.runDue(NOW)).isZero();

        assertThat(calls).isEmpty();
        verify(audit).record(AuditAction.ERASURE_POSTPONED, AuditEntityConstants.GDPR, USER_ID, USER_ID,
            Map.of("requestId", REQUEST_ID, "reason", "legal-hold"));
        verify(events).publishEvent(new ErasurePostponed(EMAIL, "Петро"));
        verify(requests, never()).markCompleted(REQUEST_ID, NOW);
    }

    @Test
    void ac5_2_theOnlySystemAdministratorIsPostponedWithoutAMail() {
        when(requests.isOnlyAdministrator(USER_ID, TODAY)).thenReturn(true);
        when(requests.markPostponed(REQUEST_ID, NOW)).thenReturn(true);

        assertThat(runner(contributor(10, "auth")).runDue(NOW)).isZero();

        assertThat(calls).isEmpty();
        verify(audit).record(AuditAction.ERASURE_POSTPONED, AuditEntityConstants.GDPR, USER_ID, USER_ID,
            Map.of("requestId", REQUEST_ID, "reason", "last-administrator"));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void ac5_10_aFailingContributorRollsBackCountsTheFailureAndLeavesTheRequestPending() {
        ErasureContributor failing = mock(ErasureContributor.class);
        when(failing.order()).thenReturn(50);
        doAnswer(invocation -> {
            throw new IllegalStateException("storage");
        }).when(failing).erase(any());

        assertThat(runner(contributor(10, "auth"), failing, contributor(90, "account")).runDue(NOW)).isZero();

        assertThat(calls).containsExactly("auth");
        verify(transactionManager).rollback(any());
        verify(requests, never()).markCompleted(REQUEST_ID, NOW);
        verify(audit, never()).record(eq(AuditAction.ERASURE_COMPLETED), any(), any(), any(), anyMap());
        verify(events, never()).publishEvent(any(Object.class));
        assertThat(meters.counter(ErasureMetrics.RUNS, "outcome", "failed").count()).isEqualTo(1.0);
    }

    @Test
    void ac5_1_aRequestLockedElsewhereOrCancelledMeanwhileIsSkipped() {
        when(requests.lockDue(REQUEST_ID, NOW)).thenReturn(Optional.empty());

        assertThat(runner(contributor(10, "auth")).runDue(NOW)).isZero();

        assertThat(calls).isEmpty();
        verify(users, never()).findByIdForUpdate(USER_ID);
    }

    private ErasureContributor contributor(int order, String name) {
        return new ErasureContributor() {
            @Override
            public int order() {
                return order;
            }

            @Override
            public void erase(ErasureContext context) {
                calls.add(name);
                context.count(name, 1);
            }
        };
    }
}

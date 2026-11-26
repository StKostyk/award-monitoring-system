package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.common.retention.RetentionContext;
import ua.edu.chnu.awards.common.retention.RetentionPolicy;
import ua.edu.chnu.awards.common.retention.RetentionProperties;
import ua.edu.chnu.awards.common.retention.RetentionResult;
import ua.edu.chnu.awards.gdpr.dto.RetentionStatus;
import ua.edu.chnu.awards.gdpr.event.RetentionRunIncomplete;
import ua.edu.chnu.awards.gdpr.repository.LegalHolds;
import ua.edu.chnu.awards.gdpr.repository.RetentionRuns;
import ua.edu.chnu.awards.user.service.RoleHolders;

class RetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-10-18T01:00:00Z");
    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
    private static final long RUN_ID = 12L;
    private static final long INTERRUPTED_ID = 11L;
    private static final Instant INTERRUPTED_START = Instant.parse("2026-10-17T01:00:00Z");
    private static final int BATCH_SIZE = 2;
    private static final String OFFICER = "officer@chnu.edu.ua";

    private final RetentionRuns runs = mock(RetentionRuns.class);
    private final LegalHolds holds = mock(LegalHolds.class);
    private final RoleHolders roleHolders = mock(RoleHolders.class);
    private final AuditService audit = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final RetentionMetrics metrics = mock(RetentionMetrics.class);
    private final List<String> calls = new ArrayList<>();
    private final RetentionProperties properties = new RetentionProperties(BATCH_SIZE, Duration.ofDays(365),
        Duration.ofDays(30), Duration.ofDays(1), Duration.ofDays(730), 3, Duration.ofDays(30), Period.ofYears(7),
        Period.ofYears(7), Period.ofYears(7));

    private RetentionJob job(RetentionPolicy... policies) {
        return new RetentionJob(runs, holds, List.of(policies), new RetentionReport(runs, roleHolders, audit, events),
            new TransactionTemplate(transactionManager), metrics, properties,
            Clock.fixed(NOW, KYIV));
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void lockFree() {
        when(runs.exclusively(any())).thenAnswer(call -> Optional.of(((Supplier<Object>) call.getArgument(0)).get()));
        when(runs.interruptRunning(NOW)).thenReturn(List.of());
        when(runs.start(NOW)).thenReturn(RUN_ID);
        when(holds.activeHolders()).thenReturn(Set.of(5L));
        when(roleHolders.findCurrentEmailsByRole(RoleType.GDPR_OFFICER, LocalDate.of(2026, 10, 18)))
            .thenReturn(List.of(OFFICER));
    }

    @Test
    void ac1_2_interruptedRunsAreClosedBeforeTheRunStartsAndEveryPolicyRunsInItsOrder() {
        Optional<Long> run = job(policy("devices", 40, 1), policy("notifications", 10, 0),
            policy("authorizations", 30, 0)).run();

        assertThat(run).contains(RUN_ID);
        assertThat(calls).containsExactly("notifications", "authorizations", "devices");
        verify(runs).interruptRunning(NOW);
        verify(runs).start(NOW);
        verify(runs).finish(eq(RUN_ID), eq(RetentionStatus.COMPLETED), anyList(), eq(NOW));
    }

    @Test
    void ac1_2_aPolicyWorksInBatchesOfTheBatchSizeAndItsCountsAreRecorded() {
        Iterator<Integer> batches = List.of(2, 2, 1).iterator();
        RetentionPolicy tokens = policy("one-time-tokens", 20,
            context -> RetentionResult.completed("one-time-tokens", context.inBatches(batches::next), 0));

        job(tokens).run();

        assertThat(batches).isExhausted();
        assertThat(finished(RetentionStatus.COMPLETED))
            .containsExactly(new RetentionResult("one-time-tokens", 5, 0, false, null));
    }

    @Test
    void ac1_2_theContextCarriesTheHoldsReadOnceAtTheStartAndTheBatchSize() {
        List<RetentionContext> seen = new ArrayList<>();
        RetentionPolicy first = policy("notifications", 10, context -> {
            seen.add(context);
            return RetentionResult.completed("notifications", 0, 0);
        });
        RetentionPolicy second = policy("devices", 40, context -> {
            seen.add(context);
            return RetentionResult.completed("devices", 0, 0);
        });

        job(first, second).run();

        assertThat(seen).extracting(RetentionContext::heldUserIds).containsOnly(Set.of(5L));
        assertThat(seen).extracting(RetentionContext::batchSize).containsOnly(BATCH_SIZE);
        assertThat(seen).extracting(RetentionContext::now).containsOnly(NOW);
        verify(holds).activeHolders();
    }

    @Test
    void ac1_2_eachPolicysResultsAreSavedAsItCompletes() {
        job(policy("notifications", 10, 3), policy("devices", 40, 1)).run();

        verify(runs).progress(RUN_ID, List.of(RetentionResult.completed("notifications", 3, 0)));
        verify(runs).progress(RUN_ID, List.of(RetentionResult.completed("notifications", 3, 0),
            RetentionResult.completed("devices", 1, 0)));
    }

    @Test
    void ac1_3_aFailingPolicyKeepsItsCommittedBatchesNamesTheErrorClassOnlyAndTheOthersStillRun() {
        Iterator<Integer> batches = List.of(2, 2).iterator();
        RetentionPolicy failing = policy("one-time-tokens", 20, context -> {
            context.inBatches(() -> {
                if (!batches.hasNext()) {
                    throw new IllegalStateException("token of petro@chnu.edu.ua");
                }
                return batches.next();
            });
            return RetentionResult.completed("one-time-tokens", 0, 0);
        });

        job(failing, policy("devices", 40, 1)).run();

        List<RetentionResult> results = finished(RetentionStatus.PARTIAL);
        assertThat(results).containsExactly(
            new RetentionResult("one-time-tokens", 4, 0, true, IllegalStateException.class.getName()),
            RetentionResult.completed("devices", 1, 0));
        assertThat(results.toString()).doesNotContain("petro");
    }

    @Test
    void ac1_3_theRunIsAuditedWithItsStatusAndCountsWithoutAnActor() {
        job(policy("notifications", 10, 3), failingPolicy("devices", 40)).run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq(AuditAction.RETENTION_RUN_COMPLETED), eq(AuditEntityConstants.RETENTION), isNull(),
            eq(RUN_ID), details.capture());
        assertThat(details.getValue()).containsEntry("status", "PARTIAL");
        assertThat(details.getValue().get("results")).asList().containsExactly(
            Map.of("policy", "notifications", "affected", 3, "held", 0, "failed", false),
            Map.of("policy", "devices", "affected", 0, "held", 0, "failed", true));
    }

    @Test
    void ac1_4_aPartialRunTellsEveryOfficerWhichPoliciesFailed() {
        when(roleHolders.findCurrentEmailsByRole(RoleType.GDPR_OFFICER, LocalDate.of(2026, 10, 18)))
            .thenReturn(List.of(OFFICER, "second@chnu.edu.ua"));

        job(policy("notifications", 10, 3), failingPolicy("devices", 40)).run();

        verify(events).publishEvent(new RetentionRunIncomplete(RUN_ID, NOW, RetentionStatus.PARTIAL.name(),
            List.of("devices"), List.of(OFFICER, "second@chnu.edu.ua")));
    }

    @Test
    void ac1_4_runsFoundRunningAtTheStartAreReportedAsInterrupted() {
        when(runs.interruptRunning(NOW)).thenReturn(List.of(new RetentionRuns.Interrupted(INTERRUPTED_ID,
            INTERRUPTED_START, List.of("notifications"))));

        job(policy("devices", 40, 0)).run();

        verify(events).publishEvent(new RetentionRunIncomplete(INTERRUPTED_ID, INTERRUPTED_START,
            RetentionStatus.INTERRUPTED.name(), List.of("notifications"), List.of(OFFICER)));
        verify(events, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void ac1_4_aCompletedRunMailsNobody() {
        job(policy("notifications", 10, 3)).run();

        verifyNoInteractions(events);
    }

    @Test
    void ac1_4_withoutAnOfficerNothingIsPublished() {
        when(roleHolders.findCurrentEmailsByRole(RoleType.GDPR_OFFICER, LocalDate.of(2026, 10, 18)))
            .thenReturn(List.of());

        job(failingPolicy("devices", 40)).run();

        verifyNoInteractions(events);
        verify(runs).finish(eq(RUN_ID), eq(RetentionStatus.PARTIAL), anyList(), eq(NOW));
    }

    @Test
    void ac1_2_aRunStartedWhileAnotherHoldsTheLockIsSkippedWithoutARow() {
        doReturn(Optional.empty()).when(runs).exclusively(any());

        Optional<Long> run = job(policy("notifications", 10, 3)).run();

        assertThat(run).isEmpty();
        assertThat(calls).isEmpty();
        verify(runs, never()).start(any());
        verify(runs, never()).interruptRunning(any());
        verifyNoInteractions(audit);
    }

    @Test
    void ac1_10_theRunAndTheRowsOfEachPolicyAreCounted() {
        job(policy("notifications", 10, 3), failingPolicy("devices", 40)).run();

        verify(metrics).run(RetentionStatus.PARTIAL);
        verify(metrics).rows("notifications", 3);
        verify(metrics).rows("devices", 0);
    }

    private List<RetentionResult> finished(RetentionStatus status) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RetentionResult>> results = ArgumentCaptor.forClass(List.class);
        verify(runs).finish(eq(RUN_ID), eq(status), results.capture(), eq(NOW));
        return results.getValue();
    }

    private RetentionPolicy policy(String key, int order, int affected) {
        return policy(key, order, context -> RetentionResult.completed(key, affected, 0));
    }

    private RetentionPolicy policy(String key, int order, Function<RetentionContext, RetentionResult> body) {
        return new FakeRetentionPolicy(key, order, body, calls);
    }

    private RetentionPolicy failingPolicy(String key, int order) {
        return policy(key, order, context -> {
            throw new IllegalStateException("broken");
        });
    }
}

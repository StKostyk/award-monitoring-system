package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.common.retention.RetentionProperties;
import ua.edu.chnu.awards.gdpr.dto.ErasureOrigin;
import ua.edu.chnu.awards.gdpr.repository.AccountRetentionCandidates;
import ua.edu.chnu.awards.gdpr.repository.ErasureRequests;

class AccountRetentionRuleTest {

    private static final Instant NOW = Instant.parse("2026-10-19T00:30:00Z");
    private static final long REQUEST_ID = 7L;
    private static final long USER_ID = 42L;

    private final AccountRetentionCandidates candidates = mock(AccountRetentionCandidates.class);
    private final ErasureRequests requests = mock(ErasureRequests.class);
    private final AuditService audit = mock(AuditService.class);
    private final AccountRetentionRule rule = new AccountRetentionRule(candidates, new RetentionProperties(1000,
        Duration.ofDays(365), Duration.ofDays(30), Duration.ofDays(1), Duration.ofDays(730), 3, Duration.ofDays(30),
        Period.ofYears(7), Period.ofYears(7), Period.ofYears(7)), requests, audit);

    @Test
    void ac3_4_theConditionIsCheckedWithBothCutOffs() {
        when(candidates.applies(anyLong(), any(), any())).thenReturn(true);

        assertThat(rule.cancelIfLapsed(locked(ErasureOrigin.RETENTION), NOW)).isFalse();

        verify(candidates).applies(USER_ID, Instant.parse("2026-09-19T00:30:00Z"),
            Instant.parse("2019-10-19T00:30:00Z"));
        verify(requests, never()).cancelRequest(anyLong(), any());
    }

    @Test
    void ac3_4_aLapsedRetentionRequestIsCancelledAndAudited() {
        assertThat(rule.cancelIfLapsed(locked(ErasureOrigin.RETENTION), NOW)).isTrue();

        verify(requests).cancelRequest(REQUEST_ID, NOW);
        verify(audit).record(eq(AuditAction.ERASURE_CANCELLED), eq(AuditEntityConstants.GDPR), eq(USER_ID),
            eq(USER_ID), eq(Map.of("requestId", REQUEST_ID, "origin", "retention", "reason", "no-longer-applies")));
    }

    @Test
    void ac3_4_aPersonsRequestIsNeitherCheckedNorSilent() {
        ErasureRequests.Locked request = locked(ErasureOrigin.REQUEST);

        assertThat(rule.cancelIfLapsed(request, NOW)).isFalse();

        verifyNoInteractions(candidates, requests, audit);
        assertThat(request.byPerson()).isTrue();
        assertThat(locked(ErasureOrigin.RETENTION).byPerson()).isFalse();
    }

    private static ErasureRequests.Locked locked(ErasureOrigin origin) {
        return new ErasureRequests.Locked(REQUEST_ID, USER_ID, origin);
    }
}

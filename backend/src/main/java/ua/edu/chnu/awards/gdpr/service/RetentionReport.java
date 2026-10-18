package ua.edu.chnu.awards.gdpr.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.common.retention.RetentionResult;
import ua.edu.chnu.awards.gdpr.dto.RetentionStatus;
import ua.edu.chnu.awards.gdpr.event.RetentionRunIncomplete;
import ua.edu.chnu.awards.gdpr.repository.RetentionRuns;
import ua.edu.chnu.awards.user.service.RoleHolders;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Closes retention runs: interrupted ones before a run starts, the current one when it ends, with the audit row and,
 * for a run that did not complete, the mail to the data protection officers. Joins the caller's transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionReport {

    private final RetentionRuns runs;
    private final RoleHolders roleHolders;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    /**
     * The addresses of the data protection officers.
     *
     * @param day the day their role must be valid on
     * @return the addresses
     */
    public List<String> officers(LocalDate day) {
        return roleHolders.findCurrentEmailsByRole(RoleType.GDPR_OFFICER, day);
    }

    /**
     * Marks the runs still working as interrupted and tells the officers about each.
     *
     * @param now      the moment they are closed
     * @param officers the addresses to tell
     */
    public void closeInterrupted(Instant now, List<String> officers) {
        for (RetentionRuns.Interrupted interrupted : runs.interruptRunning(now)) {
            log.warn("Retention run {} was interrupted", interrupted.runId());
            tell(officers, new RetentionRunIncomplete(interrupted.runId(), interrupted.startedAt(),
                RetentionStatus.INTERRUPTED.name(), interrupted.failedPolicies(), officers));
        }
    }

    /**
     * Closes the run, records the audit row with its counts and tells the officers when a policy failed.
     *
     * @param runId      the run
     * @param startedAt  when it started
     * @param outcome    {@code COMPLETED} or {@code PARTIAL}
     * @param results    the results of every policy
     * @param finishedAt when it ended
     * @param officers   the addresses to tell
     */
    public void finish(long runId, Instant startedAt, RetentionStatus outcome, List<RetentionResult> results,
                       Instant finishedAt, List<String> officers) {
        runs.finish(runId, outcome, results, finishedAt);
        audit.record(AuditAction.RETENTION_RUN_COMPLETED, AuditEntityConstants.RETENTION, null, runId,
            details(outcome, results));
        if (outcome == RetentionStatus.PARTIAL) {
            List<String> failed = results.stream().filter(RetentionResult::failed).map(RetentionResult::policy)
                .toList();
            tell(officers, new RetentionRunIncomplete(runId, startedAt, outcome.name(), failed, officers));
        }
    }

    private void tell(List<String> officers, RetentionRunIncomplete event) {
        if (officers.isEmpty()) {
            log.warn("Retention run {} did not complete and no data protection officer can be told", event.runId());
            return;
        }
        events.publishEvent(event);
    }

    private static Map<String, Object> details(RetentionStatus status, List<RetentionResult> results) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", status.name());
        details.put("results", results.stream()
            .map(result -> Map.<String, Object>of("policy", result.policy(), "affected", result.affected(),
                "held", result.held(), "failed", result.failed()))
            .toList());
        return details;
    }
}

package ua.edu.chnu.awards.gdpr.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.common.retention.RetentionContext;
import ua.edu.chnu.awards.common.retention.RetentionPolicy;
import ua.edu.chnu.awards.common.retention.RetentionProperties;
import ua.edu.chnu.awards.common.retention.RetentionResult;
import ua.edu.chnu.awards.gdpr.dto.RetentionStatus;
import ua.edu.chnu.awards.gdpr.repository.LegalHolds;
import ua.edu.chnu.awards.gdpr.repository.RetentionRuns;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The nightly retention run: closes runs left working by a stop, applies every retention policy in its order with
 * a transaction per batch, records the results, the audit row and the metrics, and tells the data protection
 * officers when a run did not complete.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionJob {

    private final RetentionRuns runs;
    private final LegalHolds holds;
    private final List<RetentionPolicy> policies;
    private final RetentionReport report;
    private final TransactionTemplate transactions;
    private final RetentionMetrics metrics;
    private final RetentionProperties properties;
    private final Clock clock;

    /**
     * One run, unless another run still holds the lock.
     *
     * @return the run, or empty when it was skipped
     */
    @Scheduled(cron = "${app.retention.cron:0 0 4 * * *}", zone = "Europe/Kyiv")
    public Optional<Long> run() {
        Optional<Long> run = runs.exclusively(this::runLocked);
        if (run.isEmpty()) {
            log.info("Retention run skipped: another run is still working");
        }
        return run;
    }

    private long runLocked() {
        Instant now = clock.instant();
        List<String> officers = report.officers(LocalDate.now(clock));
        long runId = Objects.requireNonNull(transactions.execute(status -> {
            report.closeInterrupted(now, officers);
            return runs.start(now);
        }));
        Set<Long> held = holds.activeHolders();
        List<RetentionResult> results = new ArrayList<>();
        policies.stream()
            .sorted(Comparator.comparingInt(RetentionPolicy::order))
            .forEach(policy -> {
                results.add(apply(policy, now, held));
                runs.progress(runId, List.copyOf(results));
            });
        RetentionStatus outcome = results.stream().anyMatch(RetentionResult::failed)
            ? RetentionStatus.PARTIAL : RetentionStatus.COMPLETED;
        Instant finishedAt = clock.instant();
        transactions.executeWithoutResult(status -> report.finish(runId, now, outcome, results, finishedAt,
            officers));
        metrics.run(outcome);
        log.info("Retention run {} ended {}: {}", runId, outcome, results);
        return runId;
    }

    private RetentionResult apply(RetentionPolicy policy, Instant now, Set<Long> held) {
        RetentionContext context = new RetentionContext(now, held, properties.batchSize(), transactions);
        RetentionResult result;
        try {
            result = policy.apply(context);
        } catch (Exception exception) {
            log.error("Retention policy {} failed after {} rows", policy.key(), context.affected(), exception);
            result = RetentionResult.failed(policy.key(), context.affected(), exception);
        }
        metrics.rows(policy.key(), result.affected());
        return result;
    }
}

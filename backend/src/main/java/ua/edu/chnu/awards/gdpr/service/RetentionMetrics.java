package ua.edu.chnu.awards.gdpr.service;

import java.util.Locale;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import ua.edu.chnu.awards.gdpr.dto.RetentionStatus;

import lombok.RequiredArgsConstructor;

/**
 * Counts retention runs by outcome and the rows of each policy, exposed as {@code award_retention_runs_total} and
 * {@code award_retention_rows_total}.
 */
@Component
@RequiredArgsConstructor
public class RetentionMetrics {

    static final String RUNS = "award.retention.runs";
    static final String ROWS = "award.retention.rows";

    private final MeterRegistry registry;

    /**
     * Counts a finished run.
     *
     * @param status how it ended
     */
    public void run(RetentionStatus status) {
        Counter.builder(RUNS)
            .description("Retention runs by outcome")
            .tag("outcome", status.name().toLowerCase(Locale.ROOT))
            .register(registry)
            .increment();
    }

    /**
     * Counts the rows a policy affected in a run.
     *
     * @param policy the policy key
     * @param rows   the rows, or partitions, affected
     */
    public void rows(String policy, int rows) {
        Counter.builder(ROWS)
            .description("Rows deleted, erased or partitions changed by retention policy")
            .tag("policy", policy)
            .register(registry)
            .increment(rows);
    }
}

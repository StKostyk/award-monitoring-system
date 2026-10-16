package ua.edu.chnu.awards.gdpr.service;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import lombok.RequiredArgsConstructor;

/**
 * Counts erasure runs per request by outcome, exposed as {@code award_erasure_runs_total}.
 */
@Component
@RequiredArgsConstructor
public class ErasureMetrics {

    static final String RUNS = "award.erasure.runs";

    private final MeterRegistry registry;

    /**
     * Counts an erasure carried out.
     */
    public void completed() {
        count("completed");
    }

    /**
     * Counts an erasure rolled back by a failure.
     */
    public void failed() {
        count("failed");
    }

    private void count(String outcome) {
        Counter.builder(RUNS)
            .description("Erasure runs per request by outcome")
            .tag("outcome", outcome)
            .register(registry)
            .increment();
    }
}

package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import ua.edu.chnu.awards.gdpr.dto.RetentionStatus;

class RetentionMetricsTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RetentionMetrics metrics = new RetentionMetrics(meters);

    @Test
    void ac1_10_runsAreCountedByOutcomeAndRowsByPolicy() {
        metrics.run(RetentionStatus.COMPLETED);
        metrics.run(RetentionStatus.PARTIAL);
        metrics.run(RetentionStatus.PARTIAL);
        metrics.rows("devices", 3);
        metrics.rows("devices", 2);

        assertThat(meters.get(RetentionMetrics.RUNS).tag("outcome", "completed").counter().count()).isOne();
        assertThat(meters.get(RetentionMetrics.RUNS).tag("outcome", "partial").counter().count()).isEqualTo(2.0);
        assertThat(meters.get(RetentionMetrics.ROWS).tag("policy", "devices").counter().count()).isEqualTo(5.0);
    }
}

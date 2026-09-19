package ua.edu.chnu.awards.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class BusinessMetricsServiceTest {

    private SimpleMeterRegistry registry;
    private BusinessMetricsService metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new BusinessMetricsService(registry);
    }

    @Test
    void countsSubmissionsByStatus() {
        metrics.recordAwardSubmissionSuccess();
        metrics.recordAwardSubmissionSuccess();
        metrics.recordAwardSubmissionFailure();

        assertThat(counter("award.submissions.total", "status", "success")).isEqualTo(2.0);
        assertThat(counter("award.submissions.total", "status", "failed")).isEqualTo(1.0);
    }

    @Test
    void countsApprovalsByDecisionAndLevel() {
        metrics.recordAwardApproval(true);
        metrics.recordAwardApproval(false);
        metrics.recordAwardApprovalWithLevel("DEAN", true);
        metrics.recordAwardApprovalWithLevel("DEAN", false);

        assertThat(counter("award.approvals.total", "decision", "approved")).isEqualTo(1.0);
        assertThat(counter("award.approvals.total", "decision", "rejected")).isEqualTo(1.0);
        assertThat(registry.get("award.approvals.total").tags("level", "DEAN", "decision", "approved")
            .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("award.approvals.total").tags("level", "DEAN", "decision", "rejected")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void tracksPendingRequestsGauge() {
        metrics.updatePendingAwardRequests(5);
        metrics.incrementPendingAwardRequests();
        metrics.decrementPendingAwardRequests();
        metrics.decrementPendingAwardRequests();

        assertThat(registry.get("award.requests.pending.total").gauge().value()).isEqualTo(4.0);
    }

    @Test
    void tracksActiveSessionsGauge() {
        metrics.updateActiveUserSessions(2);
        metrics.incrementActiveUserSessions();
        metrics.decrementActiveUserSessions();

        assertThat(registry.get("user.sessions.active").gauge().value()).isEqualTo(2.0);
    }

    @Test
    void timesDocumentProcessing() {
        Timer.Sample sample = metrics.startDocumentProcessing();
        metrics.stopDocumentProcessing(sample);
        String result = metrics.recordDocumentProcessing(() -> "parsed");
        metrics.recordDocumentProcessingFailure();

        assertThat(result).isEqualTo("parsed");
        assertThat(registry.get("document.processing.time").timer().count()).isEqualTo(2);
        assertThat(registry.get("document.processing.failures.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void timesWorkflowAndCountsRegistrations() {
        Timer.Sample sample = metrics.startWorkflowTimer();
        metrics.stopWorkflowTimer(sample);
        metrics.recordUserRegistration();

        assertThat(registry.get("award.workflow.time").timer().count()).isEqualTo(1);
        assertThat(registry.get("user.registrations.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void createsCustomMetersWithTags() {
        Counter counter = metrics.createCounter("custom.counter", "custom", "kind", "a", "orphan");
        counter.increment();
        Timer timer = metrics.createTimer("custom.timer", "custom timer");
        timer.record(() -> { });

        assertThat(registry.get("custom.counter").tags("kind", "a").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("custom.timer").timer().count()).isEqualTo(1);
    }

    private double counter(String name, String tagKey, String tagValue) {
        return registry.get(name).tags(tagKey, tagValue).counter().count();
    }
}

package ua.edu.chnu.award_monitoring_system.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Business metrics service for Award Monitoring System.
 * 
 * <p>Provides custom business metrics for monitoring award submissions,
 * approvals, document processing, and user activity.</p>
 * 
 * <p>Metrics exposed:</p>
 * <ul>
 *   <li>award.submissions.total - Total award submissions by status</li>
 *   <li>award.approvals.total - Total approvals by level and decision</li>
 *   <li>award.requests.pending - Current pending award requests</li>
 *   <li>document.processing.time - Document processing duration</li>
 *   <li>document.processing.failures - Document processing failures</li>
 *   <li>user.registrations.total - Total user registrations</li>
 *   <li>user.sessions.active - Current active user sessions</li>
 * </ul>
 * 
 * @author Stefan Kostyk
 * @since 0.15.0
 */
@Service
public class BusinessMetricsService {

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter awardSubmissionsSuccess;
    private final Counter awardSubmissionsFailed;
    private final Counter awardApprovalsApproved;
    private final Counter awardApprovalsRejected;
    private final Counter documentProcessingFailures;
    private final Counter userRegistrations;

    // Gauges (tracked values)
    private final AtomicLong pendingAwardRequests = new AtomicLong(0);
    private final AtomicLong activeUserSessions = new AtomicLong(0);

    // Timers
    private final Timer documentProcessingTimer;
    private final Timer awardWorkflowTimer;

    public BusinessMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.awardSubmissionsSuccess = Counter.builder("award.submissions.total")
                .description("Total award submissions")
                .tag("status", "success")
                .register(meterRegistry);

        this.awardSubmissionsFailed = Counter.builder("award.submissions.total")
                .description("Total award submissions")
                .tag("status", "failed")
                .register(meterRegistry);

        this.awardApprovalsApproved = Counter.builder("award.approvals.total")
                .description("Total award approvals")
                .tag("decision", "approved")
                .register(meterRegistry);

        this.awardApprovalsRejected = Counter.builder("award.approvals.total")
                .description("Total award approvals")
                .tag("decision", "rejected")
                .register(meterRegistry);

        this.documentProcessingFailures = Counter.builder("document.processing.failures.total")
                .description("Total document processing failures")
                .register(meterRegistry);

        this.userRegistrations = Counter.builder("user.registrations.total")
                .description("Total user registrations")
                .register(meterRegistry);

        // Initialize gauges
        Gauge.builder("award.requests.pending.total", pendingAwardRequests, AtomicLong::get)
                .description("Current pending award requests")
                .register(meterRegistry);

        Gauge.builder("user.sessions.active", activeUserSessions, AtomicLong::get)
                .description("Current active user sessions")
                .register(meterRegistry);

        // Initialize timers
        this.documentProcessingTimer = Timer.builder("document.processing.time")
                .description("Document processing time")
                .register(meterRegistry);

        this.awardWorkflowTimer = Timer.builder("award.workflow.time")
                .description("Award workflow completion time")
                .register(meterRegistry);
    }

    // === Award Submission Metrics ===

    public void recordAwardSubmissionSuccess() {
        awardSubmissionsSuccess.increment();
    }

    public void recordAwardSubmissionFailure() {
        awardSubmissionsFailed.increment();
    }

    // === Award Approval Metrics ===

    public void recordAwardApproval(boolean approved) {
        if (approved) {
            awardApprovalsApproved.increment();
        } else {
            awardApprovalsRejected.increment();
        }
    }

    public void recordAwardApprovalWithLevel(String level, boolean approved) {
        Counter.builder("award.approvals.total")
                .description("Total award approvals")
                .tag("level", level)
                .tag("decision", approved ? "approved" : "rejected")
                .register(meterRegistry)
                .increment();
    }

    // === Pending Requests Gauge ===

    public void updatePendingAwardRequests(long count) {
        pendingAwardRequests.set(count);
    }

    public void incrementPendingAwardRequests() {
        pendingAwardRequests.incrementAndGet();
    }

    public void decrementPendingAwardRequests() {
        pendingAwardRequests.decrementAndGet();
    }

    // === Document Processing Metrics ===

    public Timer.Sample startDocumentProcessing() {
        return Timer.start(meterRegistry);
    }

    public void stopDocumentProcessing(Timer.Sample sample) {
        sample.stop(documentProcessingTimer);
    }

    public <T> T recordDocumentProcessing(Supplier<T> supplier) {
        return documentProcessingTimer.record(supplier);
    }

    public void recordDocumentProcessingFailure() {
        documentProcessingFailures.increment();
    }

    // === User Metrics ===

    public void recordUserRegistration() {
        userRegistrations.increment();
    }

    public void updateActiveUserSessions(long count) {
        activeUserSessions.set(count);
    }

    public void incrementActiveUserSessions() {
        activeUserSessions.incrementAndGet();
    }

    public void decrementActiveUserSessions() {
        activeUserSessions.decrementAndGet();
    }

    // === Workflow Timing ===

    public Timer.Sample startWorkflowTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopWorkflowTimer(Timer.Sample sample) {
        sample.stop(awardWorkflowTimer);
    }

    // === Custom Metrics Creation ===

    public Counter createCounter(String name, String description, String... tags) {
        Counter.Builder builder = Counter.builder(name).description(description);
        for (int i = 0; i < tags.length - 1; i += 2) {
            builder.tag(tags[i], tags[i + 1]);
        }
        return builder.register(meterRegistry);
    }

    public Timer createTimer(String name, String description) {
        return Timer.builder(name)
                .description(description)
                .register(meterRegistry);
    }
}


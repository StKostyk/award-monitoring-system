package ua.edu.chnu.awards.support;

import java.time.Clock;
import java.time.Duration;

import ua.edu.chnu.awards.award.service.ApprovalPath;
import ua.edu.chnu.awards.award.service.StatusEstimator;
import ua.edu.chnu.awards.config.WorkflowProperties;

/**
 * The workflow timing of unit tests: the default review period of three days.
 */
public final class TestWorkflow {

    /** The default review period. */
    public static final Duration REVIEW_PERIOD = Duration.ofDays(3);

    private TestWorkflow() {
    }

    /**
     * An estimator on the system clock.
     *
     * @return the estimator
     */
    public static StatusEstimator estimator() {
        return estimator(Clock.systemUTC());
    }

    /**
     * An estimator on the given clock.
     *
     * @param clock the clock
     * @return the estimator
     */
    public static StatusEstimator estimator(Clock clock) {
        return estimator(clock, REVIEW_PERIOD);
    }

    public static StatusEstimator estimator(Clock clock, Duration reviewPeriod) {
        return new StatusEstimator(clock, new WorkflowProperties(reviewPeriod), new ApprovalPath());
    }
}

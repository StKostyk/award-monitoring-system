package ua.edu.chnu.awards.support;

import java.time.Clock;
import java.time.ZoneId;

import ua.edu.chnu.awards.award.service.ApprovalPath;
import ua.edu.chnu.awards.award.service.StatusEstimator;
import ua.edu.chnu.awards.config.WorkflowProperties;

/**
 * The workflow timing of unit tests: the default review period of three working days.
 */
public final class TestWorkflow {

    /** The default review period in working days. */
    public static final int REVIEW_WORKING_DAYS = 3;

    private TestWorkflow() {
    }

    /**
     * An estimator on the system clock in Kyiv time, as the application runs.
     *
     * @return the estimator
     */
    public static StatusEstimator estimator() {
        return estimator(Clock.system(ZoneId.of("Europe/Kyiv")));
    }

    /**
     * An estimator on the given clock.
     *
     * @param clock the clock
     * @return the estimator
     */
    public static StatusEstimator estimator(Clock clock) {
        return estimator(clock, REVIEW_WORKING_DAYS);
    }

    /**
     * An estimator on the given clock with the given review period.
     *
     * @param clock       the clock
     * @param workingDays the review period in working days
     * @return the estimator
     */
    public static StatusEstimator estimator(Clock clock, int workingDays) {
        return new StatusEstimator(clock, new WorkflowProperties(workingDays), new ApprovalPath());
    }
}

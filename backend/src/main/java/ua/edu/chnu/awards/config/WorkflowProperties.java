package ua.edu.chnu.awards.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Timing of the approval workflow.
 *
 * @param reviewPeriod how long one approval level has for its review, in calendar time
 */
@ConfigurationProperties(prefix = "app.workflow")
public record WorkflowProperties(@DefaultValue("3d") Duration reviewPeriod) {

    /**
     * Refuses a review period that is not positive, so the application never starts with deadlines in the past.
     *
     * @param reviewPeriod how long one approval level has for its review
     */
    public WorkflowProperties {
        if (reviewPeriod == null || reviewPeriod.isNegative() || reviewPeriod.isZero()) {
            throw new IllegalArgumentException("app.workflow.review-period must be positive: " + reviewPeriod);
        }
    }
}

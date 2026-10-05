package ua.edu.chnu.awards.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Timing of the approval workflow.
 *
 * @param reviewWorkingDays how many working days (Monday to Friday) one approval level has for its review
 */
@ConfigurationProperties(prefix = "app.workflow")
public record WorkflowProperties(@DefaultValue("3") int reviewWorkingDays) {

    /**
     * Refuses a review period that is not positive, so the application never starts with deadlines in the past.
     *
     * @param reviewWorkingDays how many working days one approval level has for its review
     */
    public WorkflowProperties {
        if (reviewWorkingDays <= 0) {
            throw new IllegalArgumentException("app.workflow.review-working-days must be positive: "
                + reviewWorkingDays);
        }
    }
}

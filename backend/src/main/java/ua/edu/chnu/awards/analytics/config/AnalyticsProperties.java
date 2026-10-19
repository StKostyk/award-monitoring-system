package ua.edu.chnu.awards.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits of the personal analytics.
 *
 * @param minGroupSize  the fewest active accounts a department needs before a rank is shown
 * @param timelineLimit the most awards on the timeline
 */
@ConfigurationProperties(prefix = "app.analytics")
public record AnalyticsProperties(@DefaultValue("5") int minGroupSize,
                                  @DefaultValue("500") int timelineLimit) {

    /**
     * Refuses limits that are not positive.
     *
     * @param minGroupSize  the fewest active accounts a department needs before a rank is shown
     * @param timelineLimit the most awards on the timeline
     */
    public AnalyticsProperties {
        if (minGroupSize < 1 || timelineLimit < 1) {
            throw new IllegalArgumentException("app.analytics.min-group-size and app.analytics.timeline-limit must "
                + "be positive: " + minGroupSize + ", " + timelineLimit);
        }
    }
}

package ua.edu.chnu.awards.analytics.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits of the personal, unit and university analytics.
 *
 * @param minGroupSize        the fewest active accounts a department needs before a rank is shown, and a unit
 *                            before its top performers are listed
 * @param timelineLimit       the most awards on the timeline
 * @param topPerformers       the most people in the top performers of a unit
 * @param unitCacheTtl        how long the aggregates of a unit and period stay cached
 * @param retentionStaleAfter how long after the start of the last retention run the compliance summary asks for
 *                            attention
 */
@ConfigurationProperties(prefix = "app.analytics")
public record AnalyticsProperties(@DefaultValue("5") int minGroupSize,
                                  @DefaultValue("500") int timelineLimit,
                                  @DefaultValue("10") int topPerformers,
                                  @DefaultValue("PT5M") Duration unitCacheTtl,
                                  @DefaultValue("P2D") Duration retentionStaleAfter) {

    /**
     * Refuses limits that are not positive.
     *
     * @param minGroupSize        the fewest active accounts a department needs before a rank is shown
     * @param timelineLimit       the most awards on the timeline
     * @param topPerformers       the most people in the top performers of a unit
     * @param unitCacheTtl        how long the aggregates of a unit and period stay cached
     * @param retentionStaleAfter how long after the last retention run started the summary asks for attention
     */
    public AnalyticsProperties {
        if (minGroupSize < 1 || timelineLimit < 1 || topPerformers < 1) {
            throw new IllegalArgumentException("app.analytics.min-group-size, timeline-limit and top-performers "
                + "must be positive: " + minGroupSize + ", " + timelineLimit + ", " + topPerformers);
        }
        if (!positive(unitCacheTtl) || !positive(retentionStaleAfter)) {
            throw new IllegalArgumentException("app.analytics.unit-cache-ttl and retention-stale-after must be "
                + "positive: " + unitCacheTtl + ", " + retentionStaleAfter);
        }
    }

    private static boolean positive(Duration duration) {
        return duration != null && duration.isPositive();
    }
}

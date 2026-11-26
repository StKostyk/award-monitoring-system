package ua.edu.chnu.awards.common.retention;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The catch-up of the nightly jobs at start.
 *
 * @param catchUpOnStart whether the erasure, retention and sweep jobs run once after a start
 * @param catchUpDelay   how long after the start they run
 */
@ConfigurationProperties(prefix = "app.jobs")
public record JobsProperties(@DefaultValue("false") boolean catchUpOnStart,
                             @DefaultValue("PT1M") Duration catchUpDelay) {

    /**
     * Refuses a negative delay.
     *
     * @param catchUpOnStart whether the erasure, retention and sweep jobs run once after a start
     * @param catchUpDelay   how long after the start they run
     */
    public JobsProperties {
        if (catchUpDelay.isNegative()) {
            throw new IllegalArgumentException("app.jobs.catch-up-delay must not be negative");
        }
    }
}

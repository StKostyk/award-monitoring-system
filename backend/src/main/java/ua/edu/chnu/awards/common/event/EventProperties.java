package ua.edu.chnu.awards.common.event;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Retry of the event publications whose listener did not complete.
 *
 * @param firstRun      delay of the first run of the retry job after the start
 * @param retryInterval pause between two runs of the retry job
 * @param retryAfter    how old an incomplete publication must be before the job resubmits it
 * @param giveUpAfter   how old an incomplete publication may be and still be resubmitted
 * @param keepFailed    how long an abandoned publication is kept for inspection before it is deleted
 */
@ConfigurationProperties(prefix = "app.events")
public record EventProperties(@DefaultValue("1m") Duration firstRun,
                              @DefaultValue("10m") Duration retryInterval,
                              @DefaultValue("10m") Duration retryAfter,
                              @DefaultValue("24h") Duration giveUpAfter,
                              @DefaultValue("30d") Duration keepFailed) {
}

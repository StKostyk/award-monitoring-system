package ua.edu.chnu.awards.gdpr.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Timing of account erasure.
 *
 * @param gracePeriod   how long after the request the account is erased
 * @param warningBefore how long before the erasure the reminder is mailed
 */
@ConfigurationProperties(prefix = "app.erasure")
public record ErasureProperties(@DefaultValue("P30D") Duration gracePeriod,
                                @DefaultValue("P7D") Duration warningBefore) {

    /**
     * Refuses periods that are not positive.
     *
     * @param gracePeriod   how long after the request the account is erased
     * @param warningBefore how long before the erasure the reminder is mailed
     */
    public ErasureProperties {
        if (!gracePeriod.isPositive() || !warningBefore.isPositive()) {
            throw new IllegalArgumentException("app.erasure.grace-period and app.erasure.warning-before must be "
                + "positive: " + gracePeriod + ", " + warningBefore);
        }
    }
}

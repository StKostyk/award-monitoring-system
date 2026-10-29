package ua.edu.chnu.awards.common.limit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Per-user request limits of the signed-in API.
 *
 * @param apiRequestsPerMinute    requests one user may send per minute to the API
 * @param exportRequestsPerMinute report and export downloads one user may start per minute, all such endpoints
 *                                together
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    @DefaultValue("600") int apiRequestsPerMinute,
    @DefaultValue("5") int exportRequestsPerMinute) {
}

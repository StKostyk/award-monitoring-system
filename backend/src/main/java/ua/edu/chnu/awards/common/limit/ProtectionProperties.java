package ua.edu.chnu.awards.common.limit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Brute-force protection: account lockout after repeated failures, per-address request limits and a per-account
 * limit on the sign-in form.
 *
 * @param maxFailures              failed logins within the window that lock the account
 * @param failureWindow            how long failures are remembered
 * @param lockDuration             how long a locked account refuses correct credentials
 * @param requestsPerMinute        requests one client address may send per minute to the authentication endpoints
 * @param publicRequestsPerMinute  requests one client address may send per minute to the public API
 * @param accountRequestsPerMinute sign-in posts naming one account that are accepted per minute
 */
@ConfigurationProperties(prefix = "app.auth.protection")
public record ProtectionProperties(
    @DefaultValue("5") int maxFailures,
    @DefaultValue("15m") Duration failureWindow,
    @DefaultValue("30m") Duration lockDuration,
    @DefaultValue("600") int requestsPerMinute,
    @DefaultValue("120") int publicRequestsPerMinute,
    @DefaultValue("10") int accountRequestsPerMinute) {
}

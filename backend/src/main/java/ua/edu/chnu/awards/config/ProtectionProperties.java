package ua.edu.chnu.awards.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Brute-force protection: account lockout after repeated failures and per-address request limits.
 *
 * @param maxFailures       failed logins within the window that lock the account
 * @param failureWindow     how long failures are remembered
 * @param lockDuration      how long a locked account refuses correct credentials
 * @param requestsPerMinute requests one client address may send per minute to the authentication endpoints
 */
@ConfigurationProperties(prefix = "app.auth.protection")
public record ProtectionProperties(
    @DefaultValue("5") int maxFailures,
    @DefaultValue("15m") Duration failureWindow,
    @DefaultValue("30m") Duration lockDuration,
    @DefaultValue("20") int requestsPerMinute) {
}

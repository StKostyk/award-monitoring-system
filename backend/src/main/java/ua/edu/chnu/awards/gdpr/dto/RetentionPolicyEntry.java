package ua.edu.chnu.awards.gdpr.dto;

/**
 * A retention policy as the compliance page shows it.
 *
 * @param key    the policy name, such as {@code notifications}
 * @param period the configured period, ISO 8601
 * @param action what the policy does, such as {@code delete}
 * @param order  the position in the run, lower first
 */
public record RetentionPolicyEntry(String key, String period, String action, int order) {
}

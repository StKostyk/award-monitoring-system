package ua.edu.chnu.awards.common.privacy;

/**
 * A person acknowledged a privacy notice version; listeners run inside the caller's transaction.
 *
 * @param userId  the person
 * @param version the acknowledged version
 */
public record PrivacyNoticeAcknowledged(long userId, String version) {
}

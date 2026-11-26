package ua.edu.chnu.awards.gdpr.event;

/**
 * A person cancelled the erasure of their account; they must be told once the transaction commits.
 *
 * @param email     recipient
 * @param firstName used in the greeting
 */
public record ErasureCancelled(String email, String firstName) {
}

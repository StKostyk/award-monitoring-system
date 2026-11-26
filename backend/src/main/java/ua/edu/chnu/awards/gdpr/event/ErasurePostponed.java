package ua.edu.chnu.awards.gdpr.event;

/**
 * The erasure of an account fell due while a legal hold was active and waits for the hold to be lifted; the
 * person must be told once the transaction commits.
 *
 * @param email     recipient
 * @param firstName used in the greeting
 */
public record ErasurePostponed(String email, String firstName) {
}

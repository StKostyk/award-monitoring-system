package ua.edu.chnu.awards.gdpr.event;

/**
 * An account was erased; the person must be told at the address captured before the scrub once the transaction
 * commits. The publication holds that address until the mail is sent and is then deleted.
 *
 * @param email     the address the account had before the erasure
 * @param firstName the first name it had, used in the greeting
 */
public record ErasureCompleted(String email, String firstName) {
}

package ua.edu.chnu.awards.auth.event;

/**
 * A "this was not me" link moved an account back to its previous sign-in address; the address it left is told
 * once the change commits.
 *
 * @param replacedEmail the address the account no longer signs in with, the recipient
 * @param restoredEmail the address the account signs in with again
 * @param firstName     used in the greeting
 */
public record EmailRestored(String replacedEmail, String restoredEmail, String firstName) {
}

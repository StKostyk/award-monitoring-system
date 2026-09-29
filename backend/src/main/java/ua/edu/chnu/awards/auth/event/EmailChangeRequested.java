package ua.edu.chnu.awards.auth.event;

/**
 * A sign-in address change was requested: once the surrounding transaction commits the confirmation link goes to
 * the new address and a warning goes to the current one, whose owner can stop the change by resetting the
 * password.
 *
 * @param email        the requested address, recipient of the link
 * @param currentEmail the address the account signs in with now, recipient of the warning
 * @param firstName    used in the greeting
 * @param link         the confirmation link containing the raw token
 * @param resetLink    the password reset page, which cancels a pending change
 */
public record EmailChangeRequested(String email, String currentEmail, String firstName, String link,
                                   String resetLink) {
}

package ua.edu.chnu.awards.auth.event;

/**
 * A sign-in address was changed; the previous address is told once the change commits.
 *
 * @param oldEmail   the previous address, the recipient
 * @param newEmail   the address the account now signs in with
 * @param firstName  used in the greeting
 * @param revokeLink "this was not me" link that moves the account back to the previous address
 */
public record EmailChanged(String oldEmail, String newEmail, String firstName, String revokeLink) {
}

package ua.edu.chnu.awards.auth.dto;

/**
 * Outcome of a confirmed address change.
 *
 * @param email the address the account signs in with from now on
 */
public record EmailChangeResponse(String email) {
}

package ua.edu.chnu.awards.auth.dto;

/**
 * Outcome of a confirmed address change.
 *
 * @param userId the account that moved, so a browser signed in as somebody else keeps its session
 * @param email  the address the account signs in with from now on
 */
public record EmailChangeResponse(long userId, String email) {
}

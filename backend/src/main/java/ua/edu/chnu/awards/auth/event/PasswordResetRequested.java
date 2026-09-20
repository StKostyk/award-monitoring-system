package ua.edu.chnu.awards.auth.event;

/**
 * A password reset email must be sent once the surrounding transaction commits.
 *
 * @param email     recipient
 * @param firstName used in the greeting
 * @param link      the reset link containing the raw token
 */
public record PasswordResetRequested(String email, String firstName, String link) {
}

package ua.edu.chnu.awards.auth.event;

/**
 * A verification email must be sent once the surrounding transaction commits.
 *
 * @param email     recipient
 * @param firstName used in the greeting
 * @param link      the verification link containing the raw token
 */
public record VerificationRequested(String email, String firstName, String link) {
}

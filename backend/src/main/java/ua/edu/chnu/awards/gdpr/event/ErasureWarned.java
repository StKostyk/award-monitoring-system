package ua.edu.chnu.awards.gdpr.event;

import java.time.Instant;

/**
 * The erasure of an account is near; the person must be reminded once the transaction commits.
 *
 * @param email        recipient
 * @param firstName    used in the greeting
 * @param scheduledFor when the account is erased
 */
public record ErasureWarned(String email, String firstName, Instant scheduledFor) {
}

package ua.edu.chnu.awards.gdpr.event;

import java.time.Instant;

/**
 * A person asked for the erasure of their account; they must be told once the transaction commits.
 *
 * @param email        recipient
 * @param firstName    used in the greeting
 * @param scheduledFor when the account is erased
 */
public record ErasureRequested(String email, String firstName, Instant scheduledFor) {
}

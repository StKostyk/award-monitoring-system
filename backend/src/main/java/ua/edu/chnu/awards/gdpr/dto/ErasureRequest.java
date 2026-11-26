package ua.edu.chnu.awards.gdpr.dto;

import java.time.Instant;

/**
 * A pending request to erase an account.
 *
 * @param id           the request
 * @param requestedAt  when the person asked
 * @param scheduledFor when the account is erased
 * @param postponed    true when a legal hold postponed the erasure past its date
 */
public record ErasureRequest(long id, Instant requestedAt, Instant scheduledFor, boolean postponed) {
}

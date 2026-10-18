package ua.edu.chnu.awards.gdpr.dto;

import java.time.Instant;

import ua.edu.chnu.awards.award.dto.UserRef;

/**
 * A legal hold of any account, as the compliance page lists it.
 *
 * @param id         identifier
 * @param person     the account under the hold
 * @param reason     why it was placed
 * @param placedAt   when it was placed
 * @param placedBy   who placed it
 * @param liftedAt   when it was lifted, null while active
 * @param liftedBy   who lifted it, null while active
 * @param liftReason why it was lifted, null while active
 */
public record LegalHoldEntry(long id, UserRef person, String reason, Instant placedAt, UserRef placedBy,
                             Instant liftedAt, UserRef liftedBy, String liftReason) {
}

package ua.edu.chnu.awards.gdpr.dto;

import java.time.Instant;

import ua.edu.chnu.awards.award.dto.UserRef;

/**
 * An erasure request of any account, as the compliance page lists it.
 *
 * @param id           identifier
 * @param person       the account, under its pseudonym once erased
 * @param origin       who filed the request
 * @param status       the state of the request
 * @param requestedAt  when it was filed
 * @param scheduledFor when the grace period ends
 * @param postponedAt  when a legal hold first postponed it, null before
 * @param cancelledAt  when it was cancelled, null otherwise
 * @param completedAt  when the account was erased, null otherwise
 */
public record ErasureRequestEntry(long id, UserRef person, ErasureOrigin origin, ErasureRequestStatus status,
                                  Instant requestedAt, Instant scheduledFor, Instant postponedAt,
                                  Instant cancelledAt, Instant completedAt) {
}

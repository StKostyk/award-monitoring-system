package ua.edu.chnu.awards.gdpr.dto;

import java.time.Instant;

/**
 * One audit event written by or about the caller, without values or network facts.
 *
 * @param action     the audit action code
 * @param occurredAt when it happened
 * @param entityType the audited area or table
 * @param entityId   the record concerned, or null
 * @param byMe       true when the caller performed it, false when it was about the caller
 */
public record ActivityEntry(String action, Instant occurredAt, String entityType, Long entityId, boolean byMe) {
}

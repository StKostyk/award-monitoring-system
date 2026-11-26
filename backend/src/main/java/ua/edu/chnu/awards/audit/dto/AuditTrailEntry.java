package ua.edu.chnu.awards.audit.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of an audit trail.
 *
 * @param id            audit row id
 * @param createdAt     when it was written
 * @param actorId       who acted, null for system changes
 * @param actorName     first and last name of the actor, null when unknown or erased
 * @param actorEmail    address of the actor, null when unknown or erased
 * @param action        {@code INSERT}, {@code UPDATE}, {@code DELETE} or an application event
 * @param entityType    table or area of the record
 * @param entityId      the record
 * @param changedFields columns changed by an update
 * @param oldValues     values before the change, empty when none
 * @param newValues     values after the change or the facts of an application event, empty when none
 * @param ipAddress     client address, null for trigger rows and system changes
 * @param correlationId request correlation id
 */
public record AuditTrailEntry(long id, Instant createdAt, Long actorId, String actorName, String actorEmail,
                              String action, String entityType, Long entityId, List<String> changedFields,
                              Map<String, Object> oldValues, Map<String, Object> newValues, String ipAddress,
                              UUID correlationId) {

    /**
     * Copies the collections; absent values become empty maps, and the values of a row may hold empty columns
     * as nulls.
     */
    public AuditTrailEntry {
        changedFields = List.copyOf(changedFields);
        oldValues = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNullElse(oldValues, Map.of())));
        newValues = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNullElse(newValues, Map.of())));
    }
}

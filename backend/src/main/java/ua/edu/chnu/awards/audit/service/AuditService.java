package ua.edu.chnu.awards.audit.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditLog;
import ua.edu.chnu.awards.audit.repository.AuditLogRepository;
import ua.edu.chnu.awards.common.web.ClientRequest;

import lombok.RequiredArgsConstructor;

/**
 * Writes security events to the audit trail with the client context of the current request.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    /**
     * Stores one event without extra facts.
     *
     * @param action what happened
     * @param userId the account concerned, null when unknown
     */
    @Transactional
    public void record(AuditAction action, Long userId) {
        record(action, userId, Map.of());
    }

    /**
     * Stores one event; joins the caller's transaction when there is one.
     *
     * @param action  what happened
     * @param userId  the account concerned, null when unknown
     * @param details extra facts kept as JSON (no secrets)
     */
    @Transactional
    public void record(AuditAction action, Long userId, Map<String, Object> details) {
        record(action, AuditLog.AUTHENTICATION, userId, details);
    }

    /**
     * Stores one event of a given entity type, joining the caller's transaction so the trail and the change it
     * describes stand or fall together.
     *
     * @param action     what happened
     * @param entityType the audited area, such as {@link AuditLog#AUTHORIZATION}
     * @param userId     the account concerned, null when unknown
     * @param details    extra facts kept as JSON (no secrets)
     */
    @Transactional
    public void record(AuditAction action, String entityType, Long userId, Map<String, Object> details) {
        write(action, entityType, userId, details);
    }

    /**
     * Stores one event of a given entity type in its own transaction, so a refusal that rolls the caller back
     * still leaves its trace.
     *
     * @param action     what happened
     * @param entityType the audited area, such as {@link AuditLog#AUTHORIZATION}
     * @param userId     the account concerned, null when unknown
     * @param details    extra facts kept as JSON (no secrets)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSeparately(AuditAction action, String entityType, Long userId,
                                 Map<String, Object> details) {
        write(action, entityType, userId, details);
    }

    private void write(AuditAction action, String entityType, Long userId, Map<String, Object> details) {
        ClientRequest client = ClientRequest.current();
        repository.save(AuditLog.builder()
            .userId(userId)
            .actionType(action.name())
            .entityType(entityType)
            .entityId(userId)
            .details(details)
            .ipAddress(client.address())
            .userAgent(client.userAgent())
            .correlationId(client.correlationId())
            .build());
    }
}

package ua.edu.chnu.awards.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ua.edu.chnu.awards.audit.entity.AuditLog;

/**
 * Access to {@link AuditLog} rows.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}

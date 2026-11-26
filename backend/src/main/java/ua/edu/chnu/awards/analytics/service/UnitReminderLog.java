package ua.edu.chnu.awards.analytics.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;

import lombok.RequiredArgsConstructor;

/**
 * The reminders sent to departments, read from their audit rows, and the lock that lets one reminder of a
 * department through at a time.
 */
@Component
@RequiredArgsConstructor
public class UnitReminderLog {

    private static final String LOCK = "select count(*) from (select pg_advisory_xact_lock(hashtext('unit-reminder'),"
        + " cast(:unitId as integer))) l";
    private static final String LAST_SENT = """
        select entity_id, max(created_at) as sent_at from audit_logs
        where action_type = :action and entity_type = :entityType and entity_id in (:unitIds)
          and created_at >= :since
        group by entity_id""";

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Takes the department's transaction-scoped advisory lock, so that the check of the last reminder and the
     * new one happen one sender at a time.
     *
     * @param unitId the department
     */
    public void lock(long unitId) {
        jdbc.queryForObject(LOCK, new MapSqlParameterSource("unitId", unitId), Long.class);
    }

    /**
     * The time of the last reminder of each department since a moment.
     *
     * @param unitIds the departments
     * @param since   the earliest time that counts
     * @return the time per department; departments without a reminder are absent
     */
    public Map<Long, Instant> lastSent(Collection<Long> unitIds, Instant since) {
        Map<Long, Instant> result = new HashMap<>();
        if (unitIds.isEmpty()) {
            return result;
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("action", AuditAction.UNIT_REMINDED.name())
            .addValue("entityType", AuditEntityConstants.ORGANIZATIONS)
            .addValue("unitIds", unitIds)
            .addValue("since", Timestamp.from(since));
        jdbc.query(LAST_SENT, parameters, row -> {
            result.put(row.getLong("entity_id"), row.getTimestamp("sent_at").toInstant());
        });
        return result;
    }
}

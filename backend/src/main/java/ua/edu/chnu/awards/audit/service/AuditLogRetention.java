package ua.edu.chnu.awards.audit.service;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.JdbcRowUtils;
import ua.edu.chnu.awards.common.retention.RetentionAction;
import ua.edu.chnu.awards.common.retention.RetentionContext;
import ua.edu.chnu.awards.common.retention.RetentionOrderConstants;
import ua.edu.chnu.awards.common.retention.RetentionPolicy;
import ua.edu.chnu.awards.common.retention.RetentionProperties;
import ua.edu.chnu.awards.common.retention.RetentionResult;

import lombok.RequiredArgsConstructor;

/**
 * Drops, through the guarded {@code fn_drop_expired_audit_partitions}, the monthly audit partitions that ended
 * longer ago than {@code app.retention.audit-logs}; a partition with a row by or about a held person is kept and
 * counted as held. Rows are never deleted one by one.
 */
@Component
@RequiredArgsConstructor
public class AuditLogRetention implements RetentionPolicy {

    private static final String DROP = """
        select jsonb_array_length(r -> 'dropped') as dropped, jsonb_array_length(r -> 'held') as held
          from fn_drop_expired_audit_partitions(:before, cast(:held as bigint[])) r
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RetentionProperties properties;

    @Override
    public String key() {
        return "audit-logs";
    }

    @Override
    public int order() {
        return RetentionOrderConstants.AUDIT_LOGS;
    }

    @Override
    public RetentionAction action() {
        return RetentionAction.DROP_PARTITIONS;
    }

    @Override
    public String period() {
        return RetentionPolicy.iso(properties.auditLogs());
    }

    @Override
    public RetentionResult apply(RetentionContext context) {
        MapSqlParameterSource params = new MapSqlParameterSource("before",
            JdbcRowUtils.utc(context.before(properties.auditLogs())))
            .addValue("held", context.heldArray());
        AtomicInteger held = new AtomicInteger();
        int dropped = context.inBatches(() -> Objects.requireNonNull(jdbc.queryForObject(DROP, params,
            (row, index) -> {
                held.set(row.getInt("held"));
                return row.getInt("dropped");
            })));
        return RetentionResult.completed(key(), dropped, held.get());
    }
}

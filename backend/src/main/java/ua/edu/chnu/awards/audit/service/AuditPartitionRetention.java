package ua.edu.chnu.awards.audit.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.retention.RetentionAction;
import ua.edu.chnu.awards.common.retention.RetentionContext;
import ua.edu.chnu.awards.common.retention.RetentionOrderConstants;
import ua.edu.chnu.awards.common.retention.RetentionPolicy;
import ua.edu.chnu.awards.common.retention.RetentionProperties;
import ua.edu.chnu.awards.common.retention.RetentionResult;

import lombok.RequiredArgsConstructor;

/**
 * Makes sure the audit trail has its monthly partitions from the current month to the configured months ahead.
 */
@Component
@RequiredArgsConstructor
public class AuditPartitionRetention implements RetentionPolicy {

    private static final String CREATE = "select fn_create_audit_partition(:month)";

    private final NamedParameterJdbcTemplate jdbc;
    private final RetentionProperties properties;
    private final Clock clock;

    @Override
    public String key() {
        return "audit-partitions";
    }

    @Override
    public int order() {
        return RetentionOrderConstants.AUDIT_PARTITIONS;
    }

    @Override
    public RetentionAction action() {
        return RetentionAction.CREATE_PARTITIONS;
    }

    @Override
    public String period() {
        return RetentionPolicy.iso(Period.ofMonths(properties.auditPartitionsAhead()));
    }

    @Override
    public RetentionResult apply(RetentionContext context) {
        LocalDate month = LocalDate.ofInstant(context.now(), clock.getZone()).withDayOfMonth(1);
        int created = context.inBatches(() -> (int) IntStream.rangeClosed(0, properties.auditPartitionsAhead())
            .filter(ahead -> Boolean.TRUE.equals(jdbc.queryForObject(CREATE,
                Map.of("month", month.plusMonths(ahead)), Boolean.class)))
            .count());
        return RetentionResult.completed(key(), created, 0);
    }
}

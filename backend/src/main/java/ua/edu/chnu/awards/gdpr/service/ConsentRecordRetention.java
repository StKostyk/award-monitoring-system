package ua.edu.chnu.awards.gdpr.service;

import java.time.Instant;
import java.util.Objects;

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
 * Deletes, through the guarded {@code fn_purge_consent_records}, the withdrawn ANALYTICS chains and the records of
 * erased accounts older than {@code app.retention.consent-records}; the acknowledgements of living accounts stay as
 * proof, and the records of held persons are counted as held.
 */
@Component
@RequiredArgsConstructor
class ConsentRecordRetention implements RetentionPolicy {

    private static final String PURGE = "select fn_purge_consent_records(:before, cast(:held as bigint[]))";

    private static final String HELD = """
        select count(distinct user_id) from fn_consent_purge_candidates(:before)
         where user_id = any (cast(:held as bigint[]))
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RetentionProperties properties;

    @Override
    public String key() {
        return "consent-records";
    }

    @Override
    public int order() {
        return RetentionOrderConstants.CONSENT_RECORDS;
    }

    @Override
    public RetentionAction action() {
        return RetentionAction.DELETE;
    }

    @Override
    public String period() {
        return RetentionPolicy.iso(properties.consentRecords());
    }

    @Override
    public RetentionResult apply(RetentionContext context) {
        Instant before = context.before(properties.consentRecords());
        MapSqlParameterSource params = new MapSqlParameterSource("before", JdbcRowUtils.utc(before))
            .addValue("held", context.heldArray());
        int held = Objects.requireNonNull(jdbc.queryForObject(HELD, params, Integer.class));
        int deleted = context.inBatches(() -> Objects.requireNonNull(jdbc.queryForObject(PURGE, params,
            Integer.class)));
        return RetentionResult.completed(key(), deleted, held);
    }
}

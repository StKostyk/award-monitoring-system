package ua.edu.chnu.awards.gdpr.repository;

import static ua.edu.chnu.awards.common.JdbcRowUtils.instant;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.gdpr.ComplianceFigures;
import ua.edu.chnu.awards.gdpr.dto.RetentionStatus;

import lombok.RequiredArgsConstructor;

/**
 * Reads the compliance counts in two statements: one for accounts, consents, erasure requests and holds, one for
 * the latest retention run.
 */
@Repository
@RequiredArgsConstructor
public class ComplianceCounts implements ComplianceFigures {

    private static final Duration ERASURE_GRACE = Duration.ofDays(1);

    private static final String COUNTS = """
        with active as (select user_id from users where account_status = 'ACTIVE'),
             latest as (select distinct on (c.user_id, c.consent_type) c.consent_type, c.consent_version,
                               c.is_granted
                          from consent_records c
                          join active a on a.user_id = c.user_id
                         where c.consent_type in ('DATA_PROCESSING', 'ANALYTICS')
                         order by c.user_id, c.consent_type, c.created_at desc, c.consent_id desc)
        select (select count(*) from active) as accounts,
               (select count(*) from latest
                 where consent_type = 'DATA_PROCESSING' and consent_version = :version) as acknowledged,
               (select count(*) from latest where consent_type = 'ANALYTICS' and is_granted) as granted,
               (select count(*) from erasure_requests where status = 'PENDING') as pending,
               (select count(*) from erasure_requests
                 where status = 'PENDING' and postponed_at is not null) as postponed,
               (select count(*) from erasure_requests
                 where status = 'PENDING' and postponed_at is null and scheduled_for < :overdueBefore) as overdue,
               (select count(*) from legal_holds where lifted_at is null) as holds
        """;

    private static final String LAST_RUN = """
        select status, started_at, finished_at
          from retention_runs
         order by started_at desc, run_id desc
         limit 1
        """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public Counts read(String noticeVersion, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource("version", noticeVersion)
            .addValue("overdueBefore", Timestamp.from(now.minus(ERASURE_GRACE)));
        List<RetentionRun> runs = jdbc.query(LAST_RUN, (row, index) -> new RetentionRun(
            RetentionStatus.valueOf(row.getString("status")), instant(row, "started_at"),
            instant(row, "finished_at")));
        return Objects.requireNonNull(jdbc.queryForObject(COUNTS, params, (row, index) -> new Counts(
            row.getLong("accounts"), row.getLong("acknowledged"), row.getLong("granted"), row.getLong("pending"),
            row.getLong("postponed"), row.getLong("overdue"), row.getLong("holds"),
            runs.isEmpty() ? null : runs.getFirst())));
    }
}

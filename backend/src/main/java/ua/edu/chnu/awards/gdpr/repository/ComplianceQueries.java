package ua.edu.chnu.awards.gdpr.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.common.retention.RetentionResult;
import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.gdpr.dto.ErasureOrigin;
import ua.edu.chnu.awards.gdpr.dto.ErasureRequestEntry;
import ua.edu.chnu.awards.gdpr.dto.ErasureRequestStatus;
import ua.edu.chnu.awards.gdpr.dto.LegalHoldEntry;
import ua.edu.chnu.awards.gdpr.dto.RetentionRunEntry;
import ua.edu.chnu.awards.gdpr.dto.RetentionStatus;

import lombok.RequiredArgsConstructor;

/**
 * The lists of the compliance page: retention runs, erasure requests and legal holds of every account, newest
 * first.
 */
@Repository
@RequiredArgsConstructor
public class ComplianceQueries {

    private static final TypeReference<List<RetentionResult>> RESULT_LIST = new TypeReference<>() {
    };

    private static final String PAGE = " limit :limit offset :offset";

    private static final String RUNS = """
        select run_id, status, started_at, finished_at, results::text as results
          from retention_runs
         order by started_at desc, run_id desc
        """ + PAGE;

    private static final String RUN_COUNT = "select count(*) from retention_runs";

    private static final String REQUEST_FILTER = " where cast(:status as varchar) is null or e.status = :status";

    private static final String REQUESTS = """
        select e.request_id, e.origin, e.status, e.requested_at, e.scheduled_for, e.postponed_at, e.cancelled_at,
               e.completed_at, u.user_id as person_id, u.first_name || ' ' || u.last_name as person_name,
               u.email_address as person_email
          from erasure_requests e
          join users u on u.user_id = e.user_id
        """ + REQUEST_FILTER + " order by e.requested_at desc, e.request_id desc" + PAGE;

    private static final String REQUEST_COUNT = "select count(*) from erasure_requests e" + REQUEST_FILTER;

    private static final String HOLD_FILTER = " where not :active or h.lifted_at is null";

    private static final String HOLDS = """
        select h.hold_id, h.reason, h.placed_at, h.lifted_at, h.lift_reason,
               u.user_id as person_id, u.first_name || ' ' || u.last_name as person_name,
               u.email_address as person_email,
               p.user_id as placed_by_id, p.first_name || ' ' || p.last_name as placed_by_name,
               p.email_address as placed_by_email,
               l.user_id as lifted_by_id, l.first_name || ' ' || l.last_name as lifted_by_name,
               l.email_address as lifted_by_email
          from legal_holds h
          join users u on u.user_id = h.user_id
          join users p on p.user_id = h.placed_by
          left join users l on l.user_id = h.lifted_by
        """ + HOLD_FILTER + " order by h.placed_at desc, h.hold_id desc" + PAGE;

    private static final String HOLD_COUNT = "select count(*) from legal_holds h" + HOLD_FILTER;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /**
     * The runs of the retention job.
     *
     * @param page 0-based page, a negative one read as the first
     * @param size page size, kept between 1 and {@link PageResponse#MAX_SIZE}
     * @return the page, newest first
     */
    public PageResponse<RetentionRunEntry> runs(int page, int size) {
        return page(RUNS, RUN_COUNT, new MapSqlParameterSource(), page, size, (row, index) -> run(row));
    }

    /**
     * The erasure requests of every account, of both origins.
     *
     * @param status the state to list, null for all
     * @param page   0-based page, a negative one read as the first
     * @param size   page size, kept between 1 and {@link PageResponse#MAX_SIZE}
     * @return the page, newest first
     */
    public PageResponse<ErasureRequestEntry> erasureRequests(ErasureRequestStatus status, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("status", status == null ? null : status.name());
        return page(REQUESTS, REQUEST_COUNT, params, page, size, (row, index) -> request(row));
    }

    /**
     * The legal holds of every account.
     *
     * @param active whether to list only holds not yet lifted
     * @param page   0-based page, a negative one read as the first
     * @param size   page size, kept between 1 and {@link PageResponse#MAX_SIZE}
     * @return the page, newest first
     */
    public PageResponse<LegalHoldEntry> legalHolds(boolean active, int page, int size) {
        return page(HOLDS, HOLD_COUNT, new MapSqlParameterSource("active", active), page, size,
            (row, index) -> hold(row));
    }

    private <T> PageResponse<T> page(String select, String count, MapSqlParameterSource params, int page, int size,
                                     RowMapper<T> rows) {
        PageRequest request = PageResponse.request(page, size, Sort.unsorted());
        long total = Objects.requireNonNull(jdbc.queryForObject(count, params, Long.class));
        params.addValue("limit", request.getPageSize()).addValue("offset", request.getOffset());
        List<T> content = jdbc.query(select, params, rows);
        return PageResponse.of(new PageImpl<>(content, request, total));
    }

    private RetentionRunEntry run(ResultSet row) throws SQLException {
        return new RetentionRunEntry(row.getLong("run_id"), RetentionStatus.valueOf(row.getString("status")),
            instant(row, "started_at"), instant(row, "finished_at"), results(row.getString("results")));
    }

    private List<RetentionResult> results(String results) {
        try {
            return mapper.readValue(results, RESULT_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Retention results cannot be read", e);
        }
    }

    private static ErasureRequestEntry request(ResultSet row) throws SQLException {
        return new ErasureRequestEntry(row.getLong("request_id"), ref(row, "person"),
            ErasureOrigin.valueOf(row.getString("origin")), ErasureRequestStatus.valueOf(row.getString("status")),
            instant(row, "requested_at"), instant(row, "scheduled_for"), instant(row, "postponed_at"),
            instant(row, "cancelled_at"), instant(row, "completed_at"));
    }

    private static LegalHoldEntry hold(ResultSet row) throws SQLException {
        return new LegalHoldEntry(row.getLong("hold_id"), ref(row, "person"), row.getString("reason"),
            instant(row, "placed_at"), ref(row, "placed_by"), instant(row, "lifted_at"), ref(row, "lifted_by"),
            row.getString("lift_reason"));
    }

    private static UserRef ref(ResultSet row, String prefix) throws SQLException {
        long id = row.getLong(prefix + "_id");
        return row.wasNull() ? null
            : new UserRef(id, row.getString(prefix + "_name"), row.getString(prefix + "_email"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}

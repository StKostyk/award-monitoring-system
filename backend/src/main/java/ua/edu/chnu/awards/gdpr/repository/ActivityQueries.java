package ua.edu.chnu.awards.gdpr.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import ua.edu.chnu.awards.gdpr.dto.ActivityEntry;

import lombok.RequiredArgsConstructor;

/**
 * Reads the audit events written by a person or about their account, leaving out the row-change events of the
 * table triggers and every value, address and user agent.
 */
@Repository
@RequiredArgsConstructor
public class ActivityQueries {

    /** Audited areas whose entity id is a user account. */
    public static final List<String> ACCOUNT_AREAS = List.of("USER", "AUTHENTICATION", "AUTHORIZATION", "GDPR");

    private static final String FILTER = """
          from audit_logs
         where (user_id = :userId or (entity_type in (:areas) and entity_id = :userId))
           and action_type not in ('INSERT', 'UPDATE', 'DELETE')
        """;

    private static final String ROWS = "select action_type, created_at, entity_type, entity_id,"
        + " coalesce(new_values ->> 'actorId', user_id::text) = :userText as by_me" + FILTER
        + " order by created_at desc, log_id desc limit :limit offset :offset";

    private static final String COUNT = "select count(*)" + FILTER;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * One page of a person's activity, newest first.
     *
     * @param userId   the person
     * @param pageable page number and size
     * @return the page
     */
    public Page<ActivityEntry> activity(long userId, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource(Map.of("userId", userId, "areas", ACCOUNT_AREAS,
            "userText", Long.toString(userId), "limit", pageable.getPageSize(), "offset", pageable.getOffset()));
        List<ActivityEntry> rows = jdbc.query(ROWS, params, (row, index) -> new ActivityEntry(
            row.getString("action_type"), row.getObject("created_at", OffsetDateTime.class).toInstant(),
            row.getString("entity_type"), row.getObject("entity_id", Long.class), row.getBoolean("by_me")));
        Long total = jdbc.queryForObject(COUNT, params, Long.class);
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }
}

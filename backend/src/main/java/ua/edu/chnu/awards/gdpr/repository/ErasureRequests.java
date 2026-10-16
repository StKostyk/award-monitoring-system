package ua.edu.chnu.awards.gdpr.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import ua.edu.chnu.awards.gdpr.dto.ErasureRequest;

import lombok.RequiredArgsConstructor;

/**
 * Erasure requests of accounts; at most one request per account is pending.
 */
@Repository
@RequiredArgsConstructor
public class ErasureRequests {

    private static final String USER_ID = "userId";
    private static final String REQUEST_ID = "requestId";

    private static final String PENDING = """
        select request_id, requested_at, scheduled_for, postponed_at
          from erasure_requests
         where user_id = :userId and status = 'PENDING'
        """;

    private static final String CREATE = """
        insert into erasure_requests (user_id, requested_at, scheduled_for)
        values (:userId, :at, :due)
        returning request_id
        """;

    private static final String CANCEL = """
        update erasure_requests set status = 'CANCELLED', cancelled_at = :at
         where user_id = :userId and status = 'PENDING'
        returning request_id
        """;

    private static final String ONLY_ADMINISTRATOR = """
        select exists (select 1 from user_roles
                        where user_id = :userId and role_type = 'SYSTEM_ADMIN'
                          and valid_from <= :day and (valid_to is null or valid_to >= :day))
           and not exists (select 1 from user_roles r
                             join users u on u.user_id = r.user_id
                            where r.role_type = 'SYSTEM_ADMIN' and r.user_id <> :userId
                              and u.account_status = 'ACTIVE'
                              and r.valid_from <= :day and (r.valid_to is null or r.valid_to >= :day))
        """;

    private static final String DUE_FOR_WARNING = """
        select e.request_id, e.scheduled_for, u.email_address, u.first_name
          from erasure_requests e
          join users u on u.user_id = e.user_id
         where e.status = 'PENDING' and e.warning_sent_at is null
           and e.scheduled_for > :now and e.scheduled_for <= :horizon
           and e.scheduled_for - e.requested_at > make_interval(secs => :warningSeconds)
         order by e.scheduled_for
           for update of e skip locked
        """;

    private static final String MARK_WARNED =
        "update erasure_requests set warning_sent_at = :at where request_id = :requestId";

    private static final String DUE = """
        select request_id from erasure_requests
         where status = 'PENDING' and scheduled_for <= :now
         order by scheduled_for, request_id
        """;

    private static final String LOCK_DUE = """
        select request_id, user_id from erasure_requests
         where request_id = :requestId and status = 'PENDING' and scheduled_for <= :now
           for update skip locked
        """;

    private static final String SERIALIZE = "select pg_advisory_xact_lock(hashtext('erasure-run'))";

    private static final String MARK_POSTPONED =
        "update erasure_requests set postponed_at = :at where request_id = :requestId and postponed_at is null";

    private static final String MARK_COMPLETED = """
        update erasure_requests set status = 'COMPLETED', completed_at = :at
         where request_id = :requestId and status = 'PENDING'
        """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * The pending request of an account.
     *
     * @param userId the account
     * @return the request, empty without one
     */
    public Optional<ErasureRequest> pending(long userId) {
        return jdbc.query(PENDING, Map.of(USER_ID, userId), (row, index) -> request(row)).stream().findFirst();
    }

    /**
     * Records a pending request.
     *
     * @param userId       the account
     * @param requestedAt  when the person asked
     * @param scheduledFor when the account is erased
     * @return the new request identifier
     */
    public long create(long userId, Instant requestedAt, Instant scheduledFor) {
        MapSqlParameterSource params = new MapSqlParameterSource(USER_ID, userId)
            .addValue("at", utc(requestedAt))
            .addValue("due", utc(scheduledFor));
        return Objects.requireNonNull(jdbc.queryForObject(CREATE, params, Long.class));
    }

    /**
     * Cancels the pending request of an account.
     *
     * @param userId the account
     * @param at     when
     * @return the cancelled request identifier, empty when none was pending
     */
    public Optional<Long> cancel(long userId, Instant at) {
        MapSqlParameterSource params = new MapSqlParameterSource(USER_ID, userId).addValue("at", utc(at));
        return jdbc.queryForList(CANCEL, params, Long.class).stream().findFirst();
    }

    /**
     * Whether the account holds the system administrator role on the day and no other active account does.
     *
     * @param userId the account
     * @param day    the day the roles must be valid on
     * @return true for the only active system administrator
     */
    public boolean isOnlyAdministrator(long userId, LocalDate day) {
        MapSqlParameterSource params = new MapSqlParameterSource(USER_ID, userId).addValue("day", day);
        return Boolean.TRUE.equals(jdbc.queryForObject(ONLY_ADMINISTRATOR, params, Boolean.class));
    }

    /**
     * Locks and returns the pending requests due within the warning period that were not warned yet; a request
     * made with less time left than the period is never due for a warning. Rows locked elsewhere are skipped.
     *
     * @param now           the current moment
     * @param warningBefore how long before the erasure the warning goes out
     * @return the requests, earliest first
     */
    public List<Due> dueForWarning(Instant now, Duration warningBefore) {
        MapSqlParameterSource params = new MapSqlParameterSource("now", utc(now))
            .addValue("horizon", utc(now.plus(warningBefore)))
            .addValue("warningSeconds", warningBefore.toSeconds());
        return jdbc.query(DUE_FOR_WARNING, params, (row, index) -> new Due(row.getLong("request_id"),
            instant(row, "scheduled_for"), row.getString("email_address"), row.getString("first_name")));
    }

    /**
     * Records that the warning of a request was sent.
     *
     * @param requestId the request
     * @param at        when
     */
    public void markWarned(long requestId, Instant at) {
        jdbc.update(MARK_WARNED, new MapSqlParameterSource(REQUEST_ID, requestId).addValue("at", utc(at)));
    }

    /**
     * The pending requests whose erasure is due, without locking them.
     *
     * @param now the current moment
     * @return their identifiers, earliest due first
     */
    public List<Long> due(Instant now) {
        return jdbc.queryForList(DUE, Map.of("now", utc(now)), Long.class);
    }

    /**
     * Makes erasures wait for each other until the end of the transaction, across instances, so the check for the
     * last system administrator sees the result of an erasure that ran just before.
     */
    public void serialize() {
        jdbc.getJdbcTemplate().queryForList(SERIALIZE);
    }

    /**
     * Locks a request that is still pending and due; a request another run holds, or one cancelled or completed
     * meanwhile, is not returned.
     *
     * @param requestId the request
     * @param now       the current moment
     * @return the locked request, empty when it is gone or locked elsewhere
     */
    public Optional<Locked> lockDue(long requestId, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource(REQUEST_ID, requestId).addValue("now", utc(now));
        return jdbc.query(LOCK_DUE, params, (row, index) -> new Locked(row.getLong("request_id"),
            row.getLong("user_id"))).stream().findFirst();
    }

    /**
     * Records the first postponement of a request.
     *
     * @param requestId the request
     * @param at        when
     * @return true when this was the first postponement
     */
    public boolean markPostponed(long requestId, Instant at) {
        return jdbc.update(MARK_POSTPONED, new MapSqlParameterSource(REQUEST_ID, requestId)
            .addValue("at", utc(at))) > 0;
    }

    /**
     * Marks a pending request completed.
     *
     * @param requestId the request
     * @param at        when the account was erased
     */
    public void markCompleted(long requestId, Instant at) {
        jdbc.update(MARK_COMPLETED, new MapSqlParameterSource(REQUEST_ID, requestId).addValue("at", utc(at)));
    }

    private static ErasureRequest request(ResultSet row) throws SQLException {
        return new ErasureRequest(row.getLong("request_id"), instant(row, "requested_at"),
            instant(row, "scheduled_for"), row.getObject("postponed_at") != null);
    }

    private static OffsetDateTime utc(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /**
     * A pending request due for its warning.
     *
     * @param requestId    the request
     * @param scheduledFor when the account is erased
     * @param email        the account's current address
     * @param firstName    used in the greeting
     */
    public record Due(long requestId, Instant scheduledFor, String email, String firstName) {
    }

    /**
     * A due request locked for its erasure.
     *
     * @param requestId the request
     * @param userId    the account
     */
    public record Locked(long requestId, long userId) {
    }
}

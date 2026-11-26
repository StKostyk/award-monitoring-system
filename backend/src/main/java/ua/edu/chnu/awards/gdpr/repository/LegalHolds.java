package ua.edu.chnu.awards.gdpr.repository;

import static ua.edu.chnu.awards.gdpr.repository.GdprRowsHelper.HOLD_SELECT;
import static ua.edu.chnu.awards.gdpr.repository.GdprRowsHelper.hold;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import ua.edu.chnu.awards.common.JdbcRowUtils;
import ua.edu.chnu.awards.gdpr.dto.LegalHold;

import lombok.RequiredArgsConstructor;

/**
 * Legal holds on accounts; at most one hold per account is active.
 */
@Repository
@RequiredArgsConstructor
public class LegalHolds {

    private static final String USER_ID = "userId";

    private static final String LOCK_ACCOUNT =
        "select account_status from users where user_id = :userId for update";

    private static final String OF_USER = HOLD_SELECT + """
         where h.user_id = :userId
         order by h.placed_at desc, h.hold_id desc
        """;

    private static final String ONE = HOLD_SELECT + " where h.user_id = :userId and h.hold_id = :holdId";

    private static final String EXISTS = "select exists (select 1 from users where user_id = :userId)";

    private static final String ACTIVE = "select exists (select 1 from legal_holds"
        + " where user_id = :userId and lifted_at is null)";

    private static final String ACTIVE_HOLDERS = "select distinct user_id from legal_holds where lifted_at is null";

    private static final String PLACE = """
        insert into legal_holds (user_id, reason, placed_by, placed_at)
        values (:userId, :reason, :by, :at)
        returning hold_id
        """;

    private static final String LIFT = """
        update legal_holds set lifted_by = :by, lifted_at = :at, lift_reason = :reason
         where hold_id = :holdId and lifted_at is null
        """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Locks the account row until the transaction ends, so concurrent changes of its holds see each other.
     *
     * @param userId the account
     * @return the account status, empty when there is no such account
     */
    public Optional<String> lockAccount(long userId) {
        return jdbc.queryForList(LOCK_ACCOUNT, Map.of(USER_ID, userId), String.class).stream().findFirst();
    }

    /**
     * Whether the account exists.
     *
     * @param userId the account
     * @return true when it does
     */
    public boolean accountExists(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(EXISTS, Map.of(USER_ID, userId), Boolean.class));
    }

    /**
     * Every hold of the account.
     *
     * @param userId the account
     * @return the holds, newest first
     */
    public List<LegalHold> of(long userId) {
        return jdbc.query(OF_USER, Map.of(USER_ID, userId), (row, index) -> hold(row));
    }

    /**
     * One hold of the account.
     *
     * @param userId the account
     * @param holdId the hold
     * @return the hold, empty when the account has no such hold
     */
    public Optional<LegalHold> find(long userId, long holdId) {
        return jdbc.query(ONE, Map.of(USER_ID, userId, "holdId", holdId), (row, index) -> hold(row)).stream()
            .findFirst();
    }

    /**
     * Whether the account is under an active hold.
     *
     * @param userId the account
     * @return true with an active hold
     */
    public boolean isActive(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(ACTIVE, Map.of(USER_ID, userId), Boolean.class));
    }

    /**
     * The people under an active hold.
     *
     * @return their account ids
     */
    public Set<Long> activeHolders() {
        return Set.copyOf(jdbc.queryForList(ACTIVE_HOLDERS, Map.of(), Long.class));
    }

    /**
     * Places a hold.
     *
     * @param userId the account
     * @param reason why
     * @param by     the officer
     * @param at     when
     * @return the new hold identifier
     */
    public long place(long userId, String reason, long by, Instant at) {
        MapSqlParameterSource params = new MapSqlParameterSource(USER_ID, userId)
            .addValue("reason", reason)
            .addValue("by", by)
            .addValue("at", JdbcRowUtils.utc(at));
        return Objects.requireNonNull(jdbc.queryForObject(PLACE, params, Long.class));
    }

    /**
     * Lifts an active hold.
     *
     * @param holdId the hold
     * @param reason why
     * @param by     the officer
     * @param at     when
     * @return true when the hold was active and is now lifted
     */
    public boolean lift(long holdId, String reason, long by, Instant at) {
        MapSqlParameterSource params = new MapSqlParameterSource("holdId", holdId)
            .addValue("reason", reason)
            .addValue("by", by)
            .addValue("at", JdbcRowUtils.utc(at));
        return jdbc.update(LIFT, params) == 1;
    }
}

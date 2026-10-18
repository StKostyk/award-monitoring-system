package ua.edu.chnu.awards.gdpr.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * The accounts whose retention period is over: unverified registrations without a recent verification link, and
 * retired or memorial accounts whose status changed long ago. The same conditions select the accounts for which a
 * retention erasure is filed and confirm, when the erasure is due, that it still applies.
 */
@Repository
@RequiredArgsConstructor
public class AccountRetentionCandidates {

    private static final String BEFORE = "before";

    private static final String PENDING_CONDITION = """
        u.account_status = 'PENDING' and u.created_at < :before
           and not exists (select 1 from one_time_tokens t
                            where t.user_id = u.user_id and t.purpose = 'EMAIL_VERIFICATION'
                              and t.created_at >= :before)
        """;

    private static final String FORMER_CONDITION =
        "u.account_status in ('RETIRED', 'MEMORIAL') and u.status_changed_at < :before";

    private static final String NOT_REQUESTED = """
        not exists (select 1 from erasure_requests e where e.user_id = u.user_id and e.status = 'PENDING')
        """;

    private static final String FILE = """
        insert into erasure_requests (user_id, requested_at, scheduled_for, origin)
        select u.user_id, :now, :now, 'RETENTION'
          from users u
         where /*kind*/
           and not (u.user_id = any (cast(:held as bigint[])))
           and /*not requested*/
         order by u.user_id
         limit :limit
        on conflict (user_id) where status = 'PENDING' do nothing
        """;

    private static final String HELD = """
        select count(*) from users u
         where /*kind*/
           and u.user_id = any (cast(:held as bigint[]))
           and /*not requested*/
        """;

    private static final String APPLIES = """
        select exists (select 1 from users u
                        where u.user_id = :userId
                          and ((/*pending*/) or (/*former*/)))
        """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Files due retention erasures for at most {@code limit} accounts of a kind that are not held and have no
     * pending request.
     *
     * @param kind   unverified or former accounts
     * @param before the end of the retention period
     * @param now    the moment of the run, both the request and the due date
     * @param held   the people under a legal hold, as a PostgreSQL array literal
     * @param limit  the most requests filed
     * @return how many requests were filed
     */
    public int file(Kind kind, Instant before, Instant now, String held, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource(BEFORE, utc(before))
            .addValue("now", utc(now))
            .addValue("held", held)
            .addValue("limit", limit);
        return jdbc.update(kind.sql(FILE), params);
    }

    /**
     * Counts the accounts of a kind whose period is over but which are kept for a legal hold.
     *
     * @param kind   unverified or former accounts
     * @param before the end of the retention period
     * @param held   the people under a legal hold, as a PostgreSQL array literal
     * @return the number of held accounts
     */
    public int held(Kind kind, Instant before, String held) {
        MapSqlParameterSource params = new MapSqlParameterSource(BEFORE, utc(before)).addValue("held", held);
        return Objects.requireNonNull(jdbc.queryForObject(kind.sql(HELD), params, Integer.class));
    }

    /**
     * Whether an account still meets the condition of either kind.
     *
     * @param userId        the account
     * @param pendingBefore the end of the period of unverified accounts
     * @param formerBefore  the end of the period of former accounts
     * @return true when its retention erasure still applies
     */
    public boolean applies(long userId, Instant pendingBefore, Instant formerBefore) {
        String sql = APPLIES.replace("/*pending*/", Kind.PENDING.condition.replace(":before", ":pendingBefore"))
            .replace("/*former*/", Kind.FORMER.condition.replace(":before", ":formerBefore"));
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId)
            .addValue("pendingBefore", utc(pendingBefore))
            .addValue("formerBefore", utc(formerBefore));
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, params, Boolean.class));
    }

    private static OffsetDateTime utc(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }

    /**
     * The accounts a retention policy selects.
     */
    public enum Kind {

        PENDING(PENDING_CONDITION),
        FORMER(FORMER_CONDITION);

        private final String condition;

        Kind(String condition) {
            this.condition = condition;
        }

        private String sql(String template) {
            return template.replace("/*kind*/", condition).replace("/*not requested*/", NOT_REQUESTED);
        }
    }
}

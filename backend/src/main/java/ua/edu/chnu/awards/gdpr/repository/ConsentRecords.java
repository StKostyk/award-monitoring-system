package ua.edu.chnu.awards.gdpr.repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import ua.edu.chnu.awards.gdpr.dto.ConsentOverview.HistoryEntry;
import ua.edu.chnu.awards.gdpr.dto.ConsentType;

import lombok.RequiredArgsConstructor;

/**
 * The append-only consent history of a person; the newest record per type is the current state.
 */
@Repository
@RequiredArgsConstructor
public class ConsentRecords {

    private static final String LOCK_OWNER = "select user_id from users where user_id = :userId for update";

    private static final String HISTORY = """
        select consent_type, consent_version, is_granted, coalesce(granted_at, withdrawn_at, created_at) as at
          from consent_records
         where user_id = :userId
         order by created_at, consent_id
        """;

    private static final String APPEND = """
        insert into consent_records (user_id, consent_type, consent_version, is_granted, granted_at, withdrawn_at,
                                     ip_address, user_agent, created_at)
        values (:userId, :type, :version, :granted, :grantedAt, :withdrawnAt, cast(:ip as inet), :userAgent, :at)
        """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Locks the person's account row until the transaction ends, so concurrent changes of the same person's
     * consents see each other's records.
     *
     * @param userId the person
     */
    public void lockOwner(long userId) {
        jdbc.queryForList(LOCK_OWNER, Map.of("userId", userId), Long.class);
    }

    /**
     * Every record of the person.
     *
     * @param userId the person
     * @return the records, oldest first
     */
    public List<HistoryEntry> history(long userId) {
        return jdbc.query(HISTORY, Map.of("userId", userId), (row, index) -> new HistoryEntry(
            ConsentType.valueOf(row.getString("consent_type")), row.getString("consent_version"),
            row.getBoolean("is_granted"), row.getObject("at", OffsetDateTime.class).toInstant()));
    }

    /**
     * Appends a grant or a withdrawal.
     *
     * @param userId    the person
     * @param entry     what was chosen, under which notice version and when
     * @param ip        the client address as an IP literal, or null
     * @param userAgent the client's user agent, or null
     */
    public void append(long userId, HistoryEntry entry, String ip, String userAgent) {
        Timestamp at = Timestamp.from(entry.at());
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("type", entry.type().name());
        params.put("version", entry.version());
        params.put("granted", entry.granted());
        params.put("grantedAt", entry.granted() ? at : null);
        params.put("withdrawnAt", entry.granted() ? null : at);
        params.put("ip", ip);
        params.put("userAgent", userAgent);
        params.put("at", at);
        jdbc.update(APPEND, params);
    }
}

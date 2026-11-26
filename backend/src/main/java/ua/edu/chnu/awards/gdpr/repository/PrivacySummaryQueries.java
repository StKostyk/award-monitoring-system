package ua.edu.chnu.awards.gdpr.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Counts what a person shares: approved personal awards per visibility, documents shown to colleagues, e-mail
 * notifications switched off and the last data export.
 */
@Repository
@RequiredArgsConstructor
public class PrivacySummaryQueries {

    private static final String USER_ID = "userId";

    private static final String AWARDS = """
        select visibility, count(*) as awards
          from awards
         where user_id = :userId and status = 'APPROVED' and recipient_org_id is null
         group by visibility
        """;

    private static final String DOCUMENTS = """
        select count(*)
          from documents d
          join awards a on a.award_id = d.award_id
         where a.user_id = :userId and d.shown_to_colleagues
        """;

    private static final String EMAIL_OFF = """
        select event_type
          from notification_preferences
         where user_id = :userId and not email_enabled
         order by event_type
        """;

    private static final String LAST_EXPORT = """
        select max(created_at)
          from audit_logs
         where user_id = :userId and entity_type = 'GDPR' and action_type = 'DATA_EXPORT'
        """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Approved personal awards of a person per visibility; visibilities without awards are absent.
     *
     * @param userId the award owner
     * @return award count per visibility
     */
    public Map<String, Long> awardsByVisibility(long userId) {
        Map<String, Long> counts = new HashMap<>();
        jdbc.query(AWARDS, Map.of(USER_ID, userId), (RowCallbackHandler) row ->
            counts.put(row.getString("visibility"), row.getLong("awards")));
        return counts;
    }

    /**
     * Documents of a person's awards that colleagues may open.
     *
     * @param userId the award owner
     * @return the number of documents
     */
    public long documentsShown(long userId) {
        Long count = jdbc.queryForObject(DOCUMENTS, Map.of(USER_ID, userId), Long.class);
        return count == null ? 0 : count;
    }

    /**
     * Notification types the person receives no e-mail for.
     *
     * @param userId the person
     * @return event types, alphabetical
     */
    public List<String> emailOffTypes(long userId) {
        return jdbc.queryForList(EMAIL_OFF, Map.of(USER_ID, userId), String.class);
    }

    /**
     * The time of the person's last data export.
     *
     * @param userId the person
     * @return the time, or null when the data was never exported
     */
    public Instant lastExportAt(long userId) {
        OffsetDateTime last = jdbc.queryForObject(LAST_EXPORT, Map.of(USER_ID, userId), OffsetDateTime.class);
        return last == null ? null : last.toInstant();
    }
}

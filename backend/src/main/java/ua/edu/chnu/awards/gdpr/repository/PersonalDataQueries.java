package ua.edu.chnu.awards.gdpr.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.ActivityEntry;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.ConsentEntry;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.DecisionMadeEntry;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.DocumentEntry;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.ReviewDecisionEntry;

import lombok.RequiredArgsConstructor;

/**
 * Reads the export sections that span several tables or have no entities yet: documents, review decisions,
 * consent records and the audit trail.
 */
@Repository
@RequiredArgsConstructor
public class PersonalDataQueries {

    /** API path prefix of a document download. */
    public static final String DOCUMENT_PATH = "/api/v1/documents/";

    private static final String USER_ID = "userId";

    private static final String DOCUMENTS = """
        select d.document_id, d.award_id, d.file_name, d.file_type, d.mime_type, d.file_size, d.uploaded_at
          from documents d
          join awards a on a.award_id = d.award_id
         where a.user_id = :userId
         order by d.uploaded_at, d.document_id
        """;

    private static final String DECISIONS = """
        select a.award_id, coalesce(a.title, a.title_uk) as title, coalesce(a.title_uk, a.title) as title_uk,
               d.decision, d.level, d.decided_at, d.comments,
               r.first_name || ' ' || r.last_name as reviewer,
               g.first_name || ' ' || g.last_name as delegator
          from review_decisions d
          join award_requests q on q.request_id = d.request_id
          join awards a on a.award_id = q.award_id
          join users r on r.user_id = d.reviewer_id
          left join users g on g.user_id = d.delegator_id
        """;

    private static final String REVIEW_DECISIONS = DECISIONS + """
         where a.user_id = :userId
         order by d.decided_at, d.decision_id
        """;

    private static final String DECISIONS_MADE = DECISIONS + """
         where d.reviewer_id = :userId or d.delegator_id = :userId
         order by d.decided_at, d.decision_id
        """;

    private static final String CONSENTS = """
        select consent_type, consent_version, is_granted, granted_at, withdrawn_at, host(ip_address) as ip,
               created_at
          from consent_records
         where user_id = :userId
         order by created_at, consent_id
        """;

    private static final String ACTIVITY = """
        select action_type, created_at,
               case when action_type in (:ownActions)
                     and coalesce(new_values ->> 'actorId', user_id::text) = user_id::text
                    then host(ip_address) end as ip
          from audit_logs
         where user_id = :userId and entity_type in (:areas)
         order by created_at desc, log_id desc
        """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Documents attached to the person's awards.
     *
     * @param userId the award owner
     * @return document metadata, oldest first
     */
    public List<DocumentEntry> documents(long userId) {
        return jdbc.query(DOCUMENTS, Map.of(USER_ID, userId), (row, index) -> new DocumentEntry(
            row.getLong("document_id"), row.getObject("award_id", Long.class), row.getString("file_name"),
            row.getString("file_type"), row.getString("mime_type"), row.getLong("file_size"),
            instant(row, "uploaded_at"), DOCUMENT_PATH + row.getLong("document_id")));
    }

    /**
     * Reviewer decisions on the person's awards, naming the reviewer and the holder of a borrowed role.
     *
     * @param userId the award owner
     * @return decisions, oldest first
     */
    public List<ReviewDecisionEntry> reviewDecisions(long userId) {
        return jdbc.query(REVIEW_DECISIONS, Map.of(USER_ID, userId), (row, index) -> new ReviewDecisionEntry(
            row.getLong("award_id"), row.getString("title"), row.getString("title_uk"),
            ReviewDecisionType.valueOf(row.getString("decision")), ApprovalLevel.valueOf(row.getString("level")),
            instant(row, "decided_at"), row.getString("comments"), row.getString("reviewer"),
            row.getString("delegator")));
    }

    /**
     * Decisions the person made as a reviewer and decisions a delegate made under a role the person lent, with the
     * titles of the awards and the names of reviewer and role holder.
     *
     * @param userId the reviewer or role holder
     * @return decisions, oldest first
     */
    public List<DecisionMadeEntry> decisionsMade(long userId) {
        return jdbc.query(DECISIONS_MADE, Map.of(USER_ID, userId), (row, index) -> new DecisionMadeEntry(
            row.getLong("award_id"), row.getString("title"), row.getString("title_uk"),
            ReviewDecisionType.valueOf(row.getString("decision")), ApprovalLevel.valueOf(row.getString("level")),
            instant(row, "decided_at"), row.getString("comments"), row.getString("reviewer"),
            row.getString("delegator")));
    }

    /**
     * Every consent record of the person.
     *
     * @param userId the person
     * @return consent history, oldest first
     */
    public List<ConsentEntry> consents(long userId) {
        return jdbc.query(CONSENTS, Map.of(USER_ID, userId), (row, index) -> new ConsentEntry(
            row.getString("consent_type"), row.getString("consent_version"), row.getBoolean("is_granted"),
            instant(row, "granted_at"), instant(row, "withdrawn_at"), row.getString("ip"),
            instant(row, "created_at")));
    }

    /**
     * Application events of the person in the given audit areas. The address is kept only for the listed actions
     * the person takes while signed in; events anybody can cause against an account (failed sign-ins,
     * reset requests, lockouts) and events another person caused keep their action and time only.
     *
     * @param userId     the person
     * @param areas      audit entity types to include
     * @param ownActions actions whose address is the person's own
     * @return events, newest first
     */
    public List<ActivityEntry> activity(long userId, Collection<String> areas, Collection<String> ownActions) {
        Map<String, Object> parameters = Map.of(USER_ID, userId, "areas", areas, "ownActions", ownActions);
        return jdbc.query(ACTIVITY, parameters, (row, index) -> new ActivityEntry(
            row.getString("action_type"), instant(row, "created_at"), row.getString("ip")));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}

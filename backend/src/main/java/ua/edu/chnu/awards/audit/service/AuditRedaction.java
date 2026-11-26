package ua.edu.chnu.awards.audit.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.audit.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * The only change the audit trail allows: the guarded redaction of an erased person's values through
 * {@code fn_redact_audit_subject} (ADR-024), and the names the person held according to the trail.
 */
@Component
@RequiredArgsConstructor
public class AuditRedaction {

    private static final String FORMER_NAMES = """
        select distinct concat_ws(' ', snapshot.v ->> 'first_name', snapshot.v ->> 'last_name')
          from audit_logs a
         cross join lateral (values (a.old_values), (a.new_values)) as snapshot(v)
         where a.entity_type = 'users' and a.entity_id = ?
           and snapshot.v ->> 'first_name' is not null and snapshot.v ->> 'last_name' is not null
        """;

    private static final String REDACT = "select fn_redact_audit_subject(?, ?::text[], ?::jsonb)::text";

    private final JdbcTemplate jdbc;
    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Every full name the account carried according to the snapshots of its row.
     *
     * @param userId the account
     * @return first and last name joined by a space, without duplicates
     */
    public List<String> formerNames(long userId) {
        return jdbc.queryForList(FORMER_NAMES, String.class, userId);
    }

    /**
     * Redacts the trail of an erased person in the caller's transaction, after writing out pending audit rows so
     * they are redacted too, and records {@code AUDIT_REDACTED}.
     *
     * @param userId  the person
     * @param names   every full name the person held, replaced by the pseudonym in the listed notifications
     * @param records identifiers by table of the records deleted at the erasure ({@code awards},
     *                {@code award_requests}, {@code documents}) and of the notifications renamed
     * @return the counts of redacted rows by kind
     */
    public Map<String, Integer> redactSubject(long userId, Collection<String> names, Map<String, List<Long>> records) {
        repository.flush();
        try {
            String counts = jdbc.queryForObject(REDACT, String.class, userId, names.toArray(String[]::new),
                objectMapper.writeValueAsString(records));
            return objectMapper.readValue(counts, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Audit redaction counts unreadable", exception);
        }
    }
}

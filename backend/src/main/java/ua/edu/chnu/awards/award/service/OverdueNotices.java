package ua.edu.chnu.awards.award.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.event.OverdueNoticed;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.award.service.ReviewerAvailability.Candidate;

import lombok.RequiredArgsConstructor;

/**
 * Marks open requests past their deadline once per level and tells the reviewers of the next level with a reviewer
 * about them; status, level and reviewer of a request stay as they are.
 */
@Service
@RequiredArgsConstructor
public class OverdueNotices {

    private static final String OPEN = "status IN ('SUBMITTED', 'IN_REVIEW', 'ESCALATED')";
    private static final String CLAIM = "UPDATE award_requests r"
        + " SET overdue_noticed_at = ?, overdue_noticed_level = r.current_level"
        + " WHERE r.request_id IN (SELECT request_id FROM award_requests WHERE " + OPEN
        + " AND overdue_noticed_at IS NULL AND deadline < ? FOR UPDATE SKIP LOCKED)"
        + " RETURNING r.request_id";
    private static final String COUNTS = "SELECT current_level, count(*) AS open,"
        + " count(*) FILTER (WHERE deadline < ?) AS overdue FROM award_requests WHERE " + OPEN
        + " GROUP BY current_level";

    private final JdbcTemplate jdbc;
    private final AwardRequestRepository requests;
    private final ReviewerAvailability availability;
    private final AuditService audit;
    private final ReviewMetrics metrics;
    private final ApplicationEventPublisher events;

    /**
     * One run: marks the overdue requests nobody noticed yet, writes an audit row per request, publishes one digest
     * per reviewer of the notice level and refreshes the queue gauges.
     *
     * @param now the run time
     * @return how many requests were marked
     */
    @Transactional
    public int run(Instant now) {
        Timestamp at = Timestamp.from(now);
        List<Long> ids = jdbc.queryForList(CLAIM, Long.class, at, at);
        Map<Candidate, List<OverdueNoticed.Item>> digests = new LinkedHashMap<>();
        for (AwardRequest request : requests.findAllById(ids)) {
            List<Candidate> recipients = recipients(request);
            OverdueNoticed.Item item = item(request);
            recipients.forEach(candidate -> digests.computeIfAbsent(candidate, key -> new ArrayList<>()).add(item));
            audit.record(AuditAction.REVIEW_OVERDUE_NOTICED, AuditEntityConstants.AWARDS, null,
                request.getAward().getId(), Map.of("requestId", request.getId(),
                    "level", request.getCurrentLevel().name(), "deadline", request.getDeadline().toString(),
                    "recipients", recipients.stream().map(Candidate::id).toList()));
        }
        digests.forEach((candidate, items) -> events.publishEvent(new OverdueNoticed(candidate.email(),
            candidate.name(), items.stream().sorted(Comparator.comparing(OverdueNoticed.Item::deadline)).toList())));
        refreshGauges(at);
        return ids.size();
    }

    /**
     * The reviewers told about an overdue request: those of the first level above the request's level that has an
     * eligible reviewer for its organisation, the owner and submitter excluded; nobody above the rector.
     *
     * @param request the overdue request
     * @return the reviewers, empty at the rector or when no level above has one
     */
    List<Candidate> recipients(AwardRequest request) {
        Award award = request.getAward();
        long organizationId = award.getOrganization().getId();
        long ownerId = award.getOwner().getId();
        long submitterId = request.getSubmitter().getId();
        ApprovalLevel[] levels = ApprovalLevel.values();
        for (int next = request.getCurrentLevel().ordinal() + 1; next < levels.length; next++) {
            List<Candidate> found = availability.candidates(levels[next], organizationId, ownerId, submitterId);
            if (!found.isEmpty()) {
                return found;
            }
        }
        return List.of();
    }

    private static OverdueNoticed.Item item(AwardRequest request) {
        Award award = request.getAward();
        String reviewer = request.getCurrentReviewer() == null ? null : request.getCurrentReviewer().getFullName();
        return new OverdueNoticed.Item(award.getId(), award.titleInEnglish(), award.titleInUkrainian(),
            award.getOwner().getFullName(), request.getCurrentLevel(), request.getDeadline(), reviewer);
    }

    private void refreshGauges(Timestamp at) {
        Map<ApprovalLevel, Long> open = new EnumMap<>(ApprovalLevel.class);
        Map<ApprovalLevel, Long> overdue = new EnumMap<>(ApprovalLevel.class);
        jdbc.query(COUNTS, row -> {
            ApprovalLevel level = ApprovalLevel.valueOf(row.getString("current_level"));
            open.put(level, row.getLong("open"));
            overdue.put(level, row.getLong("overdue"));
        }, at);
        metrics.refresh(open, overdue);
    }
}

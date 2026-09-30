package ua.edu.chnu.awards.audit.service;

import java.net.InetAddress;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.dto.AuditTrailEntry;
import ua.edu.chnu.awards.audit.dto.AuditTrailExport;
import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.entity.AuditLog;
import ua.edu.chnu.awards.audit.repository.AuditLogRepository;
import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reads the audit trail of one record for the oversight roles.
 */
@Service
@RequiredArgsConstructor
public class AuditTrailService {

    /** The most rows an export holds; older ones are left out. */
    public static final int MAX_EXPORT_ROWS = 10_000;

    private final AuditLogRepository logs;
    private final UserRepository users;
    private final AuditService audit;
    private final AuditTrailCsv csv;
    private final Clock clock;

    /**
     * A page of the audit rows about an award, newest first, including rows of an award deleted since.
     *
     * @param awardId the award
     * @param page    0-based page
     * @param size    page size, capped at {@link PageResponse#MAX_SIZE}
     * @return the page, with no rows in total when nothing about the award was ever logged
     */
    @Transactional(readOnly = true)
    public Page<AuditTrailEntry> aboutAward(long awardId, int page, int size) {
        Page<AuditLog> found = logs.findAboutAward(awardId, PageResponse.request(page, size, Sort.unsorted()));
        Map<Long, User> actors = actorsOf(found.getContent());
        return found.map(row -> entry(row, actors.get(row.getUserId())));
    }

    /**
     * The newest {@value #MAX_EXPORT_ROWS} rows about an award as a CSV file; the export is audited as
     * {@code AUDIT_EXPORT} with its row count.
     *
     * @param awardId  the award
     * @param auditorId who exports
     * @return the file, empty when nothing about the award was logged
     */
    @Transactional
    public Optional<AuditTrailExport> exportAward(long awardId, long auditorId) {
        List<AuditLog> found = logs.findNewestAboutAward(awardId, MAX_EXPORT_ROWS + 1);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        boolean truncated = found.size() > MAX_EXPORT_ROWS;
        List<AuditLog> rows = truncated ? found.subList(0, MAX_EXPORT_ROWS) : found;
        Map<Long, User> actors = actorsOf(rows);
        String content = csv.write(rows.stream().map(row -> entry(row, actors.get(row.getUserId()))).toList());
        audit.record(AuditAction.AUDIT_EXPORT, AuditEntityConstants.AWARDS, auditorId, awardId,
            Map.of("rows", rows.size(), "truncated", truncated));
        return Optional.of(new AuditTrailExport("award-" + awardId + "-audit-" + LocalDate.now(clock) + ".csv",
            content, truncated));
    }

    private Map<Long, User> actorsOf(List<AuditLog> rows) {
        List<Long> actorIds = rows.stream().map(AuditLog::getUserId).filter(Objects::nonNull).distinct().toList();
        return users.findAllById(actorIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static AuditTrailEntry entry(AuditLog row, User actor) {
        InetAddress address = row.getIpAddress();
        String[] changed = row.getChangedFields();
        return new AuditTrailEntry(row.getId(), row.getCreatedAt(), row.getUserId(),
            actor == null ? null : actor.getFullName(), actor == null ? null : actor.getEmailAddress(),
            row.getActionType(), row.getEntityType(), row.getEntityId(),
            changed == null ? List.of() : List.of(changed), row.getPrevious(), row.getDetails(),
            address == null ? null : address.getHostAddress(), row.getCorrelationId());
    }
}

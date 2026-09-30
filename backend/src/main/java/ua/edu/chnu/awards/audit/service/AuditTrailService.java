package ua.edu.chnu.awards.audit.service;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.dto.AuditTrailEntry;
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

    private final AuditLogRepository logs;
    private final UserRepository users;

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
        List<Long> actorIds = found.stream().map(AuditLog::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, User> actors = users.findAllById(actorIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
        return found.map(row -> entry(row, actors.get(row.getUserId())));
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

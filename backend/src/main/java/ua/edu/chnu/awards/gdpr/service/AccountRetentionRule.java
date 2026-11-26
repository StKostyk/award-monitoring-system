package ua.edu.chnu.awards.gdpr.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.common.retention.RetentionContext;
import ua.edu.chnu.awards.common.retention.RetentionProperties;
import ua.edu.chnu.awards.gdpr.dto.ErasureOrigin;
import ua.edu.chnu.awards.gdpr.repository.AccountRetentionCandidates;
import ua.edu.chnu.awards.gdpr.repository.ErasureRequests;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * What the erasure run does differently for a request the retention job filed: it confirms, under the row locks,
 * that the account still meets the condition it was filed for (an account verified, renewed or returned to service
 * meanwhile does not, and its request is cancelled), and it sends no mail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AccountRetentionRule {

    private final AccountRetentionCandidates candidates;
    private final RetentionProperties properties;
    private final ErasureRequests requests;
    private final AuditService audit;

    /**
     * Cancels a retention request whose account no longer meets the retention condition, and audits it.
     *
     * @param request the due request, its row and its account locked by the caller
     * @param now     the moment of the erasure run
     * @return true when the request was cancelled; always false for a request of the person
     */
    boolean cancelIfLapsed(ErasureRequests.Locked request, Instant now) {
        if (request.byPerson() || candidates.applies(request.userId(),
            RetentionContext.cutoff(now, properties.pendingAccounts()),
            RetentionContext.cutoff(now, properties.formerStaff()))) {
            return false;
        }
        requests.cancelRequest(request.requestId(), now);
        audit.record(AuditAction.ERASURE_CANCELLED, AuditEntityConstants.GDPR, request.userId(), request.userId(),
            Map.of("requestId", request.requestId(), "origin", ErasureOrigin.RETENTION.code(), "reason",
                "no-longer-applies"));
        log.info("Cancelled retention erasure {} of account {}: the account no longer qualifies",
            request.requestId(), request.userId());
        return true;
    }
}

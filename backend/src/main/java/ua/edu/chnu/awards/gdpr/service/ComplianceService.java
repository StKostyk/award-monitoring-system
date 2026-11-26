package ua.edu.chnu.awards.gdpr.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.common.retention.RetentionPolicy;
import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.gdpr.dto.ErasureRequestEntry;
import ua.edu.chnu.awards.gdpr.dto.ErasureRequestStatus;
import ua.edu.chnu.awards.gdpr.dto.LegalHoldEntry;
import ua.edu.chnu.awards.gdpr.dto.RetentionPolicyEntry;
import ua.edu.chnu.awards.gdpr.dto.RetentionRunEntry;
import ua.edu.chnu.awards.gdpr.repository.ComplianceQueries;

import lombok.RequiredArgsConstructor;

/**
 * What the compliance page shows the GDPR officer: the retention policies, the runs of the retention job, and the
 * erasure requests and legal holds of every account.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplianceService {

    private final List<RetentionPolicy> policies;
    private final ComplianceQueries queries;

    /**
     * The configured retention policies.
     *
     * @return the policies in their run order
     */
    public List<RetentionPolicyEntry> policies() {
        return policies.stream()
            .sorted(Comparator.comparingInt(RetentionPolicy::order))
            .map(policy -> new RetentionPolicyEntry(policy.key(), policy.period(), policy.action().code(),
                policy.order()))
            .toList();
    }

    /**
     * The runs of the retention job.
     *
     * @param page 0-based page
     * @param size page size
     * @return the page, newest first
     */
    public PageResponse<RetentionRunEntry> runs(int page, int size) {
        return queries.runs(page, size);
    }

    /**
     * The erasure requests of every account.
     *
     * @param status the state to list, null for all
     * @param page   0-based page
     * @param size   page size
     * @return the page, newest first
     */
    public PageResponse<ErasureRequestEntry> erasureRequests(ErasureRequestStatus status, int page, int size) {
        return queries.erasureRequests(status, page, size);
    }

    /**
     * The legal holds of every account.
     *
     * @param active whether to list only holds not yet lifted
     * @param page   0-based page
     * @param size   page size
     * @return the page, newest first
     */
    public PageResponse<LegalHoldEntry> legalHolds(boolean active, int page, int size) {
        return queries.legalHolds(active, page, size);
    }
}

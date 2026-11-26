package ua.edu.chnu.awards.gdpr.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.gdpr.dto.ErasureRequestEntry;
import ua.edu.chnu.awards.gdpr.dto.ErasureRequestStatus;
import ua.edu.chnu.awards.gdpr.dto.LegalHoldEntry;
import ua.edu.chnu.awards.gdpr.dto.RetentionPolicyEntry;
import ua.edu.chnu.awards.gdpr.dto.RetentionRunEntry;
import ua.edu.chnu.awards.gdpr.service.ComplianceService;

import lombok.RequiredArgsConstructor;

/**
 * The compliance page of the GDPR officer: retention policies and runs, erasure requests and legal holds.
 */
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@PreAuthorize(ComplianceController.CAN_READ)
public class ComplianceController {

    /** Access rule of every operation. */
    public static final String CAN_READ = "@access.require('compliance:read')";

    private final ComplianceService compliance;

    /**
     * The configured retention policies.
     *
     * @return the policies in their run order
     */
    @GetMapping("/retention-policies")
    public List<RetentionPolicyEntry> retentionPolicies() {
        return compliance.policies();
    }

    /**
     * The runs of the retention job.
     *
     * @param page 0-based page
     * @param size page size, capped at 100
     * @return the page, newest first
     */
    @GetMapping("/retention-runs")
    public PageResponse<RetentionRunEntry> retentionRuns(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return compliance.runs(page, size);
    }

    /**
     * The erasure requests of every account, of both origins.
     *
     * @param status the state to list, all when absent
     * @param page   0-based page
     * @param size   page size, capped at 100
     * @return the page, newest first
     */
    @GetMapping("/erasure-requests")
    public PageResponse<ErasureRequestEntry> erasureRequests(
            @RequestParam(required = false) ErasureRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return compliance.erasureRequests(status, page, size);
    }

    /**
     * The legal holds of every account.
     *
     * @param active whether to list only holds not yet lifted
     * @param page   0-based page
     * @param size   page size, capped at 100
     * @return the page, newest first
     */
    @GetMapping("/legal-holds")
    public PageResponse<LegalHoldEntry> legalHolds(@RequestParam(defaultValue = "false") boolean active,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return compliance.legalHolds(active, page, size);
    }
}

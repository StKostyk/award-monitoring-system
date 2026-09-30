package ua.edu.chnu.awards.award.controller;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.audit.dto.AuditTrailEntry;
import ua.edu.chnu.awards.audit.service.AuditTrailService;
import ua.edu.chnu.awards.award.dto.AwardVersionResponse;
import ua.edu.chnu.awards.award.service.AwardHistory;
import ua.edu.chnu.awards.award.service.AwardNotFoundException;
import ua.edu.chnu.awards.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * The history of an award: its saved versions for whoever may read it, and its audit trail for the oversight
 * roles.
 */
@RestController
@RequestMapping("/api/v1/awards/{id}")
@RequiredArgsConstructor
public class AwardHistoryController {

    private final AwardHistory history;
    private final AuditTrailService auditTrail;

    /**
     * The saved versions of an award the caller may read, newest first; readers other than the owner see them
     * from the submission on.
     *
     * @param id   the award
     * @param page 0-based page
     * @param size page size, capped at 50
     * @return the page
     */
    @GetMapping("/versions")
    @PreAuthorize("@access.require('award:read:own')")
    public PageResponse<AwardVersionResponse> versions(@PathVariable long id,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(history.versions(id, page, size));
    }

    /**
     * The audit rows about an award, newest first, also after a draft was deleted.
     *
     * @param id   the award
     * @param page 0-based page
     * @param size page size, capped at 100
     * @return the page
     * @throws AwardNotFoundException when nothing about the award was logged
     */
    @GetMapping("/audit-trail")
    @PreAuthorize("@access.require('audit:read')")
    public PageResponse<AuditTrailEntry> auditTrail(@PathVariable long id,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Page<AuditTrailEntry> trail = auditTrail.aboutAward(id, page, size);
        if (trail.getTotalElements() == 0) {
            throw new AwardNotFoundException(id);
        }
        return PageResponse.of(trail);
    }
}

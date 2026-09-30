package ua.edu.chnu.awards.award.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.audit.dto.AuditTrailEntry;
import ua.edu.chnu.awards.audit.dto.AuditTrailExport;
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

    /** Response header present on an export that left out older rows. */
    public static final String TRUNCATED_HEADER = "X-Audit-Truncated";

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

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

    /**
     * Downloads the newest audit rows about an award as a CSV attachment; a file that leaves out older rows
     * carries {@value #TRUNCATED_HEADER}.
     *
     * @param id  the award
     * @param jwt the access token of the auditor
     * @return the file
     * @throws AwardNotFoundException when nothing about the award was logged
     */
    @GetMapping("/audit-trail/export")
    @PreAuthorize("@access.require('audit:read')")
    public ResponseEntity<String> exportAuditTrail(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        AuditTrailExport export = auditTrail.exportAward(id, Long.parseLong(jwt.getSubject()))
            .orElseThrow(() -> new AwardNotFoundException(id));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(export.fileName()).build().toString())
            .cacheControl(CacheControl.noStore())
            .contentType(CSV);
        if (export.truncated()) {
            response.header(TRUNCATED_HEADER, "true");
        }
        return response.body(export.content());
    }
}

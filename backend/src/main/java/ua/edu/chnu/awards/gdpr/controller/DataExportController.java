package ua.edu.chnu.awards.gdpr.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.gdpr.dto.DataExport;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile;
import ua.edu.chnu.awards.gdpr.service.DataExportService;

import lombok.RequiredArgsConstructor;

/**
 * The caller's own data export (GDPR Article 20).
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class DataExportController {

    private final DataExportService dataExportService;

    /**
     * Downloads everything the system holds about the caller as a JSON attachment.
     *
     * @param jwt the access token
     * @return the export file
     */
    @GetMapping("/export")
    public ResponseEntity<PersonalDataFile> export(@AuthenticationPrincipal Jwt jwt) {
        DataExport export = dataExportService.export(Long.parseLong(jwt.getSubject()));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(export.fileName()).build().toString())
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_JSON)
            .body(export.content());
    }
}

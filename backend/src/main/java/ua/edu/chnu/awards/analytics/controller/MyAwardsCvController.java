package ua.edu.chnu.awards.analytics.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.analytics.export.CvExportService;
import ua.edu.chnu.awards.analytics.export.CvFile;
import ua.edu.chnu.awards.analytics.export.CvFormat;
import ua.edu.chnu.awards.analytics.export.ReportLanguage;

import lombok.RequiredArgsConstructor;

/**
 * The export of the caller's own approved awards for a CV.
 */
@RestController
@RequestMapping("/api/v1/users/me/awards")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MyAwardsCvController {

    private final CvExportService exporter;

    /**
     * The approved awards of the caller grouped by year, newest first, private ones included.
     *
     * @param format {@code pdf} or {@code text}
     * @param lang   the language of the labels, {@code uk} or {@code en}
     * @param jwt    the access token of the caller
     * @return the file as an attachment
     */
    @GetMapping("/cv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String format,
                                         @RequestParam(defaultValue = "uk") String lang,
                                         @AuthenticationPrincipal Jwt jwt) {
        CvFormat cvFormat = CvFormat.of(format);
        ReportLanguage language = ReportLanguage.of(lang);
        CvFile file = exporter.export(Long.parseLong(jwt.getSubject()), cvFormat, language);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.name()).build()
                .toString())
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(file.mediaType()))
            .body(file.content());
    }
}

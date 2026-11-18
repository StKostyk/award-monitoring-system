package ua.edu.chnu.awards.award.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ua.edu.chnu.awards.award.dto.AwardImportPreview;
import ua.edu.chnu.awards.award.dto.AwardImportResult;
import ua.edu.chnu.awards.award.dto.ImportMapping;
import ua.edu.chnu.awards.award.service.AwardImport;
import ua.edu.chnu.awards.award.service.ImportTemplate;
import ua.edu.chnu.awards.award.service.PersonRules;

import lombok.RequiredArgsConstructor;

/**
 * The spreadsheet import of people and past awards; the services refuse every caller without a faculty
 * secretary or dean role.
 */
@RestController
@RequestMapping("/api/v1/award-imports")
@RequiredArgsConstructor
public class AwardImportController {

    private static final String SIGNED_IN = "isAuthenticated()";
    private static final MediaType XLSX =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AwardImport imports;
    private final ImportTemplate template;
    private final PersonRules rules;

    /**
     * Previews a file.
     *
     * @param file    the spreadsheet
     * @param mapping the mapping, absent to guess it from the headers
     * @return the preview
     */
    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(SIGNED_IN)
    public AwardImportPreview preview(@RequestPart("file") MultipartFile file,
                                      @RequestPart(name = "mapping", required = false) ImportMapping mapping) {
        return imports.preview(file, mapping);
    }

    /**
     * Imports a previewed file.
     *
     * @param file     the spreadsheet
     * @param mapping  the mapping of the preview
     * @param checksum the checksum of the preview
     * @return the counts
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SIGNED_IN)
    public AwardImportResult run(@RequestPart("file") MultipartFile file,
                                 @RequestPart(name = "mapping", required = false) ImportMapping mapping,
                                 @RequestPart("checksum") String checksum) {
        return imports.run(file, mapping, checksum);
    }

    /**
     * The empty workbook to fill.
     *
     * @return the {@code .xlsx} file
     */
    @GetMapping("/template")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<byte[]> template() {
        rules.requireRole();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("award-import-template.xlsx").build().toString())
            .contentType(XLSX)
            .body(template.workbook());
    }
}

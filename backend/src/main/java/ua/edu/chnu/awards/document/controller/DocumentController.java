package ua.edu.chnu.awards.document.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import ua.edu.chnu.awards.document.dto.DocumentDownload;
import ua.edu.chnu.awards.document.dto.DocumentResponse;
import ua.edu.chnu.awards.document.entity.DocumentType;
import ua.edu.chnu.awards.document.service.DocumentService;
import ua.edu.chnu.awards.document.service.DocumentUpload;

import lombok.RequiredArgsConstructor;

/**
 * Files attached to awards: the owner adds and removes them while the award is a draft, everyone who may read
 * the award lists and downloads them.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentController {

    private static final String CAN_UPDATE = "@access.require('award:update:own')";
    private static final String CAN_READ_OWN = "@access.require('award:read:own')";
    private static final String DOCUMENTS = "/documents/";

    private final DocumentService documentService;
    private final DocumentUpload documentUpload;

    /**
     * Attaches a file to the caller's draft.
     *
     * @param id          the draft
     * @param file        the file: PDF, JPEG, PNG or WEBP, at most 10 MB
     * @param type        what the document is
     * @param description optional note
     * @return 201 with the document and its download location
     */
    @PostMapping(path = "/awards/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CAN_UPDATE)
    public ResponseEntity<DocumentResponse> upload(@PathVariable long id, @RequestPart("file") MultipartFile file,
                                                   @RequestParam DocumentType type,
                                                   @RequestParam(required = false) String description) {
        DocumentResponse created = documentUpload.upload(id, file, type, description);
        return ResponseEntity.created(URI.create("/api/v1" + DOCUMENTS + created.id())).body(created);
    }

    /**
     * The documents of an award the caller may read, oldest first.
     *
     * @param id the award
     * @return the documents
     */
    @GetMapping("/awards/{id}/documents")
    @PreAuthorize(CAN_READ_OWN)
    public List<DocumentResponse> list(@PathVariable long id) {
        return documentService.list(id);
    }

    /**
     * The content of a document as an attachment that the browser neither renders inline nor caches.
     *
     * @param id the document
     * @return the content
     */
    @GetMapping(DOCUMENTS + "{id}")
    @PreAuthorize(CAN_READ_OWN)
    public ResponseEntity<InputStreamResource> download(@PathVariable long id) {
        DocumentDownload download = documentService.open(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(download.mimeType()))
            .contentLength(download.size())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + UriUtils.encode(download.fileName(), StandardCharsets.UTF_8))
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "sandbox")
            .body(new InputStreamResource(download.content()));
    }

    /**
     * Removes a document from the caller's draft.
     *
     * @param id the document
     */
    @DeleteMapping(DOCUMENTS + "{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CAN_UPDATE)
    public void delete(@PathVariable long id) {
        documentService.delete(id);
    }
}

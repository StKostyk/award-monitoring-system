package ua.edu.chnu.awards.document.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.controller.AwardPermissionConstants;
import ua.edu.chnu.awards.document.dto.DocumentResponse;
import ua.edu.chnu.awards.document.dto.DocumentSharingUpdate;
import ua.edu.chnu.awards.document.service.DocumentSharing;

import lombok.RequiredArgsConstructor;

/**
 * The owner's choice of which documents of a shared award colleagues may open.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentSharingController {

    private final DocumentSharing sharing;

    /**
     * Shows or hides a document of the caller's shared award to colleagues.
     *
     * @param id   the document
     * @param body the choice
     * @return the document with its choice
     */
    @PutMapping("/{id}/sharing")
    @PreAuthorize(AwardPermissionConstants.CAN_UPDATE)
    public DocumentResponse sharing(@PathVariable long id, @RequestBody(required = false) DocumentSharingUpdate body) {
        return sharing.update(id, body);
    }
}

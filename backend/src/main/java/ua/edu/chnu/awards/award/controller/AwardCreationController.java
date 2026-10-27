package ua.edu.chnu.awards.award.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.service.AwardCreation;

import lombok.RequiredArgsConstructor;

/**
 * Creation of the caller's drafts, once per creation key.
 */
@RestController
@RequestMapping("/api/v1/awards")
@RequiredArgsConstructor
public class AwardCreationController {

    private final AwardCreation creation;

    /**
     * Creates a draft owned by the caller.
     *
     * @param creationKey the key the client generated for this creation; optional
     * @param form        the form; a title in one language is enough
     * @return 201 with the draft and its location, or 200 with the draft the key created before
     */
    @PostMapping
    @PreAuthorize(AwardPermissionConstants.CAN_CREATE)
    public ResponseEntity<AwardResponse> create(
            @RequestHeader(name = "Idempotency-Key", required = false) UUID creationKey,
            @RequestBody AwardForm form) {
        AwardCreation.Created created = creation.create(form, creationKey);
        if (created.repeated()) {
            return ResponseEntity.ok(created.award());
        }
        return ResponseEntity.created(URI.create("/api/v1/awards/" + created.award().id())).body(created.award());
    }
}

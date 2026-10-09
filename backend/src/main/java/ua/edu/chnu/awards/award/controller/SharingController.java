package ua.edu.chnu.awards.award.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.dto.AwardVisibilityUpdate;
import ua.edu.chnu.awards.award.service.AwardSharing;

import lombok.RequiredArgsConstructor;

/**
 * The owner's choice of who sees an approved personal award.
 */
@RestController
@RequestMapping("/api/v1/awards")
@RequiredArgsConstructor
public class SharingController {

    private final AwardSharing sharing;

    /**
     * Chooses who sees the caller's approved personal award.
     *
     * @param id   the award
     * @param body the chosen visibility
     * @return the award with its visibility
     */
    @PutMapping("/{id}/visibility")
    @PreAuthorize(AwardPermissionConstants.CAN_UPDATE)
    public AwardResponse visibility(@PathVariable long id, @RequestBody(required = false) AwardVisibilityUpdate body) {
        return sharing.update(id, body);
    }
}

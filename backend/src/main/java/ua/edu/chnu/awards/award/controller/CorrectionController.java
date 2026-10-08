package ua.edu.chnu.awards.award.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.AwardCorrectionRequest;
import ua.edu.chnu.awards.award.dto.CorrectionOutcome;
import ua.edu.chnu.awards.award.service.AwardCorrection;

import lombok.RequiredArgsConstructor;

/**
 * A reviewer's correction of a pending award.
 */
@RestController
@RequestMapping("/api/v1/awards")
@RequiredArgsConstructor
public class CorrectionController {

    private final AwardCorrection correction;

    /**
     * Corrects the fields of a pending award the caller may review, with a reason shown to the owner.
     *
     * @param id   the award
     * @param body the changed fields, the versions last read and the reason
     * @return the corrected award, the request version and the changed fields
     */
    @PostMapping("/{id}/corrections")
    @PreAuthorize(AwardPermissionConstants.CAN_REVIEW)
    public CorrectionOutcome correct(@PathVariable long id, @RequestBody AwardCorrectionRequest body) {
        return correction.correct(id, body);
    }
}

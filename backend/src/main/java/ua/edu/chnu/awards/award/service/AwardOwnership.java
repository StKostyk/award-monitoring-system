package ua.edu.chnu.awards.award.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;

import lombok.RequiredArgsConstructor;

/**
 * The checks every change of an award starts with: it is the caller's, it is still a draft and the caller
 * read its current version.
 */
@Component
@RequiredArgsConstructor
public class AwardOwnership {

    private final AwardRepository awards;
    private final AccessScope access;

    /**
     * Whether the caller owns the award.
     *
     * @param award the award
     * @return true for the owner
     */
    public boolean isOwn(Award award) {
        return award.getOwner().getId() == access.callerId();
    }

    /**
     * The caller's draft, locked for the rest of the transaction so that saves and submissions of one award run
     * one after the other.
     *
     * @param id the award
     * @return the draft
     * @throws AwardNotFoundException when it does not exist or is not the caller's
     * @throws ApiProblemException    409 {@code award-not-editable} when it was submitted
     */
    public Award lockedDraft(long id) {
        Award award = awards.findForUpdate(id).filter(this::isOwn)
            .orElseThrow(() -> new AwardNotFoundException(id));
        if (!award.isDraft()) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "award-not-editable",
                "The award was submitted and can no longer be changed",
                Map.of("awardStatus", award.getStatus().name()));
        }
        return award;
    }

    /**
     * Refuses a change based on an older version of the award.
     *
     * @param award   the award
     * @param version the version the caller last read, null when not sent
     * @throws ApiProblemException 422 {@code validation-failed} without a version, 409 {@code award-stale}
     *                             with the current version when it differs
     */
    public void requireVersion(Award award, Long version) {
        if (version == null) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "validation-failed",
                "The version last read is required", Map.of("errors",
                    List.of(new FieldViolation("version", "required", "The version last read is required"))));
        }
        if (!version.equals(award.getVersion())) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "award-stale", "The award was changed in the meantime",
                Map.of("currentVersion", award.getVersion()));
        }
    }
}

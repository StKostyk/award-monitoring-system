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
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * The checks every change of an award starts with: it is the caller's, it is still a draft and the caller
 * read its current version.
 */
@Component
@RequiredArgsConstructor
public class AwardOwnership {

    private final AwardRepository awards;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final AccessScope access;

    /**
     * A new, unsaved draft owned by the caller in the caller's department.
     *
     * @return the draft
     */
    public Award newDraft() {
        User owner = users.findById(access.callerId())
            .orElseThrow(() -> new IllegalStateException("Caller has no account"));
        return Award.builder().owner(owner).organization(owner.getOrganization()).build();
    }

    /**
     * Sets who received the award: a unit becomes the award's organisation, a personal award belongs to the
     * owner's current department. The unit was checked by {@link AwardInputRules}.
     *
     * @param award  the draft
     * @param unitId the faculty or department that received it, null for the owner
     */
    public void assignRecipient(Award award, Long unitId) {
        award.setRecipientOrganizationId(unitId);
        award.setOrganization(unitId == null ? award.getOwner().getOrganization()
            : organizations.findById(unitId).orElseThrow(() -> new IllegalStateException("Unknown unit " + unitId)));
    }

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
     * Whether the caller may read the award: their own, or a submitted award inside a scope that reads awards.
     *
     * @param award the award
     * @return true when readable
     */
    public boolean isReadable(Award award) {
        return isOwn(award) || !award.isDraft() && access.canReadAwards(award.getOrganization().getId());
    }

    /**
     * An award the caller may read; an unreadable one is reported as missing, so its existence is not revealed.
     *
     * @param id the award
     * @return the award
     * @throws AwardNotFoundException when it does not exist or is not readable by the caller
     */
    public Award readable(long id) {
        return awards.findById(id).filter(this::isReadable).orElseThrow(() -> new AwardNotFoundException(id));
    }

    /**
     * Same as {@link #readable(long)}, with the owner, organisation and category loaded.
     *
     * @param id the award
     * @return the award
     * @throws AwardNotFoundException when it does not exist or is not readable by the caller
     */
    public Award readableWithDetails(long id) {
        return awards.findWithDetailsById(id).filter(this::isReadable)
            .orElseThrow(() -> new AwardNotFoundException(id));
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
            throw ApiProblemException.validationFailed("The version last read is required",
                List.of(new FieldViolation("version", "required", "The version last read is required")));
        }
        if (!version.equals(award.getVersion())) {
            throw new ApiProblemException(HttpStatus.CONFLICT, ReviewProblemConstants.AWARD_STALE,
                "The award was changed in the meantime", Map.of("currentVersion", award.getVersion()));
        }
    }
}

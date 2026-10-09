package ua.edu.chnu.awards.award.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.dto.AwardVisibilityUpdate;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.AwardVisibility;
import ua.edu.chnu.awards.award.mapper.AwardMapper;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;

import lombok.RequiredArgsConstructor;

/**
 * The owner's choice of who sees an approved personal award. The choice is not award content: it leaves the
 * version and the version history alone, and the audit row of an effective change is the record of consent.
 */
@Service
@RequiredArgsConstructor
public class AwardSharing {

    private static final String VISIBILITY_FIXED = "visibility-fixed";

    private final AwardRepository awards;
    private final AwardOwnership ownership;
    private final RequestLookup requests;
    private final AwardMapper mapper;
    private final AuditService audit;

    /**
     * Sets the visibility of the caller's approved personal award.
     *
     * @param id   the award
     * @param body the chosen visibility
     * @return the award with its visibility
     * @throws AwardNotFoundException when it does not exist or is not the caller's
     * @throws ApiProblemException    422 {@code validation-failed} without a visibility,
     *                                409 {@code visibility-fixed} for an award that is not approved or a unit award
     */
    @Transactional
    public AwardResponse update(long id, AwardVisibilityUpdate body) {
        AwardVisibility visibility = body == null ? null : body.visibility();
        if (visibility == null) {
            throw ApiProblemException.validationFailed("The visibility is missing",
                List.of(new FieldViolation("visibility", "required", "Choose who sees the award")));
        }
        Award award = awards.findForUpdate(id).filter(ownership::isOwn)
            .orElseThrow(() -> new AwardNotFoundException(id));
        requireChoosable(award);
        AwardVisibility from = award.getVisibility();
        if (from != visibility) {
            awards.updateVisibility(award.getId(), visibility.name());
            award.setVisibility(visibility);
            audit.record(AuditAction.AWARD_VISIBILITY_CHANGED, AuditEntityConstants.AWARDS, award.getOwner().getId(),
                award.getId(), Map.of("from", from.name(), "to", visibility.name()));
        }
        return mapper.toResponse(award, requests.of(id).orElse(null));
    }

    private static void requireChoosable(Award award) {
        if (award.isUnitAward()) {
            throw new ApiProblemException(HttpStatus.CONFLICT, VISIBILITY_FIXED,
                "A unit award is shared once approved and has no visibility choice", Map.of());
        }
        if (award.getStatus() != AwardStatus.APPROVED) {
            throw new ApiProblemException(HttpStatus.CONFLICT, VISIBILITY_FIXED,
                "Only an approved award can be shown to others", Map.of("awardStatus", award.getStatus().name()));
        }
    }
}

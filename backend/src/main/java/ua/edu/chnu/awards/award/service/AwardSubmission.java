package ua.edu.chnu.awards.award.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.dto.DuplicateMatch;
import ua.edu.chnu.awards.award.dto.SubmitRequest;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.mapper.AwardMapper;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;

import lombok.RequiredArgsConstructor;

/**
 * Turns a complete draft into a pending award with its approval request at the faculty secretary (or the
 * first level above that the submitter does not hold alone), due by the end of one review period. A personal
 * award moves to the owner's current department; a unit award stays with its unit. The draft row is locked for
 * the whole step, so a repeated submission waits and then
 * finds the award no longer a draft.
 */
@Service
@RequiredArgsConstructor
public class AwardSubmission {

    private final AwardRepository awards;
    private final AwardRequestRepository requests;
    private final AwardOwnership ownership;
    private final AwardInputRules rules;
    private final DuplicateFinder duplicates;
    private final AwardMapper mapper;
    private final AuditService audit;
    private final AwardHistory history;
    private final StatusEstimator estimator;
    private final StartLevel startLevel;
    private final Clock clock;

    /**
     * Submits the caller's draft.
     *
     * @param id      the draft
     * @param request the version last read and whether a possible duplicate was confirmed
     * @return the pending award with its request
     * @throws ApiProblemException 409 {@code award-possible-duplicate} listing the matches when the draft looks
     *                             like another award of the owner and that was not confirmed
     */
    @Transactional
    public AwardResponse submit(long id, SubmitRequest request) {
        Award award = ownership.lockedDraft(id);
        ownership.requireVersion(award, request == null ? null : request.version());
        rules.checkComplete(award);
        List<DuplicateMatch> matches = duplicates.matches(List.of(award.getId()))
            .getOrDefault(award.getId(), List.of());
        boolean acknowledged = request != null && request.duplicateAcknowledged();
        if (!matches.isEmpty() && !acknowledged) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "award-possible-duplicate",
                "The award looks like one already entered", Map.of("matches", matches));
        }
        if (!award.isUnitAward()) {
            award.setOrganization(award.getOwner().getOrganization());
        }
        award.setStatus(AwardStatus.PENDING);
        award.setImpactScore(award.getCategory().getLevel().baseScore());
        awards.saveAndFlush(award);
        history.submitted(award);
        Instant now = clock.instant();
        AwardRequest created = requests.saveAndFlush(AwardRequest.builder()
            .award(award)
            .submitter(award.getOwner())
            .status(RequestStatus.SUBMITTED)
            .currentLevel(startLevel.of(award.getOrganization().getId(), award.getOwner().getId()))
            .submittedAt(now)
            .deadline(estimator.deadline(now))
            .build());
        Map<String, Object> details = new HashMap<>(Map.of("requestId", created.getId(),
            "level", created.getCurrentLevel().name(), "organizationId", award.getOrganization().getId()));
        if (!matches.isEmpty()) {
            details.put("duplicateAcknowledged", true);
        }
        if (award.isUnitAward()) {
            details.put("recipientOrganizationId", award.getRecipientOrganizationId());
        }
        audit.record(AuditAction.AWARD_SUBMITTED, AuditEntityConstants.AWARDS, award.getOwner().getId(), award.getId(),
            details);
        return mapper.toResponse(award, created);
    }
}

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
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
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
 * award moves to the owner's current department; a unit award stays with its unit. A returned or withdrawn award
 * reuses its request: after a return it goes back to the level that returned it, after a withdrawal to the start
 * level. The request row (when there is one) and the draft row are locked for the whole step, in the order a
 * withdrawal locks them, so a repeated submission waits and then finds the award no longer a draft.
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
        AwardRequest earlier = requests.findByAwardIdForUpdate(id).orElse(null);
        Award award = ownership.lockedDraft(id);
        final List<DuplicateMatch> matches = checked(award, request);
        final RequestStatus previous = earlier == null ? null : earlier.getStatus();
        if (!award.isUnitAward()) {
            award.setOrganization(award.getOwner().getOrganization());
        }
        award.setStatus(AwardStatus.PENDING);
        award.setImpactScore(award.getCategory().getLevel().baseScore());
        awards.saveAndFlush(award);
        history.submitted(award);
        AwardRequest created = requests.saveAndFlush(earlier == null ? newRequest(award) : reopened(award, earlier));
        Map<String, Object> details = new HashMap<>(Map.of("requestId", created.getId(),
            "level", created.getCurrentLevel().name(), "organizationId", award.getOrganization().getId()));
        if (previous != null) {
            details.put("resubmittedFrom", previous.name());
        }
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

    private List<DuplicateMatch> checked(Award award, SubmitRequest request) {
        ownership.requireVersion(award, request == null ? null : request.version());
        rules.checkComplete(award);
        List<DuplicateMatch> matches = duplicates.matches(List.of(award.getId()))
            .getOrDefault(award.getId(), List.of());
        boolean acknowledged = request != null && request.duplicateAcknowledged();
        if (!matches.isEmpty() && !acknowledged) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "award-possible-duplicate",
                "The award looks like one already entered", Map.of("matches", matches));
        }
        return matches;
    }

    private AwardRequest newRequest(Award award) {
        Instant now = clock.instant();
        ApprovalLevel level = startLevel.of(award.getOrganization().getId(), award.getOwner().getId());
        return AwardRequest.builder()
            .award(award)
            .submitter(award.getOwner())
            .status(RequestStatus.SUBMITTED)
            .currentLevel(level)
            .submittedAt(now)
            .deadline(estimator.deadline(award, level, now))
            .build();
    }

    private AwardRequest reopened(Award award, AwardRequest request) {
        long organizationId = award.getOrganization().getId();
        long submitterId = award.getOwner().getId();
        Instant now = clock.instant();
        request.setCurrentLevel(request.getStatus() == RequestStatus.RETURNED
            ? startLevel.from(request.getCurrentLevel(), organizationId, submitterId)
            : startLevel.of(organizationId, submitterId));
        request.setStatus(RequestStatus.SUBMITTED);
        request.setCurrentReviewer(null);
        request.setSubmittedAt(now);
        request.setDeadline(estimator.deadline(award, request.getCurrentLevel(), now));
        request.setCompletedAt(null);
        return request;
    }
}

package ua.edu.chnu.awards.award.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.AwardCorrectionRequest;
import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.dto.CorrectionOutcome;
import ua.edu.chnu.awards.award.dto.FieldChange;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.AwardSnapshot;
import ua.edu.chnu.awards.award.mapper.AwardMapper;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * A reviewer's correction of the fields of a pending award. It is an action on the request as a decision is: the
 * reviewer rule decides who may correct, the request row is locked first, an unclaimed request is claimed by the
 * caller, and both the award's and the request's versions must be the ones the caller read. The request keeps
 * its level and deadline; a changed category counts from the next decision on.
 */
@Service
@RequiredArgsConstructor
public class AwardCorrection {

    private final AwardRequestRepository requests;
    private final AwardRepository awards;
    private final ReviewGuards guards;
    private final ReviewAssignment assignment;
    private final AwardInputRules rules;
    private final CorrectionRules correctionRules;
    private final AwardOwnership ownership;
    private final AwardHistory history;
    private final CorrectionLog log;
    private final AwardMapper mapper;
    private final AccessScope access;

    /**
     * Corrects a pending award the caller may review.
     *
     * @param awardId the award
     * @param body    the changed fields, both versions last read and the reason
     * @return the corrected award, the request version and the changed fields
     * @throws AwardNotFoundException when the award has no open request the caller may review
     * @throws ApiProblemException    409 {@code request-claimed}, {@code request-stale} or {@code award-stale};
     *                                422 {@code validation-failed} for a missing version or reason or an invalid
     *                                field, {@code award-incomplete} for a cleared required field,
     *                                {@code no-change} when no field differs
     */
    @Transactional
    public CorrectionOutcome correct(long awardId, AwardCorrectionRequest body) {
        final String reason = correctionRules.reason(body.getReason());
        AwardRequest request = requests.findByAwardIdForUpdate(awardId)
            .filter(AwardRequest::isOpen)
            .filter(guards::isReviewable)
            .orElseThrow(() -> new AwardNotFoundException(awardId));
        guards.requirePresent(body.getRequestVersion());
        User holder = request.getCurrentReviewer();
        long callerId = access.callerId();
        if (holder != null && holder.getId() != callerId) {
            throw guards.claimed(holder);
        }
        guards.requireVersion(request, body.getRequestVersion());
        Award award = awards.findForUpdate(awardId).orElseThrow(() -> new AwardNotFoundException(awardId));
        ownership.requireVersion(award, body.getVersion());
        final AwardCategory before = award.getCategory();
        final AwardSnapshot old = AwardSnapshot.of(award);
        AwardForm form = rules.normalize(body.over(correctionRules.form(award)));
        AwardService.apply(award, form, rules.check(form, Optional.ofNullable(before)));
        rules.requireFilled(award);
        award.setImpactScore(award.getCategory().getLevel().baseScore());
        List<FieldChange> changes = AwardHistory.changes(old, AwardSnapshot.of(award));
        correctionRules.requireChange(changes);
        final User caller = holder == null ? assignment.claimOnAction(request) : holder;
        awards.saveAndFlush(award);
        history.corrected(award, reason);
        log.audit(request, callerId, changes, reason);
        log.announce(award, caller, reason, changes, before);
        return new CorrectionOutcome(mapper.toResponse(award, request), request.getVersion(),
            changes.stream().map(FieldChange::field).toList());
    }
}

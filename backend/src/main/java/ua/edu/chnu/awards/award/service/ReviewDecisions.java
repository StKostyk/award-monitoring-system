package ua.edu.chnu.awards.award.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.DecisionOutcome;
import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest;
import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reviewer decisions: approve, reject, return or escalate the request of an award at the level it stands at. A
 * decision on an unclaimed request claims it in the same transaction; the decision row records the deciding
 * level and, under a delegated role, the person who lent it. The owner is told by e-mail after the commit.
 */
@Service
@RequiredArgsConstructor
public class ReviewDecisions {

    private final AwardRequestRepository requests;
    private final DecisionRules rules;
    private final UserRepository users;
    private final ReviewGuards guards;
    private final ReviewerRule rule;
    private final Transitions transitions;
    private final StartLevel startLevel;
    private final StatusEstimator estimator;
    private final AwardHistory history;
    private final DecisionLog log;
    private final AccessScope access;
    private final Clock clock;

    /**
     * Applies a reviewer's decision to the request of an award.
     *
     * @param awardId the award
     * @param body    the decision, the request version last read and the comment
     * @return where the award and its request stand afterwards
     * @throws AwardNotFoundException when the award has no pending request the caller may review
     * @throws ApiProblemException    409 {@code request-closed}, {@code request-claimed}, {@code request-stale} or
     *                                {@code no-higher-level}; 422 {@code validation-failed} without a decision,
     *                                version or required comment, or for verified documents that do not exist
     */
    @Transactional
    public DecisionOutcome decide(long awardId, ReviewDecisionRequest body) {
        AwardRequest request = guards.lockedReviewable(awardId);
        rules.check(body);
        guards.requireNotHeldByOther(request);
        long callerId = access.callerId();
        guards.requireVersion(request, body.requestVersion());
        Award award = request.getAward();
        rules.checkVerified(awardId, body);
        ApprovalLevel decidedAt = request.getCurrentLevel();
        Transitions.Step step = transitions.of(body.decision(), decidedAt, minimum(award));
        User caller = users.findById(callerId).orElseThrow(() -> new IllegalStateException("Caller has no account"));
        Long delegatorId = rule.delegatorId(request);
        String comment = DecisionRules.comment(body);
        Instant now = clock.instant();
        log.measure(request, decidedAt, step.recorded(), now);
        log.write(request, decidedAt, step.recorded(), caller, comment, delegatorId, now);
        move(request, step, caller, comment, now);
        award.setStatus(step.award());
        if (body.isVerified() && body.decision() == Decision.APPROVE) {
            award.setVerificationBadge(true);
        }
        requests.saveAndFlush(request);
        history.decided(award);
        log.audit(request, body.decision(), decidedAt, callerId, delegatorId);
        log.announce(request, caller, comment);
        return new DecisionOutcome(award.getId(), award.getStatus(), request.getStatus(), request.getCurrentLevel(),
            request.getVersion());
    }

    private void move(AwardRequest request, Transitions.Step step, User caller, String comment, Instant now) {
        request.setStatus(step.request());
        if (step.climbs()) {
            Award award = request.getAward();
            request.setCurrentLevel(startLevel.from(transitions.above(request.getCurrentLevel()),
                award.getOrganization().getId(), request.getSubmitter().getId()));
            request.setCurrentReviewer(null);
            request.restartPeriod(estimator.deadline(award, request.getCurrentLevel(), now));
        } else if (step.isFinal()) {
            request.setCurrentReviewer(caller);
            request.setCompletedAt(now);
            if (step.request() == RequestStatus.REJECTED) {
                request.setRejectionReason(comment);
            }
        } else {
            request.setCurrentReviewer(null);
            request.restartPeriod(null);
        }
    }

    private static ApprovalLevel minimum(Award award) {
        return award.getCategory() == null ? ApprovalLevel.FACULTY_SECRETARY
            : award.getCategory().getLevel().minimumApproval();
    }
}

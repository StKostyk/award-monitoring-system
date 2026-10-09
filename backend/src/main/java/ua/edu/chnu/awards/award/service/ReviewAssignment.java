package ua.edu.chnu.awards.award.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.ReviewItem;
import ua.edu.chnu.awards.award.dto.ReviewerCandidate;
import ua.edu.chnu.awards.award.dto.ReviewerChange;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.mapper.ReviewItemMapper;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.award.repository.ReviewDecisionRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Who works on a request: a reviewer claims an unclaimed one, releases it, hands it to an eligible colleague, or
 * takes it over from a peer when their level is higher or the peer may no longer review it. Every change locks
 * the request row and compares the version the caller last read.
 */
@Service
@RequiredArgsConstructor
public class ReviewAssignment {

    private static final String REQUEST_ID = "requestId";
    private static final String LEVEL = "level";
    private static final String PREVIOUS_REVIEWER = "previousReviewerId";

    private final AwardRequestRepository requests;
    private final ReviewDecisionRepository decisions;
    private final ReviewerRule rule;
    private final ReviewerAvailability availability;
    private final UserRepository users;
    private final AuditService audit;
    private final ReviewItemMapper mapper;
    private final AccessScope access;
    private final ReviewGuards guards;

    /**
     * Claims, takes over or hands over the request of an award.
     *
     * @param awardId the award
     * @param change  the version last read, and the colleague for a hand-over or the take-over flag
     * @return the request as a queue item
     * @throws AwardNotFoundException when the award has no pending request the caller may review
     * @throws ApiProblemException    409 {@code request-closed}, {@code request-claimed} or {@code request-stale};
     *                                422 {@code validation-failed} without a version, {@code reviewer-not-eligible}
     */
    @Transactional
    public ReviewItem assign(long awardId, ReviewerChange change) {
        AwardRequest request = guards.lockedReviewable(awardId);
        guards.requirePresent(change.requestVersion());
        return change.reviewerId() == null ? claim(request, change) : handOver(request, change);
    }

    private ReviewItem claim(AwardRequest request, ReviewerChange change) {
        User holder = request.getCurrentReviewer();
        long callerId = access.callerId();
        if (holder != null && holder.getId() == callerId) {
            return mapper.toItem(request);
        }
        if (holder != null && !(change.isTakeOver() && mayTakeOver(request, holder))) {
            throw guards.claimed(holder);
        }
        guards.requireVersion(request, change.requestVersion());
        User caller = users.findById(callerId).orElseThrow(() -> new IllegalStateException("Caller has no account"));
        take(request, caller, holder);
        return mapper.toItem(request);
    }

    /**
     * Claims an unclaimed request for the caller as part of another action on it, inside the caller's
     * transaction, which has locked the request and checked its version.
     *
     * @param request the locked request nobody holds
     * @return the caller, now its holder
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public User claimOnAction(AwardRequest request) {
        User caller = users.findById(access.callerId())
            .orElseThrow(() -> new IllegalStateException("Caller has no account"));
        take(request, caller, null);
        return caller;
    }

    private void take(AwardRequest request, User caller, User holder) {
        request.setCurrentReviewer(caller);
        request.setStatus(RequestStatus.IN_REVIEW);
        requests.saveAndFlush(request);
        Map<String, Object> details = details(request);
        rule.grant(request).map(ReviewGrant::delegatorId).ifPresent(id -> details.put("delegatorId", id));
        if (holder != null) {
            details.put(PREVIOUS_REVIEWER, holder.getId());
        }
        record(holder == null ? AuditAction.REVIEW_CLAIMED : AuditAction.REVIEW_TAKEN_OVER, request, details);
    }

    /**
     * Gives the request back to the queue of its level.
     *
     * @param awardId        the award
     * @param requestVersion the version of the request the caller last read
     * @throws AwardNotFoundException when the award has no pending request the caller may review
     * @throws ApiProblemException    409 {@code request-closed}, {@code request-claimed} when the caller does not
     *                                hold it, {@code request-stale}; 422 without a version
     */
    @Transactional
    public void release(long awardId, Long requestVersion) {
        AwardRequest request = guards.lockedReviewable(awardId);
        guards.requirePresent(requestVersion);
        guards.requireHeldByCaller(request);
        guards.requireVersion(request, requestVersion);
        List<ApprovalLevel> below = request.getCurrentLevel().below();
        boolean decidedBelow = !below.isEmpty() && decisions.existsByRequestIdAndLevelIn(request.getId(), below);
        request.setCurrentReviewer(null);
        request.setStatus(decidedBelow ? RequestStatus.ESCALATED : RequestStatus.SUBMITTED);
        requests.saveAndFlush(request);
        record(AuditAction.REVIEW_RELEASED, request, details(request));
    }

    /**
     * The colleagues the request can be handed over to: eligible reviewers at its level for its organisation
     * other than the caller, the owner and the submitter.
     *
     * @param awardId the award
     * @return the candidates by name
     * @throws AwardNotFoundException when the award has no pending request the caller may review
     * @throws ApiProblemException    409 {@code request-closed} for a decided request
     */
    @Transactional(readOnly = true)
    public List<ReviewerCandidate> candidates(long awardId) {
        AwardRequest request = requests.findByAwardId(awardId).filter(guards::isReviewable)
            .orElseThrow(() -> new AwardNotFoundException(awardId));
        guards.requireOpen(request);
        return eligible(request).stream()
            .map(candidate -> new ReviewerCandidate(candidate.id(), candidate.name(), candidate.email(),
                candidate.delegated()))
            .toList();
    }

    /**
     * The open request of an award as a queue item.
     *
     * @param awardId the award
     * @return the request as a queue item
     * @throws AwardNotFoundException when the award has no open request the caller may review
     */
    @Transactional(readOnly = true)
    public ReviewItem item(long awardId) {
        return requests.findByAwardId(awardId)
            .filter(AwardRequest::isOpen)
            .filter(guards::isReviewable)
            .map(mapper::toItem)
            .orElseThrow(() -> new AwardNotFoundException(awardId));
    }

    private ReviewItem handOver(AwardRequest request, ReviewerChange change) {
        final User previous = guards.requireHeldByCaller(request);
        guards.requireVersion(request, change.requestVersion());
        ReviewerAvailability.Candidate target = eligible(request).stream()
            .filter(candidate -> change.reviewerId().equals(candidate.id()))
            .findFirst()
            .orElseThrow(() -> new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "reviewer-not-eligible",
                "The colleague may not review this request", Map.of("reviewerId", change.reviewerId())));
        request.setCurrentReviewer(users.findById(target.id())
            .orElseThrow(() -> new IllegalStateException("Unknown reviewer " + target.id())));
        requests.saveAndFlush(request);
        Map<String, Object> details = details(request);
        details.put(PREVIOUS_REVIEWER, previous.getId());
        details.put("reviewerId", target.id());
        details.put("delegated", target.delegated());
        record(AuditAction.REVIEW_HANDED_OVER, request, details);
        return mapper.toItem(request);
    }

    private List<ReviewerAvailability.Candidate> eligible(AwardRequest request) {
        long callerId = access.callerId();
        return availability.candidates(request.getCurrentLevel(), request.getAward().getOrganization().getId(),
                request.getAward().getOwner().getId(), request.getSubmitter().getId()).stream()
            .filter(candidate -> candidate.id() != callerId)
            .toList();
    }

    private boolean mayTakeOver(AwardRequest request, User holder) {
        Optional<ApprovalLevel> holderLevel = Arrays.stream(ApprovalLevel.values())
            .filter(level -> level.covers(request.getCurrentLevel()))
            .filter(level -> availability.isEligible(holder.getId(), level,
                request.getAward().getOrganization().getId(), request.getAward().getOwner().getId(),
                request.getSubmitter().getId()))
            .reduce((lower, higher) -> higher);
        return holderLevel.isEmpty() || rule.highestLevel(request)
            .filter(level -> !holderLevel.get().covers(level)).isPresent();
    }

    private static Map<String, Object> details(AwardRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put(REQUEST_ID, request.getId());
        details.put(LEVEL, request.getCurrentLevel().name());
        return details;
    }

    private void record(AuditAction action, AwardRequest request, Map<String, Object> details) {
        audit.record(action, AuditEntityConstants.AWARDS, access.callerId(), request.getAward().getId(), details);
    }
}

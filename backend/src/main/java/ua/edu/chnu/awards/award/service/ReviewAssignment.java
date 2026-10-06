package ua.edu.chnu.awards.award.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.ReviewItem;
import ua.edu.chnu.awards.award.dto.ReviewerCandidate;
import ua.edu.chnu.awards.award.dto.ReviewerChange;
import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.mapper.ReviewItemMapper;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.award.repository.ReviewDecisionRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
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
        AwardRequest request = lockedReviewable(awardId);
        requirePresent(change.requestVersion());
        return change.reviewerId() == null ? claim(request, change) : handOver(request, change);
    }

    private ReviewItem claim(AwardRequest request, ReviewerChange change) {
        User holder = request.getCurrentReviewer();
        long callerId = access.callerId();
        if (holder != null && holder.getId() == callerId) {
            return mapper.toItem(request);
        }
        if (holder != null && !(change.isTakeOver() && mayTakeOver(request, holder))) {
            throw claimed(holder);
        }
        requireVersion(request, change.requestVersion());
        User caller = users.findById(callerId).orElseThrow(() -> new IllegalStateException("Caller has no account"));
        request.setCurrentReviewer(caller);
        request.setStatus(RequestStatus.IN_REVIEW);
        requests.saveAndFlush(request);
        Map<String, Object> details = details(request);
        rule.grant(request).map(ReviewGrant::delegatorId).ifPresent(id -> details.put("delegatorId", id));
        if (holder != null) {
            details.put(PREVIOUS_REVIEWER, holder.getId());
        }
        record(holder == null ? AuditAction.REVIEW_CLAIMED : AuditAction.REVIEW_TAKEN_OVER, request, details);
        return mapper.toItem(request);
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
        AwardRequest request = lockedReviewable(awardId);
        requirePresent(requestVersion);
        requireHeldByCaller(request);
        requireVersion(request, requestVersion);
        List<ApprovalLevel> below = Arrays.stream(ApprovalLevel.values())
            .filter(level -> level.compareTo(request.getCurrentLevel()) < 0).toList();
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
        AwardRequest request = requests.findByAwardId(awardId).filter(this::isReviewable)
            .orElseThrow(() -> new AwardNotFoundException(awardId));
        requireOpen(request);
        return eligible(request).stream()
            .map(candidate -> new ReviewerCandidate(candidate.id(), candidate.name(), candidate.email(),
                candidate.delegated()))
            .toList();
    }

    private ReviewItem handOver(AwardRequest request, ReviewerChange change) {
        final User previous = requireHeldByCaller(request);
        requireVersion(request, change.requestVersion());
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
        boolean higher = rule.highestLevel(request)
            .filter(level -> level.compareTo(request.getCurrentLevel()) > 0).isPresent();
        return higher || !availability.isEligible(holder.getId(), request.getCurrentLevel(),
            request.getAward().getOrganization().getId(), request.getAward().getOwner().getId(),
            request.getSubmitter().getId());
    }

    private AwardRequest lockedReviewable(long awardId) {
        AwardRequest request = requests.findByAwardIdForUpdate(awardId).filter(this::isReviewable)
            .orElseThrow(() -> new AwardNotFoundException(awardId));
        requireOpen(request);
        return request;
    }

    private boolean isReviewable(AwardRequest request) {
        return (request.isOpen() || request.isFinal()) && rule.grant(request).isPresent();
    }

    private static void requireOpen(AwardRequest request) {
        if (request.isFinal()) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "request-closed", "The request was already decided",
                Map.of("requestStatus", request.getStatus().name()));
        }
    }

    private User requireHeldByCaller(AwardRequest request) {
        User holder = request.getCurrentReviewer();
        if (holder == null || holder.getId() != access.callerId()) {
            throw claimed(holder);
        }
        return holder;
    }

    private static void requirePresent(Long requestVersion) {
        if (requestVersion == null) {
            throw ApiProblemException.validationFailed("The request version last read is required",
                List.of(new FieldViolation("requestVersion", "required", "The request version last read is required")));
        }
    }

    private static void requireVersion(AwardRequest request, long requestVersion) {
        if (requestVersion != request.getVersion()) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "request-stale",
                "The request was changed in the meantime", Map.of("currentVersion", request.getVersion()));
        }
    }

    private static ApiProblemException claimed(User holder) {
        return new ApiProblemException(HttpStatus.CONFLICT, "request-claimed",
            holder == null ? "Nobody holds the request" : "Another reviewer holds the request",
            Collections.singletonMap("reviewer", UserRef.of(holder)));
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

package ua.edu.chnu.awards.award.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * The checks every change of a request shares: the caller may review it, it is still open, the caller read its
 * current version, and nobody else holds it.
 */
@Component
@RequiredArgsConstructor
public class ReviewGuards {

    private final AwardRequestRepository requests;
    private final ReviewerRule rule;
    private final AccessScope access;

    /**
     * The request of an award locked for a change by the caller.
     *
     * @param awardId the award
     * @return the locked open request
     * @throws AwardNotFoundException when the award has no pending request the caller may review
     * @throws ApiProblemException    409 {@code request-closed} for a decided request
     */
    public AwardRequest lockedReviewable(long awardId) {
        AwardRequest request = requests.findByAwardIdForUpdate(awardId).filter(this::isReviewable)
            .orElseThrow(() -> new AwardNotFoundException(awardId));
        requireOpen(request);
        return request;
    }

    /**
     * Whether the caller may review a request that is open or already decided.
     *
     * @param request the request
     * @return true when a role of the caller reaches it
     */
    public boolean isReviewable(AwardRequest request) {
        return (request.isOpen() || request.isFinal()) && rule.grant(request).isPresent();
    }

    /**
     * Refuses a decided request.
     *
     * @param request the request
     * @throws ApiProblemException 409 {@code request-closed}
     */
    public void requireOpen(AwardRequest request) {
        if (request.isFinal()) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "request-closed", "The request was already decided",
                Map.of("requestStatus", request.getStatus().name()));
        }
    }

    /**
     * Refuses a request the caller does not hold.
     *
     * @param request the request
     * @return the caller as its holder
     * @throws ApiProblemException 409 {@code request-claimed}
     */
    public User requireHeldByCaller(AwardRequest request) {
        User holder = request.getCurrentReviewer();
        if (holder == null || holder.getId() != access.callerId()) {
            throw claimed(holder);
        }
        return holder;
    }

    /**
     * Refuses a change without the version the caller last read.
     *
     * @param requestVersion the version sent
     * @throws ApiProblemException 422 {@code validation-failed}
     */
    public void requirePresent(Long requestVersion) {
        if (requestVersion == null) {
            throw ApiProblemException.validationFailed("The request version last read is required",
                List.of(new FieldViolation("requestVersion", "required", "The request version last read is required")));
        }
    }

    /**
     * Refuses a change based on an older version of the request.
     *
     * @param request        the locked request
     * @param requestVersion the version the caller last read
     * @throws ApiProblemException 409 {@code request-stale}
     */
    public void requireVersion(AwardRequest request, long requestVersion) {
        if (requestVersion != request.getVersion()) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "request-stale",
                "The request was changed in the meantime", Map.of("currentVersion", request.getVersion()));
        }
    }

    /**
     * The problem for a request somebody else holds, or that nobody holds when the caller should.
     *
     * @param holder the current holder, null when nobody holds it
     * @return 409 {@code request-claimed} naming the holder
     */
    public ApiProblemException claimed(User holder) {
        if (holder == null) {
            return new ApiProblemException(HttpStatus.CONFLICT, "request-claimed", "Nobody holds the request");
        }
        return new ApiProblemException(HttpStatus.CONFLICT, "request-claimed", "Another reviewer holds the request",
            Map.of("reviewer", UserRef.of(holder)));
    }
}

package ua.edu.chnu.awards.award.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.entity.ReviewDecision;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.award.repository.ReviewDecisionRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;

import lombok.RequiredArgsConstructor;

/**
 * The approval requests of awards as the owner's views read them, including drafts that were submitted before and
 * came back through a return or a withdrawal: they keep their request and its decisions.
 */
@Component
@RequiredArgsConstructor
public class RequestLookup {

    private final AwardRequestRepository requests;
    private final ReviewDecisionRepository decisions;

    /**
     * The request of one award.
     *
     * @param awardId the award
     * @return the request, empty for a draft never submitted
     */
    public Optional<AwardRequest> of(long awardId) {
        return requests.findByAwardId(awardId);
    }

    /**
     * The requests of several awards.
     *
     * @param awardIds the awards
     * @return the requests by award id; awards without one are missing
     */
    public Map<Long, AwardRequest> byAward(Collection<Long> awardIds) {
        return requests.findByAwardIdIn(awardIds).stream()
            .collect(Collectors.toMap(request -> request.getAward().getId(), Function.identity()));
    }

    /**
     * Refuses to delete a draft that has a request.
     *
     * @param awardId the draft
     * @throws ApiProblemException 409 {@code award-has-request} when it was submitted before
     */
    public void requireNeverSubmitted(long awardId) {
        requests.findByAwardId(awardId).ifPresent(request -> {
            throw new ApiProblemException(HttpStatus.CONFLICT, "award-has-request",
                "The award was submitted before, so it can no longer be deleted",
                Map.of("requestStatus", request.getStatus().name()));
        });
    }

    /**
     * The comment of the latest return of a request that waits for resubmission.
     *
     * @param request the request, null for a draft never submitted
     * @return the comment, null when the request is not returned
     */
    public String returnComment(AwardRequest request) {
        if (request == null || request.getStatus() != RequestStatus.RETURNED) {
            return null;
        }
        return decisions.findFirstByRequestIdAndDecisionOrderByDecidedAtDescIdDesc(request.getId(),
            ReviewDecisionType.RETURNED).map(ReviewDecision::getComments).orElse(null);
    }
}

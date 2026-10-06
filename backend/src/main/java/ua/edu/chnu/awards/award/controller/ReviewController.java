package ua.edu.chnu.awards.award.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.ReviewItem;
import ua.edu.chnu.awards.award.dto.ReviewQuery;
import ua.edu.chnu.awards.award.dto.ReviewerCandidate;
import ua.edu.chnu.awards.award.dto.ReviewerChange;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.service.ReviewAssignment;
import ua.edu.chnu.awards.award.service.ReviewQueue;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * The reviewer queue and who works on a request.
 */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewQueue queue;
    private final ReviewAssignment assignment;

    /**
     * The open requests the caller may review.
     *
     * @param assigned       {@code me}, {@code unassigned} or {@code others}; everybody when absent
     * @param level          the level the requests wait at; the caller's own levels when absent
     * @param organizationId a faculty or department inside the caller's scopes
     * @param overdue        true for overdue requests only
     * @param page           0-based page
     * @param size           page size, capped at 100
     * @return the page, overdue and earliest deadline first
     */
    @GetMapping("/api/v1/reviews")
    @PreAuthorize(AwardPermissionConstants.CAN_REVIEW)
    public PageResponse<ReviewItem> list(@RequestParam(required = false) String assigned,
                                         @RequestParam(required = false) ApprovalLevel level,
                                         @RequestParam(required = false) Long organizationId,
                                         @RequestParam(defaultValue = "false") boolean overdue,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(queue.list(new ReviewQuery(assignment(assigned), level, organizationId, overdue),
            page, size));
    }

    /**
     * Claims the request of an award, takes it over or hands it to a colleague.
     *
     * @param id     the award
     * @param change the request version last read; {@code reviewerId} for a hand-over, {@code takeOver}
     * @return the request as a queue item
     */
    @PutMapping("/api/v1/awards/{id}/reviewer")
    @PreAuthorize(AwardPermissionConstants.CAN_REVIEW)
    public ReviewItem assign(@PathVariable long id, @RequestBody ReviewerChange change) {
        return assignment.assign(id, change);
    }

    /**
     * Gives the request of an award back to the queue of its level.
     *
     * @param id             the award
     * @param requestVersion the request version last read
     */
    @DeleteMapping("/api/v1/awards/{id}/reviewer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(AwardPermissionConstants.CAN_REVIEW)
    public void release(@PathVariable long id, @RequestParam long requestVersion) {
        assignment.release(id, requestVersion);
    }

    /**
     * The colleagues the request of an award can be handed over to.
     *
     * @param id the award
     * @return eligible reviewers at the request's level, by name
     */
    @GetMapping("/api/v1/awards/{id}/reviewers")
    @PreAuthorize(AwardPermissionConstants.CAN_REVIEW)
    public List<ReviewerCandidate> candidates(@PathVariable long id) {
        return assignment.candidates(id);
    }

    private static ReviewQuery.Assignment assignment(String assigned) {
        if (assigned == null || assigned.isBlank()) {
            return null;
        }
        return Arrays.stream(ReviewQuery.Assignment.values())
            .filter(value -> value.name().equalsIgnoreCase(assigned))
            .findFirst()
            .orElseThrow(() -> new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "The value of assigned is not valid", Map.of("parameter", "assigned")));
    }
}

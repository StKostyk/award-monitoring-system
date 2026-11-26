package ua.edu.chnu.awards.award.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.DecisionOutcome;
import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest;
import ua.edu.chnu.awards.award.dto.ReviewItem;
import ua.edu.chnu.awards.award.dto.ReviewQuery;
import ua.edu.chnu.awards.award.dto.ReviewerCandidate;
import ua.edu.chnu.awards.award.dto.ReviewerChange;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.service.ReviewAssignment;
import ua.edu.chnu.awards.award.service.ReviewDecisions;
import ua.edu.chnu.awards.award.service.ReviewQueue;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * The reviewer queue, who works on a request, and the reviewer's decision.
 */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewQueue queue;
    private final ReviewAssignment assignment;
    private final ReviewDecisions decisions;

    /**
     * The open requests the caller may review.
     *
     * @param assigned       {@code me}, {@code unassigned} or {@code others}; everybody when absent
     * @param level          the level the requests wait at; the caller's own levels when absent
     * @param organizationId a faculty or department inside the caller's scopes
     * @param overdue        true for overdue requests only
     * @param noticed        true for requests whose missed deadline the next level was told about only
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
                                         @RequestParam(defaultValue = "false") boolean noticed,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(queue.list(new ReviewQuery(assignment(assigned), level, organizationId, overdue,
            noticed), page, size));
    }

    /**
     * The open request of an award as a queue item, for the review panel of the award page.
     *
     * @param id the award
     * @return the request as a queue item
     */
    @GetMapping("/api/v1/awards/{id}/reviewer")
    @PreAuthorize(AwardPermissionConstants.CAN_REVIEW)
    public ReviewItem item(@PathVariable long id) {
        return assignment.item(id);
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

    /**
     * Approves, rejects, returns or escalates the request of an award at the level it stands at.
     *
     * @param id       the award
     * @param decision the decision, the request version last read and the comment
     * @return where the award and its request stand afterwards
     */
    @PostMapping("/api/v1/awards/{id}/decisions")
    @PreAuthorize(AwardPermissionConstants.CAN_REVIEW)
    public DecisionOutcome decide(@PathVariable long id, @RequestBody ReviewDecisionRequest decision) {
        return decisions.decide(id, decision);
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

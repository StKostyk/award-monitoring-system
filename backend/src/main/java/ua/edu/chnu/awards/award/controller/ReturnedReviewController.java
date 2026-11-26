package ua.edu.chnu.awards.award.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.ReturnedReviewItem;
import ua.edu.chnu.awards.award.service.ReturnedReviews;
import ua.edu.chnu.awards.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * The in-rework tab of the reviewer queue.
 */
@RestController
@RequiredArgsConstructor
public class ReturnedReviewController {

    private final ReturnedReviews returned;

    /**
     * Awards sent back for rework inside the caller's review scope and not submitted again.
     *
     * @param organizationId a faculty or department inside the caller's scopes
     * @param page           0-based page
     * @param size           page size, capped at 100
     * @return the page, the longest waiting first
     */
    @GetMapping("/api/v1/reviews/returned")
    @PreAuthorize(AwardPermissionConstants.CAN_REVIEW)
    public PageResponse<ReturnedReviewItem> list(@RequestParam(required = false) Long organizationId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(returned.list(organizationId, page, size));
    }
}

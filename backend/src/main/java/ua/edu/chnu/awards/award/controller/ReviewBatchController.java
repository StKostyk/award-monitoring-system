package ua.edu.chnu.awards.award.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.BatchDecisionRequest;
import ua.edu.chnu.awards.award.dto.BatchItemResult;
import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;
import ua.edu.chnu.awards.award.dto.ReviewTemplateView;
import ua.edu.chnu.awards.award.service.BatchReview;
import ua.edu.chnu.awards.award.service.ReviewTemplates;

import lombok.RequiredArgsConstructor;

/**
 * Batch decisions over the reviewer queue and the comment templates of the decision dialog.
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewBatchController {

    private final BatchReview batch;
    private final ReviewTemplates templates;

    /**
     * Applies one decision to several awards, each in its own transaction.
     *
     * @param body the decision, comment and 1 to 50 distinct items
     * @return one result per item in the order sent
     */
    @PostMapping("/decisions")
    @PreAuthorize(AwardPermissionConstants.CAN_REVIEW)
    public List<BatchItemResult> decide(@RequestBody BatchDecisionRequest body) {
        return batch.decide(body);
    }

    /**
     * Lists the comment templates of a decision in the caller's language.
     *
     * @param decision the decision
     * @return the active templates in list order
     */
    @GetMapping("/templates")
    @PreAuthorize(AwardPermissionConstants.CAN_REVIEW)
    public List<ReviewTemplateView> templates(@RequestParam Decision decision) {
        return templates.of(decision);
    }
}

package ua.edu.chnu.awards.award.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.BatchDecisionRequest;
import ua.edu.chnu.awards.award.dto.BatchItem;
import ua.edu.chnu.awards.award.dto.BatchItemResult;
import ua.edu.chnu.awards.common.web.ApiProblemException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * One reviewer decision applied to several awards. Each item runs the single decision in its own transaction, so a
 * failed item leaves the others decided; the batch itself is recorded once in the audit log afterwards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchReview {

    /** Most awards one batch decides. */
    public static final int MAX_ITEMS = 50;

    private final ReviewDecisions decisions;
    private final DecisionRules rules;
    private final AuditService audit;
    private final AccessScope access;

    /**
     * Applies a decision to every item and reports each item's outcome in the order sent.
     *
     * @param body the decision, comment and items
     * @return one result per item
     * @throws ApiProblemException 400 {@code invalid-parameter} for no items, more than {@value #MAX_ITEMS}, an item
     *                             without an award or the same award twice; 422 {@code validation-failed} without
     *                             a decision or a required comment
     */
    public List<BatchItemResult> decide(BatchDecisionRequest body) {
        checkItems(body.items());
        rules.checkDecision(body.decision(), body.comment());
        List<BatchItemResult> results = new ArrayList<>(body.items().size());
        for (BatchItem item : body.items()) {
            results.add(decide(item, body));
        }
        record(body, results);
        return results;
    }

    private BatchItemResult decide(BatchItem item, BatchDecisionRequest body) {
        long awardId = item.awardId();
        try {
            return BatchItemResult.done(decisions.decide(awardId, body.of(item)));
        } catch (ApiProblemException problem) {
            return BatchItemResult.failed(awardId, problem.getType(), problem.getMessage());
        } catch (AwardNotFoundException missing) {
            return BatchItemResult.failed(awardId, "not-found", "The award was not found or is not reviewable");
        } catch (OptimisticLockingFailureException changed) {
            return BatchItemResult.failed(awardId, "request-stale", "The request changed meanwhile");
        } catch (DataAccessException failure) {
            log.warn("Batch decision on award {} failed in the database: {}", awardId, failure.getMessage());
            return BatchItemResult.failed(awardId, "try-again", "The award could not be decided now; try again");
        }
    }

    private void record(BatchDecisionRequest body, List<BatchItemResult> results) {
        long done = results.stream().filter(result -> result.outcome() == BatchItemResult.Outcome.DONE).count();
        Map<String, Object> details = new HashMap<>();
        details.put("decision", body.decision().name());
        details.put("items", results.size());
        details.put("done", done);
        details.put("failed", results.size() - done);
        audit.record(AuditAction.REVIEW_BATCH, AuditEntityConstants.AWARDS, access.callerId(), null, details);
    }

    private static void checkItems(List<BatchItem> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) {
            throw invalidItems("A batch holds 1 to " + MAX_ITEMS + " items");
        }
        Set<Long> seen = new HashSet<>();
        for (BatchItem item : items) {
            checkDistinct(item, seen);
        }
    }

    private static void checkDistinct(BatchItem item, Set<Long> seen) {
        if (item == null || item.awardId() == null) {
            throw invalidItems("Every item names an award");
        }
        if (!seen.add(item.awardId())) {
            throw invalidItems("Award " + item.awardId() + " appears twice");
        }
    }

    private static ApiProblemException invalidItems(String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter", detail,
            Map.of("parameter", "items"));
    }
}

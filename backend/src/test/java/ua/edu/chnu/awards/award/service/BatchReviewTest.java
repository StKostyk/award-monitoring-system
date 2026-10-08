package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.BatchDecisionRequest;
import ua.edu.chnu.awards.award.dto.BatchItem;
import ua.edu.chnu.awards.award.dto.BatchItemResult;
import ua.edu.chnu.awards.award.dto.BatchItemResult.Outcome;
import ua.edu.chnu.awards.award.dto.DecisionOutcome;
import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest;
import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.document.repository.DocumentRepository;

class BatchReviewTest {

    private static final long REVIEWER = 7L;

    private final ReviewDecisions decisions = mock(ReviewDecisions.class);
    private final AuditService audit = mock(AuditService.class);
    private final AccessScope access = mock(AccessScope.class);
    private final BatchReview batch = new BatchReview(decisions, new DecisionRules(mock(DocumentRepository.class)),
        audit, access);

    @BeforeEach
    void caller() {
        when(access.callerId()).thenReturn(REVIEWER);
    }

    @Test
    void ac4_1_everyItemRunsTheSingleDecisionAndAnswersInOrder() {
        when(decisions.decide(eq(5L), any())).thenReturn(outcome(5L));
        when(decisions.decide(eq(3L), any())).thenReturn(outcome(3L));

        List<BatchItemResult> results = batch.decide(body(Decision.APPROVE, null, item(5L, 2L), item(3L, 4L)));

        assertThat(results).extracting(BatchItemResult::awardId).containsExactly(5L, 3L);
        assertThat(results).extracting(BatchItemResult::outcome).containsOnly(Outcome.DONE);
        assertThat(results.getFirst().status()).isEqualTo(AwardStatus.APPROVED);
        assertThat(results.getFirst().level()).isEqualTo(ApprovalLevel.FACULTY_SECRETARY);
        verify(decisions).decide(5L, new ReviewDecisionRequest(Decision.APPROVE, 2L, null, null));
        verify(decisions).decide(3L, new ReviewDecisionRequest(Decision.APPROVE, 4L, null, null));
    }

    @Test
    void ac4_2_failedItemsCarryTheirProblemAndTheOthersAreDecided() {
        when(decisions.decide(eq(1L), any())).thenThrow(new ApiProblemException(HttpStatus.CONFLICT,
            "request-claimed", "Claimed by another reviewer"));
        when(decisions.decide(eq(2L), any())).thenThrow(new AwardNotFoundException(2L));
        when(decisions.decide(eq(3L), any())).thenThrow(new ObjectOptimisticLockingFailureException(
            AwardRequest.class, 3L));
        when(decisions.decide(eq(4L), any())).thenReturn(outcome(4L));

        List<BatchItemResult> results = batch.decide(body(Decision.APPROVE, null, item(1L, 1L), item(2L, 1L),
            item(3L, 1L), item(4L, 1L)));

        assertThat(results).extracting(BatchItemResult::code)
            .containsExactly("request-claimed", "not-found", "request-stale", null);
        assertThat(results.getFirst().detail()).isEqualTo("Claimed by another reviewer");
        assertThat(results.getFirst().status()).isNull();
        assertThat(results.getLast().outcome()).isEqualTo(Outcome.DONE);
    }

    @Test
    void ac4_1_aDatabaseFailureFailsOnlyItsItemAndTheBatchIsStillAudited() {
        when(decisions.decide(eq(1L), any())).thenReturn(outcome(1L));
        when(decisions.decide(eq(2L), any())).thenThrow(new PessimisticLockingFailureException("lock timeout"));
        when(decisions.decide(eq(3L), any())).thenReturn(outcome(3L));

        List<BatchItemResult> results = batch.decide(body(Decision.APPROVE, null, item(1L, 1L), item(2L, 1L),
            item(3L, 1L)));

        assertThat(results).extracting(BatchItemResult::outcome)
            .containsExactly(Outcome.DONE, Outcome.FAILED, Outcome.DONE);
        assertThat(results.get(1).code()).isEqualTo("try-again");
        assertThat(results.get(1).detail()).doesNotContain("lock timeout");
        verify(audit).record(eq(AuditAction.REVIEW_BATCH), any(), any(), isNull(), any());
    }

    @Test
    void ac4_2_anEmptyListIsRefusedAsAWhole() {
        assertInvalidItems(body(Decision.APPROVE, null));
        assertInvalidItems(new BatchDecisionRequest(Decision.APPROVE, null, null, null));
    }

    @Test
    void ac4_2_moreThanFiftyItemsAreRefusedAsAWhole() {
        BatchItem[] items = LongStream.rangeClosed(1, BatchReview.MAX_ITEMS + 1)
            .mapToObj(id -> item(id, 1L)).toArray(BatchItem[]::new);

        assertInvalidItems(body(Decision.APPROVE, null, items));
    }

    @Test
    void ac4_2_fiftyItemsAreAccepted() {
        when(decisions.decide(anyLong(), any())).thenAnswer(call -> outcome(call.getArgument(0)));
        BatchItem[] items = LongStream.rangeClosed(1, BatchReview.MAX_ITEMS)
            .mapToObj(id -> item(id, 1L)).toArray(BatchItem[]::new);

        assertThat(batch.decide(body(Decision.APPROVE, null, items))).hasSize(BatchReview.MAX_ITEMS);
    }

    @Test
    void ac4_2_duplicateOrMissingAwardsAreRefusedAsAWhole() {
        assertInvalidItems(body(Decision.APPROVE, null, item(5L, 1L), item(5L, 2L)));
        assertInvalidItems(body(Decision.APPROVE, null, item(null, 1L)));
    }

    @Test
    void ac4_2_aReturnWithoutCommentIsRefusedAsAWhole() {
        assertThatThrownBy(() -> batch.decide(body(Decision.RETURN, "  ", item(5L, 1L))))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(problem.getType()).isEqualTo("validation-failed");
            });
        verify(decisions, never()).decide(anyLong(), any());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac4_3_theBatchIsAuditedOnceWithItsCounts() {
        when(decisions.decide(eq(1L), any())).thenReturn(outcome(1L));
        when(decisions.decide(eq(2L), any())).thenThrow(new AwardNotFoundException(2L));

        batch.decide(body(Decision.RETURN, "Додайте скан", item(1L, 1L), item(2L, 1L)));

        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq(AuditAction.REVIEW_BATCH), eq(AuditEntityConstants.AWARDS), eq(REVIEWER), isNull(),
            details.capture());
        assertThat(details.getValue()).containsEntry("decision", "RETURN").containsEntry("items", 2)
            .containsEntry("done", 1L).containsEntry("failed", 1L);
    }

    private void assertInvalidItems(BatchDecisionRequest body) {
        assertThatThrownBy(() -> batch.decide(body))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(problem.getType()).isEqualTo("invalid-parameter");
                assertThat(problem.getProperties()).containsEntry("parameter", "items");
            });
    }

    private static BatchDecisionRequest body(Decision decision, String comment, BatchItem... items) {
        return new BatchDecisionRequest(decision, comment, null, List.of(items));
    }

    private static BatchItem item(Long awardId, long version) {
        return new BatchItem(awardId, version);
    }

    private static DecisionOutcome outcome(long awardId) {
        return new DecisionOutcome(awardId, AwardStatus.APPROVED, RequestStatus.APPROVED,
            ApprovalLevel.FACULTY_SECRETARY, 2L);
    }
}

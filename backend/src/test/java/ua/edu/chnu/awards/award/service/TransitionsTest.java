package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.common.web.ApiProblemException;

class TransitionsTest {

    private final Transitions transitions = new Transitions();

    @Test
    void ac2_1_anApprovalAtTheMinimumLevelEndsTheReview() {
        Transitions.Step step = transitions.of(Decision.APPROVE, ApprovalLevel.DEAN, ApprovalLevel.FACULTY_SECRETARY);

        assertThat(step).isEqualTo(new Transitions.Step(ReviewDecisionType.APPROVED, RequestStatus.APPROVED,
            AwardStatus.APPROVED, false));
        assertThat(step.isFinal()).isTrue();
    }

    @Test
    void ac2_2_anApprovalBelowTheMinimumClimbs() {
        Transitions.Step step = transitions.of(Decision.APPROVE, ApprovalLevel.FACULTY_SECRETARY,
            ApprovalLevel.RECTOR_SECRETARY);

        assertThat(step).isEqualTo(new Transitions.Step(ReviewDecisionType.APPROVED, RequestStatus.ESCALATED,
            AwardStatus.PENDING, true));
        assertThat(step.isFinal()).isFalse();
    }

    @Test
    void ac2_3_aRejectionEndsTheReview() {
        Transitions.Step step = transitions.of(Decision.REJECT, ApprovalLevel.DEAN, ApprovalLevel.FACULTY_SECRETARY);

        assertThat(step).isEqualTo(new Transitions.Step(ReviewDecisionType.REJECTED, RequestStatus.REJECTED,
            AwardStatus.REJECTED, false));
        assertThat(step.isFinal()).isTrue();
    }

    @Test
    void ac2_4_aReturnGivesTheDraftBack() {
        Transitions.Step step = transitions.of(Decision.RETURN, ApprovalLevel.DEAN, ApprovalLevel.RECTOR_SECRETARY);

        assertThat(step).isEqualTo(new Transitions.Step(ReviewDecisionType.RETURNED, RequestStatus.RETURNED,
            AwardStatus.DRAFT, false));
        assertThat(step.isFinal()).isFalse();
    }

    @Test
    void ac2_5_anEscalationClimbsFromEveryLevelBelowTheRector() {
        for (ApprovalLevel level : new ApprovalLevel[] {ApprovalLevel.FACULTY_SECRETARY, ApprovalLevel.DEAN,
            ApprovalLevel.RECTOR_SECRETARY}) {
            assertThat(transitions.of(Decision.ESCALATE, level, ApprovalLevel.FACULTY_SECRETARY))
                .isEqualTo(new Transitions.Step(ReviewDecisionType.ESCALATED, RequestStatus.ESCALATED,
                    AwardStatus.PENDING, true));
            assertThat(transitions.above(level)).isEqualTo(ApprovalLevel.values()[level.ordinal() + 1]);
        }
    }

    @Test
    void ac2_5_theRectorCannotEscalate() {
        assertThatThrownBy(() -> transitions.of(Decision.ESCALATE, ApprovalLevel.RECTOR,
            ApprovalLevel.FACULTY_SECRETARY))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(problem.getType()).isEqualTo("no-higher-level");
            });
    }
}

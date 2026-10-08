package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.ReviewDecision;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.award.event.ReviewMeasured;
import ua.edu.chnu.awards.award.repository.ReviewDecisionRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

class DecisionLogMeasureTest {

    private static final long REQUEST_ID = 8L;
    private static final Instant SUBMITTED = Instant.parse("2026-10-01T08:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-10-06T21:00:00Z");

    private final ReviewDecisionRepository decisions = mock(ReviewDecisionRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DecisionLog log = new DecisionLog(decisions, mock(UserRepository.class), mock(AuditService.class),
        events);

    @Test
    void ac2_6_theDurationRunsFromTheDecisionThatMovedTheRequestToThisLevel() {
        Instant reachedDean = Instant.parse("2026-10-02T10:00:00Z");
        when(decisions.findByRequestId(REQUEST_ID)).thenReturn(List.of(decided(SUBMITTED.minusSeconds(86_400)),
            decided(reachedDean)));

        ReviewMeasured measured = measure(Instant.parse("2026-10-02T15:00:00Z"));

        assertThat(measured).isEqualTo(new ReviewMeasured(ApprovalLevel.DEAN, ReviewDecisionType.APPROVED, true,
            Duration.ofHours(5)));
    }

    @Test
    void ac2_6_aFirstDecisionCountsFromTheSubmissionAndLateOnesAreNotOnTime() {
        when(decisions.findByRequestId(REQUEST_ID)).thenReturn(List.of());

        ReviewMeasured measured = measure(DEADLINE.plusSeconds(60));

        assertThat(measured.onTime()).isFalse();
        assertThat(measured.duration()).isEqualTo(Duration.between(SUBMITTED, DEADLINE.plusSeconds(60)));
    }

    private ReviewMeasured measure(Instant decidedAt) {
        AwardRequest request = AwardRequest.builder().id(REQUEST_ID).submittedAt(SUBMITTED).deadline(DEADLINE)
            .currentLevel(ApprovalLevel.DEAN).build();
        log.measure(request, ApprovalLevel.DEAN, ReviewDecisionType.APPROVED, decidedAt);
        ArgumentCaptor<ReviewMeasured> event = ArgumentCaptor.forClass(ReviewMeasured.class);
        verify(events).publishEvent(event.capture());
        return event.getValue();
    }

    private static ReviewDecision decided(Instant at) {
        return ReviewDecision.builder().requestId(REQUEST_ID).decision(ReviewDecisionType.ESCALATED)
            .level(ApprovalLevel.FACULTY_SECRETARY).decidedAt(at).build();
    }
}

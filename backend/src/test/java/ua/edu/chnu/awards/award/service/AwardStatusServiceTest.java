package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.award.dto.AwardStatusView;
import ua.edu.chnu.awards.award.dto.DelayReason;
import ua.edu.chnu.awards.award.dto.PathStep;
import ua.edu.chnu.awards.award.dto.StepState;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.entity.ReviewDecision;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.award.repository.ReviewDecisionRepository;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.support.TestWorkflow;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;

class AwardStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final long AWARD_ID = 5L;
    private static final long REQUEST_ID = 40L;

    private final AwardRepository awards = mock(AwardRepository.class);
    private final AwardRequestRepository requests = mock(AwardRequestRepository.class);
    private final ReviewDecisionRepository decisions = mock(ReviewDecisionRepository.class);
    private final AwardOwnership ownership = mock(AwardOwnership.class);
    private final ReviewerAvailability reviewers = mock(ReviewerAvailability.class);
    private final AwardStatusService service = new AwardStatusService(awards, requests, decisions, ownership,
        TestWorkflow.estimator(Clock.fixed(NOW, ZoneId.of("Europe/Kyiv"))), reviewers);
    private final Organization department = TestUsers.organization(64L, OrganizationType.DEPARTMENT);
    private final User owner = TestUsers.person(21L, "owner@chnu.edu.ua", department);
    private final User secretary = TestUsers.person(30L, "secretary@chnu.edu.ua", "Аліна", department);
    private final User dean = TestUsers.person(31L, "dean@chnu.edu.ua", "Петро", department);
    private Award award;

    @BeforeEach
    void setUp() {
        award = Award.builder().id(AWARD_ID).owner(owner).organization(department).status(AwardStatus.PENDING)
            .category(AwardCategory.builder().id(13L).level(RecognitionLevel.NATIONAL).build()).build();
        when(awards.findById(AWARD_ID)).thenReturn(Optional.of(award));
        when(ownership.isReadable(award)).thenReturn(true);
        when(reviewers.hasReviewer(any(), anyLong(), anyLong())).thenReturn(true);
        when(decisions.findByRequestId(REQUEST_ID)).thenReturn(List.of());
    }

    @Test
    void ac1_6_aSubmittedAwardShowsItsPathWithTheCurrentLevelAndDueDates() {
        request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY, NOW.plus(Duration.ofDays(2)));

        AwardStatusView view = service.status(AWARD_ID);

        assertThat(view.requestStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(view.path()).extracting(PathStep::state)
            .containsExactly(StepState.CURRENT, StepState.UPCOMING, StepState.UPCOMING);
        assertThat(view.path()).extracting(PathStep::dueDate).containsExactly(LocalDate.of(2026, 10, 3),
            LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 9));
        assertThat(view.estimatedCompletion()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(view.delay()).isNull();
        assertThat(view.decisions()).isEmpty();
    }

    @Test
    void ac1_6_passedLevelsAreDoneWithTheirLatestPassingDecision() {
        request(RequestStatus.IN_REVIEW, ApprovalLevel.RECTOR_SECRETARY, NOW.plus(Duration.ofDays(1)));
        when(decisions.findByRequestId(REQUEST_ID)).thenReturn(List.of(
            decision(1L, ReviewDecisionType.RETURNED, ApprovalLevel.FACULTY_SECRETARY, secretary, "2026-09-20"),
            decision(2L, ReviewDecisionType.APPROVED, ApprovalLevel.FACULTY_SECRETARY, secretary, "2026-09-25"),
            decision(3L, ReviewDecisionType.ESCALATED, ApprovalLevel.DEAN, dean, "2026-09-28")));

        AwardStatusView view = service.status(AWARD_ID);

        assertThat(view.path()).extracting(PathStep::state)
            .containsExactly(StepState.DONE, StepState.DONE, StepState.CURRENT);
        assertThat(view.path()).extracting(PathStep::completedAt).containsExactly(
            Instant.parse("2026-09-25T10:00:00Z"), Instant.parse("2026-09-28T10:00:00Z"), null);
        assertThat(view.path().getFirst().dueDate()).isNull();
        assertThat(view.decisions()).hasSize(3);
        assertThat(view.decisions().getFirst().reviewerName()).isEqualTo("Аліна Мартинюк");
        assertThat(view.decisions().getFirst().comments()).isEqualTo("comment 1");
    }

    @Test
    void ac1_5_anApprovedRequestHasEveryLevelDoneNoEstimateAndItsCompletion() {
        AwardRequest approved = request(RequestStatus.APPROVED, ApprovalLevel.RECTOR_SECRETARY, NOW);
        approved.setCompletedAt(NOW);

        AwardStatusView view = service.status(AWARD_ID);

        assertThat(view.path()).extracting(PathStep::state).containsOnly(StepState.DONE);
        assertThat(view.estimatedCompletion()).isNull();
        assertThat(view.completedAt()).isEqualTo(NOW);
        assertThat(view.delay()).isNull();
        verify(reviewers, never()).hasReviewer(any(), anyLong(), anyLong());
    }

    @Test
    void ac1_5_aRejectedRequestShowsItsReasonAndStopsAtItsLevel() {
        AwardRequest rejected = request(RequestStatus.REJECTED, ApprovalLevel.DEAN, NOW);
        rejected.setRejectionReason("Not an award of the university");

        AwardStatusView view = service.status(AWARD_ID);

        assertThat(view.rejectionReason()).isEqualTo("Not an award of the university");
        assertThat(view.path()).extracting(PathStep::state)
            .containsExactly(StepState.DONE, StepState.CURRENT, StepState.UPCOMING);
        assertThat(view.path()).extracting(PathStep::dueDate).containsOnlyNulls();
    }

    @Test
    void ac1_9_aMissingReviewerIsReportedBeforeAnOverdueReview() {
        request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY, NOW.minus(Duration.ofDays(1)));
        when(reviewers.hasReviewer(ApprovalLevel.FACULTY_SECRETARY, 64L, 21L)).thenReturn(false);

        AwardStatusView view = service.status(AWARD_ID);

        assertThat(view.overdue()).isTrue();
        assertThat(view.delay().reason()).isEqualTo(DelayReason.NO_REVIEWER);
        assertThat(view.delay().since()).isNull();
    }

    @Test
    void ac1_9_anOverdueReviewIsExplainedSinceItsDeadline() {
        Instant deadline = NOW.minus(Duration.ofDays(1));
        request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY, deadline);

        AwardStatusView view = service.status(AWARD_ID);

        assertThat(view.delay().reason()).isEqualTo(DelayReason.REVIEW_OVERDUE);
        assertThat(view.delay().since()).isEqualTo(deadline);
    }

    @Test
    void ac1_5_aReturnedRequestWaitsForItsOwnerWithoutDelay() {
        request(RequestStatus.RETURNED, ApprovalLevel.FACULTY_SECRETARY, NOW.minus(Duration.ofDays(1)));

        AwardStatusView view = service.status(AWARD_ID);

        assertThat(view.estimatedCompletion()).isNull();
        assertThat(view.overdue()).isFalse();
        assertThat(view.delay()).isNull();
    }

    @Test
    void ac1_7_aDraftHasNoRequest() {
        award.setStatus(AwardStatus.DRAFT);

        AwardStatusView view = service.status(AWARD_ID);

        assertThat(view.status()).isEqualTo(AwardStatus.DRAFT);
        assertThat(view.requestStatus()).isNull();
        assertThat(view.path()).isEmpty();
        verify(requests, never()).findByAwardId(any());
    }

    @Test
    void edge_aSubmittedAwardWithoutRequestAnswersWithoutOne() {
        when(requests.findByAwardId(AWARD_ID)).thenReturn(Optional.empty());

        assertThat(service.status(AWARD_ID).requestStatus()).isNull();
    }

    @Test
    void ac1_8_anAwardTheCallerMayNotReadIsNotFound() {
        when(ownership.isReadable(award)).thenReturn(false);

        assertThatThrownBy(() -> service.status(AWARD_ID)).isInstanceOf(AwardNotFoundException.class);
        assertThatThrownBy(() -> service.status(99L)).isInstanceOf(AwardNotFoundException.class);
    }

    private AwardRequest request(RequestStatus status, ApprovalLevel level, Instant deadline) {
        AwardRequest request = AwardRequest.builder().id(REQUEST_ID).award(award).submitter(owner).status(status)
            .currentLevel(level).submittedAt(NOW.minus(Duration.ofDays(10))).deadline(deadline).build();
        when(requests.findByAwardId(AWARD_ID)).thenReturn(Optional.of(request));
        return request;
    }

    private static ReviewDecision decision(long id, ReviewDecisionType type, ApprovalLevel level, User reviewer,
                                           String day) {
        return ReviewDecision.builder().id(id).requestId(REQUEST_ID).decision(type).level(level).reviewer(reviewer)
            .comments("comment " + id).decidedAt(Instant.parse(day + "T10:00:00Z")).build();
    }
}

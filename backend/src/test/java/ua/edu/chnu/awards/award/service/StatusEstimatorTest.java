package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.config.WorkflowProperties;

class StatusEstimatorTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private final StatusEstimator estimator = estimator(3);

    @Test
    void ac1_1_theDeadlineIsOneReviewPeriodAfterTheStart() {
        assertThat(estimator.deadline(NOW)).isEqualTo(Instant.parse("2026-10-04T09:00:00Z"));
    }

    @Test
    void ac1_3_theEstimateAddsOnePeriodForEveryLevelAfterTheCurrentOne() {
        StatusEstimator.Timeline timeline = estimator.timeline(
            request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY, days(2)), RecognitionLevel.FACULTY);

        assertThat(timeline.levels()).containsExactly(ApprovalLevel.FACULTY_SECRETARY, ApprovalLevel.DEAN);
        assertThat(timeline.due()).containsExactlyInAnyOrderEntriesOf(Map.of(
            ApprovalLevel.FACULTY_SECRETARY, LocalDate.of(2026, 10, 3),
            ApprovalLevel.DEAN, LocalDate.of(2026, 10, 6)));
        assertThat(timeline.estimatedCompletion()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(timeline.overdue()).isFalse();
        assertThat(timeline.deadline()).isEqualTo(days(2));
    }

    @Test
    void ac1_3_aOneLevelPathIsDueOnItsDeadline() {
        StatusEstimator.Timeline timeline = estimator.timeline(
            request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY, days(3)), RecognitionLevel.DEPARTMENT);

        assertThat(timeline.estimatedCompletion()).isEqualTo(LocalDate.of(2026, 10, 4));
    }

    @Test
    void ac1_3_theUniversityPathTakesThreePeriods() {
        StatusEstimator.Timeline timeline = estimator.timeline(
            request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY, days(3)), RecognitionLevel.NATIONAL);

        assertThat(timeline.estimatedCompletion()).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    void ac1_4_aLateLevelGetsAFreshPeriodFromNowAndTheEstimateNeverMovesEarlier() {
        AwardRequest late = request(RequestStatus.IN_REVIEW, ApprovalLevel.FACULTY_SECRETARY, days(-2));

        StatusEstimator.Timeline timeline = estimator.timeline(late, RecognitionLevel.FACULTY);

        assertThat(timeline.overdue()).isTrue();
        assertThat(timeline.estimatedCompletion()).isEqualTo(LocalDate.of(2026, 10, 7))
            .isAfterOrEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(timeline.due()).containsEntry(ApprovalLevel.FACULTY_SECRETARY, LocalDate.of(2026, 10, 4))
            .containsEntry(ApprovalLevel.DEAN, LocalDate.of(2026, 10, 7));
        assertThat(timeline.deadline()).isEqualTo(days(-2));
    }

    @Test
    void ac1_4_aRequestExactlyAtItsDeadlineIsNotOverdue() {
        assertThat(estimator.timeline(request(RequestStatus.SUBMITTED, ApprovalLevel.DEAN, NOW),
            RecognitionLevel.FACULTY).overdue()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"RETURNED", "APPROVED", "REJECTED", "EXPIRED"})
    void ac1_5_aReturnedOrFinalRequestHasNoEstimateAndIsNotOverdue(RequestStatus status) {
        StatusEstimator.Timeline timeline = estimator.timeline(
            request(status, ApprovalLevel.FACULTY_SECRETARY, days(-5)), RecognitionLevel.FACULTY);

        assertThat(timeline.estimatedCompletion()).isNull();
        assertThat(timeline.overdue()).isFalse();
        assertThat(timeline.due()).isEmpty();
        assertThat(estimator.isActive(request(status, ApprovalLevel.DEAN, NOW))).isFalse();
    }

    @Test
    void edge_aRequestWithoutDeadlineCountsFromItsSubmission() {
        AwardRequest request = AwardRequest.builder().status(RequestStatus.SUBMITTED)
            .currentLevel(ApprovalLevel.FACULTY_SECRETARY).submittedAt(NOW.minus(Duration.ofDays(1))).build();

        assertThat(estimator.timeline(request, RecognitionLevel.DEPARTMENT).deadline()).isEqualTo(days(2));
    }

    @Test
    void edge_aSubmissionJustBeforeKyivMidnightIsDueOnTheKyivDateThreeDaysLater() {
        AwardRequest request = request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY,
            estimator.deadline(Instant.parse("2026-10-01T20:59:00Z")));

        assertThat(estimator.timeline(request, RecognitionLevel.DEPARTMENT).estimatedCompletion())
            .isEqualTo(LocalDate.of(2026, 10, 4));
    }

    @Test
    void edge_aChangedPeriodKeepsTheStoredDeadlineAndMovesLaterLevels() {
        StatusEstimator.Timeline timeline = estimator(5).timeline(
            request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY, days(2)), RecognitionLevel.FACULTY);

        assertThat(timeline.due()).containsEntry(ApprovalLevel.FACULTY_SECRETARY, LocalDate.of(2026, 10, 3));
        assertThat(timeline.estimatedCompletion()).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test
    void edge_aPeriodAcrossTheAutumnClockChangeEndsOnTheThirdKyivDay() {
        Instant deadline = estimator.deadline(Instant.parse("2026-10-22T21:30:00Z"));

        assertThat(deadline).isEqualTo(Instant.parse("2026-10-25T22:30:00Z"));
        assertThat(LocalDate.ofInstant(deadline, KYIV)).isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    void edge_aPeriodAcrossTheSpringClockChangeEndsOnTheThirdKyivDay() {
        Instant deadline = estimator.deadline(Instant.parse("2027-03-26T21:30:00Z"));

        assertThat(deadline).isEqualTo(Instant.parse("2027-03-29T20:30:00Z"));
        assertThat(LocalDate.ofInstant(deadline, KYIV)).isEqualTo(LocalDate.of(2027, 3, 29));
    }

    @Test
    void edge_aLaterLevelAcrossTheClockChangeIsDueOnTheThirdKyivDay() {
        AwardRequest request = request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY,
            Instant.parse("2026-10-22T21:30:00Z"));

        StatusEstimator.Timeline timeline = estimator(3, Instant.parse("2026-10-21T09:00:00Z"))
            .timeline(request, RecognitionLevel.FACULTY);

        assertThat(timeline.due()).containsEntry(ApprovalLevel.FACULTY_SECRETARY, LocalDate.of(2026, 10, 23))
            .containsEntry(ApprovalLevel.DEAN, LocalDate.of(2026, 10, 26));
    }

    @Test
    void edge_aPeriodWithHoursAddsThemAfterTheDays() {
        StatusEstimator hours = new StatusEstimator(Clock.fixed(NOW, KYIV),
            new WorkflowProperties(Duration.ofHours(36)), new ApprovalPath());

        assertThat(hours.deadline(Instant.parse("2026-10-24T09:00:00Z")))
            .isEqualTo(Instant.parse("2026-10-25T22:00:00Z"));
    }

    @Test
    void edge_aReturnedRequestKeepsItsStoredDeadline() {
        assertThat(estimator.timeline(request(RequestStatus.RETURNED, ApprovalLevel.DEAN, days(-1)),
            RecognitionLevel.FACULTY).deadline()).isEqualTo(days(-1));
    }

    private static StatusEstimator estimator(int days) {
        return estimator(days, NOW);
    }

    private static StatusEstimator estimator(int days, Instant now) {
        return new StatusEstimator(Clock.fixed(now, KYIV), new WorkflowProperties(Duration.ofDays(days)),
            new ApprovalPath());
    }

    private static Instant days(int days) {
        return NOW.plus(Duration.ofDays(days));
    }

    private static AwardRequest request(RequestStatus status, ApprovalLevel level, Instant deadline) {
        return AwardRequest.builder().status(status).currentLevel(level).submittedAt(NOW.minus(Duration.ofDays(1)))
            .deadline(deadline).build();
    }
}

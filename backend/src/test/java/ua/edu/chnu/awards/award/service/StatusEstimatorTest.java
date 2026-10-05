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
import ua.edu.chnu.awards.support.TestWorkflow;

class StatusEstimatorTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
    private static final int DEFAULT_DAYS = 3;
    private static final int LONGER_DAYS = 5;

    private final StatusEstimator estimator = estimator(DEFAULT_DAYS);

    @Test
    void ac2_theDeadlineIsThreeWorkingDaysAfterTheStartAtTheSameTime() {
        assertThat(estimator.deadline(NOW)).isEqualTo(Instant.parse("2026-10-06T09:00:00Z"));
    }

    @Test
    void ac2_aStartOnFridaySkipsTheWeekend() {
        assertThat(estimator.deadline(Instant.parse("2026-10-02T09:00:00Z")))
            .isEqualTo(Instant.parse("2026-10-07T09:00:00Z"));
    }

    @Test
    void ac2_aStartOnTheWeekendCountsFromMondayMidnight() {
        assertThat(estimator.deadline(Instant.parse("2026-10-03T12:00:00Z")))
            .isEqualTo(Instant.parse("2026-10-07T21:00:00Z"));
        assertThat(estimator.deadline(Instant.parse("2026-10-04T20:59:00Z")))
            .isEqualTo(Instant.parse("2026-10-07T21:00:00Z"));
    }

    @Test
    void ac1_3_theEstimateAddsOnePeriodForEveryLevelAfterTheCurrentOne() {
        StatusEstimator.Timeline timeline = estimator.timeline(
            request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY, days(1)), RecognitionLevel.NATIONAL);

        assertThat(timeline.levels()).containsExactly(ApprovalLevel.FACULTY_SECRETARY, ApprovalLevel.DEAN,
            ApprovalLevel.RECTOR_SECRETARY);
        assertThat(timeline.due()).containsExactlyInAnyOrderEntriesOf(Map.of(
            ApprovalLevel.FACULTY_SECRETARY, LocalDate.of(2026, 10, 2),
            ApprovalLevel.DEAN, LocalDate.of(2026, 10, 7),
            ApprovalLevel.RECTOR_SECRETARY, LocalDate.of(2026, 10, 12)));
        assertThat(timeline.estimatedCompletion()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(timeline.overdue()).isFalse();
        assertThat(timeline.deadline()).isEqualTo(days(1));
    }

    @ParameterizedTest
    @EnumSource(value = RecognitionLevel.class, names = {"NATIONAL", "INTERNATIONAL"},
        mode = EnumSource.Mode.EXCLUDE)
    void ac1_aLevelFinalAtTheFacultySecretaryIsDueOnItsDeadline(RecognitionLevel level) {
        StatusEstimator.Timeline timeline = estimator.timeline(
            request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY, days(1)), level);

        assertThat(timeline.levels()).containsExactly(ApprovalLevel.FACULTY_SECRETARY);
        assertThat(timeline.estimatedCompletion()).isEqualTo(LocalDate.of(2026, 10, 2));
    }

    @Test
    void ac1_4_aLateLevelGetsAFreshPeriodFromNowAndTheEstimateNeverMovesEarlier() {
        AwardRequest late = request(RequestStatus.IN_REVIEW, ApprovalLevel.FACULTY_SECRETARY, days(-2));

        StatusEstimator.Timeline timeline = estimator.timeline(late, RecognitionLevel.NATIONAL);

        assertThat(timeline.overdue()).isTrue();
        assertThat(timeline.estimatedCompletion()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(timeline.due()).containsEntry(ApprovalLevel.FACULTY_SECRETARY, LocalDate.of(2026, 10, 6))
            .containsEntry(ApprovalLevel.DEAN, LocalDate.of(2026, 10, 9))
            .containsEntry(ApprovalLevel.RECTOR_SECRETARY, LocalDate.of(2026, 10, 14));
        assertThat(timeline.deadline()).isEqualTo(days(-2));
    }

    @Test
    void ac1_4_aRequestExactlyAtItsDeadlineIsNotOverdue() {
        assertThat(estimator.timeline(request(RequestStatus.SUBMITTED, ApprovalLevel.DEAN, NOW),
            RecognitionLevel.NATIONAL).overdue()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"RETURNED", "APPROVED", "REJECTED", "EXPIRED"})
    void ac1_5_aReturnedOrFinalRequestHasNoEstimateAndIsNotOverdue(RequestStatus status) {
        StatusEstimator.Timeline timeline = estimator.timeline(
            request(status, ApprovalLevel.FACULTY_SECRETARY, days(-5)), RecognitionLevel.NATIONAL);

        assertThat(timeline.estimatedCompletion()).isNull();
        assertThat(timeline.overdue()).isFalse();
        assertThat(timeline.due()).isEmpty();
        assertThat(estimator.isActive(request(status, ApprovalLevel.DEAN, NOW))).isFalse();
    }

    @Test
    void edge_aRequestWithoutDeadlineCountsFromItsSubmission() {
        AwardRequest request = AwardRequest.builder().status(RequestStatus.SUBMITTED)
            .currentLevel(ApprovalLevel.FACULTY_SECRETARY).submittedAt(NOW.minus(Duration.ofDays(1))).build();

        assertThat(estimator.timeline(request, RecognitionLevel.DEPARTMENT).deadline())
            .isEqualTo(Instant.parse("2026-10-05T09:00:00Z"));
    }

    @Test
    void edge_aSubmissionJustBeforeKyivMidnightIsDueOnTheKyivDateThreeWorkingDaysLater() {
        AwardRequest request = request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY,
            estimator.deadline(Instant.parse("2026-10-01T20:59:00Z")));

        assertThat(estimator.timeline(request, RecognitionLevel.DEPARTMENT).estimatedCompletion())
            .isEqualTo(LocalDate.of(2026, 10, 6));
    }

    @Test
    void edge_aChangedPeriodKeepsTheStoredDeadlineAndMovesLaterLevels() {
        StatusEstimator.Timeline timeline = estimator(LONGER_DAYS).timeline(
            request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY, days(1)), RecognitionLevel.NATIONAL);

        assertThat(timeline.due()).containsEntry(ApprovalLevel.FACULTY_SECRETARY, LocalDate.of(2026, 10, 2))
            .containsEntry(ApprovalLevel.DEAN, LocalDate.of(2026, 10, 9));
        assertThat(timeline.estimatedCompletion()).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test
    void edge_aPeriodAcrossTheAutumnClockChangeKeepsTheKyivTime() {
        Instant deadline = estimator.deadline(Instant.parse("2026-10-22T21:30:00Z"));

        assertThat(deadline).isEqualTo(Instant.parse("2026-10-27T22:30:00Z"));
        assertThat(LocalDate.ofInstant(deadline, KYIV)).isEqualTo(LocalDate.of(2026, 10, 28));
    }

    @Test
    void edge_aPeriodAcrossTheSpringClockChangeKeepsTheKyivTime() {
        Instant deadline = estimator.deadline(Instant.parse("2027-03-26T21:30:00Z"));

        assertThat(deadline).isEqualTo(Instant.parse("2027-03-31T20:30:00Z"));
        assertThat(LocalDate.ofInstant(deadline, KYIV)).isEqualTo(LocalDate.of(2027, 3, 31));
    }

    @Test
    void edge_aLaterLevelAcrossTheClockChangeIsDueOnTheThirdWorkingDay() {
        AwardRequest request = request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY,
            Instant.parse("2026-10-22T21:30:00Z"));

        StatusEstimator.Timeline timeline = estimator(DEFAULT_DAYS, Instant.parse("2026-10-21T09:00:00Z"))
            .timeline(request, RecognitionLevel.NATIONAL);

        assertThat(timeline.due()).containsEntry(ApprovalLevel.FACULTY_SECRETARY, LocalDate.of(2026, 10, 23))
            .containsEntry(ApprovalLevel.DEAN, LocalDate.of(2026, 10, 28));
    }

    @Test
    void edge_aReturnedRequestKeepsItsStoredDeadline() {
        assertThat(estimator.timeline(request(RequestStatus.RETURNED, ApprovalLevel.DEAN, days(-1)),
            RecognitionLevel.NATIONAL).deadline()).isEqualTo(days(-1));
    }

    private static StatusEstimator estimator(int days) {
        return estimator(days, NOW);
    }

    private static StatusEstimator estimator(int days, Instant now) {
        return TestWorkflow.estimator(Clock.fixed(now, KYIV), days);
    }

    private static Instant days(int days) {
        return NOW.plus(Duration.ofDays(days));
    }

    private static AwardRequest request(RequestStatus status, ApprovalLevel level, Instant deadline) {
        return AwardRequest.builder().status(status).currentLevel(level).submittedAt(NOW.minus(Duration.ofDays(1)))
            .deadline(deadline).build();
    }
}

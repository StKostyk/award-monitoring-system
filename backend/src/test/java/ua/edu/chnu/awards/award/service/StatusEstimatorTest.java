package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.edu.chnu.awards.support.TestUsers.organization;

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
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.support.TestWorkflow;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;

class StatusEstimatorTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
    private static final int DEFAULT_DAYS = 3;
    private static final int LONGER_DAYS = 5;
    private static final long FACULTY_ID = 9L;
    private static final long DEPARTMENT_ID = 64L;
    private static final long COLLEGE_ID = 3L;

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
        StatusEstimator.Timeline timeline = timeline(estimator,
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
        StatusEstimator.Timeline timeline = timeline(estimator,
            request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY, days(1)), level);

        assertThat(timeline.levels()).containsExactly(ApprovalLevel.FACULTY_SECRETARY);
        assertThat(timeline.estimatedCompletion()).isEqualTo(LocalDate.of(2026, 10, 2));
    }

    @Test
    void ac1_4_aLateLevelGetsAFreshPeriodFromNowAndTheEstimateNeverMovesEarlier() {
        AwardRequest late = request(RequestStatus.IN_REVIEW, ApprovalLevel.FACULTY_SECRETARY, days(-2));

        StatusEstimator.Timeline timeline = timeline(estimator, late, RecognitionLevel.NATIONAL);

        assertThat(timeline.overdue()).isTrue();
        assertThat(timeline.estimatedCompletion()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(timeline.due()).containsEntry(ApprovalLevel.FACULTY_SECRETARY, LocalDate.of(2026, 10, 6))
            .containsEntry(ApprovalLevel.DEAN, LocalDate.of(2026, 10, 9))
            .containsEntry(ApprovalLevel.RECTOR_SECRETARY, LocalDate.of(2026, 10, 14));
        assertThat(timeline.deadline()).isEqualTo(days(-2));
    }

    @Test
    void ac1_4_aRequestExactlyAtItsDeadlineIsNotOverdue() {
        assertThat(timeline(estimator, request(RequestStatus.SUBMITTED, ApprovalLevel.DEAN, NOW),
            RecognitionLevel.NATIONAL).overdue()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"RETURNED", "APPROVED", "REJECTED", "EXPIRED"})
    void ac1_5_aReturnedOrFinalRequestHasNoEstimateAndIsNotOverdue(RequestStatus status) {
        StatusEstimator.Timeline timeline = timeline(estimator,
            request(status, ApprovalLevel.FACULTY_SECRETARY, days(-5)), RecognitionLevel.NATIONAL);

        assertThat(timeline.estimatedCompletion()).isNull();
        assertThat(timeline.overdue()).isFalse();
        assertThat(timeline.due()).isEmpty();
        assertThat(request(status, ApprovalLevel.DEAN, NOW).isOpen()).isFalse();
    }

    @Test
    void edge_aRequestWithoutDeadlineCountsFromItsSubmission() {
        AwardRequest request = AwardRequest.builder().status(RequestStatus.SUBMITTED)
            .currentLevel(ApprovalLevel.FACULTY_SECRETARY).submittedAt(NOW.minus(Duration.ofDays(1))).build();

        assertThat(timeline(estimator, request, RecognitionLevel.DEPARTMENT).deadline())
            .isEqualTo(Instant.parse("2026-10-05T09:00:00Z"));
    }

    @Test
    void edge_aSubmissionJustBeforeKyivMidnightIsDueOnTheKyivDateThreeWorkingDaysLater() {
        AwardRequest request = request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY,
            estimator.deadline(Instant.parse("2026-10-01T20:59:00Z")));

        assertThat(timeline(estimator, request, RecognitionLevel.DEPARTMENT).estimatedCompletion())
            .isEqualTo(LocalDate.of(2026, 10, 6));
    }

    @Test
    void edge_aChangedPeriodKeepsTheStoredDeadlineAndMovesLaterLevels() {
        StatusEstimator.Timeline timeline = timeline(estimator(LONGER_DAYS),
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

        StatusEstimator.Timeline timeline = timeline(estimator(DEFAULT_DAYS, Instant.parse("2026-10-21T09:00:00Z")),
            request, RecognitionLevel.NATIONAL);

        assertThat(timeline.due()).containsEntry(ApprovalLevel.FACULTY_SECRETARY, LocalDate.of(2026, 10, 23))
            .containsEntry(ApprovalLevel.DEAN, LocalDate.of(2026, 10, 28));
    }

    @Test
    void ac1_6_aFacultyPeriodSetsTheDeadlineOfTheFacultyLevelsOnly() {
        Award award = award(RecognitionLevel.NATIONAL, LONGER_DAYS);

        assertThat(estimator.deadline(award, ApprovalLevel.FACULTY_SECRETARY, NOW))
            .isEqualTo(Instant.parse("2026-10-08T09:00:00Z"));
        assertThat(estimator.deadline(award, ApprovalLevel.DEAN, NOW))
            .isEqualTo(Instant.parse("2026-10-08T09:00:00Z"));
        assertThat(estimator.deadline(award, ApprovalLevel.RECTOR_SECRETARY, NOW)).isEqualTo(days(5));
        assertThat(estimator.deadline(award, ApprovalLevel.RECTOR, NOW)).isEqualTo(days(5));
    }

    @Test
    void ac1_6_aUnitAwardOfTheFacultyUsesTheFacultyPeriod() {
        Organization faculty = faculty(LONGER_DAYS);
        Award award = Award.builder().organization(faculty).build();

        assertThat(estimator.workingDays(award, ApprovalLevel.DEAN)).isEqualTo(LONGER_DAYS);
    }

    @Test
    void ac1_6_aUnitOutsideEveryFacultyUsesTheDefault() {
        Organization college = organization(COLLEGE_ID, OrganizationType.COLLEGE);
        Organization department = organization(DEPARTMENT_ID, OrganizationType.DEPARTMENT);
        department.setParent(college);

        assertThat(estimator.workingDays(Award.builder().organization(department).build(), ApprovalLevel.DEAN))
            .isEqualTo(DEFAULT_DAYS);
        assertThat(estimator.workingDays((Organization) null)).isEqualTo(DEFAULT_DAYS);
        assertThat(estimator.workingDays(faculty(null))).isEqualTo(DEFAULT_DAYS);
    }

    @Test
    void ac1_8_theEstimateCountsTheFacultyPeriodAtFacultyLevelsAndTheDefaultAtRectorLevels() {
        StatusEstimator.Timeline timeline = estimator.timeline(award(RecognitionLevel.NATIONAL, LONGER_DAYS),
            request(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY, days(1)));

        assertThat(timeline.due()).containsExactlyInAnyOrderEntriesOf(Map.of(
            ApprovalLevel.FACULTY_SECRETARY, LocalDate.of(2026, 10, 2),
            ApprovalLevel.DEAN, LocalDate.of(2026, 10, 9),
            ApprovalLevel.RECTOR_SECRETARY, LocalDate.of(2026, 10, 14)));
    }

    @Test
    void ac1_8_aLateFacultyLevelGetsAFreshFacultyPeriod() {
        StatusEstimator.Timeline timeline = estimator.timeline(award(RecognitionLevel.DEPARTMENT, LONGER_DAYS),
            request(RequestStatus.IN_REVIEW, ApprovalLevel.FACULTY_SECRETARY, days(-1)));

        assertThat(timeline.overdue()).isTrue();
        assertThat(timeline.estimatedCompletion()).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test
    void edge_aReturnedRequestKeepsItsStoredDeadline() {
        assertThat(timeline(estimator, request(RequestStatus.RETURNED, ApprovalLevel.DEAN, days(-1)),
            RecognitionLevel.NATIONAL).deadline()).isEqualTo(days(-1));
    }

    private static StatusEstimator estimator(int days) {
        return estimator(days, NOW);
    }

    private static StatusEstimator estimator(int days, Instant now) {
        return TestWorkflow.estimator(Clock.fixed(now, KYIV), days);
    }

    private static StatusEstimator.Timeline timeline(StatusEstimator estimator, AwardRequest request,
                                                     RecognitionLevel level) {
        return estimator.timeline(award(level, null), request);
    }

    private static Award award(RecognitionLevel level, Integer facultyDays) {
        Organization department = organization(DEPARTMENT_ID, OrganizationType.DEPARTMENT);
        department.setParent(faculty(facultyDays));
        return Award.builder().organization(department).category(AwardCategory.builder().level(level).build())
            .build();
    }

    private static Organization faculty(Integer days) {
        Organization faculty = organization(FACULTY_ID, OrganizationType.FACULTY);
        faculty.setReviewWorkingDays(days);
        return faculty;
    }

    private static Instant days(int days) {
        return NOW.plus(Duration.ofDays(days));
    }

    private static AwardRequest request(RequestStatus status, ApprovalLevel level, Instant deadline) {
        return AwardRequest.builder().status(status).currentLevel(level).submittedAt(NOW.minus(Duration.ofDays(1)))
            .deadline(deadline).build();
    }
}

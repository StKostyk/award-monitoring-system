package ua.edu.chnu.awards.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.analytics.config.AnalyticsProperties;
import ua.edu.chnu.awards.analytics.dto.ComplianceSummary;
import ua.edu.chnu.awards.analytics.dto.ComplianceSummary.Attention;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.service.ReviewBacklog;
import ua.edu.chnu.awards.common.privacy.PrivacyNoticeProperties;
import ua.edu.chnu.awards.common.privacy.PrivacyNoticeVersions;
import ua.edu.chnu.awards.gdpr.ComplianceFigures;
import ua.edu.chnu.awards.gdpr.dto.RetentionStatus;

class UniversityComplianceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final Duration STALE_AFTER = Duration.ofDays(2);
    private static final String VERSION = "1.1";

    private final ComplianceFigures figures = mock(ComplianceFigures.class);
    private final ReviewBacklog backlog = mock(ReviewBacklog.class);
    private final PrivacyNoticeVersions noticeVersions = mock(PrivacyNoticeVersions.class);
    private final Map<ApprovalLevel, ReviewBacklog.Queue> queues = new EnumMap<>(ApprovalLevel.class);
    private UniversityComplianceService service;

    @BeforeEach
    void setUp() {
        service = new UniversityComplianceService(figures, backlog, noticeVersions,
            new AnalyticsProperties(5, 500, 10, Duration.ofMinutes(5), STALE_AFTER),
            Clock.fixed(NOW, ZoneId.of("Europe/Kyiv")));
        when(noticeVersions.current()).thenReturn(new PrivacyNoticeProperties.Version(VERSION,
            LocalDate.of(2026, 9, 1)));
        for (ApprovalLevel level : ApprovalLevel.values()) {
            queues.put(level, new ReviewBacklog.Queue(0, 0));
        }
        when(backlog.at(NOW)).thenReturn(queues);
    }

    @Test
    void ac1_10_theSummaryCarriesTheCountsReadAtTheRequest() {
        ComplianceFigures.RetentionRun run = run(RetentionStatus.COMPLETED, NOW.minus(Duration.ofHours(6)));
        when(figures.read(VERSION, NOW)).thenReturn(new ComplianceFigures.Counts(120, 100, 40, 3, 1, 0, 2, run));
        queues.put(ApprovalLevel.DEAN, new ReviewBacklog.Queue(4, 0));
        queues.put(ApprovalLevel.RECTOR, new ReviewBacklog.Queue(2, 0));

        ComplianceSummary summary = service.summary();

        assertThat(summary.generatedAt()).isEqualTo(NOW);
        assertThat(summary.status()).isEqualTo(ComplianceSummary.Status.OK);
        assertThat(summary.attention()).isEmpty();
        assertThat(summary.privacyNotice()).isEqualTo(new ComplianceSummary.PrivacyNotice(VERSION, 120, 100));
        assertThat(summary.analyticsConsent()).isEqualTo(new ComplianceSummary.AnalyticsConsent(120, 40));
        assertThat(summary.erasure()).isEqualTo(new ComplianceSummary.Erasure(3, 1, 0));
        assertThat(summary.legalHolds().active()).isEqualTo(2);
        assertThat(summary.lastRetentionRun()).isEqualTo(run);
        assertThat(summary.reviews().open()).isEqualTo(6);
        assertThat(summary.reviews().byLevel()).hasSize(ApprovalLevel.values().length)
            .extracting(ComplianceSummary.LevelReviews::level, ComplianceSummary.LevelReviews::open)
            .contains(tuple(ApprovalLevel.DEAN, 4L), tuple(ApprovalLevel.RECTOR, 2L));
    }

    @Test
    void ac1_11_noRunAtAllAsksForAttention() {
        when(figures.read(VERSION, NOW)).thenReturn(counts(null, 0));

        assertThat(service.summary().attention()).containsExactly(Attention.RETENTION_NOT_RUN);
    }

    @Test
    void ac1_11_aPartialOrInterruptedRunIsFailed() {
        when(figures.read(VERSION, NOW)).thenReturn(counts(run(RetentionStatus.PARTIAL, NOW), 0));
        assertThat(service.summary().attention()).containsExactly(Attention.RETENTION_FAILED);

        when(figures.read(VERSION, NOW)).thenReturn(counts(run(RetentionStatus.INTERRUPTED, NOW), 0));
        assertThat(service.summary().attention()).containsExactly(Attention.RETENTION_FAILED);
    }

    @Test
    void ac1_11_aRunIsStaleOnlyAfterTheConfiguredLimitAndARunningOneIsJudgedByItsStart() {
        when(figures.read(VERSION, NOW)).thenReturn(counts(run(RetentionStatus.COMPLETED, NOW.minus(STALE_AFTER)),
            0));
        assertThat(service.summary().status()).isEqualTo(ComplianceSummary.Status.OK);

        Instant tooOld = NOW.minus(STALE_AFTER).minusSeconds(1);
        when(figures.read(VERSION, NOW)).thenReturn(counts(new ComplianceFigures.RetentionRun(
            RetentionStatus.RUNNING, tooOld, null), 0));
        assertThat(service.summary().attention()).containsExactly(Attention.RETENTION_STALE);
    }

    @Test
    void ac1_11_overdueErasureAndReviewsAskForAttentionTogether() {
        when(figures.read(VERSION, NOW)).thenReturn(counts(run(RetentionStatus.COMPLETED, NOW), 1));
        queues.put(ApprovalLevel.FACULTY_SECRETARY, new ReviewBacklog.Queue(3, 2));

        ComplianceSummary summary = service.summary();

        assertThat(summary.status()).isEqualTo(ComplianceSummary.Status.ATTENTION);
        assertThat(summary.attention()).containsExactly(Attention.ERASURE_OVERDUE, Attention.REVIEWS_OVERDUE);
        assertThat(summary.reviews().overdue()).isEqualTo(2);
    }

    private static ComplianceFigures.Counts counts(ComplianceFigures.RetentionRun run, long erasureOverdue) {
        return new ComplianceFigures.Counts(10, 10, 5, erasureOverdue, 0, erasureOverdue, 0, run);
    }

    private static ComplianceFigures.RetentionRun run(RetentionStatus status, Instant startedAt) {
        return new ComplianceFigures.RetentionRun(status, startedAt, startedAt.plusSeconds(30));
    }
}

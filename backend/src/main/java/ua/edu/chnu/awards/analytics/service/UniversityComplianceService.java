package ua.edu.chnu.awards.analytics.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.analytics.config.AnalyticsProperties;
import ua.edu.chnu.awards.analytics.dto.ComplianceSummary;
import ua.edu.chnu.awards.award.service.ReviewBacklog;
import ua.edu.chnu.awards.common.privacy.PrivacyNoticeVersions;
import ua.edu.chnu.awards.gdpr.ComplianceFigures;
import ua.edu.chnu.awards.gdpr.dto.RetentionStatus;

import lombok.RequiredArgsConstructor;

/**
 * The compliance summary of the executive dashboard, read at each request and never cached.
 */
@Service
@RequiredArgsConstructor
public class UniversityComplianceService {

    private static final Set<RetentionStatus> FAILED = Set.of(RetentionStatus.PARTIAL, RetentionStatus.INTERRUPTED);

    private final ComplianceFigures figures;
    private final ReviewBacklog backlog;
    private final PrivacyNoticeVersions noticeVersions;
    private final AnalyticsProperties properties;
    private final Clock clock;

    /**
     * The summary as it stands.
     *
     * @return counts only, with the reasons for attention
     */
    public ComplianceSummary summary() {
        Instant now = Instant.now(clock);
        String version = noticeVersions.current().version();
        ComplianceFigures.Counts counts = figures.read(version, now);
        List<ComplianceSummary.LevelReviews> levels = backlog.at(now).entrySet().stream()
            .map(entry -> new ComplianceSummary.LevelReviews(entry.getKey(), entry.getValue().open(),
                entry.getValue().overdue()))
            .toList();
        ComplianceSummary.Reviews reviews = new ComplianceSummary.Reviews(
            levels.stream().mapToLong(ComplianceSummary.LevelReviews::open).sum(),
            levels.stream().mapToLong(ComplianceSummary.LevelReviews::overdue).sum(), levels);
        List<ComplianceSummary.Attention> attention = attention(counts, reviews, now);
        return new ComplianceSummary(now,
            attention.isEmpty() ? ComplianceSummary.Status.OK : ComplianceSummary.Status.ATTENTION, attention,
            new ComplianceSummary.PrivacyNotice(version, counts.activeAccounts(), counts.acknowledged()),
            new ComplianceSummary.AnalyticsConsent(counts.activeAccounts(), counts.analyticsGranted()),
            new ComplianceSummary.Erasure(counts.erasurePending(), counts.erasurePostponed(),
                counts.erasureOverdue()),
            new ComplianceSummary.LegalHolds(counts.activeLegalHolds()), counts.lastRetentionRun(), reviews);
    }

    private List<ComplianceSummary.Attention> attention(ComplianceFigures.Counts counts,
                                                        ComplianceSummary.Reviews reviews, Instant now) {
        List<ComplianceSummary.Attention> reasons = new ArrayList<>();
        ComplianceFigures.RetentionRun run = counts.lastRetentionRun();
        if (run == null) {
            reasons.add(ComplianceSummary.Attention.RETENTION_NOT_RUN);
        } else {
            if (FAILED.contains(run.status())) {
                reasons.add(ComplianceSummary.Attention.RETENTION_FAILED);
            }
            if (run.startedAt().isBefore(now.minus(properties.retentionStaleAfter()))) {
                reasons.add(ComplianceSummary.Attention.RETENTION_STALE);
            }
        }
        if (counts.erasureOverdue() > 0) {
            reasons.add(ComplianceSummary.Attention.ERASURE_OVERDUE);
        }
        if (reviews.overdue() > 0) {
            reasons.add(ComplianceSummary.Attention.REVIEWS_OVERDUE);
        }
        return reasons;
    }
}

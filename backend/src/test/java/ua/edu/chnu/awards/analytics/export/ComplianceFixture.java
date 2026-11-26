package ua.edu.chnu.awards.analytics.export;

import java.time.Instant;
import java.util.List;

import ua.edu.chnu.awards.analytics.dto.ComplianceSummary;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.gdpr.ComplianceFigures;
import ua.edu.chnu.awards.gdpr.dto.RetentionStatus;

/**
 * Compliance summaries for the board report.
 */
final class ComplianceFixture {

    private ComplianceFixture() {
    }

    static ComplianceSummary compliance() {
        return new ComplianceSummary(Instant.parse("2026-10-25T12:30:00Z"), ComplianceSummary.Status.ATTENTION,
            List.of(ComplianceSummary.Attention.REVIEWS_OVERDUE),
            new ComplianceSummary.PrivacyNotice("1.2", 220, 180), new ComplianceSummary.AnalyticsConsent(220, 95),
            new ComplianceSummary.Erasure(3, 1, 0), new ComplianceSummary.LegalHolds(2),
            new ComplianceFigures.RetentionRun(RetentionStatus.COMPLETED, Instant.parse("2026-10-25T00:00:00Z"),
                Instant.parse("2026-10-25T00:01:00Z")),
            new ComplianceSummary.Reviews(5, 2, List.of(
                new ComplianceSummary.LevelReviews(ApprovalLevel.FACULTY_SECRETARY, 3, 2),
                new ComplianceSummary.LevelReviews(ApprovalLevel.RECTOR_SECRETARY, 2, 0))));
    }

    static ComplianceSummary quietCompliance() {
        return new ComplianceSummary(Instant.parse("2026-10-25T12:30:00Z"), ComplianceSummary.Status.OK, List.of(),
            new ComplianceSummary.PrivacyNotice("1.2", 0, 0), new ComplianceSummary.AnalyticsConsent(0, 0),
            new ComplianceSummary.Erasure(0, 0, 0), new ComplianceSummary.LegalHolds(0), null,
            new ComplianceSummary.Reviews(0, 0, List.of()));
    }
}

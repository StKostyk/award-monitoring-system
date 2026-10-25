package ua.edu.chnu.awards.analytics.dto;

import java.time.Instant;
import java.util.List;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.gdpr.ComplianceFigures;

/**
 * The state of the data protection and review duties of the university, in counts only.
 *
 * @param generatedAt      when the counts were read
 * @param status           whether anything asks for attention
 * @param attention        the reasons, empty when the status is OK
 * @param privacyNotice    acknowledgements of the notice in force
 * @param analyticsConsent consents to be named in analytics
 * @param erasure          pending erasure requests
 * @param legalHolds       legal holds
 * @param lastRetentionRun the latest run of the retention job; null when it never ran
 * @param reviews          open review requests
 */
public record ComplianceSummary(Instant generatedAt, Status status, List<Attention> attention,
                                PrivacyNotice privacyNotice, AnalyticsConsent analyticsConsent, Erasure erasure,
                                LegalHolds legalHolds, ComplianceFigures.RetentionRun lastRetentionRun,
                                Reviews reviews) {

    /**
     * Copies the reasons.
     *
     * @param generatedAt      when the counts were read
     * @param status           whether anything asks for attention
     * @param attention        the reasons
     * @param privacyNotice    acknowledgements of the notice
     * @param analyticsConsent consents to be named
     * @param erasure          pending erasure requests
     * @param legalHolds       legal holds
     * @param lastRetentionRun the latest retention run
     * @param reviews          open review requests
     */
    public ComplianceSummary {
        attention = List.copyOf(attention);
    }

    /**
     * The overall state.
     */
    public enum Status {
        /** Nothing asks for attention. */
        OK,
        /** At least one reason. */
        ATTENTION
    }

    /**
     * A reason for attention.
     */
    public enum Attention {
        /** The retention job never ran. */
        RETENTION_NOT_RUN,
        /** The last run ended partial or interrupted. */
        RETENTION_FAILED,
        /** The last run started longer ago than the configured limit. */
        RETENTION_STALE,
        /** Erasure requests past their schedule. */
        ERASURE_OVERDUE,
        /** Review requests past their deadline. */
        REVIEWS_OVERDUE
    }

    /**
     * Acknowledgements of the privacy notice in force.
     *
     * @param version      the version in force
     * @param accounts     active accounts
     * @param acknowledged of them, those whose latest acknowledgement is of this version
     */
    public record PrivacyNotice(String version, long accounts, long acknowledged) {
    }

    /**
     * Consents to be named in analytics.
     *
     * @param accounts active accounts
     * @param granted  of them, those whose latest choice is a grant
     */
    public record AnalyticsConsent(long accounts, long granted) {
    }

    /**
     * Pending erasure requests.
     *
     * @param pending   pending requests
     * @param postponed of them, postponed
     * @param overdue   of them, not postponed and scheduled more than a day ago
     */
    public record Erasure(long pending, long postponed, long overdue) {
    }

    /**
     * Legal holds.
     *
     * @param active holds not lifted
     */
    public record LegalHolds(long active) {
    }

    /**
     * Open review requests.
     *
     * @param open    open requests
     * @param overdue of them, past their deadline
     * @param byLevel per level, in level order
     */
    public record Reviews(long open, long overdue, List<LevelReviews> byLevel) {

        /**
         * Copies the levels.
         *
         * @param open    open requests
         * @param overdue of them, past their deadline
         * @param byLevel per level
         */
        public Reviews {
            byLevel = List.copyOf(byLevel);
        }
    }

    /**
     * Open review requests of one level.
     *
     * @param level   the level
     * @param open    open requests
     * @param overdue of them, past their deadline
     */
    public record LevelReviews(ApprovalLevel level, long open, long overdue) {
    }
}

package ua.edu.chnu.awards.gdpr;

import java.time.Instant;

import ua.edu.chnu.awards.gdpr.dto.RetentionStatus;

/**
 * Counts of the data protection duties of the whole university, for modules that report on them; no person is
 * identified.
 */
public interface ComplianceFigures {

    /**
     * The counts as they stand.
     *
     * @param noticeVersion the privacy notice version in force
     * @param now           the count time; a pending erasure request scheduled more than a day before it is overdue
     * @return the counts
     */
    Counts read(String noticeVersion, Instant now);

    /**
     * The counts of one reading.
     *
     * @param activeAccounts   active accounts
     * @param acknowledged     active accounts whose latest data processing record acknowledges the notice version
     * @param analyticsGranted active accounts whose latest analytics record is a grant
     * @param erasurePending   pending erasure requests
     * @param erasurePostponed of them, postponed
     * @param erasureOverdue   of them, not postponed and scheduled more than a day ago
     * @param activeLegalHolds legal holds not lifted
     * @param lastRetentionRun the latest run of the retention job; null when it never ran
     */
    record Counts(long activeAccounts, long acknowledged, long analyticsGranted, long erasurePending,
                  long erasurePostponed, long erasureOverdue, long activeLegalHolds, RetentionRun lastRetentionRun) {
    }

    /**
     * A run of the retention job.
     *
     * @param status     the state
     * @param startedAt  when it started
     * @param finishedAt when it finished; null while it runs
     */
    record RetentionRun(RetentionStatus status, Instant startedAt, Instant finishedAt) {
    }
}

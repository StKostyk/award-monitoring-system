package ua.edu.chnu.awards.gdpr.dto;

import java.time.Instant;
import java.util.List;

import ua.edu.chnu.awards.common.retention.RetentionResult;

/**
 * One run of the retention job.
 *
 * @param id         identifier
 * @param status     the state of the run
 * @param startedAt  when it started
 * @param finishedAt when it ended, null while running
 * @param results    the counts of each policy applied so far
 */
public record RetentionRunEntry(long id, RetentionStatus status, Instant startedAt, Instant finishedAt,
                                List<RetentionResult> results) {

    /**
     * Keeps an unmodifiable copy of the results.
     */
    public RetentionRunEntry {
        results = List.copyOf(results);
    }
}

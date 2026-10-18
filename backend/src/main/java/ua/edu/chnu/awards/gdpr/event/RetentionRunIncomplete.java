package ua.edu.chnu.awards.gdpr.event;

import java.time.Instant;
import java.util.List;

/**
 * A retention run did not complete; the data protection officers are told once the transaction commits.
 *
 * @param runId          the run
 * @param startedAt      when it started
 * @param status         {@code PARTIAL} or {@code INTERRUPTED}
 * @param failedPolicies the keys of the policies that failed
 * @param recipients     the addresses of the officers
 */
public record RetentionRunIncomplete(long runId, Instant startedAt, String status, List<String> failedPolicies,
                                     List<String> recipients) {

    public RetentionRunIncomplete {
        failedPolicies = List.copyOf(failedPolicies);
        recipients = List.copyOf(recipients);
    }
}

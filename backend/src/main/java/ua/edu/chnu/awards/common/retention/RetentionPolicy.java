package ua.edu.chnu.awards.common.retention;

import java.time.Duration;
import java.time.temporal.TemporalAmount;

/**
 * One rule of the nightly retention run. The run calls every policy in ascending {@link #order()}; a policy works
 * through {@link RetentionContext#inBatches}, so each batch commits on its own and a failure keeps the earlier ones.
 */
public interface RetentionPolicy {

    /**
     * The stable name of the policy, used in the run results and the metrics.
     *
     * @return the key, such as {@code notifications}
     */
    String key();

    /**
     * Where this policy runs; see {@link RetentionOrderConstants}.
     *
     * @return the position, lower first
     */
    int order();

    /**
     * What the policy does with the records it selects.
     *
     * @return the action
     */
    RetentionAction action();

    /**
     * The configured period as an ISO-8601 period, such as {@code P365D}.
     *
     * @return the period
     */
    String period();

    /**
     * Applies the policy once.
     *
     * @param context the moment of the run, the people under a legal hold and the batch size
     * @return the counts of the policy
     */
    RetentionResult apply(RetentionContext context);

    /**
     * Writes a period as an ISO-8601 period, a duration of whole days in days.
     *
     * @param period a {@link Duration} or a {@link java.time.Period}
     * @return the period, such as {@code P30D}, {@code P7Y}, or {@code PT5M} for a part of a day
     */
    static String iso(TemporalAmount period) {
        return period instanceof Duration duration && duration.toSeconds() % Duration.ofDays(1).toSeconds() == 0
            ? "P" + duration.toDays() + "D" : period.toString();
    }
}

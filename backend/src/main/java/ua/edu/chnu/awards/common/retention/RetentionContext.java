package ua.edu.chnu.awards.common.retention;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAmount;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

import org.springframework.transaction.support.TransactionOperations;

/**
 * What one policy of a run works with: the moment of the run, the people under a legal hold when it started and the
 * batch size. It counts the rows of the committed batches, so a policy that fails still reports what it did.
 */
@SuppressWarnings("PMD.DataClass")
public final class RetentionContext {

    private final Instant now;
    private final Set<Long> heldUserIds;
    private final int batchSize;
    private final TransactionOperations transactions;
    private int affected;

    /**
     * Creates the context of one policy.
     *
     * @param now          the moment of the run
     * @param heldUserIds  the people under an active legal hold
     * @param batchSize    the most rows one batch changes
     * @param transactions runs each batch in its own transaction
     */
    public RetentionContext(Instant now, Set<Long> heldUserIds, int batchSize, TransactionOperations transactions) {
        this.now = now;
        this.heldUserIds = Set.copyOf(heldUserIds);
        this.batchSize = batchSize;
        this.transactions = transactions;
    }

    /**
     * Runs a batch in its own transaction until a batch changes fewer rows than the batch size.
     *
     * @param batch changes at most {@link #batchSize()} rows and returns how many it changed
     * @return the rows changed by all batches
     */
    public int inBatches(IntSupplier batch) {
        int total = 0;
        int changed;
        do {
            changed = Objects.requireNonNull(transactions.execute(status -> batch.getAsInt()));
            total += changed;
            affected += changed;
        } while (changed >= batchSize);
        return total;
    }

    /**
     * The moment of the run.
     *
     * @return the moment
     */
    public Instant now() {
        return now;
    }

    /**
     * The people under an active legal hold when the run started.
     *
     * @return their account ids
     */
    public Set<Long> heldUserIds() {
        return heldUserIds;
    }

    /**
     * The people under an active legal hold as a PostgreSQL array literal, for {@code cast(:held as bigint[])}.
     *
     * @return the literal, such as {@code {4,17}}
     */
    public String heldArray() {
        return heldUserIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }

    /**
     * The moment a period before the run, a {@link java.time.Period} counted in UTC calendar units.
     *
     * @param period a {@link java.time.Duration} or a {@link java.time.Period}
     * @return the cut-off
     */
    public Instant before(TemporalAmount period) {
        return cutoff(now, period);
    }

    /**
     * The moment a period before a given instant, a {@link java.time.Period} counted in UTC calendar units.
     *
     * @param at     the instant to count back from
     * @param period a {@link java.time.Duration} or a {@link java.time.Period}
     * @return the cut-off
     */
    public static Instant cutoff(Instant at, TemporalAmount period) {
        return at.atOffset(ZoneOffset.UTC).minus(period).toInstant();
    }

    /**
     * The most rows one batch changes.
     *
     * @return the batch size
     */
    public int batchSize() {
        return batchSize;
    }

    /**
     * The rows changed by the committed batches so far.
     *
     * @return the count
     */
    public int affected() {
        return affected;
    }
}

package ua.edu.chnu.awards.common.retention;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * A policy that deletes the rows of one table in batches. The batch statement gets {@code :now}, the moment of the
 * run, {@code :before}, that moment less the period, and {@code :limit}, the batch size.
 */
public abstract class AbstractDeletePolicy implements RetentionPolicy {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates the policy.
     *
     * @param jdbc runs the batch statement
     */
    protected AbstractDeletePolicy(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RetentionAction action() {
        return RetentionAction.DELETE;
    }

    @Override
    public String period() {
        return RetentionPolicy.days(retained());
    }

    @Override
    public RetentionResult apply(RetentionContext context) {
        MapSqlParameterSource params = new MapSqlParameterSource("now", utc(context.now()))
            .addValue("before", utc(context.now().minus(retained())))
            .addValue("limit", context.batchSize());
        return RetentionResult.completed(key(), context.inBatches(() -> jdbc.update(deleteBatch(), params)), 0);
    }

    /**
     * How long a row is kept.
     *
     * @return the period
     */
    protected abstract Duration retained();

    /**
     * The statement deleting one batch.
     *
     * @return the SQL with {@code :now}, {@code :before} and {@code :limit}
     */
    protected abstract String deleteBatch();

    private static OffsetDateTime utc(Instant at) {
        return at.atOffset(ZoneOffset.UTC);
    }
}

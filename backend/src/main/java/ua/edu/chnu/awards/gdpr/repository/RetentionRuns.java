package ua.edu.chnu.awards.gdpr.repository;

import static ua.edu.chnu.awards.common.JdbcRowUtils.instant;
import static ua.edu.chnu.awards.common.JdbcRowUtils.utc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import ua.edu.chnu.awards.common.retention.RetentionResult;
import ua.edu.chnu.awards.gdpr.dto.RetentionStatus;

import lombok.RequiredArgsConstructor;

/**
 * The runs of the retention job with the results of their policies; at most one run works at a time.
 */
@Repository
@RequiredArgsConstructor
public class RetentionRuns {

    private static final String RUN_ID = "runId";
    private static final String RESULTS = "results";

    private static final String TRY_LOCK = "select pg_try_advisory_lock(hashtext('retention-run'))";

    private static final String UNLOCK = "select pg_advisory_unlock(hashtext('retention-run'))";

    private static final String INTERRUPT = """
        update retention_runs set status = 'INTERRUPTED', finished_at = :at
         where status = 'RUNNING'
        returning run_id, started_at, results::text as results
        """;

    private static final String START = "insert into retention_runs (started_at) values (:at) returning run_id";

    private static final String PROGRESS =
        "update retention_runs set results = cast(:results as jsonb) where run_id = :runId";

    private static final String FINISH = """
        update retention_runs set status = :status, finished_at = :at, results = cast(:results as jsonb)
         where run_id = :runId
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RetentionResultsJson resultsJson;

    /**
     * Runs the work while this connection holds the run lock; a second start meanwhile does nothing.
     *
     * @param work the run
     * @param <T>  what the run returns
     * @return what the work returned, or empty when another run holds the lock
     */
    public <T> Optional<T> exclusively(Supplier<T> work) {
        return Objects.requireNonNull(jdbc.getJdbcTemplate().execute((ConnectionCallback<Optional<T>>) connection -> {
            if (!lock(connection, TRY_LOCK)) {
                return Optional.empty();
            }
            try {
                return Optional.of(work.get());
            } finally {
                lock(connection, UNLOCK);
            }
        }));
    }

    /**
     * Marks the runs still working as interrupted; called before a new run starts, under the lock.
     *
     * @param at the moment they are closed
     * @return the closed runs
     */
    public List<Interrupted> interruptRunning(Instant at) {
        return jdbc.query(INTERRUPT, new MapSqlParameterSource("at", utc(at)), (row, index) ->
            new Interrupted(row.getLong("run_id"), instant(row, "started_at"),
                RetentionResult.failedPolicies(resultsJson.read(row.getString(RESULTS)))));
    }

    /**
     * Records a new run.
     *
     * @param at when it starts
     * @return the run id
     */
    public long start(Instant at) {
        return Objects.requireNonNull(jdbc.queryForObject(START, new MapSqlParameterSource("at", utc(at)),
            Long.class));
    }

    /**
     * Saves the results of the policies done so far, so an interrupted run still shows them.
     *
     * @param runId   the run
     * @param results the results so far
     */
    public void progress(long runId, List<RetentionResult> results) {
        jdbc.update(PROGRESS, new MapSqlParameterSource(RUN_ID, runId).addValue(RESULTS, resultsJson.write(results)));
    }

    /**
     * Closes a run.
     *
     * @param runId   the run
     * @param status  how it ended
     * @param results the results of every policy
     * @param at      when it ended
     */
    public void finish(long runId, RetentionStatus status, List<RetentionResult> results, Instant at) {
        jdbc.update(FINISH, new MapSqlParameterSource(RUN_ID, runId)
            .addValue("status", status.name())
            .addValue(RESULTS, resultsJson.write(results))
            .addValue("at", utc(at)));
    }

    private static boolean lock(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery(sql)) {
            return row.next() && row.getBoolean(1);
        }
    }

    /**
     * A run found still working when the next one started.
     *
     * @param runId          the run
     * @param startedAt      when it started
     * @param failedPolicies the policies it recorded as failed before it stopped
     */
    public record Interrupted(long runId, Instant startedAt, List<String> failedPolicies) {

        public Interrupted {
            failedPolicies = List.copyOf(failedPolicies);
        }
    }
}

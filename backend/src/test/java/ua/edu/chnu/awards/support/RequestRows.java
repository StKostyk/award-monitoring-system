package ua.edu.chnu.awards.support;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inserts an approval request row directly, below the service rules. Unset columns are a request submitted now
 * and waiting for the faculty secretary until three days after its submission.
 */
public final class RequestRows {

    private static final String INSERT = "insert into award_requests (award_id, submitter_id, status, "
        + "current_level, submitted_at, deadline) values (?, ?, ?, ?, ?, ?) returning request_id";

    private final JdbcTemplate jdbc;
    private final long awardId;
    private final long submitterId;
    private String status = "SUBMITTED";
    private String level = "FACULTY_SECRETARY";
    private Instant submittedAt = Instant.now();
    private Instant deadline;
    private boolean deadlineSet;

    private RequestRows(JdbcTemplate jdbc, long awardId, long submitterId) {
        this.jdbc = jdbc;
        this.awardId = awardId;
        this.submitterId = submitterId;
    }

    /**
     * Starts a request row of an award.
     *
     * @param jdbc        template of the test database
     * @param awardId     the award
     * @param submitterId who submitted it
     * @return the row with its defaults
     */
    public static RequestRows request(JdbcTemplate jdbc, long awardId, long submitterId) {
        return new RequestRows(jdbc, awardId, submitterId);
    }

    /**
     * The request of an award.
     *
     * @param jdbc    template of the test database
     * @param awardId the award
     * @return id of its request
     */
    public static long idOf(JdbcTemplate jdbc, long awardId) {
        return jdbc.queryForObject("select request_id from award_requests where award_id = ?", Long.class, awardId);
    }

    /**
     * Sets the status.
     *
     * @param value status name, such as {@code RETURNED}
     * @return this row
     */
    public RequestRows status(String value) {
        status = value;
        return this;
    }

    /**
     * Sets the current level.
     *
     * @param value level name, such as {@code DEAN}
     * @return this row
     */
    public RequestRows level(String value) {
        level = value;
        return this;
    }

    /**
     * Sets the submission time.
     *
     * @param value when the request was submitted
     * @return this row
     */
    public RequestRows submittedAt(Instant value) {
        submittedAt = value;
        return this;
    }

    /**
     * Sets the deadline of the current level.
     *
     * @param value the deadline, null for none
     * @return this row
     */
    public RequestRows deadline(Instant value) {
        deadline = value;
        deadlineSet = true;
        return this;
    }

    /**
     * Inserts the row.
     *
     * @return id of the new request
     */
    public long insert() {
        Instant end = deadlineSet ? deadline : TestWorkflow.estimator().deadline(submittedAt);
        return jdbc.queryForObject(INSERT, Long.class, awardId, submitterId, status, level,
            Timestamp.from(submittedAt), end == null ? null : Timestamp.from(end));
    }
}

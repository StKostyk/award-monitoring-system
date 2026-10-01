package ua.edu.chnu.awards.support;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inserts a reviewer decision row directly, below the service rules. Unset columns are a return to the owner by
 * the faculty secretary, made now without a comment.
 */
public final class DecisionRows {

    private static final String INSERT = "insert into review_decisions (request_id, reviewer_id, decision, level, "
        + "comments, decided_at) values (?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final long requestId;
    private final long reviewerId;
    private String decision = "RETURNED";
    private String level = "FACULTY_SECRETARY";
    private String comments;
    private Instant decidedAt = Instant.now();

    private DecisionRows(JdbcTemplate jdbc, long requestId, long reviewerId) {
        this.jdbc = jdbc;
        this.requestId = requestId;
        this.reviewerId = reviewerId;
    }

    /**
     * Starts a decision row on a request.
     *
     * @param jdbc       template of the test database
     * @param requestId  the request
     * @param reviewerId who decided
     * @return the row with its defaults
     */
    public static DecisionRows decision(JdbcTemplate jdbc, long requestId, long reviewerId) {
        return new DecisionRows(jdbc, requestId, reviewerId);
    }

    /**
     * Sets the decision.
     *
     * @param value decision name, such as {@code APPROVED}
     * @return this row
     */
    public DecisionRows type(String value) {
        decision = value;
        return this;
    }

    /**
     * Sets the level the decision was made at.
     *
     * @param value level name, such as {@code DEAN}
     * @return this row
     */
    public DecisionRows level(String value) {
        level = value;
        return this;
    }

    /**
     * Sets the reviewer comment.
     *
     * @param value the comment, null for none
     * @return this row
     */
    public DecisionRows comments(String value) {
        comments = value;
        return this;
    }

    /**
     * Sets the decision time.
     *
     * @param value when it was made
     * @return this row
     */
    public DecisionRows decidedAt(Instant value) {
        decidedAt = value;
        return this;
    }

    /**
     * Inserts the row.
     */
    public void insert() {
        jdbc.update(INSERT, requestId, reviewerId, decision, level, comments, Timestamp.from(decidedAt));
    }
}

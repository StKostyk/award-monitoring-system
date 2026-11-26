package ua.edu.chnu.awards.award.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;

import lombok.RequiredArgsConstructor;

/**
 * The open review requests of every organisation per level, and those of them past their deadline.
 */
@Component
@RequiredArgsConstructor
public class ReviewBacklog {

    private static final String COUNTS = "SELECT current_level, count(*) AS open,"
        + " count(*) FILTER (WHERE deadline < ?) AS overdue FROM award_requests"
        + " WHERE status IN ('SUBMITTED', 'IN_REVIEW', 'ESCALATED') GROUP BY current_level";

    private final JdbcTemplate jdbc;

    /**
     * The queue as it stands.
     *
     * @param now the count time; a request whose deadline lies before it is overdue
     * @return every level, in level order, zeros included
     */
    @Transactional(readOnly = true)
    public Map<ApprovalLevel, Queue> at(Instant now) {
        Map<ApprovalLevel, Queue> queues = new EnumMap<>(ApprovalLevel.class);
        for (ApprovalLevel level : ApprovalLevel.values()) {
            queues.put(level, new Queue(0, 0));
        }
        jdbc.query(COUNTS, row -> {
            queues.put(ApprovalLevel.valueOf(row.getString("current_level")),
                new Queue(row.getLong("open"), row.getLong("overdue")));
        }, Timestamp.from(now));
        return queues;
    }

    /**
     * The requests of one level.
     *
     * @param open    open requests
     * @param overdue of them, past their deadline
     */
    public record Queue(long open, long overdue) {
    }
}

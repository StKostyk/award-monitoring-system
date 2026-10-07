package ua.edu.chnu.awards.award.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.ReviewDecision;

/**
 * Read access to {@link ReviewDecision} rows.
 */
public interface ReviewDecisionRepository extends JpaRepository<ReviewDecision, Long> {

    /**
     * The decisions on a request, oldest first, with their reviewers and delegators loaded.
     *
     * @param requestId the request
     * @return the decisions
     */
    @Query("""
        select d from ReviewDecision d join fetch d.reviewer left join fetch d.delegator
        where d.requestId = :requestId
        order by d.decidedAt, d.id
        """)
    List<ReviewDecision> findByRequestId(@Param("requestId") Long requestId);

    /**
     * Whether a reviewer decided on the request at one of the given levels.
     *
     * @param requestId the request
     * @param levels    the levels
     * @return true when such a decision exists
     */
    boolean existsByRequestIdAndLevelIn(Long requestId, Collection<ApprovalLevel> levels);
}

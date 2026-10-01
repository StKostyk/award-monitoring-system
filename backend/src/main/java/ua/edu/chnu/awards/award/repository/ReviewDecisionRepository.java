package ua.edu.chnu.awards.award.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ua.edu.chnu.awards.award.entity.ReviewDecision;

/**
 * Read access to {@link ReviewDecision} rows.
 */
public interface ReviewDecisionRepository extends JpaRepository<ReviewDecision, Long> {

    /**
     * The decisions on a request, oldest first, with their reviewers loaded.
     *
     * @param requestId the request
     * @return the decisions
     */
    @Query("""
        select d from ReviewDecision d join fetch d.reviewer
        where d.requestId = :requestId
        order by d.decidedAt, d.id
        """)
    List<ReviewDecision> findByRequestId(@Param("requestId") Long requestId);
}

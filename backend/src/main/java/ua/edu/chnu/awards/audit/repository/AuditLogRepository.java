package ua.edu.chnu.awards.audit.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ua.edu.chnu.awards.audit.entity.AuditLog;

/**
 * Access to {@link AuditLog} rows.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Condition on {@code audit_logs a} selecting the rows about the award {@code :awardId}. */
    String ABOUT_AWARD = "((a.entity_type = 'awards' and a.action_type <> 'DELETE' and a.entity_id = :awardId) "
        + "or (a.entity_type = 'awards' and a.action_type = 'DELETE' "
        + "and a.old_values->>'award_id' = cast(:awardId as text)) "
        + "or (a.entity_type = 'award_requests' "
        + "and coalesce(a.new_values, a.old_values)->>'award_id' = cast(:awardId as text)))";

    /**
     * The rows about an award, newest first: rows of {@code awards} with its id (table triggers and application
     * events) and trigger rows of its approval request, also after the request is gone. Trigger {@code DELETE}
     * rows are matched on the deleted row, because those written before V023 carry the owner's id in
     * {@code entity_id}.
     *
     * @param awardId  the award
     * @param pageable page; its sort is ignored
     * @return the page
     */
    @Query(value = "select a.* from audit_logs a where " + ABOUT_AWARD
        + " order by a.created_at desc, a.log_id desc",
        countQuery = "select count(*) from audit_logs a where " + ABOUT_AWARD,
        nativeQuery = true)
    Page<AuditLog> findAboutAward(long awardId, Pageable pageable);

    /**
     * The newest rows about an award, as {@link #findAboutAward} selects them.
     *
     * @param awardId the award
     * @param limit   the most rows to return
     * @return the rows, newest first
     */
    @Query(value = "select a.* from audit_logs a where " + ABOUT_AWARD
        + " order by a.created_at desc, a.log_id desc limit :limit",
        nativeQuery = true)
    List<AuditLog> findNewestAboutAward(long awardId, int limit);
}

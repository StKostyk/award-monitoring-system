package ua.edu.chnu.awards.award.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ua.edu.chnu.awards.award.entity.AwardRequest;

/**
 * Access to {@link AwardRequest} rows.
 */
public interface AwardRequestRepository extends JpaRepository<AwardRequest, Long>,
    JpaSpecificationExecutor<AwardRequest> {

    /**
     * The request of an award.
     *
     * @param awardId the award
     * @return the request, empty for a draft
     */
    Optional<AwardRequest> findByAwardId(Long awardId);

    /**
     * The requests of several awards.
     *
     * @param awardIds the awards
     * @return their requests; drafts have none
     */
    List<AwardRequest> findByAwardIdIn(Collection<Long> awardIds);

    /**
     * The request of an award, locked for the rest of the transaction, so claims, releases, hand-overs and
     * decisions on one request run one after the other.
     *
     * @param awardId the award
     * @return the request, empty for a draft that was never submitted
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AwardRequest r where r.award.id = :awardId")
    Optional<AwardRequest> findByAwardIdForUpdate(@Param("awardId") Long awardId);

    /**
     * A page of requests with what a review item shows.
     *
     * @param specification the filter
     * @param pageable      page and order
     * @return the page
     */
    @Override
    @EntityGraph(attributePaths = {"award", "award.owner", "award.organization", "award.category", "submitter",
        "currentReviewer"})
    Page<AwardRequest> findAll(Specification<AwardRequest> specification, Pageable pageable);
}

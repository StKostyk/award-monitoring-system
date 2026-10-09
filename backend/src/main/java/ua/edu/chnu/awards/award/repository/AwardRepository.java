package ua.edu.chnu.awards.award.repository;

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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ua.edu.chnu.awards.award.entity.Award;

/**
 * Access to {@link Award} rows.
 */
public interface AwardRepository extends JpaRepository<Award, Long>, JpaSpecificationExecutor<Award> {

    /**
     * An award with what its response shows.
     *
     * @param id the award
     * @return the award, empty when unknown
     */
    @EntityGraph(attributePaths = {"owner", "organization", "category"})
    Optional<Award> findWithDetailsById(Long id);

    /**
     * An award locked for the rest of the transaction, so two submissions of it run one after the other.
     *
     * @param id the award
     * @return the award, empty when unknown
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Award a where a.id = :id")
    Optional<Award> findForUpdate(@Param("id") Long id);

    /**
     * Stores the visibility of an award without touching its version or other columns; the entity maps the column
     * read-only.
     *
     * @param id         the award
     * @param visibility the visibility name
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "update awards set visibility = :visibility where award_id = :id", nativeQuery = true)
    void updateVisibility(@Param("id") long id, @Param("visibility") String visibility);

    /**
     * A page of awards with what their responses show.
     *
     * @param specification the filter
     * @param pageable      page and order
     * @return the page
     */
    @Override
    @EntityGraph(attributePaths = {"owner", "organization", "category"})
    Page<Award> findAll(Specification<Award> specification, Pageable pageable);

    /**
     * Every award of an owner in every status, with organisation and category loaded.
     *
     * @param ownerId the owner
     * @return awards, newest first
     */
    @EntityGraph(attributePaths = {"organization", "category"})
    List<Award> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);
}

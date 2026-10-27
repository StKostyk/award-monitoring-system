package ua.edu.chnu.awards.award.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
     * The award an owner created with a creation key.
     *
     * @param ownerId     the owner
     * @param creationKey the key sent with the creation
     * @return the award id, empty when no award was created with that key
     */
    @Query(value = "select award_id from awards where user_id = :ownerId and creation_key = :creationKey",
        nativeQuery = true)
    Optional<Long> findIdByCreationKey(@Param("ownerId") Long ownerId, @Param("creationKey") UUID creationKey);

    /**
     * Stores the key a draft was created with; a second draft of the same owner with the key is refused by the
     * unique index.
     *
     * @param id          the new draft
     * @param creationKey the key sent with the creation
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "update awards set creation_key = :creationKey where award_id = :id", nativeQuery = true)
    void keepCreationKey(@Param("id") Long id, @Param("creationKey") UUID creationKey);

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
     * Sets the word similarity the trigram index uses for the rest of the transaction.
     *
     * @param threshold the lowest similarity, as text
     * @return the threshold set
     */
    @Query(value = "select set_config('pg_trgm.word_similarity_threshold', :threshold, true)", nativeQuery = true)
    String useWordSimilarity(@Param("threshold") String threshold);

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

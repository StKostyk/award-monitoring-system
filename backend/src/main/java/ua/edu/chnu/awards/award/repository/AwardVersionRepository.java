package ua.edu.chnu.awards.award.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ua.edu.chnu.awards.award.entity.AwardVersion;
import ua.edu.chnu.awards.award.entity.VersionAction;

/**
 * Access to {@link AwardVersion} rows.
 */
public interface AwardVersionRepository extends JpaRepository<AwardVersion, Long> {

    /**
     * The newest version of an award.
     *
     * @param awardId the award
     * @return the version, empty when none was recorded
     */
    Optional<AwardVersion> findFirstByAwardIdOrderByNumberDesc(Long awardId);

    /**
     * Every version of the awards a person owns, award by award, oldest first.
     *
     * @param ownerId the owner
     * @return the versions
     */
    @Query("""
        select v from AwardVersion v
        where v.awardId in (select a.id from Award a where a.owner.id = :ownerId)
        order by v.awardId, v.number
        """)
    List<AwardVersion> findOfOwner(@Param("ownerId") Long ownerId);

    /**
     * The newest version older than a given one.
     *
     * @param awardId the award
     * @param number  the version number to go below
     * @return the version, empty for the first one
     */
    Optional<AwardVersion> findFirstByAwardIdAndNumberLessThanOrderByNumberDesc(Long awardId, long number);

    /**
     * A page of the versions of an award from a version number on, with their actors.
     *
     * @param awardId  the award
     * @param number   the lowest version number included
     * @param pageable page and order
     * @return the page
     */
    @EntityGraph(attributePaths = "actor")
    Page<AwardVersion> findByAwardIdAndNumberGreaterThanEqual(Long awardId, long number, Pageable pageable);

    /**
     * The number of the first version of an award written by an action.
     *
     * @param awardId the award
     * @param action  the action
     * @return the version number, empty when there is none
     */
    @Query("select min(v.number) from AwardVersion v where v.awardId = :awardId and v.action = :action")
    Optional<Long> firstNumber(Long awardId, VersionAction action);
}

package ua.edu.chnu.awards.award.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ua.edu.chnu.awards.award.entity.AwardRequest;

/**
 * Access to {@link AwardRequest} rows.
 */
public interface AwardRequestRepository extends JpaRepository<AwardRequest, Long> {

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
}

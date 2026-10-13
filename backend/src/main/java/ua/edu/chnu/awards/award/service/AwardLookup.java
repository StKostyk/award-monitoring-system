package ua.edu.chnu.awards.award.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.repository.AwardRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reads awards for other modules.
 */
@Service
@RequiredArgsConstructor
public class AwardLookup {

    private final AwardRepository awards;

    /**
     * The award with the id, locked for the rest of the transaction.
     *
     * @param id the award
     * @return the award, empty when unknown
     */
    public Optional<Award> findForUpdate(Long id) {
        return awards.findForUpdate(id);
    }

    /**
     * The awards of an owner, newest first.
     *
     * @param ownerId the owner
     * @return the awards
     */
    public List<Award> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId) {
        return awards.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId);
    }
}

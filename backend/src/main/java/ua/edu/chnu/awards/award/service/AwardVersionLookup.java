package ua.edu.chnu.awards.award.service;

import java.util.List;

import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.award.entity.AwardVersion;
import ua.edu.chnu.awards.award.repository.AwardVersionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reads award versions for other modules.
 */
@Service
@RequiredArgsConstructor
public class AwardVersionLookup {

    private final AwardVersionRepository versions;

    /**
     * The versions of every award of an owner.
     *
     * @param ownerId the owner
     * @return the versions
     */
    public List<AwardVersion> findOfOwner(Long ownerId) {
        return versions.findOfOwner(ownerId);
    }
}

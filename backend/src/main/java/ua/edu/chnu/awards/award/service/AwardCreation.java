package ua.edu.chnu.awards.award.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.repository.AwardRepository;

import lombok.RequiredArgsConstructor;

/**
 * Creation of a draft that is repeated when its answer was lost: a creation key the caller used before answers
 * the award created with it instead of a second draft, also when both requests arrive at once.
 */
@Component
@RequiredArgsConstructor
public class AwardCreation {

    private final AwardService awards;
    private final AwardRepository repository;
    private final AccessScope access;
    private final TransactionTemplate transactions;

    /**
     * Creates a draft, or answers the one created earlier with the same key.
     *
     * @param form        the form
     * @param creationKey the key the client generated for this creation, null for none
     * @return the draft and whether it existed before
     */
    public Created create(AwardForm form, UUID creationKey) {
        if (creationKey == null) {
            return new Created(awards.create(form), false);
        }
        Optional<AwardResponse> earlier = createdWith(creationKey);
        if (earlier.isPresent()) {
            return new Created(earlier.get(), true);
        }
        try {
            return new Created(Objects.requireNonNull(transactions.execute(status -> {
                AwardResponse created = awards.create(form);
                repository.keepCreationKey(created.id(), creationKey);
                return created;
            })), false);
        } catch (DataIntegrityViolationException e) {
            return new Created(createdWith(creationKey).orElseThrow(() -> e), true);
        }
    }

    private Optional<AwardResponse> createdWith(UUID creationKey) {
        return repository.findIdByCreationKey(access.callerId(), creationKey).map(awards::get);
    }

    /**
     * The outcome of a creation.
     *
     * @param award    the draft as it is now
     * @param repeated true when the key had created it before
     */
    public record Created(AwardResponse award, boolean repeated) {
    }
}

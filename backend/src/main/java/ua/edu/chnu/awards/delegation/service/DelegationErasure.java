package ua.edu.chnu.awards.delegation.service;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.erasure.ErasureContext;
import ua.edu.chnu.awards.common.erasure.ErasureContributor;
import ua.edu.chnu.awards.common.erasure.ErasureOrderConstants;
import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.delegation.repository.RoleDelegationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Erasure of the delegations the person gave or received: every one not yet over ends, audited as
 * {@code DELEGATION_REVOKED} with the reason {@code erasure} and without a message to the other party.
 */
@Component
@RequiredArgsConstructor
public class DelegationErasure implements ErasureContributor {

    private final RoleDelegationRepository delegations;
    private final DelegationRecorder recorder;

    @Override
    public int order() {
        return ErasureOrderConstants.DELEGATIONS;
    }

    @Override
    public void erase(ErasureContext context) {
        List<RoleDelegation> open = Stream.concat(delegations.findByDelegatorId(context.userId()).stream(),
                delegations.findByDelegateId(context.userId()).stream())
            .filter(delegation -> delegation.getRevokedAt() == null)
            .filter(delegation -> !delegation.getValidTo().isBefore(context.day()))
            .toList();
        open.forEach(delegation -> {
            delegation.setRevokedAt(context.at());
            recorder.erased(delegation);
        });
        delegations.saveAllAndFlush(open);
        context.count("delegationsEnded", open.size());
    }
}

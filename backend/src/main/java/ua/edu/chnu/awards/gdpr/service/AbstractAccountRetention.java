package ua.edu.chnu.awards.gdpr.service;

import java.time.Instant;
import java.time.temporal.TemporalAmount;

import ua.edu.chnu.awards.common.retention.RetentionAction;
import ua.edu.chnu.awards.common.retention.RetentionContext;
import ua.edu.chnu.awards.common.retention.RetentionPolicy;
import ua.edu.chnu.awards.common.retention.RetentionResult;
import ua.edu.chnu.awards.gdpr.repository.AccountRetentionCandidates;

/**
 * Files a retention erasure, due at once, for every account of a kind whose period is over; the erasure run carries
 * it out. Accounts under a legal hold are counted as held and left alone.
 */
abstract class AbstractAccountRetention implements RetentionPolicy {

    private final AccountRetentionCandidates candidates;
    private final AccountRetentionCandidates.Kind kind;
    private final TemporalAmount retained;

    AbstractAccountRetention(AccountRetentionCandidates candidates, AccountRetentionCandidates.Kind kind,
                             TemporalAmount retained) {
        this.candidates = candidates;
        this.kind = kind;
        this.retained = retained;
    }

    @Override
    public RetentionAction action() {
        return RetentionAction.ERASE_ACCOUNT;
    }

    @Override
    public String period() {
        return RetentionPolicy.iso(retained);
    }

    @Override
    public RetentionResult apply(RetentionContext context) {
        Instant before = context.before(retained);
        int held = candidates.held(kind, before, context.heldArray());
        int filed = context.inBatches(() -> candidates.file(kind, before, context.now(), context.heldArray(),
            context.batchSize()));
        return RetentionResult.completed(key(), filed, held);
    }
}

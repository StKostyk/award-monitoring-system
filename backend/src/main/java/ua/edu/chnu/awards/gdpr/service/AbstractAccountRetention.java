package ua.edu.chnu.awards.gdpr.service;

import java.time.Instant;

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

    AbstractAccountRetention(AccountRetentionCandidates candidates, AccountRetentionCandidates.Kind kind) {
        this.candidates = candidates;
        this.kind = kind;
    }

    /**
     * The end of the retention period of this kind of account.
     *
     * @param context the run
     * @return the moment before which an account is past its period
     */
    protected abstract Instant before(RetentionContext context);

    @Override
    public RetentionAction action() {
        return RetentionAction.ERASE_ACCOUNT;
    }

    @Override
    public RetentionResult apply(RetentionContext context) {
        Instant before = before(context);
        int held = candidates.held(kind, before, context.heldArray());
        int filed = context.inBatches(() -> candidates.file(kind, before, context.now(), context.heldArray(),
            context.batchSize()));
        return RetentionResult.completed(key(), filed, held);
    }
}

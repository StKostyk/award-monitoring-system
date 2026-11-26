package ua.edu.chnu.awards.gdpr.service;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.retention.RetentionOrderConstants;
import ua.edu.chnu.awards.common.retention.RetentionProperties;
import ua.edu.chnu.awards.gdpr.repository.AccountRetentionCandidates;

/**
 * Erases unverified registrations whose last verification link is older than {@code app.retention.pending-accounts}.
 */
@Component
class PendingAccountRetention extends AbstractAccountRetention {

    PendingAccountRetention(AccountRetentionCandidates candidates, RetentionProperties properties) {
        super(candidates, AccountRetentionCandidates.Kind.PENDING, properties.pendingAccounts());
    }

    @Override
    public String key() {
        return "pending-accounts";
    }

    @Override
    public int order() {
        return RetentionOrderConstants.PENDING_ACCOUNTS;
    }
}

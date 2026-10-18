package ua.edu.chnu.awards.gdpr.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.retention.RetentionContext;
import ua.edu.chnu.awards.common.retention.RetentionOrderConstants;
import ua.edu.chnu.awards.common.retention.RetentionPolicy;
import ua.edu.chnu.awards.common.retention.RetentionProperties;
import ua.edu.chnu.awards.gdpr.repository.AccountRetentionCandidates;

/**
 * Erases unverified registrations whose last verification link is older than {@code app.retention.pending-accounts}.
 */
@Component
class PendingAccountRetention extends AbstractAccountRetention {

    private final RetentionProperties properties;

    PendingAccountRetention(AccountRetentionCandidates candidates, RetentionProperties properties) {
        super(candidates, AccountRetentionCandidates.Kind.PENDING);
        this.properties = properties;
    }

    @Override
    public String key() {
        return "pending-accounts";
    }

    @Override
    public int order() {
        return RetentionOrderConstants.PENDING_ACCOUNTS;
    }

    @Override
    public String period() {
        return RetentionPolicy.days(properties.pendingAccounts());
    }

    @Override
    protected Instant before(RetentionContext context) {
        return context.now().minus(properties.pendingAccounts());
    }
}

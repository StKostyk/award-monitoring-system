package ua.edu.chnu.awards.gdpr.service;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.retention.RetentionOrderConstants;
import ua.edu.chnu.awards.common.retention.RetentionProperties;
import ua.edu.chnu.awards.gdpr.repository.AccountRetentionCandidates;

/**
 * Erases retired and memorial accounts whose status changed longer ago than {@code app.retention.former-staff}.
 */
@Component
class FormerStaffRetention extends AbstractAccountRetention {

    FormerStaffRetention(AccountRetentionCandidates candidates, RetentionProperties properties) {
        super(candidates, AccountRetentionCandidates.Kind.FORMER, properties.formerStaff());
    }

    @Override
    public String key() {
        return "former-staff";
    }

    @Override
    public int order() {
        return RetentionOrderConstants.FORMER_STAFF;
    }
}

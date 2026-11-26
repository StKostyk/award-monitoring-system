package ua.edu.chnu.awards.common.retention;

import java.time.Duration;
import java.time.Period;
import java.util.stream.Stream;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Periods of the retention policies and the size of their batches; the schedule is {@code app.retention.cron}.
 *
 * @param batchSize            the most rows one transaction of a policy changes
 * @param notifications        how long a notification is kept after it was sent
 * @param oneTimeTokens        how long a one-time token is kept after its use or expiry
 * @param authorizations       how long an authorization is kept after its last expiry
 * @param devices              how long a device is kept after its last use
 * @param auditPartitionsAhead how many months ahead of the current one the audit partitions exist
 * @param pendingAccounts      how long an unverified registration is kept after its creation or last link
 * @param formerStaff          how long a retired or memorial account is kept after its status changed
 * @param consentRecords       how long a withdrawn consent chain, or an erased account's records, is kept
 * @param auditLogs            how long a monthly audit partition is kept after its end
 */
@ConfigurationProperties(prefix = "app.retention")
public record RetentionProperties(@DefaultValue("1000") int batchSize,
                                  @DefaultValue("P365D") Duration notifications,
                                  @DefaultValue("P30D") Duration oneTimeTokens,
                                  @DefaultValue("P1D") Duration authorizations,
                                  @DefaultValue("P730D") Duration devices,
                                  @DefaultValue("3") int auditPartitionsAhead,
                                  @DefaultValue("P30D") Duration pendingAccounts,
                                  @DefaultValue("P7Y") Period formerStaff,
                                  @DefaultValue("P7Y") Period consentRecords,
                                  @DefaultValue("P7Y") Period auditLogs) {

    /**
     * Refuses sizes and periods that are not positive.
     *
     * @param batchSize            the most rows one transaction of a policy changes
     * @param notifications        how long a notification is kept after it was sent
     * @param oneTimeTokens        how long a one-time token is kept after its use or expiry
     * @param authorizations       how long an authorization is kept after its last expiry
     * @param devices              how long a device is kept after its last use
     * @param auditPartitionsAhead how many months ahead of the current one the audit partitions exist
     * @param pendingAccounts      how long an unverified registration is kept after its creation or last link
     * @param formerStaff          how long a retired or memorial account is kept after its status changed
     * @param consentRecords       how long a withdrawn consent chain, or an erased account's records, is kept
     * @param auditLogs            how long a monthly audit partition is kept after its end
     */
    public RetentionProperties {
        boolean periodsPositive = Stream.of(notifications, oneTimeTokens, authorizations, devices, pendingAccounts)
            .allMatch(Duration::isPositive)
            && Stream.of(formerStaff, consentRecords, auditLogs)
            .allMatch(period -> !period.isNegative() && !period.isZero());
        if (batchSize < 1 || auditPartitionsAhead < 1 || !periodsPositive) {
            throw new IllegalArgumentException("app.retention batch size, periods and partitions ahead must be "
                + "positive");
        }
    }
}

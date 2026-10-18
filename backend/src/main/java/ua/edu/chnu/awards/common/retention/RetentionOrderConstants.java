package ua.edu.chnu.awards.common.retention;

/**
 * The positions of the retention policies: technical rows first, accounts and consent records next, the audit
 * trail last.
 */
@SuppressWarnings("PMD.DataClass")
public final class RetentionOrderConstants {

    /** Expired notifications. */
    public static final int NOTIFICATIONS = 10;

    /** Used or expired one-time tokens. */
    public static final int ONE_TIME_TOKENS = 20;

    /** Expired authorizations. */
    public static final int AUTHORIZATIONS = 30;

    /** Devices not used for long. */
    public static final int DEVICES = 40;

    /** Unverified registrations. */
    public static final int PENDING_ACCOUNTS = 50;

    /** Retired and memorial accounts. */
    public static final int FORMER_STAFF = 60;

    /** Withdrawn consent chains and the records of erased accounts. */
    public static final int CONSENT_RECORDS = 70;

    /** Audit partitions of the coming months. */
    public static final int AUDIT_PARTITIONS = 80;

    /** Audit partitions past their period. */
    public static final int AUDIT_LOGS = 90;

    private RetentionOrderConstants() {
    }
}

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

    /** Audit partitions of the coming months. */
    public static final int AUDIT_PARTITIONS = 80;

    private RetentionOrderConstants() {
    }
}

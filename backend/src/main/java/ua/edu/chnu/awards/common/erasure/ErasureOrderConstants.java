package ua.edu.chnu.awards.common.erasure;

/**
 * The positions of the erasure contributors: sign-in material first, so a running session ends with the
 * transaction, the account row last but one, the audit redaction last of all (called by the runner).
 */
@SuppressWarnings("PMD.DataClass")
public final class ErasureOrderConstants {

    /** Tokens, devices, authorizations and sessions. */
    public static final int AUTHENTICATION = 10;

    /** Role assignments. */
    public static final int ROLES = 20;

    /** Delegations given or received. */
    public static final int DELEGATIONS = 30;

    /** Documents of the person's awards, before the awards cascade them away with their storage keys. */
    public static final int DOCUMENTS = 35;

    /** Notifications, preferences and pending publications, while the award records still show who reviewed. */
    public static final int NOTIFICATIONS = 38;

    /** Awards, requests, versions and review claims. */
    public static final int AWARDS = 40;

    /** Consent records. */
    public static final int CONSENTS = 70;

    /** The account row itself. */
    public static final int ACCOUNT = 90;

    private ErasureOrderConstants() {
    }
}

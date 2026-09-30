package ua.edu.chnu.awards.audit.entity;

/**
 * Values of {@code audit_logs.entity_type} written by the application.
 */
@SuppressWarnings("PMD.DataClass")
public final class AuditEntityConstants {

    /** Sign-in, sign-out, lockout and one-time links. */
    public static final String AUTHENTICATION = "AUTHENTICATION";
    /** Roles, delegations and refused access. */
    public static final String AUTHORIZATION = "AUTHORIZATION";
    /** Award records. */
    public static final String AWARDS = "awards";
    /** Data-subject rights such as the data export. */
    public static final String GDPR = "GDPR";
    /** A person's own account: names and sign-in address. */
    public static final String USER = "USER";

    private AuditEntityConstants() {
    }
}

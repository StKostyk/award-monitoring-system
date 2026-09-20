package ua.edu.chnu.awards.auth.entity;

/**
 * What a one-time token sent by email is allowed to do.
 */
public enum TokenPurpose {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    SECURITY_REVOKE
}

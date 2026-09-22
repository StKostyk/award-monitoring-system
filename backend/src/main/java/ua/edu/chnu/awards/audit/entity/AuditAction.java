package ua.edu.chnu.awards.audit.entity;

/**
 * Security events written to {@code audit_logs} by the application (table triggers write the CRUD ones).
 */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    ACCOUNT_LOCKED,
    LOGOUT,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET,
    EMAIL_VERIFIED,
    SECURITY_REVOKE,
    ACCESS_DENIED
}

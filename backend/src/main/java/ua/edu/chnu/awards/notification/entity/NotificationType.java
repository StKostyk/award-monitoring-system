package ua.edu.chnu.awards.notification.entity;

/**
 * What a notification is about; one value per event a person is told of.
 */
public enum NotificationType {
    AWARD_APPROVED,
    AWARD_REJECTED,
    AWARD_RETURNED,
    AWARD_ESCALATED,
    AWARD_CORRECTED,
    REVIEW_OVERDUE,
    REVIEW_TAKEN_OVER,
    ROLE_ASSIGNED,
    ROLE_REVOKED,
    DELEGATION_CREATED,
    DELEGATION_REVOKED
}

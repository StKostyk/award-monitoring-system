-- ============================================================================
-- V053: Reminder to a department without awards (Feature 9.4, story 9.4.5)
-- ============================================================================
-- AWARDS_REMINDER joins the notification and preference types. The weekly limit
-- per department reads the UNIT_REMINDED audit rows (entity organizations), so
-- no table and no index of notifications are added.
-- ============================================================================

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (
    type IN ('AWARD_APPROVED', 'AWARD_REJECTED', 'AWARD_RETURNED', 'AWARD_ESCALATED', 'AWARD_CORRECTED',
             'REVIEW_OVERDUE', 'REVIEW_TAKEN_OVER', 'ROLE_ASSIGNED', 'ROLE_REVOKED',
             'DELEGATION_CREATED', 'DELEGATION_REVOKED', 'AWARDS_REMINDER')
);

ALTER TABLE notification_preferences DROP CONSTRAINT ck_notification_preferences_event;
ALTER TABLE notification_preferences ADD CONSTRAINT ck_notification_preferences_event CHECK (
    event_type IN ('AWARD_APPROVED', 'AWARD_REJECTED', 'AWARD_RETURNED', 'AWARD_ESCALATED', 'AWARD_CORRECTED',
                   'REVIEW_OVERDUE', 'REVIEW_TAKEN_OVER', 'ROLE_ASSIGNED', 'ROLE_REVOKED',
                   'DELEGATION_CREATED', 'DELEGATION_REVOKED', 'AWARDS_REMINDER')
);

COMMENT ON COLUMN notifications.type IS 'Notification type: AWARD_APPROVED, AWARD_REJECTED, AWARD_RETURNED, AWARD_ESCALATED, AWARD_CORRECTED, REVIEW_OVERDUE, REVIEW_TAKEN_OVER, ROLE_ASSIGNED, ROLE_REVOKED, DELEGATION_CREATED, DELEGATION_REVOKED, AWARDS_REMINDER';

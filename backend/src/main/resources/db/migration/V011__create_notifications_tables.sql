-- V011__create_notifications_tables.sql
-- Description: Create notifications and notification_preferences tables
-- Author: Stefan Kostyk
-- Date: 2026-01-10

-- ============================================================================
-- NOTIFICATIONS TABLE
-- ============================================================================
-- System notifications sent to users about award activities, workflow updates,
-- and system alerts. Supports multiple delivery channels.
--
-- Business Rules:
-- - Notifications created by system events (not user-initiated)
-- - Unread notifications highlighted in UI
-- - Notifications respect user preferences
-- - Retention period: 1 year
-- ============================================================================

CREATE TABLE notifications (
    -- Primary Key
    notification_id BIGSERIAL,
    
    -- Recipient
    user_id BIGINT NOT NULL,
    
    -- Notification Content
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    
    -- Status
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Delivery
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    
    -- Reference to Related Entity
    reference_type VARCHAR(50),
    reference_id BIGINT,
    
    -- Timestamps
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    
    -- Constraints
    CONSTRAINT pk_notifications PRIMARY KEY (notification_id),
    CONSTRAINT fk_notifications_users FOREIGN KEY (user_id) 
        REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ck_notifications_type CHECK (
        type IN ('AWARD_SUBMITTED', 'AWARD_APPROVED', 'AWARD_REJECTED', 'AWARD_RETURNED', 
                 'REVIEW_ASSIGNED', 'DEADLINE_REMINDER', 'SYSTEM_ALERT')
    ),
    CONSTRAINT ck_notifications_channel CHECK (
        channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH')
    ),
    -- If read, read_at must be set
    CONSTRAINT ck_notifications_read CHECK (
        (is_read = FALSE) OR (is_read = TRUE AND read_at IS NOT NULL)
    )
);

-- ============================================================================
-- NOTIFICATION_PREFERENCES TABLE
-- ============================================================================
-- User preferences for notification delivery channels by event type.
-- Default preferences created on user registration.
-- ============================================================================

CREATE TABLE notification_preferences (
    -- Primary Key
    preference_id BIGSERIAL,
    
    -- User Reference
    user_id BIGINT NOT NULL,
    
    -- Event Type
    event_type VARCHAR(50) NOT NULL,
    
    -- Channel Preferences
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Audit Columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT pk_notification_preferences PRIMARY KEY (preference_id),
    CONSTRAINT uk_notification_preferences_user_event UNIQUE (user_id, event_type),
    CONSTRAINT fk_notification_preferences_users FOREIGN KEY (user_id) 
        REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ck_notification_preferences_event CHECK (
        event_type IN ('AWARD_SUBMITTED', 'AWARD_APPROVED', 'AWARD_REJECTED', 'AWARD_RETURNED', 
                       'REVIEW_ASSIGNED', 'DEADLINE_REMINDER', 'SYSTEM_ALERT')
    )
);

-- ============================================================================
-- INDEXES - NOTIFICATIONS
-- ============================================================================

-- User lookup
CREATE INDEX idx_notifications_user ON notifications(user_id);

-- Unread notifications for user (most common query)
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, sent_at DESC)
    WHERE is_read = FALSE;

-- Sent timestamp for ordering
CREATE INDEX idx_notifications_sent ON notifications(sent_at DESC);

-- Reference lookup (find notifications for specific entity)
CREATE INDEX idx_notifications_reference ON notifications(reference_type, reference_id)
    WHERE reference_type IS NOT NULL;

-- Expiration cleanup
CREATE INDEX idx_notifications_expires ON notifications(expires_at)
    WHERE expires_at IS NOT NULL;

-- ============================================================================
-- INDEXES - NOTIFICATION_PREFERENCES
-- ============================================================================

-- User preferences lookup
CREATE INDEX idx_notification_preferences_user ON notification_preferences(user_id);

-- ============================================================================
-- COMMENTS - NOTIFICATIONS
-- ============================================================================

COMMENT ON TABLE notifications IS 'System notifications for award activities, workflow updates, and system alerts. Retention: 1 year.';

COMMENT ON COLUMN notifications.notification_id IS 'Unique notification identifier';
COMMENT ON COLUMN notifications.user_id IS 'Recipient user';
COMMENT ON COLUMN notifications.type IS 'Notification type: AWARD_SUBMITTED, AWARD_APPROVED, AWARD_REJECTED, AWARD_RETURNED, REVIEW_ASSIGNED, DEADLINE_REMINDER, SYSTEM_ALERT';
COMMENT ON COLUMN notifications.title IS 'Notification title/subject';
COMMENT ON COLUMN notifications.message IS 'Full notification message content';
COMMENT ON COLUMN notifications.is_read IS 'Read status flag';
COMMENT ON COLUMN notifications.channel IS 'Delivery channel: IN_APP, EMAIL, SMS, PUSH';
COMMENT ON COLUMN notifications.reference_type IS 'Related entity type (e.g., AWARD, REQUEST)';
COMMENT ON COLUMN notifications.reference_id IS 'Related entity ID';
COMMENT ON COLUMN notifications.sent_at IS 'Send timestamp';
COMMENT ON COLUMN notifications.read_at IS 'When notification was marked as read';
COMMENT ON COLUMN notifications.expires_at IS 'Notification expiration timestamp';

-- ============================================================================
-- COMMENTS - NOTIFICATION_PREFERENCES
-- ============================================================================

COMMENT ON TABLE notification_preferences IS 'User preferences for notification delivery channels by event type.';

COMMENT ON COLUMN notification_preferences.preference_id IS 'Unique preference identifier';
COMMENT ON COLUMN notification_preferences.user_id IS 'User owning this preference';
COMMENT ON COLUMN notification_preferences.event_type IS 'Notification event type this preference applies to';
COMMENT ON COLUMN notification_preferences.email_enabled IS 'Enable email delivery for this event';
COMMENT ON COLUMN notification_preferences.sms_enabled IS 'Enable SMS delivery for this event';
COMMENT ON COLUMN notification_preferences.in_app_enabled IS 'Enable in-app notification for this event';
COMMENT ON COLUMN notification_preferences.push_enabled IS 'Enable push notification for this event';
COMMENT ON COLUMN notification_preferences.created_at IS 'Preference creation timestamp';
COMMENT ON COLUMN notification_preferences.updated_at IS 'Last modification timestamp';


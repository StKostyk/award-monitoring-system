-- V038__notification_types.sql
-- Description: Structured in-app notifications: payload and dedupe key instead of title and message,
-- types aligned with the published events

ALTER TABLE notifications
    DROP COLUMN title,
    DROP COLUMN message,
    ADD COLUMN payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN dedupe_key VARCHAR(64) NOT NULL;

CREATE UNIQUE INDEX uk_notifications_dedupe_key ON notifications (dedupe_key);

CREATE INDEX idx_notifications_user_channel_sent ON notifications (user_id, channel, sent_at DESC);

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (
    type IN ('AWARD_APPROVED', 'AWARD_REJECTED', 'AWARD_RETURNED', 'AWARD_ESCALATED', 'AWARD_CORRECTED',
             'REVIEW_OVERDUE', 'REVIEW_TAKEN_OVER', 'ROLE_ASSIGNED', 'ROLE_REVOKED',
             'DELEGATION_CREATED', 'DELEGATION_REVOKED')
);

ALTER TABLE notification_preferences DROP CONSTRAINT ck_notification_preferences_event;
ALTER TABLE notification_preferences ADD CONSTRAINT ck_notification_preferences_event CHECK (
    event_type IN ('AWARD_APPROVED', 'AWARD_REJECTED', 'AWARD_RETURNED', 'AWARD_ESCALATED', 'AWARD_CORRECTED',
                   'REVIEW_OVERDUE', 'REVIEW_TAKEN_OVER', 'ROLE_ASSIGNED', 'ROLE_REVOKED',
                   'DELEGATION_CREATED', 'DELEGATION_REVOKED')
);

COMMENT ON TABLE notifications IS 'In-app notifications written from committed events, one row per recipient. Retention: 1 year (expires_at).';
COMMENT ON COLUMN notifications.type IS 'Notification type: AWARD_APPROVED, AWARD_REJECTED, AWARD_RETURNED, AWARD_ESCALATED, AWARD_CORRECTED, REVIEW_OVERDUE, REVIEW_TAKEN_OVER, ROLE_ASSIGNED, ROLE_REVOKED, DELEGATION_CREATED, DELEGATION_REVOKED';
COMMENT ON COLUMN notifications.payload IS 'Values the sentence of the type needs (titles, names, role, dates); rendered per language by the client. Never the reviewer comment or corrected values';
COMMENT ON COLUMN notifications.dedupe_key IS 'SHA-256 hex of type, channel, recipient, reference and event time; a repeated delivery of one event writes nothing';
COMMENT ON COLUMN notification_preferences.event_type IS 'Notification type this preference applies to (same list as notifications.type)';

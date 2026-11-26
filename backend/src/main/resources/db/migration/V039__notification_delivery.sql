-- V039__notification_delivery.sql
-- Description: Delivery log of preference mails: an EMAIL row per recipient and event, claimed before the send
-- and marked sent or failed after it

ALTER TABLE notifications
    ADD COLUMN delivery_status VARCHAR(20) NULL,
    ADD COLUMN delivered_at TIMESTAMPTZ NULL;

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_delivery_status CHECK (
    (channel = 'IN_APP' AND delivery_status IS NULL)
    OR (channel = 'EMAIL' AND delivery_status IS NOT NULL AND delivery_status IN ('SENDING', 'SENT', 'FAILED'))
);

COMMENT ON TABLE notifications IS 'In-app notifications and the delivery log of preference mails, written from committed events, one row per recipient and channel. Retention: 1 year (expires_at).';
COMMENT ON COLUMN notifications.sent_at IS 'When the in-app row was written, or when the mail was last claimed for sending';
COMMENT ON COLUMN notifications.delivery_status IS 'EMAIL rows: SENDING while claimed, SENT once the mail server accepted the mail, FAILED when it did not; null for IN_APP';
COMMENT ON COLUMN notifications.delivered_at IS 'EMAIL rows: when the mail server accepted the mail; null otherwise';

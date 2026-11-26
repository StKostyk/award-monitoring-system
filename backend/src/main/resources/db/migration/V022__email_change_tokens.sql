-- V022__email_change_tokens.sql
-- Description: Email change links; audit snapshots of users without the password hash
-- Author: Stefan Kostyk
-- Date: 2026-09-29

-- ============================================================================
-- ONE_TIME_TOKENS: EMAIL_CHANGE
-- ============================================================================
-- A sign-in address change is confirmed from the new mailbox. The token keeps
-- the requested address until the link is opened.
-- ============================================================================

ALTER TABLE one_time_tokens ADD COLUMN new_email_address VARCHAR(255);

ALTER TABLE one_time_tokens DROP CONSTRAINT ck_one_time_tokens_purpose;

ALTER TABLE one_time_tokens ADD CONSTRAINT ck_one_time_tokens_purpose CHECK (
    purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'SECURITY_REVOKE', 'EMAIL_CHANGE')
);

ALTER TABLE one_time_tokens ADD CONSTRAINT ck_one_time_tokens_new_email CHECK (
    (purpose = 'EMAIL_CHANGE') = (new_email_address IS NOT NULL)
);

COMMENT ON COLUMN one_time_tokens.purpose IS
    'EMAIL_VERIFICATION (24 h), PASSWORD_RESET (1 h), SECURITY_REVOKE (24 h), EMAIL_CHANGE (1 h)';
COMMENT ON COLUMN one_time_tokens.new_email_address IS
    'Requested sign-in address (lower case); present exactly for EMAIL_CHANGE';

-- ============================================================================
-- AUDIT TRIGGER FUNCTION WITHOUT PASSWORD HASHES
-- ============================================================================
-- Same behaviour as V013, except that snapshots of users rows leave out
-- password_hash (values and changed_fields), so the seven-year trail keeps no
-- credential material. Rows written before this migration are left unchanged.
-- The search_path is fixed because the function runs as its owner.
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_audit_trigger()
RETURNS TRIGGER AS $$
DECLARE
    v_old_values JSONB := NULL;
    v_new_values JSONB := NULL;
    v_changed_fields TEXT[] := NULL;
    v_user_id BIGINT := NULL;
    v_correlation_id UUID := NULL;
BEGIN
    BEGIN
        v_user_id := NULLIF(current_setting('app.current_user_id', TRUE), '')::BIGINT;
    EXCEPTION WHEN OTHERS THEN
        v_user_id := NULL;
    END;

    BEGIN
        v_correlation_id := NULLIF(current_setting('app.correlation_id', TRUE), '')::UUID;
    EXCEPTION WHEN OTHERS THEN
        v_correlation_id := NULL;
    END;

    IF TG_OP = 'INSERT' THEN
        v_new_values := to_jsonb(NEW);
    ELSIF TG_OP = 'UPDATE' THEN
        v_old_values := to_jsonb(OLD);
        v_new_values := to_jsonb(NEW);
    ELSIF TG_OP = 'DELETE' THEN
        v_old_values := to_jsonb(OLD);
    END IF;

    IF TG_TABLE_NAME = 'users' THEN
        v_old_values := v_old_values - 'password_hash';
        v_new_values := v_new_values - 'password_hash';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        SELECT ARRAY_AGG(key)
        INTO v_changed_fields
        FROM (
            SELECT key
            FROM jsonb_each(v_old_values)
            WHERE v_new_values->key IS DISTINCT FROM v_old_values->key
        ) AS changed;
    END IF;

    INSERT INTO audit_logs (
        user_id,
        action_type,
        entity_type,
        entity_id,
        old_values,
        new_values,
        changed_fields,
        correlation_id,
        created_at
    ) VALUES (
        v_user_id,
        TG_OP,
        TG_TABLE_NAME,
        CASE
            WHEN TG_OP = 'DELETE' THEN (v_old_values->>'user_id')::BIGINT
            ELSE (v_new_values->>
                CASE TG_TABLE_NAME
                    WHEN 'users' THEN 'user_id'
                    WHEN 'organizations' THEN 'org_id'
                    WHEN 'user_roles' THEN 'user_role_id'
                    WHEN 'award_categories' THEN 'category_id'
                    WHEN 'awards' THEN 'award_id'
                    WHEN 'documents' THEN 'document_id'
                    WHEN 'award_requests' THEN 'request_id'
                    WHEN 'review_decisions' THEN 'decision_id'
                    WHEN 'consent_records' THEN 'consent_id'
                    WHEN 'notifications' THEN 'notification_id'
                    WHEN 'notification_preferences' THEN 'preference_id'
                    ELSE 'id'
                END
            )::BIGINT
        END,
        v_old_values,
        v_new_values,
        v_changed_fields,
        v_correlation_id,
        CURRENT_TIMESTAMP
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public;

COMMENT ON FUNCTION fn_audit_trigger() IS
    'Generic audit logging function capturing INSERT/UPDATE/DELETE with old/new values and changed fields; '
    'users snapshots exclude password_hash';

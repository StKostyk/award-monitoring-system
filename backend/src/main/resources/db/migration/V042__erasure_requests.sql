-- V042__erasure_requests.sql
-- Description: Erasure requests of accounts with a grace period; at most one pending request per account. The
--              audit trigger keys erasure_requests rows by request_id.

CREATE TABLE erasure_requests (
    request_id      BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    scheduled_for   TIMESTAMPTZ NOT NULL,
    warning_sent_at TIMESTAMPTZ NULL,
    postponed_at    TIMESTAMPTZ NULL,
    cancelled_at    TIMESTAMPTZ NULL,
    completed_at    TIMESTAMPTZ NULL,
    CONSTRAINT ck_erasure_requests_status CHECK (status IN ('PENDING', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_erasure_requests_dates CHECK (
        (cancelled_at IS NULL) = (status <> 'CANCELLED') AND (completed_at IS NULL) = (status <> 'COMPLETED')
    )
);

CREATE UNIQUE INDEX uk_erasure_requests_pending ON erasure_requests (user_id) WHERE status = 'PENDING';
CREATE INDEX idx_erasure_requests_due ON erasure_requests (scheduled_for) WHERE status = 'PENDING';

COMMENT ON TABLE erasure_requests IS
    'Requests of people to erase their account after a grace period; at most one PENDING request per account';
COMMENT ON COLUMN erasure_requests.user_id IS 'The account to erase';
COMMENT ON COLUMN erasure_requests.status IS 'PENDING until cancelled by the person or completed by the erasure job';
COMMENT ON COLUMN erasure_requests.requested_at IS 'When the person asked for the erasure';
COMMENT ON COLUMN erasure_requests.scheduled_for IS 'When the grace period ends and the account is erased';
COMMENT ON COLUMN erasure_requests.warning_sent_at IS 'When the reminder before the erasure was mailed, null before';
COMMENT ON COLUMN erasure_requests.postponed_at IS 'When the erasure was first postponed by a legal hold, null before';
COMMENT ON COLUMN erasure_requests.cancelled_at IS 'When the person cancelled the request, set only when CANCELLED';
COMMENT ON COLUMN erasure_requests.completed_at IS 'When the account was erased, set only when COMPLETED';

-- ============================================================================
-- AUDIT TRIGGER FUNCTION: ERASURE REQUESTS
-- ============================================================================
-- Same behaviour as V041, plus: erasure_requests rows are keyed by request_id.
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_audit_trigger()
RETURNS TRIGGER AS $$
DECLARE
    v_old_values JSONB := NULL;
    v_new_values JSONB := NULL;
    v_changed_fields TEXT[] := NULL;
    v_user_id BIGINT := NULL;
    v_correlation_id UUID := NULL;
    v_key TEXT;
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
    ELSIF TG_TABLE_NAME = 'legal_holds' THEN
        v_old_values := v_old_values - 'reason' - 'lift_reason';
        v_new_values := v_new_values - 'reason' - 'lift_reason';
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

    v_key := CASE TG_TABLE_NAME
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
        WHEN 'legal_holds' THEN 'hold_id'
        WHEN 'erasure_requests' THEN 'request_id'
        ELSE 'id'
    END;

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
        (COALESCE(v_new_values, v_old_values)->>v_key)::BIGINT,
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
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp;

COMMENT ON FUNCTION fn_audit_trigger() IS
    'Generic audit logging function capturing INSERT/UPDATE/DELETE with old/new values and changed fields; '
    'entity_id is the key of the changed record; users snapshots exclude password_hash, legal_holds snapshots '
    'exclude reason and lift_reason';

CREATE TRIGGER trg_erasure_requests_audit
    AFTER INSERT OR UPDATE OR DELETE ON erasure_requests
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

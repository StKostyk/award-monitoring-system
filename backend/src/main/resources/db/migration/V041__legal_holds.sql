-- V041__legal_holds.sql
-- Description: Legal holds placed on accounts by the GDPR officer; an active hold blocks the erasure of the
--              account. The audit trigger keys legal_holds rows by hold_id and leaves the reason texts out.

CREATE TABLE legal_holds (
    hold_id     BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    reason      VARCHAR(500) NOT NULL,
    placed_by   BIGINT NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    placed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    lifted_by   BIGINT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    lifted_at   TIMESTAMPTZ NULL,
    lift_reason VARCHAR(500) NULL,
    CONSTRAINT ck_legal_holds_lifted CHECK (
        (lifted_at IS NULL) = (lifted_by IS NULL) AND (lifted_at IS NULL) = (lift_reason IS NULL)
    )
);

CREATE UNIQUE INDEX uk_legal_holds_active ON legal_holds (user_id) WHERE lifted_at IS NULL;
CREATE INDEX idx_legal_holds_user ON legal_holds (user_id, placed_at DESC);

COMMENT ON TABLE legal_holds IS
    'Legal holds on accounts; at most one active (not lifted) hold per account, which blocks erasure';
COMMENT ON COLUMN legal_holds.user_id IS 'The account under hold';
COMMENT ON COLUMN legal_holds.reason IS 'Why the hold was placed, 10 to 500 characters; never copied to audit_logs';
COMMENT ON COLUMN legal_holds.placed_by IS 'The GDPR officer who placed the hold';
COMMENT ON COLUMN legal_holds.placed_at IS 'When the hold was placed';
COMMENT ON COLUMN legal_holds.lifted_by IS 'The GDPR officer who lifted the hold, null while active';
COMMENT ON COLUMN legal_holds.lifted_at IS 'When the hold was lifted, null while active';
COMMENT ON COLUMN legal_holds.lift_reason IS
    'Why the hold was lifted, 10 to 500 characters, null while active; never copied to audit_logs';

-- ============================================================================
-- AUDIT TRIGGER FUNCTION: LEGAL HOLDS
-- ============================================================================
-- Same behaviour as V023, plus: legal_holds rows are keyed by hold_id, and
-- their snapshots exclude reason and lift_reason.
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

CREATE TRIGGER trg_legal_holds_audit
    AFTER INSERT OR UPDATE OR DELETE ON legal_holds
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

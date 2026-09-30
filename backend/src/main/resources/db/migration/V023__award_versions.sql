-- V023__award_versions.sql
-- Description: Saved versions of awards; trigger DELETE rows keyed by the deleted record
-- Author: Stefan Kostyk
-- Date: 2026-09-30

-- ============================================================================
-- AWARD_VERSIONS
-- ============================================================================
-- One row per saved state of an award, written by the application in the
-- transaction of the change. The snapshot holds the business fields; the
-- changes between versions are computed when read. Versions follow the award:
-- they are deleted with a draft. The rows are never changed, except that
-- erasing the actor's account clears actor_id.
-- ============================================================================

CREATE TABLE award_versions (
    version_id BIGSERIAL,
    award_id BIGINT NOT NULL,
    version_number BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    actor_id BIGINT,
    snapshot JSONB NOT NULL,
    changed_fields TEXT[],
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_award_versions PRIMARY KEY (version_id),
    CONSTRAINT fk_award_versions_awards FOREIGN KEY (award_id)
        REFERENCES awards(award_id) ON DELETE CASCADE,
    CONSTRAINT fk_award_versions_actor FOREIGN KEY (actor_id)
        REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT uk_award_versions_number UNIQUE (award_id, version_number),
    CONSTRAINT ck_award_versions_action CHECK (action IN ('BASELINE', 'CREATED', 'UPDATED', 'SUBMITTED'))
);

CREATE INDEX idx_award_versions_actor ON award_versions(actor_id);

COMMENT ON TABLE award_versions IS 'Saved versions of awards (created, edited, submitted), newest by version_number';
COMMENT ON COLUMN award_versions.version_number IS 'awards.version after the change';
COMMENT ON COLUMN award_versions.action IS 'BASELINE (state at V023), CREATED, UPDATED, SUBMITTED';
COMMENT ON COLUMN award_versions.actor_id IS 'Who saved the version; empty for baselines and erased accounts';
COMMENT ON COLUMN award_versions.snapshot IS 'Business fields of the award at this version (camelCase keys)';
COMMENT ON COLUMN award_versions.changed_fields IS 'Snapshot keys that differ from the previous version';
COMMENT ON INDEX idx_award_versions_actor IS 'Clearing the actor when an account is erased';

CREATE OR REPLACE FUNCTION fn_award_versions_immutable()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.actor_id IS NULL AND (to_jsonb(NEW) - 'actor_id') = (to_jsonb(OLD) - 'actor_id') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'award_versions rows are immutable (version_id %)', OLD.version_id;
END;
$$ LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp;

COMMENT ON FUNCTION fn_award_versions_immutable() IS
    'Refuses changes to award_versions except clearing actor_id';

CREATE TRIGGER trg_award_versions_immutable
    BEFORE UPDATE ON award_versions
    FOR EACH ROW EXECUTE FUNCTION fn_award_versions_immutable();

INSERT INTO award_versions (award_id, version_number, action, snapshot)
SELECT award_id, version, 'BASELINE', jsonb_build_object(
    'title', title,
    'titleUk', title_uk,
    'description', description,
    'descriptionUk', description_uk,
    'awardingOrganization', awarding_organization,
    'awardDate', award_date::text,
    'categoryId', category_id,
    'status', status,
    'impactScore', impact_score,
    'verificationBadge', verification_badge,
    'externalUrl', external_url,
    'organizationId', organization_id)
FROM awards;

-- ============================================================================
-- AUDIT TRIGGER FUNCTION: DELETE ROWS KEYED BY THE DELETED RECORD
-- ============================================================================
-- Same behaviour as V022, except that DELETE rows take entity_id from the
-- table's own key, as INSERT and UPDATE rows do (V013 used user_id of the old
-- row). Rows written before this migration are left unchanged.
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
    'entity_id is the key of the changed record; users snapshots exclude password_hash';

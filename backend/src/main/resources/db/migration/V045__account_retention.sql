-- V045__account_retention.sql
-- Description: Account, consent and audit retention (PRD 6.2, story 6.2.3): the moment an account's status last
--              changed, the origin of an erasure request, and the two guarded functions that delete consent records
--              and drop expired audit partitions.

ALTER TABLE users ADD COLUMN status_changed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users DISABLE TRIGGER USER;
UPDATE users SET status_changed_at = updated_at;
ALTER TABLE users ENABLE TRIGGER USER;
ALTER TABLE users ALTER COLUMN status_changed_at SET NOT NULL;
ALTER TABLE users ALTER COLUMN status_changed_at SET DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN users.status_changed_at IS
    'When account_status last changed; set by trg_users_status_changed, used by the former-staff retention policy';

CREATE OR REPLACE FUNCTION fn_users_status_changed()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.account_status IS DISTINCT FROM OLD.account_status THEN
        NEW.status_changed_at := CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp;

COMMENT ON FUNCTION fn_users_status_changed() IS 'Sets users.status_changed_at whenever account_status changes';

CREATE TRIGGER trg_users_status_changed
    BEFORE UPDATE OF account_status ON users
    FOR EACH ROW EXECUTE FUNCTION fn_users_status_changed();

CREATE INDEX idx_users_status_changed ON users (account_status, status_changed_at);

ALTER TABLE erasure_requests ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'REQUEST';
ALTER TABLE erasure_requests ADD CONSTRAINT ck_erasure_requests_origin CHECK (origin IN ('REQUEST', 'RETENTION'));

COMMENT ON COLUMN erasure_requests.origin IS
    'REQUEST: asked for by the person; RETENTION: filed by the retention job for an unverified or former account, '
    'due at once (requested_at = scheduled_for) and carried out without mails';

CREATE OR REPLACE FUNCTION fn_consent_records_guard()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.consent_retention', TRUE) = 'on' THEN
        RETURN OLD;
    END IF;
    IF TG_OP = 'UPDATE'
        AND NEW.ip_address IS NULL
        AND NEW.user_agent IS NULL
        AND (to_jsonb(NEW) - 'ip_address' - 'user_agent') = (to_jsonb(OLD) - 'ip_address' - 'user_agent') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'consent_records rows are append-only (consent_id %)', OLD.consent_id;
END;
$$ LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp;

COMMENT ON FUNCTION fn_consent_records_guard() IS
    'Refuses deletes and changes of consent_records except clearing ip_address and user_agent, and deletes while '
    'app.consent_retention is on (fn_purge_consent_records)';

CREATE OR REPLACE FUNCTION fn_consent_purge_candidates(p_before TIMESTAMPTZ)
RETURNS TABLE (consent_id BIGINT, user_id BIGINT) AS $$
    SELECT c.consent_id, c.user_id
      FROM consent_records c
     WHERE c.consent_type = 'ANALYTICS'
       AND EXISTS (SELECT 1
                     FROM (SELECT l.is_granted, l.created_at
                             FROM consent_records l
                            WHERE l.user_id = c.user_id AND l.consent_type = 'ANALYTICS'
                            ORDER BY l.created_at DESC, l.consent_id DESC
                            LIMIT 1) latest
                    WHERE NOT latest.is_granted AND latest.created_at < p_before)
    UNION
    SELECT c.consent_id, c.user_id
      FROM consent_records c
      JOIN erasure_requests e ON e.user_id = c.user_id AND e.status = 'COMPLETED'
     WHERE e.completed_at < p_before;
$$ LANGUAGE sql STABLE SET search_path = pg_catalog, public, pg_temp;

COMMENT ON FUNCTION fn_consent_purge_candidates(TIMESTAMPTZ) IS
    'Consent records past their retention: every ANALYTICS row of a person whose latest ANALYTICS row is a '
    'withdrawal written before p_before, and every row of an account erased before p_before';

CREATE OR REPLACE FUNCTION fn_purge_consent_records(p_before TIMESTAMPTZ, p_held BIGINT[])
RETURNS INTEGER AS $$
DECLARE
    v_deleted INTEGER;
BEGIN
    PERFORM set_config('app.consent_retention', 'on', TRUE);
    DELETE FROM consent_records
     WHERE consent_id IN (SELECT p.consent_id FROM fn_consent_purge_candidates(p_before) p
                           WHERE NOT (p.user_id = ANY (COALESCE(p_held, '{}'))));
    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    PERFORM set_config('app.consent_retention', 'off', TRUE);
    RETURN v_deleted;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp;

COMMENT ON FUNCTION fn_purge_consent_records(TIMESTAMPTZ, BIGINT[]) IS
    'Retention: deletes the consent records of fn_consent_purge_candidates except those of the held persons, under '
    'app.consent_retention, and returns how many were deleted';

REVOKE ALL ON FUNCTION fn_purge_consent_records(TIMESTAMPTZ, BIGINT[]) FROM PUBLIC;

CREATE OR REPLACE FUNCTION fn_drop_expired_audit_partitions(p_before TIMESTAMPTZ, p_held BIGINT[])
RETURNS JSONB AS $$
DECLARE
    v_held      BIGINT[] := COALESCE(p_held, '{}');
    v_held_text TEXT[] := COALESCE(p_held, '{}')::TEXT[];
    v_partition RECORD;
    v_kept      BOOLEAN;
    v_dropped   TEXT[] := '{}';
    v_retained  TEXT[] := '{}';
    v_result    JSONB;
BEGIN
    FOR v_partition IN
        SELECT child.relname AS name,
               substring(pg_get_expr(child.relpartbound, child.oid) FROM 'TO \(''([^'']+)''\)')::TIMESTAMPTZ AS ends
          FROM pg_inherits i
          JOIN pg_class parent ON parent.oid = i.inhparent
          JOIN pg_class child ON child.oid = i.inhrelid
         WHERE parent.relname = 'audit_logs'
           AND pg_get_expr(child.relpartbound, child.oid) <> 'DEFAULT'
         ORDER BY 2
    LOOP
        CONTINUE WHEN v_partition.ends IS NULL OR v_partition.ends > p_before;
        EXECUTE format(
            'SELECT EXISTS (SELECT 1 FROM %I '
            || 'WHERE user_id = ANY ($1) '
            || 'OR (entity_type IN (''users'', ''USER'', ''AUTHENTICATION'', ''AUTHORIZATION'', ''GDPR'') '
            || 'AND entity_id = ANY ($1)) '
            || 'OR (entity_type IN (''consent_records'', ''notification_preferences'', ''notifications'', '
            || '''user_roles'', ''role_delegations'', ''one_time_tokens'', ''legal_holds'', ''erasure_requests'') '
            || 'AND (COALESCE(new_values, old_values) ->> ''user_id'' = ANY ($2) '
            || 'OR COALESCE(new_values, old_values) ->> ''delegator_id'' = ANY ($2) '
            || 'OR COALESCE(new_values, old_values) ->> ''delegate_id'' = ANY ($2))))',
            v_partition.name) INTO v_kept USING v_held, v_held_text;
        IF v_kept THEN
            v_retained := v_retained || v_partition.name::TEXT;
        ELSE
            EXECUTE format('ALTER TABLE audit_logs DETACH PARTITION %I', v_partition.name);
            EXECUTE format('DROP TABLE %I', v_partition.name);
            v_dropped := v_dropped || v_partition.name::TEXT;
        END IF;
    END LOOP;

    v_result := jsonb_build_object('dropped', to_jsonb(v_dropped), 'held', to_jsonb(v_retained));
    IF cardinality(v_dropped) > 0 THEN
        INSERT INTO audit_logs (action_type, entity_type, new_values, correlation_id)
        VALUES ('AUDIT_PARTITIONS_DROPPED', 'RETENTION', v_result,
                NULLIF(current_setting('app.correlation_id', TRUE), '')::UUID);
    END IF;
    RETURN v_result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp;

COMMENT ON FUNCTION fn_drop_expired_audit_partitions(TIMESTAMPTZ, BIGINT[]) IS
    'Retention: detaches and drops every monthly audit_logs partition ending at or before p_before unless it holds '
    'a row by or about a held person (the person keys of fn_redact_audit_subject); never the default partition; '
    'records AUDIT_PARTITIONS_DROPPED and returns the dropped and the held partition names';

REVOKE ALL ON FUNCTION fn_drop_expired_audit_partitions(TIMESTAMPTZ, BIGINT[]) FROM PUBLIC;

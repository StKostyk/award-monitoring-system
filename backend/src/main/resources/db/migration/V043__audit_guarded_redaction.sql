-- V043__audit_guarded_redaction.sql
-- Description: Replaces the rules that kept audit_logs append-only with a row trigger that refuses every DELETE
--              and every UPDATE except the guarded redaction of an erased person's values (ADR-024), adds the
--              redaction function and drops the unused fn_anonymize_user_data.

DROP RULE audit_logs_no_update ON audit_logs;
DROP RULE audit_logs_no_delete ON audit_logs;

DROP FUNCTION IF EXISTS fn_anonymize_user_data(BIGINT);

CREATE OR REPLACE FUNCTION fn_audit_logs_guard()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'audit_logs rows cannot be deleted (log_id %)', OLD.log_id;
    END IF;
    IF current_setting('app.audit_redaction', TRUE) IS DISTINCT FROM 'on'
       OR (to_jsonb(NEW) - ARRAY['old_values', 'new_values', 'ip_address', 'user_agent'])
          IS DISTINCT FROM (to_jsonb(OLD) - ARRAY['old_values', 'new_values', 'ip_address', 'user_agent']) THEN
        RAISE EXCEPTION 'audit_logs rows are immutable (log_id %)', OLD.log_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp;

COMMENT ON FUNCTION fn_audit_logs_guard() IS
    'Keeps audit_logs append-only: DELETE raises; UPDATE raises unless app.audit_redaction is on for the '
    'transaction and only old_values, new_values, ip_address and user_agent change';

CREATE TRIGGER trg_audit_logs_guard
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION fn_audit_logs_guard();

CREATE OR REPLACE FUNCTION fn_redact_audit_values(p_values JSONB)
RETURNS JSONB AS $$
    SELECT CASE
        WHEN p_values IS NULL OR jsonb_typeof(p_values) <> 'object' THEN p_values
        ELSE COALESCE((
            SELECT jsonb_object_agg(key, CASE
                WHEN key = ANY (ARRAY['email_address', 'email', 'new_email', 'old_email', 'new_email_address',
                                      'newEmail', 'oldEmail', 'restoredEmail', 'replacedEmail', 'first_name',
                                      'last_name', 'firstName', 'lastName', 'password_hash', 'ip_address', 'ip',
                                      'user_agent', 'userAgent', 'payload'])
                     AND value <> 'null'::jsonb THEN '"[erased]"'::jsonb
                ELSE value
            END)
            FROM jsonb_each(p_values)), '{}'::jsonb)
    END;
$$ LANGUAGE sql IMMUTABLE SET search_path = pg_catalog, public, pg_temp;

COMMENT ON FUNCTION fn_redact_audit_values(JSONB) IS
    'Replaces the values of the personal keys of an audit snapshot (addresses, names, password hash, IP address, '
    'user agent, notification payload) with "[erased]"';

CREATE OR REPLACE FUNCTION fn_jsonb_replace_strings(p_values JSONB, p_names TEXT[], p_pseudonym TEXT)
RETURNS JSONB AS $$
    SELECT CASE jsonb_typeof(p_values)
        WHEN 'string' THEN CASE WHEN (p_values #>> '{}') = ANY (p_names) THEN to_jsonb(p_pseudonym)
                                ELSE p_values END
        WHEN 'object' THEN COALESCE((
            SELECT jsonb_object_agg(key, fn_jsonb_replace_strings(value, p_names, p_pseudonym))
            FROM jsonb_each(p_values)), '{}'::jsonb)
        WHEN 'array' THEN COALESCE((
            SELECT jsonb_agg(fn_jsonb_replace_strings(value, p_names, p_pseudonym) ORDER BY position)
            FROM jsonb_array_elements(p_values) WITH ORDINALITY AS element(value, position)), '[]'::jsonb)
        ELSE p_values
    END;
$$ LANGUAGE sql IMMUTABLE SET search_path = pg_catalog, public, pg_temp;

COMMENT ON FUNCTION fn_jsonb_replace_strings(JSONB, TEXT[], TEXT) IS
    'Replaces every string equal to one of the names, at any depth, with the pseudonym';

CREATE OR REPLACE FUNCTION fn_redact_audit_subject(p_user_id BIGINT, p_names TEXT[], p_entities JSONB)
RETURNS JSONB AS $$
DECLARE
    v_pseudonym     TEXT := 'Erased #' || p_user_id;
    v_awards        BIGINT[] := ARRAY(SELECT jsonb_array_elements_text(COALESCE(p_entities -> 'awards', '[]'))::BIGINT);
    v_requests      BIGINT[] := ARRAY(SELECT jsonb_array_elements_text(COALESCE(p_entities -> 'award_requests', '[]'))::BIGINT);
    v_documents     BIGINT[] := ARRAY(SELECT jsonb_array_elements_text(COALESCE(p_entities -> 'documents', '[]'))::BIGINT);
    v_notifications BIGINT[] := ARRAY(SELECT jsonb_array_elements_text(COALESCE(p_entities -> 'notifications', '[]'))::BIGINT);
    v_person        TEXT := p_user_id::TEXT;
    v_context       INTEGER;
    v_values        INTEGER;
    v_erased        INTEGER;
    v_names         INTEGER;
    v_counts        JSONB;
BEGIN
    PERFORM set_config('app.audit_redaction', 'on', TRUE);

    UPDATE audit_logs
       SET ip_address = NULL, user_agent = NULL
     WHERE user_id = p_user_id AND (ip_address IS NOT NULL OR user_agent IS NOT NULL);
    GET DIAGNOSTICS v_context = ROW_COUNT;

    UPDATE audit_logs
       SET old_values = fn_redact_audit_values(old_values),
           new_values = fn_redact_audit_values(new_values)
     WHERE ((entity_type IN ('users', 'USER', 'AUTHENTICATION', 'AUTHORIZATION', 'GDPR') AND entity_id = p_user_id)
            OR (entity_type IN ('consent_records', 'notification_preferences', 'notifications', 'user_roles',
                                'role_delegations', 'one_time_tokens', 'legal_holds', 'erasure_requests')
                AND v_person IN (COALESCE(new_values, old_values) ->> 'user_id',
                                 COALESCE(new_values, old_values) ->> 'delegator_id',
                                 COALESCE(new_values, old_values) ->> 'delegate_id')))
       AND (fn_redact_audit_values(old_values) IS DISTINCT FROM old_values
            OR fn_redact_audit_values(new_values) IS DISTINCT FROM new_values);
    GET DIAGNOSTICS v_values = ROW_COUNT;

    UPDATE audit_logs
       SET old_values = CASE WHEN old_values IS NULL THEN NULL ELSE '{"erased": true}'::jsonb END,
           new_values = CASE WHEN new_values IS NULL THEN NULL ELSE '{"erased": true}'::jsonb END
     WHERE ((entity_type = 'awards' AND entity_id = ANY (v_awards))
            OR (entity_type = 'award_requests' AND entity_id = ANY (v_requests))
            OR (entity_type = 'documents' AND entity_id = ANY (v_documents))
            OR (entity_type = 'review_decisions'
                AND (COALESCE(new_values, old_values) ->> 'request_id')::BIGINT = ANY (v_requests)))
       AND (old_values IS DISTINCT FROM '{"erased": true}'::jsonb OR new_values IS DISTINCT FROM '{"erased": true}'::jsonb)
       AND (old_values IS NOT NULL OR new_values IS NOT NULL);
    GET DIAGNOSTICS v_erased = ROW_COUNT;

    UPDATE audit_logs
       SET old_values = fn_jsonb_replace_strings(old_values, p_names, v_pseudonym),
           new_values = fn_jsonb_replace_strings(new_values, p_names, v_pseudonym)
     WHERE entity_type = 'notifications' AND entity_id = ANY (v_notifications)
       AND (fn_jsonb_replace_strings(old_values, p_names, v_pseudonym) IS DISTINCT FROM old_values
            OR fn_jsonb_replace_strings(new_values, p_names, v_pseudonym) IS DISTINCT FROM new_values);
    GET DIAGNOSTICS v_names = ROW_COUNT;

    PERFORM set_config('app.audit_redaction', 'off', TRUE);

    v_counts := jsonb_build_object('context', v_context, 'values', v_values, 'erasedRecords', v_erased,
                                   'names', v_names);
    INSERT INTO audit_logs (user_id, action_type, entity_type, entity_id, new_values, correlation_id)
    VALUES (p_user_id, 'AUDIT_REDACTED', 'GDPR', p_user_id, v_counts,
            NULLIF(current_setting('app.correlation_id', TRUE), '')::UUID);
    RETURN v_counts;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp;

COMMENT ON FUNCTION fn_redact_audit_subject(BIGINT, TEXT[], JSONB) IS
    'Erasure: redacts the audit values of a person (IP address and user agent of their rows, personal keys of rows '
    'about them, snapshots of their deleted awards, requests, decisions and documents, their names in the listed '
    'notifications of others) under app.audit_redaction, records AUDIT_REDACTED and returns the counts';

REVOKE ALL ON FUNCTION fn_redact_audit_subject(BIGINT, TEXT[], JSONB) FROM PUBLIC;

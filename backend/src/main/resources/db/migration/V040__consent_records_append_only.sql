-- V040__consent_records_append_only.sql
-- Description: Consent records become an append-only history of the notice acknowledgement and the optional
--              ANALYTICS consent; only the network facts of a row may be cleared later.

DELETE FROM consent_records WHERE consent_type NOT IN ('DATA_PROCESSING', 'ANALYTICS');

ALTER TABLE consent_records DROP CONSTRAINT ck_consent_records_type;
ALTER TABLE consent_records ADD CONSTRAINT ck_consent_records_type
    CHECK (consent_type IN ('DATA_PROCESSING', 'ANALYTICS'));

CREATE OR REPLACE FUNCTION fn_consent_records_guard()
RETURNS TRIGGER AS $$
BEGIN
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
    'Refuses deletes and changes of consent_records except clearing ip_address and user_agent';

CREATE TRIGGER trg_consent_records_guard
    BEFORE UPDATE OR DELETE ON consent_records
    FOR EACH ROW EXECUTE FUNCTION fn_consent_records_guard();

COMMENT ON TABLE consent_records IS
    'Append-only history of privacy notice acknowledgements (DATA_PROCESSING) and the optional ANALYTICS consent; '
    'the latest row per type is the current state';
COMMENT ON COLUMN consent_records.consent_type IS
    'DATA_PROCESSING: acknowledgement of a notice version; ANALYTICS: naming the person in rankings and comparisons';
COMMENT ON COLUMN consent_records.consent_version IS 'Privacy notice version in force when the row was written';

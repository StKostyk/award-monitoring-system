-- V013__create_audit_triggers.sql
-- Description: Create audit trigger function and apply to all auditable tables
-- Author: Stefan Kostyk
-- Date: 2026-01-10

-- ============================================================================
-- AUDIT TRIGGER FUNCTION
-- ============================================================================
-- Generic audit logging function that captures all INSERT, UPDATE, DELETE
-- operations with old/new values and changed fields tracking.
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
    -- Try to get current user from session variable (set by application)
    BEGIN
        v_user_id := NULLIF(current_setting('app.current_user_id', TRUE), '')::BIGINT;
    EXCEPTION WHEN OTHERS THEN
        v_user_id := NULL;
    END;
    
    -- Try to get correlation ID from session variable
    BEGIN
        v_correlation_id := NULLIF(current_setting('app.correlation_id', TRUE), '')::UUID;
    EXCEPTION WHEN OTHERS THEN
        v_correlation_id := NULL;
    END;
    
    -- Handle different operations
    IF TG_OP = 'INSERT' THEN
        v_new_values := to_jsonb(NEW);
        
    ELSIF TG_OP = 'UPDATE' THEN
        v_old_values := to_jsonb(OLD);
        v_new_values := to_jsonb(NEW);
        
        -- Calculate changed fields
        SELECT ARRAY_AGG(key)
        INTO v_changed_fields
        FROM (
            SELECT key
            FROM jsonb_each(v_old_values)
            WHERE NOT v_new_values ? key
               OR v_new_values->key IS DISTINCT FROM v_old_values->key
        ) AS changed;
        
    ELSIF TG_OP = 'DELETE' THEN
        v_old_values := to_jsonb(OLD);
    END IF;
    
    -- Insert audit log entry
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
    
    -- Return appropriate row
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- UPDATED_AT TRIGGER FUNCTION
-- ============================================================================
-- Automatically updates the updated_at timestamp on record modification.
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- APPLY AUDIT TRIGGERS
-- ============================================================================

-- Users table
CREATE TRIGGER trg_users_audit
    AFTER INSERT OR UPDATE OR DELETE ON users
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

-- Organizations table
CREATE TRIGGER trg_organizations_audit
    AFTER INSERT OR UPDATE OR DELETE ON organizations
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

CREATE TRIGGER trg_organizations_updated_at
    BEFORE UPDATE ON organizations
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

-- User roles table
CREATE TRIGGER trg_user_roles_audit
    AFTER INSERT OR UPDATE OR DELETE ON user_roles
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

-- Award categories table
CREATE TRIGGER trg_award_categories_audit
    AFTER INSERT OR UPDATE OR DELETE ON award_categories
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

CREATE TRIGGER trg_award_categories_updated_at
    BEFORE UPDATE ON award_categories
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

-- Awards table
CREATE TRIGGER trg_awards_audit
    AFTER INSERT OR UPDATE OR DELETE ON awards
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

CREATE TRIGGER trg_awards_updated_at
    BEFORE UPDATE ON awards
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

-- Documents table
CREATE TRIGGER trg_documents_audit
    AFTER INSERT OR UPDATE OR DELETE ON documents
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

-- Award requests table
CREATE TRIGGER trg_award_requests_audit
    AFTER INSERT OR UPDATE OR DELETE ON award_requests
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

CREATE TRIGGER trg_award_requests_updated_at
    BEFORE UPDATE ON award_requests
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

-- Review decisions table
CREATE TRIGGER trg_review_decisions_audit
    AFTER INSERT OR UPDATE OR DELETE ON review_decisions
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

-- Consent records table
CREATE TRIGGER trg_consent_records_audit
    AFTER INSERT OR UPDATE OR DELETE ON consent_records
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

-- Notifications table (audit only significant actions, not reads)
CREATE TRIGGER trg_notifications_audit
    AFTER INSERT OR DELETE ON notifications
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

-- Notification preferences table
CREATE TRIGGER trg_notification_preferences_audit
    AFTER INSERT OR UPDATE OR DELETE ON notification_preferences
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

CREATE TRIGGER trg_notification_preferences_updated_at
    BEFORE UPDATE ON notification_preferences
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON FUNCTION fn_audit_trigger() IS 'Generic audit logging function capturing INSERT/UPDATE/DELETE with old/new values and changed fields';
COMMENT ON FUNCTION fn_update_timestamp() IS 'Automatically updates updated_at column on record modification';


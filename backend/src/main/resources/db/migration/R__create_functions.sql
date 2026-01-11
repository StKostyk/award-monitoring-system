-- R__create_functions.sql
-- Description: Repeatable migration for utility functions
-- Author: Stefan Kostyk
-- Note: This migration re-runs whenever the file changes (checksum-based)

-- ============================================================================
-- FUNCTION: fn_calculate_impact_score
-- ============================================================================
-- Calculates impact score for an award based on category level
-- Score range: 0-100
-- Base scores: SPECIALITY=10, DEPARTMENT=20, COLLEGE=40, FACULTY=50, UNIVERSITY=60, LOCAL=70, REGIONAL=80, NATIONAL=90, INTERNATIONAL=100
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_calculate_impact_score(p_category_id BIGINT)
RETURNS INTEGER AS $$
DECLARE
    v_level VARCHAR(20);
    v_base_score INTEGER;
BEGIN
    -- Get category level
    SELECT level INTO v_level
    FROM award_categories
    WHERE category_id = p_category_id;

    IF v_level IS NULL THEN
        RETURN NULL;
    END IF;

    -- Calculate base score from level
    v_base_score := CASE v_level
        WHEN 'SPECIALITY' THEN 10
        WHEN 'DEPARTMENT' THEN 20
        WHEN 'COLLEGE' THEN 40
        WHEN 'FACULTY' THEN 50
        WHEN 'UNIVERSITY' THEN 60
        WHEN 'LOCAL' THEN 70
        WHEN 'REGIONAL' THEN 80
        WHEN 'NATIONAL' THEN 90
        WHEN 'INTERNATIONAL' THEN 100
        ELSE 0
    END;

    RETURN v_base_score;
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON FUNCTION fn_calculate_impact_score(BIGINT) IS 'Calculates impact score (0-100) based on category level';

-- ============================================================================
-- FUNCTION: fn_create_audit_partition
-- ============================================================================
-- Creates next month's partition for audit_logs table
-- Should be called monthly (on 25th) to prepare for next month
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_create_audit_partition()
RETURNS void AS $$
DECLARE
    partition_date DATE := DATE_TRUNC('month', CURRENT_DATE + INTERVAL '1 month');
    partition_name TEXT;
    start_date TEXT;
    end_date TEXT;
BEGIN
    partition_name := 'audit_logs_' || TO_CHAR(partition_date, 'YYYY_MM');
    start_date := TO_CHAR(partition_date, 'YYYY-MM-DD');
    end_date := TO_CHAR(partition_date + INTERVAL '1 month', 'YYYY-MM-DD');

    -- Check if partition already exists
    IF NOT EXISTS (
        SELECT 1 FROM pg_class WHERE relname = partition_name
    ) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF audit_logs FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
        RAISE NOTICE 'Created partition: %', partition_name;
    ELSE
        RAISE NOTICE 'Partition already exists: %', partition_name;
    END IF;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_create_audit_partition() IS 'Creates next month partition for audit_logs table';

-- ============================================================================
-- FUNCTION: fn_get_user_permissions
-- ============================================================================
-- Returns array of permission codes for a user based on their roles
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_get_user_permissions(p_user_id BIGINT)
RETURNS TEXT[] AS $$
DECLARE
    v_permissions TEXT[] := ARRAY[]::TEXT[];
    v_role RECORD;
BEGIN
    FOR v_role IN
        SELECT DISTINCT role_type
        FROM user_roles
        WHERE user_id = p_user_id
          AND (valid_to IS NULL OR valid_to >= CURRENT_DATE)
          AND valid_from <= CURRENT_DATE
    LOOP
        -- Add permissions based on role
        CASE v_role.role_type
            WHEN 'EMPLOYEE' THEN
                v_permissions := v_permissions || ARRAY['SUBMIT_AWARD', 'VIEW_OWN_AWARDS'];
            WHEN 'FACULTY_SECRETARY' THEN
                v_permissions := v_permissions || ARRAY['SUBMIT_AWARD', 'VIEW_OWN_AWARDS',
                    'REVIEW_FACULTY_AWARDS', 'VIEW_FACULTY_REPORTS'];
            WHEN 'DEAN' THEN
                v_permissions := v_permissions || ARRAY['SUBMIT_AWARD', 'VIEW_OWN_AWARDS',
                    'REVIEW_FACULTY_AWARDS', 'APPROVE_FACULTY_AWARDS', 'VIEW_FACULTY_REPORTS'];
            WHEN 'RECTOR_SECRETARY' THEN
                v_permissions := v_permissions || ARRAY['SUBMIT_AWARD', 'VIEW_OWN_AWARDS',
                    'REVIEW_ALL_AWARDS', 'VIEW_UNIVERSITY_REPORTS'];
            WHEN 'RECTOR' THEN
                v_permissions := v_permissions || ARRAY['SUBMIT_AWARD', 'VIEW_OWN_AWARDS',
                    'REVIEW_ALL_AWARDS', 'APPROVE_ALL_AWARDS', 'VIEW_UNIVERSITY_REPORTS'];
            WHEN 'SYSTEM_ADMIN' THEN
                v_permissions := v_permissions || ARRAY['MANAGE_USERS', 'MANAGE_SYSTEM',
                    'VIEW_ALL_DATA', 'CONFIGURE_WORKFLOWS'];
            WHEN 'GDPR_OFFICER' THEN
                v_permissions := v_permissions || ARRAY['VIEW_CONSENT_DATA', 'EXPORT_USER_DATA',
                    'DELETE_USER_DATA', 'VIEW_AUDIT_LOGS'];
            ELSE
                NULL;
        END CASE;
    END LOOP;

    -- Remove duplicates and return
    RETURN ARRAY(SELECT DISTINCT unnest(v_permissions) ORDER BY 1);
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON FUNCTION fn_get_user_permissions(BIGINT) IS 'Returns array of permission codes based on user roles';

-- ============================================================================
-- FUNCTION: fn_can_user_approve_award
-- ============================================================================
-- Checks if a user can approve an award at a specific level
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_can_user_approve_award(
    p_user_id BIGINT,
    p_award_id BIGINT
)
RETURNS BOOLEAN AS $$
DECLARE
    v_award_org_id BIGINT;
    v_request_level VARCHAR(30);
    v_has_role BOOLEAN;
BEGIN
    -- Get award's organization and request level
    SELECT u.organization_id, ar.current_level
    INTO v_award_org_id, v_request_level
    FROM awards a
    JOIN users u ON a.user_id = u.user_id
    LEFT JOIN award_requests ar ON a.award_id = ar.award_id
    WHERE a.award_id = p_award_id;

    IF v_request_level IS NULL THEN
        RETURN FALSE;
    END IF;

    -- Check if user has appropriate role for the level
    SELECT EXISTS (
        SELECT 1 FROM user_roles ur
        JOIN organizations o ON ur.organization_id = o.org_id
        WHERE ur.user_id = p_user_id
          AND (ur.valid_to IS NULL OR ur.valid_to >= CURRENT_DATE)
          AND ur.valid_from <= CURRENT_DATE
          AND ur.role_type = v_request_level
          -- Role org must be same or parent of award org
          AND (ur.organization_id = v_award_org_id OR
               EXISTS (
                   SELECT 1 FROM organizations ao
                   WHERE ao.org_id = v_award_org_id
                     AND ao.hierarchy_path <@ (SELECT hierarchy_path FROM organizations WHERE org_id = ur.organization_id)
               ))
    ) INTO v_has_role;

    RETURN v_has_role;
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON FUNCTION fn_can_user_approve_award(BIGINT, BIGINT) IS 'Checks if user can approve award at current workflow level';

-- ============================================================================
-- FUNCTION: fn_get_next_approval_level
-- ============================================================================
-- Returns the next approval level in the workflow
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_get_next_approval_level(p_current_level VARCHAR(30))
RETURNS VARCHAR(30) AS $$
BEGIN
    RETURN CASE p_current_level
        WHEN 'FACULTY_SECRETARY' THEN 'DEAN'
        WHEN 'DEAN' THEN 'RECTOR_SECRETARY'
        WHEN 'RECTOR_SECRETARY' THEN 'RECTOR'
        WHEN 'RECTOR' THEN NULL  -- Final level
        ELSE NULL
    END;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

COMMENT ON FUNCTION fn_get_next_approval_level(VARCHAR) IS 'Returns the next approval level in the workflow hierarchy';

-- ============================================================================
-- FUNCTION: fn_anonymize_user_data
-- ============================================================================
-- Anonymizes user personal data for GDPR Right to Erasure
-- Awards are preserved but personal data is removed
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_anonymize_user_data(p_user_id BIGINT)
RETURNS BOOLEAN AS $$
DECLARE
    v_anonymized_email TEXT;
BEGIN
    -- Generate anonymized email
    v_anonymized_email := 'deleted_' || p_user_id || '@anonymized.local';

    -- Anonymize user record
    UPDATE users SET
        email_address = v_anonymized_email,
        first_name = 'Deleted',
        last_name = 'User',
        password_hash = 'DELETED',
        account_status = 'DELETED',
        updated_at = CURRENT_TIMESTAMP
    WHERE user_id = p_user_id;

    -- Delete notification preferences
    DELETE FROM notification_preferences WHERE user_id = p_user_id;

    -- Delete notifications
    DELETE FROM notifications WHERE user_id = p_user_id;

    -- Note: Awards are preserved (public record value)
    -- Note: Consent records are preserved (audit requirement)
    -- Note: Audit logs are preserved (compliance requirement)

    RETURN FOUND;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_anonymize_user_data(BIGINT) IS 'GDPR: Anonymizes user personal data while preserving awards and audit records';

-- ============================================================================
-- FUNCTION: fn_check_consent
-- ============================================================================
-- Checks if user has active consent for a specific type
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_check_consent(
    p_user_id BIGINT,
    p_consent_type VARCHAR(50)
)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM consent_records
        WHERE user_id = p_user_id
          AND consent_type = p_consent_type
          AND is_granted = TRUE
          AND withdrawn_at IS NULL
    );
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON FUNCTION fn_check_consent(BIGINT, VARCHAR) IS 'Checks if user has active consent for specified type';

-- ============================================================================
-- FUNCTION: fn_get_organization_path
-- ============================================================================
-- Returns the full organizational path from root to specified org
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_get_organization_path(p_org_id BIGINT)
RETURNS TEXT AS $$
DECLARE
    v_path TEXT;
BEGIN
    WITH RECURSIVE org_path AS (
        SELECT org_id, name, parent_org_id, ARRAY[name] AS path_names
        FROM organizations
        WHERE org_id = p_org_id

        UNION ALL

        SELECT o.org_id, o.name, o.parent_org_id, o.name || op.path_names
        FROM organizations o
        JOIN org_path op ON o.org_id = op.parent_org_id
    )
    SELECT array_to_string(path_names, ' > ')
    INTO v_path
    FROM org_path
    WHERE parent_org_id IS NULL;

    RETURN v_path;
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON FUNCTION fn_get_organization_path(BIGINT) IS 'Returns full organizational path from root to specified org';


-- R__create_views.sql
-- Description: Repeatable migration for database views
-- Author: Stefan Kostyk
-- Note: This migration re-runs whenever the file changes (checksum-based)

-- ============================================================================
-- DROP EXISTING VIEWS (for repeatability)
-- ============================================================================

DROP VIEW IF EXISTS vw_active_awards CASCADE;
DROP VIEW IF EXISTS vw_pending_requests CASCADE;
DROP VIEW IF EXISTS vw_user_current_roles CASCADE;
DROP VIEW IF EXISTS vw_award_statistics CASCADE;
DROP VIEW IF EXISTS vw_reviewer_workload CASCADE;
DROP VIEW IF EXISTS vw_organization_hierarchy CASCADE;

-- ============================================================================
-- VIEW: vw_active_awards
-- ============================================================================
-- Shows all approved (public) awards with user and category information
-- ============================================================================

CREATE VIEW vw_active_awards AS
SELECT
    a.award_id,
    a.title,
    a.title_uk,
    a.description,
    a.awarding_organization,
    a.award_date,
    a.verification_badge,
    a.impact_score,
    a.external_url,
    a.created_at,
    -- User info
    u.user_id,
    u.first_name,
    u.last_name,
    u.email_address,
    -- Category info
    c.category_id,
    c.name AS category_name,
    c.name_uk AS category_name_uk,
    c.level AS category_level,
    -- Organization info
    o.org_id AS organization_id,
    o.name AS organization_name,
    o.name_uk AS organization_name_uk
FROM awards a
JOIN users u ON a.user_id = u.user_id
JOIN award_categories c ON a.category_id = c.category_id
JOIN organizations o ON u.organization_id = o.org_id
WHERE a.status = 'APPROVED'
  AND u.account_status = 'ACTIVE';

COMMENT ON VIEW vw_active_awards IS 'Approved (public) awards with user, category, and organization information';

-- ============================================================================
-- VIEW: vw_pending_requests
-- ============================================================================
-- Shows all pending workflow requests with award and submitter details
-- ============================================================================

CREATE VIEW vw_pending_requests AS
SELECT
    r.request_id,
    r.status AS request_status,
    r.current_level,
    r.submitted_at,
    r.deadline,
    -- Deadline status
    CASE
        WHEN r.deadline IS NULL THEN 'NO_DEADLINE'
        WHEN r.deadline < CURRENT_TIMESTAMP THEN 'OVERDUE'
        WHEN r.deadline < CURRENT_TIMESTAMP + INTERVAL '24 hours' THEN 'DUE_SOON'
        ELSE 'ON_TRACK'
    END AS deadline_status,
    -- Award info
    a.award_id,
    a.title AS award_title,
    a.award_date,
    -- Submitter info
    sub.user_id AS submitter_id,
    sub.first_name AS submitter_first_name,
    sub.last_name AS submitter_last_name,
    sub.email_address AS submitter_email,
    -- Current reviewer info
    rev.user_id AS reviewer_id,
    rev.first_name AS reviewer_first_name,
    rev.last_name AS reviewer_last_name,
    -- Category info
    c.name AS category_name,
    c.level AS category_level,
    -- Organization info
    o.name AS organization_name
FROM award_requests r
JOIN awards a ON r.award_id = a.award_id
JOIN users sub ON r.submitter_id = sub.user_id
LEFT JOIN users rev ON r.current_reviewer_id = rev.user_id
JOIN award_categories c ON a.category_id = c.category_id
JOIN organizations o ON sub.organization_id = o.org_id
WHERE r.status IN ('SUBMITTED', 'IN_REVIEW', 'ESCALATED');

COMMENT ON VIEW vw_pending_requests IS 'Pending workflow requests with deadline status, award, submitter, and reviewer details';

-- ============================================================================
-- VIEW: vw_user_current_roles
-- ============================================================================
-- Shows currently active roles for users
-- ============================================================================

CREATE VIEW vw_user_current_roles AS
SELECT
    u.user_id,
    u.email_address,
    u.first_name,
    u.last_name,
    u.account_status,
    ur.user_role_id,
    ur.role_type,
    ur.valid_from,
    ur.valid_to,
    o.org_id AS role_organization_id,
    o.name AS role_organization_name,
    o.org_type AS role_organization_type
FROM users u
JOIN user_roles ur ON u.user_id = ur.user_id
JOIN organizations o ON ur.organization_id = o.org_id
WHERE (ur.valid_to IS NULL OR ur.valid_to >= CURRENT_DATE)
  AND ur.valid_from <= CURRENT_DATE
  AND u.account_status = 'ACTIVE';

COMMENT ON VIEW vw_user_current_roles IS 'Currently active role assignments for active users';

-- ============================================================================
-- VIEW: vw_award_statistics
-- ============================================================================
-- Aggregated award statistics by organization
-- ============================================================================

CREATE VIEW vw_award_statistics AS
SELECT
    o.org_id,
    o.name AS organization_name,
    o.org_type,
    COUNT(DISTINCT a.award_id) AS total_awards,
    COUNT(DISTINCT a.award_id) FILTER (WHERE a.status = 'APPROVED') AS approved_awards,
    COUNT(DISTINCT a.award_id) FILTER (WHERE a.status = 'PENDING') AS pending_awards,
    COUNT(DISTINCT a.award_id) FILTER (WHERE a.status = 'DRAFT') AS draft_awards,
    COUNT(DISTINCT a.user_id) AS users_with_awards,
    AVG(a.impact_score) FILTER (WHERE a.status = 'APPROVED') AS avg_impact_score,
    MAX(a.award_date) FILTER (WHERE a.status = 'APPROVED') AS latest_award_date
FROM organizations o
LEFT JOIN users u ON u.organization_id = o.org_id AND u.account_status = 'ACTIVE'
LEFT JOIN awards a ON a.user_id = u.user_id
WHERE o.is_active = TRUE
GROUP BY o.org_id, o.name, o.org_type;

COMMENT ON VIEW vw_award_statistics IS 'Aggregated award statistics by organization';

-- ============================================================================
-- VIEW: vw_reviewer_workload
-- ============================================================================
-- Current workload for reviewers
-- ============================================================================

CREATE VIEW vw_reviewer_workload AS
SELECT
    u.user_id AS reviewer_id,
    u.first_name,
    u.last_name,
    u.email_address,
    ur.role_type,
    o.name AS organization_name,
    COUNT(r.request_id) AS total_pending,
    COUNT(r.request_id) FILTER (WHERE r.deadline < CURRENT_TIMESTAMP) AS overdue_count,
    COUNT(r.request_id) FILTER (WHERE r.deadline BETWEEN CURRENT_TIMESTAMP AND CURRENT_TIMESTAMP + INTERVAL '24 hours') AS due_today_count,
    MIN(r.submitted_at) AS oldest_request_date
FROM users u
JOIN user_roles ur ON u.user_id = ur.user_id
JOIN organizations o ON ur.organization_id = o.org_id
LEFT JOIN award_requests r ON r.current_reviewer_id = u.user_id
    AND r.status IN ('SUBMITTED', 'IN_REVIEW')
WHERE ur.role_type IN ('FACULTY_SECRETARY', 'DEAN', 'RECTOR_SECRETARY', 'RECTOR')
  AND (ur.valid_to IS NULL OR ur.valid_to >= CURRENT_DATE)
  AND u.account_status = 'ACTIVE'
GROUP BY u.user_id, u.first_name, u.last_name, u.email_address, ur.role_type, o.name;

COMMENT ON VIEW vw_reviewer_workload IS 'Current workload metrics for reviewers';

-- ============================================================================
-- VIEW: vw_organization_hierarchy
-- ============================================================================
-- Flattened organization hierarchy for reporting
-- ============================================================================

CREATE VIEW vw_organization_hierarchy AS
WITH RECURSIVE org_tree AS (
    -- Root level (University)
    SELECT
        org_id,
        name,
        name_uk,
        code,
        org_type,
        parent_org_id,
        depth,
        is_active,
        -- Cast to text to match the recursive term's concatenation result
        name::text AS full_path,
        ARRAY[org_id] AS path_ids
    FROM organizations
    WHERE parent_org_id IS NULL

    UNION ALL

    -- Recursive children
    SELECT
        o.org_id,
        o.name,
        o.name_uk,
        o.code,
        o.org_type,
        o.parent_org_id,
        o.depth,
        o.is_active,
        -- This concatenation now matches the text type from above
        ot.full_path || ' > ' || o.name,
        ot.path_ids || o.org_id
    FROM organizations o
    JOIN org_tree ot ON o.parent_org_id = ot.org_id
)
SELECT
    org_id,
    name,
    name_uk,
    code,
    org_type,
    parent_org_id,
    depth,
    is_active,
    full_path,
    path_ids,
    (SELECT COUNT(*) FROM users u WHERE u.organization_id = org_tree.org_id AND u.account_status = 'ACTIVE') AS active_user_count
FROM org_tree
ORDER BY path_ids;

COMMENT ON VIEW vw_organization_hierarchy IS 'Flattened organization hierarchy with full path and user counts';

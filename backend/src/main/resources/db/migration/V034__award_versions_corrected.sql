-- V034__award_versions_corrected.sql
-- Description: Reviewer correction of a pending award as an award version with the reason as comment
-- Author: Stefan Kostyk
-- Date: 2026-10-08

-- ============================================================================
-- AWARD_VERSIONS.ACTION / COMMENT
-- ============================================================================

ALTER TABLE award_versions DROP CONSTRAINT ck_award_versions_action;
ALTER TABLE award_versions ADD CONSTRAINT ck_award_versions_action
    CHECK (action IN ('BASELINE', 'CREATED', 'UPDATED', 'SUBMITTED', 'DECIDED', 'CORRECTED'));

ALTER TABLE award_versions ADD COLUMN comment TEXT NULL;

COMMENT ON COLUMN award_versions.action IS
    'BASELINE (state at V023), CREATED, UPDATED, SUBMITTED, DECIDED (a reviewer decision changed the status), '
    'CORRECTED (a reviewer corrected the fields of a pending award)';
COMMENT ON COLUMN award_versions.comment IS 'Reason a reviewer gave for a CORRECTED version; NULL otherwise';

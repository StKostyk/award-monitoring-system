-- V032__organizations_review_period.sql
-- Description: Review period of a faculty in working days, set by its dean
-- Author: Stefan Kostyk
-- Date: 2026-10-08

-- ============================================================================
-- ORGANIZATIONS.REVIEW_WORKING_DAYS
-- ============================================================================
-- Working days the faculty levels (faculty secretary, dean) have for a request
-- of the faculty or its departments. NULL means the global default
-- (app.workflow.review-working-days); only faculties carry a value.
-- ============================================================================

ALTER TABLE organizations
    ADD COLUMN review_working_days INTEGER;

ALTER TABLE organizations
    ADD CONSTRAINT ck_organizations_review_days
        CHECK (review_working_days BETWEEN 1 AND 20);

COMMENT ON COLUMN organizations.review_working_days IS
    'Working days per faculty level for requests of this faculty; NULL = global default';

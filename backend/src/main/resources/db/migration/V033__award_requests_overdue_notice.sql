-- V033__award_requests_overdue_notice.sql
-- Description: Overdue notice mark of an open request, set once per level by the overdue job
-- Author: Stefan Kostyk
-- Date: 2026-10-08

-- ============================================================================
-- AWARD_REQUESTS.OVERDUE_NOTICED_AT / OVERDUE_NOTICED_LEVEL
-- ============================================================================
-- Set together when the hourly job finds an open request past its deadline and
-- notifies the next level; cleared together whenever the deadline restarts
-- (another level, resubmission, return, withdrawal).
-- ============================================================================

ALTER TABLE award_requests
    ADD COLUMN overdue_noticed_at TIMESTAMPTZ,
    ADD COLUMN overdue_noticed_level VARCHAR(30);

ALTER TABLE award_requests
    ADD CONSTRAINT ck_award_requests_noticed_level
        CHECK (overdue_noticed_level IN ('FACULTY_SECRETARY', 'DEAN', 'RECTOR_SECRETARY', 'RECTOR'));

ALTER TABLE award_requests
    ADD CONSTRAINT ck_award_requests_noticed_pair
        CHECK ((overdue_noticed_at IS NULL) = (overdue_noticed_level IS NULL));

CREATE INDEX idx_requests_overdue_unnoticed ON award_requests (deadline)
    WHERE status IN ('SUBMITTED', 'IN_REVIEW', 'ESCALATED') AND overdue_noticed_at IS NULL;

COMMENT ON COLUMN award_requests.overdue_noticed_at IS
    'When the overdue job noticed the passed deadline and notified the next level; NULL = not noticed';
COMMENT ON COLUMN award_requests.overdue_noticed_level IS
    'Level the request was at when noticed; set together with overdue_noticed_at';

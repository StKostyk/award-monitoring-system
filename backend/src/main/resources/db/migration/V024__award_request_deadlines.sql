-- V024__award_request_deadlines.sql
-- Description: Back-fill the review deadline of requests submitted before deadlines were set
-- Author: Stefan Kostyk
-- Date: 2026-10-01

-- ============================================================================
-- AWARD_REQUESTS.DEADLINE
-- ============================================================================
-- From this version on the application sets the deadline at submission to the
-- end of the current level's review period (app.workflow.review-period,
-- 3 calendar days by default). Requests submitted earlier get the default
-- period counted from their submission.
-- ============================================================================

UPDATE award_requests
SET deadline = submitted_at + INTERVAL '3 days'
WHERE deadline IS NULL;

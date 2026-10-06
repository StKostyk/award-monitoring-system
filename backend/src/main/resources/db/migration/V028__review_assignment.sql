-- V028__review_assignment.sql
-- Description: Optimistic version of approval requests for claims; delegator stamp on review decisions
-- Author: Stefan Kostyk
-- Date: 2026-10-06

-- ============================================================================
-- A reviewer claims a request by setting current_reviewer_id. Claims,
-- releases, hand-overs and take-overs lock the request row and compare the
-- version the reviewer last read, so two colleagues never hold the same
-- request and nobody acts on an outdated view of it.
-- ============================================================================

ALTER TABLE award_requests
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_requests_open_level ON award_requests(current_level, deadline, request_id)
    WHERE status IN ('SUBMITTED', 'IN_REVIEW', 'ESCALATED');

COMMENT ON COLUMN award_requests.version IS 'Optimistic lock version; raised by every claim, release, hand-over and decision';
COMMENT ON COLUMN award_requests.current_reviewer_id IS 'Reviewer who claimed the request; NULL while nobody holds it';
COMMENT ON INDEX idx_requests_open_level IS 'Reviewer queue: open requests of a level, earliest deadline first';

-- ============================================================================
-- A delegate decides on behalf of the person who lent the role; the decision
-- keeps both. NULL when the reviewer decided under a role of their own.
-- ============================================================================

ALTER TABLE review_decisions
    ADD COLUMN delegator_id BIGINT,
    ADD CONSTRAINT fk_review_decisions_delegator FOREIGN KEY (delegator_id)
        REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE;

CREATE INDEX idx_review_decisions_delegator ON review_decisions(delegator_id) WHERE delegator_id IS NOT NULL;

COMMENT ON COLUMN review_decisions.delegator_id IS 'Holder of the role the reviewer borrowed; NULL for a decision under an own role';

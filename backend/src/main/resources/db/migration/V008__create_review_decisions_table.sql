-- V008__create_review_decisions_table.sql
-- Description: Create review_decisions table - Individual approval/rejection decisions
-- Author: Stefan Kostyk
-- Date: 2026-01-10

-- ============================================================================
-- REVIEW_DECISIONS TABLE
-- ============================================================================
-- Individual approval/rejection decisions made at each level of the workflow.
-- Provides complete audit trail of the approval process.
--
-- Business Rules:
-- - Each decision tied to one request and one reviewer
-- - Decisions are immutable once recorded
-- - Comments required for rejections and returns
-- ============================================================================

CREATE TABLE review_decisions (
    -- Primary Key
    decision_id BIGSERIAL,
    
    -- References
    request_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    
    -- Decision Details
    decision VARCHAR(20) NOT NULL,
    level VARCHAR(30) NOT NULL,
    comments TEXT,
    
    -- Timestamp (immutable)
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT pk_review_decisions PRIMARY KEY (decision_id),
    CONSTRAINT fk_review_decisions_requests FOREIGN KEY (request_id) 
        REFERENCES award_requests(request_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_review_decisions_reviewers FOREIGN KEY (reviewer_id) 
        REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_review_decisions_decision CHECK (
        decision IN ('APPROVED', 'REJECTED', 'ESCALATED', 'RETURNED')
    ),
    CONSTRAINT ck_review_decisions_level CHECK (
        level IN ('FACULTY_SECRETARY', 'DEAN', 'RECTOR_SECRETARY', 'RECTOR')
    ),
    -- Comments required for REJECTED and RETURNED decisions
    CONSTRAINT ck_review_decisions_comments CHECK (
        (decision NOT IN ('REJECTED', 'RETURNED')) OR 
        (comments IS NOT NULL AND LENGTH(TRIM(comments)) > 0)
    )
);

-- ============================================================================
-- INDEXES
-- ============================================================================

-- Foreign key indexes
CREATE INDEX idx_review_decisions_request ON review_decisions(request_id);
CREATE INDEX idx_review_decisions_reviewer ON review_decisions(reviewer_id);

-- Decision timeline
CREATE INDEX idx_review_decisions_decided ON review_decisions(decided_at DESC);

-- Composite for request decision history
CREATE INDEX idx_review_decisions_request_time ON review_decisions(request_id, decided_at DESC);

-- Decision type filtering
CREATE INDEX idx_review_decisions_type ON review_decisions(decision);

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE review_decisions IS 'Individual approval/rejection decisions at each workflow level. Immutable audit trail of the approval process.';

COMMENT ON COLUMN review_decisions.decision_id IS 'Unique decision identifier';
COMMENT ON COLUMN review_decisions.request_id IS 'Associated workflow request';
COMMENT ON COLUMN review_decisions.reviewer_id IS 'User who made the decision';
COMMENT ON COLUMN review_decisions.decision IS 'Decision type: APPROVED, REJECTED, ESCALATED, RETURNED';
COMMENT ON COLUMN review_decisions.level IS 'Approval level at time of decision: FACULTY_SECRETARY, DEAN, RECTOR_SECRETARY, RECTOR';
COMMENT ON COLUMN review_decisions.comments IS 'Reviewer comments (required for REJECTED and RETURNED)';
COMMENT ON COLUMN review_decisions.decided_at IS 'Decision timestamp (immutable)';


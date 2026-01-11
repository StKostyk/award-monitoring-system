-- V007__create_award_requests_table.sql
-- Description: Create award_requests table - Workflow tracking for multi-level approval
-- Author: Stefan Kostyk
-- Date: 2026-01-10

-- ============================================================================
-- AWARD_REQUESTS TABLE
-- ============================================================================
-- Workflow tracking entity for award approval process. Each award has exactly
-- one request that tracks its journey through the multi-level approval workflow.
--
-- Workflow Levels: Faculty Secretary → Dean → Rector Secretary → Rector
-- ============================================================================

CREATE TABLE award_requests (
    -- Primary Key
    request_id BIGSERIAL,
    
    -- Award Reference (1:1 relationship)
    award_id BIGINT NOT NULL,
    
    -- Participants
    submitter_id BIGINT NOT NULL,
    current_reviewer_id BIGINT,
    
    -- Workflow State
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    current_level VARCHAR(30) NOT NULL,
    
    -- Timestamps
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deadline TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    
    -- Rejection Details
    rejection_reason TEXT,
    
    -- Audit Columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT pk_award_requests PRIMARY KEY (request_id),
    CONSTRAINT uk_award_requests_award UNIQUE (award_id),
    CONSTRAINT fk_award_requests_awards FOREIGN KEY (award_id) 
        REFERENCES awards(award_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_award_requests_submitter FOREIGN KEY (submitter_id) 
        REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_award_requests_reviewer FOREIGN KEY (current_reviewer_id) 
        REFERENCES users(user_id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_award_requests_status CHECK (
        status IN ('SUBMITTED', 'IN_REVIEW', 'ESCALATED', 'APPROVED', 'REJECTED', 'RETURNED', 'EXPIRED')
    ),
    CONSTRAINT ck_award_requests_level CHECK (
        current_level IN ('FACULTY_SECRETARY', 'DEAN', 'RECTOR_SECRETARY', 'RECTOR')
    )
);

-- ============================================================================
-- INDEXES
-- ============================================================================

-- Foreign key indexes
CREATE INDEX idx_requests_submitter ON award_requests(submitter_id);
CREATE INDEX idx_requests_reviewer ON award_requests(current_reviewer_id);

-- Status filtering
CREATE INDEX idx_requests_status ON award_requests(status);

-- Approval level filtering
CREATE INDEX idx_requests_level ON award_requests(current_level);

-- Deadline monitoring for active requests
CREATE INDEX idx_requests_deadline ON award_requests(deadline) 
    WHERE status IN ('SUBMITTED', 'IN_REVIEW', 'ESCALATED');

-- Composite for reviewer's pending work
CREATE INDEX idx_requests_reviewer_pending ON award_requests(current_reviewer_id, deadline)
    WHERE status IN ('SUBMITTED', 'IN_REVIEW');

-- Partial index for active requests
CREATE INDEX idx_requests_active ON award_requests(current_reviewer_id, created_at DESC)
    WHERE status IN ('SUBMITTED', 'IN_REVIEW', 'ESCALATED');

-- ============================================================================
-- ADD FOREIGN KEY FROM DOCUMENTS TO AWARD_REQUESTS
-- ============================================================================

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_requests FOREIGN KEY (request_id) 
        REFERENCES award_requests(request_id) ON DELETE CASCADE ON UPDATE CASCADE;

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE award_requests IS 'Workflow requests for award approval. Tracks multi-level approval process with one request per award.';

COMMENT ON COLUMN award_requests.request_id IS 'Unique request identifier';
COMMENT ON COLUMN award_requests.award_id IS 'Associated award (unique - one request per award)';
COMMENT ON COLUMN award_requests.submitter_id IS 'User who submitted the award for approval';
COMMENT ON COLUMN award_requests.current_reviewer_id IS 'Currently assigned reviewer (nullable if unassigned)';
COMMENT ON COLUMN award_requests.status IS 'Request status: SUBMITTED, IN_REVIEW, ESCALATED, APPROVED, REJECTED, RETURNED, EXPIRED';
COMMENT ON COLUMN award_requests.current_level IS 'Current approval level: FACULTY_SECRETARY, DEAN, RECTOR_SECRETARY, RECTOR';
COMMENT ON COLUMN award_requests.submitted_at IS 'Initial submission timestamp';
COMMENT ON COLUMN award_requests.deadline IS 'Processing deadline based on SLA requirements';
COMMENT ON COLUMN award_requests.completed_at IS 'Final decision timestamp (approval or rejection)';
COMMENT ON COLUMN award_requests.rejection_reason IS 'Explanation if request was rejected';
COMMENT ON COLUMN award_requests.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN award_requests.updated_at IS 'Last modification timestamp';


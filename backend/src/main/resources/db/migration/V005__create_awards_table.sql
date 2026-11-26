-- V005__create_awards_table.sql
-- Description: Create awards table - Core business entity for professional achievements
-- Author: Stefan Kostyk
-- Date: 2026-01-10

-- ============================================================================
-- AWARDS TABLE
-- ============================================================================
-- Core business entity representing professional achievements, recognition,
-- and awards received by employees. Contains both public award information
-- and metadata. Awards are publicly visible for transparency.
-- ============================================================================

CREATE TABLE awards (
    -- Primary Key
    award_id BIGSERIAL,
    
    -- References
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    
    -- Business Attributes
    title VARCHAR(500) NOT NULL,
    title_uk VARCHAR(500),
    description TEXT,
    description_uk TEXT,
    awarding_organization VARCHAR(255) NOT NULL,
    award_date DATE NOT NULL,
    
    -- Status & Verification
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    verification_badge BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Scoring
    impact_score INTEGER,
    
    -- External Reference
    external_url VARCHAR(2048),
    
    -- Audit Columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Optimistic Locking
    version BIGINT NOT NULL DEFAULT 1,
    
    -- Constraints
    CONSTRAINT pk_awards PRIMARY KEY (award_id),
    CONSTRAINT fk_awards_users FOREIGN KEY (user_id) 
        REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_awards_categories FOREIGN KEY (category_id) 
        REFERENCES award_categories(category_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_awards_status CHECK (
        status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'ARCHIVED')
    ),
    CONSTRAINT ck_awards_impact_score CHECK (
        impact_score IS NULL OR (impact_score >= 0 AND impact_score <= 100)
    ),
    CONSTRAINT ck_awards_date CHECK (
        award_date <= CURRENT_DATE
    )
);

-- ============================================================================
-- INDEXES
-- ============================================================================

-- Foreign key indexes
CREATE INDEX idx_awards_user ON awards(user_id);
CREATE INDEX idx_awards_category ON awards(category_id);

-- Status filtering (workflow queries)
CREATE INDEX idx_awards_status ON awards(status);

-- Date-based queries (reporting, filtering)
CREATE INDEX idx_awards_date ON awards(award_date DESC);

-- Composite for user's awards filtered by status
CREATE INDEX idx_awards_user_status ON awards(user_id, status);

-- Composite for category reporting
CREATE INDEX idx_awards_category_date ON awards(category_id, award_date DESC);

-- Partial index for pending awards (reviewer dashboard)
CREATE INDEX idx_awards_pending ON awards(user_id, created_at DESC) 
    WHERE status = 'PENDING';

-- Partial index for approved awards (public display)
CREATE INDEX idx_awards_approved ON awards(award_date DESC) 
    WHERE status = 'APPROVED';

-- Full-text search on title (English)
CREATE INDEX ftidx_awards_title ON awards 
    USING GIN (to_tsvector('english', title));

-- Full-text search on Ukrainian title
CREATE INDEX ftidx_awards_title_uk ON awards 
    USING GIN (to_tsvector('simple', COALESCE(title_uk, '')));

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE awards IS 'Award records representing professional achievements and recognition. Core business entity - publicly visible for transparency.';

COMMENT ON COLUMN awards.award_id IS 'Unique award identifier';
COMMENT ON COLUMN awards.user_id IS 'Award recipient (owner)';
COMMENT ON COLUMN awards.category_id IS 'Award classification category';
COMMENT ON COLUMN awards.title IS 'Award title/name (English)';
COMMENT ON COLUMN awards.title_uk IS 'Award title/name (Ukrainian)';
COMMENT ON COLUMN awards.description IS 'Detailed award description (English)';
COMMENT ON COLUMN awards.description_uk IS 'Detailed award description (Ukrainian)';
COMMENT ON COLUMN awards.awarding_organization IS 'Organization that granted the award';
COMMENT ON COLUMN awards.award_date IS 'Date when award was granted (cannot be future)';
COMMENT ON COLUMN awards.status IS 'Workflow status: DRAFT, PENDING, APPROVED, REJECTED, ARCHIVED';
COMMENT ON COLUMN awards.verification_badge IS 'True if verified by supporting documents';
COMMENT ON COLUMN awards.impact_score IS 'Calculated significance score (0-100) based on category level and organization';
COMMENT ON COLUMN awards.external_url IS 'Link to external verification or award page';
COMMENT ON COLUMN awards.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN awards.updated_at IS 'Last modification timestamp';
COMMENT ON COLUMN awards.version IS 'Optimistic locking version';


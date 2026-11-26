-- V012__create_indexes.sql
-- Description: Create additional performance indexes not defined in table creation scripts
-- Author: Stefan Kostyk
-- Date: 2026-01-10

-- ============================================================================
-- ADDITIONAL PERFORMANCE INDEXES
-- ============================================================================
-- This migration creates additional indexes identified in PERFORMANCE_STRATEGY.md
-- that were not already created in individual table migrations.
-- ============================================================================

-- ============================================================================
-- USERS TABLE - ADDITIONAL INDEXES
-- ============================================================================

-- Case-insensitive email lookup (for login optimization)
CREATE INDEX idx_users_lower_email ON users(LOWER(email_address));

-- Status filtering
CREATE INDEX idx_users_status ON users(account_status);

-- Partial index for active users only
CREATE INDEX idx_users_active ON users(organization_id)
    WHERE account_status = 'ACTIVE';

-- Composite for user listing with status filter
CREATE INDEX idx_users_org_status ON users(organization_id, account_status);

-- ============================================================================
-- AWARDS TABLE - ADDITIONAL INDEXES
-- ============================================================================

-- Composite for user's awards with date ordering
CREATE INDEX idx_awards_user_date ON awards(user_id, award_date DESC);

-- Composite for category + status (reporting)
CREATE INDEX idx_awards_category_status ON awards(category_id, status);

-- Organization-based award queries (via user)
-- Note: This requires joining with users table, so we optimize the join

-- ============================================================================
-- AWARD_REQUESTS TABLE - ADDITIONAL INDEXES
-- ============================================================================

-- Composite for status + level (workflow routing)
CREATE INDEX idx_requests_status_level ON award_requests(status, current_level);

-- Submitted date ordering
CREATE INDEX idx_requests_submitted ON award_requests(submitted_at DESC);

-- ============================================================================
-- USER_ROLES TABLE - ADDITIONAL INDEXES
-- ============================================================================

-- Partial index for currently active, open-ended roles by organization and type
-- This handles the common case where roles have no expiration date
-- Query pattern: "Who are the current DEANs in this faculty?"
CREATE INDEX idx_user_roles_org_role_active ON user_roles(organization_id, role_type, user_id)
    WHERE valid_to IS NULL;

-- Regular composite index including valid_to for temporal queries
-- This handles roles with expiration dates
-- PostgreSQL can use this efficiently when filtering by valid_to >= CURRENT_DATE
CREATE INDEX idx_user_roles_org_role_temporal ON user_roles(organization_id, role_type, valid_to, user_id)
    WHERE valid_to IS NOT NULL;

-- ============================================================================
-- DOCUMENTS TABLE - ADDITIONAL INDEXES
-- ============================================================================

-- Upload date for chronological listing
CREATE INDEX idx_documents_uploaded ON documents(uploaded_at DESC);

-- File type filtering
CREATE INDEX idx_documents_type ON documents(file_type);

-- ============================================================================
-- CONSENT_RECORDS TABLE - ADDITIONAL INDEXES
-- ============================================================================

-- Withdrawn consents for GDPR reporting
CREATE INDEX idx_consent_records_withdrawn ON consent_records(withdrawn_at DESC)
    WHERE withdrawn_at IS NOT NULL;

-- ============================================================================
-- EXPRESSION INDEX FOR SEARCH
-- ============================================================================

-- Enable pg_trgm extension for fuzzy text search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Trigram index for fuzzy name search on users
CREATE INDEX trgm_users_name ON users
    USING GIN ((first_name || ' ' || last_name) gin_trgm_ops);

-- Trigram index for fuzzy award title search
CREATE INDEX trgm_awards_title ON awards
    USING GIN (title gin_trgm_ops);

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON INDEX idx_users_lower_email IS 'Case-insensitive email lookup optimization for login';
COMMENT ON INDEX idx_users_active IS 'Partial index for quick active user queries';
COMMENT ON INDEX idx_awards_pending IS 'Partial index for reviewer dashboard pending awards';
COMMENT ON INDEX idx_awards_approved IS 'Partial index for public award display';
COMMENT ON INDEX idx_requests_reviewer_pending IS 'Composite index for reviewer workload queries';
COMMENT ON INDEX trgm_users_name IS 'Trigram index for fuzzy user name search';
COMMENT ON INDEX trgm_awards_title IS 'Trigram index for fuzzy award title search';

COMMENT ON INDEX idx_user_roles_org_role_active IS 'Supports finding users by organization and role for permanent assignments. Use with valid_from <= CURRENT_DATE filter in queries.';
COMMENT ON INDEX idx_user_roles_org_role_temporal IS 'Supports finding users by organization and role for time-limited assignments. Enables efficient temporal filtering.';

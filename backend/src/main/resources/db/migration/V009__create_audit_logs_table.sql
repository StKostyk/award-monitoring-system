-- V009__create_audit_logs_table.sql
-- Description: Create audit_logs table - Comprehensive audit trail for GDPR compliance
-- Author: Stefan Kostyk
-- Date: 2026-01-10

-- ============================================================================
-- AUDIT_LOGS TABLE (PARTITIONED)
-- ============================================================================
-- Comprehensive audit trail for all significant system actions. Required for
-- GDPR compliance and security monitoring.
--
-- Business Rules:
-- - All data modifications logged automatically via triggers
-- - Logs are immutable (no UPDATE/DELETE allowed on this table)
-- - Retention period: 7 years (per GDPR requirements)
-- - Partitioned by month for performance and retention management
-- ============================================================================

-- Create partitioned parent table
CREATE TABLE audit_logs (
    -- Primary Key (composite with partition key)
    log_id BIGSERIAL,
    
    -- Actor
    user_id BIGINT,
    
    -- Action Details
    action_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    
    -- Change Tracking
    old_values JSONB,
    new_values JSONB,
    changed_fields TEXT[],
    
    -- Context
    ip_address INET,
    user_agent VARCHAR(500),
    correlation_id UUID,
    
    -- Timestamp (partition key)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Composite primary key required for partitioning
    CONSTRAINT pk_audit_logs PRIMARY KEY (log_id, created_at)
) PARTITION BY RANGE (created_at);

-- ============================================================================
-- CREATE INITIAL PARTITIONS
-- ============================================================================
-- Create partitions for current year and next few months
-- Additional partitions will be created by scheduled job

CREATE TABLE audit_logs_2026_01 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE TABLE audit_logs_2026_02 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

CREATE TABLE audit_logs_2026_03 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

CREATE TABLE audit_logs_2026_04 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');

CREATE TABLE audit_logs_2026_05 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE audit_logs_2026_06 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

-- Default partition for any data outside defined ranges
CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;

-- ============================================================================
-- INDEXES
-- ============================================================================
-- Note: Indexes are created on the parent table and inherited by partitions

-- User activity queries
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);

-- Entity lookup (find all changes to specific record)
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);

-- Time-based queries
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(created_at DESC);

-- Action type filtering
CREATE INDEX idx_audit_logs_action ON audit_logs(action_type);

-- Composite for user activity timeline
CREATE INDEX idx_audit_logs_user_time ON audit_logs(user_id, created_at DESC);

-- Correlation ID for request tracing
CREATE INDEX idx_audit_logs_correlation ON audit_logs(correlation_id)
    WHERE correlation_id IS NOT NULL;

-- ============================================================================
-- PREVENT MODIFICATIONS
-- ============================================================================
-- Rule to prevent UPDATE and DELETE operations on audit_logs

CREATE RULE audit_logs_no_update AS ON UPDATE TO audit_logs
    DO INSTEAD NOTHING;

CREATE RULE audit_logs_no_delete AS ON DELETE TO audit_logs
    DO INSTEAD NOTHING;

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE audit_logs IS 'Comprehensive audit trail for GDPR compliance and security monitoring. Partitioned by month. Immutable - no updates or deletes allowed.';

COMMENT ON COLUMN audit_logs.log_id IS 'Unique log entry identifier';
COMMENT ON COLUMN audit_logs.user_id IS 'User who performed the action (NULL for system operations)';
COMMENT ON COLUMN audit_logs.action_type IS 'Action type: CREATE, UPDATE, DELETE, LOGIN, LOGOUT, etc.';
COMMENT ON COLUMN audit_logs.entity_type IS 'Entity/table affected by the action';
COMMENT ON COLUMN audit_logs.entity_id IS 'ID of the affected record';
COMMENT ON COLUMN audit_logs.old_values IS 'Previous values before change (for UPDATE/DELETE)';
COMMENT ON COLUMN audit_logs.new_values IS 'New values after change (for INSERT/UPDATE)';
COMMENT ON COLUMN audit_logs.changed_fields IS 'List of column names that were modified';
COMMENT ON COLUMN audit_logs.ip_address IS 'GDPR: Technical Identifier - Client IP address';
COMMENT ON COLUMN audit_logs.user_agent IS 'Client user agent string';
COMMENT ON COLUMN audit_logs.correlation_id IS 'Request correlation ID for distributed tracing';
COMMENT ON COLUMN audit_logs.created_at IS 'Log entry timestamp (partition key)';


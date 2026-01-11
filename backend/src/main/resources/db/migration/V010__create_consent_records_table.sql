-- V010__create_consent_records_table.sql
-- Description: Create consent_records table - GDPR-compliant consent tracking
-- Author: Stefan Kostyk
-- Date: 2026-01-10

-- ============================================================================
-- CONSENT_RECORDS TABLE
-- ============================================================================
-- GDPR-compliant consent tracking for user data processing agreements.
-- Records all consent grants and withdrawals with full audit trail.
--
-- Business Rules:
-- - Each consent type tracked separately per user
-- - Consent version tracked for policy updates
-- - Withdrawal timestamp preserved for audit
-- - Historical consent records never deleted
-- ============================================================================

CREATE TABLE consent_records (
    -- Primary Key
    consent_id BIGSERIAL,
    
    -- User Reference
    user_id BIGINT NOT NULL,
    
    -- Consent Details
    consent_type VARCHAR(50) NOT NULL,
    consent_version VARCHAR(20) NOT NULL,
    
    -- State Tracking
    is_granted BOOLEAN NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE,
    withdrawn_at TIMESTAMP WITH TIME ZONE,
    
    -- Context (for audit)
    ip_address INET,
    user_agent VARCHAR(500),
    
    -- Audit Column
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT pk_consent_records PRIMARY KEY (consent_id),
    CONSTRAINT fk_consent_records_users FOREIGN KEY (user_id) 
        REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_consent_records_type CHECK (
        consent_type IN ('DATA_PROCESSING', 'PUBLIC_VISIBILITY', 'EMAIL_NOTIFICATIONS', 'SMS_NOTIFICATIONS', 'ANALYTICS')
    ),
    -- If granted, granted_at must be set
    CONSTRAINT ck_consent_records_granted CHECK (
        (is_granted = FALSE) OR (is_granted = TRUE AND granted_at IS NOT NULL)
    ),
    -- If withdrawn, withdrawn_at must be set and is_granted must be false
    CONSTRAINT ck_consent_records_withdrawn CHECK (
        (withdrawn_at IS NULL) OR (withdrawn_at IS NOT NULL AND is_granted = FALSE)
    )
);

-- ============================================================================
-- INDEXES
-- ============================================================================

-- User lookup
CREATE INDEX idx_consent_records_user ON consent_records(user_id);

-- User + type composite (common lookup pattern)
CREATE INDEX idx_consent_records_user_type ON consent_records(user_id, consent_type);

-- Partial index for active consents
CREATE INDEX idx_consent_records_active ON consent_records(user_id, consent_type)
    WHERE is_granted = TRUE AND withdrawn_at IS NULL;

-- Consent version tracking
CREATE INDEX idx_consent_records_version ON consent_records(consent_type, consent_version);

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE consent_records IS 'GDPR-compliant consent tracking for user data processing agreements. Historical records never deleted.';

COMMENT ON COLUMN consent_records.consent_id IS 'Unique consent record identifier';
COMMENT ON COLUMN consent_records.user_id IS 'User granting or withdrawing consent';
COMMENT ON COLUMN consent_records.consent_type IS 'Type of consent: DATA_PROCESSING, PUBLIC_VISIBILITY, EMAIL_NOTIFICATIONS, SMS_NOTIFICATIONS, ANALYTICS';
COMMENT ON COLUMN consent_records.consent_version IS 'Policy version consented to (e.g., "1.0.0")';
COMMENT ON COLUMN consent_records.is_granted IS 'Current consent state (true = granted, false = not granted or withdrawn)';
COMMENT ON COLUMN consent_records.granted_at IS 'Timestamp when consent was granted';
COMMENT ON COLUMN consent_records.withdrawn_at IS 'Timestamp when consent was withdrawn (if applicable)';
COMMENT ON COLUMN consent_records.ip_address IS 'GDPR: Technical Identifier - Client IP at time of action';
COMMENT ON COLUMN consent_records.user_agent IS 'Browser/client information at time of action';
COMMENT ON COLUMN consent_records.created_at IS 'Record creation timestamp';


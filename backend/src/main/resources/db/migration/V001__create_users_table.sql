-- V001__create_users_table.sql
-- Description: Create users table - Central identity entity for the award monitoring system
-- Author: Stefan Kostyk
-- Date: 2026-01-10

-- ============================================================================
-- USERS TABLE
-- ============================================================================
-- Central identity entity representing all system users including employees,
-- administrators, and approvers. Contains authentication credentials and 
-- profile information.
-- 
-- Note: FK to organizations added in V002 after organizations table exists
-- ============================================================================

CREATE TABLE users (
    -- Primary Key
    user_id BIGSERIAL,
    
    -- Identity & Authentication
    email_address VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    
    -- Account State
    account_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    
    -- Organization Reference (FK added in V002)
    organization_id BIGINT NOT NULL,
    
    -- Activity Tracking
    last_login_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit Columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Optimistic Locking
    version BIGINT NOT NULL DEFAULT 1,
    
    -- Constraints
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uk_users_email UNIQUE (email_address),
    CONSTRAINT ck_users_status CHECK (
        account_status IN ('PENDING', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'RETIRED', 'MEMORIAL', 'DELETED')
    )
);

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE users IS 'System users including employees and administrators. Central identity entity for the award monitoring system.';

-- Column comments
COMMENT ON COLUMN users.user_id IS 'Unique user identifier (auto-generated)';
COMMENT ON COLUMN users.email_address IS 'GDPR: Personal Data - User email address used as login credential';
COMMENT ON COLUMN users.first_name IS 'GDPR: Personal Data - User first name';
COMMENT ON COLUMN users.last_name IS 'GDPR: Personal Data - User last name';
COMMENT ON COLUMN users.password_hash IS 'GDPR: Confidential - BCrypt hashed password (strength 12)';
COMMENT ON COLUMN users.account_status IS 'Account state: PENDING, ACTIVE, INACTIVE, SUSPENDED, RETIRED, MEMORIAL, DELETED';
COMMENT ON COLUMN users.organization_id IS 'Reference to primary organizational unit';
COMMENT ON COLUMN users.last_login_at IS 'Timestamp of last successful authentication';
COMMENT ON COLUMN users.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN users.updated_at IS 'Last modification timestamp';
COMMENT ON COLUMN users.version IS 'Optimistic locking version for concurrent access control';


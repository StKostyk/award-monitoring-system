-- V003__create_user_roles_table.sql
-- Description: Create user_roles table - Role assignments with temporal validity
-- Author: Stefan Kostyk
-- Date: 2026-01-10

-- ============================================================================
-- USER_ROLES TABLE
-- ============================================================================
-- Role assignments linking users to specific roles within organizational contexts.
-- Supports temporal validity for role transitions and history tracking.
-- ============================================================================

CREATE TABLE user_roles (
    -- Primary Key
    user_role_id BIGSERIAL,

    -- References
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,

    -- Role Assignment
    role_type VARCHAR(30) NOT NULL,

    -- Temporal Validity
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,

    -- Audit Columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,

    -- Constraints
    CONSTRAINT pk_user_roles PRIMARY KEY (user_role_id),
    CONSTRAINT fk_user_roles_users FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_user_roles_organizations FOREIGN KEY (organization_id)
        REFERENCES organizations(org_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_user_roles_created_by FOREIGN KEY (created_by)
        REFERENCES users(user_id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_user_roles_dates CHECK (
        valid_to IS NULL OR valid_to >= valid_from
    ),
    CONSTRAINT ck_user_roles_type CHECK (
        role_type IN ('EMPLOYEE', 'FACULTY_SECRETARY', 'DEAN', 'RECTOR_SECRETARY', 'RECTOR', 'SYSTEM_ADMIN', 'GDPR_OFFICER')
    )
);

-- ============================================================================
-- INDEXES
-- ============================================================================

-- Standard lookups
CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_org ON user_roles(organization_id);
CREATE INDEX idx_user_roles_type ON user_roles(role_type);

-- Composite for user's roles in organization
CREATE INDEX idx_user_roles_user_org ON user_roles(user_id, organization_id);

-- Partial index for potentially active roles (no end date set)
-- This indexes roles that are definitely still in effect (valid_to IS NULL)
-- For roles with end dates, the application must filter by CURRENT_DATE in queries
CREATE INDEX idx_user_roles_active ON user_roles(user_id, role_type)
    WHERE valid_to IS NULL;

-- Alternative: Include valid_to in a regular index for temporal queries
-- This allows efficient querying of roles by their validity period
-- The database can use this index when filtering by valid_to >= CURRENT_DATE
CREATE INDEX idx_user_roles_temporal ON user_roles(user_id, role_type, valid_to)
    WHERE valid_to IS NOT NULL;

-- Composite index for checking role validity within date ranges
-- Useful for queries like "what roles did this user have on a specific date?"
CREATE INDEX idx_user_roles_date_range ON user_roles(user_id, valid_from, valid_to);

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE user_roles IS 'Role assignments linking users to roles within organizational contexts with temporal validity.';

COMMENT ON COLUMN user_roles.user_role_id IS 'Unique role assignment identifier';
COMMENT ON COLUMN user_roles.user_id IS 'User receiving the role assignment';
COMMENT ON COLUMN user_roles.organization_id IS 'Organizational scope of the role';
COMMENT ON COLUMN user_roles.role_type IS 'Role type: EMPLOYEE, FACULTY_SECRETARY, DEAN, RECTOR_SECRETARY, RECTOR, SYSTEM_ADMIN, GDPR_OFFICER';
COMMENT ON COLUMN user_roles.valid_from IS 'Role effective start date';
COMMENT ON COLUMN user_roles.valid_to IS 'Role effective end date (NULL means currently active)';
COMMENT ON COLUMN user_roles.created_at IS 'Role assignment creation timestamp';
COMMENT ON COLUMN user_roles.created_by IS 'Admin user who created this role assignment';

COMMENT ON INDEX idx_user_roles_active IS 'Partial index for open-ended role assignments (no end date). Query active roles by combining this with valid_from <= CURRENT_DATE filter.';
COMMENT ON INDEX idx_user_roles_temporal IS 'Index for roles with end dates, enabling efficient temporal queries. Use for historical role lookups.';
COMMENT ON INDEX idx_user_roles_date_range IS 'Supports queries checking role validity within specific date ranges.';

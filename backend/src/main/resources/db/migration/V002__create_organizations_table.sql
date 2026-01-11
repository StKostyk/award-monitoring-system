-- V002__create_organizations_table.sql
-- Description: Create organizations table - Hierarchical organizational structure
-- Author: Stefan Kostyk
-- Date: 2026-01-10

-- ============================================================================
-- PREREQUISITES
-- ============================================================================

-- Enable ltree extension for hierarchical queries
CREATE EXTENSION IF NOT EXISTS ltree;

-- ============================================================================
-- ORGANIZATIONS TABLE
-- ============================================================================
-- Hierarchical organizational structure representing the university, faculties,
-- and departments. Self-referencing for parent-child relationships.
-- ============================================================================

CREATE TABLE organizations (
    -- Primary Key
    org_id BIGSERIAL,

    -- Business Attributes
    name VARCHAR(255) NOT NULL,
    name_uk VARCHAR(255),
    code VARCHAR(50),

    -- Hierarchy
    org_type VARCHAR(20) NOT NULL,
    parent_org_id BIGINT,
    hierarchy_path LTREE,
    depth INTEGER NOT NULL DEFAULT 0,

    -- Status
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Audit Columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT pk_organizations PRIMARY KEY (org_id),
    CONSTRAINT uk_organizations_code UNIQUE (code),
    CONSTRAINT fk_organizations_parent FOREIGN KEY (parent_org_id)
        REFERENCES organizations(org_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_organizations_type CHECK (
        org_type IN ('UNIVERSITY', 'COLLEGE', 'FACULTY', 'SPECIALITY', 'DEPARTMENT')
    )
);

-- ============================================================================
-- INDEXES
-- ============================================================================

CREATE INDEX idx_organizations_parent ON organizations(parent_org_id);
CREATE INDEX gist_organizations_path ON organizations USING GIST (hierarchy_path);
CREATE INDEX idx_organizations_type ON organizations(org_type);
CREATE INDEX idx_organizations_active ON organizations(org_id) WHERE is_active = TRUE;

-- ============================================================================
-- ADD FOREIGN KEY TO USERS TABLE
-- ============================================================================
-- Now that organizations exists, add the FK constraint to users

ALTER TABLE users
    ADD CONSTRAINT fk_users_organizations
    FOREIGN KEY (organization_id) REFERENCES organizations(org_id)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- Add index for users.organization_id
CREATE INDEX idx_users_organization ON users(organization_id);

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE organizations IS 'Hierarchical organizational structure representing university, faculties, and departments.';

COMMENT ON COLUMN organizations.org_id IS 'Unique organization identifier';
COMMENT ON COLUMN organizations.name IS 'Organization full name (English)';
COMMENT ON COLUMN organizations.name_uk IS 'Organization full name (Ukrainian)';
COMMENT ON COLUMN organizations.code IS 'Internal organization code (unique)';
COMMENT ON COLUMN organizations.org_type IS 'Organization level: UNIVERSITY, FACULTY, DEPARTMENT';
COMMENT ON COLUMN organizations.parent_org_id IS 'Parent organization reference (NULL for root/university)';
COMMENT ON COLUMN organizations.hierarchy_path IS 'Materialized path for efficient hierarchy queries (ltree)';
COMMENT ON COLUMN organizations.depth IS 'Hierarchy depth level (0 = university, 1 = faculty, 2 = department)';
COMMENT ON COLUMN organizations.is_active IS 'Active status flag for soft delete';
COMMENT ON COLUMN organizations.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN organizations.updated_at IS 'Last modification timestamp';


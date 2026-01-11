-- V004__create_award_categories_table.sql
-- Description: Create award_categories table - Classification system for awards
-- Author: Stefan Kostyk
-- Date: 2026-01-10

-- ============================================================================
-- AWARD_CATEGORIES TABLE
-- ============================================================================
-- Classification system for awards defining categories, recognition levels,
-- and approval requirements. Supports hierarchical category structure.
-- ============================================================================

CREATE TABLE award_categories (
    -- Primary Key
    category_id BIGSERIAL,

    -- Business Attributes
    name VARCHAR(100) NOT NULL,
    name_uk VARCHAR(100),
    description TEXT,

    -- Classification
    level VARCHAR(20) NOT NULL,

    -- Hierarchy
    parent_category_id BIGINT,

    -- Display
    sort_order INTEGER NOT NULL DEFAULT 0,

    -- Status Flags
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,

    -- Audit Columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT pk_award_categories PRIMARY KEY (category_id),
    CONSTRAINT uk_award_categories_name UNIQUE (name),
    CONSTRAINT fk_award_categories_parent FOREIGN KEY (parent_category_id)
        REFERENCES award_categories(category_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_award_categories_level CHECK (
        level IN ('SPECIALITY', 'DEPARTMENT', 'FACULTY', 'COLLEGE', 'UNIVERSITY', 'LOCAL', 'REGIONAL', 'NATIONAL',
        'INTERNATIONAL')
    )
);

-- ============================================================================
-- INDEXES
-- ============================================================================

CREATE INDEX idx_award_categories_level ON award_categories(level);
CREATE INDEX idx_award_categories_parent ON award_categories(parent_category_id);
CREATE INDEX idx_award_categories_active ON award_categories(category_id) WHERE is_active = TRUE;
CREATE INDEX idx_award_categories_sort ON award_categories(sort_order, name);

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE award_categories IS 'Classification system for awards defining categories, recognition levels, and approval requirements.';

COMMENT ON COLUMN award_categories.category_id IS 'Unique category identifier';
COMMENT ON COLUMN award_categories.name IS 'Category name (English)';
COMMENT ON COLUMN award_categories.name_uk IS 'Category name (Ukrainian)';
COMMENT ON COLUMN award_categories.description IS 'Detailed category description';
COMMENT ON COLUMN award_categories.level IS 'Recognition level: SPECIALITY, DEPARTMENT, FACULTY, COLLEGE, UNIVERSITY, LOCAL, REGIONAL, NATIONAL, INTERNATIONAL - determines minimum approval authority';
COMMENT ON COLUMN award_categories.parent_category_id IS 'Parent category for hierarchical classification';
COMMENT ON COLUMN award_categories.sort_order IS 'Display order in UI listings';
COMMENT ON COLUMN award_categories.is_active IS 'Active status flag (inactive categories not available for new awards)';
COMMENT ON COLUMN award_categories.is_system IS 'System-defined flag (system categories cannot be deleted)';
COMMENT ON COLUMN award_categories.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN award_categories.updated_at IS 'Last modification timestamp';


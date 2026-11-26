-- ============================================================================
-- V054: Source of an award (Feature 2.5, story 2.5.5)
-- ============================================================================
-- FORM: entered through the award form and the review path; IMPORT: an approved
-- historical award read from a faculty spreadsheet without a review request.
-- A constant default fills the existing rows without an UPDATE, so the audit
-- trigger of awards does not fire.
-- ============================================================================

ALTER TABLE awards ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'FORM';
ALTER TABLE awards ADD CONSTRAINT ck_awards_source CHECK (source IN ('FORM', 'IMPORT'));

COMMENT ON COLUMN awards.source IS 'How the award was entered: FORM (award form and review) or IMPORT (spreadsheet of past awards, approved without a review request)';

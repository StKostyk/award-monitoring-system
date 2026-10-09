-- V035__award_visibility.sql
-- Description: Owner's choice of who sees an approved personal award beyond the owner and scoped reviewers
-- Author: Stefan Kostyk
-- Date: 2026-10-09

-- ============================================================================
-- AWARDS.VISIBILITY
-- ============================================================================

ALTER TABLE awards ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';

ALTER TABLE awards ADD CONSTRAINT ck_awards_visibility
    CHECK (visibility IN ('PRIVATE', 'UNIVERSITY', 'PUBLIC'));
ALTER TABLE awards ADD CONSTRAINT ck_awards_visibility_personal
    CHECK (recipient_org_id IS NULL OR visibility = 'PRIVATE');

COMMENT ON COLUMN awards.visibility IS
    'Owner''s choice for an approved personal award: PRIVATE (owner and scoped reviewers), UNIVERSITY (also '
    'signed-in colleagues), PUBLIC (also the public page); unit awards stay PRIVATE and are shared once approved';

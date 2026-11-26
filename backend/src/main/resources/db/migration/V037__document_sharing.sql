-- V037__document_sharing.sql
-- Description: Owner's choice to show a document of a shared award to signed-in colleagues
-- Author: Stefan Kostyk
-- Date: 2026-10-12

-- ============================================================================
-- DOCUMENTS.SHOWN_TO_COLLEAGUES
-- ============================================================================

ALTER TABLE documents ADD COLUMN shown_to_colleagues BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_documents_shown ON documents (award_id) WHERE shown_to_colleagues;

COMMENT ON COLUMN documents.shown_to_colleagues IS
    'Owner''s choice for a document of an approved personal award shared with colleagues (UNIVERSITY or PUBLIC): '
    'signed-in users may open it from the achievement; cleared when the award returns to PRIVATE, never public';

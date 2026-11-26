-- V025__document_type_description.sql
-- Description: Document type and description chosen by the uploader; one copy of a file per award
-- Author: Stefan Kostyk
-- Date: 2026-10-02

ALTER TABLE documents
    ADD COLUMN document_type VARCHAR(30) NOT NULL DEFAULT 'SUPPORTING_DOCUMENT',
    ADD COLUMN description VARCHAR(500),
    ADD CONSTRAINT ck_documents_document_type CHECK (
        document_type IN ('CERTIFICATE', 'DIPLOMA', 'SUPPORTING_DOCUMENT', 'PHOTO')
    );

CREATE UNIQUE INDEX uq_documents_award_checksum ON documents(award_id, checksum_sha256);

COMMENT ON COLUMN documents.document_type IS 'Kind of document chosen by the uploader: CERTIFICATE, DIPLOMA, SUPPORTING_DOCUMENT, PHOTO';
COMMENT ON COLUMN documents.description IS 'Optional note of the uploader (max 500 characters)';
COMMENT ON COLUMN documents.storage_key IS 'Object key awards/<award_id>/<random UUID>; no file name or personal data';
COMMENT ON COLUMN documents.storage_url IS 'Unused: downloads go through the API (GET /api/v1/documents/{id})';
COMMENT ON COLUMN documents.processing_status IS 'Parsing state; stays PENDING until OCR is delivered';

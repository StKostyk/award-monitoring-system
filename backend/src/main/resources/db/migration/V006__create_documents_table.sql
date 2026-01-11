-- V006__create_documents_table.sql
-- Description: Create documents table - File metadata for supporting documents
-- Author: Stefan Kostyk
-- Date: 2026-01-10

-- ============================================================================
-- DOCUMENTS TABLE
-- ============================================================================
-- File metadata for supporting documents attached to awards. Actual files
-- are stored in object storage (S3/Azure Blob). Includes AI-parsed metadata.
--
-- Business Rules:
-- - Maximum file size: 10MB (10,485,760 bytes)
-- - Allowed types: PDF, JPG, PNG, WEBP
-- - Confidence score below 0.7 triggers manual review
-- ============================================================================

CREATE TABLE documents (
    -- Primary Key
    document_id BIGSERIAL,
    
    -- Parent References (at least one should be set)
    award_id BIGINT,
    request_id BIGINT,
    
    -- File Metadata
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    mime_type VARCHAR(100),
    file_size BIGINT NOT NULL,
    
    -- Storage Reference (Object Storage)
    storage_bucket VARCHAR(100) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    storage_url VARCHAR(2048),
    
    -- Integrity
    checksum_sha256 VARCHAR(64),
    
    -- AI Processing Results
    parsed_metadata JSONB,
    confidence_score NUMERIC(5,4),
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit Columns
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by BIGINT NOT NULL,
    
    -- Constraints
    CONSTRAINT pk_documents PRIMARY KEY (document_id),
    CONSTRAINT fk_documents_awards FOREIGN KEY (award_id) 
        REFERENCES awards(award_id) ON DELETE CASCADE ON UPDATE CASCADE,
    -- Note: FK to award_requests added in V007 after that table exists
    CONSTRAINT fk_documents_uploaded_by FOREIGN KEY (uploaded_by) 
        REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_documents_file_type CHECK (
        file_type IN ('PDF', 'JPG', 'JPEG', 'PNG', 'WEBP')
    ),
    CONSTRAINT ck_documents_file_size CHECK (
        file_size > 0 AND file_size <= 10485760
    ),
    CONSTRAINT ck_documents_status CHECK (
        processing_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'MANUAL_REVIEW', 'VERIFIED')
    ),
    CONSTRAINT ck_documents_confidence CHECK (
        confidence_score IS NULL OR (confidence_score >= 0 AND confidence_score <= 1)
    )
);

-- ============================================================================
-- INDEXES
-- ============================================================================

-- Foreign key indexes
CREATE INDEX idx_documents_award ON documents(award_id);
CREATE INDEX idx_documents_request ON documents(request_id);
CREATE INDEX idx_documents_uploader ON documents(uploaded_by);

-- Processing status (AI pipeline queries)
CREATE INDEX idx_documents_status ON documents(processing_status);

-- Partial index for documents needing processing
CREATE INDEX idx_documents_pending ON documents(uploaded_at)
    WHERE processing_status = 'PENDING';

-- GIN index for JSONB metadata queries
CREATE INDEX gin_documents_metadata ON documents USING GIN (parsed_metadata);

-- GIN index with JSON path operators for specific queries
CREATE INDEX gin_documents_metadata_path ON documents 
    USING GIN (parsed_metadata jsonb_path_ops);

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE documents IS 'File metadata for supporting documents. Actual files stored in object storage (S3/Azure Blob). Includes AI-parsed metadata.';

COMMENT ON COLUMN documents.document_id IS 'Unique document identifier';
COMMENT ON COLUMN documents.award_id IS 'Associated award (if attached to award)';
COMMENT ON COLUMN documents.request_id IS 'Associated workflow request (if attached to request)';
COMMENT ON COLUMN documents.file_name IS 'Original file name as uploaded';
COMMENT ON COLUMN documents.file_type IS 'File extension/type: PDF, JPG, PNG, WEBP';
COMMENT ON COLUMN documents.mime_type IS 'MIME content type (e.g., application/pdf)';
COMMENT ON COLUMN documents.file_size IS 'File size in bytes (max 10MB = 10485760)';
COMMENT ON COLUMN documents.storage_bucket IS 'Object storage bucket name';
COMMENT ON COLUMN documents.storage_key IS 'Object storage key/path';
COMMENT ON COLUMN documents.storage_url IS 'Pre-signed access URL (may expire)';
COMMENT ON COLUMN documents.checksum_sha256 IS 'SHA-256 checksum for file integrity verification';
COMMENT ON COLUMN documents.parsed_metadata IS 'AI-extracted metadata as JSONB';
COMMENT ON COLUMN documents.confidence_score IS 'AI model confidence (0.0000-1.0000), values < 0.7 trigger manual review';
COMMENT ON COLUMN documents.processing_status IS 'AI processing state: PENDING, PROCESSING, COMPLETED, FAILED, MANUAL_REVIEW, VERIFIED';
COMMENT ON COLUMN documents.processed_at IS 'AI processing completion timestamp';
COMMENT ON COLUMN documents.uploaded_at IS 'File upload timestamp';
COMMENT ON COLUMN documents.uploaded_by IS 'User who uploaded the document';


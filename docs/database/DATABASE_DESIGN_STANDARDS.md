# Database Design Standards
## Award Monitoring & Tracking System

> **Phase 9 Deliverable**: Data Architecture & Database Design  
> **Document Version**: 1.0  
> **Last Updated**: December 2025  
> **Author**: Stefan Kostyk  
> **Database**: PostgreSQL 16  
> **Classification**: Internal

---

## Executive Summary

This document establishes comprehensive database design standards for the Award Monitoring & Tracking System. These standards ensure consistency, maintainability, and enterprise-grade quality across all database objects. All naming conventions and design patterns are optimized for PostgreSQL 16 and aligned with Spring Boot/JPA integration.

### Key Principles
- **Consistency**: Uniform naming and structure across all database objects
- **Clarity**: Self-documenting names that reflect business domain
- **Performance**: Design patterns optimized for PostgreSQL 16 capabilities
- **Compliance**: GDPR-ready audit trails and data protection patterns
- **Maintainability**: Clear conventions for schema evolution

---

## 1. Naming Conventions

### 1.1 General Rules

| **Rule** | **Standard** | **Example** |
|----------|--------------|-------------|
| Character Set | Lowercase ASCII letters, digits, underscores | `user_accounts`, `award_id` |
| Word Separator | Underscore (`_`) | `award_categories`, `created_at` |
| Maximum Length | 63 characters (PostgreSQL limit) | Keep names concise but descriptive |
| Reserved Words | Avoid PostgreSQL reserved words | Use `user_status` not `status` alone |
| Abbreviations | Minimize; use standard abbreviations only | `org` for organization, `id` for identifier |
| Language | English only | Consistency across codebase |

### 1.2 Table Naming Conventions

| **Convention** | **Pattern** | **Examples** |
|----------------|-------------|--------------|
| Format | `plural_snake_case` | `users`, `award_categories` |
| Business Domain | Reflect domain context | `award_requests`, `review_decisions` |
| Junction Tables | `entity1_entity2` (alphabetical) | `user_roles`, `award_documents` |
| Audit Tables | `entity_audit` | `users_audit`, `awards_audit` |
| Archive Tables | `entity_archive` | `awards_archive` |
| Temporary Tables | `tmp_purpose` | `tmp_import_awards` |

```sql
-- ✅ CORRECT: Table naming examples
CREATE TABLE users (...);                    -- Entity: User
CREATE TABLE award_categories (...);         -- Entity: AwardCategory
CREATE TABLE review_decisions (...);         -- Entity: ReviewDecision
CREATE TABLE user_roles (...);               -- Junction: User ↔ Role
CREATE TABLE audit_logs (...);               -- Audit entity
CREATE TABLE consent_records (...);          -- Compliance entity

-- ❌ INCORRECT: Anti-patterns to avoid
CREATE TABLE User (...);                     -- PascalCase
CREATE TABLE tblUsers (...);                 -- Hungarian notation
CREATE TABLE user (...);                     -- Singular (use plural)
CREATE TABLE UserAwardCategory (...);        -- CamelCase, no underscores
```

### 1.3 Column Naming Conventions

| **Convention** | **Pattern** | **Examples** |
|----------------|-------------|--------------|
| Format | `snake_case` | `first_name`, `email_address` |
| Primary Key | `table_singular_id` | `user_id`, `award_id` |
| Foreign Key | `referenced_table_singular_id` | `organization_id`, `category_id` |
| Boolean | `is_` or `has_` prefix | `is_active`, `has_verified` |
| Timestamps | `_at` suffix | `created_at`, `updated_at`, `deleted_at` |
| Dates | `_date` or `_on` suffix | `award_date`, `valid_from` |
| Status | `_status` suffix | `account_status`, `request_status` |
| Counts | `_count` suffix | `approval_count`, `rejection_count` |
| JSON/JSONB | Descriptive name | `parsed_metadata`, `old_values` |

```sql
-- ✅ CORRECT: Column naming examples
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,           -- Primary key
    organization_id BIGINT,                   -- Foreign key
    email_address VARCHAR(255) NOT NULL,      -- Descriptive name
    first_name VARCHAR(100) NOT NULL,         -- Snake case
    last_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,      -- Purpose-clear name
    is_active BOOLEAN DEFAULT TRUE,           -- Boolean with is_ prefix
    account_status VARCHAR(20),               -- Status field
    last_login_at TIMESTAMP WITH TIME ZONE,   -- Timestamp with _at
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT DEFAULT 1                  -- Optimistic locking
);

-- ❌ INCORRECT: Anti-patterns to avoid
CREATE TABLE users (
    id BIGSERIAL,                             -- Too generic
    orgId BIGINT,                             -- CamelCase
    Email VARCHAR(255),                       -- PascalCase
    fname VARCHAR(100),                       -- Unclear abbreviation
    active BOOLEAN,                           -- Missing is_ prefix
    status VARCHAR(20),                       -- Too generic
    lastLogin TIMESTAMP,                      -- CamelCase
    createdDate TIMESTAMP                     -- Inconsistent suffix
);
```

### 1.4 Constraint Naming Conventions

| **Constraint Type** | **Pattern** | **Examples** |
|--------------------|-------------|--------------|
| Primary Key | `pk_table` | `pk_users`, `pk_awards` |
| Foreign Key | `fk_table_referenced` | `fk_awards_users`, `fk_users_organizations` |
| Unique | `uk_table_column(s)` | `uk_users_email`, `uk_users_username` |
| Check | `ck_table_column` | `ck_users_status`, `ck_awards_impact_score` |
| Not Null | (implicit, no name) | Column definition includes `NOT NULL` |
| Default | (implicit, no name) | Column definition includes `DEFAULT` |
| Exclusion | `ex_table_description` | `ex_awards_date_overlap` |

```sql
-- ✅ CORRECT: Constraint naming examples
CREATE TABLE users (
    user_id BIGSERIAL,
    email_address VARCHAR(255) NOT NULL,
    username VARCHAR(50) NOT NULL,
    account_status VARCHAR(20) DEFAULT 'PENDING',
    
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uk_users_email UNIQUE (email_address),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT ck_users_status CHECK (
        account_status IN ('PENDING', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'RETIRED', 'MEMORIAL')
    )
);

ALTER TABLE awards
    ADD CONSTRAINT fk_awards_users 
    FOREIGN KEY (user_id) REFERENCES users(user_id);

ALTER TABLE awards
    ADD CONSTRAINT fk_awards_categories 
    FOREIGN KEY (category_id) REFERENCES award_categories(category_id);
```

### 1.5 Index Naming Conventions

| **Index Type** | **Pattern** | **Examples** |
|----------------|-------------|--------------|
| Standard | `idx_table_column(s)` | `idx_users_email`, `idx_awards_status` |
| Unique | `uidx_table_column(s)` | `uidx_users_email` (if not UK constraint) |
| Composite | `idx_table_col1_col2` | `idx_awards_user_date` |
| Partial | `idx_table_column_condition` | `idx_awards_status_pending` |
| GIN (JSONB) | `gin_table_column` | `gin_documents_metadata` |
| GiST | `gist_table_column` | `gist_locations_coordinates` |
| Full-text | `ftidx_table_column` | `ftidx_awards_title` |
| Expression | `idx_table_expression` | `idx_users_lower_email` |

```sql
-- ✅ CORRECT: Index naming and creation examples

-- Standard indexes
CREATE INDEX idx_users_organization ON users(organization_id);
CREATE INDEX idx_awards_user ON awards(user_id);
CREATE INDEX idx_awards_status ON awards(status);
CREATE INDEX idx_awards_date ON awards(award_date);

-- Composite indexes (column order matters for query optimization)
CREATE INDEX idx_awards_user_status ON awards(user_id, status);
CREATE INDEX idx_awards_category_date ON awards(category_id, award_date DESC);

-- Partial indexes (for frequently queried subsets)
CREATE INDEX idx_awards_status_pending ON awards(status) 
    WHERE status = 'PENDING';
CREATE INDEX idx_requests_active ON award_requests(current_reviewer_id) 
    WHERE status IN ('SUBMITTED', 'IN_REVIEW');

-- GIN index for JSONB columns
CREATE INDEX gin_documents_metadata ON documents USING GIN (parsed_metadata);

-- Full-text search index
CREATE INDEX ftidx_awards_title ON awards USING GIN (to_tsvector('english', title));

-- Expression-based index
CREATE INDEX idx_users_lower_email ON users(LOWER(email_address));
```

### 1.6 Sequence and Other Object Naming

| **Object Type** | **Pattern** | **Examples** |
|-----------------|-------------|--------------|
| Sequence | `seq_table_column` | `seq_users_user_id` (if manual) |
| View | `vw_purpose` | `vw_active_awards`, `vw_pending_requests` |
| Materialized View | `mvw_purpose` | `mvw_award_statistics` |
| Function | `fn_purpose` | `fn_calculate_impact_score` |
| Procedure | `sp_purpose` | `sp_process_expired_requests` |
| Trigger | `trg_table_event` | `trg_users_updated_at`, `trg_awards_audit` |
| Type/Enum | `enum_domain` | `enum_user_status`, `enum_approval_level` |
| Domain | `dom_purpose` | `dom_email`, `dom_positive_integer` |

```sql
-- ✅ CORRECT: Other object naming examples

-- Views
CREATE VIEW vw_active_awards AS
    SELECT * FROM awards WHERE status = 'APPROVED';

CREATE VIEW vw_pending_requests AS
    SELECT * FROM award_requests WHERE status IN ('SUBMITTED', 'IN_REVIEW');

-- Materialized view for reporting
CREATE MATERIALIZED VIEW mvw_award_statistics AS
    SELECT 
        organization_id,
        COUNT(*) as total_awards,
        COUNT(*) FILTER (WHERE status = 'APPROVED') as approved_awards
    FROM awards
    GROUP BY organization_id;

-- Functions
CREATE FUNCTION fn_calculate_impact_score(award_id BIGINT) 
    RETURNS INTEGER AS $$ ... $$ LANGUAGE plpgsql;

-- Triggers
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

CREATE TRIGGER trg_awards_audit
    AFTER INSERT OR UPDATE OR DELETE ON awards
    FOR EACH ROW EXECUTE FUNCTION fn_audit_log();

-- Custom types/enums
CREATE TYPE enum_user_status AS ENUM (
    'PENDING', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'RETIRED', 'MEMORIAL'
);

CREATE TYPE enum_approval_level AS ENUM (
    'FACULTY_SECRETARY', 'DEAN', 'RECTOR_SECRETARY', 'RECTOR'
);
```

---

## 2. Data Type Standards

### 2.1 Standard Data Type Mappings

| **Business Type** | **PostgreSQL Type** | **Java Type** | **Notes** |
|-------------------|--------------------|--------------:|-----------|
| Identifiers | `BIGSERIAL` / `BIGINT` | `Long` | Auto-increment for PK |
| Short Text | `VARCHAR(n)` | `String` | Specify max length |
| Long Text | `TEXT` | `String` | Unlimited length |
| Boolean | `BOOLEAN` | `Boolean` | TRUE/FALSE/NULL |
| Integer | `INTEGER` | `Integer` | -2B to +2B range |
| Decimal/Money | `NUMERIC(p,s)` | `BigDecimal` | Exact precision |
| Date Only | `DATE` | `LocalDate` | No time component |
| Timestamp | `TIMESTAMP WITH TIME ZONE` | `Instant` | Always use TZ |
| Duration | `INTERVAL` | `Duration` | Time spans |
| JSON Data | `JSONB` | `JsonNode` | Binary JSON, indexed |
| UUID | `UUID` | `UUID` | For external identifiers |
| IP Address | `INET` | `String` | IPv4/IPv6 support |
| Binary Data | `BYTEA` | `byte[]` | Small binary; use object storage for large |
| Enum | `VARCHAR(50)` or custom `ENUM` | `Enum` | See enum strategy below |

### 2.2 Recommended Column Sizes

| **Data Category** | **Type & Size** | **Rationale** |
|-------------------|-----------------|---------------|
| Email addresses | `VARCHAR(255)` | RFC 5321 maximum |
| Names (first/last) | `VARCHAR(100)` | International names support |
| Full names | `VARCHAR(255)` | Combined names |
| Titles/Subjects | `VARCHAR(500)` | Award titles can be long |
| Descriptions | `TEXT` | No arbitrary limit |
| URLs | `VARCHAR(2048)` | Browser-safe maximum |
| File paths | `VARCHAR(500)` | Reasonable path length |
| Status codes | `VARCHAR(30)` | Enum values |
| Hash values | `VARCHAR(255)` | BCrypt/SHA output |
| Phone numbers | `VARCHAR(20)` | International format |
| Postal codes | `VARCHAR(20)` | International formats |

### 2.3 Enum Strategy

For this project, we use **VARCHAR with CHECK constraints** instead of PostgreSQL ENUM types:

**Rationale:**
- Easier schema migrations (adding values doesn't require `ALTER TYPE`)
- Better JPA/Hibernate compatibility
- Clearer in data exports
- Flyway-friendly evolution

```sql
-- ✅ RECOMMENDED: VARCHAR with CHECK constraint
CREATE TABLE users (
    account_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT ck_users_status CHECK (
        account_status IN ('PENDING', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'RETIRED', 'MEMORIAL')
    )
);

-- Adding a new status value is a simple ALTER:
ALTER TABLE users DROP CONSTRAINT ck_users_status;
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (
    account_status IN ('PENDING', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'RETIRED', 'MEMORIAL', 'ARCHIVED')
);

-- ⚠️ ALTERNATIVE: PostgreSQL ENUM (use only if values are truly fixed)
CREATE TYPE enum_recognition_level AS ENUM (
    'DEPARTMENT', 'FACULTY', 'UNIVERSITY', 'NATIONAL', 'INTERNATIONAL'
);
```

---

## 3. Table Design Patterns

### 3.1 Standard Entity Structure

All business entities follow this standard structure:

```sql
-- Standard entity template
CREATE TABLE entity_name (
    -- Primary Key
    entity_name_id BIGSERIAL,
    
    -- Business Attributes
    -- ... domain-specific columns ...
    
    -- Foreign Keys
    -- ... reference columns ...
    
    -- Audit Columns (required for all entities)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,  -- References users.user_id
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,  -- References users.user_id
    
    -- Optimistic Locking (for concurrent access)
    version BIGINT NOT NULL DEFAULT 1,
    
    -- Soft Delete Support (optional, per entity)
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by BIGINT,
    
    -- Constraints
    CONSTRAINT pk_entity_name PRIMARY KEY (entity_name_id)
);
```

### 3.2 Audit Trail Pattern

For GDPR compliance and data lineage, critical entities have dedicated audit tables:

```sql
-- Audit table pattern
CREATE TABLE awards_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    
    -- Reference to original record
    award_id BIGINT NOT NULL,
    
    -- Audit metadata
    operation VARCHAR(10) NOT NULL,  -- INSERT, UPDATE, DELETE
    operation_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operation_user_id BIGINT,
    
    -- Change tracking
    old_values JSONB,
    new_values JSONB,
    changed_fields TEXT[],
    
    -- Context
    ip_address INET,
    user_agent VARCHAR(500),
    correlation_id UUID,
    
    CONSTRAINT ck_awards_audit_operation CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE'))
);

-- Index for querying audit history
CREATE INDEX idx_awards_audit_award ON awards_audit(award_id);
CREATE INDEX idx_awards_audit_timestamp ON awards_audit(operation_timestamp);
CREATE INDEX idx_awards_audit_user ON awards_audit(operation_user_id);
```

### 3.3 Hierarchical Data Pattern (Organizations)

For organizational hierarchy (University → Faculty → Department):

```sql
-- Self-referencing hierarchy pattern
CREATE TABLE organizations (
    org_id BIGSERIAL,
    
    -- Hierarchy
    parent_org_id BIGINT,
    org_type VARCHAR(20) NOT NULL,
    hierarchy_path LTREE,  -- PostgreSQL ltree extension for efficient queries
    depth INTEGER NOT NULL DEFAULT 0,
    
    -- Business attributes
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50),
    
    -- Standard audit columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 1,
    
    CONSTRAINT pk_organizations PRIMARY KEY (org_id),
    CONSTRAINT fk_organizations_parent FOREIGN KEY (parent_org_id) 
        REFERENCES organizations(org_id),
    CONSTRAINT ck_organizations_type CHECK (org_type IN ('UNIVERSITY', 'FACULTY', 'DEPARTMENT'))
);

-- Enable ltree extension (run once)
CREATE EXTENSION IF NOT EXISTS ltree;

-- Index for hierarchy queries
CREATE INDEX idx_organizations_parent ON organizations(parent_org_id);
CREATE INDEX gist_organizations_path ON organizations USING GIST (hierarchy_path);
```

### 3.4 Temporal Data Pattern (Role Validity)

For time-bounded data like role assignments:

```sql
-- Temporal validity pattern
CREATE TABLE user_roles (
    user_role_id BIGSERIAL,
    user_id BIGINT NOT NULL,
    role_type VARCHAR(30) NOT NULL,
    organization_id BIGINT NOT NULL,
    
    -- Temporal bounds
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,  -- NULL means currently valid
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    
    CONSTRAINT pk_user_roles PRIMARY KEY (user_role_id),
    CONSTRAINT fk_user_roles_users FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_user_roles_orgs FOREIGN KEY (organization_id) REFERENCES organizations(org_id),
    CONSTRAINT ck_user_roles_dates CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_user_roles_type CHECK (
        role_type IN ('EMPLOYEE', 'FACULTY_SECRETARY', 'DEAN', 'RECTOR_SECRETARY', 'RECTOR', 'SYSTEM_ADMIN', 'GDPR_OFFICER')
    )
);

-- Index for current role lookup
CREATE INDEX idx_user_roles_user_current ON user_roles(user_id) 
    WHERE valid_to IS NULL OR valid_to >= CURRENT_DATE;
```

### 3.5 Document/File Reference Pattern

For storing file metadata (actual files in object storage):

```sql
-- File reference pattern (metadata only, files in object storage)
CREATE TABLE documents (
    document_id BIGSERIAL,
    
    -- Parent references
    award_id BIGINT,
    request_id BIGINT,
    
    -- File metadata
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    checksum_sha256 VARCHAR(64),
    
    -- Storage reference (S3/Azure Blob path)
    storage_bucket VARCHAR(100) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    storage_url VARCHAR(2048),
    
    -- AI Processing results
    parsed_metadata JSONB,
    confidence_score NUMERIC(5,4),  -- 0.0000 to 1.0000
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by BIGINT NOT NULL,
    
    CONSTRAINT pk_documents PRIMARY KEY (document_id),
    CONSTRAINT fk_documents_awards FOREIGN KEY (award_id) REFERENCES awards(award_id),
    CONSTRAINT ck_documents_status CHECK (
        processing_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'MANUAL_REVIEW')
    )
);

-- Index for JSONB queries
CREATE INDEX gin_documents_metadata ON documents USING GIN (parsed_metadata);
```

---

## 4. Referential Integrity Standards

### 4.1 Foreign Key Policies

| **Relationship Type** | **ON DELETE** | **ON UPDATE** | **Use Case** |
|-----------------------|---------------|---------------|--------------|
| Strong Ownership | `CASCADE` | `CASCADE` | Child cannot exist without parent |
| Weak Reference | `SET NULL` | `CASCADE` | Reference is optional |
| Prevent Orphans | `RESTRICT` | `CASCADE` | Require explicit handling |
| Audit Preservation | `NO ACTION` | `CASCADE` | Keep history even if parent deleted |

```sql
-- Strong ownership: Documents belong to Awards
ALTER TABLE documents
    ADD CONSTRAINT fk_documents_awards 
    FOREIGN KEY (award_id) REFERENCES awards(award_id)
    ON DELETE CASCADE ON UPDATE CASCADE;

-- Weak reference: User's current reviewer can be cleared
ALTER TABLE award_requests
    ADD CONSTRAINT fk_requests_reviewer 
    FOREIGN KEY (current_reviewer_id) REFERENCES users(user_id)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- Prevent orphans: Can't delete organization with users
ALTER TABLE users
    ADD CONSTRAINT fk_users_organizations 
    FOREIGN KEY (organization_id) REFERENCES organizations(org_id)
    ON DELETE RESTRICT ON UPDATE CASCADE;
```

### 4.2 Null Handling Standards

| **Scenario** | **Nullability** | **Rationale** |
|--------------|-----------------|---------------|
| Primary Keys | `NOT NULL` | Always required |
| Required Business Data | `NOT NULL` | Data integrity |
| Optional Business Data | Allow `NULL` | Genuine absence of value |
| Foreign Keys (required) | `NOT NULL` | Relationship mandatory |
| Foreign Keys (optional) | Allow `NULL` | Relationship optional |
| Timestamps (created) | `NOT NULL` | Always track creation |
| Timestamps (optional events) | Allow `NULL` | Event may not occur |
| Booleans | `NOT NULL DEFAULT` | Avoid three-valued logic |

---

## 5. Performance Design Patterns

### 5.1 Indexing Strategy Guidelines

| **Query Pattern** | **Index Strategy** | **Example** |
|-------------------|-------------------|-------------|
| Exact lookup | B-tree on column | `idx_users_email` |
| Range queries | B-tree on column | `idx_awards_date` |
| Multiple conditions | Composite index (selective first) | `idx_awards_user_status` |
| Frequent subset | Partial index | `idx_awards_pending` |
| JSON field queries | GIN on JSONB column | `gin_documents_metadata` |
| Full-text search | GIN with tsvector | `ftidx_awards_title` |
| Case-insensitive | Expression index | `idx_users_lower_email` |
| Hierarchy traversal | GiST on ltree | `gist_orgs_path` |

### 5.2 Partitioning Strategy

For high-volume tables (audit_logs, notifications), use table partitioning:

```sql
-- Range partitioning by date for audit logs
CREATE TABLE audit_logs (
    log_id BIGSERIAL,
    user_id BIGINT,
    action_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    old_values JSONB,
    new_values JSONB,
    ip_address INET,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_audit_logs PRIMARY KEY (log_id, created_at)
) PARTITION BY RANGE (created_at);

-- Monthly partitions (created via migration scripts during development)
-- CREATE TABLE audit_logs_2025_01 PARTITION OF audit_logs
--     FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
```

### 5.3 Connection Pool Sizing

Recommended HikariCP configuration for PostgreSQL:

```yaml
# Application configuration (for reference during development)
spring:
  datasource:
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      idle-timeout: 300000        # 5 minutes
      max-lifetime: 1200000       # 20 minutes
      connection-timeout: 20000    # 20 seconds
      leak-detection-threshold: 60000  # 1 minute
```

---

## 6. GDPR Compliance Patterns

### 6.1 Data Classification in Schema

```sql
-- Column comments for data classification
COMMENT ON COLUMN users.email_address IS 'GDPR: Personal Data - Contact Information';
COMMENT ON COLUMN users.first_name IS 'GDPR: Personal Data - Identity';
COMMENT ON COLUMN users.last_name IS 'GDPR: Personal Data - Identity';
COMMENT ON COLUMN users.password_hash IS 'GDPR: Confidential - Security Credential (hashed)';
COMMENT ON COLUMN audit_logs.ip_address IS 'GDPR: Personal Data - Technical Identifier';
```

### 6.2 Right to Erasure Support

Design pattern for supporting GDPR Article 17 (Right to Erasure):

```sql
-- Anonymization function (strategy pattern)
-- Awards are anonymized, not deleted (public record value)
-- Personal data is removed while preserving achievement record

-- Example anonymization approach (implemented via application layer):
-- UPDATE users SET 
--     email_address = 'deleted_' || user_id || '@anonymized.local',
--     first_name = 'DELETED',
--     last_name = 'USER',
--     password_hash = 'DELETED',
--     account_status = 'DELETED',
--     deleted_at = CURRENT_TIMESTAMP
-- WHERE user_id = ?;
```

### 6.3 Consent Tracking Pattern

```sql
-- Granular consent tracking
CREATE TABLE consent_records (
    consent_id BIGSERIAL,
    user_id BIGINT NOT NULL,
    
    -- Consent details
    consent_type VARCHAR(50) NOT NULL,
    consent_version VARCHAR(20) NOT NULL,
    
    -- State tracking
    is_granted BOOLEAN NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE,
    withdrawn_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit
    ip_address INET,
    user_agent VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_consent_records PRIMARY KEY (consent_id),
    CONSTRAINT fk_consent_users FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT ck_consent_type CHECK (
        consent_type IN ('DATA_PROCESSING', 'PUBLIC_VISIBILITY', 'EMAIL_NOTIFICATIONS', 'SMS_NOTIFICATIONS', 'ANALYTICS')
    )
);

-- Index for user consent lookup
CREATE INDEX idx_consent_user_type ON consent_records(user_id, consent_type);
```

---

## 7. Schema Documentation Standards

### 7.1 Required Comments

All database objects must have descriptive comments:

```sql
-- Table comments
COMMENT ON TABLE users IS 'System users including employees and administrators. Central identity entity for the award monitoring system.';
COMMENT ON TABLE awards IS 'Award records representing professional achievements and recognition. Core business entity.';
COMMENT ON TABLE award_requests IS 'Workflow requests for award approval. Tracks multi-level approval process.';

-- Column comments (especially for non-obvious columns)
COMMENT ON COLUMN awards.impact_score IS 'Calculated score (0-100) representing award significance based on category, level, and organization.';
COMMENT ON COLUMN documents.confidence_score IS 'AI model confidence (0.0-1.0) for metadata extraction accuracy.';
COMMENT ON COLUMN award_requests.current_level IS 'Current position in approval workflow: FACULTY_SECRETARY → DEAN → RECTOR_SECRETARY → RECTOR';

-- Index comments
COMMENT ON INDEX idx_awards_status_pending IS 'Partial index for pending awards query optimization. Used by reviewer dashboard.';
```

### 7.2 Schema Version Tracking

```sql
-- Schema version table (managed by Flyway)
-- flyway_schema_history table is created automatically

-- Additional metadata table for custom tracking
CREATE TABLE schema_metadata (
    metadata_id SERIAL PRIMARY KEY,
    schema_version VARCHAR(50) NOT NULL,
    description TEXT,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_by VARCHAR(100),
    checksum VARCHAR(64)
);
```

---

## 8. Environment-Specific Considerations

### 8.1 Development Environment

- Use Docker PostgreSQL 16 container
- Seed data via Flyway repeatable migrations
- Enable `log_statement = 'all'` for query debugging
- Use `pgAdmin` or `DBeaver` for schema visualization

### 8.2 Testing Environment

- Use Testcontainers for integration tests
- Fresh database per test class/suite
- Disable foreign key checks for unit tests only
- Use in-memory H2 only for simple repository tests

### 8.3 Production Environment

- Connection pooling via HikariCP
- SSL/TLS required for all connections
- Read replicas for reporting queries
- Automated backups with point-in-time recovery
- Monitoring via pg_stat_statements

---

## 9. Review Checklist

Before creating any database object, verify:

- [ ] **Naming**: Follows conventions in Section 1
- [ ] **Data Types**: Uses standard types from Section 2
- [ ] **Constraints**: All constraints properly named
- [ ] **Indexes**: Appropriate indexes for query patterns
- [ ] **Audit Columns**: Includes created_at, updated_at, version
- [ ] **Comments**: Table and key columns documented
- [ ] **GDPR**: Personal data columns identified
- [ ] **Foreign Keys**: Proper ON DELETE/UPDATE policies
- [ ] **Null Handling**: Explicit NOT NULL or NULL decision

---

## Appendix A: Quick Reference Card

### Naming Patterns Summary

| Object | Pattern | Example |
|--------|---------|---------|
| Table | `plural_snake_case` | `award_categories` |
| Column | `snake_case` | `first_name` |
| Primary Key | `table_id` | `user_id` |
| Foreign Key | `referenced_id` | `organization_id` |
| PK Constraint | `pk_table` | `pk_users` |
| FK Constraint | `fk_child_parent` | `fk_awards_users` |
| Unique Constraint | `uk_table_column` | `uk_users_email` |
| Check Constraint | `ck_table_column` | `ck_users_status` |
| Index | `idx_table_column` | `idx_awards_date` |
| Partial Index | `idx_table_col_cond` | `idx_awards_pending` |
| View | `vw_purpose` | `vw_active_awards` |
| Function | `fn_purpose` | `fn_calc_score` |
| Trigger | `trg_table_event` | `trg_users_audit` |

---

*Document Version: 1.0*  
*Classification: Internal*  
*Phase: 9 - Data Architecture & Database Design*  
*Author: Stefan Kostyk*


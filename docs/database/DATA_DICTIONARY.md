# Data Dictionary
## Award Monitoring & Tracking System

> **Phase 9 Deliverable**: Data Architecture & Database Design  
> **Document Version**: 1.0  
> **Last Updated**: December 2025  
> **Author**: Stefan Kostyk  
> **Total Entities**: 14  
> **Classification**: Internal

---

## Executive Summary

This Data Dictionary provides comprehensive documentation for all database entities in the Award Monitoring & Tracking System. Each entity includes attribute definitions, data types, constraints, business rules, and relationships. This document serves as the authoritative reference for database schema implementation during the development phase.

### Entity Domains

| **Domain** | **Entities** | **Purpose** |
|------------|--------------|-------------|
| **User Domain** | `users`, `user_roles`, `organizations` | Identity, access control, organizational structure |
| **Award Domain** | `awards`, `award_categories`, `documents` | Core business entities for award management |
| **Workflow Domain** | `award_requests`, `review_decisions` | Multi-level approval workflow tracking |
| **Compliance Domain** | `audit_logs`, `consent_records` | GDPR compliance, audit trails |
| **Notification Domain** | `notifications`, `notification_preferences` | Communication and user preferences |

---

## 1. User Domain

### 1.1 Entity: `users`

**Description**: Central identity entity representing all system users including employees, administrators, and approvers. Contains authentication credentials and profile information.

**Business Rules**:
- Email address must be unique across all users
- Users belong to exactly one primary organization
- Status transitions: PENDING → ACTIVE ↔ INACTIVE → SUSPENDED → RETIRED/MEMORIAL
- Password hash uses BCrypt algorithm with strength 12

| **Column** | **Data Type** | **Nullable** | **Default** | **Constraints** | **Description** |
|------------|---------------|--------------|-------------|-----------------|-----------------|
| `user_id` | `BIGSERIAL` | NO | Auto | PK | Unique user identifier |
| `email_address` | `VARCHAR(255)` | NO | - | UK | User's email (login credential) |
| `first_name` | `VARCHAR(100)` | NO | - | - | User's first name (GDPR: Personal Data) |
| `last_name` | `VARCHAR(100)` | NO | - | - | User's last name (GDPR: Personal Data) |
| `password_hash` | `VARCHAR(255)` | NO | - | - | BCrypt hashed password (GDPR: Confidential) |
| `account_status` | `VARCHAR(20)` | NO | `'PENDING'` | CK | Account state (see enum below) |
| `organization_id` | `BIGINT` | NO | - | FK→organizations | Primary organizational unit |
| `last_login_at` | `TIMESTAMPTZ` | YES | - | - | Last successful authentication timestamp |
| `created_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Last modification timestamp |
| `version` | `BIGINT` | NO | `1` | - | Optimistic locking version |

**Status Values** (`account_status`):
| Value | Description | Transitions To |
|-------|-------------|----------------|
| `PENDING` | Awaiting email verification | ACTIVE |
| `ACTIVE` | Normal active state | INACTIVE, SUSPENDED |
| `INACTIVE` | Temporarily disabled | ACTIVE, SUSPENDED |
| `SUSPENDED` | Administrative hold | ACTIVE, RETIRED |
| `RETIRED` | Former employee (read-only access) | MEMORIAL |
| `MEMORIAL` | Deceased (awards preserved, no access) | - |

**Indexes**:
- `pk_users` - Primary key on `user_id`
- `uk_users_email` - Unique on `email_address`
- `idx_users_organization` - B-tree on `organization_id`
- `idx_users_status` - B-tree on `account_status`
- `idx_users_lower_email` - Expression index on `LOWER(email_address)`

**Relationships**:
- BELONGS TO `organizations` (N:1) via `organization_id`
- HAS MANY `user_roles` (1:N)
- HAS MANY `awards` (1:N)
- HAS MANY `audit_logs` (1:N)
- HAS MANY `consent_records` (1:N)
- HAS MANY `notifications` (1:N)

---

### 1.2 Entity: `user_roles`

**Description**: Role assignments linking users to specific roles within organizational contexts. Supports temporal validity for role transitions and history tracking.

**Business Rules**:
- Users can have multiple roles across different organizations
- Role validity period cannot have `valid_to` before `valid_from`
- Current roles are those where `valid_to IS NULL OR valid_to >= CURRENT_DATE`
- Role hierarchy determines approval authority

| **Column** | **Data Type** | **Nullable** | **Default** | **Constraints** | **Description** |
|------------|---------------|--------------|-------------|-----------------|-----------------|
| `user_role_id` | `BIGSERIAL` | NO | Auto | PK | Unique role assignment identifier |
| `user_id` | `BIGINT` | NO | - | FK→users | User receiving the role |
| `role_type` | `VARCHAR(30)` | NO | - | CK | Role type (see enum below) |
| `organization_id` | `BIGINT` | NO | - | FK→organizations | Organizational scope of role |
| `valid_from` | `DATE` | NO | `CURRENT_DATE` | - | Role effective start date |
| `valid_to` | `DATE` | YES | - | CK | Role effective end date (NULL=current) |
| `created_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Record creation timestamp |
| `created_by` | `BIGINT` | YES | - | FK→users | Admin who assigned the role |

**Role Types** (`role_type`):
| Value | Description | Permissions Level |
|-------|-------------|-------------------|
| `EMPLOYEE` | Standard employee | Submit own awards |
| `FACULTY_SECRETARY` | Faculty administrative secretary | First-level approval, faculty reports |
| `DEAN` | Faculty dean | Second-level approval, faculty management |
| `RECTOR_SECRETARY` | Rector's office secretary | Third-level approval, university reports |
| `RECTOR` | University rector | Final approval authority |
| `SYSTEM_ADMIN` | Technical administrator | System configuration, user management |
| `GDPR_OFFICER` | Data protection officer | Privacy compliance, data exports |

**Indexes**:
- `pk_user_roles` - Primary key on `user_role_id`
- `idx_user_roles_user` - B-tree on `user_id`
- `idx_user_roles_org` - B-tree on `organization_id`
- `idx_user_roles_current` - Partial index for active roles

**Relationships**:
- BELONGS TO `users` (N:1) via `user_id`
- BELONGS TO `organizations` (N:1) via `organization_id`

---

### 1.3 Entity: `organizations`

**Description**: Hierarchical organizational structure representing the university, faculties, and departments. Self-referencing for parent-child relationships.

**Business Rules**:
- University is the root organization (no parent)
- Faculties belong to University
- Departments belong to Faculties
- Organization hierarchy determines approval workflow routing
- Organization deletion requires no dependent users

| **Column** | **Data Type** | **Nullable** | **Default** | **Constraints** | **Description** |
|------------|---------------|--------------|-------------|-----------------|-----------------|
| `org_id` | `BIGSERIAL` | NO | Auto | PK | Unique organization identifier |
| `name` | `VARCHAR(255)` | NO | - | - | Organization full name |
| `name_uk` | `VARCHAR(255)` | YES | - | - | Ukrainian name |
| `code` | `VARCHAR(50)` | YES | - | UK | Internal organization code |
| `org_type` | `VARCHAR(20)` | NO | - | CK | Organization level type |
| `parent_org_id` | `BIGINT` | YES | - | FK→organizations | Parent organization (NULL for root) |
| `hierarchy_path` | `LTREE` | YES | - | - | Materialized path for queries |
| `depth` | `INTEGER` | NO | `0` | - | Hierarchy depth level |
| `is_active` | `BOOLEAN` | NO | `TRUE` | - | Active status flag |
| `created_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Last modification timestamp |

**Organization Types** (`org_type`):
| Value | Description | Depth |
|-------|-------------|-------|
| `UNIVERSITY` | Top-level institution | 0 |
| `FACULTY` | Academic faculty | 1 |
| `DEPARTMENT` | Academic department | 2 |

**Indexes**:
- `pk_organizations` - Primary key on `org_id`
- `uk_organizations_code` - Unique on `code` (where not null)
- `idx_organizations_parent` - B-tree on `parent_org_id`
- `gist_organizations_path` - GiST on `hierarchy_path` for tree queries

**Relationships**:
- BELONGS TO `organizations` (N:1, self-reference) via `parent_org_id`
- HAS MANY `organizations` (1:N, children)
- HAS MANY `users` (1:N)
- HAS MANY `user_roles` (1:N)

---

## 2. Award Domain

### 2.1 Entity: `awards`

**Description**: Core business entity representing professional achievements, recognition, and awards received by employees. Contains both public award information and metadata.

**Business Rules**:
- Each award belongs to exactly one user (recipient)
- Awards must be categorized via `category_id`
- Award status follows defined workflow progression
- Verified awards display a verification badge
- Impact score calculated based on category level and awarding organization
- Awards are publicly visible (core system feature for transparency)

| **Column** | **Data Type** | **Nullable** | **Default** | **Constraints** | **Description** |
|------------|---------------|--------------|-------------|-----------------|-----------------|
| `award_id` | `BIGSERIAL` | NO | Auto | PK | Unique award identifier |
| `user_id` | `BIGINT` | NO | - | FK→users | Award recipient |
| `category_id` | `BIGINT` | NO | - | FK→award_categories | Award classification |
| `title` | `VARCHAR(500)` | NO | - | - | Award title/name |
| `title_uk` | `VARCHAR(500)` | YES | - | - | Ukrainian title |
| `description` | `TEXT` | YES | - | - | Detailed description |
| `description_uk` | `TEXT` | YES | - | - | Ukrainian description |
| `awarding_organization` | `VARCHAR(255)` | NO | - | - | Organization that granted the award |
| `award_date` | `DATE` | NO | - | - | Date award was granted |
| `status` | `VARCHAR(20)` | NO | `'DRAFT'` | CK | Current workflow status |
| `verification_badge` | `BOOLEAN` | NO | `FALSE` | - | Verified by supporting documents |
| `impact_score` | `INTEGER` | YES | - | CK: 0-100 | Calculated significance score |
| `external_url` | `VARCHAR(2048)` | YES | - | - | Link to external verification |
| `created_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Last modification timestamp |
| `version` | `BIGINT` | NO | `1` | - | Optimistic locking version |

**Status Values** (`status`):
| Value | Description | Transitions To |
|-------|-------------|----------------|
| `DRAFT` | Initial state, being edited | PENDING |
| `PENDING` | Submitted, awaiting approval | APPROVED, REJECTED |
| `APPROVED` | Approved through workflow | ARCHIVED |
| `REJECTED` | Rejected during workflow | DRAFT (resubmission) |
| `ARCHIVED` | Historical record, no longer active | - |

**Impact Score Calculation** (0-100):
- Base score from category level (Department: 20, Faculty: 40, University: 60, National: 80, International: 100)
- Modifiers based on awarding organization prestige

**Indexes**:
- `pk_awards` - Primary key on `award_id`
- `idx_awards_user` - B-tree on `user_id`
- `idx_awards_category` - B-tree on `category_id`
- `idx_awards_status` - B-tree on `status`
- `idx_awards_date` - B-tree on `award_date DESC`
- `idx_awards_status_pending` - Partial index where `status = 'PENDING'`
- `ftidx_awards_title` - Full-text on `title` for search

**Relationships**:
- BELONGS TO `users` (N:1) via `user_id`
- BELONGS TO `award_categories` (N:1) via `category_id`
- HAS MANY `documents` (1:N)
- HAS ONE `award_requests` (1:1)

---

### 2.2 Entity: `award_categories`

**Description**: Classification system for awards defining categories, recognition levels, and approval requirements.

**Business Rules**:
- Categories are hierarchical (optional parent category)
- Recognition level determines minimum approval authority
- Categories with higher levels require escalation in workflow
- System-defined categories cannot be deleted

| **Column** | **Data Type** | **Nullable** | **Default** | **Constraints** | **Description** |
|------------|---------------|--------------|-------------|-----------------|-----------------|
| `category_id` | `BIGSERIAL` | NO | Auto | PK | Unique category identifier |
| `name` | `VARCHAR(100)` | NO | - | UK | Category name (English) |
| `name_uk` | `VARCHAR(100)` | YES | - | - | Category name (Ukrainian) |
| `description` | `TEXT` | YES | - | - | Category description |
| `level` | `VARCHAR(20)` | NO | - | CK | Recognition level |
| `parent_category_id` | `BIGINT` | YES | - | FK→award_categories | Parent category |
| `sort_order` | `INTEGER` | NO | `0` | - | Display order |
| `is_active` | `BOOLEAN` | NO | `TRUE` | - | Active status |
| `is_system` | `BOOLEAN` | NO | `FALSE` | - | System-defined (non-deletable) |
| `created_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Last modification timestamp |

**Recognition Levels** (`level`):
| Value | Description | Minimum Approval Level |
|-------|-------------|------------------------|
| `DEPARTMENT` | Department-level recognition | Faculty Secretary |
| `FACULTY` | Faculty-level recognition | Dean |
| `UNIVERSITY` | University-level recognition | Rector |
| `NATIONAL` | National-level recognition | Rector |
| `INTERNATIONAL` | International recognition | Rector |

**Indexes**:
- `pk_award_categories` - Primary key on `category_id`
- `uk_award_categories_name` - Unique on `name`
- `idx_award_categories_level` - B-tree on `level`
- `idx_award_categories_parent` - B-tree on `parent_category_id`

**Relationships**:
- BELONGS TO `award_categories` (N:1, self-reference) via `parent_category_id`
- HAS MANY `award_categories` (1:N, children)
- HAS MANY `awards` (1:N)

---

### 2.3 Entity: `documents`

**Description**: File metadata for supporting documents attached to awards. Actual files stored in object storage (S3/Azure Blob). Includes AI-parsed metadata results.

**Business Rules**:
- Documents belong to either an award or a request (or both)
- Maximum file size: 10MB per document
- Allowed file types: PDF, JPG, PNG, WEBP
- AI parsing extracts metadata from scanned certificates
- Confidence score below 0.7 triggers manual review
- Files stored in object storage, only metadata in database

| **Column** | **Data Type** | **Nullable** | **Default** | **Constraints** | **Description** |
|------------|---------------|--------------|-------------|-----------------|-----------------|
| `document_id` | `BIGSERIAL` | NO | Auto | PK | Unique document identifier |
| `award_id` | `BIGINT` | YES | - | FK→awards | Associated award |
| `request_id` | `BIGINT` | YES | - | FK→award_requests | Associated request |
| `file_name` | `VARCHAR(255)` | NO | - | - | Original file name |
| `file_type` | `VARCHAR(50)` | NO | - | CK | File extension |
| `mime_type` | `VARCHAR(100)` | YES | - | - | MIME content type |
| `file_size` | `BIGINT` | NO | - | CK: ≤10485760 | File size in bytes |
| `storage_bucket` | `VARCHAR(100)` | NO | - | - | Object storage bucket name |
| `storage_key` | `VARCHAR(500)` | NO | - | - | Object storage key/path |
| `storage_url` | `VARCHAR(2048)` | YES | - | - | Pre-signed access URL |
| `checksum_sha256` | `VARCHAR(64)` | YES | - | - | File integrity checksum |
| `parsed_metadata` | `JSONB` | YES | - | - | AI-extracted metadata |
| `confidence_score` | `NUMERIC(5,4)` | YES | - | CK: 0-1 | AI confidence (0.0000-1.0000) |
| `processing_status` | `VARCHAR(20)` | NO | `'PENDING'` | CK | AI processing state |
| `processed_at` | `TIMESTAMPTZ` | YES | - | - | AI processing completion time |
| `uploaded_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Upload timestamp |
| `uploaded_by` | `BIGINT` | NO | - | FK→users | Uploader user ID |

**Processing Status Values** (`processing_status`):
| Value | Description | Next States |
|-------|-------------|-------------|
| `PENDING` | Awaiting AI processing | PROCESSING |
| `PROCESSING` | Currently being processed | COMPLETED, FAILED |
| `COMPLETED` | Successfully processed | VERIFIED |
| `FAILED` | Processing error | PENDING (retry) |
| `MANUAL_REVIEW` | Low confidence, needs human review | VERIFIED |
| `VERIFIED` | Human-verified metadata | - |

**Parsed Metadata JSONB Structure**:
```json
{
  "extracted_title": "Certificate of Achievement",
  "extracted_date": "2024-03-15",
  "extracted_organization": "Ministry of Education",
  "extracted_recipient": "John Doe",
  "language_detected": "uk",
  "document_type": "certificate",
  "extraction_version": "1.0"
}
```

**Indexes**:
- `pk_documents` - Primary key on `document_id`
- `idx_documents_award` - B-tree on `award_id`
- `idx_documents_request` - B-tree on `request_id`
- `idx_documents_status` - B-tree on `processing_status`
- `gin_documents_metadata` - GIN on `parsed_metadata`

**Relationships**:
- BELONGS TO `awards` (N:1) via `award_id`
- BELONGS TO `award_requests` (N:1) via `request_id`
- BELONGS TO `users` (N:1) via `uploaded_by`

---

## 3. Workflow Domain

### 3.1 Entity: `award_requests`

**Description**: Workflow tracking entity for award approval process. Each award has exactly one request that tracks its journey through the multi-level approval workflow.

**Business Rules**:
- One-to-one relationship with awards (each award has exactly one request)
- Workflow levels: Faculty Secretary → Dean → Rector Secretary → Rector
- Escalation based on award category recognition level
- Deadlines calculated based on SLA requirements
- Request expiration if not processed within deadline

| **Column** | **Data Type** | **Nullable** | **Default** | **Constraints** | **Description** |
|------------|---------------|--------------|-------------|-----------------|-----------------|
| `request_id` | `BIGSERIAL` | NO | Auto | PK | Unique request identifier |
| `award_id` | `BIGINT` | NO | - | FK→awards, UK | Associated award (unique) |
| `submitter_id` | `BIGINT` | NO | - | FK→users | User who submitted |
| `status` | `VARCHAR(20)` | NO | `'SUBMITTED'` | CK | Current request status |
| `current_reviewer_id` | `BIGINT` | YES | - | FK→users | Currently assigned reviewer |
| `current_level` | `VARCHAR(30)` | NO | - | CK | Current approval level |
| `submitted_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Submission timestamp |
| `deadline` | `TIMESTAMPTZ` | YES | - | - | Processing deadline |
| `completed_at` | `TIMESTAMPTZ` | YES | - | - | Final decision timestamp |
| `rejection_reason` | `TEXT` | YES | - | - | Reason if rejected |
| `created_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Last modification timestamp |

**Request Status Values** (`status`):
| Value | Description | Transitions To |
|-------|-------------|----------------|
| `SUBMITTED` | Initial submission | IN_REVIEW |
| `IN_REVIEW` | Currently being reviewed | ESCALATED, APPROVED, REJECTED, RETURNED |
| `ESCALATED` | Moved to higher approval level | IN_REVIEW |
| `APPROVED` | Final approval granted | - |
| `REJECTED` | Final rejection | - |
| `RETURNED` | Returned for corrections | SUBMITTED |
| `EXPIRED` | Deadline passed without decision | - |

**Approval Levels** (`current_level`):
| Value | Description | Next Level |
|-------|-------------|------------|
| `FACULTY_SECRETARY` | Initial review | DEAN |
| `DEAN` | Faculty-level approval | RECTOR_SECRETARY |
| `RECTOR_SECRETARY` | Rector's office review | RECTOR |
| `RECTOR` | Final approval authority | - |

**Indexes**:
- `pk_award_requests` - Primary key on `request_id`
- `uk_award_requests_award` - Unique on `award_id`
- `idx_award_requests_status` - B-tree on `status`
- `idx_award_requests_reviewer` - B-tree on `current_reviewer_id`
- `idx_award_requests_deadline` - B-tree on `deadline`
- `idx_award_requests_active` - Partial index for active requests

**Relationships**:
- BELONGS TO `awards` (1:1) via `award_id`
- BELONGS TO `users` (N:1) via `submitter_id`
- BELONGS TO `users` (N:1) via `current_reviewer_id`
- HAS MANY `review_decisions` (1:N)
- HAS MANY `documents` (1:N)

---

### 3.2 Entity: `review_decisions`

**Description**: Individual approval/rejection decisions made at each level of the workflow. Provides complete audit trail of the approval process.

**Business Rules**:
- Each decision tied to one request and one reviewer
- Decision types: APPROVED, REJECTED, ESCALATED, RETURNED
- Decisions are immutable once recorded
- Comments required for rejections and returns
- Timestamp records exact decision moment

| **Column** | **Data Type** | **Nullable** | **Default** | **Constraints** | **Description** |
|------------|---------------|--------------|-------------|-----------------|-----------------|
| `decision_id` | `BIGSERIAL` | NO | Auto | PK | Unique decision identifier |
| `request_id` | `BIGINT` | NO | - | FK→award_requests | Associated request |
| `reviewer_id` | `BIGINT` | NO | - | FK→users | Decision maker |
| `decision` | `VARCHAR(20)` | NO | - | CK | Decision type |
| `level` | `VARCHAR(30)` | NO | - | CK | Approval level at decision |
| `comments` | `TEXT` | YES | - | - | Reviewer comments |
| `decided_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Decision timestamp |

**Decision Types** (`decision`):
| Value | Description | Request Status After |
|-------|-------------|---------------------|
| `APPROVED` | Approved at this level | ESCALATED or APPROVED (final) |
| `REJECTED` | Rejected at this level | REJECTED |
| `ESCALATED` | Manually escalated to higher level | ESCALATED |
| `RETURNED` | Returned to submitter for corrections | RETURNED |

**Indexes**:
- `pk_review_decisions` - Primary key on `decision_id`
- `idx_review_decisions_request` - B-tree on `request_id`
- `idx_review_decisions_reviewer` - B-tree on `reviewer_id`
- `idx_review_decisions_decided` - B-tree on `decided_at DESC`

**Relationships**:
- BELONGS TO `award_requests` (N:1) via `request_id`
- BELONGS TO `users` (N:1) via `reviewer_id`

---

## 4. Compliance Domain

### 4.1 Entity: `audit_logs`

**Description**: Comprehensive audit trail for all significant system actions. Required for GDPR compliance and security monitoring.

**Business Rules**:
- All data modifications logged automatically via triggers
- Logs are immutable (no UPDATE/DELETE allowed)
- Retention period: 7 years (per GDPR requirements)
- Partition by month for performance
- Stores both old and new values for change tracking

| **Column** | **Data Type** | **Nullable** | **Default** | **Constraints** | **Description** |
|------------|---------------|--------------|-------------|-----------------|-----------------|
| `log_id` | `BIGSERIAL` | NO | Auto | PK | Unique log identifier |
| `user_id` | `BIGINT` | YES | - | FK→users | User who performed action (NULL for system) |
| `action_type` | `VARCHAR(50)` | NO | - | - | Type of action performed |
| `entity_type` | `VARCHAR(50)` | NO | - | - | Entity/table affected |
| `entity_id` | `BIGINT` | YES | - | - | ID of affected record |
| `old_values` | `JSONB` | YES | - | - | Previous values (for UPDATE/DELETE) |
| `new_values` | `JSONB` | YES | - | - | New values (for INSERT/UPDATE) |
| `changed_fields` | `TEXT[]` | YES | - | - | List of changed column names |
| `ip_address` | `INET` | YES | - | - | Client IP address (GDPR: Technical ID) |
| `user_agent` | `VARCHAR(500)` | YES | - | - | Client user agent |
| `correlation_id` | `UUID` | YES | - | - | Request correlation for tracing |
| `created_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Log entry timestamp |

**Action Types** (common values):
- `CREATE`, `UPDATE`, `DELETE` - CRUD operations
- `LOGIN`, `LOGOUT`, `LOGIN_FAILED` - Authentication
- `PASSWORD_CHANGE`, `PASSWORD_RESET` - Security
- `CONSENT_GRANTED`, `CONSENT_WITHDRAWN` - Privacy
- `DATA_EXPORT`, `DATA_DELETE` - GDPR rights
- `APPROVAL`, `REJECTION` - Workflow decisions

**Indexes**:
- `pk_audit_logs` - Primary key on `(log_id, created_at)` (partitioned)
- `idx_audit_logs_user` - B-tree on `user_id`
- `idx_audit_logs_entity` - B-tree on `(entity_type, entity_id)`
- `idx_audit_logs_timestamp` - B-tree on `created_at DESC`
- `idx_audit_logs_action` - B-tree on `action_type`

**Partitioning**:
- Partitioned by `RANGE (created_at)`
- Monthly partitions for efficient retention management

**Relationships**:
- BELONGS TO `users` (N:1) via `user_id`

---

### 4.2 Entity: `consent_records`

**Description**: GDPR-compliant consent tracking for user data processing agreements. Records all consent grants and withdrawals.

**Business Rules**:
- Each consent type tracked separately per user
- Consent version tracked for policy updates
- Withdrawal timestamp preserved for audit
- Consent required before processing personal data
- Historical consent records never deleted

| **Column** | **Data Type** | **Nullable** | **Default** | **Constraints** | **Description** |
|------------|---------------|--------------|-------------|-----------------|-----------------|
| `consent_id` | `BIGSERIAL` | NO | Auto | PK | Unique consent record identifier |
| `user_id` | `BIGINT` | NO | - | FK→users | User granting consent |
| `consent_type` | `VARCHAR(50)` | NO | - | CK | Type of consent |
| `consent_version` | `VARCHAR(20)` | NO | - | - | Policy version consented to |
| `is_granted` | `BOOLEAN` | NO | - | - | Current consent state |
| `granted_at` | `TIMESTAMPTZ` | YES | - | - | Timestamp when granted |
| `withdrawn_at` | `TIMESTAMPTZ` | YES | - | - | Timestamp when withdrawn |
| `ip_address` | `INET` | YES | - | - | IP at time of action |
| `user_agent` | `VARCHAR(500)` | YES | - | - | Browser/client info |
| `created_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Record creation timestamp |

**Consent Types** (`consent_type`):
| Value | Description | Required |
|-------|-------------|----------|
| `DATA_PROCESSING` | General data processing agreement | Yes |
| `PUBLIC_VISIBILITY` | Allow public display of awards | Yes |
| `EMAIL_NOTIFICATIONS` | Receive email notifications | No |
| `SMS_NOTIFICATIONS` | Receive SMS notifications | No |
| `ANALYTICS` | Allow usage analytics | No |

**Indexes**:
- `pk_consent_records` - Primary key on `consent_id`
- `idx_consent_records_user` - B-tree on `user_id`
- `idx_consent_records_user_type` - Composite on `(user_id, consent_type)`
- `idx_consent_records_active` - Partial for active consents

**Relationships**:
- BELONGS TO `users` (N:1) via `user_id`

---

## 5. Notification Domain

### 5.1 Entity: `notifications`

**Description**: System notifications sent to users about award activities, workflow updates, and system alerts.

**Business Rules**:
- Notifications created by system events (not user-initiated)
- Unread notifications highlighted in UI
- Notifications respect user preferences
- Delivery via multiple channels (in-app, email, SMS)
- Retention period: 1 year

| **Column** | **Data Type** | **Nullable** | **Default** | **Constraints** | **Description** |
|------------|---------------|--------------|-------------|-----------------|-----------------|
| `notification_id` | `BIGSERIAL` | NO | Auto | PK | Unique notification identifier |
| `user_id` | `BIGINT` | NO | - | FK→users | Recipient user |
| `type` | `VARCHAR(50)` | NO | - | CK | Notification type |
| `title` | `VARCHAR(255)` | NO | - | - | Notification title |
| `message` | `TEXT` | NO | - | - | Full notification message |
| `is_read` | `BOOLEAN` | NO | `FALSE` | - | Read status |
| `channel` | `VARCHAR(20)` | NO | `'IN_APP'` | CK | Delivery channel |
| `reference_type` | `VARCHAR(50)` | YES | - | - | Related entity type |
| `reference_id` | `BIGINT` | YES | - | - | Related entity ID |
| `sent_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Send timestamp |
| `read_at` | `TIMESTAMPTZ` | YES | - | - | When marked as read |
| `expires_at` | `TIMESTAMPTZ` | YES | - | - | Notification expiry |

**Notification Types** (`type`):
| Value | Description | Trigger Event |
|-------|-------------|---------------|
| `AWARD_SUBMITTED` | New award submitted | Award submission |
| `AWARD_APPROVED` | Award approved | Final approval |
| `AWARD_REJECTED` | Award rejected | Rejection decision |
| `AWARD_RETURNED` | Award returned for corrections | Return decision |
| `REVIEW_ASSIGNED` | Assigned as reviewer | Workflow assignment |
| `DEADLINE_REMINDER` | Approaching deadline | Scheduled job |
| `SYSTEM_ALERT` | System announcement | Admin action |

**Notification Channels** (`channel`):
| Value | Description |
|-------|-------------|
| `IN_APP` | In-application notification |
| `EMAIL` | Email delivery |
| `SMS` | SMS delivery |
| `PUSH` | Push notification |

**Indexes**:
- `pk_notifications` - Primary key on `notification_id`
- `idx_notifications_user` - B-tree on `user_id`
- `idx_notifications_user_unread` - Partial for unread notifications
- `idx_notifications_sent` - B-tree on `sent_at DESC`
- `idx_notifications_reference` - Composite on `(reference_type, reference_id)`

**Relationships**:
- BELONGS TO `users` (N:1) via `user_id`

---

### 5.2 Entity: `notification_preferences`

**Description**: User preferences for notification delivery channels by event type.

**Business Rules**:
- Default preferences created on user registration
- Users can modify preferences at any time
- At least one channel must be enabled for mandatory notifications
- Preferences respected by notification service

| **Column** | **Data Type** | **Nullable** | **Default** | **Constraints** | **Description** |
|------------|---------------|--------------|-------------|-----------------|-----------------|
| `preference_id` | `BIGSERIAL` | NO | Auto | PK | Unique preference identifier |
| `user_id` | `BIGINT` | NO | - | FK→users | User owning preference |
| `event_type` | `VARCHAR(50)` | NO | - | CK | Notification event type |
| `email_enabled` | `BOOLEAN` | NO | `TRUE` | - | Email channel enabled |
| `sms_enabled` | `BOOLEAN` | NO | `FALSE` | - | SMS channel enabled |
| `in_app_enabled` | `BOOLEAN` | NO | `TRUE` | - | In-app channel enabled |
| `push_enabled` | `BOOLEAN` | NO | `FALSE` | - | Push notification enabled |
| `created_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | NO | `CURRENT_TIMESTAMP` | - | Last modification timestamp |

**Indexes**:
- `pk_notification_preferences` - Primary key on `preference_id`
- `uk_notification_preferences_user_event` - Unique on `(user_id, event_type)`
- `idx_notification_preferences_user` - B-tree on `user_id`

**Relationships**:
- BELONGS TO `users` (N:1) via `user_id`

---

## 6. Entity Relationship Summary

### 6.1 Relationship Matrix

| **Entity** | **Relationships** |
|------------|-------------------|
| `users` | → organizations (N:1), ← user_roles (1:N), ← awards (1:N), ← audit_logs (1:N), ← consent_records (1:N), ← notifications (1:N), ← notification_preferences (1:N) |
| `user_roles` | → users (N:1), → organizations (N:1) |
| `organizations` | → organizations (N:1, self), ← organizations (1:N), ← users (1:N), ← user_roles (1:N) |
| `awards` | → users (N:1), → award_categories (N:1), ← documents (1:N), ← award_requests (1:1) |
| `award_categories` | → award_categories (N:1, self), ← award_categories (1:N), ← awards (1:N) |
| `documents` | → awards (N:1), → award_requests (N:1), → users (N:1) |
| `award_requests` | → awards (1:1), → users (N:1, submitter), → users (N:1, reviewer), ← review_decisions (1:N), ← documents (1:N) |
| `review_decisions` | → award_requests (N:1), → users (N:1) |
| `audit_logs` | → users (N:1) |
| `consent_records` | → users (N:1) |
| `notifications` | → users (N:1) |
| `notification_preferences` | → users (N:1) |

### 6.2 Cardinality Summary

| **Relationship** | **Cardinality** | **Notes** |
|------------------|-----------------|-----------|
| User → Organization | N:1 | Required |
| User → Awards | 1:N | Employee's awards |
| Award → Request | 1:1 | Unique request per award |
| Request → Decisions | 1:N | Multiple decisions per level |
| Award → Documents | 1:N | Supporting documents |
| Organization → Organization | N:1 (self) | Hierarchy |

---

## Appendix A: Data Type Quick Reference

| **Business Concept** | **PostgreSQL Type** | **Java Type** |
|---------------------|--------------------:|---------------|
| Primary Key | `BIGSERIAL` | `Long` |
| Foreign Key | `BIGINT` | `Long` |
| Short String | `VARCHAR(n)` | `String` |
| Long Text | `TEXT` | `String` |
| Email | `VARCHAR(255)` | `String` |
| Status/Enum | `VARCHAR(30)` | `Enum` |
| Boolean | `BOOLEAN` | `Boolean` |
| Date | `DATE` | `LocalDate` |
| Timestamp | `TIMESTAMPTZ` | `Instant` |
| Money | `NUMERIC(19,4)` | `BigDecimal` |
| JSON Data | `JSONB` | `JsonNode` |
| IP Address | `INET` | `String` |
| File Size | `BIGINT` | `Long` |
| Score (0-100) | `INTEGER` | `Integer` |
| Confidence (0-1) | `NUMERIC(5,4)` | `BigDecimal` |

---

## Appendix B: Validation Rules Summary

| **Entity** | **Field** | **Validation Rule** |
|------------|-----------|---------------------|
| `users` | `email_address` | Valid email format, max 255 chars |
| `users` | `first_name` | Required, max 100 chars |
| `users` | `last_name` | Required, max 100 chars |
| `users` | `password_hash` | BCrypt format |
| `awards` | `title` | Required, max 500 chars |
| `awards` | `award_date` | Cannot be future date |
| `awards` | `impact_score` | Range 0-100 |
| `documents` | `file_size` | Max 10,485,760 bytes (10MB) |
| `documents` | `file_type` | One of: PDF, JPG, PNG, WEBP |
| `documents` | `confidence_score` | Range 0.0000-1.0000 |
| `consent_records` | `consent_version` | Semver format (e.g., "1.0.0") |

---

*Document Version: 1.0*  
*Classification: Internal*  
*Phase: 9 - Data Architecture & Database Design*  
*Author: Stefan Kostyk*


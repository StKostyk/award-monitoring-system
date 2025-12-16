# Data Architecture
## Award Monitoring & Tracking System

> **Phase 9 Deliverable**: Data Architecture & Database Design  
> **Document Version**: 1.0  
> **Last Updated**: December 2025  
> **Author**: Stefan Kostyk  
> **Classification**: Internal

---

## Executive Summary

This document defines the comprehensive data architecture for the Award Monitoring & Tracking System, covering data modeling principles, quality management, lineage tracking, and master data governance. The architecture ensures enterprise-grade data management while maintaining GDPR compliance and supporting the system's transparency objectives.

### Key Architecture Principles

| **Principle** | **Description** | **Implementation** |
|---------------|-----------------|-------------------|
| **Single Source of Truth** | Each data element has one authoritative source | Master data services, normalized schema |
| **Data Quality by Design** | Validation embedded at all layers | Bean Validation, database constraints, application rules |
| **Privacy by Default** | Personal data protected from creation | GDPR classification, encryption, access controls |
| **Auditability** | All changes tracked and traceable | Audit triggers, event sourcing, immutable logs |
| **Domain-Driven Design** | Data models reflect business domains | Bounded contexts, aggregate roots |

---

## 1. Data Modeling Approach

### 1.1 Domain-Driven Design Principles

The data model follows Domain-Driven Design (DDD) principles with clearly defined bounded contexts:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        BOUNDED CONTEXTS                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐      │
│  │   IDENTITY       │    │   AWARD          │    │   WORKFLOW       │      │
│  │   CONTEXT        │    │   CONTEXT        │    │   CONTEXT        │      │
│  ├──────────────────┤    ├──────────────────┤    ├──────────────────┤      │
│  │ • User           │    │ • Award          │    │ • AwardRequest   │      │
│  │ • UserRole       │◄───┤ • AwardCategory  │◄───┤ • ReviewDecision │      │
│  │ • Organization   │    │ • Document       │    │                  │      │
│  │                  │    │                  │    │                  │      │
│  └──────────────────┘    └──────────────────┘    └──────────────────┘      │
│           │                       │                       │                 │
│           ▼                       ▼                       ▼                 │
│  ┌──────────────────┐    ┌──────────────────┐                              │
│  │   COMPLIANCE     │    │   NOTIFICATION   │                              │
│  │   CONTEXT        │    │   CONTEXT        │                              │
│  ├──────────────────┤    ├──────────────────┤                              │
│  │ • AuditLog       │    │ • Notification   │                              │
│  │ • ConsentRecord  │    │ • NotifPreference│                              │
│  └──────────────────┘    └──────────────────┘                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Aggregate Root Identification

Each bounded context has identified aggregate roots that serve as consistency boundaries:

| **Bounded Context** | **Aggregate Root** | **Owned Entities** | **Consistency Scope** |
|---------------------|-------------------|-------------------|----------------------|
| Identity | `User` | UserRole | User with all roles |
| Identity | `Organization` | (self-contained) | Organization hierarchy |
| Award | `Award` | Document | Award with documents |
| Workflow | `AwardRequest` | ReviewDecision | Request with decisions |
| Compliance | `AuditLog` | (self-contained) | Individual log entry |
| Compliance | `ConsentRecord` | (self-contained) | Individual consent |
| Notification | `Notification` | (self-contained) | Individual notification |

### 1.3 Logical Data Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       LOGICAL DATA MODEL                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                        ┌─────────────────┐                                  │
│                        │  Organization   │                                  │
│                        │─────────────────│                                  │
│                        │ • University    │                                  │
│                        │ • Faculty       │◄────────────┐                    │
│                        │ • Department    │             │ (hierarchy)        │
│                        └────────┬────────┘             │                    │
│                                 │                      │                    │
│                                 │ belongs to           │                    │
│                                 ▼                      │                    │
│         ┌───────────────────────────────────────────────────────┐          │
│         │                      User                              │          │
│         │───────────────────────────────────────────────────────│          │
│         │ • email_address (identity)                             │          │
│         │ • first_name, last_name (PII)                         │          │
│         │ • account_status (lifecycle)                          │          │
│         └───────┬────────────────────┬─────────────────┬────────┘          │
│                 │                    │                 │                    │
│        has roles│           owns     │         has     │                    │
│                 ▼                    ▼                 ▼                    │
│  ┌──────────────────┐    ┌──────────────────┐  ┌───────────────┐          │
│  │    UserRole      │    │     Award        │  │ ConsentRecord │          │
│  │──────────────────│    │──────────────────│  │───────────────│          │
│  │ • EMPLOYEE       │    │ • title          │  │ • consent_type│          │
│  │ • FACULTY_SEC    │    │ • description    │  │ • is_granted  │          │
│  │ • DEAN           │    │ • award_date     │  │ • version     │          │
│  │ • RECTOR_SEC     │    │ • status         │  └───────────────┘          │
│  │ • RECTOR         │    │ • impact_score   │                              │
│  │ • SYSTEM_ADMIN   │    └────────┬─────────┘                              │
│  │ • GDPR_OFFICER   │             │                                        │
│  └──────────────────┘             │ has                                    │
│                                   ▼                                        │
│          ┌────────────────────────┴────────────────────────┐               │
│          │                                                  │               │
│          ▼                                                  ▼               │
│  ┌──────────────────┐                           ┌──────────────────┐       │
│  │    Document      │                           │   AwardRequest   │       │
│  │──────────────────│                           │──────────────────│       │
│  │ • file_metadata  │                           │ • status         │       │
│  │ • storage_ref    │                           │ • current_level  │       │
│  │ • ai_metadata    │                           │ • deadline       │       │
│  │ • confidence     │                           └────────┬─────────┘       │
│  └──────────────────┘                                    │                 │
│                                                          │ has             │
│                                                          ▼                 │
│                                              ┌──────────────────┐          │
│                                              │  ReviewDecision  │          │
│                                              │──────────────────│          │
│                                              │ • decision       │          │
│                                              │ • level          │          │
│                                              │ • comments       │          │
│                                              └──────────────────┘          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.4 Physical Model Mapping

| **Logical Entity** | **Physical Table(s)** | **Mapping Strategy** |
|-------------------|----------------------|---------------------|
| User | `users`, `user_roles` | One-to-many join |
| Organization | `organizations` | Self-referencing hierarchy |
| Award | `awards`, `documents` | One-to-many composition |
| AwardCategory | `award_categories` | Self-referencing hierarchy |
| AwardRequest | `award_requests`, `review_decisions` | One-to-many composition |
| AuditLog | `audit_logs` | Partitioned by date |
| ConsentRecord | `consent_records` | Simple entity |
| Notification | `notifications`, `notification_preferences` | Separate entities |

---

## 2. Data Quality Management

### 2.1 Data Quality Dimensions

| **Dimension** | **Definition** | **Measurement** | **Target** |
|---------------|----------------|-----------------|------------|
| **Accuracy** | Data correctly represents real-world | Verification rate | >99% |
| **Completeness** | Required fields populated | Null check rate | 100% required |
| **Consistency** | Same data across system | Cross-reference validation | 100% |
| **Timeliness** | Data current and available | Staleness metrics | <24h for awards |
| **Uniqueness** | No duplicate records | Duplicate detection | 0 duplicates |
| **Validity** | Data conforms to rules | Validation pass rate | 100% |

### 2.2 Validation Layer Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     VALIDATION LAYER ARCHITECTURE                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    PRESENTATION LAYER                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │   │
│  │  │  Angular Reactive Forms Validation                               │ │   │
│  │  │  • Required field indicators                                     │ │   │
│  │  │  • Format validation (email, date)                              │ │   │
│  │  │  • Length constraints                                           │ │   │
│  │  │  • Real-time feedback                                           │ │   │
│  │  └─────────────────────────────────────────────────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                     │                                        │
│                                     ▼                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                     API LAYER (Controller)                            │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │   │
│  │  │  Bean Validation (JSR-380)                                       │ │   │
│  │  │  • @NotNull, @NotBlank, @Size                                   │ │   │
│  │  │  • @Email, @Pattern                                             │ │   │
│  │  │  • @Valid for nested objects                                    │ │   │
│  │  │  • Custom validators (@ValidAwardDate)                          │ │   │
│  │  └─────────────────────────────────────────────────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                     │                                        │
│                                     ▼                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    SERVICE LAYER (Business Logic)                     │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │   │
│  │  │  Business Rule Validation                                        │ │   │
│  │  │  • State transition validation                                  │ │   │
│  │  │  • Cross-entity consistency                                     │ │   │
│  │  │  • Authorization checks                                         │ │   │
│  │  │  • Workflow rule enforcement                                    │ │   │
│  │  └─────────────────────────────────────────────────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                     │                                        │
│                                     ▼                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    REPOSITORY LAYER (JPA/Hibernate)                   │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │   │
│  │  │  JPA Validation                                                  │ │   │
│  │  │  • @Column(nullable = false)                                    │ │   │
│  │  │  • @Column(length = 255)                                        │ │   │
│  │  │  • Pre-persist/pre-update callbacks                             │ │   │
│  │  └─────────────────────────────────────────────────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                     │                                        │
│                                     ▼                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    DATABASE LAYER (PostgreSQL)                        │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │   │
│  │  │  Database Constraints (Last Line of Defense)                     │ │   │
│  │  │  • NOT NULL constraints                                         │ │   │
│  │  │  • CHECK constraints (enum values, ranges)                      │ │   │
│  │  │  • UNIQUE constraints                                           │ │   │
│  │  │  • FOREIGN KEY constraints                                      │ │   │
│  │  │  • Trigger-based validation                                     │ │   │
│  │  └─────────────────────────────────────────────────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 Entity Validation Rules

#### 2.3.1 User Entity Validation

```java
// Bean Validation Example (for documentation - actual implementation in development)
public class UserDTO {
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String emailAddress;
    
    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100, message = "First name must be 1-100 characters")
    @Pattern(regexp = "^[\\p{L}\\s'-]+$", message = "First name contains invalid characters")
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100, message = "Last name must be 1-100 characters")
    @Pattern(regexp = "^[\\p{L}\\s'-]+$", message = "Last name contains invalid characters")
    private String lastName;
    
    @NotNull(message = "Organization is required")
    private Long organizationId;
}
```

#### 2.3.2 Award Entity Validation

```java
// Award validation rules (for documentation)
public class AwardDTO {
    
    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 500, message = "Title must be 3-500 characters")
    private String title;
    
    @Size(max = 10000, message = "Description must not exceed 10000 characters")
    private String description;
    
    @NotBlank(message = "Awarding organization is required")
    @Size(max = 255, message = "Awarding organization must not exceed 255 characters")
    private String awardingOrganization;
    
    @NotNull(message = "Award date is required")
    @PastOrPresent(message = "Award date cannot be in the future")
    private LocalDate awardDate;
    
    @NotNull(message = "Category is required")
    private Long categoryId;
}
```

#### 2.3.3 Document Validation Rules

| **Field** | **Rule** | **Error Message** |
|-----------|----------|-------------------|
| `file_name` | Required, max 255 chars | "File name is required" |
| `file_type` | Must be PDF, JPG, PNG, or WEBP | "Unsupported file type" |
| `file_size` | Max 10MB (10,485,760 bytes) | "File size exceeds 10MB limit" |
| `mime_type` | Must match file_type | "MIME type mismatch" |
| `checksum` | SHA-256 format (64 hex chars) | "Invalid checksum format" |

### 2.4 Data Cleansing Rules

| **Data Type** | **Cleansing Rule** | **Implementation** |
|---------------|-------------------|-------------------|
| Email | Lowercase, trim whitespace | `email.toLowerCase().trim()` |
| Names | Trim whitespace, normalize Unicode | `Normalizer.normalize(name, NFD)` |
| Phone | Remove non-digits, format | `phone.replaceAll("[^0-9+]", "")` |
| URLs | Validate format, normalize | URL validation + encoding |
| Dates | Parse with locale awareness | `DateTimeFormatter` with locale |
| Text | Remove control characters | Regex replacement |

---

## 3. Data Lineage

### 3.1 Data Flow Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DATA LINEAGE: AWARD LIFECYCLE                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  EXTERNAL SOURCES                        SYSTEM PROCESSING                   │
│  ─────────────────                       ─────────────────                   │
│                                                                              │
│  ┌────────────────┐                                                         │
│  │ User Input     │───────────────────┐                                     │
│  │ (Web Form)     │                   │                                     │
│  └────────────────┘                   │                                     │
│                                       ▼                                     │
│  ┌────────────────┐         ┌─────────────────┐        ┌────────────────┐  │
│  │ Scanned        │────────►│ Award Service   │───────►│ awards         │  │
│  │ Certificate    │         │ (Validation)    │        │ (PostgreSQL)   │  │
│  └────────────────┘         └────────┬────────┘        └────────────────┘  │
│                                      │                                      │
│                                      │ triggers                             │
│                                      ▼                                      │
│                            ┌─────────────────┐        ┌────────────────┐   │
│                            │ AI Parser       │───────►│ documents      │   │
│                            │ (Metadata)      │        │ (parsed_meta)  │   │
│                            └────────┬────────┘        └────────────────┘   │
│                                     │                                       │
│                                     │ creates                               │
│                                     ▼                                       │
│                            ┌─────────────────┐        ┌────────────────┐   │
│                            │ Workflow Engine │───────►│ award_requests │   │
│                            │ (State Machine) │        │ review_decisions│  │
│                            └────────┬────────┘        └────────────────┘   │
│                                     │                                       │
│                                     │ all operations                        │
│                                     ▼                                       │
│                            ┌─────────────────┐        ┌────────────────┐   │
│                            │ Audit Service   │───────►│ audit_logs     │   │
│                            │ (Event Capture) │        │ (Immutable)    │   │
│                            └─────────────────┘        └────────────────┘   │
│                                                                              │
│  OUTPUT DESTINATIONS                                                        │
│  ───────────────────                                                        │
│                                                                              │
│  ┌────────────────┐         ┌─────────────────┐        ┌────────────────┐  │
│  │ Public Portal  │◄────────│ API Gateway     │◄───────│ awards (API)   │  │
│  │ (Read-only)    │         │ (Transform)     │        │ (approved only)│  │
│  └────────────────┘         └─────────────────┘        └────────────────┘  │
│                                                                              │
│  ┌────────────────┐         ┌─────────────────┐        ┌────────────────┐  │
│  │ Analytics      │◄────────│ ETL Pipeline    │◄───────│ Data Warehouse │  │
│  │ Dashboard      │         │ (Aggregate)     │        │ (Replica)      │  │
│  └────────────────┘         └─────────────────┘        └────────────────┘  │
│                                                                              │
│  ┌────────────────┐         ┌─────────────────┐        ┌────────────────┐  │
│  │ GDPR Export    │◄────────│ Export Service  │◄───────│ User Data      │  │
│  │ (JSON/CSV)     │         │ (Anonymization) │        │ (All entities) │  │
│  └────────────────┘         └─────────────────┘        └────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Source-to-Target Mapping

| **Source** | **Transformation** | **Target** | **Frequency** |
|------------|-------------------|------------|---------------|
| Web Form (Award) | Validation, enrichment | `awards` table | On submit |
| File Upload | AI parsing, storage | `documents` table + S3 | On upload |
| Award Submission | Workflow initialization | `award_requests` table | On submit |
| Reviewer Action | State transition | `review_decisions` table | On action |
| Any DB Operation | Event capture | `audit_logs` table | Real-time |
| User Consent | Record consent state | `consent_records` table | On change |
| `awards` (approved) | Filter, transform | Public API response | On request |
| All user data | Aggregation, anonymization | GDPR export file | On request |

### 3.3 Data Transformation Rules

| **Entity** | **Field** | **Transformation** | **Purpose** |
|------------|-----------|-------------------|-------------|
| Award | `title` | Trim, HTML escape | Security, consistency |
| Award | `impact_score` | Calculate from category + org | Business logic |
| Document | `parsed_metadata` | AI extraction from image | Automation |
| Document | `confidence_score` | AI model output | Quality indicator |
| User | `password_hash` | BCrypt(password, 12) | Security |
| Audit | `old_values` | JSON serialize before state | Change tracking |
| Audit | `new_values` | JSON serialize after state | Change tracking |
| Export | Personal data | Anonymization (optional) | GDPR compliance |

### 3.4 Data Lineage Tracking Implementation

```java
// Data lineage tracking via event sourcing pattern (documentation)
@Service
public class DataLineageService {
    
    public void trackDataOrigin(DataLineageEvent event) {
        LineageRecord record = LineageRecord.builder()
            .entityType(event.getEntityType())
            .entityId(event.getEntityId())
            .sourceSystem(event.getSourceSystem())
            .sourceType(event.getSourceType())  // USER_INPUT, AI_PARSED, SYSTEM_GENERATED
            .transformations(event.getTransformations())
            .createdAt(Instant.now())
            .correlationId(event.getCorrelationId())
            .build();
        
        lineageRepository.save(record);
    }
}

// Lineage metadata stored in audit_logs.new_values JSONB
// {
//   "lineage": {
//     "source": "user_input",
//     "source_field": "award_title",
//     "transformations": ["trim", "html_escape"],
//     "validation_passed": true
//   }
// }
```

---

## 4. Master Data Management

### 4.1 Master Data Entities

Master data represents core reference entities that are shared across the system and rarely change:

| **Master Data** | **Entity** | **Owner** | **Update Frequency** |
|-----------------|------------|-----------|---------------------|
| Organizations | `organizations` | System Admin | Rarely (structural changes) |
| Award Categories | `award_categories` | System Admin | Occasionally (policy changes) |
| Role Definitions | (enum values) | System Admin | Version-controlled |
| Status Codes | (enum values) | System Admin | Version-controlled |
| Consent Types | (enum values) | GDPR Officer | Policy-controlled |

### 4.2 Reference Data Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     REFERENCE DATA MANAGEMENT                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    STATIC REFERENCE DATA                              │   │
│  │  (Defined in code, managed via schema migrations)                     │   │
│  │                                                                        │   │
│  │  • UserStatus: PENDING, ACTIVE, INACTIVE, SUSPENDED, RETIRED, MEMORIAL │  │
│  │  • AwardStatus: DRAFT, PENDING, APPROVED, REJECTED, ARCHIVED          │   │
│  │  • RequestStatus: SUBMITTED, IN_REVIEW, ESCALATED, APPROVED, ...      │   │
│  │  • ApprovalLevel: FACULTY_SECRETARY, DEAN, RECTOR_SECRETARY, RECTOR   │   │
│  │  • RoleType: EMPLOYEE, FACULTY_SECRETARY, DEAN, ...                   │   │
│  │  • ConsentType: DATA_PROCESSING, PUBLIC_VISIBILITY, ...               │   │
│  │  • RecognitionLevel: DEPARTMENT, FACULTY, UNIVERSITY, NATIONAL, ...   │   │
│  │                                                                        │   │
│  │  Implementation: Java Enums + Database CHECK constraints               │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    DYNAMIC REFERENCE DATA                             │   │
│  │  (Managed via admin UI, stored in database)                           │   │
│  │                                                                        │   │
│  │  • Organizations: University structure (faculties, departments)       │   │
│  │  • Award Categories: Classification taxonomy                          │   │
│  │                                                                        │   │
│  │  Implementation: Database tables with admin CRUD operations           │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    SEED DATA MANAGEMENT                               │   │
│  │  (Initial data loaded via Flyway migrations)                          │   │
│  │                                                                        │   │
│  │  Migration Files:                                                     │   │
│  │  • R__seed_organizations.sql (Repeatable)                             │   │
│  │  • R__seed_award_categories.sql (Repeatable)                          │   │
│  │  • V002__seed_initial_admin.sql (Versioned, one-time)                 │   │
│  │                                                                        │   │
│  │  Repeatable migrations (R__) re-run when checksum changes             │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.3 Organization Hierarchy Management

```sql
-- Organization hierarchy seed data example (documentation only)
-- Actual seeding via Flyway repeatable migrations during development

-- University (Root)
INSERT INTO organizations (name, name_uk, org_type, parent_org_id, depth, hierarchy_path)
VALUES ('Example University', 'Приклад Університет', 'UNIVERSITY', NULL, 0, 'university');

-- Faculties
INSERT INTO organizations (name, name_uk, org_type, parent_org_id, depth, hierarchy_path)
VALUES ('Faculty of Engineering', 'Факультет інженерії', 'FACULTY', 1, 1, 'university.engineering');

-- Departments
INSERT INTO organizations (name, name_uk, org_type, parent_org_id, depth, hierarchy_path)
VALUES ('Computer Science Department', 'Кафедра інформатики', 'DEPARTMENT', 2, 2, 'university.engineering.cs');
```

### 4.4 Award Category Taxonomy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      AWARD CATEGORY TAXONOMY                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  RECOGNITION LEVEL: INTERNATIONAL                                           │
│  ├── International Research Award                                           │
│  ├── International Conference Best Paper                                    │
│  └── International Grant/Fellowship                                         │
│                                                                              │
│  RECOGNITION LEVEL: NATIONAL                                                │
│  ├── National Science Award                                                 │
│  ├── State Prize                                                            │
│  ├── Ministry Recognition                                                   │
│  └── National Grant                                                         │
│                                                                              │
│  RECOGNITION LEVEL: UNIVERSITY                                              │
│  ├── University Excellence Award                                            │
│  ├── Best Teacher Award                                                     │
│  ├── Research Achievement                                                   │
│  └── Innovation Award                                                       │
│                                                                              │
│  RECOGNITION LEVEL: FACULTY                                                 │
│  ├── Faculty Teaching Award                                                 │
│  ├── Faculty Research Recognition                                           │
│  └── Faculty Service Award                                                  │
│                                                                              │
│  RECOGNITION LEVEL: DEPARTMENT                                              │
│  ├── Department Appreciation                                                │
│  ├── Team Collaboration Award                                               │
│  └── Mentorship Recognition                                                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.5 Master Data Governance Rules

| **Rule** | **Description** | **Enforcement** |
|----------|-----------------|-----------------|
| Single Ownership | Each master entity has one owner role | Admin UI authorization |
| Change Control | All changes require approval | Audit log + notification |
| Version History | Full history maintained | Audit triggers |
| Referential Integrity | Cannot delete referenced data | FK constraints + soft delete |
| Bilingual Support | All names in EN and UK | Required fields |
| Active Flag | Deactivate instead of delete | `is_active` column |

---

## 5. Data Integration Patterns

### 5.1 Internal Integration

| **Source** | **Target** | **Pattern** | **Trigger** |
|------------|------------|-------------|-------------|
| Award Service | Workflow Service | Synchronous API | Award submission |
| Workflow Service | Notification Service | Async Event (Kafka) | State change |
| Document Service | AI Service | Async Event (Kafka) | Document upload |
| All Services | Audit Service | Async Event (Kafka) | Any operation |
| All Services | Cache (Redis) | Cache-aside | Read operations |

### 5.2 External Integration Points

| **External System** | **Integration Type** | **Data Exchanged** | **Security** |
|--------------------|---------------------|-------------------|--------------|
| Object Storage (S3) | REST API | Document files | IAM + encryption |
| AI/ML Service | REST API | Images, metadata | API key + TLS |
| Email Service | SMTP/API | Notifications | TLS + auth |
| SSO Provider | OAuth2/OIDC | Authentication tokens | OAuth2 flow |
| Analytics Platform | ETL/API | Aggregated metrics | API key + VPN |

### 5.3 Data Synchronization Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DATA SYNCHRONIZATION STRATEGY                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  PRIMARY DATABASE (PostgreSQL)                                               │
│  ─────────────────────────────                                               │
│  Source of truth for all transactional data                                 │
│                                                                              │
│        │                                                                     │
│        │ WAL (Write-Ahead Log)                                              │
│        ▼                                                                     │
│  ┌──────────────┐        ┌──────────────┐        ┌──────────────┐          │
│  │ Read Replica │        │ Change Data  │        │ Event Bus    │          │
│  │ (PostgreSQL) │        │ Capture      │        │ (Kafka)      │          │
│  └──────────────┘        └──────────────┘        └──────────────┘          │
│        │                        │                        │                  │
│        │                        │                        │                  │
│        ▼                        ▼                        ▼                  │
│  ┌──────────────┐        ┌──────────────┐        ┌──────────────┐          │
│  │ Reporting    │        │ Search Index │        │ Notification │          │
│  │ Queries      │        │ (Elastic)    │        │ Consumers    │          │
│  └──────────────┘        └──────────────┘        └──────────────┘          │
│                                                                              │
│  CACHE LAYER (Redis)                                                        │
│  ──────────────────                                                         │
│  • Session data                                                             │
│  • User preferences                                                         │
│  • Reference data (organizations, categories)                               │
│  • Rate limiting counters                                                   │
│                                                                              │
│  Cache Invalidation: Event-driven via Kafka topics                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Data Security Classification

### 6.1 GDPR Data Classification

| **Classification** | **Data Types** | **Protection Level** | **Retention** |
|-------------------|----------------|---------------------|---------------|
| **Public** | Approved awards, public profiles | Standard | Permanent |
| **Internal** | Organization structure, categories | Encrypted at rest | 7 years |
| **Confidential** | User PII, contact info, consents | Encrypted + access control | User-controlled |
| **Restricted** | Documents, credentials, audit logs | E2E encryption + audit | Policy-defined |

### 6.2 Data Protection Implementation

| **Control** | **Implementation** | **Data Scope** |
|-------------|-------------------|----------------|
| Encryption at Rest | PostgreSQL TDE + S3 SSE | All stored data |
| Encryption in Transit | TLS 1.3 | All network traffic |
| Field-Level Encryption | Application-level (Jasypt) | Passwords, sensitive PII |
| Tokenization | Document storage keys | File references |
| Anonymization | On-demand GDPR export | Personal data |
| Pseudonymization | User IDs in logs | Audit data |
| Access Logging | All data access logged | Confidential + Restricted |

---

## 7. Data Architecture Metrics

### 7.1 Key Performance Indicators

| **Metric** | **Target** | **Measurement** | **Frequency** |
|------------|------------|-----------------|---------------|
| Data Quality Score | >95% | Validation pass rate | Daily |
| Schema Consistency | 100% | Migration success rate | Per deployment |
| Query Performance | <200ms P99 | APM monitoring | Continuous |
| Data Freshness | <1 minute | Replication lag | Continuous |
| Storage Efficiency | <80% capacity | Disk usage monitoring | Daily |
| Backup Success Rate | 100% | Backup job completion | Daily |

### 7.2 Data Growth Projections

| **Entity** | **Initial Size** | **Monthly Growth** | **Year 1 Projection** |
|------------|-----------------|-------------------|----------------------|
| Users | 1,000 | 50 | 1,600 |
| Awards | 5,000 | 500 | 11,000 |
| Documents | 10,000 | 1,000 | 22,000 |
| Requests | 5,000 | 500 | 11,000 |
| Audit Logs | 100,000 | 50,000 | 700,000 |
| Notifications | 50,000 | 25,000 | 350,000 |

---

## 8. Implementation Roadmap

### 8.1 Development Phase Data Architecture Tasks

| **Task** | **Phase** | **Priority** | **Dependencies** |
|----------|-----------|--------------|------------------|
| Flyway setup and initial migrations | MVP | P0 | - |
| Entity validation implementation | MVP | P0 | Schema |
| Audit logging triggers | MVP | P0 | Schema |
| Master data seeding | MVP | P1 | Schema |
| Redis caching layer | MVP | P1 | Core services |
| Kafka event publishing | Post-MVP | P2 | Workflow service |
| Search index integration | Post-MVP | P2 | Award service |
| Analytics data pipeline | Post-MVP | P3 | Core features |

### 8.2 Success Criteria

- [ ] All entities have comprehensive validation at all layers
- [ ] Audit logging captures 100% of data modifications
- [ ] Master data seeding is repeatable and idempotent
- [ ] Data lineage is traceable for all award data
- [ ] GDPR data classification is enforced via access controls
- [ ] Performance targets met for typical query patterns

---

*Document Version: 1.0*  
*Classification: Internal*  
*Phase: 9 - Data Architecture & Database Design*  
*Author: Stefan Kostyk*


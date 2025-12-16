# Data Migration Strategy
## Award Monitoring & Tracking System

> **Phase 9 Deliverable**: Data Architecture & Database Design  
> **Document Version**: 1.0  
> **Last Updated**: December 2025  
> **Author**: Stefan Kostyk  
> **Migration Tool**: Flyway  
> **Classification**: Internal

---

## Executive Summary

This document defines the comprehensive data migration strategy for the Award Monitoring & Tracking System. It covers schema versioning with Flyway, data archival policies, backup and recovery procedures, and zero-downtime deployment approaches. The strategy ensures safe, repeatable, and auditable database evolution throughout the application lifecycle.

### Key Strategy Components

| **Component** | **Tool/Approach** | **Purpose** |
|---------------|-------------------|-------------|
| Schema Versioning | Flyway Community | Versioned, repeatable migrations |
| Data Archival | Scheduled Jobs + Policies | GDPR compliance, performance |
| Backup Strategy | PostgreSQL pg_dump + WAL | Point-in-time recovery |
| Recovery Procedures | Documented runbooks | Business continuity |
| Zero-Downtime | Blue-green + backward compatible | Continuous deployment |

---

## 1. Schema Versioning with Flyway

### 1.1 Flyway Configuration Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        FLYWAY MIGRATION ARCHITECTURE                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Migration File Types:                                                       │
│  ─────────────────────                                                       │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  V (Versioned Migrations)                                            │    │
│  │  ─────────────────────────                                           │    │
│  │  • Run once, in order                                                │    │
│  │  • Schema changes, data migrations                                   │    │
│  │  • Pattern: V{version}__{description}.sql                            │    │
│  │  • Example: V001__create_users_table.sql                            │    │
│  │  • Checksum validated (fail if modified)                            │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  R (Repeatable Migrations)                                           │    │
│  │  ─────────────────────────                                           │    │
│  │  • Run when checksum changes                                         │    │
│  │  • Views, functions, seed data                                       │    │
│  │  • Pattern: R__{description}.sql                                     │    │
│  │  • Example: R__seed_award_categories.sql                            │    │
│  │  • Must be idempotent (CREATE OR REPLACE)                           │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  U (Undo Migrations) - Enterprise Only                               │    │
│  │  ───────────────────────────────────                                 │    │
│  │  • Not used in Community Edition                                     │    │
│  │  • Manual rollback scripts maintained separately                     │    │
│  │  • Pattern: U{version}__{description}.sql                            │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Migration Directory Structure

```
src/main/resources/
└── db/
    └── migration/
        ├── V001__create_users_table.sql
        ├── V002__create_organizations_table.sql
        ├── V003__create_user_roles_table.sql
        ├── V004__create_award_categories_table.sql
        ├── V005__create_awards_table.sql
        ├── V006__create_documents_table.sql
        ├── V007__create_award_requests_table.sql
        ├── V008__create_review_decisions_table.sql
        ├── V009__create_audit_logs_table.sql
        ├── V010__create_consent_records_table.sql
        ├── V011__create_notifications_tables.sql
        ├── V012__create_indexes.sql
        ├── V013__create_audit_triggers.sql
        ├── R__create_views.sql
        ├── R__create_functions.sql
        ├── R__seed_organizations.sql
        └── R__seed_award_categories.sql
```

### 1.3 Version Numbering Convention

| **Version Format** | **Example** | **Use Case** |
|-------------------|-------------|--------------|
| `V{NNN}__` | `V001__` | Major schema changes (initial, new tables) |
| `V{NNN}.{N}__` | `V001.1__` | Minor additions to existing migration |
| `V{YYYYMMDD}.{N}__` | `V20251215.1__` | Date-based versioning (alternative) |

**Recommended**: Use sequential three-digit numbering (`V001`, `V002`, ...) for clarity and simplicity.

### 1.4 Spring Boot Integration Configuration

```yaml
# application.yml - Flyway configuration (for development reference)
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 0
    validate-on-migrate: true
    out-of-order: false
    clean-disabled: true  # CRITICAL: Prevent accidental data loss
    
    # PostgreSQL-specific settings
    schemas:
      - public
    default-schema: public
    
    # Placeholders for environment-specific values
    placeholders:
      schema_name: public
      
# Environment-specific overrides
---
spring:
  config:
    activate:
      on-profile: production
  flyway:
    baseline-on-migrate: false  # Never baseline in production
    validate-on-migrate: true
```

### 1.5 Migration File Templates

#### Template: Versioned Migration (Schema Change)

```sql
-- V{version}__{description}.sql
-- Description: {Brief description of changes}
-- Author: Stefan Kostyk
-- Date: {YYYY-MM-DD}
-- Ticket: {JIRA/Issue reference if applicable}

-- Pre-flight checks
DO $$
BEGIN
    -- Verify preconditions if needed
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'required_table') THEN
        RAISE EXCEPTION 'Precondition failed: required_table does not exist';
    END IF;
END $$;

-- Migration
CREATE TABLE new_table (
    id BIGSERIAL PRIMARY KEY,
    -- columns...
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 1
);

-- Indexes
CREATE INDEX idx_new_table_column ON new_table(column);

-- Comments
COMMENT ON TABLE new_table IS 'Description of table purpose';
COMMENT ON COLUMN new_table.column IS 'Description of column';

-- Grant permissions (if needed)
-- GRANT SELECT, INSERT, UPDATE, DELETE ON new_table TO app_user;
```

#### Template: Repeatable Migration (Seed Data)

```sql
-- R__seed_{entity}.sql
-- Description: Seed data for {entity} - Idempotent
-- Author: Stefan Kostyk
-- Note: This migration re-runs whenever the file changes

-- Clear and reseed approach (for reference data)
TRUNCATE TABLE reference_table CASCADE;

INSERT INTO reference_table (id, name, name_uk, description) VALUES
    (1, 'Value 1', 'Значення 1', 'Description 1'),
    (2, 'Value 2', 'Значення 2', 'Description 2');

-- Alternative: Upsert approach (for data that may be modified)
INSERT INTO reference_table (id, name, name_uk, description)
VALUES (1, 'Value 1', 'Значення 1', 'Description 1')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    name_uk = EXCLUDED.name_uk,
    description = EXCLUDED.description;

-- Reset sequence to max id
SELECT setval('reference_table_id_seq', COALESCE((SELECT MAX(id) FROM reference_table), 1));
```

---

## 2. Migration Execution Strategy

### 2.1 Environment-Specific Approach

| **Environment** | **Execution** | **Validation** | **Rollback** |
|-----------------|---------------|----------------|--------------|
| **Development** | Auto on startup | Lenient | Drop and recreate |
| **Testing** | Auto on startup | Strict | Fresh database per test |
| **Staging** | Manual approval | Strict + dry-run | Manual scripts |
| **Production** | Manual approval | Strict + dry-run | Manual scripts + backup |

### 2.2 Migration Execution Workflow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      MIGRATION EXECUTION WORKFLOW                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  DEVELOPMENT                                                                 │
│  ───────────                                                                 │
│  1. Developer creates migration file                                        │
│  2. Application starts → Flyway auto-migrates                               │
│  3. Test locally → Commit to branch                                         │
│                                                                              │
│  CI/CD PIPELINE                                                              │
│  ─────────────                                                               │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │
│  │   Build     │───►│  Validate   │───►│   Test      │───►│   Package   │  │
│  │   App       │    │  Migrations │    │  Database   │    │   Artifact  │  │
│  └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘  │
│                            │                  │                             │
│                            │                  │                             │
│                            ▼                  ▼                             │
│                     ┌─────────────┐    ┌─────────────┐                     │
│                     │ flyway      │    │ testcontainers│                    │
│                     │ validate    │    │ PostgreSQL   │                    │
│                     └─────────────┘    └─────────────┘                     │
│                                                                              │
│  STAGING DEPLOYMENT                                                          │
│  ─────────────────                                                           │
│  1. Create database backup                                                  │
│  2. Run: flyway info (check pending migrations)                             │
│  3. Run: flyway validate (verify checksums)                                 │
│  4. Run: flyway migrate (apply changes)                                     │
│  5. Run integration tests                                                   │
│  6. Manual QA verification                                                  │
│                                                                              │
│  PRODUCTION DEPLOYMENT                                                       │
│  ────────────────────                                                        │
│  1. Create database backup (full + WAL)                                     │
│  2. Notify stakeholders of deployment window                                │
│  3. Run: flyway info (review pending)                                       │
│  4. Run: flyway validate (checksum verification)                            │
│  5. Run: flyway migrate -dryRun (preview)                                   │
│  6. APPROVAL: DevOps/DBA review                                             │
│  7. Run: flyway migrate (apply)                                             │
│  8. Verify application health                                               │
│  9. Monitor for 15 minutes                                                  │
│  10. Update deployment log                                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 Backward Compatibility Rules

To enable zero-downtime deployments, all migrations must be backward compatible:

| **Change Type** | **Allowed** | **Migration Strategy** |
|-----------------|-------------|------------------------|
| Add table | ✅ Yes | Single migration |
| Add nullable column | ✅ Yes | Single migration |
| Add column with default | ✅ Yes | Single migration |
| Add index | ✅ Yes | `CREATE INDEX CONCURRENTLY` |
| Rename column | ⚠️ Carefully | Multi-phase (add new, migrate, drop old) |
| Rename table | ⚠️ Carefully | Multi-phase with views |
| Drop column | ⚠️ Carefully | Code-first (stop using), then drop |
| Drop table | ⚠️ Carefully | Code-first (stop using), then drop |
| Change column type | ⚠️ Carefully | Multi-phase migration |
| Add NOT NULL | ⚠️ Carefully | Backfill data first |

### 2.4 Multi-Phase Migration Example

```sql
-- Phase 1: Add new column (V015__add_user_display_name_phase1.sql)
-- Deploy: Application ignores new column
ALTER TABLE users ADD COLUMN display_name VARCHAR(255);

-- Phase 2: Backfill data (V016__backfill_display_name_phase2.sql)
-- Deploy: Application can read new column
UPDATE users SET display_name = first_name || ' ' || last_name 
WHERE display_name IS NULL;

-- Phase 3: Make required (V017__require_display_name_phase3.sql)
-- Deploy: Application uses new column
ALTER TABLE users ALTER COLUMN display_name SET NOT NULL;
ALTER TABLE users ALTER COLUMN display_name SET DEFAULT '';

-- Phase 4: Remove old columns (V018__remove_old_name_columns_phase4.sql)
-- Deploy: Application no longer references old columns (deployed earlier)
ALTER TABLE users DROP COLUMN first_name;
ALTER TABLE users DROP COLUMN last_name;
```

---

## 3. Data Archival Strategy

### 3.1 Archival Policy Matrix

| **Entity** | **Retention Period** | **Archive Trigger** | **Archive Strategy** |
|------------|---------------------|---------------------|---------------------|
| `audit_logs` | 7 years | Age > 7 years | Partition drop or cold storage |
| `notifications` | 1 year | Age > 1 year | Delete or archive to S3 |
| `awards` (ARCHIVED status) | Permanent | Never | Keep in main table |
| `documents` (orphaned) | 90 days | No parent reference | Delete file + metadata |
| `consent_records` | 7 years from withdrawal | Age from `withdrawn_at` | Archive to cold storage |
| `users` (DELETED) | 30 days grace + 7 years | Deletion request | Anonymize + archive |

### 3.2 Archival Implementation Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DATA ARCHIVAL ARCHITECTURE                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  HOT DATA (PostgreSQL)           WARM DATA (Archive DB)    COLD DATA (S3)  │
│  ─────────────────────           ─────────────────────     ──────────────   │
│                                                                              │
│  ┌─────────────────┐            ┌─────────────────┐     ┌─────────────────┐│
│  │ Current Year    │            │ Years 2-7       │     │ Beyond 7 years  ││
│  │ audit_logs      │───────────►│ audit_logs_arch │────►│ audit_logs.parquet│
│  │ (Partitioned)   │  Monthly   │ (Read-only)     │ Yearly │ (Compressed)   ││
│  └─────────────────┘  Archive   └─────────────────┘ Export└─────────────────┘│
│                                                                              │
│  ┌─────────────────┐                                                        │
│  │ notifications   │─────────────────────────────────────►┌─────────────┐   │
│  │ (< 1 year)      │  After 1 year: DELETE               │ /dev/null   │   │
│  └─────────────────┘  (No business value after expiry)   └─────────────┘   │
│                                                                              │
│  ┌─────────────────┐            ┌─────────────────┐                         │
│  │ consent_records │───────────►│ consent_archive │                         │
│  │ (Active)        │  On        │ (Withdrawn      │                         │
│  │                 │  Withdraw  │  consents only) │                         │
│  └─────────────────┘            └─────────────────┘                         │
│                                                                              │
│  ARCHIVAL JOB SCHEDULE                                                      │
│  ─────────────────────                                                       │
│  • Daily (2 AM UTC): Notification cleanup                                   │
│  • Weekly (Sunday 3 AM): Orphaned document cleanup                          │
│  • Monthly (1st, 4 AM): Audit log partition management                      │
│  • Quarterly: Full archival report generation                               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 Partition Management for Audit Logs

```sql
-- Partition management strategy (documented for development implementation)

-- Create parent table with partitioning
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
    
    PRIMARY KEY (log_id, created_at)
) PARTITION BY RANGE (created_at);

-- Monthly partition creation (automated via scheduled job)
-- CREATE TABLE audit_logs_2025_01 PARTITION OF audit_logs
--     FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

-- Partition maintenance function
CREATE OR REPLACE FUNCTION fn_create_audit_partition()
RETURNS void AS $$
DECLARE
    partition_date DATE := DATE_TRUNC('month', CURRENT_DATE + INTERVAL '1 month');
    partition_name TEXT := 'audit_logs_' || TO_CHAR(partition_date, 'YYYY_MM');
    start_date TEXT := TO_CHAR(partition_date, 'YYYY-MM-DD');
    end_date TEXT := TO_CHAR(partition_date + INTERVAL '1 month', 'YYYY-MM-DD');
BEGIN
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF audit_logs FOR VALUES FROM (%L) TO (%L)',
        partition_name, start_date, end_date
    );
END;
$$ LANGUAGE plpgsql;

-- Archive old partitions (move to archive schema or drop)
-- DROP TABLE audit_logs_2018_01;  -- After data exported to cold storage
```

### 3.4 GDPR Data Deletion Procedures

```sql
-- User deletion procedure (30-day grace period + anonymization)
-- This is documentation; actual implementation via application service

-- Step 1: Mark for deletion (immediate)
UPDATE users SET 
    account_status = 'DELETED',
    deletion_requested_at = CURRENT_TIMESTAMP,
    scheduled_deletion_at = CURRENT_TIMESTAMP + INTERVAL '30 days'
WHERE user_id = :user_id;

-- Step 2: After grace period - Anonymize (scheduled job)
-- Awards are preserved but anonymized (public record value)
BEGIN;
    -- Anonymize user
    UPDATE users SET
        email_address = 'deleted_' || user_id || '@anonymized.local',
        first_name = 'Deleted',
        last_name = 'User',
        password_hash = 'DELETED',
        updated_at = CURRENT_TIMESTAMP
    WHERE user_id = :user_id AND account_status = 'DELETED';
    
    -- Preserve awards but remove personal reference in public views
    UPDATE awards SET
        updated_at = CURRENT_TIMESTAMP
    WHERE user_id = :user_id;
    
    -- Archive consent records
    INSERT INTO consent_archive SELECT * FROM consent_records WHERE user_id = :user_id;
    DELETE FROM consent_records WHERE user_id = :user_id;
    
    -- Delete notification preferences
    DELETE FROM notification_preferences WHERE user_id = :user_id;
    
    -- Delete notifications (no retention value)
    DELETE FROM notifications WHERE user_id = :user_id;
COMMIT;
```

---

## 4. Backup and Recovery Strategy

### 4.1 Backup Strategy Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        BACKUP STRATEGY OVERVIEW                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  BACKUP TYPES                                                                │
│  ────────────                                                                │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  FULL BACKUP (pg_dump)                                               │    │
│  │  ─────────────────────                                               │    │
│  │  • Complete database snapshot                                        │    │
│  │  • Frequency: Daily at 2 AM UTC                                      │    │
│  │  • Retention: 30 days                                                │    │
│  │  • Storage: S3 with cross-region replication                         │    │
│  │  • Compression: gzip (typical 5:1 ratio)                             │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  CONTINUOUS WAL ARCHIVING                                            │    │
│  │  ──────────────────────                                              │    │
│  │  • Write-Ahead Log streaming                                         │    │
│  │  • Frequency: Continuous (every 16MB or 5 minutes)                   │    │
│  │  • Retention: 7 days                                                 │    │
│  │  • Enables Point-in-Time Recovery (PITR)                             │    │
│  │  • Storage: S3 with lifecycle policies                               │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  LOGICAL BACKUP (Table-level)                                        │    │
│  │  ────────────────────────────                                        │    │
│  │  • Critical tables only: users, awards, documents                    │    │
│  │  • Frequency: Hourly during business hours                           │    │
│  │  • Retention: 7 days                                                 │    │
│  │  • Format: CSV for easy restoration                                  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  BACKUP VERIFICATION                                                         │
│  ───────────────────                                                         │
│  • Daily: Automated backup integrity check                                  │
│  • Weekly: Restoration test to staging                                      │
│  • Monthly: Full disaster recovery drill                                    │
│  • Quarterly: Cross-region restoration test                                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Recovery Point Objective (RPO) and Recovery Time Objective (RTO)

| **Scenario** | **RPO** | **RTO** | **Recovery Method** |
|--------------|---------|---------|---------------------|
| **Hardware Failure** | 0 (streaming replication) | 5 minutes | Failover to replica |
| **Data Corruption** | 5 minutes (WAL) | 30 minutes | PITR to before corruption |
| **Accidental Deletion** | 5 minutes (WAL) | 1 hour | PITR to before deletion |
| **Regional Disaster** | 1 hour (cross-region) | 4 hours | Cross-region restore |
| **Complete Loss** | 24 hours (daily backup) | 8 hours | Full restore from backup |

### 4.3 Backup Commands Reference

```bash
# Full database backup (documentation for operations)
pg_dump -Fc -Z9 -f backup_$(date +%Y%m%d_%H%M%S).dump \
    -h $DB_HOST -U $DB_USER -d award_monitoring

# Schema-only backup (for migration testing)
pg_dump --schema-only -f schema_$(date +%Y%m%d).sql \
    -h $DB_HOST -U $DB_USER -d award_monitoring

# Table-specific backup
pg_dump -Fc -t users -t awards -t documents \
    -f critical_tables_$(date +%Y%m%d_%H%M%S).dump \
    -h $DB_HOST -U $DB_USER -d award_monitoring

# WAL archiving configuration (postgresql.conf)
# archive_mode = on
# archive_command = 'aws s3 cp %p s3://bucket/wal/%f'
# archive_timeout = 300  # 5 minutes

# Restore from full backup
pg_restore -d award_monitoring -h $DB_HOST -U $DB_USER backup.dump

# Point-in-Time Recovery (PITR)
# 1. Stop PostgreSQL
# 2. Restore base backup
# 3. Configure recovery.conf:
#    restore_command = 'aws s3 cp s3://bucket/wal/%f %p'
#    recovery_target_time = '2025-12-15 14:30:00'
# 4. Start PostgreSQL (recovery mode)
# 5. Promote to primary when ready
```

### 4.4 Recovery Procedures

#### Procedure 1: Single Table Recovery

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  PROCEDURE: Single Table Recovery from Backup                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Prerequisites:                                                              │
│  - Access to backup files                                                   │
│  - Target database connection                                               │
│  - Downtime window (if production)                                          │
│                                                                              │
│  Steps:                                                                      │
│                                                                              │
│  1. IDENTIFY backup containing required data                                │
│     $ aws s3 ls s3://backup-bucket/daily/ | grep 2025-12-15                │
│                                                                              │
│  2. DOWNLOAD backup to local/staging                                        │
│     $ aws s3 cp s3://backup-bucket/daily/backup_20251215.dump .            │
│                                                                              │
│  3. CREATE temporary database for extraction                                │
│     $ createdb temp_restore                                                 │
│     $ pg_restore -d temp_restore backup_20251215.dump                      │
│                                                                              │
│  4. EXTRACT table data                                                      │
│     $ pg_dump -t target_table temp_restore > table_data.sql                │
│                                                                              │
│  5. DISABLE triggers on target table (production)                           │
│     $ psql -c "ALTER TABLE target_table DISABLE TRIGGER ALL"               │
│                                                                              │
│  6. RESTORE data to production                                              │
│     $ psql -d production < table_data.sql                                  │
│                                                                              │
│  7. ENABLE triggers                                                         │
│     $ psql -c "ALTER TABLE target_table ENABLE TRIGGER ALL"                │
│                                                                              │
│  8. VERIFY data integrity                                                   │
│     $ psql -c "SELECT COUNT(*) FROM target_table"                          │
│                                                                              │
│  9. CLEANUP                                                                 │
│     $ dropdb temp_restore                                                   │
│     $ rm backup_20251215.dump table_data.sql                               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Procedure 2: Point-in-Time Recovery

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  PROCEDURE: Point-in-Time Recovery (PITR)                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Use Case: Recover to a specific moment before data corruption/loss         │
│                                                                              │
│  Prerequisites:                                                              │
│  - WAL archiving enabled                                                    │
│  - Base backup available                                                    │
│  - Target timestamp identified                                              │
│                                                                              │
│  Steps:                                                                      │
│                                                                              │
│  1. STOP application connections                                            │
│     - Scale down application pods                                           │
│     - Revoke connection permissions                                         │
│                                                                              │
│  2. IDENTIFY target recovery point                                          │
│     - Review audit logs for incident timestamp                              │
│     - Target: 5 minutes before incident                                     │
│                                                                              │
│  3. STOP PostgreSQL                                                         │
│     $ sudo systemctl stop postgresql                                        │
│                                                                              │
│  4. BACKUP current data directory (safety)                                  │
│     $ mv /var/lib/postgresql/data /var/lib/postgresql/data_incident        │
│                                                                              │
│  5. RESTORE base backup                                                     │
│     $ tar xzf base_backup.tar.gz -C /var/lib/postgresql/                   │
│                                                                              │
│  6. CONFIGURE recovery                                                      │
│     # Create postgresql.auto.conf:                                          │
│     restore_command = 'aws s3 cp s3://bucket/wal/%f %p'                    │
│     recovery_target_time = '2025-12-15 14:25:00+00'                        │
│     recovery_target_action = 'promote'                                      │
│                                                                              │
│  7. CREATE recovery signal file                                             │
│     $ touch /var/lib/postgresql/data/recovery.signal                       │
│                                                                              │
│  8. START PostgreSQL                                                        │
│     $ sudo systemctl start postgresql                                       │
│     # PostgreSQL enters recovery mode                                       │
│     # Replays WAL until target time                                         │
│     # Auto-promotes when complete                                           │
│                                                                              │
│  9. VERIFY recovery                                                         │
│     $ psql -c "SELECT pg_is_in_recovery()"  # Should be 'f'                │
│     $ psql -c "SELECT COUNT(*) FROM awards"                                │
│                                                                              │
│  10. RESTORE application connections                                        │
│      - Scale up application pods                                            │
│      - Restore connection permissions                                       │
│                                                                              │
│  11. DOCUMENT incident                                                      │
│      - Update incident report                                               │
│      - Record recovery actions                                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.5 Backup Monitoring and Alerting

| **Check** | **Frequency** | **Alert Threshold** | **Action** |
|-----------|---------------|---------------------|------------|
| Backup job completion | Daily | Missing or failed | Page on-call |
| Backup file size | Daily | <50% or >200% of average | Investigate |
| WAL archive lag | Continuous | >10 minutes | Alert team |
| Backup storage usage | Daily | >80% capacity | Expand storage |
| Restoration test | Weekly | Any failure | Escalate |

---

## 5. Zero-Downtime Deployment Strategy

### 5.1 Blue-Green Database Deployment

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    BLUE-GREEN DATABASE DEPLOYMENT                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  For major migrations requiring extended execution time:                     │
│                                                                              │
│  ┌─────────────────┐        ┌─────────────────┐                             │
│  │    BLUE         │        │    GREEN        │                             │
│  │  (Current)      │        │   (New)         │                             │
│  ├─────────────────┤        ├─────────────────┤                             │
│  │ PostgreSQL v16  │        │ PostgreSQL v16  │                             │
│  │ Schema v15      │        │ Schema v16      │                             │
│  │ ACTIVE          │        │ STANDBY         │                             │
│  └────────┬────────┘        └────────┬────────┘                             │
│           │                          │                                       │
│           │  Logical Replication     │                                       │
│           └──────────────────────────┘                                       │
│                                                                              │
│  Deployment Steps:                                                           │
│                                                                              │
│  1. Create GREEN database with new schema                                   │
│  2. Set up logical replication from BLUE → GREEN                            │
│  3. Apply migrations to GREEN (while BLUE serves traffic)                   │
│  4. Verify data consistency                                                 │
│  5. Switch application to GREEN                                             │
│  6. Monitor for issues                                                      │
│  7. Decommission BLUE (or keep as rollback)                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Online Schema Changes

For routine changes without downtime:

| **Operation** | **Online Strategy** | **Lock Level** |
|---------------|---------------------|----------------|
| Add column (nullable) | Direct ALTER | ACCESS EXCLUSIVE (brief) |
| Add column (with default) | Direct ALTER (PG 11+) | ACCESS EXCLUSIVE (brief) |
| Add index | `CREATE INDEX CONCURRENTLY` | SHARE UPDATE EXCLUSIVE |
| Drop index | `DROP INDEX CONCURRENTLY` | SHARE UPDATE EXCLUSIVE |
| Add constraint | Add as NOT VALID, then VALIDATE | Minimal lock |
| Rename column | Avoid; use multi-phase migration | - |

```sql
-- Example: Adding index without downtime
CREATE INDEX CONCURRENTLY idx_awards_new ON awards(new_column);

-- Example: Adding constraint without full lock
ALTER TABLE awards ADD CONSTRAINT ck_awards_score 
    CHECK (impact_score BETWEEN 0 AND 100) NOT VALID;
    
-- Validate separately (scans table but doesn't block writes)
ALTER TABLE awards VALIDATE CONSTRAINT ck_awards_score;
```

---

## 6. Migration Testing Strategy

### 6.1 Pre-Production Testing

| **Test Type** | **Environment** | **Purpose** | **Automation** |
|---------------|-----------------|-------------|----------------|
| Schema validation | CI pipeline | Verify SQL syntax | Flyway validate |
| Migration execution | Testcontainers | Test full migration | JUnit integration |
| Data integrity | Staging | Verify data consistency | Custom scripts |
| Performance impact | Staging | Measure query performance | JMeter/Gatling |
| Rollback verification | Staging | Test rollback scripts | Manual |

### 6.2 Testcontainers Integration

```java
// Migration testing with Testcontainers (documentation for development)
@Testcontainers
class MigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("test_db")
        .withUsername("test")
        .withPassword("test");

    @Test
    void shouldApplyAllMigrationsSuccessfully() {
        Flyway flyway = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .load();
        
        flyway.migrate();
        
        // Verify expected schema state
        assertThat(flyway.info().applied()).hasSizeGreaterThan(0);
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void shouldHaveValidMigrationChecksums() {
        Flyway flyway = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .load();
        
        // Validate should not throw
        assertDoesNotThrow(() -> flyway.validate());
    }
}
```

---

## 7. Rollback Strategy

### 7.1 Rollback Decision Matrix

| **Issue Severity** | **Response** | **Rollback Type** |
|-------------------|--------------|-------------------|
| Minor (cosmetic) | Fix forward | No rollback |
| Moderate (functionality) | Evaluate | Schema rollback if needed |
| Severe (data loss risk) | Immediate | Full rollback + PITR |
| Critical (security) | Emergency | Full rollback + incident response |

### 7.2 Manual Rollback Scripts

For each versioned migration, maintain a corresponding rollback script:

```
src/main/resources/
└── db/
    └── rollback/
        ├── V001__rollback.sql
        ├── V002__rollback.sql
        └── ...
```

```sql
-- Example: V005__rollback.sql (Rollback for V005__create_awards_table.sql)
-- WARNING: This will DELETE all awards data!
-- Only use after confirming data loss is acceptable or data has been backed up

DROP TABLE IF EXISTS awards CASCADE;

-- Re-enable any foreign keys that were disabled
-- Restore any views that depended on this table
```

### 7.3 Rollback Execution Procedure

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  PROCEDURE: Schema Rollback                                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ⚠️  WARNING: Rollback may cause data loss. Ensure backup is available.     │
│                                                                              │
│  1. STOP application deployment                                             │
│     $ kubectl rollout pause deployment/award-api                            │
│                                                                              │
│  2. IDENTIFY target rollback version                                        │
│     $ flyway info                                                           │
│     # Note current version (e.g., V016)                                     │
│     # Target version (e.g., V015)                                           │
│                                                                              │
│  3. CREATE backup before rollback                                           │
│     $ pg_dump -Fc -f pre_rollback_$(date +%Y%m%d_%H%M%S).dump $DATABASE    │
│                                                                              │
│  4. EXECUTE rollback script(s)                                              │
│     $ psql -d $DATABASE -f db/rollback/V016__rollback.sql                  │
│                                                                              │
│  5. UPDATE Flyway history (mark as rolled back)                             │
│     $ psql -d $DATABASE -c \                                                │
│       "DELETE FROM flyway_schema_history WHERE version = '016'"             │
│                                                                              │
│  6. VERIFY schema state                                                     │
│     $ flyway info                                                           │
│     $ flyway validate                                                       │
│                                                                              │
│  7. DEPLOY previous application version                                     │
│     $ kubectl rollout undo deployment/award-api                             │
│                                                                              │
│  8. VERIFY application health                                               │
│     $ kubectl get pods                                                      │
│     $ curl -f https://api.example.com/health                               │
│                                                                              │
│  9. DOCUMENT incident and rollback                                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Implementation Checklist

### 8.1 Development Phase Preparation

- [ ] Flyway Maven/Gradle plugin configured
- [ ] Migration directory structure created
- [ ] Initial schema migrations written
- [ ] Seed data migrations prepared
- [ ] Testcontainers integration tests
- [ ] Rollback scripts for each migration
- [ ] CI pipeline migration validation

### 8.2 Production Readiness

- [ ] Backup automation configured
- [ ] WAL archiving enabled
- [ ] Recovery procedures documented
- [ ] Monitoring and alerting setup
- [ ] Runbook documentation complete
- [ ] DR drill conducted successfully
- [ ] Team trained on procedures

---

*Document Version: 1.0*  
*Classification: Internal*  
*Phase: 9 - Data Architecture & Database Design*  
*Author: Stefan Kostyk*


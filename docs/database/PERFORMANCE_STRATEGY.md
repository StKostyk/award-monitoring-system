# Database Performance Strategy
## Award Monitoring & Tracking System

> **Phase 9 Deliverable**: Data Architecture & Database Design  
> **Document Version**: 1.0  
> **Last Updated**: December 2025  
> **Author**: Stefan Kostyk  
> **Database**: PostgreSQL 16  
> **Classification**: Internal

---

## Executive Summary

This document defines the comprehensive database performance strategy for the Award Monitoring & Tracking System. It covers indexing strategies, table partitioning, query optimization guidelines, connection pooling, and performance monitoring. The strategy targets sub-200ms API response times (P99) while maintaining GDPR compliance and supporting anticipated data growth.

### Performance Targets

| **Metric** | **Target** | **Measurement** |
|------------|------------|-----------------|
| API Response Time (P50) | <50ms | APM monitoring |
| API Response Time (P99) | <200ms | APM monitoring |
| Database Query Time (P50) | <10ms | pg_stat_statements |
| Database Query Time (P99) | <100ms | pg_stat_statements |
| Connection Pool Utilization | <70% | HikariCP metrics |
| Cache Hit Ratio | >95% | Redis metrics |
| Index Usage | >99% | pg_stat_user_indexes |

---

## 1. Indexing Strategy

### 1.1 Index Type Selection Guide

| **Query Pattern** | **Index Type** | **PostgreSQL Syntax** | **Use Case** |
|-------------------|----------------|----------------------|--------------|
| Equality lookup | B-tree (default) | `CREATE INDEX` | Primary lookups, FK joins |
| Range queries | B-tree | `CREATE INDEX` | Date ranges, numeric ranges |
| Pattern matching (prefix) | B-tree | `CREATE INDEX` | `LIKE 'prefix%'` |
| Pattern matching (anywhere) | GIN + pg_trgm | `USING GIN (col gin_trgm_ops)` | `LIKE '%search%'` |
| Full-text search | GIN | `USING GIN (to_tsvector(...))` | Award title search |
| JSON/JSONB queries | GIN | `USING GIN (jsonb_col)` | Document metadata |
| Hierarchical data | GiST | `USING GIST (ltree_col)` | Organization hierarchy |
| Geometric/spatial | GiST | `USING GIST (point_col)` | Not applicable |
| Array containment | GIN | `USING GIN (array_col)` | Tag searches |

### 1.2 Core Entity Indexing Plan

#### 1.2.1 Users Table Indexes

```sql
-- Primary key (automatic)
-- pk_users PRIMARY KEY (user_id)

-- Unique constraint indexes (automatic)
-- uk_users_email UNIQUE (email_address)

-- Foreign key index
CREATE INDEX idx_users_organization ON users(organization_id);

-- Status filtering (frequently used in queries)
CREATE INDEX idx_users_status ON users(account_status);

-- Case-insensitive email lookup (for login)
CREATE INDEX idx_users_lower_email ON users(LOWER(email_address));

-- Partial index for active users only (common filter)
CREATE INDEX idx_users_active ON users(organization_id) 
    WHERE account_status = 'ACTIVE';

-- Composite for user listing with status filter
CREATE INDEX idx_users_org_status ON users(organization_id, account_status);
```

#### 1.2.2 Awards Table Indexes

```sql
-- Primary key (automatic)
-- pk_awards PRIMARY KEY (award_id)

-- Foreign key indexes
CREATE INDEX idx_awards_user ON awards(user_id);
CREATE INDEX idx_awards_category ON awards(category_id);

-- Status filtering (workflow queries)
CREATE INDEX idx_awards_status ON awards(status);

-- Date-based queries (reporting, filtering)
CREATE INDEX idx_awards_date ON awards(award_date DESC);

-- Composite for user's awards filtered by status
CREATE INDEX idx_awards_user_status ON awards(user_id, status);

-- Composite for category reporting
CREATE INDEX idx_awards_category_date ON awards(category_id, award_date DESC);

-- Partial index for pending awards (reviewer dashboard)
CREATE INDEX idx_awards_pending ON awards(user_id, created_at DESC) 
    WHERE status = 'PENDING';

-- Partial index for approved awards (public display)
CREATE INDEX idx_awards_approved ON awards(award_date DESC) 
    WHERE status = 'APPROVED';

-- Full-text search on title
CREATE INDEX ftidx_awards_title ON awards 
    USING GIN (to_tsvector('english', title));

-- Full-text search on Ukrainian title
CREATE INDEX ftidx_awards_title_uk ON awards 
    USING GIN (to_tsvector('simple', COALESCE(title_uk, '')));
```

#### 1.2.3 Award Requests Table Indexes

```sql
-- Primary key (automatic)
-- pk_award_requests PRIMARY KEY (request_id)

-- Unique constraint (automatic)
-- uk_award_requests_award UNIQUE (award_id)

-- Foreign key indexes
CREATE INDEX idx_requests_submitter ON award_requests(submitter_id);
CREATE INDEX idx_requests_reviewer ON award_requests(current_reviewer_id);

-- Status filtering
CREATE INDEX idx_requests_status ON award_requests(status);

-- Deadline monitoring
CREATE INDEX idx_requests_deadline ON award_requests(deadline) 
    WHERE status IN ('SUBMITTED', 'IN_REVIEW', 'ESCALATED');

-- Composite for reviewer's pending work
CREATE INDEX idx_requests_reviewer_status ON award_requests(current_reviewer_id, status)
    WHERE status IN ('SUBMITTED', 'IN_REVIEW');

-- Approval level filtering
CREATE INDEX idx_requests_level ON award_requests(current_level);
```

#### 1.2.4 Documents Table Indexes

```sql
-- Primary key (automatic)
-- pk_documents PRIMARY KEY (document_id)

-- Foreign key indexes
CREATE INDEX idx_documents_award ON documents(award_id);
CREATE INDEX idx_documents_request ON documents(request_id);
CREATE INDEX idx_documents_uploader ON documents(uploaded_by);

-- Processing status (AI pipeline)
CREATE INDEX idx_documents_status ON documents(processing_status);

-- Partial index for documents needing processing
CREATE INDEX idx_documents_pending ON documents(uploaded_at)
    WHERE processing_status = 'PENDING';

-- GIN index for JSONB metadata queries
CREATE INDEX gin_documents_metadata ON documents USING GIN (parsed_metadata);

-- GIN index with specific JSON path operators
CREATE INDEX gin_documents_metadata_path ON documents 
    USING GIN (parsed_metadata jsonb_path_ops);
```

#### 1.2.5 Audit Logs Table Indexes

```sql
-- Primary key on partitioned table
-- pk_audit_logs PRIMARY KEY (log_id, created_at)

-- User activity queries
CREATE INDEX idx_audit_user ON audit_logs(user_id);

-- Entity lookup (find all changes to specific record)
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);

-- Time-based queries
CREATE INDEX idx_audit_timestamp ON audit_logs(created_at DESC);

-- Action type filtering
CREATE INDEX idx_audit_action ON audit_logs(action_type);

-- Composite for user activity timeline
CREATE INDEX idx_audit_user_time ON audit_logs(user_id, created_at DESC);
```

### 1.3 Index Maintenance Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       INDEX MAINTENANCE STRATEGY                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  AUTOMATIC MAINTENANCE                                                       │
│  ──────────────────────                                                      │
│  • PostgreSQL autovacuum handles routine maintenance                        │
│  • Default settings appropriate for most workloads                          │
│  • Monitor for bloat accumulation                                           │
│                                                                              │
│  SCHEDULED MAINTENANCE                                                       │
│  ─────────────────────                                                       │
│  • Weekly: ANALYZE on high-update tables                                    │
│  • Monthly: REINDEX CONCURRENTLY on bloated indexes                        │
│  • Quarterly: Full vacuum analysis                                          │
│                                                                              │
│  MONITORING QUERIES                                                          │
│  ─────────────────                                                           │
│                                                                              │
│  -- Find unused indexes                                                     │
│  SELECT schemaname, relname, indexrelname, idx_scan                        │
│  FROM pg_stat_user_indexes                                                  │
│  WHERE idx_scan = 0 AND indexrelname NOT LIKE 'pk_%'                       │
│  ORDER BY pg_relation_size(indexrelid) DESC;                               │
│                                                                              │
│  -- Find index bloat                                                        │
│  SELECT tablename, indexname,                                               │
│         pg_size_pretty(pg_relation_size(indexrelid)) as index_size,        │
│         idx_scan as index_scans                                             │
│  FROM pg_stat_user_indexes                                                  │
│  JOIN pg_index USING (indexrelid)                                          │
│  WHERE pg_relation_size(indexrelid) > 10000000  -- > 10MB                  │
│  ORDER BY pg_relation_size(indexrelid) DESC;                               │
│                                                                              │
│  REINDEX PROCEDURE (Zero-Downtime)                                          │
│  ──────────────────────────────────                                          │
│  1. CREATE INDEX CONCURRENTLY idx_new ...                                   │
│  2. DROP INDEX CONCURRENTLY idx_old                                         │
│  3. ALTER INDEX idx_new RENAME TO idx_old                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Table Partitioning Strategy

### 2.1 Partitioning Candidates

| **Table** | **Partition Key** | **Strategy** | **Justification** |
|-----------|-------------------|--------------|-------------------|
| `audit_logs` | `created_at` | Range (monthly) | High volume, time-based retention |
| `notifications` | `sent_at` | Range (monthly) | High volume, time-based queries |
| `awards` | None | Not partitioned | Volume manageable, diverse access patterns |
| `documents` | None | Not partitioned | Lower volume, mixed queries |

### 2.2 Audit Logs Partitioning Implementation

```sql
-- Parent table definition (partitioned)
CREATE TABLE audit_logs (
    log_id BIGSERIAL,
    user_id BIGINT,
    action_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    old_values JSONB,
    new_values JSONB,
    ip_address INET,
    user_agent VARCHAR(500),
    correlation_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (log_id, created_at)
) PARTITION BY RANGE (created_at);

-- Create partitions for initial deployment
-- Naming: audit_logs_YYYY_MM
CREATE TABLE audit_logs_2025_01 PARTITION OF audit_logs
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
CREATE TABLE audit_logs_2025_02 PARTITION OF audit_logs
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');
-- ... continue for anticipated months

-- Default partition for unexpected data
CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;
```

### 2.3 Partition Management Automation

```sql
-- Function to create next month's partition
CREATE OR REPLACE FUNCTION fn_create_audit_log_partition()
RETURNS void AS $$
DECLARE
    partition_date DATE := DATE_TRUNC('month', CURRENT_DATE + INTERVAL '1 month');
    partition_name TEXT;
    start_date TEXT;
    end_date TEXT;
BEGIN
    partition_name := 'audit_logs_' || TO_CHAR(partition_date, 'YYYY_MM');
    start_date := TO_CHAR(partition_date, 'YYYY-MM-DD');
    end_date := TO_CHAR(partition_date + INTERVAL '1 month', 'YYYY-MM-DD');
    
    -- Check if partition already exists
    IF NOT EXISTS (
        SELECT 1 FROM pg_class WHERE relname = partition_name
    ) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF audit_logs FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
        RAISE NOTICE 'Created partition: %', partition_name;
    ELSE
        RAISE NOTICE 'Partition already exists: %', partition_name;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Schedule via pg_cron or application scheduler
-- Run monthly on the 25th to create next month's partition
-- SELECT cron.schedule('create_audit_partition', '0 0 25 * *', 'SELECT fn_create_audit_log_partition()');
```

### 2.4 Partition Maintenance

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      PARTITION LIFECYCLE MANAGEMENT                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Month 1-12 (Current Year)                                                  │
│  ─────────────────────────                                                   │
│  • Active partitions in primary tablespace                                  │
│  • Full index coverage                                                      │
│  • Standard backup procedures                                               │
│                                                                              │
│  Month 13-84 (Years 2-7)                                                    │
│  ───────────────────────                                                     │
│  • Move to archive tablespace (if using)                                    │
│  • Reduce index coverage (drop non-essential)                               │
│  • Include in quarterly backups only                                        │
│                                                                              │
│  Month 85+ (Beyond 7 Years)                                                 │
│  ─────────────────────────                                                   │
│  • Export to cold storage (S3/Azure Blob)                                   │
│  • Drop partition from database                                             │
│  • Maintain in compliance archive                                           │
│                                                                              │
│  Partition Drop Procedure                                                   │
│  ────────────────────────                                                    │
│  1. Export partition data: \copy audit_logs_2018_01 TO 'audit_2018_01.csv' │
│  2. Upload to cold storage: aws s3 cp audit_2018_01.csv s3://archive/      │
│  3. Drop partition: DROP TABLE audit_logs_2018_01                          │
│  4. Update retention metadata                                               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Query Optimization Guidelines

### 3.1 Query Analysis Tools

| **Tool** | **Purpose** | **Usage** |
|----------|-------------|-----------|
| `EXPLAIN` | Show query plan | Basic planning analysis |
| `EXPLAIN ANALYZE` | Execute and show actual times | Performance measurement |
| `EXPLAIN (ANALYZE, BUFFERS)` | Include buffer usage | I/O analysis |
| `pg_stat_statements` | Query statistics | Top query identification |
| `auto_explain` | Automatic slow query logging | Production debugging |

### 3.2 Query Pattern Optimization

#### Pattern 1: User's Awards List

```sql
-- Inefficient: Fetches all columns, no pagination
SELECT * FROM awards WHERE user_id = 123;

-- Optimized: Select needed columns, paginated, indexed
SELECT award_id, title, award_date, status, impact_score
FROM awards
WHERE user_id = 123
  AND status = 'APPROVED'
ORDER BY award_date DESC
LIMIT 20 OFFSET 0;

-- Index support
CREATE INDEX idx_awards_user_status_date 
    ON awards(user_id, status, award_date DESC);
```

#### Pattern 2: Reviewer Dashboard

```sql
-- Query: Get pending requests for reviewer
-- Optimized with partial index

SELECT r.request_id, r.submitted_at, r.deadline,
       a.title, a.award_date,
       u.first_name, u.last_name
FROM award_requests r
JOIN awards a ON r.award_id = a.award_id
JOIN users u ON r.submitter_id = u.user_id
WHERE r.current_reviewer_id = 456
  AND r.status IN ('SUBMITTED', 'IN_REVIEW')
ORDER BY r.deadline ASC NULLS LAST
LIMIT 50;

-- Supporting index (partial)
CREATE INDEX idx_requests_reviewer_pending 
    ON award_requests(current_reviewer_id, deadline)
    WHERE status IN ('SUBMITTED', 'IN_REVIEW');
```

#### Pattern 3: Award Search

```sql
-- Full-text search with filters
SELECT a.award_id, a.title, a.award_date,
       ts_rank(to_tsvector('english', a.title), query) as rank
FROM awards a,
     to_tsquery('english', 'research & award') query
WHERE to_tsvector('english', a.title) @@ query
  AND a.status = 'APPROVED'
  AND a.award_date >= '2024-01-01'
ORDER BY rank DESC, a.award_date DESC
LIMIT 20;

-- Index support
CREATE INDEX ftidx_awards_title 
    ON awards USING GIN (to_tsvector('english', title));
CREATE INDEX idx_awards_approved_date 
    ON awards(award_date DESC) WHERE status = 'APPROVED';
```

#### Pattern 4: Organization Hierarchy Query

```sql
-- Using ltree for hierarchy queries
-- Find all awards in a faculty and its departments

SELECT a.award_id, a.title, o.name as organization
FROM awards a
JOIN users u ON a.user_id = u.user_id
JOIN organizations o ON u.organization_id = o.org_id
WHERE o.hierarchy_path <@ 'university.engineering'  -- ltree descendant operator
  AND a.status = 'APPROVED'
ORDER BY a.award_date DESC;

-- Index support
CREATE INDEX gist_org_path ON organizations USING GIST (hierarchy_path);
```

### 3.3 Anti-Patterns to Avoid

| **Anti-Pattern** | **Problem** | **Solution** |
|------------------|-------------|--------------|
| `SELECT *` | Fetches unnecessary data | Select specific columns |
| Missing `LIMIT` | Unbounded result sets | Always paginate |
| `OR` in WHERE | Poor index usage | Use `UNION ALL` or `IN` |
| Leading wildcard `%search` | Can't use B-tree index | Use GIN + pg_trgm |
| Functions on indexed columns | Index not used | Create expression index |
| Implicit type casting | Index bypass | Ensure type consistency |
| `NOT IN (subquery)` | Poor performance | Use `NOT EXISTS` or `LEFT JOIN` |
| Correlated subqueries | N+1 query pattern | Use `JOIN` or CTE |

```sql
-- Anti-pattern: OR conditions
SELECT * FROM awards WHERE status = 'PENDING' OR status = 'APPROVED';

-- Better: Use IN
SELECT * FROM awards WHERE status IN ('PENDING', 'APPROVED');

-- Anti-pattern: Function on column
SELECT * FROM users WHERE LOWER(email_address) = 'user@example.com';

-- Better: Expression index
CREATE INDEX idx_users_lower_email ON users(LOWER(email_address));
SELECT * FROM users WHERE LOWER(email_address) = 'user@example.com';

-- Anti-pattern: NOT IN with subquery
SELECT * FROM users WHERE user_id NOT IN (SELECT user_id FROM awards);

-- Better: NOT EXISTS
SELECT * FROM users u 
WHERE NOT EXISTS (SELECT 1 FROM awards a WHERE a.user_id = u.user_id);
```

---

## 4. Connection Pooling Strategy

### 4.1 HikariCP Configuration

```yaml
# Application configuration for HikariCP (reference for development)
spring:
  datasource:
    hikari:
      # Pool sizing
      minimum-idle: 5                    # Minimum connections maintained
      maximum-pool-size: 20              # Maximum connections allowed
      
      # Connection lifecycle
      idle-timeout: 300000               # 5 minutes - idle connection timeout
      max-lifetime: 1200000              # 20 minutes - maximum connection lifetime
      connection-timeout: 20000          # 20 seconds - wait for connection
      
      # Validation
      validation-timeout: 5000           # 5 seconds - validation query timeout
      
      # Leak detection (development/staging)
      leak-detection-threshold: 60000    # 1 minute - log potential leaks
      
      # PostgreSQL-specific optimizations
      data-source-properties:
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        cachePrepStmts: true
        useServerPrepStmts: true
```

### 4.2 Pool Sizing Guidelines

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     CONNECTION POOL SIZING FORMULA                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Base Formula:                                                               │
│  pool_size = (core_count * 2) + effective_spindle_count                     │
│                                                                              │
│  For SSD-based systems (typical cloud):                                     │
│  pool_size = (core_count * 2) + 1                                           │
│                                                                              │
│  Example Calculations:                                                       │
│  ─────────────────────                                                       │
│                                                                              │
│  Development (4 cores):                                                     │
│    pool_size = (4 * 2) + 1 = 9 connections                                 │
│    Recommended: minimum-idle: 2, maximum-pool-size: 10                      │
│                                                                              │
│  Production (8 cores, multiple instances):                                  │
│    pool_size per instance = (8 * 2) + 1 = 17 connections                   │
│    With 3 instances: 17 * 3 = 51 total connections                         │
│    PostgreSQL max_connections: 100 (with buffer)                           │
│    Recommended per instance: minimum-idle: 5, maximum-pool-size: 20        │
│                                                                              │
│  Important Considerations:                                                   │
│  ─────────────────────────                                                   │
│  • More connections ≠ better performance                                    │
│  • Database can only process (cores) queries simultaneously                 │
│  • Excess connections cause contention                                      │
│  • Consider connection overhead (~10MB per connection in PostgreSQL)        │
│                                                                              │
│  PostgreSQL Configuration:                                                   │
│  ─────────────────────────                                                   │
│  max_connections = (app_pool_size * instances) + admin_buffer + replication │
│  Example: (20 * 3) + 10 + 5 = 75, round up to 100                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.3 PgBouncer Consideration (Future Scaling)

For high-scale scenarios, consider PgBouncer connection pooling:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     PGBOUNCER ARCHITECTURE (FUTURE)                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                     │
│  │ App Pod 1   │    │ App Pod 2   │    │ App Pod 3   │                     │
│  │ HikariCP    │    │ HikariCP    │    │ HikariCP    │                     │
│  │ (10 conn)   │    │ (10 conn)   │    │ (10 conn)   │                     │
│  └──────┬──────┘    └──────┬──────┘    └──────┬──────┘                     │
│         │                  │                  │                             │
│         └──────────────────┼──────────────────┘                             │
│                            │                                                 │
│                            ▼                                                 │
│                   ┌─────────────────┐                                       │
│                   │   PgBouncer     │                                       │
│                   │ (Transaction    │                                       │
│                   │  Pooling Mode)  │                                       │
│                   │  30 → 20 conn   │                                       │
│                   └────────┬────────┘                                       │
│                            │                                                 │
│                            ▼                                                 │
│                   ┌─────────────────┐                                       │
│                   │   PostgreSQL    │                                       │
│                   │ (max_conn: 50)  │                                       │
│                   └─────────────────┘                                       │
│                                                                              │
│  Benefits:                                                                   │
│  • Reduced PostgreSQL connection overhead                                   │
│  • Better resource utilization                                              │
│  • Connection multiplexing for short transactions                           │
│                                                                              │
│  Implementation: Post-MVP when scaling requires                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Caching Strategy

### 5.1 Cache Layer Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CACHING ARCHITECTURE                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    APPLICATION LAYER                                  │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │   │
│  │  │  L1 Cache: In-Memory (Caffeine)                                  │ │   │
│  │  │  • JVM-local cache                                               │ │   │
│  │  │  • Ultra-fast access (<1ms)                                      │ │   │
│  │  │  • Small footprint (100MB max)                                   │ │   │
│  │  │  • Per-instance (not shared)                                     │ │   │
│  │  │                                                                   │ │   │
│  │  │  Use Cases:                                                      │ │   │
│  │  │  • User sessions (current request)                               │ │   │
│  │  │  • Reference data (categories, organizations)                    │ │   │
│  │  │  • Rate limiting counters                                        │ │   │
│  │  └─────────────────────────────────────────────────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                     │                                        │
│                          L1 Miss    │                                        │
│                                     ▼                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    DISTRIBUTED CACHE                                  │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │   │
│  │  │  L2 Cache: Redis 7                                               │ │   │
│  │  │  • Shared across instances                                       │ │   │
│  │  │  • Fast access (<5ms network)                                    │ │   │
│  │  │  • Larger capacity (1GB+)                                        │ │   │
│  │  │  • TTL-based expiration                                          │ │   │
│  │  │                                                                   │ │   │
│  │  │  Use Cases:                                                      │ │   │
│  │  │  • User profiles                                                 │ │   │
│  │  │  • Award details (frequently accessed)                           │ │   │
│  │  │  • API response caching                                          │ │   │
│  │  │  • Session storage                                               │ │   │
│  │  └─────────────────────────────────────────────────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                     │                                        │
│                          L2 Miss    │                                        │
│                                     ▼                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    DATABASE LAYER                                     │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │   │
│  │  │  PostgreSQL                                                      │ │   │
│  │  │  • Source of truth                                               │ │   │
│  │  │  • Buffer pool caching                                           │ │   │
│  │  │  • Query plan caching                                            │ │   │
│  │  └─────────────────────────────────────────────────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Cache Configuration by Entity

| **Entity** | **Cache Level** | **TTL** | **Invalidation** |
|------------|-----------------|---------|------------------|
| Organizations | L1 + L2 | 1 hour | On update (rare) |
| Award Categories | L1 + L2 | 1 hour | On update (rare) |
| User Profile | L2 | 15 min | On update |
| User Roles | L2 | 5 min | On role change |
| Award (by ID) | L2 | 10 min | On update |
| Award List | L2 | 5 min | On any award change |
| Notification Count | L2 | 1 min | On new notification |

### 5.3 Cache Invalidation Strategy

```java
// Cache-aside pattern with event-driven invalidation (documentation)
@Service
public class AwardCacheService {
    
    private static final String CACHE_PREFIX = "award:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    
    // Read-through caching
    public Award getAward(Long awardId) {
        String cacheKey = CACHE_PREFIX + awardId;
        
        // Try L2 cache (Redis)
        Award cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Cache miss - fetch from database
        Award award = awardRepository.findById(awardId)
            .orElseThrow(() -> new NotFoundException("Award not found"));
        
        // Populate cache
        redisTemplate.opsForValue().set(cacheKey, award, CACHE_TTL);
        
        return award;
    }
    
    // Event-driven invalidation
    @EventListener
    public void onAwardUpdated(AwardUpdatedEvent event) {
        String cacheKey = CACHE_PREFIX + event.getAwardId();
        redisTemplate.delete(cacheKey);
        
        // Also invalidate list caches
        redisTemplate.delete("award:list:user:" + event.getUserId());
    }
}
```

---

## 6. PostgreSQL Configuration Tuning

### 6.1 Memory Configuration

```ini
# postgresql.conf - Performance tuning (reference for deployment)

# Memory Settings
shared_buffers = 256MB              # 25% of RAM (1GB system)
effective_cache_size = 768MB        # 75% of RAM
work_mem = 16MB                     # Per-operation sort/hash memory
maintenance_work_mem = 128MB        # For VACUUM, CREATE INDEX

# For larger systems (4GB+ RAM):
# shared_buffers = 1GB
# effective_cache_size = 3GB
# work_mem = 64MB
# maintenance_work_mem = 512MB
```

### 6.2 Write Performance

```ini
# WAL Settings
wal_buffers = 16MB
checkpoint_completion_target = 0.9
max_wal_size = 1GB
min_wal_size = 80MB

# Asynchronous commit (for non-critical operations)
# synchronous_commit = off  # Use with caution - data loss risk on crash
```

### 6.3 Query Planner

```ini
# Planner Settings
random_page_cost = 1.1              # SSD optimization (default 4.0 for HDD)
effective_io_concurrency = 200      # SSD optimization (default 1)
default_statistics_target = 100     # More accurate statistics

# Parallel Query
max_parallel_workers_per_gather = 2
max_parallel_workers = 4
max_parallel_maintenance_workers = 2
```

---

## 7. Performance Monitoring

### 7.1 Key Metrics to Monitor

| **Metric** | **Source** | **Alert Threshold** | **Action** |
|------------|------------|---------------------|------------|
| Query time P99 | pg_stat_statements | >100ms | Optimize query |
| Connection count | pg_stat_activity | >80% of max | Scale or optimize |
| Cache hit ratio | pg_stat_user_tables | <95% | Increase shared_buffers |
| Index usage | pg_stat_user_indexes | <99% | Add missing index |
| Table bloat | pg_stat_user_tables | >20% | VACUUM FULL |
| Lock waits | pg_stat_activity | >5 concurrent | Investigate contention |
| Replication lag | pg_stat_replication | >1 minute | Check replica health |

### 7.2 Monitoring Queries

```sql
-- Top 10 slowest queries
SELECT query, calls, total_time, mean_time, rows
FROM pg_stat_statements
ORDER BY mean_time DESC
LIMIT 10;

-- Table cache hit ratio
SELECT 
    relname,
    heap_blks_read,
    heap_blks_hit,
    ROUND(heap_blks_hit::numeric / NULLIF(heap_blks_hit + heap_blks_read, 0) * 100, 2) as hit_ratio
FROM pg_statio_user_tables
ORDER BY heap_blks_read DESC;

-- Index usage
SELECT 
    schemaname, tablename, indexname,
    idx_scan as index_scans,
    pg_size_pretty(pg_relation_size(indexrelid)) as index_size
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;

-- Current connections
SELECT 
    state, count(*)
FROM pg_stat_activity
WHERE datname = current_database()
GROUP BY state;

-- Lock monitoring
SELECT 
    blocked.pid as blocked_pid,
    blocked.query as blocked_query,
    blocking.pid as blocking_pid,
    blocking.query as blocking_query
FROM pg_stat_activity blocked
JOIN pg_stat_activity blocking ON blocking.pid = ANY(pg_blocking_pids(blocked.pid))
WHERE blocked.pid != blocking.pid;
```

### 7.3 Grafana Dashboard Panels

| **Panel** | **Query/Metric** | **Visualization** |
|-----------|------------------|-------------------|
| Query Latency | pg_stat_statements mean_time | Time series |
| Active Connections | pg_stat_activity count | Gauge |
| Cache Hit Ratio | heap_blks_hit / total | Gauge (%) |
| Transactions/sec | xact_commit + xact_rollback rate | Time series |
| Index Scan Ratio | idx_scan / (idx_scan + seq_scan) | Gauge (%) |
| Replication Lag | pg_stat_replication | Time series |
| Table Sizes | pg_total_relation_size | Bar chart |
| Slow Queries | pg_stat_statements >100ms | Table |

---

## 8. Performance Testing Guidelines

### 8.1 Load Testing Scenarios

| **Scenario** | **Target RPS** | **Duration** | **Success Criteria** |
|--------------|----------------|--------------|----------------------|
| Normal Load | 100 RPS | 30 min | P99 < 200ms |
| Peak Load | 500 RPS | 15 min | P99 < 500ms |
| Stress Test | 1000 RPS | 5 min | No errors, graceful degradation |
| Soak Test | 100 RPS | 24 hours | No memory leaks, stable performance |

### 8.2 Database-Specific Tests

```yaml
# JMeter test plan elements (documentation for development)

# Test 1: Award Listing (Read-heavy)
- HTTP Request: GET /api/v1/awards?status=APPROVED&page=0&size=20
- Expected: <50ms P50, <100ms P99
- Concurrency: 50 threads

# Test 2: Award Submission (Write)
- HTTP Request: POST /api/v1/awards
- Expected: <100ms P50, <200ms P99
- Concurrency: 20 threads

# Test 3: Full-Text Search
- HTTP Request: GET /api/v1/awards/search?q=research
- Expected: <100ms P50, <300ms P99
- Concurrency: 30 threads

# Test 4: Report Generation
- HTTP Request: GET /api/v1/reports/organization/{id}
- Expected: <500ms P50, <1000ms P99
- Concurrency: 10 threads
```

---

## 9. Implementation Checklist

### 9.1 Pre-Development

- [ ] Review and finalize index strategy
- [ ] Plan partition implementation for audit_logs
- [ ] Document cache invalidation patterns
- [ ] Define monitoring dashboards

### 9.2 Development Phase

- [ ] Implement indexes in Flyway migrations
- [ ] Configure HikariCP connection pool
- [ ] Integrate Redis caching
- [ ] Enable pg_stat_statements
- [ ] Set up Grafana dashboards

### 9.3 Pre-Production

- [ ] Run load tests against staging
- [ ] Tune PostgreSQL configuration
- [ ] Validate cache hit ratios
- [ ] Test partition management
- [ ] Document slow query analysis procedures

---

*Document Version: 1.0*  
*Classification: Internal*  
*Phase: 9 - Data Architecture & Database Design*  
*Author: Stefan Kostyk*


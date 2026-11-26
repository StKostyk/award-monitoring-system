# ADR-005: Caching Solution Selection

**Status**: Accepted (scope narrowed, see Addendum 2026-10)  
**Date**: 2025-08-20  
**Author**: Stefan Kostyk  
**Stakeholders**: Project Architect, Development Team, Operations Team

---

## Context

The Award Monitoring & Tracking System requires a caching solution to improve performance for frequently accessed data, reduce database load, and support session management for the web application.

### Background
- Need to cache frequently accessed award and user data
- Session management for authenticated users
- Distributed caching for potential future scaling
- Integration with Spring Boot caching abstractions

---

## Decision

**Redis 7+** has been selected as the caching solution for the Award Monitoring & Tracking System.

### Chosen Approach
- **Cache Engine**: Redis 7+ (latest stable version)
- **Integration**: Spring Data Redis with Spring Cache abstraction
- **Configuration**: Single Redis instance for development, Redis Cluster for production
- **Use Cases**: Application cache, session store, temporary data storage

### Rationale
- **Performance**: In-memory storage with sub-millisecond response times
- **Spring Integration**: Excellent Spring Boot integration with auto-configuration
- **Data Structures**: Rich data types (strings, hashes, lists, sets, sorted sets)
- **Persistence**: Optional persistence for session data durability
- **Scalability**: Support for clustering and replication

---

## Consequences

### Positive Consequences
- **Performance**: Significant improvement in data access speed
- **Scalability**: Reduced database load and improved application responsiveness
- **Session Management**: Reliable distributed session storage
- **Development Productivity**: Simple annotation-based caching with Spring

### Negative Consequences
- **Memory Usage**: Additional memory requirements for cache storage
- **Complexity**: Additional component to monitor and maintain
- **Data Consistency**: Cache invalidation complexity

---

## Alternatives Considered

### Alternative 1: Hazelcast
- **Pros**: Java-native, embedded mode, advanced distributed features
- **Cons**: More complex setup, larger memory footprint
- **Reason for Rejection**: Overkill for solo developer project needs

### Alternative 2: EhCache
- **Pros**: Simple setup, JVM-local caching, good for monoliths
- **Cons**: Limited distributed capabilities, no external session storage
- **Reason for Rejection**: Limited scalability for future growth

---

## Implementation Notes

### Technical Requirements
- **Redis Version**: 7.0+ for latest performance improvements
- **Spring Integration**: Spring Data Redis 3.0+
- **Memory**: 512MB minimum, 2GB recommended for production
- **Persistence**: RDB snapshots for session data

### Configuration
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
  cache:
    type: redis
    redis:
      time-to-live: 600000
```

### Cache Strategy
- **Application Data**: 10-minute TTL for awards and user profiles
- **Session Data**: Session-based expiration
- **Static Data**: Long-term caching for institution data

---

## Success Metrics

- **Cache Hit Ratio**: > 80% for frequently accessed data
- **Response Time**: < 5ms for cache operations
- **Memory Usage**: < 90% of allocated Redis memory

---

## Related Documents

- **Tech Stack**: [Technology Stack Selection](../TECH_STACK.md)
- **Other ADRs**: [ADR-001 Backend Framework](./ADR-001-Backend-Framework.md)
- **External Resources**: [Redis Documentation](https://redis.io/documentation)

---

## Addendum 2026-10: Redis scope as built

Redis 7 is part of the stack, but in Epics 1 and 2 it holds security state only: revoked access tokens,
sign-in lockout and attempt counters, and rate-limit counters (`RevokedTokenValidator`, `LoginAttemptService`,
`RateLimitFilter`). The Spring Cache abstraction is not enabled and no award or user data is cached in Redis.

The award category catalogue, the only read-heavy reference data so far, is cached by the browser through HTTP
caching (`Cache-Control: max-age=3600` and an `ETag` on `GET /award-categories`). Application-level caching is
reconsidered when the Epic 5 dashboards produce measurable read load.

Epic 3 adds one more counter of the same kind: uploads per user (20 a minute, `UploadLimits` on the shared
`FixedWindowCounter`). Without Redis this limit, like the others, is not applied.

Epic 4 adds a third: the public API (`GET /api/v1/public/**`), 120 requests a minute per client address
(`PUBLIC_RATE_LIMIT_PER_MINUTE`), counted under `public:rate:` apart from the authentication budget. It fails open
like the others while Redis is down (an error is logged). Public answers carry `Cache-Control: no-store`, so a
withdrawn choice of visibility takes effect at the next request; nothing is cached on purpose.

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2025-08-20 | Stefan Kostyk | Initial version | Document creation |
| 2026-10-01 | Stefan Kostyk | Addendum: redis scope as built | Documentation sync after Epic 2 |
| 2026-10-05 | Stefan Kostyk | Upload counter added to the addendum | Documentation sync after Epic 3 |
| 2026-10-09 | Stefan Kostyk | Public API counter and `no-store` added to the addendum | Documentation sync after Epic 4 |

---

**Document Status**: Approved  
**Next Review Date**: 2026-02-20  
**ADR Category**: Technology 
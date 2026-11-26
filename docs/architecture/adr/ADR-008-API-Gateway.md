# ADR-008: API Gateway Selection

**Status**: Accepted (not implemented, replaced by a reverse proxy, see Addendum 2026-10)  
**Date**: 2025-08-20  
**Author**: Stefan Kostyk  
**Stakeholders**: Project Architect, Development Team, Operations Team

---

## Context

The Award Monitoring & Tracking System requires an API Gateway for request routing, load balancing, authentication, and cross-cutting concerns. This supports future microservices architecture and provides centralized API management.

### Background
- Centralized API entry point for frontend and external integrations
- Cross-cutting concerns (authentication, rate limiting, logging)
- Support for future microservices migration
- Load balancing and failover capabilities

---

## Decision

**Spring Cloud Gateway** has been selected as the API Gateway for the Award Monitoring & Tracking System.

### Chosen Approach
- **Gateway**: Spring Cloud Gateway 4.0+
- **Integration**: Seamless Spring Boot ecosystem integration
- **Configuration**: YAML-based routing and filter configuration
- **Deployment**: Embedded within Spring Boot application initially

### Rationale
- **Spring Ecosystem**: Perfect integration with Spring Boot and Spring Security
- **Reactive**: Built on Spring WebFlux for high performance
- **Programmable**: Java-based filters and predicates for complex routing
- **Cloud-Native**: Designed for cloud and microservices environments
- **Lightweight**: Lower resource footprint compared to traditional gateways

---

## Consequences

### Positive Consequences
- **Ecosystem Consistency**: Same technology stack as main application
- **Performance**: Reactive architecture with excellent throughput
- **Development Productivity**: Familiar Spring patterns and configuration
- **Flexibility**: Programmatic configuration and custom filters

### Negative Consequences
- **Spring Dependency**: Tight coupling to Spring ecosystem
- **Learning Curve**: WebFlux reactive programming concepts
- **Less Features**: Fewer built-in features compared to dedicated gateways

---

## Alternatives Considered

### Alternative 1: Kong
- **Pros**: Feature-rich, plugin ecosystem, management UI
- **Cons**: Additional infrastructure, different technology stack
- **Reason for Rejection**: Over-engineered for solo developer project

### Alternative 2: Zuul
- **Pros**: Netflix proven, Spring Cloud integration
- **Cons**: Legacy technology, maintenance mode, blocking I/O
- **Reason for Rejection**: Spring Cloud Gateway is the modern replacement

---

## Implementation Notes

### Technical Requirements
- **Spring Cloud Gateway Version**: 4.0+ for Spring Boot 3.x compatibility
- **Java**: Reactive programming with WebFlux
- **Memory**: 512MB heap for gateway functionality
- **Configuration**: YAML-based route definitions

### Gateway Configuration
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: award-service
          uri: lb://award-service
          predicates:
            - Path=/api/v1/awards/**
          filters:
            - AddRequestHeader=X-Request-Source, gateway
            - name: RateLimiter
              args:
                rate-limiter: "#{@redisRateLimiter}"
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**
```

### Cross-Cutting Concerns
- **Authentication**: JWT token validation filter
- **Rate Limiting**: Redis-based rate limiting
- **Logging**: Request/response logging filter
- **CORS**: Cross-origin resource sharing configuration

---

## Success Metrics

- **Request Latency**: < 10ms gateway overhead
- **Throughput**: > 10,000 requests/second
- **Availability**: 99.9% uptime for gateway functionality

---

## Related Documents

- **Tech Stack**: [Technology Stack Selection](../TECH_STACK.md)
- **Integration Patterns**: [Enterprise Integration Patterns](../INTEGRATION_PATTERNS.md)
- **External Resources**: [Spring Cloud Gateway Documentation](https://spring.io/projects/spring-cloud-gateway)

---

## Addendum 2026-10: Reverse proxy instead of a gateway

No API gateway is deployed. The system is one backend application, so routing, service discovery and
aggregation have nothing to coordinate. The nginx server in the frontend container serves the Angular bundle and
proxies `/api`, `/oauth2`, `/connect`, `/.well-known`, `/login` and `/logout` to the backend
(`frontend/nginx.conf`).

The gateway concerns are covered inside the application: rate limiting by `RateLimitFilter` with counters in
Redis, authentication by the resource server (ADR-009), correlation ids by `CorrelationIdFilter`. A gateway is
reconsidered only if the backend is split into services.

---

## Addendum 2026-10-05: Reverse proxy confirmed

The design review of 2026-10-04 confirmed nginx as the only entry point. Since Epic 3 it also proxies
`/login-assets/`, `/swagger-ui/` and `/v3/api-docs`, so the sign-in page, Swagger UI and the SPA share one origin
and the token endpoint needs no cross-origin access, and it answers oversized uploads with a problem document.
A gateway returns to the table only when a module is extracted under the criteria of ADR-022; until then
Spring Cloud Gateway stays the reference option.

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2025-08-20 | Stefan Kostyk | Initial version | Document creation |
| 2026-10-01 | Stefan Kostyk | Addendum: reverse proxy instead of a gateway | Documentation sync after Epic 2 |
| 2026-10-05 | Stefan Kostyk | Addendum: reverse proxy confirmed, gateway tied to ADR-022 | Design review of 2026-10-04 |

---

**Document Status**: Approved  
**Next Review Date**: 2025-07-16  
**ADR Category**: Technology 
# ADR-010: Testing Framework Selection

**Status**: Accepted  
**Date**: 2025-08-20  
**Author**: Stefan Kostyk  
**Stakeholders**: Project Architect, Development Team, QA Team

---

## Context

The Award Monitoring & Tracking System requires comprehensive testing capabilities including unit tests, integration tests, and end-to-end tests. Testing must support the chosen technology stack and provide high code coverage.

### Background
- Unit testing for business logic and service layers
- Integration testing with real databases and external services
- API testing for REST endpoints
- Target of 85%+ code coverage for portfolio demonstration

---

## Decision

**JUnit 5 + Testcontainers** has been selected as the primary testing framework for the Award Monitoring & Tracking System.

### Chosen Approach
- **Unit Testing**: JUnit 5 with Mockito for mocking
- **Integration Testing**: Testcontainers for real database and services
- **API Testing**: Spring Boot Test with MockMvc and REST Assured
- **Coverage**: JaCoCo for code coverage reporting

### Rationale
- **Modern Framework**: JUnit 5 provides modern testing features and annotations
- **Real Environment**: Testcontainers enable testing with real databases
- **Spring Integration**: Excellent Spring Boot Test integration
- **Industry Standard**: Widely adopted in enterprise Java development
- **Tooling Support**: Excellent IDE and build tool integration

---

## Consequences

### Positive Consequences
- **Real Testing**: Integration tests with actual database and services
- **High Confidence**: Tests closer to production environment
- **Docker Integration**: Consistent test environments across developers
- **Coverage Reporting**: Comprehensive code coverage metrics

### Negative Consequences
- **Resource Usage**: Docker containers require additional memory and CPU
- **Test Duration**: Integration tests are slower than pure unit tests
- **Learning Curve**: Testcontainers configuration and best practices

---

## Alternatives Considered

### Alternative 1: TestNG
- **Pros**: Advanced test configuration, parallel execution
- **Cons**: Less popular in Spring ecosystem, more complex setup
- **Reason for Rejection**: JUnit 5 better aligns with Spring Boot

### Alternative 2: Docker Compose for Testing
- **Pros**: Simpler setup, shared with development environment
- **Cons**: Less flexible, harder to manage test isolation
- **Reason for Rejection**: Testcontainers provides better test isolation

---

## Implementation Notes

### Technical Requirements
- **JUnit Version**: 5.10+ for latest features
- **Testcontainers**: 1.19+ with PostgreSQL and Redis modules
- **Coverage Tool**: JaCoCo 0.8+ for coverage reporting
- **Test Slices**: Spring Boot test slices for focused testing

### Testing Structure
```java
@SpringBootTest
@Testcontainers
class AwardServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);
    
    @Test
    void shouldCreateAward() {
        // Integration test implementation
    }
}
```

### Test Categories
- **Unit Tests**: Service layer logic with mocked dependencies
- **Integration Tests**: Database operations with Testcontainers
- **API Tests**: REST endpoints with MockMvc
- **Contract Tests**: API contract verification

---

## Success Metrics

- **Code Coverage**: > 85% overall coverage
- **Test Execution Time**: < 5 minutes for full test suite
- **Test Reliability**: < 1% flaky test rate

---

## Related Documents

- **Tech Stack**: [Technology Stack Selection](../TECH_STACK.md)
- **Other ADRs**: [ADR-004 Database](./ADR-004-Database.md)
- **External Resources**: [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2025-08-20 | Stefan Kostyk | Initial version | Document creation |

---

**Document Status**: Approved  
**Next Review Date**: 2026-02-20  
**ADR Category**: Technology 
# ADR-017: Containerization Platform Selection

**Status**: Accepted  
**Date**: 2025-01-16  
**Author**: Stefan Kostyk  
**Stakeholders**: Project Architect, DevOps Team, Development Team

---

## Context

The Award Monitoring & Tracking System requires a containerization solution to ensure consistent development and deployment environments across different platforms. The solution must support local development, testing, and production deployment scenarios.

### Background
- Need for consistent development environment across different machines
- Complex application stack with multiple services (Spring Boot, PostgreSQL, Redis, Kafka, Elasticsearch)
- Solo developer environment requiring simple setup and management
- Production deployment requirements for scalability and reliability

### Assumptions
- Containerization is essential for modern application deployment
- Docker is the industry standard for containerization
- Local development environment should mirror production as closely as possible
- Container orchestration will be needed for production deployment

---

## Decision

**Docker + Docker Compose** has been selected as the containerization platform for the Award Monitoring & Tracking System.

### Chosen Approach
- **Containerization**: Docker for application and service containerization
- **Local Orchestration**: Docker Compose for local development environment
- **Base Images**: Official OpenJDK and Node.js images for application containers
- **Multi-stage Builds**: Optimized Docker builds for production deployments

### Rationale
- **Industry Standard**: Docker is the de facto standard for containerization
- **Development Environment Consistency**: Identical environments across development machines
- **Simple Orchestration**: Docker Compose provides easy local multi-service management
- **Ecosystem Support**: Excellent tooling and community support
- **Production Ready**: Seamless transition from development to production environments

---

## Consequences

### Positive Consequences
- **Environment Consistency**: Identical behavior across development, testing, and production
- **Easy Setup**: New developers can start working immediately with docker-compose up
- **Isolation**: Services run in isolated containers preventing conflicts
- **Scalability**: Foundation for horizontal scaling in production
- **Debugging**: Consistent environment reduces "works on my machine" issues

### Negative Consequences
- **Resource Overhead**: Containers consume additional memory and CPU
- **Learning Curve**: Developers need to understand Docker concepts and commands
- **Complexity**: Additional layer of complexity in the development workflow

### Neutral Consequences
- **Storage Requirements**: Docker images and containers require disk space
- **Network Configuration**: Container networking requires understanding and configuration

---

## Alternatives Considered

### Alternative 1: Podman
- **Pros**: Daemonless architecture, rootless containers, OCI compliance
- **Cons**: Smaller ecosystem, less tooling support, learning curve for team
- **Reason for Rejection**: Docker's superior ecosystem and tooling support

### Alternative 2: Native Installation
- **Pros**: No containerization overhead, direct access to services
- **Cons**: Environment inconsistency, complex setup, dependency conflicts
- **Reason for Rejection**: Inconsistent environments and setup complexity

### Alternative 3: Virtual Machines
- **Pros**: Strong isolation, familiar technology
- **Cons**: Heavy resource usage, slow startup, complex management
- **Reason for Rejection**: Heavyweight solution with poor developer experience

---

## Implementation Notes

### Technical Requirements
- **Docker Engine**: Latest stable version (24.0+)
- **Docker Compose**: v2.0+ for latest features and performance
- **Base Images**: Official OpenJDK 21, Node 18, PostgreSQL 17, Redis 7 images
- **Registry**: Docker Hub for base images, private registry for application images

### Application Dockerfile
```dockerfile
# Multi-stage build for Spring Boot application
FROM openjdk:21-jdk-slim as builder

WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline

COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM openjdk:21-jre-slim

RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Docker Compose Configuration
```yaml
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/award_system
    depends_on:
      - db
      - redis
      - kafka
    networks:
      - award-network

  db:
    image: postgres:17
    environment:
      POSTGRES_DB: award_system
      POSTGRES_USER: award_user
      POSTGRES_PASSWORD: award_pass
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    networks:
      - award-network

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    networks:
      - award-network

  elasticsearch:
    image: elasticsearch:8.11.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
    ports:
      - "9200:9200"
    volumes:
      - es_data:/usr/share/elasticsearch/data
    networks:
      - award-network

volumes:
  postgres_data:
  es_data:

networks:
  award-network:
    driver: bridge
```

### Development Workflow
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f app

# Rebuild and restart application
docker-compose build app
docker-compose restart app

# Clean shutdown
docker-compose down
```

---

## Compliance & Quality

### Security Implications
- **Non-root Users**: Application containers run as non-root users
- **Network Isolation**: Services communicate through isolated Docker networks
- **Secret Management**: Environment variables for sensitive configuration
- **Base Image Security**: Regular updates of base images for security patches

### Performance Impact
- **Resource Usage**: Containers add minimal overhead compared to VMs
- **Startup Time**: Fast container startup compared to traditional deployment
- **Scaling**: Horizontal scaling capabilities for production deployment

### Maintainability
- **Infrastructure as Code**: Docker Compose files version controlled
- **Reproducible Builds**: Consistent builds across different environments
- **Easy Updates**: Service updates through container image updates
- **Backup Strategy**: Volume-based data persistence for databases

---

## Success Metrics

### Key Performance Indicators
- **Environment Setup Time**: < 5 minutes from clone to running application
- **Container Startup Time**: < 30 seconds for application container
- **Resource Efficiency**: < 20% overhead compared to native deployment

### Monitoring & Alerting
- **Container Health**: Health checks for all application containers
- **Resource Monitoring**: CPU and memory usage monitoring
- **Log Aggregation**: Centralized logging from all containers

---

## Related Documents

- **Technology Stack**: [Technology Stack Selection](../TECH_STACK.md)
- **Other ADRs**: [ADR-018 Orchestration](./ADR-018-Orchestration.md)
- **External Resources**: [Docker Documentation](https://docs.docker.com/)

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2025-08-21 | Stefan Kostyk | Initial version | Document creation |

---

**Document Status**: Approved  
**Next Review Date**: 2026-02-21  
**ADR Category**: Technology 
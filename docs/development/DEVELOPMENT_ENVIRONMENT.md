# Development Environment Setup
## Award Monitoring & Tracking System

> **Phase 12 Deliverable**: Development Environment & Toolchain  
> **Document Version**: 1.0  
> **Last Updated**: January 2026  
> **Author**: Stefan Kostyk

---

## Executive Summary

This document describes the development environment setup for the Award Monitoring & Tracking System. It covers prerequisites, local development services, IDE configuration, and getting started with development.

---

## 1. Prerequisites

### Required Software

| **Software** | **Minimum Version** | **Recommended** | **Purpose** |
|--------------|---------------------|-----------------|-------------|
| **Java JDK** | 21 | 21.0.2+ (Temurin) | Backend development |
| **Maven** | 3.9.0 | 3.9.6+ | Build automation |
| **Docker** | 24.0 | Latest | Development services |
| **Git** | 2.40 | Latest | Version control |
| **Node.js** | 20.0 | 22.x LTS | Frontend development |
| **npm** | 10.0 | Latest | Package management |

### Optional Tools

| **Tool** | **Purpose** |
|----------|-------------|
| IntelliJ IDEA Ultimate | Recommended IDE for Java |
| VS Code | Alternative IDE / frontend |
| DBeaver | Database management |
| Postman | API testing |
| Docker Desktop | Container management GUI |

---

## 2. Quick Start

### Windows (PowerShell)

```powershell
# Navigate to project directory
cd D:\Projects\AwardMonitoringApplication

# Run automated setup
.\tools\dev-environment-setup.ps1
```

### Linux/macOS (Bash)

```bash
# Navigate to project directory
cd ~/projects/award-monitoring-system

# Make script executable
chmod +x tools/dev-environment-setup.sh

# Run automated setup
./tools/dev-environment-setup.sh
```

---

## 3. Manual Setup

### 3.1 Java Development Kit

**Option A: SDKMAN (Recommended for Linux/macOS)**
```bash
# Install SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Java 21
sdk install java 21.0.2-tem
sdk use java 21.0.2-tem
```

**Option B: Chocolatey (Windows)**
```powershell
choco install temurin21
```

**Option C: winget (Windows)**
```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

### 3.2 Maven

**SDKMAN:**
```bash
sdk install maven 3.9.6
```

**Chocolatey:**
```powershell
choco install maven
```

### 3.3 Docker Services

Start the required development services:

```bash
# Create network
docker network create award-network

# PostgreSQL 16
docker run -d \
    --name postgres-dev \
    --network award-network \
    -e POSTGRES_USER=award_dev \
    -e POSTGRES_PASSWORD=dev_password \
    -e POSTGRES_DB=award_monitoring \
    -p 5432:5432 \
    -v postgres-dev-data:/var/lib/postgresql/data \
    postgres:16-alpine

# Redis 7
docker run -d \
    --name redis-dev \
    --network award-network \
    -p 6379:6379 \
    redis:7-alpine --appendonly yes
```

### 3.4 Node.js & Angular

```bash
# Install Node.js 22 LTS
# Use nvm (Node Version Manager) for easy version management

# Install nvm (Linux/macOS)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash

# Install Node.js
nvm install 22
nvm use 22

# Install Angular CLI globally
npm install -g @angular/cli@latest
```

---

## 4. IDE Configuration

### 4.1 IntelliJ IDEA

#### Import Project
1. `File > Open` → Select project root directory
2. Wait for Maven to sync dependencies
3. Mark `src/main/java` as Sources Root
4. Mark `src/test/java` as Test Sources Root

#### Code Style
1. `File > Settings > Editor > Code Style`
2. Click gear icon → `Import Scheme > IntelliJ IDEA code style XML`
3. Select `.idea/codeStyles/Project.xml`
4. Apply to whole project

#### Plugins
Install recommended plugins:
- **Lombok** - Annotation processing
- **Spring Boot Assistant** - Spring support
- **Checkstyle-IDEA** - Code style checking
- **SonarLint** - Code quality
- **PlantUML Integration** - Diagram viewing

#### Annotation Processing
1. `File > Settings > Build, Execution, Deployment > Compiler > Annotation Processors`
2. Check `Enable annotation processing`

#### Run Configurations
Create run configurations for:
- **Spring Boot Application**: Main class `ua.kostyk.award.AwardMonitoringApplication`
- **Maven Build**: `clean install -DskipTests`
- **Maven Tests**: `verify`

### 4.2 VS Code

#### Extensions
```json
// .vscode/extensions.json
{
    "recommendations": [
        "vscjava.vscode-java-pack",
        "vmware.vscode-spring-boot",
        "redhat.java",
        "vscjava.vscode-spring-initializr",
        "sonarsource.sonarlint-vscode",
        "shengchen.vscode-checkstyle",
        "angular.ng-template",
        "esbenp.prettier-vscode",
        "editorconfig.editorconfig"
    ]
}
```

#### Settings
```json
// .vscode/settings.json
{
    "java.configuration.updateBuildConfiguration": "automatic",
    "java.server.launchMode": "Standard",
    "editor.formatOnSave": true,
    "editor.defaultFormatter": "redhat.java",
    "[java]": {
        "editor.defaultFormatter": "redhat.java"
    }
}
```

---

## 5. Environment Variables

### Development Profile

Create a `.env` file (not committed to git):

```properties
# Database
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=award_monitoring
POSTGRES_USER=award_dev
POSTGRES_PASSWORD=dev_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Application
SPRING_PROFILES_ACTIVE=dev
JWT_SECRET=dev-secret-key-change-in-production
```

### Spring Boot Configuration

The application will read from `src/main/resources/application-dev.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:award_monitoring}
    username: ${POSTGRES_USER:award_dev}
    password: ${POSTGRES_PASSWORD:dev_password}
  
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

---

## 6. Running the Application

### Backend (Spring Boot)

```bash
# From project root
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or with specific port
mvn spring-boot:run -Dspring-boot.run.profiles=dev -Dserver.port=8080
```

### Frontend (Angular)

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies
npm install

# Start development server
ng serve

# Access: http://localhost:4200
```

### Full Stack (Docker Compose)

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down
```

---

## 7. Database Management

### Connect to PostgreSQL

```bash
# Using Docker
docker exec -it postgres-dev psql -U award_dev -d award_monitoring

# Using psql directly
psql -h localhost -p 5432 -U award_dev -d award_monitoring
```

### Run Flyway Migrations

```bash
# Apply migrations
mvn flyway:migrate

# Check migration status
mvn flyway:info

# Clean and rebuild (CAUTION: destroys data)
mvn flyway:clean flyway:migrate
```

### Database GUI

Recommended tools:
- **DBeaver** (free): Connect with PostgreSQL driver
- **pgAdmin**: Web-based PostgreSQL admin
- **IntelliJ Database Tool**: Built into Ultimate edition

Connection settings:
- Host: `localhost`
- Port: `5432`
- Database: `award_monitoring`
- User: `award_dev`
- Password: `dev_password`

---

## 8. Common Development Tasks

### Build Project

```bash
# Full build with tests
mvn clean install

# Skip tests (faster)
mvn clean install -DskipTests

# Package only
mvn package -DskipTests
```

### Run Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=UserServiceTest

# With coverage
mvn verify

# Integration tests only
mvn verify -Pit-tests
```

### Code Quality Checks

```bash
# All quality checks
mvn checkstyle:check pmd:check spotbugs:check

# Generate reports
mvn site
# Reports in: target/site/
```

### API Documentation

```bash
# Generate OpenAPI spec
mvn spring-boot:run

# Access Swagger UI
# http://localhost:8080/swagger-ui.html

# OpenAPI JSON
# http://localhost:8080/v3/api-docs
```

---

## 9. Troubleshooting

### Port Already in Use

```bash
# Find process using port 8080
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Linux/macOS
lsof -i :8080
kill -9 <PID>
```

### Docker Issues

```bash
# Reset Docker containers
docker-compose down -v
docker system prune -f

# Restart Docker services
docker restart postgres-dev redis-dev
```

### Maven Issues

```bash
# Clear Maven cache
mvn dependency:purge-local-repository

# Force update dependencies
mvn clean install -U
```

### Java Version Issues

```bash
# Check Java version
java -version

# Ensure JAVA_HOME is set
echo $JAVA_HOME  # Linux/macOS
echo %JAVA_HOME%  # Windows
```

---

## 10. Development Workflow

### Branch Strategy

```
main           ─── Production-ready code
  └── develop  ─── Integration branch
       ├── feature/AMS-123-user-auth
       ├── feature/AMS-124-award-submission
       └── bugfix/AMS-125-login-error
```

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Example:
```
feat(auth): implement JWT token refresh

- Add refresh token endpoint
- Implement token rotation
- Add Redis token blacklist

Closes #123
```

### Pre-Commit Checklist

- [ ] Code compiles without errors
- [ ] All tests pass locally
- [ ] Checkstyle/PMD checks pass
- [ ] Code is properly formatted
- [ ] New code has unit tests
- [ ] Documentation updated if needed

---

## Summary

| **Task** | **Command** |
|----------|-------------|
| Setup environment | `.\tools\dev-environment-setup.ps1` |
| Start services | `docker-compose up -d` |
| Build project | `mvn clean install` |
| Run backend | `mvn spring-boot:run` |
| Run frontend | `cd frontend && ng serve` |
| Run tests | `mvn verify` |
| Quality check | `mvn checkstyle:check pmd:check` |

---

**Document Version**: 1.0  
**Created**: January 2026  
**Next Review**: After first development sprint


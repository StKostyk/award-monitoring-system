# Award Monitoring & Tracking System

A web application for recording and reviewing the academic awards of university staff, developed for Yuriy
Fedkovych Chernivtsi National University as part of a master's thesis. Employees submit their awards with
supporting evidence, the faculty and university offices review them, and the university obtains a consistent
record of recognition. Personal data are processed in line with the GDPR.

## Scope

The system replaces spreadsheets and paper forms with one workflow:

- **Submission**: an employee drafts an award, chooses its category and recognition level, attaches the
  certificate (scanned file or phone photo) and submits it for review.
- **Review**: the request passes through the faculty secretary, the dean and the rector's office, up to the
  level that the recognition level requires.
- **Access control**: every role sees only the awards of its own organisational unit; approval authority can be
  delegated for a limited period.
- **Accountability**: every change is kept as a version and written to an append-only audit log.
- **Privacy**: users can export all data held about them; uploaded files are scanned for malware, encrypted at
  rest and readable only by those who may read the award.

Automatic extraction of award data from certificates (OCR) is planned but not yet implemented.

## Current status

Development runs in one-week sprints. Delivered so far:

| Epic | Content | State |
|------|---------|-------|
| 1 User management | Registration, sign-in on the embedded authorization server, roles scoped to organisational units, delegation, profile, GDPR data export | Done |
| 2 Award lifecycle | Drafts, validation, categories, duplicate detection, version history, review status | Done |
| 3 Documents | Certificate upload with malware scanning and per-user limits; production configuration; university brand and dark theme | Done (OCR deferred) |
| 4 Review workflow | Decisions of reviewers, escalation, deadlines | Next |

Detailed progress: [backlog](docs/project-management/BACKLOG.md), [epic trackers](docs/epics/),
[change log](CHANGELOG.md).

## Technology

| Layer | Technologies |
|-------|--------------|
| Backend | Java 21, Spring Boot 3.5, Spring Security with Spring Authorization Server (OAuth 2.1, OpenID Connect), Flyway |
| Data | PostgreSQL 17, Redis 7, MinIO (S3-compatible object storage), ClamAV |
| Frontend | Angular 21, Angular Material, NgRx, Transloco (Ukrainian and English) |
| Delivery | Docker Compose, GitHub Actions |
| Quality | JUnit 5, Testcontainers, REST-assured, JaCoCo, Vitest, Playwright, Checkstyle, PMD, SpotBugs, ESLint |

The backend is a modular monolith; the reasons and the deferred alternatives (message broker, search engine,
Kubernetes) are recorded in the [architecture decision records](docs/architecture/adr/) and the
[technology stack](docs/architecture/TECH_STACK.md).

## Running locally

Requirements: Docker, JDK 21, Node.js 22.

```bash
docker compose up -d --build          # infrastructure, backend and frontend; the application is at http://localhost
```

For development, start the infrastructure only and run the parts separately:

```bash
docker compose up -d postgres redis mailpit minio clamav
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm ci && npm start    # http://localhost:4200
```

The local profile seeds demonstration accounts (for example `employee.fmi@chnu.edu.ua`); e-mails are caught by
Mailpit at http://localhost:8025. Uploads are refused until ClamAV has loaded its signatures. Further details:
[development environment](docs/development/DEVELOPMENT_ENVIRONMENT.md), [code map](docs/development/CODE_MAP.md),
[demo deployment](docs/deployment/DEMO_DEPLOYMENT.md).

## Documentation

| Area | Main documents |
|------|----------------|
| Requirements | [Vision](docs/initiation/VISION.md), [business requirements](docs/requirements/BUSINESS_REQUIREMENTS.md), [user stories](docs/requirements/USER_STORIES.md), [traceability matrix](docs/requirements/TRACEABILITY_MATRIX.md) |
| Architecture | [ADRs](docs/architecture/adr/), [diagrams](docs/diagrams/), [technology stack](docs/architecture/TECH_STACK.md) |
| Data | [Data dictionary](docs/database/DATA_DICTIONARY.md), [data architecture](docs/database/DATA_ARCHITECTURE.md), [migration strategy](docs/database/MIGRATION_STRATEGY.md) |
| Security and privacy | [Security architecture](docs/security/SECURITY_ARCHITECTURE.md), [threat model](docs/security/THREAT_MODEL.md), [authentication and authorization](docs/security/AUTHENTICATION_AUTHORIZATION.md), [privacy by design](docs/security/PRIVACY_BY_DESIGN.md), [access matrix](docs/stakeholders/RBAC_matrix.md) |
| API | [OpenAPI specification](docs/api/openapi.yml), [Postman collection](docs/api/postman-collection.json) |
| Project management | [Project plan](docs/project-management/PROJECT_PLAN.md), [quality gates](docs/project-management/QUALITY_GATES.md), [feature specifications](docs/features/) |

Documents cited in the thesis have Ukrainian versions under [docs/ua](docs/ua/). The planning documents written
before development started (stakeholder analysis, market research, risk assessment, compliance) are kept under
[docs](docs/) as they were approved; where the implementation differs, the later documents and the ADR addenda
take precedence.

## Author

Stefan Kostyk, master's student at Yuriy Fedkovych Chernivtsi National University. The project is developed
individually. Licensed under the [MIT License](LICENSE).

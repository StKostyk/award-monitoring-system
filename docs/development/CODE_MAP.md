# Code Map
## Award Monitoring & Tracking System

> **Last Updated**: September 2026
> **Author**: Stefan Kostyk

Where things live and the conventions that keep them there. Updated whenever the structure changes.

---

## Repository layout

| Path | Contents |
|------|----------|
| `backend/` | Spring Boot 3.5 service (Java 21), Maven wrapper, Dockerfile |
| `frontend/` | Angular 21 application, client-side rendering, nginx Dockerfile |
| `docs/` | Planning and design documentation (English); `docs/ua/` Ukrainian copies of thesis-cited documents |
| `infra/` | Prometheus, Grafana, Alertmanager, Logstash, Kubernetes manifests |
| `tools/quality/` | Checkstyle, PMD and SpotBugs configuration shared by Maven and CI |
| `tools/verify-all.ps1` | Runs all quality gates and writes `build/gate-summary.txt` |
| `.github/workflows/` | CI/CD pipeline and documentation checks |

## Backend

Base package `ua.edu.chnu.awards`. Code is organised by domain, each domain owning its layers:

```
ua.edu.chnu.awards
├── AwardMonitoringSystemApplication      entry point
├── config/                                cross-cutting Spring configuration (metrics, observability, security)
├── metrics/                               Micrometer business metrics
└── <domain>/                              e.g. user, award, document, workflow, notification, compliance
    ├── controller/                        REST endpoints (thin, validation and mapping only)
    ├── service/                           business logic, transactions
    ├── repository/                        Spring Data JPA
    ├── entity/                            JPA entities and enums
    ├── dto/                               request/response records
    └── mapper/                            MapStruct mappers
```

Conventions:

- Constructor injection via Lombok `@RequiredArgsConstructor`; no field injection.
- Entities never leave the service layer; controllers exchange DTOs (Java records).
- Database schema is owned by Flyway (`src/main/resources/db/migration`): versioned `V###__*.sql` files are immutable once merged, repeatable `R__*.sql` files hold views, functions and seed data. Hibernate runs with `ddl-auto: validate`.
- Configuration lives in `application.yaml` with profiles `local`, `docker`, `production`; secrets come from environment variables, never from the file.

### Tests

| Suffix | Runner | Scope | Infrastructure |
|--------|--------|-------|----------------|
| `*Test` | Surefire | unit tests and `@WebMvcTest` slices | none |
| `*IT` | Failsafe | full context integration and `@DataJpaTest` slices (`@AutoConfigureTestDatabase(replace = NONE)`) | PostgreSQL 17 and Redis 7 via TestContainers (`support/ContainersConfiguration`) |
| `*FT` | Failsafe | functional API tests with REST-assured | same containers, random port |

`support/AbstractIntegrationTest` boots the application once per JVM with the `test` profile (`src/test/resources/application-test.yaml`). Coverage is merged from both runners; `mvn verify` fails below 85% line coverage or on any Checkstyle, PMD or SpotBugs finding.

## Frontend

```
src/app
├── core/        singletons: auth, http interceptors, guards, layout shell
├── shared/      reusable components, pipes, directives, Material re-exports
└── features/    one folder per domain with routes, components and NgRx state
```

- Standalone components, signals for local state, NgRx for cross-feature state.
- Ukrainian is the default locale, English the second, via Angular i18n.
- Unit tests run on Vitest (`npm run test:ci`), end-to-end tests on Playwright.

## Local environment

`docker compose up -d postgres redis mailpit minio` starts the infrastructure; the application then runs with the `local` profile. Mailpit exposes an inbox at http://localhost:8025, MinIO a console at http://localhost:9001.

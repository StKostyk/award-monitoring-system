# Code Map
## Award Monitoring & Tracking System

> **Last Updated**: October 2026
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
| `tools/gate.ps1` | Static checks first, then the backend build (when `backend/` changed since `develop`, or `-Full`) and the frontend lint and unit tests; writes `build/gate-summary.txt`; `-StaticOnly` stops after the static checks |
| `tools/dev-up.ps1` | Starts containers, backend (local profile) and frontend for manual testing |
| `tools/e2e.ps1` | Boots the backend if needed (pausing the `award-backend` container) and runs the Playwright suite; `-Grep` runs matching tests only |
| `tools/tracker-sync.ps1` | Moves a story in Jira and GitHub together |
| `tools/reset-db.ps1` | Recreates the local PostgreSQL volume (Flyway reapplies everything) and restarts a running backend |
| `tools/ua-drift.ps1` | Lists Ukrainian copies behind their English source; `-Missing` also lists documents without a copy |
| `tools/puml-check.ps1` | Checks that every diagram under `docs/` parses (PlantUML `-checkonly`); lists the broken ones |
| `.github/workflows/` | CI/CD pipeline and documentation checks |

## Backend

Base package `ua.edu.chnu.awards`. Code is organised by domain, each domain owning its layers:

```
ua.edu.chnu.awards
├── AwardMonitoringSystemApplication      entry point
├── config/                                cross-cutting Spring configuration (security chains, authorization server, tokens, locale, metrics)
├── common/                                Problem Details exception handling, paging, correlation id (web/), mail delivery (mail/), Redis rate limits and one-per-interval throttles (limit/)
├── metrics/                               Micrometer business metrics
├── auth/                                  authorization server pieces: user lookup, status checks, claims, refresh-token guard, login page
├── authz/                                 organisation scopes, role levels, delegated scopes, access-denied auditing
├── audit/                                 audit_logs writer and the per-award audit trail
└── <domain>/                              user, delegation, award, document, gdpr (workflow, notification, compliance follow in later epics)
    ├── controller/                        REST endpoints (thin, validation and mapping only)
    ├── service/                           business logic, transactions
    ├── repository/                        Spring Data JPA
    ├── entity/                            JPA entities and enums
    ├── dto/                               request/response records
    ├── event/                             application events handled after commit (emails)
    └── mapper/                            hand-written mapper components
```

Conventions:

- Constructor injection via Lombok `@RequiredArgsConstructor`; no field injection.
- Entities never leave the service layer; controllers exchange DTOs (Java records).
- Database schema is owned by Flyway (`src/main/resources/db/migration`): versioned `V###__*.sql` files are immutable once merged, repeatable `R__*.sql` files hold views, functions and reference data. Development accounts live in `db/seed/local` and are loaded only by the `local` and `docker` profiles; the fictional accounts of a deployed demo live in `db/seed/demo` (ADR-021). Hibernate runs with `ddl-auto: validate`; columns with PostgreSQL-specific types (`ltree`, `inet`) are left unmapped and reached through native queries.
- Document files go to MinIO through `document/service/ObjectStorage` (AWS SDK v2) and are scanned by `ClamAvScanner`; keys never contain file names or personal data.
- Configuration lives in `application.yaml` with profiles `local`, `docker`, `production`; secrets come from environment variables, never from the file.

### Tests

| Suffix | Runner | Scope | Infrastructure |
|--------|--------|-------|----------------|
| `*Test` | Surefire | unit tests and `@WebMvcTest` slices | none |
| `*IT` | Failsafe | full context integration and `@DataJpaTest` slices (`@AutoConfigureTestDatabase(replace = NONE)`) | PostgreSQL 17, Redis 7 and MinIO via TestContainers (`support/ContainersConfiguration`) |
| `*FT` | Failsafe | functional API tests with REST-assured (`support/AuthorizationCodeFlow` drives the login and PKCE exchange) | same containers, random port |

`support/AbstractIntegrationTest` boots the application once per JVM with the `test` profile (`src/test/resources/application-test.yaml`). Coverage is merged from both runners; `mvn verify` fails below 85% line coverage or on any Checkstyle, PMD or SpotBugs finding.

## Frontend

```
src/app
├── core/        singletons: auth, http interceptors, guards, layout shell
├── shared/      reusable components, pipes, directives, Material re-exports
└── features/    one folder per domain with routes, components and NgRx state
```

- Standalone components, signals for local state, NgRx for cross-feature state.
- Ukrainian is the default language, English the second, switched at runtime with Transloco (`public/i18n/*.json`).
- `core/auth` wraps `angular-oauth2-oidc`: the app initialiser configures the OpenID Connect client, the guard starts the code flow, `/callback` completes it and the interceptor attaches the access token to `/api` calls.
- Unit tests run on Vitest (`npm run test:ci`), end-to-end tests on Playwright.

## Local environment

`docker compose up -d postgres redis mailpit minio clamav` starts the infrastructure (uploads answer 503 until ClamAV is healthy); the application then runs with the `local` profile. Mailpit exposes an inbox at http://localhost:8025, MinIO a console at http://localhost:9001.

`docker compose up -d --build` runs the whole stack behind nginx at http://localhost: the frontend container proxies `/api`, `/oauth2`, `/connect`, `/.well-known`, `/login`, `/logout` and `/css` to the backend, so browser, API and authorization server share one origin (`AUTH_ISSUER=http://localhost`).

The backend is the OpenID Connect provider for the Angular app: `http://localhost:8080/.well-known/openid-configuration` lists the endpoints, `/login` is the sign-in page, and the demo accounts from `db/seed/local` (password `Passw0rd-demo`) work out of the box. End-to-end tests (`npx playwright test` in `frontend/`) expect that backend to be running.

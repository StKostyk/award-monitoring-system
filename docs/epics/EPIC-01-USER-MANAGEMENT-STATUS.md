# Epic 1: User Management & Authentication — Status

> **Started**: 2026-09-21
> **Author**: Stefan Kostyk
> **Jira epic**: SCRUM-5
> **Roadmap**: [DEVELOPMENT_ROADMAP.md § Epic 1](../../DEVELOPMENT_ROADMAP.md#epic-1-user-management--authentication)

## Progress

| Feature | Status | Started | Done |
|---------|--------|---------|------|
| 1.1 Core Authentication System | In progress | 2026-09-21 | |
| 1.2 Role-Based Access Control | Planned | | |
| 1.3 User Profile Management | Planned | | |

## Current focus

Feature 1.1 — story 1.1.0 in review; 1.1.1 (authorization server) next.

## Stories

Points follow the backlog where it had them; the rest are estimated here. `parallel` marks stories whose UI can be built in a separate lane from the OpenAPI contract while the backend is in progress.

| # | Story | Feature | Pts | Jira | GitHub | Parallel | Status |
|---|-------|---------|-----|------|--------|----------|--------|
| 1 | 1.1.0 User domain entities and auth schema | 1.1 | 3 | SCRUM-6 | #42 | no | In review |
| 2 | 1.1.1 Authorization server, PKCE login and auth shell | 1.1 | 8 | SCRUM-7 | #36 | no | Ready |
| 3 | 1.1.2 Employee registration and email verification | 1.1 | 5 | SCRUM-8 | #29 | no | Ready |
| 4 | 1.1.3 Password reset | 1.1 | 3 | SCRUM-9 | #46 | no | Ready |
| 5 | 1.1.4 Login rate limiting, lockout and auth audit | 1.1 | 3 | SCRUM-10 | #31 | no | Ready |
| 6 | 1.1.5 New device login notification | 1.1 | 3 | SCRUM-11 | #33 | no | Ready |
| 7 | 1.2.1 Permission model and organisation-scoped access | 1.2 | 5 | SCRUM-12 | #35 | no | Ready |
| 8 | 1.2.2 Role assignment | 1.2 | 8 | SCRUM-13 | #32 | yes | Ready |
| 9 | 1.2.3 Approval authority delegation | 1.2 | 5 | SCRUM-14 | #37 | yes | Ready |
| 10 | 1.3.1 Profile information update | 1.3 | 3 | SCRUM-15 | #38 | yes | Ready |
| 11 | 1.3.2 Notification preferences | 1.3 | 3 | SCRUM-16 | #39 | yes | Ready |
| 12 | 1.3.3 GDPR data portability | 1.3 | 5 | SCRUM-17 | #40 | no | Ready |

Total: 54 points, planned across sprints 2–4.

Closed without implementation: #30 and #34 (folded into 1.1.1), #47 (no HR system exists; replaced by institutional-domain check and organisation selection at registration). Deferred: #41 MFA (see decisions).

## Decisions

| Date | Decision | Rationale | Reference |
|------|----------|-----------|-----------|
| 2026-09-21 | Spring Authorization Server issues tokens; Angular is a public client using authorization code + PKCE, no client secret | Matches ADR-009 and the auth design; a browser client cannot keep a secret. SAS 1.5 issues refresh tokens to public clients when the grant is enabled | ADR-009, AUTH §1.4 |
| 2026-09-21 | Login form is served by the authorization server (custom template, uk/en); registration, verification, password reset and profile are Angular pages over REST | Authorization code flow requires the credentials to be posted to the server, not the SPA | AUTH §1.1 |
| 2026-09-21 | RS256 with JWKS endpoint; access token 15 min, refresh token 7 days with rotation and reuse detection | Settled in the auth design | AUTH §1.2, §5.1 |
| 2026-09-21 | Authorizations, registered clients and consents stored in PostgreSQL (JDBC services); Redis holds rate-limit counters, lockout state and one-time throttles | Durable, standard SAS persistence; Redis for short-lived counters only | ADR-005, AUTH §5 |
| 2026-09-21 | Access-token claims: `roles`, `permissions`, `org_id`, `org_type`; resource server maps them to `ROLE_*` and permission authorities | As designed | AUTH §1.3, §3.4 |
| 2026-09-21 | Seven roles from the data dictionary (`SYSTEM_ADMIN`, not `SUPER_ADMIN`) | Dictionary and migrations are the schema of record | DATA_DICTIONARY §1.2 |
| 2026-09-21 | Registration requires an `@chnu.edu.ua` address and a department chosen from the organisation tree; no LDAP or HR lookup | No directory service is available; the org tree is already seeded | US-001 |
| 2026-09-21 | MFA (TOTP, SMS, WebAuthn) deferred beyond Epic 1 | Not required for the thesis demo; the design remains valid for a later increment | AUTH §2 |
| 2026-09-21 | New-device and security emails go through Spring application events and an async listener; message broker decided at Epic 7 | Kafka decision is deferred by the roadmap | ADR-006 |
| 2026-09-21 | Existing migrations V001–V013 are the base; auth tables land in a new V014 | Versioned migrations are immutable once merged | MIGRATION_STRATEGY |

## Documentation deviations to resolve

Each item is applied in the PR of the story that touches it, after approval.

1. `openapi.yml` describes a password-style `/auth/login`, `/auth/refresh`, `/auth/logout`. The authorization server exposes `/oauth2/authorize`, `/oauth2/token`, `/oauth2/revoke`, `/oauth2/jwks`, `/.well-known/openid-configuration`. Replace the three paths with the standard endpoints plus `/auth/register`, `/auth/verify-email`, `/auth/password-reset/*` (story 1.1.1, 1.1.2, 1.1.3).
2. `openapi.yml` `User` schema uses a UUID id, a single `role` and a three-value `status`; the schema of record has a numeric id, many roles and seven statuses (story 1.1.0).
3. AUTH §1.4 registers a confidential client with `CLIENT_SECRET_BASIC`; update to a public PKCE client and add the login-page decision as an addendum (story 1.1.1).
4. AUTH §1.2 stores the refresh token in an HttpOnly cookie; with a public client the token is returned in the token response and held in memory by the SPA. Record the trade-off (story 1.1.1).
5. ADR-009 lists `SUPER_ADMIN` and four sample permissions; align the role list with the dictionary (story 1.2.1).
6. Role-assignment authority: US-002 lets a dean assign roles within the faculty, `RBAC_matrix.md` reserves it for the rector's office, AUTH §3.3 gives `user:manage` to `SYSTEM_ADMIN` only. Agreed rule: a user may assign roles below their own level inside their own organisation subtree; university-level roles only by `RECTOR` or `SYSTEM_ADMIN`. Add `user:manage:faculty` to the permission matrix (story 1.2.2).
7. `RBAC_matrix.md` says only employees submit awards; AUTH §3.3 grants `award:create` up to rector. Left to Epic 2, but the permission strings created in 1.2.1 follow AUTH §3.3.
8. `audit_logs` partitions end at 2026-06; rows now fall into the default partition. Add partitions through 2027 in the migration of story 1.1.4.

## Technical notes

- Old prototype (HS256 tokens, custom login endpoint) is discarded; only the table shapes for refresh tokens, verification tokens and known devices are reused, adapted to `users(user_id)`.
- SAS JDBC schema (`oauth2_registered_client`, `oauth2_authorization`, `oauth2_authorization_consent`) is added by V014 from the library's reference DDL.
- Token customizer reads roles from `user_roles` valid on the day of issue; permissions derive from a static role→permission map (AUTH §3.3), not a table.
- Lockout: 5 failures within 15 minutes lock the account for 30 minutes (roadmap 1.1.2); counters in Redis, event in `audit_logs`.
- Verification links expire after 24 hours; password-reset links after 1 hour.
- Frontend: `angular-oauth2-oidc` for the PKCE flow, tokens in memory, silent renew via refresh token; `core/auth` holds the guard, interceptor and store.

## Risks

1. Login page lives outside Angular; visual consistency and i18n must be handled in the server template.
2. Refresh token in the browser (memory) — acceptable for a public client, mitigated by rotation with reuse detection and short access-token life.
3. Registration without HR data means self-declared organisation membership; a secretary or dean confirms it implicitly when assigning roles.

## Quick links

- [ADR-009 Security Framework](../architecture/adr/ADR-009-Security-Framework.md)
- [Authentication & Authorization design](../security/AUTHENTICATION_AUTHORIZATION.md)
- [Threat model](../security/THREAT_MODEL.md)
- [RBAC matrix](../stakeholders/RBAC_matrix.md)
- [Data dictionary § User domain](../database/DATA_DICTIONARY.md#1-user-domain)
- [User account state machine](../diagrams/uml/state-machine-user-account.puml)
- [OpenAPI](../api/openapi.yml)
- [User stories US-001, US-002](../requirements/USER_STORIES.md)
- PRDs: [Feature 1.1](../features/epic-01/feature-1.1-core-authentication.md)

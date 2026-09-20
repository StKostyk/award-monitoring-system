# Feature 1.1: Core Authentication System

> **Epic**: 1 — User Management & Authentication (SCRUM-5)
> **Sprint**: 2–3 (2026-09-21 → 2026-10-04)
> **Points**: 25 (six stories)
> **Status**: Approved (2026-09-20)
> **Author**: Stefan Kostyk
> **Governing docs**: ADR-009, AUTHENTICATION_AUTHORIZATION.md §1, §5–§7, THREAT_MODEL §2.2.2, DATA_DICTIONARY §1, §4.1, state-machine-user-account.puml, openapi.yml, US-001

## 1. Problem and personas

Every later epic depends on knowing who the caller is and which organisation they belong to. The system must let a university employee create an account with an institutional address, prove ownership of that address, sign in securely, recover a lost password, and be warned when the account is used from an unknown device. Administrators need brute-force protection and an audit trail of authentication events.

| Persona | Need in this feature |
|---------|----------------------|
| Anastasia, employee | Register with `@chnu.edu.ua`, verify, sign in, reset a forgotten password |
| System administrator | Lockout after repeated failures, audit events, notification of suspicious activity |
| Any signed-in user | Notification when a new device signs in, with a way to revoke access |

## 2. Scope

**In (this feature)**

- User, organisation and role entities over the existing schema; new tables for the authorization server, one-time tokens and known devices (1.1.0)
- Spring Authorization Server with a public PKCE client for the Angular app, RS256 tokens with JWKS, refresh-token rotation, server-side login page in Ukrainian and English, resource-server protection of `/api/**`, `GET /api/v1/users/me`, Angular auth shell (1.1.1)
- Registration with domain check and department selection, verification email, activation (1.1.2)
- Password reset by email (1.1.3)
- Failure counting and account lockout, request rate limiting, authentication audit events, admin notification (1.1.4)
- Known-device tracking and new-device email with a revoke link (1.1.5)

**Out (where it goes)**

- MFA of any kind — deferred (#41, icebox)
- LDAP / HR lookup — dropped (#47); membership is self-declared and confirmed by role assignment in Feature 1.2
- Role assignment, permissions beyond "authenticated", organisation-scoped checks — Feature 1.2
- Profile editing, notification preferences, data export — Feature 1.3
- Message broker for notifications — Epic 7; this feature uses in-process events
- Geo-location of IP addresses in device emails — not planned; the email shows IP, browser and OS only

## 3. Stories

| Key | Story | Pts | Parallel | Depends on |
|-----|-------|-----|----------|------------|
| SCRUM-6 (#42) | 1.1.0 User domain entities and auth schema | 3 | no | — |
| SCRUM-7 (#36) | 1.1.1 Authorization server, PKCE login and auth shell | 8 | no | SCRUM-6 |
| SCRUM-8 (#29) | 1.1.2 Employee registration and email verification | 5 | no | SCRUM-7 |
| SCRUM-9 (#46) | 1.1.3 Password reset | 3 | no | SCRUM-8 |
| SCRUM-10 (#31) | 1.1.4 Login rate limiting, lockout and auth audit | 3 | no | SCRUM-7 |
| SCRUM-11 (#33) | 1.1.5 New device login notification | 3 | no | SCRUM-8 |

None of the stories is marked parallel: the frontend work in each is small and coupled to the server's redirect flow.

## 4. Acceptance criteria

### 1.1.0 User domain entities and auth schema (SCRUM-6)

- **AC-0.1** Given migrations V001–V014, when the application starts with `ddl-auto: validate`, then the context loads and the JPA model matches `users`, `organizations`, `user_roles`.
- **AC-0.2** Given V014 applied, then tables `oauth2_registered_client`, `oauth2_authorization`, `oauth2_authorization_consent`, `one_time_tokens`, `user_devices` exist with the constraints in §6.
- **AC-0.3** Given a user with an email stored in mixed case, when looked up by email in any case, then the user is found.
- **AC-0.4** Given a user with one expired and one open-ended role, when current roles are requested, then only the open-ended role is returned.
- **AC-0.5** Given the `local` profile, when the application starts, then the seed users in §9 exist with `ACTIVE` status and their roles; given `test` or `production`, no seed users exist.
- **AC-0.6** `openapi.yml` `User` schema matches the entity: numeric `id`, `roles[]` with organisation, seven-value `status`.

### 1.1.1 Authorization server, PKCE login and auth shell (SCRUM-7)

- **AC-1.1** Given the Angular app, when an unauthenticated user opens any route, then they are redirected to `/oauth2/authorize` with PKCE (`code_challenge_method=S256`) and land on the server login page.
- **AC-1.2** Given valid credentials of an `ACTIVE` user, when the login form is submitted, then the browser returns to `http://localhost:4200/callback` with a code, the app exchanges it at `/oauth2/token` and holds an access token (15 min) and refresh token (7 days) in session storage.
- **AC-1.3** Given an access token, when `GET /api/v1/users/me` is called with it, then 200 with the user's profile; without it, 401 with a Problem Details body.
- **AC-1.4** The access token is a JWT signed RS256 with `kid`, verifiable against `/oauth2/jwks`; claims include `sub` (user id), `email`, `roles`, `permissions`, `org_id`, `org_type`.
- **AC-1.5** Given a refresh token, when used at `/oauth2/token`, then a new pair is issued and the old refresh token is invalid; reusing it invalidates the whole authorization (reuse detection).
- **AC-1.6** Given a `PENDING`, `INACTIVE`, `SUSPENDED` or `MEMORIAL` user, when they log in, then the login page shows a status-specific message and no code is issued. `RETIRED` users log in normally (read-only rules belong to later epics).
- **AC-1.7** The login page is served in Ukrainian by default and in English when `?lang=en` is used or the browser prefers English; the choice is kept in a cookie.
- **AC-1.8** When the user logs out in the app, then the refresh token is revoked at `/oauth2/revoke`, the server session ends at `/logout`, and the app returns to the login page.
- **AC-1.9** `/actuator/health`, `/oauth2/**`, `/login`, `/.well-known/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/api/v1/auth/**` and `/api/v1/organizations/**` are reachable without a token; every other `/api/**` path requires one.

### 1.1.2 Employee registration and email verification (SCRUM-8)

- **AC-2.1** Given a new `@chnu.edu.ua` address, a password of 10–128 characters, first and last name and a department id, when `POST /api/v1/auth/register` is called, then 201, the user is created `PENDING` in that department with role `EMPLOYEE`, and a verification email is delivered (visible in Mailpit).
- **AC-2.2** Given an address with another domain, then 422 with problem type `institutional-email-required` and a message directing the person to the faculty secretary; nothing is stored.
- **AC-2.3** Given an address already registered (any case), then 409; the response does not reveal whether the account is verified.
- **AC-2.4** Given an organisation id that is not a `DEPARTMENT` or is inactive, then 422.
- **AC-2.5** Given the verification link is opened within 24 hours, when `POST /api/v1/auth/verify-email` is called with the token, then the user becomes `ACTIVE`, the token is marked used and a second use returns 410.
- **AC-2.6** Given the token is older than 24 hours, then 410 and the page offers "send again"; `POST /api/v1/auth/resend-verification` issues a new token at most once per minute per address (429 otherwise) and always answers 202.
- **AC-2.7** `GET /api/v1/organizations?type=DEPARTMENT` returns active departments with their faculty, in both languages, without a token.
- **AC-2.8** Angular pages `/register`, `/registration-pending`, `/verify-email` exist in both languages with client-side validation matching the server rules.

### 1.1.3 Password reset (SCRUM-9)

- **AC-3.1** `POST /api/v1/auth/password-reset/request` answers 202 for any address; for an existing `ACTIVE` user an email with a 1-hour link is delivered; at most one request per minute per address.
- **AC-3.2** `POST /api/v1/auth/password-reset/confirm` with a valid token and a new password updates the hash (BCrypt strength 12), marks the token used, revokes every authorization of the user and answers 204; expired or used tokens answer 410.
- **AC-3.3** After a reset, the old password fails and the new one succeeds at the login page.
- **AC-3.4** Angular pages `/forgot-password` and `/reset-password` exist in both languages.

### 1.1.4 Login rate limiting, lockout and auth audit (SCRUM-10)

- **AC-4.1** Given 5 failed logins for one account within 15 minutes, then the account is locked for 30 minutes: the login page says so and correct credentials are refused until the lock expires.
- **AC-4.2** Given more than 20 requests per minute from one IP to `/oauth2/token`, `/login` or `/api/v1/auth/**`, then 429 with a `Retry-After` header.
- **AC-4.3** Every `LOGIN_SUCCESS`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `LOGOUT`, `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET`, `EMAIL_VERIFIED` event is stored in `audit_logs` with user id (when known), IP, user agent and correlation id.
- **AC-4.4** When an account is locked, every user holding `SYSTEM_ADMIN` receives an email naming the account, the IP and the time.
- **AC-4.5** Counters and locks live in Redis with TTL; restarting the application does not clear an active lock.
- **AC-4.6** `audit_logs` has monthly partitions through 2027-12.

### 1.1.5 New device login notification (SCRUM-11)

- **AC-5.1** Given a successful login from a browser whose fingerprint (hash of user agent family, OS family and accept-language) is unknown for the user, then a `user_devices` row is created and an email is sent within one minute listing browser, OS, IP and time.
- **AC-5.2** Given a known fingerprint, then `last_used_at` and `last_ip_address` are updated and no email is sent.
- **AC-5.3** The email contains a "This was not me" link valid for 24 hours; opening it revokes all authorizations, removes the device, forces a password reset (account stays `ACTIVE`, a reset email is sent) and records `SECURITY_REVOKE` in the audit log.
- **AC-5.4** Angular page `/security/not-me` confirms the outcome in both languages.

## 5. Edge cases

- Concurrent registration of the same address: the unique index wins; the second request gets 409.
- Verification of a user who was meanwhile `SUSPENDED` by an administrator: 409, status unchanged.
- Refresh token used after the user became `INACTIVE`/`SUSPENDED`: the token endpoint refuses (`invalid_grant`) — the customizer re-checks status on every refresh.
- Password reset requested for a `PENDING` user: 202, no email (the address is unproven); for unknown addresses: 202, no email.
- Mail server down: registration still succeeds; the email is retried three times by the async sender and the failure is logged; "send again" is available to the user.
- Redis down: login proceeds without counting (fail-open, logged as an error); the health endpoint reports Redis DOWN.
- Clock skew: tokens carry `nbf` with a 60-second leeway on the resource server.
- Login page accessed while already authenticated: redirect to the app.
- Same person registering from two departments: not supported; one account per address.

## 6. Dependencies

### Tables

| Table | State | Story |
|-------|-------|-------|
| `users`, `organizations`, `user_roles`, `audit_logs` | exist (V001–V003, V009) | mapped in 1.1.0 |
| `oauth2_registered_client`, `oauth2_authorization`, `oauth2_authorization_consent` | new, V014, library reference DDL | 1.1.0 |
| `one_time_tokens` (`id`, `token_hash` unique, `user_id` FK, `purpose` in `EMAIL_VERIFICATION`, `PASSWORD_RESET`, `SECURITY_REVOKE`; `expires_at`, `used_at`, `created_at`) | new, V014 | 1.1.0 |
| `user_devices` (`id`, `user_id` FK, `fingerprint`, unique `(user_id, fingerprint)`, `browser`, `operating_system`, `last_ip_address INET`, `first_seen_at`, `last_used_at`) | new, V014 | 1.1.0 |
| `audit_logs` partitions 2026-07 … 2027-12 | new, V015 | 1.1.4 |

`hierarchy_path` (ltree) is not mapped as an entity attribute; subtree queries in Feature 1.2 use native queries. `oauth2_authorization` token columns use `TEXT` rather than the reference `BLOB`, as recommended for PostgreSQL.

### Endpoints

| Path | State | Story |
|------|-------|-------|
| `/oauth2/authorize`, `/oauth2/token`, `/oauth2/revoke`, `/oauth2/jwks`, `/.well-known/openid-configuration`, `/login`, `/logout` | provided by the authorization server; documented in `openapi.yml` as the replacement of `/auth/login`, `/auth/refresh`, `/auth/logout` | 1.1.1 |
| `GET /api/v1/users/me` | exists in `openapi.yml`; schema updated | 1.1.1 |
| `GET /api/v1/organizations` | new | 1.1.2 |
| `POST /api/v1/auth/register`, `POST /api/v1/auth/verify-email`, `POST /api/v1/auth/resend-verification` | new | 1.1.2 |
| `POST /api/v1/auth/password-reset/request`, `POST /api/v1/auth/password-reset/confirm` | new | 1.1.3 |
| `POST /api/v1/auth/security/revoke` | new | 1.1.5 |

### Services and libraries

- `spring-boot-starter-oauth2-authorization-server`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-thymeleaf` (login page), `spring-boot-starter-mail` (Mailpit locally), `bucket4j` or a hand-written Redis counter for rate limiting (decided in 1.1.4 design notes), `ua-parser` for browser/OS names.
- Frontend: `angular-oauth2-oidc`, `@jsverse/transloco` (see deviation D-4), Angular Material forms.
- Docker: Postgres, Redis, Mailpit already in Compose.

### Frontend

`core/auth` (AuthService, `authGuard`, token interceptor, `/callback`), `core/layout` (toolbar with user name, language toggle, logout), `features/auth` (register, registration-pending, verify-email, forgot-password, reset-password, security/not-me), `assets/i18n/uk.json`, `assets/i18n/en.json`.

## 7. Technical decisions

Inherited: Spring Security 6 with OAuth2 resource server and method security (ADR-009); BCrypt strength 12 (ADR-009); Redis for short-lived state (ADR-005); RS256, 15 min / 7 days, rotation (AUTH §1.2, §5.1); claims (AUTH §1.3); audit event catalogue (AUTH §7.1); account states (DATA_DICTIONARY §1.1).

Proposed deviations (applied to the docs in the story that lands them):

| # | Deviation | Reason | Doc to update |
|---|-----------|--------|---------------|
| D-1 | Public PKCE client instead of a confidential client with `CLIENT_SECRET_BASIC`; refresh token returned to the SPA and kept in session storage (survives a reload, dies with the tab), not in an HttpOnly cookie | A browser app cannot hold a secret; rotation with reuse detection covers the theft case; no BFF is needed for the thesis scope | AUTH §1.2, §1.4 addendum (1.1.1) |
| D-2 | Login form served by the authorization server, not by Angular | Required by the authorization-code flow | AUTH §1.1 note (1.1.1) |
| D-3 | `openapi.yml` `/auth/login`, `/auth/refresh`, `/auth/logout` replaced by the standard OAuth2 endpoints; `User` schema aligned with the entity | The custom endpoints contradicted the chosen flow | openapi.yml (1.1.1) |
| D-4 | Runtime translation with Transloco instead of Angular's build-time `$localize` | One build serves both languages, the toggle is instant, E2E runs once, and redirect URIs stay identical; the compile-time approach would double builds and routes | ADR-013 addendum, CODE_MAP (1.1.1) |
| D-5 | Organisation types are five (`UNIVERSITY`, `COLLEGE`, `FACULTY`, `SPECIALITY`, `DEPARTMENT`) as in V002 and the seed, not three | The migration and seed already hold real university structure | DATA_DICTIONARY §1.3 (1.1.0) |
| D-6 | One `one_time_tokens` table with a `purpose` column instead of separate verification and reset tables; tokens stored as SHA-256 hashes | One code path, no raw secrets in the database | DATA_DICTIONARY new entity (1.1.0) |
| D-7 | Notifications via Spring application events and an `@Async` listener, no Kafka | Broker decision deferred to Epic 7 | none (already in the epic tracker) |
| D-8 | Password rule: 10–128 characters, no composition rules, checked against a small list of common passwords | Length beats composition; openapi's `minLength: 8` is raised | openapi.yml, SECURITY_ARCHITECTURE password note (1.1.2) |
| D-9 | Dev seed users are loaded by Flyway from a profile-specific location (`db/seed/local`), not by the repeatable migrations in `db/migration` | Repeatable seeds run everywhere; demo accounts must never reach production | MIGRATION_STRATEGY note (1.1.0) |

## 8. Test plan

| AC | Unit | Slice | IT | FT | E2E |
|----|------|-------|----|----|-----|
| 0.1, 0.2 | | | ✓ context + schema | | |
| 0.3, 0.4 | | ✓ `@DataJpaTest` | | | |
| 0.5 | | | ✓ profile matrix | | |
| 1.1, 1.2, 1.5, 1.8 | | | | ✓ full code flow with REST-assured | ✓ Playwright login/logout |
| 1.3, 1.9 | | ✓ `@WebMvcTest` with mock JWT | | ✓ | |
| 1.4 | ✓ customizer | | ✓ JWKS | | |
| 1.6, 1.7 | ✓ status check | | | ✓ | ✓ |
| 2.x | ✓ validation, domain, password | ✓ controller | ✓ mail via GreenMail/Mailpit API | ✓ register → verify → login | ✓ |
| 3.x | ✓ | ✓ | ✓ | ✓ | ✓ |
| 4.1, 4.2, 4.5 | ✓ counter logic | | ✓ Redis | ✓ 6th attempt, 21st request | |
| 4.3, 4.4, 4.6 | ✓ | | ✓ partitions, audit rows | | |
| 5.x | ✓ fingerprint | | ✓ | ✓ | ✓ |

Coverage target 85 % lines per `mvn verify`; static analysis clean.

## 9. Manual verification

Preconditions (all stories): `docker compose up -d postgres redis mailpit minio`; backend with the `local` profile on `http://localhost:8080`; frontend `npm start` on `http://localhost:4200`; Mailpit inbox at `http://localhost:8025`. Seed accounts (password `Passw0rd-demo` for all):

| Email | Roles | Organisation |
|-------|-------|--------------|
| `admin@chnu.edu.ua` | `SYSTEM_ADMIN` | ChNU |
| `rector@chnu.edu.ua` | `RECTOR` | ChNU |
| `dean.fmi@chnu.edu.ua` | `DEAN` | Faculty of Mathematics and Computer Science |
| `secretary.fmi@chnu.edu.ua` | `FACULTY_SECRETARY` | Faculty of Mathematics and Computer Science |
| `employee.fmi@chnu.edu.ua` | `EMPLOYEE` | a department of FMI |
| `pending@chnu.edu.ua` | `EMPLOYEE`, status `PENDING` | same department |

### After 1.1.0 (SCRUM-6)

1. Start the backend with the `local` profile. Expected: log shows Flyway at version 14 and no schema validation error. (AC-0.1, 0.2)
2. `GET http://localhost:8080/actuator/health` → `status: UP`, `db` and `redis` UP.
3. In psql (`docker compose exec postgres psql -U postgres award_monitoring`): `\dt oauth2_*` lists three tables; `\d one_time_tokens` and `\d user_devices` show the columns of §6. (AC-0.2)
4. `SELECT email_address, account_status FROM users ORDER BY user_id;` → the six seed accounts, five `ACTIVE`, one `PENDING`. (AC-0.5)
5. Restart with the `test` profile against a scratch database (or run `mvn verify` and read the profile test) → no seed users. (AC-0.5)

### After 1.1.1 (SCRUM-7)

6. Open `http://localhost:4200`. Expected: redirect to `http://localhost:8080/login?...` in Ukrainian. Switch to English with the language link; reload keeps English. (AC-1.1, 1.7)
7. Sign in as `employee.fmi@chnu.edu.ua`. Expected: return to the app; toolbar shows the name and a logout button. (AC-1.2)
8. In DevTools → Network, copy the access token from the `/oauth2/token` response; paste it at `https://jwt.io`: header `alg: RS256`, `kid` present; payload has `roles: ["EMPLOYEE"]`, `org_id`, `org_type: "DEPARTMENT"`. (AC-1.4)
9. Swagger UI `http://localhost:8080/swagger-ui.html` → Authorize with the token → `GET /api/v1/users/me` returns the profile; without the token, 401. (AC-1.3, 1.9)
10. Sign out; the app returns to the login page; the old refresh token fails at `/oauth2/token` (repeat the request from DevTools → `invalid_grant`). (AC-1.8, 1.5)
11. Sign in as `pending@chnu.edu.ua`. Expected: login page says the address is not verified yet. (AC-1.6)

### After 1.1.2 (SCRUM-8)

12. Open `http://localhost:4200/register`; submit `test.user@gmail.com`. Expected: inline error about institutional address. (AC-2.2)
13. Submit `test.user@chnu.edu.ua`, password `correct-horse-battery`, a name, department "Кафедра ..." chosen from the list. Expected: page `/registration-pending`; Mailpit shows the verification email. (AC-2.1, 2.7, 2.8)
14. Try to sign in before verifying → "not verified" message. (AC-1.6)
15. Click the email link → `/verify-email?token=…` shows success; open the same link again → "link already used". (AC-2.5)
16. Sign in with the new account → app opens. In psql: `SELECT role_type FROM user_roles WHERE user_id = (SELECT user_id FROM users WHERE email_address = 'test.user@chnu.edu.ua');` → `EMPLOYEE`. (AC-2.1)
17. Register the same address again → 409 message. (AC-2.3)

### After 1.1.3 (SCRUM-9)

18. On the login page click "Forgot password", enter `test.user@chnu.edu.ua`. Expected: neutral confirmation; Mailpit shows the reset email. Repeat within a minute → still neutral, no second email. (AC-3.1)
19. Open the link, set a new password. Expected: success page; old password fails, new one works. (AC-3.2, 3.3)
20. Open the same link again → "link expired or used". (AC-3.2)

### After 1.1.4 (SCRUM-10)

21. Sign in as `employee.fmi@chnu.edu.ua` with a wrong password five times. Expected: fifth attempt shows "temporarily locked"; the correct password is refused. Mailpit shows the admin notification addressed to `admin@chnu.edu.ua`. (AC-4.1, 4.4)
22. In psql: `SELECT action_type, ip_address, created_at FROM audit_logs ORDER BY created_at DESC LIMIT 10;` → `LOGIN_FAILED` ×5 and `ACCOUNT_LOCKED`. (AC-4.3)
23. Restart the backend and retry the correct password → still locked. (AC-4.5)
24. Run `for i in $(seq 1 25); do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/oauth2/token -d grant_type=refresh_token -d refresh_token=x -d client_id=award-web; done` → the tail of the output is `429`. (AC-4.2)

### After 1.1.5 (SCRUM-11)

25. Sign in as `dean.fmi@chnu.edu.ua` in Chrome. Expected: Mailpit shows "New sign-in" with browser, OS, IP. Sign out and in again → no second email. (AC-5.1, 5.2)
26. Sign in from another browser (or with a changed user-agent in DevTools). Expected: another email. Click "This was not me". Expected: confirmation page; the app session in the first browser is refused on the next token refresh; Mailpit shows a password-reset email. (AC-5.3, 5.4)

## 10. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Authorization-server login page styling drifts from the Angular Material look | Demo polish | One shared colour palette and font; screenshot compared at `/feature-validate` |
| Refresh token in browser memory is lost on reload | User re-logs in after a hard refresh | Silent re-authorization via `/oauth2/authorize?prompt=none` using the server session cookie |
| Functional test of the full code flow is brittle (CSRF, redirects) | Flaky CI | REST-assured with a cookie filter and explicit redirect handling; one helper reused by later FTs |
| Rate limiting on a single IP hurts local demos behind NAT | Demo lock-outs | Limits configurable per profile; higher in `local` |
| `oauth2_authorization` grows without bound | Disk | Scheduled cleanup of expired rows daily |

## 11. Definition of Done

- `mvn verify` green: unit, slice, IT (Postgres + Redis containers), FT; coverage ≥ 85 % lines; Checkstyle, PMD, SpotBugs clean
- `npm run lint`, `npm run test:ci` green; Playwright login/register/reset flows green
- `openapi.yml`, `DATA_DICTIONARY.md`, AUTH addendum, ADR-013 addendum, `CHANGELOG.md [Unreleased]`, epic tracker and backlog updated in the PR that changes them
- Manual verification steps of the story performed and recorded in the tracker
- Jira story Done, GitHub issue closed by the PR

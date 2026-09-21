# Feature 1.1: Core Authentication System

> **Epic**: 1 — User Management & Authentication (SCRUM-5)
> **Sprint**: 2–3 (2026-09-21 → 2026-10-04)
> **Points**: 28 (seven stories)
> **Status**: Done — validated 2026-09-21 (§12); the manual run found F-5…F-11, fixed in 1.1.6, pending a repeat of §9 steps 21, 24, 27–32
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
| SCRUM-18 (#64) | 1.1.6 Fixes from the manual run | 3 | no | SCRUM-11 |

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

- **AC-2.1** Given a new `@chnu.edu.ua` address, a password of at least 10 characters and at most 72 bytes, first and last name and a department id, when `POST /api/v1/auth/register` is called, then 201, the user is created `PENDING` in that department with role `EMPLOYEE`, and a verification email is delivered (visible in Mailpit).
- **AC-2.2** Given an address with another domain, then 422 with problem type `institutional-email-required` and a message directing the person to the faculty secretary; nothing is stored.
- **AC-2.3** Given an address already registered (any case), then 409; the response does not reveal whether the account is verified.
- **AC-2.4** Given an organisation id that is not a `DEPARTMENT` or is inactive, then 422.
- **AC-2.5** Given the verification link is opened within 24 hours, when `POST /api/v1/auth/verify-email` is called with the token and the registration password, then the user becomes `ACTIVE`, the token is marked used and a second use returns 410. A wrong password answers 403 and leaves the token usable (added in 1.1.3: without it, anyone could register a colleague's address and have the colleague activate an account with the registrant's password).
- **AC-2.6** Given the token is older than 24 hours, then 410 and the page offers "send again"; `POST /api/v1/auth/resend-verification` issues a new token at most once per minute per address (429 otherwise) and always answers 202.
- **AC-2.7** `GET /api/v1/organizations?type=DEPARTMENT` returns active departments with their faculty, in both languages, without a token.
- **AC-2.8** Angular pages `/register`, `/registration-pending`, `/verify-email` exist in both languages with client-side validation matching the server rules.

### 1.1.3 Password reset (SCRUM-9)

- **AC-3.1** `POST /api/v1/auth/password-reset/request` answers 202 for any address; for an existing `ACTIVE` user an email with a 1-hour link is delivered; at most one email per minute per address (further requests inside the interval are accepted silently, so the response stays neutral).
- **AC-3.2** `POST /api/v1/auth/password-reset/confirm` with a valid token and a new password that meets D-8 updates the hash (BCrypt strength 12), marks the token used, revokes every authorization of the user (refresh tokens stop working at once) and expires the user's login sessions of the authorization server (a signed-in browser is sent to the login page on its next authorize request) and answers 204; expired or used tokens answer 410, a refused password 422.
- **AC-3.3** After a reset, the old password fails and the new one succeeds at the login page.
- **AC-3.4** Angular pages `/forgot-password` and `/reset-password` exist in both languages.

### 1.1.4 Login rate limiting, lockout and auth audit (SCRUM-10)

- **AC-4.1** Given 5 failed logins for one account within 15 minutes, then the account is locked for 30 minutes: the login page says so (from the fifth failure on) and correct credentials are refused until the lock expires. Unknown addresses are locked the same way so the message reveals nothing; only an existing account is audited and reported.
- **AC-4.2** Given more than 20 requests per minute from one IP to `/oauth2/token`, `/login` or `/api/v1/auth/**`, then 429 with a `Retry-After` header (Problem Details for API clients, a bilingual page for browsers). The limit is `AUTH_RATE_LIMIT_PER_MINUTE`; the client IP is the socket peer, or the address the reverse proxy asserted in `X-Forwarded-For` when the peer is a trusted proxy (`SERVER_TRUSTED_PROXIES`, default loopback and the compose network).
- **AC-4.3** Every `LOGIN_SUCCESS`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `LOGOUT`, `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET`, `EMAIL_VERIFIED` event is stored in `audit_logs` with user id (when known), IP, user agent and correlation id.
- **AC-4.4** When an account is locked, every user holding `SYSTEM_ADMIN` receives an email naming the account, the IP and the time.
- **AC-4.5** Counters and locks live in Redis with TTL; restarting the application does not clear an active lock.
- **AC-4.6** `audit_logs` has monthly partitions through 2027-12.

### 1.1.5 New device login notification (SCRUM-11)

- **AC-5.1** Given a successful login from a browser whose fingerprint (hash of user agent family, OS family and accept-language) is unknown for the user, then a `user_devices` row is created and an email is sent within one minute listing browser, OS, IP and time.
- **AC-5.2** Given a known fingerprint, then `last_used_at` and `last_ip_address` are updated and no email is sent.
- **AC-5.3** The email contains a "This was not me" link valid for 24 hours; confirming it revokes all authorizations, removes the user's known devices, forces a password reset (the current password stops working, the account stays `ACTIVE`, a reset email is sent) and records `SECURITY_REVOKE` in the audit log.
- **AC-5.4** Angular page `/security/not-me` confirms the outcome in both languages.

### 1.1.6 Fixes from the manual run (SCRUM-18)

Defects found by the author's run of §9 (findings F-5 to F-9 in §12) and one hardening decision taken with them.

- **AC-6.1** Given the refresh token is refused (`invalid_grant`/401) or the session ends, then the SPA drops its tokens and, on a guarded page, starts the sign-in flow (login page, or silent re-login while the authorization-server session lives) instead of rendering an empty shell; a transient refresh failure (network, 5xx, 429) keeps the session; a reload with an expired access token never fails the application start (a 401 on `/users/me` means "not signed in"); a 401 from the API while signed in triggers the same sign-in.
- **AC-6.2** Given a `POST /login` with a missing or stale CSRF token, then the browser returns to `/login?error=EXPIRED` with a translated message; a 403 or a server error on the authorization server shows a branded page with a link to the app instead of the default error page.
- **AC-6.3** Given a sign-in at `/login` without a pending authorization request, then the browser lands on the app; `GET /login` while signed in and `GET /` redirect to the app; only an interrupted `/oauth2/authorize` request is resumed after login, any other saved request is dropped.
- **AC-6.4** Given a password reset with the current password, then it is refused with 422 `password-same-as-current` and the reset page explains it.
- **AC-6.5** Given a password reset or a "not me" revocation, then access tokens issued up to and including the second of the revocation are refused by the API immediately (401), not only at expiry; the revocation instant is recorded after the transaction commits; without Redis the check fails open and logs the outage.
- **AC-6.6** Given a Redis server on `localhost:6379` that is not the compose container, then the dev start script warns before starting the backend.

## 5. Edge cases

- Concurrent registration of the same address: the unique index wins; the second request gets 409.
- Verification of a user who was meanwhile `SUSPENDED` by an administrator: 409, status unchanged.
- Refresh token used after the user became `INACTIVE`/`SUSPENDED`: the token endpoint refuses (`invalid_grant`) — the customizer re-checks status on every refresh.
- Password reset requested for a `PENDING` user: 202, no email (the address is unproven); for unknown addresses: 202, no email.
- Mail server down: registration still succeeds; the email is retried three times by the async sender and the failure is logged; "send again" is available to the user.
- Redis down: login proceeds without counting and the verification/reset email throttles let the request through (fail-open, logged as an error); the health endpoint reports Redis DOWN.
- Clock skew: tokens carry `nbf` with a 60-second leeway on the resource server.
- Login page accessed while already authenticated, or the authorization server's root: redirect to the app.
- Login form posted after the server restarted (stale CSRF token): back to the form with "the page has expired".
- Access token still valid by time after a reset or "not me": refused, because the API compares `iat` with the user's last sign-out-everywhere kept in Redis for the token lifetime.
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
| D-8 | Password rule: at least 10 characters and at most 72 bytes (the BCrypt limit), no composition rules, checked against a small list of common passwords | Length beats composition; openapi's `minLength: 8` is raised | openapi.yml (1.1.2) |
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
| 6.1 | ✓ auth service, 401 interceptor | | | | ✓ expired token reload, revoked refresh in an open tab |
| 6.2, 6.3 | ✓ access-denied handler | | | ✓ direct login, stale form | ✓ direct login |
| 6.4, 6.5 | ✓ reset service, revoker (after commit), validator (boundary) | | | ✓ reset flow, not-me flow | ✓ same password |

Coverage target 85 % lines per `mvn verify`; static analysis clean.

## 9. Manual verification

Container stack instead of dev mode: after `docker compose up -d --build`, every `http://localhost:4200` below is `http://localhost` and every `http://localhost:8080` (except Swagger, which stays on 8080) is also `http://localhost`.

Preconditions (all stories): run `.\tools\dev-up.ps1` from the repository root — it starts the containers, the backend with the `local` profile on `http://localhost:8080` (its own window, about a minute) and the frontend on `http://localhost:4200`, and waits for the health check. By hand the same is `docker compose up -d postgres redis mailpit minio`, then `cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"`, then `cd frontend; npm start`. Mailpit inbox at `http://localhost:8025`. Seed accounts (password `Passw0rd-demo` for all):

| Email | Roles | Organisation |
|-------|-------|--------------|
| `admin@chnu.edu.ua` | `SYSTEM_ADMIN` | ChNU |
| `rector@chnu.edu.ua` | `RECTOR` | ChNU |
| `dean.fmi@chnu.edu.ua` | `DEAN` | Faculty of Mathematics and Computer Science |
| `secretary.fmi@chnu.edu.ua` | `FACULTY_SECRETARY` | Faculty of Mathematics and Computer Science |
| `employee.fmi@chnu.edu.ua` | `EMPLOYEE` | a department of FMI |
| `pending@chnu.edu.ua` | `EMPLOYEE`, status `PENDING` | same department |

### After 1.1.0 (SCRUM-6)

1. Start the backend with the `local` profile. Expected: log shows Flyway at version 16 (V014 auth tables, V015 case-insensitive email index, V016 audit partitions) and no schema validation error. (AC-0.1, 0.2)
2. `GET http://localhost:8080/actuator/health` → `status: UP`, `db` and `redis` UP.
3. In psql (`docker compose exec postgres psql -U postgres award_monitoring`): `\dt oauth2_*` lists three tables; `\d one_time_tokens` and `\d user_devices` show the columns of §6. (AC-0.2)
4. `SELECT email_address, account_status FROM users ORDER BY user_id;` → the six seed accounts, five `ACTIVE`, one `PENDING`. (AC-0.5)
5. Restart with the `test` profile against a scratch database (or run `mvn verify` and read the profile test) → no seed users. (AC-0.5)

### After 1.1.1 (SCRUM-7)

6. Open `http://localhost:4200`. Expected: redirect to `http://localhost:8080/login?...` in Ukrainian. Switch to English with the language link; reload keeps English. (AC-1.1, 1.7)
7. Sign in as `employee.fmi@chnu.edu.ua`. Expected: return to the app; toolbar shows the name and a logout button. (AC-1.2)
8. In DevTools → Network, copy the access token from the `/oauth2/token` response; paste it at `https://jwt.io`: header `alg: RS256`, `kid` present; payload has `roles: ["EMPLOYEE"]`, `org_id`, `org_type: "DEPARTMENT"`. (AC-1.4)
9. Swagger UI `http://localhost:8080/swagger-ui.html` → **Authorize** → tick `openid`/`profile` under *oauth2* → Authorize → sign in in the popup → the dialog shows *Logout* → Close → `GET /api/v1/users/me` → Try it out → Execute → 200 with the profile. Log out of Swagger (Authorize → Logout) → Execute again → 401. (AC-1.3, 1.9)
10. Sign out; the app returns to the login page; the old refresh token fails at `/oauth2/token` (repeat the request from DevTools → `invalid_grant`). (AC-1.8, 1.5)
11. Sign in as `pending@chnu.edu.ua`. Expected: login page says the address is not verified yet. (AC-1.6)

### After 1.1.2 (SCRUM-8)

12. Open `http://localhost:4200/register` (or click «Зареєструватися» under the login form); type `test.user@gmail.com` and leave the field. Expected: inline error «Потрібна адреса в домені chnu.edu.ua». (AC-2.2)
13. Fill `test.user@chnu.edu.ua`, password `correct-horse-battery`, a first and last name, and pick «Кафедра алгебри та інформатики» (departments are grouped by faculty). Submit. Expected: page `/registration-pending`; http://localhost:8025 shows «Підтвердження адреси / Confirm your address» and the letter says the confirmation page asks for the registration password. (AC-2.1, 2.7, 2.8)
14. Click «Увійти» on the pending page and sign in with the new address → login page says «Адресу ще не підтверджено…». (AC-1.6)
15. Open the link from the Mailpit message → `/verify-email?token=…` asks for the registration password. Type `wrong-password-1` → «Пароль не збігається з обраним під час реєстрації», the form stays. Type `correct-horse-battery` → «Адресу … підтверджено». Open the same link again, enter the password → «Посилання недійсне, прострочене або вже використане» with a button to request a new one. (AC-2.5)
16. Sign in with the new account → app opens. In psql: `SELECT role_type FROM user_roles WHERE user_id = (SELECT user_id FROM users WHERE email_address = 'test.user@chnu.edu.ua');` → `EMPLOYEE`. (AC-2.1)
17. Register the same address again → «Обліковий запис із цією адресою вже існує». On `/registration-pending` press «Надіслати ще раз» twice within a minute → second time «Лист уже надсилали нещодавно». (AC-2.3, 2.6)

### After 1.1.3 (SCRUM-9)

18. Sign in as `test.user@chnu.edu.ua` in a second browser (or a private window) and keep it open. In the first browser open `http://localhost:8080/login` and click «Забули пароль?» → `http://localhost:4200/forgot-password`. Enter `test.user@chnu.edu.ua`, submit. Expected: «Якщо адресу … зареєстровано, лист із посиланням уже в дорозі. Посилання дійсне 1 годину.»; Mailpit shows «Скидання пароля / Password reset». Go back, submit the same address again within a minute → same message, no second email. Submit `nobody@chnu.edu.ua` → same message, no email. (AC-3.1, 3.4)
19. Open the link from Mailpit → `/reset-password?token=…`. Type `short` → inline «Пароль має бути щонайменше 10 символів…»; type `password123` → «Цей пароль надто поширений»; type `staple-battery-horse` → «Пароль змінено. Увійдіть із новим паролем.» (AC-3.2, 3.4)
20. Open the same link again and submit a password → «Посилання недійсне, прострочене або вже використане» with «Запитати нове посилання». (AC-3.2)
21. In the second browser reload the app after 15 minutes at most (or click «Вийти» and «Увійти»): the old session no longer refreshes and the login page appears instead of a silent re-login. Sign in with `correct-horse-battery` → «Невірна адреса або пароль»; sign in with `staple-battery-horse` → app opens. (AC-3.2, 3.3)

### After 1.1.4 (SCRUM-10)

22. Sign in as `employee.fmi@chnu.edu.ua` with a wrong password five times. Expected: the first four say «Невірна адреса або пароль», the fifth «Забагато невдалих спроб. Спробуйте пізніше.»; the correct password is refused with the same message. Mailpit shows «Обліковий запис заблоковано / Account locked» addressed to `admin@chnu.edu.ua` with the address, IP and time (in the container stack the IP is `172.25.0.1`, the host as nginx sees it). (AC-4.1, 4.4)
23. In psql: `SELECT action_type, host(ip_address), user_agent, correlation_id, created_at FROM audit_logs WHERE entity_type = 'AUTHENTICATION' ORDER BY created_at DESC LIMIT 10;` → `LOGIN_FAILED` ×6 and `ACCOUNT_LOCKED`, every row with IP, user agent and correlation id. (AC-4.3)
24. Restart the backend (Ctrl+C in its window, run `.\tools\dev-up.ps1` again) and retry the correct password → still locked. Unlock without waiting: `docker compose exec redis redis-cli DEL auth:lock:employee.fmi@chnu.edu.ua`, sign in → app opens; psql shows `LOGIN_SUCCESS`. Sign out → `LOGOUT`. (AC-4.5, 4.3)
25. Run `for i in $(seq 1 125); do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/oauth2/token -d grant_type=refresh_token -d refresh_token=x -d client_id=award-web; done` → the first 120 lines are `400`, the rest `429` (the `local` profile allows 120 requests a minute so the browser test suite fits; the container stack and production keep 20 — there, run 25 requests against `http://localhost`). Then open `http://localhost:8080/login` in the browser within the same minute → «Забагато запитів» page (429). One minute later the login page works again. (AC-4.2)
26. `\d+ audit_logs` in psql → partitions `audit_logs_2026_07` … `audit_logs_2027_12` plus `audit_logs_default`. (AC-4.6)

### After 1.1.5 (SCRUM-11)

27. Sign in as `dean.fmi@chnu.edu.ua` at `http://localhost:4200` in Chrome (first sign-in from this browser since the feature; to repeat later run `docker compose exec postgres psql -U postgres award_monitoring -c "delete from user_devices"`). Expected: Mailpit shows «Новий вхід до облікового запису / New sign-in to your account» with `Browser: Chrome`, `Operating system: Windows`, `IP: 127.0.0.1` (or `0:0:0:0:0:0:0:1`), the time and a link `http://localhost:4200/security/not-me?token=…`. Sign out and in again → no second email; psql `select browser, last_used_at from user_devices` shows one row with a fresh `last_used_at`. (AC-5.1, 5.2)
28. Sign in as the same user from Firefox or Edge (or in Chrome DevTools → Network conditions → untick "Use browser default" user agent and pick Firefox). Expected: a second email naming the other browser. Open its link. Expected: page «Це був ваш вхід?» with the button «Це був не я»; the language toggle switches it to "Was this sign-in yours?" / "This was not me". Click the button. Expected: «Доступ відкликано…»; opening the link again and clicking → «Посилання недійсне…» with a "Reset password" link. In the first browser: reload the app → still open (the access token lives up to 15 minutes), then open a new tab on `http://localhost:4200` after closing the old one → login page instead of an automatic sign-in; the old password answers «Невірна адреса або пароль»; Mailpit shows «Скидання пароля / Password reset»; its link sets a new password and the login works again. psql: `select action_type from audit_logs where action_type = 'SECURITY_REVOKE'` → one row; `user_devices` is empty for the user until the next sign-in. (AC-5.3, 5.4)

### After 1.1.6 (SCRUM-18)

29. Repeat step 21 with a second browser signed in: after the reset, reload the app in the second browser at once. Expected: the login page (or, within 15 minutes, a reload of an already open page shows the login page instead of a blank screen — never an empty shell). In the reset page, first submit the old password → «Новий пароль має відрізнятися від поточного». (AC-6.1, 6.4, 6.5)
30. Open `http://localhost:8080/login`, restart the backend (Ctrl+C in its window, `.\tools\dev-up.ps1`), then submit the stale form. Expected: the form again with «Сторінка застаріла. Спробуйте ще раз.»; signing in from that form lands on `http://localhost:4200` with the app open. Open `http://localhost:8080/login` and `http://localhost:8080/` while signed in → both redirect to the app. (AC-6.2, 6.3)
31. In the browser app, exhaust the limit (step 25) and then submit the "forgot password" form within the same minute. Expected: «Не вдалося… Спробуйте пізніше» (the 429 reaches the app with its CORS headers), not «Сервер недоступний». (AC-6.2)
32. With the dev backend running, `docker compose exec redis redis-cli KEYS 'auth:*'` after a lock-out shows the `auth:lock:` key — the backend and the container are the same Redis; `.\tools\dev-up.ps1` prints a warning when they are not. (AC-6.6)

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

## 12. Validation (2026-09-21, `develop` at 05245b4)

Gates on `develop`: `mvn verify` — 124 unit, 22 integration (`*IT`, TestContainers Postgres 17 + Redis 7), 20 functional (`*FT`, REST-assured against the booted application, Mailpit container), 97.5 % lines, Checkstyle 0, PMD 0, SpotBugs 0. Frontend: ESLint clean, 39 Vitest specs, Playwright 10/10 (`auth`, `registration`, `password-reset`, `device-notification`). Container stack `docker compose up -d --build`: all six services healthy; the flow below was driven through nginx.

### AC evidence

Test methods are named after the AC they prove (`ac21_…`); a story's functional test covers the whole flow, unit and slice tests the branches.

| AC | Evidence | Result |
|----|----------|--------|
| 0.1 | `SchemaIT#ac01_latestMigrationIsApplied`, `UserRepositoryIT#ac01_persistsEveryMappedColumn`, `ApplicationContextIT` | pass |
| 0.2 | `SchemaIT#ac02_authTablesExist/…HaveExpectedConstraints/…UseTextAndTimestamptz`, `AuthRepositoriesIT#ac02_*` (3) | pass |
| 0.3 | `UserRepositoryIT#ac03_findsUserByEmailIgnoringCase` | pass |
| 0.4 | `UserRoleRepositoryIT#ac04_returnsOnlyRolesValidToday`, `UserRoleTest#ac04_*` (2) | pass |
| 0.5 | `DevSeedIT#ac05_*` (2), `SchemaIT#ac05_noSeedUsersOutsideLocalProfile` | pass |
| 0.6 | `OpenApiContractTest#ac06_userSchemaMatchesTheProfileResponse` (added at validation); also compared by hand with the live `/v3/api-docs` `UserProfileResponse`: same nine properties, seven-value `status`, `roles[]` with `organization` | pass (finding F-1) |
| 1.1 | `AuthenticationFlowFT#ac11_anonymousAuthorizeRequestIsSentToTheLoginPage`, `RegisteredClientSeederTest#ac11_*`; `auth.guard.spec`, `auth.service.spec`; E2E `auth.spec` | pass |
| 1.2 | `AuthenticationFlowFT#ac12_ac14_loginIssuesPkceCodeExchangeableForSignedTokens`, `#ac12_codeExchangeRequiresTheMatchingVerifier`, `PublicClientRefreshAuthenticationTest#ac12_*`, `RotatingRefreshTokenGeneratorTest#ac12_*`; `callback.component.spec`, `shell.component.spec`; E2E `auth.spec` | pass |
| 1.3 | `UserControllerTest#ac13_meReturnsTheProfileOfTheTokenSubject`, `#ac13_meWithoutTokenIsUnauthorizedProblemDetails`; `home.component.spec` | pass |
| 1.4 | `AuthenticationFlowFT#ac12_ac14_…`, `JwkKeysTest#ac14_*` (2), `TokenClaimsCustomizerTest#ac14_*` (2), `JwtAuthorityConverterTest#ac14_*`, `RolePermissionsTest#ac14_*` (2) | pass |
| 1.5 | `AuthenticationFlowFT#ac15_refreshRotatesAndReuseRevokesTheWholeAuthorization`, `#ac15_suspendedAccountCannotRefresh…`, `RefreshTokenReuseGuardTest#ac15_*` (2), `PublicClientRefreshAuthenticationTest#ac15_*` (2) | pass |
| 1.6 | `AuthenticationFlowFT#ac16_pendingAndSuspendedUsersAreRefusedWithTheirStatus`, `AccountStatusCheckerTest#ac16_*` (2), `LoginFailureHandlerTest#ac16_*`; E2E `auth.spec` | pass |
| 1.7 | `AuthenticationFlowFT#ac17_loginPageIsUkrainianByDefaultAndEnglishOnRequest`; `language.service.spec`, `shell.component.spec`; E2E `auth.spec` | pass |
| 1.8 | `AuthenticationFlowFT#ac18_revokedRefreshTokenCannotBeUsedAndLogoutEndsTheSession`, `PublicClientRefreshAuthenticationTest#ac18_*`; `auth.service.spec`; E2E `auth.spec` | pass |
| 1.9 | `AuthenticationFlowFT#ac19_publicPathsAreOpenAndTheApiRequiresAToken` | pass |
| 2.1 | `RegistrationFlowFT#ac21_ac25_registerVerifyAndSignIn`, `RegistrationServiceTest#ac21_*`, `AuthControllerTest#ac21_*`, `AuthMailerTest#ac21_*`, `OneTimeTokenServiceTest#ac21_*`, `PasswordPolicyTest#ac21_*`; `register.component.spec`; E2E `registration.spec` | pass |
| 2.2 | `RegistrationFlowFT#ac22_ac23_ac24_registrationRefusals`, `RegistrationServiceTest#ac22_*`, `AuthControllerTest#ac22_*`; E2E `registration.spec` | pass |
| 2.3 | `RegistrationFlowFT#ac22_ac23_ac24_…`, `RegistrationServiceTest#ac23_existingAddressIsAConflict`, `#ac23_concurrentRegistrationLosingTheRaceIsAConflictToo`; `register.component.spec` | pass |
| 2.4 | `RegistrationFlowFT#ac22_ac23_ac24_…`, `RegistrationServiceTest#ac24_facultyOrInactiveOrganisationIsRefused` | pass |
| 2.5 | `RegistrationFlowFT#ac21_ac25_…`, `RegistrationServiceTest#ac25_*` (4), `OneTimeTokenServiceTest#ac25_*` (2), `AuthControllerTest#ac25_*`; `verify-email.component.spec`; E2E `registration.spec` | pass |
| 2.6 | `RegistrationFlowFT#ac26_resendIsThrottledAndSilentAboutUnknownAddresses`, `RegistrationServiceTest#ac26_*`, `#ac25_ac26_*`, `OneTimeTokenServiceTest#ac26_*`, `AuthControllerTest#ac26_*`; `registration-pending.component.spec`, `verify-email.component.spec`; E2E `registration.spec` | pass |
| 2.7 | `RegistrationFlowFT#ac27_departmentsAreListedWithTheirFacultyWithoutAToken`; `register.component.spec` | pass |
| 2.8 | `register.component.spec` (validation mirrors D-8 and the domain rule); E2E `registration.spec` in both languages | pass |
| 3.1 | `PasswordResetFlowFT#ac31_ac32_ac33_…`, `PasswordResetServiceTest#ac31_*` (3), `AuthControllerTest#ac31_*`, `AuthMailerTest#ac31_*`; `forgot-password.component.spec`; E2E `password-reset.spec` | pass |
| 3.2 | `PasswordResetFlowFT#ac31_ac32_ac33_…`, `PasswordResetServiceTest#ac32_*` (4), `AuthorizationRevokerTest#ac32_*`, `RetryRequestSessionExpiredStrategyTest#ac32_*` (2), `AuthControllerTest#ac32_*`; `reset-password.component.spec`; E2E `password-reset.spec` | pass |
| 3.3 | `PasswordResetFlowFT#ac31_ac32_ac33_requestResetSignInWithTheNewPasswordAndLoseOldSessions`; E2E `password-reset.spec` | pass |
| 3.4 | `forgot-password.component.spec`, `reset-password.component.spec`; E2E `password-reset.spec` | pass |
| 4.1 | `LoginProtectionFT#ac41_ac43_ac44_ac45_fiveFailuresLockTheAccountNotifyAdminsAndAreAudited`, `LoginAttemptServiceTest#ac41_*` (5), `LoginFailureHandlerTest#ac41_*` (4), `LockedAccountCheckerTest#ac41_*` (2) | pass |
| 4.2 | `LoginProtectionFT#ac42_aBurstFromOneAddressIsRefusedWithRetryAfter`, `RateLimitFilterTest#ac42_*` (3) | pass |
| 4.3 | `LoginProtectionFT#ac41_ac43_…`, `#ac43_everyResponseCarriesACorrelationId`, `AuditLogRepositoryIT#ac43_ac46_*`, `AuditServiceTest#ac43_*` (2), `ClientRequestTest#ac43_*` (2), `CorrelationIdFilterTest#ac43_*` (2), `LoginSuccessListenerTest#ac43_*` (2), `LoginFailureHandlerTest#ac41_ac43_*` | pass |
| 4.4 | `LoginProtectionFT#ac41_ac43_ac44_ac45_…`, `LoginAttemptServiceTest#ac41_ac44_*`, `AuthMailerTest#ac44_*` (2) | pass |
| 4.5 | `LoginProtectionFT#ac41_ac43_ac44_ac45_…` (lock TTL read from Redis), `LoginAttemptServiceTest#ac41_ac45_lockStateIsReadFromRedis…` | pass |
| 4.6 | `SchemaIT#ac46_auditLogsHasMonthlyPartitionsThroughDecember2027`, `AuditLogRepositoryIT#ac43_ac46_rowLandsInTheMonthPartition…` | pass |
| 5.1 | `DeviceNotificationFT#ac51_ac52_ac53_…`, `DeviceServiceTest#ac51_*`, `DeviceFingerprintTest#ac51_*` (3), `LoginSuccessListenerTest#ac43_ac51_*`, `#ac51_aFailedDeviceRecordDoesNotBreakTheLogin`, `AuthMailerTest#ac51_ac53_*`; E2E `device-notification.spec` | pass |
| 5.2 | `DeviceNotificationFT#ac51_ac52_ac53_…` (stale row refreshed, no second email), `DeviceServiceTest#ac52_*`, `DeviceFingerprintTest#ac51_ac52_*`; E2E `device-notification.spec` | pass |
| 5.3 | `DeviceNotificationFT#ac51_ac52_ac53_…`, `DeviceServiceTest#ac53_*` (2), `OneTimeTokenServiceTest#ac53_*`, `AuthControllerTest#ac53_*`; `not-me.component.spec`; E2E `device-notification.spec` | pass |
| 5.4 | `not-me.component.spec`; E2E `device-notification.spec` (uk and en) | pass |

### Edge cases (§5)

| Edge case | Evidence | Result |
|-----------|----------|--------|
| Concurrent registration of the same address | `RegistrationServiceTest#ac23_concurrentRegistrationLosingTheRaceIsAConflictToo` | covered |
| Verification of a user meanwhile `SUSPENDED` | `RegistrationServiceTest#verifyingASuspendedAccountDoesNotReactivateIt` | covered |
| Refresh after `INACTIVE`/`SUSPENDED` | `AuthenticationFlowFT#ac15_suspendedAccountCannotRefresh…`, `RefreshTokenReuseGuardTest#suspendedOrDeletedAccountsCannotRefresh…` | covered |
| Reset for `PENDING` or unknown address | `PasswordResetServiceTest#ac31_unknownAndPendingAddressesAreAcceptedSilently`, `PasswordResetFlowFT` | covered |
| Mail server down | `AuthMailerTest#retriesTwiceThenGivesUp` (three attempts, pauses 2 s and 5 s); "send again" on the pending page | covered |
| Redis down | `LoginAttemptServiceTest#redisOutageFailsOpen`, `RateLimitFilterTest#redisOutageDoesNotLimit`, `RefreshTokenReuseGuardTest` — login, limit and refresh fail open. The resend and reset throttles (`RegistrationService.throttle`, `PasswordResetService.request`) did not: a Redis outage answered 500 there | fixed in the refactor PR (finding F-2) |
| Clock skew | Library default: `JwtTimestampValidator` allows 60 s; no explicit configuration or test | accepted, not tested |
| Login page while already authenticated | Not implemented: `/login` renders the form again; the SPA never links to it directly and an authenticated browser at `/oauth2/authorize` receives a code without seeing the form | open (finding F-3, cosmetic) |
| One account per address | Unique index `users(lower(email_address))` (V015); `UserRepositoryIT#ac03_*` | covered |

### Security checklist

| OWASP | Control | Where |
|-------|---------|-------|
| A01 Broken access control | Stateless resource-server chain: `/api/**` requires a bearer access token except the listed registration/reset/revoke/organisation paths; `/actuator/**` beyond health/info/prometheus needs `ROLE_SYSTEM_ADMIN`; id tokens are rejected as API credentials (`token_use=access`) | `SecurityConfig.apiSecurityFilterChain`, `AccessTokenDecoder`, `AuthenticationFlowFT#ac19_*`, `#ac15_…IdTokenIsNotABearerToken` |
| A02 Cryptographic failures | RS256 with a configured or generated RSA key and `kid`; BCrypt strength 12; one-time tokens are 32 random bytes from `SecureRandom`, only their SHA-256 stored; refresh tokens rotate, rotated values kept as hashes in Redis; the revoke path replaces the password hash with a random one | `JwkKeys`, `SecurityConfig.passwordEncoder`, `OneTimeTokenService`, `RefreshTokenReuseGuard`, `DeviceService.revoke` |
| A03 Injection | JPA with bound parameters everywhere; the two JPQL bulk updates and the one JDBC delete use placeholders; Bean Validation on every request body (`@Email`, `@Size`, `@NotBlank`), user-agent capped at 500 chars before parsing; Thymeleaf escapes the login page | `OneTimeTokenRepository`, `AuthorizationRevoker`, DTOs in `auth/dto`, `ClientRequest.from` |
| A07 Identification and authentication failures | PKCE S256 enforced for the public client; 5 failures / 15 min → 30-min lock in Redis, unknown addresses locked identically (no enumeration); 20 requests/min per client address on the authentication endpoints; account status checked after the password (no status oracle); neutral 202/409 answers on registration and reset; verification requires the registration password; new-device email with a revoke link; every event audited with IP, agent and correlation id | `RegisteredClientSeeder`, `LoginAttemptService`, `RateLimitFilter`, `AccountStatusChecker`, `LoginFailureHandler`, `RegistrationService.verify`, `DeviceService`, `AuditService` |

Known accepted gaps are the tracker's "Security review follow-ups" 1, 3, 5–8 (refresh-token values at rest, deployment hardening, `/userinfo`, pending accounts blocking an address, in-memory session registry, shared-NAT request budget).

### Integration check

- `docker compose up -d --build` from a clean image build: `award-postgres`, `award-redis`, `award-mailpit`, `award-minio`, `award-backend`, `award-frontend` all healthy.
- Migrations on an empty database: every `*IT` run starts a fresh Postgres 17 container and applies V001–V016 plus the repeatable seeds (`SchemaIT#ac01_latestMigrationIsApplied`).
- `openapi.yml` vs live `/v3/api-docs`: the eight controller endpoints of the feature match path by path (`/auth/register`, `/auth/verify-email`, `/auth/resend-verification`, `/auth/password-reset/request`, `/auth/password-reset/confirm`, `/auth/security/revoke`, `/organizations`, `/users/me`); `/oauth2/*`, `/connect/logout`, `/.well-known/openid-configuration` and `/actuator/health` are provided by the library and are documented only in the spec; `PATCH /users/me` belongs to Feature 1.3. `User` and `UserProfileResponse` carry the same properties.
- Through nginx (`http://localhost`): discovery advertises issuer `http://localhost` and `S256`; register 201 / other domain 422 / duplicate (upper case) 409 / faculty id 422; verify with the wrong password 403, right password 200 `ACTIVE`, replay 410; resend 429 within the minute; reset request 202 for known and unknown addresses; revoke with a bad token 410; PKCE login → RS256 access token with `kid`, `iss`, `email`, `roles`, `org_type`, `token_use`; `/users/me` 200; refresh rotates, the old token and then the rotated one answer `invalid_grant`; five wrong passwords → `LOCKED`, the right one refused, admin email to `admin@chnu.edu.ua`; audit rows `EMAIL_VERIFIED`, `LOGIN_SUCCESS`, `LOGIN_FAILED` ×6, `ACCOUNT_LOCKED`, `PASSWORD_RESET_REQUESTED`; `user_devices` row `Chrome / Windows / 172.25.0.1`; Mailpit holds the verification, reset and new-sign-in messages.

### Findings

| # | Finding | Action |
|---|---------|--------|
| F-1 | AC-0.6 had no automated test | `OpenApiContractTest` added in the validation PR: the `User` schema of `openapi.yml` must list exactly the properties of `UserProfileResponse` and the values of `AccountStatus` |
| F-2 | Resend and reset throttles answered 500 when Redis is down, unlike login, limit and refresh | Fixed in the refactor PR: shared fail-open guard |
| F-3 | `/login` opened by an already authenticated browser shows the form | Fixed in 1.1.6 together with F-7 |
| F-5 | Step 21: after the silent refresh failed (revoked refresh token) the SPA rendered an empty shell, and a reload within 10 minutes of expiry failed the application start (`clockSkewInSec` default 600 s made the SPA call `/users/me` with an expired token) | Fixed in 1.1.6 (AC-6.1) |
| F-6 | Step 24: a login form rendered before a backend restart posted a stale CSRF token and got the default 403 page | Fixed in 1.1.6 (AC-6.2) |
| F-7 | Steps 27–28: signing in at `/login` directly ended on a 404 for `/` (no saved request); same root cause as F-3 | Fixed in 1.1.6 (AC-6.3), closes F-3 |
| F-8 | Step 19: the reset accepted the previous password | Fixed in 1.1.6 (AC-6.4) |
| F-9 | Step 21: a second browser stayed signed in for up to 15 minutes after the reset (documented, but unwanted) | Fixed in 1.1.6 (AC-6.5): tokens issued before the revocation are refused at once |
| F-11 | The browser test suite exceeded the 20-requests-a-minute budget once it grew past ten tests, and the 429 came back without CORS headers, so the app reported «Сервер недоступний» | Fixed in 1.1.6: the refusal carries the CORS headers; the `local` profile allows 120 a minute (container stack and production keep 20) |
| F-10 | Step 24: `redis-cli DEL` through the container had no effect because a `redis-server` inside WSL was answering `localhost:6379` for the dev backend | Environment, not code; WSL server disabled; dev script warns (AC-6.6) |
| F-4 | Refactor sweep (21 items): duplicated SHA-256 helper, link building, redeem-or-410, password check, email normalisation; narrative comment in `CorrelationIdFilter`; Angular token-link lifecycle copied in three components; e2e helpers duplicated | Worth-it items in the `refactor(auth)` PR; the rest listed in the tracker's technical notes |

Verdict: **PASSED WITH NOTES** — pending the author's run of §9 in the browser.

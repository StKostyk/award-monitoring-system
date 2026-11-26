# Feature 9.1: Security Hardening

> **Epic**: 9 — Production Hardening (SCRUM-128)
> **Sprint**: 8 (2026-11-02 → 2026-11-08); 9.1.1 may start in sprint 7, the stretch 9.1.3 in sprint 9
> **Points**: 10 (two stories) + 8 for the stretch 9.1.3
> **Status**: Approved 2026-10-29
> **Author**: Stefan Kostyk
> **Governing docs**: EPIC-09 tracker (scope, decisions, deviations 2, 3, 5), AUTHENTICATION_AUTHORIZATION §5, §9 (rows "Brute force", "Token values at rest", "Sign-out everywhere", "Refresh while the account is blocked"), THREAT_MODEL S-AUTH-01, D-AUTH-01, SECURITY_ARCHITECTURE §OWASP A07, DATA_DICTIONARY (Spring Authorization Server tables), ADR-021 (one instance), ADR-005 (Redis), PRD 5.3 note 2, PRD 6.2 AC-1.7 (`authorizations` policy), `openapi.yml` (`TooManyRequests`, `/auth/register` description), `AuthorizationStoreConfig`, `RefreshTokenReuseGuard`, `AuthorizationRevoker`, `AuthorizationRetention`, `RateLimitFilter`, `FixedWindowCounter`, `LoginAttemptService`, `RequestThrottle`, `LoginSessionConfig`

## 1. Problem and personas

Two trade-offs made for the defense matter once the system answers on a public address.

- **Token values in clear text.** `oauth2_authorization` keeps authorization codes, access, id and refresh tokens
  exactly as issued (AUTHENTICATION_AUTHORIZATION §9; the V014 table comment claims the opposite). A database dump,
  a backup copy (9.2.2) or a read-only SQL injection hands out working refresh tokens for up to their lifetime.
- **Rate limits keyed by address only.** Every authentication endpoint shares 20 requests a minute per client
  address. The university reaches the internet through a few NAT addresses, so on a Monday morning one faculty's
  sign-ins exhaust the budget for everybody behind the same address, while one person with a script and their own
  address still gets 20 attempts a minute. Signed-in calls have no limit at all, including the PDF report and the
  exports, each of which renders a file and writes an audit row (PRD 5.3 note 2).

Personas:

- **Employee on the campus network**: signs in at the same time as colleagues and must not see "too many
  requests" because of them.
- **System administrator**: wants a leaked database copy to be useless for signing in, and one misbehaving client
  to be slowed down without affecting the rest; sees refusals in the metrics.
- **Attacker** (THREAT_MODEL S-AUTH-01, D-AUTH-01): guesses passwords, replays a stolen refresh token, or floods
  the expensive endpoints.

## 2. Scope

**In (this feature)**

- Every token value in `oauth2_authorization` stored as a SHA-256 hash and looked up by hash; existing rows
  converted in place, so nobody is signed out (9.1.1)
- Indexes on the hashed value columns that the grants look up (9.1.1)
- Authentication endpoints: a per-address budget sized for one NAT, plus a per-account budget on the sign-in form
  (9.1.2)
- Signed-in API: a per-user budget on `/api/v1/**`, and a tighter per-user budget on the report and the exports
  (9.1.2)
- One metric for every refusal, by limit group (9.1.2)
- Stretch: a backend-for-frontend, so the browser holds an HttpOnly session cookie and never a token (9.1.3)

**Out (where it goes)**

- Hashing `state` (the consent flow is disabled, `requireAuthorizationConsent(false)`; the value is no credential):
  stays as the library writes it
- Encryption of the `attributes` and metadata columns (no credential inside; the id token claims hold the name and
  address that `users` already holds in clear)
- CAPTCHA and IP blocking lists (THREAT_MODEL S-AUTH-01 "secondary controls"): the per-account lockout and the
  limits cover the demo; icebox
- Limits shared across instances beyond Redis: one instance (ADR-021); the counters are already in Redis
- Spring Session on Redis, ShedLock: out of the epic (tracker decision 1)
- A WAF or provider DDoS protection (SECURITY_ARCHITECTURE "External" zone): not part of the Compose demo

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-129 (#370) | 9.1.1 Refresh tokens and authorization codes hashed at rest | 5 | no | — |
| SCRUM-130 (#371) | 9.1.2 Rate limits keyed by account and address | 5 | no | — |
| SCRUM-135 (#376) | 9.1.3 Backend-for-frontend with an HttpOnly session cookie (stretch) | 8 | no | 9.1.1, 9.1.2; design note first |

Order: 9.1.1 → 9.1.2, both backend only. 9.1.3 starts only after 9.2.4 is deployed and only if it can merge
before 2026-12-01 (decision 9); otherwise it moves to the icebox.

## 4. Acceptance criteria

**Audit action types added**: none. Refusals by a limit are counted in metrics and logged, not audited (decision 7).

### 9.1.1 Refresh tokens and authorization codes hashed at rest (SCRUM-129)

- **AC-1.1** (Hashed on save) Given any grant that saves an authorization (authorization code, token exchange, refresh, revocation, sign-out), when the row is written, then every non-null `authorization_code_value`, `access_token_value`, `oidc_id_token_value`, `refresh_token_value`, `user_code_value` and `device_code_value` is `sha256:` followed by 64 lower-case hex digits of the SHA-256 of the issued value; no column of the row contains an issued value.
- **AC-1.2** (Saved once, hashed once) Given an authorization read from the database and saved again (refresh, revocation of one token, invalidation of the used code), when it is written, then values that already carry the `sha256:` prefix are written unchanged, so the stored hash of the used authorization code, the invalidated refresh token and the id token is not hashed twice.
- **AC-1.3** (Lookup by hash) Given an issued value, when the server looks the authorization up by token (refresh grant, revocation `/oauth2/revoke`, introspection, the id token hint of the sign-out endpoint, any token type or none), then the lookup hashes the presented value and finds the row; a presented value that itself starts with `sha256:` is hashed like any other and finds nothing.
- **AC-1.4** (Flows unchanged) Given the browser application, when an employee signs in, the access token expires and is refreshed, the employee signs out, and signs in again, then every step behaves as before 9.1.1: the refresh rotates the refresh token, the old one is refused with `invalid_grant`, and presenting it again revokes the whole authorization (`RefreshTokenReuseGuard`).
- **AC-1.5** (Revocations unchanged) Given hashed rows, when a password reset, a "this was not me" link, an address change, a suspension at the next refresh or the `authorizations` retention policy removes authorizations, then they remove the same rows as before (they select by principal or by expiry, never by value).
- **AC-1.6** (Existing rows) Given V050, when it runs on a database with clear-text rows, then every non-null value column of every row is replaced by its `sha256:` hash computed in SQL, which equals the hash the application computes for the same value; a user signed in before the migration refreshes afterwards without signing in again; the migration is idempotent on rows already hashed.
- **AC-1.7** (Indexes) Given V050, then `oauth2_authorization` has indexes on `authorization_code_value`, `access_token_value`, `oidc_id_token_value` and `refresh_token_value`, and a lookup by refresh token value uses its index (`EXPLAIN` in the IT shows an index scan once the table holds enough rows for the planner to choose one).
- **AC-1.8** (Documentation) Given the story's PR, then AUTHENTICATION_AUTHORIZATION §9 "Token values at rest" states the hashing, the prefix and the conversion; the DATA_DICTIONARY paragraph on the authorization tables says values are stored as SHA-256 hashes; V050 corrects the table comment of V014.

### 9.1.2 Rate limits keyed by account and address (SCRUM-130)

- **AC-2.1** (Address budget for one NAT) Given `app.auth.protection.requests-per-minute` (default raised from 20 to 600), when one client address sends more requests a minute than the budget to `/login`, `/oauth2/token` or `/api/v1/auth/*`, then the request over the budget answers 429 with `Retry-After` and `urn:awards:problem:too-many-requests` (browsers get the 429 page), exactly as today; the local profile raises its value to 1200 for the browser test suite.
- **AC-2.2** (Account budget at sign-in) Given `app.auth.protection.account-requests-per-minute` (default 10), when the sign-in form is posted more often than that within a minute with the same `username` (lower-cased, trimmed), from one address or from many, then the posts over the budget answer the 429 page with `Retry-After` before the password is checked, and do not count as failed sign-ins for the lockout; another account behind the same address still signs in.
- **AC-2.3** (Lockout unchanged) Given five wrong passwords for one account within 15 minutes, then the account is locked for 30 minutes as before (`LoginAttemptService`); the account budget is checked first, so a script that hits the budget locks nobody faster.
- **AC-2.4** (Per-user API budget) Given `app.rate-limit.api-requests-per-minute` (default 600), when one signed-in user sends more requests a minute to `/api/v1/**` (except `/api/v1/public/**` and `/api/v1/auth/**`, which keep their own limits), counted by the user id of the access token, then the request over the budget answers 429 Problem Details with `Retry-After` and the CORS headers; requests without a valid token are not counted here (they answer 401 as today).
- **AC-2.5** (Expensive endpoints) Given `app.rate-limit.export-requests-per-minute` (default 5), when one signed-in user calls `GET /analytics/university/report`, `GET /analytics/units/{id}/export` and `GET /awards/{id}/audit-trail/export` more often than that within a minute in total, then the call over the budget answers 429 before any file is rendered or audit row written; `/users/me/export` keeps its own throttle of 6.1.
- **AC-2.6** (Independence) Given one user over the API budget, then other users behind the same address are not affected; given one address over the authentication budget, then signed-in API calls from that address still pass.
- **AC-2.7** (Redis down) Given Redis unavailable, then no limit of this story refuses a request (as the existing limits do), and the failure is logged once per minute, not per request.
- **AC-2.8** (Metric) Given a refusal by any limit (authentication address, sign-in account, public API, API user, exports, uploads), then `award_rate_limited_total{limit}` increases, with `limit` one of `auth-address`, `auth-account`, `public`, `api-user`, `export`, `upload`; the log line names the limit and the path, never the address or the account.
- **AC-2.9** (Documentation) Given the story's PR, then AUTHENTICATION_AUTHORIZATION §9 has a "Rate limits" row listing every limit with its key, budget and property; `openapi.yml` states the new budgets in the `/auth/register` description and the API info, and the three expensive operations list 429; DEMO_DEPLOYMENT §1 lists the new properties; the roadmap row of tracker deviation 2 is corrected at the documentation sync.

### 9.1.3 Backend-for-frontend with an HttpOnly session cookie (SCRUM-135)

Stretch story (decision 9).

- **AC-3.1** (No token in the browser) Given the browser application, when an employee signs in and works, then neither `sessionStorage`, `localStorage` nor any script-readable cookie holds an access, id or refresh token; the session is an HttpOnly, Secure, SameSite=Lax cookie.
- **AC-3.2** (Same rules) Given a cookie-authenticated API request, then it reaches the same `@access` rules with the same authorities and organisation scopes as a bearer token today; bearer tokens keep working for Swagger and the tests.
- **AC-3.3** (Refresh behind the server) Given an expired access token, when the browser calls the API, then the backend refreshes with the stored refresh token and the call succeeds without a redirect; a refused refresh (blocked account, reuse) ends the session and the next call answers 401, which sends the browser to sign-in.
- **AC-3.4** (CSRF) Given a cookie-authenticated state-changing request (`POST`, `PUT`, `PATCH`, `DELETE`) without a valid CSRF token, then it answers 403; the Angular application sends the token automatically.
- **AC-3.5** (Sign-out) Given sign-out, then the session ends, its authorization is revoked, and the next API call with the old cookie answers 401; "sign out everywhere" (password reset, revoke link, address change) still ends every session within one access-token lifetime.

The contract of 9.1.3 (client registration, endpoints, cookie and CSRF settings, the frontend changes) is settled in a
design note before the story starts (decision 9); the ACs above are the outcome it must reach.

## 5. Edge cases

| Case | Expected |
|------|----------|
| A refresh token issued before V050 and presented after it | Found by its hash (AC-1.6); rotated as usual |
| V050 runs while the server issues tokens (rolling start) | Not possible: Flyway runs before the server accepts requests; one instance (ADR-021) |
| An authorization saved by code that bypasses the service (direct SQL in tests) | Only tests insert directly; they insert hashed values (`RetentionIT` adjusted) |
| A refresh token reused after rotation | `RefreshTokenReuseGuard` keys its Redis memory by the SHA-256 hex of the presented value, independently of the column hash; unchanged |
| Hash collision between token types | `findByToken` with a type looks only in that type's column; without a type the library's OR query could match across columns only if two values hash alike: negligible |
| Sign-in form posted without `username` | Counted under the empty account key; the address budget still applies |
| `username` differing only in case or surrounding spaces | Same account key (lower-cased, trimmed), like the lockout |
| A user hits the API budget in the middle of a form save | 429 Problem Details; the frontend shows the existing "too many requests" message and keeps the form; the retry after `Retry-After` succeeds |
| Swagger UI or the e2e suite bursts | Local profile raises authentication to 1200, the account budget to 100 and the API budget to 6000; the container and production stacks use the defaults |
| Clock window boundary | Fixed one-minute windows as today: up to twice the budget across a boundary is accepted (documented) |
| Refusal before authentication (API budget) | Not possible: the API limit runs after the bearer token is validated, so an invalid token answers 401 and costs no budget of the user it names |
| Redis restarts mid-window | Counters start again from zero; acceptable |
| 9.1.3: two tabs, one signs out | The other's next call answers 401 and goes to sign-in |
| 9.1.3: cookie sent cross-site | SameSite=Lax keeps it off cross-site sub-requests; CSRF token required for writes |

## 6. Dependencies

### Tables

- `oauth2_authorization` (V014): values converted and indexed by V050 (9.1.1). No other table changes.

### Endpoints

- No new endpoint in 9.1.1 or 9.1.2. Changed behaviour: `/login`, `/oauth2/token`, `/api/v1/auth/*` (budgets),
  every signed-in `/api/v1/**` operation (429), the three export operations (429).
- 9.1.3: endpoints of the backend-for-frontend, settled in its design note.

### Services

- 9.1.1: a decorator of `OAuth2AuthorizationService` in `auth/security` (wraps `JdbcOAuth2AuthorizationService`
  from `AuthorizationStoreConfig`); `HashUtils.sha256Hex`.
- 9.1.2: `RateLimitFilter` (address and account keys), a per-user API filter in the API security chain after the
  bearer token filter, `FixedWindowCounter`, `ProtectionProperties` plus a new `app.rate-limit` properties record,
  Micrometer.

### Frontend

- 9.1.1, 9.1.2: none (the 429 message exists in the error interceptor; checked in the manual run).
- 9.1.3: `core/auth` (`AuthService`, interceptors, callback), all of it replaced by cookie sessions.

### External systems

- Redis (counters), PostgreSQL 17 (`sha256(bytea)`, available since PostgreSQL 11).

## 7. Technical decisions

Inherited: Spring Authorization Server with JDBC storage (AUTHENTICATION_AUTHORIZATION §1, §9), refresh token
rotation with reuse detection (`reuseRefreshTokens(false)`, `RefreshTokenReuseGuard`), Redis fixed windows for
every limit (tracker decision 4, ADR-005), problem type `urn:awards:problem:too-many-requests` with `Retry-After`,
one backend instance (ADR-021), committed migrations unchanged.

**Proposed deviations and assumptions**

1. **Hashing at the service boundary.** A decorator of `OAuth2AuthorizationService` copies the authorization with
   hashed token values before `save` and hashes the argument of `findByToken`; `findById` and `remove` pass
   through. The library's SQL, row mapper and parameter mapper stay untouched, so a library upgrade does not reach
   into the hashing. The authorization object the grant keeps in memory holds the issued values, which is how the
   response still returns them.
2. **Prefix `sha256:`** marks a stored hash. It makes AC-1.2 decidable (no double hashing of reloaded rows) and the
   conversion idempotent. Issued values never contain a colon (JWTs and Base64url strings).
3. **Plain SHA-256, no salt or pepper.** Token values are 256-bit random or signed JWTs, so a dictionary attack
   on the hash has nothing to guess; a salt would also make the lookup by value impossible. A pepper (server
   secret) adds little for the demo and would tie the database to a second key. Same choice as the one-time email
   tokens.
4. **Conversion instead of sign-out** (changes tracker decision 3): V050 hashes existing rows in SQL
   (`'sha256:' || encode(sha256(convert_to(col, 'UTF8')), 'hex')`), so nobody signs in again. The tracker decision is
   updated in this PRD's PR.
5. **Address budget 600 a minute.** A sign-in costs about three counted requests (form, code exchange, first
   refresh later); 600 covers about 200 sign-ins a minute behind one NAT address, a faculty arriving at once. The
   account budget (10 a minute) and the lockout (five failures) bound guessing per account; spraying many accounts
   from one address stays possible within 600 a minute and is the residual risk (§11).
6. **Per-user API budget 600 a minute, exports 5 a minute** (one shared window for the three operations). A page
   load of the award list or the dashboard issues fewer than 15 calls; 600 is never reached by a person.
7. **No audit rows for refusals.** A flood would write a flood of audit rows. Metric and a log line without the
   address or the account instead; the lockout keeps its `ACCOUNT_LOCKED` audit.
8. **Account key read from the form parameter** `username` of `POST /login` only. The JSON endpoints under
   `/api/v1/auth/*` already throttle per address of mail (registration, resend, password reset, address change,
   one a minute, `RequestThrottle`).
9. **9.1.3 as a stretch with a go/no-go point**: started only after 9.2.4 (the server runs) and only if it can merge
   before 2026-12-01; its design note (same-application client with the OAuth2 client support of Spring Security,
   token relay to the existing resource server rules, CSRF cookie for Angular) is written at that point. No partial
   merge: the bearer flow stays until the whole story is green.
10. **Migration number** V050 (9.1.1); 9.1.2 has no migration.

## 8. Contract

### 8.1 OpenAPI changes

Written to `docs/api/openapi.yml` with the story that ships them; no new operation in 9.1.1 or 9.1.2.

| Operation | Change | Story |
|-----------|--------|-------|
| `info.description` | Rate limits paragraph: authentication 600 a minute per address, sign-in 10 a minute per account, signed-in API 600 a minute per user, exports 5 a minute per user, public API 120 a minute per address, uploads 20 a minute per user | 9.1.2 |
| `POST /auth/register` description | "20 requests per minute" becomes the new address budget | 9.1.2 |
| `GET /analytics/university/report`, `GET /analytics/units/{id}/export`, `GET /awards/{id}/audit-trail/export` | `'429': $ref: '#/components/responses/TooManyRequests'` | 9.1.2 |

### 8.2 Migration outlines

| File | Story | Content |
|------|-------|---------|
| `V050__hash_authorization_tokens.sql` | 9.1.1 | `UPDATE oauth2_authorization SET <col> = 'sha256:' \|\| encode(sha256(convert_to(<col>, 'UTF8')), 'hex') WHERE <col> IS NOT NULL AND <col> NOT LIKE 'sha256:%'` for `authorization_code_value`, `access_token_value`, `oidc_id_token_value`, `refresh_token_value`, `user_code_value`, `device_code_value`; `CREATE INDEX idx_oauth2_authorization_code_value ON oauth2_authorization (authorization_code_value)`, `idx_oauth2_authorization_access_value`, `idx_oauth2_authorization_id_token_value`, `idx_oauth2_authorization_refresh_value`; `COMMENT ON TABLE oauth2_authorization IS 'Issued authorizations: codes, access, refresh and id tokens; token values stored as sha256: hashes'` |

### 8.3 Properties

| Property | Default | Story |
|----------|---------|-------|
| `app.auth.protection.requests-per-minute` (`AUTH_RATE_LIMIT_PER_MINUTE`) | 600 (was 20); local profile 120 → 1200 | 9.1.2 |
| `app.auth.protection.account-requests-per-minute` (`AUTH_ACCOUNT_RATE_LIMIT_PER_MINUTE`) | 10 | 9.1.2 |
| `app.rate-limit.api-requests-per-minute` (`API_RATE_LIMIT_PER_MINUTE`) | 600; local profile 6000 | 9.1.2 |
| `app.rate-limit.export-requests-per-minute` (`EXPORT_RATE_LIMIT_PER_MINUTE`) | 5 | 9.1.2 |

## 9. Test plan

| AC | Unit | IT | FT | E2E / other |
|----|------|----|----|-------------|
| 1.1, 1.2 | ✓ decorator: every token type hashed, prefixed values kept, issued values left in the caller's object | ✓ row after save contains no issued value | | |
| 1.3 | ✓ lookup hashes; a `sha256:` argument finds nothing | ✓ each token type found by its issued value | | |
| 1.4 | | | ✓ `AuthenticationFlowFT`: code, refresh, rotation, reuse revokes, sign-out, revoke endpoint | ✓ existing sign-in specs green |
| 1.5 | | ✓ revoker and retention policy on hashed rows | ✓ password reset signs out | |
| 1.6, 1.7 | | ✓ migration IT: clear rows converted, SQL hash = `HashUtils`, rerun unchanged, indexes and plan | | |
| 1.8, 2.9 | | | | doc review |
| 2.1, 2.2, 2.3 | ✓ filter: keys, budgets, empty username, normalisation, lockout untouched | ✓ counters in Redis | ✓ REST-assured: 11th post for one account 429, other account passes; lockout after 5 | |
| 2.4, 2.5, 2.6 | ✓ API filter: user key, excluded paths, export group | | ✓ user over budget 429 with `Retry-After` and CORS; second user passes; report refused before audit row | |
| 2.7 | ✓ counter failure → pass, log throttled | | | |
| 2.8 | ✓ metric per limit | | | manual run: Prometheus |
| 3.1–3.5 | per the design note | | ✓ | ✓ sign-in specs on the cookie flow |

Coverage 85 % lines; static clean. Security review for both stories (token storage, migration, filters).

## 10. Manual verification

Draft; expected texts are checked against the merged UI wording at the validation.

Preconditions: `docker compose up -d postgres redis mailpit minio clamav`; backend in `backend/` with
`$env:AUTH_RATE_LIMIT_PER_MINUTE='600'; $env:API_RATE_LIMIT_PER_MINUTE='30'; ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
(a small API budget makes step 13 practical); frontend `npm start` on `http://localhost:4200`. psql
`docker compose exec postgres psql -U postgres award_monitoring`; Redis `docker compose exec redis redis-cli`;
Swagger `http://localhost:8080/swagger-ui.html`; Prometheus `http://localhost:8080/actuator/prometheus`. Seed accounts
(password `Passw0rd-demo`, `@chnu.edu.ua`): `employee.fmi`, `dean.fmi`, `rector`. Before step 1, sign in as
`employee.fmi` in the browser and keep the tab open (the session from before V050).

1. Start the backend once on the commit before V050, sign in as `employee.fmi`, then restart on the 9.1.1 commit.
   psql `select refresh_token_value from oauth2_authorization where principal_name = 'employee.fmi@chnu.edu.ua';`
   Expected: `sha256:` and 64 hex digits; `\d oauth2_authorization` lists the four new indexes. (AC-1.6, 1.7)
2. In the open tab wait until the access token expires (15 minutes) or delete it from session storage and reload.
   Expected: the app refreshes without a sign-in page. (AC-1.6)
3. psql `select count(*) from oauth2_authorization where coalesce(access_token_value, '') not like 'sha256:%' and access_token_value is not null;` and the same for the other five value columns.
   Expected: 0 each. (AC-1.1, 1.2)
4. Sign out and sign in again as `employee.fmi`; in the developer tools copy the refresh token from session
   storage. psql: no column of the newest row equals it (`select count(*) from oauth2_authorization where refresh_token_value = '<copied>';` → 0). (AC-1.1)
5. `curl -s -X POST http://localhost:8080/oauth2/token -d grant_type=refresh_token -d client_id=award-web -d refresh_token=<copied>`.
   Expected: 200 with a new refresh token. Repeat with the old one. Expected: 400 `invalid_grant`, and the new one
   is now refused as well (the authorization is revoked). (AC-1.3, 1.4)
6. `curl -s -X POST http://localhost:8080/oauth2/token -d grant_type=refresh_token -d client_id=award-web -d refresh_token=sha256:<hash from psql>`.
   Expected: 400 `invalid_grant`. (AC-1.3)
7. Sign in as `dean.fmi`, then request a password reset for `dean.fmi` and confirm it from Mailpit. Expected: the
   dean's rows in `oauth2_authorization` are gone; the open tab is sent to sign-in at its next call. (AC-1.5)
8. In the browser sign in and sign out through the menu. Expected: back on the sign-in page; the authorization's
   tokens are invalidated (the refresh token from before the sign-out answers `invalid_grant`). (AC-1.3, 1.4)
9. Restart the backend with defaults for the authentication budget. In PowerShell post the sign-in form eleven
   times for `rector@chnu.edu.ua` with a wrong password within a minute (a loop of `curl -s -o NUL -w "%{http_code} "`
   against `http://localhost:8080/login` with `username` and `password`; the form needs the CSRF token of a GET
   `/login` in the same cookie jar). Expected: the first five redirect to `/login?error`, the account is locked
   after the fifth (mail to administrators), attempts 6 to 10 redirect to the locked message, the 11th answers 429
   with `Retry-After`. (AC-2.2, 2.3)
10. Immediately sign in as `employee.fmi` in the browser from the same machine. Expected: works (the account budget
    is per account; the address budget is far from used). (AC-2.2, 2.6)
11. Restart with `$env:AUTH_RATE_LIMIT_PER_MINUTE='20'`; send 21 `GET http://localhost:8080/login`. Expected: the
    21st answers the 429 page. Restore 600. (AC-2.1)
12. psql `select count(*) from audit_logs where action_type = 'LOGIN_FAILED' and created_at > now() - interval '5 minutes';`.
    Expected: at most 10 for `rector`; the 11th post never reached the authentication and left no row. (AC-2.2)
13. With the API budget 30, authorize as `employee.fmi` in Swagger, copy the access token and call
    `GET /api/v1/awards` 31 times within a minute with `curl -H "Authorization: Bearer <token>"`.
    Expected: the 31st answers 429 Problem Details with `Retry-After`; `dean.fmi` in a second browser profile still
    gets 200. (AC-2.4, 2.6)
14. As `rector`, call `GET /api/v1/analytics/university/report` six times within a minute. Expected: five PDFs, the
    sixth 429; psql: five new `ANALYTICS_EXPORTED` audit rows, not six. (AC-2.5)
15. Prometheus. Expected: `award_rate_limited_total{limit="auth-account"}`, `{limit="auth-address"}`,
    `{limit="api-user"}` and `{limit="export"}` greater than 0; `log` lines name the limit and the path only.
    (AC-2.8)

### Detours

16. Redis stopped (`docker compose stop redis`), repeat steps 13 and 14. Expected: no 429; the backend log has one
    warning a minute, not one per request; start Redis. (AC-2.7)
17. Hidden in state, sign-in budget: while `rector` is over the account budget (step 9 within the minute), post the
    form with the correct password. Expected: 429, not a lock reset and not a sign-in; after `Retry-After` the
    locked message (the lock holds for 30 minutes). (AC-2.2, 2.3)
18. Revoked mid-flow, refresh: `employee.fmi` signed in; an administrator suspends the account. Expected: the next
    refresh answers `invalid_grant` and the row is gone (hashed lookup finds it). Reactivate. (AC-1.5)
19. Revoked mid-flow, exports: `dean.fmi` has the unit dashboard open; the dean role is revoked; the next export
    answers 403 (not 429) and does not count against the budget of another user. Grant the role again. (AC-2.5)
20. Token expiry in an open tab after the migration: keep a tab open across the restart of step 1 for 15 minutes.
    Expected: it refreshes silently. (AC-1.6)
21. Reload after a sign-out: press Back after step 8. Expected: the sign-in page or the application asks to sign in;
    no API call succeeds with the old tokens. (AC-1.4)
22. Mixed-case user name: post the form as ` Rector@CHNU.edu.ua ` during the window of step 9. Expected: counted
    in the same account window (429). (AC-2.2)
23. Bearer token of another user in Swagger while over the API budget (step 13): authorize as `dean.fmi`.
    Expected: 200; switching back to `employee.fmi` answers 429 until the window ends. (AC-2.6)

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| A library path reads a token value from the stored row and returns it (would return the hash) | Low | High | Rotation is on, so the refresh response carries a new token; FT of every grant and the sign-out endpoint; security review |
| V050 hashes differ from the application's hashes (encoding) | Low | High | IT compares SQL and `HashUtils` on non-ASCII and JWT values |
| The NAT budget is still too small on a busy morning | Low | Medium | Property; metric `auth-address` shows refusals; the budget is raised without a release |
| Password spraying across accounts from one address within 600 a minute | Medium | Medium | Per-account lockout; metric and logs; CAPTCHA in the icebox |
| The per-user API budget refuses a legitimate burst (dashboard with many charts) | Low | Low | 600 a minute; property; the frontend shows the 429 message and keeps the page |
| 9.1.3 does not fit before the defense | Medium | Low | Stretch with a go/no-go point; bearer flow unchanged until it merges |

## 12. Definition of Done

- Every AC proven at the layers of §9; `.\tools\gate.ps1` green per story (unit, IT, FT, JaCoCo ≥ 85 %, static 0,
  frontend lint and tests)
- Security review for every story
- `openapi.yml` changes of §8.1; AUTHENTICATION_AUTHORIZATION §9; DATA_DICTIONARY (authorization tables);
  DEMO_DEPLOYMENT §1 (properties); CHANGELOG `[Unreleased]`; tracker and BACKLOG
- Manual verification (§10) run and recorded; feature validated

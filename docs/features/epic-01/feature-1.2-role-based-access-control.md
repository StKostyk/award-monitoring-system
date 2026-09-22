# Feature 1.2: Role-Based Access Control

> **Epic**: 1 — User Management & Authentication (SCRUM-5)
> **Sprint**: 2–3 (2026-09-21 → 2026-10-04)
> **Points**: 18 (three stories)
> **Status**: Approved (2026-09-21)
> **Author**: Stefan Kostyk
> **Governing docs**: ADR-009, AUTHENTICATION_AUTHORIZATION.md §3–§4 and §9, RBAC_matrix.md §2, DATA_DICTIONARY §1.2–§1.3, use-case-diagram.puml, openapi.yml, US-002, EPIC-01 tracker (deviations 5–7, follow-up 7)

## 1. Problem and personas

Feature 1.1 knows who the caller is; nothing yet decides what they may touch. Every later epic (award submission, approval workflow, reporting) needs three things this feature delivers: a permission model that is scoped to the organisation tree, a way for faculty leadership to give and take roles without involving IT, and a way to keep approvals moving when an approver is away. It also closes the gap left by self-registration: an address at `@chnu.edu.ua` proves employment at the university, not membership of the department the person picked, so somebody in that faculty has to confirm it before the account can do anything.

| Persona | Need in this feature |
|---------|----------------------|
| Prof. Martynyuk, dean | See everybody in the faculty with their roles, confirm new colleagues, make a secretary a reviewer, hand approval authority to a vice-dean for two weeks |
| Alina, faculty secretary | Confirm new department members, see her faculty's directory, be refused clearly when she reaches for another faculty |
| Anastasia, employee | Understand why the app is read-only until her department confirms her, be told when a role is given or taken |
| System administrator | Assign any role anywhere (including `RECTOR`, `SYSTEM_ADMIN`, `GDPR_OFFICER`), read every access-denied event |

## 2. Scope

**In (this feature)**

- Organisation-scoped permission model: role scopes in the access token, an in-memory organisation tree, `@PreAuthorize` helpers for "inside my subtree" and "below my level", access-denied audit events, user directory filtered by scope (1.2.1)
- Role assignment and revocation within one's subtree and below one's level, university and system roles by `RECTOR`/`SYSTEM_ADMIN` only; department-membership confirmation as the first role of a self-registered account, behind a replaceable strategy; unverified accounts can be re-registered; administration pages in Angular (1.2.2)
- Time-boxed delegation of approval authority to a colleague, visible in the token, revocable, expiring by itself (1.2.3)

**Out (where it goes)**

- ABAC policy engine, risk and time-of-day conditions (AUTH §4) — not needed before Epic 4; the scope helpers of 1.2.1 are the seam where a policy engine would plug in
- Award-level checks (`award:read:department` on an actual award, "delegated by" stamp on an approval) — Epic 2 and Epic 4 consume the helpers built here
- Bulk import of staff, LDAP/HR lookup — a second `MembershipConfirmation` strategy when the university provides a directory
- Deactivating accounts, profile editing, notification preferences — Feature 1.3
- Delegation of user-management authority — only approval authority is delegable; user management follows the role itself

## 3. Stories

| Key | Story | Pts | Parallel | Depends on |
|-----|-------|-----|----------|------------|
| SCRUM-12 (#35) | 1.2.1 Permission model and organisation-scoped access | 5 | no | Feature 1.1 |
| SCRUM-13 (#32) | 1.2.2 Role assignment and membership confirmation | 8 | yes | SCRUM-12 |
| SCRUM-14 (#37) | 1.2.3 Approval authority delegation | 5 | yes | SCRUM-12 |

SCRUM-13 and SCRUM-14 fix their API contract in `openapi.yml` first; the Angular pages are then built against the contract while the backend is in progress.

## 4. Acceptance criteria

### 1.2.1 Permission model and organisation-scoped access (SCRUM-12)

- **AC-1.1** Given a user with current roles, when an access token is issued, then it carries `role_scopes` as `["<ROLE>:<orgId>", …]` next to the existing `roles`, `permissions`, `org_id` and `org_type` claims; `permissions` is the union of the role permissions from the matrix in §7 (AUTH §3.3 plus `user:read:scope` and `user:manage:scope`).
- **AC-1.2** Given the organisation table, then the application holds the tree in memory (id, type, parent, depth; ancestry follows the parent chain) loaded at start and reloaded every five minutes, with `refresh()` for code that changes organisations; a scope check never queries the database; a parent cycle is logged and treated as outside every scope.
- **AC-1.3** Given a caller with role scope `DEAN:9`, when a resource in organisation 64 (a department under faculty 9) is requested, then `@access.inScope(64)` is true; for organisation 10 (another faculty) it is false. `SYSTEM_ADMIN`, `RECTOR`, `RECTOR_SECRETARY` and `GDPR_OFFICER` scopes cover the whole tree because their role scope is the university root.
- **AC-1.4** Given the level order `EMPLOYEE < FACULTY_SECRETARY < DEAN < RECTOR_SECRETARY < RECTOR`, then `@access.below(role)` is true when any role the caller holds is strictly above `role`, and `@access.canManage(role, orgId)` when such a role's scope also covers `orgId`; `SYSTEM_ADMIN` is above every role and alone grants system roles; `GDPR_OFFICER` is outside the line and above nothing.
- **AC-1.5** Given a request refused by a `@PreAuthorize` rule or an authority check, then the response is 403 with problem type `access-denied` and a detail naming what was missing (permission or organisation scope), and an `ACCESS_DENIED` row is written to `audit_logs` with user id, method, path, the missing requirement, IP and correlation id.
- **AC-1.6** Given `GET /api/v1/users`, when the caller holds `user:read:all`, then every user is listed; with `user:read:scope` only users whose organisation lies in the scope of a role that grants it (an `EMPLOYEE` role in another department does not widen the directory); otherwise 403. Filters `organization`, `role`, `status`, `unconfirmed` and free-text `q` (name or email) work together; the page size is capped at 100.
- **AC-1.7** Given `GET /api/v1/users/{id}`, then the profile with its current and past roles is returned under the same scope rule; outside the scope the answer is 404, not 403, so the directory does not confirm foreign ids.
- **AC-1.8** ADR-009 and AUTH §3.3 list the seven roles of the data dictionary and the permission strings of §7; the tracker's deviations 5–7 are closed.

### 1.2.2 Role assignment and membership confirmation (SCRUM-13)

- **AC-2.1** Given a caller with `user:manage:scope`, when `POST /api/v1/users/{id}/roles` is called with `role`, `organizationId`, `validFrom` (default today) and optional `validTo`, then a `user_roles` row is created if the target organisation is inside the caller's scope, the role is below the caller's level there, the target user is `ACTIVE` and the same role is not already current for that organisation; the answer is 201 with the assignment, `created_by` is the caller.
- **AC-2.2** Given a dean assigning `RECTOR`, `RECTOR_SECRETARY`, `SYSTEM_ADMIN` or `GDPR_OFFICER`, then 403 `role-above-level` with a detail that university and system roles are granted by the rector's office or the administrator. Given a faculty secretary assigning `DEAN`, the same. Given `SYSTEM_ADMIN`, any role in any organisation is allowed. Given `RECTOR`, everything except `SYSTEM_ADMIN` and `GDPR_OFFICER`.
- **AC-2.3** Given an organisation whose type does not fit the role (a department for `DEAN`, a faculty for `EMPLOYEE`, anything but the university for `RECTOR`, `RECTOR_SECRETARY`, `SYSTEM_ADMIN`, `GDPR_OFFICER`), then 422 `role-organization-mismatch`. `FACULTY_SECRETARY` accepts a faculty or a department.
- **AC-2.4** Given `DELETE /api/v1/users/{id}/roles/{roleId}`, when the caller could have assigned that role, then the row's `valid_to` becomes yesterday (history is kept, nothing is deleted), the user's sessions and tokens are ended everywhere so the lost permissions take effect at once, and the answer is 204. A caller cannot revoke their own last role above `EMPLOYEE`.
- **AC-2.5** Every assignment and revocation writes `ROLE_ASSIGNED` / `ROLE_REVOKED` to `audit_logs` (actor, target user, role, organisation, validity) and emails the target user in their language: role, organisation, who did it, validity.
- **AC-2.6** Given self-registration, when the account is created, then no role is assigned (Feature 1.1 AC-2.1 is amended); the account is `ACTIVE` after email verification but the token carries no roles or permissions, `GET /users/me` reports `membershipConfirmed: false`, and the home page shows «Ваше членство у підрозділі ще не підтверджено» with the department name. Registration answers through a `MembershipConfirmation` strategy; the only implementation, `ManualConfirmation`, always defers to a person.
- **AC-2.7** Given `GET /api/v1/users?unconfirmed=true`, then users in scope who have never held a role are listed first; assigning `EMPLOYEE` for the user's own department is offered as the one-click «Підтвердити» action, which is AC-2.1 with the department pre-filled; a secretary may also correct the department to another one inside her scope before confirming (the user's `organization_id` is updated in the same request).
- **AC-2.8** Given a registration for an address whose account is `PENDING` and whose newest verification link has expired, then the old account is replaced (name, organisation, password, new verification link) and the answer is 201; while the link is still valid the answer stays 409. A `PENDING` account is never listed in the directory.
- **AC-2.9** Given a role assigned, when the user's next access token is issued (silent refresh within 15 minutes, or a new sign-in), then the new role is in the claims; the administration page says so after assigning.
- **AC-2.10** Angular: `/admin/users` (guarded by `user:read:scope` or `user:read:all`) lists users with filters and the unconfirmed badge; `/admin/users/:id` shows profile, current and past roles, «Призначити роль» dialog (role limited to what the caller may assign, organisation limited to the scope, validity), revoke with confirmation; all texts in Ukrainian and English; the navigation shows «Користувачі» only to callers who may open it.

### 1.2.3 Approval authority delegation (SCRUM-14)

- **AC-3.1** Given a caller holding an approval role (`FACULTY_SECRETARY`, `DEAN`, `RECTOR_SECRETARY`, `RECTOR`) in organisation O, when `POST /api/v1/delegations` is called with `delegateId`, `role`, `organizationId`, `validFrom`, `validTo` and an optional `reason`, then a `role_delegations` row is created if: the caller currently holds that role in O; the delegate is a different `ACTIVE` user with at least one current role whose organisation is inside O's subtree (or O itself); `validFrom ≤ validTo`, `validTo` at most 90 days after `validFrom` and not in the past; no other active delegation of the same role in O by the same caller overlaps the period. Otherwise 422 with a typed problem (`delegation-not-holder`, `delegation-bad-delegate`, `delegation-period`, `delegation-overlap`).
- **AC-3.2** Given a current delegation, when the delegate's access token is issued, then `delegations` carries `"<ROLE>:<orgId>:<delegatorId>"`, `role_scopes` gains `<ROLE>:<orgId>`, and `permissions` gains the role's `award:read:*` and `award:approve:*` permissions only — never `user:manage:*`. Delegated authority cannot be delegated further (a delegate holding only a delegated role gets `delegation-not-holder`).
- **AC-3.3** Given `validTo` has passed, then the delegation disappears from the next token without any job; `GET /api/v1/delegations?state=expired` still lists it.
- **AC-3.4** Given `DELETE /api/v1/delegations/{id}` by the delegator, `SYSTEM_ADMIN`, or anyone who could revoke the delegator's role, then `revoked_at`/`revoked_by` are set and the delegate's sessions and tokens are ended everywhere; 204. A revoked or expired delegation cannot be revoked again (409).
- **AC-3.5** `GET /api/v1/delegations` lists delegations given and received by the caller (`state` = `active` | `upcoming` | `expired` | `revoked`); `SYSTEM_ADMIN` may list for any `delegatorId`. Creation and revocation write `DELEGATION_CREATED` / `DELEGATION_REVOKED` to `audit_logs` and email the delegate (and the delegator on revocation by somebody else).
- **AC-3.6** Angular: `/delegations` (guarded by an approval role) shows «Мої делегування» given and received with state chips, «Делегувати повноваження» dialog (role from the caller's roles, delegate picked from the scoped directory, dates with the 90-day limit, reason), revoke with confirmation; the profile header shows «Діє за дорученням: <ім'я>» while a received delegation is active.

## 5. Edge cases

- Role rows with `valid_from` in the future are not current: they neither appear in the token nor count for scope until that day; they are listed under "upcoming" in the user detail.
- Two administrators assign the same role concurrently: the second request finds the role current and answers 409 `role-already-assigned` (checked in the transaction; a partial unique index on current rows backs it).
- A dean whose own role is revoked while an administration page is open: the next API call answers 401 (sessions ended) and the app returns to the login page; after sign-in the page is refused with 403 and the navigation entry is gone.
- A user who moves department: the secretary confirms with the corrected department (AC-2.7); the user's old `EMPLOYEE` role in the previous department is revoked in the same request and both events are audited.
- The organisation tree changes (new department, deactivated faculty): the in-memory tree is refreshed by the organisation service after each change and on a five-minute timer; roles pointing at an inactive organisation still count for reading, never for assigning.
- A delegation whose delegator loses the role during the period: the delegation is revoked in the same transaction as the role revocation (`revoked_by` = the actor) and the delegate is emailed.
- A delegate who is suspended or deleted: the token customizer only issues tokens to accounts that can log in, so nothing extra; the delegation stays listed as active and is cleaned up by revocation when the delegator notices — recorded as a known gap for Feature 1.3 (account deactivation revokes received delegations).
- Time zones: validity dates are calendar dates in `Europe/Kyiv`; the application clock carries that zone and the customizer, the profile and the directory use `LocalDate.now(clock)`, so a delegation ending "today" works until midnight Kyiv time.
- `SYSTEM_ADMIN` never receives approval delegations (not an approval role) and cannot be a delegator.
- Redis down: role revocation still ends sessions (the not-before key is best effort, as in 1.1.6); the access-denied audit row is written to PostgreSQL in its own transaction, so a refusal inside a rolled-back request still leaves its trace, and a failing audit insert never turns a 403 into a 500.
- Directory search `q` shorter than two characters is ignored; email matching is case-insensitive; no wildcard injection (parameters bound, `%`/`_` escaped).

## 6. Dependencies

### Tables

| Table | Status | Change |
|-------|--------|--------|
| `user_roles` | existing | V017: partial unique index `uk_user_roles_current` on `(user_id, role_type, organization_id) WHERE valid_to IS NULL`; comment on `valid_to` clarifying "yesterday on revocation" |
| `organizations` | existing | none; `hierarchy_path` populated by `R__seed_organizations.sql` is the source of the in-memory tree |
| `role_delegations` | **new** (V017) | `delegation_id BIGSERIAL PK`, `delegator_id BIGINT NOT NULL FK users`, `delegate_id BIGINT NOT NULL FK users`, `role_type VARCHAR(30) NOT NULL CK (approval roles)`, `organization_id BIGINT NOT NULL FK organizations`, `valid_from DATE NOT NULL`, `valid_to DATE NOT NULL CK (valid_to >= valid_from AND valid_to <= valid_from + 90)`, `reason VARCHAR(500)`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `revoked_at TIMESTAMPTZ`, `revoked_by BIGINT FK users`, CK `delegator_id <> delegate_id`; indexes on `delegate_id` (partial, `revoked_at IS NULL`) and `delegator_id`. Added to DATA_DICTIONARY §1.7 in SCRUM-14 |
| `audit_logs` | existing | new `action_type` values `ACCESS_DENIED`, `ROLE_ASSIGNED`, `ROLE_REVOKED`, `DELEGATION_CREATED`, `DELEGATION_REVOKED` (no schema change; the column is free text with an application enum) |

### Endpoints

| Method and path | Status | Story |
|-----------------|--------|-------|
| `GET /api/v1/users` | existing in `openapi.yml`, unimplemented; gains `status`, `unconfirmed`, `q` filters, scope rule, `UserSummary` items | SCRUM-12 |
| `GET /api/v1/users/{id}` | new | SCRUM-12 |
| `GET /api/v1/users/me` | existing; gains `membershipConfirmed` | SCRUM-13 |
| `POST /api/v1/users/{id}/roles`, `DELETE /api/v1/users/{id}/roles/{roleId}` | new | SCRUM-13 |
| `POST /api/v1/auth/register` | existing; role no longer created, pending replacement | SCRUM-13 |
| `GET /api/v1/delegations`, `POST /api/v1/delegations`, `DELETE /api/v1/delegations/{id}` | new | SCRUM-14 |
| Problem types | `access-denied`, `role-above-level`, `role-organization-mismatch`, `role-already-assigned`, `role-last-own`, `delegation-*` | all |

### Services and libraries

- `OrganizationTree` (in-memory, refreshed) and `AccessScope` bean exposed to SpEL as `@access`; `AccessDeniedAuditListener` on Spring Security's `AuthorizationDeniedEvent` plus the problem-details handler for `AccessDeniedException`
- `RoleAssignmentService`, `MembershipConfirmation` (interface) + `ManualConfirmation`, `DelegationService`; `TokenClaimsCustomizer` extended with scopes and delegations; `AuthorizationRevoker` reused for revocations
- Emails through the existing `AuthMailer` templates (`role-assigned`, `role-revoked`, `delegation-created`, `delegation-revoked`, uk/en)
- No new libraries

### Frontend

- Routes `/admin/users`, `/admin/users/:id`, `/delegations` under the authenticated shell; guards based on the `permissions` claim decoded from the access token (already parsed for the profile); NgRx feature `admin` (users list state, filters, selected user) and `delegations`
- Components: users table with filters and pagination (Angular Material table/paginator), user detail with role history, assign-role dialog, delegation list and dialog, membership banner on the home page
- i18n keys under `admin.*`, `delegations.*`, `home.membership.*`

### External systems

None. The `MembershipConfirmation` interface is the seam for a future LDAP/HR client.

## 7. Technical decisions

| # | Decision | Reasoning | Source |
|---|----------|-----------|--------|
| D-1 | Permission checks are method security (`@PreAuthorize`) with a small SpEL bean rather than an ABAC engine | ADR-009 prescribes Spring method security; the ABAC engine of AUTH §4 has no consumer before Epic 4 and would be speculative | ADR-009, AUTH §3.4 |
| D-2 | Organisation scope travels in the token as `role_scopes`; the subtree test uses an in-memory tree built from `hierarchy_path` | ~110 organisations that almost never change; one map lookup per check instead of a query; the token stays small (a user has one to three roles) | DATA_DICTIONARY §1.3 |
| D-3 | Level order and "below own level, inside own subtree" as the assignment rule; university and system roles only by `RECTOR` (not system roles) and `SYSTEM_ADMIN` (all) | Reconciles US-002 (dean assigns within faculty), RBAC matrix (rector's office assigns) and AUTH §3.3 (`user:manage` admin only); agreed in the tracker | Tracker deviation 6 |
| D-4 | Two new permissions `user:read:scope` and `user:manage:scope` for the four approval roles; `user:read:all` and `user:manage` unchanged | The tracker named `user:manage:faculty`; `scope` is the accurate word because the rector's scope is the university | AUTH §3.3 |
| D-5 | Self-registration creates no role; the first role is the membership confirmation by a person in scope, behind `MembershipConfirmation` | Recorded on 2026-09-20; amends Feature 1.1 AC-2.1 | Tracker decisions |
| D-6 | Role revocation ends the user's sessions everywhere; assignment waits for the next token | Losing authority must be immediate (reuse of 1.1.6); gaining it within 15 minutes is acceptable and avoids logging people out for a promotion | AUTH §9 |
| D-7 | Delegation is its own table with mandatory end date (≤ 90 days), grants approval permissions only, and is never transitive | US-002 asks for temporary authority "within date range"; keeping it out of `user_roles` keeps role history honest and makes "delegated by" traceable in Epic 4 | US-002, roadmap 1.2.3 |
| D-8 | Access-denied events are audited through Spring Security's authorization events, not by hand in each controller | One listener covers every rule; the roadmap's "attempt is logged" AC holds for endpoints added later | Roadmap 1.2.2 |
| D-9 | Delegation of the "delegated by" stamp on approvals to Epic 4 | No awards exist yet; the token claim is the contract Epic 4 reads | Tracker decision 2026-09-21 |

**Proposed deviations from the docs** (applied in the story that touches them, after approval): ADR-009 role list and permission constants (deviation 5); `user:*:scope` naming instead of `user:manage:faculty` (deviation 6); Feature 1.1 AC-2.1 amended (no role at registration); `openapi.yml` `/users` no longer "Admin only".

## 8. Test plan

| AC | Unit | Slice | IT | FT | E2E |
|----|------|-------|----|----|-----|
| 1.1 | ✓ customizer, permissions | | | ✓ token claims of dean and admin | |
| 1.2, 1.3, 1.4 | ✓ tree and scope tables (parametrised) | | ✓ tree loaded from seed | | |
| 1.5 | ✓ listener | ✓ `@WebMvcTest` 403 body | ✓ audit row | ✓ secretary hits another faculty | ✓ |
| 1.6, 1.7 | ✓ service filters | ✓ controller | ✓ repository queries | ✓ admin vs dean vs employee | ✓ list and filters |
| 2.1–2.4 | ✓ assignment rules (table) | ✓ controller | ✓ unique index, revocation | ✓ dean assigns secretary, refused for rector | ✓ |
| 2.5 | ✓ mailer, audit | | ✓ | ✓ Mailpit | |
| 2.6, 2.7 | ✓ strategy | ✓ | ✓ registration | ✓ register → confirm → token has role | ✓ banner → confirmed |
| 2.8 | ✓ | | ✓ | ✓ expired pending replaced | |
| 2.9, 2.10 | ✓ Angular guards, components | | | | ✓ |
| 3.1–3.5 | ✓ rules (table), customizer | ✓ controller | ✓ constraints, expiry by date | ✓ delegate token, revoke ends session | ✓ |
| 3.6 | ✓ components | | | | ✓ dialog and chips |

Coverage target 85 % lines per `mvn verify`; static analysis clean; Playwright for every UI AC.

## 9. Manual verification

Preconditions: `.\tools\dev-up.ps1` (backend `local` profile on `http://localhost:8080`, frontend on `http://localhost:4200`, Mailpit `http://localhost:8025`). Seed accounts (password `Passw0rd-demo`): `admin@chnu.edu.ua` (`SYSTEM_ADMIN`), `rector@chnu.edu.ua`, `dean.fmi@chnu.edu.ua` (faculty 9), `secretary.fmi@chnu.edu.ua` (faculty 9), `employee.fmi@chnu.edu.ua` (department 64). Swagger at `http://localhost:8080/swagger-ui.html` signs in with the same accounts.

### After 1.2.1 (SCRUM-12)

1. Sign in as `dean.fmi@chnu.edu.ua` in Swagger, call `GET /api/v1/users/me`, copy the token from the Authorize dialog into `https://jwt.io`. Expected: `role_scopes: ["DEAN:9"]`, `permissions` contains `user:read:scope`, `user:manage:scope`, `award:approve:level2`, not `user:manage`. (AC-1.1)
2. `GET /api/v1/users` as the dean. Expected: 200 with users of faculty 9 and its departments only (`secretary.fmi`, `employee.fmi`, `pending` absent — it is `PENDING`), not `rector` or `admin`. As `admin`: everybody. As `employee.fmi`: 403 with `type: urn:awards:problem:access-denied` and a detail naming `user:read:scope`. (AC-1.5, 1.6)
3. `GET /api/v1/users?organization=1` as the dean. Expected: 403 (organisation 1 is outside faculty 9). psql: `select action_type, user_id, new_values->>'path', new_values->>'required' from audit_logs where action_type = 'ACCESS_DENIED' order by created_at desc limit 3;` → the two refusals with IP and correlation id. (AC-1.3, 1.5)
4. `GET /api/v1/users/{id of rector}` as the dean → 404; as admin → 200 with the role list. (AC-1.7)

### After 1.2.2 (SCRUM-13)

5. Register `newcomer@chnu.edu.ua` at `http://localhost:4200/register` with department «Кафедра алгебри та інформатики» (id 64), verify through the Mailpit link, sign in. Expected: home page shows «Ваше членство у підрозділі ще не підтверджено» with the department; jwt.io shows empty `roles` and `permissions`; `/admin/users` is not in the navigation and opening it by URL shows the forbidden page. (AC-2.6)
6. Sign in as `secretary.fmi@chnu.edu.ua`, open `http://localhost:4200/admin/users`. Expected: the newcomer at the top with the badge «Не підтверджено»; click «Підтвердити» → the row shows `EMPLOYEE · Кафедра …`; Mailpit has «Роль призначено / Role assigned» for the newcomer. (AC-2.5, 2.7)
7. In the newcomer's browser reload the app within 15 minutes → still the banner; sign out and in → the banner is gone, `roles: ["EMPLOYEE"]`. (AC-2.9)
8. As the secretary open `employee.fmi` and try «Призначити роль» → the role list offers only `EMPLOYEE`; as the dean it offers `EMPLOYEE` and `FACULTY_SECRETARY`; assign `FACULTY_SECRETARY` for faculty 9 to `employee.fmi` with `validTo` next month. Expected: 201, role shown with validity, email delivered. In Swagger as the dean, `POST /api/v1/users/{employee id}/roles` with `role: RECTOR` → 403 `role-above-level`; with `role: DEAN, organizationId: 64` → 422 `role-organization-mismatch`; repeating the secretary assignment → 409 `role-already-assigned`. (AC-2.1, 2.2, 2.3)
9. Sign in as `employee.fmi` in a second browser and open `/admin/users` (now allowed). Back as the dean, revoke the `FACULTY_SECRETARY` role. Expected: 204; in the second browser the next click lands on the login page; after signing in again `/admin/users` is refused; psql shows `ROLE_ASSIGNED` and `ROLE_REVOKED` rows and the role row with `valid_to` = yesterday. (AC-2.4, 2.5)
10. Register `blocked@chnu.edu.ua` without verifying; register it again at once → 409. psql: `update one_time_tokens set expires_at = now() - interval '1 hour' where purpose = 'EMAIL_VERIFICATION' and user_id = (select user_id from users where email_address = 'blocked@chnu.edu.ua');` then register again with another first name → 201, Mailpit has a fresh link, the user row carries the new name. (AC-2.8)
11. As `rector@chnu.edu.ua` in Swagger assign `RECTOR_SECRETARY` for organisation 1 to `secretary.fmi` → 201; assign `SYSTEM_ADMIN` → 403. As `admin` assign `GDPR_OFFICER` for organisation 1 to `dean.fmi` → 201. (AC-2.2)

### After 1.2.3 (SCRUM-14)

12. Sign in as `dean.fmi`, open `http://localhost:4200/delegations`, «Делегувати повноваження»: role `DEAN`, delegate `secretary.fmi`, today → in 14 days, reason «Відпустка». Expected: the delegation is listed as «Активне»; Mailpit has «Делеговано повноваження / Authority delegated» for the secretary. (AC-3.1, 3.5)
13. Sign in as `secretary.fmi` (or sign out and in). Expected: jwt.io shows `delegations: ["DEAN:9:<dean id>"]`, `role_scopes` includes `DEAN:9`, `permissions` includes `award:approve:level2` but not `user:manage:scope` beyond her own; the header shows «Діє за дорученням: Мартин Мартинюк»; `/delegations` lists it under «Отримані». Trying to delegate `DEAN` further → the dialog does not offer it; Swagger `POST /api/v1/delegations` with `role: DEAN` → 422 `delegation-not-holder`. (AC-3.2)
14. In Swagger as the dean: a second delegation of `DEAN` overlapping the period → 422 `delegation-overlap`; `validTo` 100 days out → 422 `delegation-period`; delegate `employee.fmi` with `role: RECTOR` → 422 `delegation-not-holder`. (AC-3.1)
15. As the dean revoke the delegation. Expected: state «Відкликано»; in the secretary's browser the next click lands on the login page; after sign-in the claims are back to `FACULTY_SECRETARY` only; psql shows `DELEGATION_CREATED` and `DELEGATION_REVOKED`. (AC-3.4, 3.5)
16. Create a delegation ending today, then in psql `update role_delegations set valid_to = current_date - 1 where delegation_id = <id>;`, sign the delegate out and in → no `delegations` claim; `/delegations?state=expired` lists it. (AC-3.3)

### Detours

17. Open `http://localhost:4200/admin/users/999999` as the dean → «Не знайдено» page, no error toast loop; `http://localhost:4200/delegations` as `employee.fmi` (no approval role) → the forbidden page and the navigation without the entry. (AC-1.7, 3.6)
18. Restart the backend while the assign-role dialog is open, then submit → «Сервер недоступний» message, the dialog stays filled; submit again after the health check is green → 201. (AC-2.1)
19. Let the access token expire in the admin page (15 minutes, or set `expires_at` in session storage to the past) and click a filter → silent refresh keeps the page; with the dean's role revoked meanwhile → login page. (AC-2.4, 1.1.6)
20. Second browser: keep the user list open as the dean while the admin assigns a role to the same user; reload the dean's page → the new role is visible; assign the same role from the dean's stale dialog → 409, the list refreshes. (AC-2.1, §5)
21. Press Back after confirming a newcomer, then «Підтвердити» again → 409 shown inline, no duplicate row. Reload the delegation dialog mid-way and re-submit an identical delegation → 422 `delegation-overlap`. (AC-2.7, 3.1)
22. Assign `EMPLOYEE` to a user who already holds it in the same department (just confirmed) → 409; revoke it and assign it again the same day → 201, two rows in history (the old one ending yesterday). (AC-2.1, 2.4)
23. Run `docker compose exec redis redis-cli FLUSHDB`, then revoke a role → still 204 and the target's next call is 401 (sessions ended; the not-before key is best effort). (§5)

## 10. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Level order is a judgement call the university has not confirmed (does the rector's secretary outrank a dean for user management?) | Wrong person can assign roles | The order is one enum; the PRD and AUTH §9 state it; it is read at `/thesis-sync` with the user |
| A stale in-memory tree after an organisation change on another instance | Wrong scope for up to five minutes | Timer refresh; single instance for the thesis; Redis pub/sub is the multi-instance answer (deployment story) |
| Token growth with many roles or delegations | Header size | A user has at most a handful of roles; delegations are bounded by the 90-day rule and one-per-role |
| Membership confirmation adds a manual step before anyone can submit an award | Onboarding friction at the pilot | Secretaries see unconfirmed users first; email to the secretary on every new registration is a Feature 1.3 preference |
| Revoking a role signs the user out at once | Surprising for the admin | The administration page says it before confirming |

## 11. Definition of Done

- `./mvnw verify` green (unit, slice, IT, FT), JaCoCo ≥ 85 % lines, Checkstyle/PMD/SpotBugs clean
- `npm run lint`, `npm run test:ci`, Playwright scenarios for AC-1.5–1.7, 2.6–2.10, 3.6
- Docs in the same PRs: `openapi.yml`, DATA_DICTIONARY §1.2 and new §1.7, ADR-009 role and permission list, AUTH §3.3 and §9, `CHANGELOG.md`, tracker rows, `BACKLOG.md`
- §9 walked through by the author after `/feature-validate`, including the detours

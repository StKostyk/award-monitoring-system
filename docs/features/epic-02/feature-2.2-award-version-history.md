# Feature 2.2: Award Version History & Audit Trail

> **Epic**: 2 — Award Lifecycle Management (SCRUM-20)
> **Sprint**: 3–4 (2026-09-30 → 2026-10-11)
> **Points**: 10 (two stories)
> **Status**: Approved 2026-09-30
> **Author**: Stefan Kostyk
> **Governing docs**: roadmap § Feature 2.2, DATA_GOVERNANCE §2 and §4, PRIVACY_IMPACT §4, DATABASE_DESIGN_STANDARDS §3.2, DATA_DICTIONARY §2.1 and §4.1, AUTH §3.3 and §7, RBAC_matrix.md, ADR-004, ADR-009, ADR-014, openapi.yml `/awards`, EPIC-02 tracker (decision 2026-09-28 on version-history storage, technical notes)

## 1. Problem and personas

An award changes several times before anybody approves it: the owner saves a first draft, corrects the date, picks another category, submits. From Epic 4 on, reviewers return it for changes and correct it themselves. Today the only record of these changes is the generic row trigger on `awards`, which writes whole-row snapshots to `audit_logs` without the person who made the change, and nothing in the application reads it. Nobody can answer "what did the award look like when it was submitted" or "who changed the level". This feature records every saved state of an award, shows the history on the award page and gives the oversight roles a per-award audit report they can export.

| Persona | Need in this feature |
|---------|----------------------|
| Anastasia, employee | See how her award changed and what she submitted, so a later correction by a reviewer is visible to her |
| Alina, faculty secretary; Prof. Martynyuk, dean | See the award as it was submitted and every change since, before deciding (Epic 4) |
| System administrator, GDPR officer | Read the complete audit trail of one award — who, what, when, from where — and hand it to an auditor as a file |

## 2. Scope

**In (this feature)**

- `award_versions`: one row per saved state of an award (created, edited, submitted), written in the same transaction as the change, with the actor and the changed fields; baseline rows for existing awards (2.2.1)
- The actor and the request correlation id on every row written by the table triggers, for all audited tables; trigger rows of a deleted record point at that record (2.2.1)
- `GET /awards/{id}/versions` (owner and scoped readers) with field-level changes; `GET /awards/{id}/audit-trail` for `audit:read` (2.2.1)
- History section on the award page with the changes of each version and a view of any version; audit-trail tab and CSV export for `audit:read` (2.2.2)

**Out (where it goes)**

- Versions written by reviewer corrections, returns and decisions — Epic 4 adds its actions to the same table (2.4.1 corrections)
- Restoring an older version — not requested; the owner can copy values from the version view by hand
- A system-wide audit search and compliance dashboard — Epic 6 ("compliance audit dashboard")
- Erasure of award history and of award snapshots in `audit_logs` — Epic 6 (2.4.2 GDPR deletion); §10 records the open question
- Versions of other entities (users, roles, documents) — their changes stay in `audit_logs` as today
- Retention jobs for `audit_logs` partitions — Epic 6

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-25 (#78) | 2.2.1 Award version recording | 5 | no | 2.1.1 (merged) |
| SCRUM-26 (#79) | 2.2.2 Version history view and audit export | 5 | yes (UI from the 2.2.1 contract) | 2.2.1 |

## 4. Acceptance criteria

### 2.2.1 Award version recording (SCRUM-25)

- **AC-1.1** Given an employee, when she creates a draft, then `award_versions` holds version 1 with action `CREATED`, her user id as actor, the snapshot of the business fields and no changed fields.
- **AC-1.2** Given a draft at version *n*, when the owner saves it with at least one changed field, then version *n+1* (`UPDATED`) holds the new snapshot and the names of the changed fields; when she saves it without a change, then no version is added and `awards.version` stays *n*.
- **AC-1.3** Given a complete draft, when the owner submits it, then a `SUBMITTED` version holds status `PENDING`, the impact score and the organisation set at submission.
- **AC-1.4** Given a save that fails (stale `version` 409, validation 422, duplicate 409), then no version is written.
- **AC-1.5** Given a draft with versions, when the owner deletes it, then its versions are deleted with it, and the `DELETE` row in `audit_logs` has `entity_id` = the award id and `user_id` = the owner.
- **AC-1.6** Given any request of a signed-in user that changes an audited table (award save, submission, role assignment, profile change), then every trigger row it produces carries that user's id in `user_id` and the request's `correlation_id`; changes without a signed-in user (migrations, seeds, scheduled jobs) keep `user_id` empty.
- **AC-1.7** Given awards that exist before the migration, then each has one `BASELINE` version with its current state, its current `version` number and no actor.
- **AC-1.8** Given the owner, when she calls `GET /api/v1/awards/{id}/versions`, then she gets all versions newest first (page size default 20, capped at 50), each with number, action, actor (id and name), time, the full snapshot and the list of changes (`field`, `from`, `to`) against the previous version.
- **AC-1.9** Given a reader with a scope over the award's organisation, when the award is not a draft, then the endpoint returns the versions from the first `SUBMITTED` one on, and that version lists no changes; when it is a draft, or out of scope, or does not exist, then 404.
- **AC-1.10** Given a holder of `audit:read`, when he calls `GET /api/v1/awards/{id}/audit-trail`, then he gets the `audit_logs` rows about the award (trigger rows of `awards`, trigger rows of its `award_requests` row, application rows such as `AWARD_SUBMITTED`) newest first, paged, with actor, action, entity type, changed fields, old and new values, IP address and correlation id; this also works for a deleted award when rows about it exist, and answers 404 when none exist.
- **AC-1.11** Given a caller without `audit:read`, when he calls the audit-trail endpoint, then 403 `access-denied`, audited as `ACCESS_DENIED`.

### 2.2.2 Version history view and audit export (SCRUM-26)

- **AC-2.1** Given the owner on `/awards/{id}`, then a «Історія змін» section lists the versions newest first: action («Створено», «Змінено», «Подано», «Початковий стан»), actor, date and time in Kyiv time, and for each changed field its label with the old and new value (category and organisation by name, dates in the UI date format, empty values as «—»).
- **AC-2.2** Given a version in the list, when the user chooses «Переглянути версію», then a dialog shows every field of that version.
- **AC-2.3** Given more than 20 versions, then «Показати ще» loads the next page; given a failed request, then the section shows the error with «Спробувати ще раз» while the rest of the page stays usable.
- **AC-2.4** Given a dean who may read the submitted award, then the section starts at the submission; given a draft, the section is not requested by anyone but the owner.
- **AC-2.5** Given a holder of `audit:read` on the award page, then an «Журнал аудиту» tab lists the audit rows (time, actor, action, entity, changed fields, IP) with the old and new values on expansion; other users see no tab.
- **AC-2.6** Given the audit tab, when he chooses «Експорт CSV», then `GET /api/v1/awards/{id}/audit-trail/export` downloads `award-{id}-audit-{yyyy-MM-dd}.csv` (UTF-8 with BOM, `;` separator, columns: time UTC ISO-8601, actor id, actor e-mail, action, entity type, entity id, changed fields, old values JSON, new values JSON, IP address, correlation id), and an `AUDIT_EXPORT` row is written (`entity_type` `awards`, `entity_id` the award, row count in `new_values`).
- **AC-2.7** Given a cell value starting with `=`, `+`, `-`, `@`, tab or carriage return, then the CSV cell is prefixed with `'`; given more than 10 000 rows, then the newest 10 000 are written and the response carries `X-Audit-Truncated: true`.
- **AC-2.8** Given the UI in English, then all labels of the history section, the dialog and the audit tab are in English.

## 5. Edge cases

| Case | Expected |
|------|----------|
| Two saves of the same draft in parallel | The row lock of `lockedDraft` serialises them; the second answers 409 `award-stale`; one version per `awards.version` (unique `(award_id, version_number)`) |
| Double submit | One `SUBMITTED` version (the second call answers 409 before writing) |
| Save that only reorders whitespace | Normalised by `AwardInputRules`; no field changes, no version |
| Category renamed or deactivated after a version | The version shows the category's current name (snapshots keep ids; categories are never deleted, `ON DELETE RESTRICT`) |
| Actor account erased later (Epic 6) | `actor_id` set to NULL; the UI shows «Невідомий користувач» |
| Owner moves department after submission | The snapshot keeps the organisation at that version; access follows `awards.organization_id` as in 2.1 |
| Delegate reviewer (Feature 1.2) | Reads versions like the delegated role; no write in this feature |
| Audit trail of a draft | Only `audit:read` holders; drafts are otherwise private |
| Audit trail rows written before 2.2.1 | Shown with an empty actor; `DELETE` rows of other tables written earlier keep the wrong `entity_id` (rows are immutable) |
| Export while rows are being written | One read-only transaction; the file is consistent with its moment |
| Audit values containing personal data (description, IP) | Visible to oversight roles only, as `audit:read` already allows; the export is audited |
| Non-numeric or unknown award id in the URL | 400 for non-numeric (as 2.1), 404 for unknown |

## 6. Dependencies

### Tables

| Table | Change |
|-------|--------|
| `award_versions` (new, V023) | `version_id BIGSERIAL PK`, `award_id BIGINT NOT NULL → awards ON DELETE CASCADE`, `version_number BIGINT NOT NULL`, `action VARCHAR(20) NOT NULL` (`BASELINE`, `CREATED`, `UPDATED`, `SUBMITTED`; Epic 4 extends the check), `actor_id BIGINT → users ON DELETE SET NULL`, `snapshot JSONB NOT NULL`, `changed_fields TEXT[]`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`; unique `(award_id, version_number)`; a `BEFORE UPDATE` trigger refuses changes; baseline insert for existing awards |
| `fn_audit_trigger()` (V023) | `DELETE` rows take `entity_id` from the table's own key, as `INSERT`/`UPDATE` already do |
| `audit_logs` | No schema change; new action `AUDIT_EXPORT` |

Snapshot fields: `title`, `titleUk`, `description`, `descriptionUk`, `awardingOrganization`, `awardDate`, `categoryId`, `status`, `impactScore`, `verificationBadge`, `externalUrl`, `organizationId`.

### Endpoints

| Endpoint | Story | Access |
|----------|-------|--------|
| `GET /api/v1/awards/{id}/versions?page&size` (new) | 2.2.1 | `award:read:own` for the owner; `award:read:*` scope for non-drafts |
| `GET /api/v1/awards/{id}/audit-trail?page&size` (new) | 2.2.1 | `audit:read` |
| `GET /api/v1/awards/{id}/audit-trail/export` (new, `text/csv`) | 2.2.2 | `audit:read` |

### Services and libraries

- `AwardHistory` (new, `award/service`): writes a version after `saveAndFlush` in `create`, `update` and `AwardSubmission`; reads pages and computes the changes between consecutive snapshots
- Audit context binding (new, `audit`): sets `app.current_user_id` and `app.correlation_id` with `set_config(…, true)` at the start of each read-write transaction of a signed-in request, from the JWT subject and `ClientRequest`
- `AuditTrailService` (new, `audit/service`): award-scoped query over `audit_logs` and the CSV writer
- No new library (Envers not added, D-2)

### Frontend

- `award-detail.component.ts`: new child components `award-history` (list, paging, error state) and `award-version-dialog`; `award-audit-trail` tab for `audit:read`
- `awards.service.ts`: `versions(id, page)`, `auditTrail(id, page)`, `exportAuditTrail(id)` (blob download)
- i18n keys in `uk` and `en` for actions and field labels; the award form is not touched (the split noted in the tracker stays with the Epic 4 review view)

### External systems

None.

## 7. Technical decisions

| # | Decision | Reasoning | Source |
|---|----------|-----------|--------|
| D-1 | Versions are stored as full snapshots of the business fields; the field changes are computed when read | A snapshot stands alone: any version can be shown without replaying earlier ones and one bad row does not corrupt the rest; with twelve fields per award the comparison costs nothing | Roadmap 2.2.1 ("JSON diff"), deviation 1 |
| D-2 | A dedicated `award_versions` table written by the application, not Hibernate Envers and not the `audit_logs` trigger rows | Envers would add `awards_aud` and `revinfo` beside the trigger that already records every row, know only add/modify/delete (not "submitted" or, in Epic 4, "returned") and need a custom revision listener for the actor. Reading history from `audit_logs` would tie a user-facing feature to the immutable seven-year compliance log: erasing an award (Epic 6) would then have to delete from the log, and the log holds technical columns (`updated_at`, `version`) as changes. A separate table follows the award's lifecycle (deleted with a draft, erased with an award), while `audit_logs` stays the untouched compliance record | Tracker decision 2026-09-28, ADR-004, DATA_GOVERNANCE §4 |
| D-3 | The actor for trigger rows is set per transaction with `set_config('app.current_user_id', …, true)` by the transaction setup of signed-in requests; read-only transactions are skipped | The trigger already reads the setting; a transaction-local setting cannot leak to the next borrower of a pooled connection; one place covers every audited table, including those added later | V013 trigger, Feature 2.1 risk 4 |
| D-4 | History is visible to whoever may read the award; non-owners see it from the submission on | Drafts are private work (2.1 D-8); the reviewer needs what was submitted and every change after | Feature 2.1 D-8, AUTH §3.3 |
| D-5 | The audit trail is per award and only for `audit:read` (system administrator, GDPR officer); the system-wide search is Epic 6 | Keeps this feature at the roadmap's size; the per-award report is what an auditor asks for about one decision | AUTH §3.3 permission table, roadmap Epic 6 |
| D-6 | The CSV is UTF-8 with BOM and `;` separator, formula-leading cells escaped | Ukrainian text opens correctly in Excel with a Ukrainian locale; CSV injection is a known export risk (OWASP) | SECURITY_ARCHITECTURE, DATA_GOVERNANCE §4 |

**Proposed deviations from the docs** (applied in the story that touches them, after approval):

1. **Snapshots instead of stored JSON diffs** (D-1): roadmap task "Design award_versions table with JSON diff".
2. **No Envers** (D-2): roadmap task "Implement Hibernate Envers integration".
3. **`award_versions` instead of `awards_audit`**: DATABASE_DESIGN_STANDARDS §3.2 shows an `awards_audit` table with `operation` INSERT/UPDATE/DELETE. The generic trigger into `audit_logs` already fills that role; the new table records business versions, so it is named after them and its actions are business actions. §3.2 is updated to describe both.
4. **Trigger `DELETE` rows** (V023): the V013/V022 function takes `entity_id` of every deleted row from `user_id`, so a deleted award is logged under its owner's id and a deleted role under the user. Fixed for new rows; older rows stay as written.

## 8. Test plan

| AC | Unit | Slice | IT | FT | E2E |
|----|------|-------|----|----|-----|
| 1.1–1.4 | ✓ `AwardHistory` change computation (table of field pairs, nulls, dates) | | ✓ versions written and rolled back on 409/422; unique constraint; update refused by trigger | ✓ create → save twice (one unchanged) → stale → submit: versions 1, 2, 3 | |
| 1.5 | | | ✓ cascade; trigger `DELETE` `entity_id` | ✓ delete draft, audit trail shows `DELETE` | |
| 1.6 | ✓ context binder skips read-only and anonymous | | ✓ trigger row `user_id` and `correlation_id` for award save, role assignment, profile change; migration-time rows empty | ✓ `X-Correlation-Id` sent → found in trigger row | |
| 1.7 | | | ✓ `SchemaIT`: baseline rows after V023 | | |
| 1.8, 1.9 | ✓ visibility rule | ✓ controller, paging cap, 404 | ✓ query | ✓ owner, dean of faculty 9, secretary of faculty 10, admin, draft as dean | |
| 1.10, 1.11 | | ✓ controller, 403 | ✓ rows of `awards` and `award_requests` joined; deleted award | ✓ admin 200, employee 403 + `ACCESS_DENIED` row | |
| 2.1–2.4, 2.8 | ✓ components (history, dialog, paging, error, labels) | | | | ✓ edit twice, submit, history shows 4 entries; dean sees from submission; English |
| 2.5–2.7 | ✓ CSV writer (escaping, BOM, truncation) | ✓ content type, filename | ✓ `AUDIT_EXPORT` row | ✓ export as admin, 403 as dean | ✓ admin downloads the file |

Coverage target 85 % lines per `mvn verify`; static analysis clean; Playwright for AC-2.1–2.6; the audit-context binding goes through the security review agent (it touches every write).

## 9. Manual verification

Preconditions: `.\tools\dev-up.ps1` (backend `local` profile on `http://localhost:8080`, frontend `http://localhost:4200`). Seed accounts (password `Passw0rd-demo`): `employee.fmi@chnu.edu.ua` (department 64, faculty 9), `dean.fmi@chnu.edu.ua` (faculty 9), `admin@chnu.edu.ua` (`SYSTEM_ADMIN`, holds `audit:read`); `secretary.fpp@chnu.edu.ua` (faculty 10) as in Feature 2.1 §9 preconditions. Swagger at `http://localhost:8080/swagger-ui.html`. psql: `docker compose exec postgres psql -U postgres award_monitoring`.

### After 2.2.1 (SCRUM-25)

1. psql: `select award_id, version_number, action, actor_id from award_versions order by award_id;` Expected: one `BASELINE` row per award existing before the migration, `actor_id` empty, `version_number` = `awards.version`. (AC-1.7)
2. Swagger as `employee.fmi`: `POST /api/v1/awards` with only a title. `GET /api/v1/awards/{id}/versions`. Expected: one version, `CREATED`, actor «employee.fmi»'s name, no changes. (AC-1.1, 1.8)
3. `PUT` the draft with a new title and a category (current `version`), then `PUT` the same body again with the new `version`. Expected: the first answers with `version` 2, the second keeps 2; versions list: 2 entries, the newest `UPDATED` with changes `title` and `categoryId` (old → new). (AC-1.2)
4. `PUT` again with `version` 1 → 409 `award-stale`; versions list unchanged. (AC-1.4)
5. Complete the draft (organisation, date) and `POST …/submit`. Expected: newest version `SUBMITTED`, snapshot `status` `PENDING`, `impactScore` set. (AC-1.3)
6. psql: `select action_type, entity_type, user_id, correlation_id is not null from audit_logs where entity_type in ('awards','award_requests') order by created_at desc limit 5;` Expected: `user_id` = employee's id on every row. (AC-1.6)
7. As `admin`, assign a role to any account (Feature 1.2 §9 step 8), then psql: newest `user_roles` trigger row has `user_id` = admin's id. (AC-1.6)
8. `GET …/versions` for the submitted award as `dean.fmi` → 200, starting at `SUBMITTED` with no changes; as `secretary.fpp` → 404. Create a new draft as `employee.fmi` and read its versions as `dean.fmi` → 404. (AC-1.9)
9. `GET /api/v1/awards/{id}/audit-trail` as `admin` → rows `INSERT`/`UPDATE` of `awards`, `INSERT` of `award_requests`, `AWARD_SUBMITTED`, with actor and IP. As `dean.fmi` → 403; psql shows a new `ACCESS_DENIED` row. (AC-1.10, 1.11)
10. As `employee.fmi` delete the draft of step 8. psql: `select count(*) from award_versions where award_id = <id>;` → 0. As `admin` `GET …/{id}/audit-trail` → the `DELETE` row with `entity_id` = the award id and the employee as actor; `GET …/999999/audit-trail` → 404. (AC-1.5, 1.10)

### After 2.2.2 (SCRUM-26)

11. As `employee.fmi` open the award of step 5 at `http://localhost:4200/awards/<id>`. Expected: «Історія змін» with «Подано», «Змінено», «Створено», names and Kyiv times; «Змінено» shows «Назва: <old> → <new>» and «Категорія: — → <name>». (AC-2.1)
12. «Переглянути версію» on «Створено» → dialog with only the title filled, other fields «—». (AC-2.2)
13. As `dean.fmi` open the same page. Expected: the history starts at «Подано»; no «Журнал аудиту» tab. (AC-2.4, 2.5)
14. As `admin` open it. Expected: «Журнал аудиту» tab; rows expand to old and new values. «Експорт CSV» → `award-<id>-audit-<today>.csv`; opened in Excel the Ukrainian title reads correctly; psql shows an `AUDIT_EXPORT` row with the row count. (AC-2.5, 2.6)
15. As `employee.fmi` create a draft titled `=HYPERLINK("http://example.com")`, then as `admin` export its audit trail. Expected: the title appears only inside the `new_values` JSON cell, which starts with `{`; Excel shows it as text, not a link. Cells that start with a formula character on their own are prefixed with `'` (covered by `AuditTrailCsvTest`). (AC-2.7)
16. Switch to English. Expected: «Change history», action and field labels, the dialog and the tab in English. (AC-2.8)

### Detours

17. Open `http://localhost:4200/awards/<id of another faculty's award>` as `employee.fmi` → «Не знайдено», no history request. Swagger `GET /api/v1/awards/abc/versions` → 400. (AC-1.9)
18. Stop the backend with the award page open and reload the history («Спробувати ще раз» after a failed load). Expected: the error inside the section, the award fields still shown; start the backend, «Спробувати ще раз» → the list. (AC-2.3)
19. Open the same draft in two tabs, save in the first, reload the history in the second. Expected: the new version appears; a save from the second tab answers «Нагороду змінено в іншому вікні» and adds no version. (AC-1.4)
20. Let the access token expire on the award page (15 minutes) and press «Показати ще» or «Експорт CSV». Expected: silent refresh, the action completes; with the admin's session ended in another browser → login page. (AC-2.3, 2.6)
21. As `admin`, press «Експорт CSV» twice quickly. Expected: two files, two `AUDIT_EXPORT` rows; no error. (AC-2.6)
22. As `employee.fmi` open `http://localhost:8080/api/v1/awards/<id>/audit-trail/export` directly with the token in Swagger → 403 `access-denied`. (AC-1.11)
23. Save a draft 25 times (Swagger loop or the form), open the page. Expected: 20 entries and «Показати ще»; after it, 26 entries and no button. (AC-2.3)

## 10. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| The audit-context binding misses a write path (a `REQUIRES_NEW` transaction, a JDBC call outside JPA) | Some trigger rows keep an empty actor | Bound in the transaction manager's begin, so every Spring transaction is covered; IT checks the `REQUIRES_NEW` audit path and the role assignment |
| `audit_logs` and `award_versions` both hold award content with personal data (descriptions) | GDPR erasure in Epic 6 must reach both; the log is declared immutable | `award_versions` cascades with the award; erasure inside `audit_logs` is an Epic 6 decision (tracker open item on historical snapshots) |
| One extra `set_config` statement per read-write transaction | Small latency on every write | Skipped for read-only and anonymous transactions; measured in the FT timings |
| Epic 4 needs new version actions (returned, corrected, approved) | A check constraint change | Added in a new migration by the Epic 4 story; the action list is an enum in one place |

## 11. Definition of Done

- `./mvnw verify` green (unit, slice, IT, FT), JaCoCo ≥ 85 % lines, Checkstyle/PMD/SpotBugs clean
- `npm run lint`, `npm run test:ci`, Playwright scenarios for AC-2.1–2.6
- Docs in the same PRs: `openapi.yml`, DATA_DICTIONARY §2 (`award_versions`) and §4.1 (`AUDIT_EXPORT`, trigger actor and `DELETE` key), DATABASE_DESIGN_STANDARDS §3.2, AUTH §7 row, RBAC_matrix.md (history and audit trail rows), `CHANGELOG.md`, tracker rows, `BACKLOG.md`
- §9 walked through by the author after `/feature-validate`, including the detours

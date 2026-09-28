# Feature 2.1: Award Creation & Validation

> **Epic**: 2 — Award Lifecycle Management (SCRUM-20)
> **Sprint**: 3 (2026-09-28 → 2026-10-04)
> **Points**: 17 (four stories)
> **Status**: Approved 2026-09-28
> **Author**: Stefan Kostyk
> **Governing docs**: US-003, roadmap § Feature 2.1, DATA_DICTIONARY §2.1–§2.2 and §3.1, appendix B, BRD §7.3, AUTH §3.3, RBAC_matrix.md, ADR-004, ADR-005, ADR-009, ADR-013, openapi.yml `/awards`, state-machine-award-request.puml, sequence-award-submission.puml, EPIC-02 tracker (decisions and deviations 1–5)

## 1. Problem and personas

Awards are the reason the system exists, and today there is nowhere to put one. Staff record their achievements in Word files and e-mails to the faculty secretary; the secretary retypes them into a yearly report, finds duplicates by eye and guesses the recognition level. This feature gives every confirmed member of the university a form that takes an award from a first draft to a submitted request, checks the data on the way in and suggests the category, so that the approval workflow of Epic 4 starts from clean, classified records.

| Persona | Need in this feature |
|---------|----------------------|
| Anastasia, employee | Enter an award on her phone right after the ceremony, stop half-way and finish later, be warned before she submits the same award twice, see that the submission went through |
| Alina, faculty secretary | Receive awards with a date, an awarding organisation and a sensible category; submit her own awards like everybody else |
| Prof. Martynyuk, dean | Read the submitted awards of the faculty; submit his own |
| System administrator, GDPR officer | Read submitted awards for oversight; never submit |

## 2. Scope

**In (this feature)**

- Recognition levels and the category catalogue as reference data with a read endpoint; category seed that can change without touching awards (2.1.0)
- Award drafts: create, edit, delete while `DRAFT`; submission that creates the award request at the first approval level; own list and detail; scoped read of submitted awards; mobile-first Angular form with an interruption-safe local copy (2.1.1)
- Date rules (future, older than 50 years, recent) and duplicate detection among one's own awards (2.1.2)
- Rule-based category suggestion from the title, the awarding organisation and the owner's history (2.1.3)

**Out (where it goes)**

- Certificate photo, upload and metadata auto-population (US-003) — Epic 3 (upload in 3.x, OCR later); the form leaves a place for the photo
- Offline submission (US-003 additional scenario) — Epic 8 PWA; the local copy of 2.1.1 covers interruption, not offline sync
- Reviewer queue, assignment, decisions, `RETURNED` corrections, the "nobody reviews their own award" rule — Epic 4
- Status timeline, estimated completion, request deadline — 2.3.1 (US-005)
- Version history and the actor in `audit_logs` rows written by `trg_awards_audit` (`app.current_user_id` is not set by the application yet) — design note before Feature 2.2
- E-mail confirmation of a submission — Epic 7; the on-screen confirmation is in 2.1.1
- Submitting on behalf of another person — not requested by any story; revisit at Epic 4 kickoff
- Impact-score modifiers by awarding organisation prestige — only the level base score is set (2.1.1)
- Public award pages — after Epic 4

## 3. Stories

| Key | Story | Pts | Parallel | Depends on |
|-----|-------|-----|----------|------------|
| SCRUM-21 (#43) | 2.1.0 Award domain model and category catalogue | 3 | no | Epic 1 |
| SCRUM-22 (#44) | 2.1.1 Award draft and submission (US-003) | 8 | yes | SCRUM-21 |
| SCRUM-23 (#76) | 2.1.2 Award date and duplicate validation | 3 | no | SCRUM-22 |
| SCRUM-24 (#77) | 2.1.3 Award category suggestion | 3 | no | SCRUM-21, SCRUM-22 (form) |

SCRUM-21 fixes the award and category schemas in `openapi.yml`; SCRUM-22 adds the award endpoints to the contract first, so the Angular form can be built against it while the backend is in progress.

## 4. Acceptance criteria

### 2.1.0 Award domain model and category catalogue (SCRUM-21)

- **AC-0.1** Given the nine recognition levels of `ck_award_categories_level`, then the application holds them as one enum carrying the minimum approval level and the impact base score of §7 (D-3), and DATA_DICTIONARY §2.2 lists all nine with both values.
- **AC-0.2** Given `GET /api/v1/award-categories` by any authenticated user, then the active categories are returned as a tree (roots ordered by `sort_order`, children nested), each with `id`, `name`, `nameUk`, `description`, `level` and `children`; inactive categories are absent. The answer carries `Cache-Control: max-age=3600` and an `ETag`; a matching `If-None-Match` answers 304. Unauthenticated: 401.
- **AC-0.3** Given the category seed, then the four levels without categories (`SPECIALITY`, `COLLEGE`, `LOCAL`, `REGIONAL`) get a root category and at least two children each, with Ukrainian and English names.
- **AC-0.4** Given an award that references a category, when `R__seed_award_categories.sql` changes and runs again, then the award and its category survive: the seed upserts by `category_id` (`INSERT … ON CONFLICT DO UPDATE`) and deactivates removed categories instead of truncating the table (the current `TRUNCATE … CASCADE` would delete every award).
- **AC-0.5** `openapi.yml` award schemas follow the schema of record (tracker deviation 1): integer ids, `AwardStatus` = `DRAFT | PENDING | APPROVED | REJECTED | ARCHIVED` (the award) and a separate `RequestStatus` and `ApprovalLevel` from `award_requests`, `awardingOrganization`, title up to 500, `titleUk`/`descriptionUk`, nine-value `RecognitionLevel`; `GET /awards` filter `category` is an integer and `/award-categories` exists (deviation 2).

### 2.1.1 Award draft and submission (SCRUM-22)

- **AC-1.1** Given a caller with `award:create`, when `POST /api/v1/awards` is called with at least a title (`title` or `titleUk`), then a `DRAFT` award owned by the caller is created with `organization_id` = the caller's department, the answer is 201 with `Location` and the award. Any other field may be empty in a draft; fields that are present are validated (lengths §5, category active, `externalUrl` http or https, date rules of 2.1.2). Invalid input answers 422 `validation-failed` with one entry per field.
- **AC-1.2** Given a caller without `award:create` (an unconfirmed account, `SYSTEM_ADMIN`, `GDPR_OFFICER`), then `POST /awards` answers 403 `access-denied` and the refusal is audited (Feature 1.2 AC-1.5).
- **AC-1.3** Given the owner's `DRAFT`, when `PUT /api/v1/awards/{id}` is called with the full form and the `version` last read, then the draft is replaced and the answer carries the new `version`; a stale `version` answers 409 `award-stale` with the current version in the body. An award that is not `DRAFT` answers 409 `award-not-editable`.
- **AC-1.4** Given the owner's `DRAFT`, when `DELETE /api/v1/awards/{id}` is called, then the row is deleted (the audit trigger keeps the old values) and the answer is 204; a submitted award answers 409 `award-not-editable`.
- **AC-1.5** Given the owner's `DRAFT`, when `POST /api/v1/awards/{id}/submit` is called with the current `version`, then the award is checked for completeness (title, category, awarding organisation, award date) and the date rules; missing fields answer 422 `award-incomplete` naming each field. On success, in one transaction: `awards.status` becomes `PENDING`, `impact_score` is the level base score, an `award_requests` row is created with `status = SUBMITTED`, `current_level = FACULTY_SECRETARY`, `submitter_id` = the caller, `submitted_at` = now; an `AWARD_SUBMITTED` row is written to `audit_logs`; the answer is 200 with the award and its request.
- **AC-1.6** Given the same submission repeated (double click, second tab, back and resubmit), then exactly one request exists: the second call answers 409 `award-not-editable` (status check under a row lock, backed by `uk_award_requests_award`).
- **AC-1.7** Given `GET /api/v1/awards`, then the caller's own awards are listed (all statuses, newest first), filterable by `status`, `category`, `dateFrom`, `dateTo`, page size capped at 100, each with its request status when submitted.
- **AC-1.8** Given `GET /api/v1/awards/{id}`, then the owner always gets the award; a holder of `award:read:department|faculty|all` gets a non-draft award whose `organization_id` lies in the scope of a role granting that permission (the rule of Feature 1.2 AC-1.6); anybody else, and anybody but the owner for a `DRAFT`, gets 404.
- **AC-1.9** Angular: «Мої нагороди» in the navigation for holders of `award:read:own`; `/awards` lists own awards with status chips and filters; `/awards/new` and `/awards/:id/edit` show a single-column form usable at 360 px width (title uk/en, category picker from the catalogue tree, awarding organisation, date picker limited by 2.1.2, description, external link), «Зберегти чернетку» and «Подати»; `/awards/:id` shows the award read-only once submitted. After «Подати» a confirmation screen names the award and «Подано на розгляд секретарю факультету». All texts in Ukrainian and English.
- **AC-1.10** Given an interrupted form (tab closed, phone call, session expired), when the user returns to the same form, then the unsaved values are offered back («Відновити незбережені зміни?») from a copy kept in the browser per user and award; the copy is removed on save, submit, discard and sign-out. Leaving a form with unsaved changes asks for confirmation.
- **AC-1.11** RBAC_matrix.md "Submit Award Request" and "Edit Own Award Request" are marked for the approver roles (tracker decision 2026-09-28); DATA_DICTIONARY §2.1 records the draft rules and `organization_id` of §6.

### 2.1.2 Award date and duplicate validation (SCRUM-23)

- **AC-2.1** Given an award date after today in `Europe/Kyiv`, when a draft is saved or submitted, then 422 `validation-failed` on `awardDate` («Дата нагороди не може бути в майбутньому»); the database check `ck_awards_date`, recreated on the Kyiv calendar in V020, agrees with the application at every hour (§5).
- **AC-2.2** Given an award date more than 50 years before today, then 422 on `awardDate` («Дата нагороди не може бути давнішою за 50 років»); exactly 50 years ago is accepted.
- **AC-2.3** Given an award date within the last 30 days, then the saved award carries the warning `RECENT_DATE` («Перевірте дату: вказано дату за останні 30 днів — це дата нагородження, а не подання?»); it is shown in the form and needs no acknowledgement.
- **AC-2.4** Given another award of the same owner with the same award date and a title similarity of at least 0.6 (`pg_trgm` `similarity` on the lower-cased title, Ukrainian or English, whichever both have), then the award carries the warning `POSSIBLE_DUPLICATE` with the matching awards (`id`, `title`, `awardDate`, `status`). Awards of other people are never compared or shown.
- **AC-2.5** Given a `POSSIBLE_DUPLICATE` warning, when the award is submitted without `acknowledgeDuplicate: true`, then 409 `award-possible-duplicate` listing the matches and nothing changes; with the acknowledgement it is submitted and the audit row records `duplicateAcknowledged: true`.
- **AC-2.6** Angular: the date picker disables future dates and dates older than 50 years; warnings appear under the fields they concern; a possible duplicate opens a dialog with links to the matching awards and «Це інша нагорода — подати» / «Скасувати».

### 2.1.3 Award category suggestion (SCRUM-24)

- **AC-3.1** Given `GET /api/v1/award-categories/suggestions?title=…&organization=…` by a holder of `award:create`, then up to three active categories are returned, ranked by score, each with the reasons that produced it: `KEYWORD` (category keywords found in the title or organisation), `ORGANISATION` (the awarding organisation names a unit of the organisation tree: a department → `DEPARTMENT`, a faculty → `FACULTY`, the university alone → `UNIVERSITY`), `HISTORY` (the caller's most used categories, lowest weight). Inputs shorter than three characters give an empty list.
- **AC-3.2** Given the labelled fixture of 30 award titles and organisations in `src/test/resources` (Ukrainian and English, all nine levels), then for at least 24 of them the expected level is among the top three suggestions.
- **AC-3.3** Given category keywords, then they are reference data in `award_categories.keywords` (Ukrainian and English stems), seeded by the category seed, not code.
- **AC-3.4** Angular: after the title and organisation are typed (debounced 400 ms), suggestions appear as chips under the category field with the level; choosing one fills the category; a category the user picked is never replaced; no chips when the service fails.

## 5. Edge cases

- Field limits: `title`, `titleUk` ≤ 500; `description`, `descriptionUk` ≤ 4000; `awardingOrganization` ≤ 255; `externalUrl` ≤ 2048 and `http`/`https` only (no `javascript:`); leading and trailing spaces trimmed, blank counts as empty. Text is stored as typed and escaped on output.
- A draft with only a title: allowed by V020's `ck_awards_complete` (category, organisation and date required unless `DRAFT`), so the database refuses an incomplete `PENDING` award even if the service had a gap.
- Category deactivated after the draft was saved: the draft keeps it and shows it; submission answers 422 on `categoryId` («Категорія більше не доступна»).
- Owner moved to another department between draft and submission: `organization_id` is refreshed from the owner's department at submission, so the request goes to the faculty the person belongs to now; a submitted award keeps its organisation.
- Owner loses `award:create` (role revoked) with a draft open: the next save answers 401/403 as in Feature 1.2; existing drafts stay readable and deletable through `award:read:own`.
- Two tabs editing one draft: the second save answers 409 `award-stale`; the form offers to reload and keeps the typed values in the local copy.
- Submitting a draft from a stale tab after it was submitted elsewhere: 409 `award-not-editable`, the page reloads into the read-only view.
- Time zone: "today" is `LocalDate.now(clock)` with the `Europe/Kyiv` clock. The database check `ck_awards_date` compares with `CURRENT_DATE` of the session zone (UTC), so between 00:00 and 03:00 Kyiv time it would refuse an award dated today that the application accepted (a 500). V020 replaces it with `award_date <= (now() AT TIME ZONE 'Europe/Kyiv')::date`; the tracker's note that application validation keeps the check from firing is corrected.
- Team awards: the same award received by several colleagues is not a duplicate; each colleague submits their own.
- Duplicate check performance: one indexed query on `(user_id, award_date)` then `similarity` on the few rows of that day; `trgm_awards_title` (V012) is not needed for it.
- Local copy and privacy: the browser copy holds only the form fields of the signed-in user, is keyed by user id and removed on sign-out; nothing is written for a shared or unauthenticated session.
- Category suggestion never sends another person's data: `HISTORY` reads only the caller's awards.

## 6. Dependencies

### Tables

| Table | Status | Change |
|-------|--------|--------|
| `award_categories` | existing (V004) | 2.1.0: seed rewritten as upsert, four levels seeded; 2.1.3: V021 adds `keywords TEXT[] NOT NULL DEFAULT '{}'` with a GIN index, seeded by `R__seed_award_categories.sql` |
| `awards` | existing (V005) | 2.1.1: V020 adds `organization_id BIGINT NOT NULL FK organizations` (backfill not needed, the table is empty) with index `(organization_id, status)`; `title`, `category_id`, `awarding_organization`, `award_date` become nullable; `ck_awards_title` (`title IS NOT NULL OR title_uk IS NOT NULL`); `ck_awards_complete` (`status = 'DRAFT' OR (category_id, awarding_organization, award_date all not null)`); index `(user_id, award_date)` for the duplicate check; `ck_awards_date` recreated on the Kyiv date (§5) |
| `award_requests` | existing (V007) | none; created at submission with `status = SUBMITTED`, `current_level = FACULTY_SECRETARY`; `current_reviewer_id` and `deadline` stay empty until Epic 4 / 2.3.1 |
| `audit_logs` | existing | new `action_type` `AWARD_SUBMITTED`; row changes keep coming from `trg_awards_audit` |

### Endpoints

| Method and path | Status | Story |
|-----------------|--------|-------|
| `GET /api/v1/award-categories` | new | SCRUM-21 |
| `POST /api/v1/awards`, `GET /api/v1/awards`, `GET/PUT/DELETE /api/v1/awards/{id}` | in `openapi.yml`, unimplemented; schemas aligned (AC-0.5), `version` in the update body, `warnings` in the award | SCRUM-21 (schema), SCRUM-22 |
| `POST /api/v1/awards/{id}/submit` | in `openapi.yml`; gains the body `{version, acknowledgeDuplicate}` and 409/422 answers | SCRUM-22, SCRUM-23 |
| `GET /api/v1/award-categories/suggestions` | new | SCRUM-24 |
| Problem types | `validation-failed`, `award-incomplete`, `award-stale`, `award-not-editable`, `award-possible-duplicate` | all |

### Services and libraries

- Package `ua.edu.chnu.awards.award`: `RecognitionLevel` enum, `AwardCategory`/`Award`/`AwardRequest` entities, `CategoryCatalogue`, `AwardService` (drafts), `AwardSubmission`, `AwardDateRules`, `DuplicateFinder`, `CategorySuggester`; `AwardController`, `AwardCategoryController`
- Reused from Epic 1: `@access` scope helpers and `OrganizationTree` (scoped reads, organisation matching in suggestions), `AccessDenials`, `AuditService`, the `Europe/Kyiv` clock, `ApiExceptionHandler` problem details
- No new libraries; `pg_trgm` is already installed (V012)

### Frontend

- Feature `features/awards`: routes `/awards`, `/awards/new`, `/awards/:id`, `/awards/:id/edit` under the authenticated shell, guarded by `award:read:own` / `award:create`; NgRx feature `awards` (list, filters, current award, save state) and a cached category catalogue
- Components: award list, award form (category tree picker, date picker, warnings, suggestion chips), duplicate dialog, submission confirmation, read-only detail; unsaved-changes guard; local-copy service
- i18n keys under `awards.*`, `categories.*`

### External systems

None.

## 7. Technical decisions

| # | Decision | Reasoning | Source |
|---|----------|-----------|--------|
| D-1 | The migrations are the schema of record; `openapi.yml` award schemas are rewritten to integer ids and the two status sets of `awards` and `award_requests` | As in Epic 1; the spec was written before the schema | Tracker deviation 1 |
| D-2 | A draft is an `awards` row in PostgreSQL, not a Redis entry; interruption safety is a browser copy of the unsaved form | A draft is the user's data and must survive restarts and reach other devices; ADR-005 places Redis as a cache and session store, not as the owner of business data | ADR-005, US-003 |
| D-3 | Recognition levels (minimum approval level, impact base score): `SPECIALITY` (faculty secretary, 10), `DEPARTMENT` (faculty secretary, 20), `COLLEGE` (dean, 30), `FACULTY` (dean, 40), `LOCAL` (faculty secretary, 45), `UNIVERSITY` (faculty secretary, 60), `REGIONAL` (faculty secretary, 70), `NATIONAL` (rector's secretary, 80), `INTERNATIONAL` (rector's secretary, 100). The minimum is the lowest role that may give the final approval; any role above it in the same line may approve too, so local, regional and university awards are approved by the faculty secretary or the dean, and national and international awards by the rector's secretary or the rector | Keeps the five documented values and slots the four schema-only levels between them; local (city, community) and regional (oblast) recognition is confirmed inside the faculty like department awards, while the impact score still ranks them by reach; approved 2026-09-28 | DATA_DICTIONARY §2.1–§2.2, tracker decision 2026-09-28 |
| D-4 | Every submission starts at `FACULTY_SECRETARY`; escalation by level is Epic 4 | The state machine note "first reviewer is always Faculty Secretary" | state-machine-award-request.puml |
| D-5 | Create and submit are two calls; the form's «Подати» on a new award saves then submits | One submission path, one set of checks; `openapi.yml` already separates `/submit` | openapi.yml |
| D-6 | Date rules and duplicate detection are application checks returning typed problems and warnings; the database keeps its date check as a backstop | Bean Validation alone cannot express warnings or the Kyiv "today" | Roadmap 2.1.2, BRD §7.3 |
| D-7 | Category suggestion is rule-based (keywords, organisation tree, history), measured on a labelled fixture | No training data exists; an NLP model is a year-2 item | BRD §3.3, tracker risk 3 |
| D-8 | Awards readable outside their owner only after submission, within the reader's organisation scope; foreign ids answer 404 | Drafts are private work; same pattern as the user directory | Feature 1.2 AC-1.7 |

**Proposed deviations from the docs** (applied in the story that touches them, after approval):

1. **`awards.organization_id`** (new column, V020): the award records the owner's department at submission. Without it, scoped reads and the Epic 4 reviewer routing would follow the owner's current department, so a colleague who moves faculty would carry past awards into the new faculty's reports and queues.
2. **Nullable draft columns** (V020): `title`, `category_id`, `awarding_organization`, `award_date` are required only outside `DRAFT` (`ck_awards_complete`), and a title may be Ukrainian only (`ck_awards_title`). The dictionary requires all four and an English title, which makes "save progress and complete later" (US-003) impossible and forces Ukrainian staff to invent an English title.
3. **Category seed as upsert** (2.1.0): `TRUNCATE award_categories CASCADE` in the repeatable seed would delete all awards on the next seed change.
4. **Recent-date warning wording** (2.1.2): the roadmap's "warning if within 30 days of submission" is kept as an informational hint (possible confusion of award and submission date), not a blocker, because recent awards are the normal case of US-003.
5. **Description limit 4000** instead of 2000 in `openapi.yml`: citations of research awards run longer; the column is `TEXT`.
6. **`award_categories.keywords`** (V021) for the suggestion rules.
7. RBAC_matrix: approvers may submit their own awards (tracker decision 2026-09-28).
8. **`ck_awards_date` on the Kyiv calendar** (V020): the V005 check uses the database session's date and refuses valid awards shortly after midnight Kyiv time (§5).

## 8. Test plan

| AC | Unit | Slice | IT | FT | E2E |
|----|------|-------|----|----|-----|
| 0.1, 0.3 | ✓ level table | | ✓ seed: nine levels present | | |
| 0.2 | ✓ tree builder | ✓ controller, ETag/304 | ✓ inactive hidden | ✓ | |
| 0.4 | | | ✓ reseed keeps an award | | |
| 0.5 | | | | ✓ `OpenApiContractTest` | |
| 1.1–1.4 | ✓ service rules (table) | ✓ controller, 422 body | ✓ constraints V020, optimistic lock | ✓ create → edit → stale → delete | ✓ |
| 1.5, 1.6 | ✓ submission | ✓ | ✓ request row, unique, row lock | ✓ submit twice, audit row | ✓ confirmation |
| 1.7, 1.8 | ✓ scope rule | ✓ | ✓ queries | ✓ owner, dean of faculty 9, secretary of faculty 10, admin | ✓ list |
| 1.9, 1.10 | ✓ components, local-copy service, guard | | | | ✓ form at 360 px, restore after reload |
| 2.1–2.3 | ✓ `AwardDateRules` with a fixed Kyiv clock (boundaries) | ✓ | | ✓ | |
| 2.4, 2.5 | ✓ | | ✓ similarity threshold on real Postgres | ✓ duplicate → 409 → acknowledged | ✓ dialog |
| 2.6 | ✓ date picker limits, dialog | | | | ✓ |
| 3.1, 3.3 | ✓ each rule | ✓ controller | ✓ keywords from the seed | ✓ | |
| 3.2 | ✓ fixture test (≥ 24/30) | | | | |
| 3.4 | ✓ chips, debounce, no overwrite | | | | ✓ |

Coverage target 85 % lines per `mvn verify`; static analysis clean; Playwright for every UI AC, including a 360 × 740 viewport run of the form.

## 9. Manual verification

Preconditions: `.\tools\dev-up.ps1` (backend `local` profile on `http://localhost:8080`, frontend `http://localhost:4200`, Mailpit `http://localhost:8025`). Seed accounts (password `Passw0rd-demo`): `employee.fmi@chnu.edu.ua` (`EMPLOYEE`, department 64 «Кафедра алгебри та інформатики», faculty 9), `secretary.fmi@chnu.edu.ua`, `dean.fmi@chnu.edu.ua` (faculty 9), `rector@chnu.edu.ua`, `admin@chnu.edu.ua`. Swagger at `http://localhost:8080/swagger-ui.html`. psql: `docker compose exec postgres psql -U postgres award_monitoring`. For step 9, a secretary of another faculty: register `secretary.fpp@chnu.edu.ua` with department 69 «Кафедра педагогіки та соціальної роботи», verify through Mailpit, and as `admin` assign `FACULTY_SECRETARY` for organisation 10 (Feature 1.2 §9 step 8).

### After 2.1.0 (SCRUM-21)

1. Swagger as `employee.fmi`: `GET /api/v1/award-categories`. Expected: 200, nine root categories (one per level) with children, Ukrainian and English names; response headers `Cache-Control: max-age=3600` and `ETag`. Repeat with `If-None-Match: <etag>` → 304. Without a token → 401. (AC-0.2, 0.3)
2. psql: `select level, count(*) from award_categories where is_active group by level order by 1;` → nine rows. (AC-0.1, 0.3)
3. After step 5 exists: edit a category name in `R__seed_award_categories.sql`, restart the backend. Expected: `select count(*) from awards;` unchanged, the new name in `GET /award-categories`. (AC-0.4)

### After 2.1.1 (SCRUM-22)

4. Open `http://localhost:4200` as `employee.fmi` in a phone-sized window (DevTools device toolbar, 360 × 740). Expected: «Мої нагороди» in the navigation; `/awards` shows the empty list with «Додати нагороду». (AC-1.9)
5. «Додати нагороду», type only «Грамота Міністерства освіти і науки», «Зберегти чернетку». Expected: saved, the list shows it with the chip «Чернетка»; psql `select award_id, status, organization_id, category_id from awards;` → `DRAFT`, 64, empty category. (AC-1.1)
6. Open the draft, fill category «Відзнака міністерства», awarding organisation «Міністерство освіти і науки України», a date last year, «Подати». Expected: confirmation «Подано на розгляд секретарю факультету»; the list chip «На розгляді»; psql `select status, current_level, submitter_id from award_requests;` → `SUBMITTED`, `FACULTY_SECRETARY`; `select action_type from audit_logs where action_type = 'AWARD_SUBMITTED';` → one row; `awards.impact_score` = 80. (AC-1.5, 1.9)
7. Create a second draft with only a title and click «Подати». Expected: the fields category, organisation and date are marked as required, nothing submitted; Swagger `POST /api/v1/awards/{id}/submit` → 422 `award-incomplete` listing the three fields. (AC-1.5)
8. Swagger as `employee.fmi`: `DELETE` the second draft → 204; `DELETE` the submitted award → 409 `award-not-editable`; `PUT` it → 409 `award-not-editable`. (AC-1.3, 1.4)
9. Swagger `GET /api/v1/awards/{submitted id}` as `dean.fmi` → 200; as `secretary.fpp` (faculty 10) → 404; as `admin` → 200. Create a new draft as `employee.fmi` and read it as `dean.fmi` → 404. (AC-1.8)
10. Swagger `POST /api/v1/awards` as `admin` → 403 `access-denied`; register a fresh account without confirmation and try → 403. (AC-1.2)
11. As `secretary.fmi` open `/awards/new`, submit an award of her own. Expected: accepted, request at `FACULTY_SECRETARY`. RBAC_matrix shows the approver columns ticked for "Submit Award Request". (AC-1.11)
12. Switch the app to English. Expected: the list, form, chips and confirmation in English. (AC-1.9)

### After 2.1.2 (SCRUM-23)

13. In the form open the date picker. Expected: tomorrow and dates older than 50 years are disabled. In Swagger save a draft with tomorrow's date → 422 on `awardDate`; with the date exactly 50 years ago → 201; 50 years and one day → 422. (AC-2.1, 2.2, 2.6)
14. Save a draft dated a week ago. Expected: the hint under the date field about the last 30 days; the award saves and submits without extra steps. (AC-2.3)
15. Create a draft «Грамота Міністерства освіти і науки України» with the same date as the award of step 6 and «Подати». Expected: the duplicate dialog with a link to the first award; «Скасувати» leaves it a draft; «Це інша нагорода — подати» submits; psql shows `duplicateAcknowledged` in the `AWARD_SUBMITTED` row. Swagger submit without `acknowledgeDuplicate` → 409 `award-possible-duplicate`. (AC-2.4, 2.5, 2.6)
16. As `secretary.fmi` create the same title and date. Expected: no duplicate warning (another owner). (AC-2.4)

### After 2.1.3 (SCRUM-24)

17. New award, title «Best paper award», organisation «IEEE International Conference on Software Engineering». Expected: within half a second chips appear, the first at level «Міжнародний». Choose it → the category field is filled. (AC-3.1, 3.4)
18. Title «Подяка», organisation «Факультет математики та інформатики ЧНУ». Expected: a faculty-level chip first with the reason «підрозділ університету». Pick another category by hand, then change the title → the chosen category stays. (AC-3.1, 3.4)
19. Swagger `GET /api/v1/award-categories/suggestions?title=ab` → empty list; as `admin` → 403. psql `select name, keywords from award_categories where level = 'REGIONAL';` → keywords present. (AC-3.1, 3.3)

### Detours

20. Type half an award into `/awards/new`, close the tab, open `/awards/new` again. Expected: «Відновити незбережені зміни?»; «Відновити» brings the values back; sign out and in → no offer (the copy is gone). (AC-1.10)
21. With a filled, unsaved form click «Мої нагороди». Expected: the leave-page confirmation; «Залишитися» keeps the values. (AC-1.10)
22. Open the same draft in two tabs, save in the first, then save in the second. Expected: «Нагороду змінено в іншому вікні», reload offered, the typed values kept in the local copy; Swagger `PUT` with the old `version` → 409 `award-stale`. (AC-1.3)
23. Submit a draft, press Back to the edit page and «Подати» again. Expected: 409 shown inline, the page switches to the read-only view; psql: one `award_requests` row for the award. Double-click «Подати» on a fresh draft → one request. (AC-1.6)
24. Restart the backend while the form is open and submit. Expected: «Сервер недоступний», form values kept; submit again after the health check is green → success, one request. (AC-1.5, 1.10)
25. Let the access token expire on the form (15 minutes) and save. Expected: silent refresh, saved; with the employee's role revoked meanwhile (as the dean) → login page, and after sign-in as the same user the local copy is offered only if the account still may create awards. (AC-1.2, 1.10)
26. Open `http://localhost:4200/awards/999999` and `/awards/abc` → «Не знайдено», no request for `abc`; open another person's award id → «Не знайдено». (AC-1.8)
27. As the administrator deactivate the chosen category in psql (`update award_categories set is_active = false where category_id = <id>;`, restart), then submit a draft that uses it. Expected: 422 on the category («Категорія більше не доступна»); the draft still shows the category name. (§5)
28. Between 00:00 and 03:00 Kyiv time (the hours when the UTC date is still yesterday), save a draft dated today. Expected: accepted, no future-date error. The same boundary is proven at any hour by `AwardDateRulesTest` with a fixed Kyiv clock and by the database check test in `AwardSchemaIT`. (AC-2.1, §5)

## 10. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Level order, approval minimums and base scores (D-3) are not confirmed by the university | Wrong routing and scores once Epic 4 uses them | One enum and one dictionary table; read with the user at `/thesis-sync` |
| US-003 "under 5 minutes" is judged on manual entry only | The persona test is weaker without the photo | Stated in the tracker (risk 2); the form is timed in §9 step 6 on a phone viewport |
| Suggestion quality on real titles is lower than on the fixture | Users ignore the chips | Suggestions never overwrite a choice; the fixture grows with real titles from the pilot |
| `trg_awards_audit` rows carry no actor until Feature 2.2 | Audit trail incomplete for Epic 2 changes | `AWARD_SUBMITTED` rows carry the actor; the actor for trigger rows is part of the 2.2 design note |
| Browser copy on a shared computer | Another person sees an unsaved award | Keyed by user, removed on sign-out; only unsent form fields |

## 11. Definition of Done

- `./mvnw verify` green (unit, slice, IT, FT), JaCoCo ≥ 85 % lines, Checkstyle/PMD/SpotBugs clean
- `npm run lint`, `npm run test:ci`, Playwright scenarios for AC-1.9, 1.10, 2.6, 3.4, including a 360 px viewport
- Docs in the same PRs: `openapi.yml`, DATA_DICTIONARY §2.1–§2.2 and appendix B, RBAC_matrix.md, `CHANGELOG.md`, tracker rows and deviations 1–2 closed, `BACKLOG.md`
- §9 walked through by the author after `/feature-validate`, including the detours

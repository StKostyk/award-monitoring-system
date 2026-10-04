# Epic 2: Award Lifecycle Management — Status

> **Started**: 2026-09-28
> **Done**: 2026-10-01
> **Author**: Stefan Kostyk
> **Jira epic**: SCRUM-20
> **Roadmap**: [DEVELOPMENT_ROADMAP.md § Epic 2](../../DEVELOPMENT_ROADMAP.md#epic-2-award-lifecycle-management)

## Progress

| Feature | Status | Started | Done |
|---------|--------|---------|------|
| 2.1 Award Creation & Validation | Done (validated, PRD §12; fixes 2.1.4 merged; author's run of §9 pending) ([feature-2.1](../features/epic-02/feature-2.1-award-creation-validation.md)) | 2026-09-28 | 2026-09-29 |
| 2.2 Award Version History & Audit Trail | Done (validated, PRD §12; fixes 2.2.3 merged; author's run of §9 pending) ([feature-2.2](../features/epic-02/feature-2.2-award-version-history.md)) | 2026-09-30 | 2026-09-30 |
| 2.3 Award Status Tracking | Done (validated, PRD §12; fixes 2.3.2 merged; author's run of §9 pending) ([feature-2.3](../features/epic-02/feature-2.3-award-status-tracking.md)) | 2026-10-01 | 2026-10-01 |
| 2.4 Award Modification & Archival | Moved: 2.4.1 to Epic 4, 2.4.2 to Epic 6 (see decisions) | | |

## Current focus

Feature 2.1 done 2026-09-29: 2.1.0–2.1.4 (SCRUM-21–24, 28) merged, validated (PRD §12, passed with notes F-1…F-11, all fixed), refactor sweep merged (#87); the author's run of PRD §9 is pending. Epic 1 stories 1.3.1, 1.3.3 and 1.3.4 done 2026-09-30. Feature 2.2 PRD approved 2026-09-30; 2.2.1 (SCRUM-25) done 2026-09-30 (#95); 2.2.2 (SCRUM-26) done 2026-09-30 (#96); validated 2026-09-30 (PRD §12, passed with notes F-1…F-4), fixes and refactor sweep in 2.2.3 (SCRUM-30), merged 2026-10-01 (#98). Feature 2.3 PRD approved 2026-10-01; 2.3.1 (SCRUM-27) done 2026-10-01 (#100); validated 2026-10-01 (PRD §12, passed with notes F-1…F-6), fixes and refactor sweep in 2.3.2 (SCRUM-31), merged 2026-10-01 (#102). Epic documentation sync 2026-10-01: docs reconciled with the code; three unused database objects disagree with the award model and are fixed in 2.1.5 (SCRUM-32), merged 2026-10-01 (#106). Epic 2 done 2026-10-01; the author's runs of PRD §9 for Features 2.1–2.3 are pending.

## Scope

Epic 2 takes an award from a draft to a submitted request: capture, validation, categorisation, version history and status tracking. Approval decisions belong to Epic 4, document upload to Epic 3, notifications to Epic 7, offline PWA to Epic 8. Until Epic 4 ships, an award can reach `DRAFT` and `PENDING` (request `SUBMITTED`); the status view shows reviewer decisions as soon as they exist.

US-004 (batch review, 13 points) is counted in the roadmap under both Epic 2 and Epic 4; it is implemented once, in Epic 4 (Story 4.1.1).

Out of scope here, delivered later: certificate photo and upload (Epic 3), metadata auto-population from the certificate (Epic 3 OCR), offline submission (Epic 8), push and email notifications on status change (Epic 7), public award pages (after Epic 4, when approved awards exist).

## Stories

`parallel` marks stories whose UI can be built in a separate lane from the OpenAPI contract while the backend is in progress.

| # | Story | Feature | Pts | Jira | GitHub | Parallel | Status |
|---|-------|---------|-----|------|--------|----------|--------|
| 1 | 2.1.0 Award domain model and category catalogue | 2.1 | 3 | SCRUM-21 | #43 | no | Done |
| 2 | 2.1.1 Award draft and submission (US-003) | 2.1 | 8 | SCRUM-22 | #44 | yes | Done |
| 3 | 2.1.2 Award date and duplicate validation | 2.1 | 3 | SCRUM-23 | #76 | no | Done |
| 4 | 2.1.3 Award category suggestion | 2.1 | 3 | SCRUM-24 | #77 | no | Done |
| 5 | 2.1.4 Fixes from the Feature 2.1 validation | 2.1 | 5 | SCRUM-28 | #85 | no | Done |
| 6 | 2.2.1 Award version recording | 2.2 | 5 | SCRUM-25 | #78 | no | Done |
| 7 | 2.2.2 Version history view and audit export | 2.2 | 5 | SCRUM-26 | #79 | yes | Done |
| 8 | 2.2.3 Fixes from the Feature 2.2 validation | 2.2 | 3 | SCRUM-30 | #97 | no | Done |
| 9 | 2.3.1 Award status tracking (US-005) | 2.3 | 5 | SCRUM-27 | #80 | no | Done |
| 10 | 2.3.2 Fixes from the Feature 2.3 validation | 2.3 | 2 | SCRUM-31 | #101 | no | Done |
| 11 | 2.1.5 Database views and functions follow the award model | 2.1 | 2 | SCRUM-32 | #103 | no | Done |
| 12 | 2.1.6 Fixes from the author's manual run of Feature 2.1 | 2.1 | 2 | SCRUM-38 | #117 | no | In Review |

Total: 46 points, sprints 3–4. Story order approved 2026-09-28. Feature 2.4 stories (2.4.1 correction by reviewers, 2.4.2 GDPR-compliant deletion, 5 points each) are tracked with Epics 4 and 6.

## Decisions

| Date | Decision | Rationale | Reference |
|------|----------|-----------|-----------|
| 2026-09-28 | Existing tables `awards`, `award_categories`, `award_requests`, `review_decisions`, `documents` (V004–V008) are the base; changes land in new migrations from V020 | Versioned migrations are immutable once merged | MIGRATION_STRATEGY |
| 2026-09-28 | Status changes are shown by polling the status endpoint, not WebSocket; notifications follow in Epic 7 | US-005 DoD allows WebSocket or polling; the broker decision is deferred to Epic 7 | ADR-006, US-005 |
| 2026-09-28 | US-004 batch review is implemented in Epic 4 only | Counted twice in the roadmap; review decisions are workflow-engine scope | Roadmap § Epic 4 |
| 2026-09-28 | Awards may be submitted by holders of `award:create` (employees and approvers: secretaries, deans, rector's office, rector); oversight roles do not submit; nobody reviews their own award (Epic 4 rule). `RBAC_matrix.md` is aligned in 2.1.1 | Approvers receive awards too; follows `RolePermissions` and AUTH §3.3 | AUTH §3.3, Epic 1 deviation 7 |
| 2026-09-28 | The nine recognition levels of V004 are the schema of record; the dictionary gains `SPECIALITY`, `COLLEGE`, `LOCAL`, `REGIONAL` with their minimum approval level and impact base score (2.1.0) | Migrations are the schema of record, as in Epic 1 | DATA_DICTIONARY §2.2 |
| 2026-09-28 | Version-history storage (Envers, `award_versions` or the existing `audit_logs` trigger) is settled in a design note before Feature 2.2 | Three candidate mechanisms with different GDPR-erasure consequences | Roadmap § 2.2 |
| 2026-09-28 | Feature 2.4 moves out of Epic 2: 2.4.1 corrections to the end of Epic 4, 2.4.2 GDPR deletion to Epic 6; deletion of one's own draft ships in 2.1.1 | Corrections and archival need approved awards; erasure of submitted awards is compliance scope | Roadmap § 2.4 |
| 2026-09-28 | Minimum approval level and impact base score per recognition level: `SPECIALITY`, `DEPARTMENT`, `LOCAL`, `UNIVERSITY`, `REGIONAL` → faculty secretary; `COLLEGE`, `FACULTY` → dean; `NATIONAL`, `INTERNATIONAL` → rector's secretary; scores 10/20/45/60/70, 30/40, 80/100. No level needs the rector as the minimum; the rector approves any level. A minimum admits every role above it in the line | Local and regional recognition is confirmed inside the faculty; the score still ranks by reach | Feature 2.1 PRD D-3 |
| 2026-09-28 | Feature 2.1 schema changes: `awards.organization_id`, draft columns nullable outside `DRAFT`, Ukrainian-only title allowed, `ck_awards_date` on the Kyiv calendar (V020); category seed as upsert; `award_categories.keywords` (V021) | Scoped reads and routing must not follow a person who moves; US-003 "complete later"; the V005 check refuses valid awards after midnight Kyiv time; the truncating seed would delete awards | Feature 2.1 PRD §7 |
| 2026-09-28 | Epic 1 stories 1.3.1 and 1.3.3 follow Feature 2.1; 1.3.2 notification preferences moves to Epic 7 | The data export is only meaningful once awards exist; preferences belong with the notification channels | Epic 1 tracker |
| 2026-09-29 | Drafts can be deleted from the form and the detail page («Видалити чернетку»); the award list says «Показано перші 100 з N» instead of paging | Deletion existed only in the API; one page of 100 covers a person's awards, the note makes the limit visible | Feature 2.1 validation, SCRUM-28 |
| 2026-09-29 | Local form copies survive an expired session for the same user and are removed on sign-out and when another user signs in on the browser | AC-1.10 offers the copy back after an expired session; the shared-computer risk is closed at the next sign-in | Feature 2.1 PRD §10, SCRUM-28 |
| 2026-09-29 | The unused GIN index on `award_categories.keywords` (V021) is kept for now | Matching runs in Java; the index costs little on a small reference table and serves a later SQL search | Feature 2.1 validation |
| 2026-09-30 | Award versions are full snapshots in `award_versions`, written by the application in the transaction of the change; no Envers; trigger rows get the actor and correlation id per transaction; the per-award audit trail and CSV export are for `audit:read`, the system-wide search is Epic 6 | Business actions (submitted, later returned) need names Envers lacks; the history follows the award for erasure while `audit_logs` stays the immutable record | Feature 2.2 PRD D-1–D-6 |
| 2026-09-30 | History and audit lists offer «Спробувати ще раз» only when a retry can help; a 404 or 403 on a later page says the award or the access is gone, and an export reloads the audit list from its first page | A retry of a refused page loops; the export's own `AUDIT_EXPORT` row shifted the next page | Feature 2.2 validation, SCRUM-30 |
| 2026-10-01 | Status tracking: `deadline` per level (3 calendar days, configurable), estimate computed over the remaining levels of the path, late requests explained (`REVIEW_OVERDUE`, `NO_REVIEWER`) but not expired; the award page polls every 60 s; WebSocket and push move to Epic 7 | Research targets of under 7 and 14 days; expiry and escalation are workflow-engine scope | Feature 2.3 PRD D-1–D-7 |

## Documentation deviations to resolve

Each item is applied in the PR of the story that touches it, after approval.

1. `openapi.yml` award schemas: UUID ids (the schema uses `BIGSERIAL`), `AwardStatus` enum (`SUBMITTED`, `UNDER_REVIEW`, `RETURNED`, `PUBLISHED`) differs from `awards.status`, `ApprovalLevel` enum differs from `award_requests.current_level`, `issuingOrganization` vs `awarding_organization`, title limit 255 vs `VARCHAR(500)`, no Ukrainian title or description, `AwardCategory.level` has four values. Aligned in 2.1.0 (resolved).
2. `GET /awards` filter `category` is a UUID; there is no category endpoint. Added in 2.1.0 (resolved).
3. Roadmap task "Flyway migrations V004–V008" is already done; new migrations start at V020.
4. Roadmap § 2.1 references BRD §4.2.1 and §4.2.3, which do not exist (BRD §4.2 only points to the user stories); validation rules come from BRD §7.3 and the dictionary appendix B.
5. The award state machine diagram mixes award and request states (`SUBMITTED`, `IN_REVIEW` belong to `award_requests`); the PRD maps them explicitly.
6. DATA_DICTIONARY §2.1 requires `title`, `category_id`, `awarding_organization`, `award_date` on every award and an English title; drafts and Ukrainian-only titles need them optional outside `DRAFT`. Applied in 2.1.1 (V020, resolved).
7. `R__seed_award_categories.sql` truncates `award_categories` with `CASCADE`, which would delete all awards on the next seed change. Rewritten as upsert in 2.1.0 (resolved).

## Technical notes

- Package `ua.edu.chnu.awards.award` (entities, service, controller); categories are reference data seeded by `R__seed_award_categories.sql`.
- Owner access uses `award:read:own` and `award:update:own`; scoped reads (`award:read:department|faculty|all`) reuse the organisation-subtree check of Feature 1.2.
- `awards.version` is the optimistic-lock column; a stale update answers 409 like the role assignments.
- `ck_awards_date` compares with `CURRENT_DATE` in the database session zone (UTC), so between 00:00 and 03:00 Kyiv time it refuses an award dated today that the application accepts; V020 recreates it as `award_date <= (now() AT TIME ZONE 'Europe/Kyiv')::date`.
- Trigger rows carry the actor and correlation id since 2.2.1: `AuditingTransactionManager` binds `app.current_user_id`/`app.correlation_id` for read-write transactions of token-authenticated requests (Feature 2.2 PRD D-3). Sign-in, registration and password reset run without a token and stay without an actor.
- Known limits of the category suggestion (Feature 2.1 validation): a unit of another university with the same name as a ChNU unit ("Faculty of Law, …") matches the ChNU unit; the keywords, the organisation tree and the caller's history are read on every request, without a cache. Revisit if the pilot shows wrong chips or slow answers.
- Feature 2.2 refactor sweep, left for later: organisation names of units deactivated since a version show as `#id` (the versions answer carries ids only and `GET /organizations` lists active units; resolving names on the server changes the API, decide in a design note); `AwardHistory` reads each version's actor lazily (check the query count on a 50-row page before batching like `AuditTrailService`); `e2e/award-history.spec.ts` drives the owner, the dean and the auditor in one test.
- The audit trail of a deleted draft, of another person's draft or of an award outside the auditor's read scope is reachable through `GET /awards/{id}/audit-trail` only: the award page answers «Не знайдено» before the tab exists. The system-wide audit search is Epic 6.
- Feature 2.3 refactor sweep, left for later: the expected date and «Затримка» chip are written in the award list and the home card, and three components wrap `kyivDate` in their own `date()` (a shared timing component and a date pipe when the Epic 4 review queue needs the chip); `AwardStatusFT` checks the 95th-percentile latency by wall clock, the query count of `AwardStatusIT` is the stable guard; `award-status.component` reads the award id once, so a link from one award page to another needs an input change handler. An `ESCALATED` decision counts as passing its level in the path (`AwardStatusService.passedAt`); check against the Epic 4 escalation rules.
- Epic 2 refactor sweep (2026-10-01), applied: one readable-or-404 lookup (`AwardOwnership.readable`), shared test fixtures (`TestAwards`, `AwardApi`; two functional tests had sent the misspelt `duplicateAcknowledged`, which Jackson ignored), the edit route guarded by `award:update:own`, one `isOwnAward` check, `app-request-timing` for the list and the home card, Kyiv calendar helpers in `shared/date-format.ts` with "today" read at every check of the form, server failures on the confirmation page no longer shown as «Не знайдено», shared E2E helpers on the Kyiv calendar. Left for a decision: reject unknown JSON properties (`spring.jackson.deserialization.fail-on-unknown-properties`, an API behaviour change); answer an empty audit trail with 200 and an empty page instead of 404 (the UI undoes it with `notFoundIsEmpty`; changes `openapi.yml`); `editable`/`own` flags on `AwardResponse` instead of client-side checks. Kept on purpose: `awards.levels.*` (lower case, inside a sentence) next to `roles.*` (capitalised, standalone).
- `award-form.component.ts` holds about 560 lines (form, date rules, suggestions, duplicate dialog, local copies). Split the suggestion chips and the duplicate handling into child components with the Epic 4 review view (Feature 2.2 puts the history on the detail page, not the form).

## Risks

1. The demo of Epic 2 ends at "submitted": the reviewer side appears in Epic 4, so the deployment decision after Epic 2 is based on the submitter flow only.
2. US-003 "under 5 minutes on a phone" depends on the certificate photo and OCR of Epic 3; Epic 2 meets it for manual entry only.
3. Category suggestion without training data is rule-based (keywords and the user's history); an NLP model is a year-2 item in BRD §3.3.

## Quick links

- [User stories US-003, US-005](../requirements/USER_STORIES.md#epic-2-award-lifecycle-management)
- [Data dictionary § Award domain](../database/DATA_DICTIONARY.md#2-award-domain)
- [Award request state machine](../diagrams/uml/state-machine-award-request.puml)
- [Award lifecycle activity diagram](../diagrams/uml/activity-award-lifecycle.puml)
- [Award submission sequence](../diagrams/uml/sequence-award-submission.puml)
- [OpenAPI](../api/openapi.yml)
- [Authentication & Authorization design § 3.3](../security/AUTHENTICATION_AUTHORIZATION.md)
- [RBAC matrix](../stakeholders/RBAC_matrix.md)

# Epic 2: Award Lifecycle Management — Status

> **Started**: 2026-09-28
> **Author**: Stefan Kostyk
> **Jira epic**: SCRUM-20
> **Roadmap**: [DEVELOPMENT_ROADMAP.md § Epic 2](../../DEVELOPMENT_ROADMAP.md#epic-2-award-lifecycle-management)

## Progress

| Feature | Status | Started | Done |
|---------|--------|---------|------|
| 2.1 Award Creation & Validation | PRD approved ([feature-2.1](../features/epic-02/feature-2.1-award-creation-validation.md)) | 2026-09-28 | |
| 2.2 Award Version History & Audit Trail | Planned | | |
| 2.3 Award Status Tracking | Planned | | |
| 2.4 Award Modification & Archival | Moved: 2.4.1 to Epic 4, 2.4.2 to Epic 6 (see decisions) | | |

## Current focus

Feature 2.1 PRD approved 2026-09-28; next story 2.1.0 (SCRUM-21). After Feature 2.1: Epic 1 stories 1.3.1 and 1.3.3.

## Scope

Epic 2 takes an award from a draft to a submitted request: capture, validation, categorisation, version history and status tracking. Approval decisions belong to Epic 4, document upload to Epic 3, notifications to Epic 7, offline PWA to Epic 8. Until Epic 4 ships, an award can reach `DRAFT` and `PENDING` (request `SUBMITTED`); the status view shows reviewer decisions as soon as they exist.

US-004 (batch review, 13 points) is counted in the roadmap under both Epic 2 and Epic 4; it is implemented once, in Epic 4 (Story 4.1.1).

Out of scope here, delivered later: certificate photo and upload (Epic 3), metadata auto-population from the certificate (Epic 3 OCR), offline submission (Epic 8), push and email notifications on status change (Epic 7), public award pages (after Epic 4, when approved awards exist).

## Stories

`parallel` marks stories whose UI can be built in a separate lane from the OpenAPI contract while the backend is in progress.

| # | Story | Feature | Pts | Jira | GitHub | Parallel | Status |
|---|-------|---------|-----|------|--------|----------|--------|
| 1 | 2.1.0 Award domain model and category catalogue | 2.1 | 3 | SCRUM-21 | #43 | no | Ready |
| 2 | 2.1.1 Award draft and submission (US-003) | 2.1 | 8 | SCRUM-22 | #44 | yes | Ready |
| 3 | 2.1.2 Award date and duplicate validation | 2.1 | 3 | SCRUM-23 | #76 | no | Ready |
| 4 | 2.1.3 Award category suggestion | 2.1 | 3 | SCRUM-24 | #77 | no | Ready |
| 5 | 2.2.1 Award version recording | 2.2 | 5 | SCRUM-25 | #78 | no | Ready |
| 6 | 2.2.2 Version history view and audit export | 2.2 | 5 | SCRUM-26 | #79 | yes | Ready |
| 7 | 2.3.1 Award status tracking (US-005) | 2.3 | 5 | SCRUM-27 | #80 | yes | Ready |

Total: 32 points, sprints 3–4. Story order approved 2026-09-28. Feature 2.4 stories (2.4.1 correction by reviewers, 2.4.2 GDPR-compliant deletion, 5 points each) are tracked with Epics 4 and 6.

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
| 2026-09-28 | Minimum approval level and impact base score per recognition level: `SPECIALITY`, `DEPARTMENT`, `LOCAL`, `REGIONAL` → faculty secretary; `COLLEGE`, `FACULTY` → dean; `NATIONAL`, `INTERNATIONAL` → rector's secretary; `UNIVERSITY` → rector; scores 10/20/45/70, 30/40, 80/100, 60. A minimum admits every role above it in the line | Local and regional recognition is confirmed inside the faculty; the score still ranks by reach | Feature 2.1 PRD D-3 |
| 2026-09-28 | Feature 2.1 schema changes: `awards.organization_id`, draft columns nullable outside `DRAFT`, Ukrainian-only title allowed, `ck_awards_date` on the Kyiv calendar (V020); category seed as upsert; `award_categories.keywords` (V021) | Scoped reads and routing must not follow a person who moves; US-003 "complete later"; the V005 check refuses valid awards after midnight Kyiv time; the truncating seed would delete awards | Feature 2.1 PRD §7 |
| 2026-09-28 | Epic 1 stories 1.3.1 and 1.3.3 follow Feature 2.1; 1.3.2 notification preferences moves to Epic 7 | The data export is only meaningful once awards exist; preferences belong with the notification channels | Epic 1 tracker |

## Documentation deviations to resolve

Each item is applied in the PR of the story that touches it, after approval.

1. `openapi.yml` award schemas: UUID ids (the schema uses `BIGSERIAL`), `AwardStatus` enum (`SUBMITTED`, `UNDER_REVIEW`, `RETURNED`, `PUBLISHED`) differs from `awards.status`, `ApprovalLevel` enum differs from `award_requests.current_level`, `issuingOrganization` vs `awarding_organization`, title limit 255 vs `VARCHAR(500)`, no Ukrainian title or description, `AwardCategory.level` has four values. Aligned in 2.1.0.
2. `GET /awards` filter `category` is a UUID; there is no category endpoint. Added in 2.1.0.
3. Roadmap task "Flyway migrations V004–V008" is already done; new migrations start at V020.
4. Roadmap § 2.1 references BRD §4.2.1 and §4.2.3, which do not exist (BRD §4.2 only points to the user stories); validation rules come from BRD §7.3 and the dictionary appendix B.
5. The award state machine diagram mixes award and request states (`SUBMITTED`, `IN_REVIEW` belong to `award_requests`); the PRD maps them explicitly.
6. DATA_DICTIONARY §2.1 requires `title`, `category_id`, `awarding_organization`, `award_date` on every award and an English title; drafts and Ukrainian-only titles need them optional outside `DRAFT`. Applied in 2.1.1 (V020).
7. `R__seed_award_categories.sql` truncates `award_categories` with `CASCADE`, which would delete all awards on the next seed change. Rewritten as upsert in 2.1.0.

## Technical notes

- Package `ua.edu.chnu.awards.award` (entities, service, controller); categories are reference data seeded by `R__seed_award_categories.sql`.
- Owner access uses `award:read:own` and `award:update:own`; scoped reads (`award:read:department|faculty|all`) reuse the organisation-subtree check of Feature 1.2.
- `awards.version` is the optimistic-lock column; a stale update answers 409 like the role assignments.
- `ck_awards_date` compares with `CURRENT_DATE` in the database session zone (UTC), so between 00:00 and 03:00 Kyiv time it refuses an award dated today that the application accepts; V020 recreates it as `award_date <= (now() AT TIME ZONE 'Europe/Kyiv')::date`.
- `trg_awards_audit` reads `app.current_user_id`, which the application never sets: trigger rows carry no actor. Part of the 2.2 design note.
- `trg_awards_audit` already records every change in `audit_logs` with `app.current_user_id` as the actor.

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

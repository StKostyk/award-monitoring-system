# Feature 4.2: Review Period, Escalation Notice and Reviewer Corrections

> **Epic**: 4 — Approval Workflow Engine (SCRUM-47)
> **Sprint**: 5 (2026-10-12 → 2026-10-18)
> **Points**: 15 (four stories)
> **Status**: Done 2026-10-08, pending the manual run of §10 (validation §13)
> **Author**: Stefan Kostyk
> **Governing docs**: roadmap § Feature 4.2 (story 4.2.1 "Automatic Escalation") and § Feature 2.4 (story 2.4.1 "Award Error Correction"), US-004, US-005 (delay explanation), DATA_DICTIONARY §1.3, §2.4, §3.1, §4.1, §5.1, V024, V028, V023/V029, `StatusEstimator`, `WorkflowProperties`, `ReviewerAvailability`, `ReviewerRule`, `DecisionMails`, MONITORING_OBSERVABILITY §5, RBAC_matrix.md, ADR-006 addendum, ADR-022, ADR-023, openapi.yml `/reviews`, `/awards/{id}/status`, `/awards/{id}/versions`, EPIC-04 tracker (decisions of 2026-10-05, deviation 1), Feature 4.1 PRD §12 (F-4, F-7)

## 1. Problem and personas

Every level has the same three working days, whatever the faculty's volume. A request past its deadline is shown as overdue to its owner and in the queue, but nobody above the late level hears of it, and nobody measures how long the levels take. When a reviewer finds a typo, a wrong date or a wrong category, the only tool is a return: the owner edits the draft, submits again and the request waits a second time for a fix the reviewer could have made in a minute. Feature 4.1 validation also left the decision dialog closing before the answer arrives, so a failed decision loses its comment (F-4, F-7).

| Persona | Need in this feature |
|---------|----------------------|
| Prof. Martynyuk, dean | Set his faculty's review period; hear about requests his secretaries left past their deadline |
| Alina, faculty secretary | Know her faculty's period; correct an obvious error herself with a reason instead of returning the award; keep a typed comment when a decision fails |
| Rector's secretary | Hear about faculty-approved national awards a dean left overdue |
| Anastasia, employee | See that the delay was reported upwards; be told what a reviewer changed in her award and why |
| Dmytro, GDPR officer | Every period change and correction is recorded with who, when, what and on whose behalf |
| Administrator | SLA figures per level (open, overdue, decided on time) in the existing metrics endpoint |

## 2. Scope

**In (this feature)**

- Per-faculty review period: `organizations.review_working_days` (V032), read and set by the dean, read by the faculty's reviewers; applies to the faculty secretary and dean levels of requests in that faculty
- Overdue detection: a scheduled job marks each request once per level when its deadline passes (V033) and e-mails the eligible reviewers of the next level with a digest
- The mark in the queue, on the review panel and on the owner's status page; a queue filter for marked requests (the existing `overdue` filter stays)
- SLA metrics through Micrometer: open and overdue requests per level, decisions per level with an on-time tag, decision duration, notices sent
- Reviewer corrections of a pending award: the reviewer holding (or taking) the request changes form fields with a mandatory reason; a `CORRECTED` award version with the reason (V034), an audit row, an e-mail to the owner
- Decision dialogs (panel and batch) stay open until the answer and keep the comment on failure; distinct messages per conflict (F-4, F-7)
- OpenAPI contract, data dictionary, RBAC rows, state-machine note, BPMN overdue branch, reviewer guide section

**Out (where it goes)**

- Moving, deciding or expiring a request automatically — never (tracker decision 2026-10-05, deviation 1)
- A reminder before the deadline (`DEADLINE_REMINDER`), in-app and push notices, the owner's choice of e-mail language (F-3) — Epic 7 (notification preferences)
- Corrections of approved or rejected awards — parked for the design review (§J); approved awards are corrected by a return path that does not exist yet
- Corrections of the recipient unit and of documents — the owner's choice; a return remains the tool
- Review periods for the rector's levels other than the global default, per-department periods — not requested
- Grafana dashboards and alert rules on the SLA metrics — Epic 9 (production hardening)
- ShedLock for the job — Epic 9; the job's SQL is safe on two instances anyway (D-5)
- Public holidays in the period — none under martial law (tracker 2026-10-05)

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-53 (#147) | 4.2.1 Per-faculty review period set by the dean | 3 | no | PR #161 merged |
| SCRUM-54 (#148) | 4.2.2 Overdue detection, escalation notice and SLA metrics | 5 | no | 4.2.1 (period per level) |
| SCRUM-55 (#149) | 2.4.1 Award correction by reviewers | 5 | no | 4.1.2 (claim on action) |
| SCRUM-59 (#162) | 4.2.3 Keep the decision dialog open until the answer (F-4, F-7) | 2 | no | — |

2.4.1 and 4.2.3 are independent of 4.2.1/4.2.2; they share the review panel, so the order above avoids merge conflicts in `review-panel` and `decision-dialog`.

## 4. Acceptance criteria

The **reviewer rule**, **own level** and **eligible** keep the meaning of the Feature 4.1 PRD §4. **Faculty of a request**: the award's `organization_id` if it is a faculty, else its parent faculty. **Faculty levels**: `FACULTY_SECRETARY`, `DEAN`; **rector levels**: `RECTOR_SECRETARY`, `RECTOR`.

### 4.2.1 Per-faculty review period (SCRUM-53)

- **AC-1.1** Given a faculty with no period of its own, when its dean opens `/reviews`, then the header shows «Термін розгляду: 3 робочі дні (типовий)» and «Змінити»; `GET /api/v1/organizations/{facultyId}/review-period` answers `workingDays: null`, `effectiveWorkingDays: 3`, `defaultWorkingDays: 3`.
- **AC-1.2** Given the dean (own role or a delegation in effect), when he sets 5 in the dialog, then `PUT …/review-period` with `workingDays: 5` answers 200 with `effectiveWorkingDays: 5`, an audit row `REVIEW_PERIOD_CHANGED` (`entity_type = 'organizations'`, old and new value, `delegatorId` under a delegation) is written, and the header reads «5 робочих днів».
- **AC-1.3** Given a period set, when the dean chooses «Типовий термін», then `workingDays: null` is stored and the global default applies again.
- **AC-1.4** Given `workingDays` outside 1–20 or not an integer, then 422 `validation-failed` on `workingDays` (`range`).
- **AC-1.5** Given a faculty secretary or a dean of another faculty, when they `PUT`, then 404 (outside the caller's dean scope, J11); a faculty secretary of the faculty may `GET` (200) and sees the period without «Змінити»; an employee gets 403 on both; a department or university id answers 404.
- **AC-1.6** Given a faculty period of 5 days, when a request of that faculty (any of its departments or a unit award of the faculty) is submitted, resubmitted, or moved to `DEAN` by a decision, then its `deadline` is 5 working days ahead; when it moves to `RECTOR_SECRETARY` or `RECTOR` the global default applies.
- **AC-1.7** Given a change of the period, then deadlines already set stay as they are; the new period applies from the next deadline set (submission, level change, resubmission).
- **AC-1.8** Given the period of 5 days, when the owner opens the status page, then the expected completion counts 5 working days for each faculty level still ahead and the global default for each rector level ahead.
- **AC-1.9** The dialog and header work at 360 px, in English and by keyboard; axe reports no violations.

### 4.2.2 Overdue detection, escalation notice and SLA metrics (SCRUM-54)

- **AC-2.1** Given an open request (`SUBMITTED`, `IN_REVIEW`, `ESCALATED`) whose `deadline` has passed and `overdue_noticed_at` is null, when the job runs (`app.workflow.overdue-cron`, default every hour at minute 5, Kyiv time), then `overdue_noticed_at` is set to the run time and `overdue_noticed_level` to the request's level in one statement, and an audit row `REVIEW_OVERDUE_NOTICED` (level, deadline, recipients) is written; the request's status, level and reviewer do not change.
- **AC-2.2** Given marked requests, then after the commit each eligible reviewer of the **notice level** (the first level above the request's level with an eligible reviewer for the request's organisation, the owner and submitter excluded) receives one e-mail per run listing his requests: title, owner, level, deadline, current reviewer or «не взято», a link to `/awards/{id}`; a request at `RECTOR` level, or with no eligible reviewer above, is marked without an e-mail.
- **AC-2.3** Given a request already marked at its level, when the job runs again, then no second mark and no second e-mail; when the request later moves to another level, is resubmitted or withdrawn, then `overdue_noticed_at` and `overdue_noticed_level` are cleared together with the new deadline, so the next level can be noticed once.
- **AC-2.4** Given a marked request, then `GET /api/v1/reviews` items carry `overdueNoticedAt`; the queue shows «Прострочено» and «Керівника повідомлено <дата>»; `GET /api/v1/reviews?noticed=true` lists only marked requests; the dean's queue filter «Прострочені нижчого рівня» (`level=FACULTY_SECRETARY&noticed=true`) lists the faculty secretaries' marked requests he may take over.
- **AC-2.5** Given a marked request, when the owner opens the status page, then the delay explanation `REVIEW_OVERDUE` carries `noticedAt` and the page reads «Термін розгляду минув <дата>; керівника повідомлено <дата>».
- **AC-2.6** Given decisions, then the meter `awards.review.decisions` (counter, tags `level`, `decision`, `on_time` = decided before the deadline) and `awards.review.decision.duration` (timer, tag `level`: from the time the request reached the level to the decision) are recorded after the commit; `awards.review.open` and `awards.review.overdue` (gauges, tag `level`) are refreshed by the job; `awards.review.overdue.notices` counts e-mails sent. All appear at `/actuator/prometheus` with the access it has today.
- **AC-2.7** Given the mail server down, when the job runs, then the marks are committed and the failure is logged per e-mail (Epic 1 listener behaviour); a lost notice is not retried (A3).
- **AC-2.8** Given two backend instances running the job at the same minute, then each request is marked and noticed once (the update claims rows under a row lock; D-5).
- **AC-2.9** E-mail texts in Ukrainian and English in one message (as the decision e-mails, F-3), subject «Прострочені заявки: N / Overdue requests: N».

### 2.4.1 Award correction by reviewers (SCRUM-55)

- **AC-3.1** Given a pending award the caller may review (reviewer rule) and the request unclaimed or held by him, when he opens the award, then the review panel offers «Виправити»; given the request held by someone else, «Виправити» is not offered.
- **AC-3.2** Given «Виправити», then `/awards/{id}/correct` opens the award form with the current values, the recipient and documents read-only, and a required field «Причина виправлення» (1–1000 characters); «Зберегти» is enabled only when at least one field differs, and a confirmation lists the changed fields with old → new values before sending.
- **AC-3.3** Given a valid correction, when `POST /api/v1/awards/{id}/corrections` is sent with the changed fields, `version`, `requestVersion` and `reason`, then in one transaction: the award is updated (validation of the award form, submission rules for a pending award: every required field present), `awards.version` moves, `impact_score` follows a changed category, an award version `CORRECTED` with `actor_id`, `changed_fields` and `comment = reason` is written, an unclaimed request is claimed by the caller (D-3 of 4.1), `REVIEW_CLAIMED` (if claimed now) and `AWARD_CORRECTED` (changed fields with old and new values, reason, `delegatorId` under a delegation) audit rows are written; 200 returns the `Award` and the new `requestVersion`.
- **AC-3.4** Given a correction, then the request keeps its status, level and deadline; a changed category changes the minimum approval level from the next decision on (an approval below the new minimum escalates as in 4.1).
- **AC-3.5** Given a correction, then after the commit the owner receives an e-mail listing the changed fields with old and new values, the reason and the reviewer's name (uk + en).
- **AC-3.6** Given the owner's award page, then «Історія змін» shows the `CORRECTED` version as «Виправлено рецензентом: <name>», the changed fields and the reason; scoped readers see it too (versions from the first submission, as 2.2).
- **AC-3.7** Errors: no field changed → 422 `no-change`; reason missing or longer than 1000 → 422 on `reason`; a field invalid → 422 on that field; stale `version` → 409 `award-stale`; stale `requestVersion` → 409 `request-stale`; held by another reviewer → 409 `request-claimed` with `reviewer`; draft, returned, withdrawn, approved, rejected, own or out-of-scope award → 404.
- **AC-3.8** The correction form works at 360 px, in English and by keyboard; axe reports no violations.

### 4.2.3 Keep the decision dialog open until the answer (SCRUM-59)

- **AC-4.1** Given the decision dialog (panel) with a typed comment, when «Підтвердити» is pressed, then the dialog stays open with a progress indicator and disabled buttons until the answer; on success it closes as today.
- **AC-4.2** Given a failure, then the dialog stays open with the comment kept and a message per cause: `request-claimed` «Нагороду вже взяв у роботу <name>», `request-stale` «Дані змінилися — сторінку оновлено, перевірте та підтвердіть ще раз» (the page data reload behind the dialog, the new `requestVersion` is used on the next confirm), `request-closed` «Рішення вже ухвалено» (only «Закрити»), 404 «Розгляд цієї нагороди вам більше не доступний» (only «Закрити»), network or 5xx «Не вдалося надіслати, спробуйте ще раз».
- **AC-4.3** Given a session that ended (refresh failed), then the comment is kept in the session storage of the tab for that award and decision and offered again after signing in; it is cleared after a successful decision.
- **AC-4.4** Given the batch dialog, then the same applies: the dialog stays open until the answer; on a whole-request failure (network, 400, 5xx) the comment and selection stay.

## 5. Edge cases

| Case | Expected |
|------|----------|
| Dean changes the period while requests are open | Their deadlines stay; only new deadlines use it (AC-1.7) |
| Delegated dean sets the period | Allowed; audit carries `delegatorId` |
| Unit award of the faculty itself (organisation = faculty) | Faculty of the request = the faculty (AC-1.6) |
| College or speciality requests | No faculty above them: global default; the college has no dean scope for the period (A1) |
| Request overdue at `FACULTY_SECRETARY`, no eligible dean (vacancy) | Notice goes to the rector's secretaries (first level above with an eligible reviewer) |
| Request overdue at `RECTOR` | Marked, no e-mail |
| Notice recipient is the owner or submitter of the request | Skipped for that request (reviewer rule) |
| Request decided between the job's select and update | The update repeats the conditions on the row; a decided request is not marked |
| Request escalated by a reviewer after being marked | Mark cleared with the new deadline; the dean level can be marked later |
| Clock change (DST) | Deadlines keep the Kyiv time of day (2.1.8 rule); the job compares instants |
| Backend down for a day | The first run after the restart marks everything overdue once; one digest per recipient |
| Reviewer corrects, owner had the award open | Owner sees the new version on reload; owner cannot edit a pending award anyway |
| Correction changes the category to `NATIONAL` at faculty level | Request stays at its level; the next approval escalates (minimum `RECTOR_SECRETARY`) |
| Correction of an award with a verification badge pending | Badge is set only on approval; unaffected |
| Correction while the owner withdraws | Both lock the request row; a late withdrawal answers 409 `request-claimed`; a late correction 404 |
| Two reviewers correct at once | Request row lock + versions: one 200, the other 409 `request-claimed` or `request-stale` |
| Duplicate check | Not re-run on correction (the reviewer sees the award); noted in the reviewer guide |
| Delegate corrects | `delegatorId` in the audit row; the version's actor is the delegate |
| Decision dialog open while the token expires | Silent refresh then send (unchanged); session end keeps the comment (AC-4.3) |

## 6. Dependencies

### Tables

| Table | Change |
|-------|--------|
| `organizations` (V032) | `review_working_days SMALLINT NULL`, `ck_organizations_review_days CHECK (review_working_days BETWEEN 1 AND 20)`; only faculties use it (application rule) |
| `award_requests` (V033) | `overdue_noticed_at TIMESTAMPTZ NULL`, `overdue_noticed_level VARCHAR(30) NULL` (check as `current_level`); partial index `idx_requests_overdue_unnoticed (deadline)` where open and `overdue_noticed_at IS NULL` |
| `award_versions` (V034) | `ck_award_versions_action` recreated with `CORRECTED`; `comment TEXT NULL` (reason of a correction) |
| `audit_logs` | New actions `REVIEW_PERIOD_CHANGED`, `REVIEW_OVERDUE_NOTICED`, `AWARD_CORRECTED` |

### Endpoints

| Endpoint | Change | Access |
|----------|--------|--------|
| `GET`/`PUT /api/v1/organizations/{id}/review-period` | New (4.2.1) | GET: FS or DEAN scope covering the faculty; PUT: DEAN scope (own or delegated); `SYSTEM_ADMIN` both |
| `GET /api/v1/reviews` | `noticed` filter, `overdueNoticedAt` on items (4.2.2) | Unchanged |
| `GET /api/v1/awards/{id}/status` | `delay.noticedAt` (4.2.2); expected completion per level period (4.2.1) | Unchanged |
| `POST /api/v1/awards/{id}/corrections` | New (2.4.1) | `award:approve:level1`, reviewer rule |
| `GET /api/v1/awards/{id}/versions` | `CORRECTED` action, `comment` (2.4.1) | Unchanged |
| `/actuator/prometheus` | New meters (4.2.2) | Unchanged |

### Services

- 4.2.1: `ReviewPeriods` (faculty of a request, period per level, read/set with audit), `StatusEstimator` (period per level instead of one value), `AwardSubmission`/`ReviewDecisions` (deadline through `ReviewPeriods`), `OrganizationController` or a new `ReviewPeriodController`
- 4.2.2: `OverdueNotices` (`@Scheduled`, update … returning, recipients via `ReviewerAvailability`), `OverdueMails` (after-commit listener, digest per recipient), `ReviewMetrics` (meters; decision listener after commit), `ReviewQueue`/`ReviewSpecifications` (`noticed`), `AwardStatusService` (`noticedAt`), clearing the mark where the deadline is set
- 2.4.1: `AwardCorrection` (rules, version, claim on action, audit), `AwardHistory` (`CORRECTED`, comment), `CorrectionMails`
- 4.2.3: frontend only

### Frontend

- `features/reviews`: period header and dialog (4.2.1); «Прострочено», «Керівника повідомлено», filter «Повідомлені» (4.2.2); batch dialog pending state (4.2.3)
- `features/awards/award-detail`: review panel «Виправити» (2.4.1), decision dialog pending and error states (4.2.3); status page notice line (4.2.2); history entry «Виправлено рецензентом» (2.4.1)
- `features/awards/award-form`: correction mode on route `/awards/:id/correct` with the change summary and reason (2.4.1), `reviewerGuard` resolving the reviewer rule through `GET /awards/{id}/reviewers` or the panel data
- i18n `uk` and `en` for every new text

### External systems

Mailpit in development, the SMTP relay in production (unchanged). Prometheus scraping is not part of the demo stack; the meters are read at `/actuator/prometheus` and `/actuator/metrics/{name}`.

## 7. Technical decisions

| # | Decision | Reasoning | Source |
|---|----------|-----------|--------|
| D-1 | Period stored on the faculty (`organizations.review_working_days`), nullable; the global `app.workflow.review-working-days` applies when null and at rector levels | One value per faculty; rector levels serve every faculty | Tracker 2026-10-05, design review P-6 |
| D-2 | A changed period does not move existing deadlines | A reviewer's deadline does not shift under them; no mass update of open requests | — |
| D-3 | The notice is a mark plus an e-mail to the next level; nothing moves or is decided | A decision on an award is a person's act; the dean already may take over (4.1 D-4) | Tracker 2026-10-05, deviation 1 |
| D-4 | Mark stored in two columns on `award_requests`, cleared whenever the deadline is set | One notice per request and level; no extra table; the status keeps its meaning (4.1 A4) | 4.1 PRD A4 |
| D-5 | Job: `UPDATE award_requests SET overdue_noticed_at = :now, overdue_noticed_level = current_level WHERE status IN (…) AND deadline < :now AND overdue_noticed_at IS NULL RETURNING …`, then recipients and an after-commit event; plain `@Scheduled` | Idempotent and safe on two instances without ShedLock; ShedLock waits for Epic 9 | Design review P-3 |
| D-6 | One digest e-mail per recipient per run | A dean of a large faculty would otherwise get dozens of messages after a holiday weekend | — |
| D-7 | Meters through Micrometer (already exported to Prometheus); gauges refreshed by the job, counters and timers after commit | No query per scrape; metrics never count a rolled-back decision | MONITORING_OBSERVABILITY §5 |
| D-8 | The time a request reached its level is the latest of `submitted_at` and the previous `review_decisions.decided_at` that moved it; no new column | The data exists; a column would need a back-fill for little gain | — |
| D-9 | Correction is an action of the reviewer on the request: reviewer rule, claim on action, request row lock, award `version` + `requestVersion` | Same concurrency and access as a decision (4.1 D-2, D-3) | 4.1 PRD |
| D-10 | Correction recorded as an award version `CORRECTED` with the reason in `award_versions.comment`; no `review_decisions` row | It changes the award, not the request; the history page already shows versions with field changes | Feature 2.2 D-2 |
| D-11 | Editable fields: title (both), description (both), awarding organisation, award date, category, external URL; not the recipient, not documents | A wrong recipient or a missing document is the owner's to fix (return) | Roadmap task "field-level editability rules" |
| D-12 | Correction path `POST /awards/{id}/corrections` (a record of an act), not `PUT /awards/{id}` | `PUT` stays the owner's draft edit with `award:update:own`; separate access, audit and e-mail | Roadmap task "modification REST endpoints" |
| D-13 | Comment kept per tab in session storage under the award and decision, cleared on success | Survives a sign-in round trip; never shared across tabs or stored after the tab closes | F-4 |

**Proposed deviations from the docs** (applied in the PR of the story that touches them):

1. **Label of the mark**: the 4.1 tracker decision calls the overdue mark «Ескальовано», but `ESCALATED` is already shown as «Ескальовано» (moved up by a decision, 4.1 §9 step 11). Proposed: the mark reads «Керівника повідомлено» next to «Прострочено»; «Ескальовано» stays the status. — 4.2.2, tracker decision row reworded
2. **Roadmap and BRD "automatic escalation"** (tracker deviation 1): reworded to "overdue notice to the next level"; the BPMN diagram gets the timer branch with the notice, the state machine a note "overdue: mark only". — 4.2.2
3. **`DEADLINE_REMINDER`** (DATA_DICTIONARY §5.1) stays planned for Epic 7; the notice of this feature is an e-mail, not a `notifications` row, until Epic 7 adds the table. — 4.2.2, dictionary note
4. **Roadmap 2.4.1 "modification approval workflow"**: a correction needs no second approval; it is made by the reviewer who decides the request and is shown to the owner. — 2.4.1
5. **New story 4.2.3** (F-4, F-7) added to Feature 4.2 (2 points; feature total 15): SCRUM-59, #162.
6. **F-3** (bilingual e-mails) stays open until Epic 7: users have no stored language; the new e-mails follow the same bilingual form (AC-2.9, AC-3.5).
7. **RBAC_matrix.md**: rows "Set the faculty review period" (DEAN, admin), "Read the faculty review period" (FS, DEAN, admin), "Correct a pending award" (reviewers of the level). — per story

**Assumptions**

- A1. Only units of type `FACULTY` have a period; colleges and their specialities use the global default (no dean role scoped to a college in the seed).
- A2. Range 1–20 working days (four weeks) is wide enough; a longer period would hide stuck requests.
- A3. A lost notice e-mail (mail server down) is not resent; the mark and the queue filter remain, and the dean sees the requests in «Прострочені нижчого рівня».
- A4. The rector is not e-mailed about requests overdue at the rector's secretary level unless he is the first eligible level above — which he is; at `RECTOR` nothing is sent.
- A5. Correcting the title does not re-run the duplicate check.
- A6. `overdue_noticed_level` is kept for the audit and the metrics; the UI reads only `overdueNoticedAt`.

## 8. Contract

### 8.1 OpenAPI stubs (written to `openapi.yml` in this PRD's PR, `x-status: planned`)

New operations and schemas are stubbed now. New properties and enum values of schemas the contract tests compare with the DTOs (`StatusDelay.noticedAt`, `ReviewItem.overdueNoticedAt`, `VersionAction.CORRECTED`, `AwardVersion.comment`) enter `openapi.yml` with the story that implements them.

- `GET /organizations/{id}/review-period` → 200 `ReviewPeriod`; 401, 403, 404. 4.2.1.
- `PUT /organizations/{id}/review-period`, body `ReviewPeriodUpdate` → 200 `ReviewPeriod`; 401, 403, 404, 422. 4.2.1.
- `ReviewPeriod`: `organizationId` (int64), `workingDays` (integer, nullable: own value), `effectiveWorkingDays` (integer), `defaultWorkingDays` (integer), `updatable` (boolean: the caller may change it).
- `ReviewPeriodUpdate`: `workingDays` (integer 1–20, nullable: back to the default), required.
- `GET /reviews`: query `noticed` (boolean, default false); `ReviewItem.overdueNoticedAt` (date-time, nullable). 4.2.2.
- `StatusDelay.noticedAt` (date-time, nullable). 4.2.2.
- `POST /awards/{id}/corrections`, body `AwardCorrection` → 200 `CorrectionOutcome`; 400, 401, 403, 404, 409 (`award-stale`, `request-stale`, `request-claimed`), 422 (`validation-failed`, `no-change`). 2.4.1.
- `AwardCorrection`: the fields of `AwardCreateRequest` without `recipientOrganizationId` (each optional; an absent field is unchanged, `null` clears an optional field), `version` (int64, required), `requestVersion` (int64, required), `reason` (string 1–1000, required).
- `CorrectionOutcome`: `award` (`Award`), `requestVersion` (int64), `changedFields` (array of string).
- `VersionAction` gains `CORRECTED`; `AwardVersion.comment` (string, nullable). 2.4.1.

### 8.2 Migration outlines

| File | Story | Content |
|------|-------|---------|
| `V032__organizations_review_period.sql` | 4.2.1 | `ALTER TABLE organizations ADD COLUMN review_working_days SMALLINT NULL`; `ADD CONSTRAINT ck_organizations_review_days CHECK (review_working_days BETWEEN 1 AND 20)` |
| `V033__award_requests_overdue_notice.sql` | 4.2.2 | `ADD COLUMN overdue_noticed_at TIMESTAMPTZ NULL`, `ADD COLUMN overdue_noticed_level VARCHAR(30) NULL` with `ck_award_requests_noticed_level` (same values as `current_level`) and `ck_award_requests_noticed_pair` (both null or both set); `CREATE INDEX idx_requests_overdue_unnoticed ON award_requests (deadline) WHERE status IN ('SUBMITTED','IN_REVIEW','ESCALATED') AND overdue_noticed_at IS NULL` |
| `V034__award_versions_corrected.sql` | 2.4.1 | Drop and recreate `ck_award_versions_action` with `CORRECTED`; `ADD COLUMN comment TEXT NULL`; `trg_award_versions_immutable` unchanged (still allows only clearing `actor_id`) |

## 9. Test plan

| AC | Unit | Slice | IT | FT | E2E |
|----|------|-------|----|----|-----|
| 1.1–1.5 | ✓ `ReviewPeriods`: faculty of a request, scope, range | ✓ 422, 403 | ✓ audit row | ✓ dean GET/PUT/reset; FS GET only; other dean 404; department id 404 | |
| 1.6–1.8 | ✓ deadline per level; estimate with mixed periods | | ✓ submission and level move with a faculty period | ✓ status page estimate | |
| 1.9 | ✓ header and dialog components | | | | ✓ dean sets 5 days; English; 360 px; axe |
| 2.1–2.3 | ✓ notice level (vacancy, rector, owner excluded); clearing on deadline set | | ✓ job marks once, second run nothing, cleared on escalate; two concurrent runs → one mark (two threads) | ✓ notice e-mail digest in Mailpit | |
| 2.4, 2.5 | ✓ chips, filter | ✓ `noticed` param | ✓ specification with `noticed` | ✓ queue and status fields | ✓ dean filter «Прострочені нижчого рівня» |
| 2.6 | ✓ meters with `SimpleMeterRegistry` (on-time tag, duration from D-8) | | ✓ no meter after a rollback | ✓ `/actuator/prometheus` lists the meters | |
| 2.7–2.9 | ✓ digest text uk/en | | ✓ mail failure keeps marks (Mailpit stopped container) | | |
| 3.1–3.4 | ✓ `AwardCorrection` rules (fields, no change, category → score) | ✓ 422/409 mapping | ✓ version + audit + claim in one transaction; correction vs withdrawal race | ✓ every error code; correction then approval escalates for a raised category | |
| 3.5, 3.6 | ✓ mail text | | ✓ mail after commit only | ✓ versions show `CORRECTED` with comment | |
| 3.2, 3.8 | ✓ form change detection, summary | | | | ✓ secretary corrects a date; owner sees history; English; 360 px; axe |
| 4.1–4.4 | ✓ dialog states per error, session storage | | | | ✓ conflict keeps the comment (route mocked 409) |

Coverage target 85 % lines; static analysis clean. 4.2.1 (access rule), 4.2.2 (migration, scheduled writes) and 2.4.1 (access rule, migration) go through the security review agent.

## 10. Manual verification

Preconditions: `docker compose up -d postgres redis mailpit minio clamav`, backend `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` on `http://localhost:8080` with `APP_WORKFLOW_OVERDUE_CRON="0 * * * * *"` (every minute, for the walkthrough), frontend `npm start` on `http://localhost:4200`. With the full Compose stack instead (`docker compose up -d --build`, app on `http://localhost`), start the app with `$env:APP_WORKFLOW_OVERDUE_CRON = "0 * * * * *"; docker compose up -d app` (PowerShell). Seed accounts (password `Passw0rd-demo`, `@chnu.edu.ua`): `employee.fmi`, `secretary.fmi`, `secretary2.fmi`, `dean.fmi`, `rector.secretary`, `rector`, `admin`. Mailpit `http://localhost:8025`; Swagger `http://localhost:8080/swagger-ui.html`; psql `docker compose exec postgres psql -U postgres award_monitoring`. Faculty 9 has no period of its own at the start (`update organizations set review_working_days = null where org_id = 9;`).

1. As `dean.fmi` open `http://localhost:4200/reviews`. Expected: «Термін розгляду: 3 робочі дні (типовий)» with «Змінити». (AC-1.1)
2. «Змінити» → 0. Expected: the dialog refuses (1–20). Enter 5, save. Expected: «5 робочих днів»; psql `select action_type, old_values, new_values from audit_logs where action_type = 'REVIEW_PERIOD_CHANGED' order by created_at desc limit 1;` shows 3 (default) → 5. (AC-1.2, 1.4)
3. As `secretary.fmi` open `/reviews`. Expected: «5 робочих днів», no «Змінити». Swagger `PUT /api/v1/organizations/9/review-period` → 404. (AC-1.5)
4. As `employee.fmi` submit award A (faculty level). psql `select submitted_at, deadline from award_requests where award_id = <A>;` Expected: 5 working days apart. Open A's status page. Expected: expected completion = deadline (one faculty level). (AC-1.6, 1.8)
5. As `employee.fmi` submit award N (national category). Expected: status page estimate = 5 + 5 working days for the faculty levels + 3 for the rector's secretary. (AC-1.8)
6. As `secretary.fmi` approve N. psql: N's deadline is 5 working days from now (dean level). As `dean.fmi` approve N. Expected: deadline 3 working days from now (rector's secretary). (AC-1.6)
7. As `dean.fmi` «Змінити» → «Типовий термін». Expected: «3 робочі дні (типовий)»; A's deadline unchanged in psql. (AC-1.3, 1.7)
8. psql `update award_requests set deadline = now() - interval '1 hour' where award_id = <A>;`. Wait for the next minute. Expected: psql `select overdue_noticed_at, overdue_noticed_level from award_requests where award_id = <A>;` set, level `FACULTY_SECRETARY`; status unchanged; Mailpit: one e-mail to `dean.fmi` «Прострочені заявки: 1» listing A with «не взято» and a link; none to `employee.fmi`. (AC-2.1, 2.2, 2.9)
9. Wait another minute. Expected: no new e-mail, `overdue_noticed_at` unchanged. (AC-2.3)
10. As `dean.fmi` open `/reviews`, filter «Прострочені нижчого рівня». Expected: A with «Прострочено» and «Керівника повідомлено <сьогодні>»; A's page offers «Взяти в роботу» (A is unclaimed; «Взяти на себе» appears only while a secretary holds it). As `employee.fmi` open A's status page. Expected: «Термін розгляду минув …; керівника повідомлено …». (AC-2.4, 2.5)
11. As `secretary.fmi` «Передати декану» on A. psql: notice columns null, new deadline. Set the deadline in the past again and wait a minute. Expected: one e-mail to `rector.secretary`, level `DEAN` in the mark. (AC-2.3, 2.2)
12. Repeat step 8 for a request at `RECTOR` level (escalate C up to the rector as in 4.1 §9 step 13). Expected: marked, no e-mail. (AC-2.2)
13. Open `http://localhost:8080/actuator/prometheus` (as today's access allows) and search `awards_review`. Expected: `awards_review_open`, `awards_review_overdue`, `awards_review_decisions_total{decision,level,on_time}`, `awards_review_decision_duration_seconds`, `awards_review_overdue_notices_total`. (AC-2.6)
14. As `employee.fmi` submit award B with date 2026-09-01 and a typo in the title. As `secretary.fmi` open B. Expected: «Виправити» on the panel. Click it. Expected: the form with the values, recipient and documents read-only, «Зберегти» disabled. (AC-3.1, 3.2)
15. Fix the title and set the date to 2026-09-02; leave the reason empty. Expected: refused. Type «Дата за сертифікатом», save. Expected: the confirmation lists two fields old → new; confirm. Expected: back on the award page; B is claimed by `secretary.fmi`; the request still at faculty level, same deadline. (AC-3.2, 3.3, 3.4)
16. Mailpit: e-mail to `employee.fmi` with both changes, the reason and Alina's name. As `employee.fmi` open B → «Історія змін». Expected: «Виправлено рецензентом: Аліна Секретаренко», the two fields, the reason. psql `select action_type, new_values from audit_logs where action_type in ('AWARD_CORRECTED','REVIEW_CLAIMED') order by created_at desc limit 2;` (AC-3.5, 3.6, 3.3)
17. As `secretary2.fmi` open B (held by Alina). Expected: no «Виправити». Swagger `POST /api/v1/awards/<B>/corrections` → 409 `request-claimed` with `reviewer`. (AC-3.1, 3.7)
18. As `secretary.fmi` correct B's category to a `NATIONAL` one with a reason, then approve. Expected: «Передано декану.» (AC-3.4)
19. As `secretary.fmi` open a pending unclaimed award, «Відхилити», type a comment. In another browser as `secretary2.fmi` claim that award. Back in the dialog, «Підтвердити». Expected: the dialog stays open, «Нагороду вже взяв у роботу …», the comment still there. (AC-4.1, 4.2)
20. Switch to English; repeat steps 1, 10 and 14 briefly; at 360 px open the period dialog and the correction form. Expected: English texts, usable layout, keyboard reachable. (AC-1.9, 3.8)

### Detours

21. As `employee.fmi` open `http://localhost:4200/awards/<B>/correct` directly. Expected: the forbidden page; Swagger `POST …/corrections` → 403 (no review permission). As `secretary.fmi` submit an award S of your own; Swagger `POST /api/v1/awards/<S>/corrections` as `secretary.fmi` → 404 (own award). (AC-3.7)
22. As `secretary.fmi` open `/awards/<A>/correct` after A was approved (any approved award). Expected: not found; Swagger → 404. (AC-3.7)
23. Open B's correction form in two tabs as `secretary.fmi`; save in one, then in the other. Expected: «Нагороду змінено в іншому вікні. Завантажте її знову.» with «Завантажити поточну версію»; the typed values stay until that button reloads the form. If B was unclaimed before the first save, the second tab shows «Розгляд змінився в іншому вікні…» (`request-stale`) instead, because the first save claimed it. Swagger with the old `version` → 409 `award-stale`. (AC-3.7)
24. Save a correction, press Back, then reload. Expected: the award page with the corrected values; repeating the request in Swagger with the same body → 409 `award-stale` (version moved). (AC-3.7)
25. Correct with every field unchanged in Swagger. Expected: 422 `no-change`. (AC-3.7)
26. As `employee.fmi` withdraw award D while `secretary.fmi` saves a correction of D in another browser within a second. Expected: one succeeds; the other shows its 409 or not-found message. (§5)
27. Stop the backend while a request is past its deadline (step 8 set-up), wait two minutes, start it. Expected: one mark and one e-mail after the start, not two. (§5, AC-2.3)
28. Run a second backend on port 8081 (`--server.port=8081`) with the same cron; make two requests overdue. Expected: each marked once, one digest per recipient. (AC-2.8)
29. `docker compose stop mailpit`, make a request overdue, wait a minute. Expected: the mark is set, the backend logs the mail failure; `docker compose start mailpit`, no resend. (AC-2.7)
30. Let the access token expire (15 minutes) with the correction form filled, then save. Expected: silent refresh, the correction is saved. Shortcut: in DevTools run `sessionStorage.setItem('access_token', 'x')` instead of waiting. (§5)
31. With the decision dialog open, end this tab's session: in DevTools run `sessionStorage.setItem('access_token', 'x'); sessionStorage.setItem('refresh_token', 'x')` (signing out in another tab does not end it, as each tab holds its own tokens); confirm a decision with a comment. Expected: sign-in; back on the award, opening the same decision offers the kept comment. (AC-4.3)
32. As `dean.fmi` delegate the dean role to `secretary2.fmi` for today; as `secretary2.fmi` change the period. Expected: allowed; the audit row has `delegatorId` of the dean. Revoke the delegation. Expected: the revocation ends `secretary2.fmi`'s sessions, so the next `PUT` → 401 and the app returns to sign-in; signed in again, `/reviews` shows no «Змінити». (AC-1.2, §5)
33. Swagger `PUT /api/v1/organizations/64/review-period` (a department) as `dean.fmi` → 404; with `workingDays: 21` on 9 → 422. (AC-1.4, 1.5)
34. During step 29 (Mailpit stopped) as `dean.fmi` open `/reviews` with «Прострочені нижчого рівня». Expected: the marked request is listed with «Керівника повідомлено» although no e-mail went out. (AC-2.4, 2.7)
35. As `secretary2.fmi` under the dean delegation of step 32 open a pending award's correction form; as `dean.fmi` revoke the delegation; save the correction. Expected: the sign-in page (the revocation ends the delegate's sessions), nothing saved (no new entry in «Історія змін»). (AC-3.7)
36. After step 27 (backend restarted) open `/actuator/prometheus` before the next run. Expected: `awards_review_open` and `awards_review_overdue` read the current counts at the latest after the first run; before it they may be missing or 0 (finding V-2). (AC-2.6)
37. Stop the backend (Ctrl+C, or `docker compose stop app` on the Compose stack); as `secretary.fmi` confirm a decision with a comment. Expected: an error message in the dialog (behind the Compose proxy after up to about a minute), the comment kept; start the backend, confirm again succeeds. Stopping Redis does not reproduce this: the revoked-token check fails open by design. (AC-4.2)

## 11. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| A notice storm after downtime or a holiday | Deans flooded | Digest per recipient per run (D-6); one mark per level (D-4) |
| The deadline is set in several places (submission, resubmission, decisions, withdrawal) | A path forgets to clear the mark or to use the faculty period | One `ReviewPeriods.restart(request, level, now)` sets deadline and clears the mark; unit test per transition row |
| Reviewer edits change what the owner submitted | Owner disputes the content | Mandatory reason, version history visible to the owner, e-mail, audit row with old and new values |
| Scheduled job on two instances | Double notices | Update … returning under row locks (D-5); IT with two threads |
| Gauges stale between runs | SLA figures up to an hour old | Stated in the reviewer guide; acceptable for the demo; Epic 9 alerts read counters |
| Three migrations | Ordering conflicts | V032–V034 reserved in story order |

## 12. Definition of Done

- `./mvnw verify` green (unit, slice, IT, FT), JaCoCo ≥ 85 % lines, Checkstyle/PMD/SpotBugs clean
- `npm run lint`, `npm run test:ci`, Playwright scenarios for AC-1.9, 2.4, 3.2, 3.6, 3.8, 4.2
- Docs in the same PR as the story: `openapi.yml` (stubs → implemented), DATA_DICTIONARY §1.3, §2.4, §3.1, §4.1 actions, §5.1 note, V032–V034, RBAC_matrix.md, BPMN overdue branch and state-machine note (`puml-check.ps1`), roadmap/BRD wording (deviation 2), reviewer guide in `docs/user/` (period, notices, corrections), MONITORING_OBSERVABILITY meter list, `CHANGELOG.md`, EPIC-04 tracker, `BACKLOG.md`
- Security review of 4.2.1, 4.2.2 and 2.4.1 diffs
- Audit rows for every period change, notice and correction
- §10 manual verification run in the browser after the validation, including the detours

## 13. Validation (2026-10-08, `develop` at a8e2b3f)

Gates on `develop`: `mvn verify` — 886 unit and slice tests, 293 integration and functional, 98.6 % lines, Checkstyle 0, PMD 0, SpotBugs 0; frontend lint clean, 541 Vitest; Playwright `review-period.spec`, `overdue-notice.spec`, `award-correction.spec` and the AC-4.2 case of `reviews.spec` with the review queue scenarios: 16/16. All four operations of the feature (`GET`/`PUT /organizations/{id}/review-period`, `GET /reviews?noticed`, `POST /awards/{id}/corrections`) are in `openapi.yml` without `x-status: planned`; every `*IT` applies V032–V034 to an empty database. The browser walk of §10 is left to the manual run; the UI steps are driven by the Playwright specs below.

### AC evidence

| AC | Evidence | Result |
|----|----------|--------|
| 1.1 | `ReviewPeriodsTest#ac1_1_…`, `ReviewPeriodFT#ac1_1_…`, `review-period.component.spec#ac1_1_…`, `reviews.service.spec#ac1_1_…`; E2E `review-period.spec` | pass |
| 1.2 | `ReviewPeriodsTest#ac1_2_…` (2), `ReviewPeriodFT#ac1_2_ac1_3_…`, `ReviewPeriodFT#ac1_2_aDelegatedDean…`, `review-period-dialog.component.spec#ac1_2_…`; E2E `review-period.spec` | pass |
| 1.3 | `ReviewPeriodsTest#ac1_3_…`, `ReviewPeriodFT#ac1_2_ac1_3_…`, `review-period-dialog.component.spec#ac1_3_…` (2) | pass |
| 1.4 | `ReviewPeriodsTest#ac1_4_…` (2), `ReviewPeriodFT#ac1_4_…`, `review-period-dialog.component.spec#ac1_4_…` (2) | pass |
| 1.5 | `ReviewPeriodsTest#ac1_5_…` (4), `ReviewPeriodFT#ac1_5_…` (4), `review-period.component.spec#ac1_5_…` | pass |
| 1.6 | `StatusEstimatorTest#ac1_6_…` (3), `AwardSubmissionTest#ac1_6_…` (2), `AwardResubmissionTest#ac1_6_ac1_7_…`, `ReviewPeriodFT#ac1_6_ac1_7_…` | pass |
| 1.7 | `ReviewPeriodFT#ac1_6_ac1_7_aSubmissionIsDueByThePeriodInForceAndKeepsItsDeadlineAfterAChange`, `AwardResubmissionTest#ac1_6_ac1_7_…` | pass |
| 1.8 | `StatusEstimatorTest#ac1_8_…` (2), `AwardSubmissionTest#ac1_6_ac1_8_…` | pass |
| 1.9 | `review-period-dialog.component.spec#ac1_9_…`; E2E `review-period.spec#ac1_9` (English, 360 px, keyboard, axe) | pass |
| 2.1 | `OverdueJobTest#ac2_1_…` (2), `OverdueNoticesIT#ac2_1_…`, `OverdueNoticeFT#ac2_1_ac2_2_ac2_9_…` | pass |
| 2.2 | `OverdueNoticesTest#ac2_2_…` (2), `OverdueMailsTest#ac2_2_…` (2), `OverdueNoticeFT#ac2_1_ac2_2_ac2_9_…` | pass |
| 2.3 | `AwardRequestTest#ac2_3_…`, `OverdueNoticeFT#ac2_3_…`, `OverdueNoticeFT#ac2_1_ac2_2_ac2_9_…` (second run) | pass |
| 2.4 | `ReviewEndpointsTest#ac2_4_…`, `OverdueNoticeFT#ac2_4_ac2_5_…`, `review-list.component.spec#ac2_4_…` (3); E2E `overdue-notice.spec#ac2_4` | pass |
| 2.5 | `OverdueNoticeFT#ac2_4_ac2_5_…`, `award-status.component.spec#ac2_5_…` | pass |
| 2.6 | `ReviewMetricsTest#ac2_6_…` (3), `DecisionLogMeasureTest#ac2_6_…` (2), `OverdueNoticeFT#ac2_6_…` (`/actuator/prometheus`) | pass |
| 2.7 | `OverdueMailsTest#ac2_7_…`, `OverdueJobTest#ac2_1_aDatabaseFailure…`; detour 29 | pass |
| 2.8 | `OverdueNoticesIT#ac2_8_twoInstancesAtTheSameMinuteMarkAndAuditARequestOnce` | pass |
| 2.9 | `OverdueMailsTest#ac2_9_…`, `OverdueNoticeFT#ac2_1_ac2_2_ac2_9_…` (Mailpit) | pass |
| 3.1 | `review-panel.component.spec#ac3_1_…` (2), `award-correction.component.spec#ac3_1_…` (2); E2E `award-correction.spec` | pass |
| 3.2 | `award-correction.component.spec#ac3_2_…` (5); E2E `award-correction.spec` | pass |
| 3.3 | `CorrectionRulesTest#ac3_3_…`, `CorrectionLogTest#ac3_3_…` (2), `CorrectionEndpointsTest#ac3_3_…` (2), `CorrectionFT#ac3_3_…` (2), `awards.service.spec#ac3_3_…` | pass |
| 3.4 | `CorrectionFT#ac3_3_ac3_4_ac3_5_ac3_6_…` (level and deadline kept; the next approval of a ministry category escalates) | pass |
| 3.5 | `CorrectionMailsTest#ac3_5_…` (3), `CorrectionLogTest#ac3_5_…` (2), `CorrectionFT#ac3_3_ac3_4_ac3_5_ac3_6_…` (Mailpit) | pass |
| 3.6 | `award-history.component.spec#ac3_6_…`, `CorrectionFT#ac3_3_ac3_4_ac3_5_ac3_6_…` (owner and dean read `CORRECTED`); E2E `award-correction.spec` | pass |
| 3.7 | `CorrectionRulesTest#ac3_7_…`, `CorrectionEndpointsTest#ac3_7_…` (3), `CorrectionFT#ac3_7_…` (3), `award-correction.component.spec#ac3_7_…` (3) | pass |
| 3.8 | E2E `award-correction.spec#ac3_8` (English, 360 px, keyboard, axe) | pass |
| 4.1 | `decision-dialog.component.spec#ac4_1_…` | pass |
| 4.2 | `decision-dialog.component.spec#ac4_2_…` (3), `review-panel.component.spec#ac4_2_…` (3); E2E `reviews.spec#ac4_2` (route mocked 409) | pass |
| 4.3 | `decision-dialog.component.spec#ac4_3_…` (3, incl. another user in the same tab) | pass |
| 4.4 | `review-list.component.spec#ac4_4_a_whole_request_failure_%i_…` (0, 400, 500) | pass |

### Edge cases (§5)

| Case | Covered by |
|------|------------|
| Period changed while requests are open | `ReviewPeriodFT#ac1_6_ac1_7_…` |
| Delegated dean sets the period | `ReviewPeriodFT#ac1_2_aDelegatedDean…`, `ReviewPeriodsTest#ac1_2_aDelegatedDean…` |
| Unit award of the faculty itself | `StatusEstimatorTest#ac1_6_aUnitAwardOfTheFacultyUsesTheFacultyPeriod` |
| College or speciality requests | `StatusEstimatorTest#ac1_6_aUnitOutsideEveryFacultyUsesTheDefault`, `ReviewPeriodFT#ac1_5_aDepartmentOrTheUniversityIsNoFaculty` |
| Overdue at `FACULTY_SECRETARY` with a vacant dean level | `OverdueNoticesTest#ac2_2_aVacantLevelIsSkippedForTheNextLevelWithAReviewer` |
| Overdue at `RECTOR` | `OverdueNoticesTest#ac2_2_aRequestAtTheRectorOrWithoutReviewerAboveHasNoRecipient`; §10 step 12 |
| Recipient is the owner or submitter | `OverdueNotices.recipients` passes both to `ReviewerAvailability.candidates` (reviewer rule tests of 4.1) |
| Decided between select and update | Claim SQL repeats the conditions under `FOR UPDATE SKIP LOCKED`; `OverdueNoticesIT#ac2_1_aRequestWithoutPassedDeadlineOrNotOpenIsNotMarked` |
| Escalated after the mark | `OverdueNoticeFT#ac2_3_movingToTheNextLevelClearsTheMarkWithTheNewDeadline` |
| DST | Working-day tests of 2.1.8; the claim compares instants |
| Backend down for a day | §10 detour 27 |
| Owner had the award open during a correction | §10 step 16 (reload) |
| Category raised to `NATIONAL` at faculty level | `CorrectionFT#ac3_3_ac3_4_ac3_5_ac3_6_…` (approval escalates) |
| Verification badge pending | By design: the badge is set on approval only; no test |
| Correction while the owner withdraws | `CorrectionFT#ac3_7_aCorrectionAndAWithdrawalAtOnceLeaveOneWinner` |
| Two reviewers correct at once | Request row lock and versions (`ReviewGuards`), `CorrectionFT#ac3_7_aRequestHeldByAColleague…` |
| Duplicate check not re-run | By design; reviewer guide |
| Delegate corrects | `CorrectionLogTest#ac3_3_theAuditRowCarriesOldAndNewValuesTheReasonAndTheDelegator` |
| Token expires with the dialog open | `decision-dialog.component.spec#ac4_3_…`; detours 30, 31 |

### Security checklist

| Item | Control |
|------|---------|
| A01 access control | `ReviewPeriodController` behind `CAN_READ` plus the dean scope in `ReviewPeriods` (own role or delegation in effect, faculty covered by the scope; other faculties, departments and the university 404); `CorrectionController` behind `CAN_REVIEW` plus the reviewer rule and claim through `ReviewGuards` (held by a colleague 409, own or out-of-scope 404); notice recipients through `ReviewerAvailability` (owner and submitter excluded) |
| A02 cryptography | No new secrets or tokens |
| A03 injection | Period bound to an integer 1–20; queue `noticed` bound to a boolean; overdue claim is one parameterised statement; correction fields through the award form validation, reason ≤ 1000; notice and correction e-mails plain text (`MailTextHelper`) |
| A07 authentication failures | Sign-in unchanged; the decision draft is keyed by user, award and decision and removed when the dialog closes |
| Concurrency | Overdue claim `UPDATE … FOR UPDATE SKIP LOCKED` (two instances: `OverdueNoticesIT#ac2_8_…`); corrections under the request row lock plus `@Version` on `awards` and `award_requests` |

### Findings

| # | Finding | State |
|---|---------|-------|
| V-1 | One runtime exception other than a database error while building a notice (`OverdueNotices.run`, one transaction) rolls back the marks of the whole run; the scheduler logs it and the next run repeats it. No data path leads there today (owner, submitter and organisation are NOT NULL) | Accepted; revisit with ShedLock in Epic 9 |
| V-2 | `awards.review.open` and `awards.review.overdue` are refreshed only by the job, so after a restart they read nothing until the first run (up to an hour with the default cron) | Accepted for the demo (§11 "Gauges stale between runs"); §10 detour 36 |
| V-3 | The review period has no version: two deans saving at once is last write wins, each with its own audit row; saving the same value writes no audit row | Accepted: one dean per faculty, the audit keeps both changes |
| V-4 | A 403 (CSRF from a stale page) or 429 in the decision dialog shows «Не вдалося надіслати, спробуйте ще раз»; the comment is kept | Accepted: AC-4.2 groups them with network failures |
| V-5 | Refactor sweep: duplicated `reviewerOf`, form error keys, award title fallback, award links in e-mails, known-problem lookup; copied FT helpers and spec token helpers | First group in the `refactor(award)` follow-up; FT and spec helpers in the tracker's technical notes |

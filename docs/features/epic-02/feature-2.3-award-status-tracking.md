# Feature 2.3: Award Status Tracking

> **Epic**: 2 — Award Lifecycle Management (SCRUM-20)
> **Sprint**: 3–4 (2026-10-01 → 2026-10-11)
> **Points**: 5 (one story)
> **Status**: Validated 2026-10-01 (§12, passed with notes; fixes in 2.3.2; the manual run of §9 pending)
> **Author**: Stefan Kostyk
> **Governing docs**: roadmap § Feature 2.3, US-005, DATA_DICTIONARY §2.1, §3.1 and §3.2, state-machine-award-request.puml, bpmn-approval-workflow.puml, USER_RESEARCH §"user journeys" (approval targets), SUCCESS_METRICS (time to approval), AUTH §3.3, RBAC_matrix.md, ADR-006, ADR-015, openapi.yml `/awards`, EPIC-02 tracker (decision 2026-09-28 on polling)

## 1. Problem and personas

Once an award is submitted, its owner sees one line on the award page: «Подано · Секретар факультету». She cannot tell how many approvals are still ahead, when the award will be public, whether the review is late or what a reviewer wrote. Today she asks the faculty secretary by phone or e-mail, which is the time sink the user research names first ("no visibility into award status, approval timelines"). This feature turns the request into a readable timeline: the levels the award has to pass, the level it is at, the expected completion date, the reason when it is late, and every reviewer decision with its comment.

Until Epic 4 ships, every request stays at `SUBMITTED` on the faculty secretary's level; the view is built and tested against review decisions written by test fixtures, so it shows Epic 4 decisions the day they exist.

| Persona | Need in this feature |
|---------|----------------------|
| Anastasia, employee | See where each submitted award is, when it is expected to be approved, why it is late, and what reviewers asked her to change |
| Alina, faculty secretary; Prof. Martynyuk, dean | See the same timeline on awards of their unit, including the decisions of the other levels |

## 2. Scope

**In (this feature)**

- `award_requests.deadline` set at submission to the end of the current level's review period; existing requests back-filled (V024)
- Expected completion date from the levels the award still has to pass, revised when the current level is late
- Delay explanation: the review is past its period, or no one currently holds the reviewing role for the award's unit
- `GET /api/v1/awards/{id}/status`: request status, approval path with the state of each level, deadline, estimate, delay, reviewer decisions with comments
- The request summary in `GET /awards` and `GET /awards/{id}` gains `deadline`, `estimatedCompletion` and `overdue`
- Status panel on the award page (path stepper, estimate, delay notice, decisions); polled every 60 s while the page is visible and the request is not final, with an in-page notice when the status changes
- «Мої подання» card on the home page: the caller's submitted awards that are not final yet, with level and expected date

**Out (where it goes)**

- E-mail, push and in-app notifications on status change — Epic 7 (the in-page notice of this feature is not a notification)
- Review decisions themselves (approve, reject, return, escalate), reviewer assignment and `current_reviewer_id` — Epic 4
- Automatic expiry (`EXPIRED`) and escalation of late requests — Epic 4 workflow engine; this feature only explains the delay
- WebSocket or server-sent events — not needed at 60 s polling (tracker decision 2026-09-28); revisit with the Epic 7 broker
- Working-day calendar with Ukrainian holidays — periods are calendar days (D-2)
- Appeals after rejection — not requested in Epic 2

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-27 (#80) | 2.3.1 Award status tracking (US-005) | 5 | no | 2.1.1, 2.2.1 (merged) |

The tracker marks the story `parallel: yes`; the UI is one panel and one home card over a single new endpoint, so it is built in the same lane (deviation 4).

## 4. Acceptance criteria

### 2.3.1 Award status tracking (SCRUM-27)

**Deadlines and estimate**

- **AC-1.1** Given a complete draft, when the owner submits it at time *t*, then the new request has `deadline` = *t* + the review period (default 3 days, `app.workflow.review-period`).
- **AC-1.2** Given requests created before V024 with no deadline, then after the migration each has `deadline` = `submitted_at` + 3 days.
- **AC-1.3** Given a request at level *L* that is not final, then its approval path runs from `FACULTY_SECRETARY` up to the higher of the category's minimum approval level and *L*, and `estimatedCompletion` = `deadline` + review period × the levels after *L* on that path, given as a Kyiv calendar date.
- **AC-1.4** Given a request whose `deadline` has passed and that is not final, then `overdue` is true and `estimatedCompletion` = now + review period × (the levels from *L* to the end of the path), never earlier than the original estimate.
- **AC-1.5** Given a request that is `RETURNED`, then `estimatedCompletion` is null (the clock waits for the owner); given `APPROVED`, `REJECTED` or `EXPIRED`, then it is null and `completedAt` is shown.

**Status endpoint**

- **AC-1.6** Given the owner of a submitted award, when she calls `GET /api/v1/awards/{id}/status`, then she gets the award status, the request status, current level, `submittedAt`, `deadline`, `estimatedCompletion`, `overdue`, `completedAt`, `rejectionReason`, the path as a list of levels each with state `DONE`, `CURRENT` or `UPCOMING` and its due date, and the reviewer decisions oldest first (decision, level, reviewer id and name, comments, time).
- **AC-1.7** Given the owner of a draft, then the endpoint answers 200 with status `DRAFT`, no request, an empty path and no decisions.
- **AC-1.8** Given a reader with a scope over the award's organisation, when the award is not a draft, then 200 with the same body; given a draft of someone else, an award outside the scope or an unknown id, then 404; a non-numeric id answers 400.
- **AC-1.9** Given a request at a level for which no user currently holds the role (an assignment valid today or an active delegation) with a scope that covers the award's organisation, then `delay.reason` is `NO_REVIEWER`; otherwise, given an overdue request, `delay.reason` is `REVIEW_OVERDUE` with `delay.since` = the deadline; otherwise `delay` is null.
- **AC-1.10** Given a seeded database with 200 submitted awards, then `GET …/status` answers in under 1 s at the 95th percentile in the functional test run, and the list endpoint with the new request fields adds no query per row.

**Award list and request summary**

- **AC-1.11** Given `GET /api/v1/awards` and `GET /api/v1/awards/{id}`, then each submitted award's `request` carries `deadline`, `estimatedCompletion` and `overdue` computed as in AC-1.3–1.5.

**UI**

- **AC-1.12** Given the owner on `/awards/{id}` of a submitted award, then a «Статус розгляду» panel shows a stepper «Подано» → each level of the path («Секретар факультету», «Декан», …), done levels with the decision date, the current level highlighted with «Очікується до <date>», and «Орієнтовне завершення: <date>».
- **AC-1.13** Given an overdue request, then the panel shows «Розгляд триває довше, ніж зазвичай (з <date>). Нова орієнтовна дата: <date>»; given `NO_REVIEWER`, it shows «Зараз немає працівника на посаді «<level>» для вашого підрозділу. Зверніться до деканату» instead.
- **AC-1.14** Given reviewer decisions, then the panel lists them («Схвалено», «Відхилено», «Повернуто на доопрацювання», «Передано на вищий рівень»), with the reviewer, level, Kyiv date and time and the comment; a `RETURNED` request shows «Очікує ваших виправлень» in place of the estimate; a rejected one shows the rejection reason.
- **AC-1.15** Given the award page open and visible with a request that is not final, then the status is reloaded every 60 s; when the request status, level or decisions change, the panel updates and «Статус розгляду оновлено» is announced (`aria-live`); polling pauses while the tab is hidden, stops on a final status, and stops on 403/404 with «Ця нагорода вам більше не доступна».
- **AC-1.16** Given a failed status request, then the panel shows the error with «Спробувати ще раз» (only for errors a retry can help, as in Feature 2.2), the rest of the page stays usable and polling backs off to the next interval.
- **AC-1.17** Given the employee on `/`, then a «Мої подання» card lists up to 5 of her awards with status `PENDING`, newest first, each with title, current level, expected date and an «Затримка» chip when overdue, linking to the award; with none it says «Немає нагород на розгляді» and links to «Нова нагорода»; users without `award:create` do not see the card.
- **AC-1.18** Given the award list `/awards`, then submitted awards show the expected date under the status chip and an «Затримка» chip when overdue.
- **AC-1.19** Given the UI in English, then all labels of the panel, the card and the list additions are in English.

## 5. Edge cases

| Case | Expected |
|------|----------|
| Category of the award changes after a return (Epic 4) | The path follows the current category; a path shorter than the levels already passed ends at the current level (AC-1.3 "higher of") |
| Escalation above the minimum level (Epic 4 `ESCALATED`) | The path extends to the current level; earlier levels `DONE` |
| A decision at a level twice (returned, resubmitted, reviewed again) | All decisions listed in order; a level is `DONE` only by its latest `APPROVED` decision |
| Submission just before midnight Kyiv time | `deadline` is an instant; the displayed date is the Kyiv date of the instant (3 days later, same clock time) |
| Review period changed in configuration | New submissions use the new period; stored deadlines stay; estimates of later levels use the new period |
| Reviewer account erased later (Epic 6) | Reviewer shown as «Невідомий користувач»; `reviewer_id` is `RESTRICT` today, so this waits for the Epic 6 erasure rules |
| Role holder only through a delegation (Feature 1.2) | Counts as a holder for `NO_REVIEWER` while the delegation is active |
| Holder assigned at the university level (rector's secretary, rector) | Their scope covers every unit, so `NO_REVIEWER` is false for those levels when the role is held at all |
| Owner moves department after submission | The organisation of the award (set at submission) decides the reviewer scope, as in 2.1 |
| Two tabs polling the same award | Independent reads; no state on the server |
| Request deleted with its award (draft only) | Drafts have no request; 404 after a deletion stops the polling (AC-1.15) |
| Access token expires while the page polls | The interceptor refreshes silently; an ended session goes to the login page and stops polling |
| Clock skew between browser and server | The server computes `overdue` and the estimate; the browser only formats dates |

## 6. Dependencies

### Tables

| Table | Change |
|-------|--------|
| `award_requests` (V024) | No schema change; `UPDATE … SET deadline = submitted_at + interval '3 days' WHERE deadline IS NULL` |
| `review_decisions` (V008) | No change; read-only JPA entity `ReviewDecision` added; written by Epic 4 |
| `user_roles`, `role_delegations` | Read for the `NO_REVIEWER` check (at most two `exists` queries per status call, none in the list): holders and delegates whose account may sign in, never the award's owner |

### Endpoints

| Endpoint | Change | Access |
|----------|--------|--------|
| `GET /api/v1/awards/{id}/status` | New, schema `AwardStatusView` | Owner (any status); `award:read:*` scope for non-drafts — same rule as `GET /awards/{id}` |
| `GET /api/v1/awards`, `GET /api/v1/awards/{id}` | `AwardRequestSummary` gains `deadline`, `estimatedCompletion` (date), `overdue` | Unchanged |

### Services and libraries

- `ApprovalPath` (new, `award/service`): levels of a request from the category's minimum level and the current level
- `StatusEstimator` (new): deadline at submission, estimate, overdue, with an injected `Clock` and the review period from `WorkflowProperties` (`app.workflow.review-period`, `Duration`, default `P3D`)
- `AwardStatusService` (new): assembles the view, reads decisions with their reviewers in one query, runs the reviewer-holder check
- `AwardSubmission`: sets `deadline`
- `AwardMapper`: request summary fields from `StatusEstimator`
- No new library

### Frontend

- `award-status/award-status.component.ts` (new, child of `award-detail`): stepper, estimate, delay notice, decisions, polling with `document.visibilityState`
- `home.component`: «Мої подання» card from `GET /awards?status=PENDING&size=5`
- `award-list.component.html`: expected date and «Затримка» chip
- `awards.service.ts`: `status(id)`; i18n keys `uk` and `en` for decisions, delay texts and the card

### External systems

None.

## 7. Technical decisions

| # | Decision | Reasoning | Source |
|---|----------|-----------|--------|
| D-1 | Polling every 60 s from the award page, paused when hidden, stopped on a final status; no WebSocket | Decisions are human actions taking hours to days; one read per minute per open page is negligible; the broker is chosen at Epic 7 | Tracker decision 2026-09-28, ADR-006, US-005 DoD ("WebSocket or polling") |
| D-2 | Review period of 3 calendar days per level, configurable. **Revised 2026-10-04** (design review): 3 working days (Monday to Friday) per level, configurable; a per-faculty period set by the dean is an Epic 4 story; implemented by the Sprint 4 review-decision fix story | The research targets are < 7 days for the faculty path (secretary, dean) and < 14 days for the university path (up to the rector's secretary); 3 days per level gives 6 and 9. A holiday calendar is not worth its upkeep for an estimate | USER_RESEARCH journeys, SUCCESS_METRICS "time to award approval" |
| D-3 | The path climbs every level from the faculty secretary to the category's minimum approval level | The BPMN workflow passes each level in order; the minimum level is the lowest role allowed to give the final approval (DATA_DICTIONARY §2.2) | bpmn-approval-workflow.puml, Feature 2.1 D-3 |
| D-4 | `deadline` is stored per current level (set at submission, reset by Epic 4 at each level change); the estimate is computed on read | The column and its index already exist for the overdue views (`vw_pending_requests`) and the Epic 4 escalation job; a stored estimate would go stale with every configuration change | V007, R__create_views |
| D-5 | A late request is explained, not expired or escalated | Expiry and escalation change the workflow and belong to the engine of Epic 4; the user story asks for an explanation and an updated timeline | US-005 additional scenario |
| D-6 | Reviewer names and comments are visible to everybody who may read the award | The owner needs the comment to correct the award; the reviewers act in their official role; reading scope is already limited | AUTH §3.3, RBAC_matrix.md |
| D-7 | The personal dashboard is the existing home page with a new card, not a new route | The home page is the signed-in landing page; one card avoids a second list of the same awards | US-005 "personal dashboard" |

**Proposed deviations from the docs** (applied in SCRUM-27's PR after approval):

1. **No WebSocket and no push notifications in 2.3**: roadmap tasks "Implement WebSocket for real-time updates" and "Add mobile push notifications" are replaced by polling (D-1) and move to Epic 7; INTEGRATION_PATTERNS is not touched.
2. **State machine diagram**: `SUBMITTED --> EXPIRED` and `IN_REVIEW --> EXPIRED` stay as the Epic 4 target; a note is added that until Epic 4 a late request is only marked overdue (D-5).
3. **DoD "< 1 second status updates"** is read as the status endpoint answering in < 1 s (p95) and a change appearing on an open page within one polling interval (AC-1.10, AC-1.15).
4. **Story not parallel**: the tracker's `parallel: yes` is dropped (§3).
5. **RBAC_matrix.md**: rows for reading the status timeline and reviewer comments are added.

**Assumptions**

- A1. Each level of the path reviews in turn; no level is skipped (D-3).
- A2. The reviewer-holder check uses role assignments and delegations only; whether the person is on leave is not known to the system.
- A3. Estimates are dates, not times; the UI shows them in the Kyiv calendar.

## 8. Test plan

| AC | Unit | Slice | IT | FT | E2E |
|----|------|-------|----|----|-----|
| 1.1 | ✓ `StatusEstimator` deadline (fixed `Clock`) | | | ✓ submit → `deadline` = submitted + 3 d | |
| 1.2 | | | ✓ `SchemaIT`: back-filled deadlines after V024 | | |
| 1.3–1.5 | ✓ `ApprovalPath` per recognition level and escalation; estimate on time, overdue, returned, final (table-driven) | | | | |
| 1.6, 1.7 | ✓ step states from decisions | ✓ controller body, 400 | ✓ decisions with reviewers in one query (fixture rows in `review_decisions`) | ✓ owner: submitted, draft; returned with comment (fixture) | |
| 1.8 | ✓ visibility (reuses award read rule) | ✓ 404 | | ✓ dean of the faculty 200, other faculty 404, someone else's draft 404 | |
| 1.9 | ✓ precedence `NO_REVIEWER` over `REVIEW_OVERDUE` | | ✓ holder via assignment, via delegation, none, university-wide holder | ✓ overdue by fixture deadline; no secretary after the role ends | |
| 1.10 | | | ✓ query count on the list (Hibernate statistics) | ✓ 200 awards, p95 < 1 s | |
| 1.11 | ✓ mapper | | | ✓ list and detail fields | |
| 1.12–1.14, 1.19 | ✓ component: stepper, estimate, delay texts, decisions, returned, rejected | | | | ✓ submit → panel with path and estimate; overdue via fixture; English |
| 1.15, 1.16 | ✓ polling with fake timers: interval, hidden tab, final status, 404 stop, change notice, retry | | | | ✓ status changed in the database while the page is open → panel updates within the interval (shortened in the E2E config) |
| 1.17, 1.18 | ✓ home card (empty, list, no `award:create`), list chips | | | | ✓ home card shows the submitted award |

Coverage target 85 % lines per `mvn verify`; static analysis clean. V024 is a migration, so the security review agent runs on the diff.

## 9. Manual verification

Preconditions: `.\tools\dev-up.ps1` (backend `local` profile on `http://localhost:8080`, frontend `http://localhost:4200`). Seed accounts (password `Passw0rd-demo`): `employee.fmi@chnu.edu.ua` (faculty 9), `secretary.fmi@chnu.edu.ua` (faculty secretary, faculty 9), `dean.fmi@chnu.edu.ua` (dean, faculty 9), `admin@chnu.edu.ua`. Swagger at `http://localhost:8080/swagger-ui.html`, or `http://localhost/swagger-ui/index.html` with the Compose stack. After «Logout» in Swagger, reload the page before «Authorize»; to switch accounts, also sign out of the application (or use a private window per account). psql: `docker compose exec postgres psql -U postgres award_monitoring`.

1. psql: `select request_id, submitted_at, deadline from award_requests;` Expected: every row has `deadline` = `submitted_at` + 3 days. (AC-1.2)
2. As `employee.fmi` create and submit an award of category level `FACULTY` (e.g. «Подяка декана»). Swagger `GET /api/v1/awards/{id}/status`. Expected: `requestStatus` `SUBMITTED`, `deadline` = submission + 3 days, path `FACULTY_SECRETARY` (`CURRENT`), `DEAN` (`UPCOMING`), `estimatedCompletion` = submission date + 6 days, `overdue` false, `delay` null, no decisions. (AC-1.1, 1.3, 1.6)
3. Submit a second award of level `NATIONAL`. Expected: path of three levels up to `RECTOR_SECRETARY`, estimate + 9 days. Submit one of level `DEPARTMENT`: one level, estimate = deadline date. (AC-1.3)
4. Open `http://localhost:4200/awards/<id of step 2>`. Expected: «Статус розгляду» with «Подано» → «Секретар факультету» (current, «Очікується до <date>») → «Декан»; «Орієнтовне завершення: <date>». (AC-1.12)
5. Open `http://localhost:4200/`. Expected: «Мої подання» with the three awards, level and date. Open `/awards`: the expected date under each «Подано» chip. (AC-1.17, 1.18, 1.11)
6. psql: `update award_requests set submitted_at = now() - interval '5 days', deadline = now() - interval '2 days' where award_id = <id of step 2>;` Reload the award page. Expected: «Розгляд триває довше, ніж зазвичай (з <date 2 days ago>). Нова орієнтовна дата: <today + 6 days>»; the current level «Секретар факультету» reads «Очікується до <today + 3 days>», never the past date; the home card and the list show «Затримка». (AC-1.4, 1.9, 1.13; F-4)
7. As `admin` end the `FACULTY_SECRETARY` role of `secretary.fmi` (Feature 1.2 §9, «Завершити»). As `employee.fmi` reload the award of step 3 (`DEPARTMENT`, not overdue). Expected: «Зараз немає працівника на посаді «Секретар факультету» для вашого підрозділу…». Re-assign the role afterwards. (AC-1.9, 1.13)
8. Keep the award of step 2 open as `employee.fmi`. psql: `insert into review_decisions (request_id, reviewer_id, decision, level, comments) values (<request id>, <secretary.fmi id>, 'RETURNED', 'FACULTY_SECRETARY', 'Додайте номер наказу'); update award_requests set status = 'RETURNED' where request_id = <request id>;` Wait up to 60 s. Expected: the panel updates, «Статус розгляду оновлено» is announced, «Повернуто на доопрацювання» with the secretary's name, the comment, and «Очікує ваших виправлень» instead of the estimate. Open `/` and `/awards`: the award says «Очікує ваших виправлень» under its level. (AC-1.5, 1.14, 1.15, 1.17, 1.18; F-2)
9. psql: insert an `APPROVED` decision at `FACULTY_SECRETARY` and one at `DEAN` for the award of step 3, set the request `APPROVED`, `completed_at = now()` and the award `APPROVED`. Reload. Expected: both levels done with dates, completion date shown, no estimate; network tab shows no further status requests. (AC-1.5, 1.14, 1.15)
10. As `dean.fmi` open the award of step 2. Expected: the same panel with the decision and comment. (AC-1.8)
11. Switch to English. Expected: «Review status», level names, delay texts, decisions and the home card in English. (AC-1.19)

### Detours

12. Open `http://localhost:4200/awards/<id of a draft>` as its owner. Expected: no status panel; Swagger `GET …/status` → 200 with status `DRAFT` and no request. (AC-1.7)
13. Swagger as `secretary.fpp` (faculty 10, created as in Feature 2.1 §9 preconditions) `GET /api/v1/awards/<id of step 2>/status` → 404; `GET /api/v1/awards/abc/status` → 400; `GET /api/v1/awards/999999/status` → 404. (AC-1.8)
14. With the award page open, switch to another tab for 3 minutes, then back. Expected: no status requests while hidden; one request on return. (AC-1.15)
15. Stop the backend with the award page open. Expected after the next interval: the error in the panel with «Спробувати ще раз», award fields still shown; start the backend, the next interval or «Спробувати ще раз» restores the panel. (AC-1.16)
16. Open the award of step 2 as `dean.fmi`; in another browser as `admin` end the dean's role, wait one interval. Expected: «Ця нагорода вам більше не доступна» with no path, decisions or comments left in the panel (or the login page, when the role change revoked the dean's tokens as in Feature 1.2), polling stops either way. Re-assign the role afterwards. (AC-1.15; F-3)
17. Let the access token expire on the award page (15 minutes). Expected: the next poll refreshes the token silently and the panel stays; with the session ended in another browser → login page, no polling loop. (AC-1.15)
18. Reload the award page right after step 8's change and press browser back and forward. Expected: the panel always shows the current status, never a cached older one. (AC-1.15)
19. Change `app.workflow.review-period` to `P5D`, restart the backend, open the award of step 2 (before step 8). Expected: the stored deadline unchanged, the estimate of the later level moves by 2 days; a new submission gets a 5-day deadline. Restore `P3D`. (AC-1.1, 1.3)
20. Sign in as `admin` (no `award:create`) and open `/`. Expected: no «Мої подання» card. (AC-1.17)
21. psql: `update award_requests set submitted_at = '2026-10-22 21:30+00', deadline = null where award_id = <id of step 2, FACULTY>;` (a submission at 00:30 Kyiv time three days before the change to winter time; the deadline is computed from it). Reload the award page. Expected: «Секретар факультету» «Очікується до 26.10.2026», «Декан» and the estimate 29.10.2026 — the same clock time three days later, not a day early (the request is overdue after 26.10, then the dates move as in step 6). (§5 midnight case; F-1, F-6)

## 10. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| The estimate is read as a promise | Complaints when a review takes longer | Labelled «орієнтовне»; the delay text revises it; period configurable |
| Epic 4 changes the request model (reviewer assignment, deadline reset, expiry) | The view or the path rule needs changes | The view reads only `award_requests` and `review_decisions` as documented; Epic 4 resets `deadline` per D-4; the path rule is one class |
| Fixture-written decisions differ from what Epic 4 writes | The panel is wrong on the first real decision | Fixtures follow the V008 constraints (comments on `RETURNED`/`REJECTED`); Epic 4 E2E reuses the panel assertions |
| Polling from many open pages | Load on the API | 60 s interval, paused when hidden, stopped on final status; the endpoint is one indexed read plus one `exists` query |
| `NO_REVIEWER` wrong for unusual role scopes | Misleading advice to the owner | IT covers assignment, delegation and university-wide holders; the text asks to contact the dean's office, not to act |

## 11. Definition of Done

- `./mvnw verify` green (unit, slice, IT, FT), JaCoCo ≥ 85 % lines, Checkstyle/PMD/SpotBugs clean
- `npm run lint`, `npm run test:ci`, Playwright scenarios for AC-1.12–1.17
- Docs in the same PR: `openapi.yml` (`/awards/{id}/status`, `AwardStatusView`, `AwardRequestSummary`), DATA_DICTIONARY §3.1 (`deadline` rule, review period) and §3.2 (read by the status view), state-machine note, RBAC_matrix.md rows, `CHANGELOG.md`, tracker rows (including row 8 of 2.2.3 → Done), `BACKLOG.md`
- §9 manual verification run in the browser after the validation, including the detours

## 12. Validation (2026-10-01, `develop` at 7a69ae9, fixes and refactor sweep in 2.3.2)

Gates on `develop`: `mvn verify` — 589 unit and slice tests, 180 integration and functional, 98.6 % lines, Checkstyle 0, PMD 0, SpotBugs 0; frontend lint clean, 308 Vitest; Playwright 42/42. `docker compose up -d --build` starts clean with a healthy backend; all ten award operations of `/v3/api-docs` are in `openapi.yml` (the spec's approve, reject, return and documents paths belong to Epics 3–4); every `*IT` applies the migrations, V024 included, to an empty database. On the development database 55 requests, none without a deadline; the only deadlines other than submission + 3 days are the three overdue fixtures of `e2e/award-status.spec.ts` (§9 step 1).

### AC evidence

| AC | Evidence | Result |
|----|----------|--------|
| 1.1 | `StatusEstimatorTest#ac1_1_*`, `AwardSubmissionTest#ac1_1_theRequestIsDueOneReviewPeriodAfterTheSubmission`, `WorkflowPropertiesTest#ac1_1_*`, `AwardStatusFT#ac1_1_ac1_3_ac1_6_…` | pass (F-1) |
| 1.2 | `SchemaIT#ac1_2_requestsSubmittedBeforeV024AreDueThreeDaysAfterTheirSubmission` | pass |
| 1.3 | `ApprovalPathTest#ac1_3_*`, `StatusEstimatorTest#ac1_3_*` (3), `AwardStatusFT#ac1_1_ac1_3_ac1_6_…` | pass (F-1) |
| 1.4 | `StatusEstimatorTest#ac1_4_*` (2), `AwardStatusFT#ac1_4_ac1_9_anOverdueReviewIsExplainedAndANewEstimateGiven` | pass (F-4, F-6) |
| 1.5 | `StatusEstimatorTest#ac1_5_*`, `AwardStatusServiceTest#ac1_5_*` (3), `AwardStatusFT#ac1_5_ac1_14_…` | pass |
| 1.6 | `AwardStatusServiceTest#ac1_6_*` (2), `AwardStatusEndpointsTest#ac1_6_*`, `HistoryContractTest#ac1_6_ac1_11_*`, `AwardStatusIT#ac1_6_decisionsAreReadWithTheirReviewersInOneQueryOldestFirst`, `AwardStatusFT#ac1_1_ac1_3_ac1_6_…` | pass |
| 1.7 | `AwardStatusServiceTest#ac1_7_aDraftHasNoRequest`, `AwardStatusEndpointsTest#ac1_7_*`, `AwardStatusFT#ac1_7_ac1_8_…`, `award-detail.component.spec#ac1_7_*` | pass |
| 1.8 | `AwardStatusServiceTest#ac1_8_*`, `AwardStatusEndpointsTest#ac1_8_*`, `AwardStatusFT#ac1_7_ac1_8_draftsAndScopesFollowTheReadRuleOfTheAward` | pass |
| 1.9 | `AwardStatusServiceTest#ac1_9_*` (2), `ReviewerAvailabilityTest#ac1_9_*` (3), `ReviewerAvailabilityIT#ac1_9_*` (7), `AwardStatusFT#ac1_4_ac1_9_…`, `#ac1_9_withoutAFacultySecretaryTheDelayNamesTheMissingReviewer` | pass |
| 1.10 | `AwardStatusIT#ac1_10_ac1_11_theListAddsNoQueryPerRowForTheRequestFields`, `AwardStatusFT#ac1_10_theStatusAnswersWithinOneSecondAtThe95thPercentileAmong200Awards` | pass |
| 1.11 | `HistoryContractTest#ac1_6_ac1_11_*`, `AwardStatusIT#ac1_10_ac1_11_…`, `AwardStatusFT#ac1_11_theListAndTheDetailCarryTheRequestTiming` | pass |
| 1.12 | `award-status.component.spec#ac1_12_*`; E2E `award-status.spec` (`ac1_12 ac1_17 ac1_19`) | pass (F-4) |
| 1.13 | `award-status.component.spec#ac1_13_*` (2); E2E `award-status.spec` (`ac1_13 ac1_18`) | pass |
| 1.14 | `award-status.component.spec#ac1_14_*` (3), `AwardStatusFT#ac1_5_ac1_14_…`; E2E `award-status.spec` (`ac1_14 ac1_15`) | pass |
| 1.15 | `award-status.component.spec#ac1_15_*` (4), `award-detail.component.spec#ac1_15_*`; E2E `award-status.spec` (`ac1_14 ac1_15`) | pass (F-3, F-5) |
| 1.16 | `award-status.component.spec#ac1_16_a_failure_offers_a_retry_and_polling_goes_on` | pass |
| 1.17 | `my-submissions.component.spec#ac1_17_*` (2), `home.component.spec#ac1_17_*` (2); E2E `award-status.spec` (`ac1_12 ac1_17 ac1_19`) | pass (F-2) |
| 1.18 | `award-list.component.spec#ac1_18_*`; E2E `award-status.spec` (`ac1_13 ac1_18`) | pass (F-2) |
| 1.19 | E2E `award-status.spec` (`ac1_12 ac1_17 ac1_19`, English) | pass |

### Edge cases (§5)

| Edge case | Evidence | Result |
|-----------|----------|--------|
| Category changed after a return | `ApprovalPathTest#ac1_3_*` ("higher of" the minimum and the current level) | covered |
| Escalation above the minimum level | `ApprovalPathTest#ac1_3_*`; an `ESCALATED` decision counts as passing its level (`AwardStatusService.passedAt`), revisit with Epic 4 | covered |
| A level decided twice | `AwardStatusServiceTest#ac1_6_passedLevelsAreDoneWithTheirLatestPassingDecision` | covered |
| Submission just before midnight Kyiv time | `StatusEstimatorTest#edge_aSubmissionJustBeforeKyivMidnightIsDueOnTheKyivDateThreeDaysLater`; across a daylight-saving change: F-1 | open (F-1) |
| Review period changed | `StatusEstimatorTest#edge_aChangedPeriodKeepsTheStoredDeadlineAndMovesLaterLevels`; a period that is not positive stops the start (`WorkflowPropertiesTest#edge_*`) | covered |
| Reviewer erased later | `reviewer_id` is `RESTRICT`; waits for the Epic 6 erasure rules | by design |
| Holder through a delegation | `ReviewerAvailabilityIT#ac1_9_aDelegateStandsInForASuspendedDelegator`, `#ac1_9_aDelegationOutlivingItsDelegatorsRoleGivesNoReviewer` | covered |
| University-wide holder | `ReviewerAvailabilityIT#ac1_9_aUniversityWideHolderCoversEveryUnit` | covered |
| Owner moves after submission | `AwardSubmissionTest#edge_theOrganisationIsRefreshedFromTheOwnersCurrentDepartment`; the award's organisation decides the scope | covered |
| Two tabs polling | Read-only endpoint, no server state | by design |
| Request deleted with its award | `award-status.component.spec#ac1_15_stops_when_the_award_is_no_longer_readable` | covered (F-3) |
| Access token expires while polling | `unauthorized.interceptor.spec` (Feature 1.1); §9 step 17 | covered |
| Clock skew | `overdue` and the estimate come from the server (`StatusEstimator` with the injected `Clock`) | by design |
| A request without a stored deadline | `StatusEstimatorTest#edge_aRequestWithoutDeadlineCountsFromItsSubmission`; the response field: F-6 | open (F-6) |

### Security checklist

| OWASP | Control | Where |
|-------|---------|-------|
| A01 Broken access control | `@PreAuthorize` `award:read:own` on the status; the read rule of `GET /awards/{id}` (drafts for the owner only, others inside the organisation scope from the submission on); hidden or unknown awards answer 404 | `AwardController#status`, `AwardStatusService`, `AwardStatusFT#ac1_7_ac1_8_…` |
| A02 Cryptographic failures | No new secrets or stored personal data; reviewer names and comments only to readers of the award (D-6) | `RBAC_matrix.md` |
| A03 Injection | JPQL and Spring Data queries with bound parameters; the V024 back-fill has no input | `ReviewDecisionRepository`, `UserRoleRepository`, `RoleDelegationRepository`, V024 |
| A04 Insecure design | `NO_REVIEWER` counts only holders and delegates who may sign in, never the award's owner; the review period must be positive | `ReviewerAvailability`, `WorkflowProperties` |
| A07 Authentication failures | Unchanged tokens of Feature 1.1; polling goes through the silent refresh and stops on an ended session | Feature 1.1, `award-status.component` |

### Findings

Scenario review of the untested detours and the refactor sweep; F-1…F-6 fixed in 2.3.2 (SCRUM-31, #101).

| # | Finding | Fix |
|---|---------|-----|
| F-1 | Deadlines and due dates add 72 hours, so a review period across a daylight-saving change ends on the wrong Kyiv date (a submission at 00:30 on 2026-10-23 shows 10-25 instead of 10-26) | Periods added as Kyiv calendar days; tests across both changes |
| F-2 | A returned request shows only its level on the home card and in the award list; the owner learns that she has to act only on the award page | Both say «Очікує ваших виправлень» |
| F-3 | After a 403 or 404 the panel keeps the old path, decisions and reviewer comments under «Ця нагорода вам більше не доступна» | The timeline is cleared |
| F-4 | An overdue request shows its past deadline as the current level's «Очікується до» next to the new date of the delay notice | The current level shows the revised date |
| F-5 | A change of the delay reason (`NO_REVIEWER` to `REVIEW_OVERDUE`) updates the panel without the announcement | The reason counts as a change |
| F-6 | A request without a stored deadline answers `deadline: null` with `overdue: true` | The response carries the deadline the estimate uses |

Refactor sweep, applied in 2.3.2: one set of level labels (`roles.*`, `awards.levels.*`) and one «Очікується до» key instead of the panel's copies; the panel maps 403 and 404 like the paged lists of 2.2 (`shared/paged-list.ts`); one pending check in the panel; `ApprovalLevel.role()` instead of `RoleType.valueOf(level.name())`; request and decision row builders next to `support/AwardRows` for the FT, IT and E2E fixtures. Left in the tracker's technical notes: a shared timing chip and a Kyiv date pipe for the list, the card and the panel; the latency FT of AC-1.10 as a timing assertion; the status panel reads the award id once, so a link from one award page to another would need an input change handler.

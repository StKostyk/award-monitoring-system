# Feature 4.1: Workflow Engine Core

> **Epic**: 4 — Approval Workflow Engine (SCRUM-47)
> **Sprint**: 4–5 (2026-10-06 → 2026-10-18)
> **Points**: 29 (five stories)
> **Status**: Done 2026-10-08, pending the manual run of §9 (validation in §12)
> **Author**: Stefan Kostyk
> **Governing docs**: roadmap § Feature 4.1, US-004 (batch review, DoD), US-005 (status for the owner), DATA_DICTIONARY §2.1, §3.1, §3.2, V007, V008, V024, V026, state-machine-award-request.puml, bpmn-approval-workflow.puml, sequence-approval-workflow.puml, RBAC_matrix.md, AUTHENTICATION_AUTHORIZATION §3 (scopes, delegation, "delegated by" stamp), RACI rows 27–29, ADR-006 addendum, ADR-022, openapi.yml `/awards/{id}/approve|reject|return`, `RolePermissions`, `RecognitionLevel`, EPIC-04 tracker (decisions of 2026-10-05, deviations 2, 3, 5, 6)

## 1. Problem and personas

Submitted awards wait in `award_requests` with nobody to act on them: no reviewer can find them, claim one, decide or explain a decision. Secretaries and deans see only their own awards (manual run 2026-10-04, N-5), and an owner who notices a mistake right after submitting cannot take the award back. Faculties and departments also receive awards, but the model has only personal recipients. This feature adds unit recipients, the reviewer's queue, decisions at every level, withdrawal and resubmission, and batch decisions with template responses (US-004).

| Persona | Need in this feature |
|---------|----------------------|
| Alina, faculty secretary | One queue of her faculty's requests, claimed so a colleague does not review the same one; approve, return or reject with a comment, pass a request to the dean; 15–20 requests a month decided in one batch |
| Prof. Martynyuk, dean | His level's queue, the faculty-level requests he may take over, unit awards of his faculty |
| Rector's secretary | National and international awards after the faculty levels |
| Anastasia, employee | Take back an award submitted by mistake while nobody has opened it; fix a returned award and submit it again; an e-mail about every decision |
| Dmytro, GDPR officer | Every claim, hand-over, decision and withdrawal is recorded with who did it and on whose behalf |

## 2. Scope

**In (this feature)**

- Unit recipients: a faculty secretary or dean enters an award for their faculty or one of its departments (V027)
- Reviewer queue `GET /api/v1/reviews`: requests the caller may review, filters, overdue first
- Claim, release, hand-over to a peer, take-over by a higher level; an optimistic version on `award_requests` (V028)
- Decisions `POST /api/v1/awards/{id}/decisions`: approve (final or to the next level), reject, return, escalate one level up; comments; the verification badge on approval
- Decision e-mails to the owner (uk, en) through the existing after-commit mail listener
- Withdrawal of an unclaimed request back to a draft; resubmission of a returned or withdrawn award (V030)
- Batch decisions over up to 50 requests, one transaction per item, with template responses (V031)
- Queue page `/reviews`, review panel on the award page, «Відкликати», returned-award banner in the form
- ADR-023 (transition table), redrawn request state machine, OpenAPI contract for all of the above

**Out (where it goes)**

- Per-faculty review period, overdue notice and the «Ескальовано» mark, SLA metrics — Feature 4.2 (4.2.1, 4.2.2); this feature keeps the global period of 2.1.8
- Reviewers correcting an award instead of returning it — 2.4.1 (Feature 4.2)
- Colleague, unit and public achievement pages — Feature 4.3
- In-app and push notifications, notification preferences — Epic 7 (1.3.2)
- Appeals after a rejection, workflow types "expedited", "appeal", "exception", automatic expiry (`EXPIRED`) — not planned (tracker deviation 2)
- Editing template responses in the UI — Epic 6 system configuration; seeded by migration here
- Unit awards for the whole university (entered by the rector's office) — not requested; faculty and department only
- Separate unit-award counts in analytics — Epic 5 (the API exposes the recipient type from 4.1.0)

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-48 (#142) | 4.1.0 Organisational awards: unit recipients and submitter | 5 | no | PR #152 merged |
| SCRUM-49 (#143) | 4.1.1 Reviewer queue with claim, release and hand-over | 8 | yes | 4.1.0 |
| SCRUM-50 (#144) | 4.1.2 Review decisions: approve, reject, return, escalate | 8 | yes | 4.1.1 |
| SCRUM-51 (#145) | 4.1.3 Withdraw an unclaimed request and resubmit a returned award | 3 | no | 4.1.2 (return) |
| SCRUM-52 (#146) | 4.1.4 Batch review with template responses | 5 | yes | 4.1.2 |

`parallel: yes` means that the UI can be built from the OpenAPI contract of the story's first commit while the backend is in progress. 4.1.3 and 4.1.4 are independent of each other.

## 4. Acceptance criteria

The **reviewer rule** used below: a caller may review a request at level L when they hold a role, their own or delegated, whose approval level is L or higher (`FACULTY_SECRETARY` < `DEAN` < `RECTOR_SECRETARY` < `RECTOR`), its scope covers the award's `organization_id`, and they are neither the award's owner nor the request's submitter. **Own level** means the role's level equals L. **Eligible** adds: the account can sign in and the role or delegation is in effect today (`ReviewerAvailability`).

### 4.1.0 Organisational awards (SCRUM-48)

- **AC-0.1** Given a faculty secretary or dean (own or delegated role), when they call `GET /api/v1/awards/recipient-units`, then 200 with the faculties and departments their scopes cover (`id`, `name`, `nameUk`, `type`), faculties first and departments alphabetically after; for anyone else, 200 with an empty list.
- **AC-0.2** Given such a caller, when they create or save a draft with `recipientOrganizationId` set to one of those units, then the award stores `recipient_org_id`, `user_id` is the caller (owner = submitter) and `organization_id` is the unit; with `recipientOrganizationId` null the award is personal exactly as before.
- **AC-0.3** Given a unit outside the caller's scopes, an organisation that is neither a faculty nor a department, an inactive unit, or a caller without either role, then 422 `validation-failed` on `recipientOrganizationId`, nothing saved.
- **AC-0.4** Given a unit draft, when it is submitted, then the scope is checked again (a lost role → 422 `recipient-out-of-scope`), `organization_id` stays the unit (it is not replaced by the submitter's department), `award_requests.submitter_id` is the caller and the request starts by the start-level rule (AC-0.6).
- **AC-0.5** Given a unit draft, then the duplicate check of 2.1.2 compares it with the awards of the same unit (same `award_date`, title similarity ≥ 0.6), not with the submitter's personal awards, and personal drafts are never compared with unit awards.
- **AC-0.6** Given any submission, then the request starts at `FACULTY_SECRETARY`; a level whose only eligible reviewers are the owner or submitter is passed over and the request starts at the next level (a faculty secretary's own unit or personal award starts at `DEAN` when she is the faculty's only secretary). A level with no eligible reviewer at all is not passed over (the status page keeps showing `NO_REVIEWER`).
- **AC-0.7** Given `Award` responses (single, list, export), then they carry `recipient`: `{ "type": "PERSON" }` or `{ "type": "UNIT", "organization": { id, name, nameUk } }`; «Мої нагороди» lists the caller's unit awards with the unit's name as a chip; the award page shows «Отримувач: <unit>».
- **AC-0.8** Given the award form as a faculty secretary or dean, then a «Отримувач» choice shows «Я» (default) and «Підрозділ» with a unit picker from AC-0.1; for other users the choice is not shown. Switching back to «Я» clears the unit.
- **AC-0.9** Given the submitter's GDPR export, then their unit awards are included with the recipient unit; the unit award is not part of anyone else's export.
- **AC-0.10** Given readers of a submitted unit award, then the rule of Feature 2.1 applies to its `organization_id` (the unit): holders of `award:read:*` scopes covering the unit read it; the owner reads it in every status.

### 4.1.1 Reviewer queue with claim, release and hand-over (SCRUM-49)

- **AC-1.1** Given a reviewer, when they call `GET /api/v1/reviews`, then 200 with a page (`page`, `size` ≤ 100, default 20) of the open requests (`SUBMITTED`, `IN_REVIEW`, `ESCALATED`) at their own level within their scopes, excluding awards they own or submitted; each `ReviewItem` has `awardId`, `requestId`, `requestVersion`, titles, `recipient`, owner (`UserRef`), `organization`, category and recognition level, `level`, `status`, `reviewer` (`UserRef` or null), `submittedAt`, `deadline`, `overdue`, `documentCount`, `delegatedFrom` (the delegator's `UserRef` when only a delegation grants access).
- **AC-1.2** Given filters `assigned=me|unassigned|others`, `level=<level>` (any level the caller may review, lower levels included), `organizationId=<id inside a scope>` and `overdue=true`, then only matching items; default order: overdue first, then `deadline` ascending, then `requestId`; an out-of-scope `organizationId` or a level above the caller's → 400.
- **AC-1.3** Given a caller without any approval role (own or delegated), then 403; given a caller with a role but nothing to review, then 200 with an empty page.
- **AC-1.4** Given an unclaimed request the caller may review, when they call `PUT /api/v1/awards/{id}/reviewer` with `{ "requestVersion": n }` (no `reviewerId`), then 200 with the request: `current_reviewer_id` = caller, status `IN_REVIEW`, version + 1; an `audit_logs` row `REVIEW_CLAIMED` with the request, level and, for a delegate, `delegatorId`. Claiming a request one already holds answers 200 without a change.
- **AC-1.5** Given a request claimed by someone else, then the claim answers 409 `request-claimed` with the reviewer's `UserRef`; given a stale `requestVersion`, then 409 `request-stale` with the current version; two claims at once → exactly one 200, the other 409.
- **AC-1.6** Given a request at level L claimed by a peer, when a caller whose role level is above L (a dean on a faculty-secretary request) sends `{ "requestVersion": n, "takeOver": true }`, then 200, the caller is the reviewer, `REVIEW_TAKEN_OVER` names the previous reviewer; a caller at level L gets 409 `request-claimed` with or without `takeOver`, unless the current reviewer is no longer eligible (role ended, delegation over, account blocked), when a peer may take over too.
- **AC-1.7** Given the reviewer of a request, when they call `DELETE /api/v1/awards/{id}/reviewer?requestVersion=n`, then 204, reviewer null, status back to `ESCALATED` when the request reached its level through a decision and `SUBMITTED` otherwise; `REVIEW_RELEASED`. Anyone but the reviewer → 409 `request-claimed`.
- **AC-1.8** Given the reviewer, when they call `GET /api/v1/awards/{id}/reviewers`, then 200 with the eligible reviewers at the request's level for its organisation other than themselves, owner and submitter (`UserRef` + `delegated` flag); `PUT …/reviewer` with `{ "reviewerId": x, "requestVersion": n }` hands it over: 200, reviewer = x, `REVIEW_HANDED_OVER`; x not in that list → 422 `reviewer-not-eligible`.
- **AC-1.9** Given an award that is not pending, an unknown id, or an award the caller may not review, then the claim, release, hand-over and candidate calls answer 404 (the caller's own award: 404 as well, it is never in their queue); a request in a final state → 409 `request-closed`.
- **AC-1.10** Given the queue page `/reviews` («На розгляді», side-nav entry for every approver), then a table with title, recipient, faculty or department, category level, level, submitted date, deadline (Kyiv date, «Прострочено» chip when overdue), reviewer; tabs «Мої» (claimed by me), «Нерозподілені», «Усі»; filter by level and unit; paging; row click opens the award page.
- **AC-1.11** Given the award page of a request the caller may review, then a «Розгляд» panel shows the reviewer and the deadline with «Взяти в роботу», «Звільнити», «Передати колезі…» (dialog with the AC-1.8 list) or «Взяти на себе» (take-over, with a confirmation naming the current reviewer); a 409 shows «Нагороду вже взяв у роботу <name>» or «Дані застаріли, сторінку оновлено» and reloads.
- **AC-1.12** Given the demo and local seeds, then a second faculty secretary (`secretary2.fmi`, faculty 9) and a rector's secretary (`rector.secretary`, university) exist with the demo password.
- **AC-1.13** Given English, then the queue page, panel, dialogs and errors are in English; the page passes the axe check of the E2E run and works at 360 px (cards instead of the table).

### 4.1.2 Review decisions (SCRUM-50)

- **AC-2.1** Given a request the caller holds, or an unclaimed one they may review (it is claimed in the same transaction), when they call `POST /api/v1/awards/{id}/decisions` with `{ "decision": "APPROVE", "requestVersion": n, "comment"?: … }` and the level is at or above the category's minimum approval level, then 200 with the outcome: request `APPROVED`, `completed_at` set, reviewer kept, award `APPROVED`, a `review_decisions` row `APPROVED` at that level.
- **AC-2.2** Given an approval below the category's minimum approval level (a national award approved by a faculty secretary), then the request moves to the next level not passed over by AC-0.6 with status `ESCALATED`, reviewer null, a new `deadline` (one review period from now), award stays `PENDING`; the decision row is `APPROVED` at the old level.
- **AC-2.3** Given `REJECT` with a non-blank `comment` (≤ 2000 characters), then request `REJECTED` with `rejection_reason` = comment and `completed_at`, award `REJECTED`, decision row `REJECTED`; without a comment → 422 `validation-failed` on `comment`.
- **AC-2.4** Given `RETURN` with a non-blank `comment`, then request `RETURNED`, reviewer null, deadline null; award back to `DRAFT` (editable by its owner through the existing form, readable only by the owner until resubmitted); decision row `RETURNED`; without a comment → 422.
- **AC-2.5** Given `ESCALATE` (optional comment) at any level below `RECTOR`, then the request moves one level up (and past levels passed over by AC-0.6) with status `ESCALATED`, reviewer null, a new deadline; decision row `ESCALATED`; at `RECTOR` → 409 `no-higher-level`.
- **AC-2.6** Given `APPROVE` with `"verified": true` on an award with at least one document, then `awards.verification_badge` becomes true (any level); `verified` on an award without documents → 422.
- **AC-2.7** Given a delegate who decides, then the decision row has `delegator_id` = the delegator (V028) and the status timeline shows «<delegate> (за дорученням <delegator>)»; a holder of an own role at that level and a delegation is recorded under the own role.
- **AC-2.8** Given a request claimed by someone else → 409 `request-claimed`; stale `requestVersion` → 409 `request-stale`; final request → 409 `request-closed`; unknown or not reviewable (own award included) → 404; caller without an approval role → 403; unknown `decision` → 400.
- **AC-2.9** Given any decision, then an `audit_logs` row `REVIEW_DECISION` with request, level, decision, the new level or status and `delegatorId`; the award's history (2.2) records the status change.
- **AC-2.10** Given any decision, then after the commit the owner gets one e-mail in their language: «Нагороду затверджено», «… передано на наступний рівень розгляду» (approval below the minimum level and escalation), «… повернуто на доопрацювання» with the comment, «… відхилено» with the comment; each links to the award page. A rollback sends nothing; a mail failure does not undo the decision.
- **AC-2.11** Given the award page panel of AC-1.11, then «Затвердити», «Повернути на доопрацювання», «Відхилити» and «Передати <next level>» open a dialog with a comment field (required for return and reject), «Документи перевірено» on approval when documents exist, and the result («Нагороду затверджено», «Передано декану» …); the page then shows the new status and timeline entry.
- **AC-2.12** Given a decided request, then `GET /awards/{id}/status` (2.3) shows the decision with the reviewer, the comment for return, rejection and escalation, and the new level; expected completion follows the new level.
- **AC-2.13** Given ADR-023, the redrawn `state-machine-award-request.puml` and DATA_DICTIONARY §3.1–3.2, then they describe exactly the transitions of this story and 4.1.3 (table in §7.2); `openapi.yml` drops the planned `/approve`, `/reject`, `/return` paths for `/decisions`.

### 4.1.3 Withdraw and resubmit (SCRUM-51)

- **AC-3.1** Given the owner of a pending award whose request is unclaimed (`SUBMITTED` or `ESCALATED`, reviewer null), when they call `POST /api/v1/awards/{id}/withdraw` with `{ "version": v }` (award version), then 200 with the award: status `DRAFT`, request `WITHDRAWN` (V030), deadline null; `AWARD_WITHDRAWN` in `audit_logs`; the request leaves every queue.
- **AC-3.2** Given a claimed request → 409 `request-claimed` («Нагороду вже розглядає рецензент, відкликати її не можна»); a final or returned request, or a draft → 409 `award-not-pending`; someone else's award → 404; stale version → 409 `award-stale`. Withdrawal and claim lock the same request row: one of them wins.
- **AC-3.3** Given a withdrawn award, when the owner submits it again (`POST /awards/{id}/submit`), then the existing request is reused: status `SUBMITTED`, `submitted_at` now, new deadline, starting level by AC-0.6 from `FACULTY_SECRETARY`; earlier decisions stay on the timeline.
- **AC-3.4** Given a returned award, when the owner submits it again, then the request becomes `SUBMITTED` at the level that returned it (or the next level not passed over), reviewer null, new deadline; the timeline shows «Подано повторно».
- **AC-3.5** Given a returned award, then the owner may change its fields and add or remove documents as in any draft; the form shows the banner «Рецензент повернув нагороду на доопрацювання: <comment>» until it is submitted again.
- **AC-3.6** Given the award page of the owner's pending award with an unclaimed request, then «Відкликати» asks «Відкликати нагороду? Вона знову стане чернеткою» and opens the form after withdrawal; when the request is claimed, the button is absent; a 409 shows the message of AC-3.2 and reloads.

### 4.1.4 Batch review with template responses (SCRUM-52)

- **AC-4.1** Given a reviewer, when they call `POST /api/v1/reviews/decisions` with `{ "decision", "comment"?, "verified"?: false, "items": [{ "awardId", "requestVersion" }] }` (1–50 items, distinct award ids), then 200 with one result per item in the order sent: `{ awardId, outcome: "DONE" | "FAILED", code?, detail?, status?, level? }`; every item runs the single decision of 4.1.2 in its own transaction, so a failed item never undoes the others.
- **AC-4.2** Given items that are claimed by others, stale, closed, not reviewable or missing, then those items are `FAILED` with the codes of AC-2.8 (`not-found` for 404) and the rest are decided; an empty list, over 50 items or duplicate ids → 400 for the whole request; a missing comment for `RETURN`/`REJECT` → 422 for the whole request.
- **AC-4.3** Given a batch, then one `audit_logs` row `REVIEW_BATCH` with the decision, item count, done and failed counts, plus the per-item rows of AC-2.9; each decided owner gets the e-mail of AC-2.10.
- **AC-4.4** Given 20 items, then the batch answers in under 5 s in the functional test (US-004: < 30 s).
- **AC-4.5** Given `GET /api/v1/reviews/templates?decision=RETURN`, then 200 with the active templates for that decision (`id`, `decision`, `title`, `body` in the caller's language with the other language as fallback); the seed has at least three templates for return, two for rejection, one for escalation and one for approval.
- **AC-4.6** Given the queue page, then each row has a checkbox, the header selects the page; with a selection an action bar shows «Затвердити (n)», «Повернути», «Відхилити», «Передати декану» (only when all selected items are at the faculty-secretary level; otherwise «Передати на вищий рівень»); the dialog offers «Шаблон відповіді» that fills the comment, which stays editable.
- **AC-4.7** Given a batch result with failures, then a summary «Опрацьовано: 18 з 20» lists the failed awards with their reasons and a link to each; decided rows leave the list; the selection keeps only the failed items.
- **AC-4.8** Given English, keyboard use (checkbox, action bar and dialog reachable by Tab, Space toggles a row) and the axe check, then the batch flow passes.

## 5. Edge cases

| Case | Expected |
|------|----------|
| Two secretaries claim the same request at once | One 200, one 409 `request-claimed` (row lock + version) |
| Decision sent while a colleague takes the request over | The second writer gets 409 `request-stale`; nothing is decided twice |
| Owner withdraws while a secretary claims | Both lock the request row; one wins: a late withdrawal answers 409 `request-claimed`, a late claim 404 (the request left the queue) |
| Reviewer loses the role (or the delegation ends) while holding a claim | The claim stays visible to peers as «Взято в роботу: <name>»; a decision by them answers 404; a peer or the dean takes it over (AC-1.6 allows a peer take-over when the holder is no longer eligible) |
| Delegate and delegator both review the same faculty | Either may claim; a decision records `delegator_id` only for the delegate |
| Secretary's own award, she is the only secretary | Starts at `DEAN` (AC-0.6); not in her queue |
| Dean submits a unit award for the faculty | Faculty-secretary level reviews it; the dean never sees it in his queue (own submission) |
| National award approved at the faculty and dean levels | Reaches `RECTOR_SECRETARY` as `ESCALATED`; the rector's secretary's approval is final |
| Category changed to a lower level after a return | Resubmission at the returning level; that level is at or above the new minimum, so its approval is final |
| Request at a level with no eligible reviewer (vacancy) | Stays there, `NO_REVIEWER` on the status page; a higher level may take it via the `level` filter |
| Owner deletes a returned or withdrawn draft | Deleting a returned draft is refused with 409 `award-has-request` (its request and decisions are history) |
| Batch with an item the caller just decided singly | That item `FAILED` `request-stale` or `request-closed` |
| Batch interrupted by a backend restart | Items committed before the restart stay decided; the client sees a network error and reloads the queue |
| Owner's account deactivated while pending | The request continues; the e-mail goes to the stored address or is skipped for an erased account |
| Award edited by its owner while pending | Impossible (only drafts are editable); returns and withdrawals make it a draft |
| Mail server down at decision time | Decision committed; the listener logs the failure (unchanged Epic 1 behaviour) |

## 6. Dependencies

### Tables

| Table | Change |
|-------|--------|
| `awards` (V027) | `recipient_org_id BIGINT NULL` FK → `organizations`, partial index `(recipient_org_id, award_date)` where not null; `user_id` reads "owner: the recipient of a personal award, the submitter of a unit award" |
| `award_requests` (V028) | `version BIGINT NOT NULL DEFAULT 0`; index `(current_level, status, deadline)` for open requests if the plan check of the queue needs it |
| `review_decisions` (V028) | `delegator_id BIGINT NULL` FK → `users` (tracker deviation 5) |
| `award_versions` (V029) | `ck_award_versions_action` widened with `DECIDED` (4.1.2, award history of AC-2.9) |
| `award_requests` (V030) | `ck_award_requests_status` recreated with `WITHDRAWN` |
| `review_templates` (V031, new) | `template_id`, `decision`, `title_uk`, `title_en`, `body_uk`, `body_en`, `sort_order`, `active`; seeded; audit trigger |
| `audit_logs` | New actions `REVIEW_CLAIMED`, `REVIEW_RELEASED`, `REVIEW_HANDED_OVER`, `REVIEW_TAKEN_OVER`, `REVIEW_DECISION`, `REVIEW_BATCH`, `AWARD_WITHDRAWN` |

### Endpoints

| Endpoint | Change | Access |
|----------|--------|--------|
| `GET /api/v1/awards/recipient-units` | New (4.1.0) | Any signed-in user; list empty without a FS/DEAN role |
| `POST`/`PUT /api/v1/awards…` | `recipientOrganizationId` in `AwardForm`, `recipient` in `Award` (4.1.0) | Unchanged |
| `GET /api/v1/reviews` | New (4.1.1) | An approval role, own or delegated |
| `PUT`/`DELETE /api/v1/awards/{id}/reviewer`, `GET /api/v1/awards/{id}/reviewers` | New (4.1.1) | Reviewer rule |
| `POST /api/v1/awards/{id}/decisions` | New, replaces the planned `/approve`, `/reject`, `/return` (4.1.2) | Reviewer rule |
| `POST /api/v1/awards/{id}/withdraw` | New (4.1.3) | Owner, `award:update:own` |
| `POST /api/v1/awards/{id}/submit` | Resubmission described (4.1.3) | Unchanged |
| `POST /api/v1/reviews/decisions`, `GET /api/v1/reviews/templates` | New (4.1.4) | An approval role |
| `GET /api/v1/awards/{id}/status` | Decisions with comment and "on behalf of"; resubmission step (4.1.2, 4.1.3) | Unchanged |

### Services

- 4.1.0: `RecipientUnits` (scopes → units), `AwardOwnership`/`AwardSubmission` (unit rules), `StartLevel` (AC-0.6, reused by moves up), `DuplicateFinder` (per unit)
- 4.1.1: `ReviewerRule` (who may review a request; built on `AccessScope`, `OrganizationTree`, `ReviewerAvailability`), `ReviewQueue` (specification query, paging), `ReviewAssignment` (claim, release, hand-over, take-over)
- 4.1.2: `Transitions` (the table of §7.2), `ReviewDecisions`, `DecisionMails` (after-commit listener, templates uk/en), `AwardStatusService` (on-behalf and comments)
- 4.1.3: `AwardWithdrawal`, `AwardSubmission` (reuse of the request)
- 4.1.4: `BatchReview` (calls `ReviewDecisions` through `TransactionTemplate` per item), `ReviewTemplates`

### Frontend

- `features/awards/award-form`: «Отримувач» (4.1.0), returned banner (4.1.3)
- `features/awards/award-detail`: recipient line (4.1.0), «Розгляд» panel and decision dialog (4.1.1, 4.1.2), «Відкликати» (4.1.3)
- `features/reviews/` (new, lazy route `/reviews`, `approverGuard`): queue table and cards, filters, selection and action bar, batch dialog and result (4.1.1, 4.1.4); NgRx store per feature
- Side-nav entry «На розгляді» for approvers; i18n `uk` and `en`

### External systems

Mailpit in development, the configured SMTP relay in production (unchanged).

## 7. Technical decisions

| # | Decision | Reasoning | Source |
|---|----------|-----------|--------|
| D-1 | Transition table in the `award` module (`Transitions`): (request status, decision or action) → (new status, level move, award status); no state-machine library | Seven states, four levels, one table the tests walk row by row | Tracker 2026-10-05, ADR-023 |
| D-2 | Claim = `current_reviewer_id` + `award_requests.version` (`@Version`) + row lock; 409 on a double claim; no lapse; take-over by a higher level or when the holder is no longer eligible | Two secretaries of one faculty never review the same request; nothing is taken from anyone silently | Tracker, design review P-2 |
| D-3 | Decision on an unclaimed request claims it in the same transaction | Batch decisions need no separate claim step; a claim held by someone else still wins | US-004 |
| D-4 | Higher levels may review lower levels (claim through `level` filter, take-over); the default queue shows the own level | `award:approve:level1` is held by every approver already; the dean covers a vacancy or an absent secretary | `RolePermissions`, design review J10 H-3 |
| D-5 | Return and withdrawal make the award a `DRAFT` again and keep the request (`RETURNED`, `WITHDRAWN`); resubmission reuses the request | One edit path (the draft form, documents included); `documents.request_id` stays unused; the timeline keeps every decision | — |
| D-6 | Resubmission after a return goes to the returning level; after a withdrawal it starts from the beginning | The reviewer who asked for changes checks them; a withdrawal may have happened after a lower-level approval of other content | — |
| D-7 | Rejection is final; no appeal and no `REJECTED → DRAFT` | No appeal process is defined; the owner may enter a corrected award again | Tracker deviation 2 |
| D-8 | One endpoint per decision kind collapsed into `POST /awards/{id}/decisions` with a `decision` field; batch takes the same body over items | The single and batch paths share validation and results; a fourth decision (escalate) needs no new path | Tracker deviation 3 |
| D-9 | Batch: up to 50 items, each in its own transaction, 200 with per-item results (no 207) | US-004 additional scenario; one status code keeps the client simple | Tracker 2026-10-05 |
| D-10 | Decision e-mails through the existing after-commit mail listener; no Modulith, no publication registry | ADR-006 addendum and ADR-022 step 2 place them at Epic 7 | Design review P-4 |
| D-11 | Template responses in a seeded table, read-only through the API | Editable later by Epic 6 configuration without a code change; reviewers can still edit the filled-in text | Roadmap task "template responses" |
| D-12 | Ids in workflow paths are award ids (1:1 with requests); the request version travels as `requestVersion` | The UI and the status page already work with award ids | — |
| D-13 | Reviewable-but-not-yours and unknown → 404; own award → 404 | Same rule as Feature 2.1 (J11: 404 over 403) | Design review J11 |

### 7.1 Organisational awards (design note for 4.1.0)

- **Model.** `awards.recipient_org_id` null → personal award (recipient = owner). Not null → unit award; the owner (`user_id`) is the person who entered and submitted it and keeps the draft rights; there is no separate `submitted_by` column because `user_id` and `award_requests.submitter_id` already hold it.
- **Who.** Holders of `FACULTY_SECRETARY` or `DEAN` (own or delegated) for units their scope covers, of type `FACULTY` or `DEPARTMENT`. No new permission: the check uses the role scopes the token already carries; `RBAC_matrix.md` gets a row "Submit an award for a unit".
- **Organisation.** `organization_id` of a unit award is the unit, so routing (`ReviewerAvailability`), scoped reads and the queue follow the unit. Personal awards keep "owner's department at submission".
- **Never self-reviewed.** The reviewer rule excludes owner and submitter; the start-level rule (AC-0.6) passes over a level the submitter alone would review.
- **Rejected alternatives.** A separate `recipient_type` column (redundant with a nullable FK); a recipient user *or* unit with a third "submitted by" column (two ownership rules for editing and GDPR export); a `units` table (organisations already model faculties and departments).
- **GDPR.** A unit award is the submitter's processing record: it appears in their export; erasure of the submitter (Epic 6) pseudonymises the owner and keeps the unit award.

### 7.2 Transition table (ADR-023)

| From (request) | Action | To (request) | Level | Award |
|----------------|--------|--------------|-------|-------|
| — | submit | `SUBMITTED` | start level (AC-0.6) | `PENDING` |
| `SUBMITTED`, `ESCALATED` | claim | `IN_REVIEW` | = | = |
| `IN_REVIEW` | release | `SUBMITTED` or `ESCALATED` (AC-1.7) | = | = |
| `IN_REVIEW` | hand-over, take-over | `IN_REVIEW` | = | = |
| `SUBMITTED`, `ESCALATED`, `IN_REVIEW` | approve, level ≥ minimum | `APPROVED` | = | `APPROVED` |
| same | approve, level < minimum | `ESCALATED` | next not passed over | = |
| same | escalate (level < `RECTOR`) | `ESCALATED` | next not passed over | = |
| same | return | `RETURNED` | = | `DRAFT` |
| same | reject | `REJECTED` | = | `REJECTED` |
| `SUBMITTED`, `ESCALATED` (no reviewer) | withdraw | `WITHDRAWN` | = | `DRAFT` |
| `RETURNED` | resubmit | `SUBMITTED` | = (or next not passed over) | `PENDING` |
| `WITHDRAWN` | resubmit | `SUBMITTED` | start level | `PENDING` |

`APPROVED`, `REJECTED` are final; `EXPIRED` stays in the check constraint, unused (tracker deviation 2).

**Proposed deviations from the docs** (applied in the PR of the story that touches them):

1. **openapi.yml**: `/awards/{id}/approve|reject|return` (planned, returning `Award`) replaced by `/awards/{id}/decisions`; new paths of §6; `ApprovalRequest`, `RejectionRequest`, `ReturnRequest` replaced by `ReviewDecision` (tracker deviation 3). — 4.1.1 (queue, reviewer), 4.1.2 (decisions), 4.1.3, 4.1.4
2. **State machine**: redrawn from §7.2; `IN_REVIEW → EXPIRED`, `REJECTED → DRAFT`, the appeal window and the document/OCR branch removed; `WITHDRAWN` added (tracker deviation 2). — 4.1.2, `WITHDRAWN` in 4.1.3
3. **DATA_DICTIONARY**: §2.1 `recipient_org_id`, owner meaning, award `PENDING → DRAFT` on return and withdrawal, no `REJECTED → DRAFT`; §3.1 `version`, `WITHDRAWN`, request status transitions of §7.2, `EXPIRED` unused, `documents` relation unused; §3.2 `delegator_id`, escalation at every level below the rector; new `review_templates`. — per story
4. **AUTHENTICATION_AUTHORIZATION**: the "delegated by" stamp is `review_decisions.delegator_id` (tracker deviation 5). — 4.1.2
5. **RACI matrix**, row "Escalate approval decisions": reviewers (R) escalate one level up, the next level's holder (A) decides; the project lead is informed (tracker deviation 6). — 4.1.2
6. **RBAC_matrix.md**: rows "Submit an award for a unit", "Claim and hand over a review", "Decide at a level (own or lower)", "Batch decisions", "Withdraw own unclaimed award". — per story
7. **BPMN and sequence diagrams**: the claim step and batch path added; automatic escalation removed (the 4.2.2 notice is drawn there). — 4.1.2

**Assumptions**

- A1. Faculty secretaries hold their role at faculty level (seed: org 9); department-level secretaries do not exist.
- A2. A returned award is visible only to its owner until resubmitted; the returning reviewer finds it again in the queue after resubmission.
- A3. One e-mail per decision is acceptable until notification preferences (1.3.2, Epic 7); a batch of 20 sends 20 e-mails, one per owner and award.
- A4. The overdue «Ескальовано» mark of 4.2.2 is stored apart from the request status; `ESCALATED` keeps the dictionary meaning "moved to a higher level, waiting to be claimed".
- A5. Template texts are written for the seed (three return reasons: missing certificate, wrong category, unreadable scan; two rejection reasons; one escalation; one approval).
- A6. A unit award entered by a dean is reviewed by the faculty secretary like any other request; the dean's role does not shortcut it.

## 8. Test plan

| AC | Unit | Slice | IT | FT | E2E |
|----|------|-------|----|----|-----|
| 0.1–0.3 | ✓ `RecipientUnits` from scopes and delegations | ✓ 422 field | | ✓ secretary lists units; out-of-scope unit 422; employee empty list | |
| 0.4, 0.6 | ✓ start level (table: only secretary, two secretaries, vacancy, dean submits) | | ✓ submission of a unit award with the seed tree | ✓ secretary's own award starts at `DEAN` | |
| 0.5 | ✓ duplicate per unit | | ✓ trigram query per unit | | |
| 0.7–0.8 | ✓ component: recipient choice, chip | | | ✓ `recipient` in responses | ✓ secretary enters a department award and submits it |
| 0.9, 0.10 | | | | ✓ export contains it; dean reads it; other faculty 404 | |
| 1.1–1.3 | ✓ reviewer rule (own, delegated, higher level, owner, submitter) | ✓ 400 filters, 403 | ✓ queue query plan with seed data (index use) | ✓ queue per role and filter | |
| 1.4–1.9 | ✓ assignment transitions | ✓ 404/409 mapping | ✓ concurrent claims → one 409; audit rows | ✓ claim, release, hand-over, take-over, stale | |
| 1.10–1.13 | ✓ components: tabs, panel buttons per state, 409 messages | | | | ✓ two secretaries: claim, conflict message, hand-over; 360 px; English; axe |
| 2.1–2.6 | ✓ `Transitions` row by row; minimum level; badge | ✓ 422 comment | ✓ decision + history + audit rows in one transaction | ✓ every decision kind; national award through three levels | |
| 2.7 | ✓ delegator stamp | | | ✓ delegate decides; timeline text | |
| 2.8, 2.9 | | ✓ error mapping | ✓ audit row | ✓ codes | |
| 2.10 | ✓ templates rendered uk/en | | ✓ mail after commit, none after rollback (Mailpit container) | | |
| 2.11, 2.12 | ✓ dialog validation, result | | | ✓ status timeline after decisions | ✓ secretary returns, owner sees the comment; dean approves |
| 2.13 | | | | | `puml-check.ps1`; OpenAPI diff in review |
| 3.1–3.4 | ✓ withdrawal rules; resubmission level | | ✓ withdrawal vs claim race → one 409 | ✓ withdraw, resubmit, return → resubmit at the returning level | |
| 3.5, 3.6 | ✓ banner, button visibility | | | | ✓ withdraw → edit → submit |
| 4.1–4.3 | ✓ per-item results, validation of the whole request | ✓ 400 sizes | ✓ one failing item does not roll back the others; batch audit row | ✓ mixed batch | |
| 4.4 | | | | ✓ 20 items < 5 s | |
| 4.5 | ✓ language fallback | | ✓ seed rows | ✓ list per decision | |
| 4.6–4.8 | ✓ selection, action bar labels, result summary | | | | ✓ batch of five with one claimed elsewhere; English; keyboard; axe |

Coverage target 85 % lines per `mvn verify`; static analysis clean. Every story changes access rules or migrations, so the security review agent runs on each diff (US-004 DoD "security review for bulk operations").

## 9. Manual verification

Preconditions: `docker compose up -d postgres redis mailpit minio clamav`, backend `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` on `http://localhost:8080`, frontend `npm start` on `http://localhost:4200`. Seed accounts (password `Passw0rd-demo`): `employee.fmi@chnu.edu.ua` (department 64 of faculty 9), `secretary.fmi`, `secretary2.fmi` (after 4.1.1), `dean.fmi`, `rector.secretary` (after 4.1.1), `rector`, `admin` (all `@chnu.edu.ua`). Mailpit `http://localhost:8025`; Swagger `http://localhost:8080/swagger-ui.html`; psql `docker compose exec postgres psql -U postgres award_monitoring`. Prepare as `employee.fmi`: three submitted awards A (faculty level), B (faculty level), N (national level, category «Державна премія…» or any `NATIONAL` category).

1. As `secretary.fmi` open `http://localhost:4200/awards/new`. Expected: «Отримувач» with «Я» and «Підрозділ»; the picker lists faculty 9 and its departments only. Choose a department, fill in and submit. Expected: the award page shows «Отримувач: <department>»; «Мої нагороди» shows the unit chip. (AC-0.1, 0.2, 0.7, 0.8)
2. As `employee.fmi` open `/awards/new`. Expected: no «Отримувач». Swagger: `POST /api/v1/awards` with `recipientOrganizationId` = 64 → 422. (AC-0.3)
3. psql `select a.award_id, a.user_id, a.organization_id, a.recipient_org_id, r.current_level, r.submitter_id from awards a join award_requests r using (award_id) order by a.award_id desc limit 1;` Expected: `organization_id` = `recipient_org_id` = the department, level `FACULTY_SECRETARY` (two secretaries after 4.1.1) or `DEAN` (before 4.1.1, one secretary). (AC-0.4, 0.6)
4. As `secretary2.fmi` open `http://localhost:4200/reviews`. Expected: «Нерозподілені» lists A, B, N and the unit award of step 1; `secretary.fmi`'s queue lists A, B, N but not her own unit award. (AC-1.1, 1.10)
5. As `secretary.fmi` open A, «Взяти в роботу». Expected: panel shows her as reviewer; in `secretary2.fmi`'s queue A shows «Взято в роботу: Аліна Секретаренко». As `secretary2.fmi` open A and click «Взяти в роботу» (page opened before step 5). Expected: «Нагороду вже взяв у роботу Аліна Секретаренко», the page reloads. (AC-1.4, 1.5, 1.11)
6. As `secretary.fmi` on A: «Передати колезі…» → `secretary2.fmi`. Expected: A moves to `secretary2.fmi`'s «Мої». As `secretary2.fmi` «Звільнити». Expected: A back in «Нерозподілені» with status «Подано». psql `select action_type, new_values from audit_logs where action_type like 'REVIEW_%' order by created_at desc limit 3;` Expected: claimed, handed over, released. (AC-1.7, 1.8)
7. As `secretary.fmi` claim B; as `dean.fmi` open `/reviews`, filter level «Секретар факультету», open B, «Взяти на себе», confirm. Expected: the dean is the reviewer; `REVIEW_TAKEN_OVER` names Alina. (AC-1.6, 1.2)
8. As `secretary.fmi` open A, «Повернути на доопрацювання» without a comment. Expected: the dialog refuses. Pick the template «Додайте скан сертифіката» (after 4.1.4) or type it, confirm. Expected: «Повернуто на доопрацювання». Mailpit: an e-mail to `employee.fmi` with the comment and a link. (AC-2.4, 2.10, 2.11)
9. As `employee.fmi` open A. Expected: the form with the banner and the comment; add a document, submit. Expected: the request is back with `secretary.fmi`'s level, timeline «Повернуто…» and «Подано повторно». (AC-3.4, 3.5, 2.12)
10. As `secretary.fmi` approve A with «Документи перевірено». Expected: «Нагороду затверджено», award `APPROVED`, badge shown; Mailpit «Нагороду затверджено». (AC-2.1, 2.6, 2.10)
11. As `secretary.fmi` approve N. Expected: «Передано на наступний рівень», N leaves her queue and appears in `dean.fmi`'s «Нерозподілені» as «Ескальовано». As `dean.fmi` approve N. Expected: N in `rector.secretary`'s queue. As `rector.secretary` approve. Expected: approved; the timeline shows three decisions with names; two «передано» e-mails and one «затверджено». (AC-2.2, 2.1, 2.12)
12. As `dean.fmi` reject B without a comment → refused; with «Нагорода не стосується діяльності університету» → «Відхилено»; Mailpit has the reason. (AC-2.3, 2.10)
13. Submit award C as `employee.fmi`; as `secretary.fmi` «Передати декану» with a comment. Expected: C in the dean's queue, timeline «Передано декану» with the comment. As `dean.fmi` «Передати секретарю ректора»; as `rector.secretary` «Передати ректору»; as `rector` Swagger `POST /api/v1/awards/<C>/decisions` with `ESCALATE` → 409 `no-higher-level`. (AC-2.5, 2.8)
14. As `dean.fmi` delegate the dean role to `secretary2.fmi` for today (`/delegations`); as `secretary2.fmi` decide on a dean-level request. Expected: timeline «<secretary2.fmi's name> (за дорученням Мартин Мартинюк)»; psql `select reviewer_id, delegator_id from review_decisions order by decided_at desc limit 1;` shows both. (AC-2.7, 1.1 `delegatedFrom`)
15. As `employee.fmi` submit award D, open it. Expected: «Відкликати». Confirm. Expected: the form opens, D is a draft; it is gone from `secretary.fmi`'s queue. Submit D again. Expected: request `SUBMITTED` at the first level. (AC-3.1, 3.3, 3.6)
16. Submit award E; as `secretary.fmi` claim E; as `employee.fmi` open E. Expected: no «Відкликати»; Swagger `POST /awards/<E>/withdraw` → 409 `request-claimed`. (AC-3.2, 3.6)
17. Submit six awards as `employee.fmi`. As `secretary2.fmi` claim one of them. As `secretary.fmi` in `/reviews` «Нерозподілені» select all six, «Затвердити (6)», confirm. Expected: «Опрацьовано: 5 з 6», the claimed one listed as «Взято в роботу іншим рецензентом» with a link; five e-mails in Mailpit; psql `select new_values from audit_logs where action_type = 'REVIEW_BATCH' order by created_at desc limit 1;` shows 6/5/1. (AC-4.1–4.3, 4.6, 4.7)
18. In the batch dialog choose «Повернути», open «Шаблон відповіді». Expected: the seeded templates for return; picking one fills the comment, which can be edited. (AC-4.5, 4.6)
19. Switch to English and repeat steps 4, 8 and 17 briefly. Expected: all texts in English, including the e-mail of a user whose language is English. (AC-1.13, 4.8, 2.10)
20. Narrow the browser to 360 px on `/reviews`. Expected: cards, the action bar usable. Keyboard only: Tab to a checkbox, Space selects, Tab to «Затвердити», Enter opens the dialog. (AC-1.13, 4.8)
21. As `employee.fmi` request the GDPR export; as `secretary.fmi` request hers. Expected: the unit award only in `secretary.fmi`'s export, with the unit. (AC-0.9)

### Detours

22. As `employee.fmi` open `http://localhost:4200/reviews` directly. Expected: the forbidden page; Swagger `GET /api/v1/reviews` → 403. (AC-1.3)
23. As `secretary.fmi` Swagger `PUT /api/v1/awards/<her own unit award>/reviewer` → 404; `POST …/decisions` on an award of another faculty (create one as a registered employee of faculty 10) → 404; `GET /api/v1/reviews?organizationId=10` → 400. (AC-1.9, 1.2, 2.8)
24. Open A's award page in two tabs as `secretary.fmi`; approve in one, then «Відхилити» in the other. Expected: «Дані застаріли, сторінку оновлено» and the approved state; Swagger with the old `requestVersion` → 409 `request-stale`. (AC-2.8)
25. Back button after a decision, then reload. Expected: the award page shows the final state; no decision buttons; repeating the request in Swagger → 409 `request-closed`. (AC-2.8)
26. Stop the backend while a batch of 20 runs (start it, `Ctrl+C` after a second), restart. Expected: the UI shows a network error; after reload the queue shows only the items not decided; no item half-decided (psql: every `APPROVED` request has a decision row). (§5)
27. Restart the backend with the queue open, then claim. Expected: works after a silent token refresh (or the known Docker-profile 401 is not hit with the local profile). (§5)
28. Let the access token expire on the decision dialog (15 minutes), then confirm. Expected: the decision succeeds after a silent refresh. (§5)
29. As `admin` end `secretary.fmi`'s role while she holds a claim (Feature 1.2 admin page). Expected: her next decision answers 404 (or signs her out); `secretary2.fmi` sees «Взяти на себе» on that request and takes it over. Re-assign the role. (§5, D-2)
30. Withdraw D in one browser while `secretary.fmi` claims it in another, both within a second. Expected: exactly one succeeds; the other shows its 409 message. (AC-3.2)
31. `docker compose stop mailpit`, decide on a request. Expected: the decision is saved; the backend logs the mail failure. `docker compose start mailpit`. (AC-2.10)
32. As `employee.fmi` try to delete returned award A before resubmitting (`DELETE /api/v1/awards/<A>`). Expected: 409 `award-has-request`; «Видалити» is not offered in the UI for a returned draft. (§5)
33. Swagger `POST /api/v1/reviews/decisions` with 51 items → 400; with the same award twice → 400; `RETURN` without comment → 422. (AC-4.2)
34. Reuse a template-filled comment after editing it, then pick another template. Expected: the dialog asks before replacing edited text. (AC-4.6)
35. As `secretary.fmi` claim and release an award, then send the same `DELETE /api/v1/awards/<id>/reviewer?requestVersion=<n+2>` in Swagger again. Expected: 409 `request-claimed` without `reviewer` (not 500). (AC-1.7, F-1)
36. Select three awards in `secretary.fmi`'s «Нерозподілені»; in a second browser as `secretary2.fmi` claim and release one of them (its version moves). As `secretary.fmi` «Затвердити (3)». Expected: «Опрацьовано: 2 з 3» with «Дані змінилися…» for that award; the queue reloads, the award stays selected; «Затвердити (1)» now succeeds. (AC-4.7, F-2)
37. As `secretary.fmi` paste the URL of returned award A (step 8) into the address bar. Expected: «Не знайдено», no «Розгляд» panel, no console error; a repeated decision in Swagger on A → 404 (returned and withdrawn requests are not reviewable; `request-closed` only for approved and rejected ones). (AC-2.4, 2.8)
38. As `employee.fmi` open `http://localhost:4200/awards/<pending award>/edit` directly. Expected: the read-only award page opens instead of the form. (AC-3.5)
39. `docker compose stop redis`, decide on a request and run a batch of two. Expected: both succeed (review endpoints do not use Redis). `docker compose start redis`. (§5)
40. Type a rejection comment on A in one tab after A was decided in another tab, confirm. Expected: «Дані застаріли, сторінку оновлено»; note that the typed comment is gone (F-4). (AC-2.8)
41. Return award F (category «Державна премія…») at the faculty level; as `employee.fmi` change the category to a faculty-level one and submit. Expected: it is back with the faculty secretaries; their approval is final. (§5 "Category changed to a lower level after a return")

## 10. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Queue query joins requests, awards, organisations and delegations per scope | Slow queue as requests grow | Scope ids resolved in memory (`OrganizationTree`), one specification query with indexed `(current_level, status, deadline)`; plan check in the IT (tracker risk 2) |
| The reviewer rule spreads over queue, claim, decision and batch | Inconsistent access between endpoints | One `ReviewerRule` component used everywhere; FT matrix per role |
| Unit awards change ownership semantics | Regressions in editing and GDPR export | §7.1 keeps one ownership rule; the 2.1 and 1.3.3 FTs stay green |
| Return turning the award into a draft hides it from reviewers | Reviewer cannot follow up a returned award | A2; the returning level gets it back first on resubmission; Feature 4.2 may add a «Повернуті» filter |
| E-mail volume from batches | Owners flooded | One e-mail per award and decision (A3); preferences in Epic 7 |
| Five migrations in one feature | Ordering conflicts between parallel branches | One story at a time; V027–V031 reserved in this order |

## 11. Definition of Done

- `./mvnw verify` green (unit, slice, IT, FT), JaCoCo ≥ 85 % lines, Checkstyle/PMD/SpotBugs clean
- `npm run lint`, `npm run test:ci`, Playwright scenarios for AC-0.7–0.8, 1.10–1.13, 2.11, 3.5–3.6, 4.6–4.8
- Docs in the same PR as the story: `openapi.yml`, DATA_DICTIONARY, V027–V030, ADR-023 (4.1.1, completed in 4.1.2), state machine, BPMN and sequence diagrams (`puml-check.ps1`), RBAC_matrix.md, AUTHENTICATION_AUTHORIZATION stamp, RACI row, reviewer guide section in `docs/user/` (US-004 DoD), `CHANGELOG.md`, EPIC-04 tracker, `BACKLOG.md`
- Security review of each story's diff (access rules, migrations, bulk operations)
- Audit rows for every claim, hand-over, decision, batch and withdrawal (US-004 DoD)
- §9 manual verification run in the browser after the validation, including the detours

## 12. Validation (2026-10-08, `develop` at 4bdef7b, fixes in SCRUM-58)

Gates on `develop`: `mvn verify` — 832 unit and slice tests, 269 integration and functional, 98.5 % lines, Checkstyle 0, PMD 0, SpotBugs 0; frontend lint clean, 485 Vitest; Playwright `reviews.spec` 7/7 and `unit-awards.spec` 3/3 (full suite: the known `ac1_13` load flake). After SCRUM-58: 833 unit and slice, 270 integration and functional, 98.5 % lines, static analysis 0, 486 Vitest. All ten 4.1 operations of the controllers (`/reviews`, `/reviews/decisions`, `/reviews/templates`, `/awards/{id}/reviewer` GET/PUT/DELETE, `/awards/{id}/reviewers`, `/awards/{id}/decisions`, `/awards/{id}/withdraw`, `/awards/recipient-units`) are in `openapi.yml`; every `*IT` applies V027–V031 to an empty database.

### AC evidence

| AC | Evidence | Result |
|----|----------|--------|
| 0.1 | `RecipientUnitsTest` (3), `RecipientUnitEndpointsTest` (2), `UnitAwardFT#ac0_1_…`; E2E `unit-awards.spec` | pass |
| 0.2 | `AwardInputRulesTest`, `AwardServiceTest`, `UnitAwardFT#ac0_2_ac0_4_ac0_7_…`; E2E `unit-awards.spec` | pass |
| 0.3 | `RecipientUnitsTest`, `AwardInputRulesTest`, `award-form.spec#ac0_3_*`, `UnitAwardFT#ac0_3_…` | pass |
| 0.4 | `AwardSubmissionTest`, `AwardInputRulesTest#ac0_4_*`, `UnitAwardFT#ac0_4_…` (2) | pass |
| 0.5 | `DuplicateFinderIT` (2) | pass |
| 0.6 | `StartLevelTest` (4), `ReviewerAvailabilityTest` (2), `ReviewerAvailabilityIT` (2), `AwardFT#ac1_11_ac0_6_…`, `UnitAwardFT#ac0_6_…` | pass |
| 0.7 | `award-list.spec`, `award-detail.spec` (2), `UnitAwardFT#ac0_2_ac0_4_ac0_7_…`; E2E `unit-awards.spec` | pass |
| 0.8 | `award-form.spec` (4), `AwardServiceTest#ac0_8_*`; E2E `unit-awards.spec` (3) | pass |
| 0.9, 0.10 | `UnitAwardFT#ac0_9_…`, `#ac0_10_…` | pass |
| 1.1 | `ReviewerRuleTest` (7), `ReviewEndpointsTest`, `ReviewFT#ac1_1_*` (2) | pass |
| 1.2 | `ReviewerRuleTest#ac1_2_*`, `ReviewEndpointsTest#ac1_2_*`, `permissions.spec`, `review-list.spec`, `ReviewFT#ac1_1_ac1_2_…` | pass |
| 1.3 | `ReviewerRuleTest`, `ReviewEndpointsTest`, `review-list.spec#ac1_3_*`, `ReviewFT` | pass |
| 1.4, 1.5 | `ReviewAssignmentTest` (6), `ReviewEndpointsTest`, `ReviewFT#ac1_4_ac1_5_ac1_7_…`, `#ac1_5_twoClaimsAtOnceLeaveOneReviewer` | pass |
| 1.6 | `ReviewAssignmentTest` (5), `ReviewerAvailabilityIT#ac1_6_*`, `ReviewFT`; E2E `reviews.spec#ac1_10 ac1_11` | pass |
| 1.7 | `ReviewAssignmentTest` (4, incl. `ac1_7_aRequestNobodyHoldsIsNeitherReleasedNorHandedOver`), `ReviewFT#ac1_4_ac1_5_ac1_7_…` | pass after F-1 |
| 1.8 | `ReviewAssignmentTest` (3), `ReviewerAvailabilityIT#ac1_8_*`, `hand-over-dialog.spec` (3), `ReviewFT#ac1_8_…` | pass |
| 1.9 | `ReviewAssignmentTest#ac1_9_*` (3), `ReviewEndpointsTest`, `ReviewFT#ac1_9_unknownDraftAndOwnAwardsAnswer404` | pass |
| 1.10 | `review-list.spec` (3), `reviews.store.spec` (3), `auth.guard.spec`, `permissions.spec`; E2E `reviews.spec#ac1_10 ac1_11` | pass |
| 1.11 | `review-panel.spec` (8), `award-detail.spec`, `ReviewAssignmentTest` (2); E2E `reviews.spec#ac1_10 ac1_11` | pass |
| 1.12 | `DevSeedIT#ac05_seedUsersExistWithRolesAndStatuses`, `DemoSeedIT#demoAccountsExistWithRoles` | pass |
| 1.13 | `review-list.spec#ac1_13_*`; E2E `reviews.spec#ac1_13` (360 px, English, axe) | pass (flaky under full-suite load) |
| 2.1, 2.2 | `TransitionsTest`, `ReviewDecisionsTest#ac2_1_*`, `#ac2_2_*`, `DecisionFT#ac2_1_ac2_2_ac2_6_aNationalAwardClimbsThreeLevelsToApproval`; E2E `reviews.spec#ac2_2 ac2_5 ac2_11` | pass |
| 2.3, 2.4 | `ReviewDecisionsTest` (4), `decision-dialog.spec` (3), `DecisionFT#ac2_3_ac2_4_ac2_10_…`; E2E `reviews.spec#ac2_4 ac2_11` | pass |
| 2.5 | `TransitionsTest` (2), `ReviewDecisionsTest` (2), `review-panel.spec`, `DecisionFT#ac2_5_ac2_12_…` | pass |
| 2.6 | `ReviewDecisionsTest#ac2_6_*` (2), `decision-dialog.spec`, `DecisionFT#ac2_1_ac2_2_ac2_6_…` | pass |
| 2.7 | `ReviewDecisionsTest`, `AwardStatusServiceTest`, `award-status.spec`, `DecisionFT#ac2_7_…` | pass |
| 2.8 | `ReviewDecisionsTest#ac2_8_*` (5), `DecisionEndpointsTest` (2), `DecisionFT#ac2_8_…` (2, incl. `aReturnedAwardIsNoLongerReviewableAndARepeatedDecisionIsClosed`) | pass |
| 2.9 | `DecisionFT#ac2_1_ac2_2_ac2_6_…` (three `REVIEW_DECISION` rows), `#ac2_7_…` (`delegatorId`), `BatchReviewFT#ac4_1_ac4_2_ac4_3_…` | pass |
| 2.10 | `DecisionMailsTest` (6), `DecisionFT#ac2_3_ac2_4_ac2_10_…` (Mailpit) | pass with F-3 |
| 2.11, 2.12 | `review-panel.spec` (3), `award-detail.spec`, `award-status.spec`, `AwardStatusServiceTest`, `DecisionFT#ac2_5_ac2_12_…`; E2E `reviews.spec` | pass |
| 2.13 | `HistoryContractTest#ac2_13_*`, `puml-check.ps1`, OpenAPI paths above | pass |
| 3.1, 3.2 | `AwardWithdrawalTest` (8), `WithdrawEndpointsTest` (4), `WithdrawFT` (3, incl. the withdrawal–claim race); E2E `reviews.spec#ac3_1 ac3_6`, `#ac3_2 ac3_6` | pass |
| 3.3, 3.4 | `AwardResubmissionTest` (3), `award-status.spec` (2), `WithdrawFT#ac3_1_ac3_3_…`, `#ac3_4_ac3_5_…` | pass |
| 3.5, 3.6 | `ReturnedDraftTest` (2), `award-form.spec` (2), `award-detail.spec` (3), `WithdrawFT#ac3_4_ac3_5_…`; E2E `reviews.spec` | pass |
| 4.1–4.3 | `BatchReviewTest` (9), `BatchEndpointsTest`, `BatchReviewFT#ac4_1_ac4_2_ac4_3_…`, `#ac4_2_invalidBatchesAreRefusedAsAWhole` | pass |
| 4.4 | `BatchReviewFT#ac4_4_twentyItemsAreDecidedWithinFiveSeconds` | pass |
| 4.5 | `ReviewTemplatesTest` (2), `BatchEndpointsTest` (3), `decision-dialog.spec`, `BatchReviewFT#ac4_5_…` | pass |
| 4.6–4.8 | `review-list.spec` (8, incl. `ac4_7_a_batch_without_failures_does_not_reload_the_queue`), `decision-dialog.spec` (5), `reviews.store.spec`; E2E `reviews.spec#ac4_6 ac4_7 ac4_8` (keyboard, English, axe) | pass after F-2 |

### Edge cases (§5)

| Case | Covered by |
|------|------------|
| Two claims at once | `ReviewFT#ac1_5_twoClaimsAtOnceLeaveOneReviewer` |
| Decision during a take-over | `DecisionFT#ac2_8_conflicts…` (the loser gets `request-claimed`: the holder check runs before the version) |
| Withdrawal vs claim | `WithdrawFT#ac3_2_aWithdrawalAndAClaimAtOnceLeaveOneWinner` |
| Reviewer loses the role while holding a claim | `ReviewAssignmentTest#ac1_6_aPeerTakesOverFromAReviewerWhoIsNoLongerEligible`, `ReviewDecisionsTest#ac2_8_aRequestTheCallerMayNotReviewIsNotFound`; §9 step 29 |
| Delegate and delegator on one faculty | `ReviewerRuleTest#ac1_1_anOwnRoleIsPreferredToADelegationOfTheSameReach`, `DecisionFT#ac2_7_…` |
| Secretary's own award, only secretary | `StartLevelTest`, `UnitAwardFT#ac0_6_…`, `AwardFT#ac1_11_ac0_6_…` |
| Dean submits a unit award | `ReviewerRuleTest#ac1_1_nobodyReviewsAnAwardTheyOwn` (owner and submitter excluded) |
| National award through three levels | `DecisionFT#ac2_1_ac2_2_ac2_6_…` |
| Category lowered after a return | Open: §9 step 41 (manual) |
| Vacant level | `StartLevelTest#ac0_6_aVacantLevelIsNotPassedOver` |
| Delete a returned draft | `ReturnedDraftTest#edge_aReturnedOrWithdrawnDraftKeepsItsHistoryAndIsNotDeleted` |
| Batch item decided singly | `BatchReviewFT#ac4_1_ac4_2_ac4_3_…` (`request-stale`), `ReviewDecisionsTest#ac2_8_aDecidedRequestAnswersRequestClosed` |
| Batch interrupted by a restart | §9 step 26 (manual); per-item transactions in `BatchReviewTest#ac4_1_aDatabaseFailureFailsOnlyItsItemAndTheBatchIsStillAudited` (F-6) |
| Owner deactivated or erased | `DecisionMailsTest#ac2_10_anErasedOwnerGetsNoMessage` |
| Mail server down | Epic 1 `MailDeliveryTest`; §9 step 31 |

### Security checklist

| Item | Control |
|------|---------|
| A01 access control | `@PreAuthorize(CAN_REVIEW)` on `ReviewController` and `ReviewBatchController`, `CAN_UPDATE` plus the owner check on `WithdrawalController`; one `ReviewerRule` (level, scope, owner and submitter excluded, delegation in effect) behind `ReviewGuards.lockedReviewable` for queue, claim, decision and every batch item; 404 for unreviewable and own awards (D-13); unit recipients checked against the caller's scopes on save and again on submit (AC-0.3, 0.4) |
| A02 cryptography | No new secrets or tokens; JWT and password storage unchanged |
| A03 injection | Queue filters bound to enums and ids (`ReviewSpecifications`, criteria API), unknown values → 400; comments ≤ 2000 characters; templates read-only from a migration; decision e-mails are plain text (`MailTextHelper`) |
| A07 authentication failures | Sign-in unchanged; `ReviewerAvailability` excludes blocked accounts and ended roles or delegations from claims, hand-overs and decisions |
| Concurrency (US-004 bulk DoD) | `PESSIMISTIC_WRITE` on the request row plus `@Version` on `award_requests` and `awards`; one transaction per batch item; `REVIEW_BATCH` audit row with counts |

### Findings

| # | Finding | State |
|---|---------|-------|
| F-1 | Releasing or handing over a request nobody holds answered 500 (`Map.copyOf` with a null reviewer) instead of 409 `request-claimed` | Fixed in SCRUM-58 (`ReviewGuards.claimed`; §9 step 35) |
| F-2 | After a batch with failures the queue kept the old request versions, so a retry of a `request-stale` item failed again | Fixed in SCRUM-58: the queue reloads after a partial failure and keeps the failed items selected (§9 step 36) |
| F-3 | Decision e-mails are bilingual (uk and en in one message), not in the owner's language as AC-2.10 reads | Open: accept, or a follow-up with notification preferences (Epic 7) |
| F-4 | A decision that fails on a conflict, network error or ended session loses the typed comment: the dialog closes before the request is sent (panel and batch) | Fixed in Feature 4.2 (SCRUM-59): the dialog stays open until the answer and keeps the comment |
| F-5 | A returned or withdrawn request answers 404 to a replayed decision, not 409 `request-closed` (AC-2.8 names `request-closed` for final requests only) | As designed; §9 steps 25 and 37 state it |
| F-6 | A batch cut by a restart keeps its committed items but writes no `REVIEW_BATCH` row (written after the loop); the e-mail of the last committed item may be lost (sent asynchronously after commit) | Accepted; the per-item audit rows remain |
| F-7 | The panel shows «Дані застаріли» for `request-claimed`, `request-closed` and a 404 after a lost role alike | Fixed in Feature 4.2 (SCRUM-59): the decision dialog names each cause |

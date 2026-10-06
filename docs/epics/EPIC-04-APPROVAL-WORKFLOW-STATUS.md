# Epic 4: Approval Workflow Engine — Status

> **Started**: 2026-10-06
> **Done**: —
> **Author**: Stefan Kostyk
> **Jira epic**: SCRUM-47
> **Roadmap**: DEVELOPMENT_ROADMAP.md § Epic 4 (local planning document)

## Progress

| Feature | Status | Started | Done |
|---------|--------|---------|------|
| 4.1 Workflow Engine Core (organisational awards, queue, decisions, withdrawal, batch review) | Approved ([PRD](../features/epic-04/feature-4.1-workflow-engine-core.md)) | | |
| 4.2 Review Period & Escalation (per-faculty period, overdue notice, reviewer corrections) | Planned | | |
| 4.3 Achievements (colleague visibility, unit and public pages) | Planned | | |

## Current focus

Epic kickoff 2026-10-06: stories created in Jira and GitHub, order approved. Feature 4.1 PRD approved 2026-10-06,
including the organisational award design (§7.1), the transition table (§7.2) and the settlement of deviations 2,
3, 5 and 6 (§7). Story status lives in the table below.

## Scope

Reviewers work through the submitted awards: a queue per approval level and organisation, claim and hand-over,
decisions with comments (approve, reject, return, escalate to the dean), batch decisions with template responses,
and the owner's withdrawal while nobody has claimed the request. Awards may name a unit (faculty or department) as
recipient. Overdue requests are marked and the next level is told; nothing moves or decides on its own. Approved
awards become visible beyond their owner on an achievements page, by the owner's choice for personal awards and
university-wide for unit awards.

Out of scope here: workflow types "expedited", "appeal" and "exception" (BRD §2, Epic 4 summary), appeals after a
rejection, in-app and push notifications and the notification centre (Epic 7), search over awards (Epic 5),
Spring Modulith and the event publication registry (Epic 7, ADR-022 step 2), ShedLock for the scheduled job
(Epic 9), public holidays in the review period.

## Stories

`parallel` marks stories whose UI can be built in a separate lane from the OpenAPI contract while the backend is in progress.

| # | Story | Feature | Pts | Jira | GitHub | Parallel | Status |
|---|-------|---------|-----|------|--------|----------|--------|
| 1 | 4.1.0 Organisational awards: unit recipients and submitter | 4.1 | 5 | SCRUM-48 | #142 | no | Done |
| 2 | 4.1.1 Reviewer queue with claim, release and hand-over | 4.1 | 8 | SCRUM-49 | #143 | yes | In review |
| 3 | 4.1.2 Review decisions: approve, reject, return, escalate to the dean | 4.1 | 8 | SCRUM-50 | #144 | yes | To do |
| 4 | 4.1.3 Withdraw an unclaimed request and resubmit a returned award | 4.1 | 3 | SCRUM-51 | #145 | no | To do |
| 5 | 4.1.4 Batch review with template responses | 4.1 | 5 | SCRUM-52 | #146 | yes | To do |
| 6 | 4.2.1 Per-faculty review period set by the dean | 4.2 | 3 | SCRUM-53 | #147 | no | To do |
| 7 | 4.2.2 Overdue detection, escalation notice and SLA metrics | 4.2 | 5 | SCRUM-54 | #148 | no | To do |
| 8 | 2.4.1 Award correction by reviewers | 4.2 | 5 | SCRUM-55 | #149 | no | To do |
| 9 | 4.3.1 Colleague visibility and the achievements page | 4.3 | 5 | SCRUM-56 | #150 | yes | To do |
| 10 | 4.3.2 Unit achievement pages and public achievements | 4.3 | 5 | SCRUM-57 | #151 | yes | To do |

Total: 52 points, sprints 4–5.

## Decisions

| Date | Decision | Rationale | Reference |
|------|----------|-----------|-----------|
| 2026-10-05 | Workflow engine: a hand-written transition table in the `award` module, not Spring State Machine or Flowable | Seven request states and four levels; a BPMN engine adds its own schema, a second state store and a learning cost the thesis gains nothing from | ADR-023 (with 4.1.1) |
| 2026-10-05 | Claim model: `current_reviewer_id` plus an optimistic version, 409 on a double claim, release and hand-over to a peer, the dean may take over; a claim never lapses on its own | Two secretaries of one faculty must not review the same request; an automatic lapse would take work away silently | 4.1.1 |
| 2026-10-05 | Escalation of an overdue request notifies the next level and marks the request «Ескальовано»; it never moves or decides a request. A plain `@Scheduled` job with idempotent SQL; ShedLock waits for Epic 9 | A decision on an award is a person's act; one backend instance in the demo | 4.2.2 |
| 2026-10-05 | Decision emails go through the existing mail listener; Spring Modulith and the publication registry stay at Epic 7 | ADR-006 addendum and ADR-022 step 2 already place them there | ADR-006, ADR-022 |
| 2026-10-05 | Organisational awards: the submitter owns the award (`submitted_by`), a nullable `recipient_org_id` names the unit; faculty secretaries and deans submit for their faculty and its departments; the submitter never reviews it; approved unit awards are visible university-wide and counted separately in analytics | Faculties and departments receive awards too; one ownership rule keeps editing and GDPR export unchanged | Design note before 4.1.0 |
| 2026-10-05 | Per-faculty review period: nullable `organizations.review_working_days`, set by the dean; `app.workflow.review-working-days` (default 3) applies otherwise | Faculties differ in volume; the global default stays the rule | 4.2.1 |
| 2026-10-05 | Batch decisions: every item in its own transaction, per-item results | US-004: invalid items are shown individually while the rest are processed | 4.1.4 |
| 2026-10-05 | Review period counts Monday to Friday only, no public holidays | Under martial law there are no public days off | DATA_DICTIONARY § award_requests |
| 2026-10-05 | The administrator's award audit trail stays API-only until Epic 6 | No reviewer needs it in Epic 4 | Design review §P |
| 2026-10-05 | «Відкликати»: the owner withdraws a submitted award back to a draft while the request is unclaimed; no edit window after a claim | Owners noticed mistakes right after submitting (manual run of Feature 2.1) | 4.1.3 |
| 2026-10-05 | Colleague visibility: the owner opts in per approved award («Показувати колегам»); a separate opt-in publishes it on the public achievements page; unit awards are public once approved | Employees see only their own awards today; publication of personal data needs the owner's choice | 4.3.1, 4.3.2 |
| 2026-10-06 | Story order: organisational award model first, achievement pages last | Changing the award model before the queue and decisions avoids reworking their access rules | This kickoff |

## Documentation deviations to resolve

Each is settled in the Feature 4.1 or 4.2 PRD and applied in the PR of the story that touches it.

1. The roadmap and BRD describe automatic escalation; decided: a notice and a mark, never a move. The roadmap's
   Feature 4.2 and the BRD wording are updated with 4.2.2.
2. `state-machine-award-request.puml` has `IN_REVIEW → EXPIRED`, `REJECTED → DRAFT`, an appeal window and a
   document and OCR branch; the data dictionary lists `EXPIRED`. Without automatic expiry or appeals, `EXPIRED`
   stays unused; the diagram is redrawn from the transition table of ADR-023.
3. `openapi.yml` plans `POST /awards/{id}/approve|reject|return` returning `Award`; claim, release, hand-over,
   escalate, withdraw, the queue and batch decisions have no paths yet. Contract settled in the Feature 4.1 PRD.
4. The roadmap estimates 13 points ("included in US-004") for sprints 6–8; the planned scope is 52 points in
   sprints 4–5 (calendar weeks).
5. `review_decisions` has no column for a delegated decision; the authorization document promises a "delegated by"
   stamp in Epic 4.
6. The RACI matrix gives "Escalate approval decisions" to the project lead (A) and reviewers (R); the system's
   escalation is the dean's decision on a request the faculty secretary escalated.

## Technical notes

- `award_requests` (V024) already holds `current_reviewer_id`, `current_level` and `deadline`; `review_decisions`
  (V026) and the status timeline (`GET /awards/{id}/status`) exist since 2.1.8. The workflow writes rows the
  timeline already reads.
- `RecognitionLevel.minimumApproval()` sets the lowest level that may approve: faculty secretary for every level
  up to REGIONAL, rector's secretary for NATIONAL and INTERNATIONAL; every level above may approve too.
- Permissions `award:approve:level1|level2|level3|final` exist in `RolePermissions`; delegated roles already carry
  them through the `delegations` claim, and `@access.inScope` accepts borrowed scopes.
- Approvers may submit their own awards (review 2026-10-04); no one reviews an award they own or submitted.
- `documents.request_id` exists for documents added during a resubmission (deferred from Epic 3).
- The mail listener of Epic 1 sends after commit; decision emails reuse it with new templates (uk, en).

## Risks

1. The organisational award model touches access rules, the award form and the GDPR export at once; a design note
   before 4.1.0 keeps the change in one story.
2. The queue query joins requests, awards, organisations and delegations per reviewer scope; it needs the partial
   indexes of V012 and a plan check with the seed data.
3. Story 4.3.2 publishes personal data beyond the university; the public page shows only what the owner opted into,
   and the privacy documents are updated with it.

## Quick links

- [User story US-004](../requirements/USER_STORIES.md#us-004-batch-award-review-and-approval)
- [Data dictionary § award_requests](../database/DATA_DICTIONARY.md#31-entity-award_requests) · [§ review_decisions](../database/DATA_DICTIONARY.md#32-entity-review_decisions)
- [Request state machine](../diagrams/uml/state-machine-award-request.puml) · [BPMN approval workflow](../diagrams/data-flow/bpmn-approval-workflow.puml) · [Approval sequence](../diagrams/uml/sequence-approval-workflow.puml)
- [OpenAPI](../api/openapi.yml) · [Authentication and authorization](../security/AUTHENTICATION_AUTHORIZATION.md)
- [RACI matrix](../stakeholders/RACI_matrix.md) · [ADR-006 Message queue](../architecture/adr/ADR-006-Message-Queue.md) · [ADR-022 Modular monolith](../architecture/adr/ADR-022-Modular-Monolith-Multi-Tenancy.md)

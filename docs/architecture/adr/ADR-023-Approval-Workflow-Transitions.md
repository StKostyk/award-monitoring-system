# ADR-023: Approval Workflow Transitions and Review Claims

**Status**: Proposed  
**Date**: 2026-10-06  
**Author**: Stefan Kostyk  
**Stakeholders**: Faculty secretaries, deans, rector's office, Thesis Supervisor

---

## Context

A submitted award travels through up to four review levels (faculty secretary, dean, rector's secretary, rector).
The request has seven statuses (`award_requests.status`) and the award four of its own. The original state machine
diagram included states the university does not use (appeal window, automatic expiry during review, a document/OCR
branch) and gave no rule for two reviewers of the same level opening the same request at the same time.

### Background
- A faculty usually has more than one secretary; each sees the same requests of the faculty.
- Higher levels cover a vacancy or an absent secretary below them (`award:approve:level1` is held by every approver).
- Decisions are made one by one and in batches (US-004), so a decision must not depend on a separate claim step.

### Assumptions
- One request per award (ids in workflow paths are award ids; the request version travels as `requestVersion`).
- Who may review a request is decided by the reviewer rule of Feature 4.1 (role and delegation scopes, owner and
  submitter excluded).

---

## Decision

### Chosen Approach
1. **One transition table** in the `award` module, no state-machine library. Rows of 4.1.1 (claims):

   | From (request) | Action | To (request) | Level | Award |
   |----------------|--------|--------------|-------|-------|
   | — | submit | `SUBMITTED` | start level (AC-0.6) | `PENDING` |
   | `SUBMITTED`, `ESCALATED` | claim | `IN_REVIEW` | = | = |
   | `IN_REVIEW` | release | `ESCALATED` when a lower level decided, else `SUBMITTED` | = | = |
   | `IN_REVIEW` | hand-over, take-over | `IN_REVIEW` | = | = |

   Rows of 4.1.2 (decisions, `POST /awards/{id}/decisions`; an unclaimed request is claimed by the decision):

   | From (request) | Action | To (request) | Level | Award |
   |----------------|--------|--------------|-------|-------|
   | `SUBMITTED`, `ESCALATED`, `IN_REVIEW` | approve, level ≥ category minimum | `APPROVED` | = | `APPROVED` |
   | same | approve, level < category minimum | `ESCALATED` | next not passed over | = |
   | same | escalate (level < `RECTOR`) | `ESCALATED` | next not passed over | = |
   | same | return (comment required) | `RETURNED` | = | `DRAFT` |
   | same | reject (comment required) | `REJECTED` | = | `REJECTED` |

   The withdraw and resubmit rows follow in 4.1.3 from the PRD table (Feature 4.1 §7.2). `APPROVED` and `REJECTED`
   are final; `EXPIRED` stays in the check constraint, unused.
2. **Claim = reviewer + version + row lock.** `current_reviewer_id` names the holder; the request row is locked
   (`SELECT … FOR UPDATE`) and `award_requests.version` is checked, so of two claims at once exactly one succeeds and
   the other answers 409 `request-claimed` with the holder. A stale version answers 409 `request-stale`.
3. **No lapse.** A claim is never taken away silently. It ends by release, hand-over to an eligible colleague, a
   decision, or a take-over by a higher level (or by a peer once the holder may no longer review: role ended,
   delegation over, account blocked). Every change is audited (`REVIEW_CLAIMED`, `REVIEW_RELEASED`,
   `REVIEW_HANDED_OVER`, `REVIEW_TAKEN_OVER`).
4. **Higher levels may review lower levels** through the `level` filter of the queue and take-over; the default queue
   shows the caller's own levels.
5. **Decisions.** Every decision writes one `review_decisions` row at the level it was taken (`APPROVED` for an
   approval that only passes the request on), stamped with `delegator_id` when the reviewer acts under a delegated
   role, an `audit_logs` row `REVIEW_DECISION` and an award version `DECIDED`. A level passed over without a decision
   shows as `SKIPPED` in the status path. The owner's e-mail is sent after the commit, so a rollback sends nothing
   and a mail failure does not undo the decision. `verified: true` (approve only, award with documents) sets the
   verification badge.
6. **404 over 403.** A request the caller may not review, an unknown id and the caller's own award all answer 404;
   a decided request answers 409 `request-closed`.

### Rationale
- Seven states and four levels fit one table that the tests walk row by row; a library would add configuration
  without removing a rule.
- Optimistic version plus a row lock gives a definite winner without a distributed lock; Redis is not involved.
- Secretaries share the queue of a faculty, so an explicit claim tells colleagues who works on what.

---

## Consequences

### Positive Consequences
- Two reviewers never work on the same request unknowingly; the panel names the holder on a conflict.
- Every move of a request is visible in the audit trail and on the award page.

### Negative Consequences
- A holder who leaves a request untouched keeps it until someone above takes it over; the overdue chip and the
  deadline order of the queue make that visible.
- The client must send the version it read with every change.

---

## Alternatives Considered

- **Spring Statemachine**: heavy configuration for seven states, persisted machine context not needed.
- **Claim with a time-out**: silently moves work away from a reviewer who is reading the documents.
- **No claim, last decision wins**: two secretaries could reach different decisions on the same award.

---

## Related Decisions
- ADR-004 (PostgreSQL row locks), ADR-009 (permissions and scopes).
- Feature 4.1 PRD §7 (D-1 to D-4, D-12, D-13).

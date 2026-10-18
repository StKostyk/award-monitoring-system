# Epic 6: Compliance & Audit System — Status

> **Started**: 2026-10-15 (kickoff)
> **Done**: 2026-10-18
> **Author**: Stefan Kostyk
> **Jira epic**: SCRUM-85
> **Roadmap**: DEVELOPMENT_ROADMAP.md § Epic 6 (local planning document)

## Progress

| Feature | Status | Started | Done |
|---------|--------|---------|------|
| 6.1 GDPR consent management and data subject rights (privacy notice, consents, privacy centre, legal hold, erasure) | Done | 2026-10-15 | 2026-10-18 |
| 6.2 Automated data retention (retention job, compliance page) | Done | 2026-10-18 | 2026-10-18 |

## Current focus

Both features done. Feature 6.1 done 2026-10-18: validated 2026-10-17 (PRD §13), manual run of PRD §10 passed 40 of 40. Feature 6.2 done 2026-10-18 ([feature-6.2-automated-data-retention.md](../features/epic-06/feature-6.2-automated-data-retention.md)): validated (PRD §13), manual run of §10 passed 26 of 27, step 9 accepted on the ITs. Epic-end reconciliation done 2026-10-18 (documentation sync, refactor sweeps). Next: Epic 5.

## Scope

The privacy notice gets versions in Ukrainian and English; registration records its acknowledgement and a new version
asks for it again. `consent_records`, present since V010 and written by no code so far, holds the acknowledgements and
the optional consents with their history. A privacy page in the profile gathers the choices that already exist (award
visibility, document sharing, notification preferences, data export) with the consents and the log of one's own
actions. A user can ask for the erasure of the account; after a 30-day grace period every module erases or anonymises
its part, approved awards stay under a pseudonym, and a legal hold set by the GDPR officer blocks both erasure and
retention. A nightly job applies retention policies kept as properties and reports each run; the GDPR officer sees
runs, erasure requests and holds on one page.

Out of scope here: backups (none exist before the deployment of Epic 9; the backup procedure then states how erased
accounts are excluded), CSV and PDF variants of the personal data export (JSON already meets Art. 20; deviation 6),
breach notification (PRIVACY_BY_DESIGN §7, Epic 9 runbook), `SMS_NOTIFICATIONS` and `DOCUMENT_AI_PROCESSING` consents
(SMS out of scope since Epic 7, OCR deferred), a legal review and GDPR officer sign-off (outside the software;
recorded in the thesis as a limitation).

## Stories

`parallel` marks stories whose UI can be built in a separate lane from the OpenAPI contract while the backend is in progress.

| # | Story | Feature | Pts | Jira | GitHub | Parallel | Status |
|---|-------|---------|-----|------|--------|----------|--------|
| 1 | 6.1.1 Versioned privacy notice and consent records | 6.1 | 5 | SCRUM-86 | #252 | no | Done |
| 2 | 6.1.2 Privacy centre in the profile | 6.1 | 5 | SCRUM-87 | #253 | yes | Done |
| 3 | 6.1.3 Legal hold on accounts | 6.1 | 3 | SCRUM-88 | #254 | no | Done |
| 4 | 6.1.4 Account erasure request with a 30-day grace period | 6.1 | 5 | SCRUM-89 | #255 | no | Done |
| 5 | 6.1.5 Erasure across modules with anonymised awards | 6.1 | 8 | SCRUM-90 | #256 | no | Done |
| 5a | 6.1.6 Mails without a reverse DNS lookup per message | 6.1 | 2 | SCRUM-93 | #266 | no | Done |
| 5b | 6.1.7 Shared erasure guard, postponement enum and consent append helper | 6.1 | 2 | SCRUM-94 | #268 | no | Done |
| 6 | 6.2.1 Nightly retention job with configurable policies | 6.2 | 5 | SCRUM-91 | #257 | no | Done |
| 6a | 6.2.3 Retention of accounts, consent records and audit partitions | 6.2 | 5 | SCRUM-95 | #272 | no | Done |
| 7 | 6.2.2 Compliance page for retention runs, erasures and legal holds | 6.2 | 5 | SCRUM-92 | #258 | yes | Done |
| 7a | 6.2.4 Nightly erasure run outside the clock-change hour | 6.2 | 1 | SCRUM-96 | #277 | no | Done |

Total: 46 points (roadmap estimate 26; 6.2.1 split into 6.2.1 and 6.2.3), planned for sprints 6 and 7 (2026-10-19 to 2026-11-01). 6.1.5 replaces
2.4.2 "GDPR-compliant award deletion" (moved here at the Epic 2 kickoff, no tracker item existed).

## Decisions

| Date | Decision | Rationale | Reference |
|------|----------|-----------|-----------|
| 2026-10-15 | Legal hold moves from Feature 6.2 to 6.1 (6.1.3) | Erasure checks the hold, so the hold exists before erasure is built | US-012 additional scenario |
| 2026-10-15 | Erasure is split into the request (6.1.4) and the cross-module execution (6.1.5) | Request flow and UI stand alone; execution touches every module and gets the security review | ADR-022 |
| 2026-10-15 | Each module erases its own data behind one contract, called from the `gdpr` module | Keeps ADR-022 boundaries; the same contributors can serve the export (Epic 7 refactor note) | ADR-022 |
| 2026-10-15 | Audit trail: guarded redaction (design note option B); V043 replaces the immutability rules with a row trigger and `fn_redact_audit_subject` | Keeps every row and its legal value while removing the identity of an erased person | PRD 6.1 §7 decision 11, §8.4; ADR-024 with 6.1.5 |
| 2026-10-15 | Feature 6.1 PRD approved with deviations 1–14 (consent types, client-side acknowledgement, static notice pages, `legal-hold:manage`, pseudonym "Erased #<id>", usable account during the grace period) | One-pass approval | PRD 6.1 §7 |
| 2026-10-17 | Feature 6.1 validated; four detours added to the manual scenario (last administrator at execution, restart mid-run, old password, odd `returnUrl`); lost mails on an outage stay a project-wide finding for Epic 9 | Scenario review found no break in the merged code | PRD 6.1 §13 |
| 2026-10-18 | Feature 6.1 done after the manual run (40 of 40); step 5 uses a pending notice 1.1 instead of deleting a consent row | Consent rows are append-only, so the scenario no longer removes one | PRD 6.1 §10 |
| 2026-10-18 | Feature 6.2 PRD approved with deviations 1–10; 6.2.1 split into 6.2.1 (job, technical policies) and 6.2.3 (SCRUM-95: accounts, consent records, audit partitions) | The second part changes guarded tables and the erasure path and gets its own review | PRD 6.2 §7 |
| 2026-10-18 | Feature 6.2 done after the manual run (26 of 27); step 9 (direct DELETEs on `consent_records` and `audit_logs`) accepted on the ITs | The guards are trigger-level and proven by `ConsentRecordsIT`, `RecordRetentionIT` and `ErasureIT` on every gate | PRD 6.2 §10, §13 |

## Documentation deviations found at kickoff

1. `audit_logs` is immutable (V009 rules `audit_logs_no_update`/`no_delete`, seven-year legal obligation), while
   PRIVACY_BY_DESIGN §5.2 anonymises audit logs within 30 days; trigger rows carry names and addresses in `old_values`
   and `new_values`. Options (keep under Art. 17(3)(b), a privileged anonymising function, per-person keys) go to the
   design note before the PRD.
2. DATA_GOVERNANCE §2.3 deletes from `user_profiles` and `contact_information`, sets `recipient_name` and an
   `ANONYMIZED` status; none of these exist (`users.account_status` has `DELETED`). It also deletes `consent_records`,
   against V010 ("never deleted") and §2.1 ("seven years from withdrawal"). The section is rewritten with 6.1.5.
3. PRIVACY_BY_DESIGN §4.2 marks `DATA_PROCESSING` and `PUBLIC_VISIBILITY` as required consents. Processing rests on
   legitimate interest (DATA_GOVERNANCE §2.1), and visibility is already a per-award opt-in (V035, default `PRIVATE`);
   a required consent would not be freely given (Art. 7(4)). Proposal: `DATA_PROCESSING` records the acknowledgement
   of the notice version, `PUBLIC_VISIBILITY` is retired in favour of the per-award choice, `ANALYTICS` stays the one
   optional consent type. Settled in the 6.1 PRD.
4. DATA_GOVERNANCE §2.1 keeps profiles "seven years post-employment"; `users` stores no date of the change to
   `RETIRED`. The 6.2 PRD adds `users.status_changed_at`, set by a trigger (PRD 6.2 §7 decision 2, story 6.2.3).
   Closed 2026-10-18: V045 adds the column and `trg_users_status_changed`; the `former-staff` policy counts from it.
5. USER_STORIES.md titles the next section "Epic 6: Mobile & Accessibility"; the roadmap numbers compliance as Epic 6
   and mobile as Epic 8. The heading is corrected in the 6.1 PRD.
6. PRIVACY_BY_DESIGN §9.2 promises CSV and PDF exports "with Epic 6"; JSON stays the only format, and the row is
   reworded.

## Technical notes

- Present today: `consent_records` (V010, types `DATA_PROCESSING`, `PUBLIC_VISIBILITY`, `EMAIL_NOTIFICATIONS`,
  `SMS_NOTIFICATIONS`, `ANALYTICS`; `ON DELETE RESTRICT`), `users.account_status` value `DELETED`, role
  `GDPR_OFFICER` with `consent:manage`, `audit:read`, `data:export` (RolePermissions), JSON export `GET
  /users/me/export` (`gdpr` module, `PersonalDataAssembler`), audit trail export per award, `DocumentSweeper` for
  orphaned objects, scheduled jobs on `Europe/Kyiv`.
- Registration records no consent today, and the export's consent list is always empty.
- Carried from Epic 7: on erasure, delete incomplete `event_publication` rows of the person, pseudonymise names in
  other people's notification payloads and in exports, clear `shown_to_colleagues` with the visibility; one
  reachability rule for e-mail and in-app with a split `skipped` metric; the one-year notification retention
  (`plusYears(1)` in `NotificationRows`) becomes a property in 6.2.1.
- Audit partitions are monthly (V009, V016, `fn_create_audit_partition`); dropping a partition older than seven years
  is the retention path that keeps the immutability rules.
- For Epic 5: the `ANALYTICS` consent decides whether a person is named in rankings and comparisons of unit
  reports; without it the person is counted only in totals (PRD 6.1 decision 1).
- Documents live in MinIO; object deletion runs after the commit, with `DocumentSweeper` as the safety net.
- Refactor candidates left after the 6.1 sweep: a shared submit helper for the legal hold and erasure dialogs (when
  a third dialog appears), one `startAction()` for the privacy centre's busy and message resets, the cancel problem
  keys under `privacy.erasure.cancel.*`, one account clean-up helper in `AbstractFunctionalTest` for the GDPR FTs,
  `namesOf` in `ErasureRunner`, one row record in `DocumentErasure`.
- Left open at the end of the epic (no story yet): the `status` query parameter of the erasure tab stays in the URL
  on the legal holds tab; no metric for retention runs skipped because the session lock was held; no FT for the
  compliance endpoints after a role revocation; ended roles can get `valid_to` before `valid_from` on a same-day
  erasure or revocation; revoking `GDPR_OFFICER` or `SYSTEM_ADMIN` has no last-holder guard and is not serialised
  with erasures; the erasure card shows a date where the mail shows a time. Noted by the 6.2 sweep and left: a JDBC
  page helper, `UserRef` in `shared/`, the policy-key list, the double `queryParamMap` read on the compliance page,
  the pass-through `ComplianceService`.

## Risks

1. 6.1.5 touches every module (8 points). If it grows, notifications and event publications move to a follow-up
   story; the security review covers the whole diff.
2. Erasure cannot be undone; tests and manual runs use fresh accounts, and the grace period is a property so the
   manual run can shorten it.
3. The audit decision changes a legal-obligation table; it is made in the design note before any migration.

## Quick links

- [User stories US-011, US-012](../requirements/USER_STORIES.md) · [Traceability FR-010, FR-011, NFR-011](../requirements/TRACEABILITY_MATRIX.md)
- [Privacy by design §4 consent, §5 erasure, §8 dashboard](../security/PRIVACY_BY_DESIGN.md)
- [Data governance §2 retention](../compliance/DATA_GOVERNANCE.md) · [Privacy impact](../compliance/PRIVACY_IMPACT.md)
- [Data dictionary § users, consent_records, audit_logs](../database/DATA_DICTIONARY.md) · [OpenAPI](../api/openapi.yml)
- [ADR-022 Modular monolith](../architecture/adr/ADR-022-Modular-Monolith-Multi-Tenancy.md) · [Authentication and authorization § RBAC](../security/AUTHENTICATION_AUTHORIZATION.md)

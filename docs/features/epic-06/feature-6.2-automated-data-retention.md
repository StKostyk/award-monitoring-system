# Feature 6.2: Automated Data Retention

> **Epic**: 6 — Compliance & Audit System (SCRUM-85)
> **Sprint**: 7 (2026-10-26 → 2026-11-01)
> **Points**: 15 (three stories; tracked 13 before the split of decision 1)
> **Status**: Approved 2026-10-18
> **Author**: Stefan Kostyk
> **Governing docs**: EPIC-06 tracker (scope, deviation 4, technical notes), US-012, DATA_GOVERNANCE §2.1–§2.3, PRIVACY_BY_DESIGN §5.2, DATA_DICTIONARY §1.1, §1.4, §1.5, §1.6, §4.1–§4.4, §5.1, §5.3, ADR-022 (modules), ADR-024 (guarded redaction), ADR-021 (one instance), AUTHENTICATION_AUTHORIZATION §3.3, PRD 6.1 (§7 decisions 9, 13, 15; AC-5.1–5.10), `ErasureRunner`, `ErasureGuard`, `ErasureContributor`, `PublicationRetry`, `NotificationRows`, `DocumentSweeper`, `fn_create_audit_partition`, `RolePermissions`, `openapi.yml` (`/users/{id}/legal-holds`)

## 1. Problem and personas

Personal data outlives its purpose. Expired notifications, used sign-in links, expired OAuth2 authorizations and devices not seen for years pile up; unverified registrations stay `PENDING` forever with an acknowledgement row; former staff keep their profiles indefinitely, against the seven years of DATA_GOVERNANCE §2.1; consent records and audit trails have a seven-year period and no code that ends it. Nothing reports what was removed.

- **GDPR officer** (`gdpr`): wants the periods applied every night without manual work, a record of each run, and one page with runs, erasure requests and legal holds (US-012: "I receive reports on retention activities"; "exceptions for legal hold requirements are properly handled").
- **Former employee**: expects the profile to be anonymised after the period, with approved awards kept as the university's record (US-012: "award records are preserved with personal identifiers removed").
- **System administrator**: wants the job to run without load on the day's work and to see failures in the logs and metrics.

## 2. Scope

**In (this feature)**

- A nightly retention job (04:00 Kyiv) running every policy in its own transactions, in batches, with a `retention_runs` row per run, one audit row, metrics and a mail to GDPR officers when a run does not complete
- Policies as properties, each a period and an action: expired notifications (the one-year period of `NotificationRows` becomes the property), used or expired one-time tokens, expired OAuth2 authorizations, devices not used for two years, unverified registrations, former staff (`RETIRED`, `MEMORIAL`), consent records seven years after withdrawal or erasure, audit partitions older than seven years
- Monthly audit partitions created three months ahead (V009/V016 end at 2027-12)
- Account retention through the erasure of 6.1.5: the job files an `erasure_requests` row of origin `RETENTION`, the 03:30 erasure run executes it the next night with the same contributors, holds and last-administrator check, without mails
- A legal hold skips the held person in the account, consent and audit policies
- `users.status_changed_at` (tracker deviation 4)
- Compliance page `/compliance` for the GDPR officer: policies and runs, erasure requests of every origin, legal holds

**Out (where it goes)**

- Setting `RETIRED` or `MEMORIAL` from the interface: no screen changes the status today; the policy acts on the status whoever sets it. Parked in design-review §L with the admin user management follow-ups (the manual run sets it in psql)
- User-defined retention of scanned documents (DATA_GOVERNANCE §2.1 "1–10 years"): no such choice exists; documents go with their award or the account (decision 5)
- System log retention: logs are files rolled after 30 days or 1 GB (`logback-spring.xml`), not database rows; container log rotation is part of the Epic 9 runbook (decision 6)
- Event publications: already purged by `PublicationRetry` after `app.events.keep-failed` (30 days)
- "Run now" button and editable policies: the cron and the periods are properties (decision 10)
- Backups: none before Epic 9; the backup procedure then states how expired data leaves them

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-91 (#257) | 6.2.1 Nightly retention job with configurable policies | 5 | no | — |
| SCRUM-95 (#272) | 6.2.3 Retention of accounts, consent records and audit partitions | 5 | no | 6.2.1 (job, contract) |
| SCRUM-92 (#258) | 6.2.2 Compliance page for retention runs, erasures and legal holds | 5 | yes | 6.2.1 (runs), 6.2.3 (origin column) |

Order: 6.2.1 → 6.2.3 → 6.2.2. 6.2.1 and 6.2.3 are backend only; 6.2.2 splits at the green backend gate, and its UI can start from the stubs (§8.1).

## 4. Acceptance criteria

**Audit action types added**: `RETENTION_RUN_COMPLETED` (`entity_type` = `RETENTION`, `entity_id` = the run, no actor). Account retention reuses `ERASURE_COMPLETED` and `ERASURE_POSTPONED` with `origin: retention`.

### 6.2.1 Nightly retention job with configurable policies (SCRUM-91)

- **AC-1.1** Given V044, then `retention_runs` exists (§8.2) with the status check `RUNNING`, `COMPLETED`, `PARTIAL`, `INTERRUPTED` and the index on `started_at DESC`.
- **AC-1.2** Given `app.retention.cron` (default `0 0 4 * * *`, `Europe/Kyiv`), when the job runs, then it first marks rows still `RUNNING` as `INTERRUPTED` with `finished_at`, inserts a `RUNNING` row, runs every `RetentionPolicy` bean in its `order()`, each in its own transactions of at most `app.retention.batch-size` (default 1000) rows, and records per policy `{policy, affected, held, failed}` in `results`.
- **AC-1.3** Given every policy succeeded, then the run is `COMPLETED`; given one throws, then its batch rolls back, `failed` is true with the exception class only (no message), the other policies still run, and the run is `PARTIAL`. Either way `RETENTION_RUN_COMPLETED` records the status and the counts.
- **AC-1.4** Given a run that is `PARTIAL`, or rows marked `INTERRUPTED` at the start, then after the commit one mail goes to every account with an active `GDPR_OFFICER` role in its language, naming the run, the failed policies and a link to `/compliance` (§8.3).
- **AC-1.5** Given `app.retention.notifications` (default `P365D`), then new notifications get `expires_at` = `sent_at` + the period, and the `notifications` policy deletes rows with `expires_at` before now; existing rows keep their `expires_at`.
- **AC-1.6** Given `app.retention.one-time-tokens` (default `P30D`), then the `one-time-tokens` policy deletes rows used or expired longer ago than the period (DATA_DICTIONARY §1.4 "removed by a scheduled cleanup", which no code did so far).
- **AC-1.7** Given `app.retention.authorizations` (default `P1D`), then the `authorizations` policy deletes `oauth2_authorization` rows whose every expiry column is null or older than the period; an authorization with a live refresh token stays.
- **AC-1.8** Given `app.retention.devices` (default `P730D`), then the `devices` policy deletes `user_devices` rows with `last_used_at` older than the period; the next sign-in from such a device counts as a new device.
- **AC-1.9** Given `app.retention.audit-partitions-ahead` (default 3), then the `audit-partitions` policy ensures the monthly partitions from the current month to three months ahead exist (`fn_create_audit_partition(p_month DATE)`), and `affected` counts the partitions created.
- **AC-1.10** Given the metrics endpoint, then `award_retention_runs_total{outcome}` and `award_retention_rows_total{policy}` grow with each run.
- **AC-1.11** Given the documentation, then DATA_DICTIONARY has `retention_runs` and the corrected §1.4 and §5.1 retention lines, DATA_GOVERNANCE §2.1 the matrix as built (decisions 5, 6, 12) and §2.2 the job as built instead of the sketch, ADR-022 the `common.retention` contract.

### 6.2.3 Retention of accounts, consent records and audit partitions (SCRUM-95)

- **AC-3.1** Given V045, then `users.status_changed_at TIMESTAMPTZ NOT NULL` exists, backfilled with `updated_at`, and a trigger sets it to `now()` whenever `account_status` changes; `erasure_requests.origin` exists (`REQUEST` default, `RETENTION`), with `requested_at` = `scheduled_for` for retention rows.
- **AC-3.2** Given `app.retention.pending-accounts` (default `P30D`), when the job runs, then for every `PENDING` account created longer ago than the period, without a pending erasure request and without an active legal hold, it files a `RETENTION` request due at once; `affected` counts the requests filed and `held` the accounts skipped for a hold.
- **AC-3.3** Given `app.retention.former-staff` (default `P7Y`), then the same happens for `RETIRED` and `MEMORIAL` accounts with `status_changed_at` older than the period.
- **AC-3.4** Given a `RETENTION` request, when the 03:30 erasure run reaches it, then the account is erased exactly as in 6.1.5 (contributors, pseudonym, approved awards kept `PRIVATE`, audit redaction) and `ERASURE_COMPLETED` carries `origin: retention`; no request, reminder, postponement or completion mail is sent; a hold placed in between, or the only system administrator, postpones it as in AC-5.2 of 6.1 without a mail; when the policy's condition no longer holds under the row lock (verified, renewed or back to `ACTIVE`), the request becomes `CANCELLED` without a mail.
- **AC-3.5** Given `app.retention.consent-records` (default `P7Y`), then the `consent-records` policy deletes, through `fn_purge_consent_records(p_before)`, the `ANALYTICS` rows of a person whose latest `ANALYTICS` row is a withdrawal older than the period, and every row of an account whose erasure completed longer ago than the period; `DATA_PROCESSING` rows of living accounts stay; held persons are skipped. A direct `DELETE` outside the function still raises.
- **AC-3.6** Given `app.retention.audit-logs` (default `P7Y`), then the `audit-logs` policy detaches and drops, through `fn_drop_expired_audit_partitions(p_before, p_held)`, every monthly partition that ends before now minus the period, except a partition with a row whose `user_id`, or whose `entity_id` for the person entity types of `fn_redact_audit_subject`, is a held person; `affected` counts partitions dropped, `held` partitions kept. The default partition is never dropped; a direct `DELETE` still raises.
- **AC-3.7** Given the documentation, then DATA_DICTIONARY has the two columns, the consent purge in §4.2 and the partition drop in §4.1; DATA_GOVERNANCE §2.1 names the account policies; PRIVACY_BY_DESIGN §5.2 the retention origin; ADR-024 the two new guarded functions; tracker deviation 4 closed.

### 6.2.2 Compliance page for retention runs, erasures and legal holds (SCRUM-92)

- **AC-2.1** Given `compliance:read` (GDPR officer only), when `GET /compliance/retention-policies` is called, then each policy is listed with its key, period (ISO 8601) and action (`delete`, `erase-account`, `create-partitions`, `drop-partitions`); others get 403.
- **AC-2.2** Given `GET /compliance/retention-runs?page&size`, then runs are listed newest first with status, start, end and the per-policy results.
- **AC-2.3** Given `GET /compliance/erasure-requests?status&page&size`, then requests of both origins are listed newest first with the person (`UserRef`, the pseudonym once erased), origin, status and the dates; `status` filters, the default lists all.
- **AC-2.4** Given `GET /compliance/legal-holds?active&page&size`, then holds of every account are listed newest first with the person, reason, who placed and lifted them and when; `active=true` lists only active ones.
- **AC-2.5** Given a GDPR officer, then a navigation entry "Compliance" leads to `/compliance` with three tabs: "Retention" (policy table, then runs with a status chip, duration and expandable per-policy counts; failed policies marked), "Erasure requests" (status filter, origin, dates, person linking to `/admin/users/:id`), "Legal holds" (active/all filter, reason, placed and lifted by). Others don't see the entry, and the route ends on `/forbidden`.
- **AC-2.6** Given an empty list, an error or a slow answer, then each tab shows an empty state, a retry message or a loading bar; the tabs work at 360 px, in both languages and both themes.
- **AC-2.7** Given the documentation, then AUTHENTICATION_AUTHORIZATION §3.3 has `compliance:read`, the admin guide the compliance page, `openapi.yml` loses `x-status: planned` on the four operations.

## 5. Edge cases

| Case | Expected |
|------|----------|
| Backend stops in the middle of a run | The row stays `RUNNING`; the next run marks it `INTERRUPTED` and mails the officers; every committed batch stays committed, no batch is half applied |
| Two runs at once (manual cron override overlapping a slow run) | One instance (ADR-021); a session advisory lock held for the whole run (a run spans many transactions) makes the second start skip with a log line, no row |
| A policy fails at batch 3 of 5 | Batches 1–2 stay committed; `affected` counts them; `failed` true; run `PARTIAL` |
| Mail server down when the failure mail is sent | Registry retries (`PublicationRetry`), as every other mail |
| No GDPR officer exists | No mail; the log names the run |
| Legal hold placed after the request was filed, before 03:30 | Erasure postponed (6.1.5 path), no mail |
| Person under hold also has expired notifications and old devices | Those still go (decision 7); account, consents and audit rows stay |
| A `PENDING` account is verified, or taken over by a new registration (`RegistrationService`), after its `RETENTION` request was filed | The erasure run re-checks the policy's condition under the row lock and cancels the request (`CANCELLED`, no mail) when it no longer holds |
| Former staff account changed back to `ACTIVE` before the erasure run | Same re-check: the request is cancelled |
| The person already has a `PENDING` request of their own | No second request (`uk_erasure_requests_pending`); the own request runs on its date |
| Audit partition older than seven years that holds a held person's row | Kept and counted as `held`; dropped at the first run after the hold is lifted |
| Partition three months ahead already exists | `fn_create_audit_partition` skips it; `affected` 0 |
| Notification property shortened (e.g. `P30D`) | Only new rows get the shorter `expires_at`; old rows expire on their own date |
| Consent chain grant → withdraw → grant (latest is a grant) | Nothing deleted, whatever the age of the withdrawal |
| `oauth2_authorization` with an expired access token but a live refresh token | Kept |
| Large backlog on the first run (devices, tokens) | Batches of 1000, each its own transaction; no long lock on the tables |
| Officer opens `/compliance` with a stale tab after the role was revoked | Next request 401 (session revoked with the role), then `/forbidden` after signing in again |

## 6. Dependencies

### Tables

- Existing: `notifications`, `one_time_tokens`, `oauth2_authorization`, `user_devices`, `users`, `consent_records`, `audit_logs` (partitions), `legal_holds`, `erasure_requests`, `user_roles`
- New: `retention_runs` (V044); columns `users.status_changed_at`, `erasure_requests.origin` (V045)

### Endpoints

- New (6.2.2): `GET /compliance/retention-policies`, `GET /compliance/retention-runs`, `GET /compliance/erasure-requests`, `GET /compliance/legal-holds`
- Unchanged: `/users/{id}/legal-holds` (the user detail card keeps placing and lifting)

### Services

- `common.retention`: `RetentionPolicy` (`key()`, `order()`, `action()`, `period()`, `apply(RetentionContext)` → `RetentionResult`), `RetentionContext` (now, held user ids, batch size)
- `gdpr`: `RetentionJob`, `RetentionRuns`, `RetentionMetrics`, `RetentionProperties`, account and consent policies, `ComplianceController`/`ComplianceService` (6.2.2); `ErasureRunner` honours `origin`
- `notification`: `NotificationRetention`; `auth`: token, authorization and device policies; `audit`: partition policies

### Frontend

- `features/compliance/` lazy route `/compliance` with a permission guard (`compliance:read`), `ComplianceComponent` with three tab components, `ComplianceService`; navigation entry; i18n keys `compliance.*` in uk and en

### External systems

- Mailpit/Brevo (failure mail), Prometheus (metrics); MinIO only through the erasure contributors

## 7. Technical decisions

Inherited: ADR-022 (modules, a contract in `common` like `common.erasure`, `ModularityTest`), ADR-024 (append-only tables change only through guarded `SECURITY DEFINER` functions), ADR-021 (one instance; the advisory lock still guards an overlapping run), ADR-005 (committed migrations unchanged), PRD 6.1 decisions 9, 13, 15 (contributor order, mails through the registry, last administrator), scheduled jobs on `Europe/Kyiv`, tracker scope ("policies kept as properties").

**Proposed deviations and assumptions**

1. **Split of 6.2.1** (8 points) into 6.2.1 (job, runs, technical policies, 5) and a new 6.2.3 (accounts, consents, audit, 5): the second touches guarded tables and the erasure path and gets its own security review. Feature total 15 instead of 13; 6.2.3 is SCRUM-95 (#272).
2. **`users.status_changed_at`** (tracker deviation 4): a column set by a trigger on every status change, backfilled with `updated_at`, instead of searching the audit trail (the trail is redacted on erasure and partitioned). No screen sets `RETIRED` or `MEMORIAL` yet (parked, §2).
3. **Account retention is an erasure.** The job files a `RETENTION` request and the 03:30 erasure run executes it the next night, so one code path anonymises accounts, with holds, the last-administrator check, rollback and the audit redaction of 6.1.5. "Anonymise the profile, keep the awards" (US-012) is what that erasure does. No mails: an unverified address was never confirmed, and a former employee's address is usually gone.
4. **Unverified registrations** after 30 days are erased the same way: the row holds a name, an address and an acknowledgement with IP address. DATA_GOVERNANCE §2.1 gains the row.
5. **Scanned documents** have no user-defined period: the matrix row becomes "with the award; deleted with the award or the account (6.1.5)".
6. **System logs**: the matrix row becomes "application log files, 30 days / 1 GB (logback); container logs per the Epic 9 runbook"; the database keeps no system log.
7. **Legal hold scope**: a hold keeps the person's account, consent records and audit rows; technical rows (expired notifications, used tokens, expired authorizations, old devices) still go, since they prove nothing a hold protects. The hold set is read once at the start of the run.
8. **Consent retention**: "seven years from withdrawal" applies to the `ANALYTICS` chain whose latest row is a withdrawal (all its rows go, so no grant without its withdrawal remains); acknowledgements of a living account stay as proof; an erased account's rows go seven years after the erasure.
9. **Audit retention by partition**: whole monthly partitions are dropped once they end before the period (DATA_DICTIONARY §4.1 "partition by month for retention"); row deletion stays impossible. With partitions from 2026-01, nothing is dropped before 2033; the ITs create an old partition.
10. **Report** = the run row, the audit row, the compliance page and a mail only when a run does not complete; no daily report mail (it would train the officer to ignore it). No "run now": the cron is a property, and the manual run sets it to every minute.
11. **Permission `compliance:read`** for `GDPR_OFFICER` only. The system administrator sees failures in the log and the metrics; erasure requests name people and belong to the officer.
12. **Assumed periods** not in the governance doc: one-time tokens 30 days after use or expiry, authorizations 1 day after the last expiry, devices two years after last use, unverified accounts 30 days. All are properties and appear in the matrix.
13. **`retention_runs` are kept** without a period: they hold counts and policy keys only.
14. **Migration numbers** follow the merge order: V044 (6.2.1), V045 (6.2.3). `fn_create_audit_partition` gains a month parameter in `R__create_functions.sql` (the no-argument form stays and calls it with next month).
15. **Policy order** (`order()`): `notifications` 10, `one-time-tokens` 20, `authorizations` 30, `devices` 40, `pending-accounts` 50, `former-staff` 60, `consent-records` 70, `audit-partitions` 80, `audit-logs` 90.

## 8. Contract

### 8.1 OpenAPI stubs

Written to `docs/api/openapi.yml` in this PRD's PR with `x-status: planned` and the story in `description`; new tag `Compliance`.

| Operation | Response | Problems |
|-----------|----------|----------|
| `GET /compliance/retention-policies` (`listRetentionPolicies`) | 200 `RetentionPolicy[]` (`key`, `period`, `action`, `order`) | 401, 403 |
| `GET /compliance/retention-runs` (`listRetentionRuns`, `page`, `size`) | 200 `RetentionRunPage` of `RetentionRun` (`id`, `status`, `startedAt`, `finishedAt`, `results[]` of `RetentionResult`: `policy`, `affected`, `held`, `failed`, `error`) | 401, 403 |
| `GET /compliance/erasure-requests` (`listErasureRequests`, `status`, `page`, `size`) | 200 `ErasureRequestEntryPage` of `ErasureRequestEntry` (`id`, `person` `UserRef`, `origin`, `status`, `requestedAt`, `scheduledFor`, `postponedAt`, `cancelledAt`, `completedAt`) | 400 (`status` not in the enum), 401, 403 |
| `GET /compliance/legal-holds` (`listAllLegalHolds`, `active`, `page`, `size`) | 200 `LegalHoldEntryPage` of `LegalHoldEntry` (`LegalHold` fields + `person` `UserRef`) | 401, 403 |

### 8.2 Migration outlines

| File | Story | Content |
|------|-------|---------|
| `V044__retention_runs.sql` | 6.2.1 | `retention_runs (run_id BIGSERIAL PK, started_at TIMESTAMPTZ NOT NULL DEFAULT now(), finished_at TIMESTAMPTZ NULL, status VARCHAR(20) NOT NULL DEFAULT 'RUNNING', results JSONB NOT NULL DEFAULT '[]')`; `ck_retention_runs_status IN ('RUNNING','COMPLETED','PARTIAL','INTERRUPTED')`; `ck_retention_runs_finished CHECK ((status = 'RUNNING') = (finished_at IS NULL))`; `idx_retention_runs_started (started_at DESC)`; comments. `R__create_functions.sql`: `fn_create_audit_partition(p_month DATE)` |
| `V045__account_retention.sql` | 6.2.3 | `ALTER TABLE users ADD status_changed_at TIMESTAMPTZ`; `UPDATE users SET status_changed_at = updated_at`; `SET NOT NULL`, `DEFAULT now()`; `fn_users_status_changed()` + `trg_users_status_changed BEFORE UPDATE OF account_status`; `idx_users_status_changed (account_status, status_changed_at)`; `ALTER TABLE erasure_requests ADD origin VARCHAR(20) NOT NULL DEFAULT 'REQUEST'` + `ck_erasure_requests_origin IN ('REQUEST','RETENTION')`; `fn_consent_records_guard` re-created to pass a `DELETE` while `app.consent_retention` is `on`; `fn_purge_consent_records(p_before TIMESTAMPTZ, p_held BIGINT[]) RETURNS INTEGER` and `fn_drop_expired_audit_partitions(p_before TIMESTAMPTZ, p_held BIGINT[]) RETURNS JSONB` (`SECURITY DEFINER`, `SET search_path`, flag set and reset with `set_config(…, true)`); comments |

### 8.3 Mails

| Mail | To | Content (uk / en) |
|------|----|-------------------|
| Retention run not completed (6.2.1) | Active GDPR officers | Subject "Збереження даних: запуск № <id> не завершено" / "Data retention: run <id> did not complete"; the run's start, status (`PARTIAL` or `INTERRUPTED`), the failed policies by name, link to `/compliance`; no personal data |

## 9. Test plan

| AC | Unit | IT | FT | E2E / other |
|----|------|----|----|-------------|
| 1.1 | | ✓ migration IT: check constraints | | |
| 1.2, 1.3 | ✓ job with fake policies: order, batch loop, failure isolation, status | ✓ advisory lock skips a second run; interrupted marking | | |
| 1.4 | ✓ recipients, no mail when completed | | ✓ failing policy bean → mail in Mailpit | |
| 1.5–1.8 | ✓ cut-off arithmetic per policy | ✓ per policy against Testcontainers: deleted vs kept rows at the boundary | | |
| 1.9 | | ✓ partitions created, existing skipped | | |
| 1.10 | ✓ metrics | | | |
| 1.11, 2.7, 3.7 | | ✓ `ModularityTest` | | doc review |
| 3.1 | | ✓ trigger sets on change only; backfill; origin check | | |
| 3.2–3.4 | ✓ candidate selection, held skipped, re-check cancels | ✓ request filed; runner without mails; hold postpones | ✓ end to end with short periods: `PENDING` account erased, no mail | |
| 3.5 | | ✓ chain rules, erased accounts, held skipped, direct DELETE raises | | |
| 3.6 | | ✓ old partition dropped, held kept, default untouched, DELETE raises | | |
| 2.1–2.4 | ✓ service mapping, filters | ✓ queries | ✓ REST-assured: officer 200, admin and employee 403, paging, filters, 400 | |
| 2.5, 2.6 | ✓ components, guard, empty/error states | | | ✓ Playwright: officer opens the three tabs; employee gets `/forbidden`; screen check 360/1280, uk/en, light/dark |

Coverage 85 % lines; static clean. Security review for 6.2.1 (deletes), 6.2.3 (guarded functions, erasure path) and 6.2.2 (permission).

## 10. Manual verification

Draft; expected texts quote the planned wording and are checked against the merged i18n files at validation.

Preconditions: `docker compose up -d postgres redis mailpit minio clamav`; backend in `backend/` with `$env:APP_RETENTION_CRON='0 * * * * *'; $env:APP_ERASURE_CRON='30 * * * * *'; $env:APP_RETENTION_PENDING_ACCOUNTS='PT5M'; $env:APP_RETENTION_DEVICES='P30D'; ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`; frontend `npm start` on `http://localhost:4200`. Mailpit `http://localhost:8025`; psql `docker compose exec postgres psql -U postgres award_monitoring`; Swagger `http://localhost:8080/swagger-ui.html`. Seed accounts (password `Passw0rd-demo`, `@chnu.edu.ua`): `employee.fmi`, `admin`, `gdpr`. Fresh accounts R1–R5 are registered in the steps (department ФМІ; R1 left unverified, the others verified through Mailpit).

1. psql `\d retention_runs`, `\d users` (`status_changed_at`), `\d erasure_requests` (`origin`); `select tgname from pg_trigger where tgrelid = 'users'::regclass;`. Expected: the checks and `trg_users_status_changed`. (AC-1.1, 3.1)
2. Wait one minute. Expected: one `retention_runs` row `COMPLETED` with nine results; `RETENTION_RUN_COMPLETED` in `audit_logs`; partitions to three months ahead exist (`\dt audit_logs_*`). (AC-1.2, 1.3, 1.9)
3. psql: insert a notification for `employee.fmi` with `expires_at = now() - interval '1 day'`, a used one-time token from 40 days ago, an `oauth2_authorization` with every expiry two days ago, and set one device of `employee.fmi` to `last_used_at = now() - interval '40 days'`. Wait one minute. Expected: all four gone; the run's results count them; a live authorization (signed-in session) stays. (AC-1.5–1.8)
4. Sign in as `employee.fmi` from the device of step 3. Expected: the new-device mail in Mailpit. (AC-1.8)
5. Register R1 and do not verify it. Wait six minutes. Expected: a `RETENTION` request for R1 with `requested_at` = `scheduled_for`; at the next erasure run R1 is `DELETED` with the pseudonym; `ERASURE_COMPLETED` with `origin: retention`; Mailpit has no erasure mail for R1. (AC-3.2, 3.4)
6. Register R2 (verified) with an approved award (submit, `secretary.fmi` approves). psql `update users set account_status = 'RETIRED' where user_id = <R2>;` Expected: `status_changed_at` is now. psql `update users set status_changed_at = now() - interval '8 years' where user_id = <R2>;` (the trigger fires only on status changes). Wait two minutes. Expected: R2 erased; the award stays `APPROVED`, `PRIVATE`, by "Erased #<id>". (AC-3.1, 3.3, 3.4)
7. Register R3 and R4. psql: for R3 insert an `ANALYTICS` grant dated 9 years ago and a withdrawal dated 8 years ago (its only `ANALYTICS` rows); for R4 the same two plus a grant dated 1 year ago. Wait one minute. Expected: R3's two `ANALYTICS` rows gone, its `DATA_PROCESSING` row kept; R4 keeps all three; `consent-records` counts 2. (AC-3.5)
8. Register R5. psql `select fn_create_audit_partition('2018-01-01'); select fn_create_audit_partition('2018-02-01');`, insert one audit row dated 2018-01-15 with `user_id` R5 and one dated 2018-02-15 with `user_id` `employee.fmi`. As `gdpr` place a legal hold on R5. Wait one minute. Expected: `audit_logs_2018_02` dropped, `audit_logs_2018_01` kept; `audit-logs` `affected` 1, `held` 1. Lift the hold. Expected: the next run drops `audit_logs_2018_01`. (AC-3.6)
9. psql as the application user: `delete from consent_records where user_id = <R4>;` and `delete from audit_logs where log_id = (select max(log_id) from audit_logs);`. Expected: both raise. (AC-3.5, 3.6)
10. Prometheus `http://localhost:8080/actuator/prometheus`: `award_retention_runs_total{outcome="completed"}` and `award_retention_rows_total{policy="notifications"}` present. (AC-1.10)
11. As `gdpr`, the navigation shows "Compliance" → `/compliance`. Expected: "Retention" tab with the nine policies and their periods, the runs newest first, a run expanded shows the counts of steps 3–8. (AC-2.1, 2.2, 2.5)
12. "Erasure requests" tab. Expected: R1 and R2 with origin "Retention", status "Completed", persons shown as "Erased #<id>"; filter "Pending" empty. "Legal holds" tab, filter "All": R5's hold with both dates and reasons. (AC-2.3, 2.4, 2.5)
13. Swagger as `admin` and `employee.fmi`: the four `GET /compliance/*` → 403; as `gdpr` `GET /compliance/erasure-requests?status=X` → 400. (AC-2.1, 2.3)

### Detours

14. Direct URL `http://localhost:4200/compliance` as `employee.fmi`. Expected: `/forbidden`; no navigation entry. (AC-2.5)
15. Stop the backend (Ctrl+C) while a run is `RUNNING` (set `APP_RETENTION_BATCH_SIZE=1` and insert 200 expired notifications), start it again. Expected: the old row `INTERRUPTED` with `finished_at`; Mailpit the failure mail to `gdpr`; deleted notifications stay deleted, the rest go in the new run. (§5, AC-1.4)
16. Failing policy: psql as `postgres` `revoke delete on user_devices from <application role>;` and wait one minute. Expected: run `PARTIAL`, `devices` failed with the exception class, the other policies counted; one mail to `gdpr`; the "Retention" tab marks the policy. `grant delete on user_devices to <application role>;` Expected: the next run `COMPLETED`, no mail. (AC-1.3, 1.4, 2.5)
17. Hidden in state, retention request: R4 requests its own erasure in the privacy centre; then psql puts R4 to `RETIRED` and `status_changed_at` 8 years back as in step 6. Expected: no second request; R4's own request stays the only pending one and runs on its date. (§5)
18. Revoked mid-flow, account retention: restart the backend with the default erasure cron; register R6 and leave it unverified; after six minutes a `RETENTION` request exists. Verify R6 through Mailpit, then restart with `APP_ERASURE_CRON='30 * * * * *'`. Expected: at the next erasure run the request is `CANCELLED` without a mail; R6 stays `ACTIVE`. (§5)
19. Revoked mid-flow, legal hold: after a `RETENTION` request for a `RETIRED` account is filed, `gdpr` places a hold before the erasure run. Expected: `PENDING` with `postponed_at`, `ERASURE_POSTPONED` `origin: retention`, no mail; the "Erasure requests" tab shows "Postponed". (AC-3.4)
20. Revoked mid-flow, officer: `gdpr` has `/compliance` open while `admin` revokes `GDPR_OFFICER`. Expected: the next tab switch or filter ends in the sign-in redirect; after signing in `/compliance` answers `/forbidden`. Assign the role again. (AC-2.5)
21. Reload and Back on `/compliance` with a filter set. Expected: the page reads the server state again; the tab and filter are kept in the URL. (AC-2.5)
22. Two overlapping runs: set `APP_RETENTION_CRON='*/10 * * * * *'` with a batch size of 1 and many rows. Expected: only one run row at a time; the log notes the skipped start. (§5)
23. Mailpit stopped during a failing run (detour 16 with Mailpit down), then started. Expected: the mail arrives after the publication retry. (§5)

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| A policy deletes rows that should stay (wrong cut-off or column) | Low | High | Boundary ITs per policy; security review; manual run with inserted rows at both sides of the boundary |
| Account retention erases someone by mistake (status set wrongly) | Low | High | Seven-year period on `status_changed_at`, set by a trigger; legal hold; re-check under the row lock; the erasure runs a night later than the request |
| The guarded functions open a delete path for other code | Low | High | `SECURITY DEFINER` with fixed arguments, flag transaction-local; ITs prove a direct `DELETE` still raises |
| First run on the demo server deletes a large backlog at once | Medium | Low | Batches of 1000 in their own transactions; 04:00 |
| Mail noise for the officer | Low | Low | Mail only when a run does not complete (decision 10) |
| 6.2.3 grows (consent purge semantics, partition drop) | Medium | Medium | Split already made (decision 1); consent semantics fixed in decision 8 |

## 12. Definition of Done

- Every AC proven at the layers of §9; `.\tools\gate.ps1` green per story (unit, IT, FT, JaCoCo ≥ 85 %, static 0, frontend lint and tests); Playwright for 6.2.2
- Screen check of `/compliance` (three tabs) and the navigation entry
- Security review for every story
- `openapi.yml` without `x-status: planned` on the four operations; DATA_DICTIONARY §1.1, §1.4, §4.1, §4.2, §4.4, §5.1, new table; DATA_GOVERNANCE §2.1, §2.2; PRIVACY_BY_DESIGN §5.2; AUTHENTICATION_AUTHORIZATION §3.3; ADR-022; ADR-024; admin guide (compliance page); CHANGELOG `[Unreleased]`; tracker and BACKLOG
- Manual verification (§10) run and recorded; feature validated

# Feature 6.1: GDPR Consent Management and Data Subject Rights

> **Epic**: 6 — Compliance & Audit System (SCRUM-85)
> **Sprint**: 6 (2026-10-19 → 2026-10-25), 6.1.5 may spill into sprint 7
> **Points**: 26 (five stories)
> **Status**: Approved 2026-10-15
> **Author**: Stefan Kostyk
> **Governing docs**: EPIC-06 tracker (decisions of 2026-10-15, deviations 1–6, technical notes, risks 1–3), design note erasure-audit-trail of 2026-10-15 (option B "guarded redaction"), US-011, US-012 (legal hold scenario), roadmap Feature 6.1, PRIVACY_BY_DESIGN §4.2, §5, §8, §9.2, DATA_GOVERNANCE §2.1, §2.3, §4.3, DATA_DICTIONARY §1.1, §4.1, §4.2, §5, ADR-022 (modules), ADR-021 (one instance), AUTHENTICATION_AUTHORIZATION §3.3 (permission matrix), V009, V010, V013, V023, `RolePermissions`, `PersonalDataAssembler`, `PrivacyMails`, `RegistrationService`, `AuthorizationRevoker`, `DocumentSweeper`, `openapi.yml` (`/auth/register`, `/users/me/*`, `/users/{id}`)

## 1. Problem and personas

The system keeps names, addresses, awards and documents of university staff, yet a person cannot see on what terms. Registration shows no privacy notice and records nothing; `consent_records` exists since V010 and stays empty. The choices that already exist (award visibility, documents shown to colleagues, notification preferences, the data export) are scattered over three screens, and nothing shows a person what the system logged about them. There is no way to ask for the erasure of an account: GDPR Art. 17 is marked "designed" and nothing more. The GDPR officer cannot stop an erasure when the records are needed for a dispute or an inspection.

| Persona | Need in this feature |
|---------|----------------------|
| Anastasia, employee | Reads the privacy notice before registering and when it changes; sees all her privacy choices and her activity in one place; can ask for her account to be erased and change her mind within 30 days |
| Prof. Martynyuk, dean (reviewer) | Decisions he made stay valid when the employee concerned erases her account; claimed reviews of an erased colleague return to the queue |
| Galyna, GDPR officer | Every acknowledgement and consent is provable (version, time, source); can place a legal hold that blocks erasure; the audit trail keeps its legal value without keeping the erased person's identity |
| System administrator | Erasure never locks the university out of administration |

## 2. Scope

**In (this feature)**

- Versioned privacy notice in Ukrainian and English: current version as a property, texts as static pages per version and language, a public page that shows any version
- Registration requires acknowledging the current notice and records it in `consent_records`; a new version asks every signed-in person to acknowledge it again
- One optional consent, `ANALYTICS`, granted and withdrawn by the person; every change appended to `consent_records` with IP address and user agent
- Privacy centre at `/privacy`: notice status, consents with history, a summary of the existing choices with links, the data export (moved from the profile), the person's own activity log, and the erasure request
- Legal hold on an account, placed and lifted with a reason by the GDPR officer on the user detail page; blocks erasure (and retention in 6.2.1)
- Erasure request with password confirmation, a 30-day grace period, cancellation, and mails at request, one week before, on postponement and on completion
- Erasure across modules behind one contract: own data deleted, approved awards kept under a pseudonym, the account scrubbed to `DELETED`, the audit trail redacted by a guarded database function (design note option B), documents removed from MinIO
- Export format 1.6: legal holds (dates only) and erasure requests

**Out (where it goes)**

- Retention job, consent record retention (seven years from withdrawal), notification expiry and the GDPR officer's compliance page with runs, erasure requests and holds: Feature 6.2
- `SMS_NOTIFICATIONS` and `DOCUMENT_AI_PROCESSING` consents: SMS out since Epic 7, OCR deferred (tracker scope)
- CSV and PDF exports: JSON already meets Art. 20 (tracker deviation 6)
- Backups: none before Epic 9; the backup procedure then states how erased accounts are excluded
- Erasure requested by the GDPR officer on someone's behalf (letter, deceased person): not planned; `MEMORIAL` covers deceased staff
- Rectification by request: the profile already edits names and the address (Feature 1.3)
- Legal review of the notice text and the officer's sign-off: outside the software, recorded in the thesis as a limitation

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-86 (#252) | 6.1.1 Versioned privacy notice and consent records | 5 | no | — |
| SCRUM-87 (#253) | 6.1.2 Privacy centre in the profile | 5 | yes | 6.1.1 (consent endpoints) |
| SCRUM-88 (#254) | 6.1.3 Legal hold on accounts | 3 | no | — |
| SCRUM-89 (#255) | 6.1.4 Account erasure request with a 30-day grace period | 5 | no | 6.1.2 (privacy centre), 6.1.3 (hold check) |
| SCRUM-90 (#256) | 6.1.5 Erasure across modules with anonymised awards | 8 | no | 6.1.4 (requests, job) |

Every story has a backend and a UI part except 6.1.5 (backend only) and splits at the green backend gate. 6.1.2's UI can start from the stubs (§8.1) while its two read endpoints are built.

## 4. Acceptance criteria

**Audit action types added** (`entity_type` = `GDPR`, `entity_id` = the person concerned): `PRIVACY_NOTICE_ACKNOWLEDGED`, `CONSENT_GRANTED`, `CONSENT_WITHDRAWN`, `LEGAL_HOLD_PLACED`, `LEGAL_HOLD_LIFTED`, `ERASURE_REQUESTED`, `ERASURE_CANCELLED`, `ERASURE_POSTPONED`, `ERASURE_COMPLETED`, `AUDIT_REDACTED`.

### 6.1.1 Versioned privacy notice and consent records (SCRUM-86)

- **AC-1.1** Given V040 is applied, then `ck_consent_records_type` allows only `DATA_PROCESSING` and `ANALYTICS`, and an `UPDATE` of a `consent_records` row raises unless it only sets `ip_address` and `user_agent` to NULL; a `DELETE` raises (§8.2).
- **AC-1.2** Given `app.privacy.notice-version` = `1.0` and `app.privacy.notice-versions` lists each version with its publication date, when anyone calls `GET /api/v1/public/privacy-notice`, then `{ "version": "1.0", "publishedOn": …, "versions": [ … ] }` comes back without a token, newest first.
- **AC-1.3** Given the registration form, then a required checkbox "I have read the privacy notice" links to `/privacy-notice` (new tab), and the form sends `privacyNoticeVersion`; when `POST /auth/register` succeeds, then one `consent_records` row `DATA_PROCESSING`, the version, `is_granted` true, `granted_at`, IP address and user agent is written in the same transaction as the account, and `PRIVACY_NOTICE_ACKNOWLEDGED` is audited.
- **AC-1.4** Given a registration without `privacyNoticeVersion`, then 400; with a version other than the current one, then 422 `validation-failed` (field `privacyNoticeVersion`, code `outdated`) and no account is created.
- **AC-1.5** Given a signed-in person, when `GET /api/v1/users/me/consents` is called, then `ConsentOverview` returns the current notice version, the last acknowledged version and time, `acknowledgementRequired` (true when they differ or none exists), the current state of `ANALYTICS` (false when never chosen), and the history oldest first, without IP addresses.
- **AC-1.6** Given `acknowledgementRequired`, when `POST /api/v1/users/me/consents/notice-acknowledgement` with the current version is called, then a `DATA_PROCESSING` row is appended and audited and the overview comes back with `acknowledgementRequired` false; a repeated call appends nothing (200); an older version gives 422 `outdated`.
- **AC-1.7** Given a signed-in person, when `PUT /api/v1/users/me/consents/ANALYTICS` with `{ "granted": true }` (or false) is called, then a row is appended (`granted_at`, or `withdrawn_at` with `is_granted` false), `CONSENT_GRANTED` or `CONSENT_WITHDRAWN` is audited, and the overview comes back; the unchanged state appends nothing; `DATA_PROCESSING` gives 422 `validation-failed` (code `not-optional`), an unknown type 400.
- **AC-1.8** Given a signed-in person whose `acknowledgementRequired` is true, when any route of the shell other than `/privacy-notice`, `/privacy`, `/forbidden` and `/not-found` opens, then the app redirects to `/privacy-notice?acknowledge=true&returnUrl=…`, which shows the current version with "I have read the notice" and "Sign out"; acknowledging returns to `returnUrl`.
- **AC-1.9** Given `/privacy-notice` (signed in or not), then the current version shows in the interface language, a version selector loads older texts, and a switch of language reloads the text; the texts of version 1.0 exist in `public/privacy/1.0/uk.html` and `en.html` and name the controller, purposes, legal bases (legitimate interest, consent for `ANALYTICS`), recipients, retention periods (DATA_GOVERNANCE §2.1), rights and the contact of the GDPR officer.
- **AC-1.10** Given the export, then its `consents` section lists the rows of AC-1.3–1.7 (it was always empty before). Given the documentation, then DATA_DICTIONARY §4.2 describes the append-only rule and the two types, PRIVACY_BY_DESIGN §4.2 the reworded consent table (tracker deviation 3), USER_STORIES the corrected epic headings (deviation 5), and `openapi.yml` loses `x-status: planned` on the four operations.

### 6.1.2 Privacy centre in the profile (SCRUM-87)

- **AC-2.1** Given a signed-in person, when `GET /api/v1/users/me/privacy` is called, then `PrivacySummary` returns the number of own personal approved awards per visibility, the number of own documents shown to colleagues, the notification types with e-mail off, and the time of the last `DATA_EXPORT` (null if none).
- **AC-2.2** Given a signed-in person, when `GET /api/v1/users/me/activity?page=&size=` is called, then audit rows written by the caller (`user_id`) or about the caller (`entity_type` in `USER`, `AUTHENTICATION`, `AUTHORIZATION`, `GDPR` and `entity_id` = caller) come back newest first, page size 20 by default and clamped to 1–100; trigger rows (`INSERT`, `UPDATE`, `DELETE`) are left out; each entry has `action`, `occurredAt`, `entityType`, `entityId` and `byMe`, never values, IP address or user agent.
- **AC-2.3** Given a signed-in person, when the account menu entry "Privacy" is chosen, then `/privacy` opens (lazy-loaded) with cards in this order: privacy notice (acknowledged version and date, link to read), consents (`ANALYTICS` switch with its purpose, history in an expansion panel), your choices (awards per visibility with a link to the achievements list, documents shown, e-mail switched off for N types with a link to `/profile#notifications`), your data (export, moved from the profile with its messages), your activity (paged table), delete my account (filled by 6.1.4).
- **AC-2.4** Given the `ANALYTICS` switch, when it is toggled, then the PUT of AC-1.7 runs, the switch shows the stored state, the history gains a line, and a failure reverts the switch with an error line.
- **AC-2.5** Given the profile page, then the "My data" card is replaced by a link "Privacy and my data" to `/privacy`; the export works from the privacy centre exactly as before (rate limit message included).
- **AC-2.6** Given the activity table, then each action shows a translated label (unknown codes show the code), the time in the interface locale, and "by me" or "about me"; an empty log shows an empty state.
- **AC-2.7** Given both languages and both themes at 360 and 1280 px, then the page has no horizontal scroll, no missing keys and no axe violation of serious or critical level; the initial bundle does not grow (lazy route).

### 6.1.3 Legal hold on accounts (SCRUM-88)

- **AC-3.1** Given V041 is applied, then `legal_holds` exists with at most one active hold per user (partial unique index where `lifted_at` is null) (§8.2), and `RolePermissions` grants `legal-hold:manage` to `GDPR_OFFICER` only.
- **AC-3.2** Given a holder of `legal-hold:manage`, when `POST /api/v1/users/{id}/legal-holds` with a reason of 10–500 characters is called, then 201 returns the hold (reason, placed by, placed at) and `LEGAL_HOLD_PLACED` is audited with the reason length, not the text; an active hold gives 409 `legal-hold-active`; an erased account 409 `account-erased`; an unknown user 404.
- **AC-3.3** Given an active hold, when `POST /api/v1/users/{id}/legal-holds/{holdId}/lift` with a reason of 10–500 characters is called, then 200 returns the hold with lifted by, at and reason, and `LEGAL_HOLD_LIFTED` is audited; a lifted hold gives 409 `legal-hold-lifted`; a hold of another user 404.
- **AC-3.4** Given a holder of `legal-hold:manage`, when `GET /api/v1/users/{id}/legal-holds` is called, then `LegalHoldOverview` returns the holds newest first (and, from 6.1.4, `erasureScheduledFor`).
- **AC-3.5** Given a caller without `legal-hold:manage` (system administrator, dean, the person), then the three operations answer 403; without a token 401.
- **AC-3.6** Given the GDPR officer on `/admin/users/:id`, then a "Legal hold" card shows the active hold or "No legal hold", the history, and "Place hold" or "Lift hold" opening a dialog with a required reason; other roles do not see the card.
- **AC-3.7** Given the person's export, then format 1.5 lists `legal_holds` with `placed_at` and `lifted_at` only. Given the documentation, then DATA_DICTIONARY has `legal_holds`, AUTHENTICATION_AUTHORIZATION §3.3 the permission, and `openapi.yml` loses `x-status: planned` on the three operations.

### 6.1.4 Account erasure request with a 30-day grace period (SCRUM-89)

- **AC-4.1** Given V042 is applied, then `erasure_requests` exists with statuses `PENDING`, `CANCELLED`, `COMPLETED` and at most one `PENDING` row per user (§8.2).
- **AC-4.2** Given a signed-in person, when `POST /api/v1/users/me/erasure-request` with the current password is called, then 201 returns the request with `scheduledFor` = now + `app.erasure.grace-period` (default `P30D`), `ERASURE_REQUESTED` is audited, and a privacy mail names the date and links to `/privacy`.
- **AC-4.3** Given the request is refused, then: wrong password 403 `password-mismatch` (counts as a failed sign-in, as in the address change); locked account 423; a pending request 409 `erasure-pending`; an active legal hold 409 `legal-hold` (the reason is not disclosed); the caller is the only active `SYSTEM_ADMIN` 409 `last-administrator`. Nothing is written in any of these cases.
- **AC-4.4** Given a pending request, when `POST /api/v1/users/me/erasure-request/cancel` is called, then the row becomes `CANCELLED` with `cancelled_at`, `ERASURE_CANCELLED` is audited, a mail confirms it, and 204 is returned; without a pending request 409 `erasure-not-pending`.
- **AC-4.5** Given `GET /users/me/privacy`, then `erasure` holds the pending request (`id`, `requestedAt`, `scheduledFor`, `postponed`) or null; `GET /users/{id}/legal-holds` carries `erasureScheduledFor`.
- **AC-4.6** Given the erasure job (`app.erasure.cron`, default `0 30 3 * * *`, zone `Europe/Kyiv`), when a pending request is due within `app.erasure.warning-before` (default `P7D`) and not yet warned, then one warning mail goes out and `warning_sent_at` is set; a request created with less time left than the warning period gets no warning.
- **AC-4.7** Given the privacy centre without a pending request, then "Delete my account" explains what is erased and what is kept (approved awards under a pseudonym, redacted audit records, consent records for seven years) and opens a dialog with the password and a required checkbox; on success the card shows "Your account will be erased on <date>" with "Cancel the erasure"; refusals of AC-4.3 show their own message in the dialog.
- **AC-4.8** Given a pending request, then a banner in the shell names the date and links to `/privacy` on every page until it is cancelled.
- **AC-4.9** Given the export, then format 1.6 lists `erasure_requests`. Given the documentation, then DATA_DICTIONARY has `erasure_requests`, PRIVACY_BY_DESIGN §5.1 the request flow, the user guide the privacy centre, and `openapi.yml` loses `x-status: planned` on the two operations.

### 6.1.5 Erasure across modules with anonymised awards (SCRUM-90)

- **AC-5.1** Given the erasure job and a pending request with `scheduled_for` in the past and no active legal hold, when the job runs, then the row is locked (`FOR UPDATE SKIP LOCKED`) and every `ErasureContributor` runs in one transaction in its order (§7 decision 9, §8.4); on success the request is `COMPLETED` with `completed_at` and `ERASURE_COMPLETED` is audited with the counts each contributor reports.
- **AC-5.2** Given an active legal hold on a due request, then nothing is erased, the request stays `PENDING` with `postponed_at` set, `ERASURE_POSTPONED` is audited and one mail tells the person the erasure is postponed for a legal obligation; the next run after the hold is lifted erases.
- **AC-5.3** Given the erased account, then `users` keeps the row with `account_status` `DELETED`, `email_address` `erased-<id>@erased.invalid`, the pseudonym names (§7 decision 6), an unusable password hash and no `last_login_at`; `one_time_tokens`, `user_devices`, email change tokens and `oauth2_authorization` rows of the person are deleted, so a signed-in session ends at its next request (401) and sign-in fails; active roles end with `ROLE_REVOKED` (`reason: erasure`) and delegations given or received end with `DELEGATION_REVOKED` (`reason: erasure`), without notifications.
- **AC-5.4** Given the person's awards, then approved personal and unit awards stay with their versions, their visibility becomes `PRIVATE` and every document is deleted; awards in any other status are deleted with their requests, decisions, versions and documents; the MinIO objects of deleted documents are removed after the commit (`DocumentSweeper` removes leftovers); `award_versions.actor_id` of the person becomes null.
- **AC-5.5** Given the person as a reviewer, then claims they hold are released to the level's queue, their review templates are deleted, and their decisions on other people's awards stay, shown under the pseudonym.
- **AC-5.6** Given the person's notifications and preferences, then they are deleted; incomplete `event_publication` rows whose event carries the person's id are deleted; in other people's notifications that refer to an award the person reviewed or a delegation they were party to, every payload string equal to a name the person held (current or from `PROFILE_UPDATED` rows) becomes the pseudonym.
- **AC-5.7** Given the person's `consent_records`, then the rows stay with `ip_address` and `user_agent` null; `legal_holds` and `erasure_requests` rows stay.
- **AC-5.8** Given V043 is applied, then the rules `audit_logs_no_update` and `audit_logs_no_delete` are gone and a row trigger on `audit_logs` (inherited by every partition) raises on any `DELETE` and on any `UPDATE` unless the transaction-local setting `app.audit_redaction` is `on`, and then only `old_values`, `new_values`, `ip_address` and `user_agent` may change.
- **AC-5.9** Given `fn_redact_audit_subject(p_user_id, p_names, p_entities)` called by the audit contributor last, then the rows of §8.4 are redacted, one `AUDIT_REDACTED` row records the counts, the rows of the same transaction (scrub, role and delegation ends, `ERASURE_COMPLETED`) are redacted too, and a direct `UPDATE` outside the function still raises.
- **AC-5.10** Given the transaction fails in any contributor, then nothing is erased, the request stays `PENDING`, `award_erasure_runs_total{outcome="failed"}` grows, and the next run retries; given success, the `completed` outcome grows and, after the commit, a confirmation mail goes to the address captured before the scrub.
- **AC-5.11** Given an erased owner, then their approved awards appear in reviewers' and unit lists under the pseudonym, never on public or colleague pages, and the address of the erased account can register again as a new account.
- **AC-5.12** Given the documentation, then ADR-024 records the guarded redaction, DATA_DICTIONARY §4.1 the trigger and the function, PRIVACY_BY_DESIGN §5.2 the audit row, DATA_GOVERNANCE §2.3 the procedure as built (tracker deviation 2), ADR-022 the contributor contract, and the unused `fn_anonymize_user_data` is dropped from `R__create_functions.sql`.

## 5. Edge cases

| Case | Expected |
|------|----------|
| Notice version raised while a person is signed in | The next shell navigation reads the overview (cached per session, refreshed on sign-in and every 15 minutes) and redirects to acknowledgement |
| Registration form opened before a version change, sent after | 422 `outdated`; the form reloads the notice version and asks to tick the box again |
| Acknowledgement and ANALYTICS changed in two tabs | Append-only rows; each request reads the latest row under a row lock on the user, so an unchanged state appends nothing |
| A person never signs in again after a version change | No acknowledgement row; processing rests on legitimate interest, so nothing is blocked server-side (§7 decision 2) |
| Legal hold placed while an erasure is pending | Allowed; the officer sees `erasureScheduledFor`; at the due date the erasure is postponed (AC-5.2) |
| Hold lifted after the due date | The next nightly run erases; no new grace period |
| Erasure requested, then the address changed during the grace period | Allowed; mails go to the current address; the confirmation goes to the address at the moment of the scrub |
| Person signed in when the erasure runs | Next request 401; the refresh fails; the sign-in page refuses the old address |
| Erasure cancelled at the moment the job locks the row | The cancel waits for the lock and then finds `COMPLETED` (409 `erasure-not-pending`), or the job skips the row it cannot lock and the cancel wins |
| Only system administrator asks for erasure | 409 `last-administrator`; another administrator must be appointed first |
| Pending request of the person submitted on behalf of a unit | Deleted with the award (AC-5.4); the unit's secretary loses it from the queue; recorded in the user guide |
| A reviewer's claim on a request of an erased person | The request is deleted with the award; the claim disappears |
| A delegation partner of the erased person | The delegation ends without a notification; the audit row records it |
| Same name held by another person | Payload names are replaced only in notifications whose `reference_id` points at an award the erased person reviewed or a delegation they were party to (§8.4); a namesake elsewhere is untouched |
| MinIO down during erasure | The database part commits; object deletion fails and is logged; `DocumentSweeper` removes the orphans later |
| Job runs twice at once (second instance, manual trigger) | `SKIP LOCKED`; one erasure per request |
| Account already `DELETED` (erased earlier) | Hold refused with 409 `account-erased`; the account cannot sign in to ask again |
| Erased person re-registers with the same address | New account, new consent rows; no link to the old one |

## 6. Dependencies

### Tables

| Table | Change |
|-------|--------|
| `consent_records` (V010) | V040: type check reduced to two types, append-only trigger (6.1.1) |
| `legal_holds` | New, V041 (6.1.3) |
| `erasure_requests` | New, V042 (6.1.4) |
| `audit_logs` (V009) | V043: rules replaced by the guarded row trigger; `fn_redact_audit_subject` (6.1.5) |
| `users`, `user_roles`, `role_delegations`, `one_time_tokens`, `user_devices`, `oauth2_authorization`, email change tokens, `awards`, `award_requests`, `review_decisions`, `review_templates`, `award_versions`, `documents`, `notifications`, `notification_preferences`, `event_publication` | Written by the erasure contributors (6.1.5), no schema change |

### Endpoints

| Endpoint | State |
|----------|-------|
| `POST /auth/register` | Changed: `privacyNoticeVersion` (6.1.1) |
| `GET /users/me/export` | Changed: format 1.5 (6.1.3), 1.6 (6.1.4) |
| `GET /public/privacy-notice`, `GET /users/me/consents`, `POST /users/me/consents/notice-acknowledgement`, `PUT /users/me/consents/{type}` | New (6.1.1) |
| `GET /users/me/privacy`, `GET /users/me/activity` | New (6.1.2); `erasure` field (6.1.4) |
| `GET`, `POST /users/{id}/legal-holds`, `POST /users/{id}/legal-holds/{holdId}/lift` | New (6.1.3) |
| `POST /users/me/erasure-request`, `POST /users/me/erasure-request/cancel` | New (6.1.4) |

### Services

- `gdpr`: `ConsentService`, `PrivacyNoticeProperties`, `PrivacySummaryQueries`, `ActivityQueries` (audit read through the `audit` named interface), `LegalHoldService`, `ErasureRequestService`, `ErasureJob`, `ErasureRunner`; contract `ErasureContributor` in the named interface `gdpr :: erasure`; `PrivacyMails` gains the erasure mails
- Contributors: `auth` (tokens, devices, sessions, password), `user` (roles, scrub), `delegation`, `award` (awards, requests, claims, templates, versions), `document` (rows, objects after commit), `notification` (rows, preferences, publications, payload names), `gdpr` (consent context), `audit` (redaction, last)
- `RegistrationService` writes the acknowledgement; `AuthorizationRevoker` ends sessions

### Frontend

- `features/privacy` (new, lazy): `privacy-notice` page, `notice-acknowledgement` guard, privacy centre with its cards, erasure dialog, shell banner
- `features/auth/register`: checkbox and link
- `features/profile/profile`: "My data" card replaced by a link
- `features/admin/user-detail`: legal hold card and dialogs
- `public/privacy/1.0/{uk,en}.html`; i18n keys under `privacy.*`

### External systems

- Mailpit / Brevo: five new privacy mails (requested, warning, cancelled, postponed, completed)
- MinIO: object deletion after commit

## 7. Technical decisions

Inherited: ADR-022 (modules, named interfaces, `ModularityTest`), ADR-021 (one instance, so the nightly job needs no distributed lock; the row lock still guards a manual run), ADR-005 (Flyway; committed migrations unchanged), design note of 2026-10-15 (guarded redaction), tracker decisions of 2026-10-15 (hold in 6.1, request and execution split, contributors behind one contract), scheduled jobs on `Europe/Kyiv`.

**Proposed deviations and assumptions**

1. **Consent types** (tracker deviation 3). `DATA_PROCESSING` records the acknowledgement of a notice version; processing itself rests on legitimate interest (DATA_GOVERNANCE §2.1), and a required consent would not be freely given (Art. 7(4)). `PUBLIC_VISIBILITY`, `EMAIL_NOTIFICATIONS` and `SMS_NOTIFICATIONS` leave the check (per-award visibility, notification preferences, no SMS). `ANALYTICS` stays the one optional consent, with the purpose "my name may appear in rankings and comparisons of unit reports; without it I am counted only in totals"; Epic 5 must honour it (added to the tracker's technical notes). PRIVACY_BY_DESIGN §4.2 and DATA_DICTIONARY §4.2 change in 6.1.1.
2. **Acknowledgement is enforced in the client only.** The redirect of AC-1.8 informs (Art. 13); the API does not refuse requests of a person who has not acknowledged the latest version, as no processing depends on it. `/privacy` stays reachable so rights can be exercised without acknowledging.
3. **Notice texts as static pages per version** (`public/privacy/<version>/<lang>.html`), the version and dates as properties. A new version is a release anyway; a database table with an editor would add a screen nobody needs before the defense.
4. **Append-only consent history.** Each change is a new row; the current state is the latest row per type. V040 guards it with a trigger like `award_versions`, allowing only the erasure of IP address and user agent.
5. **Legal hold has its own permission** `legal-hold:manage` (GDPR officer). `consent:manage` stays unused: consents are the person's own choice, and the officer does not change them.
6. **Pseudonym** "Erased #<id>" (`first_name` `Erased`, `last_name` `#<id>`), language-neutral and the same in both interfaces; the e-mail `erased-<id>@erased.invalid` keeps the unique index and can never receive mail. DATA_GOVERNANCE §2.3's `ANONYMIZED_USER_<id>` and `ANONYMIZED` status are replaced (tracker deviation 2).
7. **Account usable during the grace period.** The person keeps working and can cancel from the privacy centre; a banner names the date. Locking at once would make cancellation need a separate signed link.
8. **Password confirmation, not a mailed link**, for the request, as for the address change; the mail at request time alerts the owner if someone else used the password.
9. **Contributor order**: `auth` 10, `user` 20 (roles), `delegation` 30, `award` 40 (returns the ids of deleted awards, requests and documents), `document` 50, `notification` 60, `gdpr` 70, `user` scrub 90, `audit` 100 (redaction with the names and ids gathered). The contract lives in the named interface `gdpr :: erasure`; if `ModularityTest` finds a cycle, it moves to `common` and ADR-022 says so.
10. **Approved awards stay, everything else goes** (PRIVACY_BY_DESIGN §5.2 "Awards: anonymisation", "Documents: full deletion"). Documents of approved awards are deleted too: they hold the person's certificates, and the award record remains the public record.
11. **Audit redaction scope** (§8.4, design note option B): values, not rows; action, entity, time and the actor id stay, so counts and the order of events survive. `password_hash` keys in `users` snapshots written before V022 are redacted for the erased person (design review E-6); the general purge of historical hashes stays with the design review list.
12. **Migration numbers** follow the merge order: V040 (6.1.1), V041 (6.1.3), V042 (6.1.4), V043 (6.1.5). The design note's "V040" for the redaction becomes V043.
13. **Completion mail through the registry.** The confirmation goes through the registry like the other privacy mails; its publication holds the captured address until it completes and is then deleted (`completion-mode: delete`), so no copy remains.
14. **Points**: 26 as tracked. If 6.1.5 grows, the notification and publication part moves to a follow-up story (tracker risk 1).

## 8. Contract

### 8.1 OpenAPI stubs

Written to `docs/api/openapi.yml` in this PRD's PR, each with `x-status: planned` and the story in `description`; new tag `Privacy`.

| Operation | Request | Response | Problems |
|-----------|---------|----------|----------|
| `GET /public/privacy-notice` (`getPrivacyNotice`, 6.1.1) | — | 200 `PrivacyNotice` | — |
| `POST /auth/register` (changed, 6.1.1) | `RegisterRequest` + `privacyNoticeVersion` (string, required) | as today | 422 `validation-failed` (`privacyNoticeVersion`: `outdated`) |
| `GET /users/me/consents` (`getConsents`, 6.1.1) | — | 200 `ConsentOverview` | 401 |
| `POST /users/me/consents/notice-acknowledgement` (`acknowledgePrivacyNotice`, 6.1.1) | `NoticeAcknowledgement` { `version` } | 200 `ConsentOverview` | 400; 401; 422 `validation-failed` (`outdated`) |
| `PUT /users/me/consents/{type}` (`setConsent`, 6.1.1) | path `type` (`ConsentType`); `ConsentChoice` { `granted` } | 200 `ConsentOverview` | 400; 401; 422 `validation-failed` (`not-optional`) |
| `GET /users/me/privacy` (`getPrivacySummary`, 6.1.2) | — | 200 `PrivacySummary` | 401 |
| `GET /users/me/activity` (`listMyActivity`, 6.1.2) | `PageParam`, `SizeParam` | 200 `ActivityPage` (`Page` of `ActivityEntry`) | 400; 401 |
| `GET /users/{id}/legal-holds` (`listLegalHolds`, 6.1.3) | — | 200 `LegalHoldOverview` | 401; 403; 404 `user-not-found` |
| `POST /users/{id}/legal-holds` (`placeLegalHold`, 6.1.3) | `LegalHoldReason` { `reason` 10–500 } | 201 `LegalHold` | 400; 401; 403; 404; 409 `legal-hold-active`, `account-erased`; 422 `validation-failed` |
| `POST /users/{id}/legal-holds/{holdId}/lift` (`liftLegalHold`, 6.1.3) | `LegalHoldReason` | 200 `LegalHold` | 400; 401; 403; 404 `legal-hold-not-found`; 409 `legal-hold-lifted`; 422 |
| `POST /users/me/erasure-request` (`requestErasure`, 6.1.4) | `ErasureConfirmation` { `password` } | 201 `ErasureRequest` | 400; 401; 403 `password-mismatch`; 409 `erasure-pending`, `legal-hold`, `last-administrator`; 423 `account-locked` |
| `POST /users/me/erasure-request/cancel` (`cancelErasure`, 6.1.4) | — | 204 | 401; 409 `erasure-not-pending` |

Schemas:

- `PrivacyNotice` { `version`, `publishedOn` (date), `versions`: [{ `version`, `publishedOn` }] }
- `ConsentType`: enum `DATA_PROCESSING`, `ANALYTICS`
- `ConsentOverview` { `notice`: { `currentVersion`, `acknowledgedVersion` (nullable), `acknowledgedAt` (nullable), `acknowledgementRequired` }, `consents`: [{ `type`, `granted`, `changedAt` (nullable) }], `history`: [{ `type`, `version`, `granted`, `at` }] }
- `PrivacySummary` { `awardsByVisibility` { `PRIVATE`, `UNIVERSITY`, `PUBLIC`: integer }, `documentsShown`, `emailOffTypes`: `NotificationType`[], `lastExportAt` (nullable), `erasure`: `ErasureRequest` (nullable, 6.1.4) }
- `ActivityEntry` { `action`, `occurredAt`, `entityType`, `entityId` (nullable), `byMe` }
- `LegalHold` { `id`, `reason`, `placedAt`, `placedBy` (`UserRef`), `liftedAt`, `liftedBy`, `liftReason` (nullable) }; `LegalHoldOverview` { `holds`: `LegalHold`[], `erasureScheduledFor` (nullable, 6.1.4) }
- `ErasureRequest` { `id`, `requestedAt`, `scheduledFor`, `postponed` (boolean) }

### 8.2 Migration outlines

| File | Story | Content |
|------|-------|---------|
| `V040__consent_records_append_only.sql` | 6.1.1 | Delete rows of the retired types (none outside tests); drop and re-create `ck_consent_records_type` with `DATA_PROCESSING`, `ANALYTICS`; `fn_consent_records_guard()` + `trg_consent_records_guard BEFORE UPDATE OR DELETE … FOR EACH ROW`: `DELETE` raises; `UPDATE` passes only when every column but `ip_address`/`user_agent` is unchanged and both new values are null (as `fn_award_versions_immutable`); `SET search_path`; comments |
| `V041__legal_holds.sql` | 6.1.3 | `legal_holds (hold_id BIGSERIAL PK, user_id BIGINT NOT NULL FK users RESTRICT, reason VARCHAR(500) NOT NULL, placed_by BIGINT NOT NULL FK users RESTRICT, placed_at TIMESTAMPTZ NOT NULL DEFAULT now(), lifted_by BIGINT NULL FK users RESTRICT, lifted_at TIMESTAMPTZ NULL, lift_reason VARCHAR(500) NULL)`; `ck_legal_holds_lifted CHECK ((lifted_at IS NULL) = (lifted_by IS NULL) AND (lifted_at IS NULL) = (lift_reason IS NULL))`; `uk_legal_holds_active ON (user_id) WHERE lifted_at IS NULL`; `idx_legal_holds_user (user_id, placed_at DESC)`; audit trigger `trg_legal_holds_audit`; comments |
| `V042__erasure_requests.sql` | 6.1.4 | `erasure_requests (request_id BIGSERIAL PK, user_id BIGINT NOT NULL FK users RESTRICT, status VARCHAR(20) NOT NULL DEFAULT 'PENDING', requested_at TIMESTAMPTZ NOT NULL DEFAULT now(), scheduled_for TIMESTAMPTZ NOT NULL, warning_sent_at, postponed_at, cancelled_at, completed_at TIMESTAMPTZ NULL)`; `ck_erasure_requests_status IN ('PENDING','CANCELLED','COMPLETED')`; `ck_erasure_requests_dates` (cancelled only when `CANCELLED`, completed only when `COMPLETED`); `uk_erasure_requests_pending ON (user_id) WHERE status = 'PENDING'`; `idx_erasure_requests_due ON (scheduled_for) WHERE status = 'PENDING'`; audit trigger; comments |
| `V043__audit_guarded_redaction.sql` | 6.1.5 | `DROP RULE audit_logs_no_update, audit_logs_no_delete`; `fn_audit_logs_guard()` + `trg_audit_logs_guard BEFORE UPDATE OR DELETE ON audit_logs FOR EACH ROW` (inherited by partitions): `DELETE` raises; `UPDATE` raises unless `current_setting('app.audit_redaction', true) = 'on'` and only the four value columns differ; `fn_redact_audit_subject(p_user_id BIGINT, p_names TEXT[], p_entities JSONB) RETURNS JSONB` (`SECURITY DEFINER`, `SET search_path`, sets the flag with `set_config(…, true)`, applies §8.4, writes `AUDIT_REDACTED`, resets the flag, returns the counts); comments. `R__create_functions.sql` drops `fn_anonymize_user_data` |

6.1.2 has no migration.

### 8.3 Mails

| Mail | Story | Trigger | Content |
|------|-------|---------|---------|
| Erasure requested | 6.1.4 | AC-4.2 | Date of erasure, what is kept, link to `/privacy` to cancel, "if this was not you, change your password" |
| Erasure warning | 6.1.4 | AC-4.6 | Date in seven days, link to cancel |
| Erasure cancelled | 6.1.4 | AC-4.4 | Confirmation |
| Erasure postponed | 6.1.5 | AC-5.2 | Postponed for a legal obligation, contact of the GDPR officer; no reason |
| Erasure completed | 6.1.5 | AC-5.10 | Done, what was kept under a pseudonym; no link |

All are privacy mails (always sent, bilingual, the HTML layout of 7.2.4) and leave no `notifications` row.

### 8.4 Audit redaction map (6.1.5)

| Rows | Redaction |
|------|-----------|
| `user_id` = the person | `ip_address`, `user_agent` → NULL |
| `entity_type` in `users`, `USER`, `AUTHENTICATION`, `AUTHORIZATION`, `GDPR` with `entity_id` = the person; trigger rows of `consent_records`, `notification_preferences`, `notifications`, `user_roles`, `role_delegations`, `one_time_tokens`, `legal_holds`, `erasure_requests` whose snapshot `user_id` (or `delegator_id`/`delegate_id`) = the person | In `old_values`/`new_values`: keys `email_address`, `email`, `new_email`, `old_email`, `first_name`, `last_name`, `password_hash`, `ip_address`, `user_agent`, `payload` → `"[erased]"`; `changed_fields` untouched |
| Rows of awards, requests and documents deleted at erasure (ids from the award contributor) | `old_values`, `new_values` → `{"erased": true}` |
| Trigger rows of other people's `notifications` whose payload names the person (§5 namesake rule) | The matching string values → the pseudonym |
| Kept as they are | Rows of approved awards and their decisions (the public record), counts, actions, entity ids, times, actor ids |

## 9. Test plan

| AC | Unit | IT | FT | E2E / other |
|----|------|----|----|-------------|
| 1.1 | | ✓ migration IT: check, guard raises, ip/ua nulling passes | | |
| 1.2–1.7 | ✓ version rules, overview, unchanged state | ✓ append under concurrent calls | ✓ REST-assured: public notice, register with/without/outdated version, ack, ANALYTICS, 422 cases, audit rows | |
| 1.8, 1.9 | ✓ guard, notice page, register checkbox | | | ✓ Playwright: register with the box, version bump via property → redirect → acknowledge; screen check |
| 1.10 | ✓ assembler | | ✓ export lists consents | doc review |
| 2.1, 2.2 | ✓ query mapping | ✓ activity filter (by me, about me, triggers out) | ✓ summary counts, paging, 401 | |
| 2.3–2.7 | ✓ cards, switch revert, activity labels | | | ✓ Playwright: menu → `/privacy`, toggle analytics, export; screen check 360/1280, uk/en, light/dark |
| 3.1–3.5 | ✓ service rules | ✓ partial unique index under two inserts | ✓ place, lift, 409s, 403 for admin/person, audit without reason text | |
| 3.6 | ✓ card and dialogs | | | ✓ Playwright: officer places and lifts; admin sees no card |
| 3.7 | ✓ assembler | | ✓ export 1.5 | doc review |
| 4.1–4.6 | ✓ refusals, scheduling with a fixed clock, warning window | ✓ one pending per user | ✓ request, cancel, 403/409/423, mails in Mailpit, job warning with a short period | |
| 4.7, 4.8 | ✓ dialog, banner | | | ✓ Playwright: request with a fresh account, banner, cancel |
| 4.9 | ✓ assembler | | ✓ export 1.6 | doc review |
| 5.1, 5.2, 5.10 | ✓ runner order, failure path, metric | ✓ due/skip-locked, hold postpones, failing contributor rolls back | ✓ job with a short grace: completed mail | |
| 5.3–5.7 | ✓ each contributor | ✓ per contributor against Testcontainers (rows deleted, kept, nulled; claim released; payload names) | ✓ end to end: erased account 401, sign-in refused, awards listed under pseudonym | |
| 5.8, 5.9 | | ✓ SchemaIT: direct UPDATE/DELETE raise on a partition; function redacts the map, own-transaction rows included, `AUDIT_REDACTED` | | |
| 5.11 | | ✓ public and colleague queries exclude | ✓ re-registration | |
| 5.12 | | ✓ `ModularityTest` | | doc review |

Coverage 85 % lines; static clean. Every story goes through the security review (consent records, permissions, password check, erasure, audit trigger).

## 10. Manual verification

Draft; expected texts quote the planned wording and are checked against the merged i18n files at validation.

Preconditions: `docker compose up -d postgres redis mailpit minio clamav`; backend in `backend/` with `$env:APP_ERASURE_GRACE_PERIOD='PT10M'; $env:APP_ERASURE_WARNING_BEFORE='PT5M'; $env:APP_ERASURE_CRON='0 * * * * *'; ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`; frontend `npm start` on `http://localhost:4200`. Mailpit `http://localhost:8025`; psql `docker compose exec postgres psql -U postgres award_monitoring`; Swagger `http://localhost:8080/swagger-ui.html`. Seed accounts (password `Passw0rd-demo`, `@chnu.edu.ua`): `employee.fmi`, `secretary.fmi`, `dean.fmi`, `admin`, `gdpr`. Fresh accounts E1, E2, E3 are registered in the steps (department ФМІ, verified through Mailpit).

1. psql `\d consent_records`, `\d legal_holds`, `\d erasure_requests`; `select tgname from pg_trigger where tgrelid = 'audit_logs'::regclass;`. Expected: two-type check, guard triggers on `consent_records` and `audit_logs`, the partial unique indexes; `select rulename from pg_rules where tablename = 'audit_logs';` empty. (AC-1.1, 3.1, 4.1, 5.8)
2. Signed out, open `http://localhost:4200/privacy-notice`; switch uk/en. Expected: version 1.0 with its date in both languages; the selector lists 1.0. (AC-1.2, 1.9)
3. Register E1 at `/register` without ticking the box. Expected: the form refuses with a message under the box. Tick it and register. Expected: psql `select consent_type, consent_version, is_granted, ip_address is not null from consent_records where user_id = <E1>;` → `DATA_PROCESSING 1.0 t t`; `PRIVACY_NOTICE_ACKNOWLEDGED` in `audit_logs`. (AC-1.3)
4. Swagger `POST /auth/register` with `privacyNoticeVersion` `0.9`. Expected: 422 `outdated`, no account. (AC-1.4)
5. Sign in as `employee.fmi` (seeded, no row). Expected: redirect to `/privacy-notice?acknowledge=true…`; "I have read the notice" returns to the start page; a second sign-in goes straight in. (AC-1.5, 1.6, 1.8)
6. Account menu → "Privacy". Expected: `/privacy` with the six cards in order; notice 1.0 acknowledged today; analytics off; your choices with the employee's award counts; activity lists the acknowledgement and the sign-ins. (AC-2.1–2.3, 2.6)
7. Turn analytics on, then off. Expected: two history lines; psql two rows (`is_granted` t, then f with `withdrawn_at`); `CONSENT_GRANTED`, `CONSENT_WITHDRAWN` audited. (AC-1.7, 2.4)
8. Download the data from the privacy centre. Expected: the file's `consents` lists the three rows; `/profile` shows the link "Privacy and my data" instead of the card; a second download within a minute shows the rate-limit message. (AC-1.10, 2.5)
9. As `gdpr` open `http://localhost:4200/admin/users` → E2 (registered now). Expected: "Legal hold" card "No legal hold"; place a hold with the reason "Inspection 2026/14 by the ministry". Expected: card shows it; psql `LEGAL_HOLD_PLACED` with `reasonLength`, no text. (AC-3.2, 3.4, 3.6)
10. As E2 on `/privacy` → "Delete my account", correct password, box ticked. Expected: "Your account cannot be deleted now because of a legal obligation…"; no `erasure_requests` row. (AC-4.3)
11. As `gdpr` lift the hold with a reason. Expected: history shows both; `LEGAL_HOLD_LIFTED`. As E2 request erasure again. Expected: 201; the card says "Your account will be erased on <today + 10 min>"; the banner shows on `/awards`; Mailpit has the request mail with the date and the link. (AC-3.3, 4.2, 4.5, 4.7, 4.8)
12. Wait for the job (5 minutes before). Expected: the warning mail once; `warning_sent_at` set. (AC-4.6)
13. As E2 "Cancel the erasure". Expected: banner gone, cancelled mail, row `CANCELLED`, `ERASURE_CANCELLED`. (AC-4.4)
14. Prepare E3: an approved personal award A (submit, `secretary.fmi` approves, shared `PUBLIC`, one document shown to colleagues), a draft B with a document, a pending award C claimed by `secretary.fmi`; `admin` assigns E3 `FACULTY_SECRETARY` at ФМІ, `employee.fmi` submits award D, and E3 claims D. E3 requests erasure and downloads the export. Expected: format 1.6 with the erasure request. (AC-4.9)
15. As `gdpr` open E3. Expected: "Erasure scheduled for <date>" on the card. Place a hold; wait past the due time. Expected: request `PENDING` with `postponed_at`; postponed mail; `ERASURE_POSTPONED`; E3 can still sign in. Lift the hold; wait one minute. (AC-4.5, 5.2)
16. Expected after the run: request `COMPLETED`; psql E3 `account_status DELETED`, `erased-<id>@erased.invalid`, names `Erased` `#<id>`; no `user_devices`/`one_time_tokens`/`oauth2_authorization` rows; roles ended; Mailpit the completed mail to E3's address. (AC-5.1, 5.3, 5.10)
17. psql and MinIO console: A stays `APPROVED`, `visibility` `PRIVATE`, no documents; B and C gone with their requests; the objects of both documents gone from the bucket. D is unclaimed and back in the queue of `secretary.fmi`. (AC-5.4, 5.5)
18. As `secretary.fmi`: the approved award list of ФМІ shows A by "Erased #<id>"; the public page `http://localhost:4200/public/achievements` and the colleague view do not list A. (AC-5.11)
19. psql: `select count(*) from audit_logs where user_id = <E3> and (ip_address is not null or user_agent is not null);` → 0; `select new_values from audit_logs where entity_type = 'users' and entity_id = <E3> order by created_at limit 3;` → names and address `"[erased]"`; one `AUDIT_REDACTED` row with counts; `consent_records` of E3 with null IP. (AC-5.7, 5.9)
20. psql as the application user: `update audit_logs set action_type = 'X' where log_id = (select max(log_id) from audit_logs);` and `delete from audit_logs where log_id = …;`. Expected: both raise. (AC-5.8)
21. Prometheus `http://localhost:8080/actuator/prometheus`: `award_erasure_runs_total{outcome="completed"}` is 1. (AC-5.10)

### Detours

22. Sign in as E3 with the old address. Expected: "Invalid e-mail or password" (no hint that the account was erased). Register a new account with E3's old address. Expected: works; a new `user_id`. (AC-5.3, 5.11)
23. Keep E3's session open in a second browser during step 16, then navigate. Expected: the sign-in page; no data of E3 loads. (AC-5.3)
24. Hidden in state, request: with a pending request, the privacy centre shows only "Cancel the erasure"; Swagger `POST /users/me/erasure-request` → 409 `erasure-pending`. (AC-4.3)
25. Hidden in state, cancel: without a pending request there is no cancel button; `POST …/erasure-request/cancel` → 409 `erasure-not-pending`; after step 16 (erased) the call is 401. (AC-4.4)
26. Hidden in state, hold: with an active hold, the card offers only "Lift hold"; `POST /users/{id}/legal-holds` → 409 `legal-hold-active`; lifting the same hold twice → 409 `legal-hold-lifted`; placing a hold on E3 after step 16 → 409 `account-erased`. (AC-3.2, 3.3)
27. Hidden in state, consent: `PUT /users/me/consents/DATA_PROCESSING` → 422 `not-optional`; the UI shows no switch for it. Acknowledge the current version again in Swagger → 200, no new row. (AC-1.6, 1.7)
28. As `admin` request erasure. Expected: 409 "You are the only system administrator…". As `admin` and `dean.fmi`, `GET /users/<E2>/legal-holds` → 403; as E2 the same → 403. (AC-3.5, 4.3)
29. Wrong password five times in the erasure dialog. Expected: the fifth answers 423 and the account is locked as after failed sign-ins. (AC-4.3)
30. Raise `app.privacy.notice-versions` with 1.1 (and texts copied), restart the backend with `employee.fmi` signed in. Expected: the next navigation redirects to the 1.1 acknowledgement; `/privacy` and `/privacy-notice` stay reachable without it; the API keeps answering. Open the registration form before the restart and send it after. Expected: 422 and the form asks again. (AC-1.6, 1.8, §5)
31. Revoked mid-flow, legal hold: `gdpr` has the "Place hold" dialog open for E2 while `admin` revokes the `GDPR_OFFICER` role. Expected: saving ends in the sign-in redirect (session revoked with the role); after signing in, `/admin/users/<E2>` shows no hold card; no hold was written. A second officer would keep full access (none seeded: assign `gdpr` again to check the card returns). (AC-3.5, 3.6)
32. Revoked mid-flow, reviewer: E3 (secretary) has a claimed review open when the erasure runs. Expected: E3's next action 401; `secretary.fmi` finds the request unclaimed and decides it. (AC-5.5)
33. Revoked mid-flow, delegation: `dean.fmi` delegates to E2 and E2 requests erasure; at the run the delegation ends. Expected: `dean.fmi` sees it ended on `/delegations`, no notification; `DELEGATION_REVOKED` with `reason: erasure`. (AC-5.3)
34. Stop MinIO during the run of another fresh account with a document. Expected: the erasure completes; the log shows the failed object deletion; after MinIO starts, `DocumentSweeper` removes the object at its next run. (§5)
35. Back after cancelling the erasure, and a reload of `/privacy` right after the request. Expected: the page reads the server state; no second request is sent. (AC-4.7)
36. Two tabs on `/privacy`: request erasure in one, then cancel in the other without reloading. Expected: the second tab's cancel works (204) or, if done first, the request tab shows 409 `erasure-pending` after the reload. (§5)

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| 6.1.5 touches every module and grows past 8 points | Medium | Medium | Notifications and publications move to a follow-up (decision 14); contributors tested one by one |
| Erasure deletes something that should stay (or the reverse) | Low | High | Keep/delete table in AC-5.4–5.7 and §8.4; ITs per contributor; security review; manual run on fresh accounts |
| The audit trigger blocks a path that wrote to `audit_logs` before | Low | Medium | `SchemaIT` and the full IT suite; `MalwareScanIT` teardown no longer deletes rows |
| Name matching in payloads misses or over-matches | Medium | Low | Restricted to notifications referring to the person's reviews or delegations (§5) |
| Notice text read as legal advice | Medium | Low | Labelled as the system's notice; legal review out of scope and named in the thesis |
| Acknowledgement redirect loops (stale overview cache) | Low | Medium | Cache refreshed on acknowledgement and sign-in; Playwright covers the loop |

## 12. Definition of Done

- Every AC proven at the layers of §9; `.\tools\gate.ps1` green per story (unit, IT, FT, JaCoCo ≥ 85 %, static 0, frontend lint and tests); Playwright for 6.1.1–6.1.4
- Screen check of `/privacy-notice`, `/register`, `/privacy`, the user detail card and the banner
- Security review for every story
- `openapi.yml` without `x-status: planned` on the twelve operations; DATA_DICTIONARY §4.1, §4.2, new tables; PRIVACY_BY_DESIGN §4.2, §5, §8, §9.2; DATA_GOVERNANCE §2.3; AUTHENTICATION_AUTHORIZATION §3.3; ADR-022; ADR-024; user guide (privacy centre, erasure); admin guide (legal hold); CHANGELOG `[Unreleased]`; tracker and BACKLOG
- Manual verification (§10) run and recorded; feature validated

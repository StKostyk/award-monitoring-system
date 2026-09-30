# Feature 1.3: User Profile Management

> **Epic**: 1 — User Management & Authentication (SCRUM-5)
> **Sprint**: 3–4 (2026-09-29 → 2026-10-11)
> **Points**: 10 (two stories; 1.3.2 notification preferences moved to Epic 7)
> **Status**: Done 2026-09-30 (validated, §12; fix story for F-1…F-4 pending)
> **Author**: Stefan Kostyk
> **Governing docs**: roadmap § Feature 1.3, US-011 (export part), PRIVACY_BY_DESIGN §3–§6, DATA_GOVERNANCE §2 and §4, COMPLIANCE_ASSESSMENT (rights of rectification and portability), AUTHENTICATION_AUTHORIZATION §6 and §9, DATA_DICTIONARY §1.1, §1.4, §4.1, §4.2, openapi.yml `/users/me`, Feature 1.1 PRD (verification, reset, sign-out everywhere), EPIC-01 tracker

## 1. Problem and personas

An employee can register, sign in and submit awards, but cannot see what the system holds about them, cannot correct a misspelt name without asking an administrator, and has no way to move the account to a new institutional address. GDPR gives every data subject the right to rectification (Art. 16) and to receive their data in a machine-readable form (Art. 20); the compliance assessment lists both as month-one obligations, and the awards that Feature 2.1 now stores make the export meaningful.

| Persona | Need in this feature |
|---------|----------------------|
| Anastasia, employee | See her profile, roles and department in one place; fix the transliteration of her surname; download everything the system holds about her |
| Prof. Martynyuk, dean | Change the sign-in address after the university renames the faculty's mail domain alias, without losing his roles and awards |
| GDPR officer | Evidence that rectification and export requests are honoured and audited |
| System administrator | Fewer name-correction tickets; a trace of every change in `audit_logs` |

## 2. Scope

**In (this feature)**

- Profile page with the user's data, editable first and last name, change of the sign-in address with confirmation from the new mailbox, change audit (1.3.1)
- Export of the user's personal data as one JSON file, audited and announced by email (1.3.3)
- Password hashes removed from the row snapshots the audit trigger writes for `users` (1.3.1, see D-5)

**Out (where it goes)**

- Notification preferences and phone number — Epic 7 with the notification channels (tracker decision 2026-09-28); no phone is collected before a channel uses it (data minimisation, PRIVACY_BY_DESIGN §4)
- Consent management, withdrawal and erasure requests (rest of US-011) — Epic 5 compliance; `fn_anonymize_user_data` exists and is wired there
- CSV and PDF exports (PRIVACY_BY_DESIGN §6.1) — PDF with the Epic 6 reports; JSON covers Art. 20
- In-app password change — the reset flow of Feature 1.1 covers it; revisit if the manual run shows the need
- Change of department — done by the faculty secretary through the membership confirmation (Feature 1.2 AC-2.7)
- Account deactivation and the revocation of delegations a deactivated user received (Feature 1.2 §5 known gap) — Epic 8 administration (decided 2026-09-29)

## 3. Stories

| Key | Story | Pts | Parallel | Depends on |
|-----|-------|-----|----------|------------|
| SCRUM-15 (#38) | 1.3.1 Profile information update | 5 | yes | Feature 1.1, 1.2 |
| SCRUM-17 (#40) | 1.3.3 GDPR data portability | 5 | no | SCRUM-15 (profile page), Feature 2.1 (awards) |

SCRUM-15 fixes the `/users/me` contract in `openapi.yml` first; the profile page can be built against it while the backend is in progress. The estimate of 1.3.1 grows from 3 to 5 points because the address change needs a confirmation flow and a migration.

## 4. Acceptance criteria

### 1.3.1 Profile information update (SCRUM-15)

- **AC-1.1** Given a signed-in user in any state that can sign in (with or without membership confirmation), when `/profile` is opened, then it shows the email address, first and last name, department with its faculty, current roles with validity, received delegations that are active, membership state, account creation and last sign-in dates; the user menu of the shell has «Мій профіль» / "My profile".
- **AC-1.2** Given `PATCH /api/v1/users/me` with `firstName` and/or `lastName`, when the values pass the registration rules (trimmed, 1–100 characters, letters of the Ukrainian and Latin alphabets, apostrophe `'` or `ʼ`, hyphen, space), then they are stored and the updated `User` is returned with 200; otherwise 422 with the field errors. Unknown properties (`email`, `organizationId`, `status`, `roles`) are rejected with 422, not ignored. A request that changes nothing answers 200 and writes no audit row.
- **AC-1.3** Given a stored name change, then a `PROFILE_UPDATED` row is written to `audit_logs` in the same transaction (`entity_type` `USER`, `entity_id` the user, old and new values of the changed fields only, `changed_fields`, IP, correlation id), and the shell header shows the new name at once without signing in again.
- **AC-1.4** Given `POST /api/v1/users/me/email-change` with `newEmail` and `currentPassword`, when the password is right, the address ends in `@chnu.edu.ua`, differs from the current one and belongs to no other account (case-insensitive), then an `EMAIL_CHANGE` one-time token (1 hour) holding the new address is created, any earlier unused `EMAIL_CHANGE` token of the user is invalidated, a link to `/confirm-email-change?token=…` is sent to the new address, `EMAIL_CHANGE_REQUESTED` is audited and the answer is 202. A wrong password answers 403 `password-mismatch` and counts as a failed sign-in for the lockout of Feature 1.1 AC-4; another domain 422 `institutional-email-required`; an address in use 409 `email-taken`; a second request within a minute 429.
- **AC-1.5** Given the link, when `POST /api/v1/auth/email-change/confirm` is called with the token, then the account's address becomes the new one (stored lower-case), the token is used, `EMAIL_CHANGED` is audited with old and new address, the user is signed out everywhere (authorizations deleted, sessions expired, not-before key set, as for a password reset), a notice goes to the old address naming the new one and telling the owner to contact the administrator if the change was not theirs, and the page says to sign in with the new address. A used or expired token answers 410 `token-invalid`; an address taken since the request 409 `email-taken`, and the account keeps the old address.
- **AC-1.6** Given the confirmed change, then sign-in works only with the new address; roles, delegations, awards and devices stay with the account (they reference `user_id`).
- **AC-1.7** Angular: `/profile` (auth guard) with the read-only data of AC-1.1, a name form (save disabled while pristine or invalid, server field errors under the fields), «Змінити адресу» dialog (new address, current password, hint that the link goes to the new mailbox), and `/confirm-email-change` (public) that confirms on load and shows the outcome for 200, 409 and 410; all texts in Ukrainian and English.
- **AC-1.8** Given any update of a `users` row, then the snapshot the audit trigger writes to `audit_logs.old_values`/`new_values` no longer contains `password_hash` (D-5).

### 1.3.3 GDPR data portability (SCRUM-17)

- **AC-3.1** Given a signed-in user, when `GET /api/v1/users/me/export` is called, then the answer is 200 `application/json` with `Content-Disposition: attachment; filename="award-monitoring-export-<yyyy-MM-dd>.json"` and `Cache-Control: no-store`, generated synchronously.
- **AC-3.2** The file follows PRIVACY_BY_DESIGN §6.2: `export_metadata` (export instant, user id, `format_version` `1.0`, GDPR article), `personal_data.profile` (address, names, department and faculty, status, created, last sign-in), `roles` (current and past, with validity and the organisation), `delegations` (given and received; the other party by name only), `awards` (every own award in every status including drafts, both languages, category, awarding organisation, date, status, timestamps), `documents` (metadata and API path of each document of those awards; empty before Epic 3), `consent_history` (all `consent_records` rows), `devices` (browser, OS, last IP, first and last seen), `activity_log` (the user's own application events: authentication, authorization, profile, GDPR — action, time, IP). IP addresses are the user's own data and are included (D-3).
- **AC-3.3** The file never contains the password hash, one-time tokens or their hashes, authorization-server records, the trigger's row snapshots, or personal data of other users beyond the name of a delegation's other party.
- **AC-3.4** Given an export, then `DATA_EXPORT` is audited (`entity_type` `GDPR`, entity id the user, section counts in `new_values`) and an email «Ваші дані експортовано / Your data was exported» with time, IP and browser goes to the account's address after commit. A second export within a minute answers 429.
- **AC-3.5** Angular: the profile page has a «Мої дані» / "My data" section explaining what the file contains and a «Завантажити мої дані» button that downloads the file; the button is disabled while the download runs; 429 shows «Забагато запитів. Спробуйте пізніше.».
- **AC-3.6** Given an account with 200 awards, then the export answers within 2 seconds on the development stack (US-011 performance target).

## 5. Edge cases

- Names with surrounding spaces are trimmed; an empty name after trimming is 422; a Latin transliteration is allowed (the university's English documents use it).
- `PATCH` from two tabs: the entity carries `version`; a stale write is not possible through the API because the body carries no version, so the later write wins — acceptable for the user's own names; a concurrent secretary change of the organisation is a different column and does not conflict (optimistic lock retried once, then 409).
- Address change requested twice with different targets: only the newest link works; the older answers 410.
- Address change confirmed from another browser where nobody is signed in: works, the confirm endpoint is public and relies on the token.
- The new address registers as a separate account between request and confirmation: confirmation answers 409 and changes nothing; the unique index on `LOWER(email_address)` backs the check.
- A `PENDING` account cannot sign in and so cannot request a change; a user locked after five wrong passwords (including wrong passwords in AC-1.4) is signed out by the lockout.
- The sign-in address is the principal name of the authorization server; ending every session at confirmation keeps no token alive with the old name (the not-before check covers access tokens; Redis-down window as in Feature 1.2 §5).
- The export of a user without roles (unconfirmed) or without awards has empty arrays, not missing keys.
- Export through nginx: the attachment header is passed through; the Angular client downloads it as a Blob, so the bearer token never appears in a URL.
- Mail delivery down: the address change answers 202 and the link is lost; the user requests again after a minute. The export notice is best effort after commit and never fails the download.

## 6. Dependencies

### Tables

| Table | Status | Change |
|-------|--------|--------|
| `users` | existing | none; names and address updated in place |
| `one_time_tokens` | existing | V022: purpose `EMAIL_CHANGE` (1 hour) added to `ck_one_time_tokens_purpose`; new nullable column `new_email_address VARCHAR(255)`, CK: present exactly when the purpose is `EMAIL_CHANGE`. DATA_DICTIONARY §1.4 in SCRUM-15 |
| `audit_logs` | existing | new application `action_type` values `PROFILE_UPDATED`, `EMAIL_CHANGE_REQUESTED`, `EMAIL_CHANGED`; `DATA_EXPORT` is already listed. V022 also replaces `fn_audit_trigger()` so `users` snapshots drop `password_hash` (D-5). DATA_DICTIONARY §4.1 |
| `awards`, `documents`, `user_roles`, `role_delegations`, `consent_records`, `user_devices` | existing | read by the export only |

### Endpoints

| Method and path | Status | Story |
|-----------------|--------|-------|
| `GET /api/v1/users/me` | existing | — |
| `PATCH /api/v1/users/me` | in `openapi.yml`, unimplemented; `UserUpdateRequest` loses `notificationPreferences` (Epic 7) and forbids other properties | SCRUM-15 |
| `POST /api/v1/users/me/email-change` | new | SCRUM-15 |
| `POST /api/v1/auth/email-change/confirm` | new, public | SCRUM-15 |
| `GET /api/v1/users/me/export` | new | SCRUM-17 |
| Problem types | `password-mismatch` (403), `institutional-email-required` (422), `email-taken` (409) and `token-invalid` (410), all existing from registration | SCRUM-15 |

### Services and libraries

- `UserProfileService` extended with the name update; `EmailChangeService` (request, confirm) reusing `OneTimeTokenService`, the lockout counter of 1.1.4, `AuthorizationRevoker` and the one-minute Redis throttle of the resend endpoint
- `PersonalDataExport` assembling the sections from existing repositories, with Jackson snake-case records matching §6.2
- Emails through `MailDelivery`: `email-change-link`, `email-changed-notice`, `data-exported` (Ukrainian and English in one message, as the existing mails)
- No new libraries

### Frontend

- Routes `/profile` (auth guard) and `/confirm-email-change` (public) under the shell; user-menu entry
- Components: profile page (data card, name form, address dialog, «Мої дані» section), confirm page; a `ProfileService` over `/users/me`; the shell's user signal updated from the PATCH response
- i18n keys under `profile.*` and `emailChange.*`

### External systems

None.

## 7. Technical decisions

| # | Decision | Reasoning | Source |
|---|----------|-----------|--------|
| D-1 | The sign-in address can change only through a link sent to the new mailbox, started with the current password; confirmation signs the user out everywhere | The address is the principal and the recovery channel; re-authentication blocks a hijacked session from redirecting the account; ending sessions removes tokens bearing the old name | AUTH §6, §9 |
| D-2 | The export is generated synchronously over the authenticated API and downloaded as a Blob | One user's data is tens of kilobytes; a background job and signed link (roadmap tasks) need the message-broker decision of Epic 7 and add a bearer-less URL to protect. **Proposed deviation** from the roadmap tasks | Roadmap 1.3.3, ADR-006 |
| D-3 | IP addresses appear unredacted in the export | Art. 15 and 20 cover the subject's own identifiers; §6.2 shows `[REDACTED]` for the activity log. **Proposed deviation**: §6.2 updated in SCRUM-17 | PRIVACY_BY_DESIGN §6.2 |
| D-4 | JSON only | Machine-readable is what Art. 20 requires; PDF belongs with the reporting engine | PRIVACY_BY_DESIGN §6.1 |
| D-5 | The audit trigger stops copying `password_hash` into `users` snapshots (new migration replacing `fn_audit_trigger()`) | The seven-year audit table currently holds every historical hash; profile edits would add more. Existing rows are left as they are (audit rows are immutable); DATA_GOVERNANCE §4 gets a note. **Proposed deviation** (a correction of V013 behaviour) | DATA_DICTIONARY §4.1, DATA_GOVERNANCE §4 |
| D-6 | Profile changes are audited by the application (`PROFILE_UPDATED` with changed fields only) in addition to the trigger row | The trigger row has no actor (`app.current_user_id` is never set) and no IP; the application row does | DATA_GOVERNANCE §4 |
| D-7 | Estimate of 1.3.1 raised from 3 to 5 points | Address change with confirmation, migration and two pages | This PRD |

**Proposed deviations from the docs** (applied in the story that touches them, after approval): D-2 (roadmap tasks of 1.3.3), D-3 (PRIVACY_BY_DESIGN §6.2), D-5 (audit trigger), `UserUpdateRequest` without `notificationPreferences`. D-5 and the request change were applied in SCRUM-15; D-2 and D-3 in SCRUM-17 (PRIVACY_BY_DESIGN §6.2, §6.3, §9.2).

## 8. Test plan

| AC | Unit | Slice | IT | FT | E2E |
|----|------|-------|----|----|-----|
| 1.1 | ✓ mapper | ✓ `@WebMvcTest` | | ✓ unconfirmed user reads own profile | ✓ profile page |
| 1.2, 1.3 | ✓ name rules (table), no-op detection | ✓ 422 field errors, unknown property | ✓ audit row, trigger row without hash (1.8) | ✓ rename via API | ✓ rename, header updates |
| 1.4 | ✓ request rules | ✓ controller | ✓ token row, older token invalidated | ✓ 202/403/409/422/429, lockout after five | ✓ dialog |
| 1.5, 1.6 | ✓ confirm rules | | ✓ address changed, sessions ended, race → 409 | ✓ Mailpit link → confirm → old address refused, new one signs in | ✓ confirm page outcomes |
| 3.1–3.3 | ✓ assembler sections, exclusions | ✓ headers | ✓ export of a seeded user with roles, delegation, awards, devices | ✓ download, no hash or token in the body | ✓ download |
| 3.4 | ✓ | | ✓ audit row | ✓ Mailpit notice, 429 | |
| 3.5 | ✓ component | | | | ✓ |
| 3.6 | | | ✓ 200 awards under 2 s | | |

Coverage target 85 % lines per `mvn verify`; static analysis clean; Playwright for every UI AC. The address-change spec uses a fresh account from `registerAndVerify` because it ends sessions.

## 9. Manual verification

Preconditions: `.\tools\dev-up.ps1` (backend `local` profile on `http://localhost:8080`, frontend `http://localhost:4200`, Mailpit `http://localhost:8025`). Seed accounts (password `Passw0rd-demo`): `employee.fmi@chnu.edu.ua` (`EMPLOYEE`, department 64), `dean.fmi@chnu.edu.ua` (faculty 9), `admin@chnu.edu.ua`. Swagger at `http://localhost:8080/swagger-ui.html`. psql: `docker compose exec postgres psql -U postgres award_monitoring`.

### After 1.3.1 (SCRUM-15)

1. Sign in as `employee.fmi@chnu.edu.ua`, open the user menu → «Мій профіль». Expected: `http://localhost:4200/profile` shows the address, names, «Кафедра алгебри та інформатики» with its faculty, role `EMPLOYEE` with its start date, «Членство підтверджено», account and last sign-in dates. (AC-1.1)
2. Change the last name to `Петренко-Коваль`, save. Expected: «Збережено», the header shows the new name without reloading; psql `select action_type, changed_fields, old_values, new_values from audit_logs where action_type = 'PROFILE_UPDATED' order by created_at desc limit 1;` → only `last_name`, old and new values, with IP. (AC-1.2, 1.3)
3. Enter `Петренко1` and `   ` in the name fields. Expected: save disabled with field messages. In Swagger `PATCH /api/v1/users/me` with `{"lastName": "X1"}` → 422 with a field error; `{"email": "x@chnu.edu.ua"}` → 422; `{"lastName": "Петренко-Коваль"}` again → 200 and no new audit row. (AC-1.2)
4. psql: `select new_values ? 'password_hash' from audit_logs where entity_type = 'users' order by created_at desc limit 1;` → `f`. (AC-1.8)
5. Register and verify `mover@chnu.edu.ua` (Feature 1.1 flow), sign in, open `/profile` → «Змінити адресу»: new address `mover.new@chnu.edu.ua`, wrong password. Expected: «Невірний пароль», nothing sent. With the right password: «Посилання надіслано на mover.new@chnu.edu.ua»; Mailpit has the link addressed to the new address and a warning «Запит на зміну адреси для входу» to `mover@chnu.edu.ua` naming the new address with a link to the password reset. (AC-1.4)
6. Open the link from Mailpit. Expected: «Адресу змінено. Увійдіть з новою адресою.»; Mailpit has a notice to `mover@chnu.edu.ua` naming the new address; the open app tab lands on the login page at its next click; signing in with the old address fails, with the new one succeeds and `/profile` shows it. psql shows `EMAIL_CHANGE_REQUESTED` and `EMAIL_CHANGED`. (AC-1.5, 1.6)
7. In Swagger as the mover request a change to `employee.fmi@chnu.edu.ua` → 409 `email-taken`; to `mover@gmail.com` → 422 `institutional-email-required`; two valid requests within a minute → second 429. (AC-1.4)
8. Switch the UI to English. Expected: every label of the profile page, dialog and confirm page in English. (AC-1.7)

### After 1.3.3 (SCRUM-17)

9. As `employee.fmi` open `/profile` → «Мої дані» → «Завантажити мої дані». Expected: file `award-monitoring-export-2026-…json` downloads; it contains the profile, the `EMPLOYEE` role, the Feature 2.1 awards including drafts, `devices` with the browser and IP, `activity_log` with `LOGIN_SUCCESS` and the `PROFILE_UPDATED` of step 2; searching the file for `password`, `$2a$` and `token` finds nothing. (AC-3.1–3.3, 3.5)
10. Mailpit has «Ваші дані експортовано / Your data was exported» with time, IP and browser; psql `select new_values from audit_logs where action_type = 'DATA_EXPORT' order by created_at desc limit 1;` → the section counts. Clicking the button again at once → «Забагато запитів». (AC-3.4)
11. As `dean.fmi` with an active delegation (Feature 1.2 §9 step 12) export → `delegations` lists it with the secretary's name only. As an unconfirmed newcomer export → empty `roles` and `awards` arrays. (AC-3.2, 3.3)

### Detours

12. Open `http://localhost:4200/profile` signed out → login page, then back on `/profile` after signing in. (AC-1.7)
13. Open the address-change link a second time, and a link older than the newest one → «Посилання недійсне або прострочене» (410). psql `update one_time_tokens set expires_at = now() - interval '1 minute' where purpose = 'EMAIL_CHANGE';` then open a fresh link → 410. (AC-1.4, 1.5)
14. Request a change to `race@chnu.edu.ua`, then register `race@chnu.edu.ua` as a new account, then open the link → 409, the account keeps its address. (AC-1.5, §5)
15. Open the confirmation link in a second browser where nobody is signed in → the change is confirmed. (§5)
16. Edit the name, stop the backend, click save → «Сервер недоступний», the form keeps the input; start the backend and save again → 200. (AC-1.2)
17. Let the access token expire on `/profile` (15 minutes, or set `expires_at` in session storage to the past), then save the name → silent refresh, 200. (AC-1.7, Feature 1.2 AC-4.6)
18. Five wrong passwords in the address dialog → the account is locked and signed out; the administrator email of Feature 1.1 arrives. (AC-1.4)
18a. Request a change, then reset the password from the warning email before opening the link → the link answers 410 and the address stays. (review finding, AC-1.4)
19. Press Back after the confirm page and reload it → 410 page, the address unchanged a second time. (AC-1.5)
20. Export in two tabs at once → one download, the other «Забагато запитів». (AC-3.4)
21. Request a change as the mover, then type a wrong password five times on the login page for the current address, then open the link. Expected after the fix story: the change is refused and the account stays locked; today the account moves and the new address signs in at once (F-1).
22. Sign in as the mover from a new browser so «Новий пристрій» reaches the current address, confirm an address change, then press «Це був не я» in that older email. Expected after the fix story: «Посилання недійсне»; today the moved account is signed out and its password scrambled (F-2).
23. Sign in as `employee.fmi` in one browser and open another user's address-change link there. Expected: the change is confirmed; note that `employee.fmi` is signed out locally and «Увійти» signs them back in without a password (F-3).
24. Kill the backend (or `docker compose stop postgres`) right after pressing «Надіслати посилання», start it again and repeat at once. Expected after the fix story: a new link; today «Забагато запитів» for a minute (F-4).

## 10. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| A hijacked session changes the address and locks the owner out | Account takeover | Current password required (D-1), notice to the old address, lockout counts wrong passwords, administrator can restore the address |
| Export leaks internal data (hashes, tokens, others' data) | GDPR breach | Explicit section assembler instead of dumping tables; FT asserts the absence of hashes and tokens (AC-3.3) |
| Historical `password_hash` values stay in old audit rows | Hashes kept for seven years | D-5 stops new ones; purging old rows needs a decision on audit immutability — raised at `/thesis-sync` |
| Export grows with Epic 3 documents and Epic 4 decisions | Slow download | Metadata only, no binaries; 2-second target tested with 200 awards (AC-3.6) |

## 11. Definition of Done

- `./mvnw verify` green (unit, slice, IT, FT), JaCoCo ≥ 85 % lines, Checkstyle/PMD/SpotBugs clean
- `npm run lint`, `npm run test:ci`, Playwright scenarios for AC-1.1, 1.3, 1.5, 1.7, 3.5
- Docs in the same PRs: `openapi.yml`, DATA_DICTIONARY §1.4 and §4.1, PRIVACY_BY_DESIGN §6.2, DATA_GOVERNANCE §4 note, AUTH §9 row for the address change, `CHANGELOG.md`, tracker rows, `BACKLOG.md`
- §9 walked through by the author after `/feature-validate`, including the detours

## 12. Validation (2026-09-30, `develop` at 0d30db5)

Gates on `develop`: `mvn verify` — 501 unit and slice tests, 144 integration and functional, 98.7 % lines (2142/2171), Checkstyle 0, PMD 0, SpotBugs 0; frontend lint clean, 248 Vitest; Playwright 37/37 after a wait for the organisation list in `role-assignment.spec` (it clicked the select before the options arrived and failed in 3 of 5 runs; 30/30 with the wait). `docker compose up -d --build app frontend` starts clean with a healthy backend; the four profile endpoints and `/users/me/export` of `/v3/api-docs` are in `openapi.yml`; every `*IT` applies the migrations, V022 included, to an empty database.

### AC evidence

| AC | Evidence | Result |
|----|----------|--------|
| 1.1 | `UserProfileServiceTest#ac11_theProfileNamesTheFacultyOfTheDepartment`, `UserControllerTest#ac13_meReturnsTheProfileOfTheTokenSubject`, `#ac2_6_meReportsAnUnconfirmedMembershipWithNoRoles`, `ProfileFlowFT#ac11_ac12_ac13_…`; `profile.component.spec` (ac11); E2E `profile.spec` (ac11…ac17) | pass |
| 1.2 | `ProfileNameRulesTest#ac12_*` (5), `UserProfileServiceTest#ac12_*` (3), `UserProfileEndpointsTest#ac12_*` (3), `ProfileChangeIT#ac12_aChangeOfNothingWritesNoAuditRow`, `ProfileFlowFT#ac11_ac12_ac13_…`, `OpenApiContractTest#ac12_ac14_…` | pass |
| 1.3 | `UserProfileServiceTest#ac12_ac13_storesTheNewNameAndAuditsOnlyTheChangedField`, `ProfileChangeIT#ac13_ac18_…`; `profile.component.spec` (ac13); E2E `profile.spec` (header follows) | pass |
| 1.4 | `EmailChangeServiceTest#ac14_*` (6), `ProfileChangeIT#ac14_*` (2), `UserProfileEndpointsTest#ac14_*` (3), `AuthenticationMailsTest#ac14_*`, `ProfileFlowFT#ac14_ac15_ac16_…`, `#edge_fiveWrongPasswordsLockTheAccountAndEndItsSessions`; `email-change-dialog.component.spec` (2) | pass (F-4) |
| 1.5 | `EmailChangeServiceTest#ac15_*` (2), `#edge_anAddressTaken*` (2), `ProfileChangeIT#ac15_ac16_…`, `AuthControllerTest#ac15_*` (2), `AuthenticationMailsTest#ac15_*`, `ProfileFlowFT#ac14_ac15_ac16_…`; `confirm-email-change.component.spec`; E2E `profile.spec` (ac14…ac17) | pass (F-1, F-2) |
| 1.6 | `EmailChangeServiceTest#ac15_ac16_…`, `ProfileChangeIT#ac15_ac16_…`, `ProfileFlowFT#ac14_ac15_ac16_…` (old address refused, new one signs in) | pass |
| 1.7 | `profile`, `email-change-dialog`, `confirm-email-change` component specs (200, 409, 410, no token); E2E `profile.spec` (all four, uk) | pass (F-3) |
| 1.8 | `ProfileChangeIT#ac13_ac18_aNameChangeIsAuditedWithTheChangedFieldAndNoHashInTheTriggerRow` | pass |
| 3.1 | `UserProfileEndpointsTest#ac31_*` (2), `DataExportServiceTest#ac31_ac34_…`, `DataExportFT#ac31_ac33_ac34_…`; E2E `profile.spec` (ac31…ac35) | pass |
| 3.2 | `PersonalDataAssemblerTest#ac32_*` (5), `DataExportIT#ac32_ac33_…`, `OpenApiContractTest#ac32_exportSchemaListsTheSectionsOfTheFile` | pass |
| 3.3 | `PersonalDataAssemblerTest#ac32_ac33_…`, `DataExportIT#ac32_ac33_theExportCarriesEverySectionAndNothingSecret`, `DataExportFT#ac31_ac33_ac34_…` (no hash or token in the body) | pass |
| 3.4 | `DataExportServiceTest#ac34_*` (4), `DataExportIT#ac34_…`, `PrivacyMailsTest#ac34_…`, `UserProfileEndpointsTest#ac34_…`, `DataExportFT#ac31_ac33_ac34_…` (Mailpit notice, 429) | pass |
| 3.5 | `profile.component.spec` (ac35, 3); E2E `profile.spec` (download once a minute) | pass |
| 3.6 | `DataExportIT#ac36_twoHundredAwardsExportWithinTwoSeconds` | pass |

### Edge cases (§5)

| Edge case | Evidence | Result |
|-----------|----------|--------|
| Trimmed names, empty after trim, Latin transliteration | `ProfileNameRulesTest#ac12_*` | covered |
| Concurrent writes of the names | `UserProfileServiceTest#edge_aConcurrentWriteIsRetriedOnce`, `#edge_aSecondConflictIsAnswered409` | covered |
| Two requests with different targets | `ProfileChangeIT#ac14_aNewRequestCancelsTheOlderLink`, `EmailChangeServiceTest#ac14_aOneHourLinkGoesToTheNewAddressAndOlderLinksStop` | covered |
| Confirmation from a browser with nobody signed in | `AuthControllerTest#ac15_theAddressChangeIsConfirmedWithoutAToken`; §9 step 15 | covered |
| New address registered before the confirmation | `ProfileChangeIT#edge_anAddressRegisteredBeforeTheConfirmationKeepsTheAccountWhereItWas`, `EmailChangeServiceTest#edge_anAddressTaken*` | covered |
| Lockout through the dialog; account no longer active | `EmailChangeServiceTest#edge_theFailureThatLocksTheAccountSignsItOutEverywhere`, `#edge_anAccountThatIsNoLongerActiveIsNotMoved`, `ProfileFlowFT#edge_fiveWrongPasswordsLockTheAccountAndEndItsSessions` | covered; lock from the sign-in form open (F-1) |
| Password reset during a pending change | `ProfileChangeIT#edge_aPasswordResetCancelsAPendingAddressChange` | covered |
| Sessions with the old principal name | `ProfileFlowFT#ac14_ac15_ac16_…` | covered |
| Export of a user without roles or awards | `PersonalDataAssemblerTest#ac32_edge_aPersonWithoutAnythingGetsEmptySectionsNotMissingOnes` | covered |
| Export through nginx as a Blob | E2E `profile.spec` (download through the dev-server proxy), `profile.component.spec` (attachment name); `http://localhost/api/v1/users/me/export` routed by nginx (401 problem without a token), no response header hidden | covered |
| Mail down | Export notice after commit (`DataExportServiceTest#ac31_ac34_…`); a failed export gives its claim back (`#ac34_anExportThatDoesNotCommitGivesItsClaimBack`) | covered; address-change claim open (F-4) |

### Security checklist

| OWASP | Control | Where |
|-------|---------|-------|
| A01 Broken access control | Every `/users/me` operation acts on the token subject only; the export reads the caller's rows; the confirm endpoint is public and relies on a one-time token | `UserController`, `DataExportService`, `EmailChangeService#confirm` |
| A02 Cryptographic failures | One-time tokens stored as hashes and redeemed once; no hashes, tokens or authorization records in the export; the trigger no longer copies `password_hash` (V022) | `OneTimeTokenService`, `PersonalDataAssembler`, V022 |
| A03 Injection | Name rules of registration; unknown properties refused with 422; bound parameters in `PersonalDataQueries` | `ProfileNameRules`, `PersonalDataQueries` |
| A04 Insecure design | Address change needs the current password and a link to the new mailbox; the older link is invalidated; confirmation ends every session; one request and one export per minute | `EmailChangeService`, `RequestThrottle` |
| A07 Authentication failures | Wrong passwords in the dialog count towards the lockout of Feature 1.1; a password reset cancels a pending change; the old address is told of the request and of the move | `LoginAttemptService`, `AuthenticationMails` |
| A09 Logging | `PROFILE_UPDATED` (changed fields only), `EMAIL_CHANGE_REQUESTED`, `EMAIL_CHANGED`, `DATA_EXPORT` with section counts, all with IP and correlation id | `AuditService` |

### Findings

Scenario review of the untested detours (§9 steps 21–24); none is covered by a test yet. Proposed for a fix story 1.3.4.

| # | Finding | Proposed fix |
|---|---------|--------------|
| F-1 | Five wrong passwords on the login page lock the typed address only (`LoginAttemptService`); confirming a pending address change moves the account away from the lock, and the new address signs in at once with a fresh failure count | `confirm()` refuses while the current address is locked, and the lock that the login form sets also cancels a pending `EMAIL_CHANGE` token; FT `edge_aLockFromTheSignInFormCancelsThePendingAddressChange` |
| F-2 | «Це був не я» links (`SECURITY_REVOKE`, 24 h) mailed to the old address still work after the move; whoever reads the old mailbox can sign the moved account out and scramble its password | `confirm()` also invalidates `SECURITY_REVOKE`; FT `edge_linksMailedToTheOldAddressStopWorkingAfterTheMove` |
| F-3 | The confirm page always drops the local session, also when a different user is signed in in that browser; «Увійти» then signs that user back in silently | Drop the local session only when the confirmed address belongs to the signed-in user (`confirm` answers the account id); component spec |
| F-4 | The address-change request claims the one-minute throttle before its writes; a request that does not commit leaves the user on 429 for a minute with no link sent. The export already gives its claim back | A shared `RequestThrottle.claimForTransaction` used by both services; unit test |

Checked and covered: a missing, empty or garbage token on `/confirm-email-change` (410, and 400 shows the failure page), a used link, 409/422/429, old sessions ending, the old address refused at sign-in, the export of an unconfirmed user. The server-down and expired-token detours of the profile form are covered by `profile.component.spec` and `auth.service.spec` and by §9 steps 16–17.

### Refactor sweep

No defects. Worth a `refactor(user)` PR together with the fix story: audit entity types `USER` and `GDPR` as constants on `AuditLog` (today `EmailChangeService` and `PersonalDataAssembler` import them from services); `AuditService.recordChange` duplicates the private `write`; the name rule duplicated between `register.component.ts` and `profile/name-rules.ts`; the `email-taken` and `institutional-email-required` problems built in two services; the email-change link lifetime as an `AuthProperties` value like the other links; functional-test setup (port, Mailpit, active user) moved into `AbstractFunctionalTest`; Redis key prefixes shared with the tests; named addresses instead of `OTHERS.get(n)` in `ProfileChangeIT`.

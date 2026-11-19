# Feature 2.5: Awards of people without an account

> **Epic**: 2 Award lifecycle management (SCRUM-20) · **Tracker**: `docs/epics/EPIC-02-AWARD-LIFECYCLE-STATUS.md`
> **Sprint**: 10 and 11 (2026-11-16 to 2026-11-29); 2.5.5 after Feature 9.4 · **Points**: 32
> **Status**: Approved 2026-11-16; 2.5.5 revised 2026-11-18 (column mapping instead of one sample layout);
> validated 2026-11-18, pending the manual run of §10 (steps 1-19 and 21-40)
> **Governing docs**: US-001 and FR-005 (account creation and profile linking), US-003, Feature 1.2 (registration,
> verification, membership confirmation, AUTH §9 table), Feature 4.1.0 (unit awards, `RecipientUnits`, submitter
> never reviews), Feature 6.1 (consent, privacy notice, erasure 6.1.5), Feature 7.1 (mail delivery),
> `DATA_GOVERNANCE.md` §1.2, §4.2, §4.3, `PRIVACY_IMPACT.md`, `DATA_DICTIONARY.md` §1.1 and §2.1, `openapi.yml`
> `POST /awards`, `GET /users`, `POST /auth/register`, the design-session decision of 2026-11-14

## 1. Problem and personas

Not every employee registers, and some never will, but their awards still belong in the university's record: a
department's totals and reports are wrong without them. Today a faculty secretary can enter awards only for
themselves or for a whole unit; an award of a colleague without an account cannot be recorded at all (Feature 2.1
left "submitting on behalf of someone else" out of scope).

| Persona | Need |
|---------|------|
| Faculty secretary, dean (Olena) | Record a colleague's award without waiting for the colleague to register |
| Employee without an account (Anastasia, before registration) | Be told that the university keeps their awards, and find them in their account when they register |
| Reviewer | Review such an award like any other, never one they entered themselves |
| Rector's office | Department and faculty totals that include every employee |

## 2. Scope

In (MVP):
- A secretary or dean adds a **person without an account** (name and department in their scope; the
  `@chnu.edu.ua` address when known). The person is a `users` row in the new state `UNCLAIMED`: no sign-in, no
  roles, a random password hash. A person with the same name in the same department is shown as a possible
  duplicate before saving.
- A person with an address receives an **invitation mail** that is also the GDPR Art. 14 notice (who entered the
  data, why, the rights, how to object) with a link to the registration page; without an address no mail goes
  until one is entered.
- The secretary edits the person while unclaimed (name, address, department), sends the invitation again, or
  removes the person.
- The award form offers a third recipient, «Колега без облікового запису»; the award is **owned by the person**.
  Secretaries and deans whose scope covers the person's department manage the person's drafts, submit them, attach
  documents and withdraw them; the submitter is the one who submitted, so that person never reviews it.
- Review mails and in-app notices of such an award go to the request's submitter while the owner is unclaimed.
- **Claim at registration**: registering with the person's address takes the prepared row over. After the address
  is verified the account is `ACTIVE`, holds the employee role in the prepared department, and every award entered
  for the person is in their own list; secretaries lose the right to manage them.
- **Link at membership confirmation**: a person without an address (or registered under another address) is joined
  to the new account by the secretary who confirms the registrant's membership, choosing among the department's
  people without an account; the awards move to the account in one transaction.
- **Import from a spreadsheet** (2.5.5, after Feature 9.4): people and their past awards from the faculties'
  spreadsheets, recorded as approved historical awards; the column layout follows a sample spreadsheet.
- Placeholders count in department and faculty totals; their awards stay `PRIVATE` and outside rankings (no
  analytics consent) until the person claims the account and chooses.

Out (deferred):
- Awards for an already registered colleague (they enter their own) — not planned.
- A person whose only known address is outside `@chnu.edu.ua` — the address field takes institutional addresses
  only; such a person is added without one.
- Merging two accounts that both signed in, or a placeholder into an account of another department — an admin story
  after the defense (design-review §L).
- An erasure request of a person who never registers: handled by the data protection officer by mail and the
  removal of 2.5.1 (drafts only); erasure of submitted awards needs the claim and 6.1.5 — Risk 3.
- A "this award is not mine" flag after the claim — the person withdraws a pending award or asks a reviewer for a
  correction (4.4) as today.

## 3. Stories

| # | Story | Pts | Parallel | Depends on |
|---|-------|-----|----------|------------|
| 2.5.1 | People without an account: add, change, invite, remove | 8 | no | - |
| 2.5.2 | Awards for a person without an account | 8 | no | 2.5.1 |
| 2.5.3 | Claim at registration | 5 | no | 2.5.1 |
| 2.5.4 | Link at membership confirmation | 3 | no | 2.5.1 |
| 2.5.5 | Import people and past awards from a spreadsheet | 8 | no | 2.5.2 |
| 2.5.6 | Normalise imported text to NFC (fix of step 40) | 2 | no | 2.5.5 |

2.5.1, 2.5.2, 2.5.4 and 2.5.5 have a backend and a UI part and split at the backend gate; 2.5.3 is backend with one
page change. 2.5.2, 2.5.3 and 2.5.4 can run in any order once 2.5.1 is merged. 2.5.5 follows Feature 9.4 and waits
for a sample spreadsheet of a faculty; its ACs are provisional until the sample fixes the columns.

## 4. Acceptance criteria

### 2.5.1 People without an account: add, change, invite, remove (SCRUM-150)

- **AC-1.1** (Add) Given a faculty secretary or dean (own or delegated role) and a department inside their scope, when
  they post `POST /api/v1/people` with first name, last name, the department and optionally an `@chnu.edu.ua`
  address, then 201
  answers the person with status `UNCLAIMED`, `addedBy` the caller and `invitedAt` now (null without an address); the row has a random password
  hash, no role, and a `PERSON_ADDED` audit row (actor, person, department).
- **AC-1.2** (Address taken) Given an address that already belongs to any account (also `PENDING` or another
  placeholder; `DELETED` addresses are free, 6.1.5), when the person is added, then 409 `email-taken` and nothing is
  stored; two concurrent adds of one address store one row.
- **AC-1.3** (Scope and input) Given a department outside the caller's scope, an inactive or non-department unit, a
  non-institutional address, or a caller without either role (an employee, the rector's office, an admin), then 422
  `validation-failed` (`organizationId` `out-of-scope`, `email` `institutional`) or 403 `access-denied`; names follow
  the registration rules (1–100 characters, trimmed).
- **AC-1.4** (Invitation) Given a person was added with an address, when the transaction commits, then one bilingual mail goes to the
  address naming the faculty secretary or dean who added it, the department, the purpose (the university's record of
  awards), the legal basis, the right to object, access and erasure, the contact of the data protection officer and
  a link to `/register?email=<address>`; an `INVITATION_SENT` audit row is written. Without an address no mail goes,
  `invitedAt` stays empty and the person page says «Запрошення не надіслано: адреса невідома».
- **AC-1.5** (Invite again) Given an unclaimed person in scope, when `POST /api/v1/people/{id}/invitation` is called,
  then the mail goes again and `invitedAt` moves; a second call within `app.people.invitation-interval` (default
  `PT1H`) answers 429 with `Retry-After`; for a claimed person, 409 `person-claimed`; for a person without an
  address, 409 `no-address`.
- **AC-1.6** (Change) Given an unclaimed person in scope, when `PUT /api/v1/people/{id}` changes name, address or
  department (with `version`), then the row changes, `PERSON_CHANGED` is audited with the changed field names, and a
  new or first address receives the invitation; an address cannot be removed once entered (422 `email`
  `required`); a stale version answers 409 `version-conflict`; a department outside the
  scope 422 as in AC-1.3; a claimed person 409 `person-claimed`.
- **AC-1.7** (Remove) Given an unclaimed person in scope whose awards are all drafts, when
  `DELETE /api/v1/people/{id}` is called, then the drafts are deleted, the row is scrubbed as an erased account
  (6.1.5: status `DELETED`, the address free again) and `PERSON_REMOVED` is audited; with any award outside `DRAFT`,
  409 `person-has-awards`.
- **AC-1.8** (No sign-in) Given an unclaimed person, when anyone signs in with the address, then the answer is the
  one for a wrong password (no lockout difference, no hint), password reset sends nothing, a role assignment answers
  422 `validation-failed` (`userId` `unclaimed`), and the person never appears as a reviewer, delegate or analytics
  ranking entry.
- **AC-1.9** (Directory and person page) Given the user list `/admin/users`, when a secretary opens it, then people
  without an account in the caller's scope are listed with the chip «Без облікового запису» and the status filter
  offers it; a secretary or dean sees the button «Додати колегу без облікового запису», which opens a dialog with the four
  fields; the person page `/admin/users/:id` shows when and by whom the invitation went, the person's awards and the
  actions «Змінити», «Надіслати запрошення ще раз», «Видалити» and «Додати нагороду», each only when allowed.
- **AC-1.10** (Possible duplicate) Given a person without an account with the same first and last name (case,
  apostrophes and surrounding spaces ignored) in the same department, when another one is added without
  `duplicateAcknowledged: true`, then 409 `possible-duplicate` lists the matches (id, name, address masked as
  `p***@chnu.edu.ua`, award count); the dialog shows them with «Це інша людина, додати» and links to the existing
  ones; with the flag the person is stored.

### 2.5.2 Awards for a person without an account (SCRUM-151)

- **AC-2.1** (Recipient choice) Given a secretary or dean on `/awards/new`, when they pick «Колега без облікового
  запису», then a search field lists the unclaimed people in their scope (`GET /api/v1/people?q=`, name or address, at
  least 2 characters); `/awards/new?person=<id>` preselects one.
- **AC-2.2** (Create) Given `POST /awards` with `recipientUserId` of an unclaimed person in scope, then the draft is
  owned by the person, `organization_id` is the person's department, and the response carries
  `ownerWithoutAccount: true` and `managedByCaller: true`; `recipientUserId` together with `recipientOrganizationId`,
  or a person who is out of scope, claimed or unknown, answers 422 `validation-failed` (`recipientUserId`
  `out-of-scope`).
- **AC-2.3** (Manage) Given a draft or pending award of an unclaimed owner, when a secretary or dean whose scope covers
  the owner's department edits, deletes, uploads a document, submits or withdraws it, then each works as for the
  owner (same rules and 409s); `award_requests.submitter_id` is the caller; the version and the audit rows name the
  caller as actor; any other caller gets the answers of today (404 or 403).
- **AC-2.4** (Never one's own review) Given an award that a secretary submitted for a person, when the submitter or
  their delegates look at the review queue, then the award is not offered to them (submitter rule of 4.1.0), and the
  duplicate check compares with the person's awards.
- **AC-2.5** (Notices) Given an award of an unclaimed owner, when a reviewer approves, returns, rejects, passes on,
  corrects it or it becomes overdue, then the mail and the in-app notice go to the request's submitter, naming the
  person («Нагорода колеги Іваненко Петро …»); nothing is sent to the person's address.
- **AC-2.6** (Visibility and figures) Given an approved award of an unclaimed owner, then its visibility is
  `PRIVATE` and `PUT /awards/{id}/visibility` answers 403 for anyone but the owner; the award counts in unit,
  faculty and university totals, and is left out of rankings and the achievements pages.
- **AC-2.7** (Award page) Given such an award, when the secretary opens `/awards/:id`, then a chip «Без облікового
  запису» stands next to the owner's name and the edit, submit, withdraw and delete actions follow AC-2.3; the
  person page lists the award (AC-1.9).

### 2.5.3 Claim at registration (SCRUM-152)

- **AC-3.1** (Take over) Given an unclaimed person, when someone registers with that address, then the same row
  becomes `PENDING` with the new password, keeps the prepared name and department, and a verification link is sent
  as for any registration; the answer is the one of a new registration (no hint that the address was prepared), and a
  `REGISTRATION_REPLACED` audit row with `claim: true` is written.
- **AC-3.2** (Verify) Given that pending row, when the address is verified with the registration password, then it is
  `ACTIVE`, holds the `EMPLOYEE` role in the prepared department (the person's adding counts as the membership
  confirmation), `ACCOUNT_CLAIMED` is audited with the number of awards, and the verification page says «Адресу
  підтверджено. На вас уже чекають нагороди: N.» when N > 0.
- **AC-3.3** (Rights move) Given a claimed account, then its awards are in the person's own list, the person may
  change visibility, withdraw and edit drafts, review notices go to the person, and secretaries' edit, submit and
  withdraw calls answer as for any other employee's award; the person page shows the person as a regular user.
- **AC-3.4** (Abandoned claim) Given a claim whose verification link ran out unused, then the row is still managed
  by secretaries as unclaimed (status `PENDING` with `added_by_id` set counts as without an account), and a new
  registration with the address replaces it as in Feature 1.2.
- **AC-3.5** (Name and department later) Given a claimed account, when the person wants another name or department,
  then the profile name edit and the department change of Feature 1.2 apply; the registration form's name and
  department are not used for a prepared row (deviation D-3).

### 2.5.4 Link at membership confirmation (SCRUM-153)

- **AC-4.1** (Candidates) Given a verified registrant without a role and a secretary or dean confirming the
  membership in «Підтвердити членство», when a department is chosen, then the dialog lists the people without an
  account of that department (`GET /api/v1/people?organization=<id>`), those with the registrant's last name first,
  with name, masked address and award count, under «Це та сама людина»; nothing is preselected.
- **AC-4.2** (Link) Given a candidate is chosen, when the confirmation is saved, then
  `POST /api/v1/people/{id}/link` with `userId` and the person's `version` moves every award of the person to the
  account in one transaction (owner, the open requests keep their submitter), grants `EMPLOYEE` in the department as
  the confirmation does, scrubs the person as in AC-1.7, and writes `ACCOUNT_LINKED` (person, account, award count);
  the account's name and address stay; the person sees the awards in their list on the next load.
- **AC-4.3** (Refusals) Given a person or account outside the caller's scope, a claimed person, an account that is
  not `ACTIVE`, or the account's own placeholder, then 422 `validation-failed` (`userId` or person) or 404; a stale
  version answers 409 `version-conflict`; a person linked or removed meanwhile answers 404 and the dialog reloads
  the candidates.
- **AC-4.4** (Already a member) Given an account that already holds a role, when its page is opened by a secretary
  in scope, then «Приєднати нагороди колеги без облікового запису» offers the same link without a new role.

### 2.5.5 Import people and past awards from a spreadsheet (SCRUM-154)

The faculties' spreadsheets differ (risk 7), so the import maps the columns instead of fixing one layout: the
columns are guessed from the Ukrainian or English headers and the caller confirms or changes the mapping. Required
fields: the full name (or surname and first name), the title and the date; optional: department, level, awarding
organisation, address (only to tell apart people of one name) and notes. The department, level and awarding
organisation of rows that leave them empty come from the mapping. Formats read: a full name «Прізвище Ім'я [По
батькові]» or with initials («Петренко І. П.», matching one member only); dates `dd.MM.yyyy`, `yyyy-MM-dd`, a month
`MM.yyyy` (its first day), a year (1 January) or an Excel date; levels by the stems of their names («всеукраїнський»,
«обласна», «International»). A template workbook with the recognised columns can be downloaded.

- **AC-5.1** (Upload, mapping and preview) Given a secretary or dean, when an `.xlsx` or `.csv` file of at most
  2 MB and 1,000 rows is uploaded on `/admin/import`, then nothing is stored yet, the columns are mapped from the
  headers and can be changed, and a preview lists every row with the matched person (an account or a person without
  an account of the department, by name), the category of its level, and its problems (a department unknown or
  outside the scope, a missing name, title, date, level or awarding organisation, an unreadable or future date, two
  people of the name, a probable duplicate of an approved award, a row of the caller or of who lent them the role).
- **AC-5.2** (Import) Given a preview without blocking problems, when «Імпортувати» is pressed, then in one
  transaction people missing from the department are added without an address (AC-1.1), each row becomes an
  `APPROVED` award with `source = IMPORT`, no review request and an `AWARD_IMPORTED` audit row naming the file and
  the row; a repeated import of the same file adds nothing (rows are matched with approved awards by person, title
  and date).
- **AC-5.3** (Ownership) Given an imported award of an account, then the account's owner sees it marked «Імпортовано»
  and may hide or correct it through a reviewer (4.4) like any approved award; imported awards of people without an
  account follow AC-2.6.
- **AC-5.4** (Refusals) Given a file over the limits, of another type, or with rows outside the caller's scope, then
  422 names the file or the rows, and nothing is stored.

### 2.5.6 Normalise imported text to NFC (SCRUM-160)

Fix of manual step 40 (finding F-1): some macOS and older Excel exports save decomposed letters («й», «ї» as a base
letter and a combining mark).

- **AC-6.1** Given an uploaded file, then its headers and cells (names, titles, department, awarding organisation,
  notes) are read in Unicode normalisation form C before they are matched and stored.
- **AC-6.2** Given an NFD file whose row repeats an approved NFC award of the same person and date, then the preview
  skips the row as `already-stored`, and an NFD name matches the NFC person of the department.

## 5. Edge cases

- A person moves to another department while unclaimed (AC-1.6): drafts follow, pending and approved awards keep
  their `organization_id` from submission (2.1 rule); the old department's secretary loses the right to manage.
- A secretary's role ends or is narrowed while they edit a person's draft (dialog open): the next save answers 403
  `access-denied`, the draft stays; colleagues at the same level whose scope covers the department can continue.
- A delegation of `FACULTY_SECRETARY` lets the delegate add people and manage their awards in the delegated scope;
  revoking it does the same as above.
- The person claims while a secretary has the draft open: the secretary's save answers 403 (no longer managed).
- The person registers under another address: two rows until the secretary links them at the membership
  confirmation or on the account page (2.5.4).
- Two people of one department share a name: the duplicate warning (AC-1.10) and the masked address and award count
  in the link dialog (AC-4.1) tell them apart; a wrong link is undone only by a reviewer correction or an admin
  (Risk 6).
- A claim with the link still valid blocks a second registration for 24 h (Feature 1.2 C-13).
- Bounced invitation: retried and logged as in 7.1; the delivery state is not kept (§7), so the person page shows
  when the invitation went, not whether it arrived.
- Unit erasure, legal hold and retention: a placeholder is a `users` row; the legal-hold and former-staff policies
  (6.2) apply unchanged and never pick a placeholder (status `UNCLAIMED` is not a former-staff status).
- Concurrency: two secretaries change one person (version 409); add and remove at once (row lock on the person).

## 6. Dependencies

### Tables
- `users`: new status `UNCLAIMED`, new columns `added_by_id`, `invited_at` (V051, 2.5.1); a person without a known
  address holds the placeholder `unclaimed-<uuid>@unclaimed.invalid`, so `email_address` stays NOT NULL.
- `awards`: new column `source` (V054, 2.5.5).
- `award_requests`, `audit_logs`, `notifications`, `user_roles`: unchanged.

### Endpoints
- New: `POST/GET /api/v1/people`, `GET/PUT/DELETE /api/v1/people/{id}`, `POST /api/v1/people/{id}/invitation`
  (2.5.1); `POST /api/v1/people/{id}/link` (2.5.4); `POST /api/v1/award-imports/preview`,
  `POST /api/v1/award-imports` (2.5.5).
- Changed: `POST/PUT /awards` (`recipientUserId`), `Award` (`ownerWithoutAccount`, `managedByCaller`) (2.5.2);
  `GET /users` (`UNCLAIMED` listed), `AccountStatus` enum, `POST /users/{id}/roles` (422 `unclaimed`) (2.5.1);
  `POST /auth/register`, `POST /auth/verify-email` (claimed count) (2.5.3).

### Services
- `user`: `RegistrationService` take-over and verification through the interface `WaitingAwards` (defined in
  `user`, implemented in `award`); `User.hasMailbox()`; role assignment refuses `UNCLAIMED`.
- `award`: `PeopleController`, `People`, `PersonRules`, `PersonInvitations`, `PersonRemoval` (the erasure chain
  of 6.1.5 run at once), `PersonLink` (2.5.4); `AwardOwnership` (manage rule), `AwardInputRules`
  (`recipientUserId`), `RecipientUnits` (people scope); notice events carry the submitter for an unclaimed owner.
- `common`: `MailDelivery` drops every `*.invalid` recipient.
- `notification`: `InvitationMails`; decision, correction and overdue mails read the recipient from the event.

### Frontend
- `features/admin`: user list button, add dialog, status chip and filter, person page actions and award list.
- `features/awards`: recipient choice and person search, `managedByCaller` instead of `isOwnAward` for actions,
  owner chip.
- `features/auth` verify page: the claimed count.
- `features/admin` confirm-membership dialog and user page: the link (2.5.4); `/admin/import` page (2.5.5).

### External systems
- Mail (Mailpit locally, Brevo on the demo server) for the invitation.
- Spreadsheet reading (2.5.5): fastexcel-reader for `.xlsx` (already used by the analytics export tests, now in
  the main code), Commons CSV for `.csv` (new dependency; UTF-8 or Windows-1251, semicolon, comma or tab); the
  files pass the ClamAV scan of Feature 3.1 and are not stored.

## 7. Technical decisions

Inherited: ADR-001 (modular monolith; the people code lives in `award`, which already depends on `user`; `award`
publishes `PersonInvited` and `notification` sends the mail; see the ADR-001 addendum 2026-11), ADR-006 addendum (Spring events with the publication registry), ADR-023 (award and request
transitions unchanged), 4.1.0 (submitter never reviews), 6.1 (privacy notice versions, erasure scrub), 7.1 (mail
delivery log and retries).

### Proposed deviations and assumptions

- **D-1** A person without an account is a `users` row in the new state `UNCLAIMED`, not a separate `people` table:
  awards, scopes, statistics and the claim keep one owner id and no data moves at claim time. The status table in
  DATA_DICTIONARY §1.1 gains `UNCLAIMED → PENDING` (claim) and `UNCLAIMED → DELETED` (removal).
- **D-2** Legal basis for the placeholder and its awards: legitimate interest of the university in a complete record
  of awards (DATA_GOVERNANCE §4.2, the same basis as award verification), with the Art. 14 notice as the invitation
  mail (AC-1.4); `PRIVACY_IMPACT.md` and DATA_GOVERNANCE §1.2 and §4.3 gain a section on data entered by a third
  party. No analytics consent is assumed. A person added without an address cannot be told directly; Art. 14(5)(b)
  applies (informing them is impossible), the published privacy notice covers them and they are told at the link or
  claim; the privacy impact assessment records it.
- **D-3** At the claim, the prepared name and department win over the registration form (AC-3.1, AC-3.5): otherwise
  anyone who knows the address could rename a placeholder for the 24 h of a pending link without owning the mailbox.
- **D-4** Adding a person counts as the membership confirmation of Feature 1.2: verification grants the `EMPLOYEE`
  role in the prepared department, because a secretary in scope vouched for it.
- **D-5** The address is optional and, when given, institutional. Without an address there is no automatic claim;
  the person is joined to an account only by a secretary or dean who confirms the registrant's membership (2.5.4),
  never by a name match of the system.
- **D-6** The invitation link is a plain registration link with the address filled in, not a token: the claim is
  proven by the existing verification of the address.
- **D-7** Review notices of an unclaimed owner's award go to the request's submitter (AC-2.5), not to every
  secretary in scope.
- **D-8** "Without an account" is `UNCLAIMED`, or `PENDING` with `added_by_id` set (AC-3.4), so an abandoned claim
  does not orphan the awards.
- **D-9** Imported awards (2.5.5) are stored as `APPROVED` without a review request: they were awarded in the past
  and recorded by the faculty already; the secretary who imports answers for them, the audit row names the file and
  row, and `awards.source` keeps them apart in reports. This is a new path around ADR-023's submission and is the
  one point of the feature that changes the approval rule.
- **D-10** (2.5.1) A person without a known address holds a placeholder `unclaimed-<uuid>@unclaimed.invalid` instead
  of a null address: the column, its unique index and every query that reads an address stay as they are, and one
  guard in `MailDelivery` keeps mail away from `*.invalid`. All `/people` code lives in `award` to avoid a
  `user` → `award` module cycle (ADR-001 addendum 2026-11). The invitation's delivery state is not part of
  `Person`: a person without an account has no notification row, so the mail goes through `MailDelivery` with its
  retries and a failed send is logged.

## 8. Contract

### 8.1 OpenAPI changes

The new paths and schemas are `x-status: planned` in the PR of this PRD. Changes to existing schemas
(`AccountStatus`, `AwardCreateRequest`, `Award`, `RegistrationResponse`) land with their story: `OpenApiContractTest`
pins those schemas to the DTOs and the entity enum.

- `/people` `post` (2.5.1): `PersonCreateRequest` {`firstName`, `lastName`, `email`, `organizationId`} → 201
  `Person` {`id`, `firstName`, `lastName`, `email` (nullable), `organization` (`OrganizationRef`), `status`
  (`AccountStatus`), `addedBy` (`UserRef`), `invitedAt` (nullable), `awardCount`, `version`}; request also `duplicateAcknowledged` (boolean); 409
  `email-taken`, `possible-duplicate` (`PersonDuplicateProblem` with `matches`: id, names, `maskedEmail`,
  `awardCount`); 422 `validation-failed`; 403 `access-denied`.
- `/people` `get` (2.5.1): `q` (≥ 2 characters), `organization`, `page`, `size` → `PersonPage`; people without an
  account inside the caller's secretary or dean scopes, address masked in the list.
- `/people/{id}` `get` (2.5.1): `Person` with `awards` (`AwardSummary[]`); 404 outside scope.
- `/people/{id}` `put` (2.5.1): `PersonUpdateRequest` (create fields + `version`) → `Person`; 409
  `version-conflict`, `person-claimed`, `email-taken`; 422.
- `/people/{id}` `delete` (2.5.1): 204; 409 `person-has-awards`, `person-claimed`.
- `/people/{id}/invitation` `post` (2.5.1): 202; 429 with `Retry-After`; 409 `person-claimed`, `no-address`.
- `/people/{id}/link` `post` (2.5.4): `PersonLinkRequest` {`userId`, `version`} → 200 {`userId`, `movedAwards`};
  404; 409 `version-conflict`; 422 `validation-failed`.
- `/award-imports/preview` `post` (2.5.5): multipart `file` and an optional `ImportMapping` {`columns`,
  `departmentId`, `defaultLevel`, `defaultAwardingOrganization`} → `AwardImportPreview` {`checksum`, `headers`,
  `mapping` (the one sent or the guess), `rows`}; 422 (size, type, rows, mapping). `/award-imports` `post`: the
  same file, the mapping and the preview's `checksum` → 201 {`imported`, `peopleAdded`, `skipped`}; 409
  `preview-stale`; 422 `rows-blocked` with the row numbers. `/award-imports/template` `get`: the `.xlsx` template.
- `AccountStatus` (2.5.1): adds `UNCLAIMED`. `/users/{id}/roles` `post` (2.5.1): 422 code `unclaimed`.
- `AwardCreateRequest` (2.5.2): `recipientUserId` (integer, nullable); 422 `out-of-scope`, or with
  `recipientOrganizationId` code `one-recipient`.
- `Award` (2.5.2): `ownerWithoutAccount` (boolean), `managedByCaller` (boolean); (2.5.5) `source` (`FORM` |
  `IMPORT`).
- `/auth/verify-email` `post` response (2.5.3): `RegistrationResponse.waitingAwards` (integer, 0 when not claimed).

### 8.2 Migration outlines

`V051__people_without_account.sql` (2.5.1):
- `users.added_by_id BIGINT NULL` FK → `users(user_id)` `ON DELETE SET NULL`; `users.invited_at TIMESTAMPTZ NULL`.
- `ck_users_status` recreated with `UNCLAIMED`.
- `users.email_address` unchanged (NOT NULL, unique): a person without a known address gets
  `unclaimed-<uuid>@unclaimed.invalid`, like the `erased-<id>@erased.invalid` of an erased account; the API answers
  `email: null` for it and no mail goes to any `*.invalid` address.
- Partial index `idx_users_unclaimed` on `(organization_id, last_name)` where `account_status = 'UNCLAIMED'`.

`V054__award_source.sql` (2.5.5): `awards.source VARCHAR(10) NOT NULL DEFAULT 'FORM'`, CK `FORM`, `IMPORT`.

### 8.3 Properties

`app.people.invitation-interval` (default `PT1H`, `APP_PEOPLE_INVITATION_INTERVAL`) and
`app.people.data-protection-contact` (default `gdpr@chnu.edu.ua`), 2.5.1; `app.award-import.max-rows` (1000) and
`app.award-import.max-size` (2MB), 2.5.5.

## 9. Test plan

| AC | Unit | IT | FT | E2E |
|----|------|----|----|-----|
| 1.1–1.3, 1.6, 1.7 | scope and input rules | `PeopleIT` (constraints, concurrency, scrub) | `PeopleFT` (codes, audit rows) | `people.spec.ts` add, change, remove |
| 1.4, 1.5 | `InvitationMails` text | delivery row | `PeopleFT` (Mailpit, 429) | - |
| 1.8 | - | `AccountStatusChecker`, reset | `LoginFT`, `RoleAssignmentFT` | - |
| 1.9 | list, dialog, page specs | - | - | `people.spec.ts` |
| 2.1–2.3, 2.7 | `AwardOwnership`, form spec | `AwardSubmissionIT` | `ProxyAwardFT` | `people.spec.ts` award for a person |
| 2.4, 2.6 | `ReviewerRule` | statistics queries | `ProxyAwardFT` | - |
| 2.5 | event mapping | - | `ProxyAwardFT` (Mailpit) | - |
| 3.1–3.5 | `RegistrationService` | `RegistrationIT` take-over, D-8 | `ClaimFT` | `claim.spec.ts` register, verify, own list |
| 1.10 | name normalisation | `PeopleIT` (match query) | `PeopleFT` (409, flag) | `people.spec.ts` duplicate dialog |
| 4.1–4.4 | candidate order | `PersonLinkIT` (awards move, one transaction, lock) | `PersonLinkFT` (codes, audit) | `people.spec.ts` confirm with link |
| 5.1–5.4 | row parsing and matching | `AwardImportIT` (one transaction, repeat) | `AwardImportFT` (limits, scope) | `import.spec.ts` preview and import |

## 10. Manual verification

Preconditions: stack from `docker compose up -d --build` with the 2.5 build; dev seed accounts (password
`Passw0rd-demo`): `secretary.fmi@chnu.edu.ua` (faculty secretary, FMI), `dean.fmi@chnu.edu.ua`,
`secretary.fe@chnu.edu.ua` (another faculty), `rector.secretary@chnu.edu.ua`; Mailpit at `http://localhost:8025`;
for step 13 `APP_PEOPLE_INVITATION_INTERVAL=PT1M`; for step 38 `APP_EVENTS_RETRY_INTERVAL=PT1M` and
`APP_EVENTS_RETRY_AFTER=PT1M`.

1. Sign in as `secretary.fmi@`, open `/admin/users`, press «Додати колегу без облікового запису», enter Петро
   Іваненко, `p.ivanenko@chnu.edu.ua`, a department of FMI. Expected: the person page opens with «Надіслано <time>»
   under «Запрошення» and no «Призначити роль»; back in the list, the person carries «Без облікового запису» and the
   status filter offers it. (AC-1.1, AC-1.9)
2. Mailpit. Expected: one mail to `p.ivanenko@` naming the secretary, the department, the purpose, the rights and a
   registration link with the address. (AC-1.4)
3. psql `select account_status, added_by_id from users where email_address = 'p.ivanenko@chnu.edu.ua'`. Expected:
   `UNCLAIMED`, the secretary's id; `audit_logs` has `PERSON_ADDED` and `INVITATION_SENT`. (AC-1.1, AC-1.4)
4. Add the same address again. Expected: «Ця адреса вже використовується», nothing new. (AC-1.2) Add a second
   person without an address. Expected: «Запрошення не надіслано: адреса невідома», no mail in Mailpit, and psql
   shows `email_address` `unclaimed-<uuid>@unclaimed.invalid` for the row. (AC-1.4, D-10)
5. Sign in at `/login` as `p.ivanenko@` with any password. Expected: the wrong-password message. Request a password
   reset for it. Expected: no mail. (AC-1.8)
6. As `secretary.fmi@`, open the person, «Додати нагороду». Expected: `/awards/new?person=<id>` with the person
   chosen; fill the award and save the draft. (AC-2.1, AC-2.2)
7. Open the draft. Expected: «Без облікового запису» next to «Іваненко Петро», edit and submit offered; submit.
   (AC-2.3, AC-2.7)
8. Review queue of `secretary.fmi@`. Expected: the award is not offered there. Sign in as `dean.fmi@`
   and return it with a comment. Expected: the mail in Mailpit goes to `secretary.fmi@` and names the person;
   nothing to `p.ivanenko@`. (AC-2.4, AC-2.5)
9. As `secretary.fmi@`, correct and submit again; as `dean.fmi@`, approve. Expected: approved, visibility
   `PRIVATE`; FMI unit figures count it; the achievements page does not list it. (AC-2.6)
10. As `secretary.fmi@`, change the person's department to another FMI department and the address to
    `p.ivanenko2@chnu.edu.ua`. Expected: saved, a new invitation to the new address. (AC-1.6)
11. Register at `/register` as `p.ivanenko2@chnu.edu.ua` with the name «Petro Test» and any department. Expected:
    the usual «check your mail» page. (AC-3.1)
12. Open the verification link, enter the password. Expected: «Адресу підтверджено. На вас уже чекають нагороди:
    1.»; sign in; `/awards` lists the approved award; the profile shows Петро Іваненко and the prepared department;
    psql shows `ACTIVE`, an `EMPLOYEE` role and `ACCOUNT_CLAIMED`. (AC-3.2, AC-3.3, AC-3.5)
13. With the 1-minute interval: add a second person, press «Надіслати запрошення ще раз» twice. Expected: one more
    mail, then «Запрошення вже надіслано, спробуйте пізніше». (AC-1.5)
14. Remove the second person (no awards). Expected: gone from the list, the address can be added again. (AC-1.7)

15. As `secretary.fmi@`, add Оксана Мельник in an FMI department without an address. Expected: listed with
    «Без облікового запису», the person page says «Запрошення не надіслано: адреса невідома», no mail in Mailpit.
    (AC-1.1, AC-1.4)
16. Add Оксана Мельник in the same department again. Expected: the duplicate dialog lists the first one with its
    award count; «Це інша людина, додати» stores a second one; remove it again. (AC-1.10)
17. Enter an award for Оксана Мельник and have `dean.fmi@` approve it (steps 6 to 9). Register a new account
    `o.melnyk@chnu.edu.ua` in the same department and verify it. Expected: the account has no role and no awards.
18. As `secretary.fmi@`, find the registrant in `/admin/users` and press «Змінити кафедру» on the row (the row's
    «Підтвердити» confirms the membership at once, without candidates), choose the department. Expected: the
    dialog lists Оксана Мельник under «Це та сама людина» with the award count, nothing preselected; choose that entry
    and confirm. (AC-4.1)
19. Sign in as `o.melnyk@`. Expected: the role «Працівник», the approved award in `/awards`; psql shows the
    placeholder `DELETED` and `ACCOUNT_LINKED` with `movedAwards` 1. (AC-4.2)
20. 2.5.5: download the template on `/admin/import`, or take a faculty spreadsheet; upload it. Expected: the
    columns are mapped from the headers (change one and preview again), the preview lists every row with its
    person, department, category and problems, nothing stored; «Імпортувати» stores the rows as approved awards
    marked «Імпортовано» and adds the missing people; a second import of the file adds nothing. (AC-5.1 to AC-5.3)

### Detours

21. Direct URL: as `secretary.fe@` open the person page of an FMI person. Expected: «Не знайдено», API 404.
22. `/awards/new?person=<id of a claimed or unknown person>`. Expected: the recipient is not preselected; a crafted
    `POST /awards` with that id answers 422 `out-of-scope`.
23. Hidden in state: the person page of a claimed person. Expected: no «Змінити», «Надіслати запрошення ще раз»,
    «Видалити»; `GET /api/v1/people/{id}` answers 404 (the user directory shows the account), `PUT`, `DELETE` and
    the invitation answer 409 `person-claimed`.
24. Blocked in state: a person with a pending or approved award. Expected: «Видалити» stays offered (drafts are
    removed with the person) and after the confirmation answers «У колеги є нагороди, що не є чернетками, тож
    видалити неможливо.»; `DELETE` 409 `person-has-awards`.
25. Hidden in state: after the claim, as `secretary.fmi@` open the award. Expected: no edit, submit or withdraw;
    the API answers 404 or 403 as for any colleague's award. (AC-3.3)
26. Revoked mid-flow: `secretary.fmi@` opens a person's draft for editing; `admin@` ends that faculty secretary role.
    Expected: the role change ends the secretary's session, so the save leads to the sign-in page; the draft is
    unchanged; `dean.fmi@` can still edit and submit it.
27. Revoked mid-flow: a delegation of faculty secretary to an employee, the delegate opens the add dialog; the
    delegation is revoked before saving. Expected: 403, nothing stored; the secretary can add the person.
28. Second browser: the person claims (step 12) while the secretary has the draft of another award of the person
    open. Expected: the secretary's save answers 404 and the form falls back to a new award; the claimed person's
    draft is no longer visible to the secretary (`GET /awards/{id}` 404; approved awards stay read-only, step 25).
29. Abandoned claim: register for an unclaimed address and do not verify; set the link expiry in psql to the past.
    Expected: the secretary still manages the awards; a new registration replaces the pending row. (AC-3.4)
30. Token expiry in an open tab: the add dialog stays open past the 15-minute access token. Expected: the save
    renews the token silently and stores the person once.
31. Back/reload after a one-time action: reload after «Видалити». Expected: «Не знайдено», no error dialog.
32. Role assignment: `admin@` opens an unclaimed person. Expected: no «Призначити роль»; a crafted
    `POST /users/{id}/roles` answers 422 `unclaimed`. (AC-1.8)
33. Hidden in state: the link on a person with a pending claim (`PENDING`, D-8) or with an address and an account
    under that address. Expected: not offered as a candidate; `POST /people/{id}/link` answers 422. (AC-4.3)
34. Revoked mid-flow: `secretary.fmi@` has the confirm dialog open with a candidate chosen; `admin@` ends the faculty
    secretary role. Expected: the role change ends the session, so the save leads to the sign-in page; no role and
    no award moves; `dean.fmi@` can link.
35. Second browser: two secretaries link the same person to two registrants at once. Expected: one link succeeds,
    the other answers 404 and the dialog reloads the candidates without the person. (AC-4.3)
36. 2.5.5: a file of 1,001 rows, a `.pdf` renamed `.csv`, a row of a FE department. Expected: 422 naming the limit,
    the type or the rows; nothing stored. (AC-5.4)
37. Repeating a one-time action: after step 12, reload the verification page and open the link in a second tab.
    Expected: no second `ACCOUNT_CLAIMED` row, one `EMPLOYEE` role, the page explains the link was used. (AC-3.2)
38. Mail down: `docker stop award-mailpit`, add a person with an address, start Mailpit again. Expected: the person
    is stored with `invitedAt` set, the send is retried and logged, and the mail arrives once. (AC-1.4, §5)
39. Two tabs: preview one file in two tabs as `secretary.fmi@` and press «Імпортувати» in both. Expected today:
    both may pass the duplicate check (known limit, PR #441); record the counts. (AC-5.2)
40. Text forms: save the step 20 file with the titles in NFD (decomposed «й», «ї») next to an NFC award of the same
    title, person and date entered through the form. Expected: the preview skips the row as «already-stored», as
    for the NFC file; a person added from an NFD file is found by a normal search. (AC-5.1, AC-6.2)

### Run 2026-11-18

Container stack (docker compose build --pull=false, app + frontend) at develop 2b9741f, default settings; steps 20 and 36 only. Result: 2 pass.

| Step | Result | Evidence |
|---|---|---|
| 20 | PASS | Template award-import-template.xlsx (8 columns + level sheet). Faculty CSV with own headers: 6/7 columns guessed, «Посада» remapped to «Примітки», preview again; rows 2-4 with person, department, category, 1 in-file repeat; imported awards 7 before and after both previews. «Імпортувати»: 2 imported, 1 colleague added, 1 skipped; awards 1869/1870 APPROVED source IMPORT, notes stored, Новачук added UNCLAIMED without an address (users 1402->1403); /awards/1870 shows «Затверджено» + «Імпортовано». Second upload: rows «Уже збережено», submit disabled, count stays 9 |
| 36 | PASS | 1,001 rows: preview 422 too-many-rows maxRows=1000; PDF renamed .csv: 422 unsupported-type; FE row: preview flags row 2 department-out-of-scope, import 422 rows-blocked rows=[2]; imported awards 7 and users 1402 before and after |

### Run 2026-11-19

docker compose stack, image 2b9741f (develop 0c39df7), APP_PEOPLE_INVITATION_INTERVAL=PT1M; step 38 also APP_EVENTS_RETRY_INTERVAL/AFTER=PT1M. Result: 38 pass, 1 fail.

| Step | Result | Evidence |
|---|---|---|
| 1 | PASS | added Іваненко (user 1404) in dept 64; page shows Надіслано 11:49, no Призначити роль; list/filter part not re-checked in UI |
| 2 | PASS | 1 mail 11:49 to p.ivanenko@, names secretary, dept, purpose, rights, link /register?email=p.ivanenko%40chnu.edu.ua |
| 3 | PASS | UNCLAIMED, added_by_id 5; audit PERSON_ADDED 1, INVITATION_SENT 1 |
| 4 | PASS | duplicate address: «Ця адреса вже використовується», dialog stays, 409; second person 1405 via API (UI message checked in step 15): invitedAt null, placeholder unclaimed-<uuid>@unclaimed.invalid, no mail |
| 5 | PASS | login p.ivanenko@ -> BAD_CREDENTIALS «Невірна адреса або пароль.»; password-reset request 202, 0 mails after sign-in attempt |
| 6 | PASS | person page «Додати нагороду» -> /awards/new?person=1404 with Іваненко Петро chosen; draft saved as award 1871 |
| 7 | PASS | draft 1871 shows «Без облікового запису» beside Іваненко Петро, edit/submit offered; submitted -> PENDING v4 (category had to be chosen first; my fill error, not product) |
| 8 | PASS | 1871 not in secretary.fmi review queue; dean.fmi RETURN -> 1 mail 11:53 to secretary.fmi@ (names Іваненко), 0 to p.ivanenko@ |
| 9 | PASS | 1871 APPROVED visibility PRIVATE (category international chain: dean -> escalated x2 -> rector.secretary approve); FMI unit figures approved 258 -> 259 after cache; secretary /achievements has no 1871 (step text assumed dean approves; international category escalates) |
| 10 | PASS | PUT /people/1404 dept 64->65, address -> p.ivanenko2@: 200, invitedAt 11:54, new mail to p.ivanenko2@ 11:54 (done through API, dialog not used) |
| 11 | PASS | register p.ivanenko2@ as Petro Test, dept 1st option -> /registration-pending (check your mail page) |
| 12 | PASS | verify page: «Адресу p.ivanenko2@… підтверджено. На вас уже чекають нагороди: 1.»; ACTIVE, 1 EMPLOYEE role (dept 65), ACCOUNT_CLAIMED 1; sign-in ok, /awards lists 1871, profile Петро Іваненко dept Кафедра диференціальних рівнянь |
| 13 | PASS | person 1406 (b.druhyi@): after 65 s resend 202 -> 2nd mail 11:57; immediate 2nd resend 429 'invitation was sent a short while ago' retryAfter 59 (API; UI wording not viewed) |
| 14 | PASS | DELETE /people/1406 -> 204, GET 404; same address added again -> 201 (1407, since removed) |
| 15 | PASS | UI: Мельник Оксана (user 1408) dept 64, no address: page «Запрошення не надіслано: адреса невідома», status Без облікового запису; 0 mails to anyone in Mailpit; list shows the label (15 rows with it); status-filter options not read (selector guess failed) |
| 16 | PASS | second add: dialog «На цій кафедрі вже є колега з таким ім'ям: Мельник Оксана · Адреса невідома · нагород: 0»; confirm (API duplicateAcknowledged) stored 1409; removed 204 (button click not driven by script) |
| 17 | PASS | award 1872 for person 1408 (cat 21) approved by dean.fmi at FACULTY_SECRETARY; o.melnyk@ (user 1410) ACTIVE, roles [], /awards answers 403 (no role) |
| 18 | PASS | row action «Змінити кафедру» (not «Підтвердити», which confirms directly: my first click on o.melnyk@ assigned EMPLOYEE at once, so a fresh o.melnyk2@ was used) opens «Підтвердити членство»: 16 candidates under «Це та сама людина», incl. Мельник Оксана · нагород: 1, 0 radios checked; chose it, confirmed, dialog closed |
| 19 | PASS | o.melnyk2@ (user 1411, registrant name Iryna Nova from fresh): role EMPLOYEE, /awards lists 1872 (totalElements 1); placeholder 1408 DELETED; ACCOUNT_LINKED {accountId:1411, movedAwards:1} (sign-in via API token, not UI) |
| 21 | PASS | secretary.fe: /admin/users/1405 «Користувача не знайдено», API GET /people/1405 404 |
| 22 | PASS | UI: /awards/new?person=1404 (claimed): recipient not preselected; POST /awards recipientUserId 1404 (fmi) and 1405 (fe) -> 422 validation-failed field recipientUserId code out-of-scope (step text says 'out-of-scope' problem; it is the field error code) |
| 23 | PASS | claimed 1404: page has no Змінити/Надіслати/Видалити; GET 404, PUT/DELETE/invitation 409 person-claimed |
| 24 | PASS | step text corrected: «Видалити» is offered (drafts are deletable) and confirmed -> message «У колеги є нагороди, що не є чернетками, тож видалити неможливо.»; DELETE /people/1403 (approved award 1870) 409 person-has-awards |
| 25 | PASS | after claim secretary.fmi sees 1871 read-only (no edit/submit/withdraw buttons); PUT 404 |
| 26 | PASS | step text corrected: secretary had 1878 open in edit, admin revoked FACULTY_SECRETARY #250 -> save got 401 and the page went to /login (a role change ends the session), not «Немає доступу»; draft unchanged (title_uk «Чернетка ролі»); dean.fmi PUT v2 + submit -> PENDING; role re-granted (1252) |
| 27 | PASS | API (UI not reachable: a delegate lands on /forbidden at /admin/users, so the dialog cannot be opened with a borrowed role): delegation 27 FACULTY_SECRETARY employee.fmi -> after re-login POST /people 201; delegation deleted 204; same token POST /people -> 403 access-denied, nothing stored; secretary adds the person 201 |
| 28 | PASS | step text corrected (2.5.2 visibility 404, known): person c.claim@ (1420) claimed while secretary had draft 1877 open: save -> 404 and the form drops to /awards/new with «Відновити незбережені зміни?», no «Немає доступу»; reload/GET /awards/1877 -> 404 not found (a claimed person's DRAFT is hidden, not read-only; the approved 1871 stays read-only per step 25) |
| 29 | PASS | person 1412 (a.aband@) + draft 1873; register -> PENDING 201; token 2299 expired in psql: secretary still edits 1873 (PUT v2), withdraw 409 as expected for draft; re-register 201 PENDING, 1 users row for the address, old token marked used, new token 2300 valid, 2nd verification mail 12:05:13 |
| 30 | PASS | step text corrected: add dialog open 16 min (access token 15m) -> save POST /people 201 once (1426), no sign-in prompt: the expired access token is renewed silently; 1 row stored |
| 31 | PASS | UI: person 1413 removed with the dialog -> /admin/users; reload of /admin/users/1413 -> «Користувача не знайдено», 0 dialogs |
| 32 | PASS | admin: API POST /users/1405/roles -> 422 validation-failed code unclaimed «A person without an account cannot hold a role»; UI shows no «Призначити роль» for an unclaimed person at all (so the Людина ще не має облікового запису text is not reachable from the screen) |
| 33 | PASS | pending person 1412 absent from the «Це та сама людина» list of o.third; POST /people/1412/link -> 422 validation-failed field person code claimed (address+account variant not run separately) |
| 34 | PASS | step text corrected (as step 26): o.fifth@ candidate Лінковий chosen, FACULTY_SECRETARY #1252 revoked -> save 401 + sign-in, no «Немає доступу»; registrant roles 0, person 1424 still UNCLAIMED; dean.fmi link 200 {userId:1425, movedAwards:0}; role re-granted (1254) |
| 35 | PASS | API race (secretary.fmi vs dean.fmi, person 1416, registrants 1414/1415): 200 {userId:1414, movedAwards:0} and 404 «Person 1416 not found»; dialog reload in the UI not driven |
| 36 | PASS | rerun: rows-1001.csv 422 too-many-rows maxRows 1000; renamed-pdf.csv 422 unsupported-type; fe-row.csv preview 200 flags row 2 department-out-of-scope, import 422 rows-blocked rows [2]; nothing stored |
| 37 | PASS | after verification: ACCOUNT_CLAIMED 1 row, EMPLOYEE roles 1; second tab/reload with the used link -> «Посилання недійсне, прострочене або вже використане» (page strips token on load, so plain reload shows the same) |
| 38 | PASS | Mailpit stopped; person 1427 (m.down@) stored with invitedAt 10:23:06Z; log 'was not delivered' x3 (in-call attempts); Mailpit started -> mail 12:24:46 arrived once, still 1 after 70 s more (retry PT1M) |
| 39 | PASS | known limit reproduced (API, two previews then two parallel imports of one file): both 201 imported 1; 2 awards and 2 person rows created for the same row, no duplicate guard at import |
| 40 | FAIL | NFC control row skipped «already-stored»; same title in NFD (22 vs 20 code points) not marked at all, preview problems []: ImportDuplicates.plainTitle splits at combining marks (known F-1) |


## 11. Risks

| # | Risk | Mitigation |
|---|------|------------|
| 1 | 24 points before the code freeze of 2026-11-22, 2.5.5 after 9.4 | 2.5.1 and 2.5.2 first; 2.5.3 and 2.5.4 can follow 9.4 |
| 2 | Wrong address: the invitation reaches another employee | Address editable, removal while drafts; mail says to ignore if not meant for them |
| 3 | A person who never registers asks for erasure of submitted awards | DPO handles by mail; documented in DATA_GOVERNANCE §4.3; admin tool later |
| 4 | Duplicate rows when the person registers under another address | Out of scope merge; secretary sees both in the directory |
| 5 | Ownership checks spread over many services miss the manage rule | One `AwardOwnership.canManage`; `ProxyAwardFT` walks every owner endpoint |
| 6 | A secretary links the wrong person of the same name | Masked address and award count in the dialog, nothing preselected, audit row; undo by a reviewer correction or an admin |
| 7 | The spreadsheets differ between faculties | A column mapping step guessed from the headers (decided 2026-11-18); real samples tune the header words |

## 12. Definition of Done

- `.\tools\gate.ps1` green: unit, IT, FT, JaCoCo ≥ 85 %, Checkstyle, PMD and SpotBugs at 0, frontend lint and tests;
  Playwright for 2.5.1, 2.5.2 and 2.5.3.
- Security review of each story (auth, migrations, scope checks).
- Screenshots at 360 and 1280 px, light and dark, Ukrainian and English, for each changed screen.
- The manual steps of each story run and recorded in §10.
- Docs updated in the story that changes them: `openapi.yml` (planned → implemented), DATA_DICTIONARY §1.1,
  DATA_GOVERNANCE §1.2 and §4.3, `PRIVACY_IMPACT.md`, AUTH §9 table, `USER_GUIDE.md`, `CHANGELOG.md
  [Unreleased]`, the epic tracker and `BACKLOG.md`.

## Validation (2026-11-18)

Result: **PASSED WITH NOTES**, pending the manual run of §10 (steps 20 and 36 passed 2026-11-18). Stories
2.5.1-2.5.5 merged (#426-#429, #441).

Suite on `develop` 735af52: unit 1 484, IT/FT 624, JaCoCo 98.3 % lines, Checkstyle/PMD/SpotBugs 0; frontend lint,
1 342 unit tests, production build 855 kB. Playwright: the full run on the 2.5.5 branch (240 passed, the last 4 specs
passed on their own after the 45 min timeout); `develop` differs from that branch in docs only. Compose stack built
and healthy from 2b9741f; migrations apply on the empty database of every IT; `OpenApiContractTest` passes.

| AC | Evidence (all passing) |
|----|------------------------|
| 1.1, 1.4 | `PeopleFT` ac1_1_ac1_4, ac1_4 without an address; `InvitationMailsTest` (2); e2e `people.spec` ac1_1 ac1_4 ac1_5 ac1_9 |
| 1.2 | `PeopleFT` ac1_2 address taken, ac1_2 two concurrent adds |
| 1.3 | `PeopleFT` ac1_3; `PersonRulesTest` ac1_3 (4) |
| 1.5 | `PeopleFT` ac1_5 (Mailpit, 429 with `Retry-After`) |
| 1.6 | `PeopleFT` ac1_6 (audit fields, first address invited, 409 version, 422 `required`); `PersonRulesTest` ac1_6 |
| 1.7 | `PeopleFT` ac1_7 (2) |
| 1.8 | `PeopleFT` ac1_8 |
| 1.9 | `PeopleFT` ac1_9 (2); e2e `people.spec` ac1_9 (2) |
| 1.10 | `PeopleFT` ac1_10; `PersonRulesTest` ac1_10 (2); e2e `people.spec` ac1_4 ac1_10 |
| 2.1 | e2e `people.spec` ac2_1 (2) |
| 2.2 | `PersonAwardsFT` ac2_2 (2); `ManagedOwnersTest` ac2_2 |
| 2.3 | `PersonAwardsFT` ac2_3 (3); `ManagedOwnersTest` ac2_3 |
| 2.4, 2.5 | `PersonAwardsFT` ac2_4_ac2_5_ac2_6, ac2_3_ac2_4; `ManagedOwnersTest` ac2_5 |
| 2.6 | `PersonAwardsFT` ac2_4_ac2_5_ac2_6 (PRIVATE, visibility change refused, not in achievements); `TopPerformersRankingTest` ac2_6 (added by the validation); unit totals: code only (F-2) |
| 2.7 | e2e `people.spec` ac2_1 ac2_7 |
| 3.1-3.3 | `ClaimFT` ac3_1_ac3_2_ac3_3; `AccountClaimTest` ac3_1 (2), ac3_2; e2e `claim.spec` |
| 3.4 | `ClaimFT` ac3_4; `AccountClaimTest` ac3_4 |
| 3.5 | `AccountClaimTest` ac3_1 the registration brings only the password (D-3) |
| 4.1 | e2e `person-link.spec` ac4_1 ac4_2 ac4_4 |
| 4.2, 4.4 | `PersonLinkFT` ac4_2, ac4_4; e2e `person-link.spec` |
| 4.3 | `PersonLinkFT` ac4_3 |
| 5.1 | `AwardImportFT` ac5_1; `ImportColumnsTest` (3), `ImportValuesTest` (9), `ImportDuplicatesTest`, `SpreadsheetReaderTest` (3); e2e `award-import.spec` ac5_1 |
| 5.2 | `AwardImportFT` ac5_2; `ImportDuplicatesTest` ac5_2; e2e `award-import.spec` ac5_2 (2) |
| 5.3 | `AwardImportFT` ac5_3; e2e `award-import.spec` ac5_3 |
| 5.4 | `AwardImportFT` ac5_4 (5); `SpreadsheetReaderTest` ac5_4 (3); `XlsxTablesTest` (2); e2e `award-import.spec` ac5_4 |

Edge cases of §5: department move (code: `AwardSubmission` takes the owner's department at submission,
`ManagedOwners` checks the owner's current department; no test, F-3), role ended or delegation revoked mid-edit
(manual steps 26-27), claim while the draft is open (`ClaimFT` ac3_3, step 28), registration under another address
(`PersonLinkFT`), namesakes (ac1_10 tests), claim link still valid (`AccountClaimTest` ac3_1 usable link), bounced
invitation (§5 corrected to §7), erasure and retention (`UNCLAIMED` is outside the former-staff statuses), concurrency
(`PeopleFT` 409 tests).

Security: A01 every `/people` and `/award-imports` call checks the role (`PersonRules.requireRole`) and the scope
(`PeopleLookup.inScope`, `ManagedOwners`), the decision rule checks the current owner (`ReviewerRule`), the import
blocks rows of the caller or a delegator; A02 a placeholder holds a random password hash (`SecretUtils.randomSecret`)
and a masked address in lists; A03 parameterised queries (`UserRepository.findWithoutAccount`, `ImportDuplicates`),
files type-checked by content, scanned, capped in size, rows and xlsx inflation; A07 an unclaimed address answers as a
wrong password (`LoginFailureHandler`), password reset sends nothing, registration gives no hint of a prepared row.

Findings:
- F-1 Import text is not NFC-normalised (`ImportDuplicates.plainTitle`, stored names): an NFD file misses the probable
  duplicate of an NFC award, and people added from it are not found by an NFC search. Manual step 40. Fixed by
  2.5.6 (SCRUM-160).
- F-2 AC-2.6 totals: unit and university figures count awards of unclaimed owners (no owner-status filter); no test.
- F-3 Department move with a draft: no test (see edge cases).
- F-4 Two imports of one file at once can both pass the duplicate check (known, PR #441). Manual step 39.
- F-5 Not confirmed: a notice of an unclaimed owner's award when the submitter's account was erased goes to
  `request.getSubmitter()` without a guard (`NoticeRecipientHelper`).
- Refactor sweep: findings in the epic tracker's technical notes, none applied (code freeze).

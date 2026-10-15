# Feature 7.2: Notification Centre and Preferences

> **Epic**: 7 — Notification & Communication System (SCRUM-63)
> **Sprint**: 5 (2026-10-12 → 2026-10-18), spilling into sprint 6
> **Points**: 19 (five stories)
> **Status**: Done 2026-10-15, manually verified (validation §13, all 31 steps of §10 pass; approved 2026-10-14)
> **Author**: Stefan Kostyk
> **Governing docs**: EPIC-07 tracker (decisions of 2026-10-09, deviations 3–8, technical notes, risk 3), Feature 7.1 PRD (§7 decisions 2, 6, 12; validation F-1, F-4), ADR-006 addendum, ADR-022 (module table), ADR-021, DATA_DICTIONARY §4 `consent_records`, §5, PRIVACY_BY_DESIGN §4.2, US-005, roadmap story 1.3.2, `openapi.yml` (`/users/me`, `NotificationPreferences`), the mail listeners (`DecisionMails`, `CorrectionMails`, `OverdueMails`, `TakeOverMails`, `AuthorityChangeMails`, `PrivacyMails`, `AuthenticationMails`), `MailDelivery`, `PersonalDataAssembler`, `award-status.component.ts` (polling), `shell.component.html` (toolbar)

## 1. Problem and personas

Everything the system tells a person today arrives as a bilingual e-mail. Nothing in the application says what happened since the last visit: an employee learns that an award was returned only from the mail, a reviewer finds an overdue request only in the digest. A person cannot choose which messages reach the mailbox, so a dean who reviews dozens of awards receives a mail for every hand-over and every overdue digest. Nobody can tell whether a given mail went out, and the personal data export says nothing about the messages sent to the person. The mails are plain text with no university branding.

| Persona | Need in this feature |
|---------|----------------------|
| Anastasia, employee | Sees in the toolbar that her award was approved or returned, opens it in one click, in her interface language |
| Prof. Martynyuk, dean (reviewer) | Keeps the overdue digest and hand-overs in the application and switches their e-mails off |
| Faculty secretary | Sees role and delegation changes in the centre as well as by mail |
| Dmytro, GDPR officer | Notifications hold no more than needed, expire after a year, appear in the export; a change of channel is recorded |
| Operator | Can tell how many mails were sent or failed per type; a resend after a crash does not send twice |

## 2. Scope

**In (this feature)**

- `notification` module stores in-app notifications for eleven types from the existing events (decisions, escalation, corrections, overdue digests, hand-overs, roles, delegations); one registry listener per channel
- REST API: list, unread count, mark one or all as read; scoped to the caller
- Notification centre in the toolbar: bell with unread badge, menu with the latest ten, page `/notifications` with paging and an unread filter; polls every 60 s while the tab is visible
- Preferences per type and channel (e-mail, in app) on the profile page; the decision, correction, overdue, hand-over, role and delegation mails move into `notification` and obey them
- Delivery log: one `EMAIL` row per sent or failed mail of those types, a counter per type and outcome; a resend of a mail already logged as sent is skipped (7.1 validation F-1)
- Notifications, the delivery log and the preferences in the personal data export (format 1.4)
- HTML version of every mail in a university-branded layout, next to the plain text

**Out (where it goes)**

- SMS and browser push: tracker decision 2026-10-09 (no gateway; push with the PWA work of Epic 8); `sms_enabled` and `push_enabled` stay `FALSE` and unused
- WebSocket or server-sent events: not planned (design review H-1)
- Daily digests of notifications: not planned
- Purging notifications past `expires_at`: retention job of Epic 6, Feature 6.2
- A person's notifications at erasure: removed by `ON DELETE CASCADE` when the account is deleted; pseudonymised accounts are handled by the Epic 6 erasure story (noted in the data dictionary)
- A notification to reviewers when a request enters their queue: no event exists; the queue and the overdue digest cover it; parked for the Epic 9 feedback round
- An administration screen for the delivery log: not planned; the operator uses the counter and psql
- Per-user language for mails: mails stay bilingual (Feature 4.1 validation F-3); in-app texts follow the interface language
- Security and privacy mails (verification, password reset, address change and restore, new device, lock, data export): always sent, no preference, no in-app copy, not in the delivery log (tracker decision 2026-10-09)

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-66 (#183) | 7.2.1 In-app notifications from award and review events | 5 | no | 7.1 (registry, `notification` module) |
| SCRUM-67 (#184) | 7.2.2 Notification centre in the toolbar | 5 | yes | 7.2.1 contract (§8.1); UI only |
| SCRUM-16 (#39) | 1.3.2 Notification preferences | 3 | yes | 7.2.1 (types, recipient ids) |
| SCRUM-68 (#185) | 7.2.3 Delivery log and notifications in the personal data export | 3 | no | 1.3.2 (mails in `notification`) |
| SCRUM-69 (#186) | 7.2.4 HTML e-mail templates in the university brand | 3 | no | 1.3.2 (preferences link in the footer) |

7.2.1 is backend only; 7.2.2 is frontend only and can start from the stubs. 1.3.2 has both parts and splits at the green backend gate.

## 4. Acceptance criteria

**Types** (`notifications.type`, `notification_preferences.event_type`): `AWARD_APPROVED`, `AWARD_REJECTED`, `AWARD_RETURNED`, `AWARD_ESCALATED` (from `AwardDecided` by outcome), `AWARD_CORRECTED` (`AwardCorrected`), `REVIEW_OVERDUE` (`OverdueNoticed`), `REVIEW_TAKEN_OVER` (`ReviewTakenOver`), `ROLE_ASSIGNED`, `ROLE_REVOKED`, `DELEGATION_CREATED`, `DELEGATION_REVOKED`. Recipients are those of today's mail for the same event.

### 7.2.1 In-app notifications from award and review events (SCRUM-66)

- **AC-1.1** Given V038 is applied, then `ck_notifications_type` and `ck_notification_preferences_event` list exactly the eleven types; `notifications` has `payload JSONB NOT NULL` and `dedupe_key VARCHAR(64) NOT NULL` with a unique index, and no `title` or `message` column (§8.2).
- **AC-1.2** Given an event of the type list commits, when the in-app listener of `notification` runs, then one `IN_APP` row per recipient is written with the type, `payload` (§8.3), `reference_type`/`reference_id` (`AWARD` and the award id for award types, null otherwise), `sent_at` now and `expires_at` = `sent_at` + 1 year.
- **AC-1.3** Given the same event reaches the in-app listener twice (registry retry), then the second insert hits the dedupe key and writes nothing; the publication completes.
- **AC-1.4** Given each listed event, then it carries the recipient's user id (`userId`; `recipients` with id and address for `DelegationRevoked`) and the moment it occurred (`occurredAt`); the JSON round trip of every changed event holds, and every existing mail is unchanged.
- **AC-1.5** Given a recipient id that no longer exists or an account without an address (pseudonymised), then no row is written and the publication completes.
- **AC-1.6** Given a mail listener and the in-app listener on one event and the mail server down, then two publications are written, the in-app one completes, the mail one stays incomplete and is retried alone (7.1 validation F-4).
- **AC-1.7** Given a signed-in person, when `GET /api/v1/notifications?unread=&page=&size=` is called, then only the caller's `IN_APP` rows come back, newest first, page size 20 by default, values outside 1–100 clamped as on the other paged endpoints; `unread=true` filters to unread.
- **AC-1.8** Given a signed-in person, when `GET /api/v1/notifications/unread-count` is called, then `{ "unread": n }` counts the caller's unread `IN_APP` rows.
- **AC-1.9** Given the caller's notification, when `POST /api/v1/notifications/{id}/read` is called, then `is_read` and `read_at` are set and 204 is returned; an already-read one stays unchanged with 204. Another person's id, an `EMAIL` row or an unknown id gives 404 (`urn:awards:problem:notification-not-found`).
- **AC-1.10** Given the caller has unread notifications, when `POST /api/v1/notifications/read-all` is called, then all of them are marked read and `{ "marked": n }` is returned; with none, `{ "marked": 0 }`.
- **AC-1.11** Given no token, then each endpoint answers 401. Given the documentation, then DATA_DICTIONARY §5.1 describes the columns, types, payload and retention, and `openapi.yml` loses `x-status: planned` on the four operations.

### 7.2.2 Notification centre in the toolbar (SCRUM-67)

- **AC-2.1** Given a signed-in person, then the toolbar shows a bell button before the account menu, with a badge of the unread count (hidden at 0, "99+" above 99) and an accessible name that includes the count.
- **AC-2.2** Given the tab is visible, then the count is fetched at start, every 60 s and when the tab becomes visible again; while hidden or signed out nothing is fetched; a 401 stops the polling.
- **AC-2.3** Given the bell is opened, then a menu lists the latest ten notifications (unread marked by weight and a dot), each with its text in the interface language rendered from type and payload, and a relative time; an empty-state text when there are none.
- **AC-2.4** Given an item is chosen, then it is marked read (only if unread), the badge updates, and the app navigates to its target: the award page for award types and `REVIEW_TAKEN_OVER`, `/reviews` for `REVIEW_OVERDUE`, `/profile` for role types, `/delegations` for delegation types.
- **AC-2.5** Given unread notifications, then the menu and the page offer "Mark all as read", which empties the badge; with none it is disabled.
- **AC-2.6** Given `/notifications`, then the page lists the caller's notifications with a paginator (20 per page) and an "Unread only" toggle kept in the query string, with the same item behaviour as the menu.
- **AC-2.7** Given the language is switched, then the texts change without a reload; uk and en keys exist for every type; the screens pass the screen check (360 / 1280 px, light / dark, both languages, axe clean).

### 1.3.2 Notification preferences (SCRUM-16)

- **AC-3.1** Given a signed-in person without stored preferences, when `GET /api/v1/users/me/notification-preferences` is called, then all eleven types come back with `email: true` and `inApp: true`.
- **AC-3.2** Given `PUT /api/v1/users/me/notification-preferences` with a list of types and channels, then the given types are stored (one row per type, updated on change), types not in the list keep their state, and the full list is returned.
- **AC-3.3** Given a type with both channels off, an unknown type or a repeated type, then 422 `validation-failed` with `errors[]` naming the entry (`both-off`, `invalid`, `duplicate`) and nothing is stored.
- **AC-3.4** Given an effective change, then one `NOTIFICATION_PREFERENCES_CHANGED` audit row records the changed types with old and new values; a PUT that changes nothing writes no row.
- **AC-3.5** Given the decision, correction, overdue, hand-over, role and delegation mail listeners, then they live in `notification` (the first four moved from `award`), and a mail is sent only if the recipient's preference for the type has e-mail on; the in-app listener writes a row only if in-app is on. Subjects and bodies are unchanged.
- **AC-3.6** Given the profile page, then a "Notifications" card (anchor `#notifications`) lists the types in three groups (my awards, reviewing, roles and delegations) with an e-mail and an in-app checkbox each; the last checked box of a type is disabled with a hint; "Save" sends one PUT and shows a confirmation; a line names the security mails as always sent.
- **AC-3.7** Given the documentation, then PRIVACY_BY_DESIGN §4.2 and DATA_DICTIONARY state that `notification_preferences` is the one store and `EMAIL_NOTIFICATIONS` is not used, and `openapi.yml` replaces `NotificationPreferences` and the `UserUpdateRequest` note.

### 7.2.3 Delivery log and notifications in the personal data export (SCRUM-68)

- **AC-4.1** Given V039 is applied, then `notifications` has `delivery_status VARCHAR(20) NULL` with `ck_notifications_delivery_status` (`SENDING`, `SENT`, `FAILED` for `EMAIL`, null for `IN_APP`) and `delivered_at TIMESTAMPTZ NULL`.
- **AC-4.2** Given a mail listener of a preference type, when it runs, then it claims an `EMAIL` row by dedupe key (`SENDING`) before the send, and sets `SENT` with `delivered_at` after `MailDelivery` returns, or `FAILED` when it throws (the exception still propagates, so the publication stays incomplete).
- **AC-4.3** Given an `EMAIL` row for the same dedupe key is already `SENT`, or `SENDING` and younger than 10 minutes, when the listener runs again, then no mail is sent and the publication completes (F-1). A `FAILED` row or a `SENDING` one older than 10 minutes is claimed again.
- **AC-4.4** Given deliveries, then the counter `award.notifications.delivered` with tags `channel` (`email`, `in_app`), `type` and `outcome` (`sent`, `failed`, `skipped`, `off`) appears in `/actuator/prometheus`.
- **AC-4.5** Given the export, then `export_metadata.format_version` is `1.4`, `notifications` lists each row (type, channel, payload, sent, read and delivered moments, delivery status) and `notification_preferences` the stored choices (snake_case like every section of the file); the user guide's export section names both.
- **AC-4.6** Given the documentation, then DATA_DICTIONARY §5.1 describes the delivery columns, and the data export section of PRIVACY_BY_DESIGN lists the new parts.

### 7.2.4 HTML e-mail templates in the university brand (SCRUM-69)

- **AC-5.1** Given any mail sent through `MailDelivery`, then it is `multipart/alternative` with the existing plain text unchanged and an HTML part.
- **AC-5.2** Given the HTML part, then it uses one Thymeleaf layout (`templates/mail/layout.html`): header with the university name in uk and en and the brand colour, the text split into paragraphs, the first application link rendered as a button and every link clickable, a footer with the sender line; inline CSS only, no remote images or tracking, width ≤ 600 px.
- **AC-5.3** Given a mail of a preference type, then the footer links to `/profile#notifications` ("Керувати сповіщеннями / Manage notifications"); security and privacy mails have no such link.
- **AC-5.4** Given text with `<`, `&` or quotes (titles, comments), then it is escaped in the HTML part (Thymeleaf `th:text`); a test mail with `<script>` in a title shows it as text.
- **AC-5.5** Given Mailpit, then its HTML check reports no error-level issue for a decision mail, and the rendered mail reads well at 360 px.

## 5. Edge cases

| Case | Expected |
|------|----------|
| Event published before 7.2.1 and retried after it (no `userId` in its JSON) | Mail listener sends as before; no in-app publication exists for it, so no in-app row |
| In-app insert commits, then the completion update fails | Registry retries; the dedupe key makes the second insert a no-op (AC-1.3) |
| Mail sent, crash before `SENT` is written | Row stays `SENDING`; after 10 minutes the retry claims it and sends again: at-least-once remains, only in this window |
| Two publications of one mail in flight (backlog behind the SMTP lock, F-1) | The second finds `SENDING` younger than 10 minutes or `SENT` and skips |
| Same award returned twice with the same comment | Two events with different `occurredAt`, so two keys and two notifications |
| Recipient switched e-mail off after the event, before the retry | The preference is read when the listener runs; the retry does not send (`outcome=off`) |
| Preferences changed in two tabs | Last PUT wins per type; each effective change audited |
| `DelegationRevoked` with two recipients | One in-app row per recipient, each with its own key; recipients as in today's mail |
| Notification for an award the person can no longer open (role revoked, award withdrawn) | The row stays; the link leads to the award page's no-access or not-found handling |
| Person reads a notification in one tab, the other tab still shows it unread | The next poll corrects the badge; marking it again is a 204 no-op |
| Page size above 100 or negative page | Clamped (`PageParam`, `SizeParam`), as on the other paged endpoints |
| Payload text with HTML | Rendered as text in Angular (interpolation) and in the mail (AC-5.4) |
| Very long award title in the menu | Two lines with an ellipsis; full text on the page |
| Account deleted | `ON DELETE CASCADE` removes rows and preferences |

## 6. Dependencies

### Tables

| Table | State | Used for |
|-------|-------|----------|
| `notifications` | Existing (V011); V038 (7.2.1), V039 (7.2.3) | In-app notifications and the e-mail delivery log |
| `notification_preferences` | Existing (V011); type check in V038 | Preferences (1.3.2) |
| `event_publication` | Existing (V036) | One publication per listener |
| `audit_logs` | Existing | `NOTIFICATION_PREFERENCES_CHANGED` (new `AuditAction`, no constraint change) |

### Endpoints

| Endpoint | State | Story |
|----------|-------|-------|
| `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all` | New | 7.2.1 |
| `GET`, `PUT /users/me/notification-preferences` | New | 1.3.2 |
| `GET /users/me/export` | Changed (format 1.4) | 7.2.3 |

### Services

- `notification`: `InAppNotifications` (one listener per event, writes rows), `NotificationController`, `NotificationService`, `NotificationPreferenceService` and its controller (1.3.2), `NotificationKeys` (dedupe key: SHA-256 of type, channel, recipient id, reference and `occurredAt`), `DeliveryLog` (7.2.3); a `service` named interface with a lookup for `gdpr` (7.2.3)
- `award`, `user`, `delegation`: events gain `userId`/`recipients` and `occurredAt` (7.2.1)
- `award.service.{Decision,Correction,Overdue,TakeOver}Mails` move to `notification` (1.3.2); `OverdueMails` keeps its metric
- `common.mail.MailDelivery`: HTML part from the layout (7.2.4); `MailContent` (text, preferences-link flag)
- `gdpr.PersonalDataAssembler`: `notifications`, `notification_preferences` (7.2.3), read through `notification.service.NotificationLookup`
- ADR-022 module table: `notification` also depends on `award` (events); `gdpr` → `notification` (`service`)

### Frontend

- `core/notifications/`: `NotificationApi`, `UnreadCount` (signal store, polling as in `award-status.component.ts`), `notificationText` (type and payload → i18n key and params)
- `core/layout/shell.component`: bell button and `mat-menu`
- `features/notifications/`: page `/notifications` (lazy, `authGuard`)
- `features/profile/`: "Notifications" card (1.3.2)
- i18n `notifications.*` in uk and en

### External systems

- SMTP (Mailpit locally, Brevo for the demo): HTML parts; Brevo accepts multipart as sent
- Thymeleaf (already a dependency, `spring-boot-starter-thymeleaf`)

## 7. Technical decisions

Inherited: ADR-006 addendum (in-process events on the publication registry, at-least-once), ADR-022 (modules, named interfaces, verification test), ADR-021 (one instance), ADR-005 (Flyway), tracker decisions of 2026-10-09 (channels, polling, security mails always sent, 1.3.2 in this epic), 7.1 PRD decision 12 (listener ids).

**Proposed deviations and assumptions**

1. **Structured payload instead of `title` and `message`** (tracker deviation 4, data dictionary §5.1). No language is stored per person and the interface switches between uk and en, so a stored sentence would be in the wrong language half the time. V038 drops `title` and `message` (never written) and adds `payload JSONB`; the frontend renders the text from type and payload with Transloco. The payload holds only what the sentence needs (§8.3), not the reviewer's comment or the corrected values, which the award page shows to whoever may see them.
2. **One table for in-app rows and the delivery log** (tracker deviation 5). `notifications.channel` already separates `IN_APP` and `EMAIL`; V039 adds `delivery_status` and `delivered_at`. The centre reads only `IN_APP`; the export reads both. A separate table would duplicate the type, recipient, payload and retention.
3. **Dedupe key per channel and event.** Events gain `occurredAt` and the recipient id; the key is SHA-256 over type, channel, recipient id, reference (award id or none) and `occurredAt`, unique in `notifications`; the reference keeps two awards decided in one batch apart even at the same instant. It makes the in-app insert idempotent at once (7.2.1) and lets the mail listener claim a send (7.2.3, F-1). The `SENDING` timeout of 10 minutes equals `app.events.retry-after`.
4. **One store for the e-mail choice** (tracker deviation 6). `notification_preferences` holds it per type and channel; consent type `EMAIL_NOTIFICATIONS` is marked "not used", like `PUBLIC_VISIBILITY`. Service messages about one's own awards and roles rest on the contract, not on consent; the preference is a convenience, and its changes are audited.
5. **No rows until a change.** A missing row means both channels on, so no backfill for existing accounts and no write at registration (the data dictionary's "default preferences created on registration" changes).
6. **At least one channel per type** (the data dictionary rule "at least one channel for mandatory notifications", applied to every type). A person can stop the mails but keeps the record of a decision in the app; 422 `both-off` otherwise.
7. **Types aligned with events** (tracker deviation 4). `AWARD_SUBMITTED`, `REVIEW_ASSIGNED`, `DEADLINE_REMINDER` and `SYSTEM_ALERT` are dropped (no event); `AWARD_ESCALATED`, `AWARD_CORRECTED`, `REVIEW_OVERDUE`, `REVIEW_TAKEN_OVER` and the four role and delegation types are added. Both tables are empty, so V038 replaces the checks without data changes.
8. **Mail listeners move into `notification` at 1.3.2** (7.1 PRD scope "Out"). The registry identifies listeners by method, so pending mail publications under the old ids are left to the 24-hour / 30-day rule, as in 7.1 decision 12.
9. **In-app listener on the registry, own transaction** (`@ApplicationModuleListener`: async, after commit, new transaction), not inside the publishing transaction. A failed insert is retried instead of rolling back a decision; the two-listener IT closes F-4.
10. **`openapi.yml` paths** under `/notifications` (new tag `Notifications`) and `/users/me/notification-preferences`; `NotificationPreferences` is rewritten (tracker deviation 7). Mark-read is `POST …/read` (an action, as `/awards/{id}/submit`) and idempotent with 204, so a repeated read is not an error; the "hidden in state" detour for it checks the UI sends nothing.
11. **HTML from the plain text, one layout** instead of a template per mail type. Every mail gets the brand in one story; the plain text stays the source and the text part, so the existing tests keep proving content. Per-type HTML can follow if the layout reads poorly.
12. **US-005 "mobile notifications" and BRD §6.2 SMS/push** stay out (tracker deviation 3); the user story's definition of done gets a note at the documentation sync at the end of the epic.
13. **Points**: 19 as tracked. If the sprint runs short, 7.2.4 moves to sprint 6 first (tracker decision on story order).

## 8. Contract

### 8.1 OpenAPI stubs

Written to `docs/api/openapi.yml` in this PRD's PR, each with `x-status: planned` and the story in `description`:

| Operation | Request | Response | Problems |
|-----------|---------|----------|----------|
| `GET /notifications` (`listNotifications`, 7.2.1) | query `unread` (boolean, default false), `PageParam`, `SizeParam` | 200 `NotificationPage` (`Page` with `content`: `Notification`[]) | 400 `invalid-parameter`; 401 |
| `GET /notifications/unread-count` (`countUnreadNotifications`, 7.2.1) | — | 200 `UnreadCount` { `unread`: integer } | 401 |
| `POST /notifications/{id}/read` (`markNotificationRead`, 7.2.1) | — | 204 | 401; 404 `notification-not-found` |
| `POST /notifications/read-all` (`markAllNotificationsRead`, 7.2.1) | — | 200 `MarkedCount` { `marked`: integer } | 401 |
| `GET /users/me/notification-preferences` (`getNotificationPreferences`, 1.3.2) | — | 200 `NotificationPreferences` { `preferences`: `NotificationPreference`[] } | 401 |
| `PUT /users/me/notification-preferences` (`updateNotificationPreferences`, 1.3.2) | `NotificationPreferences` | 200 `NotificationPreferences` | 401; 422 `validation-failed` (`both-off`, `invalid`, `duplicate`) |

`Notification` { `id` (int64), `type` (`NotificationType`), `payload` (object, §8.3), `referenceType` (`AWARD` or null), `referenceId` (int64 or null), `read` (boolean), `sentAt` (date-time) }. `NotificationPreference` { `type`, `email` (boolean), `inApp` (boolean) }. `NotificationType`: enum of the eleven types.

### 8.2 Migration outlines

| File | Story | Content |
|------|-------|---------|
| `V038__notification_types.sql` | 7.2.1 | `ALTER TABLE notifications DROP COLUMN title, DROP COLUMN message, ADD COLUMN payload JSONB NOT NULL DEFAULT '{}'::jsonb, ADD COLUMN dedupe_key VARCHAR(64) NOT NULL`; `CREATE UNIQUE INDEX uk_notifications_dedupe_key ON notifications (dedupe_key)`; drop and re-create `ck_notifications_type` and `ck_notification_preferences_event` with the eleven types; `CREATE INDEX idx_notifications_user_channel_sent ON notifications (user_id, channel, sent_at DESC)` (list query); comments updated. The table is empty, so `NOT NULL` without a default for `dedupe_key` is safe |
| `V039__notification_delivery.sql` | 7.2.3 | `ADD COLUMN delivery_status VARCHAR(20) NULL, ADD COLUMN delivered_at TIMESTAMPTZ NULL`; `ck_notifications_delivery_status CHECK ((channel = 'IN_APP' AND delivery_status IS NULL) OR (channel = 'EMAIL' AND delivery_status IN ('SENDING', 'SENT', 'FAILED')))`; comments |

1.3.2, 7.2.2 and 7.2.4 have no migration.

### 8.3 Payload per type

| Type | Payload fields | Target |
|------|----------------|--------|
| `AWARD_APPROVED`, `AWARD_REJECTED`, `AWARD_RETURNED`, `AWARD_ESCALATED` | `title`, `titleUk`, `reviewer`, `level` | `/awards/{id}` |
| `AWARD_CORRECTED` | `title`, `titleUk`, `reviewer`, `fields` (names only) | `/awards/{id}` |
| `REVIEW_OVERDUE` | `count`, `oldestDeadline` | `/reviews` |
| `REVIEW_TAKEN_OVER` | `title`, `titleUk`, `reviewer` | `/awards/{id}` |
| `ROLE_ASSIGNED`, `ROLE_REVOKED` | `role`, `organization`, `organizationUk`, `validTo` | `/profile` |
| `DELEGATION_CREATED`, `DELEGATION_REVOKED` | `role`, `organization`, `organizationUk`, `otherParty`, `validTo` | `/delegations` |

`REVIEW_TAKEN_OVER` points at the award; the former holder may no longer open it and then sees the award page's no-access message (§5).

## 9. Test plan

| AC | Unit | IT | FT | E2E / other |
|----|------|----|----|-------------|
| 1.1 | | ✓ migration IT (constraints, index, columns) | | |
| 1.2, 1.3, 1.5 | ✓ payload mapping per type; key derivation | ✓ registry IT: decision commit → one row; second delivery → none; missing user → none | | |
| 1.4 | ✓ JSON round trip per changed event | | ✓ existing mail FTs | |
| 1.6 | | ✓ two-listener IT with a failing `JavaMailSender` | | |
| 1.7–1.11 | ✓ service paging and scoping | ✓ repository queries | ✓ REST-assured: own rows only, 404 on another's, 401, idempotent read, read-all | |
| 2.1–2.6 | ✓ store, polling with fake timers, text renderer per type, components | | | ✓ Playwright: decision → badge 1 → open → award page, badge 0; mark all; page filter |
| 2.7 | ✓ i18n key check | | | screen check of the toolbar and `/notifications` |
| 3.1–3.4 | ✓ validation, merge, audit diff | ✓ upsert | ✓ GET defaults, PUT, 422 cases, audit row | |
| 3.5 | ✓ listener gating per channel | ✓ e-mail off → no mail, row; in-app off → mail, no row | ✓ decision with e-mail off: Mailpit empty | |
| 3.6 | ✓ card component | | | ✓ Playwright: e-mail off for approvals, save, reload keeps it; screen check of `/profile` |
| 4.1–4.3 | ✓ claim rules with a fixed clock | ✓ claim race (two threads, one send); `FAILED` reclaimed; stale `SENDING` reclaimed | | |
| 4.4 | ✓ counter tags | | ✓ prometheus lists the counter | |
| 4.5 | ✓ assembler | | ✓ export contains both sections | |
| 5.1–5.4 | ✓ layout rendering, escaping, link button, footer rule, MIME parts | | ✓ HTML part through the Mailpit API (decision mail, password reset); existing mail FTs read the text part | |
| 5.5 | | | | manual step 12 |
| 1.11, 3.7, 4.6 | | | | doc review in the PR |

Coverage 85 % lines; static clean. 7.2.1 (migration, endpoints), 1.3.2 (endpoint, audit) and 7.2.3 (migration, export) go through the security review.

## 10. Manual verification

Expected texts checked against the merged i18n files on 2026-10-15 (step 5 after SCRUM-82). Steps 27–31 come from the validation's scenario review and have not been run yet.

Preconditions: `docker compose up -d postgres redis mailpit minio clamav`; backend in `backend/` with `$env:APP_EVENTS_RETRY_INTERVAL='PT1M'; $env:APP_EVENTS_RETRY_AFTER='PT1M'; ./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (as in 7.1); frontend `npm start` on `http://localhost:4200`. Mailpit `http://localhost:8025`; psql `docker compose exec postgres psql -U postgres award_monitoring`; Swagger `http://localhost:8080/swagger-ui.html`. Seed accounts (password `Passw0rd-demo`, `@chnu.edu.ua`): `employee.fmi`, `secretary.fmi`, `secretary2.fmi`, `dean.fmi`, `admin`. Preparation: as `employee.fmi` create and submit awards A, B, C, E (department-level category) and D (faculty-level, passed on to the dean).

1. psql `\d notifications`. Expected: `payload`, `dedupe_key`, `delivery_status`, `delivered_at`; no `title` or `message`; the type check lists the eleven types. (AC-1.1, 4.1)
2. As `secretary.fmi` approve A. Expected: Mailpit has the decision mail with HTML and text parts; psql `select type, channel, delivery_status from notifications where user_id = (select user_id from users where email_address = 'employee.fmi@chnu.edu.ua');` → `AWARD_APPROVED IN_APP` and `AWARD_APPROVED EMAIL SENT`. (AC-1.2, 4.2, 5.1)
3. As `employee.fmi` at `http://localhost:4200`. Expected: the bell shows 1 within 60 s and its accessible name reads "Сповіщення, непрочитаних: 1"; the menu shows «A» схвалено (рівень: секретар факультету): <reviewer's name>, a dot, bold text and «зараз» or «N хвилин тому»; switch to English: "«A» approved by <reviewer's name> at the faculty secretary level" without a reload; "See all notifications" opens `/notifications`. (AC-2.1–2.3, 2.7)
4. Choose the item. Expected: the award page of A opens; the badge disappears. (AC-2.4)
5. `secretary.fmi` returns B with a comment and rejects C; D is passed on at the faculty level. Expected: three new items for `employee.fmi` (returned, rejected, passed on); the returned item carries no comment text; the passed-on item names the target level ("passed on to the dean level", uk "передано на рівень «декан»"); "Mark all as read" empties the badge. (AC-1.2, 2.5, §8.3)
6. Open `http://localhost:4200/notifications` and turn on "Unread only". Expected: the list, `?unread=true` in the URL, the empty state after step 5. (AC-2.6)
7. As `employee.fmi` on `/profile`, untick e-mail for "Award returned" and save. Expected: confirmation; the in-app box of that row is disabled; a reload keeps the choice; psql `audit_logs` has `NOTIFICATION_PREFERENCES_CHANGED`. (AC-3.2, 3.4, 3.6)
8. Correct B and resubmit; `secretary.fmi` returns it again. Expected: an in-app item, no new mail in Mailpit; `http://localhost:8080/actuator/prometheus` the `award_notifications_delivered_total` series with `channel="email"`, `outcome="off"` and `type="AWARD_RETURNED"` (labels in alphabetical order, next to `application` and `environment`) grew by 1. (AC-3.5, 4.4)
9. `secretary.fmi` corrects A through the correction flow. Expected: a mail and an in-app item about the correction of «A» naming the fields. (AC-1.2)
10. As `admin` assign `secretary2.fmi` a role; as `dean.fmi` delegate to `secretary2.fmi` and revoke. Expected: three items for `secretary2.fmi` (role, delegation, revoked) and no revoke item for `dean.fmi`, who revoked it; delegate again and revoke as `admin`, and `dean.fmi` gets the revoke item too; the links lead to `/profile` and `/delegations`. (AC-1.2, 2.4)
11. As `employee.fmi` call `GET /api/v1/users/me/export`. Expected: `export_metadata.format_version` `1.4`; `notifications` with the rows of steps 2–9 including the `EMAIL` rows with `delivery_status` `SENT` and `delivered_at`; `notification_preferences` with the returned-award choice (`email` false, `in_app` true). (AC-4.5)
12. Mailpit: open the decision mail of step 2, tabs HTML and HTML Check; narrow the window to 360 px. Expected: brand header, button for "Переглянути нагороду", footer link to `/profile#notifications`; no error-level finding. Request a password reset and open that mail. Expected: the same layout, no preferences link. (AC-5.2, 5.3, 5.5)

### Detours

13. Swagger as `employee.fmi`: `POST /notifications/{id}/read` with the id of a `secretary2.fmi` row (from psql). Expected: 404; that row stays unread. The same with an `EMAIL` row id of `employee.fmi`. Expected: 404. (AC-1.9)
14. Hidden in state, read item: in the menu a read item has no unread dot, and choosing it only navigates (Network shows no `read` request). In Swagger the same `POST …/read` answers 204 and `read_at` is unchanged. (AC-1.9, 2.4)
15. Hidden in state, nothing unread: open the bell. Expected: "Mark all as read" disabled; `POST /notifications/read-all` → `{ "marked": 0 }`. (AC-1.10, 2.5)
16. Hidden in state, last channel: on `/profile` the second box of a type cannot be unticked; PUT with both channels off for `AWARD_APPROVED` → 422 `both-off`; unknown type `SYSTEM_ALERT` → 422 `invalid`. (AC-3.3, 3.6)
17. Call any notification endpoint without a token. Expected: 401. Let the access token expire with the app open. Expected: polling stops, the sign-in redirect applies, and polling resumes after signing in. (AC-1.11, 2.2)
18. Switch to another tab for two minutes while `secretary.fmi` approves E, then return. Expected: no count request while hidden (Network), one on return; the badge updates. (AC-2.2)
19. Open the centre in two browsers as `employee.fmi`; read an item in one. Expected: the other shows it unread until its next poll; choosing it there works (204). (§5)
20. Revoked mid-flow, role: `secretary.fmi` holds a claimed review of a new award F; `secretary2.fmi` takes it over, so `secretary.fmi` has a `REVIEW_TAKEN_OVER` item; with the bell menu open, `admin` revokes `secretary.fmi`'s role. Expected: the revocation ends the session (the next request answers 401 and the sign-in form opens); after signing in again the item stays listed and can be marked read; following it opens `/forbidden`; `secretary2.fmi`, still a secretary at the same level, keeps working on F. (§5, AC-2.4)
21. Revoked mid-flow, delegation: `dean.fmi` revokes the delegation while `secretary2.fmi` has a delegated review open. Expected: `secretary2.fmi` gets `DELEGATION_REVOKED` in-app and by mail; the centre and the notification endpoints keep working for them, as they need no role. (AC-1.2)
22. With Mailpit stopped, `secretary.fmi` approves another award. Expected: the in-app item appears at once; psql shows the `EMAIL` row `FAILED` and one incomplete publication (the mail listener only). Start Mailpit. Expected: within 2 minutes the mail arrives once and the row is `SENT`. (AC-1.6, 4.2)
23. Repeat step 22 and stop the backend right after the mail arrives; start it again. Expected: the mail is in Mailpit once; one `EMAIL` row for its key. The race itself is proven by the claim IT. (AC-4.3)
24. Reload `/notifications?unread=true` and press Back after "Mark all as read". Expected: the filter is kept, nothing is marked twice, the list stays empty. (AC-2.6)
25. With e-mail switched off for every type, request a password reset. Expected: the reset mail still arrives. (scope: security mails)
26. Send step 2's approval again in Swagger. Expected: 409 from the workflow; no new notification row. (AC-1.2)
27. Signed out, open the footer link of step 12's mail (`/profile#notifications`). Expected: the sign-in form; after signing in the profile opens scrolled to the "Notifications" card. (AC-5.3, 3.6)
28. Open `/profile#notifications` in two tabs; in tab 1 untick e-mail for "Award returned" and save; in tab 2, not reloaded, untick in-app for "Award approved" and save. Expected: "Award returned" keeps e-mail off, "Award approved" has in-app off; one audit row per save. (§5, SCRUM-83)
29. As a fresh account on `/profile` change a box, then revoke the session by confirming a password reset for that account in another browser or through the API (deleting `oauth2_authorization` rows only blocks the next refresh: the access token stays valid for 15 minutes and the login session signs the SPA in again), press Save. Expected: the sign-in redirect or an error line; after signing in the stored choice is unchanged and the form loads fresh. (AC-3.6)
30. Stop Redis (`docker compose stop redis`) with the bell open; wait for a poll, open the menu, mark one read, save a preference. Expected: all answer as with Redis up; start Redis again. (§5)
31. Open `http://localhost:4200/notifications?page=999` and `?unread=junk`. Expected: an empty page with a working paginator back to page 1, or the first page; no console error. (AC-2.6)

### Run 2026-10-15

container stack (app, frontend at http://localhost) on develop b776fc0, APP_EVENTS_RETRY_INTERVAL/AFTER=PT1M; Mailpit, psql, Playwright MCP. Result: 26 pass.

| Step | Result | Evidence |
|---|---|---|
| 1 | PASS | payload, dedupe_key, delivery_status, delivered_at present; no title/message; type check lists 11 types |
| 2 | PASS | award 425 approved; mail 14:05 with HTML+text parts; AWARD_APPROVED IN_APP and EMAIL SENT |
| 3 | PASS | bell 'Сповіщення, непрочитаних: 1' on load; uk/en texts match, bold+dot, 'зараз'/'now', switch without reload, See all -> /notifications |
| 4 | PASS | item opens /awards/425 'MR72 A'; badge aria-label 'Notifications' (no count) |
| 5 | PASS | 3 new items (escalated/rejected/returned) for employee.fmi; payload has no comment text; Mark all as read -> badge empty. Defect noted: escalated text says 'from the dean level' (level = new level), SCRUM-82 / #239 |
| 6 | PASS | Unread only -> /notifications?unread=true, empty state 'No unread notifications' |
| 7 | PASS | email unticked for AWARD_RETURNED, snackbar 'Notification settings saved', in-app box disabled+checked, reload keeps it; NOTIFICATION_PREFERENCES_CHANGED in audit_logs (5 rows, extra saves from my toggling AWARD_APPROVED back and forth) |
| 8 | PASS | 426 returned again: in-app item (in_app sent 2.0), 0 mails; email/off/AWARD_RETURNED series 1.0 (new) |
| 9 | PASS | correction of 430 by secretary.fmi: mail 14:10 'A reviewer corrected your award' + IN_APP and EMAIL SENT rows, fields [title] (A was already approved, used pending award G) |
| 10 | PASS | secretary2.fmi: ROLE_ASSIGNED, DELEGATION_CREATED, DELEGATION_REVOKED (IN_APP+EMAIL SENT each); dean's own revoke gives dean no item, revoke by admin (delegation 16) does; links /profile, /delegations in notification-text.ts; step text corrected |
| 11 | PASS | format_version 1.4; 11 notification rows (6 IN_APP, 5 EMAIL SENT with delivered_at); preferences AWARD_RETURNED email=false in_app=true (+AWARD_APPROVED both true from step 7 toggling) |
| 12 | PASS | approval mail HTML: header, button 'Переглянути нагороду' -> /awards/431, footer link /profile#notifications; Mailpit HTML check 86.6% supported / 0.88% unsupported, no error level; 360 px: scrollWidth=clientWidth=360, button 208 px wide; password-reset mail same layout (600 px container), one link, no preferences link |
| 13 | PASS | secretary2 row 112 and own EMAIL row 101: both 404 notification-not-found; row 112 still unread |
| 14 | PASS | read item has no 'Unread' marker; click navigates to /awards/431 with no POST .../read (only the poll); API POST read of read row answers 204, read_at unchanged |
| 15 | PASS | nothing unread: 'Mark all as read' disabled; POST read-all -> {marked:0} |
| 16 | PASS | UI: unticking email makes in-app box disabled (not saved); PUT both off AWARD_APPROVED 422 both-off; SYSTEM_ALERT 422 invalid |
| 17 | PASS | no token: GET/POST notification endpoints 401; dean.fmi session with deleted authorizations: after the backend restart (14:25) the page sat on /login, 0 count requests in 65 s; after sign-in polling resumed (unread-count 200 at 14:28:16) |
| 18 | PASS | tab hidden 14:14:47-14:20:44 (visibilityState override), E (428) approved 14:20:26: 0 unread-count requests while hidden, 1 on return, badge 'Notifications, 1 unread' |
| 19 | PASS | two contexts as employee.fmi on /notifications: read E in #2 -> #1 still shows it under 'Unread:' until its next poll; choosing it in #1 -> POST read 204, opens /awards/428 |
| 20 | PASS | secretary.fmi claimed 435 (deadline moved past in psql), secretary2.fmi took over -> REVIEW_TAKEN_OVER item 141; admin revoked role 152 with menu open: mark-read 401 + sign-in form (token revocation by design); after sign-in item listed, read 204, link -> /forbidden; secretary2.fmi approved 435; role re-granted (344); step text corrected |
| 21 | PASS | dean.fmi revoked delegation 17 while secretary2.fmi had 432's reviewer item open: DELEGATION_REVOKED IN_APP + EMAIL SENT, mail 14:21:37; unread-count 200 {unread:7}, list 200, read-all {marked:7}; reviewer item now 404 |
| 22 | PASS | Mailpit down 14:22:00, 433 approved: IN_APP at once, EMAIL FAILED, one incomplete publication (DecisionMails.onDecided); Mailpit up 14:23:14 -> mail 14:23:16 once, row SENT, 0 incomplete |
| 23 | PASS | Mailpit down, 434 approved -> FAILED, Mailpit up, mail 14:25:09, backend stopped 14:25:10 and started: still 1 mail, 1 EMAIL row SENT, 0 incomplete publications |
| 24 | PASS | /notifications?unread=true reload keeps the toggle on; Mark all as read -> one POST read-all; Back -> /, Forward -> filter kept, list empty 'No unread notifications' |
| 25 | PASS | fresh mr72.allmailoff: e-mail off for all 11 types (PUT 200), password reset 202 -> mail 'Скидання пароля / Password reset' 14:31:15 |
| 26 | PASS | replayed approval of 425: 409 request-closed 'already decided'; notification rows for 425 stay 2 |

### Run 2026-10-15

Compose stack (app, frontend rebuilt from feature/SCRUM-83 30b68c0, PR #244 open), default retry settings; steps 27-31; fresh account pref.run28 for 28-29. Result: 4 pass, 1 fail.

| Step | Result | Evidence |
|---|---|---|
| 27 | PASS | login redirect, after sign-in /profile#notifications, card top at y=0.2 (scrolled), no console errors |
| 28 | PASS | two sessions of pref.run28 (separate contexts): RETURNED email=false kept, APPROVED inApp=false; audit NOTIFICATION_PREFERENCES_CHANGED 8->10 (one per save) |
| 29 | PASS | step text corrected (psql delete only blocks refresh; revoke via password reset): Save -> PUT 401, refresh 400, /login; after sign-in /profile#notifications, REVIEW_OVERDUE email still on, Save disabled, audit rows for user 365 stay 3 |
| 30 | PASS | Redis stopped: bell poll (unread-count) and menu list 200, no console errors; RETURN notification created; POST read 204, unread 1->0; preference PUT 200; Redis restarted |
| 31 | FAIL | page=999: API 200 empty (totalElements 12, totalPages 1) but UI shows «Сповіщень поки немає», no paginator, no way back; unread=junk shows first page (12 rows); no console errors |

### Run 2026-10-15

Container stack, frontend rebuilt from develop 8649062 (SCRUM-84 merged); app at http://localhost, employee.fmi. Result: 1 pass.

| Step | Result | Evidence |
|---|---|---|
| 31 | PASS | Frontend rebuilt from develop 8649062 (SCRUM-84). /notifications?page=999 -> redirected to /notifications (last page = page 1, list shown); ?unread=junk -> first page listed, URL unchanged; 0 console errors |

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Event changes (`userId`, `occurredAt`) ripple through publishers and tests | Medium | Medium | Done once in 7.2.1 with the JSON round-trip tests; no behaviour change for mails |
| Moving four mail listeners in 1.3.2 breaks `ModularityTest` or the overdue metric | Medium | Low | ADR-022 module table updated in the same PR; metric name kept |
| Polling load with many tabs | Low | Low | One count request per visible tab per minute; indexed query |
| The dedupe claim misses a race and sends twice | Low | Low | Unique key with an `INSERT … ON CONFLICT … DO UPDATE … WHERE` claim; two-thread IT |
| HTML mails flagged as spam by Brevo | Low | Medium | No remote images, text part kept, sender domain as today; checked at the demo deployment |
| Sprint 5 ends before 7.2.4 | High | Low | 7.2.4 moves to sprint 6 (decision 13) |

## 12. Definition of Done

- Every AC proven at the layers of §9; `.\tools\gate.ps1` green per story (unit, IT, FT, JaCoCo ≥ 85 %, static 0, frontend lint and tests); Playwright for 7.2.2 and 1.3.2
- Screen check of the toolbar, `/notifications` and the profile card
- Security review for 7.2.1, 1.3.2 and 7.2.3
- `openapi.yml` without `x-status: planned` on the six operations; DATA_DICTIONARY §4 and §5; PRIVACY_BY_DESIGN §4.2 and the export section; ADR-022 module table; user guide (centre, preferences, export); CHANGELOG `[Unreleased]`; tracker and BACKLOG
- Manual verification (§10) run and recorded; feature validated

## 13. Validation (2026-10-15, `develop` at 67dd103)

Result: **PASSED WITH NOTES**. Closed 2026-10-15 at `develop` 9e07cc8: SCRUM-83 (#244) and SCRUM-84 (#247) merged, gate of SCRUM-84 green (static 0, 669 frontend tests), §10 steps 27–31 run (#246, #248), every step of §10 now passes. Stories merged: SCRUM-66 (#234), SCRUM-67 (#235), SCRUM-16 (#236), SCRUM-68 (#237), SCRUM-69 (#238), SCRUM-82 (#241); manual run of §10 on 2026-10-15 (#240): 26 of 26 pass, the one defect fixed by SCRUM-82.

Gate (`gate.ps1 -Full`, 15:03, 3.2 GB free, sequential): 1 059 unit, 361 IT/FT with one failure (`InAppNotificationsIT.ac4_2`, F-6; the class passes 8 of 8 on rerun), coverage 98.8 % lines (4173/4224), Checkstyle/PMD/SpotBugs 0; frontend lint, 663 tests, prod build pass (996 kB initial). e2e: full suite 86 of 91; of the five failures, four pass on a rerun with one worker (mails arriving late under memory pressure), and one is a race in `notifications.spec.ts` fixed here (F-7; the notification centre specs then pass 4 of 4). `openapi.yml` compared with the live `/v3/api-docs` of the container stack: same six notification operations and parameters. Migrations V038 and V039 apply to an empty database in every IT (Testcontainers).

### AC evidence

| AC | Tests | Result |
|----|-------|--------|
| 1.1 | `NotificationSchemaIT.ac1_1_both…`, `ac1_1_payload…`, `ac1_1_the…`; manual 1 | pass |
| 1.2 | `InAppNotificationsIT.ac1_2_aCommittedDecisionWritesOneInAppRowForTheOwner`, `InAppNotificationsTest.ac1_2_…`, `NotificationDraftsTest` (4 × ac1_2), `AuthorityDraftsTest` (2 × ac1_2), `InAppNotificationsRoutingTest.ac1_2_every…`; manual 2, 5, 9, 10, 21, 26 | pass |
| 1.3 | `InAppNotificationsIT.ac1_3_aSecondDeliveryOfTheSameEventWritesNothingAndCompletes`, `InAppNotificationsTest.ac1_3_…`, `NotificationKeysTest` (2 × ac1_3) | pass |
| 1.4 | `AwardEventJsonTest.scrum66_ac1_4_aDecisionStoredWithoutRecipientIdStillReads`, `AccountEventJsonTest.scrum66_ac1_4_aRevocationStoredWithBareAddressesStillReads`, the ac1_8 round trips, existing mail tests and FTs | pass |
| 1.5 | `InAppNotificationsIT.ac1_5_anUnknownOrDeletedRecipientGetsNoRowAndThePublicationCompletes`, `InAppNotificationsTest.ac1_5_…` | pass |
| 1.6 | `InAppNotificationsIT.ac1_6_withTheMailServerDownTheInAppRowIsWrittenAndOnlyTheMailIsRetried`; manual 22 | pass |
| 1.7 | `NotificationFT.ac1_7_theCallerSeesOnlyOwnInAppRowsNewestFirst`, `ac1_7_unreadFiltersAndPageSizeIsClamped`, `NotificationServiceTest` (2 × ac1_7) | pass |
| 1.8 | `NotificationFT.ac1_8_ac1_9_markingReadSetsTheFlagOnceAndCountsDown`, `NotificationServiceTest.ac1_8_…` | pass |
| 1.9 | `NotificationFT.ac1_8_ac1_9_…`, `ac1_9_anotherPersonsAnEmailOrAnUnknownIdIsNotFound`, `NotificationServiceTest` (3 × ac1_9); manual 13, 14 | pass |
| 1.10 | `NotificationFT.ac1_10_readAllMarksTheCallersUnreadRowsOnly`, `NotificationServiceTest.ac1_10_…`; manual 15 | pass |
| 1.11 | `NotificationFT.ac1_11_everyEndpointNeedsAToken`; DATA_DICTIONARY §5.1; `openapi.yml` has no `planned` notification operation, and the live `/v3/api-docs` lists the same six operations and parameters | pass |
| 2.1 | e2e `notifications.spec.ts` "ac2_1 ac2_3 ac2_4 ac2_7 …", `notification-bell.component.spec.ts` ac2_1, `unread-count.spec.ts` ac2_1; manual 3 | pass |
| 2.2 | `unread-count.spec.ts` (5 × ac2_2: start and interval, hidden, signed out, sign-out, 401); manual 17, 18 | pass |
| 2.3 | `notification-bell.component.spec.ts` (2 × ac2_3), `notification-text.spec.ts` (3 × ac2_3); e2e as 2.1; manual 3 | pass |
| 2.4 | `notification-bell…` ac2_4, `notification-list…` ac2_4, `notification-text.spec.ts` ac2_4_leads_each_type_to_its_page, `unread-count.spec.ts` (2 × ac2_4); e2e as 2.1; manual 4, 10, 14, 20 | pass |
| 2.5 | e2e "ac2_5 ac2_6 …", `notification-bell…` ac2_5, `notification-list…` (2 × ac2_5), `unread-count.spec.ts` (2 × ac2_5); manual 5, 15 | pass |
| 2.6 | `notification-list.component.spec.ts` (4 × ac2_6); e2e "ac2_5 ac2_6 …"; manual 6, 24 | pass |
| 2.7 | `notification-text.spec.ts` ac2_7 (key sets, switch); screen checks in #235; manual 3 | pass |
| 3.1 | `NotificationPreferenceFT.ac3_1_without…`, `NotificationPreferenceServiceTest.ac3_1_…` | pass |
| 3.2 | `NotificationPreferenceFT.ac3_2_listed…`, `NotificationPreferenceServiceTest` (2 × ac3_2); manual 7 | pass |
| 3.3 | `NotificationPreferenceFT.ac3_3_…`, `NotificationPreferenceServiceTest` (4 × ac3_3); manual 16 | pass |
| 3.4 | `NotificationPreferenceFT.ac3_4_…`, `NotificationPreferenceServiceTest` (2 × ac3_4); manual 7 | pass |
| 3.5 | `InAppNotificationsIT.ac3_5_eachChannelFollowsTheRecipientsPreferenceForTheType`; ac3_5 tests of `DecisionMails`, `CorrectionMails`, `OverdueMails`, `TakeOverMails`, `AuthorityChangeMails`, `InAppNotificationsTest`; `DecisionFT.ac4_4_aMailSwitchedOffAndAWrittenInAppRowAreCountedAtThePrometheusEndpoint` (Mailpit empty); manual 8, 25 | pass |
| 3.6 | `notification-preferences.component.spec.ts` (4 × ac3_6), e2e `profile.spec.ts` "ac3_6 keeps one channel per type and stores the choice"; manual 7, 16 | pass |
| 3.7 | PRIVACY_BY_DESIGN §4.2, DATA_DICTIONARY, `openapi.yml` (#236) | doc review |
| 4.1 | `DeliveryLogIT.ac4_1_anInAppRowCannotCarryADeliveryStatusAndAMailRowNeedsOne`, `NotificationSchemaIT.ac4_1_…`; manual 1 | pass |
| 4.2 | `DeliveryLogIT.ac4_2_aClaimWritesASendingRowAndFinishRecordsTheOutcome`, `InAppNotificationsIT.ac4_2_…`, `DeliveryLogTest` (2 × ac4_2); manual 2, 22 | pass |
| 4.3 | `DeliveryLogIT.ac4_3_aSentOrFreshlyClaimedMailIsNotClaimedAgain`, `ac4_3_aFailedOrStaleClaimIsTakenOver`, `ac4_3_ofTwoConcurrentClaimsOnlyOneWins`, `InAppNotificationsIT.ac4_3_…`; manual 23 | pass |
| 4.4 | `DeliveryLogTest` (2 × ac4_4), `DecisionFT.ac4_4_…`; manual 8 | pass |
| 4.5 | `PersonalDataNotificationsTest` (2 × ac4_5), `DataExportFT` (format 1.4, both sections); manual 11 | pass |
| 4.6 | DATA_DICTIONARY §5.1, PRIVACY_BY_DESIGN export section (#237) | doc review |
| 5.1 | `MailDeliveryTest.ac5_1_…`, `DecisionFT.ac5_1_ac5_3_ac5_4_aDecisionMailHasAnEscapedHtmlPartWithThePreferencesLink`; manual 2 | pass |
| 5.2 | `MailHtmlRendererTest` (5 × ac5_2); manual 12 | pass |
| 5.3 | `MailHtmlRendererTest` (2 × ac5_3), `MailDeliveryTest.ac5_3_…`, `DecisionFT.ac5_1_ac5_3_…`, `PasswordResetFlowFT` (no preferences link); manual 12 | pass |
| 5.4 | `MailHtmlRendererTest.ac5_4_escapes…`, `DecisionFT.ac5_1_ac5_3_ac5_4_…` | pass |
| 5.5 | manual 12 (Mailpit HTML check: no error-level finding; 360 px) | pass |

### Edge cases (§5)

| Case | Covered by |
|------|------------|
| Event published before 7.2.1, retried after it | `InAppNotificationsTest.edgeCase_anEventStoredBeforeTheRecipientWasCarriedWritesNothing`, `DeliveryLogTest.edgeCase_anEventStoredBeforeItCarriedRecipientAndTimeIsSentWithoutALogRow`, the `scrum66_ac1_4_…` round trips |
| In-app insert commits, completion update fails | `InAppNotificationsIT.ac1_3_…` (second delivery writes nothing) |
| Mail sent, crash before `SENT` | `DeliveryLogIT.ac4_3_aFailedOrStaleClaimIsTakenOver`; manual 23 |
| Two publications of one mail in flight | `DeliveryLogIT.ac4_3_ofTwoConcurrentClaimsOnlyOneWins`, `ac4_3_aSentOrFreshlyClaimedMailIsNotClaimedAgain` |
| Same award returned twice with the same comment | `NotificationKeysTest.ac1_3_…` (the key includes `occurredAt`); manual 8 |
| E-mail switched off before the retry | `InAppNotificationsIT.ac3_5_…` (preference read when the listener runs); a `FAILED` row followed by e-mail off is not tested (F-3) |
| Preferences changed in two tabs | Fixed in SCRUM-83: save sends only the changed types, last PUT wins per type (F-1); manual 28 |
| `DelegationRevoked` with two recipients | `InAppNotificationsIT.edgeCase_aRevokedDelegationWritesOneRowPerRecipientNamingTheOtherSide`, `AuthorityDraftsTest.edgeCase_…`; manual 10, 21 |
| Award the person can no longer open | Manual 20 (`/forbidden`) |
| Read in one tab, unread in the other | Manual 19; `NotificationFT.ac1_8_ac1_9_…` (a second mark is a no-op) |
| Page size above 100 or negative page | `NotificationFT.ac1_7_unreadFiltersAndPageSizeIsClamped`; SPA side SCRUM-84 (out-of-range page moves to the last page), manual 31 |
| Payload text with HTML | Angular interpolation in `notification-text.ts`; `MailHtmlRendererTest.ac5_4_…` |
| Very long title in the menu | Screen check in #235 |
| Account deleted | `ON DELETE CASCADE` in V038 (`NotificationSchemaIT`); others' payloads with the Epic 6 erasure story |

### Security checklist

| Item | Control |
|------|---------|
| A01 Access control | `NotificationController` and `NotificationPreferenceController` under `@PreAuthorize("isAuthenticated()")`; every query takes the caller's id from the token (`findByUserIdAndChannel…`, `markRead(id, userId, IN_APP, …)`); another person's or an `EMAIL` id answers 404 (FT 1.9, manual 13); the security reviews of #234, #236 and #237 found nothing medium or higher |
| A02 Cryptographic failures | Payloads hold titles, names and levels only, no tokens or comment text (§8.3, manual 5); mails carry no tracking or remote images (AC-5.2) |
| A03 Injection | Native inserts and the preference upsert use bound parameters; types validated against the enum (422 `invalid`); HTML escaped by `th:text` in mails and by interpolation in Angular (AC-5.4) |
| A07 Authentication failures | Every endpoint answers 401 without a token (FT 1.11, preference FT); polling stops on 401 (AC-2.2); role revocation ends the session (manual 20); security mails ignore preferences (manual 25). CSRF is disabled for the bearer-token API, and these endpoints are not rate limited (Redis down: manual 30) |

### Findings

- **F-1** Preferences saved from a stale tab write back every type and revert the changes made in another tab (§5 expects last PUT wins per type). Fixed in SCRUM-83 (#242): save sends only the changed types. Two tabs changing different channels of the same type still resolve per type (last PUT wins); manual 28.
- **F-2** Deep link `/profile#notifications` while signed out: the card is scrolled to only if the fragment survives the sign-in redirect; manual 27 passes (card scrolled into view after sign-in).
- **F-3** Not tested: a `FAILED` mail row whose recipient then switches e-mail off; expected `outcome=off` on the retry with the row left `FAILED`. Candidate IT in `InAppNotificationsIT` with the next notification story.
- **F-4** Session expiry on the preferences save shows the generic error (no 401 branch) while the shell redirects; manual 29 passes (redirect to /login, no change saved); the generic message stays.
- **F-5** Refactor sweep, for a follow-up `refactor(notification)` PR: the `ApprovalLevel` uk/en maps duplicated in `DecisionMails` and `OverdueMails`; the `NotificationType` union and `NOTIFICATION_TYPES` list the same literals (derive the type with `as const`); `open()` duplicated in the bell and the list, swallowing errors; the `NotificationRows` test fixture built in four places; hand-rolled polling loops in `InAppNotificationsIT` and `Mailpit`. Left as is (EPIC-07 technical notes): bilingual text helpers across five mail listeners, field labels in Java and in i18n, the index pairing of drafts and recipients in `AuthorityChangeMails`.
- **F-6** `InAppNotificationsIT.ac4_2_aSentMailIsLoggedAsSentWithTheMomentTheServerAcceptedIt` found the row still `SENDING` after its 30 s wait in the full gate (sends serialised behind the one SMTP lock, 7.1 F-1, with 3.2 GB free); 8 of 8 on rerun. Watch in the next gates; a longer wait for this one assertion if it recurs.
- **F-7** `notifications.spec.ts` counted all of the owner's rows and expected 1; since 7.2.3 a sent mail adds an `EMAIL` row, so the count is 2 whenever both land before the first poll. Both queries now filter `channel = 'IN_APP'`.

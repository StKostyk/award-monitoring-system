# Feature 9.4: Dashboards and widget layout

> **Epic**: 9 Production hardening (SCRUM-128) · **Tracker**: `docs/epics/EPIC-09-PRODUCTION-HARDENING-STATUS.md`
> **Sprint**: 11 (2026-11-23 to 2026-11-29), started in Sprint 10 · **Points**: 23
> **Status**: Approved 2026-11-17
> **Governing docs**: ADR-014 and ADR-016 (9.3 addenda: tokens, effect components, chart theme), ADR-015 (NgRx per
> feature), ADR-006 addendum (notifications from committed events), `docs/frontend/UI_GUIDELINES.md` §1-8,
> `DATA_DICTIONARY.md` §1.8 `dashboard_preferences` and §5.1 `notifications`, `openapi.yml` `GET /analytics/me`,
> `GET /analytics/units/{id}`, `GET /analytics/university`, `GET|PUT /users/me/dashboard-preferences`,
> `GET /users`, `GET /users/me/export`; Features 5.1, 5.2 and 5.3 (the data behind every widget), 9.1 AC-2.5
> (export budget), 9.3 §2 "Out" and decision D3; the prototype canvas of 2026-11-14 (screens «Нагороди ЧНУ»,
> secretary's day, «Мої нагороди»)

## 1. Problem and personas

Feature 9.3 restyled every screen but kept the 5.1 to 5.3 dashboards as they were: one personal page with six
hideable cards, a dean's analytics page and a rector's page, all built as reports to open, not as a place to land
after sign-in. The sign-in still lands on a short home page with the caller's submissions.

- **Rector and dean** (Prof. Biloskurskyi, Prof. Martynyuk): open the system a few times a month and want to see at
  a glance how the university or the faculty stands this year against last year, what stands out, and who leads.
- **Faculty secretary** (Olena): works the queue daily; wants today's decisions, the departments that have entered
  nothing this year and a way to nudge them, without going through the analytics pages.
- **Employee** (Anastasia): wants her own record in one place — totals, the years, the levels, who can see what —
  and a list she can paste into a CV or a grant application.

All three want the page arranged their way, and the light or dark scheme they picked to follow them to another
browser (9.3 kept it in the browser, decision D3).

## 2. Scope

In (MVP):
- The scheme (system, light, dark) and the widget order and visibility of each dashboard stored on the account.
- A widget kit on the 9.3 tokens and effect components: K1 (number, sparkline, delta), K3 (ticker with border beam),
  T1 (years with annotations), T2 (cumulative race of this year against last year by month), H1 (records), H4
  (typed "did you know" fact), P2 (top people as bars with initials), P6 (focus cards), V3 (level stairs), V6 (level
  treemap). Facts and records are generated in the browser from the analytics responses; no stored text.
- Monthly counts (`byMonth`) added to the unit and university analytics responses for T2 and the year-to-date delta.
- D1 «Зала визнання» (Hall of Fame) for the rector (university) and the dean (faculty).
- D5 «Командний центр» (Command centre) for the faculty secretary and the rector's secretary: today's decisions, the
  first requests of the queue with approve and return, the units without awards, top people, and the "remind head of
  department" mail for a department without awards this year.
- «Мої нагороди» (the 5.1 personal dashboard rebuilt): totals, years, levels as stairs, who sees what, submissions in
  progress, and the CV export (PDF and plain text).
- The home page `/` opens the dashboard of the caller's highest role.

Out:
- Free placement and resizing of widgets on a grid: order and visibility only (the cut planned on 2026-11-14 if time
  is short; it is). Deferred to the icebox.
- The year recap (D6), share card images, server-side generated texts: icebox.
- A head-of-department field on `organizations`: the reminder goes to a colleague the sender picks (§7 deviation 2).
- New analytics numbers beyond `byMonth`: the widgets read what 5.1 to 5.3 already return.
- The report and the unit export of 5.2 and 5.3 stay on `/analytics`; the dashboards link to them.

## 3. Stories

| # | Story | Pts | Parallel | Depends on |
|---|-------|-----|----------|------------|
| 9.4.1 | Scheme and dashboard layout on the account | 5 | no | - |
| 9.4.2 | Widget kit, generated facts and monthly counts | 5 | no | - |
| 9.4.3 | Hall of Fame for the rector and the dean, role home | 3 | no | 9.4.1, 9.4.2 |
| 9.4.4 | «Мої нагороди» with the CV export | 5 | no | 9.4.1, 9.4.2 |
| 9.4.5 | Command centre with the department reminder | 5 | no | 9.4.3 |

Order 9.4.1 → 9.4.2 → 9.4.3 → 9.4.4 → 9.4.5. If the freeze of 2026-11-22 comes first, 9.4.5 follows 2.5.5 and the
demo server waits for it (§11 risk 1).

## 4. Acceptance criteria

### 9.4.1 Scheme and dashboard layout on the account (SCRUM-155)

- **AC-1.1** (Scheme stored) Given a signed-in account, when it picks «Темна» in the toolbar scheme menu, then
  `PUT /users/me/dashboard-preferences` stores `colorScheme` `DARK`, and a sign-in from another browser opens in the
  dark scheme without a light flash after the profile loads.
- **AC-1.2** (Browser value until loaded) Given a stored scheme, when the page loads, then the browser's last value
  applies until the preferences arrive and the stored value replaces it; signed out (sign-in page, public pages) only
  the browser value applies.
- **AC-1.3** (Layout stored) Given a dashboard in edit mode («Налаштувати»), when the account moves a widget by
  dragging or with the move-up and move-down buttons, hides one and presses «Готово», then the order and the hidden
  list of that dashboard are stored in `layouts.<DASHBOARD>` and the next visit shows them; «Скинути» restores the
  default order with nothing hidden.
- **AC-1.4** (Validation) Given a `PUT` with a widget that does not belong to that dashboard, a duplicate in `order`
  or `hidden`, an unknown dashboard key or an unknown scheme, then 400 `invalid-parameter` naming the field
  (`layouts.HALL_OF_FAME.order`, `colorScheme`) and nothing is stored.
- **AC-1.5** (New widgets) Given a stored `order` that misses a widget of the dashboard (added in a later release),
  when the dashboard renders, then the missing widget is shown after the ordered ones in its default position order.
- **AC-1.6** (Migration) Given rows of V046 with hidden widgets, when V052 runs, then each row's list is in
  `layouts.MY_AWARDS.hidden` with the 9.4 names (`SUMMARY`→`KPI`, `YEAR_TREND`→`TREND`, `BY_LEVEL`→`LEVEL_STAIRS`,
  `BY_CATEGORY` and `RANK` unchanged, `TIMELINE` dropped; a row left with nothing hidden keeps `{}`) and an empty
  `order`, `color_scheme` is `SYSTEM`, and `hidden_widgets` is gone.
- **AC-1.7** (Export and erasure) Given an account with preferences, when it exports its data, then
  `dashboard_preferences` carries `color_scheme`, `layouts` and `updated_at` (format 1.8); the erasure still deletes
  the row (`dashboardPreferencesDeleted`).
- **AC-1.8** (Keyboard) Given edit mode, then every move and hide is reachable by keyboard, the moved widget keeps the
  focus and a polite live region announces its new position.

### 9.4.2 Widget kit, generated facts and monthly counts (SCRUM-156)

- **AC-2.1** (Monthly counts) Given `GET /analytics/units/{id}` or `GET /analytics/university` for a period whose
  last year is Y, then `byMonth` lists Y and Y-1 with 12 counts each, counted with the same rules and date as
  `byYear`; months after the current one in the current year are 0 and `partialYear` is true.
- **AC-2.2** (K1) Given a series, when K1 renders, then it shows the number counting up, a sparkline of the years and
  a delta against the same months of the previous year («↓ 29 %», «111 за той самий період 2025»); a previous value
  of 0 shows no percentage.
- **AC-2.3** (T1 and T2) Given `byYear` and `byMonth`, then T1 marks the peak year and the current partial year, and
  T2 draws both years cumulatively by month with the month this year fell behind or overtook last year annotated
  («обгін»); with no crossing there is no annotation.
- **AC-2.4** (Facts and records) Given an analytics response, then H1 lists up to three records (peak year, the
  unit or person with most awards, the highest level reached this year) and H4 types one fact chosen from the
  generators that have data (share of a level this year, growth against last year, streak of years with awards);
  each fact is a translation key with values, in Ukrainian and English, never a stored sentence.
- **AC-2.5** (P2, P6, V3, V6) Given the data, then P2 shows up to five people as bars with initials (no photos),
  P6 shows focus cards with a title, a line and one action, V3 shows the levels as steps from faculty to world, and
  V6 shows the levels as a treemap with area by count; each has an empty state.
- **AC-2.6** (Motion and access) Given `prefers-reduced-motion`, then counts, typing, tickers and beams show their
  final state at once; every chart widget has a text alternative (a table or a list behind «Дані») and passes axe in
  both schemes.

### 9.4.3 Hall of Fame for the rector and the dean, role home (SCRUM-157)

- **AC-3.1** (Rector) Given a rector (own or delegated role), when it opens `/`, then «Зала визнання» of the
  university shows K1 (approved this year), T1, T2, H1, H4, P2 and V6 from `GET /analytics/university`, with links
  to `/analytics/university` and the board report.
- **AC-3.2** (Dean) Given a dean, when it opens `/`, then the Hall of Fame of its faculty reads
  `GET /analytics/units/{faculty}`; with delegated scope over more than one faculty a unit picker switches between
  them and the choice stays in the address (`?unit=`).
- **AC-3.3** (Role home) Given an account, when it opens `/`, then it sees the dashboard of its highest role: Hall
  of Fame for a rector or dean, Command centre for a faculty or rector's secretary, «Мої нагороди» for anyone else;
  the sidebar lists every dashboard the account's roles allow.
- **AC-3.4** (Access) Given an account without a rector or dean role, when it opens `/hall-of-fame`, then the
  forbidden page shows and the analytics endpoints answer 403 as today.
- **AC-3.5** (Layout) Given the Hall of Fame, then its widgets follow `layouts.HALL_OF_FAME` (AC-1.3); at 360 px they
  stack in one column with no sideways scroll.

### 9.4.4 «Мої нагороди» with the CV export (SCRUM-158)

- **AC-4.1** (Page) Given an employee, when it opens `/dashboard` («Мої нагороди»), then it shows K1 (approved total
  and this year), T1 (its years with «з нагородою щороку з …» when the streak is at least 3), V3 (its levels), the
  in-progress submissions with their status, the 5.1 category chart and rank, and the visibility widget.
- **AC-4.2** (Visibility widget) Given the account's awards, then the widget counts public, colleague-only and
  private awards and how many carry a document, and links to `/awards` filtered by visibility.
- **AC-4.3** (CV export) Given an account with approved awards, when it chooses «Експорт для резюме» → PDF or text,
  then `GET /users/me/awards/cv?format=pdf|text&lang=uk|en` returns its approved awards grouped by year (newest
  first) with title, level, issuer and date, in the chosen language; private awards are included (it is the
  account's own list); no other person's data.
- **AC-4.4** (Budget) Given more than `app.rate-limit.export-requests-per-minute` CV exports in a minute by one
  account, then 429 with `Retry-After`, sharing the window of the 9.1 exports, and the metric counts `export`.
- **AC-4.5** (Empty) Given an account without approved awards, then the page shows the empty state with «Додати
  нагороду» and the export menu is disabled; the endpoint answers 200 with an empty list text or a one-page PDF.
- **AC-4.6** (Audit) Given a CV export, then one audit row records the account, the format and the count, as the 9.1
  exports do; no award content in the row.

### 9.4.5 Command centre with the department reminder (SCRUM-159)

- **AC-5.1** (Today) Given a faculty secretary, when it opens `/`, then K3 shows the number of requests waiting for
  its decision and the decisions it made today, and the first three requests of its queue are listed with «Затвердити»
  and «Повернути», which act as in the review queue (10 s undo included) and refresh the count.
- **AC-5.2** (Units) Given the secretary's faculty, then P6 lists its departments with `NO_AWARDS` this year first
  («Кафедра …: жодної нагороди у 2026», «За попередні роки — 14») and then `DECLINE`, each with its count of earlier
  years; P2 lists the faculty's top people.
- **AC-5.3** (Reminder) Given a department with no award this year, when the secretary or the dean presses
  «Нагадати завідувачу», picks an active colleague of that department and confirms, then
  `POST /analytics/units/{id}/reminders` answers 202, the colleague gets an in-app notification and a mail
  `AWARDS_REMINDER` naming the department, the year and the sender, and the card shows «Нагадано …».
- **AC-5.4** (Once a week) Given a reminder for a department in the last 7 days, when anyone sends another, then 409
  `recently-reminded` with the date of the last one, and the card's button is disabled with that date.
- **AC-5.5** (Scope) Given a department outside the caller's faculty or delegated scope, a recipient who is not an
  active member of that department, or a department with awards this year, then 403, 422 `invalid-recipient` or 409
  `has-awards` respectively, and nothing is sent.
- **AC-5.6** (Rector's secretary) Given a rector's secretary, then the Command centre lists the faculties instead of
  departments, its queue at the rector level, and no reminder action.
- **AC-5.7** (Layout) Given the Command centre, then its widgets follow `layouts.COMMAND_CENTRE`; at 360 px the queue
  items keep both buttons at least 44 px high.

## 5. Edge cases

- **Validation**: preferences `PUT` replaces the whole document; missing `layouts` keys mean the default; an empty
  body keeps `SYSTEM` and empty layouts. CV `format` or `lang` outside the lists: 400 `invalid-parameter`.
- **Authorization**: the dashboard a role does not open still validates in `PUT` (a dean who loses the role keeps the
  stored layout, harmless). Reminder: own or delegated `FACULTY_SECRETARY` or `DEAN` with the department in scope;
  the rector's secretary and rector have no reminder (AC-5.6).
- **Delegation ends mid-flow**: the Hall of Fame of a delegated dean reloads into «Мої нагороди» on the next
  navigation; a reminder dialog open when the delegation ends answers 403 on confirm.
- **Concurrency**: two tabs saving layouts: last write wins (one row, whole document); two secretaries reminding the
  same department at once: one 202, one 409 (the check and the insert run under the department's advisory lock).
- **External services**: the mail goes through the notification outbox (ADR-006 addendum); an outage retries as for
  the other notification mails; the 202 does not wait for the mail.
- **State**: a recipient deleted after the reminder gets nothing (the notification rule "no row for a deleted
  recipient"); a department that gets its first award after the reminder hides the card on the next load.
- **Time**: "this year" is the calendar year in `Europe/Kyiv`; "today's decisions" from 00:00 Kyiv time.

## 6. Dependencies

### Tables
- `dashboard_preferences` (V046): V052 adds `color_scheme` and `layouts`, moves and drops `hidden_widgets` (9.4.1).
- `notifications` (V038, V039): V053 adds `AWARDS_REMINDER` to the type check (9.4.5).
- Read only: `awards`, `award_requests`, `review_decisions`, `organizations`, `users`, `user_roles`,
  `role_delegations`, `audit_logs` (CV export row).

### Endpoints
- Changed: `GET|PUT /users/me/dashboard-preferences` (9.4.1), `GET /users/me/export` format 1.8 (9.4.1),
  `GET /analytics/units/{id}` and `GET /analytics/university` (`byMonth`, 9.4.2).
- New: `GET /users/me/awards/cv` (9.4.4), `POST /analytics/units/{id}/reminders` (9.4.5).
- Read as they are: `GET /analytics/me`, `GET /analytics/units`, `GET /reviews` (the review queue), `POST /awards/{id}/decisions`, `GET /users?organization=&status=ACTIVE` (recipient picker),
  `GET /awards?visibility=`.

### Services
- `DashboardPreferenceService` (layout validation per dashboard), `UnitAnalyticsService` and
  `UniversityAnalyticsService` (`byMonth`), a `CvExportService` beside the report renderer of 5.3, a
  `UnitReminderService` publishing `UnitReminderRequested` to the notification module, `RequestThrottle` (exports).

### Frontend
- `shared/widgets/` (K1, K3, T1, T2, H1, H4, P2, P6, V3, V6, `facts.ts` generators), `shared/dashboard/`
  (`DashboardLayoutComponent`: edit mode, drag with the CDK, move buttons, hide, reset), `core/preferences/`
  (NgRx slice for scheme and layouts; the 9.3 `ThemeService` reads it).
- Routes: `/` (role home redirect), `/hall-of-fame`, `/command-centre`, `/dashboard` («Мої нагороди»); `HomeComponent`
  and `my-submissions` folded into «Мої нагороди».

### External systems
- Mail (Mailpit locally, Brevo on the demo) for `AWARDS_REMINDER`.

## 7. Technical decisions

Inherited:
1. ADR-014 and ADR-016 addenda (9.3): tokens only, Lucide icons, the six effect components, `motion` only for
   count-ups, staggers and springs, ECharts theme from `--app-chart-1..8`.
2. ADR-015: one NgRx slice per feature; the preferences slice lives in `core/` because the shell reads the scheme.
3. ADR-006 addendum: the reminder is an event after commit; the notification module writes the in-app row and the
   mail with its delivery log and dedupe key.
4. 5.2 privacy rules: top people and units come from the analytics responses that already apply them; the widgets add
   no query of their own.
5. 9.1 AC-2.5: the CV export joins the shared per-user export window and its audit row.

### Proposed deviations and assumptions

1. **Order and visibility, no free grid.** Widgets are placed in a responsive column grid in the stored order; no
   drag to arbitrary cells and no resize. Saves a grid library and a layout model; the cut named on 2026-11-14.
2. **No head-of-department field.** `organizations` has no head; the sender picks the recipient among the
   department's active members (`GET /users?organization=`). Adding a head would need a schema change, an admin
   screen and data nobody keeps; the picker covers the case.
3. **`hidden_widgets` dropped, `hiddenWidgets` leaves the API.** V052 moves the lists into `layouts.MY_AWARDS.hidden`;
   only this frontend reads the field, so no deprecation period. Export format becomes 1.8.
4. **Facts and records generated in the browser** from the analytics responses: no new endpoint, no stored text,
   both languages from translation keys.
5. **`/` redirects by highest role**; the 5.1 summary page `/dashboard/summary` stays; `HomeComponent` is removed.
6. **CV export as PDF and text, not DOCX**: the PDF reuses the 5.3 report renderer; text is for pasting. The export
   includes private awards because the list is the account's own.
7. **Reminder once a week per department**, checked against the `UNIT_REMINDED` audit row the reminder writes in its
   own transaction under the department's advisory lock, no new table (the in-app row appears only after the commit
   and is skipped when the recipient switched reminders off, so it cannot settle two senders at once; ADR-006
   addendum 2026-11-18).
8. **Rector's secretary gets the Command centre at faculty level without the reminder**; reminding a faculty is out.

## 8. Contract

### 8.1 OpenAPI changes

Written in this PRD's PR, `x-status: planned`:

- `GET|PUT /users/me/dashboard-preferences` (9.4.1): schema `DashboardPreferences` becomes
  `{colorScheme: ColorScheme, layouts: DashboardLayouts}`, required both in responses; `ColorScheme` enum
  `[SYSTEM, LIGHT, DARK]`; `DashboardLayouts` with optional `MY_AWARDS`, `HALL_OF_FAME`, `COMMAND_CENTRE`, each a
  `DashboardLayout {order: DashboardWidget[], hidden: DashboardWidget[]}`; `DashboardWidget` gains `KPI`, `TREND`,
  `RACE`, `RECORDS`, `FACT`, `TOP`, `LEVEL_MAP`, `LEVEL_STAIRS`, `TODAY`, `QUEUE`, `UNITS`, `VISIBILITY`,
  `IN_PROGRESS`; 400 `invalid-parameter` naming the field.
- `GET /users/me/export` (9.4.1): `dashboard_preferences` with `color_scheme`, `layouts`, `updated_at`; format 1.8.
- `UnitAnalytics`, `UniversityAnalytics` (9.4.2): `byMonth: MonthCount[]`, `MonthCount {year, month (1-12),
  count}`, both years in full.
- `GET /users/me/awards/cv` (9.4.4): query `format` (`pdf`|`text`, required), `lang` (`uk`|`en`, default `uk`);
  200 `application/pdf` or `text/plain; charset=UTF-8` with `Content-Disposition: attachment`; 400
  `invalid-parameter`; 401; 429 `rate-limited` with `Retry-After`.
- `POST /analytics/units/{id}/reminders` (9.4.5): body `UnitReminderRequest {recipientId: int64}`; 202
  `UnitReminder {unit: OrganizationRef, recipient: UserRef, sentAt}`; 400 `invalid-parameter`; 403 (scope); 404
  (unknown unit); 409 `recently-reminded` (with `lastSentAt`) or `has-awards`; 422 `invalid-recipient`.
- `GET /analytics/units/{id}` (9.4.5): `units[].lastReminderAt` (date-time, null) for departments, so the card shows
  «Нагадано …» without a second call. A faculty secretary whose role is held on a faculty may read that faculty here
  (AC-5.2 needs its department comparison and top people); it stays out of `GET /analytics/units` and the export.
- `GET /reviews/today` (9.4.5): `ReviewDay {decided, since}`, the caller's decisions since 00:00 Kyiv time, for K3.

### 8.2 Migration outlines

- `V052__dashboard_layouts.sql` (9.4.1): `dashboard_preferences` add `color_scheme VARCHAR(10) NOT NULL DEFAULT
  'SYSTEM'` with `ck_dashboard_preferences_color_scheme` (`SYSTEM`, `LIGHT`, `DARK`), add `layouts JSONB NOT NULL
  DEFAULT '{}'`; `UPDATE` rows with a non-empty `hidden_widgets` to
  `{"MY_AWARDS": {"order": [], "hidden": [...]}}`; drop `hidden_widgets`; comments on both columns.
- `V053__awards_reminder_notification.sql` (9.4.5): replace `ck_notifications_type` and
  `ck_notification_preferences_event` with the current list plus `AWARDS_REMINDER`; no index (the weekly check reads
  `audit_logs` through `idx_audit_logs_entity`).

### 8.3 Properties

- `app.reminders.unit-interval` (default `P7D`), 9.4.5.
- CV export uses `app.rate-limit.export-requests-per-minute` (9.1, default 5).

## 9. Test plan

| AC | Unit | IT | FT | E2E |
|----|------|----|----|-----|
| 1.1, 1.2 | preferences reducer, `ThemeService` | - | preferences PUT and GET | `dashboard-layout.spec` scheme across contexts |
| 1.3, 1.5, 1.8 | `DashboardLayoutComponent`, order merge | - | - | edit, move, hide, reset, keyboard |
| 1.4 | `DashboardPreferenceService` | - | 400 cases | - |
| 1.6 | - | V052 on seeded rows | - | - |
| 1.7 | - | export and erasure | export 1.8 | - |
| 2.1 | month bucketing | analytics repository | `byMonth` on both endpoints | - |
| 2.2-2.5 | each widget, `facts.ts` generators | - | - | Hall of Fame renders |
| 2.6 | reduced motion | - | - | axe both schemes |
| 3.1-3.5 | home redirect, guards | - | - | `hall-of-fame.spec` rector, dean, delegated, 360 px |
| 4.1, 4.2, 4.5 | widgets, empty state | - | - | `my-awards.spec` |
| 4.3, 4.4, 4.6 | `CvExportService` | renderer | PDF, text, 429, audit row | download text |
| 5.1, 5.7 | queue widget | - | - | `command-centre.spec` approve with undo |
| 5.2, 5.6 | units ordering | - | - | rector's secretary view |
| 5.3-5.5 | `UnitReminderService` | lock, notification row | 202, 409, 403, 422 | remind and Mailpit |

## 10. Manual verification

Preconditions: `docker compose up -d --build` from the branch with the dev seed (`.\tools\reset-db.ps1` first),
accounts `rector`, `dean.fmi`, `secretary.fmi`, `lecturer.fmi` (approved awards), `employee.fmi` (none) with the
seed password; Mailpit at `http://localhost:8025`; Chrome at `http://localhost` and a second browser profile; a
department of the Faculty of Mathematics and Informatics with no award in the current year (the seed's `NO_AWARDS`
highlight on `/analytics/units/{fmi}`; if none, return its only current-year award first).

1. Sign in as `rector`, open `/`. Expected: redirect to `/hall-of-fame`, university numbers, K1 with delta against the
   same months of last year, T2 with both years. (AC-3.1, AC-3.3, AC-2.2, AC-2.3)
2. Compare K1 and T1 with `/analytics/university` for the same year; `GET /api/v1/analytics/university`. Expected:
   same totals; `byMonth` has 24 rows, Y-1 and Y, whose sums equal `byYear` of those years, 0 after the current
   month. (AC-2.1)
3. Reload twice. Expected: the same «did you know» fact (it is picked by the day of the month); every fact and
   record true against the analytics page. (AC-2.4)
4. «Налаштувати», move T2 first with the buttons, hide V6, «Готово», reload. Expected: T2 first, V6 gone. (AC-1.3)
5. Toolbar scheme «Темна». Open the second profile, sign in as `rector`. Expected: dark scheme there. (AC-1.1)
6. psql: `select color_scheme, layouts from dashboard_preferences` for the rector. Expected: `DARK`, the
   `HALL_OF_FAME` order and hidden list. (AC-1.1, AC-1.3)
7. «Налаштувати» → «Скинути» → «Готово». Expected: default order, nothing hidden; psql `layouts` has no key for
   that dashboard. (AC-1.3)
8. Sign in as `dean.fmi`, open `/`. Expected: Hall of Fame of the faculty, no unit picker. (AC-3.2)
9. Sign in as `secretary.fmi`, open `/`. Expected: Command centre, K3 with the queue count, three requests listed.
   (AC-5.1)
10. «Затвердити» on the first request, keep the page open for 15 s (the 10 s undo window runs first). Expected: count down by one, the request approved on its page.
   (AC-5.1)
11. The units list. Expected: the department without awards first with «жодної нагороди у …» and its earlier count.
   The unit analytics are cached for 5 minutes: wait that long after changing award data. (AC-5.2)
12. «Нагадати завідувачу», pick a colleague, confirm. Expected: 202, card «Нагадано …», Mailpit mail naming the
   department and the sender, the colleague's notification centre shows it. (AC-5.3)
13. Repeat on the same card through the API. Expected: 409 `recently-reminded`, button disabled. (AC-5.4)
14. Sign in as `lecturer.fmi`, open `/`. Expected: redirect to `/dashboard` («Мій дашборд») with this year's count
   and the totals, years (with «З нагородою щороку з …» after three years in a row), levels as stairs, the
   submissions in progress and «Хто бачить мої нагороди»; a visibility count opens
   `/awards?status=APPROVED&visibility=…` with both filters set. (AC-4.1, AC-4.2, AC-3.3)
15. «Експорт для резюме» → «Текст», then switch to English and → PDF. Expected: `awards-cv-<date>.txt|pdf`, awards
   by year, newest first; psql `select new_values from audit_logs where action = 'ANALYTICS_EXPORTED' order by id
   desc limit 2` shows `cv-text` and `cv-pdf` with the count. (AC-4.3, AC-4.6)
16. Sign in as `employee.fmi`. Expected: empty state, export disabled. (AC-4.5)
17. Every dashboard at 360 px, light and dark, both languages. Expected: one column, no sideways scroll, no console
   error, axe clean. (AC-2.6, AC-3.5, AC-5.7)

### Detours

18. `employee.fmi` opens `/hall-of-fame` and `/command-centre` directly. Expected: the forbidden page; the API 403.
   (AC-3.4)
19. `lecturer.fmi` opens `/` with a stored dark scheme after the browser was switched to light. Expected: light until
   the profile loads, then dark, no flash after load. (AC-1.2)
20. `PUT /users/me/dashboard-preferences` with `TODAY` in `HALL_OF_FAME`, then a duplicate, then `colorScheme` `BLUE`.
   Expected: 400 naming the field each time, the row unchanged. (AC-1.4)
21. Hidden in state: the reminder on a department with an award this year. Expected: no button on its card; the API
   409 `has-awards`. (AC-5.5)
22. Hidden in state: the reminder within 7 days. Expected: button disabled; the API 409. (AC-5.4)
23. Hidden in state: «Експорт для резюме» with no approved awards. Expected: menu disabled; the API 200 empty.
   (AC-4.5)
24. Reminder to a member of another department, and to a department of another faculty. Expected: 422
   `invalid-recipient`, 403. (AC-5.5)
25. Revoked mid-flow (delegated dean): the rector delegates the dean role of another faculty to `secretary.fmi`
   (the Command centre shows the caller's own faculty, so it has no dialog for the delegated one); `POST
   /analytics/units/{department}/reminders` for a department of that faculty. Expected: the scope passes (422 for a
   recipient of another department). The rector ends the delegation; the same call. Expected: 403, no mail; the role
   change ends the sessions, so the next navigation goes to `/login`. (AC-5.5, AC-3.2)
26. Revoked mid-flow (secretary role): with the Command centre open in one profile, the admin removes
   `secretary.fmi`'s role in the other (a rector's revoke is refused); press «Затвердити». Expected: 401, because a
   role change ends the sessions, and the app returns to `/login`; no decision is recorded and the request stays in
   the queue for the other secretaries of the faculty. (AC-5.1)
27. Two profiles as `secretary.fmi` and `dean.fmi` remind the same department within a second. Expected: one 202, one
   409. (Edge cases)
28. Eleven CV exports within a minute. Expected: 429 with `Retry-After` from the sixth on. (AC-4.4)
29. Token expiry: with «Мої нагороди» in edit mode, end the session (a password reset through Mailpit revokes the sessions at
   once; the refresh token lives 7 days), press «Готово». Expected: `/login`, nothing saved, the layout after signing
   in as it was before the edit (no partial save). (AC-1.3)
30. Back after a reminder: browser back and forward. Expected: the card still «Нагадано …», no second mail.
   (AC-5.4)

### Run 2026-11-19

container stack at 9436bbe (develop, image 2026-11-19 14:35), default settings; seed data; awards 496/506/517/522/538/554 set to DRAFT for the reminder steps and restored; lecturer.fmi password reset twice and restored. Result: 30 pass.

| Step | Result | Evidence |
|---|---|---|
| 1 | PASS | rector / -> /hall-of-fame; K1 227, ↓19 % vs 112 same period 2025 (complete months Jan-Oct: 91 vs 112), T2 both years, university numbers |
| 2 | PASS | API byMonth 24 rows (2025-01..2026-12), sums 112 / 227 = byYear, Dec 2026 = 0; totals match page |
| 3 | PASS | same fact on two reloads (all years 2022-2026); records 2026=227, FMI 260, level INTERNATIONAL match /analytics/university |
| 4 | PASS | T2 moved first with buttons, V6 «Рівні нагород» hidden, after reload order T2,K1,T1,... and no V6 |
| 5 | PASS | toolbar Тема→Темна; second browser context signed in as rector: dark (body rgb(6,9,20)) |
| 6 | PASS | dashboard_preferences: DARK, HALL_OF_FAME order [RACE,KPI,TREND,...] hidden [LEVEL_MAP] |
| 7 | PASS | Скинути+Готово: default order, V6 back, layouts = {} (no key) |
| 8 | PASS | dean.fmi / -> /hall-of-fame, FMI faculty numbers (166, ↑20 %), no unit picker |
| 9 | PASS | secretary.fmi / -> /command-centre, K3 19 awaiting, three requests (9.3 крок 22, 23, 30) listed |
| 10 | PASS | Затвердити + confirm; count 19→18; award 971 APPROVED in DB after the 10 s undo window (first attempt closed the browser before the send: wait >= 12 s) |
| 11 | PASS | dept 65 Кафедра диференціальних рівнянь first: «Жодної нагороди у 2026 · За 2022–2025 — 8» (precondition: its only 2026 award 496 set back to DRAFT first; analytics cache 5 min delayed the list) |
| 12 | PASS | Нагадати завідувачу -> colleague Іваненко Петро: card «Нагадано 19.11.2026»; Mailpit 14:56:52 to p.ivanenko2 naming Кафедра диференціальних рівнянь and Аліна Секретаренко (uk+en); notification 883 AWARDS_REMINDER in his centre (202 not read directly; the card shows it) |
| 13 | PASS | API repeat by secretary.fmi and by dean.fmi: 409 recently-reminded with lastSentAt 12:56:52Z; card button is disabled «Нагадано 19.11.2026» |
| 14 | PASS | lecturer.fmi / -> /dashboard «Мій дашборд»: 9 this year, 18 total, years, «З нагородою щороку з 2019», level stairs, in progress 3, visibility 2/0/16; link opens /awards?status=APPROVED&visibility=PUBLIC with both filters |
| 15 | PASS | awards-cv-2026-11-19.txt (uk) and .pdf (en, 2 pages), by year newest first, 18 awards; audit ANALYTICS_EXPORTED cv-text count 18 uk, cv-pdf count 18 en |
| 16 | PASS | employee.fmi /dashboard: «Затверджених нагород поки немає» empty state with next steps, export button disabled (in progress 121 / drafts 28 shown) |
| 17 | PASS | 360 px, uk+en, light+dark (profile scheme), 5 dashboards (rector/dean Hall of Fame, Command centre, lecturer and employee /dashboard): no horizontal scroll (scrollWidth 360), 0 console errors, axe only the best-practice rule region on the shell title and a header link, identical on every page and outside 9.4 (WCAG A/AA rules clean) |
| 18 | PASS | employee.fmi: /hall-of-fame and /command-centre -> /forbidden; API /analytics/university, /analytics/units, /analytics/units/9 and /reviews 403 |
| 19 | PASS | localStorage light + stored DARK: body light rgb(246,247,245) at 31 ms, dark rgb(6,9,20) from 150 ms when the profile arrives, no change afterwards (6 s sampled); localStorage updated to dark |
| 20 | PASS | PUT with TODAY in HALL_OF_FAME, duplicate KPI, colorScheme BLUE: three 400 invalid-parameter naming layouts.HALL_OF_FAME.order / colorScheme; GET after: DARK, layouts {} unchanged |
| 21 | PASS | API reminder for dept 64 (155 awards in 2026): 409 has-awards; dept 68 card (2 in 2026) has no reminder button |
| 22 | PASS | within 7 days: disabled button «Нагадано 19.11.2026» (disabled=true), API 409 recently-reminded |
| 23 | PASS | employee (no approved awards): export menu disabled in UI; API cv text 200 (88 B heading only), pdf 200 12.6 kB |
| 24 | PASS | secretary.fmi: dept 67 (no awards in 2026, set DRAFT for the run) with recipient 886 of dept 64 -> 422 invalid-recipient; dept 20 of another faculty -> 403 access-denied |
| 25 | PASS | rector delegates DEAN of faculty 5 to secretary.fmi: hall-of-fame shows Географічний факультет; API reminder on dept 44 passes the scope (422 for recipient 886); after the rector ends the delegation the same call 403, no reminder mail (only role mails); the revoke ends the session: next navigation -> /login. UI dialog for the delegated faculty not reachable (command centre scope = own faculty), done through the API |
| 26 | PASS | step text corrected: admin removes FACULTY_SECRETARY (a rector's revoke is refused); the press gets 401 not 403, because a role change ends the sessions (Feature 1.2 AC: next call 401, back to /login); no decision recorded, request still queued (reviews list shows award 978 first, 30 pending); role re-granted |
| 27 | PASS | secretary.fmi and dean.fmi API calls in parallel for dept 68 (set DRAFT for the run): 202 and 409 recently-reminded; Mailpit one new reminder mail (two profiles replaced by two simultaneous API calls) |
| 28 | PASS | 11 CV exports by lecturer.fmi: 200 x5, 429 from the 6th on; header Retry-After: 32, body too-many-requests retryAfter 42 |
| 29 | PASS | lecturer.fmi /dashboard in edit mode (hid «Підсумок»), session revoked (password reset) before «Готово»: save answered 401, page went to /login, no dashboard_preferences row written, layout after sign-in as before (default). Natural 15-min expiry not waited: sessions revoked instead (refresh token lives 7 d) |
| 30 | PASS | command-centre -> /awards -> Back (and forward/back again): card still «Нагадано», button disabled, Mailpit still one mail to p.ivanenko2 |

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| 23 points do not fit before the freeze of 2026-11-22 next to 2.5.5 | High | High | 9.4.5 may follow after 2.5.5; the cut order is T2, then the PDF of the CV export (text stays), then the reminder |
| Lighthouse on `/` drops (charts above the fold) | Medium | Medium | ECharts lazy per widget, K1 and H4 without ECharts, measured at 9.2.4 |
| Generated facts read wrong in Ukrainian plural forms | Medium | Low | ICU plurals in Transloco, unit tests per form |
| Reminder used as spam | Low | Medium | once a week per department, members only, audit via notifications |
| `byMonth` slows the analytics query | Low | Medium | same index as `byYear`, one grouped query |

## 12. Definition of Done

- `.\tools\gate.ps1` green: unit, IT, FT, JaCoCo ≥ 85 %, Checkstyle, PMD and SpotBugs at 0, frontend lint and tests;
  Playwright for every story.
- Screenshots at 360 and 1280 px, light and dark, Ukrainian and English, for each dashboard.
- The manual steps of each story run and recorded in §10.
- Docs updated in the story that changes them: `openapi.yml` (`x-status: implemented`), `DATA_DICTIONARY.md` §1.8 and
  §5.1, `UI_GUIDELINES.md` (widgets section), ADR-016 addendum (preferences slice), the export format note in the
  privacy docs, `CHANGELOG.md [Unreleased]`, the epic tracker and `BACKLOG.md`.

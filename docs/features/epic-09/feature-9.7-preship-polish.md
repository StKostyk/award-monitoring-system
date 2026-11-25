# Feature 9.7: Pre-ship polish

> **Epic**: 9 Production hardening (SCRUM-128) · **Tracker**: `docs/epics/EPIC-09-PRODUCTION-HARDENING-STATUS.md`
> **Sprint**: 11 (2026-11-23 to 2026-11-29) and 12 (2026-11-30 to 2026-12-06) · **Points**: 30
> **Status**: Validated 2026-11-25 (manual run 2026-11-25: 48 of 49, step 41 covered by e2e
> `privacy-notice.spec` ac1_8); fix 9.7.13 (SCRUM-193) open
> **Governing docs**: ADR-014 and ADR-016 (9.3 addenda: tokens, effect components, chart theme), ADR-015 (NgRx per
> feature, selection in the URL), ADR-021 (one backend instance, settings through the environment),
> `docs/frontend/UI_GUIDELINES.md`, Feature 9.6 (widget header 8, the «Графік / Таблиця» switch, common widget
> rules), Feature 6.1 (privacy centre, activity log, export, erasure), Feature 7.x (notifications, delegations);
> `openapi.yml` `GET /analytics/me`, `GET /users`, `GET /notifications`, `POST /notifications/{id}/read`,
> `GET /users/me/privacy`, `GET /users/me/activity`, `GET /delegations`, `GET|PUT /users/me/dashboard-preferences`;
> `DATA_DICTIONARY.md` §1.8 `dashboard_preferences`; the pre-ship canvas of 2026-11-23/24 (pages 1 Sign-in,
> 2 Logo, 3 Widget tables, 4 Timeline, 6 Empty space, 7 First sign-in, 8 Unit analytics, 9 Privacy,
> 10 Notifications)

## 1. Problem and personas

A feedback round on the shipped 9.6 screens left ten comments. The sign-in page is still the plain card of 9.3 and
the neutral brand has a placeholder mark. The table side of the «Графік / Таблиця» switch is a list of rows that
looks like another widget. The My dashboard timeline is a row of dots. Users, unit analytics and delegations leave
half the screen empty. A new account lands on My dashboard with no hint of what to do first. Privacy and my data
is a long form, and a click on a notification leaves the page, which reads as the page closing. Each comment got
one picked option on the canvas; this feature builds the picks.

- **Every visitor**: sees the sign-in page first; a daily quote and a university look make the first impression at
  the defense.
- **Employee** (Anastasia): signs in for the first time and wants to know the next step; reads her awards as a road
  on the desktop and as a dated list on the phone; reads notifications without losing her place.
- **Secretary and dean** (Olena, Petro): read unit analytics tables as a spreadsheet; open a top performer's
  portfolio from the list; see who holds whose authority at a glance.
- **Administrator** (Ihor): scans all users in one table, opens one person beside the list.
- **Every signed-in user**: sees the state of their privacy choices on one panel.

## 2. Scope

**In**

- Sign-in page: three themes rendered by the backend (A catalogue cards, the default; B night sky; C embroidered
  rushnyk), chosen by one deployment setting without a rebuild; «ЦИТАТА ДНЯ» from a list of 18 quotes, one per
  Kyiv date, the same for everyone, in Ukrainian and English; phone layouts at 360.
- Logo mark 9 «Книга-сходи» as the neutral brand's mark (sign-in page, sidebar, favicon and app icons of the
  neutral brand). The ChNU brand keeps its slot for the real logo.
- Widget tables: spreadsheet grid for year by unit matrices (T2), grouped headers for multi-metric summaries (T4),
  a frozen first column with the newest year first on phones for every widget table (T5).
- My dashboard timeline: journey road at 960 px and wider (TL5), dated feed below 960 (TL3).
- Unit analytics: page header, then a sticky rail (B v2); clickable top performers with a hover card; the
  comparison paragraph removed, «до 2017–2021» under each change of the summary, no subtitle or footnote on
  «Нагороди за роками».
- Users: the full table until a person is selected, then the list narrows and the person pane opens beside it (A).
- Delegations: status board with «Від мене» and «Мені» (A).
- Notifications: inbox with a reading pane; selecting reads the notification in place, only the button navigates.
- Privacy and my data: control panel (A).
- First sign-in: getting-started checklist on My dashboard, progress kept on the server, role-specific steps; an
  optional four-step spotlight tour.

**Out**

- Sign-in themes for the Angular account pages (registration, forgotten and new password, pending registration):
  they keep the 9.3 card with the brand logo (assumption A2).
- The real ChNU logo: waits for the university's permission; which brand the demo runs is set with the demo server
  (SCRUM-134).
- An audit tab in the Users person pane (the canvas «Журнал»): no per-person audit endpoint for the
  administrator exists; the compliance officer's audit page covers it (A4).
- Sign-in boards D, E and F, timeline options TL1, TL2, TL4, the other layouts of pages 6, 9 and 10, onboarding
  option C: not picked.
- The 9.6 review items (open deviations of #495-#497): handled in the 9.6 validation, except AC-3.3 (D5).

## 3. Stories

| # | Story | Pts | Parallel | Depends on |
|---|-------|-----|----------|------------|
| 9.7.1 | Sign-in themes, quote of the day and logo mark 9 (SCRUM-181) | 5 | no | - |
| 9.7.2 | Spreadsheet tables in widgets (SCRUM-182) | 3 | no | - |
| 9.7.3 | Timeline as a journey road and a dated feed (SCRUM-183) | 3 | no | - |
| 9.7.4 | Unit analytics rail and clickable top performers (SCRUM-184) | 3 | no | 9.7.2 |
| 9.7.5 | Users list that splits on select (SCRUM-185) | 3 | no | - |
| 9.7.6 | Delegations status board (SCRUM-186) | 2 | no | - |
| 9.7.7 | Notifications inbox with a reading pane (SCRUM-187) | 3 | no | - |
| 9.7.8 | Privacy control panel (SCRUM-188) | 3 | no | - |
| 9.7.9 | First sign-in checklist and spotlight tour (SCRUM-189) | 5 | no | 9.7.8 |
| 9.7.11 | UI review fixes: logo mark 9, checklist card, unit switch in place (SCRUM-191) | 3 | no | 9.7.9 |
| 9.7.12 | One analytics page (SCRUM-192) | 5 | no | 9.7.11 |

Order 9.7.1 → 9.7.9 as listed. The branches stack and the full gate and e2e run once on the 9.7.9 branch (D1). If time
runs short before the demo server, 9.7.9 and then 9.7.8 move after the deploy (§11 risk 1).

## 4. Acceptance criteria

The look of each AC is the named board of the pre-ship canvas, in the light burgundy and cream scheme and in the
dark Aurora scheme; the common widget rules of 9.6 §4 hold (contrast at least 4.5:1, neutral change chips, gold
only on ink or burgundy in the light scheme).

### 9.7.1 Sign-in themes, quote of the day and logo mark 9 (SCRUM-181)

- **AC-1.1** (Catalogue theme) Given the default setting, when `/login` opens at 1280, then the left side shows two
  lanes of ruled catalogue cards moving slowly, one card stamped «ЦИТАТА ДНЯ»; the right side shows the form under
  «Каталог досягнень університету» with nothing above it in the top-left corner, and a footer with the
  organization name and the privacy link (board Signin-A).
- **AC-1.2** (Sky theme) Given the setting `sky`, when `/login` opens, then the page shows the night sky with the
  book and steps constellations, two shooting stars, the old-town skyline with smoke as thin rising strips, «УКР ·
  EN» top right and the form card, no faculty names (board Signin-B).
- **AC-1.3** (Rushnyk theme) Given the setting `rushnyk`, when `/login` opens, then the page shows the stitched top
  band, the three-step stitched path, the motifs star, kalyna, tree of life and rosette spread out, no hooked or
  rotational motif anywhere, and the form card at the right at vertical centre with its strip across the full card
  width (board Signin-C).
- **AC-1.4** (Setting) Given `APP_SIGN_IN_THEME` set to `catalogue`, `sky` or `rushnyk` on the backend container,
  when the container restarts, then `/login` uses that theme without a rebuild; an unset value means `catalogue`;
  any other value stops the start with a message naming the three allowed values.
- **AC-1.5** (Quote of the day) Given two visitors on the same Kyiv date, when each opens `/login`, then both see
  the same quote, chosen by the day index of the Kyiv date over the quote list; the next Kyiv day shows the next
  quote, and after the last one the list starts again. In `uk` the quote, author and source are Ukrainian, in
  `en` English. Every theme shows it (A: stamped card; B: under the headline; C: under the motifs).
- **AC-1.6** (Phone) Given `/login` at 360 in each theme, then the form is fully visible without horizontal scroll,
  the decoration sits above it in a band no taller than a third of the screen, and the quote stays readable
  (boards Signin-A/B/C-Phone).
- **AC-1.7** (Logo mark 9) Given the neutral brand, then the sign-in page, the sidebar, the favicon and the app
  icons show mark 9 «Книга-сходи» (open book with three rising steps, the top step gold) in both schemes; the
  ChNU brand keeps its own logo (board Logo-More, 9).
- **AC-1.8** (Behaviour kept) Given any theme, then the sign-in errors, the signed-out notice, the language switch,
  «Забули пароль?», «Зареєструватися» and the rate-limit message behave as before, and the moving decoration stops
  under `prefers-reduced-motion`.

### 9.7.2 Spreadsheet tables in widgets (SCRUM-182)

- **AC-2.1** (Spreadsheet grid) Given a widget whose table is a year by unit matrix («Нагороди за роками» on unit
  and university analytics), when «Таблиця» is chosen, then the table has every border drawn, a total per row and
  per column, a «Разом» corner, the running year marked «*» with the footnote «рік триває», and the first column
  pinned while scrolling sideways (board Table-2).
- **AC-2.2** (Grouped headers) Given a multi-metric summary table («Підсумок за кафедрами», «Порівняння
  кафедр»), then the columns sit under two-level headers «Нагороди» (Усього, Верифік.) and «Люди» (Нагороджені,
  Працівники, На 100 прац.), the first group lightly tinted, the faculty total as the last row (board Table-4).
- **AC-2.3** (Phone) Given any widget table at 360, then the first column stays pinned and wraps names, a shadow
  marks more columns to the right, year columns run newest first, a caption «N з M років — гортайте →» shows how
  many fit, and the scroll area takes keyboard focus with an accessible name (board Table-5).
- **AC-2.4** (Same numbers) Given a widget, then its table shows the same numbers as its chart for the same period.

### 9.7.3 Timeline as a journey road and a dated feed (SCRUM-183)

- **AC-3.1** (Level in the timeline) Given `GET /analytics/me`, then every `timeline` entry carries `level` (the
  recognition level of the award's category, `RecognitionLevel`).
- **AC-3.2** (Journey road) Given My dashboard at 960 px or wider, then the timeline widget is a road from the first
  award to today with one milestone per award (year, title, level), years marked along the road (board TL-5).
- **AC-3.3** (Dated feed) Given My dashboard below 960 px, then the timeline is a feed grouped by year with the
  count per year, each row with day and month, title, «✓ верифіковано» for verified awards, organization and
  category, and the level chip; chips «Усі» and «Від національного» filter it (board TL-3).
- **AC-3.4** (Link) Given a milestone or a feed row, when it is activated with the mouse or Enter, then the award
  page opens.

### 9.7.4 Unit analytics rail and clickable top performers (SCRUM-184)

- **AC-4.1** (Header, then rail) Given unit analytics at 1280, then the page header with the period filters and the
  export scrolls with the page and the unit rail under it stays sticky beside the widgets (board Analytics-B v2).
- **AC-4.2** (Comparison caption) Given the summary, then the paragraph about the comparison period and the partial
  year is gone; each change chip has the caption «до 2017–2021» (the previous period of the filter) under it;
  «Нагороди за роками» has no subtitle and no footnote, and the hatched running year explains itself in the
  tooltip (board AF-2).
- **AC-4.3** (Clickable performers) Given «Найкращі», then every row is a link to the person's portfolio
  (achievements filtered by the person); hover or focus shows a card with the name, position and unit, the award
  count of the period and the three latest approved awards, and «Портфоліо →»; Tab and Enter reach and open it
  (board AF-1). Only people who agreed to rankings appear, as before.
- **AC-4.4** (Names) Given a long department or person name in the rail, the comparison table or «Найкращі», then
  it wraps to at most two lines and is never cut off (D5).

### 9.7.5 Users list that splits on select (SCRUM-185)

- **AC-5.1** (Last sign-in) Given `GET /users`, then every `UserSummary` carries `lastLoginAt` (null when the account
  never signed in, and for erased accounts).
- **AC-5.2** (Full list) Given `/admin/users` at 1280 with nobody selected, then the table spans the page with the
  columns name with the address, unit, roles, status and last sign-in, under the toolbar of 9.6 (board Users-A).
- **AC-5.3** (Split on select) Given a row is selected, then the list narrows to name and unit and the person pane
  opens beside it, sticky while the list scrolls, with the tabs «Ролі» and «Обліковий запис» and the links
  «Нагороди · N» (the person's achievements) and «Делегування» (their delegations page); «×» closes the pane and
  the full table returns; the selection stays in the URL.
- **AC-5.4** (Phone) Given 360, then the list is full width and a selected person opens as a full page with a back
  link, as in 9.5.

### 9.7.6 Delegations status board (SCRUM-186)

- **AC-6.1** (Board) Given `/delegations` at 1280, then the page shows a toggle «Від мене · N» / «Мені · N» and three
  columns «Активні», «Заплановані», «Завершені» (expired and revoked), each card with the initials, the other
  person, the role and unit, the dates, the note and the allowed action (board Delegations-A).
- **AC-6.2** (Actions by state) Given a card, then «Відкликати» shows only on active and scheduled delegations given
  by the caller; received and ended cards have no action.
- **AC-6.3** (Phone) Given 360, then the columns stack in the order active, scheduled, ended.

### 9.7.7 Notifications inbox with a reading pane (SCRUM-187)

- **AC-7.1** (Mark unread) Given a read notification of the caller, when `DELETE /notifications/{id}/read` is sent,
  then it answers 204 and the notification is unread again; a notification of another person answers 404.
- **AC-7.2** (Filter by type) Given `GET /notifications?type=AWARD_APPROVED&type=AWARD_REJECTED`, then only those
  types come back, newest first, combinable with `unread`; an unknown type answers 400.
- **AC-7.3** (Inbox) Given `/notifications` at 1280, then the list sits on the left with the chips «Усі · N»,
  «Непрочитані · N», «Нагороди», «Розгляд», «Ролі» and «Позначити всі прочитаними», and the reading pane on the
  right (board Notif-A).
- **AC-7.4** (Read in place) Given a notification is selected, then it opens in the reading pane and is marked
  read; the page never navigates on select; only the pane's button («Відкрити нагороду», «До черги розгляду» and
  so on) navigates; «Позначити непрочитаним» marks it unread again.
- **AC-7.5** (Phone) Given 360, then a selected notification opens as a full page with a back link, and back
  returns to the same place in the list.

### 9.7.8 Privacy control panel (SCRUM-188)

- **AC-8.1** (Panel) Given `/privacy` at 1280, then the page shows a status line («Усе гаразд» or what needs
  attention) and tiles for the privacy notice (version and the acknowledgement date, «Прочитати»), rankings
  consent, award visibility («Змінити»), e-mail notifications (types switched off, link to Profile), the data
  download (what it holds, the last download, «Завантажити»), recent activity and account deletion (board
  Priv-A).
- **AC-8.2** (Activity) Given the activity tile, then it shows the latest entries with the toggle «Мої» / «Щодо мене»
  (`byMe`) and «Увесь журнал →» to the full list.
- **AC-8.3** (Actions kept) Given the panel, then consent changes, the download and the deletion request and its
  cancellation work as in 6.1, with their dialogs; a pending deletion turns the status line into the countdown
  with «Скасувати запит».
- **AC-8.4** (Phone) Given 360, then the tiles stack in one column, status line first.

### 9.7.9 First sign-in checklist and spotlight tour (SCRUM-189)

- **AC-9.1** (Steps per role) Given `GET /users/me/onboarding`, then it returns the caller's steps in order with
  `done` flags, `dismissed` and `tourSeen`: every account gets `NOTICE`, `PROFILE`, `FIRST_AWARD`, `VISIBILITY`,
  `NOTIFICATIONS`; holders of `award:approve:level1` add `REVIEW_QUEUE`; deans and the rector add `DELEGATE`;
  faculty secretaries add `IMPORT`; accounts without `award:create` (administrator, data protection officer) get
  only `NOTICE`, `PROFILE`, `NOTIFICATIONS`.
- **AC-9.2** (Done from data) Given the caller acknowledged the privacy notice, owns an award, or gave a delegation,
  then `NOTICE`, `FIRST_AWARD` or `DELEGATE` are done without being stored.
- **AC-9.3** (Stored progress) Given `PUT /users/me/onboarding` with `done`, `dismissed` and `tourSeen`, then the
  choice is stored and returned by the next GET on any device; a step the caller's roles do not have answers 400
  with `invalid-parameter`; the save is not audited and is part of the export and the erasure.
- **AC-9.4** (Checklist) Given a signed-in account with open steps and not dismissed, when My dashboard opens, then
  the card «Вітаємо, <ім'я>! · Перші кроки» lists the steps with their buttons; a button opens the step's page and
  marks a stored step done; «Сховати список» dismisses it for good; when every step is done the card says so once
  and does not come back (board Onb-A).
- **AC-9.5** (Spotlight tour) Given the checklist, when «Показати тур по екрану» is chosen, then four steps
  highlight search (Ctrl K), the navigation, «Нова нагорода» and the «Графік / Таблиця» switch with «Крок N з 4»,
  «Назад», «Далі» and «Пропустити»; Escape ends it; after the tour `tourSeen` is stored (board Onb-B).
- **AC-9.6** (Access) Given no token, then both endpoints answer 401; each account reads and writes only its own
  progress.

### 9.7.10 Legal pages and mail headers (SCRUM-190)

- **AC-10.1** (Notice 1.1) Given the privacy notice, then version 1.1 (uk, en) adds «Файли cookie та сховище
  браузера»: the sign-in session cookie, sign-in keys in session storage, language and colour scheme, unsaved form
  drafts, the application cache; no analytics or third-party cookies. 1.1 is current, so every account acknowledges
  it at the next sign-in; 1.0 stays readable.
- **AC-10.2** (Terms of use) Given `/terms`, then the terms (uk, en, version 1.0) open without an account and are
  linked from sign-up, the sign-in page and the privacy notice page.
- **AC-10.3** (List-Unsubscribe) Given a notification mail that carries the preferences link, then it also carries a
  `List-Unsubscribe` header pointing to the notification preferences; security mails (verification, password reset,
  lockout) carry none.

### 9.7.11 UI review fixes (SCRUM-191)

Fixes from the UI review of the stack; supersedes the ChNU half of AC-1.7 and the card look of AC-9.4.

- **AC-11.1** (Mark 9 for ChNU) Given the ChNU brand, then the sidebar, the sign-in and error pages, the favicon and
  the installed-app icons show mark 9 «Книга-сходи»; the crimson theme and the ChNU names stay. The university logo
  replaces the same files once its use is approved.
- **AC-11.2** (Checklist card, board Onb-A) Given the checklist, then it is a brand-coloured card: on the left a
  progress ring «N/M» with «Перші кроки», the greeting and «Виконано N з M»; on the right each step on its own light
  card with a numbered circle, a done step ticked, struck through and marked «готово»; the first open step ringed in
  gold with a filled button, later steps with a text link; «Показати тур по екрану» and «Сховати список» in light
  text below. Dark mode uses the same tokens.
- **AC-11.3** (Unit switch in place) Given the unit analytics page, when another unit is chosen in the rail, then the
  panes stay on screen marked as updating (`aria-busy`) while the new figures load, the scroll position and the
  focus stay, and the new figures replace the old in place; a failed load shows the error as before.

### 9.7.12 One analytics page (SCRUM-192)

University and unit analytics become one section; the routes `/analytics`, `/analytics/university` and
`/analytics/units/:id` stay, so saved links keep working.

- **AC-12.1** (Rail) Given an analytics page, then the side rail lists «Увесь університет» to a holder of
  `analytics:read:university`, then the faculties open to the caller (a faculty whose parent is not in the list) in
  the order of the API, and the departments of the shown faculty only, indented under it; the shown row has
  `aria-current`.
- **AC-12.2** (Scope) Given a dean or a faculty secretary, then the rail lists only what `GET /analytics/units`
  returns, without «Увесь університет»; departments whose faculty is not open to the caller are listed as top rows.
- **AC-12.3** (Counts) Given the unit page, then the open faculty and its departments show the approved awards of
  the period; other rows show none.
- **AC-12.4** (Switch) Given a rail row, then it opens that page for the same period; unit to unit switches in place
  (AC-11.3), the university page and a unit page are separate pages with the same rail (D7).
- **AC-12.5** (Landing) Given `/analytics`, then a holder of the university view lands on `/analytics/university`,
  anyone else on the first faculty open to them (the first unit when none is), keeping the period.
- **AC-12.6** (Navigation) Given the side menu or the command palette, then one entry «Аналітика» leads to the
  section and stays current on every analytics page.
- **AC-12.7** (Layout) Given `/analytics/university`, then it has the unit page layout: the page header, the
  summary tiles and the rail in the sticky side column, the faculties, facts, trend, category, level, source and
  comparison in the main column; it fits 360 px without sideways scroll.

### 9.7.13 Users search matches names typed in decomposed Unicode (SCRUM-193)

Found in the validation (2026-11-25): a «й» or «ї» typed as a base letter plus a combining mark matched nobody.

- **AC-13.1** (Users) Given `GET /users?q=` with a fragment in decomposed form (NFD), then it finds the same users
  as the composed (NFC) form.
- **AC-13.2** (Other name searches and inputs) Given `GET /people?q=`, then it normalises the fragment the same way;
  award and achievement searches already normalise through `SearchTerms` and stay unchanged. Given a decomposed
  name at registration, in a profile change or in an added person, then it passes the name rules (which accept no
  combining mark) and is stored composed.

## 5. Edge cases

- Sign-in: the quote list is read once at start; a day change at Kyiv midnight while the page is open changes
  nothing until reload. The rate-limit and error pages keep the chosen theme. Reduced motion freezes the cards,
  stars and smoke.
- Quote with a doubtful source (Pasteur): ships only if the wording and source are confirmed; otherwise the list has
  17 quotes (A3).
- Tables: a unit with zero awards in a year shows «0», not an empty cell; a period of one year still has the totals.
- Timeline: no awards shows the empty state of 9.6; a single award draws the road with one milestone; the
  `truncated` flag of `GET /analytics/me` shows «Усі нагороди →».
- Top performers: a person who withdraws the rankings consent disappears on the next load; the hover card of a
  person whose awards are all private shows the count only.
- Users: the selected person is erased or loses the caller's scope while open; the pane answers with the 404 or 403
  message and the full list returns.
- Delegations: a scheduled delegation becomes active, or an active one expires, while the board is open; the card
  moves on the next load. Revoking an already revoked delegation answers 409 as before.
- Notifications: marking unread an unread notification is a no-op (204); the unread counter in the sidebar follows
  both directions; a notification whose award is gone keeps the pane text and hides the button.
- Privacy: the export rate limit (429) and an active legal hold keep their 6.1 messages on the tiles.
- Onboarding: a role assigned or revoked later adds or removes its steps on the next GET; stored keys of a removed
  role stay in the row and are ignored; two devices saving at once, last write wins; an account created before this
  feature sees the checklist until it dismisses it.

## 6. Dependencies

### Tables
- `dashboard_preferences`: new column `onboarding` (V055, 9.7.9). Read: `users`, `consent_records`, `awards`,
  `role_delegations`, `notifications`, `audit_logs` (existing).

### Endpoints
- New: `DELETE /notifications/{id}/read` (9.7.7), `GET|PUT /users/me/onboarding` (9.7.9).
- Changed: `GET /analytics/me` (`TimelineEntry.level`, 9.7.3), `GET /users` (`UserSummary.lastLoginAt`, 9.7.5),
  `GET /notifications` (`type` filter, 9.7.7), `GET /users/me/activity` (`byMe` filter, 9.7.8),
  `GET /users/me/export` (format 1.9 with `onboarding`, 9.7.9).
- Read as is: `GET /analytics/units/{id}`, `GET /analytics/university`, `GET /achievements?person=`,
  `GET /delegations`, `GET /users/me/privacy`, `GET /users/me/consents`.

### Services
- Backend: `LoginController` and the `login.html` template with a theme fragment per theme, a quote-of-the-day
  component with a `Clock` in Europe/Kyiv; `AnalyticsService` timeline query; `UserService` summary mapping;
  `NotificationService`; a new onboarding service in the `user` domain; export and erasure of the preferences row.

### Frontend
- `core/brand` (neutral mark 9), `shared` widget table (grid, grouped header, frozen column), `analytics`
  (timeline, unit analytics rail, top performers), `admin/user-list`, `delegations/delegation-list`,
  `notifications/notification-list`, `privacy/privacy-centre`, a new onboarding checklist and tour under
  `features/home` or `analytics/dashboard`.

### External systems
- None.

## 7. Technical decisions

Inherited: ADR-014 and ADR-016 addenda (tokens only, Lucide icons, chart theme), ADR-015 (NgRx per feature,
selection in the URL), ADR-021 (settings through the environment of the container), the 9.5 page kit and the 9.6
pane header.

### Proposed deviations and assumptions

1. **D1 One test run at the end**, as in 9.6 D1 and also for the backend parts (decided 2026-11-24, time before the
   defense): each story on its own branch stacked on the previous one with the static checks clean and its own new
   tests run singly; the full gate and the full e2e run once on the 9.7.9 branch, spec fixes are committed to the
   story they belong to; the PRs open in order.
2. **D2 The sign-in theme is a backend setting.** The pre-ship notes asked for a runtime frontend setting, but
   `/login` is rendered by the backend (Spring Authorization Server), so the switch is `app.sign-in.theme`
   (`APP_SIGN_IN_THEME`) next to `app.brand` (`APP_BRAND`): no rebuild, a container restart.
3. **D3 Quotes live in the backend.** A resource file with the 18 quotes (17 until A3 is settled) (author, year, source and text in `uk`
   and `en`); the day index is the number of days since 1970-01-01 of the Kyiv date, modulo the list length.
4. **D4 Onboarding progress is a column of `dashboard_preferences`**, not a table: the row is already per account,
   in the export and deleted by the erasure. It is written only by `PUT /users/me/onboarding`; the preferences save
   keeps its column list and never touches it. Steps that the data already proves are computed, not stored.
5. **D5 AC-3.3 of 9.6 reworded**: «the full department name on one line» cannot hold in the 503 px pane; names wrap
   to at most two lines and are never cut (AC-4.4).
6. **D6 Notification groups are client-side.** «Нагороди» = `AWARD_APPROVED`, `AWARD_REJECTED`, `AWARD_RETURNED`,
   `AWARD_CORRECTED`, `AWARDS_REMINDER`; «Розгляд» = `AWARD_ESCALATED`, `REVIEW_OVERDUE`, `REVIEW_TAKEN_OVER`;
   «Ролі» = `ROLE_ASSIGNED`, `ROLE_REVOKED`, `DELEGATION_CREATED`, `DELEGATION_REVOKED`; the API filters by type.
7. **D7 University and unit stay two routed pages** (AC-12.4): the university report and a unit report come from
   different endpoints and stores, so «Увесь університет» ↔ a unit is a page change with the same rail, not an
   in-place switch; only unit ↔ unit switches in place.

Assumptions: **A1** The catalogue theme is the default. **A2** The Angular account pages keep the 9.3 card.
**A3** The Pasteur quote ships only once its wording and source are confirmed. **A4** No «Журнал» tab in the
person pane. **A5** The checklist shows on My dashboard for every account with `analytics:read:own`; the
administrator and the data protection officer, who have no My dashboard, see it on their landing page. **A6** The
tour has four steps on My dashboard only.

Quote list (author, year or date, source): Newton; Shevchenko; Feynman; Bacon; Skovoroda; Seneca; Socrates (the
seven kept from round 3); Marie Curie, letter of 1894; Lesya Ukrainka «Contra spem spero!» 1890 and «Слово, моя
ти єдиная зброє» 1896; Ivan Franko «Vivere memento!» 1883; Louis Pasteur, Lille 1854 (A3); Michael Faraday,
laboratory diary 19.03.1849; Albert Einstein, LIFE 2.5.1955; Immanuel Kant, 1784, «Sapere aude»; Confucius,
Analects 2.15; Hippocrates, Aphorisms 1.1; Shevchenko «Кавказ» 1845.

## 8. Contract

### 8.1 OpenAPI changes
Written as stubs with `x-status: planned` in this PRD's PR:
- `TimelineEntry.level`: `$ref: RecognitionLevel`, required (9.7.3).
- `UserSummary.lastLoginAt`: `string`, `date-time`, nullable (9.7.5).
- `GET /notifications`: query `type`, array of `NotificationType`, `explode: true`, optional; 400 on an unknown
  value (9.7.7).
- `DELETE /notifications/{id}/read`, `operationId: markNotificationUnread`: 204; 401; 404 `notification-not-found` for a
  missing or foreign notification (9.7.7).
- `GET /users/me/onboarding`, `operationId: getOnboarding`: 200 `OnboardingState`; 401 (9.7.9).
- `PUT /users/me/onboarding`, `operationId: saveOnboarding`: body `OnboardingUpdate`; 200 `OnboardingState`; 400
  `invalid-parameter` (unknown step or a step outside the caller's roles); 401 (9.7.9).
- Schemas: `OnboardingStepKey` enum `NOTICE`, `PROFILE`, `FIRST_AWARD`, `VISIBILITY`, `NOTIFICATIONS`,
  `REVIEW_QUEUE`, `DELEGATE`, `IMPORT`; `OnboardingStep {key: OnboardingStepKey, done: boolean, derived: boolean}`;
  `OnboardingState {steps: OnboardingStep[], dismissed: boolean, tourSeen: boolean}`;
  `OnboardingUpdate {done: OnboardingStepKey[] (unique), dismissed: boolean, tourSeen: boolean}`.
- `GET /users/me/export`: format 1.9, `dashboard_preferences.onboarding` (9.7.9).

### 8.2 Migration outlines
- `V055__onboarding_progress.sql` (9.7.9): `ALTER TABLE dashboard_preferences ADD COLUMN onboarding JSONB NOT NULL
  DEFAULT '{}'` holding `{"done": [...], "dismissed": bool, "tourSeen": bool}`; no index, no trigger (not audited,
  as the rest of the row).

### 8.3 Properties
- `app.sign-in.theme` (`APP_SIGN_IN_THEME`): `catalogue` (default), `sky`, `rushnyk`; validated at start (9.7.1).

## 9. Test plan

| AC | Unit (vitest / JUnit) | IT / FT | E2E (Playwright) |
|----|-----------------------|---------|------------------|
| AC-1.1 to AC-1.3, AC-1.6 | - | `LoginPageFT`: theme markers per setting | `login.spec.ts`: each theme at 360 and 1280, no horizontal scroll |
| AC-1.4 | properties validation test | `SignInThemeIT` (unknown value fails the context) | - |
| AC-1.5 | `QuoteOfTheDayTest`: fixed clock, Kyiv midnight, wrap-around, `uk`/`en` | `LoginPageFT`: same quote on two requests | - |
| AC-1.7 | `BrandService` spec: neutral mark | - | `layout.spec.ts`: sidebar logo under the neutral brand |
| AC-1.8 | - | existing `LoginPageFT`, rate-limit FT | `login.spec.ts` existing cases, reduced motion |
| AC-2.1 to AC-2.4 | widget table: borders, totals, grouped header, pinned column, year order on phones | - | `analytics.spec.ts`: table matches the API numbers, 360 scroll caption |
| AC-3.1 | `PersonalAnalyticsServiceTest` | `AwardStatisticsIT`, `PersonalAnalyticsFT` (`level` per entry) | - |
| AC-3.2 to AC-3.4 | road and feed by width, level filter | - | `dashboard.spec.ts`: road at 1280, feed at 360, link opens the award |
| AC-4.1 to AC-4.4 | `split` sticky side, `delta-tiles` caption, `year-grid` without footnote, `top-performers` card (latest awards, count only, Escape) | - | `unit-analytics.spec.ts`: rail stays in view, performer link opens the portfolio by keyboard |
| AC-5.1 | `UserServiceTest` | `UserAdminIT` (`lastLoginAt`) | - |
| AC-5.2 to AC-5.4 | list modes, URL selection | - | `people.spec.ts`: full table, split, close |
| AC-6.1 to AC-6.3 | columns by state, actions | - | `delegations.spec.ts`: board, revoke from a card |
| AC-7.1, AC-7.2 | `NotificationServiceTest` | `NotificationIT`: unread, foreign 404, type filter, 400 | - |
| AC-7.3 to AC-7.5 | select reads in place, groups | - | `notifications.spec.ts`: select does not navigate, button does |
| AC-8.1 to AC-8.4 | panel tiles, status line | - | `privacy-centre.spec.ts` updated |
| AC-9.1 to AC-9.3, AC-9.6 | `OnboardingServiceTest`: steps per role, derived flags | `OnboardingIT`: GET/PUT, 400, 401, export and erasure | - |
| AC-9.4, AC-9.5 | checklist and tour components | - | `onboarding.spec.ts` with a fresh account from `registerAndVerify` |

## 10. Manual verification

Preconditions: the container stack built from the 9.7.9 branch (`docker compose build --pull=false app frontend`,
then `docker compose up -d --no-build`), seed data, accounts `employee.fmi`, `secretary.fmi`, `dean.fmi`,
`rector`, `admin`, `gdpr` (`@chnu.edu.ua`, `Passw0rd-demo`), one fresh account from self-registration (Mailpit at
`http://localhost:8025`) for steps 20-24. Widths 360 and 1280, both schemes; uk unless marked. Expected texts are
draft wording and get the merged i18n text at the validation.

1. Open `http://localhost/login` at 1280. Expected: catalogue cards on the left, one stamped «ЦИТАТА ДНЯ», form under
   «Каталог досягнень університету», empty top-left corner (AC-1.1).
2. Switch to EN with the language link. Expected: the same quote in English, the form in English (AC-1.5, AC-1.8).
3. Open `/login` in a second browser. Expected: the same quote as step 1 (AC-1.5).
4. Set `APP_SIGN_IN_THEME=sky`, `docker compose up -d --no-build app`, reload `/login`. Expected: night sky with the
   book and steps constellations, two shooting stars, skyline, «УКР · EN» top right (AC-1.2, AC-1.4).
5. Set `rushnyk`, restart, reload. Expected: stitched band and path, star, kalyna, tree and rosette, no hooked
   motif, card at the right (AC-1.3).
6. Each theme at 360. Expected: form fully visible, decoration band at most a third of the screen, no horizontal
   scroll (AC-1.6).
7. In each theme sign in with a wrong password, then with `employee.fmi`. Expected: the error message, then My
   dashboard (AC-1.8).
8. With `APP_BRAND=neutral` and the neutral `brand/brand.json`, manifest and icons in the frontend (as in
   `DEMO_DEPLOYMENT.md`, «University brand»): `/login`, the sidebar and the browser tab. Expected: mark 9, top step
   gold (AC-1.7).
9. As `dean.fmi`, unit analytics of the faculty (unit 9), «Нагороди за роками» → «Таблиця». Expected: full grid,
   row and column totals, «Разом», «2026*» with «рік триває» (AC-2.1). As `rector`, `/analytics/university`, the
   same widget → «Таблиця». Expected: one row per faculty plus «Рівень університету» when it has awards, the
   «Разом» row equal to the chart's columns, the projection line under the grid.
10. «Порівняння кафедр» → «Таблиця». Expected: two-level headers «Нагороди» (Усього, Зміна, Верифік.) and «Люди»
    (Нагороджені, Працівники, На 100 прац.), «Увесь факультет» last; sorting by every column still works (AC-2.2).
11. Same tables at 360. Expected: pinned first column, newest year first, «N з M років — гортайте →», Tab reaches
    the scroll area (AC-2.3); numbers equal the chart (AC-2.4).
12. Unit analytics at 1280, scroll. Expected: header scrolls away, rail stays (AC-4.1); no comparison paragraph,
    «до 2017–2021» under each change, no subtitle on «Нагороди за роками» (AC-4.2).
13. Hover and then Tab to «Найкращі» row 1, Enter. Expected: hover card with the name, unit, «Нагород за період: N»
    and up to three latest public awards (none listed when every award is private), Escape hides it; Enter opens the
    person's portfolio (AC-4.3). Long department names in the rail and the comparison table wrap, never cut (AC-4.4).
14. As `employee.fmi`, My dashboard at 1280. Expected: the journey road with one milestone per award (AC-3.2);
    click one → its award page; Tab to another and Enter → its award page (AC-3.4).
15. At 360. Expected: dated feed by year with the count per year, «Від національного» narrows it to national and
    international awards, «Усі» restores it (AC-3.3).
16. As `admin`, `/admin/users`. Expected: full table with last sign-in (AC-5.1, AC-5.2); select `dean.fmi` → narrow
    list and the person pane with tabs and links; «Делегування» → `/delegations?delegatorId=<id>` read only, without
    «Делегувати» or «Відкликати», the toggle reads «Видані · N» / «Отримані · N»; «×» → full table (AC-5.3); 360 → full page with back (AC-5.4).
17. As `dean.fmi`, `/delegations`, delegate to `secretary.fmi` for next week. Expected: card under «Заплановані» in
    «Від мене», with «Відкликати»; as `secretary.fmi` the card is under «Мені» without an action (AC-6.1, AC-6.2).
18. As `employee.fmi` after `dean.fmi` approves one award: `/notifications`, select it. Expected: reading pane,
    marked read, the URL stays `/notifications` (AC-7.3, AC-7.4); «Позначити непрочитаним» → unread, sidebar
    counter up; «Відкрити нагороду» → the award (AC-7.1, AC-7.4); chip «Ролі» hides it (AC-7.2).
19. `/privacy`. Expected: «Усе гаразд», the tiles of AC-8.1; «Щодо мене» in activity; «Увесь журнал →» opens the
    whole log in the tile with «Показати ще»; «Завантажити» downloads the JSON (AC-8.1 to AC-8.3); «Видалити мій
    обліковий запис» with the password turns the status line into the countdown, «Скасувати запит» there brings
    «Усе гаразд» back; 360 one column, status line first (AC-8.4).
20. Register a fresh account, confirm in Mailpit, sign in, acknowledge the notice. Expected: My dashboard with
    «Вітаємо, <ім'я>!», NOTICE done, four open steps (AC-9.1, AC-9.2, AC-9.4).
21. «Додати» on the first award step, save a draft, back to My dashboard. Expected: `FIRST_AWARD` done (AC-9.2).
22. «Налаштувати» on visibility. Expected: the privacy panel opens; back on My dashboard the step is done (AC-9.4).
23. «Показати тур по екрану», go through all four steps. Expected: «Крок N з 4» on search, navigation, «Нова
    нагорода», the switch (AC-9.5); without a chart on the page (no approved award yet) or with the menu closed on a
    phone, the step's bubble shows in the middle of the screen. Escape on any step ends the tour.
24. `curl -H "Authorization: Bearer <token>" http://localhost/api/v1/users/me/onboarding`. Expected: the steps with
    `done`, `tourSeen: true` (AC-9.3); without the header 401 (AC-9.6).
25. As `dean.fmi`, `GET /users/me/onboarding`. Expected: `REVIEW_QUEUE` and `DELEGATE` added, `DELEGATE` done after
    step 17 (AC-9.1, AC-9.2).

### Detours

26. Set `APP_SIGN_IN_THEME=stars` and start the app. Expected: the start fails with the three allowed values;
    unset it and start again (AC-1.4).
27. `/login` with `prefers-reduced-motion: reduce` (Playwright emulation). Expected: no moving cards, stars or smoke
    (AC-1.8).
28. Open `/login`, wait past Kyiv midnight without reload (or set the clock in a test container). Expected: the
    quote changes only after reload.
29. Direct URL `/notifications?selected=<id of another person's notification>`. Expected: the pane shows «not
    found», the list stays; `DELETE /notifications/<that id>/read` answers 404 (AC-7.1).
30. Reload `/notifications` with a notification selected. Expected: the same one open, still read; after
    «Позначити непрочитаним» a reload or Back keeps it unread (only a click in the list reads it).
31. Back after «Відкрити нагороду». Expected: `/notifications` with the same notification selected.
32. Direct URL `/admin/users/<erased person id>` as `admin`. Expected: 404 message in the pane, full list.
33. **Hidden in state**: on the delegations board, an ended (revoked or expired) card and a received card. Expected:
    no «Відкликати»; `DELETE /delegations/<ended id>` answers 409, the received one 403 or 404 as in 7.x (AC-6.2).
34. **Hidden in state**: a notification that is already unread. Expected: «Позначити непрочитаним» absent; the API
    call is a no-op 204 (AC-7.1).
35. **Hidden in state**: the checklist after «Сховати список». Expected: no checklist on reload, on a second browser,
    or after a new sign-in; `PUT` with `dismissed: false` brings it back (AC-9.3, AC-9.4).
36. **Hidden in state**: `PUT /users/me/onboarding` as `employee.fmi` with `done: ["REVIEW_QUEUE"]`. Expected: 400
    `invalid-parameter` (AC-9.3).
37. **Revoked mid-flow**: `admin` revokes `secretary.fmi`'s secretary role while `secretary.fmi` has My dashboard
    with the checklist open. Expected: a revoke signs the holder out everywhere, so the open `IMPORT` button leads to
    the sign-in page; after signing in again the step is gone; another faculty secretary keeps it (AC-9.1).
38. **Revoked mid-flow**: `dean.fmi` revokes the delegation of step 17 while `secretary.fmi` has the board open on
    «Мені». Expected: on reload the card moves to «Завершені»; `dean.fmi` sees it there without an action (AC-6.1).
39. **Revoked mid-flow**: `admin` revokes `secretary.fmi`'s secretary role (`user:manage:scope`) while
    `secretary.fmi` has `/admin/users` open with a person pane. Expected: the next tab change or action ends at the
    sign-in page (the revoke signs the holder out everywhere); after signing in again `/admin/users` opens the
    forbidden page; `dean.fmi` at the same faculty keeps the page (AC-5.3).
40. Token expiry with the person pane or the reading pane open, then an action. Expected: the silent refresh keeps the
    session, or the sign-in page returns to the same URL.

### Legal pages and mail headers (9.7.10, SCRUM-190)

41. Sign in as `employee.fmi` (acknowledged 1.0). Expected: `/privacy-notice?acknowledge=true` with version 1.1 and the
    section «Файли cookie та сховище браузера»; after «Я ознайомився» the app opens; the selector lists 1.0 and 1.1.
42. Signed out, open `/login`. Expected: footer links «Конфіденційність» → `/privacy-notice` (no sign-in loop) and
    «Умови користування» → `/terms`, which opens without an account in uk and en and links back to the notice; the
    register form shows the terms line with its link.
43. Trigger a notification mail (submit an award as `employee.fmi`, decide it as `dean.fmi`) and a password reset
    mail; open both in Mailpit → «Headers». Expected: the decision mail has `List-Unsubscribe:
    <http://localhost/profile#notifications>`; the reset mail has none.

### UI review fixes (9.7.11, SCRUM-191)

44. With the default `APP_BRAND` (chnu), open `/login`, sign in and look at the sidebar, the browser tab and
    `http://localhost/brand/icons/icon-192.png`. Expected: mark 9 «Книга-сходи» everywhere, crimson theme, «Облік
    нагород ЧНУ».
45. Register a fresh account and open My dashboard in light and dark. Expected: the crimson checklist card with the
    ring «1/5», «Перші кроки», the done step struck through with «готово», the first open step («Перевірити
    профіль») ringed in gold with a filled button, the later steps as text links, the tour and hide links in light text (AC-11.2).
46. As `dean.fmi`, open `/analytics`, scroll down to the rail and choose another department. Expected: the panes stay
    (dimmed briefly), the page does not jump to the top, the focus stays on the chosen row, the new figures appear
    in place (AC-11.3).

### One analytics page (9.7.12, SCRUM-192)

47. As `rector`, open `http://localhost/analytics?from=2021&to=2025`. Expected: the address becomes
    `/analytics/university?from=2021&to=2025`; the side menu has one «Аналітика» entry, marked current; the side
    column shows «Підсумок» and «Підрозділи» with «Увесь університет» selected and the faculties below it, no
    department; the main column starts with «Факультети» (AC-12.1, 12.5–12.7).
48. Choose «Факультет математики та інформатики» in the rail, then «Кафедра алгебри та інформатики». Expected: the
    period stays in the address; the faculty row and its departments show counts and the departments are indented;
    the department switch happens in place; «Аналітика» stays current; «Увесь університет» returns to the
    university page (AC-12.1, 12.3, 12.4).
49. As `dean.fmi`, open `/analytics`; as `secretary.fmi`, open `/analytics`. Expected: the dean lands on the
    faculty with its departments and no «Увесь університет»; the secretary sees only the departments the API
    returns; `/analytics/university` as either opens the forbidden page (AC-12.2, 12.5).
### Detours from the validation
50. Signed out, open `/notifications?selected=<id>&filter=awards` and `/profile#notifications` (as `employee.fmi`)
    and `/analytics/units/9?from=2021&to=2025` (as `dean.fmi`), then sign in. Expected: each lands on the same
    address with its query and fragment. Checked in the validation 2026-11-25: all three kept.
51. In `/admin/users` search a surname with «й» typed as «и» plus U+0306 (`GET /users?q=` with `%D0%B8%CC%86`).
    Expected: the same people as the composed spelling (9.7.13, SCRUM-193; the validation found 10 against 0).
    As `secretary.fmi`, `GET /people?q=` with the same decomposed fragment. Expected: the same people without an
    account as the composed spelling (AC-13.2).

### Run 2026-11-25

Compose stack at c4453e5 (develop after #523/#524; app rebuilt, frontend from 83ca484 with the same tree), defaults catalogue/chnu; sky, rushnyk, neutral (frontend brand file copied in) and stars set per step and restored; MCP Chromium 1280/360. Result: 48 pass, 1 blocked.

| Step | Result | Evidence |
|---|---|---|
| 1 | PASS | catalogue theme at 1280: card stream left, quote card «ЦИТАТА ДНЯ» (Українка, 1890), form under «Каталог досягнень університету», no brand top left (screenshot) |
| 2 | PASS | EN link: same quote «Without hope I shall still hope…» (Ukrainka 1890), labels Email address/Password, button Sign in |
| 3 | PASS | fresh browser context: same quote of the day (Українка, «Contra spem spero!», 1890) |
| 4 | PASS | APP_SIGN_IN_THEME=sky (env checked), /login: night sky with «Сузір'я книги» and «Сузір'я сходинок», 2 sky-shoot animations, skyline with smoke, «УКР · EN» top right, same quote of the day (screenshot) |
| 5 | PASS | APP_SIGN_IN_THEME=rushnyk: stitched band left, path of three cross-stitch steps, eight-point star and rosettes, kalyna and tree motifs faint behind, all mirror-symmetric (no hooked motif), card at the right (form x 904-1194), same quote (screenshots) |
| 6 | PASS | 360 x 740: catalogue band 240 px, sky band ~240 px, rushnyk band 160 px (each at most a third), form full width (x 20-325 / 40-305), scrollWidth = clientWidth in all three (screenshots) |
| 7 | PASS | catalogue, sky and rushnyk: wrong password -> /login?error=BAD_CREDENTIALS «Невірна адреса або пароль.» in the same theme; employee.fmi -> /dashboard «Мій дашборд» |
| 8 | PASS | step text corrected (the frontend brand comes from brand/brand.json, DEMO_DEPLOYMENT.md «University brand»; neutral brand.json, manifest and icons copied into award-frontend): APP_BRAND=neutral /login footer mark 9 «Облік нагород» «Університет»; sidebar mark 9 (book + steps, top step gold) «Облік нагород / Університет», green #2d6a4f theme, tab «Мій дашборд — Облік нагород» with the neutral icons |
| 9 | PASS | dean.fmi units/9 «Таблиця»: 6 rows + «Разом», row and column totals add up (algebra 319, 2022 col 28, total 402), «2026*» with «рік триває»; rector /analytics/university 2021-2025: 13 faculty/college rows + «Рівень університету» 15, «Разом» 102/113/126/126/125 = API byYear, projection line «Прогноз на 2026: близько 136 нагород» |
| 10 | PASS | «Порівняння кафедр» table: header groups «Нагороди» (Усього, Зміна, Верифік.) and «Люди» (Нагороджені, Працівники, На 100 прац.), «Увесь факультет» last (402), each of the 6 columns sorts (aria-sort ascending, order changes) |
| 11 | PASS | dean units/9 at 360: both tables in a role=region tabindex=0 scroll area with aria-label «… гортається вбік», first column sticky (left 33.5 -> 33 after scrollLeft 150), years newest first (2026* … 2022), «2 з 5 років — гортайте →»; values as in the 1280 table; page scrollWidth 360 |
| 12 | PASS | dean units/9 at 1280: after scrolling 1200 px h1 at -1150, rail «Підрозділи» sticky (478 -> 443); summary without comparison paragraph, both change chips with «до 2017–2021», «Нагороди за роками» starts with the Графік/Таблиця switch (no subtitle/footnote) (screenshots) |
| 13 | PASS | «Найбільше нагород» row 1 hover: card «Секретаренко Аліна / Факультет математики та інформатики / Нагород за період: 7 / Портфоліо →», no awards listed (all 10 PRIVATE; 7 = personal awards 2022-2026), Escape hides it, focus shows it, Enter -> /public/achievements?person=5; long department names wrap in rail and comparison table |
| 14 | PASS | employee.fmi My dashboard 1280: «Хронологія» road with 19 stops = 19 approved awards; click stop 1 -> /awards/942 «Подяка кафедри 2020»; focus on stop 2 (A /awards/941) + Enter -> /awards/941 «Грамота факультету 2021» |
| 15 | PASS | 360: dated feed by year («2026 10 нагород», day + month), 19 rows; «Від національного» -> 2 (2024: international + national, = DB levels), «Усі» -> 19; page scrollWidth 360 |
| 16 | PASS | admin /admin/users: full table (Ім'я та адреса, Підрозділ, Ролі, Статус, Останній вхід: «Ще не входив» / «21.11.2026, 21:10»); dean.fmi -> /admin/users/4 narrow list without table header + pane tabs Ролі/Обліковий запис, links «Нагороди · 0», «Делегування» -> /delegations?delegatorId=4 read only (0 «Делегувати», 0 «Відкликати»), toggle «Видані · 26» / «Отримані · 0»; «Закрити картку» -> full table; 360: full-page card with «До списку користувачів», scrollWidth 360 |
| 17 | PASS | dean.fmi /delegations «Делегувати»: Декан · ФМІ to secretary.fmi 30.11-06.12.2026 -> card under «Заплановані 1» in «Від мене» with «Відкликати» («Почнеться через 5 днів»); secretary.fmi «Мені · 27»: card under «Заплановані» with no button |
| 18 | PASS | dean approved award 2366 -> employee /notifications, click: reading pane, bell 11 -> 10, path /notifications (?selected=1888); «Позначити непрочитаним» -> bell 11; «Відкрити нагороду» -> /awards/2366; chip «Ролі» -> ?filter=roles, item gone («У цій групі сповіщень немає») |
| 19 | PASS | employee /privacy: «Усе гаразд…», tiles notice 1.1, rankings, visibility 19/0/0/0, mail, data, activity; «Щодо мене» empty = GET /users/me/activity?byMe=false 0 rows; «Увесь журнал →» 14 -> 44 lines in the tile, «Показати ще» -> 84; «Завантажити» -> award-monitoring-export-2026-11-25.json (12 sections); delete with password + checkbox -> «Обліковий запис буде видалено 25.12.2026, залишилось днів: 30.», «Скасувати запит» -> «Усе гаразд»; 360 one column, status line first, scrollWidth 360 |
| 20 | PASS | mr97.onb2 (registered + verified via Mailpit, notice 1.1 at sign-up, EMPLOYEE) signs in -> /dashboard «Вітаємо, Iryna!», «Виконано 1 з 5», NOTICE «Готово», 4 open steps (PROFILE, FIRST_AWARD, VISIBILITY, NOTIFICATIONS) |
| 21 | PASS | «Додати» on FIRST_AWARD -> /awards/new, «Заповнити без документа», title, «Зберегти чернетку» -> /awards/2367/edit; back on My dashboard FIRST_AWARD done (NOTICE, FIRST_AWARD done) |
| 22 | PASS | «Налаштувати» on VISIBILITY -> /privacy#choices «Конфіденційність і мої дані»; Back -> My dashboard, VISIBILITY done |
| 23 | PASS | «Показати тур по екрану» at 1280: «Крок 1 з 4» Пошук, 2 Навігація, 3 Нова нагорода, 4 Графік / Таблиця (no chart: bubble centred 640,450), «Готово»; 360 with the menu closed: steps 1-2 centred (180,370); Escape ends the tour on step 2 and 4 |
| 24 | PASS | GET /users/me/onboarding with Bearer (mr97.onb2): steps with done (NOTICE, FIRST_AWARD, VISIBILITY true), tourSeen true, dismissed false; without the header 401 |
| 25 | PASS | dean.fmi GET /users/me/onboarding: steps NOTICE, PROFILE, FIRST_AWARD, VISIBILITY, NOTIFICATIONS + REVIEW_QUEUE and DELEGATE; DELEGATE done (derived) after the step 17 delegation |
| 26 | PASS | APP_SIGN_IN_THEME=stars: start fails, IllegalStateException «app.sign-in.theme (APP_SIGN_IN_THEME) must be one of catalogue, sky, rushnyk, not 'stars'», container restarts; restarted without the variable -> healthy, catalogue |
| 27 | PASS | reducedMotion reduce: catalogue 0 running animations and card positions unchanged after 1.5 s (no-preference: catalogue-up/down running, cards move); sky 0 running (normally sky-twinkle, sky-shoot, sky-wisp smoke, sky-rise); rushnyk 0 running |
| 28 | PASS | browser clock set to 23:59:50 Kyiv and run 90 s past midnight: quote unchanged, no request sent; the page is server-rendered, the change on reload is QuoteOfTheDayTest.ac1_5_the_quote_changes_at_kyiv_midnight_not_at_utc_midnight (server clock not moved) |
| 29 | PASS | employee /notifications?selected=1685 (dean's): pane «Сповіщення не знайдено: … належить іншій особі.», list stays (9 items); DELETE /notifications/1685/read 404 notification-not-found, dean's row unchanged |
| 30 | PASS | 1888 read (bell 10) + reload: same notification open, bell 10, «Позначити непрочитаним» shown; after «Позначити непрочитаним» reload and Back keep it unread (bell 11) |
| 31 | PASS | «Відкрити нагороду» -> /awards/2366, Back -> /notifications?selected=1888 with the same notification selected |
| 32 | PASS | admin /admin/users/2113 (DELETED account): GET /users/2113 404, pane «Користувача не знайдено. Такого користувача немає або він поза межами ваших повноважень.», list beside it with all 1958 people (20 links, «1–20 з 1958») |
| 33 | PASS | dean «Завершені · 26» cards (revoked/expired) without «Відкликати»; secretary received card without a button; DELETE /delegations/55 (revoked) as dean 409 delegation-not-active, DELETE /delegations/58 (received) as secretary 404 |
| 34 | PASS | 1035 unread: pane without «Позначити непрочитаним» (only «Відкрити нагороду»); DELETE /notifications/1035/read 204, still unread |
| 35 | PASS | mr97.onb2 «Сховати список»: no checklist on reload, in a second browser after a new sign-in (dismissed true); PUT /users/me/onboarding dismissed false -> 200, checklist back on reload |
| 36 | PASS | employee.fmi PUT /users/me/onboarding done [REVIEW_QUEUE] -> 400 urn:awards:problem:invalid-parameter, parameter done; state unchanged |
| 37 | PASS | step text corrected (a revoke signs the holder out everywhere, AUTHENTICATION_AUTHORIZATION.md role assignment): admin revokes FACULTY_SECRETARY of secretary.fmi while My dashboard shows IMPORT; the IMPORT button leads to the sign-in page; after sign-in GET /users/me/onboarding lists NOTICE, PROFILE, NOTIFICATIONS (IMPORT gone); mr97.sec2 (faculty secretary of 9) keeps REVIEW_QUEUE and IMPORT |
| 38 | PASS | secretary.fmi /delegations on «Мені» with delegation 58 under «Заплановані»; dean.fmi «Відкликати» -> dean «Заплановані 0», «Завершені 27»; secretary reload (server session idle 19 min -> sign-in -> back at /delegations) shows «Мені · 27» with the card «Відкликано 30.11.2026 — 06.12.2026» under «Завершені»; dean's ended cards without «Відкликати» |
| 39 | PASS | step text corrected (as 37): secretary.fmi /admin/users/1098 pane, after the revoke the next tab change ends at the sign-in page, after sign-in /admin/users/1098 -> /forbidden; dean.fmi on /admin/users/1098 keeps the page and switches to «Обліковий запис» |
| 40 | PASS | admin /admin/users/4 pane and employee /notifications?selected=1888 opened 18:02Z, idle past the 15 min access token; 18:17Z tab change + search: GET /users?q=… 200, /users/4 200; reading-pane action: 204 /notifications/669/read, unread-count 200; URLs unchanged, no sign-in page (silent refresh) |
| 41 | BLOCKED | every seed account already acknowledged 1.1 and consent_records are append-only (fn_consent_records_guard refused the 1.0 setup), so the redirect at sign-in was not walked; covered by e2e privacy-notice.spec ac1_8; checked: /privacy-notice?acknowledge=true shows «Файли cookie та сховище браузера», the hint «…версію 1.1…», «Я ознайомився», selector 1.1 · 26.11.2026 and 1.0 · 15.10.2026 |
| 42 | PASS | signed out: footer «Конфіденційність» -> /privacy-notice (h1 shown, no sign-in loop), «Умови користування» -> /terms in uk and en (EN switch: «Terms of use»), links back to the notice; /register shows «Користуючись системою, ви погоджуєтеся з умовами користування.» with the link |
| 43 | PASS | employee submits 2366, dean approves: «Нагороду затверджено / Award approved» 20:01 to employee.fmi has List-Unsubscribe: <http://localhost/profile#notifications>; «Скидання пароля / Password reset» to mr97.notice (POST /auth/password-reset/request 202) has no List-Unsubscribe |
| 44 | PASS | default brand chnu: /login footer and sidebar show mark 9 (book with steps, top step gold) and «Облік нагород ЧНУ», primary #7a1f2e crimson, tab «Мій дашборд — Облік нагород ЧНУ» with brand/icons/favicon.ico, /brand/icons/icon-192.png 200 image/png mark 9 |
| 45 | PASS | step text corrected (the first open step is «Перевірити профіль», AC-11.2): crimson card light / dark tokens, ring «1/5», «Перші кроки», NOTICE struck through with «Готово», first open step ringed gold with the filled «Перевірити», later steps as text links, «Показати тур по екрану» / «Сховати список» in light text (screenshots) |
| 46 | PASS | dean.fmi /analytics -> units/9, scrolled 450, chose «Кафедра математичного аналізу»: /analytics/units/66 in the same document, aria-busy on the panes during the load, scrollTop stays 450, focus on the chosen row, figures replaced in place (21, +9 (+75 %)) |
| 47 | PASS | rector /analytics?from=2021&to=2025 -> /analytics/university?from=2021&to=2025; one «Аналітика» nav entry aria-current=page; side column «Підсумок», «Підрозділи» with «Увесь університет» (aria-current) and 12 faculties, no department; main column starts with «Факультети» |
| 48 | PASS | rector: faculty FMI -> /analytics/units/9?from=2021&to=2025, rail faculty «147 схвалених нагород» and 5 departments with counts indented (padding 44 vs 18 px); department -> /analytics/units/64?from=2021&to=2025 in place (same document, scrollTop 400 kept, focus on the row), nav «Аналітика» current; «Увесь університет» -> /analytics/university?from=2021&to=2025 |
| 49 | PASS | dean /analytics -> units/9, rail faculty + 5 departments, no «Увесь університет»; secretary /analytics -> units/64, rail = 5 departments of GET /analytics/units (64-68), no faculty/university; /analytics/university as either -> /forbidden «Доступ заборонено» (API 403) |

## 11. Risks

| # | Risk | Mitigation |
|---|------|------------|
| 1 | Nine stories before the demo server and the defense (~2026-12-15) | order by visibility; 9.7.9 then 9.7.8 move after the deploy |
| 2 | Three sign-in themes triple the page weight | one theme rendered per request; decoration as inline SVG and CSS, no images over 30 kB |
| 3 | A quote's wording or source is wrong in front of the committee | only quotes with a named source ship; doubtful ones (A3) are left out |
| 4 | One test run at the end finds many failing specs (D1) | static checks and backend gates per story, data-testids kept |
| 5 | The checklist annoys the demo accounts at the defense | one «Сховати список» per account before the defense, or seed rows with `dismissed` |

## 12. Definition of Done

- Every AC proven by its unit, IT/FT or e2e test; full gate green (JaCoCo ≥ 85 %, static 0, frontend lint and
  tests) and full e2e green on the 9.7.9 branch.
- UI check of the changed pages at 360 and 1280, light and dark, uk and en, compared with the boards; axe without
  serious findings.
- `openapi.yml` stubs turned into the shipped contract, `DATA_DICTIONARY.md` §1.8 (`onboarding`, export 1.9),
  `UI_GUIDELINES.md` (widget tables, sign-in themes), CHANGELOG lines, tracker and BACKLOG rows.
- The manual run of §10 recorded in this PRD.

## Validation (2026-11-25)

Result: **PASSED WITH NOTES**. Stories 9.7.1 to 9.7.12 merged (#510-#512, #515-#523); manual run of §10 on
2026-11-25: 48 of 49 steps, step 41 covered by e2e `privacy-notice.spec` ac1_8 (every seed account had already
acknowledged 1.1, and consent rows are append-only). One fix story from the scenario review: 9.7.13 (SCRUM-193, #526).

Suite on `develop` plus the e2e fix of this PR: unit 1 498, IT/FT 644, JaCoCo 98.2 % lines (7 392/7 527),
Checkstyle/PMD/SpotBugs 0; frontend lint, 1 607 unit tests, production build 930 kB; Playwright 280/280 on one worker (55 min): `command-centre.spec` ac5_1 ac5_7 and
`people.spec` ac1_4 ac1_10 passed on retry, the retry also ran the three command-centre tests the first failure had
left unrun.

| AC | Evidence (all passing) |
|----|------------------------|
| 1.1-1.3, 1.6 | `LoginPageFT` ac1_1, `SignInThemesFT` ac1_2, ac1_3; e2e `login.spec` ac1_1 ac1_5; manual 1, 4-6 |
| 1.4 | `SignInThemeTest` ac1_4 (4, unknown value stops the start), `SignInThemesFT` ac1_4_ac1_8 (error page keeps the theme); manual 26 |
| 1.5 | `QuoteOfTheDayTest` ac1_5 (5: Kyiv midnight, wrap-around, language), `LoginPageFT` ac1_5; manual 2, 3, 28 |
| 1.7, 11.1 | `brand.service.spec` (neutral fallback, ChNU file), `LoginPageFT` (logo per brand), e2e `theme.spec` (neutral logo path); the artwork itself by manual 8 and 44 |
| 1.8 | `LoginPageFT` ac1_8, e2e `login.spec` ac1_8 (reduced motion); manual 7, 27 |
| 2.1-2.4 | `year-grid.component.spec`, `unit-comparison.component.spec`; e2e `unit-analytics.spec` 9.7 ac2_1 ac2_2 ac2_4, 9.7 ac2_3, `university-analytics.spec` 9.7 ac2_1 ac2_4; manual 9-11 |
| 3.1 | `PersonalAnalyticsServiceTest`, `AwardStatisticsIT`, `PersonalAnalyticsFT` (level per entry) |
| 3.2-3.4 | `timeline-chart.component.spec`; e2e `dashboard.spec` ac3_2 ac3_4, ac3_3 ac3_4; manual 14, 15 |
| 4.1-4.3 | `delta-tiles.component.spec` ac4_1, ac4_2, `top-performers.component.spec` ac4_3 (5), `unit-analytics.component.spec` ac4_1, ac4_2; e2e `unit-analytics.spec` 9.7 ac4_1 ac4_3; manual 12, 13 |
| 4.4 | No automated test: long names wrap in the rail and the comparison table by manual 13 only |
| 5.1 | `UserDirectoryServiceTest`, `AuthenticationFlowFT` (`lastLoginAt`) |
| 5.2-5.4 | `user-list`, `user-detail.component.spec`, `admin-users.feature.spec`; e2e `people.spec` ac5_1 ac5_2 ac5_3, ac5_3, ac5_4; manual 16, 32 |
| 6.1-6.3 | `delegation-list.component.spec` ac6_1, ac6_2, `delegations.feature.spec`; e2e `delegation.spec` ac6_3, ac34; manual 17, 33, 38 |
| 7.1, 7.2 | `NotificationFT` ac7_1 (2), ac7_2, `NotificationServiceTest`, `unread-count.spec`; manual 18, 29, 34 |
| 7.3-7.5 | `notification-list.component.spec` ac7_3, ac7_4 (12), ac7_5; e2e `notifications.spec` ac7_2 ac7_3 ac7_4; manual 18, 30, 31 |
| 8.1-8.4 | `privacy-centre.component.spec` ac8_1 (2), ac8_2 (3), ac8_3; e2e `privacy-centre.spec` ac8_1 ac8_2, ac8_4; manual 19 |
| 9.1-9.3, 9.6 | `OnboardingServiceTest` (9), `OnboardingQueriesIT` ac9_2, ac9_3, `OnboardingFT` ac9_1, ac9_3 (2), ac9_6, `DataExportFT`; manual 24, 25, 36 |
| 9.4, 9.5 | `onboarding-checklist`, `onboarding-tour.component.spec`, `onboarding.service.spec`; e2e `onboarding.spec` ac9_4, ac9_5; manual 20-23, 35 |
| 10.1 | e2e `privacy-notice.spec` ac1_2 ac1_9 (1.1 and 1.0 in the selector), ac1_8 (acknowledgement of 1.1); manual 41 (page part) |
| 10.2 | `terms.component.spec`, `register.component.spec` ac10_2; e2e `privacy-notice.spec` ac10_2; manual 42 |
| 10.3 | `MailDeliveryTest` ac10_3 (2); manual 43 |
| 11.2 | `onboarding-checklist.component.spec` ac11_2 (2); manual 45 |
| 11.3 | `navigation-focus.service.spec`, `unit-analytics.effects.spec`, e2e `unit-analytics.spec` ac11_3; manual 46 |
| 12.1-12.7 | `analytics-rail.component.spec` ac12_1 to ac12_4, `unit-analytics-entry.component.spec` ac12_5, `shell.component.spec` ac12_6, `university-analytics.component.spec` ac12_7; e2e `university-analytics.spec` 9.7 ac12_1 ac12_4 ac12_5, 9.7 ac12_7; manual 47-49 |

Edge cases of §5: quote at midnight (`QuoteOfTheDayTest`, manual 28), theme on the error and rate-limit pages
(`SignInThemesFT`), reduced motion (e2e `login.spec` ac1_8, manual 27); the Pasteur quote did not ship (17 quotes in
`quotes.json`, A3); zero years and one-year periods (`year-grid.component.spec`); empty, single and truncated
timeline (`timeline-chart.component.spec`); rankings consent (`PersonalAnalyticsServiceTest` ac1_6_ac1_7), private
awards in the hover card (`top-performers.component.spec`, manual 13); erased or out-of-scope person
(`user-detail.component.spec`, `admin-users.effects.spec`, manual 32); delegation state changes and the 409 (manual
33, 38); mark unread no-op and a notification without a target (`NotificationFT`, `notification-list` ac7_4, manual
34); export 429 and legal hold (`privacy-centre.component.spec`); onboarding roles added or removed and stored keys of
a removed role (`OnboardingServiceTest`, `OnboardingFT`, manual 37). A draft that never reached review is the only
award that can be deleted, so no decision notification points at a missing award.

Security: A01 every new endpoint takes the person from the token (`OnboardingController`, `NotificationController`
`DELETE /{id}/read`, `PrivacyCentreController` `byMe`); foreign notifications 404 (`NotificationFT` ac7_1), steps
outside the caller's roles 400 (`OnboardingFT` ac9_3), `/analytics/university` 403 for a dean or secretary (manual
49); a revoked role signs the holder out everywhere (manual 37, 39). A02 nothing new. A03 the onboarding keys are an
enum checked before the JSONB write; the user search escapes `%`, `_` and `\` (`UserSpecifications`). A07 401
without a token (`OnboardingFT` ac9_6, `NotificationFT` ac1_11); the sign-in themes keep the CSRF form, the lockout
and the rate-limit page (`SignInThemesFT`), and `/login` answers `Cache-Control: no-cache, no-store`.

Integration: the Compose stack rebuilt from `develop` starts clean; Flyway applies every migration on the empty
Testcontainers database of the IT/FT run; `openapi.yml` lists all 99 operations of `/v3/api-docs` (the 7 extra
entries are the authorization server and actuator).

Findings:
1. User search with decomposed input: «Олійник» finds 10 people, the same name with «й» as «и» + U+0306 finds none
   (`UserSpecifications.matching` does not normalise). Fix story 9.7.13 (SCRUM-193, #526); detour step 51.
2. Deep links through sign-in keep the query and the fragment (`/notifications?selected=…&filter=…`,
   `/profile#notifications`, `/analytics/units/9?from=…`); detour step 50.
3. A read notification opened from the «Непрочитані» chip and reloaded shows the not-found text of a selection
   outside the list («немає на цій сторінці списку або воно належить іншій особі»). Kept: the text names both cases.
4. Two tabs saving onboarding progress: the last full write wins, so a stale tab can show a hidden checklist again
   (§5 says last write wins). Kept.
5. A second tab that requests erasure after the first one did gets the 409 message without reloading the status
   line; a query-only change of `delegatorId` on the delegations board keeps the first person. Both rare; kept.
6. CI on `develop` failed on e2e strict-mode races: `review-queue.spec` ac6_6 (fixed in #524) and
   `documents.spec` ac2_1 (two «Dismiss» buttons, fixed in this PR).
7. Refactor sweep: duplicated category and level rows and period helpers in unit and university analytics, the
   chart-pane hold repeated in five chart components, `DeltaTilesComponent` `bento` layout and `years` input unused,
   fixed waits in `review-queue.spec`, `OnboardingRequest` copying its list without `List.copyOf`. No defect; listed
   in the tracker's technical notes for a later clean-up.

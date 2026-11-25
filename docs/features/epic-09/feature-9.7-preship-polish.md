# Feature 9.7: Pre-ship polish

> **Epic**: 9 Production hardening (SCRUM-128) · **Tracker**: `docs/epics/EPIC-09-PRODUCTION-HARDENING-STATUS.md`
> **Sprint**: 11 (2026-11-23 to 2026-11-29) and 12 (2026-11-30 to 2026-12-06) · **Points**: 30
> **Status**: Approved 2026-11-24
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
  `GET /notifications` (`type` filter, 9.7.7), `GET /users/me/export` (format 1.9 with `onboarding`, 9.7.9).
- Read as is: `GET /analytics/units/{id}`, `GET /analytics/university`, `GET /achievements?person=`,
  `GET /delegations`, `GET /users/me/privacy`, `GET /users/me/activity`, `GET /users/me/consents`.

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
| AC-4.1 to AC-4.4 | rail, caption, performer card | - | `analytics.spec.ts`: performer link opens the portfolio |
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
8. With `APP_BRAND=neutral`: `/login`, the sidebar and the browser tab. Expected: mark 9, top step gold (AC-1.7).
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
13. Hover and then Tab to «Найкращі» row 1, Enter. Expected: hover card with three latest awards, then the person's
    portfolio (AC-4.3).
14. As `employee.fmi`, My dashboard at 1280. Expected: the journey road with one milestone per award (AC-3.2);
    click one → its award page; Tab to another and Enter → its award page (AC-3.4).
15. At 360. Expected: dated feed by year with the count per year, «Від національного» narrows it to national and
    international awards, «Усі» restores it (AC-3.3).
16. As `admin`, `/admin/users`. Expected: full table with last sign-in (AC-5.1, AC-5.2); select `dean.fmi` → narrow
    list and the person pane with tabs and links; «×» → full table (AC-5.3); 360 → full page with back (AC-5.4).
17. As `dean.fmi`, `/delegations`, delegate to `secretary.fmi` for next week. Expected: card under «Заплановані» in
    «Від мене», with «Відкликати»; as `secretary.fmi` the card is under «Мені» without an action (AC-6.1, AC-6.2).
18. As `employee.fmi` after `dean.fmi` approves one award: `/notifications`, select it. Expected: reading pane,
    marked read, the URL stays `/notifications` (AC-7.3, AC-7.4); «Позначити непрочитаним» → unread, sidebar
    counter up; «Відкрити нагороду» → the award (AC-7.1, AC-7.4); chip «Ролі» hides it (AC-7.2).
19. `/privacy`. Expected: «Усе гаразд», the tiles of AC-8.1; «Щодо мене» in activity; «Завантажити» downloads the
    JSON (AC-8.1 to AC-8.3); 360 one column (AC-8.4).
20. Register a fresh account, confirm in Mailpit, sign in, acknowledge the notice. Expected: My dashboard with
    «Вітаємо, <ім'я>!», NOTICE done, four open steps (AC-9.1, AC-9.2, AC-9.4).
21. «Додати» on the first award step, save a draft, back to My dashboard. Expected: `FIRST_AWARD` done (AC-9.2).
22. «Налаштувати» on visibility. Expected: the privacy panel opens; back on My dashboard the step is done (AC-9.4).
23. «Показати тур по екрану», go through all four steps. Expected: «Крок N з 4» on search, navigation, «Нова
    нагорода», the switch (AC-9.5).
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
30. Reload `/notifications` with a notification selected. Expected: the same one open, still read.
31. Back after «Відкрити нагороду». Expected: `/notifications` with the same notification selected.
32. Direct URL `/admin/users?selected=<erased person id>` as `admin`. Expected: 404 message in the pane, full list.
33. **Hidden in state**: on the delegations board, an ended (revoked or expired) card and a received card. Expected:
    no «Відкликати»; `DELETE /delegations/<ended id>` answers 409, the received one 403 or 404 as in 7.x (AC-6.2).
34. **Hidden in state**: a notification that is already unread. Expected: «Позначити непрочитаним» absent; the API
    call is a no-op 204 (AC-7.1).
35. **Hidden in state**: the checklist after «Сховати список». Expected: no checklist on reload, on a second browser,
    or after a new sign-in; `PUT` with `dismissed: false` brings it back (AC-9.3, AC-9.4).
36. **Hidden in state**: `PUT /users/me/onboarding` as `employee.fmi` with `done: ["REVIEW_QUEUE"]`. Expected: 400
    `invalid-parameter` (AC-9.3).
37. **Revoked mid-flow**: `admin` revokes `secretary.fmi`'s secretary role while `secretary.fmi` has My dashboard
    with the checklist open. Expected: the open `IMPORT` button leads to the forbidden page; on reload the step is
    gone; another faculty secretary keeps it (AC-9.1).
38. **Revoked mid-flow**: `dean.fmi` revokes the delegation of step 17 while `secretary.fmi` has the board open on
    «Мені». Expected: on reload the card moves to «Завершені»; `dean.fmi` sees it there without an action (AC-6.1).
39. **Revoked mid-flow**: `admin` revokes `secretary.fmi`'s secretary role (`user:manage:scope`) while
    `secretary.fmi` has `/admin/users` open with a person pane. Expected: the next tab change or action answers 403
    and the shell sends the account to `/forbidden`; `dean.fmi` at the same faculty keeps the page (AC-5.3).
40. Token expiry with the person pane or the reading pane open, then an action. Expected: the silent refresh keeps the
    session, or the sign-in page returns to the same URL.

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

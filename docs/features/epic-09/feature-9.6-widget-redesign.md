# Feature 9.6: Widget and shell redesign

> **Epic**: 9 Production hardening (SCRUM-128) · **Tracker**: `docs/epics/EPIC-09-PRODUCTION-HARDENING-STATUS.md`
> **Sprint**: 11 (2026-11-23 to 2026-11-29) and 12 (2026-11-30 to 2026-12-06) · **Points**: 29
> **Status**: Done 2026-11-26 (manual run of §10: 26 of 27, step 25 accepted on `highlights.component.spec`)
> **Governing docs**: ADR-014 and ADR-016 (9.3 addenda: tokens, effect components, chart theme), ADR-015 (NgRx per
> feature), `docs/frontend/UI_GUIDELINES.md` §1-8, Feature 9.3 (the look), Feature 9.4 (widget kit, role
> dashboards, `dashboard_preferences`), Feature 9.5 (page kit: page header, split, bento, pane, list row);
> `openapi.yml` `GET /analytics/me`, `GET /analytics/units/{id}`, `GET /analytics/university`,
> `GET|PUT /users/me/notification-preferences`, `GET /users/me`; the widget canvas of 2026-11-22
> (artboards 1 My dashboard, 2 Unit analytics, 3 University analytics, 4 Hall of Fame, 5 Profile, 6 Widget header
> styles) and the all-pages canvas of 2026-11-22 (24 artboards, one per page)

## 1. Problem and personas

Feature 9.5 gave every page its layout, and the first look pass on SCRUM-174 brought the colours, banners and chips
of the layout canvas. The widgets inside the panes still use the 9.4 drawings: thin lines, small bars, a pale heading
that merges with the body, a «Показати таблицею» switch inside the chart, and the same green and gold accents on
light and dark. A widget round on 2026-11-22 picked one option per widget; this feature builds those picks, removes
the top bar (its tools move to the sidebar) and gives Profile the split of the other pages.

- **Employee** (Anastasia): reads My dashboard; wants to see at once whether this year is better than the last one
  and in which categories her awards are.
- **Secretary and dean** (Olena, Petro): read unit analytics to compare departments and to see who leads.
- **Rector** (Roman): opens Hall of Fame and university analytics before a council meeting; wants big numbers, the
  year in progress, and faculties compared with the previous period without reading two tiles.
- **Every signed-in user**: changes their name, sign-in address and notifications on Profile.

## 2. Scope

**In**

- Shell: no top bar on signed-in pages at 960 px and wider; search, language, theme, notifications bell and the user
  menu with sign-out at the bottom of the sidebar. Below 960 px a slim bar with the menu button and the app name;
  the same tools sit at the bottom of the drawer. Signed-out pages keep their slim bar.
- Widget header style 8 for every pane with a heading: tinted strip, 2 px primary line under it, an icon before
  the title, the «Графік / Таблиця» switch in the header for widgets that have a table.
- Widgets of My dashboard, unit analytics, university analytics and Hall of Fame as picked (§4).
- Profile as a split: sections on the left, the chosen section on the right; notifications as switches.
- The rest of the all-pages canvas that the look pass did not reach: award detail and privacy in two columns,
  the 404 page as a tile, Users filters in a toolbar under the title, Command centre panes with header 8, the
  backend sign-in page with the gold logo, outlined field labels that no longer touch the border.
- One API field: the previous-period count per unit (§8.1), for the faculty tile of university analytics.

**Out**

- Year recap (D6) and OCR (3.2): unchanged decisions of 2026-11-14.
- Compliance on university analytics: the tile is not drawn in the canvas; Compliance stays the data protection
  officer's page (assumption A3).
- New widgets in the widget catalogue or new dashboard layouts: the stored `layouts` keep their widget lists.
- Board report PDF (5.3) content: it keeps its own drawing.

## 3. Stories

| # | Story | Pts | Parallel | Depends on |
|---|-------|-----|----------|------------|
| 9.6.1 | Canvas look, sidebar tools and widget header (SCRUM-174) | 5 | no | - |
| 9.6.2 | My dashboard widgets | 3 | no | 9.6.1 |
| 9.6.3 | Unit analytics widgets | 5 | no | 9.6.1 |
| 9.6.4 | University bento with the faculty comparison | 5 | no | 9.6.3 |
| 9.6.5 | Hall of Fame widgets | 5 | no | 9.6.1 |
| 9.6.6 | Profile with sections | 3 | no | 9.6.1 |
| 9.6.7 | Remaining pages of the all-pages canvas | 3 | no | 9.6.1 |

Order 9.6.1 → 9.6.2 → 9.6.3 → 9.6.4 → 9.6.5 → 9.6.6 → 9.6.7. SCRUM-174 (created as 9.5.11 with the first look pass
already on its branch) becomes 9.6.1. The branches stack; tests run once at the end (D1). The demo server
(SCRUM-134) follows this feature; if time runs short, 9.6.7 and then 9.6.6 move after the deploy (§11 risk 1).

## 4. Acceptance criteria

The look of each AC is the named artboard of the widget canvas (W) or the all-pages canvas (P), in the light
burgundy and cream scheme and in the dark Aurora scheme. Common rules for every widget of this feature: bars and
columns at least 14 px thick, no line chart thinner than 3 px, every bar or column labelled with its number, change
chips neutral (primary text on the tinted strip colour, sign and number, never green or red), gold only on ink or
burgundy surfaces in the light scheme, text contrast at least 4.5:1 in both schemes.

### 9.6.1 Canvas look, sidebar tools and widget header (SCRUM-174)

- **AC-1.1** (No top bar) Given any signed-in page at 960 px or wider, when it renders, then there is no top bar;
  search (opens the Ctrl K palette) sits at the top of the sidebar under the logo, and its bottom holds the language
  switch, the theme switch, the notifications bell with its unread count, and the user's initials with name, role
  and sign-out; every one is a
  button or link with an accessible name and a visible focus ring (P «Мій дашборд»).
- **AC-1.2** (Phone shell) Given a signed-in page below 960 px, then a slim bar shows the menu button and the app
  name, the drawer holds the navigation and, at its bottom, the same tools as AC-1.1; there is no bottom bar and no
  horizontal scroll at 360.
- **AC-1.3** (Signed-out bar) Given the sign-in, registration, privacy notice, public achievements or 404 page
  while signed out, then a slim bar with the logo, the language switch and the theme switch stays at the top.
- **AC-1.4** (Widget header) Given any pane with a heading, then the heading sits on a tinted strip with a 2 px
  primary line under it, a 20 px icon before a 14 px bold title and the pane's meta on the right (W6, option 8);
  ink and brand panes keep their own surface with the same layout.
- **AC-1.5** (Chart or table) Given a widget that has a table form, then a segmented «Графік / Таблиця» control sits
  in its header, «Графік» selected by default, the old «Показати таблицею» switch is gone from the body, and the
  choice switches the body between the chart and the table without reloading the data.
- **AC-1.6** (Canvas tokens) Given the light scheme, then the page background is cream, panes are off-white with a
  warm border, the sidebar is solid burgundy with the gold logo and the active item cream; given the dark scheme,
  then the Aurora pairs apply (navy background, cyan primary, gold accent) (P, every artboard).
- **AC-1.7** (Field labels) Given an outlined form field with a value (Profile «Ім'я»), then its label never
  overlaps or touches the field border, in both schemes and both languages.

### 9.6.2 My dashboard widgets (SCRUM-175)

- **AC-2.1** (Awards by year) Given `employee.fmi` with approved awards in 2020-2026, when My dashboard renders, then
  «Нагороди за роками» opens with three number cards (this year, last year, the average of the period's earlier
  years) and under them one thick horizontal bar per year, newest on top, with the year on the left and the count
  at the end of the bar; the current year's bar is primary and the others muted (W1, J2).
- **AC-2.2** (By year, table) Given «Таблиця» in that header, then a table lists year and count with the same
  numbers as the bars.
- **AC-2.3** (By category) Given approved awards in several categories, then «За категоріями» shows the top three as
  cards (rank, category, count, share of the total) and the rest as a ranked list with a thick bar per category
  (W1, D); fewer than three categories show only cards.
- **AC-2.4** (Empty years) Given a person whose first award is this year, then the average card shows «-» with the
  hint that there are no earlier years, and only this year's bar is drawn.

### 9.6.3 Unit analytics widgets (SCRUM-176)

- **AC-3.1** (Years per department) Given `dean.fmi` on unit analytics for the faculty, then «Нагороди за роками»
  shows one small column chart per department, each on its own scale, with the number on every column and the
  current year in primary (W2, A); «Таблиця» lists departments by years.
- **AC-3.2** (Leaders) Given top performers, then «Найбільше нагород» shows the first three on a podium (the first
  on a burgundy card, the next two on cream cards, gold rank on dark only) and the rest as a ranked list with
  initials, department and count (W2, A).
- **AC-3.3** (Department comparison) Given the department rows, then «Порівняння кафедр» is a table with the full
  department name on one line, approved count with an inline bar, the change against the previous period as a
  neutral chip, and per 100 members with an inline bar; the separate «на 100 працівників» chart is gone (W2, B).
- **AC-3.4** (Highlights) Given highlights, then each sits on the ink panel as a big number (current count) with
  «було N» beside it and one line of text under it (W2, A).
- **AC-3.5** (Levels and categories) Given levels and categories, then each has two thick bars: the previous period
  muted and the current one in primary, with numbers at the end; no legend switch (W2, A).

### 9.6.4 University bento with the faculty comparison (SCRUM-177)

- **AC-4.1** (Faculties tile) Given `rector` on university analytics, then one «Факультети» tile shows a thick bar
  per faculty for the current period, a dark tick at the previous period's value and «+N» or «−N» at the end, sorted
  by the current count; there is no separate «Динаміка факультетів» tile (W3, A++ S1): the «Тенденції» tile (rising
  and falling categories and sources) is the one the canvas drew under that name and leaves the page.
- **AC-4.2** (Previous count in the API) Given `GET /analytics/university` and `GET /analytics/units/{id}`, then each
  row of `units` carries the approved awards of that unit in `previousPeriod` as `approved.previous` (the `Figure`
  of 5.2.1), 0 when there were none; a unit that did not exist then has 0. No new field: the tick reads it.
- **AC-4.3** (Highlights capped) Given more than four highlights, then the ink panel shows four and a «Усі N» button
  that expands the panel in place and turns into «Згорнути».
- **AC-4.4** (Years and forecast) Given the years with the projection, then the tile spans the full width: thick
  columns with numbers, the projected rest of the current year hatched, «рік триває» on it (W3, A).
- **AC-4.5** (Bento tiles) Given the KPI tiles, the comparison table and levels and categories, then they follow
  W3 A: KPI tiles with a number, a short sparkline at least 3 px thick and a neutral chip; the comparison table as
  in AC-3.3; levels and categories as in AC-3.5; no Compliance tile.

### 9.6.5 Hall of Fame widgets (SCRUM-178)

- **AC-5.1** (This year) Given `rector` on Hall of Fame, then «Нагороди цього року» is the page hero: a burgundy
  card with the year's count in large type, a ring showing the share of the year passed, and a neutral chip against
  the same date last year (W4, B).
- **AC-5.2** (By year) Given the years, then «Нагороди за роками» shows thick horizontal bars (at least 26 px) with
  the year on the left and the count at the end, the current year marked «рік триває» (W4, B).
- **AC-5.3** (This year against last) Given this and last year by month, then «Цей рік проти минулого» shows two
  large numbers as a scoreboard (this year to date, last year to the same date), two thick bars under them and one
  line naming the month this year overtook the last one, or the gap when it did not (W4, C).
- **AC-5.4** (Facts and records) Given facts and records, then the facts use the ink panel of the dashboard (number
  in gold, text under it) and the records are equal cards with a large value, a label and the holder (W4).
- **AC-5.5** (Side by side) Given 1280 px, then «Нагороди за роками» and «Цей рік проти минулого» sit side by side
  under the hero; below 960 they stack (P «Зала визнання»).

### 9.6.6 Profile with sections (SCRUM-179)

- **AC-6.1** (Banner) Given any signed-in user on `/profile`, then a burgundy banner shows the initials on gold,
  the name, the role and unit, the membership chip and, at 960 and wider, the sign-in address, account creation
  date and last sign-in (W5, A).
- **AC-6.2** (Sections) Given the page at 960 or wider, then the left pane lists «Ім'я та прізвище», «Адреса для
  входу», «Сповіщення», «Конфіденційність і мої дані» and «Ролі», each with an icon and a one-line summary; the
  chosen one is marked `aria-current` and shown in the right pane; the choice is in the URL (`?section=`) and
  survives a reload; below 960 the list shows alone and a section opens full width with a back action.
- **AC-6.3** (Notifications as switches) Given «Сповіщення», then events are grouped («Мої нагороди», «Розгляд»,
  «Ролі та доручення») with two switches per event (e-mail, in the system), each with an accessible name; a change
  is saved at once through `PUT /users/me/notification-preferences` and turning off the last channel of an event is
  refused with the existing message.
- **AC-6.4** (Privacy section) Given «Конфіденційність і мої дані», then the pane links to consents, data export and
  account deletion on `/privacy`.

### 9.6.7 Remaining pages of the all-pages canvas (SCRUM-180)

- **AC-7.1** (Award detail) Given an award detail at 1280, then the award facts and the review journey sit in two
  columns; below 960 they stack, facts first (P «Нагорода»).
- **AC-7.2** (Privacy) Given `/privacy` at 1280, then consents and the data actions sit in two columns (P
  «Конфіденційність і мої дані»).
- **AC-7.3** (Not found) Given an unknown route, then the 404 page is one centred tile with the code, the message
  and a link home, in both schemes (P «Сторінку не знайдено»).
- **AC-7.4** (Users toolbar) Given `/admin/users`, then «Додати людину» sits in the title row and search and filters
  in a toolbar under the title, wrapping at 360 (P «Користувачі»).
- **AC-7.5** (Command centre) Given `secretary.fmi` on the Command centre, then its panes use header 8 and the
  common widget rules (P «Командний центр»).
- **AC-7.6** (Sign-in page logo) Given the backend sign-in page, then it shows the gold logo of the canvas on the
  burgundy panel, not the cyan one.

## 5. Edge cases

- Zero data: every widget of this feature has an empty state in the pane body (no empty chart frame); the header
  stays, the «Графік / Таблиця» switch is hidden.
- A department with no awards in any year: its small chart shows zeros with numbers, not a blank card.
- One department or one faculty: podium and comparison show a single card or row; no ranking chips.
- Previous period without data: chips show «нове» instead of a percentage; the faculty tick is absent.
- Long Ukrainian names: department names wrap to two lines in the comparison table below 960, never ellipsised at
  1280.
- Notification switch saved while the token has expired: the refresh flow of 1.x runs; on failure the switch goes
  back and the error is announced.
- The theme or language switched from the sidebar: the chart colours and labels follow without a reload.

## 6. Dependencies

### Tables
None changed.

### Endpoints
- Used unchanged: `GET /analytics/units/{id}`, `GET /analytics/university` (`UnitComparisonRow.approved.previous`),
  `GET /analytics/me`, `GET|PUT /users/me/notification-preferences`, `GET /users/me`.

### Services
- Backend: the unit comparison query of 5.2.1 counts the previous period in the same pass; the analytics cache
  (5 min) keeps its keys.

### Frontend
- `core/layout/shell.component.*` (sidebar tools), `shared/layout/pane.component.ts` (header 8, icon, table switch),
  `shared/charts/chart.component.ts` (switch moves to the pane), `shared/widgets/*` (redrawn), `features/analytics/*`,
  `features/hall-of-fame/*`, `features/profile/*`, `features/awards` (detail), `features/privacy`,
  `features/not-found`, `features/admin` (users toolbar), `features/command-centre`.
- Backend template `templates/login.html` and its logo asset (AC-7.6).

### External systems
None.

## 7. Technical decisions

Inherited: ADR-014 and ADR-016 addenda (tokens only, Lucide icons, chart theme), ADR-015 (NgRx per feature,
selection in the URL), Angular Material restyled, sidebar navigation only, the 9.5 page kit.

### Proposed deviations and assumptions

1. **D1 One test run at the end.** Each story is committed on its own branch stacked on the previous one with the
   static checks clean; unit specs, the full gate and the full e2e run once on the 9.6.7 branch, spec fixes are
   committed to the story they belong to, then the seven PRs open in order. Usually every PR has its own green
   gate; here the run on the top branch covers every branch below it.
2. **D2 Widgets keep their keys.** The redrawn widgets keep the `DashboardWidget` keys and the stored layouts, so no
   migration and no change to `dashboard_preferences`.
3. **D3 The table switch belongs to the pane.** `app-pane` gets `icon` and `tableable` inputs and holds the
   chart-or-table state; `app-chart` reads it from the enclosing pane (optional injection) and falls back to the
   chart without a pane.
4. **D4 Neutral change chips everywhere**, including the unit sidebar (the canvas of 2026-11-22 first drew green and
   red there, the later university round chose neutral).
5. **D5 Profile section in the query** (`/profile?section=notifications`), as Reviews and Users keep their
   selection, so `/profile` stays the route of 1.x and its links.

Assumptions: **A1** Profile option A (sections on the left, the chosen one on the right). **A2** «Усі N» expands in
place rather than opening a dialog. **A3** No Compliance tile for the rector (the canvas drew none; the page belongs
to the data protection officer). **A4** The average card of AC-2.1 averages the period's years before the current
one.

## 8. Contract

### 8.1 OpenAPI changes
- None. 9.6.4 first planned `UnitComparisonRow.previousApproved`, but `approved.previous` already carries that count
  on both endpoints, so the contract stays as it is.

### 8.2 Migration outlines
None.

### 8.3 Properties
None.

## 9. Test plan

| AC | Unit (vitest / JUnit) | E2E (Playwright) |
|----|-----------------------|------------------|
| AC-1.1 to AC-1.3 | shell: tools in the sidebar, drawer below 960, signed-out bar | `layout.spec.ts`: no top bar at 1280, drawer tools at 360, no horizontal scroll |
| AC-1.4, AC-1.5 | pane header with icon and switch; chart reads the pane state | `analytics.spec.ts`: switch to table and back on one widget |
| AC-1.6, AC-1.7 | - | `accessibility.spec.ts`: axe contrast on every changed page, both schemes; field label box outside the border |
| AC-2.1 to AC-2.4 | year cards and bars, average, category cards and list | `dashboard.spec.ts` updated: numbers match `GET /analytics/me` |
| AC-3.1 to AC-3.5 | small multiples scales, podium split, comparison rows, paired bars | `analytics.spec.ts` updated: same numbers as the API for a fixed period |
| AC-4.1, AC-4.3 to AC-4.5 | faculty bars and ticks, highlight cap, bento spans | `analytics.spec.ts`: one faculty tile, «Усі N» |
| AC-4.2 | existing `UniversityAnalyticsServiceTest`, `UnitAnalyticsServiceTest`, `UniversityAnalyticsIT`, `UnitAnalyticsIT` (`approved.previous` per row) | - |
| AC-5.1 to AC-5.5 | hero ring share, scoreboard and overtake month, record cards | `hall-of-fame.spec.ts` updated |
| AC-6.1 to AC-6.4 | section from the URL, switches save and refuse the last channel | `profile.spec.ts` updated: section deep link, switch saved after reload |
| AC-7.1 to AC-7.6 | two-column breakpoints, users toolbar, 404 code tile | `award-look.spec.ts`, `award-mobile.spec.ts`, `people.spec.ts`, `privacy-centre.spec.ts`, `command-centre.spec.ts`, `not-found` check in `achievements.spec.ts`; login page logo in `login.spec.ts` and `LoginPageFT` |

Backend: only 9.6.4 changes it (one field and its query).

## 10. Manual verification

Preconditions: the container stack built from the 9.6.7 branch (`docker compose build --pull=false app frontend`,
then `docker compose up -d --no-build`), seed data with the personal seed awards of 9.4.6, accounts `employee.fmi`,
`secretary.fmi`, `dean.fmi`, `rector`, `admin`, `gdpr` (`@chnu.edu.ua`, `Passw0rd-demo`); a fresh account from
self-registration for step 24. Widths 360 and 1280, both schemes, both languages where marked. Each step compares
the screen with the named artboard of the canvas.

1. Sign in as `employee.fmi`, open `http://localhost/dashboard` at 1280 in Ukrainian, light. Expected: no top bar;
   search «Пошук і переходи» at the top of the burgundy sidebar under the logo; «EN», theme, bell and the user with
   «Вийти» at its bottom. (AC-1.1, AC-1.6)
2. Same page at 360. Expected: slim bar with the menu button; the drawer shows the tools at its bottom; no bottom
   bar, no horizontal scroll. (AC-1.2)
3. Look at «Нагороди за роками». Expected: header strip with icon, primary line under it, «Графік / Таблиця» on the
   right; three cards (2026, 2025, average) and thick year bars with numbers. Press «Таблиця». Expected: the same
   numbers as rows; the old «Показати таблицею» is gone. (AC-1.4, AC-1.5, AC-2.1, AC-2.2)
4. Look at «За категоріями». Expected: three cards and a ranked list with bars; numbers match
   `GET /api/v1/analytics/me` `byCategory`. (AC-2.3)
5. Switch to dark with the sidebar theme button and to English. Expected: Aurora colours, gold only as accent,
   labels in English, no reload. (AC-1.6)
6. Sign in as `dean.fmi`, open `/analytics/units` for the faculty. Expected: one small chart per department with
   numbers; podium with three cards and a list; comparison table with bars and neutral chips; highlights as big
   numbers with «було N»; levels and categories as paired bars. (AC-3.1 to AC-3.5)
7. Sign in as `rector`, open `/analytics/university`. Expected: one «Факультети» tile with ticks and «+N», no
   «Динаміка факультетів», no Compliance tile; years and forecast in the main column with «рік триває» (the
   one-column layout of 9.7.12, AC-12.7);
   `GET /api/v1/analytics/university` rows carry `approved.previous`, and each tick sits at that value.
   (AC-4.1, AC-4.2, AC-4.4, AC-4.5)
8. In the highlights panel press «Усі N». Expected: all highlights shown, the button reads «Згорнути». (AC-4.3)
9. Open `/hall-of-fame` at 1280. Expected: burgundy hero with the year's count and the ring; year bars and the
   scoreboard side by side; facts on the ink panel; records as cards. At 360 they stack. (AC-5.1 to AC-5.5)
10. Open `/profile` as `rector`. Expected: banner with initials, role, membership chip and the three facts; sections
    on the left. (AC-6.1, AC-6.2)
11. Choose «Сповіщення», reload. Expected: `?section=notifications` kept; three groups with two switches per event.
    Turn off «Нагороду погоджено» by e-mail, reload. Expected: still off. (AC-6.2, AC-6.3)
12. Turn off the other channel of the same event. Expected: refused with the existing message, the switch back on.
    (AC-6.3)
13. Choose «Ім'я та прізвище». Expected: the field labels sit clear of the border in both schemes. (AC-1.7)
14. Choose «Конфіденційність і мої дані». Expected: links to consents, export and deletion lead to `/privacy`, the
    privacy control panel of 9.7.8: a status line, four tiles in a row and then two columns at 1280. (AC-6.4, AC-7.2)
15. Open an approved award of `employee.fmi`. Expected: facts and journey in two columns at 1280, stacked at 360.
    (AC-7.1)
16. As `secretary.fmi` open `/admin/users` (the administrator holds no role that adds people). Expected: «Додати
    колегу без облікового запису» in the title row, search and filters in a toolbar under it, wrapping at 360.
    (AC-7.4)
17. As `secretary.fmi` open the Command centre. Expected: header 8 on every pane. (AC-7.5)
18. Sign out, open `http://localhost/register`, `/privacy-notice` and `/no-such-page`. Expected: slim top bar with
    logo, language and theme. Open `http://localhost/login` (the backend sign-in page, themed since 9.7.1). Expected:
    its own language switch and the gold logo mark on burgundy, not the cyan one. (AC-1.3, AC-7.6)
19. Open `http://localhost/no-such-page`. Expected: one centred 404 tile with a link home. (AC-7.3)

### Detours

20. Direct URL: open `/profile?section=unknown`. Expected: the first section is chosen, no error.
21. Reload on `/hall-of-fame` with the table form chosen on a widget. Expected: the chart form again (the switch is
    not stored), no console error.
22. Token expiry in an open tab: let the access token expire on `/profile?section=notifications`, flip a switch.
    Expected: the refresh runs and the change is saved; if the refresh fails, the switch goes back and the sign-in
    page opens.
23. Second browser: change a notification switch in browser A, reload `/profile?section=notifications` in browser
    B. Expected: B shows the change.
24. Hidden in state: a widget with no data (fresh account from self-registration with a confirmed employee role, My
    dashboard). Expected: one page-level empty state «Затверджених нагород поки немає» with «Внести нагороду», no
    panes and no «Графік / Таблиця» switch.
25. Hidden in state: university highlights with four or fewer entries (period with few awards). Expected: no
    «Усі N» button.
26. Revoked mid-flow: a fresh account with `RECTOR_SECRETARY` on the university (the seed `rector` keeps its role
    history) is on `/analytics/university`; `admin` removes that role. Expected: the next data request answers 401
    and the sign-in page opens (a revoked role signs the holder out); after signing in, the sidebar has no analytics
    and `/analytics/university` opens the forbidden page; `dean.fmi` keeps unit analytics unchanged.
27. Revoked mid-flow: while `secretary.fmi` holds a delegation from `dean.fmi` and has unit analytics open, the dean
    revokes it. Expected: the next data request answers 401 and the sign-in page opens (a revoke signs the delegate
    out); after signing in again, the department scope only (`GET /analytics/units` lists the departments, the
    faculty export answers 404). Unit analytics reads own roles only, so the delegation never widened it.

### Run 2026-11-26

Compose stack rebuilt from develop 2b7bf38 (app + frontend), default settings; Playwright MCP at 1280/360; fresh accounts mr96.empty2, mr96.rsec; delegation 61 created and revoked. Result: 26 pass, 1 blocked.

| Step | Result | Evidence |
|---|---|---|
| 1 | PASS | step text corrected: no top bar at 1280; burgundy sidebar rgb(90,22,34); «EN», theme, bell (9 unread), Анастасія Працівник + «Вийти» at its bottom (y 788-845); search «Пошук і переходи Ctrl K» sits at the top under the logo since 9.6.1 (ac35d6e), AC-1.1 text aligned |
| 2 | PASS | 360: slim bar 56 px «Облік нагород ЧНУ» with menu button; drawer: nav, then user + EN/theme/bell/«Вийти» at its bottom (y 668-725); no bottom bar, no horizontal scroll |
| 3 | PASS | «Нагороди за роками»: header strip with icon, primary line, «Графік / Таблиця» right; cards 2026 10 / 2025 2 / average 1,5; thick bars with numbers; «Таблиця» rows 2026 10 … 2020 1 = bars = /analytics/me byYear; «Показати таблицею» count 0 |
| 4 | PASS | «За категоріями»: cards №1 10 (53 %), №2 2, №3 2 (11 %) + ranked list of five with bars, 1 each; equals /analytics/me byCategory (10,2,2,1×5) |
| 5 | PASS | sidebar theme menu (Як на пристрої / Світла / Темна) → Темна + «EN»: Aurora (body rgb(6,9,20), sidebar rgb(11,17,36), aurora glow), gold only on bars, avatar and sparkline; «My dashboard», «Awards by year», «By level» in English; in-page marker kept (no reload) |
| 6 | PASS | dean.fmi /analytics/units/9?from=2021&to=2025: small multiple per department with yearly numbers (algebra 61 … faculty level 22); podium 3 cards (7/6/4) + list 4-10; comparison table with bars and neutral chips (+177 % …); highlights big numbers «61 було 22»; levels and categories as paired bars (previous/current) |
| 7 | PASS | step text corrected (9.7.12 AC-12.7: years and forecast sit in the main column, 541 px, not full width): rector /analytics/university (2022-2026, partialYear true): one «Факультети» tile with ticks and «+N» (FMI 413 +336, tick at 77 = 18.6 %; FG 62, tick at previous 43); no «Динаміка факультетів», no Compliance tile; «рік триває» on 2026; API units carry approved.previous |
| 8 | PASS | «Усі 15» (API highlights 15): 4 → 15 entries shown, button reads «Згорнути» |
| 9 | PASS | rector /hall-of-fame 1280: burgundy hero rgb(90,22,34) with 356 and ring 11/12; «Нагороди за роками» and scoreboard «Цей рік проти минулого» side by side (x 292 / 773); «Чи знаєте ви» on ink rgb(43,27,30); «Рекорди» as cards; 360: all tiles x 16 stacked, no horizontal scroll |
| 10 | PASS | rector /profile: burgundy banner with initials РР, «Ректор · ЧНУ», chip «Членство підтверджено», facts address / created 20.11.2026 / last sign-in; five sections on the left |
| 11 | PASS | «Сповіщення» + reload keeps /profile?section=notifications; groups МОЇ НАГОРОДИ / РОЗГЛЯД / РОЛІ ТА ДОРУЧЕННЯ, 24 switches = 12 events × e-mail + in-app; «Е-пошта: Нагороду погоджено» off, after reload aria-checked=false |
| 12 | PASS | «У системі: Нагороду погоджено» off refused: «Кожна подія зберігає принаймні один канал, тому останній увімкнений перемикач вимкнути не можна.»; switch stays on |
| 13 | PASS | «Ім’я та прізвище» light and dark: labels «Ім’я»/«Прізвище» inside the outline notch (notch 29/60 px > label 20/51 px), label bottom 392 above input 397; same geometry in both schemes |
| 14 | PASS | step text corrected (9.7.8 privacy control panel): «Конфіденційність і мої дані» links «Згоди», «Вивантажити мої дані», «Видалити обліковий запис» → /privacy; at 1280 status line, four tiles in a row (x 292/532/773/1013), then two columns «Ваші дані» / «Ваша активність» |
| 15 | PASS | employee.fmi /awards/2366 (approved): 1280 details x292 w565 beside «Award journey» x896; 360 all x16-35 stacked, no horizontal scroll |
| 16 | PASS | step text corrected (account secretary.fmi, label «Додати колегу без облікового запису»; admin holds no people role, canManagePeople): 1280 button in the title row (y 40-84, right 1237) beside «Користувачі», search + Роль/Статус/«Лише непідтверджені» toolbar under it (y 128); 360 button wraps under the title, filters stack at x16, no horizontal scroll |
| 17 | PASS | secretary.fmi /command-centre: «Сьогодні», «Черга розгляду», «Кафедри, що потребують уваги», «Лідери факультету» each with icon, tinted strip rgb(251,244,234) and 2 px burgundy bottom line rgb(122,31,46) (header 8) |
| 18 | PASS | step text corrected (9.7.1 sign-in themes): «Вийти» → /login; /register, /privacy-notice and /no-such-page signed out: 56 px bar with logo «Облік нагород ЧНУ», «EN», «Тема», «Увійти»; backend sign-in page (form action /login) has its own language switch УКР · EN and the gold-bar logo mark on burgundy in the panel footer, no cyan logo |
| 19 | PASS | /no-such-page: one centred tile «404 · Сторінку не знайдено» with «На головну» → / and «Досягнення університету» |
| 20 | PASS | employee.fmi /profile?section=unknown: first section «First and last name» chosen, no console error |
| 21 | PASS | dean.fmi /hall-of-fame: «Нагороди за роками» → «Таблиця» (table 1), reload → chart again («Графік» pressed, table 0), no console error |
| 22 | PASS | rector tab on /profile?section=notifications since 22:02:45 UTC (access token 15 min); at 22:19:26 «Е-пошта: Нагороду погоджено» on → PUT /users/me/notification-preferences 200, on after reload; the token had been refreshed in the background before the click (no 401, no sign-in page) |
| 23 | PASS | employee.fmi browser A: «E-mail: Award rejected» off; browser B (new context) reload /profile?section=notifications: «Е-пошта: Нагороду відхилено» off; switched back on in A |
| 24 | PASS | step text corrected (no-data dashboard is one page-level empty state): fresh employee mr96.empty2 /dashboard: «Затверджених нагород поки немає» with three steps and «Внести нагороду», no panes, «Графік / Таблиця» switches 0; getting-started card 1/5 |
| 25 | ACCEPTED (unit spec) | accepted on `highlights.component.spec` ac4_5_offers_no_toggle_when_the_list_fits; seed data cannot produce ≤ 4 university highlights: every active faculty without awards adds a NO_AWARDS entry (1995: 0 awards, 9 highlights; 2000, 2005, 2011: 9); covered by highlights.component.spec ac4_5_offers_no_toggle_when_the_list_fits |
| 26 | PASS | step text corrected (fresh holder instead of the seed rector; revoke signs out): mr96.rsec RECTOR_SECRETARY on ChNU (role 2079) on /analytics/university; admin DELETE /users/2225/roles/2079 → 204; next request 401, sign-in page; after sign-in no «Аналітика» in the sidebar, /analytics/university → /forbidden; dean.fmi /analytics/units/9 still 200 (147) |
| 27 | PASS | step text corrected (delegated roles do not widen unit analytics, own roles only; revoke signs the delegate out): delegation 61 dean.fmi → secretary.fmi (DEAN, FMI) active, secretary on /analytics/units/9; dean DELETE /delegations/61 → 204; next request 401, sign-in page; after sign-in GET /analytics/units = 5 departments, faculty export 404 |

## 11. Risks

| # | Risk | Mitigation |
|---|------|------------|
| 1 | Seven stories before the deploy and the defense (~2026-12-15) | order by visibility; 9.6.7 then 9.6.6 move after the deploy |
| 2 | One test run at the end finds many failing specs at once | keep data-testids; static checks per story; spec fixes go to their story's branch |
| 3 | Stacked PRs conflict if one changes after review | merge in order; later branches rebase on the merged one |
| 4 | The look drifts from the canvas again (the 9.5 gap) | every AC names its artboard; the UI check compares screenshots with it |

## 12. Definition of Done

- Every AC proven by its unit or e2e test; full gate green (JaCoCo ≥ 85 %, static 0, frontend lint and tests) and
  full e2e green on the 9.6.7 branch.
- UI check of the changed pages at 360 and 1280, light and dark, uk and en, compared with the artboards; axe
  without serious findings.
- `UI_GUIDELINES.md` (widget header, chart rules, shell), CHANGELOG lines,
  tracker and BACKLOG rows.
- The manual run of §10 recorded in this PRD.

## Validation (2026-11-26)

Result: **PASSED WITH NOTES**. Stories 9.6.1 to 9.6.7 merged (#492-#498); manual run of §10 on 2026-11-26: 26 of 27
steps, step 25 blocked (the seed always gives at least nine university highlights) and covered by
`highlights.component.spec` ac4_5_offers_no_toggle_when_the_list_fits. Several 9.6 screens were reworked by Feature 9.7
(one analytics page, spreadsheet tables, sign-in themes, privacy panel); the run checked the merged code and the step
texts follow it.

Suite on `develop` plus the tests of this PR: unit 1 502, IT/FT 646, JaCoCo 98.2 % lines (7 394/7 529),
Checkstyle/PMD/SpotBugs 0; frontend lint, 1 610 unit tests, production build 930 kB; Playwright 283/283: 276 in the full run
on two workers (52 min), the other 7 in a one-worker rerun (four failed on worker crashes and a step timeout under
memory pressure, three were left unrun); `mobile-layout.spec` ac3_1 ac3_2 (reviewers and officers) timed out in the
rerun too and passed alone in 2 min.

| AC | Evidence (all passing) |
|----|------------------------|
| 1.1 | `shell.component.spec` ac1_1_puts_the_tools_and_the_bell_after_the_user_at_the_foot_of_the_sidebar, ac1_1_ac1_2_makes_the_sidebar_the_banner_and_a_phone_the_slim_bar, ac3_4 palette from the toolbar; e2e `layout.spec` ac1_1 ac1_4 (no sideways scroll), `theme.spec` ac1_4 (named icon-only controls), `keyboard.spec` ac1_5 (focus indicator); manual 1 |
| 1.2 | `shell.component.spec` ac1_2_keeps_the_scheme_and_sign_out_in_the_drawer_on_a_phone, ac3_1 no bottom bar; e2e `shell.spec` ac1_2; manual 2 |
| 1.3 | `shell.component.spec` hides the user controls when anonymous, ac2_5_offers_an_anonymous_visitor_to_sign_in; e2e `accessibility.spec` pages without a session; manual 18 |
| 1.4 | `layout.spec` (page kit) ac1_2_pane_names_its_region_after_its_heading; e2e `theme.spec` ac7_1 (Lucide icons, heading font); manual 3, 17 |
| 1.5 | `chart.component.spec` ac2_6_switches_to_the_projected_table_and_back, `trend-widget`/`race-widget`/`year-trend-chart` ac2_6 table views (pane state from `pane.testing`); manual 3, 21 |
| 1.6 | e2e `theme.spec` ac1_1 (Aurora in the dark), ac6 ac1_5 axe in both schemes; manual 1, 5 |
| 1.7 | e2e `layout.spec` 9.6 ac1_7 (label inside the notch and above the input, light and dark; added here); manual 13 |
| 2.1, 2.2, 2.4 | `year-trend-chart.component.spec` ac2_4_summarises_the_last_year_the_year_before_and_the_earlier_average, ac2_4_shows_a_dash_for_the_average_without_earlier_years, ac2_5_draws_one_bar_per_year_newest_on_top, ac2_6_shows_the_same_figures_as_a_table; e2e `dashboard.spec` ac2_4 ac2_5 ac2_11; manual 3 |
| 2.3 | `category-chart.component.spec` ac2_5_shows_the_most_frequent_categories_as_cards, ac4_5_puts_three_cards_and_the_rest_as_bars; manual 4 |
| 3.1 | `unit-charts.spec` ac2_3_ac4_2_draws_one_small_chart_per_department, ac2_4_the_faculty_grid_shows_the_numbers; e2e `unit-analytics.spec` 9.7 ac2_1 ac2_2 ac2_4; manual 6 |
| 3.2 | `top-performers.component.spec` ac3_5_puts_the_first_three_on_a_podium_and_ranks_the_rest, ac2_5_shows_one_person_without_a_position; manual 6 |
| 3.3 | `unit-comparison.component.spec` ac3_3_draws_approved_and_per_100_members_as_bars, ac3_3_shows_every_figure_in_the_full_table, `delta-tiles.component.spec` ac4_2 chips; manual 6 (names wrap, 9.7 D5) |
| 3.4 | `highlights.component.spec` ac2_6_shows_each_highlight_with_its_count_the_previous_one_and_a_sentence; manual 6 |
| 3.5 | `unit-charts.spec` ac2_3_draws_this_period_beside_the_previous_one_with_a_table; manual 6 |
| 4.1 | `faculty-bars.component.spec` ac4_2_sizes_bars_and_ticks, ac4_2_marks_no_change_and_a_new_faculty, ac4_2_draws_no_ticks_or_key_without_a_previous_period; manual 7 |
| 4.2 | `UniversityAnalyticsServiceTest` ac1_3_ac1_4 and `UnitAnalyticsServiceTest` ac1_5 (`approved.previous` per row), `UnitAnalyticsIT` ac1_4, `UniversityAnalyticsIT`; e2e `university-analytics.spec` 9.7 ac12_7 (numbers of the API) |
| 4.3 | `highlights.component.spec` ac4_5_caps_the_list_and_expands_it_in_place, ac4_5_offers_no_toggle_when_the_list_fits; manual 8 (25 blocked) |
| 4.4 | `projection-trend-chart.component.spec` ac4_3_draws_a_column_per_year_with_its_number_and_hatches_the_projected_rest, ac4_3_shows_no_key_when_nothing_is_hatched; manual 7 (main column since 9.7.12) |
| 4.5 | `delta-tiles.component.spec` ac4_1_shows_a_detailed_chip_and_the_sparkline_on_the_bento_tiles, ac4_1_draws_no_sparkline_from_a_single_year; manual 7 |
| 5.1 | `hall-of-fame-hero.component.spec` ac5_1 (count, ring share, chip; added here); manual 9 |
| 5.2 | `trend-widget.component.spec` ac5_2_draws_a_thick_bar_per_year_and_hatches_the_running_year; e2e `hall-of-fame.spec` ac5_2; manual 9 |
| 5.3 | `race-widget.component.spec` ac5_3 (four: score, overtaking month, behind with a gap, level); manual 9 |
| 5.4 | `records-widget.component.spec` ac5_4, `facts.spec` ac3_lists_up_to_four_facts; manual 9 |
| 5.5 | `hall-of-fame.component.spec` ac3_5 layout; e2e `hall-of-fame.spec` ac3_5 (stacks at 360); manual 9 |
| 6.1, 6.2 | `profile.component.spec` ac6_1 banner, ac6_2 (three: names first, section from the address, old anchor); manual 10, 11, 20 |
| 6.3 | `notification-preferences.component.spec` ac3_6 groups, ac6_3 (four: one PUT per switch, last channel refused, stored choices, failed save turns back); e2e `profile.spec` ac3_6 ac6_3; manual 11, 12, 22, 23 |
| 6.4 | `profile.component.spec` ac2_5_links_the_privacy_section_to_the_privacy_centre; manual 14 |
| 7.1 | e2e `layout.spec` 9.6 ac7_1 (added here); manual 15 |
| 7.2 | e2e `privacy-centre.spec` ac2_7 ac8_4 (phone and desktop); manual 14 |
| 7.3 | `not-found.component.spec` ac2_4; e2e `keyboard.spec` ac1_2 (unknown path keeps its address); manual 19 |
| 7.4 | e2e `people.spec` ac5_4 (360 full width); manual 16 |
| 7.5 | e2e `command-centre.spec` ac5_7 (44 px buttons, axe); manual 17 |
| 7.6 | `LoginPageFT` ac1 (brand logo path), e2e `theme.spec` (logo per brand); manual 18 |

Edge cases (§5): zero data as one page-level empty state (`trend-widget`/`race-widget`/`records-widget` ac2_5
empty states, manual 24); a department without awards draws zeros (`unit-charts.spec` ac2_4); one department or
faculty (`top-performers` ac2_5 one person); previous period without data (`faculty-bars` ac4_2 «нове» and
no ticks); long names wrap below 960 (manual 6, 9.7 D5); notification switch with an expired token (manual 22,
`notification-preferences` failed save turns back); theme and language from the sidebar redraw the charts
(`chart.component.spec` ac2_4, e2e `university-analytics.spec` 9.3 ac2_4, manual 5).

Security: no backend logic and no endpoint changed (the only backend file is the sign-in logo SVG). A01 the routes
keep their guards; a revoked role or delegation signs the holder out and the analytics routes refuse afterwards
(manual 26, 27). A02 nothing new. A03 no new `innerHTML` or sanitiser bypass in the 9.6 diff; notification switches
use the existing `PUT /users/me/notification-preferences`. A07 a switch saved after the access token expired goes
through the refresh (manual 22).

Integration: the Compose stack rebuilt from `develop` 2b7bf38 starts clean (manual run); no migration and no
`openapi.yml` change in this feature (§8).

Scenario review: the detours of §10 (20-27) cover the checklist items that apply to a front-end redesign (direct
URL, reload after a choice, token expiry in an open tab, second browser, revoked role and delegation, hidden empty
states); the feature creates nothing, matches no text and schedules nothing, so the lost-create, NFC and cron
items do not apply. No separate refactor sweep: the 9.7 validation sweep of 2026-11-25 covered the same widget and
analytics code (tracker technical notes).

Findings:
1. AC-5.1 (Hall of Fame hero) and AC-1.7, AC-7.1 had no automated test; added in this PR
   (`hall-of-fame-hero.component.spec`, e2e `layout.spec` 9.6 ac1_7 and ac7_1).
2. Accepted deviations from the reviews of #495-#497: the sparkline only on the approved KPI tile (the others have
   no series); the hero ring counts months begun and its chip compares complete months, the scoreboard includes the
   running month; the two Hall of Fame year widgets pair from 960 px; the first profile section is `aria-current`
   before a choice; the last channel is refused on the client before any request.
3. ACs reworded by later stories: AC-1.1 search at the top of the sidebar (9.6.1), AC-3.3 names wrap in the 503 px
   pane (9.7 D5), AC-4.4 years and forecast in the main column (9.7.12 AC-12.7), AC-6.4 and AC-7.6 through the 9.7
   privacy panel and sign-in themes.
4. Step 25 accepted on the unit spec `highlights.component.spec` ac4_5_offers_no_toggle_when_the_list_fits: the seed cannot produce four or fewer university highlights.

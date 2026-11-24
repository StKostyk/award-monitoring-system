# Feature 9.6: Widget and shell redesign

> **Epic**: 9 Production hardening (SCRUM-128) · **Tracker**: `docs/epics/EPIC-09-PRODUCTION-HARDENING-STATUS.md`
> **Sprint**: 11 (2026-11-23 to 2026-11-29) and 12 (2026-11-30 to 2026-12-06) · **Points**: 29
> **Status**: Approved 2026-11-23
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
  the bottom of the sidebar holds search (opens the Ctrl K palette), the language switch, the theme switch, the
  notifications bell with its unread count, and the user's initials with name, role and sign-out; every one is a
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
  by the current count; there is no separate «Динаміка факультетів» tile (W3, A++ S1).
- **AC-4.2** (Previous count in the API) Given `GET /analytics/university` and `GET /analytics/units/{id}`, then each
  row of `units` carries `previousApproved`, the approved awards of that unit in `previousPeriod`, 0 when there were
  none; a unit that did not exist then has 0.
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
None changed. `previousApproved` is computed from `awards` like `approved` (5.2.1, 5.3.1).

### Endpoints
- Changed: `GET /analytics/units/{id}`, `GET /analytics/university` (`UnitComparisonRow.previousApproved`, 9.6.4).
- Used unchanged: `GET /analytics/me`, `GET|PUT /users/me/notification-preferences`, `GET /users/me`.

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
- `components.schemas.UnitComparisonRow.properties.previousApproved`: integer, minimum 0, required,
  `x-status: planned`, description «Approved awards of the unit in `previousPeriod` (9.6.4)». Written in the 9.6.4
  PR, so this PRD's PR stays documentation only.

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
| AC-4.2 | `UnitAnalyticsServiceTest` (previous count); `UniversityAnalyticsIT`/FT: `previousApproved` per row, 0 for a new unit | - |
| AC-5.1 to AC-5.5 | hero ring share, scoreboard and overtake month, record cards | `hall-of-fame.spec.ts` updated |
| AC-6.1 to AC-6.4 | section from the URL, switches save and refuse the last channel | `profile.spec.ts` updated: section deep link, switch saved after reload |
| AC-7.1 to AC-7.6 | two-column breakpoints, users toolbar | `award-detail.spec.ts`, `admin-users.spec.ts`, `not-found` check; login page logo in `auth.spec.ts` |

Backend: only 9.6.4 changes it (one field and its query).

## 10. Manual verification

Preconditions: the container stack built from the 9.6.7 branch (`docker compose build --pull=false app frontend`,
then `docker compose up -d --no-build`), seed data with the personal seed awards of 9.4.6, accounts `employee.fmi`,
`secretary.fmi`, `dean.fmi`, `rector`, `admin`, `gdpr` (`@chnu.edu.ua`, `Passw0rd-demo`); a fresh account from
self-registration for step 24. Widths 360 and 1280, both schemes, both languages where marked. Each step compares
the screen with the named artboard of the canvas.

1. Sign in as `employee.fmi`, open `http://localhost/dashboard` at 1280 in Ukrainian, light. Expected: no top bar;
   search, «EN», theme, bell and the user with «Вийти» at the bottom of the burgundy sidebar. (AC-1.1, AC-1.6)
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
   «Динаміка факультетів», no Compliance tile; years and forecast across the full width with «рік триває»;
   `GET /api/v1/analytics/university` rows carry `previousApproved`. (AC-4.1, AC-4.2, AC-4.4, AC-4.5)
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
14. Choose «Конфіденційність і мої дані». Expected: links to consents, export and deletion lead to `/privacy`, which
    shows two columns at 1280. (AC-6.4, AC-7.2)
15. Open an approved award of `employee.fmi`. Expected: facts and journey in two columns at 1280, stacked at 360.
    (AC-7.1)
16. As `admin` open `/admin/users`. Expected: «Додати людину» in the title row, search and filters in a toolbar
    under it, wrapping at 360. (AC-7.4)
17. As `secretary.fmi` open the Command centre. Expected: header 8 on every pane. (AC-7.5)
18. Sign out, open `http://localhost/login` and the backend sign-in page. Expected: slim top bar with logo,
    language and theme; the sign-in page shows the gold logo. (AC-1.3, AC-7.6)
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
24. Hidden in state: a widget with no data (fresh account from self-registration, My dashboard). Expected: empty
    states in the panes and no «Графік / Таблиця» switch.
25. Hidden in state: university highlights with four or fewer entries (period with few awards). Expected: no
    «Усі N» button.
26. Revoked mid-flow: while `rector` is on `/analytics/university`, `admin` removes the rector role. Expected: the
    next data request answers 403 and the page shows the forbidden state; the sidebar no longer lists the analytics
    after a reload; `dean.fmi` keeps unit analytics unchanged.
27. Revoked mid-flow: while `secretary.fmi` holds a delegation from `dean.fmi` and has unit analytics open, the dean
    revokes it. Expected: the faculty-level data request answers 403 or the department scope only, as in 2.4.

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
- `UI_GUIDELINES.md` (widget header, chart rules, shell), `openapi.yml` (`previousApproved`), CHANGELOG lines,
  tracker and BACKLOG rows.
- The manual run of §10 recorded in this PRD.

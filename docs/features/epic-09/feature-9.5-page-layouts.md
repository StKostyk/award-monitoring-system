# Feature 9.5: Page layouts

> **Epic**: 9 Production hardening (SCRUM-128) · **Tracker**: `docs/epics/EPIC-09-PRODUCTION-HARDENING-STATUS.md`
> **Sprint**: 11 (2026-11-23 to 2026-11-29) and 12 (2026-11-30 to 2026-12-06) · **Points**: 31
> **Status**: Approved 2026-11-20
> **Governing docs**: ADR-014 and ADR-016 (9.3 addenda: tokens, effect components, chart theme), ADR-015 (NgRx per
> feature), `docs/frontend/UI_GUIDELINES.md` §1-8, Feature 9.3 (the look), Feature 9.4 (widget kit, dashboard
> layouts in `dashboard_preferences`, role home), Features 5.1-5.3, 6.x (compliance), 2.3-2.5 (reviews,
> delegations), 1.x (user administration); `openapi.yml` `GET /analytics/me`, `GET /analytics/units/{id}`,
> `GET /analytics/university`, `GET /achievements`, `GET /users`, `GET /users/{id}`, `POST /users/{id}/roles`,
> `GET /reviews`, `POST /awards/{id}/decisions`, `GET|POST /delegations`, `DELETE /delegations/{id}`,
> `GET /compliance/retention-policies`, `GET /compliance/retention-runs`; the page-layout canvases of 2026-11-19/20
> (five directions, winners against runners-up)

## 1. Problem and personas

Features 9.3 and 9.4 gave the application its colours, fonts, icons, motion and two new role dashboards, but the
working pages kept the 5.x and 6.x structure: one long column of cards, tables that open a dialog or a second page
for every row, and filters above everything. After a round of five layout directions on eight pages the chosen
directions are: **C split list and detail** for My dashboard, Users, unit analytics, Compliance, Reviews and My
delegations; **D story feed** for Achievements; **B bento** for university analytics. Navigation stays in the left
sidebar (a top bar was rejected twice).

- **Employee** (Anastasia): opens My dashboard to see her record and the latest awards at a glance, and Achievements
  to see what colleagues and units received this year.
- **Faculty secretary** (Olena): works Reviews daily and confirms new members in Users; wants the request or the
  person next to the list instead of a dialog per row.
- **Dean and rector** (Prof. Martynyuk, Prof. Biloskurskyi): read unit and university analytics, pick a department
  and compare without leaving the page.
- **System administrator and data protection officer**: Users and Compliance; want the status of each section at a
  glance (waiting accounts, open erasure requests, last retention run).

## 2. Scope

In (MVP):
- A page kit on the 9.3 tokens: page header (context line, title, chips, actions), split (list or side column plus
  main), pane, list row, detail pane, bento grid, year groups; phone layouts for each.
- My dashboard (`/dashboard`) in the split layout: profile, KPIs and facts on the side; years, levels, in progress,
  latest awards and the remaining 9.4 widgets in the main column.
- Achievements (`/achievements`, `/units/:id/achievements`, the public pages) as a story feed: All / Staff / Units
  chips, a toolbar, the highlight of the month and the list grouped by years.
- Users (`/admin/users`) as a list with the selected person beside it; `/admin/users/:id` opens the same page with
  that person selected.
- Reviews (`/reviews`) as the queue with the selected request beside it and the decision actions in the pane.
- Unit analytics (`/analytics/units/:id`) in the split layout with the department picker; university analytics
  (`/analytics/university`) as a bento grid.
- Compliance (`/compliance`) with the sections as a vertical list beside the selected section, and the duration of
  the last seven retention runs.
- My delegations (`/delegations`) as given and received lists with the selected delegation beside them.
- The ui-check findings of 2026-11-20 and the 9.4 run: sidebar labels cut off in Ukrainian at 1280, the T1
  annotation clipped at 360, axe `region` on the shell title and a header link.

Out:
- Hall of Fame, Command centre, «Мої нагороди» (`/awards`), award pages, sign-in and error pages: 9.3 and 9.4 layouts
  stay.
- New endpoints, new fields and migrations: every number on the new pages comes from responses that exist (§7 D3);
  the only contract change is two widgets accepted on My dashboard (§8.1).
- A list of decided requests on Reviews (the canvas showed «Вирішені»; it needs a new endpoint): icebox.
- Moving a widget between the side and the main column of My dashboard (§7 D2).
- A top navigation bar or a bottom bar on phones (rejected 2026-11-14 and 2026-11-19).

## 3. Stories

| # | Story | Pts | Parallel | Depends on |
|---|-------|-----|----------|------------|
| 9.5.1 | Page kit and shell fixes | 3 | no | - |
| 9.5.2 | My dashboard in the split layout | 5 | no | 9.5.1 |
| 9.5.3 | Achievements as a story feed | 3 | no | 9.5.1 |
| 9.5.4 | Users with the person beside the list | 5 | no | 9.5.1 |
| 9.5.5 | Reviews with the request beside the queue | 5 | no | 9.5.1 |
| 9.5.6 | Unit analytics split and university bento | 5 | no | 9.5.1 |
| 9.5.7 | Compliance and delegations in the split layout | 5 | no | 9.5.1 |

Order 9.5.1 → 9.5.2 → 9.5.3 → 9.5.4 → 9.5.5 → 9.5.6 → 9.5.7. The demo server (9.2.4, SCRUM-134) follows this
feature; if the time before the defense runs short, 9.5.7 and then 9.5.6 move after the deploy (§11 risk 1).

## 4. Acceptance criteria

### 9.5.1 Page kit and shell fixes (SCRUM-163)

- **AC-1.1** (Page header) Given any page of this feature, when it renders, then the header shows a context line
  (unit, period or counts), the page title as the only `h1`, the page's chips and actions on the right at 1280 and
  wrapped below the title at 360, with no horizontal scroll at 360.
- **AC-1.2** (Split) Given a split page at 1280 or wider, then the list or side column (at most 380 px) and the main
  column sit side by side and scroll with the page; below 960 px they stack, list first; a selected row shows its
  detail in the main column at 960 and wider and opens the detail route (or a full-width pane) below 960.
- **AC-1.3** (List row and selection) Given a list row in a split page, then it is a link or button with a visible
  focus ring, at least 44 px tall, marked `aria-current="true"` when selected; arrow keys move between rows and
  Enter selects.
- **AC-1.4** (Bento) Given a bento page, then the tiles form a four-column grid at 1280, two columns below 960 and one
  column below 600, with tiles that span several columns keeping their order.
- **AC-1.5** (Sidebar labels) Given the Ukrainian interface at 1280, then every sidebar label is shown in full,
  wrapping to a second line where needed («Аналітика підрозділів», «Аналітика університету»), and the active item
  keeps its background on both lines.
- **AC-1.6** (Chart annotations) Given the T1 trend at 360 in English, then the annotation of the last bar («Peak ·
  Year to date») stays inside the chart: placed above the bar and shortened or wrapped, never clipped.
- **AC-1.7** (Landmarks) Given any signed-in page, then the axe scan has no `region` finding: the shell title and the
  header links sit inside the banner or navigation landmarks.

### 9.5.2 My dashboard in the split layout (SCRUM-164)

- **AC-2.1** (Layout) Given an employee at `/dashboard`, then the side column shows the profile (initials, name,
  position and department; not a widget), «Підсумок» (KPI) and the facts (`FACT`), and the main column shows «Нагороди
  за роками» (T1), «За рівнями» (V3) beside «У роботі», the latest awards (`LATEST`), and «За категоріями», «Місце на
  кафедрі» and «Хто бачить мої нагороди» below them; the header shows the period, «Експорт для резюме» and «Додати
  нагороду».
- **AC-2.2** (Latest awards) Given approved awards, then the latest-awards pane lists the five newest (title, issuer,
  date, level chip) with a link to each award and «Усі N» to `/awards`; without approved awards it shows the 9.4
  empty state.
- **AC-2.3** (Layout stored) Given edit mode («Налаштувати»), when the account hides a widget or moves it up or down,
  then it moves only within its column, and the stored `layouts.MY_AWARDS` keeps the 9.4 format (one `order` list,
  one `hidden` list); a widget never changes column.
- **AC-2.4** (Widgets accepted) Given `PUT /users/me/dashboard-preferences` with `FACT` or `LATEST` in
  `layouts.MY_AWARDS.order` or `hidden`, then it is stored; a stored 9.4 order without them shows both after the
  ordered widgets of their column (9.4 AC-1.5).
- **AC-2.5** (Personal facts) Given the account's analytics (`GET /analytics/me`: years, levels, first and latest
  award), then the facts list shows up to four of the 9.4.6 facts that apply to one person (best year, first award,
  streak, level share); with fewer than two approved awards the widget is left out.
- **AC-2.6** (Phone) Given 360 px, then the profile and KPI tiles come first, then facts, years, levels, in progress
  and latest awards in one column, and the header actions wrap below the title.

### 9.5.3 Achievements as a story feed (SCRUM-165)

- **AC-3.1** (Chips and toolbar) Given `/achievements`, then «Усі», «Працівники» and «Підрозділи» chips set the
  `recipient` filter (none, `PERSON`, `UNIT`), and the toolbar holds the search and the unit, level and year filters;
  each filter is kept in the URL and survives a reload.
- **AC-3.2** (Highlight of the month) Given approved achievements dated in the current calendar month (Kyiv), then a
  highlight pane shows the one with the highest level (the newest among equals) with its title, recipient, unit,
  level and date and a link to it; with none this month the pane is left out.
- **AC-3.3** (Year groups) Given the list, then it is grouped by award year, newest first, with a year heading per
  group and the total count in the pane title («906 досягнень»); paging keeps the groups.
- **AC-3.4** (Same data) Given the unit page (`/units/:id/achievements`) and the public pages, then they use the same
  feed with the unit header of 7.x above it; the public pages show only what they show today.

### 9.5.4 Users with the person beside the list (SCRUM-166)

- **AC-4.1** (List and pane) Given an administrator or a secretary at `/admin/users`, then the list shows each person
  with initials, name, role and unit and a status chip, and selecting one shows status, unit, roles, last sign-in,
  history and the actions of the 1.x detail page in the pane beside it; the URL becomes `/admin/users/:id`.
- **AC-4.2** (Header counts) Given the page, then the context line shows the accounts in the caller's scope, those
  waiting for a membership check and the blocked ones, from `GET /users` with `size=1` per status.
- **AC-4.3** (Direct link) Given `/admin/users/:id` opened directly or after a reload, then the page opens with that
  person selected and scrolled into view; an id outside the caller's scope shows the 1.x not-found state in the pane.
- **AC-4.4** (Add a person) Given the page, then «Додати колегу без облікового запису» stays in the header and opens
  the 2.5 dialog; after saving, the new person is selected.
- **AC-4.5** (Actions refresh) Given an action in the pane (confirm membership, reject, assign or revoke a role,
  legal hold), when it succeeds, then the pane and the list row update without a full reload.

### 9.5.5 Reviews with the request beside the queue (SCRUM-167)

- **AC-5.1** (Queue and pane) Given a reviewer at `/reviews`, then the queue (oldest first) shows each request with
  initials, title, recipient, sent date, due or overdue chip and level chip, and selecting one shows the request
  (level, submitted date, overdue, title, recipient, department, issuer, date, documents with their check state) and
  its route in the pane; the URL keeps the selection (`?request=<id>`).
- **AC-5.2** (Decisions in the pane) Given a request the caller may decide, then «Затвердити», «Повернути на
  доопрацювання», «Передати колезі…» and, where allowed, «Передати {level}» are in the pane and use the 2.3 and 2.5
  dialogs and endpoints; after a decision the next request in the queue is selected.
- **AC-5.3** (Tabs as chips) Given the «Мої», «Нерозподілені» and «Усі» chips, then they switch the queue as the 2.3
  tabs did; a request opened by `?request=<id>` that is no longer waiting at the caller's level shows its state and
  no decision actions.
- **AC-5.4** (Keyboard) Given the 4.x review shortcuts, then they act on the selected request, and the 9.3 held
  decision with undo still applies.

### 9.5.6 Unit analytics split and university bento (SCRUM-168)

- **AC-6.1** (Unit split) Given a dean at `/analytics/units/:id`, then the side column shows the delta tiles and the
  department picker («Увесь факультет» first, then each department with its approved count), and the main column the
  years, the most awarded people and the facts; picking a department opens its page in the same layout.
- **AC-6.2** (Period chips) Given the period chips (current year, whole period) and «Експорт», then they keep the 5.2
  period and export behaviour.
- **AC-6.3** (University bento) Given the rector at `/analytics/university`, then the bento shows the KPI tiles, the
  faculties by approved awards (bars, spanning three columns and two rows), the facts and the years (spanning four
  columns), with the 5.3 period chips and export.
- **AC-6.4** (Same numbers) Given the same period, then every number equals the 5.2 and 5.3 page of before for that
  period.

### 9.5.7 Compliance and delegations in the split layout (SCRUM-169)

- **AC-7.1** (Compliance sections) Given the data protection officer at `/compliance`, then the side column lists
  «Зберігання» (policy count), «Запити на видалення» (open count) and «Юридичні утримання» (in force count) as a
  vertical list, and the main column shows the selected section; the section is kept in the URL.
- **AC-7.2** (Retention runs) Given the retention section, then the policies table and a bar chart of the duration of
  the last seven runs (seconds, from `startedAt` and `finishedAt`) are shown, a failed run in the error colour.
- **AC-7.3** (Delegations split) Given `/delegations`, then the side column lists given and received delegations
  with person, role, period and state chip, and selecting one shows the period with days left, the reason, what the
  delegate can and cannot do, and «Відкликати» for a given active one.
- **AC-7.4** (Can and cannot) Given a delegated role, then the «can» and «cannot» lines come from the 2.4 rules of
  that role (no free text), in both languages.

## 5. Edge cases

- Empty lists in a split page: the list pane shows its empty state and the main column a short hint, no blank pane.
- A selected row that disappears after a refresh (request decided by a colleague, person moved out of scope,
  delegation revoked): the selection clears, the main column shows the hint, and a snack bar says why.
- Deep links to a selected item the caller may not see: the pane shows not-found, the list stays usable.
- Narrow screens: no detail beside a list below 960 px; the back action returns to the list with the scroll position
  kept.
- Long Ukrainian labels and names: wrap, never ellipsis on actions; ellipsis only on long award titles in rows, with
  the full title in the pane.
- Analytics cache (5 min): the new pages read the same cached responses; no new invalidation.
- Keyboard: arrow keys in lists must not fight the 4.x review shortcuts (shortcuts only when focus is not in a text
  field).

## 6. Dependencies

### Tables
None new; no migration.

### Endpoints
Existing only: `GET /analytics/me`, `GET /analytics/units/{id}`, `GET /analytics/university`, `GET /achievements`
(`recipient`, `unit`, `level`, `year`, `q`), `GET /users` (`status`, `size=1` for counts), `GET /users/{id}`,
`POST /users/{id}/roles`, `GET /reviews`, `POST /awards/{id}/decisions`, `GET /delegations`,
`DELETE /delegations/{id}`, `GET /compliance/retention-policies`, `GET /compliance/retention-runs`, the erasure and
legal-hold endpoints of 6.x.

### Services
`DashboardWidget` gains `LATEST`; `Dashboard.MY_AWARDS` lists `FACT` and `LATEST` (9.5.2). No other backend change.

### Frontend
- `shared/layout/`: page header, split, pane, list row, bento, year group components (9.5.1).
- Changed: `features/analytics/dashboard`, `features/achievements/achievement-list`, `features/admin/user-list` and
  `user-detail`, `features/reviews/review-list` (and `review-panel`), `features/analytics/units/unit-analytics`,
  `features/analytics/university`, `features/compliance`, `features/delegations/delegation-list`, the shell sidebar,
  the T1 trend widget.
- Data-testids of the e2e specs stay; new ones for panes and rows.

### External systems
None.

## 7. Technical decisions

Inherited: ADR-014 and ADR-016 addenda (tokens only, Lucide icons, effect components, chart theme), ADR-015 (NgRx
per feature; selection state in the feature store and the URL), Angular Material restyled (decision of 2026-11-14,
no component library change), sidebar navigation only.

### Proposed deviations and assumptions

1. **D1 Detail beside the list replaces the user detail page.** `/admin/users/:id` stays as a URL but renders the
   split page with the person selected; on phones it shows the pane alone with a back action. No second component.
2. **D2 My dashboard columns are fixed.** The 9.4 edit mode keeps hide and move, but only within a widget's column;
   the stored format of `layouts.MY_AWARDS` does not change, so no migration and no API change. Dragging across
   columns is out.
3. **D3 No new endpoint or field.** The one contract change is the widget list of My dashboard (§8.1). Header counts come from `GET /users?status=…&size=1` (three calls), the highlight of the
   month from the first page of `GET /achievements` for the current year (client-side pick), the run durations from
   `GET /compliance/retention-runs?size=7`. If a count call fails, the context line leaves that count out.
4. **D4 Reviews selection in the query string** (`?request=<id>`) rather than a child route, so the 4.x shortcuts and
   the held decision of 9.3 keep their route.
5. **D5 Delegations "can/cannot" from a frontend table** keyed by role, matching the 2.4 permission rules; the
   backend stays the authority (a refused action answers 403 as today).
6. **D6 Hall of Fame and Command centre keep the 9.4 layout**; they were not part of the 2026-11-19 round.

## 8. Contract

### 8.1 OpenAPI changes
- `components.schemas.DashboardWidget.enum` gains `LATEST` (`x-status: planned`, description names 9.5.2).
- The `DashboardPreferences` description of `layouts.MY_AWARDS` lists `KPI, FACT, TREND, LEVEL_STAIRS, IN_PROGRESS,
  LATEST, BY_CATEGORY, RANK, VISIBILITY`; a widget of another dashboard still answers 400 `invalid-parameter`
  (9.4 AC-1.4). Written in the 9.5.2 PR together with the enum change, so this PRD's PR stays documentation only.

### 8.2 Migration outlines
None.

### 8.3 Properties
None.

## 9. Test plan

| AC | Unit (vitest) | E2E (Playwright) |
|----|---------------|------------------|
| AC-1.1 to AC-1.4 | page kit components: header wrap, split breakpoints, row selection and keys, bento spans | `layout.spec.ts`: 360 and 1280 on one split page, no horizontal scroll |
| AC-1.5, AC-1.6 | sidebar label rendering; trend annotation placement | screenshot-free checks of label text and annotation box inside the chart |
| AC-1.7 | - | `accessibility.spec.ts`: axe on every page of this feature, no `region` |
| AC-2.1 to AC-2.6 | dashboard column assignment, latest-awards pane, personal facts, edit within a column | `dashboard.spec.ts` updated: panes, edit mode, phone order |
| AC-2.4 | `DashboardTest` (MY_AWARDS widgets); `DashboardPreferencesIT`/FT: `FACT` and `LATEST` stored, `QUEUE` refused | - |
| AC-3.1 to AC-3.4 | chips to `recipient`, highlight pick, year grouping | `achievements.spec.ts` updated: chips, URL filters, highlight shown and absent |
| AC-4.1 to AC-4.5 | selection from the URL, counts line, pane refresh | `admin-users.spec.ts` updated: select, deep link, confirm membership in the pane |
| AC-5.1 to AC-5.4 | selection in the query, next after decision | `review-queue.spec.ts` updated: decide in the pane, decided chip, shortcuts |
| AC-6.1 to AC-6.4 | department picker, bento spans | `analytics.spec.ts` updated: same numbers as before for a fixed period |
| AC-7.1 to AC-7.4 | section from the URL, run durations, can/cannot table | `compliance.spec.ts`, `delegation.spec.ts` updated |

Backend: only 9.5.2 changes it (the widget lists); the full gate runs on each story branch.

## 10. Manual verification

Preconditions: the container stack built from the story branch (`docker compose up -d --build app frontend`), seed
data with the personal seed awards of 9.4.6, accounts `employee.fmi`, `secretary.fmi`, `dean.fmi`, `rector`,
`admin`, `gdpr` (`@chnu.edu.ua`, `Passw0rd-demo`); one fresh self-registered account waiting for a membership check
in the secretary's faculty; one submitted request waiting at the faculty level; one active delegation from the dean
to the secretary. Widths 360 and 1280, both schemes, both languages for the steps marked so.

1. Sign in as `rector`, open `/dashboard` at 1280 in Ukrainian. Expected: header with the page title, the context
   line and the actions on one row; every sidebar label in full, two lines where needed. (AC-1.1, AC-1.5)
2. Same page at 360. Expected: no horizontal scroll; actions below the title; one column. (AC-1.1, AC-2.6)
3. Sign in as `employee.fmi`, open `/dashboard` at 1280. Expected: profile, «Підсумок» and up to four facts on the
   left; years, levels beside «У роботі», latest awards (five, newest first, «Усі N»), then categories, «Місце на
   кафедрі» and «Хто бачить мої нагороди» on the right. (AC-2.1, AC-2.2, AC-2.5)
4. «Налаштувати»: move the facts above «Підсумок», hide «Місце на кафедрі», «Готово», reload. Expected: the change is
   kept; no widget left its column; `GET /users/me/dashboard-preferences` keeps one `order` and one `hidden` list
   with `FACT` in it. (AC-2.3, AC-2.4)
5. Open `/achievements`, press «Підрозділи», reload. Expected: only unit awards; `recipient=UNIT` in the URL and kept
   after the reload; year groups newest first; the count in the pane title. (AC-3.1, AC-3.3)
6. Same page with an approved achievement dated this month (create one through the API if none). Expected: the
   highlight shows the highest level of this month; with none this month the highlight is absent. (AC-3.2)
7. Open `/units/<department id>/achievements` and `/public/achievements` signed out. Expected: the same feed with the
   unit header; the public page shows only public achievements. (AC-3.4)
8. Sign in as `secretary.fmi`, open `/admin/users`. Expected: the context line with accounts, waiting and blocked
   counts; selecting the fresh account shows its status, claimed unit, roles and «Підтвердити членство» beside the
   list; the URL is `/admin/users/<id>`. (AC-4.1, AC-4.2)
9. Confirm the membership in the pane. Expected: the status chip and the roles update in the pane and in the row
   without a reload. (AC-4.5)
10. Reload `/admin/users/<id>`. Expected: the same person selected and in view. (AC-4.3)
11. «Додати колегу без облікового запису», save a new person. Expected: the new person is selected. (AC-4.4)
12. Open `/reviews` as `secretary.fmi`. Expected: the queue oldest first; selecting the waiting request shows its
    details, documents and route beside the queue; `?request=<id>` in the URL. (AC-5.1)
13. «Затвердити» in the pane. Expected: the 2.3 dialog; after it, the next request is selected or the empty hint is
    shown. (AC-5.2)
14. Press «Нерозподілені», then «Усі», then open `/reviews?request=<id of the request just approved>`. Expected: the
    chips switch the queue; the approved request shows its state and no decision actions. (AC-5.3)
15. Sign in as `dean.fmi`, open the faculty's unit analytics. Expected: delta tiles and the department picker on the
    left, years, most awarded and facts on the right; picking a department opens its page in the same layout; the
    numbers match `GET /api/v1/analytics/units/<id>` for the period. (AC-6.1, AC-6.2, AC-6.4)
16. Sign in as `rector`, open `/analytics/university` at 1280, 900 and 360. Expected: four, two and one bento columns;
    the faculties tile spans three columns and two rows at 1280. (AC-6.3, AC-1.4)
17. Sign in as `gdpr`, open `/compliance`. Expected: the three sections with their counts on the left; retention
    selected with the policies and the last seven runs as bars; a failed run in the error colour. (AC-7.1, AC-7.2)
18. Sign in as `dean.fmi`, open `/delegations`, select the delegation to the secretary. Expected: period with days
    left, reason, the can and cannot lines in Ukrainian and in English, «Відкликати». (AC-7.3, AC-7.4)
19. Run the axe scan on every page above at 1280 in both schemes. Expected: no serious finding and no `region`.
    (AC-1.7)
20. Open the trend widget of the Hall of Fame as `dean.fmi` at 360 in English. Expected: the last bar's annotation
    stays inside the chart. (AC-1.6)

### Detours

21. Open `/admin/users/<id of a person in another faculty>` as `secretary.fmi`. Expected: not-found in the pane, the
    list usable.
22. Hidden in state: select an already confirmed person in Users. Expected: no «Підтвердити членство» in the pane;
    `POST /users/<id>/roles` with `updateOrganization` answers 409 `membership-already-confirmed`.
23. Hidden in state: open `/reviews?request=<id>` of a request returned to its submitter. Expected: no decision
    buttons; `POST /awards/<id>/decisions` answers 409.
24. Hidden in state: select a revoked or expired delegation. Expected: no «Відкликати»; `DELETE
    /delegations/<id>` answers 409.
25. Two browsers as `secretary.fmi` and `dean.fmi` with the same request selected; the dean decides it first.
    Expected: the secretary's next refresh clears the selection with a snack bar; deciding from the stale pane
    answers 409 and the queue reloads.
26. Revoked mid-flow: the dean revokes the delegation while the secretary has a delegated request selected in Reviews.
    Expected: the secretary's next action answers 403, the queue reloads without the faculty-level requests;
    colleagues at the same level keep their queue.
27. Revoked mid-flow: the administrator revokes the secretary's role while `/admin/users` is open with a person
    selected. Expected: the next action answers 403 and the page shows the forbidden state; the administrator's own
    page is unchanged.
28. Reload `/reviews?request=<id>` after the token expired. Expected: sign-in, then the same selection.
29. Back after selecting three people in Users. Expected: the browser goes back through the selections, then to the
    previous page; at 360 Back from a pane returns to the list at the same scroll position.
30. Restart the backend while a split page is open, then select another row. Expected: the pane loads after the
    restart; no blank page.

## 11. Risks

| # | Risk | Mitigation |
|---|------|------------|
| 1 | Seven stories before the deploy and the defense (~2026-12-15) | order by visibility (dashboard, achievements first); 9.5.7 then 9.5.6 move after the deploy if needed |
| 2 | E2E specs rely on the old structure | keep data-testids; update specs in the same story; full e2e at each story end |
| 3 | Split pages on tablets (960-1280) feel cramped | list column capped at 380 px; check 1024 in the ui-check of each story |
| 4 | Reviews shortcuts and held decisions break with the pane | selection in the query (D4); the 4.x and 9.3 e2e specs run unchanged |

## 12. Definition of Done

- Every AC proven by its unit or e2e test; full gate green (JaCoCo ≥ 85 %, static 0, frontend lint and tests);
  full e2e green at each story end.
- UI check of the changed pages at 360 and 1280, light and dark, uk and en; axe without serious findings.
- `UI_GUIDELINES.md` §4 (Layout) describes the page kit; CHANGELOG lines; tracker and BACKLOG rows.
- The manual run of §10 recorded in this PRD.

# Feature 5.1: Personal Achievement Dashboard

> **Epic**: 5 — Analytics & Reporting Dashboard (SCRUM-97)
> **Sprint**: 6 (2026-10-19 → 2026-10-25)
> **Points**: 16 (three stories; 13 at kickoff, see deviation 9)
> **Status**: Done 2026-10-23 (validated 2026-10-19; 5.1.4 merged and the manual run of §10 passed 2026-10-23)
> **Author**: Stefan Kostyk
> **Governing docs**: EPIC-05 tracker (decisions of 2026-10-19, deviations 2 and 8, technical notes, risks), US-008, roadmap Feature 5.1, RBAC_matrix §4, AUTHENTICATION_AUTHORIZATION §3.3, PRIVACY_BY_DESIGN §4.2 (`ANALYTICS`), DATA_DICTIONARY §1.1, §1.3, §2.1, §2.2, §4.2, ADR-005, ADR-013, ADR-014, ADR-015, ADR-022 (modules), `Achievements`, `ConsentService`, `PersonalDataAssembler` (format 1.6), `ErasureContributor`, `openapi.yml` (`/achievements`, `/public/achievements`, `/users/me/*`)

## 1. Problem and personas

An employee sees her awards as a list of records with workflow states (`/awards`) and colleagues' shared awards on `/achievements`, but nothing shows how her recognition grew: which years, which levels, which kinds of award, and how she compares with her department. Preparing a contract renewal or an attestation means counting by hand. US-008 asks for a dashboard with a timeline, trends by category and year, a department rank "if enabled", a shareable summary and a choice of widgets.

| Persona | Need in this feature |
|---------|----------------------|
| Anastasia, employee | Sees her approved awards on a timeline and by year, level and category; learns her place in the department only if she agreed to be ranked; prints a summary for an attestation and shares a link to her public awards; hides widgets she does not use |
| New employee without awards | Is shown how to enter and submit the first award instead of empty charts |
| Colleagues in the same department | Count in the department's group size without being named, whatever their consent |
| Faculty secretary, dean | Have own awards too and get the same dashboard; unit analytics come with Feature 5.2 |

## 2. Scope

**In (this feature)**

- `GET /analytics/me`: the caller's approved personal awards in a year range as totals, a timeline, counts by year (split by level), by category and by level, the number of own drafts and pending awards, and the department rank under the `ANALYTICS` consent
- New module `analytics`, permission `analytics:read:own`, a consent lookup exposed by `gdpr`, award aggregates exposed by `award`
- Local and demo seed of approved awards over several years in two departments of ФМІ (tracker risk 2)
- Dashboard at `/dashboard`: period choice, summary tiles, timeline, trend by year, by category, by level, rank card, guidance when there are no approved awards; charts with ngx-echarts (SVG renderer, ARIA description, a table view of every chart)
- Widget choice stored per account (`GET`/`PUT /users/me/dashboard-preferences`, table `dashboard_preferences`), part of the export (format 1.7) and of the erasure
- Printable achievement summary at `/dashboard/summary` and a link to the person's public achievements (`person` filter on `GET /public/achievements`)

**Out (where it goes)**

- Department, faculty and university analytics, period-over-period comparison, Redis cache: Features 5.2 and 5.3
- Server-side PDF, CSV and Excel: 5.2.3 (tracker decision: one PDF library, introduced once); the summary prints to PDF from the browser
- Widget order and drag-and-drop layout: not planned; visibility covers "customise which widgets appear"
- Rank by impact score or by level: not planned; the count of approved awards is the only basis (deviation 4)
- Unit awards the person entered as secretary or dean: not her personal achievements; they appear in unit analytics (5.2)
- User guide chapter "My dashboard": in 5.1.2; it covers the US-008 DoD item on dashboard customisation documentation

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-98 (#285) | 5.1.1 Personal analytics API with a consented department rank | 5 | no | — |
| SCRUM-99 (#286) | 5.1.2 Personal dashboard with charts and widget choice | 8 | yes | 5.1.1 (`/analytics/me`) |
| SCRUM-100 (#287) | 5.1.3 Shareable achievement summary | 3 | no | 5.1.2 (dashboard, period state) |
| SCRUM-108 (#300) | 5.1.4 Dashboard period and failure states (validation fix) | 5 | no | 5.1.3 |

5.1.1 is backend only (with the seed). 5.1.2 has a backend part (preferences, V046, export, erasure) and splits at the green backend gate; its UI can start from the `/analytics/me` stub while 5.1.1 is built. 5.1.3 has a small backend part (`person` filter) and splits likewise.

## 4. Acceptance criteria

Common definitions: an award **counts** when it is `APPROVED`, personal (`recipient_org_id` NULL), owned by the person and its `award_date` year lies in the period. The **period** is `from`..`to` in calendar years (Europe/Kyiv); without `from` it starts at the year of the person's first counted award (the current year when there is none), without `to` it ends at the current year. The person's **department** is `users.organization_id` when that unit is of type `DEPARTMENT`.

### 5.1.1 Personal analytics API with a consented department rank (SCRUM-98)

- **AC-1.1** Given a signed-in account holding any role, when it calls `GET /analytics/me`, then it receives 200 `PersonalAnalytics` for its own awards only; there is no way to name another person. An account without a role (membership not confirmed) receives 403; without a token 401.
- **AC-1.2** Given approved, pending, draft, rejected and unit awards of the caller across several years, when the analytics are read, then `totals.approved`, `totals.verified`, `totals.firstAwardDate`, `totals.latestAwardDate` and `totals.highestLevel` reflect counted awards only, and `inProgress.drafts` and `inProgress.pending` count the caller's own drafts and pending personal awards regardless of the period.
- **AC-1.3** Given counted awards in 2021, 2023 and 2026 and `from=2021`, when the analytics are read, then `byYear` lists every year 2021 to 2026 in order, zeros included, each with `total` and a `byLevel` map of non-zero levels; `byLevel` lists the non-zero levels in `RecognitionLevel` order; `byCategory` lists categories with counts, highest count first, then by English name.
- **AC-1.4** Given counted awards, when the analytics are read, then `timeline` lists them newest award date first with id, both titles, award date, category (with level), awarding organisation and the verification badge; more than 500 counted awards return the newest 500 with `timelineTruncated` true.
- **AC-1.5** Given `from` after `to`, a year outside 1950..2100 or a non-numeric year, when the analytics are read, then 400 `invalid-parameter` names the parameter.
- **AC-1.6** Given the caller's latest `ANALYTICS` row is a grant and the department has at least `app.analytics.min-group-size` (default 5) `ACTIVE` accounts, when the analytics are read, then `rank` is `RANKED` with `position` (competition ranking: equal counts share a position, the next position skips), `groupSize`, `sharedWith` (other members with the same count) and the department; members are every `ACTIVE` account of the department, with or without the consent and with or without awards, ranked by their counted awards in the same period.
- **AC-1.7** Given no `ANALYTICS` grant (never given, or withdrawn), when the analytics are read, then `rank.status` is `CONSENT_REQUIRED` and no position or group size is returned; given the consent but a unit of another type or fewer members than the minimum, `NO_DEPARTMENT` or `GROUP_TOO_SMALL`, again without figures.
- **AC-1.8** Given seeded data of 10 000 approved awards over 200 accounts of one department, when the analytics are read, then the response arrives within 1 s in the IT (US-008 DoD: dashboard under 3 s), with the plan reading the awards of each member through a `user_id` index (`idx_awards_user` or `idx_awards_user_status`), never a sequential scan of `awards` (the small `users` table may be scanned).
- **AC-1.9** Given the `local` or `docker` profile, when the database is migrated, then `R__seed_users_dev_analytics.sql` adds the account `lecturer.fmi@chnu.edu.ua` (Department of Algebra and Informatics, `EMPLOYEE`, `ANALYTICS` granted) with approved awards in at least five years and four levels, eleven colleagues there and six in a second ФМІ department with approved awards 2019–2026, some with `ANALYTICS`; reruns add nothing; the seeded awards are `PRIVATE` except two `PUBLIC` awards of `lecturer.fmi`; `employee.fmi` is unchanged.
- **AC-1.10** Given the new permission, when the docs are read, then `analytics:read:own` (every role) is in `RolePermissions`, AUTHENTICATION_AUTHORIZATION §3.3 and RBAC_matrix §4 ("View Personal Analytics" for every role, deviation 2 of the tracker); ADR-022 lists the `analytics` module and the exposed `gdpr` and `award` types; `ModularityTest` passes.

### 5.1.2 Personal dashboard with charts and widget choice (SCRUM-99)

- **AC-2.1** Given V046, when the schema is inspected, then `dashboard_preferences` (`user_id` PK and FK to `users`, `hidden_widgets` `TEXT[]` not null default `'{}'`, `updated_at`) exists and DATA_DICTIONARY documents it.
- **AC-2.2** Given a signed-in account, when it calls `GET /users/me/dashboard-preferences`, then it receives `{ hiddenWidgets: [] }` until it saved a choice; `PUT` with a list of `DashboardWidget` values stores it (duplicates collapsed) and returns it; an unknown value is 400 `invalid-parameter`; no audit row is written.
- **AC-2.3** Given a saved choice, when the account downloads its data, then the export (format 1.7) carries `dashboard_preferences` (`hidden_widgets`, `updated_at`); when its erasure runs, the row is deleted (user contributor at order 20).
- **AC-2.4** Given an account with `analytics:read:own`, when it opens the navigation, then "My dashboard" leads to `/dashboard`; an account without it gets `/forbidden` on the direct URL and sees no entry.
- **AC-2.5** Given counted awards, when `/dashboard` opens, then it shows the widgets in this order: summary tiles (approved, verified, highest level, first and latest award), the timeline, the trend by year (bars stacked by level), by category (horizontal bars), by level, and the rank card; the period selector ("All years" or a from–to pair of years) reloads them and is kept in the URL (`?from=&to=`).
- **AC-2.6** Given any chart, when it renders, then it uses the SVG renderer with an ARIA description, colours from the theme tokens in light and dark, no animation under `prefers-reduced-motion`, and a "Show as table" switch that shows the same figures as a table; a keyboard user reaches the switch and every timeline entry links to `/awards/{id}`.
- **AC-2.7** Given the rank states, when the rank card renders, then `RANKED` reads "<position> of <groupSize> in <department>" (with "shared with N colleagues" when `sharedWith` > 0); `CONSENT_REQUIRED` explains the consent and links to `/privacy`; `GROUP_TOO_SMALL` and `NO_DEPARTMENT` explain why no rank is shown.
- **AC-2.8** Given no counted awards in the period, when `/dashboard` opens, then the charts give way to guidance: with no counted award at all, the steps to enter and submit the first award with a button to `/awards/new` (when the account holds `award:create`) and, when `inProgress` is non-zero, "N awards are in review" / "N drafts" with a link to `/awards`; with awards outside the chosen period only, "No approved awards in this period" and a button "All years".
- **AC-2.9** Given "Customise", when the account unticks widgets in the dialog and saves, then they disappear, the choice survives a reload and another browser, and hiding every widget leaves a hint with the "Customise" button.
- **AC-2.10** Given 360 px and 1280 px, uk and en, light and dark, when the dashboard is screen-checked, then charts fit without horizontal scroll, axis labels and tooltips are translated, and axe reports no serious violation; ADR-014 has the chart library addendum; the user guide has the "My dashboard" section.
- **AC-2.11** Given the dashboard loads, when measured in the Playwright spec on the local stack with the seed, then it is usable within 3 s.

### 5.1.3 Shareable achievement summary (SCRUM-100)

- **AC-3.1** Given `/dashboard` with a period, when the account chooses "Summary", then `/dashboard/summary?from=&to=` shows a print layout: name, department, period, the totals, counts by level and the list of counted awards (title in the interface language with the other as fallback, awarding organisation, date, level, the verification badge), and the date it was produced.
- **AC-3.2** Given the summary, when it is printed (browser print or "Print"), then the print stylesheet removes the shell, navigation and buttons, keeps A4 portrait margins and avoids breaking an award across pages.
- **AC-3.3** Given `GET /public/achievements?person=<id>`, when it is called, then it lists only that account's approved personal awards with visibility `PUBLIC` (none for a non-`ACTIVE` account and none for an unknown id: an empty page, not 404, so ids cannot be probed); combined with `recipient=UNIT` the page is empty.
- **AC-3.4** Given at least one counted award with visibility `PUBLIC`, when the summary shows, then "Copy link to my public achievements" copies `<origin>/public/achievements?person=<id>` and confirms; without such an award the button is absent and a hint explains that only awards shared publicly appear there.
- **AC-3.5** Given the public page opened with `person`, when it renders, then it lists the person's public awards with the person's name in the heading, and the other filters still apply.

### 5.1.4 Dashboard period and failure states (SCRUM-108)

Added by the validation of 2026-10-19 (§13, scenario review and edge-case check). Frontend, plus one backend unit test (AC-4.6).

- **AC-4.1** Given a year after the current Kyiv year, or `from` after `to`, on `/dashboard` or `/dashboard/summary`, when the page opens, then it shows "The period is not valid" with a way back to all years instead of the load error with Retry; both screens read the period through one shared parser (today `summary.component.ts` imports `yearParameter` from `dashboard.component.ts` and parses again in `readPeriod`).
- **AC-4.2** Given the widget choice failed to load (`preferencesLoadFailed` has no reducer case, so `hidden` stays `[]`), when the account opens "Customise", then saving cannot overwrite the stored choice with an empty list: the dialog is unavailable or reloads the choice first.
- **AC-4.3** Given the check for public awards fails on the summary (today `catchError(() => of(false))`), when it renders, then an error state shows instead of the hint that only publicly shared awards appear.
- **AC-4.4** Given the summary is loading a new period, when it renders, then "Print" is unavailable until the figures of that period are shown.
- **AC-4.5** Given more than 12 categories in the period, when the category chart renders, then the 12 largest are drawn and the rest form one "Other" bar, while the table view lists every category (§5 Accessibility, not implemented in 5.1.2).
- **AC-4.6** Given the clock at 31 December 22:30 UTC (1 January in Kyiv), when the analytics are read without `to`, then the period ends in the new Kyiv year (unit test with a fixed clock).

## 5. Edge cases

- **Period and time zone**: the current year is the Kyiv year; an award dated 31 December counts in that year whatever the server zone. `from` without `to` ends at the current year; `to` before the first award gives an empty period with `byYear` zeros for the requested years.
- **Large ranges**: `from=1950` with no awards before 2020 still lists all years (at most 151 entries); the chart starts at the first non-zero year unless the account chose `from` explicitly.
- **Consent changes**: the rank reads the latest `ANALYTICS` row on every request (no cache), so a withdrawal hides the rank at the next reload; earlier screenshots are outside the system's control (purpose text "Withdrawing it does not affect earlier reports").
- **Department change**: a transfer moves the person into the new department's group at once; awards submitted in the old department still count as hers.
- **Erased and inactive colleagues**: only `ACTIVE` accounts are members; `DELETED`, `LOCKED`, `PENDING` and `MEMORIAL` accounts leave the group, so the group size may drop below the minimum.
- **Small groups**: `groupSize` and `position` are withheld under the minimum to keep a colleague's count from being derived (e.g. 2 members, I am first with 3 awards). The minimum is a property; the IT covers 4 and 5 members.
- **Unit awards and roles**: a dean who entered unit awards sees only her personal ones; the timeline never shows unit awards.
- **Category or level changed after approval**: counts use the current category and level (no history in analytics).
- **Concurrency**: two `PUT` preference calls: last write wins (upsert); no version check, as the value is a whole list.
- **Authorization**: no path or query parameter names a person in `/analytics/me`; `person` on the public endpoint returns only public data; the dashboard route needs `analytics:read:own` and the API checks it again.
- **External**: none; the public endpoint keeps its rate limit and `Cache-Control: no-store`.
- **Accessibility**: charts with many categories (more than 12) group the rest as "Other" in the chart and list all in the table view.

## 6. Dependencies

### Tables

| Table | Change |
|-------|--------|
| `awards`, `award_categories`, `users`, `organizations`, `consent_records` | Read only (5.1.1) |
| `dashboard_preferences` | New, V046 (5.1.2) |
| Seed `db/seed/local/R__seed_users_dev_analytics.sql` | New, local and docker profiles (5.1.1) |

### Endpoints

| Endpoint | State |
|----------|-------|
| `GET /analytics/me` | New (5.1.1), tag `Analytics` |
| `GET`, `PUT /users/me/dashboard-preferences` | New (5.1.2) |
| `GET /users/me/export` | Changed: format 1.7 (5.1.2) |
| `GET /public/achievements` | Changed: `person` filter (5.1.3) |
| `/reports/awards/*` | Unchanged (redesigned in 5.2.1) |

### Services

- `analytics` (new module): `PersonalAnalyticsService`, `AnalyticsProperties` (`app.analytics.min-group-size`), `AnalyticsController`
- `award` `service`: `AwardStatistics` (native SQL: counted awards of one owner by year and level, by category, the timeline, drafts and pending counts, counted awards per member of a department)
- `gdpr` base: `ConsentLookup` (`granted(userId, ConsentType)`; batch form for 5.2)
- `user` `service`: `DashboardPreferenceService`; `RoleErasure` (order 20) also deletes the preferences; `gdpr.PersonalDataAssembler` format 1.7
- `award` `Achievements`: `person` filter on the published query (5.1.3)

### Frontend

- `features/analytics` (new, lazy): `dashboard` page, widget components (`summary-tiles`, `timeline-chart`, `year-trend-chart`, `category-chart`, `level-chart`, `rank-card`, `dashboard-empty`), `customise-dialog`, `summary` print page; NgRx feature `personalAnalytics` (period, data, preferences, load state)
- `shared/charts`: `chart` wrapper (ECharts options from theme tokens, reduced motion, table view)
- `core/layout/shell`: navigation entry; `core/auth`: `analyticsGuard`
- `features/achievements/achievement-list`: `person` query parameter and heading (5.1.3)
- Dependencies: `echarts` 6, `ngx-echarts` 21 (Angular ≥ 21); i18n keys under `analytics.*`

### External systems

None.

## 7. Technical decisions

Inherited: tracker decisions of 2026-10-19 (approved awards only, `ANALYTICS` for naming, SQL on demand, printable summary), ADR-022 (modules and named interfaces), ADR-015 (NgRx per feature), ADR-014 (Material), ADR-013 (Angular, no SSR), ADR-005 (Flyway, Redis available), `Achievements` year bounds 1950..2100.

**Proposed deviations and assumptions**

1. **Chart library: ngx-echarts 21 on ECharts 6** (tracker deviation 8). SVG renderer (crisp in print, scalable, inspectable), built-in ARIA description and decal patterns, tree-shaken imports from `echarts/core` (bar, scatter, grid, tooltip, legend, aria, dataset) in the lazy chunk only. ng2-charts draws on canvas, which gives screen readers nothing and prints blurred; its peer range already requires Angular 22. ADR-014 addendum in 5.1.2.
2. **Personal analytics are not cached.** The queries hit `idx_awards_user_status` and one department grouping; a cache would show an approval or a consent withdrawal minutes late. The Redis cache of the tracker decision starts with unit analytics in 5.2.1.
3. **Rank needs the person's own `ANALYTICS` consent; the group does not.** Being counted in the group size is "counted only in totals" (the consent's purpose text); only the person learns her own position. Reading "if enabled" in US-008 as this consent avoids a second switch.
4. **Rank basis: the count of approved personal awards in the period**, competition ranking. Impact score sums are not a stable measure yet (the score has no organisation modifier, DATA_DICTIONARY §2.1), and a single number is easier to explain at an attestation.
5. **Minimum group size 5** (`app.analytics.min-group-size`) for the rank, the same rule 5.2 is expected to apply to named comparisons (tracker risk 3).
6. **Department = `users.organization_id` of type `DEPARTMENT`**; group = its `ACTIVE` accounts. A person registered at faculty level gets `NO_DEPARTMENT`.
7. **New module `analytics`** depending on `common`, `authz`, `organization`, `user`, `award`, `gdpr`; nothing depends on it. `gdpr` exposes `ConsentLookup` in its base package (like `award`'s `AwardDocuments`) rather than opening its whole `service` package; `award` adds `AwardStatistics` to its `service` interface. ADR-022 table updated in 5.1.1.
8. **Widget choice stored on the server** (`dashboard_preferences`, `user` module) so it follows the account across browsers; it is personal data, so it joins the export and the erasure. Visibility only, fixed order. Not audited: it records a display choice, not a processing decision.
9. **Points**: 5.1.2 goes from 5 to 8 for the preferences backend (migration, endpoint, export, erasure) next to the dashboard UI; the feature totals 16, the epic 49. Keeping 5 would mean storing the choice in the browser only.
10. **Seed under `db/seed/local`** as a repeatable script with `WHERE NOT EXISTS` guards; a new account `lecturer.fmi` carries the demo awards so the e2e specs that rely on `employee.fmi` keep their data. The file is named `R__seed_users_dev_analytics.sql` because Flyway runs repeatable scripts in description order: the name sorts after the organisation, category and dev-user seeds it builds on. The colleagues sit in DAI and DMA (Department of Mathematical Analysis).
11. **Permission `analytics:read:own` for every role** (tracker deviation 2): every role holder can own awards; RBAC_matrix §4 limited it to employees.
12. **Shareable link = public achievements filtered by person** (tracker decision: printable page plus link). Only awards the owner already made `PUBLIC` appear; unknown and inactive ids return an empty page.
13. **Period in calendar years**, not dates: attestations and the trend charts use years; `from`/`to` keep the API and the URL short.

## 8. Contract

### 8.1 OpenAPI stubs

Written to `docs/api/openapi.yml` in this PRD's PR, each with `x-status: planned` and the story in `description`; new tag `Analytics`.

| Operation | Request | Response | Problems |
|-----------|---------|----------|----------|
| `GET /analytics/me` (`getPersonalAnalytics`, 5.1.1) | query `from`, `to` (integer 1950..2100, optional) | 200 `PersonalAnalytics` | 400 `invalid-parameter` (`from`, `to`); 401; 403 |
| `GET /users/me/dashboard-preferences` (`getDashboardPreferences`, 5.1.2) | — | 200 `DashboardPreferences` | 401 |
| `PUT /users/me/dashboard-preferences` (`setDashboardPreferences`, 5.1.2) | `DashboardPreferences` | 200 `DashboardPreferences` | 400 `invalid-parameter` (`hiddenWidgets`); 401 |
| `GET /public/achievements` (changed, 5.1.3) | + `AchievementPersonParam` (`person`, int64) | as today | as today |

Schemas:

- `PersonalAnalytics` { `period` { `from`, `to` }, `totals` { `approved`, `verified`, `highestLevel` (`RecognitionLevel`, nullable), `firstAwardDate`, `latestAwardDate` (date, nullable) }, `inProgress` { `drafts`, `pending` }, `byYear`: [{ `year`, `total`, `byLevel`: map `RecognitionLevel` → integer }], `byCategory`: [{ `category` (`AwardCategoryRef`), `count` }], `byLevel`: [{ `level`, `count` }], `timeline`: `TimelineEntry`[], `timelineTruncated`, `rank` (`PersonalRank`) }
- `TimelineEntry` { `awardId`, `title`, `titleUk`, `awardDate`, `category` (`AwardCategoryRef`), `awardingOrganization`, `verified` }
- `PersonalRank` { `status`: enum `RANKED`, `CONSENT_REQUIRED`, `NO_DEPARTMENT`, `GROUP_TOO_SMALL`; `position`, `groupSize`, `sharedWith` (integer, nullable); `department` (`OrganizationRef`, nullable) }
- `DashboardWidget`: enum `SUMMARY`, `TIMELINE`, `YEAR_TREND`, `BY_CATEGORY`, `BY_LEVEL`, `RANK`
- `DashboardPreferences` { `hiddenWidgets`: `DashboardWidget`[] }
- `AchievementPersonParam`: `person`, query, int64, "Owner of personal awards; only public awards of active accounts (5.1.3)"

### 8.2 Migration outlines

| File | Story | Content |
|------|-------|---------|
| `V046__dashboard_preferences.sql` | 5.1.2 | `dashboard_preferences`: `user_id BIGINT` PK, FK `users(user_id)` ON DELETE CASCADE; `hidden_widgets TEXT[] NOT NULL DEFAULT '{}'`; `updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`; no audit trigger; `GRANT` as the other tables |
| `db/seed/local/R__seed_users_dev_analytics.sql` | 5.1.1 | Accounts (`ACTIVE`, seed password), `EMPLOYEE` roles, `DATA_PROCESSING` and some `ANALYTICS` rows, approved awards with `award_requests` `APPROVED`, guarded by `WHERE NOT EXISTS` on e-mail and on (owner, `title_uk`) |

No index is added: `idx_awards_user_status`, `idx_awards_user_date` and `idx_users_organization` cover the queries (checked by the plan in AC-1.8).

### 8.3 Properties

| Property | Default | Story |
|----------|---------|-------|
| `app.analytics.min-group-size` (`ANALYTICS_MIN_GROUP_SIZE`) | 5 | 5.1.1 |
| `app.analytics.timeline-limit` | 500 | 5.1.1 |

## 9. Test plan

| AC | Unit | IT | FT | E2E / other |
|----|------|----|----|-------------|
| 1.1 | ✓ permission set | | ✓ REST-assured: 200 for each role, 403 without role, 401 | |
| 1.2–1.4 | ✓ period resolution, zero-filled years, ordering, truncation | ✓ `AwardStatistics` against Testcontainers: status, unit awards, other owners excluded; Kyiv year boundary | ✓ shape of the response for the seeded fixture | |
| 1.5 | ✓ validation | | ✓ 400 cases | |
| 1.6, 1.7 | ✓ competition ranking, states | ✓ group of 4 and 5, inactive members out, consent withdrawn | ✓ consent toggle changes the state | |
| 1.8 | | ✓ 10 000 awards, time and `EXPLAIN` | | |
| 1.9 | | ✓ seed applied twice in a `local`-profile IT | | psql in §10 |
| 1.10 | | ✓ `ModularityTest` | | doc review |
| 2.1–2.3 | ✓ service, dedupe, assembler | ✓ migration, upsert, erasure contributor | ✓ GET/PUT, 400, export 1.7 | |
| 2.4–2.9 | ✓ components, store, guard, options builder (tokens, reduced motion), table view | | | ✓ Playwright: `lecturer.fmi` dashboard, period, customise and reload, empty state with a fresh account, rank states |
| 2.10 | | | | screen check of `/dashboard` 360/1280, uk/en, light/dark, axe; doc review |
| 2.11 | | | | ✓ Playwright timing |
| 3.1, 3.2, 3.4, 3.5 | ✓ summary component, copy button, public heading | | | ✓ Playwright: summary, print media emulation, copy link, public page |
| 3.3 | ✓ query | ✓ filter: public only, inactive account, unit combination | ✓ unknown id → empty page | |

Coverage 85 % lines; static clean. Security review for 5.1.1 (new permission, consent read, module boundary) and 5.1.3 (public endpoint filter); 5.1.2 for the migration and the erasure contributor.

## 10. Manual verification

Expected texts quote the English i18n file as merged (checked at the validation of 2026-10-19). Local e2e runs register fresh accounts in the e2e unit, so the local group size of `lecturer.fmi` can be far above the seeded 12; compare positions with psql rather than with fixed numbers.

Preconditions: `docker compose up -d postgres redis mailpit minio clamav`; backend in `backend/` with `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (applies the analytics seed); frontend `npm start` on `http://localhost:4200`. psql `docker compose exec postgres psql -U postgres award_monitoring`; Swagger `http://localhost:8080/swagger-ui.html`. Seed accounts (password `Passw0rd-demo`, `@chnu.edu.ua`): `lecturer.fmi` (seeded awards, `ANALYTICS` granted), `employee.fmi`, `secretary.fmi`, `dean.fmi`, `admin`, `gdpr`. A fresh account E1 is registered in the steps (Department of Algebra and Informatics, verified through Mailpit `http://localhost:8025`, `EMPLOYEE` confirmed by `secretary.fmi`).

1. psql: the `ACTIVE` accounts of the Department of Algebra and Informatics (`organizations.code = 'DAI'`) and the approved awards of `lecturer.fmi` by year. Expected: at least 12 members in DAI; awards in five or more years and four levels; restart the backend and rerun: counts unchanged. (AC-1.9)
2. Sign in as `lecturer.fmi`; Swagger `GET /analytics/me`. Expected: 200; `byYear` from the first award year to 2026 with zeros; `rank.status` `RANKED` with `position`, `groupSize` ≥ 12. (AC-1.1–1.4, 1.6)
3. Swagger `GET /analytics/me?from=2027&to=2021`, `?from=1900`, `?from=abc`. Expected: 400 `invalid-parameter` naming `from`/`to` each time. (AC-1.5)
4. psql: `select status, count(*) from awards where user_id = <lecturer> group by status;` against `totals.approved` and `inProgress`. Expected: only `APPROVED` personal awards counted; drafts and pending match. (AC-1.2)
5. Open `http://localhost:4200`, navigation "My dashboard". Expected: `/dashboard` with summary tiles, timeline, trend by year, by category, by level and the rank card "<n> of <m> in Department of Algebra and Informatics". (AC-2.4, 2.5, 2.7)
6. Choose the period 2022–2024. Expected: the URL shows `?from=2022&to=2024`, all widgets reload with those years only; reload keeps the period. (AC-2.5)
7. On the trend chart switch "Show as table". Expected: a table with the same figures per year and level band (the levels below the university form one band, as in the chart); on the timeline switch "Show as table" with the keyboard and tab through it: each entry focusable and opens `/awards/{id}`; a click on a timeline point opens the award too. (AC-2.6)
8. "Customise" → untick timeline and by level → Save. Expected: both gone; reload: still gone; psql `select hidden_widgets from dashboard_preferences where user_id = <lecturer>;` → `{TIMELINE,BY_LEVEL}`; open the dashboard in a second browser as the same account: same choice. (AC-2.2, 2.9)
9. Untick every widget. Expected: the hint and the "Customise" button only; restore all. (AC-2.9)
10. Download the data from `/privacy`. Expected: format `1.7` with `dashboard_preferences.hidden_widgets` = `["TIMELINE", "BY_LEVEL"]` and its `updated_at`. (AC-2.3)
11. Register E1 and have `secretary.fmi` confirm the membership; open `/dashboard`. Expected: guidance to enter the first award with "Enter an award"; enter and submit one award; reload. Expected: "Awards in review: 1." with a link to `/awards`. (AC-2.8)
12. As `secretary.fmi` approve E1's award; E1 reloads. Expected: the charts with one award; rank card "Your place in the department is shown only with the consent to rankings and comparisons. …" with "Open the privacy centre" linking to `/privacy`. (AC-1.7, 2.7, 2.8)
13. E1 on `/privacy` turns analytics on, back to `/dashboard`. Expected: rank "<n> of <m> in Department of Algebra and Informatics"; with one award E1 may share a position: "Shared with colleagues who have the same number of awards: N". (AC-1.6, 2.7)
14. Choose a period with no award of E1 (e.g. 2019–2020). Expected: "No approved awards in this period" and "All years". (AC-2.8)
15. Screen check of `/dashboard` at 360 and 1280 px, uk/en, light/dark. Expected: no horizontal scroll, translated axes and tooltips, no serious axe finding; with "reduce motion" emulated: no chart animation. (AC-2.6, 2.10)
16. As `lecturer.fmi` choose "Summary" with 2019–2026. Expected: `/dashboard/summary?from=2019&to=2026` with name, department, period, totals, counts by level, the award list and the production date; "Print" preview without the shell, A4 portrait. (AC-3.1, 3.2)
17. "Copy link to my public achievements"; open the link signed out. Expected: confirmation; the public page lists the two public awards of `lecturer.fmi` with the name in the heading. (AC-3.3–3.5)
18. Swagger `GET /public/achievements?person=<employee.fmi id>` and `?person=999999`. Expected: only public awards of `employee.fmi` (or an empty page), and an empty page for the unknown id. (AC-3.3)
19. Run the Playwright spec of 5.1.2 and note the time to a usable dashboard. Expected: under 3 s. (AC-2.11)

### Detours

20. Direct URL `/dashboard` as E1 before the membership is confirmed (in step 11). Expected: no navigation entry, `/forbidden`; `GET /analytics/me` → 403. (AC-1.1, 2.4)
21. Reload `/dashboard/summary?from=2019&to=2026` directly and with `from=abc`. Expected: the same summary; for the bad year the error message "The period is not valid" and a link back to `/dashboard`. (AC-1.5, 3.1)
22. Restart the backend while `/dashboard` is open, then change the period. Expected: one error notice while it is down, the widgets load after it is up; no stale mix of periods. (AC-2.5)
23. Let the access token expire with the dashboard open, then change the period. Expected: silent refresh, data loads; reload after expiry: the dashboard opens without a sign-in. (AC-2.5)
24. Hidden in state, rank: with `ANALYTICS` withdrawn the rank card shows no figures and `GET /analytics/me` returns `rank` without `position`/`groupSize`. (AC-1.7)
25. Hidden in state, copy link: for E1 (no public award) the summary has no "Copy link" button, only the hint; `GET /public/achievements?person=<E1>` returns an empty page. (AC-3.4)
26. Hidden in state, small group: restart the backend with `ANALYTICS_MIN_GROUP_SIZE=50`. Expected: `lecturer.fmi` rank card "Department of Algebra and Informatics has too few members for a ranking that keeps colleagues anonymous."; API `GROUP_TOO_SMALL` without figures; restore. (AC-1.7)
27. Revoked mid-flow, consent: `lecturer.fmi` has `/dashboard` open in tab A, withdraws analytics in tab B. Expected: tab A keeps the rank until its next load; changing the period hides it; colleagues' positions are unchanged (she still counts in the group). (AC-1.6, 1.7)
28. Revoked mid-flow, role: `admin` revokes E1's only role while E1 has `/dashboard` open. Expected: E1 is signed out everywhere (role revocation ends sessions); after signing in again the entry is gone and the URL gives `/forbidden`; the rank of `lecturer.fmi` is unchanged while E1 stays `ACTIVE` (members are accounts, not roles). (AC-1.1, 1.6, 2.4)
29. Department transfer: `admin` moves E1 to another department. Expected: E1's rank now names the new department and its group size; the awards still count as E1's. (AC-1.6)
30. Save the customise dialog twice with the same choice, then from two browsers with different choices. Expected: no error; the last save wins on reload in both. (AC-2.2)
31. Erasure of E1 (with `APP_ERASURE_GRACE_PERIOD=PT10M`, as in Feature 6.1 §10): after completion psql `select * from dashboard_preferences where user_id = <E1>;` → no row; the group size of `lecturer.fmi` drops by one. (AC-2.3, §5)
32. Direct URLs `/dashboard?from=2027`, `/dashboard?from=2026&to=2021` and `/dashboard/summary?from=2027`. Expected after 5.1.4: "The period is not valid." with a way back to all years, no Retry loop; before 5.1.4 the load error with a Retry that cannot succeed. (AC-1.5, 4.1)
33. With DevTools blocking `GET /api/v1/users/me/dashboard-preferences`, open `/dashboard` and "Customise". Expected after 5.1.4: "The widget choice could not be loaded." with Retry, "Customise" disabled until the choice loads; psql `hidden_widgets` unchanged. (AC-2.2, 4.2)
34. With DevTools blocking `GET /api/v1/public/achievements`, open `/dashboard/summary` as `lecturer.fmi`. Expected after 5.1.4: "It could not be checked whether your awards are public. Reload the page to try again.", not the hint that none of the awards is shared publicly, and no copy-link button. (AC-3.4, 4.3)
35. With network throttling, change the summary period and press "Print" at once. Expected after 5.1.4: "Print" waits for the figures of the new period. (AC-3.2, 4.4)
36. `admin` revokes the only role of E1 while E1 keeps `/dashboard` open, then E1 changes the period without reloading. Expected: the revocation ends E1's sessions (as in step 28), so the change ends in a refused token refresh and the sign-in page rather than a 403 with the old token; after signing in again `GET /analytics/me` answers 403 and `/dashboard` gives `/forbidden`. (AC-1.1, 2.4)
37. Stop Redis (`docker compose stop redis`), call `GET /public/achievements?person=<lecturer.fmi id>` signed out, and open `/dashboard`. Expected: both answer (the rate limit fails open without Redis, personal analytics are uncached); start Redis again. (§5 External)

### Run 2026-10-23

container stack from 1a1da70 (develop after PR #301), defaults except step 26 ANALYTICS_MIN_GROUP_SIZE=5000 and step 31 APP_ERASURE_GRACE_PERIOD=PT10M, WARNING_BEFORE=PT5M, CRON every minute; restored afterwards. Result: 37 pass.

| Step | Result | Evidence |
|---|---|---|
| 1 | PASS | DAI 1121 ACTIVE (e2e pile-up); lecturer.fmi 9 approved over 8 years 2019–2026, 6 levels; after the backend restarts counts unchanged (9; same years) |
| 2 | PASS | 200; byYear 2019-2026 zeros filled; rank RANKED position 2 of 1121 |
| 3 | PASS | 3x 400 invalid-parameter; parameter field names from (1900, abc) |
| 4 | PASS | psql: 9 APPROVED, 0 other; totals.approved 9, inProgress 0/0 |
| 5 | PASS | /dashboard (uk UI): tiles 9/3, timeline, trend, category, level, rank card '2 з 1121 на кафедрі «Кафедра алгебри та інформатики»' |
| 6 | PASS | period 2022-2024 -> ?from=2022&to=2024, approved 4, rank 1 of 1121; reload keeps period |
| 7 | PASS | trend table 2022:1, 2023:2 (band + national), 2024:1 matches chart; timeline table via keyboard Space/Tab/Enter -> /awards/987; point click -> /awards/987 |
| 8 | PASS | untick timeline+by level, save: gone, reload keeps, psql {TIMELINE,BY_LEVEL}, second browser same |
| 9 | PASS | all unticked: hint 'Усі віджети приховано' + Налаштувати (period selects stay in toolbar); restore all ok |
| 10 | PASS | export via GET /users/me/export: dashboard_preferences hidden_widgets [TIMELINE,BY_LEVEL] + updated_at |
| 11 | PASS | E1=e1.dash51 (user 1314, DAI, EMPLOYEE granted by secretary.fmi API); E2 (e2.dash51) empty state with 'Внести нагороду'; E1 after submit 1333: 'Нагороди на розгляді: 1' link /awards |
| 12 | PASS | after APPROVE (secretary.fmi, 1333): 1 award tile, rank card consent text + 'Відкрити центр конфіденційності' -> /privacy |
| 13 | PASS | consent on in /privacy: rank 65 of 1123, 'Розділяєте місце…: 77'; psql 2026 period: 64 with more, 78 equal -> 65 / 77 ✓ |
| 14 | PASS | E1 ?from=2019&to=2020: 'У цьому періоді немає затверджених нагород' + 'Усі роки' |
| 15 | PASS | check.cjs /dashboard + /summary: 360/1280, uk/en, light/dark: no h-scroll, axe serious 0, console 0; reduced-motion: bars full at first paint, without: animate (0 -> 6 -> 206) |
| 16 | PASS | lecturer.fmi /dashboard/summary?from=2019&to=2026: name, DAI, period 2019–2026, 9/3, six levels, award list, 'Сформовано: 23.10.2026'; print media hides sidenav/toolbar/buttons; @page A4 portrait |
| 17 | PASS | copy button -> status 'Посилання на ваші публічні досягнення скопійовано.'; link /public/achievements?person=966 signed out: heading 'Досягнення: Оксана Викладач', 2 public awards |
| 18 | PASS | person=8: 2 awards (76, 75, both PUBLIC of 47 approved, recipient Анастасія Працівник); person=999999: empty page 200 |
| 19 | PASS | dashboard.spec 'opens from the navigation with every widget in order within 3 s' green (asserts < 3000 ms, 8.2 s whole test); first attempt had failed on my leftover hidden widgets, restored |
| 20 | PASS | E1 before role: no nav entry, /dashboard -> /forbidden, GET /analytics/me 403 |
| 21 | PASS | summary reload ?from=2019&to=2026 same summary; ?from=abc: 'Період некоректний.' + link 'До панелі' -> /dashboard |
| 22 | PASS | app stopped, period -> 2021: progress bar over the previous figures until nginx 504 after 60 s, then one notice 'Не вдалося завантажити дашборд.' + Retry; app up: change -> 401, silent refresh 200, 2022: 6, 2022–2024: 4; no mixed periods after load (standalone Playwright script, MCP disconnected) |
| 23 | PASS | lecturer tab idle > 15 min (TTL 15m): period 2020 -> analytics/me 200 (renewed by the token_expires timer, no 401), 6 awards; reload opens /dashboard?from=2020&to=2024 without sign-in |
| 24 | PASS | E1 ANALYTICS withdrawn: rank status CONSENT_REQUIRED, position/groupSize/sharedWith null; card shows consent text, no figures |
| 25 | PASS | E1 /dashboard/summary: no copy-link button, hint 'На публічній сторінці з’являються лише нагороди…'; GET /public/achievements?person=1314 empty page |
| 26 | PASS | ANALYTICS_MIN_GROUP_SIZE=5000 (DAI has 1136 members locally, 50 would not trigger): API GROUP_TOO_SMALL, position/groupSize null; card 'На кафедрі «Кафедра алгебри та інформатики» замало працівників для рейтингу…'; restore at finish |
| 27 | PASS | lecturer tab A '2 з 1136', withdrawal in browser B: A keeps '2 з 1136' until reload; period 2022 -> consent text, no figures; colleague e2.dash51 142 of 1136 (shared 994) before and after; consent re-granted |
| 28 | PASS | admin revoked E1's only role (1186, 204) at 15:25: sessions ended (refresh 400, reload -> /login); after sign-in no nav entry, /dashboard -> /forbidden, API 403; E1 stays ACTIVE in DMA, DMA count 7 unchanged |
| 29 | PASS | admin moves E1 to DMA (66) with fromOrganizationId 64: E1 rank 1 of 7 in Department of Mathematical Analysis, 1 award kept; lecturer group 1137 -> 1136 |
| 30 | PASS | API: same choice twice 200; UI: tab A saves TIMELINE twice then BY_CATEGORY, browser B saves BY_LEVEL: no error; both reload without 'За рівнями', psql {BY_LEVEL}; restored {} |
| 31 | PASS | E1 erasure (request 59, PT10M, minute cron) COMPLETED 12:39:58Z; dashboard_preferences rows for 1314: 1 -> 0; E1 had moved to DMA in step 29, so DMA ACTIVE 7 -> 6 (lecturer group not affected) |
| 32 | PASS | /dashboard?from=2027 and ?from=2026&to=2021: 'Період некоректний.' + 'Усі роки' button, no Retry; /dashboard/summary?from=2027: message + 'До панелі' -> /dashboard |
| 33 | PASS | prefs request aborted: 'Не вдалося завантажити вибір віджетів.' + 'Спробувати ще раз', Налаштувати disabled; psql hidden_widgets {} unchanged |
| 34 | PASS | public/achievements aborted: 'Не вдалося перевірити, чи є ваші нагороди публічними. Оновіть сторінку…', no hint of 'none public', no copy-link button |
| 35 | PASS | analytics/me delayed 3 s, SPA period change 2019–2026 -> 2022–2024 (summary has no period control; changed via URL): Друкувати disabled, no print call while loading; then period 2022–2024, 4 awards |
| 36 | PASS | step text corrected: revocation ends the session, so the period change triggers a refused refresh (400) and sign-in instead of a 403 with the old token; after sign-in GET /analytics/me 403 and /forbidden |
| 37 | PASS | redis stopped: GET /public/achievements?person=966 signed out 200; /dashboard as lecturer.fmi loads (9 approved, 2 of 1136), analytics/me 200; redis started |

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| ECharts adds weight to the bundle and trips the budget | Medium | Low | Tree-shaken `echarts/core` imports in the lazy `analytics` chunk; budget checked in the 5.1.2 build |
| A rank still lets a colleague's count be guessed in a small department | Low | Medium | Minimum group size, position only, no names; the minimum is a property |
| Seed changes break existing e2e specs | Medium | Medium | Seed only new accounts; awards `PRIVATE` except two; full e2e run in 5.1.1 |
| Chart accessibility falls short of WCAG 2.1 AA | Medium | Medium | Table view per chart, ARIA description, decal patterns, axe in the screen check |
| 5.1.2 at 8 points overruns the sprint | Medium | Low | Widget choice can drop to the browser (deviation 9) without changing the API of 5.1.1 |

## 12. Definition of Done

- Every AC proven at the layers of §9; `.\tools\gate.ps1` green per story (unit, IT, FT, JaCoCo ≥ 85 %, static 0, frontend lint and tests); Playwright for 5.1.2 and 5.1.3
- Screen check of `/dashboard`, the customise dialog, `/dashboard/summary` (screen and print) and the public page with `person`
- Security review for 5.1.1, 5.1.2 and 5.1.3
- `openapi.yml` without `x-status: planned` on the four operations; DATA_DICTIONARY (`dashboard_preferences`, export format); AUTHENTICATION_AUTHORIZATION §3.3; RBAC_matrix §4; ADR-014 addendum; ADR-022 module table; user guide "My dashboard"; CHANGELOG `[Unreleased]`; tracker and BACKLOG
- Manual verification (§10) run and recorded; feature validated

## 13. Validation (2026-10-19, `develop` at 3f5262c)

PRs #297, #298, #299 merged. Result: **passed with notes**, pending 5.1.4 (SCRUM-108) and the manual run of §10. The scenario review found three defects in the failure paths of the UI, none in the API: a period the server refuses but the screens accept, a widget-choice save after a failed load, and a failed public-awards check shown as "none shared"; The edge-case check found the "Other" bar of §5 missing and no test of the Kyiv New Year. 5.1.4 covers all of them (AC-4.1–4.6), and §10 gains detours 32–37.

| Layer | Result |
|-------|--------|
| Backend unit / IT + FT | 1182 / 468 on `develop`, all green (`gate.ps1 -Full`, 2026-10-19 15:13) |
| Coverage, static | 98.7 % lines (5132/5200); Checkstyle, PMD, SpotBugs 0 |
| Frontend | lint clean, 822 tests, initial bundle 952.99 kB |
| Integration | Containers built from a tree equal to `develop` (`git diff 42b7ff9 3f5262c` empty), healthy; Flyway at V046; `/v3/api-docs` lists `GET /analytics/me` (`from`, `to`), `GET`/`PUT /users/me/dashboard-preferences` and `person` on `GET /public/achievements`, as in `openapi.yml`; the three operations still `x-status: planned` belong to 5.2 and 5.3 |
| Playwright | Full run 2026-10-19: 102 of 109; `dashboard.spec` 7/8 in the run and 8/8 alone, with `awards.spec:231` green alone too (sign-in redirect timing under 2 workers). The other five are the known `auth.spec:85` (local seed), `reviews.spec:174/387`, `notifications.spec:162`, `overdue-notice.spec:62`, none in this feature |
| Security | Reviews of #297, #298 (migration, erasure contributor) and #299 (public filter): no finding of medium or higher |

### AC evidence

| AC | Unit | IT / FT | E2E |
|----|------|---------|-----|
| 1.1 | `RolePermissionsTest.ac14_*` | `PersonalAnalyticsFT.ac1_1_everyRoleReadsItsOwnAnalytics`, `ac1_1_anAccountWithoutARoleOrTokenIsRefused` | `dashboard.spec` ac2_4 (no role) |
| 1.2 | `PersonalAnalyticsServiceTest.ac1_2_*` | `AwardStatisticsIT.ac1_2_onlyApprovedPersonalAwardsInThePeriodCount`, `PersonalAnalyticsFT.ac1_2_*` | |
| 1.3 | `AwardCountsHelperTest.ac1_3_*` (3), `PersonalAnalyticsServiceTest.ac1_3_*` (2) | `AwardStatisticsIT.ac1_3_countsAreGroupedByYearAndCategory` | |
| 1.4 | `PersonalAnalyticsServiceTest.ac1_4_theTimelineIsCutAtTheLimit` | `AwardStatisticsIT.ac1_4_*`, `OpenApiContractTest.ac1_4_ac1_6_*` | |
| 1.5 | `PersonalAnalyticsServiceTest.ac1_5_*` | `PersonalAnalyticsFT.ac1_5_invalidYearsNameTheParameter` | `dashboard.spec` ac3_1 ac3_4 (invalid period) |
| 1.6 | `PersonalAnalyticsServiceTest.ac1_6_competitionRankingSharesThePositionOfEqualCounts` | `AwardStatisticsIT.ac1_6_activeMembersWithoutAwardsCountAndInactiveOnesDoNot`, `PersonalAnalyticsFT.ac1_6_ac1_7_theRankFollowsTheConsent` | |
| 1.7 | `PersonalAnalyticsServiceTest.ac1_7_*` (4) | `PersonalAnalyticsFT.ac1_6_ac1_7_*` | |
| 1.8 | | `AwardStatisticsIT.ac1_8_tenThousandAwardsOfTwoHundredAccountsAnswerWithinASecond` (time and plan) | |
| 1.9 | | `DevSeedIT.ac1_9_theAnalyticsSeedFillsTwoDepartmentsOnceAndLeavesEmployeeFmiAlone` | psql in §10 step 1 |
| 1.10 | `RolePermissionsTest` | `ModularityTest.ac21_*` | doc review ✓ |
| 2.1 | | `DashboardPreferenceQueriesIT.ac2_1_*` | |
| 2.2 | `DashboardPreferenceServiceTest.ac2_2_*` (6) | `DashboardPreferenceQueriesIT.ac2_2_*` (2), `DashboardPreferenceFT.ac2_2_*` (5) | |
| 2.3 | `PersonalDataAssemblerTest.ac2_3_*` | `DataExportFT` (format 1.7), `ErasureIT.ac5_1_*` (row deleted, counted), `DashboardPreferenceQueriesIT.ac2_3_*` | |
| 2.4 | `auth.guard.spec`, `permissions.spec`, `shell.component.spec` ac2_4 | | `dashboard.spec` ac2_4 (2) |
| 2.5 | `dashboard.component.spec` (5), `analytics.effects.spec` (3), `analytics.service.spec` (2), chart specs (4) | | `dashboard.spec` ac2_5 (2) |
| 2.6 | `chart.component.spec` (3), `chart-theme.service.spec` (3), `timeline-chart.component.spec` (4), `year-trend-chart.component.spec` | | `dashboard.spec` ac2_6 |
| 2.7 | `rank-card.component.spec` (4) | | |
| 2.8 | `dashboard-empty.component.spec` (3), `dashboard.component.spec` | | `dashboard.spec` ac2_5 ac2_8, ac2_8 ac2_9 |
| 2.9 | `customise-dialog.component.spec`, `dashboard.component.spec` (2), `analytics.effects.spec` (3) | | `dashboard.spec` ac2_8 ac2_9 (two browsers) |
| 2.10 | | | `dashboard.spec` ac2_10 (360 px, dark, en, axe); screen check in #298 and #299; doc review ✓ |
| 2.11 | | | `dashboard.spec` ac2_4 ac2_5 ac2_11 (under 3 s) |
| 3.1 | `summary.component.spec` (6) | | `dashboard.spec` ac3_1 ac3_2 ac3_4 ac3_5, ac3_1 ac3_4 |
| 3.2 | `summary.component.spec` | | `dashboard.spec` ac3_1 ac3_2 … (print media) |
| 3.3 | | `AchievementsFT.ac3_3_*` (2), `PublicAchievementEndpointsTest.ac3_3_*` | |
| 3.4 | `summary.component.spec` (2) | | `dashboard.spec` ac3_1 ac3_2 ac3_4 ac3_5, ac3_1 ac3_4 (hint) |
| 3.5 | `achievement-list.component.spec` ac3_5 (4) | | `dashboard.spec` ac3_1 ac3_2 ac3_4 ac3_5 |

### Edge cases (§5)

| Edge case | Covered by |
|-----------|------------|
| Kyiv year, `from` without `to`, `to` before the first award | `PersonalAnalyticsServiceTest.ac1_3_*` (clock fixed in `Europe/Kyiv`); 31 December 22:30 UTC: `ac4_6_onNewYearsEveInUtcThePeriodEndsInTheNewKyivYear`; frontend `analytics-period.spec.ts` `ac4_1_takes_the_current_year_in_kyiv` |
| Large ranges | `AwardCountsHelperTest.ac1_3_everyYearOfThePeriodIsListedWithItsLevels` |
| Consent changes | `PersonalAnalyticsFT.ac1_6_ac1_7_*`; §10 steps 24, 27 |
| Department change | §10 step 29 (manual) |
| Erased and inactive colleagues | `AwardStatisticsIT.ac1_6_*`, `PersonalAnalyticsServiceTest.ac1_7_anAccountThatIsNoLongerActiveHasNoRank`; §10 step 31 |
| Small groups | `PersonalAnalyticsServiceTest.ac1_7_aSmallGroupHasNoFigures`; §10 step 26 |
| Unit awards | `AwardStatisticsIT.ac1_2_*` |
| Category or level changed | no test; counts join `award_categories` at query time (`AwardStatistics`), so the current category and level count |
| Concurrency of `PUT` | `DashboardPreferenceQueriesIT.ac2_2_aChoiceIsInsertedThenReplacedByTheLastWrite`; §10 step 30 |
| Authorization | `PersonalAnalyticsFT.ac1_1_*`, `AchievementsFT.ac3_3_*`, `dashboard.spec` ac2_4 |
| External (rate limit, `no-store`) | `PublicAchievementEndpointsTest.ac2_1_*`, `AchievementsFT.ac2_3_*`; Redis down fails open (`FixedWindowCounter`), §10 step 37 |
| More than 12 categories | `category-chart.component.spec.ts` `ac4_5_draws_the_twelve_largest_categories_and_one_other_bar` (5.1.4) |

### Security (OWASP)

- **A01**: `GET /analytics/me` needs `analytics:read:own` (`@access.require`) and reads the token subject only, with no person parameter. The preferences endpoints are `isAuthenticated()` on the caller's own row. `person` on the public list only narrows the published query (`PUBLIC`, `APPROVED`, personal, owner `ACTIVE`); unknown ids return an empty page.
- **A02**: no new secrets or crypto. The rank exposes no colleague's identity, and no figures below `app.analytics.min-group-size`.
- **A03**: `AwardStatistics` uses `NamedParameterJdbcTemplate` with bound `:userId`, `:from`, `:to` (the only concatenation is the constant `COUNTED`). The `DashboardWidget` enum rejects unknown values with 400.
- **A07**: no change to authentication. The rate limit of the public endpoint applies to the `person` queries too.

### Findings

1. Defects in the failure paths, the missing "Other" bar and the untested Kyiv New Year: → 5.1.4 (SCRUM-108), AC-4.1–4.6.
2. Refactor sweep, worth a PR:
   - The category and level charts duplicate the horizontal-bar option and height; a `horizontalBarOption`/`barChartHeight` helper in `shared/charts/chart-style.ts` comes with 5.1.4, since AC-4.5 changes the category chart.
   - The period parsing is shared through `dashboard.component.ts`; it moves with AC-4.1.
   - The other notes are in the tracker's technical notes.
3. The analytics FTs repeat the `like 'prefix%'` teardown seen in about ten FTs; this becomes a repo-wide chore, not part of this feature.

### Close-out (2026-10-23)

5.1.4 (SCRUM-108, PR #301) merged; the manual run of §10 passed all 37 steps by hand (step 19 through the timed `dashboard.spec` test), so no step rests on the ITs alone. All four stories Done in Jira and GitHub. Carried as notes, not blockers: step 22 (with the backend stopped the dashboard keeps the previous period's figures for about 60 s until the proxy answers 504) and the department rank counting members without the consent, both for the Epic 5 review.

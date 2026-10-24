# Feature 5.2: Faculty Performance Analytics

> **Epic**: 5 — Analytics & Reporting Dashboard (SCRUM-97)
> **Sprint**: 6–7 (2026-10-23 → 2026-11-01; 5.2.1 and 5.2.2 in sprint 6, 5.2.3 in sprint 7)
> **Points**: 18 (three stories; 15 at kickoff, see deviation 12)
> **Status**: Done 2026-10-24 (validated with notes, §13; manual run of §10 35/35)
> **Author**: Stefan Kostyk
> **Governing docs**: EPIC-05 tracker (decisions of 2026-10-19, deviations 2, 3, 5 and 6, technical notes, risks 3 and 4), US-009, roadmap Feature 5.2, RBAC_matrix §4, AUTHENTICATION_AUTHORIZATION §3.3, PRIVACY_BY_DESIGN §4.2 (`ANALYTICS`), DATA_DICTIONARY §1.3, §2.1, §2.2, §4.1, Appendix C, ADR-005, ADR-014, ADR-015, ADR-022, PRD 5.1 (period, rank, chart wrapper, seed), `AccessScope`, `OrganizationTree`, `AwardStatistics`, `ConsentLookup`, `ActorDirectory`, `openapi.yml` (`/analytics/me`, `/reports/awards/*`)

## 1. Problem and personas

A dean reads every award of the faculty one by one in the review queue and the award list, but cannot see how the faculty's recognition develops: which departments grow, which fall behind, which kinds of award and which awarding bodies dominate, and who the most recognised people are. Reports for the rector's office are counted by hand. US-009 asks for trends across the departments, a comparison between them, patterns by category and source, top performers and potential support needs, a date filter with a comparison to the previous period, and exports for university presentations.

| Persona | Need in this feature |
|---------|----------------------|
| Prof. Martynyuk, dean | Opens the faculty, compares its departments over a period and against the previous one, sees categories, levels and awarding bodies, the top performers who agreed to be named and the departments that need attention; exports the report as PDF, Excel or CSV |
| Alina, faculty secretary | Opens each department of her scope with the same figures, to prepare reports for the dean |
| Rector and rector's secretary | Open any faculty or department; the university view comes with Feature 5.3 |
| Employees of the faculty | Are named in top-performer lists only with the `ANALYTICS` consent; otherwise counted in totals only |
| GDPR officer, administrator | No unit analytics (tracker deviation 2) |

## 2. Scope

**In (this feature)**

- `GET /analytics/units` (the units the caller may analyse) and `GET /analytics/units/{id}` (one faculty or department over a period of calendar years, compared with the previous period of the same length): totals, counts by year, a comparison of the child departments, counts by category, level and awarding organisation, top performers under the `ANALYTICS` consent, and rule-based highlights
- Permissions `analytics:read:department` and `analytics:read:faculty`, evaluated on the caller's held role scopes
- Redis cache of the unit aggregates (first use of Spring Cache), names and consent resolved per request
- `vw_award_statistics` removed; `/reports/awards/*` drafts replaced in `openapi.yml` (tracker deviations 3 and 6)
- Seed: unit awards and awarding-organisation spellings in ФМІ
- Page `/analytics/units/{id}` with a unit and period choice, tiles with deltas, charts with table views, the department comparison table, top performers and highlights; navigation entry "Unit analytics"
- Export of the same report as CSV, Excel and PDF (`GET /analytics/units/{id}/export`), audited
- User guide "Unit analytics" with the interpretation of each figure (US-009 DoD "analytics guide, interpretation guide")

**Out (where it goes)**

- University level, comparison of faculties, trend projection, compliance summary: Feature 5.3 (5.3.1, 5.3.2)
- Board report with charts: 5.3.3 reuses the PDF component of 5.2.3
- Statistical significance tests: not planned (tracker deviation 5); deltas show the counts behind them
- Date filters finer than a calendar year: not planned; the period stays in years as in 5.1 (deviation 6)
- Workflow figures (approval rate, processing days, counts by status): stay with the review metrics of 4.2.2; the `/reports/awards/summary` draft is removed (tracker deviation 3)
- Custom report builder, scheduled or e-mailed reports: not planned
- Delegated analytics: delegations carry award reading and approval only (AUTHENTICATION_AUTHORIZATION, approval delegation)

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-101 (#288) | 5.2.1 Unit analytics API for departments and faculties with period comparison | 8 | no | — |
| SCRUM-102 (#289) | 5.2.2 Faculty analytics page with department comparison and top performers | 5 | yes | 5.2.1 (`/analytics/units/*`) |
| SCRUM-103 (#290) | 5.2.3 Analytics report export to CSV, Excel and PDF | 5 | no | 5.2.1 (service); UI button after 5.2.2 |

5.2.1 is backend only (with the seed). 5.2.2 is frontend only and can start from the stubs while 5.2.1 is built. 5.2.3 has a backend part (export component, endpoint, audit) and a small UI part (export menu); it splits at the green backend gate.

## 4. Acceptance criteria

Common definitions: an award **counts** for unit U when it is `APPROVED`, its `organization_id` lies in the subtree of U (the department of the submission, DATA_DICTIONARY Appendix C, not the owner's current department) and its `award_date` year lies in the period; personal awards (`recipient_org_id` NULL) and unit awards both count, reported apart. The **period** is `from`..`to` in calendar years (Europe/Kyiv); without `to` it ends at the current year, without `from` it is the five years ending at `to`. The **previous period** has the same length and ends the year before `from`. **Members** of a unit are the `ACTIVE` accounts whose `users.organization_id` lies in its subtree. An **analysable unit** is a `FACULTY` or a `DEPARTMENT` covered by a held role scope (`AccessScope.heldScopes`) of a role granting `analytics:read:faculty` (any analysable type) or `analytics:read:department` (departments only).

### 5.2.1 Unit analytics API for departments and faculties with period comparison (SCRUM-101)

- **AC-1.1** Given the permission matrix, when the roles are inspected, then `analytics:read:department` is held by `FACULTY_SECRETARY`, `DEAN`, `RECTOR_SECRETARY` and `RECTOR`, and `analytics:read:faculty` by `DEAN`, `RECTOR_SECRETARY` and `RECTOR`; neither is delegable; `RolePermissions`, AUTHENTICATION_AUTHORIZATION §3.3 and RBAC_matrix §4 (with a footnote correcting "View Faculty Analytics" for the faculty secretary) agree.
- **AC-1.2** Given a signed-in account, when it calls `GET /analytics/units`, then it receives its analysable units (id, names, code, type, parent id), each faculty followed by its departments, alphabetically by Ukrainian name; `secretary.fmi` (scope ФМІ) gets the departments of ФМІ only, `dean.fmi` ФМІ and its departments, the rector every faculty and department; an account with neither permission receives 403, without a token 401.
- **AC-1.3** Given an analysable unit, when the caller calls `GET /analytics/units/{id}`, then it receives 200 `UnitAnalytics` with the unit, the period, the previous period, `generatedAt` and `partialYear` (true when `to` is the current year); a unit that is unknown, of another type or not analysable for the caller (a borrowed scope included) answers 404 with the same body, so ids cannot be probed; invalid years answer 400 `invalid-parameter` as in AC-1.5 of 5.1.
- **AC-1.4** Given approved, pending, rejected and draft awards inside and outside the unit, when the analytics are read, then `totals` holds `approved` and `awardedPeople` (distinct owners of counted personal awards) as `Figure` { `current`, `previous`, `delta`, `deltaPercent` (null when `previous` is 0) }, and `personal`, `unitAwards`, `verified`, `members` and `perHundredMembers` (counted awards per 100 members, one decimal, null without members) for the current period; only counted awards appear anywhere in the response.
- **AC-1.5** Given a faculty, when its analytics are read, then `units` lists every active child department and, when it has members or counted awards, a row for the faculty level itself (`facultyLevel` true), each with `members`, `approved` (`Figure`), `awardedPeople`, `perHundredMembers`, `verified` and `byYear` (counts per year of the period, zeros included); the rows' approved counts sum to the faculty total; for a department `units` is empty.
- **AC-1.6** Given counted awards, when the analytics are read, then `byYear` lists every year of the period with `total`, `personal` and `unitAwards`; `byCategory` and `byLevel` give `count` and `previous` per entry (levels in `RecognitionLevel` order, categories by count, then English name); `bySource` groups `awarding_organization` case- and space-insensitively under its most frequent spelling, the 15 largest by count with `otherCount` for the rest.
- **AC-1.7** Given members with counted personal awards, when the analytics are read and the unit has at least `app.analytics.min-group-size` members, then `topPerformers.status` is `LISTED` and `people` lists up to `app.analytics.top-performers` (10) `ACTIVE` owners whose latest `ANALYTICS` row is a grant, by count, then by surname, each with name, department, count and highest level, without positions; owners without the consent, erased or inactive owners and unit awards are never listed; below the minimum the status is `GROUP_TOO_SMALL` with no people.
- **AC-1.8** Given a faculty, when its analytics are read, then `highlights` lists, per department row with members: `NO_AWARDS` (no counted award), `DECLINE` (previous ≥ 4 and current ≤ half of it), `GROWTH` (previous ≥ 4 and current ≥ 1.5 times it) and, for rows with at least the minimum group of members, `BELOW_AVERAGE` (awards per member under half of the faculty's), each with the unit and the two counts; the rules and thresholds are documented in the user guide (5.2.2).
- **AC-1.9** Given the same unit and period read twice within `app.analytics.unit-cache-ttl` (5 minutes), when the second request runs, then the aggregates come from the Redis cache `unit-analytics` (same `generatedAt`), while names, the `ACTIVE` filter and the consent filter are applied per request, so a withdrawn consent or an erased account disappears from `topPerformers` at the next request; with Redis stopped the analytics are computed and returned, with a warning in the log.
- **AC-1.10** Given 30 000 approved awards over 10 departments and 600 members of one faculty, when its analytics are read without the cache, then the response arrives within 2 s in the IT (US-009 DoD: under 5 s), and the plan reads `awards` through `idx_awards_organization_status`, not a sequential scan.
- **AC-1.11** Given the migrated schema, when it is inspected, then `vw_award_statistics` no longer exists (dropped by `R__create_views.sql`), DATA_DICTIONARY Appendix C says so, and `openapi.yml` has no `/reports/awards/*` paths nor the `AwardsSummary` schema.
- **AC-1.12** Given the `local` or `docker` profile, when the database is migrated, then `R__seed_users_dev_analytics.sql` adds at least three approved unit awards of DAI and two of ФМІ itself, and awarding organisations written in two spellings (case and spacing) that group into one source; reruns add nothing; the e2e specs of 5.1 still pass.
- **AC-1.13** Given the module rules, when `ModularityTest` runs, then it passes: `analytics` reads awards through `award`'s `UnitStatistics`, consent through `ConsentLookup.grantedAmong`, names through `UserAccounts` (`ActorDirectory` carries neither the surname nor the department; ADR-022 addendum 2026-10-23), the analysable units through `authz` and the tree through `OrganizationTree`; ADR-022 lists the new exposed types.

### 5.2.2 Faculty analytics page with department comparison and top performers (SCRUM-102)

- **AC-2.1** Given an account with `analytics:read:department`, when it opens the navigation, then "Unit analytics" leads to `/analytics`, which opens the first unit of `GET /analytics/units` at `/analytics/units/{id}`; an account without it sees no entry and gets `/forbidden` on the direct URL; a unit id the API answers 404 for shows "This unit is not available" with a link to `/analytics`.
- **AC-2.2** Given the page, when it renders, then a unit selector (faculties and their departments, indented) and the period selector of 5.1 (a from–to pair of years, kept in the URL as `?from=&to=`) reload every widget, and a line states the previous period it is compared with and, when `partialYear`, that the current year is not over.
- **AC-2.3** Given the analytics, when the page renders, then it shows in this order: tiles for approved awards and awarded people with their delta (arrow, signed count and percent, "new" when the previous count is 0), unit awards, verified and awards per 100 members; the trend by year (one line per department for a faculty, bars of personal and unit awards for a department); the department comparison (faculty only); by category and by level (current and previous side by side); by awarding organisation; top performers; highlights.
- **AC-2.4** Given the department comparison, when it renders, then a table lists each row with members, approved with delta, awarded people, per 100 members and verified, sortable by every column, with "Faculty level" for the faculty row; a department name opens that department's page with the same period; a bar chart shows approved per 100 members per department.
- **AC-2.5** Given the top performers, when `status` is `LISTED`, then the list shows name, department, count and highest level, with the note that only people who agreed to be named appear; when the list is empty, "Nobody in this unit has agreed to be named yet."; when `GROUP_TOO_SMALL`, an explanation of the minimum group without figures.
- **AC-2.6** Given highlights, when they render, then each reads as a sentence naming the department and the counts (e.g. "Department of Mathematical Analysis: 3 awards, 8 in the previous period"), strengths and gaps in two groups; with none, "Nothing stands out in this period."
- **AC-2.7** Given any chart of the page, when it renders, then it uses the chart wrapper of 5.1.2 (SVG, ARIA, theme tokens, reduced motion, "Show as table"); categories and sources beyond 12 bars form "Other" in the chart only; a failed load shows the error with Retry, an invalid period "The period is not valid" as in 5.1.4.
- **AC-2.8** Given 360 px and 1280 px, uk and en, light and dark, when the page is screen-checked, then nothing scrolls horizontally except the comparison table inside its own container, texts are translated and axe reports no serious violation; the page is usable within 5 s with the seed (Playwright); the user guide has "Unit analytics" with the counting rule, the previous period, per 100 members, the highlight rules and the consent rule.

### 5.2.3 Analytics report export to CSV, Excel and PDF (SCRUM-103)

- **AC-3.1** Given an analysable unit, when the caller calls `GET /analytics/units/{id}/export?format=csv|xlsx|pdf&from=&to=&lang=uk|en`, then it receives the file with `Content-Disposition: attachment; filename="analytics-<code>-<from>-<to>.<ext>"`, the media type of the format and `Cache-Control: no-store`; access, 404 and 400 follow AC-1.3; an unknown `format` or `lang` answers 400 `invalid-parameter`.
- **AC-3.2** Given the export, when it is opened, then every format carries the same figures as `GET /analytics/units/{id}` for that period: a header (unit, period, previous period, produced at, produced by) and the sections totals, by year, departments, by category, by level, by source, top performers and highlights, labelled in `lang` with category and unit names in that language; the file carries the note that named people agreed to be named and that a later withdrawal does not change files already produced.
- **AC-3.3** Given CSV, when it is produced, then it is UTF-8 with a byte-order mark, RFC 4180 quoting, one long table (`section`, `key`, `label`, `year`, `current`, `previous`); given Excel, one sheet per section with a header row and number cells; in both a text cell starting with `=`, `+`, `-`, `@`, tab or carriage return is prefixed with `'` (formula injection).
- **AC-3.4** Given PDF, when it is produced, then it is A4 portrait from a Thymeleaf template with an embedded font that renders Ukrainian, tables that do not split rows across pages, page numbers, and opens in a standard viewer; the faculty report with the seed fits in at most 6 pages.
- **AC-3.5** Given an export, when it completes, then one `ANALYTICS_EXPORTED` audit row is written (`entity_type` `ANALYTICS`, `entity_id` the unit, `new_values` with format, period, language and the number of people named); a refused request writes none.
- **AC-3.6** Given the page of 5.2.2, when the figures are shown, then an "Export" menu offers PDF, Excel and CSV for the current unit, period and interface language and downloads the file; it is disabled while loading, after a failed load and for an invalid period; a failed download shows "The report could not be exported." without leaving the page.
- **AC-3.7** Given the new libraries, when the gate runs, then the export component is one `analytics.export` package behind a `ReportWriter` interface (one implementation per format), the class fan-out stays ≤ 25, SpotBugs and PMD report nothing, and the libraries and the font licence are listed in the README third-party section.

## 5. Edge cases

- **Period**: `from` without `to` ends at the current year; `to` without `from` is five years; a single year compares with the year before. `from=1950` makes a previous period before 1950, which is allowed internally and simply empty. The current year is the Kyiv year.
- **Award moved between units**: an award counts where it was submitted (`organization_id`); a later transfer of the owner does not move it. Members count where the account is now; per-member figures mix both, as stated in the user guide.
- **Inactive departments**: an inactive child department with counted awards still gets a row (its awards are part of the faculty total), marked inactive; one without awards is left out.
- **Faculty-level staff**: the dean's or secretary's personal awards submitted at faculty level land in the "Faculty level" row, not in a department.
- **Consent changes and erasure**: names are resolved per request, so a withdrawal or an erasure removes the person at the next request even while the aggregates are cached; anonymised awards of erased accounts keep counting in totals. Exports already produced are outside the system's control (purpose text of the consent).
- **Small groups**: top performers need the minimum group (5) in the requested unit; department figures are shown at every size, since every viewer already reads the same awards through `award:read:*` (deviation 4).
- **Ties and positions**: no positions are shown, so a gap left by an unlisted colleague cannot be seen.
- **Sources**: free text grouped by `lower(regexp_replace(btrim(x), '\s+', ' ', 'g'))`; different names of the same body (abbreviation vs full name) stay apart.
- **Concurrency and cache**: an approval appears after at most the TTL; `generatedAt` tells the viewer how fresh the figures are. Two simultaneous misses compute twice (no lock), harmless.
- **Authorization**: the scope check uses held scopes only, so a delegate never reads unit analytics; the 404 for out-of-scope units mirrors the review period endpoint; role revocation ends sessions (existing behaviour).
- **Export**: large faculties stay small in the file (aggregates only, top 10 people); the export is synchronous; formula injection is neutralised; the file name uses the unit code, never a person's name.
- **External**: Redis down: computed without cache (AC-1.9); the PDF font is bundled, no network access at render time.

## 6. Dependencies

### Tables

| Table | Change |
|-------|--------|
| `awards`, `award_categories`, `users`, `organizations`, `consent_records` | Read only (5.2.1) |
| `audit_logs` | New action `ANALYTICS_EXPORTED` (5.2.3); `action_type` has no check constraint, no migration |
| `vw_award_statistics` | Removed from `R__create_views.sql` (its existing `DROP VIEW IF EXISTS` stays) (5.2.1) |
| Seed `db/seed/local/R__seed_users_dev_analytics.sql` | Extended: unit awards, source spellings (5.2.1) |

No versioned migration; `idx_awards_organization_status` covers the unit query.

### Endpoints

| Endpoint | State |
|----------|-------|
| `GET /analytics/units` | New (5.2.1) |
| `GET /analytics/units/{id}` | New (5.2.1) |
| `GET /analytics/units/{id}/export` | New (5.2.3) |
| `GET /reports/awards/summary`, `GET /reports/awards/export` | Removed drafts (5.2.1) |

### Services

- `analytics`: `UnitAnalyticsService` (period, previous period, assembling, names and consent per request), `UnitScope` (analysable units from held scopes and the tree), `UnitAggregates` (cached, `@Cacheable("unit-analytics")`), `HighlightRules`, `AnalyticsCacheConfig` (Redis cache manager, TTL, error handler), `UnitAnalyticsController`; `analytics.export`: `ReportWriter`, `CsvReportWriter`, `XlsxReportWriter`, `PdfReportWriter`, `ReportLabels` (uk/en), `UnitReportExporter` (audit)
- `award` `service`: `UnitStatistics` (native SQL over a set of organisation ids and two periods: counts by year and personal/unit, by organisation, by category, by level, by normalised source, per owner of personal awards, verified, members per organisation)
- `gdpr`: `ConsentLookup.grantedAmong(Collection<Long>, ConsentType)` (batch form, added in 5.2.1)
- `user`: `UserAccounts.findAllById` (names, status and department of the top performers)
- `organization`: `OrganizationTree.subtree`, `node`; names through the existing organisation reference lookup
- `authz`: `RolePermissions` (two permissions, not delegable), `AccessScope.heldScopes`

### Frontend

- `features/analytics/units` (lazy route `/analytics`): `unit-analytics` page, `unit-picker`, `delta-tile`, `unit-trend-chart`, `unit-comparison` (table and chart), `comparison-bars` (category and level, current vs previous), `source-chart`, `top-performers`, `highlights`, `export-menu` (5.2.3); NgRx feature `unitAnalytics` (units, selected unit, period, data, load state)
- Reuse: `shared/charts` wrapper, `analytics-period` parser, horizontal-bar helper with "Other" (5.1.4)
- `core/layout/shell`: "Unit analytics" entry; `core/auth`: `unitAnalyticsGuard`
- i18n keys under `unitAnalytics.*`

### External systems

None. New backend libraries (5.2.3): `io.github.openhtmltopdf:openhtmltopdf-pdfbox` (HTML to PDF over PDFBox, LGPL-2.1), `org.dhatim:fastexcel` (XLSX writer, Apache-2.0); font Noto Sans (SIL OFL 1.1) under `resources/fonts`.

## 7. Technical decisions

Inherited: tracker decisions of 2026-10-19 (approved awards only, `ANALYTICS` for naming, SQL on demand with a short Redis TTL, one PDF library from 5.2.3), ADR-022 (modules), ADR-015 (NgRx per feature), ADR-014 with the 5.1.2 chart addendum (ngx-echarts), ADR-005 (Redis), PRD 5.1 deviations 5 (minimum group 5) and 13 (years), the award scope rule of AUTHENTICATION_AUTHORIZATION (an award belongs to the unit of its submission).

**Proposed deviations and assumptions**

1. **Two permissions, scope decides the unit** (tracker deviation 2): `analytics:read:department` for every approver role, `analytics:read:faculty` from the dean upward. A faculty secretary scoped to a whole faculty therefore opens each of its departments but not the faculty comparison; RBAC_matrix §4 gave the secretary "View Faculty Analytics" and is corrected with a footnote. Held scopes only: analytics are not part of a delegation.
2. **404 for a unit outside the scope**, like `GET /organizations/{id}/review-period`, rather than 403: the unit list is the way to discover units.
3. **Awards count where they were submitted** (`awards.organization_id`), members where they are now. This keeps the figures stable after transfers and matches the award reading rule and Appendix C; per-member figures are explained in the guide.
4. **Unit figures at every group size; only people need the minimum group.** Everyone with unit analytics already reads each of these awards through `award:read:*`, so hiding a small department's count would protect nothing from the viewer; the `ANALYTICS` consent governs naming (PRIVACY_BY_DESIGN §4.2), and the minimum group of 5 applies to the top-performer list (tracker risk 3).
5. **Top performers without positions**, consenting `ACTIVE` owners only, up to 10. A numbered list would reveal where an unlisted colleague sits.
6. **Period in years, default five, previous period of the same length** (US-009 "compare current period to previous periods"). Deltas show counts and percent; no significance test (tracker deviation 5). The current year is flagged `partialYear` instead of being compared to date.
7. **Highlights by four fixed rules** (`NO_AWARDS`, `DECLINE`, `GROWTH`, `BELOW_AVERAGE`) with the thresholds of AC-1.8 as constants, documented in the guide; "potential support needs" of US-009 read as departments, never as people.
8. **Per 100 members** rather than per member: whole numbers read better in a comparison table and a presentation.
9. **Cache the aggregates, not the response**: `UnitAggregates` holds counts and the per-owner counts keyed by unit and period, TTL 5 minutes; names, the `ACTIVE` filter and the consent are applied per request, so privacy changes are never delayed by the cache. Personal analytics stay uncached (PRD 5.1 deviation 2). A Redis failure falls back to computing (`CacheErrorHandler`), as the rate limit does.
10. **Export libraries**: openhtmltopdf with Thymeleaf (already used for mail) so the PDF is an HTML template that 5.3.3 reuses; FastExcel for XLSX (streaming, no XMLBeans, a fraction of Apache POI's weight); CSV written by hand (one long table). One `ReportWriter` interface keeps the fan-out down (tracker risk 4). Exports are synchronous: aggregates only, a few hundred rows.
11. **Exports are audited** (`ANALYTICS_EXPORTED`) because the file leaves the system with named people; viewing the page is not audited, like the other read screens.
12. **Points**: 5.2.1 goes from 5 to 8 for the cache infrastructure, the highlights, the unit list and the seed next to the aggregation; the feature totals 18, the epic 52. Keeping 5 would mean moving the cache to 5.3.
13. **`vw_award_statistics` dropped, not reused** (tracker deviation 6): it reports draft and pending counts and groups by one organisation; the service queries replace it.
14. **`/reports/awards/*` drafts replaced by `/analytics/units/*`** (tracker deviation 3), tag `Analytics`.

## 8. Contract

### 8.1 OpenAPI stubs

Written to `docs/api/openapi.yml` in this PRD's PR, each with `x-status: planned` and the story in `description`; tag `Analytics`. The `/reports/awards/*` drafts and `AwardsSummary` are removed in 5.2.1.

| Operation | Request | Response | Problems |
|-----------|---------|----------|----------|
| `GET /analytics/units` (`listAnalysableUnits`, 5.2.1) | — | 200 `AnalysableUnit`[] | 401; 403 |
| `GET /analytics/units/{id}` (`getUnitAnalytics`, 5.2.1) | path `id` (int64); query `from`, `to` (integer 1950..2100, optional) | 200 `UnitAnalytics` | 400 `invalid-parameter` (`from`, `to`); 401; 403; 404 |
| `GET /analytics/units/{id}/export` (`exportUnitAnalytics`, 5.2.3) | as above + `format` (enum `csv`, `xlsx`, `pdf`, required), `lang` (enum `uk`, `en`, default `uk`) | 200 `text/csv`, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `application/pdf` (binary) | 400 `invalid-parameter` (`format`, `lang`, `from`, `to`); 401; 403; 404 |

Schemas:

- `AnalysableUnit` { `id`, `name`, `nameUk`, `code`, `type` (`FACULTY`, `DEPARTMENT`), `parentId` (nullable) }
- `UnitAnalytics` { `unit` (`AnalysableUnit`), `period` { `from`, `to` }, `previousPeriod` { `from`, `to` }, `partialYear`, `generatedAt` (date-time), `totals` (`UnitTotals`), `byYear`: [{ `year`, `total`, `personal`, `unitAwards` }], `units`: `UnitComparisonRow`[], `byCategory`: [{ `category` (`AwardCategoryRef`), `count`, `previous` }], `byLevel`: [{ `level`, `count`, `previous` }], `bySource` { `sources`: [{ `name`, `count` }], `otherCount` }, `topPerformers` (`TopPerformers`), `highlights`: `UnitHighlight`[] }
- `Figure` { `current`, `previous`, `delta` (integer), `deltaPercent` (number, nullable) }
- `UnitTotals` { `approved` (`Figure`), `awardedPeople` (`Figure`), `personal`, `unitAwards`, `verified`, `members`, `perHundredMembers` (number, nullable) }
- `UnitComparisonRow` { `unit` (`OrganizationRef`), `facultyLevel`, `active`, `members`, `approved` (`Figure`), `awardedPeople`, `perHundredMembers` (nullable), `verified`, `byYear`: integer[] }
- `TopPerformers` { `status`: enum `LISTED`, `GROUP_TOO_SMALL`; `people`: [{ `userId`, `firstName`, `lastName`, `department` (`OrganizationRef`, nullable), `count`, `highestLevel` (`RecognitionLevel`) }] }
- `UnitHighlight` { `type`: enum `NO_AWARDS`, `DECLINE`, `GROWTH`, `BELOW_AVERAGE`; `unit` (`OrganizationRef`), `current`, `previous` }

### 8.2 Migration outlines

| File | Story | Content |
|------|-------|---------|
| `R__create_views.sql` (repeatable) | 5.2.1 | Remove the `vw_award_statistics` definition; the `DROP VIEW IF EXISTS vw_award_statistics CASCADE` line stays, so existing databases lose the view |
| `db/seed/local/R__seed_users_dev_analytics.sql` | 5.2.1 | Unit awards (`recipient_org_id` = DAI and ФМІ) with `award_requests` `APPROVED`; awards of existing colleagues with an awarding organisation in two spellings; guarded by `WHERE NOT EXISTS` on (owner or unit, `title_uk`) |

No versioned migration.

### 8.3 Properties

| Property | Default | Story |
|----------|---------|-------|
| `app.analytics.unit-cache-ttl` (`ANALYTICS_UNIT_CACHE_TTL`) | `PT5M` | 5.2.1 |
| `app.analytics.top-performers` | 10 | 5.2.1 |
| `app.analytics.min-group-size` | 5 (existing) | 5.1.1 |

## 9. Test plan

| AC | Unit | IT | FT | E2E / other |
|----|------|----|----|-------------|
| 1.1 | ✓ permission sets, not delegable | | ✓ 403 for employee, GDPR officer, administrator, delegate | doc review |
| 1.2 | ✓ unit list from scopes and tree | | ✓ list per seed role | |
| 1.3 | ✓ period and previous period | | ✓ 200, 404 unknown/other type/out of scope, 400 | |
| 1.4–1.6 | ✓ figures, deltas, per 100, row sums | ✓ `UnitStatistics` on Testcontainers: status, subtree, submission unit vs current unit, unit awards, sources grouping, Kyiv year | ✓ response shape on the seed | |
| 1.7 | ✓ consent, active, ordering, limit | ✓ group of 4 and 5 | ✓ consent toggle changes the list | |
| 1.8 | ✓ each rule and threshold edge | | | |
| 1.9 | ✓ error handler fallback | ✓ cache hit (`generatedAt`), withdrawal visible while cached, Redis stopped | | |
| 1.10 | | ✓ 30 000 awards, time and `EXPLAIN` | | |
| 1.11, 1.12 | | ✓ view absent; seed applied twice | | e2e 5.1 rerun |
| 1.13 | | ✓ `ModularityTest` | | doc review |
| 2.1–2.7 | ✓ components, store, guard, sorting, delta formatting, sentences | | | ✓ Playwright: dean faculty page, period, department drill-down, secretary list, forbidden for employee, invalid period |
| 2.8 | | | | screen check 360/1280, uk/en, light/dark, axe; timing; doc review |
| 3.1, 3.5 | ✓ file name, audit details | | ✓ three formats: headers, media types, 400/404, audit row | |
| 3.2–3.4 | ✓ each writer: sections, labels, injection guard, BOM | ✓ PDF parsed back (PDFBox text extraction: Ukrainian text, page count); XLSX read back | | |
| 3.6 | ✓ export menu states | | | ✓ Playwright: download of each format |
| 3.7 | | | | gate static; doc review |

Coverage 85 % lines; static clean. Security review for 5.2.1 (permissions, scope check, consent filter, cache contents) and 5.2.3 (export, injection, audit).

## 10. Manual verification

Expected texts are planned wording; they are replaced by the merged English i18n texts at the feature validation. Local e2e runs register accounts in the e2e unit, so figures of ФМІ grow over time; compare with psql rather than with fixed numbers.

Preconditions: `docker compose up -d postgres redis mailpit minio clamav`; backend `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (applies the seed); frontend `npm start` on `http://localhost:4200`. psql `docker compose exec postgres psql -U postgres award_monitoring`; Swagger `http://localhost:8080/swagger-ui.html`; Redis `docker compose exec redis redis-cli`. Seed accounts (password `Passw0rd-demo`, `@chnu.edu.ua`): `dean.fmi` (DEAN, ФМІ), `secretary.fmi` and `secretary2.fmi` (FACULTY_SECRETARY, ФМІ), `rector`, `employee.fmi`, `lecturer.fmi` (`ANALYTICS` granted), `admin`, `gdpr`.

1. psql: counted awards of ФМІ per department for 2022–2026 and 2017–2021 (`status = 'APPROVED'`, `organization_id` in the ФМІ subtree), the unit awards of DAI and ФМІ, and the `ACTIVE` members per department. Expected: DAI and DMA with awards in both periods; at least three DAI and two ФМІ unit awards. (AC-1.12)
2. Sign in as `dean.fmi`; Swagger `GET /analytics/units`. Expected: ФМІ followed by its departments. As `secretary.fmi`: the departments of ФМІ only; as `rector`: every faculty and department. (AC-1.1, 1.2)
3. As `dean.fmi`, `GET /analytics/units/9`. Expected: 200; period 2022–2026, previous 2017–2021, `partialYear` true; `totals.approved.current` equals step 1; the `units` rows sum to the totals; a "Faculty level" row with the ФМІ unit awards. (AC-1.3–1.5)
4. Same response: `byYear` five entries with personal and unit counts; `bySource` shows the two seeded spellings as one source with their sum. (AC-1.6)
5. `topPerformers`: `LISTED`, `lecturer.fmi` among the people, nobody without an `ANALYTICS` grant (check with psql against `consent_records`), no positions. (AC-1.7)
6. `highlights`: compare each with the rules of AC-1.8 using the counts of step 1. (AC-1.8)
7. Call it again. Expected: same `generatedAt`; `redis-cli keys '*unit-analytics*'` shows the entry; it expires after 5 minutes. (AC-1.9)
8. `GET /analytics/units/9?from=2027`, `?from=2026&to=2021`, `?from=abc`. Expected: 400 `invalid-parameter` naming the parameter. (AC-1.3)
9. As `secretary.fmi`, `GET /analytics/units/9` and `GET /analytics/units/1`. Expected: 404 for both; a DAI id → 200. (AC-1.3, deviation 1)
10. Open `http://localhost:4200` as `dean.fmi`, navigation "Unit analytics". Expected: `/analytics/units/9` with unit and period selectors, the comparison line "Compared with 2017–2021" and the note that the current year is not over; tiles with deltas, trend lines per department, the comparison table, category and level charts, sources, top performers, highlights. (AC-2.1–2.3)
11. Sort the comparison table by "Per 100 members", then open DAI from the table. Expected: the order follows the column; `/analytics/units/<DAI>` (the default period stays implicit in the address; a chosen period is carried as `?from=&to=`) with bars of personal and unit awards and no comparison section. (AC-2.4)
12. Choose 2024–2026 on the faculty. Expected: URL `?from=2024&to=2026`, compared with 2021–2023, every widget reloaded. (AC-2.2)
13. "Show as table" on the trend and the category chart. Expected: the same figures as the API; categories beyond 12 grouped as "Other" only in the chart. (AC-2.7)
14. As `lecturer.fmi` on `/privacy` withdraw analytics; as `dean.fmi` reload the faculty within 5 minutes. Expected: `lecturer.fmi` is gone from top performers while `generatedAt` is unchanged; grant again. (AC-1.7, 1.9, 2.5)
15. Export PDF, Excel and CSV from the "Export" menu in English and in Ukrainian. Expected: three downloads named `analytics-<code>-2022-2026.*`; the PDF in A4 with Ukrainian text, page numbers and the consent note; the Excel file with one sheet per section; the CSV, comma-separated, opens in Excel through Data → From Text/CSV with Cyrillic intact (a double-click splits columns only where the list separator is a comma); the figures match the page. Then choose the single year 2026 and export CSV: the header shows the period "2026" and the previous period "2025". (AC-3.1–3.4, 3.6)
16. psql `select action_type, entity_id, new_values from audit_logs where action_type = 'ANALYTICS_EXPORTED' order by log_id desc limit 3;`. Expected: one row per export with format, period, language and the number named. (AC-3.5)
17. As `dean.fmi`, enter an award whose awarding organisation is `=HYPERLINK("http://example.org")` and have it approved through the usual workflow; export CSV and Excel. Expected: the cell shows the text with a leading `'`, no formula. (AC-3.3)
18. Screen check of `/analytics/units/9` at 360 and 1280 px, uk/en, light/dark. Expected: no page-level horizontal scroll, the table scrolls in its container, no serious axe finding. (AC-2.8)
19. Run the Playwright spec of 5.2.2 and note the time to a usable page. Expected: under 5 s. (AC-2.8)

### Detours

20. Direct URL `/analytics` as `employee.fmi`, `admin` and `gdpr`. Expected: no navigation entry, `/forbidden`; `GET /analytics/units` → 403. (AC-1.2, 2.1)
21. Direct URL `/analytics/units/9` as `secretary.fmi` and `/analytics/units/999999` as `dean.fmi`. Expected: "This unit is not available" with a link to `/analytics`. (AC-2.1)
22. Direct URLs `/analytics/units/9?from=2027` and `?from=2026&to=2021`. Expected: "The period is not valid" with a way back; the "Export" menu disabled. (AC-2.7, 3.6)
23. Reload the page and use back/forward after changing the unit and period twice. Expected: each history entry restores its unit and period. (AC-2.2)
24. Restart the backend while the page is open, then change the period; stop it again and reload the page. Expected: one error with Retry while it is down, the figures after it is up; "Export" disabled while the error shows; after the reload, Retry with the backend up fills the unit selector as well as the figures. (AC-2.7, 3.6)
25. Let the access token expire with the page open, then export. Expected: silent refresh, the file downloads. (AC-3.6)
26. Stop Redis (`docker compose stop redis`) and reload the faculty. Expected: the page loads (a cache warning in the backend log); start Redis again. (AC-1.9)
27. Hidden in state, export: during loading, after a failed load and for an invalid period the "Export" menu is disabled; `GET /analytics/units/9/export?format=docx` → 400; `?format=pdf` as `secretary.fmi` → 404. (AC-3.1, 3.6)
28. Hidden in state, top performers: restart the backend with `ANALYTICS_MIN_GROUP_SIZE=5000` (above the member count of the faculty, which local e2e accounts push past 1 200; 50 only hides the small departments). Expected: the faculty's top performers explain the minimum group without names; API `GROUP_TOO_SMALL`, no people; the department figures stay. Restore. (AC-1.7, 2.5)
29. Hidden in state, faculty comparison: as `secretary.fmi` open a department: no comparison section, no faculty in the unit selector. (AC-1.2, 2.4)
30. Revoked mid-flow, role: `admin` revokes the DEAN role of `dean.fmi` while the faculty page is open with the export menu open. Expected: the revocation ends the dean's sessions; the next request leads to the sign-in page; after signing in "Unit analytics" is gone (unless another role grants it) and an export URL answers 403. `secretary.fmi` keeps the department pages unchanged. Re-grant the role. (AC-1.1, 2.1, 3.1)
31. Revoked mid-flow, delegation: `secretary.fmi` lends FACULTY_SECRETARY of ФМІ to `employee.fmi`. Expected: `employee.fmi` still gets 403 on `GET /analytics/units` and no navigation entry (analytics are not delegable); revoking the delegation changes nothing for analytics. (AC-1.1, deviation 1)
32. Revoked mid-flow, consent: `lecturer.fmi` withdraws analytics while `dean.fmi` has the page open. Expected: the open page keeps the name until its next load; an export started after the withdrawal leaves the name out. (AC-1.7, 3.2)
33. Erasure of a top performer (a fresh account with the consent and approved awards, `APP_ERASURE_GRACE_PERIOD=PT10M` as in Feature 6.1 §10): after completion the person is gone from top performers at the next request, the faculty totals keep the anonymised awards. (AC-1.7, §5)
34. Transfer `lecturer.fmi` from DAI to DMA (`admin`). Expected: the DAI members drop by one and DMA's grow, the awards stay in DAI's count; the top-performer entry names DMA. (AC-1.5, deviation 3)
35. Approve a new award in DAI and reload the faculty at once, then after 5 minutes. Expected: unchanged figures with the old `generatedAt`, then the new count. (AC-1.9)

### Run 2026-10-24

container stack rebuilt from feature/SCRUM-103 (cf3e8c0, merged as PR #305); defaults except step 28 (ANALYTICS_MIN_GROUP_SIZE=5000) and 33 (APP_ERASURE_GRACE_PERIOD=PT10M, WARNING_BEFORE=PT5M, CRON every minute); steps 10-34, defaults restored. Result: 25 pass.

| Step | Result | Evidence |
|---|---|---|
| 10 | PASS | dean.fmi: /analytics/units/9, line 2022–2026 vs 2017–2021 + partial-year note, tiles with deltas, trend, table, category/level/source charts, top performers, highlights; 3.2 s to usable |
| 11 | PASS | step text corrected: default period is implicit in the URL (no ?from/to until chosen); sort by per-100 follows column both ways; DAI /units/64 same period, no comparison section |
| 12 | PASS | ?from=2024&to=2026, line 2024–2026 vs 2021–2023, tiles reloaded (298 approved) |
| 13 | PASS | 5 tables match API (12 categories 215/7, 46/8…, 6 levels); 'Other' grouping not exercised: exactly 12 categories |
| 14 | PASS | lecturer.fmi (966) withdrawn: top performers 7→6 without 966, generatedAt 09:07:09 unchanged; granted again: 966 back |
| 15 | PASS | uk+en: analytics-FMI-2022-2026.pdf/.xlsx/.csv; PDF 4 pages 'Сторінка n з 4'/'Page n of 4', Cyrillic + consent note, A4 per ui-check; xlsx 9 sheets; CSV BOM, 134 rows x 6 cols, approved 325/37 = page |
| 16 | PASS | 6 new rows (ids 46983–46988) ANALYTICS_EXPORTED entity 9: format, from/to 2022–2026, language, peopleNamed 7 |
| 17 | PASS | award 1401 (dean.fmi, approved by rector) with org =HYPERLINK(...): CSV bySource row and XLSX shared string start with ', no <f> formula element |
| 18 | PASS | ui-check fallback 1280/360, light/dark uk + 1280 en: scrollWidth = viewport, axe serious+ 0, console errors 0; 360 en scrollWidth 360 |
| 19 | PASS | e2e unit-analytics.spec 8/8 (assertion < 5 s inside test 1); manual open 3.2 s to usable |
| 20 | PASS | employee.fmi/admin/gdpr: no Analytics nav link, /analytics → /forbidden; GET /analytics/units and /units/9 → 403 |
| 21 | PASS | secretary.fmi /units/9 and dean.fmi /units/999999: 'This unit is not available' + link to /analytics; API 404 both |
| 22 | PASS | ?from=2027 and ?from=2026&to=2021: 'The period is not valid', Export disabled, All years button; API 400 invalid-parameter (also for export) |
| 23 | PASS | units 9→64, from 2023, to 2025: Back ×3 restore 64?from=2023, 9?from=2023, 9; forward and reload keep unit+period |
| 24 | PASS | backend stopped, period change on DAI: figures stale ~2.5 s then one error 'Не вдалося завантажити…' + Retry at ~30 s, Export disabled; after start, Retry shows 2024–2026 vs 2021–2023 (283), Export enabled |
| 25 | PASS | secretary.fmi page open since sign-in 09:19:40 (token 15 min TTL), export CSV at 09:36: 200, analytics-DAI-2022-2026.csv downloaded, no sign-in page |
| 26 | PASS | Redis stopped: GET /analytics/units/9 → 200 (generatedAt 09:20:17); backend log lettuce 'Cannot reconnect to redis'; Redis started again |
| 27 | PASS | Export disabled while loading, after the failed load (24) and for invalid periods (22); API format=docx → 400, secretary.fmi unit 9 pdf → 404 |
| 28 | PASS | step text corrected: 50 is below the ~1200 members the e2e accounts piled into DAI/FMI; with ANALYTICS_MIN_GROUP_SIZE=5000 faculty topPerformers GROUP_TOO_SMALL, no people, UI text 'менше працівників, ніж мінімальна група…', totals/departments stay (302/21); at 50 DMA (6 members) GROUP_TOO_SMALL; restored |
| 29 | PASS | secretary.fmi on DAI (64): no comparison section; unit selector lists only the 5 departments |
| 30 | PASS | admin DELETE role 15: open export click → 401 → /login; re-sign-in lands on /forbidden, no nav entry, export URL 403; secretary.fmi dept 64 still 200; DEAN re-granted (role 1251), dean sees analytics again |
| 31 | PASS | delegation 34 FACULTY_SECRETARY FMI → employee.fmi: GET /analytics/units and /units/9 → 403 while active and after DELETE (204); nav entry not checked in UI |
| 32 | PASS | lecturer.fmi withdraws: open-page behaviour inherent; CSV export after withdrawal 6 topPerformers rows without the name (7 before and after re-grant) |
| 33 | PASS | fresh e52.erasure (uid 1526, DAI, EMPLOYEE, ANALYTICS granted, 7 approved awards 1402–1408) listed top-1 with 7; erasure request 61 (PT10M, cron 1 min) COMPLETED 09:57; next load: not in topPerformers, faculty total 333 unchanged, 7 awards still APPROVED |
| 34 | PASS | admin POST /users/966/roles EMPLOYEE 66 updateOrganization from 64: after cache TTL DAI members 1197→1196, DMA 6→7, approved unchanged 302/21, top performer department 66 |

### Run 2026-10-24 (validation)

Container stack from a tree equal to `develop` 68c26ee (`git diff cf3e8c0 68c26ee -- backend frontend docker-compose.yaml` empty), defaults; steps 1–9 and 35 through the API (`/api/v1`), psql and redis-cli. Result: 10 pass, so all 35 steps are run by hand.

| Step | Result | Evidence |
|---|---|---|
| 1 | PASS | psql 2022–2026 / 2017–2021: DAI 309/25 (4 unit awards), DMA 21/12, ФМІ level 3/0 (2 unit awards); ACTIVE members DAI 1198, DDE 1, DMA 6, ФМІ 3 |
| 2 | PASS | dean.fmi 6 units (FMI, DAI, DDE, DMA, DMM, DAMIT); secretary.fmi the 5 departments, no faculty; rector 85 units, 12 faculties; employee.fmi 403 |
| 3 | PASS | /units/9: period 2022–2026, previous 2017–2021, partialYear true; approved 333/37 = psql (309+21+3); rows sum 333/37; faculty-level row 3 members, 3 awards; unitAwards 6 = psql |
| 4 | PASS | byYear 2022–2026 (13, 14, 14, 16, 276) with personal/unit split; the four spellings of "Міністерство освіти і науки України" grouped as one source with 220 = psql over the normalised name |
| 5 | PASS | LISTED, 7 people (966 lecturer.fmi top with 6); all 7 have a latest ANALYTICS grant and psql counts exactly 7 consenting ACTIVE owners; keys userId, firstName, lastName, department, count, highestLevel, no position |
| 6 | PASS | NO_AWARDS DDE (1 member, 0/0); GROWTH DAI 309/25 and DMA 21/12; DMM and DAMIT (no members) left out; no BELOW_AVERAGE (DAI 0.26 per member vs faculty 0.28) |
| 7 | PASS | second call same generatedAt 10:06:34.419Z; key `awardapp:unit-analytics::9:2022:2026`, TTL 291 s |
| 8 | PASS | ?from=2027, ?from=2026&to=2021, ?from=abc: 400 invalid-parameter, `parameter: from` |
| 9 | PASS | secretary.fmi /units/9 and /units/1: 404 with the same body; /units/64 (DAI): 200 |
| 35 | PASS | award 1409 (lecturer.fmi, DAI) approved; at once 333 with generatedAt 10:06:34; after the key expired 334 with generatedAt 10:11:37 |

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| openhtmltopdf or FastExcel trip SpotBugs or the dependency check | Medium | Medium | One `analytics.export` package; exclusions only with a reason; Apache POI as fallback for XLSX |
| Spring Cache with Redis serialisation of records fails at runtime only | Medium | Low | IT with a cache hit on Testcontainers Redis; JSON serializer with type information limited to the analytics package |
| Department figures read as a ranking of people in small departments | Low | Medium | No names without the consent; minimum group for top performers; guide text explains the figures |
| 5.2.1 at 8 points overruns sprint 7 | Medium | Medium | The cache (AC-1.9) can move to 5.3.1 without changing the API |
| Calendar (deployment mid-November) | Medium | High | 5.2.3 can ship CSV and Excel before PDF; the board report (5.3.3) stays the cut line |

## 12. Definition of Done

- Every AC proven at the layers of §9; `.\tools\gate.ps1` green per story; Playwright for 5.2.2 and 5.2.3
- Screen check of `/analytics/units/{id}` (faculty and department) and the export menu; the PDF opened and checked by eye
- Security review for 5.2.1 and 5.2.3
- `openapi.yml` without `x-status: planned` on the three operations and without `/reports/awards/*`; DATA_DICTIONARY Appendix C and `ANALYTICS_EXPORTED`; AUTHENTICATION_AUTHORIZATION §3.3; RBAC_matrix §4; ADR-022 module table; README third-party libraries and font licence; user guide "Unit analytics"; CHANGELOG `[Unreleased]`; tracker and BACKLOG
- Manual verification (§10) run and recorded; feature validated

## 13. Validation (2026-10-24, `develop` at 68c26ee)

PRs #303, #304, #305 merged, all three stories Done in Jira and GitHub. Result: **passed with notes**. Every AC has a test, every step of §10 passed by hand (25 in the first run, 10 in the validation run). The scenario review found two small defects in the failure paths of the page and no defect in the API; the refactor sweep found nothing worth a PR on its own (findings 2–4).

| Layer | Result |
|-------|--------|
| Backend unit / IT + FT | 1237 / 486 on `develop`, all green (`gate.ps1 -Full`, 2026-10-24 13:22) |
| Coverage, static | 98.7 % lines (5668/5745); Checkstyle, PMD, SpotBugs 0 |
| Frontend | lint clean, 881 tests, initial bundle 958.33 kB |
| Integration | Containers built from a tree equal to `develop`, healthy; migrations apply on empty Testcontainers databases in every IT; Flyway at V046; `/v3/api-docs` lists `/analytics/me`, `/analytics/units`, `/analytics/units/{id}`, `/analytics/units/{id}/export` as in `openapi.yml`, which has no `x-status: planned` operation and no `/reports/awards/*` left |
| Playwright | Full run 2026-10-24: 114 of 118, `unit-analytics.spec` 8/8 and the 5.1 `dashboard.spec` green; the four failures are the known `auth.spec:85` (local seed), `reviews.spec:174/387` and `theme.spec:116` (axe mid-load), none in this feature |
| Security | Checklist below; the PRs of #303 (permissions, scope check, consent filter, cache) and #305 (export, injection, audit) leave no finding open |

### AC evidence

| AC | Unit | IT / FT | E2E / manual |
|----|------|---------|--------------|
| 1.1 | `RolePermissionsTest.ac1_1_unitAnalyticsBelongToTheApproversAndAreNeverLent` | `UnitAnalyticsFT.ac1_1_rolesWithoutUnitAnalyticsAndDelegatesAreRefused` | §10 steps 20, 30, 31; doc review ✓ |
| 1.2 | `UnitScopeTest.ac1_2_*` (4), `UnitAnalyticsServiceTest.ac1_2_unitsComeFromTheScope` | `UnitAnalyticsFT.ac1_2_eachRoleListsTheUnitsOfItsOwnScope` | step 2 |
| 1.3 | `UnitAnalyticsServiceTest.ac1_3_*` (3), `UnitScopeTest.ac1_3_aUnitOutsideTheScopeOfAnotherTypeOrUnknownIsNotFound` | `UnitAnalyticsFT.ac1_3_unitsOutsideTheCallersAnalysableUnitsAnswerNotFoundAlike`, `ac1_3_invalidYearsNameTheParameter` | steps 3, 8, 9 |
| 1.4 | `UnitFiguresHelperTest.ac1_4_*` (2) | `UnitAnalyticsIT.ac1_4_onlyApprovedAwardsSubmittedInTheSubtreeAndPeriodCountWhereverTheOwnerWorksNow`, `UnitStatisticsIT.ac1_4_anEmptySetOfOrganisationsCountsNothing`, `UnitAnalyticsFT.ac1_4_ac1_5_*` | step 3 |
| 1.5 | `UnitAnalyticsServiceTest.ac1_5_*` (2) | `UnitAnalyticsIT.ac1_5_theDepartmentRowsAndTheFacultyLevelAddUpToTheFaculty`, `UnitAnalyticsFT.ac1_4_ac1_5_*` | steps 3, 34 |
| 1.6 | `UnitFiguresHelperTest.ac1_6_*` (2) | `DevSeedIT.ac1_12_*` (spellings) | step 4 |
| 1.7 | `TopPerformersRankingTest.ac1_7_*` (3), `UnitAnalyticsServiceTest.ac1_7_*` | `UnitAnalyticsIT.ac1_7_peopleAreListedFromTheMinimumGroupWithTheirConsentOnly`, `UnitAnalyticsFT.ac1_7_aWithdrawnConsentRemovesThePersonAtTheNextRequest` | steps 5, 14, 28, 32, 33 |
| 1.8 | `HighlightRulesTest.ac1_8_*` (5), `UnitAnalyticsServiceTest.ac1_8_*` | | step 6 |
| 1.9 | `CacheFailureLoggerTest.ac1_9_*` (2) | `UnitAnalyticsIT.ac1_9_cachedAggregatesKeepTheirTimeWhileConsentIsReadAtEveryRequest`, `DevSeedIT.ac1_9_*` | steps 7, 14, 26, 35 |
| 1.10 | | `UnitStatisticsIT.ac1_10_thirtyThousandAwardsOfSixHundredMembersAnswerWithinTwoSecondsThroughTheIndex` (time and plan) | |
| 1.11 | | `AwardDatabaseObjectsIT` (view absent) | `openapi.yml` and DATA_DICTIONARY Appendix C ✓ |
| 1.12 | | `DevSeedIT.ac1_12_theSeedHoldsApprovedUnitAwardsAndOneSourceInSeveralSpellings` | step 1; e2e 5.1 specs in the full run |
| 1.13 | | `ModularityTest` | ADR-022 ✓ |
| 2.1 | `unit-analytics-entry.component.spec` (3), `unit-analytics.component.spec` ac2_1 (2) | | `unit-analytics.spec` ac2_1 (3 tests); steps 10, 20, 21 |
| 2.2 | `unit-analytics.component.spec` ac2_2 (3), `unit-analytics.effects.spec` (5) | | `unit-analytics.spec` ac2_2 ac2_4, ac2_2 (secretary); steps 12, 23 |
| 2.3 | `unit-analytics.component.spec` ac2_3 (2), `delta.spec` (2) | | `unit-analytics.spec` ac2_1 ac2_3 ac2_8; step 10 |
| 2.4 | `unit-comparison.component.spec` (4) | | `unit-analytics.spec` ac2_2 ac2_4; step 11 |
| 2.5 | `top-performers.component.spec` (3) | | steps 14, 28 |
| 2.6 | `highlights.component.spec` (4) | | step 10 |
| 2.7 | `unit-charts.spec` (5), `unit-analytics.component.spec` ac2_7 (2) | | `unit-analytics.spec` ac2_1 ac2_7; steps 13, 22, 24 |
| 2.8 | | | `unit-analytics.spec` ac2_8 (360 px, dark, axe), ac2_1 ac2_3 ac2_8 (under 5 s); steps 18, 19; user guide "Unit analytics" ✓ |
| 3.1 | `UnitReportExporterTest.ac3_1_*` (3) | `UnitAnalyticsExportFT.ac3_1_*` (3) | steps 15, 27 |
| 3.2 | `UnitReportAssemblerTest.ac3_2_*` (7), writer tests ac3_2 (4) | `UnitAnalyticsExportFT.ac3_2_ac3_4_thePdfAndTheWorkbookCarryTheSectionsInUkrainian` | steps 15, 32 |
| 3.3 | `CsvReportWriterTest.ac3_3_*` (3), `XlsxReportWriterTest.ac3_3_*` (3) | `UnitAnalyticsExportFT.ac3_3_ac3_5_theCsvDefusesFormulasAndTheExportIsAudited` | steps 15, 17 |
| 3.4 | `PdfReportWriterTest.ac3_4_*` (3, incl. six pages for a large faculty) | `UnitAnalyticsExportFT.ac3_2_ac3_4_*` | step 15 (4 pages) |
| 3.5 | `UnitReportExporterTest.ac3_5_*` (2, incl. no row when refused) | `UnitAnalyticsExportFT.ac3_3_ac3_5_*` | step 16 |
| 3.6 | `export-menu.component.spec` (5), `unit-analytics.component.spec` ac3_6 (2) | | `unit-analytics.spec` ac3_6 (2); steps 15, 22, 24, 25, 27 |
| 3.7 | `UnitReportExporterTest.ac3_7_aFormatWithoutAWriterIsRefused` | | gate static 0; README third-party (Noto Sans, OFL) ✓ |

### Edge cases (§5)

| Edge case | Covered by |
|-----------|------------|
| Period defaults, single year, Kyiv year | `UnitAnalyticsServiceTest.ac1_3_thePeriodDefaultsToFiveYearsEndingNowComparedWithTheFiveBefore`, `ac1_3_aSingleYearIsComparedWithTheYearBeforeAndAPastYearIsComplete`; `from=1950&to=2100` answers 200 (probe) |
| Award moved between units | `UnitAnalyticsIT.ac1_4_*WhereverTheOwnerWorksNow`; step 34 |
| Inactive departments | `UnitAnalyticsServiceTest.ac1_5_theFacultyRowsCoverEveryActiveDepartmentAndTheFacultyLevelAndAddUp` (`UnitTreeFixture` with an inactive department with and without awards) |
| Faculty-level staff | `UnitAnalyticsIT.ac1_5_*`; step 3 |
| Consent changes and erasure | `UnitAnalyticsFT.ac1_7_*`, `UnitAnalyticsIT.ac1_9_*`; steps 14, 32, 33 |
| Small groups | `TopPerformersRankingTest.ac1_7_belowTheMinimumGroupNobodyIsListed`, `UnitAnalyticsIT.ac1_7_*` (groups of 4 and 5); step 28 |
| Ties and positions | `TopPerformersRankingTest.ac1_7_onlyActiveConsentingOwnersAreListedByCountThenSurnameUpToTheLimit`; step 5 |
| Sources | `UnitFiguresHelperTest.ac1_6_sourcesGroupTheirSpellingsUnderTheMostFrequentAndKeepTheFifteenLargest`; step 4 |
| Concurrency and cache | `UnitAnalyticsIT.ac1_9_*`; steps 7, 35 |
| Authorization | `UnitScopeTest.ac1_2_rolesWithoutUnitAnalyticsAndBorrowedScopesListNothing`, `UnitAnalyticsFT.ac1_1_*`, `ac1_3_*`; steps 30, 31 |
| Export | writer tests ac3_3 (injection), `UnitReportExporterTest.ac3_1_*` (file name from the code or the id); widest period `from=1950&to=2100`: PDF 7.6 s, 206 kB, XLSX 0.9 s (probe) |
| External | `CacheFailureLoggerTest.ac1_9_*`, step 26 (Redis down); `PdfReportWriterTest.ac3_4_theFontIsEmbedded` |

### Security (OWASP)

- **A01**: the three endpoints need `analytics:read:department` or `analytics:read:faculty` (`@access.require`). `UnitScope` checks the unit against the held role scopes only (`access.heldScopes()`), so delegations never apply. An unknown unit, a unit of another type and one out of scope all answer the same 404. The cache key (`unitId:from:to`) holds aggregates only; the scope check runs before the cache and names and consent are resolved per request.
- **A02**: no new secrets or crypto. All three responses are `Cache-Control: no-store`. People are named only with the latest `ANALYTICS` grant, and only from the minimum group up.
- **A03**: `UnitStatistics` uses `NamedParameterJdbcTemplate` with bound parameters. `ReportTextHelper.guard` prefixes text starting with `=`, `+`, `-`, `@`, tab or CR in CSV and XLSX (step 17). The Thymeleaf template has no `th:utext`. `format` and `lang` are enums, and unknown values answer 400.
- **A07**: no change to authentication. Role revocation ends the sessions (step 30).

### Findings

1. Scenario review, two small defects in the failure paths of the page (no AC broken):
   - When `GET /analytics/units` fails on `/analytics/units/{id}` (backend down at a reload), the unit selector stays empty: the component requests the units only while their status is `idle` (`unit-analytics.component.ts:119`), and Retry reloads only the analytics (`:153`).
   - A page left open over New Year on the default period exports the new year's period: the store keeps no years for the default period, so the server resolves the period again at export time, and the file covers 2023–2027 while the page shows 2022–2026.
   Proposed as one fix story 5.2.4 (about 2 points): Retry also reloads a failed unit list, and the export sends the period the page shows; a component spec for each, a clock test for New Year.
2. Scenario review, accepted without a change: two tabs exporting write two audit rows (by design); the export has no rate limit beyond the 1950..2100 period bound (widest PDF 7.6 s); a cached entry of an older shape after a deploy is absorbed by `CacheFailureLogger` (covered at unit level only); an inactive unit opened by its id answers 200 but is missing from the selector.
3. Refactor sweep, small items for the next story that touches the code: `OrganizationRef.of(Organization)` for the seven sites that build it by hand; one paired-count helper for `byCategory` and `byLevel` in `UnitFiguresHelper`; `source-chart` through the shared `withOther`; one helper for the faculty-level row name in the comparison table and the trend chart; a `ReportTextHelperTest` instead of the guard assertions in `CsvReportWriterTest`; the unused `MARK_GAP` in `chart-style.ts`.
4. Refactor sweep, one behaviour difference: the export header writes a single-year period as "2026–2026", while the page shows "2026". It goes with the fix story of finding 1.

# Feature 5.3: Executive University Dashboard

> **Epic**: 5 — Analytics & Reporting Dashboard (SCRUM-97)
> **Sprint**: 7 (2026-10-26 → 2026-11-01)
> **Points**: 16 (three stories; 13 at kickoff, see deviation 12)
> **Status**: Validated 2026-10-25, pending the manual run of §10
> **Author**: Stefan Kostyk
> **Governing docs**: EPIC-05 tracker (decisions of 2026-10-19 and 2026-10-23, deviations 2, 4 and 5, risks 1 and 4), US-010, roadmap Feature 5.3, RBAC_matrix §4, AUTHENTICATION_AUTHORIZATION §3.3, PRIVACY_BY_DESIGN §4.2, DATA_DICTIONARY §2.2 (`award_requests` deadlines), §4.2–4.5, Appendix C, ADR-005, ADR-014, ADR-015, ADR-022, PRD 5.2 (unit analytics, cache, highlights, export component), `UnitAggregates`, `UnitComparison`, `HighlightRules`, `ComplianceQueries`, `OverdueNotices`, `PdfReportWriter`, `openapi.yml` (`/analytics/units/*`, `/compliance/*`)

## 1. Problem and personas

The rector answers for the university's recognition record in front of the academic council and the supervisory board, but the system shows it one faculty at a time: the faculty pages of 5.2 open for the rector, yet nothing compares the faculties and colleges, shows which kinds of award and which awarding bodies gain or lose ground across the university, or says whether the data protection duties and the review deadlines are kept. Board material is assembled by hand from several exports. US-010 asks for metrics across all faculties, a real-time compliance status, trending categories and sources, strengths and improvement opportunities, and a presentation-ready report with comparative data and trend analysis.

| Persona | Need in this feature |
|---------|----------------------|
| Prof. Biloskurskyi, rector | Opens the university view over a period compared with the previous one; sees faculties and colleges side by side, the trend with a projection for the next year, trending categories and sources, strengths and gaps, and the compliance status; downloads a board report |
| Rector's secretary | Prepares the board meeting: the same view and the board report |
| Dean | Keeps the faculty view of 5.2; no university view (RBAC_matrix §4) |
| GDPR officer | Keeps the compliance page with every record; the executive summary carries counts only (tracker deviation 4) |
| Employees | Are never named in the university view; top performers stay on the faculty and department pages under the `ANALYTICS` consent |

## 2. Scope

**In (this feature)**

- `GET /analytics/university` (one period of calendar years compared with the previous one): totals, counts by year, a comparison of the faculties and colleges, counts by category, level and awarding organisation, trending categories and sources, a linear projection, and highlights for strengths and gaps
- `GET /analytics/university/compliance`: counts only — privacy notice acknowledgements, `ANALYTICS` consents, erasure requests, legal holds, the last retention run, open and overdue reviews — and an overall status with the reasons, read live
- Permission `analytics:read:university` (rector's secretary, rector), not delegable
- The unit aggregates of 5.2 serve the university (same cache); the previous period's sources are added to them
- Page `/analytics/university` with a period choice, tiles with deltas, the trend with the projection, the faculty comparison, categories and levels, sources, trends, highlights and the compliance panel; navigation entry "University analytics"
- Board report: `GET /analytics/university/report` (PDF, A4 landscape, one topic per page, bar charts drawn in the template), audited, with a "Board report" button on the page
- User guide "University analytics" with the interpretation of each figure, the projection and the compliance reasons (US-010 DoD "executive guide, strategic interpretation")

**Out (where it goes)**

- Recommendations engine and forecasting models: not planned (tracker deviation 5); four plus one fixed highlight rules and a least-squares line over complete years, both labelled as such
- Top performers at university level: not planned (deviation 3)
- CSV and Excel of the university figures: not planned; the faculty exports of 5.2.3 stay, the board report is PDF only (deviation 9)
- Colleges as analysable units: not planned; a college appears as a row of the university comparison only (deviation 4)
- Compliance records (who asked for erasure, which holds): stay on the GDPR officer's compliance page (Epic 6)
- Live push of the compliance status: not planned; it is read at each page load and on "Refresh" (deviation 7)
- Cross-university comparison: not planned (ADR-022, one tenant)

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-104 (#291) | 5.3.1 University analytics API with trends and a compliance summary | 8 | no | — |
| SCRUM-105 (#292) | 5.3.2 Executive dashboard | 5 | yes | 5.3.1 (`/analytics/university*`) |
| SCRUM-106 (#293) | 5.3.3 Presentation-ready board report | 3 | no | 5.3.1 (service); button after 5.3.2 |

5.3.1 is backend only. 5.3.2 is frontend only and can start from the stubs while 5.3.1 is built. 5.3.3 has a backend part (template, endpoint, audit) and a small UI part (button); it splits at the green backend gate.

## 4. Acceptance criteria

Common definitions: the **counting rule**, the **period**, the **previous period**, **members** and `Figure` are those of PRD 5.2 §4, applied to the university's subtree (every unit of the tree). The **comparison units** are the active direct children of the university of type `FACULTY` or `COLLEGE`, plus an inactive one with counted awards, plus a **university-level** row for awards submitted at the university itself and its members (the rector, the administrator). A **complete year** is a year of the period before the current Kyiv year.

### 5.3.1 University analytics API with trends and a compliance summary (SCRUM-104)

- **AC-1.1** Given the permission matrix, when the roles are inspected, then `analytics:read:university` is held by `RECTOR_SECRETARY` and `RECTOR` only and is not delegable; `RolePermissions`, AUTHENTICATION_AUTHORIZATION §3.3 and RBAC_matrix §4 ("View University Analytics", with a footnote naming the compliance summary) agree.
- **AC-1.2** Given `rector` or `rector.secretary`, when they call `GET /analytics/university`, then they receive 200 `UniversityAnalytics` with the university (`OrganizationRef`), the period, the previous period, `partialYear` and `generatedAt`; `dean.fmi`, `employee.fmi`, `gdpr`, `admin` and a delegate of the rector receive 403, a call without a token 401; invalid years answer 400 `invalid-parameter` as in AC-1.3 of 5.2.
- **AC-1.3** Given approved, pending, rejected and draft awards in several faculties, colleges and at the university itself, when the analytics are read, then `totals`, `byYear`, `byCategory`, `byLevel` and `bySource` follow AC-1.4 and AC-1.6 of 5.2 over the whole university, and only counted awards appear anywhere in the response.
- **AC-1.4** Given the comparison units, when the analytics are read, then `units` lists one `UnitComparisonRow` per faculty and college (by Ukrainian name) and, when it has members or counted awards, the university-level row last (`facultyLevel` true), with the fields of AC-1.5 of 5.2 over each subtree; the rows' approved counts sum to the university total.
- **AC-1.5** Given counted awards in both periods, when the analytics are read, then `trends.categories` and `trends.sources` each hold `rising` and `falling`: up to 5 entries whose current plus previous count is at least 3, with `current`, `previous`, `delta` and `deltaPercent`, `rising` by largest positive delta, `falling` by largest negative delta (ties by name); sources are grouped as in `bySource` and over both periods.
- **AC-1.6** Given the period, when the analytics are read, then `projection` is `PROJECTED` when the period has at least 3 complete years, with `basis` (the complete years), `slope` (awards per year from a least-squares line over their totals, one decimal), `year` (the year after `basis.to`) and `estimate` (the line's value for that year, rounded, never below 0); with fewer complete years `status` is `TOO_FEW_YEARS` and the other fields are null.
- **AC-1.7** Given the rows of AC-1.4, when the analytics are read, then `highlights` applies the four rules of AC-1.8 of 5.2 to the faculty and college rows with the university as the average, plus `ABOVE_AVERAGE` (awards per member at least 1.5 times the university's, rows with at least the minimum group of members); the university-level row gets no highlight; no person is named anywhere in the response.
- **AC-1.8** Given the same period read twice within `app.analytics.unit-cache-ttl`, when the second request runs, then the aggregates come from the cache `unit-analytics` (same `generatedAt`) under the university's id; the cached entry also holds the previous period's sources; with Redis stopped the analytics are computed and returned with a warning in the log.
- **AC-1.9** Given 100 000 approved awards over 10 faculties and 3 000 members, when the university analytics are read without the cache, then the response arrives within 3 s in the IT (US-010 DoD: under 10 s) and the plan reads `awards` through an index, not a sequential scan.
- **AC-1.10** Given `rector` or `rector.secretary`, when they call `GET /analytics/university/compliance`, then they receive 200 `ComplianceSummary`, computed at the request and never cached (`generatedAt` the request time), with: `privacyNotice` (current version, `ACTIVE` accounts, those whose latest `DATA_PROCESSING` row acknowledges that version), `analyticsConsent` (`ACTIVE` accounts, those whose latest `ANALYTICS` row is a grant), `erasure` (`PENDING` requests, of them postponed, of them overdue: not postponed and `scheduled_for` more than one day past), `legalHolds.active`, `lastRetentionRun` (status, `startedAt`, `finishedAt`, null when none) and `reviews` (open and overdue requests per `ApprovalLevel` and in total, as the overdue gauges count them); access follows AC-1.2.
- **AC-1.11** Given the summary, when its `status` is computed, then it is `ATTENTION` with the codes in `attention` when any of these hold, else `OK` with none: `RETENTION_NOT_RUN` (no run), `RETENTION_FAILED` (last run `PARTIAL` or `INTERRUPTED`), `RETENTION_STALE` (last run started more than `app.analytics.retention-stale-after`, `P2D`, ago), `ERASURE_OVERDUE` (overdue erasure requests), `REVIEWS_OVERDUE` (overdue reviews); no id, name or e-mail appears in the response.
- **AC-1.12** Given the module rules, when `ModularityTest` runs, then it passes: `analytics` reads the compliance counts through a new `gdpr` exposed type `ComplianceFigures` and the review counts through a new `award` exposed type `ReviewBacklog` (the counting query of `OverdueNotices`, shared), the tree through `OrganizationTree`; ADR-022 lists the new exposed types; the class fan-out stays ≤ 25.

### 5.3.2 Executive dashboard (SCRUM-105)

- **AC-2.1** Given an account with `analytics:read:university`, when it opens the navigation, then "University analytics" leads to `/analytics/university`; other accounts see no entry and get `/forbidden` on the direct URL; the rector also keeps "Unit analytics".
- **AC-2.2** Given the page, when it renders, then the period selector of 5.2 (kept in the URL as `?from=&to=`) reloads every widget except the compliance panel, and a line states the previous period and, when `partialYear`, that the current year is not over.
- **AC-2.3** Given the analytics, when the page renders, then it shows in this order: the compliance panel; tiles for approved awards and awarded people with their delta, unit awards, verified and awards per 100 members (the delta tiles of 5.2.2); the trend by year with the projection as a dashed segment to the projected year and a sentence ("Projected for 2026: about 140 awards, based on 2022–2025") or "Too few complete years for a projection"; the faculty comparison; by category and by level; by awarding organisation; trends; highlights.
- **AC-2.4** Given the faculty comparison, when it renders, then the comparison table of 5.2.2 lists the faculties and colleges with "University level" for the university row, sortable by every column; a faculty name opens `/analytics/units/{id}` with the same period, a college name is plain text; a bar chart shows approved awards per 100 members per row.
- **AC-2.5** Given the trends, when they render, then "Rising" and "Falling" lists for categories and for awarding organisations show each entry with the two counts and the signed delta; an empty list reads "No category changed noticeably." or "No awarding organisation changed noticeably."
- **AC-2.6** Given the highlights, when they render, then strengths (`GROWTH`, `ABOVE_AVERAGE`) and improvement opportunities (`DECLINE`, `BELOW_AVERAGE`, `NO_AWARDS`) appear in two groups as sentences naming the faculty and the counts; with none, "Nothing stands out in this period."
- **AC-2.7** Given the compliance summary, when the panel renders, then it shows "Everything is in order" or "Needs attention" with one sentence per reason, tiles for the notice acknowledgements (count and percent), the analytics consents, pending, postponed and overdue erasure requests, active legal holds, the last retention run (status and time, or "No run yet") and open and overdue reviews; an "As of" time and a "Refresh" button that reloads the panel only; a failed panel load shows its own error with Retry while the rest of the page stays.
- **AC-2.8** Given any chart, failure and invalid period, when the page renders, then it uses the chart wrapper of 5.1.2 with "Show as table" (the projected point marked in the table), a failed load shows the error with Retry and an invalid period "The period is not valid" as in 5.2.2.
- **AC-2.9** Given 360 px and 1280 px, uk and en, light and dark, when the page is screen-checked, then nothing scrolls horizontally except the comparison table inside its container, texts are translated and axe reports no serious violation; the page is usable within 10 s with the seed (Playwright; US-010 DoD); the user guide has "University analytics" with the counting rule, the trends threshold, the projection and its limits, the five highlight rules and each compliance reason.

### 5.3.3 Presentation-ready board report (SCRUM-106)

- **AC-3.1** Given `rector` or `rector.secretary`, when they call `GET /analytics/university/report?from=&to=&lang=uk|en`, then they receive `application/pdf` with `Content-Disposition: attachment; filename="board-report-<code>-<from>-<to>.pdf"` and `Cache-Control: no-store`; access and 400 follow AC-1.2; an unknown `lang` answers 400 `invalid-parameter`.
- **AC-3.2** Given the report, when it is opened, then it is A4 landscape with the embedded font of 5.2.3, page numbers and one topic per page: title (university, period, previous period, produced at, produced by), key figures with deltas, trend by year with the projection, faculty comparison (table and bars per 100 members), categories and levels (current vs previous), trending categories and sources, strengths and improvement opportunities, compliance snapshot; every figure equals `GET /analytics/university` and `/compliance` at that moment; it fits in at most 10 pages with the seed.
- **AC-3.3** Given the charts of the report, when it is rendered, then the bars are drawn by the Thymeleaf template (CSS widths over the largest value, labelled with the counts), so no chart library or image renderer is added; long faculty names wrap without overflowing; the labels follow `lang`.
- **AC-3.4** Given a report, when it completes, then one `ANALYTICS_EXPORTED` audit row is written (`entity_type` `ANALYTICS`, `entity_id` the university, `new_values` with format `board-pdf`, period and language, people named 0); a refused request writes none.
- **AC-3.5** Given the page of 5.3.2, when the figures are shown, then a "Board report" button downloads the PDF for the current period and interface language; it is disabled while loading, after a failed load and for an invalid period; a failed download shows "The report could not be exported." without leaving the page.

## 5. Edge cases

- **Period**: as in 5.2 (default five years ending at the current year). A period ending last year has no partial year, so every year counts for the projection; `from` = `to` = current year gives `TOO_FEW_YEARS`.
- **Projection**: based on complete years only, so the current year's partial count never drags the line down; the estimate is a guide value, not a forecast, as the sentence and the guide say. A falling line is clamped at 0.
- **Trends noise**: entries with fewer than 3 awards over both periods are left out; a category new in the period shows `deltaPercent` null and reads "new".
- **Colleges**: direct children of the university, counted and compared like faculties; their pages do not exist (`/analytics/units` lists faculties and departments only), so the name is not a link.
- **University level**: awards submitted at the university itself (the rector's own, unit awards of the university) form the last row and no highlight.
- **Inactive units**: as in 5.2, a row only with counted awards, marked inactive.
- **Cache**: the university shares the `unit-analytics` cache under its id; entries written before the deployment of 5.3.1 lack the previous sources and fail to read, which the cache error handler treats as a miss (logged once); within 5 minutes they are gone.
- **Compliance live**: the summary is read at each load and on "Refresh"; the retention job runs daily at 04:00 (`APP_RETENTION_CRON`), so `P2D` tolerates one missed night before `RETENTION_STALE`; a run still `RUNNING` is judged by its start time.
- **Compliance privacy**: counts only; a count of 1 (one pending erasure) can point to a person only for someone who already knows of the request, and no id, date of a person or reason is returned; the GDPR officer's page keeps the records.
- **Authorization**: held scopes do not matter (the permission is university-wide by role); delegations never carry analytics; role revocation ends sessions (existing behaviour).
- **Board report**: synchronous, aggregates only, no names; produced from the same service calls as the page, so figures can differ from an open page only by the cache age (`generatedAt` printed); the compliance snapshot is the moment of production.
- **External**: Redis down: computed without the cache; the PDF font is bundled.

## 6. Dependencies

### Tables

| Table | Change |
|-------|--------|
| `awards`, `award_categories`, `users`, `organizations` | Read only (5.3.1) |
| `consent_records`, `erasure_requests`, `legal_holds`, `retention_runs`, `award_requests` | Read only, counts (5.3.1) |
| `audit_logs` | `ANALYTICS_EXPORTED` with format `board-pdf` (5.3.3); no migration |

No migration. `idx_awards_organization_status` serves a faculty subtree; the university subtree is every organisation, so the counted rows are about half of `awards` (55 000 of 102 000 in the AC-1.9 load) and PostgreSQL reads the table sequentially, which is cheaper than any index there; the `(status, award_date)` partial index would not change that plan and is not added. AC-1.9 is held by the response time: under 3 s for 100 000 approved awards of 3 000 members, without the cache (5.3.1).

### Endpoints

| Endpoint | State |
|----------|-------|
| `GET /analytics/university` | New (5.3.1) |
| `GET /analytics/university/compliance` | New (5.3.1) |
| `GET /analytics/university/report` | New (5.3.3) |
| `GET /analytics/units/{id}` | Unchanged; target of the faculty links |

### Services

- `analytics`: `UniversityAnalyticsService` (period, figures, rows, trends, projection, highlights), `TrendRules` (rising and falling), `ProjectionHelper` (least squares), `UniversityComplianceService` (status and reasons), `UniversityAnalyticsController`; `UnitAggregates` gains the previous sources; `UnitComparison.rows` generalised to a parent and its child types; `HighlightRules` gains `ABOVE_AVERAGE` for the university; `analytics.export`: `BoardReportExporter` (audit), template `report/board.html`, labels in `ReportLabels`
- `gdpr`: exposed `ComplianceFigures` (implemented by `ComplianceQueries` or a new `ComplianceCounts`)
- `award`: exposed `ReviewBacklog` (open and overdue per level; `OverdueNotices` reuses it for the gauges)
- `authz`: `RolePermissions` (one permission, not delegable)
- `common/privacy`: `PrivacyNoticeVersions` (current version)

### Frontend

- `features/analytics/university` (route `/analytics/university` under the lazy `/analytics` routes): `university-analytics` page, `projection-trend-chart`, `trend-lists`, `compliance-panel`, `board-report-button` (5.3.3); NgRx feature `universityAnalytics` (period, data, compliance, load states)
- Reuse from 5.2.2: `delta-tile`, `unit-comparison` (row label and link rule as inputs), `comparison-bars`, `source-chart`, `highlights` (strength and gap groups, the new type), `period-select`, chart wrapper
- `core/layout/shell`: "University analytics" entry; `core/auth`: `universityAnalyticsGuard`
- i18n keys under `universityAnalytics.*`

### External systems

None. No new library: the board report uses openhtmltopdf and Noto Sans of 5.2.3.

## 7. Technical decisions

Inherited: tracker decisions of 2026-10-19 (approved awards only, `ANALYTICS` for naming, SQL on demand with a short Redis TTL) and 2026-10-23 (awards count in the unit of submission, aggregates cached with names applied per request, one export component), ADR-022, ADR-015, ADR-014 with the chart addendum, ADR-005, PRD 5.2 deviations 3, 6, 7, 8 and 10.

**Proposed deviations and assumptions**

1. **Own permission and endpoint for the university** (tracker deviation 2): `analytics:read:university` for the rector and the rector's secretary; `/analytics/units` keeps listing faculties and departments, so the 5.2 contract and its 404 rule stay as they are.
2. **Compliance summary under the analytics permission, counts only** (tracker deviation 4): `compliance:read` stays with the GDPR officer; the executive sees the state of the duties, not the records. "University policies" of US-010 read as the review deadlines (4.2.2), the only policy the system enforces with a measurable deadline.
3. **No people at university level**: the rector reads named top performers per faculty; a university list would rank people across units without a purpose US-010 states.
4. **Colleges compared, not opened**: they are direct children of the university with their own awards; making them analysable units would change the 5.2 unit list and scope rules for a demo with seven colleges.
5. **Trends as period-over-period deltas** with a minimum of 3 awards and 5 entries per direction; no significance test (tracker deviation 5).
6. **Projection by least squares over complete years, at least 3**, labelled as an estimate (tracker deviation 5: "forecasting" scoped to a linear projection).
7. **Live compliance by request, not push**: read at each load and on "Refresh", never cached; no WebSocket for one panel.
8. **Strengths and opportunities by five fixed rules**: the four of 5.2 plus `ABOVE_AVERAGE`, which only the university view returns (the faculty page never receives it); "recommendations engine" of the DoD scoped down (tracker deviation 5).
9. **Board report as a landscape PDF only**, from a second Thymeleaf template on the 5.2.3 component, bars drawn in HTML and CSS: no SVG renderer (Batik) or chart image pipeline; PowerPoint is out, a PDF is shown on any projector.
10. **The university shares the unit cache**: `UnitAggregates` keyed by the university's id; the previous sources are one more query in the same entry.
11. **Board reports are audited** as `ANALYTICS_EXPORTED` (format `board-pdf`), like the unit exports, though they name nobody: the file leaves the system.
12. **Points**: 5.3.1 goes from 5 to 8 for the compliance summary across two modules and the generalised comparison next to the aggregation; the feature totals 16, the epic 55. Keeping 5 would mean moving the compliance summary to its own story.

## 8. Contract

### 8.1 OpenAPI stubs

Written to `docs/api/openapi.yml` in this PRD's PR, each with `x-status: planned` and the story in `description`; tag `Analytics`.

| Operation | Request | Response | Problems |
|-----------|---------|----------|----------|
| `GET /analytics/university` (`getUniversityAnalytics`, 5.3.1) | query `from`, `to` (integer 1950..2100, optional) | 200 `UniversityAnalytics` | 400 `invalid-parameter` (`from`, `to`); 401; 403 |
| `GET /analytics/university/compliance` (`getComplianceSummary`, 5.3.1) | — | 200 `ComplianceSummary` | 401; 403 |
| `GET /analytics/university/report` (`getBoardReport`, 5.3.3) | `from`, `to` as above; `lang` (enum `uk`, `en`, default `uk`) | 200 `application/pdf` (binary) | 400 `invalid-parameter` (`lang`, `from`, `to`); 401; 403 |

Schemas:

- `UniversityAnalytics` { `unit` (`OrganizationRef`), `period`, `previousPeriod` (`YearPeriod`), `partialYear`, `generatedAt`, `totals` (`UnitTotals`), `byYear`, `units` (`UnitComparisonRow`[]), `byCategory`, `byLevel`, `bySource` (as `UnitAnalytics`), `trends` (`AnalyticsTrends`), `projection` (`TrendProjection`), `highlights` (`UnitHighlight`[]) }
- `AnalyticsTrends` { `categories` { `rising`, `falling`: `CategoryTrend`[] }, `sources` { `rising`, `falling`: `SourceTrend`[] } }
- `CategoryTrend` { `category` (`AwardCategoryRef`), `current`, `previous`, `delta`, `deltaPercent` (nullable) }; `SourceTrend` { `name`, `current`, `previous`, `delta`, `deltaPercent` (nullable) }
- `TrendProjection` { `status`: enum `PROJECTED`, `TOO_FEW_YEARS`; `basis` (`YearPeriod`, nullable), `slope` (number, nullable), `year` (nullable), `estimate` (nullable) }
- `UnitHighlight.type` gains `ABOVE_AVERAGE` (university only)
- `ComplianceSummary` { `generatedAt`, `status`: enum `OK`, `ATTENTION`; `attention`: enum[] `RETENTION_NOT_RUN`, `RETENTION_FAILED`, `RETENTION_STALE`, `ERASURE_OVERDUE`, `REVIEWS_OVERDUE`; `privacyNotice` { `version`, `accounts`, `acknowledged` }; `analyticsConsent` { `accounts`, `granted` }; `erasure` { `pending`, `postponed`, `overdue` }; `legalHolds` { `active` }; `lastRetentionRun` { `status` (`RetentionStatus`), `startedAt`, `finishedAt` (nullable) } nullable; `reviews` { `open`, `overdue`, `byLevel`: [{ `level` (`ApprovalLevel`), `open`, `overdue` }] } }

### 8.2 Migration outlines

None planned (see §6 Tables for the conditional index of AC-1.9).

### 8.3 Properties

| Property | Default | Story |
|----------|---------|-------|
| `app.analytics.retention-stale-after` (`ANALYTICS_RETENTION_STALE_AFTER`) | `P2D` | 5.3.1 |
| `app.analytics.unit-cache-ttl`, `min-group-size` | `PT5M`, 5 (existing) | 5.2.1, 5.1.1 |

## 9. Test plan

| AC | Unit | IT | FT | E2E / other |
|----|------|----|----|-------------|
| 1.1 | ✓ permission set, not delegable | | ✓ 403 for dean, employee, GDPR officer, administrator, delegate | doc review |
| 1.2 | ✓ period | | ✓ 200 rector and secretary, 400, 401 | |
| 1.3, 1.4 | ✓ rows of faculties and colleges, university level, sums | ✓ `UnitStatistics` over the whole tree on Testcontainers | ✓ response shape on the seed | |
| 1.5 | ✓ thresholds, ordering, ties, limit, grouping over both periods | | | |
| 1.6 | ✓ least squares, rounding, clamp, complete years, `TOO_FEW_YEARS` | | | |
| 1.7 | ✓ `ABOVE_AVERAGE` edge, university row excluded | | ✓ no person field in the response | |
| 1.8 | ✓ error handler on an old entry | ✓ cache hit (`generatedAt`), Redis stopped | | |
| 1.9 | | ✓ 100 000 awards, time (plan: §6) | | |
| 1.10, 1.11 | ✓ each reason and its edge (`P2D`, one day past) | ✓ counts on Testcontainers (versions, latest row, postponed, overdue) | ✓ live after a change, no ids in the body | |
| 1.12 | | ✓ `ModularityTest` | | doc review, gate static |
| 2.1–2.8 | ✓ components, store, guard, projection sentence, trend lists, compliance sentences, refresh | | | ✓ Playwright: rector page, period, faculty drill-down, college not linked, compliance refresh, forbidden for dean, invalid period |
| 2.9 | | | | screen check 360/1280, uk/en, light/dark, axe; timing; doc review |
| 3.1, 3.4 | ✓ file name, audit details | | ✓ headers, media type, 400/403, audit row | |
| 3.2, 3.3 | ✓ model of the template (sections, bar widths, labels) | ✓ PDF parsed back (page count, landscape, Ukrainian text, figures) | | PDF checked by eye |
| 3.5 | ✓ button states | | | ✓ Playwright: download |

Coverage 85 % lines; static clean. Security review for 5.3.1 (permission, compliance counts without personal data) and 5.3.3 (export, audit).

## 10. Manual verification

Expected texts are the merged English i18n texts (checked at the validation, 2026-10-25). Local e2e runs register accounts in the e2e unit, so the figures grow over time; compare with psql rather than with fixed numbers.

Preconditions: `docker compose up -d postgres redis mailpit minio clamav`; backend `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`; frontend `npm start` on `http://localhost:4200`. psql `docker compose exec postgres psql -U postgres award_monitoring`; Swagger `http://localhost:8080/swagger-ui.html`; Redis `docker compose exec redis redis-cli`. Seed accounts (password `Passw0rd-demo`, `@chnu.edu.ua`): `rector`, `rector.secretary`, `dean.fmi`, `secretary.fmi`, `employee.fmi`, `lecturer.fmi`, `admin`, `gdpr`.

1. psql: approved awards for 2022–2026 and 2017–2021 per direct child of the university (faculties and colleges, subtree), at the university itself, and the `ACTIVE` members per child. Expected: ФМІ with awards in both periods; the university total equals the sum. (AC-1.3, 1.4)
2. As `rector`, Swagger `GET /analytics/university`. Expected: 200; period 2022–2026, previous 2017–2021, `partialYear` true; `totals.approved.current` and the `units` rows equal step 1; colleges among the rows; "University level" row last when it has members or awards. As `rector.secretary`: the same body. (AC-1.2–1.4)
3. Same response: `trends`: compute the category deltas from `byCategory` and check `rising`/`falling` against the rule (≥ 3 over both periods, up to 5, order). (AC-1.5)
4. Same response: `projection` `PROJECTED`, basis 2022–2025, `year` 2026; check the slope and estimate with a least-squares fit of the four `byYear` totals (psql `regr_slope`/`regr_intercept`). Then `?from=2025&to=2026`: `TOO_FEW_YEARS`. (AC-1.6)
5. Same response: `highlights` against the rules with the counts of step 1 (university awards per member as the average); no `userId`, name or e-mail anywhere (search the JSON). (AC-1.7)
6. Call it again. Expected: same `generatedAt`; `redis-cli keys '*unit-analytics*'` shows the university's entry. (AC-1.8)
7. `GET /analytics/university/compliance`. Expected: `privacyNotice.version` 1.0 and counts equal to psql over `consent_records` (latest row per account), `analyticsConsent`, `erasure`, `legalHolds`, `lastRetentionRun`, `reviews` equal to psql; `generatedAt` now; status and `attention` follow AC-1.11. (AC-1.10, 1.11)
8. Make the summary change: as `employee.fmi` request erasure on `/privacy` (cancel it afterwards); as `gdpr` place a legal hold on `lecturer.fmi` (lift it afterwards). Call compliance after each. Expected: `erasure.pending` and `legalHolds.active` change at once, no cache. (AC-1.10)
9. psql: `update erasure_requests set scheduled_for = now() - interval '2 days' where request_id = <the pending one>;` then compliance. Expected: `ATTENTION` with `ERASURE_OVERDUE`; cancel the request afterwards. Also `update retention_runs set started_at = now() - interval '3 days' where run_id = (select max(run_id) from retention_runs);` → `RETENTION_STALE` (restore the value). (AC-1.11)
10. `GET /analytics/university?from=2027`, `?from=2026&to=2021`, `?from=abc`. Expected: 400 `invalid-parameter` naming the parameter. (AC-1.2)
11. Open `http://localhost:4200` as `rector`, navigation "University analytics". Expected: `/analytics/university` with the compliance panel first, the comparison line "2022–2026, compared with 2017–2021." and the partial-year note "2026 is not over yet, so its figures are still growing.", tiles with deltas, the trend with a dashed projection and "Projected for 2026: about … awards, based on 2022–2025.", the faculty comparison, categories and levels, sources, trends, highlights. "Unit analytics" is still in the navigation. (AC-2.1–2.3)
12. Sort the comparison by "Per 100 members", open ФМІ from the table. Expected: the order follows the column; `/analytics/units/9` with the same period; a college row is plain text. (AC-2.4)
13. Choose 2024–2026. Expected: `?from=2024` (`to` stays out of the URL while it is the default year), compared with 2021–2023, every widget reloaded, the compliance "As of" time unchanged. (AC-2.2)
14. Trends and highlights: the lists match steps 3 and 5; an empty list shows its sentence (choose a single past year such as `?from=2019&to=2019`). (AC-2.5, 2.6)
15. Compliance panel: compare with step 7; repeat the change of step 8 in another tab and press "Refresh". Expected: the panel changes, the "As of" time moves, the rest of the page does not reload. (AC-2.7)
16. "Show as table" on the trend. Expected: the yearly totals and a projected row marked as such. (AC-2.8)
17. "Board report" in English and in Ukrainian. Expected: `board-report-<code>-2022-2026.pdf`; A4 landscape, page numbers, one topic per page, bars with counts, Ukrainian text in both (unit names), the figures equal the page and step 7, at most 10 pages. (AC-3.1–3.3, 3.5)
18. psql `select action_type, entity_id, new_values from audit_logs where action_type = 'ANALYTICS_EXPORTED' order by log_id desc limit 2;`. Expected: one row per report, format `board-pdf`, the period, the language, 0 named. (AC-3.4)
19. Screen check of `/analytics/university` at 360 and 1280 px, uk/en, light/dark. Expected: no page-level horizontal scroll, the table scrolls in its container, no serious axe finding. (AC-2.9)
20. Run the Playwright spec of 5.3.2 and note the time to a usable page. Expected: under 10 s. (AC-2.9)

### Detours

21. Direct URL `/analytics/university` as `dean.fmi`, `secretary.fmi`, `employee.fmi`, `admin` and `gdpr`. Expected: no navigation entry, `/forbidden`; `GET /analytics/university`, `/compliance` and `/report` → 403. (AC-1.1, 1.2, 2.1)
22. Direct URLs `/analytics/university?from=2027` and `?from=2026&to=2021`. Expected: "The period is not valid." with a way back; "Board report" disabled; the compliance panel still loads. (AC-2.8, 3.5)
23. Reload and use back/forward after changing the period twice. Expected: each history entry restores its period. (AC-2.2)
24. Stop the backend while the page is open, press "Refresh" on the panel, then change the period; start it again and press Retry. Expected: the panel's own error with Retry, then the page error with Retry; "Board report" disabled while the error shows; both fill after Retry. (AC-2.7, 2.8, 3.5)
25. Let the access token expire with the page open, then press "Refresh" and "Board report". Expected: silent refresh, the panel reloads and the file downloads. (AC-2.7, 3.5)
26. Second browser: `rector.secretary` opens the page while `rector` has it open; both export a board report. Expected: two files, two audit rows with their own user ids. (AC-3.4)
27. Stop Redis and reload. Expected: the page loads (cache warning in the backend log); start Redis again. (AC-1.8)
28. Hidden in state, board report: during loading, after a failed load and for an invalid period the button is disabled; `GET /analytics/university/report?lang=de` → 400; as `dean.fmi` → 403. (AC-3.1, 3.5)
29. Hidden in state, refresh: while the panel loads "Refresh" is disabled; a double click sends one request (network tab). (AC-2.7)
30. Hidden in state, projection: `?from=2026&to=2026` shows "Too few complete years for a projection." and no dashed segment; the board report says the same. (AC-1.6, 2.3)
31. Revoked mid-flow, role: `admin` revokes the RECTOR role of `rector` while the page is open and a board report is about to be requested. Expected: the revocation ends the rector's sessions; the next request leads to the sign-in page; after signing in "University analytics" is gone and the report URL answers 403; `rector.secretary` keeps the page unchanged. Re-grant the role. (AC-1.1, 2.1, 3.1)
32. Revoked mid-flow, delegation: `rector` delegates approval to `dean.fmi`. Expected: `dean.fmi` still gets 403 on `/analytics/university` and no entry (not delegable); revoking the delegation changes nothing for analytics. (AC-1.1)
33. Revoked mid-flow, consent: `lecturer.fmi` withdraws analytics while `rector` has the page open. Expected: no name on the page in either state; `analyticsConsent.granted` drops by one after "Refresh". (AC-1.7, 1.10)
34. Approve a new award in a faculty and reload at once, then after 5 minutes. Expected: unchanged figures with the old `generatedAt`, then the new count; the compliance `reviews.open` drops at once. (AC-1.8, 1.10)
35. Run the retention job off schedule (restart with `APP_RETENTION_CRON` one minute ahead), then press "Refresh". Expected: `lastRetentionRun` shows the new run; a stale reason clears. Restore. (AC-1.10, 1.11)
36. Repeated one-time action: double-click "Board report", then press back and forward. Expected: one request in the network tab, one file, one new `ANALYTICS_EXPORTED` row; history does not download again. The same button in two tabs gives two files and two rows. (AC-3.4, 3.5)
37. Redis down during the export: stop Redis, press "Board report". Expected: the PDF downloads, a cache warning in the backend log, one audit row; start Redis again. (AC-1.8, 3.4)
38. Server restart mid-export: stop the backend right after pressing "Board report". Expected: "The report could not be exported.", no new audit row (the row is written after the file); after the restart the button works. (AC-3.4, 3.5)
39. Session gone: sign out in the open page (a sign-out in a second browser session ends only that session, so the first page keeps working), then press Back and sign in as `dean.fmi`. Expected: the sign-in page; Back shows no figures; `dean.fmi` reaches `/forbidden` without any figures, and no empty file is saved. (AC-2.7, 3.5)

Not turned into steps (findings of the scenario review): the report endpoint has no rate limit, like the 5.2 exports (each call renders a PDF and writes an audit row; the button allows one call at a time per page); the retention cron `0 0 4 * * *` in Europe/Kyiv lies outside the 03:00–04:00 DST window, and `RETENTION_STALE` uses a fixed 48 h, so neither DST change trips it; the default period is resolved on the server in Europe/Kyiv, and a tab left open over New Year shows the old year until it reloads.

### Run 2026-10-25

compose stack rebuilt from develop 70236ab; defaults except APP_RETENTION_CRON for step 35 (restored); Playwright via walk.cjs and MCP. Result: 39 pass.

| Step | Result | Evidence |
|---|---|---|
| 1 | PASS | psql per child via organization_id subtree: current 470 / previous 57, FG 10/20, FMI 460/37, rest 0; ChNU direct 0; members 132+1955+3+4=2094 ACTIVE |
| 2 | PASS | 200 rector; period 2022-2026, previous 2017-2021, partialYear true; totals 470/57 and unit rows equal step 1; ChNU row last; rector.secretary body byte-identical |
| 3 | PASS | category rising 4 / falling 1 (University Excellence Award 21 vs 28) equal the rule recomputed from byCategory |
| 4 | PASS | PROJECTED basis 2022-2025 slope 1.3 estimate 20 = psql regr 1.30 / 20.0; from=2025&to=2026 TOO_FEW_YEARS |
| 5 | PASS | highlights NO_AWARDS FPPSW, DECLINE+BELOW_AVERAGE FG 10/20, GROWTH FMI 460/37 = rules over step 1 (avg 22.4/100); no userId/email/name keys in JSON |
| 6 | PASS | second call same generatedAt 17:11:40.113Z; redis key awardapp:unit-analytics::1:2022:2026 |
| 7 | PASS | privacyNotice 1.0 2094/2094, analytics 34/2094, erasure 1/0/0, holds 31, last run 99 COMPLETED, reviews 95/71 per level = psql and awards_review_* gauges; ATTENTION [RETENTION_STALE, REVIEWS_OVERDUE] (run 7 days old); generatedAt 17:13 = request time |
| 8 | PASS | employee.fmi erasure request (API): pending 1->2 in the next call; hold on lecturer.fmi (966): active 31->32 at once; both undone (cancel 204, lift 200): 1 / 31 |
| 9 | PASS | request 76 scheduled_for -2d: ERASURE_OVERDUE, overdue 1; retention run 99 started -1d clears RETENTION_STALE, -3d sets it; values restored, request cancelled |
| 10 | PASS | 400 invalid-parameter for from=2027, from=2026&to=2021, from=abc; body field parameter=from |
| 11 | PASS | rector: nav has University analytics + Unit analytics; /analytics/university sections in order Compliance, Summary, Awards by year, Faculty and college comparison, categories/levels/sources, Trends, Highlights; comparison line, partial-year note and 'Projected for 2026: about 20 awards, based on 2022–2025.' present in en (after reload, see defect on language switch) |
| 12 | PASS | sort by Per 100 members asc/desc reorders rows (FMI 23.5 first desc); FMI link /analytics/units/9?from=2022&to=2026; Applied College and University level rows plain text |
| 13 | PASS | step text corrected: choosing from=2024 gives ?from=2024 (to omitted while it is the default year); 'compared with 2021–2023', one request /analytics/university?from=2024 and none to compliance, As of 19:20 unchanged, approved 439 |
| 14 | PASS | rising 4 / falling 1 categories and highlights (GROWTH FMI, NO_AWARDS FPPSW, DECLINE+BELOW FG) = steps 3 and 5; ?from=2019&to=2019 shows 'No category changed noticeably.' and 'No awarding organisation changed noticeably.' |
| 15 | PASS | second change via API, Refresh: Pending erasure requests 1->2, As of 19:21->19:22, only GET /analytics/university/compliance sent; request cancelled after |
| 16 | PASS | Show as table: yearly totals 2022-2026 (15,16,17,19,403) and row '2026 — about 20 (projected)' |
| 17 | PASS | board-report-ChNU-2022-2026.pdf uk 8 pages 36 KB, en 8 pages 38 KB; MediaBox 841.9x595.3 (A4 landscape); 'Page n of 8'; Ukrainian unit names in both; totals 470/57/2094 and compliance 2094 of 2094, 34 of 2094, 95/71 equal the API |
| 18 | PASS | two ANALYTICS_EXPORTED rows (entity 1): board-pdf 2022–2026 language uk and en, peopleNamed 0 |
| 19 | PASS | check.cjs rector /analytics/university 1280+360, light+dark uk, 1280/360 light en: scrollWidth = viewport, axe serious+ 0, console errors 0; 360 en dark scrollWidth 360, table scrolls in .comparison__scroll; separate defect SCRUM-112 (#319): raw i18n keys after switching to English without reload |
| 20 | PASS | e2e.ps1 -Grep 'university analytics' -Workers 1: 8 passed 0 failed in 1 min; the usable-page assertion (< 10 s) is in the spec |
| 21 | PASS | dean.fmi, secretary.fmi, employee.fmi, admin, gdpr: no University analytics entry, /forbidden; all 3 endpoints 403 for each (15 calls); without token 401 |
| 22 | PASS | ?from=2027 and ?from=2026&to=2021: 'The period is not valid.', 'All years' button, Board report disabled, compliance panel (As of) loaded |
| 23 | PASS | from=2023 then to=2025: back -> ?from=2023, back -> default, forward -> ?from=2023, reload keeps ?from=2023 (2023–2026 vs 2019–2022) |
| 24 | PASS | backend stopped: Refresh -> 'Не вдалося завантажити зведення відповідності.' + retry, period change -> 'Не вдалося завантажити аналітику університету.' + retry, Board report disabled; backend started, one Спробувати ще раз filled panel (As of 19:52) and page, Board report enabled |
| 25 | PASS | page open since 17:34 (token expires 17:49:35): at 17:50:24 Refresh -> compliance 200, As of 19:50, Board report -> 200 and board-report-ChNU-2022-2026.pdf saved, no sign-in page; oauth2_authorization shows a token issued 17:45:38 (renewed silently) |
| 26 | PASS | rector.secretary (user 7) and rector (user 2) each exported en PDF (38114 B / 38084 B): two files, two ANALYTICS_EXPORTED rows with their own user ids |
| 27 | PASS | redis stopped, page reloaded: all sections and compliance load; backend log WARN CacheFailureLogger 'Cache unit-analytics could not be read/written for 1:2022:2026; computing without it'; redis started again |
| 28 | PASS | Board report disabled while the page loads (delayed API), after an aborted load and for an invalid period; lang=de -> 400 parameter lang; dean.fmi -> 403 |
| 29 | PASS | Refresh double-click: 1 request to /compliance |
| 30 | PASS | ?from=2026&to=2026: 'Too few complete years for a projection.' and no dashed segment; the board report for 2026-2026 says the same, 200 application/pdf board-report-ChNU-2026-2026.pdf |
| 31 | PASS | admin revoked RECTOR of rector (#2) with the page open: Refresh -> /login (401, token refresh refused); after sign-in nav shows only Home and Achievements, /analytics/university -> /forbidden, report 403; rector.secretary 200 unchanged; role granted again (new user_roles row 1917) |
| 32 | PASS | delegation 49 rector->dean.fmi (RECTOR, org 1): dean.fmi still 403 on /analytics/university and /compliance, roles show only DEAN; after DELETE (204) unchanged; rector 200 |
| 33 | PASS | lecturer.fmi withdraws analytics: compliance granted 34->33 at once; no lecturer/userId/e-mail text in the university body before or after; granted again -> 34 (page Refresh = same endpoint, step 15) |
| 34 | PASS | award 2114 approved 17:36: reload at once gave 470 with generatedAt 17:32:13, at 17:39 471 with new generatedAt; compliance reviews.open 84->83 immediately |
| 35 | PASS | APP_RETENTION_CRON '0 8 20 * * *': run 100 started 18:08:00Z COMPLETED 18:08:04Z; compliance lastRetentionRun shows it and RETENTION_STALE cleared (attention [REVIEWS_OVERDUE]); backend recreated without the variable (cron 0 0 4 * * *) |
| 36 | PASS | Board report double click: Back/Forward no new download or row (audit count unchanged); two tabs -> two files, two rows (step 26); a scripted click pair in the same ms sends 2 requests (guard missing in download(), AC3 added to #319); clicks 30-150 ms apart send 1 |
| 37 | PASS | redis stopped, Board report -> board-report-ChNU-2022-2026.pdf downloaded, cache warnings in the log, 1 new ANALYTICS_EXPORTED row; redis started |
| 38 | PASS | report request held, backend stopped: 'Не вдалося експортувати звіт.', ANALYTICS_EXPORTED count 118 before and after; after restart the button downloads board-report-ChNU-2024-2026.pdf |
| 39 | PASS | step text corrected: sign-out in a second session leaves the first page working (Refresh 200); own sign-out -> /login, Back -> /login with no figures, dean.fmi on /analytics/university -> /forbidden without figures |

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| The university subtree query misses the 3 s with 100 000 awards | Low | Medium | Conditional index of §6; the cache covers repeat views |
| Generalising `UnitComparison` changes the faculty rows of 5.2 | Medium | Medium | 5.2 FTs and the unit-row unit tests stay unchanged and green |
| A count in the compliance summary points to a person | Low | Medium | Counts only, no dates of persons; security review of 5.3.1 |
| The projection reads as a promise | Medium | Low | Sentence says "about" and "based on"; guide explains the line; hidden under 3 complete years |
| openhtmltopdf lays out landscape bars badly | Medium | Low | Tables with CSS widths, checked by eye; fallback: tables only |
| Calendar (deployment mid-November) | Medium | High | 5.3.3 is the cut line; the page stands without it |

## 12. Definition of Done

- Every AC proven at the layers of §9; `.\tools\gate.ps1` green per story; Playwright for 5.3.2 and 5.3.3
- Screen check of `/analytics/university`; the board report opened and checked by eye in both languages
- Security review for 5.3.1 and 5.3.3
- `openapi.yml` without `x-status: planned` on the three operations; AUTHENTICATION_AUTHORIZATION §3.3; RBAC_matrix §4; ADR-022 module table; DATA_DICTIONARY `ANALYTICS_EXPORTED` (`board-pdf`); user guide "University analytics"; CHANGELOG `[Unreleased]`; tracker and BACKLOG
- Manual verification (§10) run and recorded; feature validated

## 13. Validation (2026-10-25, `develop` at 2921bc6)

PRs #315, #316, #317 merged, all three stories Done in Jira and GitHub. Result: **passed with notes**, pending the manual run of §10. Every AC has a test; AC-1.9 holds its time bound but not the index clause (finding 1).

| Layer | Result |
|-------|--------|
| Backend unit / IT + FT | 1291 / 503 on `develop`, all green (`gate.ps1 -Full`, 2026-10-25 16:47) |
| Coverage, static | 98.7 % lines (6043/6120); Checkstyle, PMD, SpotBugs 0 |
| Frontend | lint clean, 927 tests, initial bundle 959.71 kB |
| Playwright | Full run 2026-10-25 (one worker): 126 of 126 passed in 15.7 min, `university-analytics.spec` 8/8 |
| Security | Checklist below; the reviews of #315 (permission, counts-only summary) and #317 (export, audit) leave no finding open |
| Docs | `openapi.yml` (no `x-status: planned` on the three operations), AUTHENTICATION_AUTHORIZATION §3.3, RBAC_matrix §4 footnote ²⁰, ADR-022 module table, DATA_DICTIONARY `ANALYTICS_EXPORTED` (`board-pdf`), user guide uk/en, CHANGELOG `[Unreleased]` |

### AC evidence

| AC | Unit | IT / FT | E2E / manual |
|----|------|---------|--------------|
| 1.1 | `RolePermissionsTest.ac1_1_universityAnalyticsBelongToTheRectorAndTheSecretaryAndAreNeverLent` | `UniversityAnalyticsFT.ac1_1_ac1_2_otherRolesAndDelegatesOfTheRectorAreRefused` | `university-analytics.spec` "ac2_1 refuses every account…"; steps 21, 31, 32; doc review ✓ |
| 1.2 | `UniversityAnalyticsServiceTest.ac1_2_*` (3) | `UniversityAnalyticsFT.ac1_2_theRectorAndTheSecretaryReadTheUniversityAndTheCompliance`, `ac1_2_invalidYearsNameTheParameter` | steps 2, 10 |
| 1.3 | `UniversityAnalyticsServiceTest.ac1_3_ac1_4_*` | `UniversityAnalyticsIT.ac1_3_ac1_4_countedAwardsOfTheWholeUniversityLandInTheirFacultyCollegeOrTheUniversityLevel` | steps 1, 2 |
| 1.4 | same | same, `UniversityAnalyticsFT.ac1_4_to_ac1_7_*`, `UnitStatisticsIT.ac1_4_anEmptySetOfOrganisationsCountsNothing` | step 2 |
| 1.5 | `TrendRulesTest.ac1_5_*` (4) | `UniversityAnalyticsFT.ac1_4_to_ac1_7_*` | step 3 |
| 1.6 | `ProjectionHelperTest.ac1_6_*` (4) | same | steps 4, 30 |
| 1.7 | `HighlightRulesTest.ac1_7_*` (3) | same (no person field) | steps 5, 33 |
| 1.8 | `CacheFailureLoggerTest` (5.2, Redis errors) | `UniversityAnalyticsIT.ac1_8_theUniversitySharesTheUnitCacheWithThePreviousSourcesAndAnOldEntryIsAMiss` | steps 6, 27, 34, 37 |
| 1.9 | | `UnitStatisticsIT.ac1_9_aHundredThousandAwardsOfThreeThousandMembersAnswerTheUniversityWithinThreeSeconds` (time only) | finding 1 |
| 1.10 | `UniversityComplianceServiceTest.ac1_10_theSummaryCarriesTheCountsReadAtTheRequest` | `ComplianceCountsIT.ac1_10_*` (3), `UniversityAnalyticsFT.ac1_10_ac1_11_theComplianceSummaryIsCountsOnlyAndReadLive` | steps 7, 8, 35 |
| 1.11 | `UniversityComplianceServiceTest.ac1_11_*` (4) | same FT | step 9 |
| 1.12 | | `ModularityTest` (gate) | doc review ✓ (ADR-022) |
| 2.1 | `auth.guard.spec`, `permissions.spec`, `shell.component.spec` `ac2_1_*university*` | | `university-analytics.spec` "ac2_1 ac2_3 ac2_9 opens the university…", "ac2_1 refuses every account…"; step 11, 21 |
| 2.2 | `university-analytics.component.spec` `ac2_2_*` (2), `effects.spec`, `service.spec` `ac2_2_*` | | "ac2_2 ac2_4 keeps the period in the address…"; steps 13, 23 |
| 2.3 | `projection-trend-chart.component.spec` `ac2_3_*` (3), `university-analytics.component.spec.ac2_3_*` | | "ac2_1 ac2_3 ac2_9 …"; step 11 |
| 2.4 | `unit-comparison.component.spec` `ac2_4_*` (5) | | "ac2_2 ac2_4 …"; step 12 |
| 2.5 | `trend-lists.component.spec` `ac2_5_*` (2) | | step 14 |
| 2.6 | `highlights.component.spec` `ac2_6_*` (5) | | step 14 |
| 2.7 | `compliance-panel.component.spec` `ac2_7_*` (4), `university-analytics.component.spec` `ac2_7_*` (2), `effects.spec` `ac2_7_*` (2) | | "ac2_7 refreshes only the compliance panel…"; steps 15, 29 |
| 2.8 | `projection-trend-chart.component.spec.ac2_8_*`, `university-analytics.component.spec` `ac2_8_*` (2), `effects.spec.ac2_8_*` | | "ac2_8 refuses a period that is not valid…"; steps 16, 22, 24 |
| 2.9 | `ac2_9_reads_in_ukrainian` (3 components) | | "ac2_9 fits a phone in dark mode…", timing in "ac2_1 ac2_3 ac2_9 …"; ui-check of #316; steps 19, 20; guide ✓ |
| 3.1 | `BoardReportExporterTest.ac3_1_*` (2) | `UniversityAnalyticsFT.ac3_1_ac3_2_ac3_4_*`, `ac3_1_ac3_4_refusedReportRequestsWriteNoAuditRow` | steps 17, 28 |
| 3.2 | `BoardReportAssemblerTest.ac3_2_*` (7), `BoardPdfWriterTest.ac3_2_*` (6), `BoardReportExporterTest.ac3_2_*` | `UniversityAnalyticsFT.ac3_1_ac3_2_ac3_4_theRectorDownloadsTheBoardReportWithTheFiguresOfTheDashboard` | step 17; PDFs uk/en checked by eye in #317 (8 pages) |
| 3.3 | `BoardReportAssemblerTest.ac3_3_*` (2), `BoardPdfWriterTest.ac3_3_longFacultyNamesWrapInsideThePage` | `UniversityAnalyticsFT.ac3_3_theSecretaryGetsTheReportInUkrainianByDefault` | step 17 |
| 3.4 | `BoardReportExporterTest.ac3_4_*` (2) | `UniversityAnalyticsFT.ac3_1_ac3_2_ac3_4_*`, `ac3_1_ac3_4_refused*` | steps 18, 26, 36, 38 |
| 3.5 | `board-report-button.component.spec` `ac3_5_*` (5, double click added at the validation), `university-analytics.component.spec` `ac3_5_*` (2) | | "ac3_5 downloads the board report…", "ac3_5 offers no board report for an invalid period…"; steps 17, 22, 24, 25, 28, 36, 39 |

### Edge cases (§5)

| Edge case | Covered by |
|-----------|-----------|
| Period, `from` = `to` = current year | `UniversityAnalyticsServiceTest.ac1_2_*`, `ProjectionHelperTest.ac1_6_fewerThanThreeCompleteYearsGiveNoLine`; step 30 |
| Projection on complete years, clamp at 0 | `ProjectionHelperTest.ac1_6_aLineThroughTheCompleteYearsLeavesThePartialYearOut`, `ac1_6_aFallingLineIsClampedAtZero` |
| Trends noise, new category | `TrendRulesTest.ac1_5_entriesUnderThreeAwardsOverBothPeriodsAndUnchangedOnesAreLeftOut`, `ac1_5_categoriesRiseAndFallByDeltaWithPercentAndNullForNewOnes` |
| Colleges not linked | `unit-comparison.component.spec.ac2_4_university_links_faculties_names_the_university_row_and_leaves_colleges_plain`; e2e "ac2_2 ac2_4 …" |
| University level, no highlight | `HighlightRulesTest.ac1_7_theUniversityLevelRowGetsNoHighlightAndStrengthsComeLast` |
| Inactive units | 5.2 `UnitComparison` tests, shared code; `UniversityAnalyticsIT.ac1_3_ac1_4_*` |
| Cache shared, old entry a miss | `UniversityAnalyticsIT.ac1_8_*` |
| Compliance live, `P2D`, running run | `UniversityComplianceServiceTest.ac1_11_aRunIsStaleOnlyAfterTheConfiguredLimitAndARunningOneIsJudgedByItsStart`; FT read live |
| Compliance privacy (counts only) | `UniversityAnalyticsFT.ac1_10_ac1_11_*` (no ids in the body); security review #315 |
| Authorization by role, delegations | `RolePermissionsTest.ac1_1_*`, FT delegate refused; step 32 |
| Board report figures equal the page | `UniversityAnalyticsFT.ac3_1_ac3_2_ac3_4_*` |
| Redis down | `CacheFailureLoggerTest`; steps 27, 37 |

### Security (OWASP)

| Item | Control |
|------|---------|
| A01 access control | `@PreAuthorize(CAN_READ)` on the three operations of `UniversityAnalyticsController` (`analytics:read:university`, not delegable in `RolePermissions`); guard and navigation on the client; FT 403 for five roles and a delegate |
| A02 crypto / exposure | Counts only in the summary and the report, no ids, names or e-mails; `Cache-Control: no-store` on all three responses |
| A03 injection | `ComplianceCounts` and `UnitStatistics` use bound parameters; years and `lang` parsed to types before use (400 otherwise); the Thymeleaf template escapes every value (no `th:utext`) |
| A07 auth failures | Bearer JWT on the API chain (CSRF not used); role revocation ends sessions (Detour 31) |

### Findings

1. AC-1.9: the university read is a sequential scan by design; the IT proves only the 3 s bound. To decide: reword the AC to the time bound, or add a covering index.
2. The report endpoint, like the 5.2 exports, has no rate limit; each call renders a PDF and writes an audit row. Belongs to the production hardening of Epic 9.
3. Refactor sweep: eight notes in the epic tracker (technical notes), proposed as one refactor chore with the 5.2 notes.
4. Integration check of the composed stack (`docker compose up --build`, `/v3/api-docs` against `openapi.yml`) and the walk of §10 move to the manual run; the migrations apply on the empty Testcontainers databases of every IT (no new migration in 5.3).

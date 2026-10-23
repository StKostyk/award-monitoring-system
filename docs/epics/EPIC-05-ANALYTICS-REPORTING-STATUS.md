# Epic 5: Analytics & Reporting Dashboard — Status

> **Started**: 2026-10-19 (kickoff)
> **Done**: -
> **Author**: Stefan Kostyk
> **Jira epic**: SCRUM-97
> **Roadmap**: DEVELOPMENT_ROADMAP.md § Epic 5 (local planning document)

## Progress

| Feature | Status | Started | Done |
|---------|--------|---------|------|
| 5.1 Personal achievement dashboard (US-008) | Done | 2026-10-19 | 2026-10-23 |
| 5.2 Faculty performance analytics (US-009) | Planned | - | - |
| 5.3 Executive university dashboard (US-010) | Planned | - | - |
| 5.4 Full-text award search (ADR-007 addendum) | Planned | - | - |

## Current focus

Feature 5.1 Done 2026-10-23: validated with notes, 5.1.4 (SCRUM-108) merged, manual run of §10 passed 37/37 ([feature-5.1-personal-achievement-dashboard.md](../features/epic-05/feature-5.1-personal-achievement-dashboard.md) §10, §13). Next: Feature 5.2.

## Scope

Dashboards over approved awards at four levels: one's own (timeline, trends by category, level and year, rank in the
department, a shareable summary, a choice of widgets), the department and faculty (comparison of departments,
categories and awarding organisations, top performers, a selectable period compared with the previous one), and the
university (faculties, trending categories and sources, a simple trend projection, an aggregate compliance status).
Reports export to CSV, Excel and PDF; the rector gets a presentation-ready board report. Award lists get full-text
search on PostgreSQL in Ukrainian and English.

Out of scope here: custom report builder ("Create Custom Reports" in RBAC matrix §4; the period, unit and widget
choices cover US-008 to US-010), a recommendations engine and statistical significance tests (US-009/US-010 DoD; a
rule-based list of strengths and gaps and period-over-period deltas instead), raw data access, Elasticsearch (ADR-007
addendum: chosen only if PostgreSQL falls short), cross-university analytics (ADR-022, one tenant).

## Stories

`parallel` marks stories whose UI can be built in a separate lane from the OpenAPI contract while the backend is in progress.

| # | Story | Feature | Pts | Jira | GitHub | Parallel | Status |
|---|-------|---------|-----|------|--------|----------|--------|
| 1 | 5.1.1 Personal analytics API with a consented department rank | 5.1 | 5 | SCRUM-98 | #285 | no | Done |
| 2 | 5.1.2 Personal dashboard with charts and widget choice | 5.1 | 8 | SCRUM-99 | #286 | yes | Done |
| 3 | 5.1.3 Shareable achievement summary | 5.1 | 3 | SCRUM-100 | #287 | no | Done |
| 3a | 5.1.4 Dashboard period and failure states | 5.1 | 5 | SCRUM-108 | #300 | no | Done |
| 4 | 5.2.1 Unit analytics API for departments and faculties with period comparison | 5.2 | 5 | SCRUM-101 | #288 | no | Planned |
| 5 | 5.2.2 Faculty analytics page with department comparison and top performers | 5.2 | 5 | SCRUM-102 | #289 | yes | Planned |
| 6 | 5.2.3 Analytics report export to CSV, Excel and PDF | 5.2 | 5 | SCRUM-103 | #290 | no | Planned |
| 7 | 5.3.1 University analytics API with trends and a compliance summary | 5.3 | 5 | SCRUM-104 | #291 | no | Planned |
| 8 | 5.3.2 Executive dashboard | 5.3 | 5 | SCRUM-105 | #292 | yes | Planned |
| 9 | 5.3.3 Presentation-ready board report | 5.3 | 3 | SCRUM-106 | #293 | no | Planned |
| 10 | 5.4.1 Full-text award search on PostgreSQL | 5.4 | 5 | SCRUM-107 | #294 | no | Planned |

Total: 49 points (roadmap estimate 55; 5.1.2 raised from 5 to 8 in the 5.1 PRD), planned for sprints 6 and 7 (2026-10-19 to 2026-11-01).

## Decisions

Kickoff defaults, confirmed or changed in the feature PRDs.

| Date | Decision | Rationale | Reference |
|------|----------|-----------|-----------|
| 2026-10-19 | Analytics count `APPROVED` awards only; pending and rejected ones appear only in the workflow metrics shipped with 4.2.2 | Approved awards are the public record (BO-002); drafts and rejections are personal data of the workflow | US-008–US-010, BRD §3.1 |
| 2026-10-19 | A person is named in ranks, comparisons and top-performer lists only with the `ANALYTICS` consent; otherwise counted in totals only. Anonymised awards of erased accounts count in totals and are never named | The consent type exists since 6.1.1 for exactly this purpose | PRIVACY_BY_DESIGN §4, DATA_DICTIONARY `consent_records` |
| 2026-10-19 | Aggregation on demand in SQL over `awards`, cached in Redis with a short TTL; no materialised views | Tens of thousands of awards per university; the 3/5/10 s targets hold without a refresh pipeline | ADR-005, BRD §5.1 |
| 2026-10-19 | Award search on PostgreSQL: `tsvector` (Ukrainian and English) with a GIN index plus `pg_trgm`; Elasticsearch stays out | ADR-007 addendum 2026-10-05; one store, same access rules | ADR-007 |
| 2026-10-19 | The shareable summary is a printable page (print CSS) with a link to the published achievements; server-side PDF starts at 5.2.3 | One PDF library, introduced once | US-008 |
| 2026-10-19 | Charts with ngx-echarts 21 on ECharts 6 (SVG renderer, ARIA, table view per chart); personal analytics uncached; rank by count of approved awards under the person's own `ANALYTICS` consent, minimum group 5; widget choice stored per account (`dashboard_preferences`, V046) | Accessible and printable charts; fresh personal figures; no colleague named or derivable | PRD 5.1 §7 |
| 2026-10-19 | Feature 5.1 validation: fix story 5.1.4 (SCRUM-108, 5 points) for the period the server refuses, the widget-choice save after a failed load, the public-awards check failure, Print while loading, the missing "Other" bar and a Kyiv New Year test | Scenario review and edge-case check found them; the validation docs ship in its PR | PRD 5.1 §13 |
| 2026-10-23 | Feature 5.1 closed: manual run 37/37; the stale figures while the backend is down (step 22) and the rank counting members without the consent go to the Epic 5 review | No AC broken; both are product questions, not defects | PRD 5.1 §10, §13 |

## Documentation deviations found at kickoff

1. ADR-007 defers the search engine decision to Epic 5, but the roadmap's Epic 5 has no search story. Feature 5.4 (5.4.1) added.
2. RBAC matrix §4 gives the faculty secretary "View Faculty Analytics", while `award:read:faculty` excludes that role; it also limits "View Personal Analytics" to employees, though every role holder has own awards. Default: personal analytics for every account, department analytics for faculty secretaries, faculty analytics from the dean upward, university analytics for the rector and rector's secretary, none for the GDPR officer and administrator. New `analytics:read:*` permissions; the matrix is corrected in 5.1.1 and 5.2.1.
3. `openapi.yml` drafts `/reports/awards/summary` and `/reports/awards/export` (`x-status: planned`) with workflow figures (approval rate, processing days, counts by status) that US-009 does not ask for. Redesigned in 5.2.1 under `/analytics/...`.
4. US-010 asks for "real-time compliance status", but `compliance:read` is held by the GDPR officer only. Default: the executive widget shows counts and the outcome of the last retention run, no personal data.
5. US-010 DoD "strategic insights and recommendations engine" and "forecasting", US-009 DoD "statistical significance": scoped down to rule-based highlights, a linear trend projection and period-over-period deltas with counts (a thesis limitation).
6. `vw_award_statistics` (R__create_views) groups by `organization_id` and reports draft and pending counts; no code reads it. 5.2.1 replaces or drops it.
7. BACKLOG "Later epics" listed US-008 and US-010 but not US-009; replaced by the Epic 5 section.
8. ADR-013 and ADR-014 name no chart library. Chosen in the Feature 5.1 PRD (candidates: ngx-echarts with the SVG renderer and ARIA, ng2-charts on Chart.js); ADR-014 addendum in 5.1.2.

## Technical notes

- Roles and scopes: `authz/AccessScope`, `RolePermissions`; department = unit of the role, faculty = parent unit.
- Existing reads to reuse: `award/service/Achievements` (published awards with unit, year, level and recipient filters), `award/service/ReviewMetrics` (workflow metrics), `DuplicateFinder` (`pg_trgm`).
- No chart library in `frontend/package.json`; dashboards follow the NgRx-per-feature layout (ADR-015).
- Exports need new Excel and PDF dependencies; one online `./mvnw test-compile` after adding them.
- Open fixes from Epic 6 (two failing e2e specs, 360 px clipping, role end dates) become separate fix stories once agreed.
- Feature 5.1 refactor sweep (2026-10-19), notes only:
  - `retry()` and the four store selectors are repeated in the dashboard and summary components.
  - `PersonalAnalyticsFT` and `DashboardPreferenceFT` hand-roll the `like 'prefix%'` teardown of about ten FTs; a shared `purgeUsersLike` belongs in a chore.
  - `ac1_1_everyRoleReadsItsOwnAnalytics` does not name the failing role; a parameterised test would.
  - `PersonalAnalyticsService.of` checks `approved() == 0` twice.
  - The time axis of `timeline-chart` could join `categoryAxis`/`valueAxis` in `chart-style.ts`.
  - `dashboard.spec` measures the 3 s with `Date.now()`.
  - The module-level helpers of `dashboard.component.ts` are ordered inconsistently.
  - The shared horizontal-bar helper for the category and level charts goes into 5.1.4, next to AC-4.5.

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Calendar: deployment mid-November, defense ~2026-12-15; Epics 8 and 9 still follow | Epic 5 overruns into deployment time | 5.3.3 and 5.4.1 are the cut line; both droppable without breaking other stories |
| Seed data too thin for meaningful charts | Demo and manual runs look empty | 5.1.1 adds a local and demo seed of approved awards over several years and units |
| Small groups re-identify people in aggregates | GDPR exposure in department comparisons | Minimum group size for named comparisons, decided in PRD 5.2 |
| PDF and Excel libraries raise fan-out and dependency weight | Gate and SpotBugs noise | One export component, decided in PRD 5.2 |

## Quick links

- [USER_STORIES.md](../requirements/USER_STORIES.md) US-008, US-009, US-010
- [RBAC_matrix.md](../stakeholders/RBAC_matrix.md) §4
- [PRIVACY_BY_DESIGN.md](../security/PRIVACY_BY_DESIGN.md) consent types
- [ADR-007](../architecture/adr/ADR-007-Search-Engine.md), [ADR-005](../architecture/adr/ADR-005-Caching.md), [ADR-014](../architecture/adr/ADR-014-UI-Component-Library.md), [ADR-015](../architecture/adr/ADR-015-State-Management.md)
- [BACKLOG.md](../project-management/BACKLOG.md) Epic 5

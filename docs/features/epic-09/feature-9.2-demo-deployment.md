# Feature 9.2: Demo deployment and operations

> **Epic**: 9 Production hardening (SCRUM-128) · **Tracker**: `docs/epics/EPIC-09-PRODUCTION-HARDENING-STATUS.md`
> **Status**: 9.2.1 in progress; the sections of 9.2.2 to 9.2.4 are written before 9.2.2 starts.

## 1. Problem

The demo runs on one server (ADR-021) from the production build. Feature 8.1 left two signed-in pages under the
Lighthouse mobile target of NFR-009 (finding F-2 of the 8.1 PRD: performance 74 to 80 on `/awards/new` and
`/awards`, from total blocking time at 4x CPU). This feature makes the build the deployment ships measure well,
then backs it up, deploys it and starts the server.

## 3. Stories

| # | Story | Points | Jira | GitHub |
|---|-------|--------|------|--------|
| 9.2.1 | Signed-in pages reach Lighthouse mobile performance 90 | 3 | SCRUM-131 | #372 |
| 9.2.2 | Nightly backups with a restore drill | 5 | SCRUM-132 | #373 |
| 9.2.3 | Deploy job and production server settings | 3 | SCRUM-133 | #374 |
| 9.2.4 | Demo server on AWS EC2 with a smoke test | 3 | SCRUM-134 | #375 |
| 9.2.5 | Award form in steps on phones | 5 | SCRUM-136 | #381 |

## 4. Acceptance criteria

### 9.2.1 Signed-in pages reach Lighthouse mobile performance 90 (SCRUM-131)

Carried from AC-3.7 of Feature 8.1, restricted to the two pages of F-2.

- **AC-1.1** (Lighthouse) Given the Compose build, `tools/lighthouse` (`npm run flow`, mobile profile, simulated
  throttling) three times, then the median performance on `/awards/new` and `/awards` is recorded with the
  Lighthouse version; accessibility and best practices stay at 90 or above, `/login` keeps its scores and the
  initial bundle stays within the `angular.json` budget. Target 90; the measured result is in §10.
- **AC-1.2** (List filters on a phone) Given a screen of at most 600 px, when `/awards` opens, then the search
  field is shown and the status, category and date filters stay behind a "Filters" button (`aria-expanded`) that
  counts the filters in use; they render when the button opens them. Wider screens show every filter as before.
- **AC-1.3** (Page size on a phone) Given a screen of at most 600 px when the application starts, when `/awards`
  opens, then it lists 10 awards a page; 10 is offered beside 20, 50 and 100 on every screen. The size is read
  once, as the reduced-motion setting is.
- **AC-1.4** (Dates off the render path) Plain dates (`YYYY-MM-DD`) are shown without a date formatter, in the same
  text as before (`09.10.2026`, `09/10/2026`); the first formatter of a page is created while the start-up
  requests are under way, not while a page renders.

## 7. Technical decisions

- **D-1** The award form keeps its single page in 9.2.1. Its eight Material fields cost about 800 ms of blocking
  time in one task; a deferred lower half does not help, because on the Lighthouse phone (412 x 823) the lower
  fields are on screen and render straight after the first ones. Steps on phones follow in 9.2.5.
- **D-2** Status, unit, visibility and delay labels of the award list are styled spans (`.app-chip`, the look of a
  Material chip), not `mat-chip` components: the labels are not interactive.
- **D-3** The shell stays on default change detection: `OnPush` gave no measurable gain, and its template binds
  state of child references (`drawer.opened`, `routerLinkActive`) that does not mark it for check.

AC-1.1 is not met: the scores in §10 stay under 90; 9.2.5 continues it for the form.

## 10. Manual verification

Preconditions: Compose stack built from the branch (`docker compose up -d --build frontend app`), at least 4 GB
of free memory (below that the runs vary by 30 points), `tools/lighthouse` installed (`npm ci`).

1. `npm run flow` in `tools/lighthouse`, three times. Expected: per page the median performance, accessibility and
   best practices; record them with the Lighthouse version. (AC-1.1)
2. Chrome DevTools, device toolbar at 360 px, `http://localhost/awards` as `employee.fmi`. Expected: the search
   field and a "Фільтри" button; the button opens the status, category and date filters; with a status chosen
   the button reads "Фільтри: 1"; the paginator offers 10 and shows 10 a page. At 1280 px every filter is shown
   and no button. (AC-1.2, AC-1.3)
3. Same list in English. Expected: dates as `09/10/2026`, the button reads "Filters". (AC-1.4)

### Run notes

Lighthouse 12.8.2, mobile profile, simulated throttling, Compose stack with the branch build, 4.4 GB free,
median of three runs (performance / accessibility / best practices):

| Page | Before (develop) | After (9.2.1) |
|------|------------------|---------------|
| `/login` | 100 / 100 / 100 | 100 / 100 / 100 |
| `/awards/new` | 79 / 100 / 100 | 79 / 100 / 100 |
| `/awards` | 76 / 100 / 96 | 79 / 100 / 96 |

Single runs after the change: `/awards` 77, 79, 82; `/awards/new` 74, 79, 80. Target 90 not reached: the gain on
`/awards` (blocking time of the list tasks from about 1 200 to 800 ms in the profile) is within three points of the
spread between runs. `/awards` is limited by the rendering of the filter bar and the rows; `/awards/new` by the
form (D-1, 9.2.5). The best-practices 96 on `/awards` is a 504 the
service worker returns when `/brand/brand.json` exceeds its 3 s freshness timeout during a throttled run; it does
not occur on every run.

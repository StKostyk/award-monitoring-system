# Epic 8: Mobile Optimization & Accessibility — Status

> **Started**: 2026-10-26 (kickoff)
> **Done**: -
> **Author**: Stefan Kostyk
> **Jira epic**: SCRUM-114
> **Roadmap**: DEVELOPMENT_ROADMAP.md § Epic 8 (local planning document)

## Progress

| Feature | Status | Started | Done |
|---------|--------|---------|------|
| 8.1 Mobile responsive design and PWA (installable shell, offline draft, touch layouts, camera) | Validated 2026-10-27 with notes ([PRD](../features/epic-08/feature-8.1-mobile-pwa.md) §13): fix 8.1.4 (SCRUM-121, AC-2.9) in progress; AC-3.7 partly met, performance of the signed-in pages moves to Epic 9; manual run of §10 pending | 2026-10-26 | - |
| 8.2 WCAG 2.1 AA accessibility (page structure, automated audit of every route) | Validated 2026-10-28 with notes ([PRD](../features/epic-08/feature-8.2-accessibility.md) §13): finding F-1 (failed translation load) checked in the manual run; manual run of §10 pending | 2026-10-27 | - |

## Current focus

Feature 8.1 validated 2026-10-27 with notes: fix story 8.1.4 (SCRUM-121) for a new award created twice when the save answer is lost (AC-2.9); AC-3.7 performance of `/awards/new` and `/awards` moves to an Epic 9 story. Feature 8.2 validated 2026-10-28 with notes (PRD §13); the manual runs of 8.1 §10 and 8.2 §10 are next, then the
documentation reconciliation of the epic.

## Scope

US-013 and US-014 over the screens of Epics 1 to 7. The system becomes an installable progressive web app: a web
app manifest per brand and an Angular service worker that caches the application shell, icons and translations, so
the app opens on a weak connection; API answers and documents stay out of the cache. An award being entered survives a
lost connection on the device and is saved as a server draft when the connection returns, so it can be finished on
another device (server drafts exist since 2.1.1, local form copies since Epic 2). Every page works by touch at 360 px
in both orientations; certificates can be photographed (the upload already offers `capture="environment"`).

On the accessibility side the pages get their language and title, a skip link and focus management after
navigation. The epic ends with an axe sweep of every route in both languages and themes, enforced by the gate. The
claim is "WCAG 2.1 AA checked automatically on every page", not a conformance audit.

Out of scope here: browser push notifications (deferred from Epic 7; needs VAPID keys, a subscription table and a
consent entry, see Decisions), native apps, background sync of uploads (documents need a connection), offline reading
of awards (personal data stays out of the service worker cache), OCR (Epic 3 continuation after this epic), the
form, table and announcement work of 8.2.2 and a public accessibility statement (icebox, see Decisions).

## Stories

`parallel` marks stories whose UI can be built in a separate lane from the OpenAPI contract while the backend is in progress.
All six stories are frontend work; none has a backend lane.

| # | Story | Feature | Pts | Jira | GitHub | Parallel | Status |
|---|-------|---------|-----|------|--------|----------|--------|
| 1 | 8.1.1 Installable app with an offline application shell | 8.1 | 5 | SCRUM-115 | #331 | no | Done |
| 2 | 8.1.2 Award draft kept offline and saved on reconnect | 8.1 | 5 | SCRUM-116 | #332 | no | Done |
| 3 | 8.1.3 Touch-ready layouts at 360 px and camera capture | 8.1 | 5 | SCRUM-117 | #333 | no | Done |
| 4 | 8.2.1 Page language, titles, skip link and focus management | 8.2 | 5 | SCRUM-118 | #334 | no | Done |
| 5 | 8.2.3 Automated WCAG 2.1 AA sweep of every route | 8.2 | 3 | SCRUM-120 | #336 | no | Done |
| 5a | 8.1.4 New award created once when the save answer is lost | 8.1 | 3 | SCRUM-121 | #346 | no | Done |
| - | 8.2.2 Described forms, tables and announced changes | 8.2 | 5 | SCRUM-119 | #335 | no | Icebox |

Total: 23 points (roadmap estimate 21: US-013 13, US-014 8; the roadmap's single story per feature is split by
concern), planned for sprints 7 and 8 (2026-10-26 to 2026-11-08), before the deployment in mid-November.

## Decisions

Kickoff defaults, confirmed or changed in the feature PRDs.

| Date | Decision | Rationale | Reference |
|------|----------|-----------|-----------|
| 2026-10-26 | Offline means a fallback for the award form, not an offline-first client: the service worker caches the application shell, icons and translations only; no API answer, document or personal data is cached | BRD §3.2 excludes offline-only clients; personal data in a shared cache outlives sign-out and escapes the erasure of Epic 6 | ADR-013 addendum in the 8.1 PRD |
| 2026-10-26 | Cross-device continuation rides on the server draft (`DRAFT` status, 2.1.1): an offline form is kept in the existing local form copy and saved as a draft on reconnect; no separate sync store | One source of truth for drafts; the local copy is already per user and cleared on sign-out | ADR-005 not involved |
| 2026-10-26 | Browser push notifications stay out of Epic 8 (icebox) | Backend work (VAPID keys, subscription table, preference channel, export and erasure) for a feature iOS offers only to installed apps; e-mail and in-app channels cover the events | Epic 7 scope note |
| 2026-10-26 | Accessibility in a slim form: page language, titles, skip link and focus (8.2.1) plus an automated axe sweep (WCAG 2.1 AA tags) of every route in the gate (8.2.3); 8.2.2 and the accessibility statement go to the icebox; no manual conformance audit | NFR-010 and US-014 are met by automated checks at a third of the cost; Angular Material forms and dialogs already pass the axe checks of 19 specs; the thesis claims automated checking, not certification | QUALITY_GATES §8, 8.2 PRD |
| 2026-10-26 | Lighthouse (mobile profile) above 90 for performance, accessibility and best practices on the sign-in page, the award form and the award list, measured against the production build in Compose | US-013 DoD, NFR-009, ADR-013 | 8.1.3 |
| 2026-10-26 | Feature 8.1 PRD approved: one service worker data group for the brand files (freshness), refining decision 1; Ukrainian-only manifest; Lighthouse measured by hand and the service worker spec run against the Compose build, both outside the gate; installability checked in the Chrome Application panel; server drafts written on "Save draft" and on reconnect only; navigation of every signed-in user after an offline start | Brand files change per deployment without a rebuild and hold no personal data; a manifest has no language switch; the worker exists only in the production build; Lighthouse 12 has no PWA category | 8.1 PRD §7 D-1 to D-8 |
| 2026-10-28 | Feature 8.2 validated with notes; NFR-010 counts as met by the automated sweep, every axe scan of the e2e suite uses the same WCAG 2.1 A and AA tags | The tracker decision of 2026-10-26 replaces the manual audit by automated checks; one tag list keeps the 19 older specs and the sweep from drifting apart | 8.2 PRD §13, TRACEABILITY NFR-010 |

## Documentation deviations found at kickoff

1. The roadmap cites QUALITY_GATES §6 for accessibility; §6 holds performance gates, the accessibility gates are §8.
2. The roadmap cites BRD §4.10 (accessibility) and §4.6 (camera); the BRD has no such sections. Mobile and
   accessibility scope sits in BRD §3.1 "Mobile & Accessibility". The roadmap links are corrected at the epic's
   documentation sync.
3. The roadmap cites INTEGRATION_PATTERNS for offline-first service workers and ADR-005 (caching) for cross-device
   sync; neither covers them. The offline policy goes into an ADR-013 addendum; sync uses the server draft.
4. The roadmap task "offline-first with service workers" conflicts with BRD §3.2 ("no offline-first applications").
   Resolved by the first decision above.
5. US-014 DoD and QUALITY_GATES §8.2 require testing with a screen reader (NVDA/JAWS/VoiceOver) "with actual screen
   reader users". The manual scenario uses the browser accessibility tree and a keyboard-only walk instead; testing
   with a screen reader and with screen reader users is a thesis limitation (decided 2026-10-26).
6. US-013 DoD "mobile user guide": a section "Using the app on a phone" in `docs/user/USER_GUIDE.md`, written
   with 8.1.3 (8.1 PRD D-3). The US-014 DoD
   "accessibility guide", "WCAG AA compliance verified through audit" and "keyboard navigation throughout" are met
   only as far as the automated sweep and 8.2.1 reach; the remainder is a thesis limitation.

## Technical notes

- Two brands (`public/brand/chnu`, `public/brand/neutral`, `brand.json`) are chosen at runtime by `BrandService`,
  which already sets the document title. The manifest therefore needs a name, icons and theme colour per brand:
  either one manifest per brand linked at start-up or a manifest generated at build time per deployment.
- `@angular/service-worker` is not installed; `ng add @angular/pwa` adds it, then one online `npm ci`. Service worker
  only in the production build; `ngsw-config.json` data groups stay empty (first decision).
- The nginx config of the `frontend` image must serve `ngsw.json` and `ngsw-worker.js` without caching, and the
  manifest with `application/manifest+json`.
- Local form copies: `core/storage/FormCopiesService` (per user, cleared on sign-out), used by the award form; the
  offline save path reuses it.
- Camera: `award-documents.component.html` already sets `capture="environment"`; 8.1.3 checks the fallback picker and
  the size limit message for phone photos.
- Accessibility already in place: the axe helper `seriousViolations` (WCAG 2.1 AA tags) in 19 Playwright specs;
  chart descriptions and a "show as table" switch per chart (`shared/charts/ChartComponent`); live regions in 43
  templates. Missing: `<html lang>` stays `uk` after a switch to English (WCAG 3.1.1), one title for every route
  (2.4.2), no skip link (2.4.1), no `prefers-reduced-motion` styles, no `LiveAnnouncer` use.
- The screenshot check of each UI story covers 360/1280 px, both themes and both languages; 8.2.3 turns its route
  list into a Playwright sweep so the audit runs in the gate.
- Validation of 8.1 (PRD §13), left open: a save sent on reconnect while the access token expired on the same tick
  may refresh twice in sequence (F-3); values typed while a save is in flight are marked pristine when it finishes
  (F-4, since Feature 2.1). Refactor sweep applied after the validation: one "go pending" step in
  `award-form.component.ts`, one `REDUCED_MOTION` query in `shared/touch.ts`, the offline notice and
  `RECONNECT_MS` shared in the e2e helpers, the 3 s start check in `pwa.spec.ts` as an assertion timeout. Left
  open: the offline flags of the form could become one state value.
- Validation of 8.2 (PRD §13), refactor sweep: applied in the validation PR (one `AXE_TAGS` list and `isSerious`
  in the e2e helpers, so the 19 specs scanning with `seriousViolations` now include `wcag21a`; keyboard spec
  contexts closed in `finally`, the category choice asserted, the focus walk names its screen). Left open: the
  open-states test of `accessibility.spec.ts` (~90 lines, hand-built dean context) into small helpers; one shared
  route loop and viewport constants for `accessibility.spec.ts` and `mobile-layout.spec.ts`; `openRoute` waits for
  `networkidle` with a swallowed 5 s timeout instead of a page signal; `theme.spec.ts` constant names and its
  400 px phone; `NavigationFocusService.focusPage` (~30 lines of closures) as a small state object.

## Risks

| Risk | Mitigation |
|------|------------|
| A stale service worker serves an old shell after a deployment | Version prompt from `SwUpdate`; nginx serves `ngsw.json` uncached; tested by a rebuild in the manual run |
| Service worker interferes with Playwright e2e and the dev server | Registered only in the production build; e2e runs against the dev build, one spec against the Compose build |
| No physical iOS device for Safari testing | Playwright WebKit with the iPhone profile plus an Android phone through Chrome remote debugging; real iOS check named as a limitation |
| Screen reader testing cannot be automated | Accessibility tree snapshots and a keyboard-only walk in the scenario; no screen reader session (deviation 5) |
| Layout pass touches many templates late in the project | Narrow-screen rules in shared styles first (tables, filter bars), per-page fixes after; screenshots before and after |

## Quick links

- User stories: [US-013, US-014](../requirements/USER_STORIES.md#us-013-mobile-award-submission)
- Traceability: [FR-012, FR-013, NFR-009, NFR-010](../requirements/TRACEABILITY_MATRIX.md)
- Quality gates: [§6.1 performance, §8 accessibility](../project-management/QUALITY_GATES.md)
- ADRs: [ADR-013 frontend](../architecture/adr/ADR-013-Frontend-Framework.md),
  [ADR-014 UI library](../architecture/adr/ADR-014-UI-Component-Library.md),
  [ADR-016 styling](../architecture/adr/ADR-016-Styling.md)
- PRDs: `docs/features/epic-08/`

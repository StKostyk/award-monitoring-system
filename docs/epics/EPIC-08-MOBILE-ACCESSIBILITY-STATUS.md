# Epic 8: Mobile Optimization & Accessibility — Status

> **Started**: 2026-10-26 (kickoff)
> **Done**: -
> **Author**: Stefan Kostyk
> **Jira epic**: SCRUM-114
> **Roadmap**: DEVELOPMENT_ROADMAP.md § Epic 8 (local planning document)

## Progress

| Feature | Status | Started | Done |
|---------|--------|---------|------|
| 8.1 Mobile responsive design and PWA (installable shell, offline draft, touch layouts, camera) | Not started | - | - |
| 8.2 WCAG 2.1 AA accessibility (page structure, described forms and tables, audit and statement) | Not started | - | - |

## Current focus

Kickoff 2026-10-26. Next: the Feature 8.1 PRD.

## Scope

US-013 and US-014 over the screens of Epics 1 to 7. The system becomes an installable progressive web app: a web
app manifest per brand and an Angular service worker that caches the application shell, icons and translations, so
the app opens on a weak connection; API answers and documents stay out of the cache. An award being entered survives a
lost connection on the device and is saved as a server draft when the connection returns, so it can be finished on
another device (server drafts exist since 2.1.1, local form copies since Epic 2). Every page works by touch at 360 px
in both orientations; certificates can be photographed (the upload already offers `capture="environment"`).

On the accessibility side the pages get their language and title, a skip link, focus management after navigation,
fields tied to their errors, described tables, dialogs that return focus and announcements for changing content.
The epic ends with an axe sweep of every route, a keyboard-only walk, a screen reader pass and a public
accessibility statement.

Out of scope here: browser push notifications (deferred from Epic 7; needs VAPID keys, a subscription table and a
consent entry, see Decisions), native apps, background sync of uploads (documents need a connection), offline reading
of awards (personal data stays out of the service worker cache), OCR (Epic 3 continuation after this epic).

## Stories

`parallel` marks stories whose UI can be built in a separate lane from the OpenAPI contract while the backend is in progress.
All six stories are frontend work; none has a backend lane.

| # | Story | Feature | Pts | Jira | GitHub | Parallel | Status |
|---|-------|---------|-----|------|--------|----------|--------|
| 1 | 8.1.1 Installable app with an offline application shell | 8.1 | 5 | SCRUM-115 | #331 | no | Planned |
| 2 | 8.1.2 Award draft kept offline and saved on reconnect | 8.1 | 5 | SCRUM-116 | #332 | no | Planned |
| 3 | 8.1.3 Touch-ready layouts at 360 px and camera capture | 8.1 | 5 | SCRUM-117 | #333 | no | Planned |
| 4 | 8.2.1 Page language, titles, skip link and focus management | 8.2 | 5 | SCRUM-118 | #334 | no | Planned |
| 5 | 8.2.2 Described forms, tables and announced changes | 8.2 | 5 | SCRUM-119 | #335 | no | Planned |
| 6 | 8.2.3 WCAG 2.1 AA audit and accessibility statement | 8.2 | 3 | SCRUM-120 | #336 | no | Planned |

Total: 28 points (roadmap estimate 21: US-013 13, US-014 8; the roadmap's single story per feature is split by
concern), planned for sprints 7 and 8 (2026-10-26 to 2026-11-08), before the deployment in mid-November.

## Decisions

Kickoff defaults, confirmed or changed in the feature PRDs.

| Date | Decision | Rationale | Reference |
|------|----------|-----------|-----------|
| 2026-10-26 | Offline means a fallback for the award form, not an offline-first client: the service worker caches the application shell, icons and translations only; no API answer, document or personal data is cached | BRD §3.2 excludes offline-only clients; personal data in a shared cache outlives sign-out and escapes the erasure of Epic 6 | ADR-013 addendum in the 8.1 PRD |
| 2026-10-26 | Cross-device continuation rides on the server draft (`DRAFT` status, 2.1.1): an offline form is kept in the existing local form copy and saved as a draft on reconnect; no separate sync store | One source of truth for drafts; the local copy is already per user and cleared on sign-out | ADR-005 not involved |
| 2026-10-26 | Browser push notifications stay out of Epic 8 (icebox) | Backend work (VAPID keys, subscription table, preference channel, export and erasure) for a feature iOS offers only to installed apps; e-mail and in-app channels cover the events | Epic 7 scope note |
| 2026-10-26 | Target WCAG 2.1 AA (the tags the axe helper already checks); accessibility statement modelled on the EU web accessibility directive template | QUALITY_GATES §8, NFR-010; Ukraine aligns public-sector sites with EN 301 549 | 8.2 PRD |
| 2026-10-26 | Lighthouse (mobile profile) above 90 for performance, accessibility and best practices on the sign-in page, the award form and the award list, measured against the production build in Compose | US-013 DoD, NFR-009, ADR-013 | 8.1.3 |

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
   reader users". The manual scenario uses the browser accessibility tree and a keyboard-only walk; one NVDA pass on
   Windows (about 30 minutes, script in the 8.2 PRD) is recorded separately. User testing with screen reader users is
   a thesis limitation.
6. US-013 DoD "mobile user guide" and US-014 DoD "accessibility guide": one user-facing page each in `docs/guides/`,
   written with 8.1.3 and 8.2.3.

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

## Risks

| Risk | Mitigation |
|------|------------|
| A stale service worker serves an old shell after a deployment | Version prompt from `SwUpdate`; nginx serves `ngsw.json` uncached; tested by a rebuild in the manual run |
| Service worker interferes with Playwright e2e and the dev server | Registered only in the production build; e2e runs against the dev build, one spec against the Compose build |
| No physical iOS device for Safari testing | Playwright WebKit with the iPhone profile plus an Android phone through Chrome remote debugging; real iOS check named as a limitation |
| Screen reader testing cannot be automated | Accessibility tree snapshots in the scenario; one recorded NVDA pass (deviation 5) |
| Layout pass touches many templates late in the project | Narrow-screen rules in shared styles first (tables, filter bars), per-page fixes after; screenshots before and after |

## Quick links

- User stories: [US-013, US-014](../requirements/USER_STORIES.md#us-013-mobile-award-submission)
- Traceability: [FR-012, FR-013, NFR-009, NFR-010](../requirements/TRACEABILITY_MATRIX.md)
- Quality gates: [§6.1 performance, §8 accessibility](../project-management/QUALITY_GATES.md)
- ADRs: [ADR-013 frontend](../architecture/adr/ADR-013-Frontend-Framework.md),
  [ADR-014 UI library](../architecture/adr/ADR-014-UI-Component-Library.md),
  [ADR-016 styling](../architecture/adr/ADR-016-Styling.md)
- PRDs: `docs/features/epic-08/`

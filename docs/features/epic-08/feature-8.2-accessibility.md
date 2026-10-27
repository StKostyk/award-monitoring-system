# Feature 8.2: WCAG 2.1 AA Accessibility

> **Epic**: 8 — Mobile Optimization & Accessibility (SCRUM-114)
> **Sprint**: 8 (2026-11-02 → 2026-11-08)
> **Points**: 8 (two stories; 8.2.2 in the icebox)
> **Status**: Planned
> **Author**: Stefan Kostyk
> **Governing docs**: EPIC-08 tracker (decision 2026-10-26 "accessibility in a slim form", deviations 5 and 6, technical notes), US-014, TRACEABILITY NFR-010 / TC-031, QUALITY_GATES §8, BRD §3.1, ADR-013, Feature 8.1 PRD (route list, `mobile-layout.spec.ts`), `LanguageService`, `BrandService`, `ShellComponent`, `e2e/helpers.ts` (`seriousViolations`)

## 1. Problem and personas

The screens are built from Angular Material and 19 Playwright specs already run axe on the screens they touch, but nothing checks the pages as a whole. The document language stays `uk` after a switch to English, so a screen reader reads English text with Ukrainian pronunciation (WCAG 3.1.1). Every tab shows the same title, the brand name, so a list of open tabs or the history cannot be told apart (2.4.2). There is no way to skip the navigation drawer (2.4.1), and after a route change the focus stays on the link that was clicked, so a screen reader user hears nothing of the new page. Material animations play even when the system asks for reduced motion. No check catches a regression on a route no spec opens.

| Persona | Need in this feature |
|---------|----------------------|
| Visually impaired employee (US-014) | Hears the page in the right language, knows which page opened, skips the menu and lands on the page heading |
| Keyboard-only user | Reaches every control of the award flow without a mouse and always sees where the focus is |
| User sensitive to motion | Pages change without sliding and fading when the system asks for reduced motion |
| University (NFR-010) | Every route checked against WCAG 2.1 AA in the gate, so a regression fails the build |

## 2. Scope

**In (this feature)**

- `<html lang>` follows the interface language, at start and on every switch
- A title per route in the interface language, "‹page› — ‹brand›"
- Skip link to the main content as the first focusable element of the shell
- Focus on the page heading after every navigation, not on the first load
- Visible focus indicator on every interactive element; reduced motion respected
- Keyboard-only walk of the award flow proven by an E2E spec
- Short "Accessibility" section in the user guide (keyboard use, skip link, reduced motion, language)
- An axe sweep (WCAG 2.0/2.1 A and AA tags) of every route in both languages, both themes, at 360 and 1280 px, plus the open states that matter (dialog, menu, form errors); serious and critical findings fail the gate

**Out (where it goes)**

- Described form fields, table headers and announced changes beyond what Material gives: 8.2.2 (icebox, tracker decision 2026-10-26)
- Testing with NVDA/JAWS/VoiceOver and with screen reader users, a conformance audit, an accessibility statement: thesis limitation (tracker deviations 5 and 6)
- The backend sign-in and error pages: they already set `lang` from the locale and have their own titles; they are scanned by the sweep, not changed
- E-mails and the PDF board report: not web pages; out of WCAG page scope here

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-118 (#334) | 8.2.1 Page language, titles, skip link and focus management | 5 | no | — |
| SCRUM-120 (#336) | 8.2.3 Automated WCAG 2.1 AA sweep of every route | 3 | no | 8.2.1 (titles and skip link are part of what the sweep checks), 8.1.3 (route table of `mobile-layout.spec.ts`) |
| SCRUM-119 (#335) | 8.2.2 Described forms, tables and announced changes | 5 | no | Icebox |

Both stories are frontend only; no backend change, migration or endpoint.

## 4. Acceptance criteria

**Definitions.** *Route*: every path of the route files that renders a page, as listed in the 8.1 PRD §6 "Frontend routes" plus `/login` (backend). *Navigation*: a router navigation that changes the path; a change of query parameters only (filters, paging) is not a navigation. *Page heading*: the first `h1` inside `<main>`.

### 8.2.1 Page language, titles, skip link and focus management (SCRUM-118)

- **AC-1.1** (Page language) Given the app in Ukrainian, when the language is switched to English (or started with English stored), then `<html lang>` is `en`, and `uk` after switching back; the value changes in the same task as the translation, never before the English text is shown.
- **AC-1.2** (Page titles) Given any route, when it has loaded, then the document title is "‹page title› — ‹brand name›" in the interface language (e.g. "Мої нагороди — Облік нагород ЧНУ", "My awards — ChNU Awards"); switching the language updates it; a route showing one record uses the page kind ("Нагорода", "Редагування чернетки"), never the award title or a person's name; unknown paths use the not-found title.
- **AC-1.3** (Skip link) Given any signed-in page, when Tab is pressed once after the page has loaded, then a link "Перейти до основного вмісту" ("Skip to main content") appears at the top; Enter moves the focus to `<main>`, and the next Tab reaches the first control of the page, not the navigation drawer. Pages without the shell (offline page, public pages without a session) have the same link when they have navigation before the content.
- **AC-1.4** (Focus after navigation) Given a page, when a navigation happens (link, button, browser back or forward), then once the new page has rendered the focus is on its page heading (or on `<main>` when the page has none) and the heading is not a Tab stop of its own (`tabindex="-1"`); on the first load of the app the focus is left to the browser; a change of query parameters only keeps the focus where it is; an open dialog keeps its focus.
- **AC-1.5** (Focus visible) Given a keyboard-only walk through the shell, the award form and the award list in light and dark, then every element that receives focus shows an indicator (outline or ring) with a contrast of at least 3:1 against its background; no style removes the outline without a replacement.
- **AC-1.6** (Reduced motion) Given `prefers-reduced-motion: reduce`, when dialogs, menus, the navigation drawer, snackbars and expansion panels open and close, then they change without animation (Material animations off, CSS transitions and animations at most 0.01 ms); charts already follow the setting (`ChartThemeService`).
- **AC-1.7** (Keyboard-only award flow) Given an employee using only the keyboard, when they open "Мої нагороди", add an award, fill the title, choose a category, type a date, attach a file through the picker, save the draft and submit it, then every step is reachable with Tab, Shift+Tab, Enter, Space, the arrow keys and Escape, and the focus never lands on an invisible element.
- **AC-1.8** (User guide) Given `docs/user/USER_GUIDE.md` and its Ukrainian copy, then a section "Accessibility" lists the skip link, the focus behaviour, the keyboard keys of menus, dialogs and the date picker, the language switch and reduced motion, and states that the pages are checked automatically against WCAG 2.1 AA.

### 8.2.3 Automated WCAG 2.1 AA sweep of every route (SCRUM-120)

- **AC-3.1** (Sweep) Given every route, each signed in with the role that sees it (the route table of `mobile-layout.spec.ts`, moved to a shared `e2e/routes.ts`), when it is opened in Ukrainian and English, light and dark, at 360×740 and 1280×800, then axe with the tags `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa` reports no serious or critical violation; the spec (`accessibility.spec.ts`) runs in the gate's e2e step.
- **AC-3.2** (Open states) Given the states a page load does not show, when the sweep opens them, then they pass the same scan: the award form with field errors after "Подати" on an empty form, the confirm dialog of "Видалити чернетку", the language and profile menus, the navigation drawer at 360 px, a review decision dialog, the date picker, the documents preview dialog and the offline notice.
- **AC-3.3** (Exceptions) Given a finding that cannot be fixed in this feature (a third-party widget), then it is listed in the spec with the rule, the selector, the reason and an issue number, excluded by selector only, and named in the PR; a rule is never disabled globally.
- **AC-3.4** (Report) Given a sweep run, then the moderate and minor findings are written per rule and route to `build/accessibility-report.json` (not failing); their counts are recorded in §10 run notes at the feature validation.
- **AC-3.5** (Titles and language checked) Given every scan of AC-3.1, then the sweep also asserts the 8.2.1 contract: `<html lang>` equals the interface language, the title ends with the brand name and differs from the bare brand name, and the skip link is the first focusable element of signed-in pages.

## 5. Edge cases

| Case | Expected | AC |
|------|----------|----|
| Language switched while a dialog is open | `lang` and the title change; focus stays in the dialog | 1.1, 1.4 |
| Translation file fails to load on a switch | The language stays as it was (existing), so `lang` and title stay too | 1.1 |
| Navigation to the same path (clicking the active menu item) | No focus move | 1.4 |
| Navigation guarded by "unsaved changes" and cancelled | Focus returns to the element that started it (existing dialog behaviour) | 1.4 |
| Route redirect (`/admin` → `/admin/users`, a forbidden page) | Focus on the heading of the page finally shown | 1.4 |
| Page whose heading appears after data loads (award page) | Focus waits for the heading up to the first render after loading; falls back to `<main>` | 1.4 |
| Skip link activated on a page whose content is still loading | Focus on `<main>`; the content loads inside it | 1.3 |
| Award or person names in titles | Never; titles name the page kind only (history and tab lists are visible to others on a shared device) | 1.2 |
| Charts (ECharts canvas) | Already have a text alternative and a table switch; the sweep scans the table switch and the description | 3.1 |
| A route with no record on the database (fresh run) | Left out of that run, as in the layout sweep | 3.1 |
| Sweep runtime | About 30 routes × 8 combinations; one sign-in per role; budget 12 minutes with one worker | 3.1 |

## 6. Dependencies

**Tables / migrations**: none. **Endpoints**: none.

**Frontend**

- `core/i18n/LanguageService`: sets `document.documentElement.lang` when a language becomes active
- New `core/i18n/PageTitleStrategy` (Angular `TitleStrategy`): route `title` keys (`titles.*` in `uk.json`/`en.json`) + brand name from `BrandService`, re-applied on language change; `BrandService` stops setting the title itself
- `core/layout/ShellComponent`: skip link, `<main id="main" tabindex="-1">`, focus after navigation (router `NavigationEnd`, after render)
- `app.config.ts`: Material animations off under reduced motion (`MATERIAL_ANIMATIONS` with `animationsDisabled`), global reduced-motion CSS in `src/styles/`
- Route files: a `title` per route
- E2E: `e2e/routes.ts` (shared route table), `accessibility.spec.ts`, `keyboard.spec.ts`; `mobile-layout.spec.ts` reads the shared table

**External systems**: none. axe-core 4.13 through `@axe-core/playwright` (installed).

## 7. Technical decisions

Inherited: ADR-013 (Angular, client rendering only, Material), tracker decision 2026-10-26 (automated checks, no manual audit), QUALITY_GATES §8.2 (axe scan, keyboard-only navigation, contrast, focus indicators; the screen reader item is a limitation).

**Proposed deviations and assumptions**

- **D-1** Titles name the page kind, never the record (award title, person): the tab title is kept in the browser history and shown in tab lists of a shared device; a screen reader still reads the record from the page heading.
- **D-2** After navigation the focus moves to the page heading instead of a live-region announcement of the title: one mechanism, works with every screen reader, and the heading text is what the user needs next.
- **D-3** The sweep fails on serious and critical findings only (as all 19 existing specs do); moderate and minor ones are reported (AC-3.4). Enabling `wcag22aa` stays limited to `target-size` in the layout sweep of 8.1.3.
- **D-4** Contrast of focus indicators (AC-1.5) is checked by a computed-style assertion in the keyboard spec, since axe has no rule for it.
- **D-5** The sweep reuses the role and route table of 8.1.3 and signs in once per role; the full matrix (two languages × two themes × two widths) runs on every route, so the spec is split into one test per role for parallel workers.

## 8. Contract

No OpenAPI or migration change. i18n blocks added: `titles.*` (one key per route), `app.skipLink`. Route data: `title: 'titles.<key>'` on every route.

## 9. Test plan

| AC | Unit | E2E | Manual step |
|----|------|-----|-------------|
| 1.1 | `LanguageService` sets `lang` after the translation loads | sweep asserts `lang` (AC-3.5) | 1 |
| 1.2 | `PageTitleStrategy`: key + brand, language change, missing key falls back to brand | sweep asserts titles | 2 |
| 1.3 | shell: skip link first, target `main` | `keyboard.spec.ts` | 3 |
| 1.4 | shell: focus on `h1` after `NavigationEnd`, not on first load, not on query change | `keyboard.spec.ts` (link, back, redirect) | 4–5 |
| 1.5 | — | `keyboard.spec.ts` computed outline per focused element, light and dark | 6 |
| 1.6 | config: animations disabled under reduced motion | `keyboard.spec.ts` with `reducedMotion: 'reduce'` (dialog opens without animation) | 7 |
| 1.7 | — | `keyboard.spec.ts` keyboard-only award flow | 8 |
| 1.8 | — | — | 9 |
| 3.1–3.5 | — | `accessibility.spec.ts` | 10–11 |

## 10. Manual verification

**Preconditions**: Compose stack up (`docker compose up -d --build`), app at `http://localhost`; seed accounts `employee.fmi`, `dean.fmi`, `gdpr`, `rector` with `Passw0rd-demo`; Chrome with DevTools (Elements, Accessibility tree), keyboard only from step 3.

1. Sign in as `employee.fmi`; in Elements read `<html lang>`; switch to English with the language button; read it again; reload. Expected: `uk`, then `en`, still `en` after the reload. (AC-1.1)
2. Open `/awards`, an award page, `/awards/new`, `/profile`, `/not-a-page`; read the tab title each time; switch language on `/awards`. Expected: "Мої нагороди — Облік нагород ЧНУ", "Нагорода — …", "Нова нагорода — …", "Профіль — …", the not-found title; English titles after the switch; no award title in any tab title. (AC-1.2)
3. Reload `/awards`, press Tab once. Expected: "Перейти до основного вмісту" visible at the top; Enter, then Tab. Expected: the focus is on the first control of the award list (search or "Додати нагороду"), not in the menu. (AC-1.3)
4. From `/awards` open an award with Enter, then Alt+Left (back). Expected: the Accessibility pane shows focus on the heading "‹award›" then on "Мої нагороди"; no visible focus ring on the heading itself, a ring on the next Tab stop. (AC-1.4)
5. On `/awards` change the status filter or the page. Expected: the focus stays on the filter or paginator. Open `/admin` as `dean.fmi`. Expected: focus on the heading of `/admin/users`. (AC-1.4)
6. Tab through the shell, `/awards/new` and `/awards` in light and in dark theme. Expected: every focused element shows a ring or outline clearly visible against its background. (AC-1.5)
7. Turn on reduced motion (Windows: Settings → Accessibility → Visual effects → Animation effects off; or DevTools Rendering → Emulate `prefers-reduced-motion: reduce`), open the menu, a dialog ("Видалити чернетку") and the drawer at 360 px. Expected: they appear and disappear without sliding or fading. (AC-1.6)
8. Keyboard only: open "Мої нагороди", "Додати нагороду", fill the title, open the category list with Alt+Down and choose with the arrows and Enter, type a date, attach a PDF with the picker (Enter on the drop zone), "Зберегти чернетку", "Подати", confirm. Expected: every step done without the mouse; the submitted page opens. (AC-1.7)
9. Read the user guide section "Accessibility" against steps 3–8. Expected: the text matches the behaviour. (AC-1.8)
10. Run `npx playwright test accessibility` (or `.\tools\e2e.ps1 -Grep accessibility`). Expected: green; `build/accessibility-report.json` lists the moderate and minor findings per rule. (AC-3.1, 3.4)
11. Break the contrast on purpose (DevTools: set `--mat-sys-on-surface-variant` to `#bbb` on `/awards`) and run axe from the DevTools extension or the spec locally against that change. Expected: `color-contrast` serious; restore. (AC-3.1)

### Detours

12. Switch the language with the language menu open in a dialog (profile, date picker). Expected: `lang` and title change, the focus stays in the dialog. (§5)
13. Click the active menu item ("Мої нагороди" while on `/awards`). Expected: no focus move. (§5)
14. On a dirty award form follow a menu link and cancel the "unsaved changes" dialog. Expected: the focus returns to the link; the title stays "Нова нагорода — …". (§5)
15. Direct URL to an award page in a new tab. Expected: first load, so the focus is not moved; the title is "Нагорода — …". (AC-1.4)
16. Restart the backend (`docker compose restart app`) while on `/awards` and navigate to `/profile` once it answers. Expected: the title and the focus follow as usual; no title of the error. (AC-1.2, 1.4)
17. Access token expired in an open tab (wait 16 minutes), then navigate. Expected: silent renewal, title and focus as usual; after a session ended elsewhere the sign-in page opens with its own `lang` and title. (AC-1.1, 1.2)
18. Second browser in English, first in Ukrainian, same account. Expected: each tab keeps its own `lang` and titles (language is per browser). (AC-1.1)
19. Back after submitting an award (the submitted page). Expected: the award page or list with its own title and focus on its heading; no second submission. (AC-1.4)
20. Hidden in state: the feature adds no action on an object; the skip link is absent on pages without the shell navigation (`/offline` has none before the content). Expected: the first Tab reaches the page's own control. (AC-1.3)
21. Revoked mid-flow: the feature checks no role; when `admin` revokes the dean role while `dean.fmi` is on `/reviews`, the next navigation shows the forbidden page with its title "Немає доступу — …" and focus on its heading. (AC-1.2, 1.4)

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Focus moved before the page heading renders (lazy routes, data loads) | Medium | Medium | Move after render; fall back to `<main>`; spec covers a lazy route and the award page |
| Sweep runtime grows the gate's e2e step | Medium | Low | One sign-in per role, one test per role, parallel workers; budget 12 minutes |
| Material components report serious findings that need upstream fixes | Low | Medium | Exception list by selector with an issue (AC-3.3) |
| Title keys missing for a new route later | Medium | Low | Sweep asserts every title differs from the bare brand name (AC-3.5) |

## 12. Definition of Done

- `.\tools\gate.ps1` green (frontend lint and unit tests, prod build within the budget), full e2e green including `accessibility.spec.ts` and `keyboard.spec.ts`
- Screen check of the shell, `/awards`, `/awards/new` at 360/1280, light/dark, uk/en
- User guide (en + uk) "Accessibility"; CHANGELOG `[Unreleased]`; tracker and BACKLOG rows; TRACEABILITY NFR-010 / TC-031 status at the feature validation
- Manual verification above run and recorded at the feature validation

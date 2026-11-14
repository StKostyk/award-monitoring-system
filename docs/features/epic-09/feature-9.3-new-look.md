# Feature 9.3: New look

> **Epic**: 9 Production hardening (SCRUM-128) · **Tracker**: `docs/epics/EPIC-09-PRODUCTION-HARDENING-STATUS.md`
> **Sprint**: 10 and 11 (2026-11-16 to 2026-11-29) · **Points**: 31
> **Status**: Approved 2026-11-14
> **Governing docs**: ADR-014 and ADR-016 (addenda of this feature), `docs/frontend/UI_GUIDELINES.md` §1-8,
> ADR-023 (decision transitions), Feature 8.1 (phone layout, offline page), Feature 8.2 (WCAG 2.2 AA, focus,
> reduced motion), Feature 9.2 AC-1.1 (Lighthouse mobile 90), `openapi.yml` `POST /awards/{id}/decisions`,
> `GET /awards` (`q`), `e2e/theme.spec.ts`, `e2e/login.spec.ts`, the prototype canvas of 2026-11-14 (main
> prototype, Burgundy and Aurora sheets)

## 1. Problem and personas

The application works but looks like an unthemed Material demo: the same cards, icons and blue on every screen. For
the defense and the demo server it has to look like a product of the university, read well on a phone and make the
daily work of the busiest people faster.

| Persona | What changes for them |
|---------|-----------------------|
| Employee (`employee.fmi`) | A recognisable shell, an award list and award page that show the award's journey, a two-step "new award" flow on any screen |
| Unit secretary and dean (`secretary.fmi`, `dean.fmi`) | A review queue with a checklist, keyboard shortcuts and a 10 s undo, so a batch of requests takes minutes |
| Everyone | Light (burgundy and cream) and dark (Aurora) schemes, a command palette (Ctrl K), the same look on the sign-in page |

## 2. Scope

In:
- Theme: the `chnu` brand gets the burgundy and cream light scheme and the Aurora dark scheme as `light-dark()` pairs
  of every Material colour role and the new `--app-*` tokens; Manrope (headings) and Onest (body) in both brands;
  `neutral` keeps its green and amber and takes the new fonts and shapes.
- Lucide icons instead of Material Icons; the `motion` `animate` function for count-ups, staggered entries and
  springs; six effect components (beam, border beam, spotlight, ticker, marquee, typed text); the ECharts theme read
  from chart tokens.
- Shell: left sidebar from 960 px, a top bar with a drawer and a "+" button below; the Aurora background in the dark
  scheme; the scheme switch in the toolbar; the command palette.
- Screens: sign-in (Thymeleaf) with a live count of approved awards, registration and password pages, award list,
  award page with the journey, new award (upload, then the form; the phone steps of 9.2.5 kept), review queue with
  checklist, shortcuts and undo, and a restyle of every other screen (profile, notifications, delegations, compliance,
  administration, analytics, achievements and their public pages, privacy, error and offline pages).

Out:
- Dashboards D1 (rector, dean) and D5 (secretary), the employee home "Мої нагороди", the widgets (K1, K3, T1, T2, H1,
  H4, P2, P6, V3, V6), generated facts and records, the "remind head of department" mail (it sits on the dean
  dashboard) and the CV export (it sits on "Мої нагороди"): Feature 9.4. The current `/dashboard` keeps its widgets
  in the new style until then.
- The scheme saved per account: 9.4, with the layout in `dashboard_preferences` (decision D3 of the design).
- Scan recognition in the new-award flow (Feature 3.2); the flow uploads the file and opens the manual form.
- The year recap (D6), a share card image, a bottom navigation bar (never).

## 3. Stories

| # | Story | Pts | Parallel | Depends on |
|---|-------|-----|----------|------------|
| 9.3.1 | Theme tokens, fonts and icons | 5 | no | - |
| 9.3.2 | Effect components, motion and chart theme | 3 | no | 9.3.1 |
| 9.3.3 | Shell, navigation and command palette | 5 | no | 9.3.1 |
| 9.3.4 | Sign-in and account pages | 3 | no | 9.3.1 |
| 9.3.5 | Award list, award page and new award | 5 | no | 9.3.2, 9.3.3 |
| 9.3.6 | Review queue with checklist, shortcuts and undo | 5 | no | 9.3.3 |
| 9.3.7 | Remaining screens in the new look | 5 | no | 9.3.2, 9.3.3 |

No story has a backend part except the count on the sign-in page (9.3.4, a model attribute of `LoginController`), so
no story splits at a backend gate. 9.3.4 to 9.3.7 can run in any order once 9.3.3 is merged.

## 4. Acceptance criteria

### 9.3.1 Theme tokens, fonts and icons (SCRUM-138)

- **AC-1.1** (Two-hue schemes) Given the `chnu` brand, when the light scheme is active, then the page background is
  `#F6EFE4`, the navigation panel `#5E1621` and `--mat-sys-primary` `#7A1F2B`; when the dark scheme is active, then
  the background is `#060914`, `--mat-sys-primary` `#7DDCFF` and `--app-gold` `#F5C451`; every Material colour role
  is a `light-dark()` pair in `brands/chnu/_brand.scss`.
- **AC-1.2** (Neutral brand) Given `brand.json` names an unknown id, when the app starts, then the `neutral` brand
  applies with its green primary in both schemes and the new fonts.
- **AC-1.3** (Fonts) Given any page, when it loads, then headings use Manrope and body text Onest, both served from
  the app's own origin with the Cyrillic and Latin subsets, and no request goes to another host; Nunito, IBM Plex
  Sans and Material Icons are no longer in `package.json`.
- **AC-1.4** (Icons) Given any screen, when it renders, then every icon is a Lucide SVG, no `<mat-icon>` remains,
  and every icon-only button has an `aria-label`.
- **AC-1.5** (Contrast) Given the home, award list and award form pages in both brands and both schemes, when axe
  runs (WCAG 2.1 AA, serious and critical), then it finds nothing; gold and the ChNU cyan appear only as fills behind
  `--app-on-*` text.
- **AC-1.6** (Lighthouse) Given the Compose build, when `npm run flow:median` runs, then `/awards/new` and `/awards`
  keep mobile performance at 90 or above (9.2 AC-1.1).

### 9.3.2 Effect components, motion and chart theme (SCRUM-139)

- **AC-2.1** (Effect components) Given `shared/effects/`, when a screen uses beam, border beam, spotlight, ticker,
  marquee or typed text, then each takes its colours from tokens, the decorative ones carry `aria-hidden="true"`, and
  typed text exposes its full sentence to screen readers from the first render.
- **AC-2.2** (Reduced motion) Given `prefers-reduced-motion: reduce`, when any screen renders, then no effect moves,
  count-ups show the final number at once and list entries appear without a stagger.
- **AC-2.3** (Motion helper) Given a component that counts a number up or staggers a list, when it runs, then it
  calls the shared helper over `motion`'s `animate`, and the animation is cancelled when the component is destroyed.
- **AC-2.4** (Chart theme) Given an ECharts chart on `/analytics`, when the scheme switches, then the chart redraws
  with the series colours `--app-chart-1..8`, the axis and label colours of the new scheme, Onest labels, and no
  reload.

### 9.3.3 Shell, navigation and command palette (SCRUM-140)

- **AC-3.1** (Sidebar) Given a window of 960 px or more, when a signed-in person opens any page, then a left sidebar
  shows the logo, the sections the person may open (same permission helpers as their routes) and the current item
  marked; there is no bottom bar at any width.
- **AC-3.2** (Phone top bar) Given a window below 960 px, when a page opens, then a top bar shows the menu button,
  the page title and a "+" button that opens `/awards/new` for people who may create awards; the menu button opens
  the drawer with the same sections.
- **AC-3.3** (Scheme switch) Given the toolbar, when the person picks «Як на пристрої», «Світла» or «Темна», then
  the scheme changes without a reload and survives a reload in the same browser; the Aurora background shows only in
  the dark scheme and stays still under reduced motion.
- **AC-3.4** (Palette opens) Given a signed-in page, when Ctrl K (Cmd K on macOS) is pressed or the search field in
  the toolbar is clicked, then a dialog opens with focus in its field, lists the sections the person may open, and
  Escape closes it returning focus to where it was.
- **AC-3.5** (Palette search) Given the palette, when two or more characters are typed, then sections filter by
  name and, after 250 ms, up to 5 awards from `GET /awards?q=` appear; Enter or a click opens the chosen item; arrow
  keys move the selection.
- **AC-3.6** (Palette scope) Given an employee, when they search in the palette, then only awards the list endpoint
  returns to them appear and administration sections are not offered.

### 9.3.4 Sign-in and account pages (SCRUM-141)

- **AC-4.1** (Sign-in look) Given `/login`, when it renders in either scheme, then it shows the brand panel with
  the count of approved awards ("886 нагород" with the dev seed), the claim and the form in the new tokens and fonts,
  with no script or style from another host and no inline script.
- **AC-4.2** (Count) Given the approved awards, when the sign-in page renders, then the number equals
  `select count(*) from awards where status = 'APPROVED' and deleted_at is null`, read at most once in 10 minutes;
  when the query fails, the panel shows the claim without a number and the form still works.
- **AC-4.3** (Account pages) Given `/register`, `/registration-pending`, `/verify-email`, `/forgot-password`,
  `/reset-password` and `/confirm-email-change`, when they open, then they use the same brand panel layout as the
  sign-in page.
- **AC-4.4** (Phone and checks) Given 360 px, when the sign-in page opens with and without an error, then the brand
  strip sits above the form, controls are at least 44 px high, nothing scrolls sideways and axe finds nothing in
  either scheme.

### 9.3.5 Award list, award page and new award (SCRUM-142)

- **AC-5.1** (List) Given `/awards`, when it loads, then each row shows level, title, recipient, date and status
  chip, the status filter chips show their counts, and the filters of 9.2.5 behave as before at 360 and 1280 px.
- **AC-5.2** (Award journey) Given an award with a request, when its page opens, then a "Шлях нагороди" section lists
  submission, every decision with level, reviewer and date, and the current step, from the existing history and
  decision data.
- **AC-5.3** (New award) Given `/awards/new`, when the person drops or picks a PDF, JPG or PNG up to 10 MB, then the
  file uploads with progress and the form opens with the document attached; "Інший файл" replaces it; skipping the
  file opens the form directly.
- **AC-5.4** (Empty states) Given a list or search without results, when it renders, then an empty state with an
  icon, one sentence and the next action appears instead of an empty table.
- **AC-5.5** (Loading) Given a slow list, when data is loading, then skeleton rows show in place of the table.

### 9.3.6 Review queue with checklist, shortcuts and undo (SCRUM-143)

- **AC-6.1** (Queue layout) Given `/reviews` as a reviewer, when it opens, then the queue lists the open requests
  with the number left, and the selected request shows the document, the fields and a checklist «Документ
  читається», «Назва й дата збігаються», «Рівень визначено правильно».
- **AC-6.2** (Checklist) Given the checklist, when items are ticked, then nothing is sent or stored; approval is
  allowed with any number of ticks (the checklist is an aid, ADR-023 transitions unchanged).
- **AC-6.3** (Shortcuts) Given focus inside the queue and not in a text field, when J or K is pressed, then the next
  or previous request is selected; A opens approval and R opens the return dialog; the shortcuts are listed in a
  line under the actions.
- **AC-6.4** (Undo) Given a decision confirmed in the queue, when it is confirmed, then a snackbar «Рішення
  збережено» with «Скасувати» shows for 10 s, the request leaves the list, and `POST /awards/{id}/decisions` is
  sent only when the 10 s end; «Скасувати» within the 10 s puts the request back and sends nothing.
- **AC-6.5** (Flush) Given a held decision, when the person navigates within the app, then it is sent at once; when
  the tab closes, it is sent with `fetch` `keepalive` and the bearer token.
- **AC-6.6** (Late failure) Given a held decision, when the request returns 409 (`request-claimed`, `request-stale`,
  `request-closed`) or 403 after the 10 s, then the request comes back into the list with the problem's message and
  the queue reloads.
- **AC-6.7** (Empty queue) Given no open requests, when the queue opens or the last one is decided, then «Черга
  порожня» shows with a check mark.

### 9.3.7 Remaining screens in the new look (SCRUM-144)

- **AC-7.1** (Restyle) Given `/dashboard`, `/profile`, `/notifications`, `/delegations`, `/compliance`, `/admin`,
  `/analytics`, `/achievements`, `/units/:id/achievements`, the public achievement pages, `/privacy`,
  `/privacy-notice`, `/forbidden`, `/not-found` and `/offline`, when they open, then they use the new tokens, fonts,
  cards and icons, with no hex colour in component SCSS (stylelint rule).
- **AC-7.2** (Phone) Given each screen of AC-7.1 at 360 px, when it opens, then nothing scrolls sideways and tables
  turn into cards or scroll inside their own container.
- **AC-7.3** (Checks) Given `e2e/theme.spec.ts` extended to the screens of AC-7.1, when it runs, then axe finds
  nothing serious or critical in either brand and scheme.

## 5. Edge cases

- Scheme: an unknown stored value falls back to «Як на пристрої»; the sign-in page on another origin (dev server)
  follows the device only (UI_GUIDELINES §7).
- Palette: a 401 during the search shows sections only; a search slower than the next keystroke is cancelled
  (`switchMap`); Ctrl K inside a text field of a dialog does not open a second palette.
- Undo: two decisions within 10 s are held and sent independently; a token refresh during the hold uses the new
  token; sign-out flushes held decisions before the session ends; offline (8.1) keeps the snackbar and sends when the
  connection returns, else the request comes back with «Немає з'єднання».
- Revoked role while a decision is held: the send answers 403, the request comes back with «Немає доступу» and the
  queue reloads without it (AC-6.6).
- Shortcuts: ignored while a dialog is open, inside inputs and textareas, and with Ctrl, Alt or Meta held.
- Upload: a type or size outside the rule shows the 3.1 message before uploading; ClamAV down (503) shows «Перевірка
  файлів недоступна» and lets the person continue without a document.
- Fonts: a missing woff2 falls back to `system-ui` without layout breaking.

## 6. Dependencies

### Tables

None new. Read: `awards` (sign-in count), the history and decision data behind the award page (existing endpoints).

### Endpoints

Existing only: `GET /awards` (`q`, list and palette), `GET /awards/{id}` and its history, `POST /awards/{id}/documents`,
`POST /awards/{id}/decisions`, `GET /reviews`. No OpenAPI change.

### Services

`LoginController` (count model attribute), a cached approved-award count (10 min, Spring cache on Redis, ADR-005);
`ColorSchemeService`, `BrandService`, new `CommandPaletteService`, `HeldDecisionService`, `MotionHelper`.

### Frontend

`styles/brands/*`, `styles/_tokens.scss` (new), `core/layout/shell.*`, `shared/effects/*` (new), `shared/icons`
(Lucide provider), `features/awards/{award-list,award-detail,award-form}`, `features/reviews/{review-list,review-panel}`,
every other feature's templates for icons and tokens.

### External systems

npm: `lucide-angular`, `motion`, `@fontsource-variable/manrope`, `@fontsource-variable/onest`; removed:
`@fontsource/nunito`, `@fontsource/ibm-plex-sans`, `@fontsource/material-icons`.

## 7. Technical decisions

Inherited: Angular Material stays (ADR-014), SCSS and tokens (ADR-016), `light-dark()` colours and token-only
component styles (UI_GUIDELINES §2, §5), self-hosted fonts under the CSP (§6), the sign-in page as its own Thymeleaf
copy of the brand (§7), decision transitions untouched (ADR-023).

Agreed at the design of 2026-11-14:
1. D1: the `chnu` palette is replaced in place; each colour role is a `light-dark(burgundy, aurora)` pair computed
   once from both palettes; `neutral` stays as fallback and second-brand check.
2. D2: Lucide icons registered per use; Manrope and Onest variable fonts; CSS and Angular `animate.enter`/`leave`
   first, `motion` `animate` only for count-ups, staggers and springs; six effect components in `shared/effects/`;
   the Aurora background as fixed gradients moved by `transform` only, no `backdrop-filter` on scrolling areas.
3. D3: the scheme stays in the browser in 9.3 and moves to the account in 9.4.
4. D4: chart tokens `--app-chart-1..8`; sidebar from 960 px, top bar and drawer below, no bottom bar; manifest and
   `theme-color` `#5E1621`; the 10 s undo holds the decision in the browser (no revert transition in the backend).

### Proposed deviations and assumptions

1. The "remind head of department" mail and the CV export move to 9.4: both belong to screens 9.4 builds (dean
   dashboard, "Мої нагороди"), so 9.3 stays without new endpoints.
2. The sign-in count is all approved, not deleted awards, private ones included: an aggregate number is not personal
   data; cached 10 minutes so the sign-in page adds no query per visit.
3. The review checklist is not stored and does not gate the approval; storing it would be a schema change with no
   use in reports.
4. A stylelint rule (`color-no-hex` in `features/**` and `shared/**` SCSS) enforces the token-only rule from 9.3.7 on.
5. The scan-and-fill text of the prototype («Поля заповняться самі») is replaced by «Документ додасться до нагороди»
   until 3.2.

## 8. Contract

### 8.1 OpenAPI changes

None.

### 8.2 Migration outlines

None.

### 8.3 Properties

`app.login.award-count-ttl` (default `PT10M`), 9.3.4.

## 9. Test plan

| AC | Unit (Jest / JUnit) | IT | E2E (Playwright) |
|----|---------------------|----|------------------|
| AC-1.1, 1.2 | - | - | `theme.spec` computed styles per brand and scheme |
| AC-1.3 | - | - | `theme.spec` font family, no other hosts |
| AC-1.4 | lint rule (no `mat-icon`) | - | `theme.spec` icon buttons named |
| AC-1.5, 7.3 | - | - | `theme.spec` axe matrix |
| AC-1.6 | - | - | Lighthouse `flow:median` |
| AC-2.1 to 2.3 | effect components, `MotionHelper` (reduced motion, cancel) | - | `effects.spec` reduced motion |
| AC-2.4 | chart theme builder | - | `analytics.spec` scheme switch |
| AC-3.1 to 3.3 | `ShellComponent`, `ColorSchemeService` | - | `shell.spec` at 360 and 1280 |
| AC-3.4 to 3.6 | `CommandPaletteService`, palette component | - | `palette.spec` (employee, secretary) |
| AC-4.1, 4.2 | `LoginController` model test | `LoginPageIT` (count, cache, failure) | `login.spec` |
| AC-4.3, 4.4 | account pages | - | `login.spec` 360 px, axe |
| AC-5.1 to 5.5 | list, journey, upload step | - | `awards.spec`, `award-form.spec` |
| AC-6.1 to 6.7 | `HeldDecisionService` (timer, undo, flush, failure) | - | `review-queue.spec` (undo, shortcuts, 409) |
| AC-7.1, 7.2 | stylelint | - | `theme.spec` 360 px overflow |

## 10. Manual verification

Preconditions: `docker compose up -d --build` from the branch with the dev seed (`.\tools\reset-db.ps1` first, 886
awards), accounts `employee.fmi`, `secretary.fmi`, `dean.fmi`, `admin` with the seed password; Chrome at
`http://localhost`; a second browser profile for step 22.

1. `http://localhost/login` in light and dark (DevTools rendering, `prefers-color-scheme`). Expected: burgundy and
   cream panel with «886 нагород», then the Aurora version; Manrope headings. (AC-4.1, AC-4.2)
2. Same page at 360 px, wrong password. Expected: strip above the form, error shown, no sideways scroll. (AC-4.4)
3. Sign in as `employee.fmi`. Expected: left sidebar, current item marked, no bottom bar. (AC-3.1)
4. Toolbar scheme menu, «Темна», reload. Expected: Aurora background, dark kept after reload. (AC-3.3)
5. Resize to 360 px. Expected: top bar with menu, title and "+"; "+" opens `/awards/new`. (AC-3.2)
6. Ctrl K, type «алгеб». Expected: matching sections, then up to 5 awards; arrows and Enter open one; Escape returns
   focus. No «Адміністрування». (AC-3.4 to AC-3.6)
7. `/awards`. Expected: rows with level, title, recipient, date, status chip; filter chips with counts. (AC-5.1)
8. Filter to a status with no awards. Expected: empty state with one sentence and an action. (AC-5.4)
9. Open an approved award. Expected: «Шлях нагороди» with submission and each decision. (AC-5.2)
10. `/awards/new`, drop a 1 MB PDF. Expected: progress, then the form with the document; «Інший файл» replaces it.
    (AC-5.3)
11. DevTools, `prefers-reduced-motion: reduce`, reload `/dashboard`. Expected: numbers final at once, nothing moves.
    (AC-2.2)
12. Sign in as `dean.fmi`, `/analytics`, switch the scheme. Expected: charts redraw in the new colours without a
    reload. (AC-2.4)
13. Sign in as `secretary.fmi`, `/reviews`. Expected: queue with the number left, document, fields, checklist.
    (AC-6.1)
14. Tick one checklist item, press A, confirm. Expected: snackbar «Рішення збережено» with «Скасувати», the request
    leaves; DevTools Network shows no decision call for 10 s, then one. (AC-6.2, AC-6.4)
15. Decide the next one, press «Скасувати» within 10 s. Expected: request back, no call sent. (AC-6.4)
16. Press J and K, then type J inside the comment field. Expected: selection moves; in the field the letter is typed.
    (AC-6.3)
17. Decide the last open request. Expected: «Черга порожня». (AC-6.7)
18. Open every screen of AC-7.1 at 1280 and 360 px in both schemes. Expected: new look, no sideways scroll. (AC-7.1,
    AC-7.2)
19. Set `brand.json` to an unknown id, reload. Expected: neutral green brand with the new fonts. (AC-1.2)
20. DevTools Network on `/awards`, filter by domain. Expected: fonts and icons only from `localhost`. (AC-1.3,
    AC-1.4)
21. `npm run flow:median` in `tools/lighthouse`. Expected: 90 or above on both pages. (AC-1.6)

### Detours

22. Revoked mid-flow: as `secretary.fmi`, confirm an approval; within the 10 s, as `admin` in the second profile,
    revoke the secretary role. Expected: after 10 s the send answers 403, the request comes back with «Немає
    доступу», the queue reloads and `/reviews` then shows the forbidden page; `dean.fmi` still sees the request in
    the faculty queue. (AC-6.6)
23. Revoked mid-flow (delegation): `dean.fmi` delegates review to `secretary.fmi`; the secretary holds a decision on a
    dean-level request and the dean revokes the delegation within 10 s. Expected: 403 on the send, request back,
    the dean decides it. (AC-6.6)
24. Hidden in state: as `secretary.fmi` open an award already approved, then `POST /awards/{id}/decisions` through
    the API. Expected: the queue does not list it, A does nothing on its page, the API answers 409 `request-closed`.
25. Hidden in state: as `employee.fmi` open `/reviews` directly. Expected: forbidden page; the palette never offers
    the queue; the "+" stays.
26. Concurrency: `dean.fmi` and `secretary.fmi` both hold a decision on the same request. Expected: the second send
    answers 409, that request comes back with the message. (AC-6.6)
27. Navigate away during the hold (click «Нагороди»). Expected: the call is sent at once. Close the tab during a
    hold. Expected: the decision is stored (check the award's status). (AC-6.5)
28. Stop the network (DevTools offline) during a hold. Expected: snackbar stays; on reconnect the call goes out, else
    «Немає з'єднання» and the request comes back.
29. Reload `/reviews` within the 10 s. Expected: the decision is sent before the reload (flush) and the request is
    not listed.
30. Token expiry: leave `/reviews` open past the access token lifetime, then decide. Expected: refresh, send with the
    new token, no error.
31. Server restart (`docker compose restart app`) while the palette searches. Expected: sections stay, awards
    appear after the restart on the next keystroke.
32. Stop Redis, reload `/login`. Expected: the page renders without the number (AC-4.2).
33. Direct URLs `/awards/999999` and `/nonexistent`. Expected: the new not-found page.

The expected texts quote the planned wording; the validation replaces them with the merged i18n texts.

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Lighthouse drops below 90 (fonts, motion, gradients) | Medium | High | Subset woff2, `font-display: swap`, `motion` only where listed, AC-1.6 on every story touching the shell |
| Contrast of burgundy tints and gold in light | Medium | Medium | axe matrix, gold only as fill |
| Restyle of every screen overruns two sprints | Medium | High | 9.3.7 last; screens already token-only need little; cut public pages first |
| Held decision lost on a crash before send | Low | Medium | `keepalive` flush, decision stays undone and the request reappears; documented |
| Lucide swap misses an icon name | Low | Low | lint rule on `mat-icon`, e2e screenshots |

## 12. Definition of Done

- `.\tools\gate.ps1` green: unit, IT, FT, JaCoCo ≥ 85 %, Checkstyle, PMD and SpotBugs at 0, frontend lint and tests;
  Playwright for every story; Lighthouse 90 for 9.3.1, 9.3.3 and 9.3.5.
- Screenshots at 360 and 1280 px, light and dark, Ukrainian and English, for each changed screen.
- The manual steps of each story run and recorded in §10.
- Docs updated in the story that changes them: ADR-014 and ADR-016 addenda and `UI_GUIDELINES.md` §1, §2, §5, §6,
  §7 (9.3.1, §7 in 9.3.4), `CHANGELOG.md [Unreleased]`, the epic tracker and `BACKLOG.md`.

# Feature 9.3: New look

> **Epic**: 9 Production hardening (SCRUM-128) · **Tracker**: `docs/epics/EPIC-09-PRODUCTION-HARDENING-STATUS.md`
> **Sprint**: 10 and 11 (2026-11-16 to 2026-11-29) · **Points**: 31
> **Status**: Done 2026-11-16 (Validated 2026-11-16, Approved 2026-11-14)
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
  the count of approved awards ("789 нагород" with the dev seed), the claim and the form in the new tokens and fonts,
  with no script or style from another host and no inline script.
- **AC-4.2** (Count) Given the approved awards, when the sign-in page renders, then the number equals
  `select count(*) from awards where status = 'APPROVED'` (approved awards are never deleted), held in Redis for 10 minutes;
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
  the queue reloads. When the session ends during the hold (a revoked role or delegation signs the holder out
  everywhere), the decision is not stored, the page goes to sign-in, and the next sign-in of the same account in that
  tab names the award(s) whose decision was not saved. The request stays at its level; when its review period ends,
  Feature 4.2 marks it overdue, notifies the next level with a reviewer, and the dean takes it over from «Прострочені
  нижчого рівня».
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
2. The sign-in count is all approved awards, private ones included: an aggregate number is not personal
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
awards, 789 of them approved, 3 open requests in the queue of `secretary.fmi`), accounts `lecturer.fmi` (9 approved
awards, two with «алгебр» in the title; `employee.fmi` owns none in the seed), `secretary.fmi`, `dean.fmi`, `admin`
with the seed password; every seed account acknowledges the privacy notice at its first sign-in; Chrome at
`http://localhost`; a second browser profile for step 22.

1. `http://localhost/login` in light and dark (DevTools rendering, `prefers-color-scheme`). Expected: burgundy and
   cream panel with «789 нагород», then the Aurora version; Manrope headings. (AC-4.1, AC-4.2)
2. Same page at 360 px, wrong password. Expected: strip above the form, error shown, no sideways scroll. (AC-4.4)
3. Sign in as `lecturer.fmi`. Expected: left sidebar, current item marked, no bottom bar. (AC-3.1)
4. Toolbar scheme menu, «Темна», reload. Expected: Aurora background, dark kept after reload. (AC-3.3)
5. Resize to 360 px. Expected: top bar with menu, title and "+"; "+" opens `/awards/new`. (AC-3.2)
6. Ctrl K, type «алгеб». Expected: matching sections, then up to 5 awards; arrows and Enter open one; Escape returns
   focus. No «Адміністрування». (AC-3.4 to AC-3.6)
7. `/awards`. Expected: rows with level, title, recipient, date, status chip; status chips «Усі», «Чернетка» …
   with counts; a chip filters the list like the status filter. Throttle the network to Slow 3G and reload:
   skeleton rows until the list arrives. (AC-5.1, AC-5.5)
8. Click the chip of a status with no awards. Expected: empty state with an icon, «Немає нагород, що відповідають
   пошуку.» and «Скинути фільтри». (AC-5.4)
9. Open an award that went through review (one submitted in an earlier step, or an approved one with a request).
   Expected: «Шлях нагороди» with «Подано», each decision with level, reviewer and date, and «Зараз» on the current
   step while it is under review. (AC-5.2)
10. `/awards/new`, drop a 1 MB PDF. Expected: «Документ додасться до нагороди», progress, then the form of a new
    draft titled after the file with the document listed; «Інший файл» on that document replaces it;
    «Заповнити без документа» opens the empty form; a `.txt` file is refused before uploading. (AC-5.3)
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
    revoke the secretary role. Expected: revocation signs the holder out everywhere, so after 10 s the send answers
    401 and the page goes to `/login`; after a new sign-in in the same tab `/reviews` shows the forbidden page with
    «Ваш доступ змінився, поки рішення очікувало. Рішення не збережено, нагороди: №<id>». No decision row exists
    for the request; it stays at `FACULTY_SECRETARY`. After its deadline passes and the overdue job runs (4.2 §9
    preconditions), `dean.fmi` gets the overdue e-mail and lists it under «Прострочені нижчого рівня», from where the
    dean takes it over. (AC-6.6)
23. Revoked mid-flow (delegation): `dean.fmi` delegates review to `secretary.fmi`; the secretary holds a decision on a
    dean-level request and the dean revokes the delegation within 10 s. Expected: 401 on the send (the delegate is
    signed out), the decision is not stored, the next sign-in of the secretary names the award, and the dean decides
    it. (AC-6.6)
24. Hidden in state: as `secretary.fmi` open an award already approved, then `POST /awards/{id}/decisions` through
    the API. Expected: the queue does not list it, A does nothing on its page, the API answers 409 `request-closed`.
25. Hidden in state: as `lecturer.fmi` open `/reviews` directly. Expected: forbidden page; the palette never offers
    the queue; the "+" stays.
26. Concurrency: two sessions of `secretary.fmi` both hold a decision on the same request. Expected: the second send
    answers 409 and the message «Розгляд цієї нагороди вже завершено» shows; the reloaded queue no longer lists it.
    (AC-6.6)
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
33. Direct URLs `/awards/999999`, `/admin/users/999999` (as `admin`) and `/nonexistent`. Expected: the not-found card
    (icon, heading, one sentence, the way back) in every case, not a bare line of text. (9.3.9)
34. Palette with decomposed text: paste «Мій дашборд» typed as NFD (`'Мій'.normalize('NFD')`, e.g. from macOS) into
    the palette. Expected: «Мій дашборд» is offered as for the typed text. (9.3.9)

The expected texts quote the planned wording; the validation replaces them with the merged i18n texts.

### Run 2026-11-16

Compose stack rebuilt at a70dd31 (app and frontend), default settings, seed 961 awards (808 approved) after earlier e2e runs; secretary role and delegation restored. Result: 31 pass, 3 fail.

| Step | Result | Evidence |
|---|---|---|
| 1 | PASS | login light: cream bg + burgundy panel rgb(94,22,33), «808 нагород» (seed now 961/808 after e2e runs); dark: Aurora navy rgb(6,9,20); Manrope headings |
| 2 | PASS | 360px wrong password: strip (brand) above form, error «Невірна адреса або пароль.», scrollWidth 360=clientWidth |
| 3 | PASS | lecturer.fmi: sidenav 264px left, aria-current=«Мій дашборд», no bottom bar in screenshots |
| 4 | PASS | scheme menu «Темна»: Aurora glow visible, body rgb(6,9,20) kept after reload |
| 5 | PASS | 360px: top bar menu + title «Мій дашборд» (truncated) + search + '+'; '+' -> /awards/new; scrollWidth 360 |
| 6 | PASS | Ctrl K «алгеб»: no section matches, 2 awards; ArrowDown+Enter opened /awards/9; Escape returns focus to the opener; no «Адміністрування» (palette «нагород» lists «Мої нагороди» + award) |
| 7 | PASS | /awards 10 rows: level chip, title, recipient, date, status chip; chips Усі 10/Чернетка 1/Затверджено 9/…; Затверджено chip -> 9 approved rows; Slow-3G-like throttle shows awards-loading skeleton |
| 8 | PASS | chip «На розгляді 0»: empty state «Немає нагород, що відповідають пошуку.» + «Скинути фільтри» |
| 9 | PASS | /awards/968 (under review): «Шлях нагороди», «Подано», «Секретар факультету · Зараз · очікується до 19.11»; /awards/897 and 932 after the secretary's decision: «Схвалено · Аліна Секретаренко · Секретар факультету · date», dean step «Зараз». (/awards/9 seed has no request: «пропущено») |
| 10 | PASS | fresh mr93a: 1 MB PDF -> progress, /awards/970/edit titled «Грамота МОН» with document; «Інший файл» replaced it (scan.pdf, 1 item); .txt refused «Непідтримуваний формат файлу»; «Заповнити без документа» opens empty form at /awards/new |
| 11 | PASS | reduced-motion: dashboard numbers 9/3 final in the first sample; normal motion counts 1..9 over ~0.5 s |
| 12 | PASS | dean.fmi /analytics (-> units/9): chart fills change in place with the scheme (light rgb(122,31,43)… -> dark rgb(125,220,255)…), no reload |
| 13 | PASS | secretary.fmi /reviews: «Залишилось: 3», document block, fields, checklist (3 items), shortcuts hint |
| 14 | FAIL | A is ignored after ticking a checklist item (focus on the checkbox INPUT); after a click on the heading A works: snackbar «Рішення збережено / Скасувати», no call at 1 s, one POST after ~10 s. SCRUM-147 |
| 15 | PASS | decided next (queue 2->1), «Скасувати» within 10 s: request back (2), no decision POST in 12 s |
| 16 | PASS | J moves to the next request, K back; in the return dialog comment field «jk J» typed as text, dialog closes with Esc |
| 17 | PASS | last open request (968) decided with A: queue «Залишилось: 0 / Черга порожня» |
| 18 | PASS | admin (+dean, gdpr for /delegations, /analytics, /compliance) 14 screens x 1280/360 x light/dark: 0 sideways scroll, new look; compliance table scrolls inside its card at 360 |
| 19 | PASS | brand.json id replaced with an unknown id (route fulfil): html.brand-neutral, primary #2d6a4f green, Onest Variable font, cream-green bg |
| 20 | PASS | /awards load + reload: only host localhost (4 woff2 fonts, svg logo/icons) |
| 21 | FAIL | flow:median twice: /login 99, /awards/new 85 and 84, /awards 75 and 75 (TBT 0.27-0.53). SCRUM-148 |
| 22 | FAIL | secretary holds 971, admin revokes FACULTY_SECRETARY: send answers 401 (signed out everywhere, by design; step text corrected), page -> /login; fresh sign-in gets 403 on /reviews. FAIL: dean.fmi does not see the orphaned secretary-level request (lists DEAN-level only), see SCRUM-149 |
| 23 | PASS | step text corrected: secretary (delegated DEAN) holds 932, dean DELETE /delegations/2 within the hold -> send answers 401 (loss of authority signs the holder out everywhere, AUTHENTICATION_AUTHORIZATION role assignment/delegation), page goes to /login, decision not stored, dean's queue still lists 932 |
| 24 | PASS | secretary on /awards/9 (approved): A opens no dialog; not in the queue; POST /awards/9/decisions -> 409 urn:awards:problem:request-closed |
| 25 | PASS | lecturer.fmi /reviews -> /forbidden; no nav item, palette 'розгляд' offers nothing; '+' stays at 360 px (phone-only control) |
| 26 | PASS | two secretary sessions hold APPROVE on 981: A 200, B 409 «Розгляд цієї нагороди вже завершено» (request leaves B's reloaded queue). Step text corrected: dean and secretary never see the same request (secretary queue lists level-1, dean DEAN-level); with two dean sessions B got 404 instead of 409 on 897 (moved to rector level) |
| 27 | PASS | 974: click «Нагороди» during hold -> POST /awards/974/decisions sent at once, APPROVED; 975: tab closed during hold -> APPROVED (psql) |
| 28 | PASS | 976: offline during hold -> snackbar «Рішення надішлеться, щойно з'єднання відновиться» stays, no call; back online -> POST sent (16 s), APPROVED |
| 29 | PASS | 977: reload within the hold -> decision stored (APPROVED) and request not in the queue after reload |
| 30 | PASS | dean.fmi left /reviews open 16 min (token ttl 15m): silent POST /oauth2/token 200 at 12:33, held approve of 932 at 12:38 answered 200 with the new token, no error (it was a DEAN-level request, the 'крок 30' item is not in the dean's queue) |
| 31 | PASS | backend stopped/started (docker stop/start, same JVM restart) while the palette was open: during the outage typing gave 0 options and no crash (502), after healthy the next keystroke «алгеб» listed the 2 awards again (401 after key rotation handled) |
| 32 | PASS | redis stopped, /login reloaded: form renders, no «N нагород» number; redis restarted (healthy) |
| 33 | PASS | admin: /awards/999999, /admin/users/999999, /nonexistent each show the not-found card (icon, heading «Не знайдено»/«Користувача не знайдено»/«Сторінку не знайдено», sentence, link back) |
| 34 | PASS | palette with NFD «Мій дашборд» (12 code points vs 11) offers «Мій дашборд» like the typed text |

Step 21 after 9.3.21 (SCRUM-148, Compose build, Lighthouse 12.8.2, median of 3): before `/awards/new` 80, `/awards`
77 (6.5 GB free); after 84 and 77 (3.9 GB free); accessibility 100, best practices 100 and 96. Both pages lose only on
blocking time. The long tasks (`npm run timeline`, phone-sized since 9.3.21, and a source-mapped CPU profile) hold no
single hotspot: module evaluation of the 848 kB initial bundle (~600 ms at 4x CPU), one re-render of the shell per
response of the sign-in chain (users/me, consents, privacy), and the first render of the shell with the page
(~425 ms on `/awards/new`, ~500 ms on `/awards`), where Material form fields, the drawer list and starting the
requests (~14 ms each) weigh most. An empty award list template leaves `/awards` at 77. 9.3.21 starts the five
status-count requests and the category request of `/awards` in tasks of their own (largest `/awards` task 607 →
449 ms simulated). A CSS stagger and a drawer list built only on opening gained about one point each and were left
out. AC-1.6 stays as decided on 2026-11-15 (measured again on the demo server in 9.2.4).

Close-out 2026-11-16: all 34 steps were run by hand. The failed steps are accepted on the tests of their fix
stories: step 14 (SCRUM-147, PR #416) on `review-queue.component.spec`
ac14_shortcuts_keep_working_after_a_checklist_item_is_ticked and e2e `review-queue.spec` «ac6_1 to ac6_4», which
now ticks a checklist item before A; step 22 (SCRUM-149, PR #418) on e2e `review-queue.spec` «ac1 the decision lost
with the session is named after the next sign-in», `sign-in-notice.service.spec` and `held-decision.service.spec`,
with the orphaned secretary-level request left to 4.2 (overdue at the deadline, taken over by the dean); step 21
(SCRUM-148, PR #417) under the AC-1.6 decision above.

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

## Validation (2026-11-16)

Result: **PASSED WITH NOTES**, pending the manual run of §10. Stories 9.3.1-9.3.7 merged (#399-#403, #405, #406);
9.3.8 (#408, stable e2e) and 9.3.9 (#409, the two defects below) in review.

Suite on `develop` plus 9.3.8: unit 1 380, IT/FT 574, JaCoCo 98.7 % lines, Checkstyle/PMD/SpotBugs 0; frontend lint
(ESLint, Stylelint), 1 175 unit tests, production build 845 kB; Playwright 220/220 (one worker: 150 in the first pass
before the 45 min timeout, 70 in the follow-up; keyboard ac1_7 and the dean accessibility sweep passed on retry).

| AC | Evidence (all passing) |
|----|------------------------|
| 1.1, 1.2, 1.3 | `theme.spec` «applies the ChNU brand with self-hosted fonts», «uses the aurora scheme», «applies the neutral brand»; `brand.service.spec` ac1_1 |
| 1.4 | `theme.spec` «draws every icon as a Lucide SVG»; `icon.component.spec` ac1_4 (3) |
| 1.5, 7.3 | `theme.spec` axe matrix (brand × scheme), `theme.spec` ac7_3 per screen group |
| 1.6 | Accepted below 90 on the development machine (tracker decision 2026-11-15); measured again on the demo server in 9.2.4; long tasks and 9.3.21 medians under §10 Run 2026-11-16 |
| 2.1-2.3 | `effects.spec` (unit, 13), `motion.helper.spec` (4), `count-up.directive.spec` (2), e2e `effects.spec` ac2_2, ac2_3 |
| 2.4 | `chart-theme.service.spec` (2), `chart.component.spec`, e2e `university-analytics.spec` 9.3 ac2_4 |
| 3.1-3.3 | `shell.component.spec` (7), e2e `shell.spec` ac3_1, ac3_2, ac3_3 |
| 3.4-3.6 | `command-palette.service.spec` (6), `command-palette.component.spec` (12), e2e `palette.spec` (3) |
| 4.1, 4.2 | `LoginAwardCountTest` (5), `LoginControllerTest` (3), `LoginPageIT` (3), `LoginPageFT` (2), e2e `login.spec` ac4_1 |
| 4.3, 4.4 | `auth-layout.component.spec` (2), e2e `login.spec` ac4_3, ac4_4 |
| 5.1, 5.4, 5.5 | `award-list.component.spec` ac5_1 (4), ac5_4 (2), ac5_5; `empty-state.component.spec`; e2e `award-look.spec` ac5_1, ac5_4 ac5_5 |
| 5.2 | `award-status.component.spec` ac5_2, e2e `award-status.spec` ac5_2 |
| 5.3 | `award-upload-step.component.spec` (8), `award-form.component.spec` ac5_3 (4), `award-documents.component.spec`, e2e `award-look.spec` ac5_3 (2) |
| 6.1-6.3, 6.7 | `review-queue.component.spec` ac6_1, ac6_2, ac6_3, ac6_7 and two edge cases; e2e `review-queue.spec` ac6_1 to ac6_4 |
| 6.4-6.6 | `held-decision.service.spec` (11), `review-list.component.spec` ac6_4, ac6_6, `auth.service.spec` ac6_5; e2e `review-queue.spec` ac6_5, ac6_6 |
| 7.1, 7.2 | Stylelint colour rules in the gate; e2e `theme.spec` ac7_1, ac7_2 per screen group |

Edge cases of §5: unknown stored scheme (`color-scheme.service.spec` ac4_ignores_an_unknown_stored_value), palette
401 and slower answers (ac3_5 tests), Ctrl K inside a dialog (`command-palette.service.spec`), two holds, sign-out,
offline and revoked role (`held-decision.service.spec` edge tests), shortcuts (review-queue edge test), upload rules
and ClamAV down (`award-upload-step.component.spec` edge tests, e2e), font fallback (`system-ui` in
`--app-font-heading/body`). Token refresh during a hold: `auth.service.spec` ac4_6 for the in-page send; see note 2.

Manual walk on the Compose build (2026-11-16, fresh seed): steps 3-10 and 13-17 passed; detours 25, 32 and 33 run
(33 failed, fixed in 9.3.9); detour 24 accepted on `DecisionFT.ac2_8_aReturnedAwardIsNoLongerReviewableAndARepeatedDecisionIsClosed`.
Steps 1, 2, 11, 12 and 18-20 rely on the e2e tests above in this pass.

Findings:
1. Record pages (award, award form, submitted award, user, unit analytics) showed a bare line instead of the
   not-found card; the palette compared section names without Unicode normalisation (NFD input from macOS found
   nothing). Fixed in 9.3.9 with unit tests (`ac9_1`, `ac9_2`) and e2e `award-look.spec` ac9_1; steps 33-34.
2. A held decision sent on tab close uses the stored access token as it is; after a sleep longer than the token's
   lifetime the send answers 401 and the request reappears in the queue undecided (risk §11, row 4). Kept.
3. A 429 on a held decision shows the generic message; the request comes back. Kept.
4. Refactor sweep: shared shortcut guard and Apple-platform check (review queue, palette, shell), repeated card and
   facts-collapse SCSS blocks (9 files), the brand mark repeated in shell and auth layout, fixed waits in
   `review-queue.spec`. No defect; listed for a later clean-up.

# UI Guidelines

**Status**: Active
**Date**: 2026-10-04 (new look 2026-11-14)
**Author**: Stefan Kostyk
**Related**: [ADR-016 Styling](../architecture/adr/ADR-016-Styling.md) (addendum 2026-10-04), [ADR-014 UI Component Library](../architecture/adr/ADR-014-UI-Component-Library.md)

---

## 1. Themes and brands

| Brand | Use | Primary | Navigation panel | Accent | Surfaces (light / dark) | Font |
|-------|-----|---------|------------------|--------|-------------------------|------|
| `chnu` | Chernivtsi National University | light: burgundy `#7A1F2B` (gradient to `#A23B47`); dark: aurora cyan `#7DDCFF` | `#5E1621` / `#0B1124` | gold `#C9952B` / `#F5C451` (fill only; text form `#8A5A00`) | `#F6EFE4`, cards `#FFFCF6` / `#060914`, cards `#0B1124` | Manrope (headings), Onest (text) |
| `neutral` | fallback and second-brand check | green `#2D6A4F` / `#8FD5B0` | `#1E3D30` | amber `#E0A526` | `#F6F7F5` / `#181C1A` | Manrope, Onest |

- The ChNU brand has two hues in one theme: burgundy and cream in the light scheme, the aurora (deep navy with cobalt, cyan and gold light) in the dark one. Every Material colour role in `brands/chnu/_brand.scss` is a `light-dark(<burgundy>, <aurora>)` pair.

- A brand is a folder `frontend/src/styles/brands/<id>/` with `_theme-colors.scss` (tonal palettes from the brand colours, Angular Material `theme-color` schematic) and `_brand.scss` (`theme()` mixin), included in `styles.scss` under `html.brand-<id>`, and the id in `BRAND_IDS` (`core/brand/brand.service.ts`).
- The deployment chooses the brand with `brand/brand.json`: `id`, `name` and `organization` (each `{ "uk", "en" }`) and `logo` (a path inside the app, `.svg` or `.png`). The image ships the ChNU file; another deployment mounts its own file and logo over `/usr/share/nginx/html/brand/`.
- The installed app takes its name and icon from `brand/manifest.webmanifest` and `brand/icons/` (192 and 512 px, a 512 px maskable icon with the logo inside the central 72 %, and a 180 px `apple-touch-icon` on the logo colour); the neutral set lives in `brand/neutral/` and a deployment copies it together with `brand.json`. The manifest is in Ukrainian only and its `theme_color` is the brand primary, which `BrandService` also sets on `<meta name="theme-color">`.
- The neutral theme applies when the file is missing, unreadable, names an unknown id, lacks a language or points the logo outside the app.

## 2. Tokens

Component styles take colours, type and shape from tokens only. No hex, `rgb()` or named colours in component SCSS, and no fallback values inside `var()`.

| Token | Meaning |
|-------|---------|
| `--mat-sys-*` | Material 3 system tokens (surface, on-surface, primary, outline-variant, error, type scale `--mat-sys-title-medium` …) |
| `--app-nav-container`, `--app-nav-on-container`, `--app-nav-active` | side menu panel, its text and the current item |
| `--app-gold`, `--app-on-gold`, `--app-gold-text` | gold accent as a fill, the text colour that reads on it, and the darker gold for text on light surfaces |
| `--app-cyan`, `--app-on-cyan` | the ChNU cyan as a fill, with its text colour |
| `--app-ok`, `--app-bad` | positive and negative figures (deltas, checks) |
| `--app-accent-from`, `--app-accent-to`, `--app-accent-gradient` | gradient stops (each a `light-dark()` colour) and the gradient built from them for primary buttons and highlights (a plain primary in the neutral brand) |
| `--app-aurora-1` … `--app-aurora-3` | background light of the dark ChNU scheme (transparent in the neutral brand) |
| `--app-font-heading`, `--app-font-body` | Manrope and Onest stacks |
| `--app-card-radius`, `--app-radius-sm/md/lg` | brand corner radius for custom cards, and the radius scale |
| `--app-icon-size` | drawn size of an `app-icon` (24 px by default; `inline` icons take the text size) |
| `--app-chart-1` … `--app-chart-8` | categorical series colours of charts (burgundy, gold, teal … in light; cyan, gold, rose … in dark), each at least 3:1 against the surfaces |
| `--app-chart-bar`, `--app-chart-band-1` … `--app-chart-band-5` | single-series bars (`--app-chart-1`) and the five steps of one hue for ordered values such as levels (burgundy in light, cyan in dark; monotone lightness, at least 2:1 against the surfaces) |
| `--app-status-<status>`, `--app-on-status-<status>` | award status chips (`draft`, `pending`, `approved`, `rejected`, `archived`); the same in every brand |

Brand-independent tokens and their defaults live in `styles/_tokens.scss`; a brand mixin overrides them.

Material component colours are changed with the component's `*-overrides` mixin or its `--mat-<component>-*` tokens, never by targeting internal `.mdc-*` classes.

## 3. Colour and contrast

- Text 4.5:1, large text and control borders 3:1 (WCAG 2.2 AA) in light and dark.
- Gold and the ChNU cyan are below 3:1 on light surfaces: they are fills behind `--app-on-gold` / `--app-on-cyan` text, never a text or icon colour there (`--app-gold-text` is the text form of gold).
- State is never colour alone: status chips carry the status name, the archived chip adds a dashed outline.

## 4. Layout

- Shell: side menu from 960 px (`WIDE_LAYOUT`), drawer with a menu button below; no bottom bar at any width. The sections come from one list, `core/layout/nav-entries.ts`, filtered by the same permission helpers as the route guards; the side menu and the command palette both read it. Below 960 px the top bar shows the menu button, the page title (`PageTitleStrategy.page`) and a «+» button to `/awards/new` for people who may create awards. The toolbar holds the palette field (Ctrl K, Cmd K on macOS), the language switch, the scheme switch, the bell, the user menu and sign-out; below 600 px (`NARROW_SCREEN`) the palette field becomes an icon button and the scheme switch and sign-out move into the user menu.
- Command palette (`CommandPaletteService`, `core/layout/command-palette`): a dialog with one combobox field; it lists the allowed sections, filters them from 2 characters and after 250 ms adds up to 5 awards from `GET /awards?q=` (a failed search shows sections only). Arrow keys move the selection, Enter or a click opens it, Escape closes it and returns the focus. It never opens over another dialog.
- Content width up to 1200 px, padding 28 px (16 px on phones). Check every screen at 360 px and 1280 px; nothing scrolls sideways.
- Navigation items carry an icon and a label; new top-level sections are added to the side menu, guarded by the same permission helper as their route.
- Review queue (`features/reviews/review-queue`): one request at a time above the list, with document, fields and a checklist that is never sent. Single-letter shortcuts (J, K, A, R) read `KeyboardEvent.code`, so they work on the Ukrainian layout, and are ignored inside text fields, pickers and menus, with Ctrl, Alt or Meta held and while a dialog is open; the line under the actions lists them. A decision confirmed there is held for 10 s by `HeldDecisionService` with «Скасувати» in a snackbar; it goes out at once on navigation and before sign-out (`AuthService.beforeSignOut`), and with a `fetch` keepalive request and the bearer token when the tab closes.
- Lists: while a list loads it shows `app-skeleton-rows` (`shared/skeleton`, a named `role="status"` region, the pulse stops under reduced motion) in place of the rows; a list or search without results shows `app-empty-state` (`shared/empty-state`: an icon, one sentence and the next action projected as a button or link) instead of an empty table. Status filters with counts are chips (`aria-pressed`) that scroll inside their own row on phones.

## 5. Dark mode

- The scheme follows the device (`color-scheme: light dark`); the toolbar scheme switch offers «Як на пристрої», «Світла», «Темна», kept in the browser.
- The aurora background of the shell shows only in the dark scheme: three fixed radial gradients from `--app-aurora-*` that move by `transform` only (no `backdrop-filter`, no `filter`), still under reduced motion and below 600 px, `aria-hidden`.
- New colours are written as `light-dark(<light>, <dark>)` inside a brand or token mixin, never as two separate rule sets.
- In the ChNU brand the light scheme is burgundy and the dark scheme is the aurora; both come from the same pairs, so no component knows which hue is active.

## 6. Fonts, icons and motion

- Manrope (headings, Material brand family) and Onest (text, Material plain family) are variable fonts served by the app (`@fontsource-variable`, `angular.json` styles); the browser loads only the Cyrillic and Latin files the page needs (`unicode-range`). No third-party font requests (CSP `font-src 'self' data:`); a missing file falls back to `system-ui`.
- Icons are Lucide SVGs drawn by `<app-icon name="…">` (`shared/icons`); `icons.ts` lists every icon the app uses, so only those reach the bundle. A new icon is added there first. Icons are decorative (`aria-hidden`); icon-only buttons need an `aria-label`. The host carries the `mat-icon` class, so Material sizes and spaces it; inside a button or menu item it takes the `matButtonIcon` / `matMenuItemIcon` attribute so Material projects it into the icon slot. `@angular/material/icon` is blocked by the lint rule `no-restricted-imports`.
- Motion is CSS transitions and Angular `animate.enter`/`animate.leave` first. The `motion` library's `animate` is used only through `MotionHelper` (`shared/effects`) for count-ups (`[appCountUp]`), staggered list entries (`appStagger`) and springs; each animation stops when its component is destroyed. Only `transform` and `opacity` move, never layout properties or `backdrop-filter`.
- Effect components in `shared/effects`: `app-beam`, `app-border-beam` and `app-spotlight` (decorative, `aria-hidden`, placed inside a positioned parent), `app-ticker` and `app-marquee` (lines of text; screen readers get one static list, the moving copy is hidden; both are focusable and pause under the pointer or while focused) and `app-typed-text` (the whole sentence is in a visually hidden copy from the first render). Colours come from `--app-accent-*`, `--app-gold`, `--app-cyan` and `--app-aurora-*` only.
- Under `prefers-reduced-motion: reduce` nothing moves: count-ups show the final number, lists appear without a stagger, typed text is complete, the ticker stays on its first line, the marquee wraps, beams and the spotlight stand still, and charts draw without animation.
- Charts take their colours from the `--app-chart-*` tokens through `ChartThemeService`, which reads them again when the scheme changes, so a chart redraws in the new scheme without a reload; labels use the body font (Onest).

## 7. Sign-in and error pages

The authorization server renders the sign-in, error and 429 pages itself (Thymeleaf, `backend/src/main/resources/templates`), so they carry their own copy of the brand:

- `APP_BRAND` (`app.brand.id`, default `chnu`) picks the brand; the ids are those of `brand.json`, and an unknown id gives the neutral brand. The browser application and the backend are configured separately, so a deployment sets both.
- `static/login-assets/brand-<id>.css` holds the brand tokens (`--lp-*`, `light-dark()` values copied from the Material theme roles and the `--app-nav-*`, `--app-aurora-*` tokens of `brands/<id>/_brand.scss`), `login.css` the layout and the self-hosted Manrope and Onest variable fonts (Cyrillic and Latin subsets copied from `@fontsource-variable`, OFL notices beside them), `<id>/logo.svg` the logo; product name and organisation are the message keys `brand.<id>.name` and `brand.<id>.organization`.
- Layout: brand panel (logo, name, organisation, the count of approved awards and the claim) beside the form from 760 px, a strip above it on phones (logo, name and count); controls at least 44 px high.
- The count is `select count(*) from awards where status = 'APPROVED'`, held in Redis under `login:award-count` for `app.login.award-count-ttl` (`APP_LOGIN_AWARD_COUNT_TTL`, default 10 minutes); when Redis or the query fails the panel shows the claim without a number. Ukrainian plural forms come from `login.count.one|few|many`.
- The account pages of the browser application (`/register`, `/registration-pending`, `/verify-email`, `/forgot-password`, `/reset-password`, `/confirm-email-change`) wrap their card in `app-auth-layout` (`features/auth/auth-layout`): the same panel with logo, name, organisation and claim, drawn from `--app-nav-container` and the aurora tokens; it has no count, so the browser application needs no new endpoint.
- The scheme follows the device; on the application's own origin (production, the container stack) `scheme.js` also applies the scheme chosen in the app. No inline script or style, nothing from another host.
- A new brand adds `brand-<id>.css`, the logo folder and the message keys next to its Angular theme.

## 8. Checks

- `e2e/theme.spec.ts`: brand class and title, brand colours and fonts per scheme, fallback to neutral, Lucide icons with named icon-only controls, scheme switch, phone drawer, axe (WCAG 2.1 AA, serious and critical) on home, award list and award form for each brand in light and dark.
- `e2e/login.spec.ts`: sign-in page brand, count and claim, Manrope and Onest without other hosts, 360 px strip above the form, target sizes and axe in light and dark, also with an error shown; the brand panel on the six account pages, and the phone strip with axe on `/register`.
- `e2e/award-look.spec.ts`: award list rows, status chips with counts, empty states, skeleton rows, the document step of `/awards/new` (upload, «Інший файл», refused file, scanner outage), axe on the list and the step; `e2e/award-status.spec.ts` covers «Шлях нагороди».
- `e2e/review-queue.spec.ts`: checklist, A with undo and the 10 s hold checked in the database, flush on navigation, a request changed during the hold coming back.
- `/ui-check` screenshots at 360 and 1280 px, light and dark, Ukrainian and English for every changed screen.

## Manual verification

1. Run `docker compose up -d --build`, open `http://localhost` and sign in as `employee.fmi@chnu.edu.ua` / `Passw0rd-demo`. Expected (light scheme): cream background `#F6EFE4`, burgundy side menu `#5E1621` with the logo, «Облік нагород ЧНУ» and the university name; the tab title is «Облік нагород ЧНУ»; headings in Manrope, text in Onest; every icon a line icon (Lucide).
2. Click «EN». Expected: the tab title becomes «ChNU Awards», the menu and the name switch to English. Switch back.
3. Open the scheme switch in the toolbar (in the user menu below 600 px) → «Темна». Expected: the aurora scheme (background `#060914` with slow cobalt, cyan and gold light behind the content, cyan primary `#7DDCFF`, gold accents), readable text and status chips; reload keeps the dark scheme. Choose «Як на пристрої» and change the system setting: the app follows it.
4. Narrow the window below 960 px (or use the phone view of the developer tools at 360 px). Expected: the side menu disappears, the top bar shows the page title and a «+» button, a menu button opens the side menu as a drawer, choosing «Мої нагороди» opens the list and closes the drawer; no sideways scrolling.
5. Open «Мої нагороди» and use the «Статус» filter for the rejected and archived chips (no screen archives an award yet: set one with `update awards set status='ARCHIVED' where award_id=<id>`). Expected: status chips coloured by status (grey draft, amber pending, green approved, red rejected, outlined archived) in both schemes.
6. In the developer tools, Network tab, reload. Expected: no request to `fonts.googleapis.com` or `fonts.gstatic.com`; the font files (Manrope and Onest, Cyrillic and Latin) come from `localhost`.
7. Replace the brand: `docker compose exec frontend sh -c "echo '{\"id\":\"neutral\",\"name\":{\"uk\":\"Облік нагород\",\"en\":\"Award Registry\"},\"organization\":{\"uk\":\"Університет\",\"en\":\"University\"},\"logo\":\"brand/neutral/logo.svg\"}' > /usr/share/nginx/html/brand/brand.json"` and reload. Expected: green neutral theme in Manrope and Onest with the neutral logo. Restore with `docker compose up -d --build --force-recreate frontend`.
8. Sign out and open `http://localhost` with the browser language set to Ukrainian (the sign-in page follows the browser language, not the choice made in the app). Expected: the sign-in page has the burgundy brand panel with the logo, «Облік нагород ЧНУ», the university name, the gold count («886 нагород» with the dev seed) and the claim beside the form on a cream page (a strip with logo, name and count above it below 760 px), Manrope headings and Onest text, a burgundy «Увійти» button; «English» switches the page and the brand name to English.
9. In the app choose «Темна», sign out. Expected: the sign-in page is dark as well (aurora: `#060914` page, navy panel with cyan count); with «Як на пристрої» it follows the system setting.
10. Enter a wrong password. Expected: the error appears in a red box inside the card, readable in both schemes; at 360 px nothing scrolls sideways.
11. Set `APP_BRAND=neutral` (for example `APP_BRAND=neutral docker compose up -d app`) and reload the sign-in page. Expected: green neutral panel, Manrope and Onest, «Облік нагород» and «Університет». Restore with `docker compose up -d app`.

### Run 2026-10-11

docker compose stack, default settings (brand swap and APP_BRAND=neutral per steps 7/11, restored), commit fc2f1b3, browser contexts uk-UA for the sign-in steps. Result: 11 pass.

| Step | Result | Evidence |
|---|---|---|
| 1 | PASS | employee.fmi signed in; sidenav rgb(0,50,120) with chnu logo, «Облік нагород ЧНУ» + university name; title «Облік нагород ЧНУ»; body font Nunito |
| 2 | PASS | EN: title «ChNU Awards», menu English; UK toggle back restores «Облік нагород ЧНУ» |
| 3 | PASS | Темна: body rgb(31,31,31); reload keeps dark; Як на пристрої follows emulated system dark (31,31,31) / light (249,248,255) |
| 4 | PASS | 360 px: sidenav hidden (right edge 0), menu button opens drawer (right 264), «Мої нагороди» → /awards and drawer closes (0); scrollWidth 360 = viewport |
| 5 | PASS | light/dark chips (status filter): Чернетка grey 230,233,238/58,64,72; На розгляді amber 255,231,176/90,67,0; Затверджено green 205,239,217/15,77,44; Відхилено red 255,218,213/107,29,22; В архіві transparent with outline (award 102 set ARCHIVED via psql, no archive action yet) |
| 6 | PASS | reload: 32 requests, all to localhost; 0 to fonts.googleapis/gstatic; fonts self-hosted (nunito woff2, material-icons) |
| 7 | PASS | brand.json → neutral: html.brand-neutral, title «Облік нагород», IBM Plex Sans, sidenav rgb(30,61,48) with /brand/neutral/logo.svg, «Університет», primary button rgb(45,106,79); restored with --force-recreate |
| 8 | PASS | locale uk-UA: /login navy panel rgb(0,50,120) with chnu logo, «Облік нагород ЧНУ» + university, Nunito, «Увійти» rgb(0,71,171); strip above form at 700 px; «English» → ?lang=en, «ChNU Awards»/«Sign in». Note: page language follows browser locale (en locale shows English) |
| 9 | PASS | after «Темна» + sign-out /login body rgb(31,31,31) data-color-scheme=dark; with «Як на пристрої» light 249,248,255 / emulated dark 31,31,31 |
| 10 | PASS | wrong password: «Невірна адреса або пароль.» in box bg 147,0,10 on dark and 255,218,214/text 186,26,26 on light; 360 px scrollWidth 360 |
| 11 | PASS | APP_BRAND=neutral on app: /login panel rgb(30,61,48), IBM Plex Sans, «Облік нагород» + «Університет», /login-assets/neutral/logo.svg, button rgb(45,106,79); restored with docker compose up -d app |

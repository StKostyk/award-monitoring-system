# UI Guidelines

**Status**: Active
**Date**: 2026-10-04
**Author**: Stefan Kostyk
**Related**: [ADR-016 Styling](../architecture/adr/ADR-016-Styling.md) (addendum 2026-10-04), [ADR-014 UI Component Library](../architecture/adr/ADR-014-UI-Component-Library.md)

---

## 1. Themes and brands

| Brand | Use | Primary | Navigation panel | Accent | Surfaces (light / dark) | Font |
|-------|-----|---------|------------------|--------|-------------------------|------|
| `chnu` | Chernivtsi National University | cobalt `#0047AB` | navy `#003278` | cyan `#00BBFF` (fill only) | `#F9F8FF`, containers `#E7F4F6`, borders `#DFE9EB` / `#1F1F1F` | Nunito |
| `neutral` | fallback and second-brand check | green `#2D6A4F` | `#1E3D30` | amber `#E0A526` | `#F6F7F5` / `#181C1A` | IBM Plex Sans |

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
| `--app-brand-accent`, `--app-on-brand-accent` | brand accent as a fill, with the text colour that reads on it |
| `--app-card-radius` | brand corner radius for custom cards |
| `--app-status-<status>`, `--app-on-status-<status>` | award status chips (`draft`, `pending`, `approved`, `rejected`, `archived`); the same in every brand |

Material component colours are changed with the component's `*-overrides` mixin or its `--mat-<component>-*` tokens, never by targeting internal `.mdc-*` classes.

## 3. Colour and contrast

- Text 4.5:1, large text and control borders 3:1 (WCAG 2.2 AA) in light and dark.
- The ChNU cyan is about 2.2:1 on white: it is a fill behind `--app-on-brand-accent` text, never a text or icon colour on a light surface.
- State is never colour alone: status chips carry the status name, the archived chip adds a dashed outline.

## 4. Layout

- Shell: side menu from 960 px (`WIDE_LAYOUT`), drawer with a menu button below; the toolbar keeps the language switch, the user menu (profile, theme) and sign-out at every width.
- Content width up to 1200 px, padding 28 px (16 px on phones). Check every screen at 360 px and 1280 px; nothing scrolls sideways.
- Navigation items carry an icon and a label; new top-level sections are added to the side menu, guarded by the same permission helper as their route.

## 5. Dark mode

- The scheme follows the device (`color-scheme: light dark`); the user menu offers «Як на пристрої», «Світла», «Темна», kept in the browser.
- New colours are written as `light-dark(<light>, <dark>)` inside a brand or token mixin, never as two separate rule sets.

## 6. Fonts and icons

- Nunito, IBM Plex Sans and Material Icons are served by the app (`@fontsource`, `angular.json` styles); no third-party font requests (CSP `font-src 'self' data:`).
- Icons are `<mat-icon>` ligatures; icon-only buttons need an `aria-label`.

## 7. Sign-in and error pages

The authorization server renders the sign-in, error and 429 pages itself (Thymeleaf, `backend/src/main/resources/templates`), so they carry their own copy of the brand:

- `APP_BRAND` (`app.brand.id`, default `chnu`) picks the brand; the ids are those of `brand.json`, and an unknown id gives the neutral brand. The browser application and the backend are configured separately, so a deployment sets both.
- `static/login-assets/brand-<id>.css` holds the brand tokens (`--lp-*`, `light-dark()` values mirroring the Material theme), `login.css` the layout and the self-hosted fonts, `<id>/logo.svg` the logo; product name and organisation are the message keys `brand.<id>.name` and `brand.<id>.organization`.
- Layout: brand panel (logo, name, organisation) beside the form from 760 px, a strip above it on phones; controls at least 44 px high.
- The scheme follows the device; on the application's own origin (production, the container stack) `scheme.js` also applies the scheme chosen in the app. No inline script or style, nothing from another host.
- A new brand adds `brand-<id>.css`, the logo folder and the message keys next to its Angular theme.

## 8. Checks

- `e2e/theme.spec.ts`: brand class and title, fallback to neutral, scheme switch, phone drawer, axe (WCAG 2.1 AA, serious and critical) on home, award list and award form for each brand in light and dark.
- `e2e/login.spec.ts`: sign-in page brand, fonts without other hosts, 360 px layout, target sizes and axe in light and dark, also with an error shown.
- `/ui-check` screenshots at 360 and 1280 px, light and dark, Ukrainian and English for every changed screen.

## Manual verification

1. Run `docker compose up -d --build`, open `http://localhost` and sign in as `employee.fmi@chnu.edu.ua` / `Passw0rd-demo`. Expected: navy side menu with the logo, «Облік нагород ЧНУ» and the university name; the tab title is «Облік нагород ЧНУ»; text in Nunito.
2. Click «EN». Expected: the tab title becomes «ChNU Awards», the menu and the name switch to English. Switch back.
3. Open the user menu → «Тема» → «Темна». Expected: dark surfaces (`#1F1F1F`), readable text and status chips; reload keeps the dark scheme. Choose «Як на пристрої» and change the system setting: the app follows it.
4. Narrow the window below 960 px (or use the phone view of the developer tools at 360 px). Expected: the side menu disappears, a menu button opens it as a drawer, choosing «Мої нагороди» opens the list and closes the drawer; no sideways scrolling.
5. Open «Мої нагороди» and use the «Статус» filter for the rejected and archived chips (no screen archives an award yet: set one with `update awards set status='ARCHIVED' where award_id=<id>`). Expected: status chips coloured by status (grey draft, amber pending, green approved, red rejected, outlined archived) in both schemes.
6. In the developer tools, Network tab, reload. Expected: no request to `fonts.googleapis.com` or `fonts.gstatic.com`.
7. Replace the brand: `docker compose exec frontend sh -c "echo '{\"id\":\"neutral\",\"name\":{\"uk\":\"Облік нагород\",\"en\":\"Award Registry\"},\"organization\":{\"uk\":\"Університет\",\"en\":\"University\"},\"logo\":\"brand/neutral/logo.svg\"}' > /usr/share/nginx/html/brand/brand.json"` and reload. Expected: green neutral theme in IBM Plex Sans with the neutral logo. Restore with `docker compose up -d --build --force-recreate frontend`.
8. Sign out and open `http://localhost` with the browser language set to Ukrainian (the sign-in page follows the browser language, not the choice made in the app). Expected: the sign-in page has the navy brand panel with the logo, «Облік нагород ЧНУ» and the university name beside the form (a strip above it below 760 px), Nunito text, a cobalt «Увійти» button; «English» switches the page and the brand name to English.
9. In the app choose «Темна», sign out. Expected: the sign-in page is dark as well (`#1F1F1F`); with «Як на пристрої» it follows the system setting.
10. Enter a wrong password. Expected: the error appears in a red box inside the card, readable in both schemes; at 360 px nothing scrolls sideways.
11. Set `APP_BRAND=neutral` (for example `APP_BRAND=neutral docker compose up -d app`) and reload the sign-in page. Expected: green neutral panel, IBM Plex Sans, «Облік нагород» and «Університет». Restore with `docker compose up -d app`.

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

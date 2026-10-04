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

## 7. Checks

- `e2e/theme.spec.ts`: brand class and title, fallback to neutral, scheme switch, phone drawer, axe (WCAG 2.1 AA, serious and critical) on home, award list and award form for each brand in light and dark.
- `/ui-check` screenshots at 360 and 1280 px, light and dark, Ukrainian and English for every changed screen.

## Manual verification

1. Run `docker compose up -d --build`, open `http://localhost` and sign in as `employee.fmi@chnu.edu.ua` / `Passw0rd-demo`. Expected: navy side menu with the logo, «Облік нагород ЧНУ» and the university name; the tab title is «Облік нагород ЧНУ»; text in Nunito.
2. Click «EN». Expected: the tab title becomes «ChNU Awards», the menu and the name switch to English. Switch back.
3. Open the user menu → «Тема» → «Темна». Expected: dark surfaces (`#1F1F1F`), readable text and status chips; reload keeps the dark scheme. Choose «Як на пристрої» and change the system setting: the app follows it.
4. Narrow the window below 960 px (or use the phone view of the developer tools at 360 px). Expected: the side menu disappears, a menu button opens it as a drawer, choosing «Мої нагороди» opens the list and closes the drawer; no sideways scrolling.
5. Open «Мої нагороди». Expected: status chips coloured by status (grey draft, amber pending, green approved, red rejected, outlined archived) in both schemes.
6. In the developer tools, Network tab, reload. Expected: no request to `fonts.googleapis.com` or `fonts.gstatic.com`.
7. Replace the brand: `docker compose exec frontend sh -c "echo '{\"id\":\"neutral\",\"name\":{\"uk\":\"Облік нагород\",\"en\":\"Award Registry\"},\"organization\":{\"uk\":\"Університет\",\"en\":\"University\"},\"logo\":\"brand/neutral/logo.svg\"}' > /usr/share/nginx/html/brand/brand.json"` and reload. Expected: green neutral theme in IBM Plex Sans with the neutral logo. Restore with `docker compose up -d --build --force-recreate frontend`.

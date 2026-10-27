# Feature 8.1: Mobile Responsive Design and PWA

> **Epic**: 8 — Mobile Optimization & Accessibility (SCRUM-114)
> **Sprint**: 7 (2026-10-26 → 2026-11-01), spilling into sprint 8
> **Points**: 15 (three stories)
> **Status**: Approved 2026-10-26 (deviations D-1 to D-8 accepted)
> **Author**: Stefan Kostyk
> **Governing docs**: EPIC-08 tracker (decisions of 2026-10-26, deviations 3–6, technical notes, risks), US-013, TRACEABILITY FR-012 NFR-009, BRD §3.1 and §3.2, QUALITY_GATES §6.1, ADR-013 (service workers, rendering addendum), ADR-014, ADR-016 (brands addendum), ADR-022 (brand per deployment), Feature 2.1 (server drafts, local form copies), `FormCopiesService`, `AuthService`, `BrandService`, `award-form.component.ts`, `award-documents.component.html`, `frontend/nginx.conf`, `security-headers.conf`

## 1. Problem and personas

The application works in a desktop browser on a steady connection. On a phone it opens as an ordinary web page: nothing to install, the shell is downloaded again on every visit, a weak connection in a corridor or on a bus leaves a blank page, and several screens scroll sideways at 360 px. An award received at a ceremony is entered later at a desk, because a lost connection during the save shows an error and the person cannot tell whether the entry survived. The upload already offers the rear camera, but it has not been checked on a phone.

| Persona | Need in this feature |
|---------|----------------------|
| Anastasia, employee | Installs the app on her phone, photographs the certificate at the ceremony, enters the award on a weak connection and finishes it later on her desktop |
| Faculty secretary | Enters an award for the faculty from a phone between meetings; the screens fit without sideways scrolling |
| Prof. Martynyuk, dean | Opens the review queue and an award on a tablet in either orientation |
| Dmytro, GDPR officer | No personal data stays on the device in a cache that outlives sign-out or escapes erasure |
| Operator | A new release reaches installed apps without users clearing caches; a broken service worker can be switched off from the server |

## 2. Scope

**In (this feature)**

- Web app manifest and icons per brand, replaced per deployment together with `brand.json`; theme colour and Apple touch icon in `index.html`
- Angular service worker in the production build: caches the application shell, translations, fonts and the brand files; never an API answer, a document or a privacy text
- Start-up without a connection: the cached shell opens with an offline notice; without a stored session an offline page replaces the sign-in redirect
- Connection state for the whole app (`ConnectivityService`) and a notice in the shell; recovery when the connection returns
- New version prompt from `SwUpdate`; nginx serves the service worker files uncached
- Award form without a connection: values kept on the device (the existing form copy), "Save draft" deferred and carried out when the connection returns, submission and uploads paused with a notice
- Cross-device continuation through the server draft (`DRAFT` since 2.1.1)
- Every route usable at 360 px in portrait and landscape: no sideways page scroll, wide tables scroll inside their card, touch targets of WCAG 2.5.8
- Camera capture checked on a phone, with the file picker as the fallback; size and type messages for phone photos
- Lighthouse (mobile) above 90 for performance, accessibility and best practices on the sign-in page, the award form and the award list
- User guide section "Using the app on a phone"

**Out (where it goes)**

- Offline reading of awards, the review queue or any API answer: tracker decision 2026-10-26 (personal data stays out of the cache)
- Background sync of saves or uploads after the tab is closed: not planned; the form copy is offered back on the next visit
- Browser push notifications: icebox (tracker decision 2026-10-26)
- Automatic saving of server drafts while online: not planned (deviation D-7)
- Shrinking photos above 10 MB in the browser: not planned; the size message names the limit (risk 4)
- Native apps, offline sign-in: not planned
- Page language, titles, skip link, focus and the axe sweep of every route: Feature 8.2

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-115 (#331) | 8.1.1 Installable app with an offline application shell | 5 | no | — |
| SCRUM-116 (#332) | 8.1.2 Award draft kept offline and saved on reconnect | 5 | no | 8.1.1 (`ConnectivityService`, service worker) |
| SCRUM-117 (#333) | 8.1.3 Touch-ready layouts at 360 px and camera capture | 5 | no | 8.1.1 (manifest, offline notice in the shell) |

All three stories are frontend only (plus nginx in 8.1.1); none needs a backend change or a split.

## 4. Acceptance criteria

**Definitions.** *Offline*: `navigator.onLine` is false, or the last API request failed with status 0 and no request has succeeded since. *Online again*: the `online` event, or any API request that succeeds. *Shell*: `index.html`, the JavaScript and CSS bundles, `favicon.ico`, fonts, `i18n/*.json` and the brand files. *Backend paths*: `/api/`, `/oauth2/`, `/connect/`, `/.well-known/`, `/login`, `/logout`, `/error`, `/login-assets/`, `/swagger-ui/`, `/v3/api-docs`, `/privacy/`, `/health`.

### 8.1.1 Installable app with an offline application shell (SCRUM-115)

- **AC-1.1** (Manifest) Given the production build, when `GET /brand/manifest.webmanifest` is sent, then it answers 200 with `Content-Type: application/manifest+json` and `Cache-Control: no-cache`; the ChNU manifest holds `name` "Облік нагород ЧНУ", `short_name` "Нагороди ЧНУ", `lang` "uk", `start_url` "/", `scope` "/", `display` "standalone", no `orientation`, the brand's `theme_color` and `background_color`, and PNG icons 192 and 512 px plus a 512 px `maskable` icon; `index.html` links it and carries `<meta name="theme-color">` and `<link rel="apple-touch-icon">`.
- **AC-1.2** (Brand per deployment) Given `public/brand/neutral/` holds a manifest "Облік нагород" with neutral icons, when a deployment copies it over `brand/manifest.webmanifest` and `brand/icons/` together with `brand.json`, then the installed name and icon follow without a rebuild; the deployment guide says which files to copy.
- **AC-1.3** (Installable) Given the Compose build at `http://localhost` in Chrome, when the Application panel is opened, then the manifest shows no errors or warnings, a service worker is active for scope `/`, and Chrome offers "Install"; the installed app opens standalone on `/` at the start page of the signed-in user.
- **AC-1.4** (Production only) Given `npm start` or the e2e dev build, when the app loads, then no service worker is registered; in the production build it registers when the app is stable or after 30 s at most.
- **AC-1.5** (Cache policy) Given `ngsw-config.json`, then the asset groups list only shell files, the only data group is `brand` (`/brand/**`, freshness, 3 s timeout, 7 days), no pattern matches a backend path, and `navigationUrls` exclude every backend path; a unit spec reads the file and fails on an `/api` pattern or a missing exclusion. After a session with awards, documents and analytics opened, Cache Storage holds no entry under a backend path.
- **AC-1.6** (Backend pages) Given an active service worker, when the browser navigates to `/login`, `/oauth2/authorize?…`, `/logout`, `/swagger-ui/index.html` or `/privacy/<version>/uk.html`, then the answer comes from the network (the backend page or nginx), never the cached shell.
- **AC-1.7** (Offline start with a session) Given a signed-in tab with an active service worker, when the connection drops and the tab is reloaded, then the shell opens from the cache within 3 s with the offline notice "Немає з'єднання з сервером. Сторінки, яким потрібні дані, відкриються, щойно з'єднання відновиться." (en "No connection to the server. Pages that need data will open once the connection returns."), the user stays signed in and is not sent to the sign-in page; the navigation shows the items of every signed-in user until the profile loads; pages that need data show their existing network messages.
- **AC-1.8** (Offline start without a session) Given no stored session and no connection, when the app is opened (installed icon or a new tab), then an offline page "Немає з'єднання" with "Увійти можна, щойно з'єднання відновиться." and a "Спробувати ще раз" button is shown instead of the sign-in redirect; when online again, the button (or the `online` event) starts the sign-in.
- **AC-1.9** (Back online) Given the offline notice, when the connection returns, then the notice disappears within 2 s, the discovery document and the profile are loaded if they were missing, the navigation follows the roles, and no reload is needed; token renewal that failed offline is retried once.
- **AC-1.10** (New version) Given an installed or open app on version N, when version N+1 is deployed and the service worker detects it (at start, when the tab becomes visible, at most every 6 h), then a snackbar "Доступна нова версія застосунку." with "Оновити" appears; "Оновити" reloads on N+1; dismissing it keeps N until the next start; an unrecoverable worker state reloads the page with the same notice; a dirty award form keeps its local copy across the reload.
- **AC-1.11** (Worker files) Given nginx, then `ngsw-worker.js`, `ngsw.json` and `safety-worker.js` are served with `Cache-Control: no-cache` (not the year-long `immutable` of other `.js`), and the content security policy is unchanged; the ADR-013 addendum names the kill switch (serve `safety-worker.js` as `ngsw-worker.js`, or remove `ngsw.json`, so the worker unregisters).
- **AC-1.12** (Sign-out) Given a signed-in user on a device with the service worker, when the user signs out, then the form copies and tokens are removed as today and the caches hold only the shell; a sign-out without a connection removes the local copies and tokens and shows the offline page (the server session ends at the refresh token's expiry).

### 8.1.2 Award draft kept offline and saved on reconnect (SCRUM-116)

- **AC-2.1** (Offline notice in the form) Given the award form open for a new award or a draft, when the app goes offline, then above the actions the form shows "Немає з'єднання. Введене зберігається на цьому пристрої і буде збережено як чернетку, щойно з'єднання відновиться." (en "No connection. What you enter is kept on this device and saved as a draft once the connection returns."); the fields stay editable and every change is kept in the form copy as today.
- **AC-2.2** (Save offline) Given the form offline and valid for a draft (a title in one language), when "Зберегти чернетку" is chosen, then no request is sent and the notice changes to "Чернетку буде збережено, щойно з'єднання відновиться."; a form not valid for a draft marks its fields as today.
- **AC-2.3** (Saved on reconnect) Given changes made, or a save requested, while offline and the form still open, when the connection returns, then the draft is saved once (a new award: `POST /awards`, the address becomes `/awards/{id}/edit`; a draft: `PUT /awards/{id}` with its version), the message "З'єднання відновлено, чернетку збережено." appears and the copy is removed; if the form is not valid for a draft, nothing is sent and the notice asks for a title.
- **AC-2.4** (Submit and upload paused) Given the form offline, when "Подати" is chosen, then nothing is sent and the form says "Подати нагороду можна лише з'єднавшись із сервером. Збережіть чернетку — її буде збережено автоматично."; the document area and "Сфотографувати" are disabled with "Документи можна додати, щойно з'єднання відновиться."; both return when online.
- **AC-2.5** (Conflicts on reconnect) Given a draft open offline on the phone, when it was changed, deleted or submitted on another device and the connection returns, then the automatic save meets the existing outcomes: changed → "The award was changed in another window. Your changes are kept in this browser." (`award-stale`, copy kept); deleted → `award-deleted` (the values become a new unsaved draft); submitted → the award page with `award-not-editable`.
- **AC-2.6** (Closed while offline) Given values entered offline and the tab closed or reloaded before the connection returns, when `/awards/new` is opened again (online or offline), then "Відновити незбережені зміни?" is offered; after "Відновити" the values are back and saving follows AC-2.2 and 2.3.
- **AC-2.7** (Continue on another device) Given a draft saved from the phone (AC-2.3 or "Зберегти чернетку" online), when the same user signs in on a desktop, then the award list shows it as a draft and the form opens it with the phone's values, documents and photos; it can be completed and submitted there. A draft edited on the desktop meanwhile is detected on the phone by AC-2.5.
- **AC-2.8** (Session ended offline) Given the access token expired while offline, when the connection returns, then the token is renewed and the save goes through; if the session was ended elsewhere, the sign-in page opens, the copy stays for the same user and is offered back after signing in.
- **AC-2.9** (Lost connection mid-save) Given a save sent when the connection drops (status 0), then the form switches to the offline state of AC-2.1 with the save pending, instead of the network problem text, and AC-2.3 applies; a save that reached the server and whose answer was lost is detected by the version check (`award-stale`) or the duplicate warning, never saved twice silently.

### 8.1.3 Touch-ready layouts at 360 px and camera capture (SCRUM-117)

- **AC-3.1** (No sideways scroll) Given every route of §6 Frontend, signed in with the role that sees it, in Ukrainian and English, at 360×740 and at 740×360, when the page has loaded, then the page does not scroll sideways (`scrollWidth` ≤ `innerWidth`); tables wider than the screen scroll inside their card; dialogs fit the screen. A Playwright sweep (`mobile-layout.spec.ts`, Chromium) proves it in the gate.
- **AC-3.2** (Touch targets) Given the same sweep, then the axe rule `target-size` (WCAG 2.5.8) reports nothing, and the form actions, toolbar buttons and paginators are at least 44 px high at 360 px.
- **AC-3.3** (Award form on a phone) Given the award form at 360 px, then fields fill the width in one column, the actions are reachable without zooming, the date opens the device picker, the web link uses `inputmode="url"`, and no input text is under 16 px (no zoom on focus in Safari).
- **AC-3.4** (Orientation) Given the award form with values, when the device turns between portrait and landscape, then the values, the focused field and the open notice stay; the manifest locks no orientation (WCAG 1.3.4).
- **AC-3.5** (Camera) Given a phone, when "Сфотографувати" is chosen, then the rear camera opens (`capture="environment"`) and the photo uploads as a JPEG document with its preview; where the browser offers no camera (desktop), the button opens the file picker for images; a photo over 10 MB shows "The file is larger than 10 MB" and an unsupported format (e.g. HEIC on Android) shows the unsupported-type message naming the accepted formats.
- **AC-3.6** (WebKit) Given the Playwright projects `mobile-chrome` (Pixel 7) and `mobile-safari` (iPhone 13, WebKit), when the award form spec runs (create, save draft, attach an image, submit), then it passes in both; they run in `e2e.ps1` and the gate's e2e step.
- **AC-3.7** (Lighthouse) Given the Compose build, Chrome Lighthouse in the mobile profile (simulated throttling), then performance, accessibility and best practices are each 90 or above on `/login`, `/awards/new` and `/awards`; the scores and the Lighthouse version are recorded in the §10 run notes; the initial bundle stays within the `angular.json` budget.
- **AC-3.8** (User guide) Given `docs/user/USER_GUIDE.md`, then a section "Using the app on a phone" covers installing on Android and iOS, what works without a connection (AC-2.x), photographing a certificate and the 10 MB limit, and continuing on a desktop.

## 5. Edge cases

| Case | Expected | AC |
|------|----------|----|
| Weak connection: `navigator.onLine` true, requests time out | A request failing with status 0 sets offline; the first successful request clears it | Def., 2.9 |
| Offline start on `/awards/{id}/edit` | The draft is not cached: the existing network problem with "Try again"; the copy of that draft is offered once it loads | 1.7, 2.6 |
| Category tree offline | Kept in memory for the open session (`shareReplay`); after an offline start the category list is empty with the network text; the category is needed only for submission | 2.1, 2.4 |
| Unit recipient chosen, role revoked while offline | The save on reconnect answers `recipient-out-of-scope` with its existing message; copy kept | 2.3 |
| Two tabs offline on the same draft | Each keeps the same copy; on reconnect both try one save: the second meets `award-stale` | 2.5 |
| Online/offline flapping | Saves are serialised through the existing `running` request; one pending flag, no queue | 2.3 |
| Storage full or disabled (private mode) | The form copy is not kept (existing); the offline notice says so: "Пристрій не зберігає введене — не закривайте сторінку." | 2.1 |
| Service worker disabled by the browser or the user | The app works online as today; offline the browser's own page | 1.4 |
| New version while a form is dirty | Snackbar only; no automatic reload; "Оновити" keeps the copy (beforeunload) | 1.10 |
| Brand file changed on the server | The freshness group fetches it first; offline the cached brand is used | 1.5 |
| `ngsw.json` removed (kill switch) | The worker unregisters on its next check; the app works without it | 1.11 |
| Discovery document unreachable at start | Start-up continues with the stored session; discovery retried when online | 1.7, 1.9 |
| Sign-out offline | Local data removed, offline page shown | 1.12 |
| Back after the reconnect save of a new award | `/awards/new` (replaced history) opens empty; no copy, no second draft | 2.3 |
| Photo rotated by EXIF | Preview and stored file as delivered by the browser; no server-side rotation (unchanged) | 3.5 |

## 6. Dependencies

### Tables

None. No migration.

### Endpoints

None new or changed. Used: `POST /awards`, `PUT /awards/{id}`, `POST /awards/{id}/submit`, `POST /awards/{id}/documents`, `GET /award-categories`, `GET /users/me`, the authorization server endpoints.

### Services and files

- New `core/connectivity/ConnectivityService` (signal `online`, fed by `online`/`offline` events and an HTTP interceptor that reports status 0 and successes)
- New `core/pwa/UpdatePromptService` (`SwUpdate.versionUpdates`, `unrecoverable`, checks on start, on `visibilitychange` and every 6 h)
- `AuthService.init()`: a network failure of discovery or profile no longer aborts start-up; offline page when there is no session
- `award-form.component.ts`: pending save, offline notice, paused submit; `award-documents.component`: disabled offline
- `ngsw-config.json`, `app.config.ts` (`provideServiceWorker`), `index.html`, `angular.json` (`serviceWorker`), `package.json` (`@angular/service-worker` 21)
- `frontend/nginx.conf`: exact locations for the worker files; `webmanifest` type
- Shared narrow-screen styles (tables in cards, filter bars, dialogs) in `src/styles/`

### Frontend routes (sweep of AC-3.1)

Public: `/login` (backend), `/register`, `/registration-pending`, `/verify-email`, `/forgot-password`, `/reset-password`, `/public/achievements`, `/public/units/:id/achievements`, `/privacy-notice`, `/not-found`, `/forbidden`. Signed in: `/dashboard`, `/awards`, `/awards/new`, `/awards/:id`, `/awards/:id/edit`, `/awards/:id/correct`, `/awards/:id/submitted`, `/achievements`, `/units/:id/achievements`, `/analytics/summary`, `/analytics/university`, `/analytics/units/:id`, `/reviews`, `/reviews/:id`, `/delegations`, `/notifications`, `/profile`, `/privacy`, `/compliance`, `/admin/users`, `/admin/users/:id`. The final list is taken from the route files when 8.1.3 starts.

### External systems

Chrome and Safari on real phones for the manual run (Android through Chrome remote debugging; iOS where a device is available, else Playwright WebKit); Lighthouse in Chrome DevTools.

## 7. Technical decisions

Inherited:

1. Client-side rendering only, standalone components, Angular 21 (ADR-013 and its rendering addendum); Angular Material components and breakpoints (ADR-014); brand themes and `brand.json` per deployment (ADR-016 addendum, ADR-022).
2. Offline is a fallback for the award form, not an offline-first client; the service worker caches the shell only (tracker decision 1, BRD §3.2).
3. Cross-device continuation through the server draft; the offline copy is the existing `FormCopiesService` entry (tracker decision 2, Feature 2.1).
4. Lighthouse mobile above 90 on three pages (tracker decision 5, US-013 DoD, NFR-009).

New, recorded in an ADR-013 addendum "Service worker and offline policy" in 8.1.1:

5. `@angular/service-worker` over Workbox: shipped with Angular, hashed manifest per build, `SwUpdate` API, no extra build step.
6. Navigation requests to backend paths bypass the worker (`navigationUrls`), so the Spring login page and the authorization flow never receive the cached shell.
7. `ConnectivityService` combines browser events with request outcomes; components read one signal.

**Proposed deviations**

- **D-1** One data group, `brand` (freshness), against "data groups stay empty": brand files are replaced per deployment without a rebuild, so they cannot sit in a hashed asset group; they hold no personal data.
- **D-2** The manifest is in Ukrainian only (`lang` "uk"): a manifest has no runtime language switch; the installed name is "Нагороди ЧНУ" in both interface languages.
- **D-3** The mobile user guide is a section of `docs/user/USER_GUIDE.md`, not a page in `docs/guides/` (tracker deviation 6 corrected; the folder does not exist).
- **D-4** Lighthouse is measured by hand in the manual run and recorded in this PRD, not a gate step: two of the three pages need a signed-in session on the Compose build. The gate enforces the layout sweep, `target-size` and the bundle budget.
- **D-5** The service worker spec (`pwa.spec.ts`: offline reload, offline page, cache contents, update prompt) runs against the Compose build on demand (`PLAYWRIGHT_BASE_URL=http://localhost npx playwright test pwa`), not in the gate, because the worker exists only in the production build; it is run in §10 and at the feature validation.
- **D-6** Lighthouse 12 has no PWA category; installability is checked in the Chrome Application panel (AC-1.3), which covers US-013 DoD "PWA functionality tested".
- **D-7** "Form progress is saved automatically" (US-013) is met by the device copy, kept on every change; a server draft is written on "Зберегти чернетку" and on reconnect, not on every change, so typing creates no drafts and no version conflicts.
- **D-8** After an offline start the navigation shows only the items of every signed-in user until the profile loads; the profile is not cached on the device.

## 8. Contract

### 8.1 OpenAPI stubs

None: the feature changes no endpoint.

### 8.2 Migration outlines

None.

### 8.3 Files of the brand

| Path (served) | ChNU (default in the image) | Neutral (copied by a deployment) |
|---------------|-----------------------------|----------------------------------|
| `brand/manifest.webmanifest` | `public/brand/manifest.webmanifest` | `public/brand/neutral/manifest.webmanifest` |
| `brand/icons/icon-192.png`, `icon-512.png`, `icon-maskable-512.png`, `apple-touch-icon.png` (180 px) | `public/brand/icons/` | `public/brand/neutral/icons/` |

## 9. Test plan

| AC | Unit | E2E (dev build, gate) | `pwa.spec.ts` (Compose, on demand) | Manual (§10) |
|----|------|------------------------|------------------------------------|--------------|
| 1.1, 1.2 | manifest JSON spec (fields, icon files exist) | — | manifest headers | 1, 2 |
| 1.3 | — | — | worker active | 3 |
| 1.4 | `app.config` provider spec | no worker in the dev build | — | — |
| 1.5, 1.6 | `ngsw-config` spec | — | cache contents, `/login` navigation | 4, 5 |
| 1.7–1.9 | `ConnectivityService`, `AuthService.init` offline, offline page | offline notice via `context.setOffline` | offline reload, offline page | 6–8 |
| 1.10 | `UpdatePromptService` | — | update after rebuild | 9, 29 |
| 1.11, 1.12 | logout offline | — | worker file headers | 10, 22, 33 |
| 2.1–2.4, 2.9 | form: pending save, notice, paused submit/upload, status 0 | `award-offline.spec.ts` with `setOffline` | — | 11–14, 26, 28 |
| 2.5, 2.6, 2.8 | form: stale, deleted, restore after reload | two contexts: stale on reconnect | — | 15, 23–25 |
| 2.7 | — | phone context saves, desktop context submits | — | 16 |
| 3.1, 3.2 | — | `mobile-layout.spec.ts` sweep with axe `target-size` | — | 17 |
| 3.3, 3.4 | form input attributes | award form at 360 and rotated | — | 18 |
| 3.5 | upload component: picker fallback | image upload at 360 | — | 19 |
| 3.6 | — | `mobile-chrome`, `mobile-safari` projects | — | — |
| 3.7 | — | — | — | 20 |
| 3.8 | — | — | — | 21 |

## 10. Manual verification

Expected texts are the planned wording; they are checked against the merged i18n files at the feature validation.

Preconditions: `docker compose up -d --build` (production build with the service worker; app at `http://localhost`); Chrome with DevTools (Network "Offline", Application → Manifest, Service workers, Cache storage); the Playwright MCP for offline steps (`context.setOffline(true)` through `browser_run_code_unsafe`); an Android phone over Chrome remote debugging where available. psql `docker compose exec postgres psql -U postgres award_monitoring`. Seed accounts (password `Passw0rd-demo`, `@chnu.edu.ua`): `employee.fmi`, `secretary.fmi`, `admin`.

1. `curl -sI http://localhost/brand/manifest.webmanifest`. Expected: 200, `application/manifest+json`, `Cache-Control: no-cache`; the body has the fields of AC-1.1 and the three icons load. (AC-1.1)
2. Copy `public/brand/neutral/manifest.webmanifest` and `icons/` over the served brand files in the container together with the neutral `brand.json`; reload. Expected: Application → Manifest shows "Облік нагород" and the neutral icon; restore the ChNU files. (AC-1.2)
3. Open `http://localhost`, sign in as `employee.fmi`. Expected: Application shows the manifest without errors, the worker "activated and running" for `/`, the install icon in the address bar; install and open the app: a standalone window on the start page. (AC-1.3)
4. Open `/awards`, an award with a document, `/analytics/summary` and `/profile`; Application → Cache storage. Expected: only `ngsw:` caches with shell files, `i18n/*.json` and `brand/*`; no `/api/`, document or `/privacy/` entry. (AC-1.5)
5. Sign out; with the worker active, open `http://localhost/login`, `http://localhost/swagger-ui/index.html` (if enabled) and `/privacy/<version>/uk.html`. Expected: the Spring sign-in page, Swagger (or its 404) or the notice text, never the application shell (no `<app-root>` in the response); the Network panel may still show "(ServiceWorker)", because the worker passes requests it does not cache to the network. (AC-1.6)
6. Sign in as `employee.fmi`, open `/awards`; set Network to Offline; reload. Expected: the shell within 3 s, the offline notice of AC-1.7, still signed in, the list area shows the network message; no request to `/oauth2/authorize`. (AC-1.7)
7. Still offline, open a new tab on `http://localhost`. Expected: the shell; with no session in that tab, the offline page "Немає з'єднання" and "Спробувати ще раз", no redirect. Go online, press the button (or wait for the `online` event). Expected: the sign-in flow starts; since the sign-in of step 6 still holds a server session, it ends on the start page without the form. (AC-1.8)
8. Back in the tab of step 6, go online. Expected: the notice disappears within 2 s, the full navigation returns, the list loads after Try again or a navigation, no reload. (AC-1.9)
9. Change a visible text in `frontend/src`, `docker compose up -d --build frontend`; switch back to the open app tab. Expected: within a minute the snackbar "Доступна нова версія застосунку." with "Оновити"; after "Оновити" the new text. Revert the text and rebuild. (AC-1.10)
10. `curl -sI http://localhost/ngsw-worker.js` and `/ngsw.json`. Expected: `Cache-Control: no-cache`; `curl -sI http://localhost/main-<hash>.js` (a real bundle name) keeps `public, immutable`; the `Content-Security-Policy` header is unchanged. (AC-1.11)
11. As `employee.fmi` open `/awards/new`, type a title, go offline. Expected: the form notice of AC-2.1; type a description and an awarding organisation. (AC-2.1)
12. Press "Зберегти чернетку". Expected: no request in Network; the notice "Чернетку буде збережено, щойно з'єднання відновиться." (AC-2.2)
13. Press "Подати". Expected: the message of AC-2.4, no request; the document area and "Сфотографувати" disabled with their notice. (AC-2.4)
14. Go online. Expected: one `POST /awards` 201, the address `/awards/{id}/edit`, "З'єднання відновлено, чернетку збережено."; psql `select status, title_uk, title from awards where id = <id>;` → `DRAFT` with the typed values; Application → Local storage has no `awards.form-copy.<user>.award-new`. (AC-2.3)
15. Go offline, change the description, close the tab. Open `http://localhost/awards/<id>/edit` online. Expected: the draft loads with "Відновити незбережені зміни?"; "Відновити" brings the change back; "Зберегти чернетку" saves it (PUT 200). (AC-2.6)
16. On the phone (or a Playwright context at 360×740) save a draft with a photo; sign in as `employee.fmi` on the desktop. Expected: `/awards` lists it as a draft; the form shows the values and the photo; submission succeeds. (AC-2.7)
17. Run `npx playwright test mobile-layout` and look at `/awards`, `/reviews`, `/analytics/summary`, `/admin/users` and `/profile` at 360×740 and 740×360 in Ukrainian. Expected: green; no sideways page scroll; tables scroll inside their card. (AC-3.1, 3.2)
18. Award form at 360 px on the phone: fill every field, turn the phone. Expected: one column, no zoom on focus, the device date picker, the URL keyboard for the link; values and focus kept after turning. (AC-3.3, 3.4)
19. On the phone press "Сфотографувати" and photograph a certificate. Expected: the rear camera, the upload with a preview. On the desktop the same button opens the file picker. Upload a photo over 10 MB. Expected: "The file is larger than 10 MB". (AC-3.5)
20. Lighthouse (mobile, Navigation, Performance + Accessibility + Best practices) on `/login`, `/awards/new` and `/awards` signed in as `employee.fmi`. Expected: each score ≥ 90; record the three rows and the Lighthouse version. (AC-3.7)
21. Read the user guide section "Using the app on a phone" against steps 3, 11–14 and 19. Expected: the text matches the screens. (AC-3.8)

### Detours

22. Sign out while offline (profile menu → sign out). Expected: the offline page; Local storage has no `awards.form-copy.*`; after going online the sign-in page. (AC-1.12)
23. Open the draft of step 14 on the phone and go offline; on the desktop change its title and save; change the description on the phone and go online. Expected: "The award was changed in another window. Your changes are kept in this browser."; no second draft in psql. (AC-2.5)
24. Repeat with the desktop deleting the draft. Expected: the `award-deleted` text, the address `/awards/new`, and one save creates a new draft. Repeat with the desktop submitting it. Expected: the award page with the `award-not-editable` text. (AC-2.5)
25. Go offline with the form dirty and wait 16 minutes (access token expired); go online. Expected: the token is renewed and the save goes through. Repeat, but reset the password of `employee.fmi` from another browser while offline. Expected: the sign-in page; after signing in, the restore offer at `/awards/new`. (AC-2.8)
26. Block `POST /awards` in DevTools (request blocking, the request fails with status 0) and press "Зберегти чернетку" online. Expected: the pending offline state, not "The server is not reachable. Your values are kept; please try again."; unblock and trigger a successful request (navigate within the form or wait for the `online` check). Expected: one draft. (AC-2.9)
27. Back after step 14. Expected: `/awards/new` empty without a restore offer; no second draft. Reload `/awards/<id>/edit` offline. Expected: the network message with Try again; going online and Try again loads the draft. (§5)
28. Hidden in state, offline: "Подати" and the upload are inactive only while offline; a submitted award (`/awards/<id>` of a pending award) has no form offline, so no pending save exists; online, the form shows no offline notice and `POST /awards/{id}/submit` works. (AC-2.4)
29. Hidden in state, update: with no new version deployed, no snackbar after a reload or a tab switch; after step 9 a second reload shows none. (AC-1.10)
30. Revoked mid-flow, role: as `secretary.fmi` start a faculty draft (recipient: the faculty) and go offline; `admin` revokes the secretary role; go online. Expected: the session ends (401) and the sign-in page opens, the copy stays; after signing in the restore offer appears, and a save with the faculty answers `recipient-out-of-scope` with its message; "Me" saves. Another faculty secretary at the same level keeps entering faculty awards. (§5)
31. Two tabs on the same draft offline, both edited; go online. Expected: one tab saves, the other shows the stale message; one row in psql. (§5)
32. Block site storage (Chrome Settings → Site data) and type in the form offline. Expected: the notice "Пристрій не зберігає введене — не закривайте сторінку."; a private window offline without an earlier visit shows Chrome's own offline page. (§5)
33. Rename `ngsw.json` in the container (`docker compose exec frontend mv /usr/share/nginx/html/ngsw.json /tmp/`), reload twice. Expected: Application shows the worker redundant or gone; the app works online; restore the file. (AC-1.11)

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| A stale worker keeps an old shell after a deployment | Medium | High | Uncached worker files (AC-1.11), update prompt (AC-1.10), kill switch in the ADR addendum, steps 9 and 33 |
| Offline start-up breaks the OIDC flow (discovery, silent refresh) | Medium | High | `AuthService.init` unit tests for each failure; backend paths excluded from navigation; steps 6–8 and 25 |
| Many templates need narrow-screen fixes late in the project | Medium | Medium | Shared table, filter bar and dialog styles first, then per page; the sweep in the gate stops regressions |
| Phone photos over 10 MB | Medium | Low | Clear limit message; the guide advises the camera's standard resolution; in-browser shrinking parked |
| No iOS device | High | Low | Playwright WebKit iPhone profile; real iOS named as a thesis limitation |
| WebKit projects lengthen the e2e run | Medium | Low | Only the award form spec in the mobile projects; layout sweep in Chromium |
| Lighthouse performance under 90 on `/awards` | Low | Medium | Lazy routes exist; bundle budget; record and fix in 8.1.3 |

## 12. Definition of Done

- Every AC proven at the layers of §9; `.\tools\gate.ps1` green per story (frontend lint and tests, e2e with the sweep and the mobile projects); `pwa.spec.ts` green against the Compose build
- Screen check of the shell notice, the offline page, the award form and the swept routes at 360/1280, both themes and languages
- ADR-013 addendum (service worker and offline policy); deployment guide (brand files, worker files, kill switch); user guide section; CHANGELOG `[Unreleased]`; tracker and BACKLOG
- Lighthouse scores recorded (AC-3.7)
- Manual verification (§10) run and recorded; feature validated

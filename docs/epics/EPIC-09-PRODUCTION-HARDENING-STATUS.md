# Epic 9: Production Hardening — Status

> **Started**: 2026-10-29 (kickoff)
> **Done**: -
> **Author**: Stefan Kostyk
> **Jira epic**: SCRUM-128
> **Roadmap**: DEVELOPMENT_ROADMAP.md § Epic 9 (local planning document)

## Progress

| Feature | Status | Started | Done |
|---------|--------|---------|------|
| 9.1 Security hardening (tokens hashed at rest, rate limits by account and address; backend-for-frontend as a stretch) | PRD approved 2026-10-29 ([PRD](../features/epic-09/feature-9.1-security-hardening.md)) | - | - |
| 9.2 Demo deployment and operations (Lighthouse 90 on the signed-in pages, nightly backups, deploy job, AWS EC2 server, award form in steps on phones) | PRD approved 2026-11-10 ([PRD](../features/epic-09/feature-9.2-demo-deployment.md)) | 2026-10-29 | - |
| 9.3 New look (burgundy and cream light, Aurora dark, Manrope and Onest, Lucide icons, shell with command palette, review queue with undo, every screen restyled) | Done 2026-11-16: 7 planned stories and 5 fixes, 41 points; manual run of §10 34 steps, the three failed steps fixed in 9.3.14, 9.3.21 and 9.3.22 ([PRD](../features/epic-09/feature-9.3-new-look.md)) | 2026-11-15 | 2026-11-16 |
| 9.4 Dashboards and widget layout (scheme and layout on the account, widget kit, Hall of Fame, «Мої нагороди» with the CV export, Command centre with the department reminder) | PRD approved 2026-11-17, 23 points ([PRD](../features/epic-09/feature-9.4-dashboards.md)) | - | - |
| 9.5 Page layouts (page kit, My dashboard and unit analytics split, Achievements story feed, Users and Reviews with the item beside the list, university bento, Compliance and delegations split) | Done 2026-11-22: 7 planned stories and 3 fixes, 36 points; manual run of §10 30 steps, the three failed steps fixed in 9.5.8, 9.5.9 and 9.5.10 and passed in the rerun of 2026-11-22 ([PRD](../features/epic-09/feature-9.5-page-layouts.md)) | 2026-11-20 | 2026-11-22 |
| 9.6 Widget and shell redesign (sidebar tools instead of the top bar, widget header with the chart or table switch, redrawn widgets of My dashboard, unit and university analytics and Hall of Fame, Profile in sections, remaining pages of the all-pages canvas) | PRD approved 2026-11-23: 7 stories, 29 points ([PRD](../features/epic-09/feature-9.6-widget-redesign.md)) | 2026-11-23 | - |
| 9.7 Pre-ship polish (sign-in themes with the quote of the day and logo mark 9, spreadsheet tables in widgets, timeline road and feed, unit analytics rail, Users split on select, delegations board, notifications inbox, privacy control panel, first sign-in checklist and tour) | PRD approved 2026-11-24: 9 stories, 30 points ([PRD](../features/epic-09/feature-9.7-preship-polish.md)) | - | - |

## Current focus

Feature 9.5 done 2026-11-22 (SCRUM-163 to SCRUM-169, fixes SCRUM-171 to SCRUM-173). Feature 9.6 (SCRUM-174 to SCRUM-180) is in progress: stories stacked one branch on the
next, the full gate and e2e once on the last branch; then the 9.4 close-out and the demo server 9.2.4 (SCRUM-134).

## Scope

The trade-offs accepted for the defense that matter once the system runs on a public address (design review of
2026-10-04), and the demo deployment itself (ADR-021, deployment in mid-November, code freeze around 2026-11-22).

- Token values in `oauth2_authorization` hashed at rest.
- Rate limits keyed by account and by address, sized so that one campus NAT does not lock out a faculty, plus
  per-user limits on the expensive signed-in endpoints (exports, the PDF report).
- Lighthouse mobile performance 90 on `/awards/new` and `/awards` (carried from AC-3.7 of Feature 8.1).
- Nightly database and document backups copied off the server, with a rehearsed restore.
- A CI deploy job over SSH, log rotation and memory limits, then the demo server on AWS EC2.
- Stretch: a backend-for-frontend with an HttpOnly session cookie, only if it fits before about 2026-12-01.

Out of scope here: Spring Session on Redis and ShedLock (the demo runs one backend instance, ADR-021; both stay
production work at the university, ADR-021 addendum 2026-10-05), Grafana dashboards and alert rules (Feature 4.2),
mails lost on an outage between commit and send (Feature 6.1 finding, icebox), breach notification, a domain
purchase.

## Stories

`parallel` marks stories whose UI can be built in a separate lane from the OpenAPI contract while the backend is in
progress. No story has a large UI part; 9.1.3 has one but is a stretch.

| # | Story | Feature | Pts | Jira | GitHub | Parallel | Status |
|---|-------|---------|-----|------|--------|----------|--------|
| 1 | 9.1.1 Refresh tokens and authorization codes hashed at rest | 9.1 | 5 | SCRUM-129 | #370 | no | Done |
| 2 | 9.1.2 Rate limits keyed by account and address | 9.1 | 5 | SCRUM-130 | #371 | no | Done |
| 3 | 9.2.1 Signed-in pages reach Lighthouse mobile performance 90 | 9.2 | 3 | SCRUM-131 | #372 | no | Done |
| 4 | 9.2.2 Nightly backups with a restore drill | 9.2 | 5 | SCRUM-132 | #373 | no | Done |
| 5 | 9.2.3 Deploy job and production server settings | 9.2 | 3 | SCRUM-133 | #374 | no | Done |
| 5a | 9.2.6 Dependency upgrade for critical CVEs | 9.2 | 2 | SCRUM-137 | #387 | no | Done |
| 5b | 9.3.1 Theme tokens, fonts and icons | 9.3 | 5 | SCRUM-138 | #391 | no | Done |
| 5c | 9.3.2 Effect components, motion and chart theme | 9.3 | 3 | SCRUM-139 | #392 | no | Done |
| 5d | 9.3.3 Shell, navigation and command palette | 9.3 | 5 | SCRUM-140 | #393 | no | Done |
| 5e | 9.3.4 Sign-in and account pages | 9.3 | 3 | SCRUM-141 | #394 | no | Done |
| 5f | 9.3.5 Award list, award page and new award | 9.3 | 5 | SCRUM-142 | #395 | no | Done |
| 5g | 9.3.6 Review queue with checklist, shortcuts and undo | 9.3 | 5 | SCRUM-143 | #396 | no | Done |
| 5h | 9.3.7 Remaining screens in the new look | 9.3 | 5 | SCRUM-144 | #397 | no | Done |
| 5i | 9.3.8 Stable review-queue and award-mobile e2e | 9.3 | 2 | SCRUM-145 | #407 | no | Done |
| 5j | 9.3.9 Not-found card on record pages and palette text normalisation | 9.3 | 2 | SCRUM-146 | #409 | no | Done |
| 5m | 9.3.22 Held decision after a revoked role ends at sign-in and the request is orphaned | 9.3 | 2 | SCRUM-149 | #414 | no | Done |
| 5l | 9.3.21 Lighthouse performance below 90 on the signed-in pages | 9.3 | 3 | SCRUM-148 | #413 | no | Done |
| 5k | 9.3.14 Review queue shortcuts stop after ticking a checklist item | 9.3 | 1 | SCRUM-147 | #412 | no | Done |
| 5r | 9.4.5 Command centre with the department reminder | 9.4 | 5 | SCRUM-159 | #434 | no | Done |
| 5q | 9.4.4 My awards with the CV export | 9.4 | 5 | SCRUM-158 | #433 | no | Done |
| 5p | 9.4.3 Hall of Fame for the rector and the dean, role home | 9.4 | 3 | SCRUM-157 | #432 | no | Done |
| 5o | 9.4.2 Widget kit, generated facts and monthly counts | 9.4 | 5 | SCRUM-156 | #431 | no | Done |
| 5n | 9.4.1 Scheme and dashboard layout on the account | 9.4 | 5 | SCRUM-155 | #430 | no | Done |
| 5s | 9.4.6 Role-aware navigation, personal tabs and dashboard facts | 9.4 | 3 | SCRUM-161 | #450 | no | Done |
| 5t | 9.4.7 KPI card names the compared months | 9.4 | 1 | SCRUM-162 | #453 | no | Done |
| 5aa | 9.5.7 Compliance and delegations in the split layout | 9.5 | 5 | SCRUM-169 | #462 | no | Done |
| 5z | 9.5.6 Unit analytics split and university bento | 9.5 | 5 | SCRUM-168 | #461 | no | Done |
| 5y | 9.5.5 Reviews with the request beside the queue | 9.5 | 5 | SCRUM-167 | #460 | no | Done |
| 5x | 9.5.4 Users with the person beside the list | 9.5 | 5 | SCRUM-166 | #459 | no | Done |
| 5w | 9.5.3 Achievements as a story feed | 9.5 | 3 | SCRUM-165 | #458 | no | Done |
| 5v | 9.5.2 My dashboard in the split layout | 9.5 | 5 | SCRUM-164 | #457 | no | Done |
| 5u | 9.5.1 Page kit and shell fixes | 9.5 | 3 | SCRUM-163 | #456 | no | Done |
| 5za | 9.7.9 First sign-in checklist and spotlight tour | 9.7 | 5 | SCRUM-189 | #507 | no | To do |
| 5za | 9.7.8 Privacy control panel | 9.7 | 3 | SCRUM-188 | #506 | no | To do |
| 5za | 9.7.7 Notifications inbox with a reading pane | 9.7 | 3 | SCRUM-187 | #505 | no | To do |
| 5za | 9.7.6 Delegations status board | 9.7 | 2 | SCRUM-186 | #504 | no | To do |
| 5za | 9.7.5 Users list that splits on select | 9.7 | 3 | SCRUM-185 | #503 | no | To do |
| 5za | 9.7.4 Unit analytics rail and clickable top performers | 9.7 | 3 | SCRUM-184 | #502 | no | To do |
| 5za | 9.7.3 Timeline as a journey road and a dated feed | 9.7 | 3 | SCRUM-183 | #501 | no | To do |
| 5za | 9.7.2 Spreadsheet tables in widgets | 9.7 | 3 | SCRUM-182 | #500 | no | To do |
| 5za | 9.7.1 Sign-in themes, quote of the day and logo mark 9 | 9.7 | 5 | SCRUM-181 | #499 | no | To do |
| 5za | 9.6.7 Remaining pages of the all-pages canvas | 9.6 | 3 | SCRUM-180 | #491 | no | In review |
| 5za | 9.6.6 Profile with sections | 9.6 | 3 | SCRUM-179 | #490 | no | In review |
| 5za | 9.6.5 Hall of Fame widgets | 9.6 | 5 | SCRUM-178 | #489 | no | In review |
| 5za | 9.6.4 University bento with the faculty comparison | 9.6 | 5 | SCRUM-177 | #488 | no | In review |
| 5za | 9.6.3 Unit analytics widgets | 9.6 | 5 | SCRUM-176 | #487 | no | In review |
| 5za | 9.6.2 My dashboard widgets | 9.6 | 3 | SCRUM-175 | #486 | no | In review |
| 5za | 9.6.1 Canvas look, sidebar tools and widget header | 9.6 | 5 | SCRUM-174 | #485 | no | In review |
| 5za | 9.5.10 Back from a pane at 360 px loses the list scroll | 9.5 | 2 | SCRUM-173 | #479 | no | Done |
| 5za | 9.5.9 Selection that left the list stays in Reviews and Users | 9.5 | 2 | SCRUM-172 | #478 | no | Done |
| 5za | 9.5.8 Dashboard header without context line, actions wrap | 9.5 | 1 | SCRUM-171 | #477 | no | Done |
| 5za | 9.4.8 Logo opens the home of a signed-in person | 9.4 | 1 | SCRUM-170 | #471 | no | Done |
| 6 | 9.2.4 Demo server on AWS EC2 with a smoke test | 9.2 | 3 | SCRUM-134 | #375 | no | Planned |
| 7 | 9.1.3 Backend-for-frontend with an HttpOnly session cookie (stretch) | 9.1 | 8 | SCRUM-135 | #376 | no | Planned |

Total: 24 points without the stretch, 32 with it; sprints 7 to 9 (2026-10-29 to 2026-11-15), the server in sprint 9.

## Decisions

Kickoff defaults, confirmed or changed in the feature PRDs.

| Date | Decision | Rationale | Reference |
|------|----------|-----------|-----------|
| 2026-10-29 | Scope: hashed tokens, rate limits, Lighthouse 90, backups, deploy job, AWS EC2 server; backend-for-frontend as a stretch; Spring Session on Redis out | The demo runs one instance, so shared sessions buy nothing before a second one; the four security and operations items are what a public address needs | ADR-021 |
| 2026-10-29 | No story for a fixed signing key: `docker-compose.prod.yml` already refuses to start without `JWK_KEY_ID`, `JWK_PRIVATE_KEY` and `JWK_PUBLIC_KEY` (SCRUM-33); the deploy story only checks it | Already shipped | DEMO_DEPLOYMENT §1 |
| 2026-10-29 | Hashing converts the stored authorizations in place (V050 computes the hashes in SQL), so nobody signs in again; replaces the kickoff default of invalidating them | Same result without a forced sign-in; the conversion is idempotent | 9.1 PRD §7 decision 4 |
| 2026-10-29 | Feature 9.1 PRD approved: hashing in a decorator of the authorization service with a `sha256:` prefix, plain SHA-256; budgets 600 a minute per address (authentication), 10 per account (sign-in form), 600 per user (API), 5 per user (report and exports); refusals counted in a metric, not audited; 9.1.3 only after 9.2.4 and before 2026-12-01 | NAT sizing, no double hashing of reloaded rows, no audit flood | 9.1 PRD §7 |
| 2026-10-29 | Rate limits stay on the existing Redis fixed-window counter (`FixedWindowCounter`, `RateLimitFilter`, `RequestThrottle`); no new library | One mechanism already in use for the authentication, public and upload limits | 9.1.2 |
| 2026-10-29 | Backups go to an object store outside the server (an S3 bucket in the same AWS account by default), with the document encryption key stored apart | ADR-021 requires off-server copies; the key decrypts every document | 9.2.2 |
| 2026-11-14 | Feature 9.3 New look: the `chnu` palette replaced in place by `light-dark()` pairs (burgundy and cream light, Aurora dark), Manrope and Onest, Lucide icons, `motion` only for count-ups and staggers, six own effect components; the scheme stays in the browser until 9.4; the 10 s undo holds the decision in the browser | The look chosen from the prototype rounds; no new library route (Material stays), no API or schema change, ADR-023 untouched | 9.3 PRD §7, ADR-014 and ADR-016 addenda (9.3.1) |
| 2026-11-15 | 9.3 AC-1.6 accepted with the development-machine medians below 90 (`/awards` 81, `/awards/new` 77, develop 76 / 76): the new look does not lower them; the 90 target is measured again on the demo server in 9.2.4 | With 3 GB free the local runs vary by up to 30 points; the server is the environment NFR-009 is about | 9.2 PRD §10 step 16, PR #399 |
| 2026-11-15 | Chart colours follow the theme: `--app-chart-1..8` categorical, bands one hue per scheme (burgundy light, cyan dark) | The blue single hue clashed with both schemes; the contrast checks of the 2026-10 addendum still hold | ADR-014 addendum 2026-11-15 (9.3.2) |
| 2026-11-16 | 9.3.21 keeps the 2026-11-15 decision: only the request deferral on `/awards` ships (medians 84 / 77 after, 80 / 77 before); 90 at 4x CPU needs fewer start-up requests and a lighter phone shell, not trimming | The long tasks are spread over bootstrap, the sign-in chain and Material rendering; an empty award list left `/awards` at 77 | 9.3 PRD §10 Run 2026-11-16, SCRUM-148 |
| 2026-11-16 | Feature 9.3 closed: steps 14 and 22 of the manual run, failed in the run, are accepted on the tests of their fix stories instead of a second walk (`review-queue.component.spec` ac14, e2e `review-queue.spec` ac6_1 to ac6_4 and «the decision lost with the session is named after the next sign-in»); step 21 stays under the 2026-11-15 decision | The fixes are frontend-only and their e2e cases replay the failed steps on the Compose stack | 9.3 PRD §10 Run 2026-11-16, PRs #416, #417, #418 |
| 2026-11-21 | Feature 9.5 validated: every AC has a named test (AC-3.4 assertions added to `achievements.spec.ts`); full gate and e2e green; five findings recorded, a selection that leaves the list keeps its state in Reviews and Users | All 9.5 code merged; the findings do not block the manual run | [PRD 9.5 Validation](../features/epic-09/feature-9.5-page-layouts.md#validation) |
| 2026-11-22 | Feature 9.5 closed: steps 1, 25 and 29, failed in the run of 2026-11-21, walked again after their fixes and passed; the dashboard context line keeps the label «Зараз на розгляді», AC-8.1 and step 1 follow it | Rerun on the Compose stack at develop fabb23e, no step accepted on tests alone | 9.5 PRD §10 Run 2026-11-22, PRs #481, #482, #483 |
| 2026-11-17 | Feature 9.4 PRD approved: widget order and visibility only (no free grid); the scheme and layouts in `dashboard_preferences` (V052 drops `hidden_widgets`, export format 1.8); facts generated in the browser; the reminder goes to a colleague the sender picks, once a week per department; CV export as PDF and text; `/` opens the dashboard of the highest role | The cut named on 2026-11-14; no head-of-department data exists; no stored text to translate | 9.4 PRD §7 |
| 2026-11-14 | The "remind head of department" mail and the CV export move to 9.4; the demo server 9.2.4 moves after 9.3, the Epic 2 feature for people without an account and 9.4 | Both sit on 9.4 screens; the demo should show the finished look | 9.3 PRD §7 deviation 1 |
| 2026-10-29 | Lighthouse 90 sits in Feature 9.2: it is measured against the production build that the deployment ships | Same build, same measurement setup as 8.1 | 8.1 PRD F-2 |

## Documentation deviations found at kickoff

1. ADR-021 names Hetzner and a 4 vCPU / 8 GB server; the target moved to an AWS EC2 instance paid from sign-up
   credits (2026-10-11) without an addendum. 9.2.3 adds it; DEMO_DEPLOYMENT §3 follows.
2. The roadmap rate-limit row says only "20 requests per minute per client address on the authentication
   endpoints"; uploads (20 a minute per user) and the public API (120 a minute per address) are limited too, the
   exports and the PDF report are not (Feature 5.3 PRD note 2).
3. THREAT_MODEL shows a Resilience4j `RateLimiter` aspect; the code uses Redis fixed windows. The as-built rules
   stay in AUTHENTICATION_AUTHORIZATION §9; THREAT_MODEL is not rewritten.
4. The Epic 6 PRDs (6.1, 6.2) require the backup procedure to state how erased accounts and expired data leave the
   backups; 9.2.2 carries this as an acceptance criterion.
5. The roadmap and ADR-021 addendum list Spring Session on Redis as Epic 9 work; it is out by the scope decision
   and stays a production item.

## Risks

- Memory: the stack (ClamAV about 1.2 GB, PostgreSQL, Redis, MinIO, backend, frontend, Caddy) may not fit a 4 GB
  instance. 9.2.3 measures the rehearsal with `docker stats`; an 8 GB size (or swap) is chosen if it does not.
  The sizes the account's free plan allows are checked before 9.2.4.
- The server costs money from the day it starts; 9.2.4 waits until the stories before it are merged.
- Hashing changes how the authorization server looks tokens up; the refresh, revocation and sign-out-everywhere
  paths are retested end to end (security review).
- The backend-for-frontend touches every authenticated call of the frontend; if it does not fit before
  2026-12-01 it moves to the icebox with no partial merge.

## Technical notes

- 9.5 refactor candidates (validation sweep 2026-11-21): `compliance.component.html` (~500 lines) as one child
  component per section; batch selection bookkeeping in `review-list.component.ts` as a helper; one count-line
  helper for the Users context line and the delegation days-left phrase (`pluralForm` against Transloco plurals);
  a shared list test fixture for the three split pages; `initialsOf` wrappers against a `name` input on `appListRow`.

- `oauth2_authorization` is written by `JdbcOAuth2AuthorizationService`; lookups by token value go through
  `findByToken`. Hashing needs the stored value and the lookup parameter to be hashed in one place.
- Existing limits: `auth/security/RateLimitFilter` (per-address windows, prefixes `auth:rate:` and
  `public:rate:`), `auth/service/LoginAttemptService` (failures per address, lock after five),
  `common/limit/RequestThrottle` (registration, password reset, email change, data export).
- CI pushes images to GHCR on `main` and `develop` (`docker-build`); `deploy-demo` deploys the `main-<sha>` images
  to the demo server over SSH, or an earlier tag on a manual run (9.2.3).
- Lighthouse runs through `tools/lighthouse` (`run.mjs`, mobile profile) against the Compose build.
- 9.3 clean-up candidates (validation sweep, no defect): one shortcut guard and one Apple-platform check shared by
  the review queue, palette and shell; a card-surface and facts-collapse mixin for the nine SCSS copies; one brand
  mark component for the shell and the auth layout; Playwright clock instead of the fixed waits in
  `review-queue.spec`.

## Documentation quick links

- [ADR-021 Deployment target](../architecture/adr/ADR-021-Deployment-Target.md) ·
  [Demo deployment runbook](../deployment/DEMO_DEPLOYMENT.md)
- [AUTHENTICATION_AUTHORIZATION §9](../security/AUTHENTICATION_AUTHORIZATION.md) ·
  [THREAT_MODEL](../security/THREAT_MODEL.md) · [SECURITY_ARCHITECTURE](../security/SECURITY_ARCHITECTURE.md)
- [Feature 8.1 PRD](../features/epic-08/feature-8.1-mobile-pwa.md) (F-2, AC-3.7) ·
  [Feature 6.1 PRD](../features/epic-06/feature-6.1-consent-data-subject-rights.md),
  [Feature 6.2 PRD](../features/epic-06/feature-6.2-automated-data-retention.md) (backups)

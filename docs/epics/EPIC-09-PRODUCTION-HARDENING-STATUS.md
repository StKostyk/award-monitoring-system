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
| 9.2 Demo deployment and operations (Lighthouse 90 on the signed-in pages, nightly backups, deploy job, AWS EC2 server) | Planned | - | - |

## Current focus

Feature 9.1 PRD approved; the next story is 9.1.1 (SCRUM-129).

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
| 2 | 9.1.2 Rate limits keyed by account and address | 9.1 | 5 | SCRUM-130 | #371 | no | In review |
| 3 | 9.2.1 Signed-in pages reach Lighthouse mobile performance 90 | 9.2 | 3 | SCRUM-131 | #372 | no | Planned |
| 4 | 9.2.2 Nightly backups with a restore drill | 9.2 | 5 | SCRUM-132 | #373 | no | Planned |
| 5 | 9.2.3 Deploy job and production server settings | 9.2 | 3 | SCRUM-133 | #374 | no | Planned |
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

- `oauth2_authorization` is written by `JdbcOAuth2AuthorizationService`; lookups by token value go through
  `findByToken`. Hashing needs the stored value and the lookup parameter to be hashed in one place.
- Existing limits: `auth/security/RateLimitFilter` (per-address windows, prefixes `auth:rate:` and
  `public:rate:`), `auth/service/LoginAttemptService` (failures per address, lock after five),
  `common/limit/RequestThrottle` (registration, password reset, email change, data export).
- CI already pushes images to GHCR on `main` and `develop` (`docker-build`); `deploy-staging` and the production job
  are placeholders.
- Lighthouse runs through `tools/lighthouse` (`run.mjs`, mobile profile) against the Compose build.

## Documentation quick links

- [ADR-021 Deployment target](../architecture/adr/ADR-021-Deployment-Target.md) ·
  [Demo deployment runbook](../deployment/DEMO_DEPLOYMENT.md)
- [AUTHENTICATION_AUTHORIZATION §9](../security/AUTHENTICATION_AUTHORIZATION.md) ·
  [THREAT_MODEL](../security/THREAT_MODEL.md) · [SECURITY_ARCHITECTURE](../security/SECURITY_ARCHITECTURE.md)
- [Feature 8.1 PRD](../features/epic-08/feature-8.1-mobile-pwa.md) (F-2, AC-3.7) ·
  [Feature 6.1 PRD](../features/epic-06/feature-6.1-consent-data-subject-rights.md),
  [Feature 6.2 PRD](../features/epic-06/feature-6.2-automated-data-retention.md) (backups)

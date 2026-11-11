# Feature 9.2: Demo deployment and operations

> **Epic**: 9 Production hardening (SCRUM-128) · **Tracker**: `docs/epics/EPIC-09-PRODUCTION-HARDENING-STATUS.md`
> **Sprint**: 9 (9.2.1 and 9.2.2 in Sprint 8) · **Points**: 19
> **Status**: Approved 2026-11-10 (9.2.1 done 2026-10-29; sections 9.2.2 to 9.2.5 added 2026-10-30)
> **Governing docs**: ADR-021 (single host, Compose, Caddy, Brevo, nightly `pg_dump` and MinIO mirror), ADR-017,
> ADR-019, `docs/deployment/DEMO_DEPLOYMENT.md` §1-3, PRIVACY_BY_DESIGN §5.2 (backups row), Feature 6.1 and 6.2
> scope (backup procedure states how erased and expired data leave the backups), SECURITY_FRAMEWORK I004,
> THREAT_MODEL I-DB-01 and I-DOC-02, Feature 8.1 AC-3.7 and finding F-2, `.github/workflows/ci-cd.yml`,
> `docker-compose.yaml`, `docker-compose.prod.yml`, `ErasureJob`, `RetentionJob`, `DocumentSweeper`,
> `AwardFormComponent`

## 1. Problem and personas

The demo runs on one server (ADR-021) from the production build. Feature 8.1 left two signed-in pages under the
Lighthouse mobile target of NFR-009 (finding F-2 of the 8.1 PRD: performance 74 to 80 on `/awards/new` and
`/awards`, from total blocking time at 4x CPU). This feature makes the build the deployment ships measure well,
then backs it up, deploys it and starts the server.

Today nothing is backed up: a lost volume loses every award and document. The CI file has two placeholder deploy
jobs that only print text. The production override sets no memory limits and no log rotation, so a long-running
server can fill its disk with container logs or let ClamAV push the backend out of memory. ADR-021 still names
Hetzner, while the demo now runs on AWS EC2 on sign-up credits (decided 2026-10-11).

| Persona | Need |
|---|---|
| Operator (me, then the university's IT) | Nightly backups off the server that I can restore in one documented run; a deploy that is one merge to `main`; a server whose memory and disk stay within bounds |
| Galyna, GDPR officer | An erased account does not come back with a restore; backups are encrypted and expire |
| Defense committee, pilot users | A public HTTPS address that answers, sends mail and keeps its data |
| Anastasia, employee on a phone | The award form opens quickly and fits the screen |

## 2. Scope

In:

- 9.2.1 (done): lighter award list on phones, dates without a formatter on the render path.
- 9.2.2: a `backup` service in the production Compose stack: nightly encrypted database dump and document archive
  uploaded to an S3 bucket outside the server with a 14-day expiry; a restore command; a catch-up run of the
  erasure, retention and document jobs at start, so a restore does not bring back erased or expired data; a
  rehearsed restore on the local rehearsal stack.
- 9.2.3: a `deploy-demo` CI job over SSH (disabled until its variables exist) instead of the two placeholders;
  log rotation and memory limits for every service of the production override, sized from a measurement; ADR-021
  addendum for AWS EC2.
- 9.2.4: the demo server on EC2 (`m7i-flex.large`, `eu-central-1`), the backup bucket, a budget alarm, an uptime
  check, the runbook's manual verification against the public address and one restore on the server.
- 9.2.5: the award form in three steps on phones.

Out:

- A domain purchase (the address is `<elastic-ip>.sslip.io`; Epic 9 scope).
- Spring Session on Redis, ShedLock, a second backend instance (ADR-021 addendum 2026-10-05).
- Point-in-time recovery (WAL archiving): the loss window is one day, accepted for the demo (risk R-2).
- Grafana and alert rules on the server (Feature 4.2 stays local); the uptime check is the only alert.
- Infrastructure as code for AWS (console steps in the runbook; one server, one bucket).
- Steps on the correction form (`AwardCorrectionComponent`): it is reached from a returned award, not measured.

### 2.1 Target environment

| Part | Choice | Approx. cost a month |
|---|---|---|
| Network | Default VPC of `eu-central-1`, one public subnet, no NAT gateway | 0 |
| Compute | One EC2 `m7i-flex.large` (2 vCPU, 8 GB), Ubuntu 24.04 LTS, the whole Compose stack on it | ~73 USD (credits) |
| Storage | One encrypted 40 GB gp3 root volume: PostgreSQL, MinIO, Redis and ClamAV volumes | ~4 USD |
| Address | Elastic IP, `<ip>.sslip.io`, TLS from Let's Encrypt through Caddy | ~4 USD |
| Firewall | Security group: 80 and 443 from anywhere, 22 key sign-in only | 0 |
| Recovery | Automatic recovery on a failed system status check (default on `m7i`), a CloudWatch alarm that reboots on a failed instance status check | 0 (free alarms) |
| Backups | S3 bucket in `eu-north-1`, 14-day lifecycle rule, put-only IAM user | < 1 USD |
| Cost guard | AWS Budgets alarm at 10 USD actual cost | 0 |
| Delivery | GitHub Actions builds, GHCR holds the images, `deploy-demo` deploys over SSH | 0 |
| Mail | Brevo SMTP relay | 0 (free plan) |

No load balancer, Auto Scaling group, RDS, ElastiCache or second instance:

- **Load balancer** (ALB, about 18 USD a month plus traffic units): with one target it only adds a hop. Caddy
  already terminates TLS, and a load balancer would need either a domain for an ACM certificate or plain HTTP
  inside.
- **Auto Scaling group**: with one instance it only replaces a failed host, and the replacement would start
  with an empty disk: the database and the documents live on the instance's volume. Automatic recovery keeps the
  same instance, volume and address, which is what a stateful single host needs. A replacement from scratch is
  the restore of §4 of the runbook (about an hour, covered by the 9.2.4 drill).
- **Scaling out**: a second backend instance needs Spring Session on Redis and ShedLock, which are production
  work (ADR-021 addendum 2026-10-05). Managed PostgreSQL and Redis are ADR-020's production target at about 10
  times the cost.

At the university this becomes ADR-018/020's layout (load balancer, several backend instances, managed database,
S3 for documents). The Compose stack keeps the same images and settings, so that move changes the infrastructure,
not the application.

## 3. Stories

| # | Story | Points | Parallel | Depends on | Jira | GitHub |
|---|-------|--------|----------|------------|------|--------|
| 9.2.1 | Signed-in pages reach Lighthouse mobile performance 90 | 3 | no | - | SCRUM-131 | #372 |
| 9.2.2 | Nightly backups with a restore drill | 5 | no | - | SCRUM-132 | #373 |
| 9.2.3 | Deploy job and production server settings | 3 | no | 9.2.2 (memory of the backup service) | SCRUM-133 | #374 |
| 9.2.4 | Demo server on AWS EC2 with a smoke test | 3 | no | 9.2.2, 9.2.3 | SCRUM-134 | #375 |
| 9.2.5 | Award form in steps on phones | 5 | no | 9.2.1 | SCRUM-136 | #381 |

Order: 9.2.2 → 9.2.3 → 9.2.4 (server in mid-November, before the code freeze around 2026-11-22) → 9.2.5. 9.2.5 is
frontend only and can move ahead of 9.2.4 if provisioning waits; the deploy job ships it either way.

## 4. Acceptance criteria

### 9.2.1 Signed-in pages reach Lighthouse mobile performance 90 (SCRUM-131)

Carried from AC-3.7 of Feature 8.1, restricted to the two pages of F-2.

- **AC-1.1** (Lighthouse) Given the Compose build, `tools/lighthouse` (`npm run flow`, mobile profile, simulated
  throttling) three times, then the median performance on `/awards/new` and `/awards` is recorded with the
  Lighthouse version; accessibility and best practices stay at 90 or above, `/login` keeps its scores and the
  initial bundle stays within the `angular.json` budget. Target 90; the measured result is in §10.
- **AC-1.2** (List filters on a phone) Given a screen of at most 600 px, when `/awards` opens, then the search
  field is shown and the status, category and date filters stay behind a "Filters" button (`aria-expanded`) that
  counts the filters in use; they render when the button opens them. Wider screens show every filter as before.
- **AC-1.3** (Page size on a phone) Given a screen of at most 600 px when the application starts, when `/awards`
  opens, then it lists 10 awards a page; 10 is offered beside 20, 50 and 100 on every screen. The size is read
  once, as the reduced-motion setting is.
- **AC-1.4** (Dates off the render path) Plain dates (`YYYY-MM-DD`) are shown without a date formatter, in the same
  text as before (`09.10.2026`, `09/10/2026`); the first formatter of a page is created while the start-up
  requests are under way, not while a page renders.

### 9.2.2 Nightly backups with a restore drill (SCRUM-132)

- **AC-2.1** (Backup service) Given the production override, then a `backup` service (image built from
  `infra/backup/`, based on `postgres:17-alpine` with `age`, `rclone` and the MinIO client; no published port;
  `TZ=Europe/Kyiv`) runs `backup.sh` daily at `BACKUP_CRON` (default `30 4 * * *`, after the erasure job at 02:30
  and the retention job at 04:00). The development stack does not start it.
- **AC-2.2** (What a run writes) Given a run at time T, then it uploads `db-<T>.dump.age` (`pg_dump -Fc` of the
  database) and then `documents-<T>.tar.age` (every object of `award-documents`, read through the MinIO API), both
  encrypted with `age` to the public key `BACKUP_AGE_RECIPIENT`, to `BACKUP_REMOTE` (an rclone remote: an S3 bucket
  on the server, a bucket of the Compose MinIO in the rehearsal), then `manifest-<T>.json` (file names, sizes,
  SHA-256 of the encrypted files, row count of `users` and `awards`, object count, image tag). No unencrypted dump
  or archive is left in the container; a run that fails at any point uploads no manifest and exits non-zero.
- **AC-2.3** (Health) Given the service, then its health check reports `unhealthy` when the last successful run
  (the time `backup.sh` writes after the manifest upload) is older than 26 hours, or no run has succeeded since
  the container started more than 26 hours ago; `docker compose ps backup` shows it. `backup.sh now` runs one
  backup at once.
- **AC-2.4** (Expiry) Given the S3 bucket, then a lifecycle rule deletes objects 14 days after upload and the
  backup's S3 account may list, read and put objects but not delete them (runbook §4); in the rehearsal
  `backup.sh` deletes remote files older than `BACKUP_KEEP_DAYS` (default 14) itself, since the Compose MinIO has
  no lifecycle rule. Every erased or expired record has therefore left every backup at most 14 days after the
  nightly job removed it.
- **AC-2.5** (Restore) Given a manifest time T and the private key file, when `restore.sh T` runs in the `backup`
  service with the key mounted read-only (`docker compose run --rm -v ./backup.key:/run/backup.key:ro backup
  restore.sh T`) while `app` is stopped, then it checks the SHA-256 of both files against the manifest, restores
  the database in one transaction (schema `public` dropped and recreated, the dump replayed through `psql
  --single-transaction`; `pg_restore --clean` cannot drop the inherited keys of the audit partitions), replaces
  the objects of
  `award-documents` with those of the archive (objects not in the archive are removed), and prints the row and
  object counts beside those of the manifest. A wrong key, a changed file or a running `app` stops it before
  anything is written, with a message naming the cause.
- **AC-2.6** (Catch-up at start) Given `app.jobs.catch-up-on-start=true` (default `false`; `true` in the
  `production` profile), when the backend starts, then after `app.jobs.catch-up-delay` (default `PT1M`) it runs
  the erasure job, the retention job and the document sweep once, in this order, logging one line per job. A
  restored database whose erasure requests fell due after the dump therefore has those accounts erased before
  the next night; the jobs are idempotent (reminders carry `warning_sent_at`, retention runs are recorded in
  `retention_runs`), so a start without a restore changes nothing beyond a `retention_runs` row.
- **AC-2.7** (Keys apart) Given the runbook, then the private backup key, `.env.prod` and a copy of
  `MINIO_KMS_SECRET_KEY` are kept off the server and outside the bucket (my password manager); the server holds
  only the public key. The archive holds plain document content (encrypted by `age`), so a restore to a new
  server works with a new `MINIO_KMS_SECRET_KEY`.
- **AC-2.8** (Drill) Given the rehearsal stack of DEMO_DEPLOYMENT §2 with the demo seed, when one backup runs,
  an award with a document is added, an account whose erasure is due after the dump is set up, and the backup of
  before is restored, then the added award is gone, the earlier award and its document are back and open, and the
  account is erased within two minutes of the start (AC-2.6). The run is recorded in §10.
- **AC-2.9** (Docs) Given the documentation, then DEMO_DEPLOYMENT gets §4 "Backups and restore" (variables, bucket
  and policy, key handling, restore steps, the drill); PRIVACY_BY_DESIGN §5.2 row "Backups" reads "Encrypted,
  expire after 14 days; a restore runs the erasure and retention jobs before the next night"; the 6.1 and 6.2
  PRDs' backup lines point to it; SECURITY_FRAMEWORK I004 names `age` encryption and the no-delete account.

### 9.2.3 Deploy job and production server settings (SCRUM-133)

- **AC-3.1** (Deploy job) Given a push to `main`, when `docker-build` has pushed the images tagged `main-<sha>`,
  then `deploy-demo` (needs `docker-build` and `security-scan`, GitHub environment `demo`, `if:
  vars.DEMO_HOST != ''`) copies `docker-compose.yaml`, `docker-compose.prod.yml` and `infra/caddy/Caddyfile` to
  `DEMO_DIR` over SSH (`secrets.DEMO_SSH_KEY`, host key pinned by `secrets.DEMO_KNOWN_HOSTS`, user
  `vars.DEMO_USER`), together with `infra/minio/` and `infra/backup/` (the `minio-init` script and the backup image
  source), writes `.env.release` with `BACKEND_IMAGE` and `FRONTEND_IMAGE` of that tag, and runs
  `docker compose ... --env-file .env.prod --env-file .env.release pull`, `build backup` and `up -d --wait`,
  after a scan of exactly those images that stops on a fixable critical vulnerability (D-14). Without `DEMO_HOST`
  the job is skipped, not failed. The placeholder jobs `deploy-staging` and `deploy-production` are removed.
- **AC-3.2** (Smoke test) Given the deploy, then the job checks within 5 minutes that
  `https://<SITE_ADDRESS>/.well-known/openid-configuration` answers 200 with the issuer `https://<SITE_ADDRESS>`
  and that `docker compose ps` shows no service `unhealthy` or `exited` (except `minio-init`); otherwise it fails
  and prints the last 50 log lines of the unhealthy services.
- **AC-3.3** (Rollback) Given `workflow_dispatch` with the input `image_tag` (an earlier `main-<sha>`), then
  `deploy-demo` deploys that tag without a build; the `deploy_environment` input is removed.
- **AC-3.4** (Log rotation) Given the production override, then every service logs through `json-file` with
  `max-size: 10m` and `max-file: 5` (one YAML anchor), so the container logs stay under about 50 MB a service.
- **AC-3.5** (Memory limits) Given `docker stats` over the manual scenario of DEMO_DEPLOYMENT on the rehearsal
  stack (peak per service recorded in §10), then every service of the production override has `mem_limit` at
  about 1.5 times its peak, rounded up to 64 MB steps, the backend `-XX:MaxRAMPercentage` fits its limit, and the
  limits add up to at most 6.5 GB, leaving room for the host on an 8 GB server.
- **AC-3.6** (ADR) Given ADR-021, then an addendum records AWS EC2 `m7i-flex.large` (2 vCPU, 8 GB, free-plan
  eligible) in `eu-central-1` on sign-up credits instead of Hetzner, the backup bucket in `eu-north-1`, the
  `sslip.io` address on an Elastic IP, the deploy job and the environment of §2.1 with the reasons for one instance
  without a load balancer or Auto Scaling group; `docs/diagrams/deployment-demo.puml` shows the AWS parts (VPC,
  security group, Elastic IP, EBS volume, S3 bucket in the second region, GitHub Actions and GHCR); status,
  revision history and DEMO_DEPLOYMENT §3 follow.

### 9.2.4 Demo server on AWS EC2 with a smoke test (SCRUM-134)

- **AC-4.1** (Server) Given the runbook §3, then one `m7i-flex.large` instance (Ubuntu 24.04 LTS, 40 GB gp3,
  encrypted volume) in `eu-central-1` with an Elastic IP runs Docker Engine with the Compose plugin and a 2 GB swap
  file; its security group allows 80 and 443 from anywhere and 22 only for key sign-in (password sign-in and root
  sign-in off in `sshd_config`); `unattended-upgrades` installs security updates. Automatic recovery is on
  (`aws ec2 describe-instances` shows `AutoRecovery: default`), and a CloudWatch alarm on
  `StatusCheckFailed_Instance` (2 of 2 minutes) reboots the instance.
- **AC-4.2** (Stack) Given `.env.prod` with `SITE_ADDRESS=<elastic-ip>.sslip.io`, Brevo SMTP and the demo seed,
  when `deploy-demo` runs after its variables and secrets are set, then the job is green and the smoke test of
  AC-3.2 passes against the public address with a Let's Encrypt certificate.
- **AC-4.3** (Runbook check) Given the public address, when the manual verification of DEMO_DEPLOYMENT runs (steps
  2 to 10 against `https://<SITE_ADDRESS>`; the reset mail of step 6 to a real mailbox through Brevo), then every
  step passes; results recorded in §10.
- **AC-4.4** (Backups on the server) Given the bucket `award-backups-<suffix>` in `eu-north-1` with the 14-day
  lifecycle rule and an IAM user limited to list, get and put on it, then the first nightly backup appears in the
  bucket, the `backup` service is healthy the next morning, and one restore of it on the server (AC-2.5) succeeds
  with matching counts.
- **AC-4.5** (Cost and uptime) Given the account, then an AWS budget alarm mails at 10 USD of monthly actual cost,
  and a scheduled workflow `uptime` (every 30 minutes, `if: vars.DEMO_HOST != ''`) fails, and so mails me, when the
  discovery document of the public address does not answer 200 within 20 seconds.

### 9.2.5 Award form in steps on phones (SCRUM-136)

- **AC-5.1** (Steps) Given a screen of at most 600 px when the application starts (`NARROW_SCREEN`, read once),
  when `/awards/new` or `/awards/:id/edit` opens, then the form shows three steps with a step header
  "Крок n з 3" / "Step n of 3" and the step name: 1 "Нагорода" / "Award" (recipient, unit, both titles,
  category), 2 "Видача" / "Issue" (awarding organisation, date), 3 "Опис і документи" / "Details and documents"
  (both descriptions, link, documents); only the fields of the current step are rendered.
- **AC-5.2** (Moving) Given a step, then "Далі" / "Next" marks the step's fields touched and moves on only when
  they are valid, otherwise focuses the first invalid field; "Назад" / "Back" moves back without validation; the
  step header is focused after a move; the browser back button leaves the form as before (steps add no history
  entries).
- **AC-5.3** (Save and submit) Given any step, then "Зберегти чернетку" / "Save draft" is available as on the
  single page; "Подати" / "Submit" is shown on step 3 only. When the server refuses a save or submit with field
  errors, the form moves to the first step holding a field in error and shows the errors there.
- **AC-5.4** (Behaviour kept) Given the steps, then draft restore, the offline save and the offline notices, the
  duplicate warning (shown on step 1 under the titles), category suggestions, the returned-award banner, the
  unsaved-changes guard and the document upload of a saved draft behave as on the single page; the form value and
  its dirty state survive moving between steps.
- **AC-5.5** (Wide screens) Given a screen wider than 600 px, then the form is the single page of today, unchanged.
- **AC-5.6** (Lighthouse) Given the Compose build, when `npm run flow:median` runs in `tools/lighthouse`, then the
  median performance of `/awards/new` is recorded in §10 with the Lighthouse version; target 90, accessibility and
  best practices at 90 or above, the initial bundle within the `angular.json` budget.

## 5. Edge cases

| Case | Expected |
|---|---|
| Backup while a user uploads | The dump comes first, the archive second: an object uploaded in between is in the archive without a row; after a restore the document sweep removes it (AC-2.6). An object deleted in between is in the dump's rows but not the archive: the restore reports the object count below the manifest's row-based expectation and the document opens as "not found"; at 04:30 neither is likely |
| Upload to S3 fails (network, credentials) | `backup.sh` retries each upload three times, then exits non-zero without the manifest; the health check turns `unhealthy` after 26 hours; the next night tries again |
| Disk full during a run | The run streams `pg_dump` through `age` into a temporary file in the container's `/tmp` (a tmpfs of 768 MB, 9.2.3) next to the copied documents; at demo scale they stay under 350 MB; a failed write exits non-zero |
| Restore with `app` running | Refused before writing (AC-2.5) |
| Restore of a manifest older than the latest | Allowed; erasure requests that fell due later are carried out by the catch-up (AC-2.6); an erasure that was cancelled after that dump comes back as pending and is carried out at its old date. The restore prints the pending requests whose date has passed so that I can check them before starting `app` |
| Wrong or missing private key | `age` fails; nothing is written; message "cannot decrypt: check the key file" |
| Catch-up while the nightly job runs | The catch-up runs one minute after start; a deploy at 02:30 can overlap the scheduled run. The erasure runner and the retention job already lock their rows (6.1, 6.2), so the second run finds nothing to do |
| Deploy while a backup runs | `up -d` recreates only services whose image or configuration changed; the `backup` service is only recreated when its image changes; a backup cut short leaves no manifest and the next night runs again |
| Deploy with a failing health check | The job fails after 5 minutes and prints logs; the previous images are gone from the running containers, so rollback is a dispatch with the previous tag (AC-3.3) |
| Deploy job without secrets (forks, before 9.2.4) | Skipped by `if: vars.DEMO_HOST != ''` |
| SSH host key changed | `StrictHostKeyChecking=yes` with the pinned key fails the job |
| Server out of memory | Limits stop one container (it restarts), not the host; swap absorbs short peaks |
| Free plan ends (6 months or credits used) | Budget alarm at 10 USD; the server stops with the plan unless the account is upgraded (risk R-1) |
| Phone rotated to landscape during the form | The layout chosen at start stays (`NARROW_SCREEN` read once) |
| Draft restore offered on a narrow screen | The restored value fills all steps; the form opens on step 1 |
| Server field error on a field of step 1 while on step 3 | The form moves to step 1 and focuses that field |
| Offline save on step 2 | Saves the whole form value as on the single page; the notice shows on the current step |

## 6. Dependencies

### Tables

No new table or column. `erasure_requests` (`scheduled_for`, `warning_sent_at`), `retention_runs` and
`document_files` are read by the existing jobs the catch-up calls.

### Endpoints

None new or changed. The smoke test and the uptime check use `/.well-known/openid-configuration`.

### Services

- New `JobCatchUp` (`common/retention`, `@Component`, `ApplicationReadyEvent` plus a delayed task on the Spring
  task scheduler): calls `ErasureJob.run()`, `RetentionJob.run()` and `DocumentSweeper.sweep()` once when
  `app.jobs.catch-up-on-start` is `true`.
- New `infra/backup/`: `Dockerfile`, `backup.sh`, `restore.sh`, `crontab` template, `healthcheck.sh`, `rclone`
  configuration through `RCLONE_CONFIG_*` environment variables.
- Compose: `backup` service in `docker-compose.prod.yml`; a `minio-init` step creating `award-backups` only under
  the `local-mail` rehearsal profile.

### Frontend

- `AwardFormComponent` (template split into three step blocks under `@switch`, a `step` signal, step
  navigation), `award-fields.ts` (field to step map), i18n keys `awards.form.steps.*` in `uk.json` and `en.json`.
- `NARROW_SCREEN` from `shared/touch.ts` (9.2.1).

### External systems

- AWS: EC2 `m7i-flex.large` in `eu-central-1`, Elastic IP, S3 bucket in `eu-north-1` with a lifecycle rule, IAM
  user for the backup, AWS Budgets.
- GitHub: environment `demo`, variables `DEMO_HOST`, `DEMO_USER`, `DEMO_DIR`, `SITE_ADDRESS`, secrets
  `DEMO_SSH_KEY`, `DEMO_KNOWN_HOSTS`; GHCR images (the server signs in to GHCR with a read-only token kept in its
  Docker configuration).
- Brevo SMTP (DEMO_DEPLOYMENT §1), Let's Encrypt through Caddy, `sslip.io`.

## 7. Technical decisions

- **D-1** The award form keeps its single page in 9.2.1. Its eight Material fields cost about 800 ms of blocking
  time in one task; a deferred lower half does not help, because on the Lighthouse phone (412 x 823) the lower
  fields are on screen and render straight after the first ones. Steps on phones follow in 9.2.5.
- **D-2** Status, unit, visibility and delay labels of the award list are styled spans (`.app-chip`, the look of a
  Material chip), not `mat-chip` components: the labels are not interactive.
- **D-3** The shell stays on default change detection: `OnPush` gave no measurable gain, and its template binds
  state of child references (`drawer.opened`, `routerLinkActive`) that does not mark it for check.

AC-1.1 is not met: the scores in §10 stay under 90; 9.2.5 continues it for the form.

- **D-4** (ADR-021) Backups are logical: `pg_dump -Fc` and an archive of the objects through the MinIO API, not
  volume snapshots. They restore across PostgreSQL minor versions and a new `MINIO_KMS_SECRET_KEY`, and the same
  scripts run on the workstation rehearsal. An EBS snapshot would hold the encrypted MinIO volume, readable only
  with that key, and would not expire erased data on a schedule I control.
- **D-5** Encryption with `age` to a public key: the server can write backups but not read them, so a server
  compromise does not open 14 days of history (THREAT_MODEL I-DB-01). The private key lives only in my password
  manager next to `.env.prod`.
- **D-6** A full archive every night instead of an incremental mirror: with the 50 MB per-user quota and tens of
  pilot users the archive stays far under a gigabyte, and every night is self-contained, so the 14-day expiry
  removes an erased person's documents without bookkeeping. Incremental copies wait for real volumes.
- **D-7** Erased and expired data are not removed from existing backups; they expire with them after 14 days
  (within the one month of GDPR Art. 12(3)), and a restore re-applies erasures through the catch-up (AC-2.6).
  PRIVACY_BY_DESIGN's "marked for exclusion, next backup cycle" becomes this rule.
- **D-8** The bucket is in `eu-north-1`, a second EU region of the same account; the backup's IAM user cannot
  delete, and the lifecycle rule does the deleting. A second account or provider would protect against losing the
  AWS account itself; for a demo on credits that is accepted (risk R-3).
- **D-9** The deploy job pins images by the `main-<sha>` tag the build already pushes, written to a separate
  `.env.release` (Compose reads several `--env-file`s), so `.env.prod` on the server holds secrets only and a
  rollback is a dispatch with an older tag.
- **D-10** `m7i-flex.large` (8 GB) instead of the 4 GB `t3.medium` assumed on 2026-10-11: the stack's limits add up
  to more than 4 GB with ClamAV (about 1.2 GB) and the backend (1 to 2 GB), and the flex size is free-plan
  eligible for accounts created after 2025-07-15, so the sign-up credits pay for it.
- **D-11** (9.2.5) Steps are three blocks of one form group under `@switch`, not `mat-stepper`: the stepper renders
  every step's content projection and adds its own animations and header, which is what the step split saves.
- **D-12** (9.2.3) The backend gets a fixed address (`172.26.255.10`, outside the network's dynamic range
  `172.26.0.0/17`) in the production override: the frontend's nginx resolves `app` once at start, so a backend
  recreated by a deploy or a restore came back on another address and every API call answered 502 (seen in the
  9.2.3 rehearsal). A runtime resolver in nginx would break the e2e harness, whose nginx reaches the local backend
  through `--add-host`. The backup tmpfs shrinks from 2 GB to 768 MB so that its memory limit (tmpfs plus
  384 MB for `pg_dump`, `age` and `rclone`) stays inside the 6.5 GB budget; a full tmpfs fails the run with a write
  error before the limit is reached. Plaintext documents stay off the disk, so a disk work directory is no option.
- **D-14** (9.2.3) The deploy job scans the exact `main-<sha>` images it deploys (Trivy, critical, fixable only) and
  stops on a finding, on a push and on a rollback; `security-scan` and the image scan in `docker-build` only report.
  A dispatch runs only from `main`, and the `demo` environment allows `main` only with my approval, so a branch
  cannot reach the deploy key with an edited workflow.
- **D-15** (9.2.6) A critical finding fixed only on a newer major line (two spring-webmvc CVEs fixed in Spring
  Framework 7.0.9, none in 6.2.x) goes into `.trivyignore` with the reason and an expiry date
  (2027-01-31, after the defense) instead of a Spring Boot 4 migration; both need features the application does
  not use (XsltView, SSE view fragments). The deploy scans pass the file explicitly.
- **D-13** (9.2.3) `DEMO_HOST` is a repository variable: a job-level `if` is evaluated before the job enters its
  environment, so a `demo` environment variable would always read empty there and the job would never run. The
  other three variables and both secrets stay in the `demo` environment.

### Proposed deviations

1. PRIVACY_BY_DESIGN §5.2 "Backups: marked for exclusion, next backup cycle" → expiry after 14 days plus the
   restore catch-up (D-7).
2. ADR-021 "Hetzner, 4 vCPU / 8 GB" → AWS EC2 `m7i-flex.large` (2 vCPU / 8 GB) in `eu-central-1`; DEMO_DEPLOYMENT §3
   "4 vCPU / 8 GB" follows (D-10, AC-3.6).
3. ADR-021 "nightly `pg_dump` and a MinIO mirror" → an encrypted archive each night instead of a mirror (D-6).
4. SECURITY_ARCHITECTURE's "AES-256-CBC, separate backup keys" target → `age` (X25519 and ChaCha20-Poly1305) with
   one key pair for the demo (D-5).
5. Port 22 open to every address (key sign-in only): GitHub runners have no fixed addresses. Restricting it would
   need a self-hosted runner or AWS Systems Manager instead of SSH (risk R-4).

## 8. Contract

### 8.1 OpenAPI changes

None.

### 8.2 Migration outlines

None.

### 8.3 Properties and environment

| Name | Default | Story | Meaning |
|---|---|---|---|
| `app.jobs.catch-up-on-start` | `false` (`true` in the `production` profile of `application.yaml`) | 9.2.2 | Run erasure, retention and sweep once after start |
| `app.jobs.catch-up-delay` | `PT1M` | 9.2.2 | Delay of that run after `ApplicationReadyEvent` |
| `BACKUP_CRON` | `30 4 * * *` | 9.2.2 | Backup time, `Europe/Kyiv` |
| `BACKUP_REMOTE` | - (required) | 9.2.2 | rclone path, e.g. `s3:award-backups-x` |
| `RCLONE_CONFIG_S3_*` | - | 9.2.2 | Provider, region, key, secret, endpoint of the remote |
| `BACKUP_AGE_RECIPIENT` | - (required) | 9.2.2 | `age1...` public key |
| `BACKUP_KEEP_DAYS` | `14` | 9.2.2 | Rehearsal deletion window (S3 uses the lifecycle rule) |
| `DEMO_HOST` | - | 9.2.3 | GitHub repository variable; the deploy job is skipped while it is empty (D-13) |
| `DEMO_USER`, `DEMO_DIR`, `SITE_ADDRESS` | - | 9.2.3 | GitHub environment `demo` variables |
| `DEMO_SSH_KEY`, `DEMO_KNOWN_HOSTS` | - | 9.2.3 | GitHub environment `demo` secrets |

## 9. Test plan

| AC | Unit | IT | FT / script | E2E | Manual (§10) |
|---|---|---|---|---|---|
| AC-2.1 to AC-2.3 | - | - | `infra/backup/test.sh`: builds the image, runs one backup against the Compose MinIO, checks the three files, the manifest hashes and that `/tmp` is empty; a run with a wrong remote exits non-zero without a manifest | - | 4, 5 |
| AC-2.4 | - | - | `test.sh`: a file dated 15 days back is deleted by the rehearsal run | - | 5 |
| AC-2.5 | - | - | `test.sh`: restore into an empty database; wrong key and changed file refused | - | 6, 7 |
| AC-2.6 | `JobCatchUpTest` (order, disabled, one failing job does not stop the others) | `JobCatchUpIT` (a due erasure request is carried out after start with the property on) | - | - | 7 |
| AC-2.7, AC-2.9 | - | - | - | - | doc review |
| AC-2.8 | - | - | - | - | 4 to 8 |
| AC-3.1 to AC-3.3 | - | - | `actionlint` on the workflow in the gate's static step | - | 12, 13 (9.2.4) |
| AC-3.4, AC-3.5 | - | - | `docker compose config` check in `test.sh` (every service has `logging` and `mem_limit`) | - | 9 |
| AC-3.6 | - | - | `puml-check.ps1` if the deployment diagram changes | - | doc review |
| AC-4.1 to AC-4.5 | - | - | - | - | 10 to 16 |
| AC-5.1, AC-5.2 | `AwardFormComponent` spec: steps at narrow width, Next with invalid fields, Back, focus | - | - | `award-form-steps.spec.ts` (Playwright, 360 px): create a draft through three steps | 17, 18 |
| AC-5.3 | spec: server field error on step 1 while on step 3 | - | - | same spec: submit on step 3 | 18 |
| AC-5.4 | existing specs run at narrow width too (draft restore, offline, duplicates, suggestions, guard) | - | - | existing award form specs stay green at 1280 px | 19 |
| AC-5.5 | spec: wide screen renders all fields | - | - | existing specs | 17 |
| AC-5.6 | - | - | `npm run flow:median` | - | 20 |

## 10. Manual verification

Preconditions for 9.2.1: Compose stack built from the branch (`docker compose up -d --build frontend app`), at
least 4 GB of free memory (below that the runs vary by 30 points), `tools/lighthouse` installed (`npm ci`).

1. `npm run flow` in `tools/lighthouse`, three times. Expected: per page the median performance, accessibility and
   best practices; record them with the Lighthouse version. (AC-1.1)
2. Chrome DevTools, device toolbar at 360 px, `http://localhost/awards` as `employee.fmi`. Expected: the search
   field and a "Фільтри" button; the button opens the status, category and date filters; with a status chosen
   the button reads "Фільтри: 1"; the paginator offers 10 and shows 10 a page. At 1280 px every filter is shown
   and no button. (AC-1.2, AC-1.3)
3. Same list in English. Expected: dates as `09/10/2026`, the button reads "Filters". (AC-1.4)

Preconditions for 9.2.2 and 9.2.3: the rehearsal stack of DEMO_DEPLOYMENT §2 (`P=...` as there) with the demo seed,
`BACKUP_REMOTE=s3:award-backups` on the Compose MinIO, a key pair from `age-keygen -o build/backup.key` (public key
in `BACKUP_AGE_RECIPIENT`), `BACKUP_CRON` left at its default. Before step 4, a `PENDING` erasure request for
`secretary.fmi@demo.example` (no legal hold) inserted through psql with `scheduled_for` ten minutes ahead, so the
dump holds it and it falls due before the restore; the nightly job does not run in between.

4. `$P exec backup backup.sh now`, then list the remote (`$P exec backup rclone ls s3:award-backups`). Expected:
   `db-<T>.dump.age`, `documents-<T>.tar.age`, `manifest-<T>.json`; the manifest shows the five demo accounts and
   the object count; `$P exec backup ls /tmp` is empty. (AC-2.1, AC-2.2)
5. `$P ps backup`. Expected: `healthy`. Copy a file named `db-<15 days ago>.dump.age` into the remote and run step 4
   again. Expected: that file is gone. (AC-2.3, AC-2.4)
6. Sign in at `https://localhost` as `employee.fmi@demo.example`, create the draft "Restore drill" with a PDF.
   (preparation for AC-2.8)
7. `$P stop app backup`, then `$P run --rm -v ./build/backup.key:/run/backup.key:ro backup restore.sh <T>`. Expected: hash
   check passed, the database and object counts equal the manifest, the pending request of step 6 is listed as
   due. Run it once with `-v ./build/other.key:...` first. Expected: "cannot decrypt: check the key file", nothing
   changed. (AC-2.5)
8. `$P start app backup`; wait two minutes; `$P logs app | grep -i catch-up`. Expected: three catch-up lines; "Restore
   drill" is not in the employee's list; `secretary.fmi` shows as "Erased #<id>" in the admin's user list; the
   award with the seed document opens its PDF. (AC-2.6, AC-2.8)
9. `docker stats --no-stream` sampled every two seconds during steps 4 to 8, the DEMO_DEPLOYMENT scenario and a
   forced signature reload (`docker exec award-clamav clamdscan --reload`); record the peaks. `infra/backup/test.sh`
   passes its AC-3.4/AC-3.5 check (every service with `json-file` 10m × 5 and a `mem_limit`, 6.5 GB at most in all).
   Expected: limits at about 1.5 times the peaks; `docker inspect` shows no `OOMKilled` after the same steps with
   the limits on; `$P stop app`, `$P start app` and `$P up -d --force-recreate app` keep `/login` at 200. (AC-3.4,
   AC-3.5, D-12)

Preconditions for 9.2.4: the EC2 server of the runbook §3, `.env.prod` there, the GitHub environment `demo` filled,
the bucket and its IAM user, a mailbox I can read for the Brevo test.

10. `ssh -o PasswordAuthentication=yes ubuntu@<ip>` with no key. Expected: refused (`Permission denied
    (publickey)`). `sudo swapon --show` shows 2 GB. (AC-4.1)
11. Merge a docs-only change to `main`. Expected: `deploy-demo` green, its smoke step prints the issuer
    `https://<ip>.sslip.io`. (AC-3.1, AC-3.2, AC-4.2)
12. Dispatch the workflow with `image_tag` of the previous deploy. Expected: green; `docker inspect award-backend`
    shows that tag; dispatch again with the latest tag. (AC-3.3)
13. Run the DEMO_DEPLOYMENT manual verification steps 2 to 10 against `https://<ip>.sslip.io`; step 6 sends the
    reset mail to my own mailbox registered as a `chnu.edu.ua`-domain account or, if none is available, to a demo
    account changed to my address through psql. Expected: every step as written; a valid Let's Encrypt
    certificate. (AC-4.3)
14. The next morning: the bucket lists the night's three files; `docker compose ps backup` is `healthy`. (AC-4.4)
15. On the server: `docker compose stop app`, `restore.sh` of that night with the key copied in for the run and
    deleted afterwards, `docker compose start app`. Expected: counts match the manifest; sign-in works. (AC-4.4)
16. AWS Budgets shows the 10 USD alarm; GitHub Actions shows `uptime` runs every 30 minutes, green. (AC-4.5)

Preconditions for 9.2.5: Compose stack from the branch, `employee.fmi` and `secretary.fmi` (local seed).

17. DevTools at 360 px, `http://localhost/awards/new` as `employee.fmi`. Expected: "Крок 1 з 3 · Нагорода" with the
    titles and the category only; at 1280 px the full single page. (AC-5.1, AC-5.5)
18. At 360 px: "Далі" with empty titles. Expected: stays on step 1, the title field is focused with its error. Fill
    step 1, "Далі", fill step 2, "Далі", fill step 3, "Подати". Expected: the submitted page, as on the single page.
    (AC-5.2, AC-5.3)
19. At 360 px: type a title equal to an existing award's (duplicate warning on step 1); go to step 3; switch the
    network to offline in DevTools; "Зберегти чернетку". Expected: the offline notice; back online, the draft
    saves; navigate away with a change: the unsaved-changes dialog. Reload `/awards/new`: the restore offer, and
    the restored value on all steps. (AC-5.4)
20. `npm run flow:median` in `tools/lighthouse`. Expected: median performance of `/awards/new` recorded. (AC-5.6)

### Detours

- **Backup run killed mid-way** (`$P restart backup` during step 4). Expected: no manifest for that time; the next
  `backup.sh now` writes a complete set; a restore of the cut-short time is refused ("no manifest").
- **Restore of a missing time** (`restore.sh 19990101T000000`). Expected: "no manifest", nothing written.
- **Restore twice in a row** (step 7 repeated). Expected: the same counts; no duplicate rows (`--clean`).
- **Restore with `app` running** (skip `$P stop app`). Expected: refused before writing (AC-2.5).
- **Backend restart without a restore** (`$P restart app`). Expected: three catch-up lines, nothing erased, one new
  `retention_runs` row with nothing deleted.
- **Catch-up off** (`app.jobs.catch-up-on-start=false` in the development stack). Expected: no catch-up lines.
- **Revoked mid-flow: erasure cancelled after the dump** (cancel the request of step 6 after step 4, then restore).
  Expected: the restore lists it as due; I cancel it again through psql before `$P start app`; the account stays.
- **Revoked mid-flow: legal hold placed after the dump** (hold on `secretary.fmi` after step 4, restore). Expected:
  the restored database has no hold, the catch-up erases the account; the restore output lists due requests so
  the hold can be placed again before the start; recorded as risk R-5.
- **Deploy while a user signs in** (open the sign-in form, deploy, submit). Expected: the backend restarts; the
  submit fails once with the error page or a new sign-in, then works; no data lost.
- **Second deploy of the same tag**. Expected: no container recreated; smoke test green.
- **Server reboot** (`sudo reboot`). Expected: every container back by `restart: unless-stopped`; the catch-up runs.
- **Token expiry in an open tab across a deploy**. Expected: the signing key is unchanged, so tokens stay valid;
  the next call refreshes as usual.
- **Hidden in state (form steps)**: "Подати" on steps 1 and 2. Expected: absent. On a returned award at 360 px: the
  banner above step 1, "Подати" on step 3 only.
- **Form reloaded on step 3**. Expected: the form opens on step 1 with the restore offer; nothing lost.
- **Back button on step 2**. Expected: leaves the form through the unsaved-changes guard, as on the single page.
- **Direct URL `/awards/<id>/edit` of someone else's award at 360 px**. Expected: the existing denial, no steps.
- **Revoked mid-flow (form)**: `secretary.fmi` on step 3 of a draft for a unit; the admin revokes the secretary
  role; "Зберегти чернетку". Expected: the existing refusal of a lost unit scope (`validation-failed` on create
  and update), shown on step 1 at the unit field; other secretaries of the faculty are unaffected.

### Run notes

Lighthouse 12.8.2, mobile profile, simulated throttling, Compose stack with the branch build, 4.4 GB free,
median of three runs (performance / accessibility / best practices):

| Page | Before (develop) | After (9.2.1) |
|------|------------------|---------------|
| `/login` | 100 / 100 / 100 | 100 / 100 / 100 |
| `/awards/new` | 79 / 100 / 100 | 79 / 100 / 100 |
| `/awards` | 76 / 100 / 96 | 79 / 100 / 96 |

Single runs after the change: `/awards` 77, 79, 82; `/awards/new` 74, 79, 80. Target 90 not reached: the gain on
`/awards` (blocking time of the list tasks from about 1 200 to 800 ms in the profile) is within three points of the
spread between runs. `/awards` is limited by the rendering of the filter bar and the rows; `/awards/new` by the
form (D-1, 9.2.5). The best-practices 96 on `/awards` is a 504 the
service worker returns when `/brand/brand.json` exceeds its 3 s freshness timeout during a throttled run; it does
not occur on every run.

**9.2.2 drill, 2026-11-10** (rehearsal stack, demo seed, branch build; steps 4 to 8 and the detours named):

| Step | Result |
|------|--------|
| Pre | Erasure request #1 for `secretary.fmi` (user 4), due three minutes after the dump. The seed has no awards, so the award "Drill seed award" with `drill.pdf` was added before step 4 |
| 4 | Pass: `backup.sh now` wrote `db-`, `documents-` and `manifest-20261110T232309` (7 users, 1 award, 1 object); `/tmp` empty |
| 5 | Pass: `healthy`; a `db-` file dated 15 days back was expired by the next run |
| 6 | Pass: draft "Restore drill" with a PDF added (award 2) |
| 7 | Pass after a fix: the first restore stopped at `pg_restore --clean` ("cannot drop inherited constraint `audit_logs_default_pkey1`") and left the database unchanged; with the schema replay the hashes and key were checked, the restored counts equalled the manifest (7 / 1 / 1), request #1 was listed as due. Wrong key: "cannot decrypt: check the key file"; `app` running: refused (11 sessions); `19990101T000000`: "no manifest"; a second restore gave the same counts |
| 8 | Pass: three catch-up lines at 21:29:00 UTC, 91 s after the start; user 4 is "Erased #4", request `COMPLETED`; "Restore drill" not in the list; the PDF of "Drill seed award" opens |

The browser of the MCP refuses the local certificate of Caddy, so steps 6 and 8 ran through a Playwright script
with certificate errors ignored; on this workstation `localhost` resolves to `::1` first, which Docker Desktop does
not forward for port 443, so the first request may time out once.

**9.2.3 memory, 2026-11-11** (rehearsal stack, demo seed, branch build, Docker Desktop VM of 4 GB): a Playwright
script signed in four sessions (admin, secretary, two employee) and opened ten pages five times each, the employee
sessions uploading two PDFs (9 MB and 2 MB) a round; then `backup.sh now`, a restore of that time, and a forced
ClamAV reload. First run without limits, second with them (11 uploads scanned and stored, 19 objects backed up):

| Service | Peak MiB | Limit MB | Note |
|---------|---------:|---------:|------|
| `clamav` | 1 932 | 2 944 | Concurrent signature reload; 1 664 at start, about 950 at rest |
| `app` | 666 | 1 024 | `MaxRAMPercentage=60`: heap at most 614 MB |
| `minio` | 327 | 512 | During the restore |
| `backup` | 203 | 1 152 | Process plus staged files; set by the 768 MB tmpfs plus 384 MB (D-12); restore run 80 |
| `postgres` | 160 | 256 | |
| `caddy` | 49 | 128 | |
| `mailpit` | 17 | 64 | Rehearsal only |
| `frontend` | 12 | 64 | |
| `redis` | 10 | 64 | |
| `minio-init`, `backup-bucket-init` | - | 128 each | One-shot, ends before a sample |

Sum 6 464 MB (6 272 MB on the server, without the two rehearsal-only services). No container was `OOMKilled` or
restarted with the limits on. Under the first run a restore left `/login` at 502: the restore container took the
backend's address while `app` was stopped, and nginx kept the old one (D-12); with the fixed address `/login` stays
at 200 after a restore and after `up -d --force-recreate app`.

## 11. Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-1 | The free plan ends (6 months from sign-up or when the credits are used) before the defense | Low | High | Budget alarm at 10 USD; the defense is about 2026-12-15; upgrade the account if needed |
| R-2 | Up to one day of data lost in a disaster | Medium | Medium | Accepted for the demo; WAL archiving is production work |
| R-3 | The AWS account itself lost or locked: server and bucket together | Low | High | `.env.prod`, keys and the latest manifest set also downloaded to my workstation before the defense |
| R-4 | SSH open to every address | Medium | Medium | Key sign-in only, `unattended-upgrades`; Systems Manager as the production alternative |
| R-5 | A restore drops a legal hold or a cancellation made after the dump | Low | Medium | `restore.sh` lists due requests before the start; I recheck holds before `app` starts |
| R-6 | Brevo mail from an unverified domain lands in spam | Medium | Low | Demo accounts use `@demo.example` (no mail); a verified sender; a domain with SPF and DKIM stays out of scope |
| R-7 | Memory limits too tight cause restarts under the demo | Medium | Medium | Limits from measured peaks with 1.5 headroom (AC-3.5); swap; `docker events` checked after the demo rehearsal |
| R-8 | The award form steps do not reach Lighthouse 90 | Medium | Low | Result recorded either way (AC-5.6); the remaining gap goes to the thesis as a measured limit |

## 12. Definition of Done

- `.\tools\gate.ps1` green: unit, IT, FT, JaCoCo ≥ 85 %, Checkstyle, PMD and SpotBugs at 0, frontend lint and tests;
  `infra/backup/test.sh` green for 9.2.2 and 9.2.3; Playwright for 9.2.5.
- The manual steps of each story run and recorded in §10; the 9.2.4 steps on the public address.
- Docs updated in the story that changes them: DEMO_DEPLOYMENT §3 and §4, ADR-021 addendum, PRIVACY_BY_DESIGN §5.2,
  SECURITY_FRAMEWORK I004, the 6.1 and 6.2 PRD backup lines, `CHANGELOG.md [Unreleased]`, the epic tracker and
  `BACKLOG.md`.

# Demo Deployment Runbook

Single-host deployment for the thesis defense (ADR-021, addendum 2026-11-11): Docker Compose with
`docker-compose.prod.yml` on one AWS EC2 instance, Caddy for TLS, Brevo for mail, backups to S3 in a second region.
The same configuration is rehearsed on a workstation with `SITE_ADDRESS=localhost`.

---

## 1. Configuration

Copy `.env.prod.example` to `.env.prod` (ignored by git) and fill it in.

| Variable | Value |
|---|---|
| `SITE_ADDRESS` | Domain, `<server-ip>.sslip.io`, or `localhost` for a rehearsal; also the host name in the SMTP greeting |
| `BACKEND_IMAGE`, `FRONTEND_IMAGE` | Not in `.env.prod` on the server: the deploy job writes them to `.env.release` (GHCR `main-<sha>` tags); `award-backend:prod` / `award-frontend:prod` when built locally |
| `POSTGRES_*`, `REDIS_PASSWORD`, `MINIO_ROOT_*` | Generated random values (`openssl rand -base64 24`) |
| `MINIO_APP_USER`, `MINIO_APP_PASSWORD` | The backend's MinIO account, created by the one-shot `minio-init` service with rights on the objects of `award-documents` only (no bucket settings, policies or admin); the root account stays with MinIO |
| `MINIO_KMS_SECRET_KEY` | Encryption key of the document objects, see below |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Brevo SMTP relay: `smtp-relay.brevo.com`, `587`, the SMTP login and key from the Brevo account |
| `MAIL_FROM` | A sender verified in Brevo |
| `ALLOWED_EMAIL_DOMAINS` | Domains accepted at registration (default `chnu.edu.ua`) |
| `APP_BRAND` | Brand of the sign-in and error pages (`chnu` default, `neutral`); keep it equal to the `id` in the frontend's `brand.json` |
| `JWK_KEY_ID`, `JWK_PRIVATE_KEY`, `JWK_PUBLIC_KEY` | Token signing key, see below |
| `FLYWAY_LOCATIONS`, `DEMO_PASSWORD_HASH` | Demo accounts, see below |

**Signing key.** PEM bodies on one line (the loader ignores the header lines and whitespace):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwk.pem
openssl pkey -in jwk.pem -pubout -out jwk.pub.pem
grep -v -- ----- jwk.pem | tr -d '\n'        # JWK_PRIVATE_KEY
grep -v -- ----- jwk.pub.pem | tr -d '\n'    # JWK_PUBLIC_KEY
```

Changing the key signs every user out; keep it for the life of the environment.

**Document encryption key.** MinIO encrypts every document object with one static key (SSE-S3). The value is a
name and 32 random bytes in base64:

```bash
echo "award-key:$(openssl rand -base64 32)"
```

Losing or changing the key makes every stored document unreadable: keep a copy with the backups, never in the
repository. The MinIO image is `cgr.dev/chainguard/minio` (MinIO publishes no images of its own any more); it runs
as root so that it owns its volume.

**Upload limits.** nginx accepts request bodies up to 11 MB on the API paths and Spring up to 10 MB per file and
11 MB per request; Caddy sets no body limit. A larger upload is answered by nginx with the same
`urn:awards:problem:file-too-large` problem details as the application. Per user, the application accepts at most
50 MB of documents and 20 uploads a minute (`APP_DOCUMENTS_USER_QUOTA`, `APP_DOCUMENTS_UPLOAD_RATE`).

**Request limits.** Per client address: 600 requests a minute to the sign-in and token endpoints
(`AUTH_RATE_LIMIT_PER_MINUTE`) and 120 to the public API (`PUBLIC_RATE_LIMIT_PER_MINUTE`); per account, 10 sign-in
posts a minute (`AUTH_ACCOUNT_RATE_LIMIT_PER_MINUTE`); per signed-in user, 600 API requests a minute
(`API_RATE_LIMIT_PER_MINUTE`) and 5 reports or exports a minute (`EXPORT_RATE_LIMIT_PER_MINUTE`). The address
budgets assume that the proxy passes the client address in `X-Forwarded-For`; refusals are counted in
`award_rate_limited_total`.

**Malware scanner.** The `clamav` service (`clamav/clamav:stable`, no published port) scans every upload; the
backend waits for it to be healthy, which takes one to three minutes after a start while the signatures load, and
refuses uploads while it is down. It needs about 1 GB of memory, up to 1.9 GB while it reloads new signatures
next to the old ones (its limit is 2.9 GB, section 3), and keeps its signatures in the `clamav_data`
volume; `freshclam` inside the container updates them every two hours, so the server needs outbound HTTPS. On the
very first start the full signature download can take longer than the health check allows (about 5.5 minutes);
if `docker compose up` then reports `clamav` unhealthy, wait until `docker compose ps clamav` shows `healthy` and
run `docker compose up -d` again.

**Demo accounts.** `db/seed/demo` creates `admin`, `rector`, `dean.fmi`, `secretary.fmi` and `employee.fmi`
at `@demo.example` (a reserved domain, so no mail reaches a real mailbox) with one shared password. Set
`FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed/demo` and the bcrypt hash of the password, in single
quotes because it contains `$`:

```bash
docker run --rm httpd:2.4-alpine htpasswd -nbB -C 12 "" 'the-demo-password' | tr -d ':\n'
```

Without `DEMO_PASSWORD_HASH` the seed creates nothing. The seed only inserts: a new hash later does not change
the password of accounts that already exist.

**University brand.** The frontend image ships the ChNU brand (`brand/brand.json` with `"id": "chnu"` and its
logo). Another university mounts its own file and logo read-only over the image's, for example in an override of
the `frontend` service:

```yaml
    volumes:
      - ./brand/brand.json:/usr/share/nginx/html/brand/brand.json:ro
      - ./brand/logo.svg:/usr/share/nginx/html/brand/<id>/logo.svg:ro
      - ./brand/manifest.webmanifest:/usr/share/nginx/html/brand/manifest.webmanifest:ro
      - ./brand/icons:/usr/share/nginx/html/brand/icons:ro
```

The manifest and the icons (`icon-192.png`, `icon-512.png`, `icon-maskable-512.png`, `apple-touch-icon.png`)
give the installed app its name and icon; the neutral set is in the image under `brand/neutral/`
(`frontend/public/brand/neutral/`) and is copied with the brand file. No rebuild is needed: the service worker
fetches `/brand/**` from the network first.

**Service worker.** nginx serves `ngsw-worker.js`, `ngsw.json` and `safety-worker.js` with `Cache-Control:
no-cache`, so a new image reaches installed apps on their next check. If a release leaves clients stuck on a broken
worker, the kill switch is to serve `safety-worker.js` as `ngsw-worker.js` (or remove `ngsw.json`) in the running
container: the worker unregisters itself and the app works without it (ADR-013, addendum 2026-10).

The `id` must name a theme compiled into the image (`chnu`, `neutral`); anything else falls back to the neutral
theme. The sign-in and error pages come from the backend and take their brand from `APP_BRAND` (same ids, logo
built into the backend image). Format and rules: [UI guidelines](../frontend/UI_GUIDELINES.md) §1 and §7.

---

## 2. Local rehearsal

The rehearsal uses its own project name, so its volumes are separate from the development stack. The container
names are shared, so the development stack is stopped first (its data stays in its volumes).

```bash
docker compose stop && docker compose rm -f
P="docker compose -p award-prod -f docker-compose.yaml -f docker-compose.prod.yml --env-file .env.prod --profile local-mail"
$P build app frontend
$P up -d --wait
```

For the rehearsal, `.env.prod` uses `SITE_ADDRESS=localhost`, `SMTP_HOST=mailpit`, `SMTP_PORT=1025`, any
`SMTP_USERNAME`/`SMTP_PASSWORD`, and `SMTP_STARTTLS_REQUIRED=false`. Caddy issues a certificate from its own
local authority, so the browser shows a certificate warning once.

Afterwards: `$P down -v` (removes only the rehearsal volumes), then `docker compose up -d` for development.

---

## 3. Server deployment

**Server.** One EC2 `m7i-flex.large` (2 vCPU, 8 GB) in `eu-central-1`, Ubuntu 24.04 LTS, an encrypted 40 GB gp3
volume, an Elastic IP (`SITE_ADDRESS=<ip>.sslip.io`), a security group with 80 and 443 open and 22 for key sign-in
only. Install Docker Engine with the Compose plugin and a 2 GB swap file, sign Docker in to GHCR with a read-only
token (`docker login ghcr.io`), create the deploy directory (`DEMO_DIR`) and put `.env.prod` there (section 1).
Set up the backup bucket and its account (section 4) before the first start.

**Deploy job.** `deploy-demo` in `.github/workflows/ci-cd.yml` runs after every push to `main` whose images were
built and scanned. It needs:

| Where | Name | Value |
|---|---|---|
| Repository variable | `DEMO_HOST` | The Elastic IP; while it is empty the job is skipped (a job condition cannot read environment variables) |
| Environment `demo`, variables | `DEMO_USER`, `DEMO_DIR`, `SITE_ADDRESS` | SSH user, deploy directory, the public address |
| Environment `demo`, secrets | `DEMO_SSH_KEY` | Private key of a key pair used only for deploys, its public key in `~/.ssh/authorized_keys` of `DEMO_USER` |
| | `DEMO_KNOWN_HOSTS` | `ssh-keyscan -t ed25519 <ip>`, checked against the instance's console output; a changed host key fails the job |

In the repository settings, the `demo` environment allows deployments from `main` only and requires my approval,
so a workflow edited on another branch never receives the deploy key.

The job scans the two `main-<sha>` images it is about to deploy with Trivy and stops on a critical vulnerability
that has a fix (also on a rollback, so an old tag with a known hole is refused). A finding whose fix exists only on a
newer major line than the one in use is accepted in `.trivyignore` with the affected feature, the reason the
application is not exposed and an expiry date; after that date the scan fails again. It then copies
`docker-compose.yaml`, `docker-compose.prod.yml`, `infra/caddy/Caddyfile`, `infra/minio/` and `infra/backup/` to
`DEMO_DIR`, writes `.env.release` with the image names, then runs `pull`,
`build backup` and `up -d --wait` with `--env-file .env.prod --env-file .env.release`. Its smoke test waits up to
five minutes for `https://<SITE_ADDRESS>/.well-known/openid-configuration` to answer with the issuer
`https://<SITE_ADDRESS>` and fails when a service other than `minio-init` is unhealthy or exited; a failure prints
the last 50 log lines of those services. On the very first start the ClamAV signature download can outlast the
wait (section 1): run the job again once `clamav` is healthy.

**Rollback.** Actions → CI/CD Pipeline → Run workflow on `main` with `image_tag` set to an earlier `main-<sha>`
(the tags of earlier runs are in GHCR): the build and test jobs are skipped and the job deploys that tag; a run
started from another branch is skipped. Database
migrations do not roll back; a tag older than a migration needs the restore of section 4.

**Limits.** Every service logs through `json-file` with `max-size: 10m` and `max-file: 5`, so its logs stay under
about 50 MB. Each has a `mem_limit` of about 1.5 times its peak measured on the rehearsal stack (Feature 9.2 PRD
§10 run notes), rounded up to 64 MB steps:

| Service | Limit | | Service | Limit |
|---|---|---|---|---|
| `clamav` | 2 944 MB | | `postgres` | 256 MB |
| `backup` | 1 152 MB (768 MB tmpfs) | | `caddy`, `minio-init` | 128 MB |
| `app` | 1 024 MB, `MaxRAMPercentage=60` | | `frontend`, `redis` | 64 MB |
| `minio` | 512 MB | | | |

6 272 MB in all, which leaves about 1.7 GB and the swap to the host on an 8 GB instance. A container that exceeds
its limit is killed and restarted (`docker inspect -f '{{.State.OOMKilled}}'`); raise that one limit in
`docker-compose.prod.yml` and redeploy. The backend has a fixed address in the Compose network, so the frontend
proxy keeps reaching it after a deploy or a restore recreates the container. The backup stages the copied
documents and their archive in a 768 MB tmpfs (so plaintext never reaches the disk), which holds a document store
of about 350 MB; beyond that the run fails with a write error and the health check turns `unhealthy` the next
day: then raise the tmpfs and the limit together.

**Runs.** Run the manual verification below against the public address, and the drill of section 4 once. Run
one backend instance: the hourly overdue job is a plain `@Scheduled` task without a lock, so a second instance
would send the overdue notices twice.

---

## 4. Backups and restore

The `backup` service of `docker-compose.prod.yml` (image from `infra/backup/`) runs `backup.sh` daily at
`BACKUP_CRON`, 04:30 Kyiv time by default, after the erasure job (02:30) and the retention job (04:00). A run
uploads `db-<T>.dump.age` (`pg_dump -Fc`), then `documents-<T>.tar.age` (every object of `award-documents`, read
through the MinIO API with the backend's own account), then `manifest-<T>.json` (sizes, SHA-256 of both files,
counts of users, awards and objects, the backend image). Both files are encrypted with `age` to a public key; the
server cannot read them. A run that fails at any point uploads no manifest. The health check turns `unhealthy` when
no run succeeded in the last 26 hours (`docker compose ps backup`).

| Variable | Server | Rehearsal |
|---|---|---|
| `BACKUP_AGE_RECIPIENT` | public key (`age1...`) | same |
| `BACKUP_REMOTE` | `s3:<bucket>` | `s3:award-backups` (created by `backup-bucket-init`, profile `local-mail`) |
| `BACKUP_KEEP_DAYS` | `0` (the lifecycle rule expires) | `14` (`backup.sh` deletes older files itself) |
| `RCLONE_CONFIG_S3_PROVIDER`, `_REGION`, `_ENDPOINT` | `AWS`, `eu-north-1`, empty | `Minio`, any, `http://minio:9000` |
| `RCLONE_CONFIG_S3_ACCESS_KEY_ID`, `_SECRET_ACCESS_KEY` | the backup account | the MinIO root account |
| `BACKUP_CRON` | `30 4 * * *` | same |

**Bucket and account.** One S3 bucket in `eu-north-1` (a second EU region), public access blocked, default
encryption on, and a lifecycle rule that expires every object 14 days after its upload. The backup's IAM user has
one policy: `s3:ListBucket` on the bucket and `s3:GetObject`, `s3:PutObject` on its objects; no delete. A stolen
server key can add files but neither read old ones in clear nor remove them.

**Keys apart.** `age-keygen -o backup.key` on my workstation; the public key goes into `.env.prod`, the file
`backup.key` into my password manager next to `.env.prod` and a copy of `MINIO_KMS_SECRET_KEY`, and never to the
server or the bucket. The archive holds the plain document content (inside `age`), so a restore to a new server
works with a new `MINIO_KMS_SECRET_KEY`.

**Erased and expired data.** Backups are never edited: an erased account or an expired record leaves every backup
at most 14 days after the nightly job removed it. A restored database may hold erasure requests that fell due after
its dump; with `app.jobs.catch-up-on-start` (on in the `production` profile) the backend runs the erasure job, the
retention job and the document sweep one minute after every start, so they are carried out before the next night.

**Restore** (`P` as in section 2, or without `-p`/`--profile` on the server):

```bash
$P exec backup rclone lsf s3:<bucket> | grep manifest     # pick the time T
$P stop app backup
$P run --rm -v ./backup.key:/run/backup.key:ro backup restore.sh <T>
$P start app backup                                        # the catch-up runs one minute later
```

`restore.sh` refuses before writing anything when the manifest is missing, a file's SHA-256 differs from the
manifest, the key cannot decrypt ("cannot decrypt: check the key file") or `app` still holds database sessions. It
restores the database in one transaction (it drops and recreates the schema `public` and replays the dump through
`psql --single-transaction`, since `pg_restore --clean` cannot drop the inherited keys of the audit partitions;
a failure leaves the database as it was), replaces the objects of
`award-documents` with those of the archive, prints the restored counts beside the manifest's, and lists the pending
erasure requests whose date has passed. An erasure cancelled or a legal hold placed after the dump is not in the
restored database: check that list and repeat such a cancellation or hold through psql before `$P start app`.
Delete `backup.key` from the server afterwards.

The manifest is not signed: the backups are written on the server, so a signing key would sit there too. Someone
holding the backup account's key could therefore add a forged backup encrypted to the public key, though not read
or remove the real ones. Restore only a time I picked from the nightly runs and compare the printed counts with the
manifest before starting `app`.

**Drill** (once before the defense, and after any change of `infra/backup/`): the steps 4 to 8 of the Feature 9.2
manual verification on the rehearsal stack: one backup, a new draft with a PDF, an erasure due after the dump, the
restore, then the start with the catch-up. `infra/backup/test.sh` checks the scripts against the development stack
(`docker compose up -d postgres minio`).

---

## Manual verification

Rehearsal on a workstation, `.env.prod` as in section 2 with the demo seed enabled and the password
`Demo-pass-2026`.

1. Run the commands in section 2. Expected: all containers healthy; `docker ps` shows published ports only for
   `award-caddy` (80, 443) and `award-mailpit` (1025, 8025).
2. Open `http://localhost`. Expected: redirect to `https://localhost`; after accepting the certificate warning
   the application loads.
3. Open `https://localhost/.well-known/openid-configuration`. Expected: `"issuer":"https://localhost"`.
4. Sign in as `admin@demo.example` / `Demo-pass-2026`. Expected: the home page; the browser address stays on
   `https://localhost` through the whole sign-in (never `http://` or `:80`).
5. In the developer tools, Application → Cookies. Expected: `JSESSIONID` is `Secure` and `HttpOnly`.
6. Sign out, choose "Forgot password", enter `employee.fmi@demo.example`. Expected: the message arrives in
   Mailpit at `http://localhost:8025`, sent from `MAIL_FROM`.
7. Open `https://localhost/swagger-ui/index.html`. Expected: 404, not Swagger UI (API documentation is
   disabled in production).
8. `docker logs award-backend | grep -c '"level":"DEBUG"'`. Expected: `0`.
9. `docker exec award-redis redis-cli ping`. Expected: `NOAUTH Authentication required.`
10. `curl -k https://localhost/api/v1/public/achievements` without a token. Expected: 200 and a JSON page; the answer
    carries `Cache-Control: no-store`. The public limit counts requests per client address in Redis (120 a minute
    by default); it is not applied while Redis is down.
11. Clean up as in section 2. Expected: `docker compose up -d` brings the development stack back with its data.

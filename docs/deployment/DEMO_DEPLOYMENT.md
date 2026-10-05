# Demo Deployment Runbook

Single-host deployment for the thesis defense (ADR-021): Docker Compose with `docker-compose.prod.yml`, Caddy
for TLS, Brevo for mail. The server is rented shortly before the defense; until then the same configuration is
rehearsed on a workstation with `SITE_ADDRESS=localhost`.

---

## 1. Configuration

Copy `.env.prod.example` to `.env.prod` (ignored by git) and fill it in.

| Variable | Value |
|---|---|
| `SITE_ADDRESS` | Domain, `<server-ip>.sslip.io`, or `localhost` for a rehearsal |
| `BACKEND_IMAGE`, `FRONTEND_IMAGE` | GHCR image references on the server; `award-backend:prod` / `award-frontend:prod` when built locally |
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
`urn:awards:problem:file-too-large` problem details as the application.

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
```

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

## 3. Server deployment (deferred)

1. Rent a 4 vCPU / 8 GB server in an EU location; install Docker Engine with the Compose plugin.
2. Firewall: allow 22, 80, 443 only.
3. Copy `docker-compose.yaml`, `docker-compose.prod.yml`, `infra/caddy/Caddyfile` and `.env.prod`.
4. `docker compose -f docker-compose.yaml -f docker-compose.prod.yml --env-file .env.prod up -d --wait`.
5. Run the manual verification below against the public address.
6. Schedule the nightly `pg_dump` and MinIO mirror to a target outside the server, with a copy of
   `MINIO_KMS_SECRET_KEY` stored apart from them; restore once to test it.

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
10. Clean up as in section 2. Expected: `docker compose up -d` brings the development stack back with its data.

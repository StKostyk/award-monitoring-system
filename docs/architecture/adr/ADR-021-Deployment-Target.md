# ADR-021: Deployment Target for the Thesis Defense

**Status**: Accepted; amended 2026-11-11 (AWS EC2 instead of Hetzner, see the addendum of that date)  
**Date**: 2026-10-02  
**Author**: Stefan Kostyk  
**Stakeholders**: Project Architect, Thesis Supervisor

---

## Context

The system has to run live at the thesis defense and, possibly, for a short pilot at the university. ADR-018
(Kubernetes) and ADR-020 (AWS) describe a production-grade target; both were deferred in 2026-10 until Epic 2 was
demoable.

### Background
- After Epic 2 the system consists of the backend (Spring Boot with the embedded authorization server), the
  Angular frontend behind nginx, PostgreSQL 17, Redis 7 and MinIO; Elasticsearch joins in Epic 5, and Kafka may
  join in Epic 7. In total about 6–8 GB of memory at the end of the project.
- The system has no active users yet; every month a server runs before the defense is cost without benefit.
- The quality gates and the Playwright suite run against the Docker Compose stack (ADR-017).
- Email verification, password reset and security notices need a real outbound mail service.
- Personal data must stay in the EU (GDPR).

### Assumptions
- One demo environment is enough; there is no separate staging environment.
- A short outage at the demo environment is acceptable; data loss is not.
- No domain name is registered yet; one can be added at deployment time.

---

## Decision

**One virtual server at an EU hosting provider (Hetzner, Germany or Finland), running the existing Docker
Compose stack with a production override, Caddy for TLS and Brevo as the SMTP relay.** The server is rented only
shortly before the defense or when a pilot user appears.

### Chosen Approach
- `docker-compose.yaml` plus `docker-compose.prod.yml`: Spring profile `production`, secrets from an `.env.prod`
  file kept on the server only, no published ports except 80 and 443, a password on Redis, Mailpit not started.
- Caddy terminates TLS (Let's Encrypt) for a domain or `<ip>.sslip.io`; nginx keeps the TLS proxy's scheme so the
  issuer and every redirect use `https://`.
- Images built by CI and pulled from GHCR; a `deploy-demo` CI job connects over SSH and runs `docker compose pull`
  and `up -d`. Until a server exists the job stays disabled.
- Fictional demo accounts (`db/seed/demo`, `@demo.example`) are created only when `DEMO_PASSWORD_HASH` is set.
- Nightly `pg_dump` and an archive of the document objects, encrypted with `age` and copied off the server; they
  expire after 14 days (DEMO_DEPLOYMENT §4).

### Rationale
- Runs exactly what the gates and the end-to-end tests run; no second deployment model to maintain.
- Roughly €10–20 per month while running, against more than $200 for the managed AWS stack of ADR-020.
- Can host every service planned up to Epic 8 on one 8 GB machine.
- Deferring the rental costs nothing: the production configuration is rehearsed locally with
  `SITE_ADDRESS=localhost`, so the remaining work at deployment time is renting the server, DNS and secrets.

---

## Consequences

### Positive Consequences
- Low cost and low operational effort for a solo project.
- The production profile is exercised before any money is spent (TLS, secure cookies, issuer, SMTP with
  authentication, no seeded development accounts).

### Negative Consequences
- A single host: no high availability, no horizontal scaling; maintenance needs a short downtime.
- Operating system updates and backups are the operator's job, not a managed service's.

### Neutral Consequences
- ADR-018 and ADR-020 stay as the reference architecture for a university-wide rollout.
- `ENVIRONMENT_PROMOTION.md` describes five environments; in practice there are two: local and demo.

---

## Alternatives Considered

### Alternative 1: Single server with k3s
- **Description**: The same server, with the Kubernetes manifests made real.
- **Pros**: Keeps the ADR-018 direction; demonstrates Kubernetes.
- **Cons**: Manifests, persistent volumes and ingress to maintain next to the Compose file the tests use.
- **Reason for Rejection**: More work and more risk without a user-visible benefit at the defense.

### Alternative 2: AWS managed services (ADR-020 as written)
- **Description**: EKS, RDS, ElastiCache, S3 and OpenSearch.
- **Pros**: Matches the original architecture; managed backups and failover.
- **Cons**: More than $200 per month; weeks of infrastructure work (IAM, VPC, NAT, infrastructure as code).
- **Reason for Rejection**: Cost and schedule.

### Alternative 3: Oracle Cloud free tier
- **Description**: The same Compose setup on a free ARM instance.
- **Pros**: No cost.
- **Cons**: Multi-architecture images needed; free instances can be reclaimed.
- **Reason for Rejection**: Reliability risk on the day of the defense.

---

## Implementation Notes

### Technical Requirements
- Server: 4 vCPU, 8 GB memory, 80 GB disk, Ubuntu LTS, Docker Engine with the Compose plugin.
- Brevo account with a verified sender; a domain with SPF and DKIM records for reliable delivery.

### Implementation Steps
1. Local rehearsal (done in SCRUM-33): `docs/deployment/DEMO_DEPLOYMENT.md`.
2. At deployment time: rent the server, open ports 22, 80 and 443 only, copy `.env.prod`, enable the CI job.
3. Smoke test against the public address: discovery document, sign-in, a verification email.

### Migration Considerations
- A fresh database; Flyway creates the schema. No data is migrated from development.

---

## Compliance & Quality

### Security Implications
- Secrets live only in `.env.prod` on the server and in CI secrets; the signing key is configured, never
  generated, in the `production` profile.
- Database, Redis and object storage are reachable only inside the Compose network.
- Data stays in an EU data centre.

### Performance Impact
- Sufficient for a demonstration and a pilot with tens of concurrent users.

### Maintainability
- One Compose file set for local, rehearsal and demo.

---

## Success Metrics

### Key Performance Indicators
- The demo environment answers the smoke test after every deployment.
- A restore from the nightly backup is rehearsed once before the defense.

### Monitoring & Alerting
- Container health checks and `restart: unless-stopped`; an external uptime check on the public address.

---

## Related Documents

- **Other ADRs**: ADR-017 (containerization), ADR-018 (orchestration), ADR-019 (CI/CD), ADR-020 (cloud platform)
- **Runbook**: `docs/deployment/DEMO_DEPLOYMENT.md`
- **Diagram**: `docs/diagrams/deployment-demo.puml`

---

## Addendum 2026-10-05: Production at the university

This decision covers the defense demo. A production deployment at ChNU runs the same Compose stack on university
infrastructure if the university's IT department can host it (the question is open with them); otherwise on a
server like the one above, rented by the university. Either way, each university gets its own deployment
(ADR-022), and the work left for production is the Epic 9 hardening (backend-for-frontend with an HttpOnly
cookie, refresh tokens hashed at rest, rate limits that suit a campus NAT, shared sessions in Redis).

## Addendum 2026-11-11: AWS EC2 for the demo (Feature 9.2)

The single-host decision stands; the provider changes. The demo runs on one AWS EC2 `m7i-flex.large` (2 vCPU,
8 GB, free-plan eligible) in `eu-central-1`, paid from the account's sign-up credits (about 73 USD a month for the
instance, so the credits cover the weeks up to the defense) instead of a rented Hetzner server. The 8 GB size
follows from the measured memory limits of the stack (6.3 GB in all, see below), which a 4 GB instance cannot hold
with ClamAV in it.

- **Environment** (Feature 9.2 PRD §2.1): the default VPC with one public subnet, a security group with 80 and 443
  open and 22 for key sign-in only, an encrypted 40 GB gp3 volume for every Docker volume, an Elastic IP with the
  address `<ip>.sslip.io` (Let's Encrypt through Caddy, no domain needed), automatic recovery on a failed system
  status check, an AWS Budgets alarm at 10 USD.
- **Backups**: the nightly encrypted archives go to an S3 bucket in `eu-north-1`, a second region, written by an
  IAM user that may only put objects; a 14-day lifecycle rule deletes them (DEMO_DEPLOYMENT §4).
- **Delivery**: GitHub Actions builds the images and pushes them to GHCR as `main-<sha>`; the `deploy-demo` job
  (GitHub environment `demo`) copies the Compose files over SSH with a pinned host key, writes `.env.release` with
  the two image names, scans exactly those images (a fixable critical vulnerability stops the deploy), runs
  `docker compose pull` and `up -d --wait`, and checks the issuer at
  `https://<SITE_ADDRESS>/.well-known/openid-configuration`. A manual run with `image_tag` deploys an earlier tag,
  which is the rollback. `.env.prod` with the secrets stays on the server only.
- **Server settings**: every service logs through `json-file` with 10 MB × 5 rotation and has a `mem_limit` of
  about 1.5 times its peak on the rehearsal stack (ClamAV 2.9 GB for its signature reload, backend 1 GB with
  `MaxRAMPercentage=60`, backup 1.1 GB for its 768 MB tmpfs); the backend has a fixed address so the frontend proxy
  keeps reaching it after the backend container is recreated.
- **One instance, no load balancer or Auto Scaling group**: with one target a load balancer only adds a hop and a
  monthly charge, and Caddy already terminates TLS. An Auto Scaling group would replace a failed host with an
  empty disk, while the database and the documents live on the instance's volume; automatic recovery keeps the
  same instance, volume and address. A second backend instance needs shared sessions and a scheduler lock, which
  are production work (addendum 2026-10-05).

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2026-10-02 | Stefan Kostyk | Initial version | Deployment target decision after Epic 2 |
| 2026-10-02 | Stefan Kostyk | MinIO image `cgr.dev/chainguard/minio` (runs as root), server-side encryption with a static key (`MINIO_KMS_SECRET_KEY`) | MinIO no longer publishes images on Docker Hub or quay.io; Feature 3.1 D-8 |
| 2026-10-05 | Stefan Kostyk | Addendum: production on university infrastructure, Epic 9 hardening | Design review of 2026-10-04 |
| 2026-10-05 | Stefan Kostyk | ClamAV (`clamav/clamav:stable`, about 1.2 GB) joins the Compose stack; the backend depends on it being healthy | Feature 3.1.3, PRD assumption A4 |
| 2026-11-11 | Stefan Kostyk | Addendum: AWS EC2 `m7i-flex.large` in `eu-central-1` on sign-up credits instead of Hetzner, S3 backups in `eu-north-1`, `deploy-demo` job, log rotation and memory limits | Feature 9.2 (D-10, AC-3.6); credits instead of a paid server |

---

**Document Status**: Approved  
**Next Review Date**: After the defense (2026-12-15)  
**ADR Category**: Technology

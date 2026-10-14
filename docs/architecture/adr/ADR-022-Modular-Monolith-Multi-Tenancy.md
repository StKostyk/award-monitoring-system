# ADR-022: Modular Monolith and Multi-University Deployment

**Status**: Proposed  
**Date**: 2026-10-05  
**Author**: Stefan Kostyk  
**Stakeholders**: Project Architect, Thesis Supervisor, IT departments of interested universities

---

## Context

The system is built for Chernivtsi National University: one Spring Boot backend, one Angular frontend, one
PostgreSQL database, deployed with Docker Compose (ADR-017, ADR-021). Other Ukrainian universities manage awards
in the same way, so the system should be able to serve them without a rewrite. The original architecture
documents also expect a later move to microservices (ADR-006, ADR-008, ADR-018) without saying when or why.

### Background
- The backend is organised by domain: `auth`, `authz`, `user`, `delegation`, `award`, `document`, `audit`,
  `gdpr`, `metrics`, with `common` and `config` shared. Domains call each other through services and in-process
  application events; nothing enforces the boundaries yet.
- Every university is a separate data controller under the GDPR and has its own organisation tree, roles and
  brand (ADR-016 addendum).
- One developer builds and operates the system; every extra deployable costs build, test and operations effort.

### Assumptions
- A university has at most a few hundred concurrent users; one 8 GB server carries one university (ADR-021).
- Fewer than ten universities in the foreseeable future.
- Document parsing with OCR (Epic 3, deferred) is the first workload with a different runtime profile.

---

## Decision

**One deployable organised as a modular monolith, one deployment per university, and services extracted only
when a module meets the extraction criteria below.**

### Chosen Approach
- **Modules.** Each top-level package is a module with a public API: its base package and the named interfaces
  `dto`, `event` and, where listed under Implementation Notes, `service` and `entity`. Other modules use only
  those, never its `repository`, `controller`, `mapper`, `security` or `web` package; entities they may read are
  changed only through the owning module's services. Since 7.1.2 the Spring Modulith test `ModularityTest`
  (`ApplicationModules.verify()`) enforces the boundaries, together with the event publication registry of the
  ADR-006 addendum.
- **Tenancy: deployment per university.** The same images run as one Compose stack per university, each with its
  own database, Redis, object storage bucket, signing key, mail sender, brand (`brand.json`, `APP_BRAND`) and
  organisation tree seed. A deployment is the tenant: tables carry no tenant id and no request resolves a tenant.
- **Extraction criteria.** A module becomes a separate service only when at least one applies:
  1. It needs independent scaling or a different runtime (CPU-heavy OCR, a Python model).
  2. It must fail or be updated without affecting award submission (scanning or parsing untrusted files).
  3. External systems consume its events and need a stable contract (then Kafka, ADR-006).
  4. A separate team owns it with its own release cadence.

  Code size and module count are not criteria. An extracted service brings back the gateway (ADR-008) and
  orchestration (ADR-018) questions.
- **Theming.** Brands follow the ADR-016 addendum: precompiled themes selected by `brand.json` and `APP_BRAND`.
  Because the deployment is the tenant, one instance never switches brands by host name.

### Rationale
- Separate deployments keep each university's personal data in its own database, under its own controller and,
  where the university wishes, on its own servers, with no risk of one tenant reading another's rows.
- No query, trigger or audit row changes: a shared schema would add a tenant column to every table, every index
  and every access rule, for a benefit that appears only with many tenants.
- Module boundaries keep the extraction path open at almost no cost today; the criteria stop a split that adds
  network calls, distributed transactions and deployments without solving a real problem.

---

## Consequences

### Positive Consequences
- Onboarding a university is configuration: a server, `.env`, `brand.json`, a logo, the organisation tree and,
  for a new brand, one SCSS folder.
- Strong isolation of personal data; a breach or outage stays inside one university.
- The tested Compose stack is what every university runs.

### Negative Consequences
- Updates are rolled out per deployment; ten universities mean ten upgrades (automated by the CI deploy job).
- No cross-university view without extra work; a ministry-level report would need an export or an aggregated
  read model fed by events.
- Fixed cost per university (one server each) instead of a shared pool.

### Neutral Consequences
- Module boundaries become a reviewed rule from Epic 7 on; existing cross-module repository calls are fixed then.
- ADR-018 and ADR-020 stay the reference for a single large deployment, not for many small ones.

---

## Alternatives Considered

### Alternative 1: Shared schema with a tenant column and row-level security
- **Pros**: One deployment for everyone; cheapest per tenant at scale; cross-university reports are one query.
- **Cons**: Every table, index, view, trigger and access rule changes; a missing filter leaks data between
  controllers; one controller relationship for many universities.
- **Reason for Rejection**: High risk and effort for fewer than ten tenants. Revisit above roughly ten.

### Alternative 2: Schema per university in one database
- **Pros**: One deployment; data separated by schema.
- **Cons**: Flyway runs per schema, connections are routed per request, the organisation tree cache and Redis
  keys need a tenant prefix; the backend still has to resolve the tenant on every request.
- **Reason for Rejection**: Most of the complexity of Alternative 1 with little gain over separate deployments.

### Alternative 3: Microservices now
- **Pros**: Independent deployment and scaling per domain; matches the original ADR-006/008/018 direction.
- **Cons**: Distributed transactions where audit rows and versions are now written in the change's own
  transaction (ADR-006 addendum); a gateway, a broker and an orchestrator to run; slower development.
- **Reason for Rejection**: No module meets an extraction criterion yet.

---

## Implementation Notes

### Steps
1. Now: nothing to build; this decision guides reviews (no new cross-module repository use).
2. Epic 7 kickoff: add Spring Modulith, the `ApplicationModules.verify()` test and the event publication
   registry; list and fix the boundary violations it reports. **Done**: registry in 7.1.1, modules and test in
   7.1.2 with no remaining violation or exception.

### Modules (7.1.2)

| Module | Depends on | Exposed to others |
|--------|------------|-------------------|
| `common` (open) | — | everything |
| `audit` | common | base (`ActorDirectory`), `service`, `dto`, `entity` |
| `organization` | — | base (`OrganizationTree`), `entity`, `dto`, `service` (`OrganizationService`, `OrganizationLookup`) |
| `authz` | audit, organization | base (`AccessScope`, `RoleLevels`, `RoleType`, `RolePermissions`, `TokenClaimConstants`) |
| `user` | common, audit, organization, authz | base (`AccountSessions`, `LentAuthority`), `entity`, `dto`, `event`, `service` (`UserAccounts`, `RoleHolders`) |
| `delegation` | common, audit, organization, authz, user | `entity`, `dto`, `event`, `service` (`DelegationLookup`) |
| `auth` | common, audit, organization, authz, user, delegation | `entity`, `dto`, `event`, `service` (`DeviceLookup`) |
| `award` | common, audit, organization, authz, user, delegation | base (`AwardDocuments`), `entity`, `dto`, `event`, `service` (`AwardOwnership`, `AwardLookup`, `AwardVersionLookup`) |
| `document` | common, audit, authz, user, award | `dto`, `event` |
| `gdpr` | common, audit, organization, authz, user, delegation, auth, award | `dto`, `event` |
| `notification` | common, authz, user, delegation, award | — |
| `metrics` | — | — |
| `config` | common, audit | — (depended on by none) |

- Cycles were broken by dependency inversion, with no behaviour change: `audit` names actors through
  `ActorDirectory` (implemented in `user`); `award` reads documents through `AwardDocuments` (implemented in
  `document`); `user` signs accounts out through `AccountSessions` (implemented in `auth`) and takes back lent
  authority through `LentAuthority` (implemented in `delegation`); the not-found exceptions extend
  `common.web.NotFoundException`, and the 403 handler lives in `authz`. The address-change and export endpoints
  moved to `auth` and `gdpr` with unchanged paths.
- Cross-module repository calls go through lookup services of the owning module whose methods keep the
  repository's names and transaction behaviour.
- Besides `User` and `Organization`, the entities of `award`, `delegation` and `auth` are readable by other
  modules (the document mapping and the data export read them); they are changed only by their own module.
- `ModularityTest` also writes the C4 component diagram and the module canvases; the copies are in
  `docs/architecture/diagrams/modules/` (`metrics` has no relationship and appears only in its own diagram).
3. When OCR returns (after Epic 8): evaluate document parsing against the extraction criteria; it is the first
   expected service, fed by document events.
4. A second university: document the onboarding steps in `docs/deployment/` and parameterise the organisation
   tree seed.

### Migration Considerations
- Moving to a shared schema later means adding a tenant column with a default, back-filling it per database and
  merging databases; separate deployments do not block that path.

---

## Compliance & Quality

### Security Implications
- Isolation by deployment: separate credentials, signing keys and storage per university.
- Each university remains the data controller for its deployment; the processing records (ROPA) are per
  deployment.

### Performance Impact
- None today; module boundaries add no network calls.

### Maintainability
- One code base, one image set, one test suite; boundaries checked by a test from Epic 7 on.

---

## Success Metrics

### Key Performance Indicators
- A second university can be configured and started without code changes, apart from a brand folder.
- The module verification test passes on `develop` from Epic 7 on.

### Monitoring & Alerting
- Per deployment, as in ADR-021.

---

## Related Documents

- **Other ADRs**: [ADR-006 Message Queue](./ADR-006-Message-Queue.md), [ADR-008 API Gateway](./ADR-008-API-Gateway.md),
  [ADR-016 Styling](./ADR-016-Styling.md) (addendum: brands), [ADR-018 Orchestration](./ADR-018-Orchestration.md),
  [ADR-021 Deployment Target](./ADR-021-Deployment-Target.md)
- **UI guidelines**: [UI_GUIDELINES.md](../../frontend/UI_GUIDELINES.md)

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2026-10-05 | Stefan Kostyk | Initial version (proposed) | Design review of 2026-10-04 |
| 2026-10-13 | Stefan Kostyk | Module list, named interfaces, step 2 done | Story 7.1.2 (SCRUM-65) |

---

**Document Status**: Draft  
**Next Review Date**: Epic 7 kickoff  
**ADR Category**: Architecture

# Feature 7.1: Reliable Events and Module Boundaries

> **Epic**: 7 — Notification & Communication System (SCRUM-63)
> **Sprint**: 5 (2026-10-12 → 2026-10-18)
> **Points**: 13 (two stories)
> **Status**: Approved 2026-10-09
> **Author**: Stefan Kostyk
> **Governing docs**: EPIC-07 tracker (decisions of 2026-10-09, deviation 1, technical notes, risks 1–2), ADR-006 addendum 2026-10-05, ADR-022 (modules, step 2), ADR-021 (one instance per university), DATA_DICTIONARY §1.6, §5, PRIVACY_BY_DESIGN §4.2, `MailDelivery`, the mail listeners (`AuthenticationMails`, `AuthorityChangeMails`, `DecisionMails`, `CorrectionMails`, `OverdueMails`, `PrivacyMails`), `StoredObjectCleanup`, `ReviewMetrics`, the 18 records under `*/event/`

## 1. Problem and personas

Mails that follow a change are sent by after-commit listeners. If the mail server is down for the three attempts of `MailDelivery`, or the backend stops between the commit and the send, the mail is gone: a reviewer's decision, a correction, an overdue digest or a role change reaches nobody, and nothing records that it was missed. Feature 7.2 adds in-app notifications as more listeners on the same events, so the gap would grow with every listener.

The module boundaries of ADR-022 are a rule nobody checks. Modules read each other's repositories and entities directly and several depend on each other in a circle (`user` ↔ `auth`, `user` ↔ `authz`, `award` ↔ `document`, `common` → domain modules). The `notification` module of 7.2 should start inside checked boundaries, not add to the tangle.

| Persona | Need in this feature |
|---------|----------------------|
| Anastasia, employee | The decision or correction mail about her award arrives even if the mail server was down at that moment |
| Prof. Martynyuk, dean (reviewer) | The overdue digest and the role or delegation notices are not lost silently |
| Dmytro, GDPR officer | Pending messages keep personal data only as long as needed; no usable sign-in link is stored in the database |
| Operator (system administrator) | Can see how many messages are waiting or were given up, and a restart does not lose them |
| Developer (maintainer, thesis reviewer) | A failing test, not a review, tells when a module reaches into another one's internals; a generated diagram shows the modules |

## 2. Scope

**In (this feature)**

- Spring Modulith event publication registry (JDBC, PostgreSQL) for the after-commit listeners of decisions, corrections, overdue notices, role and delegation changes, the restored address notice, the data export notice, document object cleanup and review metrics
- `event_publication` table as a Flyway migration (V036); completed publications deleted at once
- `MailDelivery` reports a final failure as an exception, so the publication stays incomplete
- A retry job: incomplete publications are resubmitted every 10 minutes, given up after 24 hours, deleted after 30 days; two gauges and log lines for the operator
- Security mails that carry a one-time link (verification, password reset, address change, new device) and the lock notice stay off the registry, sent after the commit as today
- Spring Modulith `ApplicationModules` verification test; all module cycles removed; cross-module access only through each module's base package and named interfaces
- Two new modules: `organization` (organisation tree) and `notification` (first resident: the role and delegation mails)
- Generated module diagram under `docs/architecture/diagrams/modules/`; ADR-006 and ADR-022 updated

**Out (where it goes)**

- In-app notifications, preferences, delivery log, HTML mails: Feature 7.2
- Moving the decision, correction, overdue and privacy mails into `notification`: 7.2.1 / 1.3.2, when preferences have to decide whether a mail goes out
- Externalising events to Kafka: only for a consumer outside the application (ADR-006 addendum)
- A lock for the retry job across instances: Epic 9, before a second instance (ADR-021 runs one)
- An administration screen for failed publications: not planned; the operator uses the gauges, the log and psql
- Removing pending publications of a person at erasure: Epic 6 (erasure story), noted in the data dictionary
- Module-level integration tests (`@ApplicationModuleTest`) for every module: added per module when a story touches it

## 3. Stories

| Key | Story | Points | Parallel | Depends on |
|-----|-------|--------|----------|------------|
| SCRUM-64 (#181) | 7.1.1 Event publication registry for the after-commit mails | 5 | no | — |
| SCRUM-65 (#182) | 7.1.2 Module boundaries checked by Spring Modulith | 8 | no | 7.1.1 (Modulith dependencies, listener set) |

Backend only; neither story has a UI part, so neither splits.

## 4. Acceptance criteria

**Registry events**: `EmailRestored`, `RoleAssigned`, `RoleRevoked`, `DelegationCreated`, `DelegationRevoked`, `AwardDecided`, `AwardCorrected`, `OverdueNoticed`, `DataExported`, `ObjectsReleased`, `ReviewMeasured`. **Transient events**: `VerificationRequested`, `PasswordResetRequested`, `EmailChangeRequested`, `EmailChanged`, `NewDeviceSignedIn` (each carries a link with a raw one-time token), `AccountLocked` (published outside a transaction). `ObjectStored` keeps its after-rollback listener, which the registry does not cover.

### 7.1.1 Event publication registry for the after-commit mails (SCRUM-64)

- **AC-1.1** Given V036 is applied, when a change that publishes a registry event commits, then one `event_publication` row per after-commit listener is written in the same transaction; when the listener returns normally, the row is deleted.
- **AC-1.2** Given the publishing transaction rolls back, then no `event_publication` row remains and no mail is sent.
- **AC-1.3** Given the mail server refuses all three attempts, when a registry mail listener runs, then `MailDelivery` throws `MailNotDeliveredException`, the publication stays incomplete, and the error log names the subject and recipients but not the body.
- **AC-1.4** Given an incomplete publication older than 10 minutes and younger than 24 hours, when the retry job runs (every 10 minutes, first run 1 minute after start), then the listener runs again and the row is deleted once it succeeds. A restart needs nothing else: the first run after start picks up what was left.
- **AC-1.5** Given an incomplete publication older than 24 hours, then the retry job no longer resubmits it and logs one error line per run with the count. Given one older than 30 days, then the job deletes it and logs a warning with the count.
- **AC-1.6** Given the registry, then the gauges `award.events.incomplete` (all incomplete rows) and `award.events.abandoned` (incomplete rows older than 24 hours) appear in `/actuator/prometheus`.
- **AC-1.7** Given a transient event is published, then no `event_publication` row is written, the mail is sent after the commit as before, and nothing is sent after a rollback; no row ever contains a one-time link.
- **AC-1.8** Given each registry event type, then it serialises to JSON and back to an equal object with the application's `ObjectMapper` (one test per type), so a publication can never make the business transaction fail.
- **AC-1.9** Given the existing mail behaviour, then subjects, bodies and recipients are unchanged (existing listener tests and mail FTs pass), and `OverdueMails` counts a sent notice only after `send` returns.
- **AC-1.10** Given the documentation, then ADR-006 states the registry as the decision (status line, at-least-once delivery, the transient exception), DATA_DICTIONARY describes `event_publication` (columns, personal data, retention), and the tracker records the retry settings.

### 7.1.2 Module boundaries checked by Spring Modulith (SCRUM-65)

- **AC-2.1** Given `ModularityTest`, when it runs in the unit phase, then `ApplicationModules.of(AwardMonitoringSystemApplication.class)` finds exactly the modules `audit`, `auth`, `authz`, `award`, `common`, `config`, `delegation`, `document`, `gdpr`, `metrics`, `notification`, `organization`, `user`, and `verify()` reports no violation.
- **AC-2.2** Given the module graph, then it has no cycle; `common` depends on no other module; `config` is depended on by none.
- **AC-2.3** Given a module, then other modules use only its base package and its named interfaces (`event`, `dto`, and where listed in §8.3 `service` and `entity`); no module uses another's `repository`, `controller`, `mapper`, `security` or `web` package.
- **AC-2.4** Given a deliberate violation in a test fixture package (a class using another fixture module's repository), when the fixture is verified, then the violation is reported naming both types (proves the test fails on a real breach).
- **AC-2.5** Given the refactor, then behaviour is unchanged: no migration, no `openapi.yml` change, every existing unit, IT and FT passes with at most import and package changes, and the role and delegation mails (now in `notification`) are still sent and still registry-backed.
- **AC-2.6** Given the Spring Modulith `Documenter`, when `ModularityTest` runs, then it writes the C4 component diagram and module canvases; the copies committed under `docs/architecture/diagrams/modules/` parse with `puml-check`, and ADR-022 lists the modules and marks step 2 done.
- **AC-2.7** Given the fixes outgrow the story, then remaining non-cycle violations are listed in `ModularityTest` as named exceptions, each with a reason and a follow-up story key, and the test fails on any violation not in the list. Cycles are never listed. The goal and expected outcome is an empty list.

## 5. Edge cases

| Case | Expected |
|------|----------|
| Mail accepted by the server, then the completion update fails (database gone at that moment) | Mail resent at the next retry: at-least-once delivery, stated in ADR-006 |
| Retry job runs while the first attempt is still in progress | Not possible within the 10-minute threshold: one listener run ends within ~100 s (three tries of at most 30 s with SMTP timeouts of 10 s, pauses 2 s + 5 s) |
| Backend stops during an `@Async` send or with publications queued on the executor | Rows stay incomplete; the first retry run after start sends them |
| Overdue digest resent after a crash | The reviewer gets the digest twice; the overdue flag of V033 was set in the publishing transaction, so no further digest repeats it |
| Listener throws for a non-mail reason (bug, bad data) | Same as a mail failure: retried for 24 h, then counted as abandoned, deleted after 30 days; the log has the exception each time |
| Event cannot be serialised | Prevented by AC-1.8; otherwise the business transaction would fail at commit |
| Transient security mail fails three times | Lost as today; the person repeats the action (resend verification, request a new reset link, change the address again); logged at error |
| Listener class moves package in 7.1.2 (listener id changes) | Rows written under the old id are not resubmitted, counted as abandoned after 24 h and deleted after 30 days; acceptable with no production data (decision 12) |
| Two instances running | Both retry jobs would resubmit the same rows; not supported until the lock of Epic 9 (single instance, ADR-021) |
| Pending row holds personal data (address, name, reviewer comment) | Deleted at completion; failed rows deleted after 30 days; the erasure story of Epic 6 removes a person's pending rows |
| `ObjectsReleased` cleanup fails (MinIO down) | `StoredObjectCleanup` logs and leaves the objects to `DocumentSweeper` as today, so the publication completes; no change |
| Synchronous listeners introduced in 7.1.2 to break cycles (token revocation, delegation end on role change) | Run in the publishing transaction (`@EventListener`, not after commit), so a failure still rolls back the role change as the direct call did |
| Endpoint moves module in 7.1.2 (address change, data export, organisations) | Path, method, security rule and response unchanged; contract tests and FTs prove it |

## 6. Dependencies

### Tables

| Table | State | Used for |
|-------|-------|----------|
| `event_publication` | New, V036 (7.1.1) | Publication registry |
| `award_requests.overdue_notice_*` (V033) | Existing | Overdue flag set before the digest event (unchanged) |

### Endpoints

None new or changed. 7.1.2 moves three controllers between modules with unchanged paths: `/users/me/email-change` → `auth`, `/users/me/export` → `gdpr`, `/organizations` → `organization`; `/organizations/{id}/review-period` stays where it is.

### Services

- `common.mail.MailDelivery`: `send` returns `void` and throws `MailNotDeliveredException` (new, `common.mail`) after the last attempt
- `common.event.AfterCommit` (new): publishes a transient event after the commit of the current transaction, or at once without one
- `common.event.PublicationRetry` (new): the scheduled retry and clean-up job, gauges; uses Spring Modulith `IncompleteEventPublications` and a `JdbcTemplate` delete
- Publishers of transient events in `auth` (`RegistrationService`, `PasswordResetService`, `EmailChangeService`, `DeviceService`) switch to `AfterCommit`; their listeners in `AuthenticationMails` become `@Async @EventListener`
- 7.1.2: see §8.3

### Frontend

None.

### External systems

- Maven: `spring-modulith-bom` (newest 1.4.x patch, the line for Spring Boot 3.5), `spring-modulith-starter-jdbc` (compile), `spring-modulith-starter-test` and `spring-modulith-docs` (test). One online `./mvnw test-compile` after adding them.
- SMTP (Mailpit locally, Brevo for the demo): unchanged

## 7. Technical decisions

Inherited: ADR-006 addendum 2026-10-05 (in-process events, publication registry in PostgreSQL, no broker), ADR-022 (modules with a public API and events, verification test at Epic 7), ADR-021 (one instance per university), ADR-005 (Flyway owns the schema), tracker decisions of 2026-10-09.

**Proposed deviations and assumptions**

1. **ADR-006 decision line.** The ADR still names Kafka; the addendum becomes the decision and the status line changes to "Accepted: Spring application events with the Spring Modulith publication registry; Kafka only for an outside consumer" (tracker deviation 1). 7.1.1.
2. **Security mails with a one-time link stay off the registry.** The registry stores the event as JSON; these events carry a raw token in the link, while `one_time_tokens` stores only its hash. Storing them would put usable sign-in and reset links in the database for as long as a row lives. The person can repeat each of these actions, so a lost mail costs one retry. They are published after the commit through `AfterCommit` and heard by `@Async @EventListener`, which the registry does not intercept; an IT proves no row is written. `EmailRestored` (no token) goes on the registry.
3. **Completed publications deleted at once** (`spring.modulith.events.completion-mode: delete`), not kept as the registry does by default. The rows hold addresses, names and reviewer comments; keeping them serves no purpose, as audit rows record the changes. No archive table.
4. **Own retry job instead of republish on restart.** `republish-outstanding-events-on-restart` stays off: it would resubmit every incomplete row, however old, and nothing at all while the application runs. The job resubmits rows older than 10 minutes and younger than 24 hours every 10 minutes, and deletes rows older than 30 days. Properties `app.events.retry-interval` (PT10M), `app.events.retry-after` (PT10M), `app.events.give-up-after` (PT24H), `app.events.keep-failed` (P30D).
5. **`MailDelivery` throws after the last attempt** instead of returning `false`. A transient listener's exception reaches the async exception handler and is logged, which matches today's outcome.
6. **At-least-once delivery.** A crash after the server accepted a mail and before the row was deleted sends it again. Accepted: a duplicate is better than a lost decision mail. Stated in ADR-006 and the user guide FAQ.
7. **Module list changes from ADR-022** (updated in 7.1.2):
   - `organization` (new): `Organization`, `OrganizationType`, `OrganizationRepository`, `OrganizationService`, `OrganizationController` from `user`, and `OrganizationTree` from `authz`.
   - `notification` (new): `AuthorityChangeMails` from `authz`. It listens to events of `user` and `delegation`, so it cannot stay in `authz`, which both depend on.
   - `authz` owns the role model: `RoleType` from `user`, `RolePermissions` and the claim names of `TokenClaimsCustomizer` from `auth.security`.
   - Security configuration (`SecurityConfig`, `PublicApiSecurityConfig`, `AuthorizationServerConfig`, `AuthorizationStoreConfig`, `TokenConfig`, `LoginSessionConfig`) moves from `config` to `auth`, `StorageConfig` to `document`, and the properties classes to the module that reads them (`AuthProperties` → `auth`, `DocumentProperties` → `document`, `WorkflowProperties` → `award`, `ProtectionProperties` → `common.limit`).
   - `common` is an open module (`@ApplicationModule(type = OPEN)`), so `common.web`, `common.mail`, `common.limit` and `common.event` are usable everywhere.
8. **`User` and `Organization` exposed as a named interface `entity`, not hidden behind lookups.** `Award`, `OneTimeToken` and `UserDevice` map them with `@ManyToOne`. Replacing those mappings with ids would rewrite the award queries and the achievements projection for no behaviour gain. Other modules read these entities; they change them only through the owning module's services. Repositories are never exposed: the ~45 cross-module repository calls become service calls.
9. **Cycles broken by events or by dependency inversion, with no behaviour change:**
   - `user` → `auth`/`delegation`: token revocation and delegation end on a role change or move become synchronous `@EventListener`s in `auth` and `delegation` on `RoleAssigned`/`RoleRevoked` (and a move event if the existing ones do not carry it), in the same transaction.
   - `delegation` → `auth`: token revocation on `DelegationRevoked`, the same way.
   - `user` → `gdpr`/`auth`: the export and address-change endpoints move to `gdpr` and `auth`; `ProfileNameRules` stops using `RegisterRequest`.
   - `audit` → `user`: actor names come through an `ActorDirectory` interface in `audit`, implemented in `user`.
   - `award` ↔ `document`: `award` defines `AwardDocuments` (counts, release) in its base package, implemented in `document`.
   - `common` → domain modules: their not-found exceptions extend `ApiProblemException`; `ApiExceptionHandler` handles the base type; the `AccessDenials` reference moves to `authz`.
10. **Estimate.** 7.1.2 stays at 8 points. If the fixes outgrow it, AC-2.7 applies and a follow-up story 7.1.3 is created with `tracker-sync create-story` only then (tracker risk 1).
11. **Single instance.** The retry job has no lock (ADR-021: one backend per university). The lock comes with any second instance (Epic 9).
12. **Listener ids.** The registry identifies a listener by its method signature, so moving `AuthorityChangeMails` changes its id. With no production data before the demo, rows under an old id are left to the 24-hour / 30-day rule; no migration of ids.

## 8. Contract

### 8.1 OpenAPI stubs

None: no endpoint is added or changed. The controllers moved in 7.1.2 keep their paths and schemas.

### 8.2 Migration outlines

| File | Story | Content |
|------|-------|---------|
| `V036__event_publication.sql` | 7.1.1 | `CREATE TABLE event_publication` with `id UUID PRIMARY KEY`, `listener_id TEXT NOT NULL`, `event_type TEXT NOT NULL`, `serialized_event TEXT NOT NULL`, `publication_date TIMESTAMPTZ NOT NULL`, `completion_date TIMESTAMPTZ NULL`; `CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event)`; `CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date)`; `COMMENT ON TABLE` (registry, personal data, retention). Columns and index names checked against `schema-postgresql.sql` in the resolved `spring-modulith-events-jdbc` jar; `spring.modulith.events.jdbc.schema-initialization.enabled: false` |

7.1.2 has no migration.

### 8.3 Module API (7.1.2)

| Module | Depends on | Exposed to others |
|--------|------------|-------------------|
| `common` (open) | — | everything |
| `audit` | common | base (`ActorDirectory`), `service` (`AuditService`), `dto`, `entity` (`AuditAction`) |
| `organization` | common, audit | base (`OrganizationTree`), `entity`, `dto`, `service` (`OrganizationService`) |
| `authz` | common, organization | base (`AccessScope`, `RoleLevels`, `RoleType`, `RolePermissions`, claim names) |
| `user` | common, audit, organization, authz | `entity`, `dto`, `event`, `service` (lookups, `UserNotFoundException`) |
| `delegation` | common, audit, authz, user | `event`, `service` (lookups for `award` and `gdpr`) |
| `auth` | common, audit, organization, authz, user, delegation | `security` for nothing outside `auth` once the security config moves; `service` (`DeviceFingerprint`, device lookups for `gdpr`) |
| `award` | common, audit, organization, authz, user, delegation | base (`AwardDocuments`), `event`, `dto`, `service` (`AwardOwnership`, `AwardNotFoundException`, lookups for `gdpr`), `controller` constants moved to base (`AwardPermissionConstants`) |
| `document` | common, audit, authz, user, award | `event` |
| `gdpr` | common, audit, user, auth, award, delegation | `event` |
| `notification` | common, user, delegation, organization, authz | — |
| `metrics` | common | base |
| `config` | any | — |

The table is the target; the story may move a type to the base package instead of opening a `service` interface where that is smaller, and records the final list in ADR-022.

## 9. Test plan

| AC | Unit | IT | FT | Other |
|----|------|----|----|-------|
| 1.1, 1.2 | | ✓ registry IT (Testcontainers): decision commit writes and then deletes the row; rollback leaves none | | |
| 1.3 | ✓ `MailDelivery` throws after three attempts, pauses as before | ✓ failing `JavaMailSender` bean: row stays incomplete | | |
| 1.4, 1.5 | ✓ `PublicationRetry` window predicate and clean-up with a fixed clock | ✓ incomplete row aged by SQL is resubmitted, an old one is skipped, a 31-day one deleted | | |
| 1.6 | ✓ gauges with stubbed counts | | ✓ `/actuator/prometheus` lists both gauges | |
| 1.7 | ✓ `AfterCommit` with and without a transaction | ✓ reset request: no row; rollback: no mail | ✓ registration and reset FTs unchanged | |
| 1.8 | ✓ JSON round trip per registry event type | | | |
| 1.9 | ✓ existing listener tests; `OverdueMails` metric | | ✓ existing mail FTs | |
| 1.10 | | | | doc review in the PR |
| 2.1–2.3, 2.7 | ✓ `ModularityTest` | | | |
| 2.4 | ✓ fixture modules under `src/test/java/.../modulith/fixture` | | | |
| 2.5 | ✓ all | ✓ all | ✓ all, incl. role and delegation mail FTs | e2e suite run once |
| 2.6 | ✓ `Documenter` output | | | `puml-check.ps1` |

Coverage target 85 % lines; static analysis clean. 7.1.1 (migration, `auth` publishers) and 7.1.2 (security configuration moves, `@PreAuthorize` beans, `auth/`) go through the security review agent.

## 10. Manual verification

Preconditions: `docker compose up -d postgres redis mailpit minio clamav`; backend started with the short retry settings: `$env:APP_EVENTS_RETRY_INTERVAL='PT1M'; $env:APP_EVENTS_RETRY_AFTER='PT1M'; ./mvnw spring-boot:run -Dspring-boot.run.profiles=local` in `backend/`; frontend `npm start` on `http://localhost:4200`. Mailpit `http://localhost:8025`; psql `docker compose exec postgres psql -U postgres award_monitoring`. Seed accounts (password `Passw0rd-demo`, `@chnu.edu.ua`): `employee.fmi`, `secretary.fmi`, `secretary2.fmi`, `dean.fmi`, `admin`. Preparation: as `employee.fmi` create and submit awards A, B, C and D (department-level category).

1. psql `\d event_publication`. Expected: the six columns and two indexes of §8.2; `select count(*) from event_publication;` → 0. (AC-1.1)
2. As `secretary.fmi` approve A. Expected: Mailpit has the decision mail to `employee.fmi`; the count stays 0. (AC-1.1, 1.9)
3. `docker compose stop mailpit`; as `secretary.fmi` return B with a comment. Expected: the backend log shows three warnings and one error naming the subject, no body text; psql `select event_type, publication_date from event_publication where completion_date is null;` → one row ending in `AwardDecided`; `http://localhost:8080/actuator/prometheus` has `award_events_incomplete 1.0`. (AC-1.3, 1.6)
4. `docker compose start mailpit`; wait up to 2 minutes. Expected: the mail for B arrives once; the row is gone; the gauge reads 0. (AC-1.4)
5. `docker compose stop mailpit`; approve C; stop the backend (Ctrl+C) right after. Start Mailpit, start the backend again. Expected: within 2 minutes of the start, the mail for C arrives; the row is gone. (AC-1.4)
6. `docker compose stop mailpit`; on `http://localhost:4200/forgot-password` request a reset for `employee.fmi`. Expected: psql count 0; `select count(*) from event_publication where serialized_event ilike '%token%' or serialized_event ilike '%reset%';` → 0. Start Mailpit. Expected: no reset mail arrives later; request again: the mail arrives and its link works. (AC-1.7)
7. With Mailpit stopped, as `admin` assign a role to `secretary2.fmi`. Expected: one incomplete row ending in `RoleAssigned`. Start Mailpit. Expected: the role mail arrives within 2 minutes. (AC-1.4)
8. As `employee.fmi` call `GET /api/v1/users/me/export` in Swagger (`http://localhost:8080/swagger-ui.html`). Expected: the export, and the «Ваші дані експортовано» mail; count 0. (AC-1.1)
9. In `backend/` run `./mvnw test -Dtest=ModularityTest`. Expected: pass; open `docs/architecture/diagrams/modules/components.puml` (or its rendering). Expected: the 13 modules of AC-2.1, arrows only in the direction of §8.3. (AC-2.1, 2.2, 2.6)
10. Smoke in the browser after 7.1.2: sign in as each account; `employee.fmi` submits D and uploads a PDF to it; `secretary.fmi` approves D; `secretary2.fmi` opens `/achievements`; `admin` assigns and revokes a role and `dean.fmi` creates and revokes a delegation; `employee.fmi` requests an address change and an export. Expected: every screen and mail as before 7.1.2; the role and delegation mails arrive. (AC-2.5)

### Detours

11. psql `update event_publication set publication_date = now() - interval '25 hours' where completion_date is null;` after leaving one row incomplete (Mailpit stopped, decide an award), then start Mailpit and wait 2 minutes. Expected: no mail; the log has the given-up error with count 1; `award_events_abandoned 1.0`. Then `... interval '31 days'` and wait. Expected: the row is deleted and a warning logs the count. (AC-1.5, 1.6)
12. Open B's review in two tabs as `secretary.fmi` and `secretary2.fmi`; decide in one, then in the other. Expected: the second gets 409; psql shows no row left over; one mail only. (AC-1.2)
13. In Swagger send the same decision for A again. Expected: 409; no new row, no second mail. (AC-1.2)
14. Reload the review page and press Back after a decision. Expected: no new row, no mail. (AC-1.1)
15. After step 3, sign out (or let the access token expire) before step 4. Expected: the pending mail still arrives; it does not depend on any session. (AC-1.4)
16. With Mailpit stopped, `secretary.fmi` and `secretary2.fmi` decide two different awards from two browsers within a minute. Expected: two incomplete rows; both mails arrive once after the start, none duplicated. (AC-1.4)
17. With Mailpit stopped, request an address change for `employee.fmi`. Expected: no row. Start Mailpit and request again. Expected: the new link confirms; no earlier link exists anywhere in the database. (AC-1.7)
18. Add `private final ua.edu.chnu.awards.user.repository.UserRepository users;` to any `award` service locally and run `ModularityTest`. Expected: it fails, naming the `award` class and `UserRepository`; revert. (AC-2.3, 2.4)
19. `docker compose stop postgres` while a row is incomplete and the backend runs, then start it. Expected: the retry job logs a failure and recovers on the next run; the mail arrives. (AC-1.4)

## 11. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| A registry event fails to serialise | The business transaction fails at commit | JSON round-trip test per event type (AC-1.8); new event types added to that test by convention |
| One-time links stored in the database | Usable credentials at rest | Transient path for token events; IT asserts no row is written (AC-1.7); security review |
| Duplicate mails after a crash | Confusion | 10-minute threshold well above one attempt's duration; at-least-once stated in ADR-006 |
| Silent long-term failure (bad address, bug) | Mails never sent | Gauges, error log per run, 30-day keep for inspection |
| 7.1.2 grows beyond 8 points | Sprint slips, 7.2 waits | Named-exception fallback (AC-2.7), follow-up story; cycles fixed first since 7.2 needs `notification` above them |
| Refactor changes behaviour unnoticed | Regression in security rules or mails | No test logic changes allowed beyond imports; full gate and the e2e suite once; security review of the moved configuration |
| Spring Modulith 1.4 behaviour differs from the outline (columns, listener interception) | Rework | Schema checked against the jar; ITs cover interception and the transient path before the listeners change |

## 12. Definition of Done

- `.\tools\gate.ps1` green (unit, slice, IT, FT, JaCoCo ≥ 85 % lines, Checkstyle/PMD/SpotBugs clean, frontend lint, tests, prod build)
- e2e suite run once after 7.1.2 (no UI change; behaviour proof)
- Docs in the same PR as the story: ADR-006 (decision, status, at-least-once, transient events), DATA_DICTIONARY (new §5.3 `event_publication`), V036 comment, ADR-022 (module list, named interfaces, step 2 done), module diagrams with `puml-check` passing, `CHANGELOG.md`, EPIC-07 tracker, `BACKLOG.md`, user guide FAQ line on possible duplicate mails
- Security review of both diffs
- §10 manual verification run after the validation, including the detours

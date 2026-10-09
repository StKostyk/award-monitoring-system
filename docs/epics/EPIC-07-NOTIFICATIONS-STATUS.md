# Epic 7: Notification & Communication System — Status

> **Started**: 2026-10-09 (kickoff)
> **Done**: -
> **Author**: Stefan Kostyk
> **Jira epic**: SCRUM-63
> **Roadmap**: DEVELOPMENT_ROADMAP.md § Epic 7 (local planning document)

## Progress

| Feature | Status | Started | Done |
|---------|--------|---------|------|
| 7.1 Reliable events and module boundaries (publication registry, Spring Modulith verification) | Not started, PRD next | - | - |
| 7.2 Notification centre and preferences (in-app notifications, preferences, delivery log, HTML mails) | Not started | - | - |

## Current focus

Kickoff 2026-10-09. Feature 7.1 first: every notification built in 7.2 is a listener on the publication registry and
lives in a module the verification test already checks.

## Scope

Events that already send e-mail after the commit (authentication, roles and delegations, decisions, corrections,
overdue notices, data export) move onto the Spring Modulith event publication registry, so a failed send or a restart
no longer loses them. The module boundaries of ADR-022 become a test. A new `notification` module stores in-app
notifications for award and review events and serves the notification centre in the toolbar; each person chooses per
event type whether it reaches them by e-mail, in the app or both. Deliveries are recorded, counted as metrics and
included in the personal data export. Notification e-mails get HTML templates in the university brand.

Out of scope here: SMS (needs a paid gateway; no spend before the demo, ADR-021), browser push notifications (with the
PWA work of Epic 8), Kafka (ADR-006 addendum: only for a consumer outside the application), real-time delivery over
WebSocket or server-sent events (polling as on the award page, design review H-1), daily digests, purging expired
notifications (retention job of Epic 6, Feature 6.2), the three code gaps of the Epic 4 documentation sync (separate
fix stories if chosen).

## Stories

`parallel` marks stories whose UI can be built in a separate lane from the OpenAPI contract while the backend is in progress.

| # | Story | Feature | Pts | Jira | GitHub | Parallel | Status |
|---|-------|---------|-----|------|--------|----------|--------|
| 1 | 7.1.1 Event publication registry for the after-commit mails | 7.1 | 5 | SCRUM-64 | #181 | no | Planned |
| 2 | 7.1.2 Module boundaries checked by Spring Modulith | 7.1 | 8 | SCRUM-65 | #182 | no | Planned |
| 3 | 7.2.1 In-app notifications from award and review events | 7.2 | 5 | SCRUM-66 | #183 | no | Planned |
| 4 | 7.2.2 Notification centre in the toolbar | 7.2 | 5 | SCRUM-67 | #184 | yes | Planned |
| 5 | 1.3.2 Notification preferences | 7.2 | 3 | SCRUM-16 | #39 | yes | Planned |
| 6 | 7.2.3 Delivery log and notifications in the personal data export | 7.2 | 3 | SCRUM-68 | #185 | no | Planned |
| 7 | 7.2.4 HTML e-mail templates in the university brand | 7.2 | 3 | SCRUM-69 | #186 | no | Planned |

Total: 32 points, planned for sprint 5 (2026-10-12 to 2026-10-18).

## Decisions

| Date | Decision | Rationale | Reference |
|------|----------|-----------|-----------|
| 2026-10-09 | Events stay in-process Spring application events on the Spring Modulith publication registry (PostgreSQL); no Kafka in this epic | No consumer outside the application exists; the registry closes the lost-mail gap without operating a broker | ADR-006 addendum 2026-10-05 (confirmed) |
| 2026-10-09 | Spring Modulith verification test and boundary fixes in Feature 7.1, before the `notification` module is written | ADR-022 step 2; the new module is checked from its first commit | ADR-022 |
| 2026-10-09 | Channels: e-mail and in-app; SMS out of scope, browser push with Epic 8 | No paid gateway before the demo; push needs the service worker of the PWA work | BRD §6.2, ADR-021 |
| 2026-10-09 | The notification centre polls the unread count while the tab is visible; no WebSocket | Same pattern as the award page; one instance, no session affinity needed | Design review H-1 |
| 2026-10-09 | Security mails (verification, password reset, new device, lock, address change) are always sent and have no preference | They protect the account; a person must not switch them off | PRIVACY_BY_DESIGN §4.2 |
| 2026-10-09 | Story 1.3.2 (notification preferences) moves from Epic 1 to this epic | Preferences need the notification types and channels built here | Design review A-9 |
| 2026-10-09 | Story order: registry, boundaries, in-app notifications, centre, preferences, delivery log, HTML templates | Infrastructure before features; the HTML templates are the first to drop if the sprint runs short | This kickoff |

## Documentation deviations

To settle in the Feature 7.1 and 7.2 PRDs; each document is updated in the story that changes it.

1. ADR-006 still names Kafka as the decision; the addendum becomes the decision and the status line changes at 7.1.1.
2. Roadmap task "Create notification queue with Kafka" and the 3-week, sprint 11 estimate: superseded by the decisions
   above; the roadmap stays as the local original.
3. BRD §6.2 and US-005 (definition of done "Mobile notifications") promise SMS and push; e-mail and in-app only here.
4. `ck_notifications_type` (V011) lists `AWARD_SUBMITTED` … `SYSTEM_ALERT`; the events that exist are decisions
   (including escalation), corrections, overdue notices and hand-over. A new migration aligns the list (7.2.1).
5. `notifications` has no delivery status; the delivery log of 7.2.3 needs one (new migration).
6. PRIVACY_BY_DESIGN §4.2 keeps the e-mail choice as a consent type `EMAIL_NOTIFICATIONS` "per type", while
   `notification_preferences` holds the same choice per event type and channel. One store, decided in the 7.2 PRD.
7. `openapi.yml` `NotificationPreferences` (`emailEnabled`, `browserEnabled`) and the `UserUpdateRequest` note do
   not match the per-type table; rewritten with 1.3.2.
8. DATA_DICTIONARY § notifications says no code writes the table; updated with 7.2.1.

## Technical notes

- Eighteen event records under `*/event/` and eleven listener classes; mail listeners are `@Async`
  `@TransactionalEventListener(AFTER_COMMIT)`. `MailDelivery` retries three times and then logs and drops; for the
  registry a final failure has to propagate so the publication stays incomplete.
- Spring Modulith's JDBC registry needs the `event_publication` table; Flyway owns the schema, so it comes as a
  `V` migration, with schema initialisation off.
- Module boundaries today: about 74 imports of another module's `repository` or `entity` package (gdpr 23, award 14,
  auth 13, delegation 10, authz 6, document 4, audit 2, user 2). `User` and `Organization` are read everywhere; the
  PRD decides between service lookups and a named interface for read-only types.
- `notifications` and `notification_preferences` exist since V011 and no code writes them; `notifications.expires_at`
  carries the one-year retention.
- Decision and overdue mails are bilingual (uk and en in one message, Feature 4.1 validation F-3); no per-user
  language is stored.
- The award page already polls the status every 60 s and pauses while hidden (`features/awards`); the centre reuses
  that approach.

## Risks

1. 7.1.2 touches every module; done as one refactor with no behaviour change, proven by the full gate, before any new
   feature code. If the fixes outgrow 8 points, the remaining violations are listed in the test as named exceptions
   and closed in a follow-up story.
2. Moving the mails onto the registry changes when a send counts as finished; a mail that fails three times is retried
   at the next start, so duplicates after a crash are possible. The PRD states the at-least-once behaviour.
3. Notification rows carry personal data (award titles, decisions, reviewer comments); they enter the export, the
   erasure rules of Epic 6 and the one-year retention.

## Quick links

- [ADR-006 Message queue](../architecture/adr/ADR-006-Message-Queue.md) · [ADR-022 Modular monolith](../architecture/adr/ADR-022-Modular-Monolith-Multi-Tenancy.md) · [ADR-023 Approval workflow](../architecture/adr/ADR-023-Approval-Workflow-Transitions.md)
- [Data dictionary § Notification domain](../database/DATA_DICTIONARY.md#5-notification-domain)
- [Privacy by design § Consent types](../security/PRIVACY_BY_DESIGN.md) · [User story US-005](../requirements/USER_STORIES.md)
- [Business requirements § Communication services](../requirements/BUSINESS_REQUIREMENTS.md) · [OpenAPI](../api/openapi.yml)

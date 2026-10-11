# ADR-006: Message Queue Selection

**Status**: Accepted as amended: Spring application events with the Spring Modulith event publication registry (Addendum 2026-10-09); Kafka only for a consumer outside the application  
**Date**: 2025-08-20  
**Author**: Stefan Kostyk  
**Stakeholders**: Project Architect, Development Team, Operations Team

---

## Context

The Award Monitoring & Tracking System requires a message queue for event-driven architecture, asynchronous processing, and decoupling of system components. This supports audit trails, notifications, and future microservices migration.

### Background
- Event-driven architecture for award lifecycle events
- Asynchronous processing for document parsing and notifications
- GDPR audit trail requirements through event sourcing
- Future microservices communication needs

---

## Decision

> Amended by the Addendum 2026-10-09: inside the application, events are Spring application events recorded in
> the Spring Modulith event publication registry. Kafka, as described below, remains the option for a consumer
> outside the application.

**Apache Kafka** has been selected as the message queue solution for the Award Monitoring & Tracking System.

### Chosen Approach
- **Message Broker**: Apache Kafka 3.0+
- **Integration**: Spring Kafka with Spring Boot auto-configuration
- **Topics**: Event-specific topics (award-events, user-events, document-events)
- **Deployment**: Single broker for development, cluster for production

### Rationale
- **Event Sourcing**: Perfect for maintaining audit trails and event history
- **Scalability**: Horizontal scaling and high throughput capabilities
- **Durability**: Persistent event storage with configurable retention
- **Spring Integration**: Excellent Spring Kafka integration
- **Enterprise Adoption**: Industry standard for event-driven systems

---

## Consequences

### Positive Consequences
- **Decoupling**: Loose coupling between system components
- **Audit Trail**: Complete event history for GDPR compliance
- **Scalability**: Async processing improves system responsiveness
- **Future-Proof**: Ready for microservices architecture migration

### Negative Consequences
- **Complexity**: Additional infrastructure component to manage
- **Learning Curve**: Kafka concepts and best practices
- **Resource Usage**: Memory and disk requirements for message storage

---

## Alternatives Considered

### Alternative 1: RabbitMQ
- **Pros**: Simpler setup, good for request-reply patterns, management UI
- **Cons**: Lower throughput, less suitable for event sourcing
- **Reason for Rejection**: Kafka better suited for event-driven architecture

### Alternative 2: Amazon SQS
- **Pros**: Managed service, no infrastructure overhead
- **Cons**: Vendor lock-in, higher costs, limited event sourcing features
- **Reason for Rejection**: Prefer open-source solution for portfolio

---

## Implementation Notes

### Technical Requirements
- **Kafka Version**: 3.0+ for latest features and performance
- **Spring Integration**: Spring Kafka 3.0+
- **Storage**: 10GB minimum for event retention
- **Memory**: 2GB heap for Kafka broker

### Event Schema
```json
{
  "eventType": "award.created",
  "eventId": "uuid",
  "timestamp": "2025-01-16T10:30:00Z",
  "source": "award-service",
  "subject": "award-123",
  "data": { /* event payload */ }
}
```

### Topic Configuration
- **award-events**: Award lifecycle events (created, approved, published)
- **user-events**: User management events (registered, updated)
- **document-events**: Document processing events (uploaded, parsed)

---

## Success Metrics

- **Message Throughput**: > 1000 messages/second
- **End-to-End Latency**: < 100ms for event processing
- **Consumer Lag**: < 5 seconds under normal load

---

## Related Documents

- **Tech Stack**: [Technology Stack Selection](../TECH_STACK.md)
- **Integration Patterns**: [Enterprise Integration Patterns](../INTEGRATION_PATTERNS.md)
- **External Resources**: [Apache Kafka Documentation](https://kafka.apache.org/documentation/)

---

## Addendum 2026-10: Implementation deferred

No message broker is deployed in Epics 1 and 2. Audit rows and award version snapshots are written
synchronously in the same transaction as the change they describe, so the history can never disagree with the
data. Emails that follow a change (verification, role and delegation changes, new device, data export) are
sent after the commit through in-process Spring application events (the `event` packages); in-app notifications
do not exist yet.

The choice between Apache Kafka and in-process Spring application events is made at the Epic 7 (notifications)
kickoff, when the first asynchronous consumer appears. The decision above remains the reference option.

---

## Addendum 2026-10-05: Spring events with a publication registry

Direction after the design review of 2026-10-04, confirmed at the Epic 7 kickoff (see Addendum 2026-10-09):

- Inside the application, events stay in-process Spring application events. Spring Modulith's event
  publication registry stores each event in PostgreSQL in the publishing transaction and marks it complete when
  the listener succeeds, so a crash or a failed email no longer loses an event, and unfinished publications are
  retried at start-up. No broker is operated for this.
- Kafka is introduced only when a consumer outside the application needs the events (another system, an
  extracted service under the ADR-022 criteria). Spring Modulith can then externalise selected events to Kafka
  without changing the publishers.
- Audit rows and award versions stay synchronous, in the transaction of the change.

Through Epic 4 the after-commit mails ran without the registry; a crash between the commit and the send lost
that mail. Story 7.1.1 introduced the registry (Addendum 2026-10-09).

---

## Addendum 2026-10-09: Decision, event publication registry

**Decision.** Events inside the application are Spring application events. Every `@TransactionalEventListener`
running after the commit is recorded by the Spring Modulith event publication registry (`spring-modulith-starter-jdbc`
1.4, table `event_publication`, migration V036): one row per listener is written in the publishing transaction and
deleted when the listener returns normally (`spring.modulith.events.completion-mode: delete`). A rollback leaves no
row and runs no listener.

**Delivery is at least once.** `MailDelivery` tries a mail three times and then throws `MailNotDeliveredException`,
so the publication stays incomplete. The job `PublicationRetry` runs every 10 minutes (first run 1 minute after the
start) and resubmits incomplete publications older than 10 minutes and younger than 24 hours; a restart needs no
other step, because the first run picks up what an earlier process left. Older publications are no longer
resubmitted and are reported by one error line per run; after 30 days they are deleted with a warning. A crash
after the send but before the row is deleted sends the mail a second time; that duplicate is accepted.
Settings: `app.events.retry-interval`, `retry-after`, `give-up-after`, `keep-failed`, `first-run`. Gauges
`award.events.incomplete` and `award.events.abandoned` in `/actuator/prometheus`.

**Transient events.** Events carrying a one-time link (`VerificationRequested`, `PasswordResetRequested`,
`EmailChangeRequested`, `EmailChanged`, `NewDeviceSignedIn`) are never stored: a link at rest would be a usable
credential. They are published through `AfterCommit` once the transaction commits and handled by plain
`@EventListener`s; a failed send is logged and the user requests a new link. `AccountLocked` is published outside a
transaction and stays a plain listener. `ObjectStored` keeps its after-rollback listener, which the registry does
not record.

**Rules for new events.** A registry event must serialise to JSON and back with the application's `ObjectMapper`
(one test per type) and must not carry a secret. Listener class and method names are the registry key: renaming
or moving a listener with incomplete rows orphans them, so such a change ships when the table is empty.

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2025-08-20 | Stefan Kostyk | Initial version | Document creation |
| 2026-10-01 | Stefan Kostyk | Addendum: implementation deferred | Documentation sync after Epic 2 |
| 2026-10-05 | Stefan Kostyk | Addendum: Spring events with a publication registry, Kafka for external consumers | Design review of 2026-10-04 |
| 2026-10-09 | Stefan Kostyk | Epic 4 mails use after-commit listeners without the registry | Documentation sync after Epic 4 |
| 2026-10-09 | Stefan Kostyk | Addendum: decision for the event publication registry, at-least-once delivery, transient events | Story 7.1.1 |

---

**Document Status**: Approved  
**Next Review Date**: 2026-02-20  
**ADR Category**: Technology 
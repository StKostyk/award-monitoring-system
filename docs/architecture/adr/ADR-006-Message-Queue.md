# ADR-006: Message Queue Selection

**Status**: Accepted (implementation deferred to Epic 7; Spring events first, see Addendum 2026-10-05)  
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

Direction after the design review of 2026-10-04, to be confirmed at the Epic 7 kickoff:

- Inside the application, events stay in-process Spring application events. Spring Modulith's event
  publication registry stores each event in PostgreSQL in the publishing transaction and marks it complete when
  the listener succeeds, so a crash or a failed email no longer loses an event, and unfinished publications are
  retried at start-up. No broker is operated for this.
- Kafka is introduced only when a consumer outside the application needs the events (another system, an
  extracted service under the ADR-022 criteria). Spring Modulith can then externalise selected events to Kafka
  without changing the publishers.
- Audit rows and award versions stay synchronous, in the transaction of the change.

As built through Epic 4: the mails of decisions, corrections and overdue notices (`DecisionMails`, `CorrectionMails`,
`OverdueMails`) use after-commit listeners like those of Epics 1 and 2, without the registry; a crash between the
commit and the send loses that mail. The registry is still to be confirmed at the Epic 7 kickoff.

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2025-08-20 | Stefan Kostyk | Initial version | Document creation |
| 2026-10-01 | Stefan Kostyk | Addendum: implementation deferred | Documentation sync after Epic 2 |
| 2026-10-05 | Stefan Kostyk | Addendum: Spring events with a publication registry, Kafka for external consumers | Design review of 2026-10-04 |
| 2026-10-09 | Stefan Kostyk | Epic 4 mails use after-commit listeners without the registry | Documentation sync after Epic 4 |

---

**Document Status**: Approved  
**Next Review Date**: 2026-02-20  
**ADR Category**: Technology 
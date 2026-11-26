# ADR-024: Audit Trail with Guarded Redaction

**Status**: Accepted  
**Date**: 2026-10-16  
**Author**: Stefan Kostyk  
**Stakeholders**: GDPR officer, system administrators, Thesis Supervisor

---

## Context

`audit_logs` has been append-only since V009: two rules turned every `UPDATE` and `DELETE` into nothing. The
trail holds personal data of the people it describes: addresses, names, IP addresses, user agents and full row
snapshots written by the table triggers (`fn_audit_trigger`). When a person exercises the right to erasure
(Art. 17, Feature 6.1.5), the account row is scrubbed, but the trail would still identify them, and a rule that
silently ignores updates cannot be relaxed for one purpose only.

### Background
- The trail is evidence: who did what and when must survive an erasure (Art. 17(3)(b) and (e)), and counts of
  events feed the security reports.
- The rules were already dropped and recreated once in V026 to remove password hashes from `users` snapshots.
- An erasure runs in one transaction across every module (PRD 6.1 §7 decision 9); the redaction must join it and
  roll back with it.

### Assumptions
- The application and the migrations use the same database role; the guard protects against mistakes and
  ordinary code paths, not against someone who can run arbitrary SQL as that role.

---

## Decision

### Chosen Approach
- **Guard trigger instead of rules (V043).** `trg_audit_logs_guard` (`BEFORE UPDATE OR DELETE … FOR EACH ROW`,
  inherited by every partition) raises on every `DELETE` and on every `UPDATE` unless the transaction-local
  setting `app.audit_redaction` is `on` and only `old_values`, `new_values`, `ip_address` and `user_agent`
  change. An attempt now fails loudly instead of being ignored.
- **One redaction function.** `fn_redact_audit_subject(p_user_id, p_names, p_entities)` (`SECURITY DEFINER`,
  fixed `search_path`) sets the flag with `set_config(…, true)`, applies the redaction map of PRD 6.1 §8.4, resets
  the flag, writes one `AUDIT_REDACTED` row with the counts and returns them. Nothing else sets the flag.
- **Values, not rows.** Action, entity type and id, actor id, time and `changed_fields` stay; personal keys
  become `"[erased]"`, snapshots of awards, requests, decisions and documents deleted at the erasure become
  `{"erased": true}`, the person's names in the listed notifications of others become the pseudonym, and the
  person's own rows lose their IP address and user agent.
- **Last step of the erasure.** `AuditRedaction` (module `audit`) flushes pending audit rows and calls the
  function after every contributor and the `ERASURE_COMPLETED` row, so the rows written by the erasure itself
  are redacted too.

### Rationale
- Keeping every row preserves the evidential value and the counts; removing values removes the identity.
- A trigger can tell a guarded redaction from any other change; a rule cannot.
- A database function keeps the redaction in the same transaction as the erasure and in one reviewed place.

---

## Consequences

### Positive Consequences
- Erasure covers the trail without breaking its integrity; the redaction itself is audited.
- Accidental updates and deletes of audit rows now raise instead of passing unnoticed.

### Negative Consequences
- Test teardowns can no longer delete audit rows; tests must use unique accounts and filter by them.
- The redaction map must grow with new personal keys in audit details (kept in `fn_redact_audit_values`).

### Neutral Consequences
- Partition maintenance (`fn_create_audit_partition`) is unaffected: partitions inherit the trigger.

---

## Alternatives Considered

### Alternative 1: Delete the person's audit rows
- **Pros**: Simple, nothing left to identify.
- **Cons**: Destroys evidence the university must keep and changes security counts.
- **Rejection Reason**: Conflicts with Art. 17(3) exemptions and the retention of DATA_GOVERNANCE §2.2.

### Alternative 2: Pseudonymise at read time
- **Pros**: The stored trail never changes.
- **Cons**: The personal data stays in the database and in every backup; every reader must apply the mapping.
- **Rejection Reason**: Does not erase anything.

### Alternative 3: Keep the rules and drop them for each redaction
- **Pros**: No new trigger.
- **Cons**: DDL inside the erasure transaction, locks the table, and other updates stay silently ignored.
- **Rejection Reason**: Heavier and less safe than a guard that checks each row.

---

## Implementation Notes

- V043: drops the rules, adds `fn_audit_logs_guard`, `trg_audit_logs_guard`, `fn_redact_audit_values`,
  `fn_jsonb_replace_strings`, `fn_redact_audit_subject`, and drops the unused `fn_anonymize_user_data` (also
  removed from `R__create_functions.sql`).
- Personal keys redacted: `email_address`, `email`, `new_email`, `old_email`, `new_email_address`, `newEmail`,
  `oldEmail`, `restoredEmail`, `replacedEmail`, `first_name`, `last_name`, `firstName`, `lastName`,
  `password_hash`, `ip_address`, `ip`, `user_agent`, `userAgent`, `payload`.
- Tests: `ErasureIT` (AC-5.8, AC-5.9), `SchemaIT` (V026 path now raises on a direct update).
- V045 (6.2.3) adds two more guarded functions of the same shape for retention.
  `fn_drop_expired_audit_partitions(p_before, p_held)` detaches and drops whole monthly partitions that ended before the period unless they hold a row by or about
  a held person (the person keys above); `DELETE` of single rows still raises, and the drop is recorded as
  `AUDIT_PARTITIONS_DROPPED`. `fn_purge_consent_records(p_before, p_held)` sets `app.consent_retention` for the
  transaction, which `fn_consent_records_guard` honours for `DELETE` only, and deletes the withdrawn `ANALYTICS`
  chains and the records of accounts erased before the period. Both are `SECURITY DEFINER` with `EXECUTE`
  revoked from `PUBLIC`; the security note below applies to them as well.

---

## Compliance & Quality

### Security Implications
- The function is `SECURITY DEFINER` with `search_path = pg_catalog, public, pg_temp`.
- The flag is transaction-local, so it cannot leak into pooled connections.
- `EXECUTE` on `fn_redact_audit_subject` is revoked from `PUBLIC`; only its owner, the application role, may
  call it. Since that role can also set the flag itself, the guard prevents accidents and ordinary code paths
  from changing the trail; it is not a defence against a compromised application role (a separate migration
  role is the step for production hardening, Epic 9).

### Maintainability
- One function holds the redaction map; DATA_DICTIONARY §4.1 and PRIVACY_BY_DESIGN §5.2 describe it.

---

## Related Documents

- **PRD**: [Feature 6.1](../../features/epic-06/feature-6.1-consent-data-subject-rights.md) §7 decision 11, §8.4
- **Other ADRs**: [ADR-022 Modular Monolith](./ADR-022-Modular-Monolith-Multi-Tenancy.md) (erasure contributors)
- **Compliance**: [DATA_GOVERNANCE.md](../../compliance/DATA_GOVERNANCE.md) §2.3,
  [PRIVACY_BY_DESIGN.md](../../security/PRIVACY_BY_DESIGN.md) §5.2

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2026-10-16 | Stefan Kostyk | Initial version (accepted) | Story 6.1.5 (SCRUM-90) |
| 2026-10-18 | Stefan Kostyk | Retention functions of V045 | Story 6.2.3 (SCRUM-95) |

---

**Document Status**: Approved  
**Next Review Date**: When a new personal key appears in audit details

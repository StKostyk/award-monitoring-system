-- V026__review_decisions.sql
-- Description: Impact scores of the revised recognition levels; password hashes removed from old audit rows
-- Author: Stefan Kostyk
-- Date: 2026-10-05

-- ============================================================================
-- AWARDS.IMPACT_SCORE
-- ============================================================================
-- Recognition levels were re-ranked (UNIVERSITY 50, LOCAL 60; the others keep
-- their score). Submitted awards take the base score of their category's level
-- again; drafts get theirs at submission. The scores are spelled out because
-- repeatable migrations (fn_calculate_impact_score) run after this one.
-- ============================================================================

UPDATE awards a
SET impact_score = CASE c.level
        WHEN 'UNIVERSITY' THEN 50
        WHEN 'LOCAL' THEN 60
    END
FROM award_categories c
WHERE c.category_id = a.category_id
  AND c.level IN ('UNIVERSITY', 'LOCAL')
  AND a.status <> 'DRAFT';

-- ============================================================================
-- AUDIT_LOGS: PASSWORD HASHES IN USERS SNAPSHOTS
-- ============================================================================
-- Since V022 the audit trigger leaves password_hash out of users snapshots.
-- Rows written before that still hold historical hashes; they are removed from
-- old_values, new_values and changed_fields. The rules that keep audit_logs
-- append-only are dropped for this statement only and recreated unchanged.
-- ============================================================================

DROP RULE audit_logs_no_update ON audit_logs;

UPDATE audit_logs
SET old_values = old_values - 'password_hash',
    new_values = new_values - 'password_hash',
    changed_fields = array_remove(changed_fields, 'password_hash')
WHERE entity_type = 'users'
  AND (old_values ? 'password_hash'
       OR new_values ? 'password_hash'
       OR 'password_hash' = ANY (changed_fields));

CREATE RULE audit_logs_no_update AS ON UPDATE TO audit_logs
    DO INSTEAD NOTHING;

-- ============================================================================
-- ONE_TIME_TOKENS.NEW_EMAIL_ADDRESS FOR SECURITY_REVOKE
-- ============================================================================
-- After a sign-in address change, "this was not me" links carry the previous
-- address: redeeming one moves the account back to it before the revocation.
-- EMAIL_CHANGE still always carries the requested address.
-- ============================================================================

ALTER TABLE one_time_tokens DROP CONSTRAINT ck_one_time_tokens_new_email;

ALTER TABLE one_time_tokens ADD CONSTRAINT ck_one_time_tokens_new_email CHECK (
    CASE purpose
        WHEN 'EMAIL_CHANGE' THEN new_email_address IS NOT NULL
        WHEN 'SECURITY_REVOKE' THEN TRUE
        ELSE new_email_address IS NULL
    END
);

COMMENT ON COLUMN one_time_tokens.new_email_address IS
    'EMAIL_CHANGE: requested sign-in address (lower case, always present); SECURITY_REVOKE: the address to '
    'restore when the link follows an address change (otherwise null)';

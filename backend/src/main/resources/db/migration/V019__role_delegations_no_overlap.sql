-- V019__role_delegations_no_overlap.sql
-- Description: One standing delegation of a role per organization and delegator at a time
-- Author: Stefan Kostyk
-- Date: 2026-09-25

-- ============================================================================
-- The application refuses a delegation whose period overlaps one the same
-- person already gave for the same role in the same organization. Two requests
-- arriving at once both read an empty overlap set, so the rule needs a
-- constraint behind it. An exclusion constraint over the inclusive date range
-- says exactly that; btree_gist supplies the equality operators for the
-- scalar columns beside the range.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE role_delegations ADD CONSTRAINT uk_role_delegations_standing EXCLUDE USING gist (
    delegator_id WITH =,
    role_type WITH =,
    organization_id WITH =,
    daterange(valid_from, valid_to, '[]') WITH &&
) WHERE (revoked_at IS NULL);

COMMENT ON CONSTRAINT uk_role_delegations_standing ON role_delegations IS 'A role is lent by one person in one organization to one colleague at a time; delegations taken back are exempt so the period can be lent again';

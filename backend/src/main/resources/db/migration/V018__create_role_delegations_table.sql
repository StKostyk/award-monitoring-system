-- V018__create_role_delegations_table.sql
-- Description: Create role_delegations table - temporary handover of approval authority
-- Author: Stefan Kostyk
-- Date: 2026-09-24

-- ============================================================================
-- ROLE_DELEGATIONS TABLE
-- ============================================================================
-- An approval role held by one user is lent to another for a bounded period.
-- The delegate borrows the reading and approving permissions of the role only;
-- user management never travels with a delegation and a delegation is never
-- passed on further. Nothing is deleted: a delegation taken back keeps its row
-- with revoked_at and revoked_by, and an expired one simply stops matching the
-- current date.
-- ============================================================================

CREATE TABLE role_delegations (
    -- Primary Key
    delegation_id BIGSERIAL,

    -- References
    delegator_id BIGINT NOT NULL,
    delegate_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,

    -- Delegated Authority
    role_type VARCHAR(30) NOT NULL,
    reason VARCHAR(500),

    -- Temporal Validity
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,

    -- Audit Columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by BIGINT,

    -- Constraints
    CONSTRAINT pk_role_delegations PRIMARY KEY (delegation_id),
    CONSTRAINT fk_role_delegations_delegator FOREIGN KEY (delegator_id)
        REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_role_delegations_delegate FOREIGN KEY (delegate_id)
        REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_role_delegations_organizations FOREIGN KEY (organization_id)
        REFERENCES organizations(org_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_role_delegations_revoked_by FOREIGN KEY (revoked_by)
        REFERENCES users(user_id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_role_delegations_parties CHECK (
        delegator_id <> delegate_id
    ),
    CONSTRAINT ck_role_delegations_dates CHECK (
        valid_to >= valid_from AND valid_to <= valid_from + 90
    ),
    CONSTRAINT ck_role_delegations_type CHECK (
        role_type IN ('FACULTY_SECRETARY', 'DEAN', 'RECTOR_SECRETARY', 'RECTOR')
    ),
    CONSTRAINT ck_role_delegations_revocation CHECK (
        (revoked_at IS NULL AND revoked_by IS NULL) OR (revoked_at IS NOT NULL)
    )
);

-- ============================================================================
-- INDEXES
-- ============================================================================

-- Token issuing reads the delegations of one delegate that have not been taken back
CREATE INDEX idx_role_delegations_delegate ON role_delegations(delegate_id, valid_from, valid_to)
    WHERE revoked_at IS NULL;

-- The delegator's own page and the overlap check
CREATE INDEX idx_role_delegations_delegator ON role_delegations(delegator_id);

-- Revoking a role takes back what that role had delegated
CREATE INDEX idx_role_delegations_role ON role_delegations(delegator_id, role_type, organization_id)
    WHERE revoked_at IS NULL;

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE role_delegations IS 'Temporary handover of an approval role to a colleague, bounded to at most 90 days.';

COMMENT ON COLUMN role_delegations.delegation_id IS 'Unique delegation identifier';
COMMENT ON COLUMN role_delegations.delegator_id IS 'User who holds the role and lends its approval authority';
COMMENT ON COLUMN role_delegations.delegate_id IS 'User who borrows the approval authority';
COMMENT ON COLUMN role_delegations.organization_id IS 'Organizational scope of the delegated role';
COMMENT ON COLUMN role_delegations.role_type IS 'Delegated approval role: FACULTY_SECRETARY, DEAN, RECTOR_SECRETARY, RECTOR';
COMMENT ON COLUMN role_delegations.reason IS 'Why the authority was handed over, shown to the delegate';
COMMENT ON COLUMN role_delegations.valid_from IS 'First day the delegated authority applies';
COMMENT ON COLUMN role_delegations.valid_to IS 'Last day the delegated authority applies; mandatory and at most 90 days after valid_from';
COMMENT ON COLUMN role_delegations.created_at IS 'Delegation creation timestamp';
COMMENT ON COLUMN role_delegations.revoked_at IS 'When the delegation was taken back before its last day (NULL while it stands)';
COMMENT ON COLUMN role_delegations.revoked_by IS 'Who took the delegation back';

COMMENT ON INDEX idx_role_delegations_delegate IS 'Supports issuing a token: the delegations a user has received that have not been taken back.';
COMMENT ON INDEX idx_role_delegations_role IS 'Supports revoking the delegations made by a role when that role itself is revoked.';

-- V051__people_without_account.sql
-- Description: People without an account (story 2.5.1). A faculty secretary or dean can add a colleague who has not
--              registered; the row has status UNCLAIMED, records who added it and when the invitation went. A person
--              without a known address gets a placeholder unclaimed-<uuid>@unclaimed.invalid, so the address column
--              stays NOT NULL and unique.

ALTER TABLE users
    ADD COLUMN added_by_id BIGINT,
    ADD COLUMN invited_at TIMESTAMPTZ,
    ADD CONSTRAINT fk_users_added_by FOREIGN KEY (added_by_id) REFERENCES users (user_id) ON DELETE SET NULL;

ALTER TABLE users DROP CONSTRAINT ck_users_status;
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (
    account_status IN ('PENDING', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'RETIRED', 'MEMORIAL', 'DELETED', 'UNCLAIMED')
);

CREATE INDEX idx_users_unclaimed ON users (organization_id, last_name) WHERE account_status = 'UNCLAIMED';

COMMENT ON COLUMN users.added_by_id IS 'Faculty secretary or dean who added the person without an account';
COMMENT ON COLUMN users.invited_at IS 'When the last invitation to register went to the person';
COMMENT ON INDEX idx_users_unclaimed IS 'People without an account by department, for the directory and the duplicate check';

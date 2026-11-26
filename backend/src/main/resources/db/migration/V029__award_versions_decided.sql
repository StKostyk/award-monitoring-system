-- A reviewer decision that changes the award's status (approved, returned to draft, rejected) is a version of
-- the award, so its history shows who moved it and when.

ALTER TABLE award_versions DROP CONSTRAINT ck_award_versions_action;
ALTER TABLE award_versions ADD CONSTRAINT ck_award_versions_action
    CHECK (action IN ('BASELINE', 'CREATED', 'UPDATED', 'SUBMITTED', 'DECIDED'));

COMMENT ON COLUMN award_versions.action IS
    'BASELINE (state at V023), CREATED, UPDATED, SUBMITTED, DECIDED (a reviewer decision changed the status)';

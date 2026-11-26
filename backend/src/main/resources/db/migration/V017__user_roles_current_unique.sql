-- V017__user_roles_current_unique.sql
-- Description: One open-ended assignment of a role per user and organisation
-- Author: Stefan Kostyk
-- Date: 2026-09-22

ALTER TABLE user_roles DROP CONSTRAINT ck_user_roles_dates;

ALTER TABLE user_roles ADD CONSTRAINT ck_user_roles_dates CHECK (
    valid_to IS NULL OR valid_to >= valid_from - 1
);

UPDATE user_roles older
SET valid_to = GREATEST(older.valid_from - 1, CURRENT_DATE - 1)
WHERE older.valid_to IS NULL
  AND EXISTS (
    SELECT 1 FROM user_roles newer
    WHERE newer.valid_to IS NULL
      AND newer.user_id = older.user_id
      AND newer.role_type = older.role_type
      AND newer.organization_id = older.organization_id
      AND newer.user_role_id > older.user_role_id
  );

CREATE UNIQUE INDEX uk_user_roles_current ON user_roles (user_id, role_type, organization_id)
    WHERE valid_to IS NULL;

COMMENT ON INDEX uk_user_roles_current IS 'A role is held at most once per organisation while it is open-ended; revocation sets valid_to to yesterday instead of deleting the row';

COMMENT ON COLUMN user_roles.valid_to IS 'Role effective end date (NULL means open-ended); revocation sets it to yesterday so the history is kept, which is why a role revoked on the day it began may end one day before it started';

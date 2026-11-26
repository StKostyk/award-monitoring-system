-- V015__users_email_unique_lower.sql
-- Description: Addresses are unique regardless of letter case
-- Author: Stefan Kostyk
-- Date: 2026-09-21

UPDATE users SET email_address = LOWER(email_address) WHERE email_address <> LOWER(email_address);

CREATE UNIQUE INDEX uk_users_email_lower ON users (LOWER(email_address));

COMMENT ON INDEX uk_users_email_lower IS 'One account per address regardless of letter case';

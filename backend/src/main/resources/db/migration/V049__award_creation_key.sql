-- V049__award_creation_key.sql
-- Description: A new award is created once (story 8.1.4): the form sends a key generated in the browser with
--              POST /awards, and a repeat of that request with the same key answers the award it created.

ALTER TABLE awards ADD COLUMN creation_key UUID;

CREATE UNIQUE INDEX ux_awards_creation_key ON awards (user_id, creation_key) WHERE creation_key IS NOT NULL;

COMMENT ON COLUMN awards.creation_key IS 'Idempotency-Key of the POST /awards request that created the draft; '
    'unique per owner, null for awards created without one';

-- V027__unit_award_recipients.sql
-- Description: Awards received by a faculty or department, entered by its secretary or dean
-- Author: Stefan Kostyk
-- Date: 2026-10-06

-- ============================================================================
-- An award without recipient_org_id is personal: its owner received it. With
-- recipient_org_id the unit received it, and the owner is the faculty
-- secretary or dean who entered and submitted it. The award belongs to the
-- unit (organization_id), so scoped reads and the reviewer routing follow the
-- unit; ck_awards_recipient keeps both columns equal.
-- ============================================================================

ALTER TABLE awards
    ADD COLUMN recipient_org_id BIGINT,
    ADD CONSTRAINT fk_awards_recipient_organizations FOREIGN KEY (recipient_org_id)
        REFERENCES organizations(org_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT ck_awards_recipient CHECK (recipient_org_id IS NULL OR recipient_org_id = organization_id);

CREATE INDEX idx_awards_recipient_date ON awards(recipient_org_id, award_date) WHERE recipient_org_id IS NOT NULL;

COMMENT ON COLUMN awards.recipient_org_id IS 'Faculty or department that received the award; NULL for a personal award';
COMMENT ON COLUMN awards.user_id IS 'Owner: the recipient of a personal award, the person who entered a unit award';
COMMENT ON CONSTRAINT ck_awards_recipient ON awards IS 'A unit award belongs to the unit that received it';
COMMENT ON INDEX idx_awards_recipient_date IS 'Duplicate check among the awards of one unit';

-- V020__award_drafts.sql
-- Description: Award drafts, the owner's department on the award and the award date on the Kyiv calendar
-- Author: Stefan Kostyk
-- Date: 2026-09-28

-- ============================================================================
-- An award is entered as a draft and completed later, so the fields a
-- submission needs are required only outside DRAFT. A title in one language
-- is enough. The award keeps the department of its owner at submission, so
-- scoped reads and the reviewer routing do not follow a later transfer.
-- "Today" for the award date is the Kyiv calendar day, as in the application.
-- ============================================================================

ALTER TABLE awards ADD COLUMN organization_id BIGINT;

UPDATE awards a SET organization_id = u.organization_id FROM users u WHERE u.user_id = a.user_id;

ALTER TABLE awards
    ALTER COLUMN organization_id SET NOT NULL,
    ALTER COLUMN title DROP NOT NULL,
    ALTER COLUMN category_id DROP NOT NULL,
    ALTER COLUMN awarding_organization DROP NOT NULL,
    ALTER COLUMN award_date DROP NOT NULL,
    ADD CONSTRAINT fk_awards_organizations FOREIGN KEY (organization_id)
        REFERENCES organizations(org_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT ck_awards_title CHECK (title IS NOT NULL OR title_uk IS NOT NULL),
    ADD CONSTRAINT ck_awards_complete CHECK (
        status = 'DRAFT'
        OR (category_id IS NOT NULL AND awarding_organization IS NOT NULL AND award_date IS NOT NULL)
    ),
    DROP CONSTRAINT ck_awards_date,
    ADD CONSTRAINT ck_awards_date CHECK (award_date <= (now() AT TIME ZONE 'Europe/Kyiv')::date);

CREATE INDEX idx_awards_organization_status ON awards(organization_id, status);

COMMENT ON COLUMN awards.organization_id IS 'Department of the owner, refreshed at submission and kept afterwards';
COMMENT ON COLUMN awards.title IS 'Award title (English); a draft or an award may have the Ukrainian title only';
COMMENT ON COLUMN awards.award_date IS 'Date when award was granted (not after today in Europe/Kyiv); empty only in a draft';
COMMENT ON CONSTRAINT ck_awards_complete ON awards IS 'Category, awarding organization and date are required once the award leaves DRAFT';
COMMENT ON INDEX idx_awards_organization_status IS 'Scoped reads of submitted awards by organization';

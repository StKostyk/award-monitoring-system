-- V021__award_category_keywords.sql
-- Description: Keywords of award categories for the category suggestion
-- Author: Stefan Kostyk
-- Date: 2026-09-29

-- ============================================================================
-- Lower-case Ukrainian and English stems that point to a category. A stem
-- matches the start of a word; a keyword of several stems matches consecutive
-- words. Stems of a root category name its recognition level. The values are
-- reference data written by R__seed_award_categories.sql.
-- ============================================================================

ALTER TABLE award_categories ADD COLUMN keywords TEXT[] NOT NULL DEFAULT '{}';

CREATE INDEX idx_award_categories_keywords ON award_categories USING GIN (keywords);

COMMENT ON COLUMN award_categories.keywords IS 'Lower-case Ukrainian and English stems that suggest the category';
COMMENT ON INDEX idx_award_categories_keywords IS 'Lookup of categories by keyword';

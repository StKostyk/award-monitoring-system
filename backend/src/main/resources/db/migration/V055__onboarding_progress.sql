-- ============================================================================
-- V055: Getting-started progress (Feature 9.7, story 9.7.9)
-- ============================================================================
-- The steps a person marked done, whether the checklist was hidden and whether
-- the screen tour was seen, kept beside the dashboard choice of the same person.
-- Steps decided by the data (notice, first award, delegation) are not stored.
-- A constant default fills the existing rows; the table has no audit trigger.
-- ============================================================================

ALTER TABLE dashboard_preferences ADD COLUMN onboarding JSONB NOT NULL DEFAULT '{}';

COMMENT ON COLUMN dashboard_preferences.onboarding IS 'Getting-started progress: {"done": [step keys], "dismissed": bool, "tourSeen": bool}; empty until saved';

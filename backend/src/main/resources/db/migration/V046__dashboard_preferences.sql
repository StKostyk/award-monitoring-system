-- ============================================================================
-- V046: Widget choice of the personal dashboard (Feature 5.1, story 5.1.2)
-- ============================================================================
-- One row per account that changed the default; no row means every widget is
-- shown. A display choice, so the table carries no audit trigger. The row is
-- personal data: it is exported and deleted by the erasure.
-- ============================================================================

CREATE TABLE dashboard_preferences (
    user_id BIGINT,
    hidden_widgets TEXT[] NOT NULL DEFAULT '{}',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_dashboard_preferences PRIMARY KEY (user_id),
    CONSTRAINT fk_dashboard_preferences_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

COMMENT ON TABLE dashboard_preferences IS 'Widgets a person hid on the personal dashboard';
COMMENT ON COLUMN dashboard_preferences.user_id IS 'The account the choice belongs to';
COMMENT ON COLUMN dashboard_preferences.hidden_widgets IS
    'Hidden widget names (SUMMARY, TIMELINE, YEAR_TREND, BY_CATEGORY, BY_LEVEL, RANK), no duplicates';
COMMENT ON COLUMN dashboard_preferences.updated_at IS 'When the choice was last saved';

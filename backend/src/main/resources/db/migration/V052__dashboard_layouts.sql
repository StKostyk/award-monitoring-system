-- ============================================================================
-- V052: Colour scheme and dashboard layouts on the account (Feature 9.4, story 9.4.1)
-- ============================================================================
-- The row now holds the colour scheme and, per dashboard, the widget order and
-- the hidden widgets. The hidden lists of V046 move to the MY_AWARDS layout with
-- the widget names of 9.4: SUMMARY becomes KPI, YEAR_TREND becomes TREND,
-- BY_LEVEL becomes LEVEL_STAIRS, BY_CATEGORY and RANK keep their names, and
-- TIMELINE, which has no counterpart, is dropped. The table still carries no
-- audit trigger.
-- ============================================================================

ALTER TABLE dashboard_preferences
    ADD COLUMN color_scheme VARCHAR(10) NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN layouts JSONB NOT NULL DEFAULT '{}',
    ADD CONSTRAINT ck_dashboard_preferences_color_scheme CHECK (color_scheme IN ('SYSTEM', 'LIGHT', 'DARK'));

UPDATE dashboard_preferences p
   SET layouts = jsonb_build_object('MY_AWARDS', jsonb_build_object('order', '[]'::jsonb, 'hidden', mapped.hidden))
  FROM (
        SELECT d.user_id, jsonb_agg(m.new_name ORDER BY m.position) AS hidden
          FROM dashboard_preferences d
         CROSS JOIN LATERAL unnest(d.hidden_widgets) AS old(name)
          JOIN (VALUES ('SUMMARY', 'KPI', 1), ('YEAR_TREND', 'TREND', 2), ('BY_LEVEL', 'LEVEL_STAIRS', 3),
                       ('BY_CATEGORY', 'BY_CATEGORY', 5), ('RANK', 'RANK', 6)) AS m(old_name, new_name, position)
            ON m.old_name = old.name
         GROUP BY d.user_id
       ) AS mapped
 WHERE p.user_id = mapped.user_id;

ALTER TABLE dashboard_preferences DROP COLUMN hidden_widgets;

COMMENT ON TABLE dashboard_preferences IS 'Colour scheme and dashboard layouts a person chose';
COMMENT ON COLUMN dashboard_preferences.color_scheme IS 'SYSTEM follows the browser, LIGHT or DARK fixes the scheme';
COMMENT ON COLUMN dashboard_preferences.layouts IS
    'Per dashboard (MY_AWARDS, HALL_OF_FAME, COMMAND_CENTRE): {"order": [widget...], "hidden": [widget...]}; '
    'a missing dashboard keeps its default';

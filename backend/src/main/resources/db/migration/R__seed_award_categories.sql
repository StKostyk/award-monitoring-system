-- R__seed_award_categories.sql
-- Description: Seed data for award categories - Idempotent
-- Author: Stefan Kostyk
-- Note: This migration re-runs whenever the file changes

-- ============================================================================
-- SEED AWARD CATEGORIES
-- ============================================================================
-- Hierarchical award classification by recognition level
-- Using UPSERT pattern for idempotency
-- ============================================================================

-- Temporarily disable triggers for bulk insert
ALTER TABLE award_categories DISABLE TRIGGER trg_award_categories_audit;

-- Clear and reseed (reference data approach)
TRUNCATE TABLE award_categories CASCADE;

-- Reset sequence
ALTER SEQUENCE award_categories_category_id_seq RESTART WITH 1;

-- ============================================================================
-- INTERNATIONAL LEVEL AWARDS
-- ============================================================================

INSERT INTO award_categories (category_id, name, name_uk, description, level, parent_category_id, sort_order, is_active, is_system)
VALUES 
    (1, 'International Awards', 'Міжнародні нагороди', 'Awards and recognition at international level', 'INTERNATIONAL', NULL, 100, TRUE, TRUE),
    (2, 'International Research Award', 'Міжнародна дослідницька нагорода', 'Recognition for outstanding international research contributions', 'INTERNATIONAL', 1, 101, TRUE, TRUE),
    (3, 'International Conference Best Paper', 'Найкраща стаття міжнародної конференції', 'Best paper award at international conferences', 'INTERNATIONAL', 1, 102, TRUE, TRUE),
    (4, 'International Grant/Fellowship', 'Міжнародний грант/стипендія', 'International research grants and fellowships', 'INTERNATIONAL', 1, 103, TRUE, TRUE),
    (5, 'International Collaboration Award', 'Нагорода за міжнародну співпрацю', 'Recognition for international academic collaboration', 'INTERNATIONAL', 1, 104, TRUE, TRUE);

-- ============================================================================
-- NATIONAL LEVEL AWARDS
-- ============================================================================

INSERT INTO award_categories (category_id, name, name_uk, description, level, parent_category_id, sort_order, is_active, is_system)
VALUES 
    (10, 'National Awards', 'Національні нагороди', 'Awards and recognition at national level', 'NATIONAL', NULL, 200, TRUE, TRUE),
    (11, 'National Science Award', 'Національна наукова премія', 'State-level science and research awards', 'NATIONAL', 10, 201, TRUE, TRUE),
    (12, 'State Prize', 'Державна премія', 'State prizes for outstanding achievements', 'NATIONAL', 10, 202, TRUE, TRUE),
    (13, 'Ministry Recognition', 'Відзнака міністерства', 'Recognition from government ministries', 'NATIONAL', 10, 203, TRUE, TRUE),
    (14, 'National Grant', 'Національний грант', 'National research grants and funding', 'NATIONAL', 10, 204, TRUE, TRUE),
    (15, 'National Teaching Excellence', 'Національна педагогічна майстерність', 'National recognition for teaching excellence', 'NATIONAL', 10, 205, TRUE, TRUE);

-- ============================================================================
-- UNIVERSITY LEVEL AWARDS
-- ============================================================================

INSERT INTO award_categories (category_id, name, name_uk, description, level, parent_category_id, sort_order, is_active, is_system)
VALUES 
    (20, 'University Awards', 'Університетські нагороди', 'Awards and recognition at university level', 'UNIVERSITY', NULL, 300, TRUE, TRUE),
    (21, 'University Excellence Award', 'Нагорода університетської досконалості', 'University-wide excellence recognition', 'UNIVERSITY', 20, 301, TRUE, TRUE),
    (22, 'Best Teacher Award', 'Нагорода найкращого викладача', 'University best teacher recognition', 'UNIVERSITY', 20, 302, TRUE, TRUE),
    (23, 'Research Achievement', 'Наукові досягнення', 'University research achievement awards', 'UNIVERSITY', 20, 303, TRUE, TRUE),
    (24, 'Innovation Award', 'Нагорода за інновації', 'University innovation and entrepreneurship', 'UNIVERSITY', 20, 304, TRUE, TRUE),
    (25, 'Service Excellence', 'Досконалість обслуговування', 'Outstanding service to university', 'UNIVERSITY', 20, 305, TRUE, TRUE);

-- ============================================================================
-- FACULTY LEVEL AWARDS
-- ============================================================================

INSERT INTO award_categories (category_id, name, name_uk, description, level, parent_category_id, sort_order, is_active, is_system)
VALUES 
    (30, 'Faculty Awards', 'Факультетські нагороди', 'Awards and recognition at faculty level', 'FACULTY', NULL, 400, TRUE, TRUE),
    (31, 'Faculty Teaching Award', 'Факультетська педагогічна нагорода', 'Faculty-level teaching excellence', 'FACULTY', 30, 401, TRUE, TRUE),
    (32, 'Faculty Research Recognition', 'Факультетське визнання досліджень', 'Faculty research achievements', 'FACULTY', 30, 402, TRUE, TRUE),
    (33, 'Faculty Service Award', 'Факультетська нагорода за службу', 'Outstanding service to faculty', 'FACULTY', 30, 403, TRUE, TRUE),
    (34, 'Faculty Mentorship Award', 'Нагорода за наставництво', 'Excellence in student mentorship', 'FACULTY', 30, 404, TRUE, TRUE);

-- ============================================================================
-- DEPARTMENT LEVEL AWARDS
-- ============================================================================

INSERT INTO award_categories (category_id, name, name_uk, description, level, parent_category_id, sort_order, is_active, is_system)
VALUES 
    (40, 'Department Awards', 'Кафедральні нагороди', 'Awards and recognition at department level', 'DEPARTMENT', NULL, 500, TRUE, TRUE),
    (41, 'Department Appreciation', 'Подяка кафедри', 'Department-level appreciation', 'DEPARTMENT', 40, 501, TRUE, TRUE),
    (42, 'Team Collaboration Award', 'Нагорода за командну співпрацю', 'Recognition for team collaboration', 'DEPARTMENT', 40, 502, TRUE, TRUE),
    (43, 'Mentorship Recognition', 'Визнання наставництва', 'Department mentorship recognition', 'DEPARTMENT', 40, 503, TRUE, TRUE),
    (44, 'Professional Development', 'Професійний розвиток', 'Professional development achievements', 'DEPARTMENT', 40, 504, TRUE, TRUE);

-- Reset sequence to next available
SELECT setval('award_categories_category_id_seq', COALESCE((SELECT MAX(category_id) FROM award_categories), 1));

-- Re-enable triggers
ALTER TABLE award_categories ENABLE TRIGGER trg_award_categories_audit;


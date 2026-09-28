-- R__seed_award_categories.sql
-- Description: Seed data for award categories - Idempotent
-- Author: Stefan Kostyk
-- Note: This migration re-runs whenever the file changes

-- ============================================================================
-- SEED AWARD CATEGORIES
-- ============================================================================
-- Hierarchical award classification, one root per recognition level.
-- Rows are upserted by category_id so awards keep their categories; system
-- categories that are no longer listed here are deactivated, never deleted.
-- ============================================================================

ALTER TABLE award_categories DISABLE TRIGGER trg_award_categories_audit;

DROP TABLE IF EXISTS seed_categories;

CREATE TEMPORARY TABLE seed_categories (
    category_id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    name_uk VARCHAR(100),
    description TEXT,
    level VARCHAR(20) NOT NULL,
    parent_category_id BIGINT,
    sort_order INTEGER NOT NULL
);

INSERT INTO seed_categories (category_id, name, name_uk, description, level, parent_category_id, sort_order)
VALUES
-- INTERNATIONAL
(1, 'International Awards', 'Міжнародні нагороди', 'Awards and recognition at international level', 'INTERNATIONAL', NULL, 100),
(2, 'International Research Award', 'Міжнародна дослідницька нагорода', 'Recognition for outstanding international research contributions', 'INTERNATIONAL', 1, 101),
(3, 'International Conference Best Paper', 'Найкраща стаття міжнародної конференції', 'Best paper award at international conferences', 'INTERNATIONAL', 1, 102),
(4, 'International Grant/Fellowship', 'Міжнародний грант/стипендія', 'International research grants and fellowships', 'INTERNATIONAL', 1, 103),
(5, 'International Collaboration Award', 'Нагорода за міжнародну співпрацю', 'Recognition for international academic collaboration', 'INTERNATIONAL', 1, 104),
-- NATIONAL
(10, 'National Awards', 'Національні нагороди', 'Awards and recognition at national level', 'NATIONAL', NULL, 200),
(11, 'National Science Award', 'Національна наукова премія', 'State-level science and research awards', 'NATIONAL', 10, 201),
(12, 'State Prize', 'Державна премія', 'State prizes for outstanding achievements', 'NATIONAL', 10, 202),
(13, 'Ministry Recognition', 'Відзнака міністерства', 'Recognition from government ministries', 'NATIONAL', 10, 203),
(14, 'National Grant', 'Національний грант', 'National research grants and funding', 'NATIONAL', 10, 204),
(15, 'National Teaching Excellence', 'Національна педагогічна майстерність', 'National recognition for teaching excellence', 'NATIONAL', 10, 205),
-- REGIONAL
(80, 'Regional Awards', 'Регіональні нагороди', 'Awards and recognition at oblast level', 'REGIONAL', NULL, 250),
(81, 'Regional State Administration Recognition', 'Відзнака обласної державної адміністрації', 'Letters of thanks and honours of the regional state administration', 'REGIONAL', 80, 251),
(82, 'Regional Council Award', 'Нагорода обласної ради', 'Honours of the regional council', 'REGIONAL', 80, 252),
(83, 'Regional Science Prize', 'Обласна премія в галузі науки', 'Regional prizes for science and education', 'REGIONAL', 80, 253),
-- LOCAL
(70, 'Local Awards', 'Місцеві нагороди', 'Awards and recognition of a city or community', 'LOCAL', NULL, 280),
(71, 'City Council Recognition', 'Відзнака міської ради', 'Letters of thanks and honours of the city council', 'LOCAL', 70, 281),
(72, 'Community Service Award', 'Нагорода за служіння громаді', 'Recognition for service to the local community', 'LOCAL', 70, 282),
-- UNIVERSITY
(20, 'University Awards', 'Університетські нагороди', 'Awards and recognition at university level', 'UNIVERSITY', NULL, 300),
(21, 'University Excellence Award', 'Нагорода університетської досконалості', 'University-wide excellence recognition', 'UNIVERSITY', 20, 301),
(22, 'Best Teacher Award', 'Нагорода найкращого викладача', 'University best teacher recognition', 'UNIVERSITY', 20, 302),
(23, 'Research Achievement', 'Наукові досягнення', 'University research achievement awards', 'UNIVERSITY', 20, 303),
(24, 'Innovation Award', 'Нагорода за інновації', 'University innovation and entrepreneurship', 'UNIVERSITY', 20, 304),
(25, 'Service Excellence', 'Досконалість обслуговування', 'Outstanding service to university', 'UNIVERSITY', 20, 305),
-- FACULTY
(30, 'Faculty Awards', 'Факультетські нагороди', 'Awards and recognition at faculty level', 'FACULTY', NULL, 400),
(31, 'Faculty Teaching Award', 'Факультетська педагогічна нагорода', 'Faculty-level teaching excellence', 'FACULTY', 30, 401),
(32, 'Faculty Research Recognition', 'Факультетське визнання досліджень', 'Faculty research achievements', 'FACULTY', 30, 402),
(33, 'Faculty Service Award', 'Факультетська нагорода за службу', 'Outstanding service to faculty', 'FACULTY', 30, 403),
(34, 'Faculty Mentorship Award', 'Нагорода за наставництво', 'Excellence in student mentorship', 'FACULTY', 30, 404),
-- COLLEGE
(60, 'College Awards', 'Нагороди коледжу', 'Awards and recognition of a university college', 'COLLEGE', NULL, 450),
(61, 'College Teaching Award', 'Педагогічна нагорода коледжу', 'College-level teaching excellence', 'COLLEGE', 60, 451),
(62, 'College Service Award', 'Нагорода коледжу за службу', 'Outstanding service to a college', 'COLLEGE', 60, 452),
-- DEPARTMENT
(40, 'Department Awards', 'Кафедральні нагороди', 'Awards and recognition at department level', 'DEPARTMENT', NULL, 500),
(41, 'Department Appreciation', 'Подяка кафедри', 'Department-level appreciation', 'DEPARTMENT', 40, 501),
(42, 'Team Collaboration Award', 'Нагорода за командну співпрацю', 'Recognition for team collaboration', 'DEPARTMENT', 40, 502),
(43, 'Mentorship Recognition', 'Визнання наставництва', 'Department mentorship recognition', 'DEPARTMENT', 40, 503),
(44, 'Professional Development', 'Професійний розвиток', 'Professional development achievements', 'DEPARTMENT', 40, 504),
-- SPECIALITY
(50, 'Speciality Awards', 'Нагороди спеціальності', 'Awards and recognition within an academic speciality', 'SPECIALITY', NULL, 600),
(51, 'Speciality Best Lecturer', 'Найкращий викладач спеціальності', 'Best lecturer of a speciality by student vote', 'SPECIALITY', 50, 601),
(52, 'Speciality Student Supervision', 'Керівництво студентськими роботами спеціальності', 'Recognition for supervising student research', 'SPECIALITY', 50, 602);

-- A category created by users is never taken over: stop if it holds a seeded id or name.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM award_categories c JOIN seed_categories s ON s.category_id = c.category_id
               WHERE NOT c.is_system) THEN
        RAISE EXCEPTION 'award_categories seed: a seeded category_id is held by a category that is not system-defined';
    END IF;
    IF EXISTS (SELECT 1 FROM award_categories c JOIN seed_categories s ON s.name = c.name
               WHERE c.category_id <> s.category_id AND NOT c.is_system) THEN
        RAISE EXCEPTION 'award_categories seed: a seeded name is held by a category that is not system-defined';
    END IF;
END $$;

-- Free the names the seed is about to write: retired system categories keep a marked name,
-- seeded rows whose name changes get a placeholder until the upsert (so names can move between ids).
UPDATE award_categories c
SET name = LEFT(c.name, 80) || ' (retired ' || c.category_id || ')', is_active = FALSE,
    updated_at = CURRENT_TIMESTAMP
FROM seed_categories s
WHERE s.name = c.name AND c.category_id <> s.category_id AND c.is_system
  AND c.category_id NOT IN (SELECT category_id FROM seed_categories);

UPDATE award_categories c
SET name = '~seed ' || c.category_id
FROM seed_categories s
WHERE s.category_id = c.category_id AND c.name <> s.name;

INSERT INTO award_categories (category_id, name, name_uk, description, level, parent_category_id, sort_order,
                              is_active, is_system)
SELECT category_id, name, name_uk, description, level, parent_category_id, sort_order, TRUE, TRUE
FROM seed_categories
ON CONFLICT (category_id) DO UPDATE SET
    name = EXCLUDED.name,
    name_uk = EXCLUDED.name_uk,
    description = EXCLUDED.description,
    level = EXCLUDED.level,
    parent_category_id = EXCLUDED.parent_category_id,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE award_categories.is_system;

UPDATE award_categories
SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
WHERE is_system AND is_active AND category_id NOT IN (SELECT category_id FROM seed_categories);

SELECT setval('award_categories_category_id_seq', GREATEST((SELECT MAX(category_id) FROM award_categories), 100));

ALTER TABLE award_categories ENABLE TRIGGER trg_award_categories_audit;

DROP TABLE seed_categories;

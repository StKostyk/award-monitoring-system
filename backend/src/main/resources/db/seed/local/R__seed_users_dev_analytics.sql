-- R__seed_users_dev_analytics.sql
-- Description: Analytics demo data for local and docker profiles only (password: Passw0rd-demo):
--              lecturer.fmi with approved awards 2019-2026, eleven colleagues in DAI and six in DMA,
--              unit awards of DAI and of the faculty, and one awarding organisation in several spellings.
--              Named to run after R__seed_users_dev.sql (repeatables run in description order).
-- Author: Stefan Kostyk
-- Date: 2026-10-19

CREATE TEMPORARY TABLE seed_analytics_people (
    email_address VARCHAR(255) PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    organization_id BIGINT NOT NULL,
    seq INT NOT NULL,
    analytics BOOLEAN NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_analytics_people VALUES
    ('lecturer.fmi@chnu.edu.ua', 'Оксана', 'Викладач', 64, 0, TRUE),
    ('dai.bondar@chnu.edu.ua', 'Андрій', 'Бондар', 64, 1, TRUE),
    ('dai.hnatiuk@chnu.edu.ua', 'Марія', 'Гнатюк', 64, 2, FALSE),
    ('dai.kovalchuk@chnu.edu.ua', 'Василь', 'Ковальчук', 64, 3, TRUE),
    ('dai.melnyk@chnu.edu.ua', 'Ірина', 'Мельник', 64, 4, FALSE),
    ('dai.savchuk@chnu.edu.ua', 'Дмитро', 'Савчук', 64, 5, FALSE),
    ('dai.tkachuk@chnu.edu.ua', 'Наталія', 'Ткачук', 64, 6, TRUE),
    ('dai.fedoriuk@chnu.edu.ua', 'Юрій', 'Федорюк', 64, 7, FALSE),
    ('dai.shevchuk@chnu.edu.ua', 'Світлана', 'Шевчук', 64, 8, FALSE),
    ('dai.lysiuk@chnu.edu.ua', 'Олег', 'Лисюк', 64, 9, TRUE),
    ('dai.romaniuk@chnu.edu.ua', 'Тетяна', 'Романюк', 64, 10, FALSE),
    ('dai.danyliuk@chnu.edu.ua', 'Богдан', 'Данилюк', 64, 11, FALSE),
    ('dma.ivaniuk@chnu.edu.ua', 'Людмила', 'Іванюк', 66, 12, TRUE),
    ('dma.palamar@chnu.edu.ua', 'Сергій', 'Паламар', 66, 13, FALSE),
    ('dma.kushnir@chnu.edu.ua', 'Олена', 'Кушнір', 66, 14, FALSE),
    ('dma.vasylyk@chnu.edu.ua', 'Микола', 'Василик', 66, 15, TRUE),
    ('dma.hrytsiuk@chnu.edu.ua', 'Галина', 'Грицюк', 66, 16, FALSE),
    ('dma.kravets@chnu.edu.ua', 'Петро', 'Кравець', 66, 17, FALSE);

INSERT INTO users (email_address, first_name, last_name, password_hash, account_status, organization_id)
SELECT p.email_address, p.first_name, p.last_name, '$2a$12$aHfrRHPVY2s/0iSzOm5Jkua5t87xeHqTfXWPovs7uM/trJp2mgoTi',
       'ACTIVE', p.organization_id
FROM seed_analytics_people p
ON CONFLICT (email_address) DO NOTHING;

INSERT INTO user_roles (user_id, organization_id, role_type, valid_from)
SELECT u.user_id, p.organization_id, 'EMPLOYEE', DATE '2026-09-01'
FROM seed_analytics_people p
JOIN users u ON u.email_address = p.email_address
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles ur
    WHERE ur.user_id = u.user_id AND ur.role_type = 'EMPLOYEE' AND ur.organization_id = p.organization_id
);

INSERT INTO consent_records (user_id, consent_type, consent_version, is_granted, granted_at)
SELECT u.user_id, c.consent_type, '1.0', TRUE, TIMESTAMPTZ '2026-10-15 09:00:00+03'
FROM seed_analytics_people p
JOIN users u ON u.email_address = p.email_address
CROSS JOIN (VALUES ('DATA_PROCESSING'), ('ANALYTICS')) AS c(consent_type)
WHERE (c.consent_type = 'DATA_PROCESSING' OR p.analytics)
  AND NOT EXISTS (
    SELECT 1 FROM consent_records cr WHERE cr.user_id = u.user_id AND cr.consent_type = c.consent_type
);

CREATE TEMPORARY TABLE seed_analytics_awards (
    email_address VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    title_uk VARCHAR(500) NOT NULL,
    category_id BIGINT NOT NULL,
    awarding_organization VARCHAR(255) NOT NULL,
    award_date DATE NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    verified BOOLEAN NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_analytics_awards VALUES
    ('lecturer.fmi@chnu.edu.ua', 'Department appreciation for the algebra course', 'Подяка кафедри за курс алгебри', 41,
     'Кафедра алгебри та інформатики', DATE '2019-06-20', 'PRIVATE', FALSE),
    ('lecturer.fmi@chnu.edu.ua', 'Faculty teaching award', 'Факультетська педагогічна нагорода', 31,
     'Факультет математики та інформатики', DATE '2020-05-14', 'PRIVATE', FALSE),
    ('lecturer.fmi@chnu.edu.ua', 'Best teacher of the university', 'Найкращий викладач університету', 22,
     'Чернівецький національний університет імені Юрія Федьковича', DATE '2021-10-01', 'PUBLIC', TRUE),
    ('lecturer.fmi@chnu.edu.ua', 'Regional science prize for young scientists', 'Обласна премія молодим ученим', 83,
     'Чернівецька обласна державна адміністрація', DATE '2022-12-08', 'PRIVATE', FALSE),
    ('lecturer.fmi@chnu.edu.ua', 'Letter of thanks of the Ministry of Education and Science',
     'Подяка Міністерства освіти і науки України', 13, 'Міністерство освіти і науки України', DATE '2023-04-18',
     'PUBLIC', TRUE),
    ('lecturer.fmi@chnu.edu.ua', 'Faculty research recognition', 'Факультетське визнання досліджень', 32,
     'Факультет математики та інформатики', DATE '2023-11-23', 'PRIVATE', FALSE),
    ('lecturer.fmi@chnu.edu.ua', 'Best paper of the International Conference on Algebra',
     'Найкраща доповідь Міжнародної конференції з алгебри', 3, 'International Algebra Society', DATE '2024-07-05',
     'PRIVATE', TRUE),
    ('lecturer.fmi@chnu.edu.ua', 'University research achievement', 'Наукові досягнення університету', 23,
     'Чернівецький національний університет імені Юрія Федьковича', DATE '2025-03-12', 'PRIVATE', FALSE),
    ('lecturer.fmi@chnu.edu.ua', 'Diploma of the Ministry of Education and Science', 'Грамота Міністерства освіти і науки України',
     13, 'Міністерство освіти і науки України', DATE '2026-06-15', 'PRIVATE', FALSE);

INSERT INTO seed_analytics_awards
SELECT p.email_address, k.title || ' ' || y.year, k.title_uk || ' ' || y.year, k.category_id, k.awarding_organization,
       make_date(y.year, 3 + (p.seq + y.year) % 6, 10), 'PRIVATE', (p.seq + y.year) % 4 = 0
FROM seed_analytics_people p
CROSS JOIN generate_series(2019, 2026) AS y(year)
JOIN (VALUES
    (0, 'Department appreciation', 'Подяка кафедри', 41, 'Кафедра'),
    (1, 'Faculty teaching award', 'Факультетська педагогічна нагорода', 31, 'Факультет математики та інформатики'),
    (2, 'University excellence award', 'Нагорода університетської досконалості', 21,
     'Чернівецький національний університет імені Юрія Федьковича'),
    (3, 'Letter of thanks of the regional state administration', 'Подяка обласної державної адміністрації', 81,
     'Чернівецька обласна державна адміністрація'),
    (4, 'Letter of thanks of the Ministry', 'Подяка міністерства', 13, 'Міністерство освіти і науки України')
) AS k(kind, title, title_uk, category_id, awarding_organization) ON k.kind = (p.seq * 3 + y.year) % 5
WHERE p.seq > 0 AND (p.seq + y.year) % 3 <> 0;

INSERT INTO seed_analytics_awards VALUES
    ('dai.hnatiuk@chnu.edu.ua', 'Ministry diploma for the methodology handbook', 'Грамота міністерства за методичний посібник',
     13, 'міністерство  освіти і науки УКРАЇНИ', DATE '2024-09-16', 'PRIVATE', FALSE),
    ('dma.palamar@chnu.edu.ua', 'Ministry letter of thanks for the summer school', 'Подяка міністерства за літню школу', 13,
     ' Міністерство освіти і науки  України', DATE '2025-08-29', 'PRIVATE', FALSE);

INSERT INTO awards (user_id, organization_id, title, title_uk, status, category_id, awarding_organization,
                    award_date, verification_badge, visibility)
SELECT u.user_id, u.organization_id, a.title, a.title_uk, 'APPROVED', a.category_id, a.awarding_organization,
       a.award_date, a.verified, a.visibility
FROM seed_analytics_awards a
JOIN users u ON u.email_address = a.email_address
WHERE NOT EXISTS (
    SELECT 1 FROM awards aw WHERE aw.user_id = u.user_id AND aw.title_uk = a.title_uk
);

INSERT INTO award_requests (award_id, submitter_id, status, current_level, submitted_at, completed_at)
SELECT aw.award_id, aw.user_id, 'APPROVED', 'FACULTY_SECRETARY', aw.award_date + TIME '10:00',
       aw.award_date + INTERVAL '3 days' + TIME '12:00'
FROM awards aw
JOIN seed_analytics_awards a ON a.title_uk = aw.title_uk
JOIN users u ON u.user_id = aw.user_id AND u.email_address = a.email_address
WHERE NOT EXISTS (SELECT 1 FROM award_requests r WHERE r.award_id = aw.award_id);

CREATE TEMPORARY TABLE seed_analytics_unit_awards (
    email_address VARCHAR(255) NOT NULL,
    recipient_org_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    title_uk VARCHAR(500) NOT NULL,
    category_id BIGINT NOT NULL,
    awarding_organization VARCHAR(255) NOT NULL,
    award_date DATE NOT NULL,
    verified BOOLEAN NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_analytics_unit_awards VALUES
    ('secretary.fmi@chnu.edu.ua', 64, 'Department of the year of the faculty', 'Кафедра року факультету', 31,
     'Факультет математики та інформатики', DATE '2023-12-20', FALSE),
    ('secretary.fmi@chnu.edu.ua', 64, 'Ministry diploma for the olympiad team',
     'Грамота міністерства за підготовку олімпіадної команди', 13, 'МІНІСТЕРСТВО ОСВІТИ І НАУКИ УКРАЇНИ',
     DATE '2025-04-11', TRUE),
    ('secretary.fmi@chnu.edu.ua', 64, 'University award for the informatics curriculum',
     'Нагорода університету за програму з інформатики', 21,
     'Чернівецький національний університет імені Юрія Федьковича', DATE '2026-05-28', FALSE),
    ('dean.fmi@chnu.edu.ua', 9, 'Regional recognition of the faculty', 'Обласне визнання факультету', 81,
     'Чернівецька обласна державна адміністрація', DATE '2024-10-02', FALSE),
    ('dean.fmi@chnu.edu.ua', 9, 'Ministry rating of the faculty', 'Рейтинг міністерства для факультету', 13,
     'Міністерство освіти і науки України', DATE '2026-03-17', TRUE);

INSERT INTO awards (user_id, organization_id, recipient_org_id, title, title_uk, status, category_id,
                    awarding_organization, award_date, verification_badge, visibility)
SELECT u.user_id, a.recipient_org_id, a.recipient_org_id, a.title, a.title_uk, 'APPROVED', a.category_id,
       a.awarding_organization, a.award_date, a.verified, 'PRIVATE'
FROM seed_analytics_unit_awards a
JOIN users u ON u.email_address = a.email_address
WHERE NOT EXISTS (
    SELECT 1 FROM awards aw WHERE aw.recipient_org_id = a.recipient_org_id AND aw.title_uk = a.title_uk
);

INSERT INTO award_requests (award_id, submitter_id, status, current_level, submitted_at, completed_at)
SELECT aw.award_id, aw.user_id, 'APPROVED', 'FACULTY_SECRETARY', aw.award_date + TIME '10:00',
       aw.award_date + INTERVAL '3 days' + TIME '12:00'
FROM awards aw
JOIN seed_analytics_unit_awards a ON a.recipient_org_id = aw.recipient_org_id AND a.title_uk = aw.title_uk
WHERE NOT EXISTS (SELECT 1 FROM award_requests r WHERE r.award_id = aw.award_id);

-- R__seed_users_dev_personal.sql
-- Description: Personal awards of the base demo accounts for local and docker profiles only (password: Passw0rd-demo):
--              approved awards 2019-2026 for employee.fmi, the FMI dean and secretaries, the rector and the
--              rector's secretary, so their personal dashboards have data, and the e2e.employee account that the
--              Playwright specs write to instead of the demo accounts.
--              Runs after R__seed_users_dev_analytics.sql.
-- Author: Stefan Kostyk
-- Date: 2026-11-20

INSERT INTO users (email_address, first_name, last_name, password_hash, account_status, organization_id)
VALUES ('e2e.employee@chnu.edu.ua', 'Тест', 'Автоматичний',
        '$2a$12$aHfrRHPVY2s/0iSzOm5Jkua5t87xeHqTfXWPovs7uM/trJp2mgoTi', 'ACTIVE', 64)
ON CONFLICT (email_address) DO NOTHING;

INSERT INTO user_roles (user_id, organization_id, role_type, valid_from)
SELECT u.user_id, 64, 'EMPLOYEE', DATE '2026-09-01'
FROM users u
WHERE u.email_address = 'e2e.employee@chnu.edu.ua'
  AND NOT EXISTS (
    SELECT 1 FROM user_roles ur WHERE ur.user_id = u.user_id AND ur.role_type = 'EMPLOYEE'
);

CREATE TEMPORARY TABLE seed_personal_people (
    email_address VARCHAR(255) PRIMARY KEY,
    seq INT NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_personal_people VALUES
    ('employee.fmi@chnu.edu.ua', 1),
    ('dean.fmi@chnu.edu.ua', 2),
    ('secretary.fmi@chnu.edu.ua', 3),
    ('secretary2.fmi@chnu.edu.ua', 4),
    ('rector@chnu.edu.ua', 5),
    ('rector.secretary@chnu.edu.ua', 6);

CREATE TEMPORARY TABLE seed_personal_awards (
    email_address VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    title_uk VARCHAR(500) NOT NULL,
    category_id BIGINT NOT NULL,
    awarding_organization VARCHAR(255) NOT NULL,
    award_date DATE NOT NULL,
    verified BOOLEAN NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_personal_awards
SELECT p.email_address, k.title || ' ' || y.year, k.title_uk || ' ' || y.year, k.category_id,
       k.awarding_organization, make_date(y.year, 2 + (p.seq * 5 + y.year) % 9, 12), (p.seq + y.year) % 3 = 0
FROM seed_personal_people p
CROSS JOIN generate_series(2019, 2026) AS y(year)
JOIN (VALUES
    (0, 'Department letter of thanks', 'Подяка кафедри', 41, 'Кафедра'),
    (1, 'Faculty diploma', 'Грамота факультету', 31, 'Факультет математики та інформатики'),
    (2, 'Rector''s letter of thanks', 'Подяка ректора', 22,
     'Чернівецький національний університет імені Юрія Федьковича'),
    (3, 'Regional council diploma', 'Почесна грамота обласної ради', 83, 'Чернівецька обласна рада'),
    (4, 'Ministry letter of thanks', 'Подяка Міністерства освіти і науки України', 13,
     'Міністерство освіти і науки України'),
    (5, 'University excellence award', 'Нагорода університетської досконалості', 21,
     'Чернівецький національний університет імені Юрія Федьковича')
) AS k(kind, title, title_uk, category_id, awarding_organization) ON k.kind = (p.seq * 2 + y.year) % 6
WHERE y.year >= 2019 + p.seq % 3;

INSERT INTO seed_personal_awards
SELECT p.email_address, 'Faculty research recognition ' || y.year, 'Визнання наукової роботи ' || y.year, 32,
       'Факультет математики та інформатики', make_date(y.year, 11, 5), FALSE
FROM seed_personal_people p
CROSS JOIN generate_series(2022, 2025) AS y(year)
WHERE (p.seq + y.year) % 2 = 0;

INSERT INTO seed_personal_awards VALUES
    ('employee.fmi@chnu.edu.ua', 'Best paper of the International Conference on Algebra',
     'Найкраща доповідь Міжнародної конференції з алгебри', 3, 'International Algebra Society', DATE '2024-07-05',
     TRUE),
    ('rector@chnu.edu.ua', 'Order of Merit, third class', 'Орден «За заслуги» III ступеня', 13,
     'Президент України', DATE '2023-08-24', TRUE);

INSERT INTO awards (user_id, organization_id, title, title_uk, status, category_id, awarding_organization,
                    award_date, verification_badge, visibility)
SELECT u.user_id, u.organization_id, a.title, a.title_uk, 'APPROVED', a.category_id, a.awarding_organization,
       a.award_date, a.verified, 'PRIVATE'
FROM seed_personal_awards a
JOIN users u ON u.email_address = a.email_address
WHERE NOT EXISTS (
    SELECT 1 FROM awards aw WHERE aw.user_id = u.user_id AND aw.title_uk = a.title_uk
);

INSERT INTO award_requests (award_id, submitter_id, status, current_level, submitted_at, completed_at)
SELECT aw.award_id, aw.user_id, 'APPROVED', 'FACULTY_SECRETARY', aw.award_date + TIME '10:00',
       aw.award_date + INTERVAL '3 days' + TIME '12:00'
FROM awards aw
JOIN seed_personal_awards a ON a.title_uk = aw.title_uk
JOIN users u ON u.user_id = aw.user_id AND u.email_address = a.email_address
WHERE NOT EXISTS (SELECT 1 FROM award_requests r WHERE r.award_id = aw.award_id);

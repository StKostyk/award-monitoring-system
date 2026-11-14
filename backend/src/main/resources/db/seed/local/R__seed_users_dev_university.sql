-- R__seed_users_dev_university.sql
-- Description: University-wide demo data for local and docker profiles only (password: Passw0rd-demo):
--              a dean (dean.<code>) and a secretary (secretary.<code>) for eight more faculties, three to seven
--              employees (<department code>.<surname>) in three departments of each of them and in three more
--              FMI departments, personal awards 2019-2026 (ten kinds, five topics; mostly approved; pending,
--              rejected and drafts in 2026) and unit awards of departments and faculties.
--              Runs after R__seed_users_dev_analytics.sql.
-- Author: Stefan Kostyk
-- Date: 2026-11-14

CREATE TEMPORARY TABLE seed_uni_departments ON COMMIT DROP AS
SELECT o.org_id, o.parent_org_id, lower(o.code) AS code, o.name_uk, f.name_uk AS faculty_name_uk
FROM (
    SELECT org_id, parent_org_id, code, name_uk,
           row_number() OVER (PARTITION BY parent_org_id ORDER BY org_id) AS rn
    FROM organizations
    WHERE org_type = 'DEPARTMENT'
) o
JOIN organizations f ON f.org_id = o.parent_org_id
WHERE (o.parent_org_id IN (2, 3, 5, 6, 8, 10, 12, 13) AND o.rn <= 3) OR o.org_id IN (65, 67, 68);

CREATE TEMPORARY TABLE seed_uni_people (
    email_address VARCHAR(255) PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    organization_id BIGINT NOT NULL,
    role_type VARCHAR(30) NOT NULL,
    seq INT NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_uni_people
SELECT d.code || '.' || s.latin || '@chnu.edu.ua', fn.name, s.name, d.org_id, 'EMPLOYEE',
       (d.org_id * 10 + i.n)::INT
FROM seed_uni_departments d
CROSS JOIN LATERAL generate_series(1, 3 + d.org_id::INT % 5) AS i(n)
JOIN (VALUES
    (0, 'Бойко', 'boiko'), (1, 'Коваленко', 'kovalenko'), (2, 'Олійник', 'oliinyk'), (3, 'Шевченко', 'shevchenko'),
    (4, 'Поліщук', 'polishchuk'), (5, 'Лисенко', 'lysenko'), (6, 'Гуменюк', 'humeniuk'), (7, 'Марчук', 'marchuk'),
    (8, 'Остапчук', 'ostapchuk'), (9, 'Чорней', 'chornei'), (10, 'Руснак', 'rusnak'), (11, 'Ткач', 'tkach'),
    (12, 'Попович', 'popovych'), (13, 'Яковчук', 'yakovchuk'), (14, 'Гордійчук', 'hordiichuk'),
    (15, 'Козак', 'kozak'), (16, 'Мороз', 'moroz'), (17, 'Павлюк', 'pavliuk'), (18, 'Семенюк', 'semeniuk'),
    (19, 'Кравчук', 'kravchuk')
) AS s(k, name, latin) ON s.k = (i.n + d.org_id * 3) % 20
JOIN (VALUES
    (0, 'Олександр'), (1, 'Наталія'), (2, 'Іван'), (3, 'Оксана'), (4, 'Віктор'), (5, 'Юлія'), (6, 'Михайло'),
    (7, 'Ганна'), (8, 'Тарас'), (9, 'Лариса'), (10, 'Роман'), (11, 'Катерина'), (12, 'Степан'), (13, 'Софія'),
    (14, 'Ярослав'), (15, 'Христина'), (16, 'Остап'), (17, 'Діана'), (18, 'Георгій'), (19, 'Вікторія')
) AS fn(k, name) ON fn.k = (i.n * 7 + d.org_id) % 20;

INSERT INTO seed_uni_people
SELECT r.prefix || '.' || lower(f.code) || '@chnu.edu.ua', n.first_name, n.last_name, f.org_id, r.role_type, 0
FROM organizations f
CROSS JOIN (VALUES (0, 'dean', 'DEAN'), (1, 'secretary', 'FACULTY_SECRETARY')) AS r(k, prefix, role_type)
JOIN (VALUES
    (2, 0, 'Володимир', 'Дячук'), (2, 1, 'Марина', 'Фесюк'), (3, 0, 'Анатолій', 'Ульяненко'),
    (3, 1, 'Людмила', 'Гаврилюк'), (5, 0, 'Петро', 'Заблотовський'), (5, 1, 'Тетяна', 'Кифорук'),
    (6, 0, 'Сергій', 'Нікифорчук'), (6, 1, 'Олена', 'Мартинюк'), (8, 0, 'Олександр', 'Добржанський'),
    (8, 1, 'Інна', 'Кушнірик'), (10, 0, 'Микола', 'Бабюк'), (10, 1, 'Ольга', 'Вакарюк'),
    (12, 0, 'Богдан', 'Мельничук'), (12, 1, 'Аліна', 'Гаврищук'), (13, 0, 'Дмитро', 'Онищук'),
    (13, 1, 'Надія', 'Тимощук')
) AS n(org_id, k, first_name, last_name) ON n.org_id = f.org_id AND n.k = r.k
WHERE f.org_id IN (2, 3, 5, 6, 8, 10, 12, 13);

INSERT INTO users (email_address, first_name, last_name, password_hash, account_status, organization_id)
SELECT p.email_address, p.first_name, p.last_name, '$2a$12$aHfrRHPVY2s/0iSzOm5Jkua5t87xeHqTfXWPovs7uM/trJp2mgoTi',
       'ACTIVE', p.organization_id
FROM seed_uni_people p
ON CONFLICT (email_address) DO NOTHING;

INSERT INTO user_roles (user_id, organization_id, role_type, valid_from)
SELECT u.user_id, p.organization_id, p.role_type, DATE '2026-09-01'
FROM seed_uni_people p
JOIN users u ON u.email_address = p.email_address
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles ur
    WHERE ur.user_id = u.user_id AND ur.role_type = p.role_type AND ur.organization_id = p.organization_id
);

INSERT INTO consent_records (user_id, consent_type, consent_version, is_granted, granted_at)
SELECT u.user_id, c.consent_type, '1.0', TRUE, TIMESTAMPTZ '2026-10-15 09:00:00+03'
FROM seed_uni_people p
JOIN users u ON u.email_address = p.email_address
CROSS JOIN (VALUES ('DATA_PROCESSING'), ('ANALYTICS')) AS c(consent_type)
WHERE (c.consent_type = 'DATA_PROCESSING' OR p.seq % 3 <> 0)
  AND NOT EXISTS (
    SELECT 1 FROM consent_records cr WHERE cr.user_id = u.user_id AND cr.consent_type = c.consent_type
);

CREATE TEMPORARY TABLE seed_uni_awards ON COMMIT DROP AS
SELECT p.email_address, p.seq, y.year,
       k.title || CASE WHEN k.kind = 8 THEN '' ELSE ' ' || tp.en END || ', ' || y.year AS title,
       k.title_uk || CASE WHEN k.kind = 8 THEN '' ELSE ' ' || tp.uk END || ', ' || y.year AS title_uk,
       k.category_id,
       CASE k.kind WHEN 0 THEN d.name_uk WHEN 1 THEN d.faculty_name_uk ELSE k.awarding_organization END
           AS awarding_organization,
       make_date(y.year, 1 + (p.seq + y.year) % 10, 1 + (p.seq * 3) % 27) AS award_date,
       CASE (p.seq + y.year) % 5 WHEN 0 THEN 'PUBLIC' WHEN 1 THEN 'UNIVERSITY' ELSE 'PRIVATE' END AS visibility,
       CASE
           WHEN y.year = 2026 AND p.seq % 5 = 1 THEN 'DRAFT'
           WHEN y.year = 2026 AND p.seq % 4 = 0 THEN 'PENDING'
           WHEN (p.seq + y.year) % 11 = 0 THEN 'REJECTED'
           ELSE 'APPROVED'
       END AS status
FROM seed_uni_people p
JOIN seed_uni_departments d ON d.org_id = p.organization_id
CROSS JOIN generate_series(2019, 2026) AS y(year)
JOIN (VALUES
    (0, 'Department appreciation', 'Подяка кафедри', 41, NULL),
    (1, 'Faculty teaching award', 'Факультетська педагогічна нагорода', 31, NULL),
    (2, 'University excellence award', 'Нагорода університетської досконалості', 21,
     'Чернівецький національний університет імені Юрія Федьковича'),
    (3, 'Best teacher of the university', 'Найкращий викладач університету', 22,
     'Чернівецький національний університет імені Юрія Федьковича'),
    (4, 'Letter of thanks of the regional state administration', 'Подяка обласної державної адміністрації', 81,
     'Чернівецька обласна державна адміністрація'),
    (5, 'Regional science prize', 'Обласна премія в галузі науки', 83, 'Чернівецька обласна рада'),
    (6, 'Letter of thanks of the Ministry', 'Подяка Міністерства освіти і науки', 13,
     'Міністерство освіти і науки України'),
    (7, 'National teaching excellence diploma', 'Диплом за педагогічну майстерність', 15,
     'Міністерство освіти і науки України'),
    (8, 'Best paper of an international conference', 'Найкраща доповідь міжнародної конференції', 3,
     'International Academic Conference Board'),
    (9, 'City council recognition', 'Відзнака міської ради', 71, 'Чернівецька міська рада')
) AS k(kind, title, title_uk, category_id, awarding_organization) ON k.kind = (p.seq / 10 * 3 + p.seq * 7 + y.year) % 10
JOIN (VALUES
    (0, 'for research', 'за наукову роботу'),
    (1, 'for teaching excellence', 'за педагогічну майстерність'),
    (2, 'for student mentoring', 'за підготовку студентів'),
    (3, 'for international cooperation', 'за міжнародну співпрацю'),
    (4, 'for methodological work', 'за методичну роботу')
) AS tp(topic, en, uk) ON tp.topic = (p.seq / 10 + p.seq * 3 + y.year * 2) % 5
WHERE p.role_type = 'EMPLOYEE' AND (p.seq + y.year) % 3 <> 0;

INSERT INTO awards (user_id, organization_id, title, title_uk, status, category_id, awarding_organization,
                    award_date, verification_badge, visibility)
SELECT u.user_id, u.organization_id, a.title, a.title_uk, a.status, a.category_id, a.awarding_organization,
       a.award_date, a.status = 'APPROVED' AND (a.seq + a.year) % 4 = 0, a.visibility
FROM seed_uni_awards a
JOIN users u ON u.email_address = a.email_address
WHERE NOT EXISTS (
    SELECT 1 FROM awards aw WHERE aw.user_id = u.user_id AND aw.title_uk = a.title_uk
);

INSERT INTO award_requests (award_id, submitter_id, status, current_level, submitted_at, deadline, completed_at,
                            rejection_reason)
SELECT aw.award_id, aw.user_id,
       CASE a.status WHEN 'PENDING' THEN 'SUBMITTED' ELSE a.status END,
       'FACULTY_SECRETARY',
       CASE a.status WHEN 'PENDING' THEN now() - make_interval(days => a.seq % 9)
                     ELSE aw.award_date + TIME '10:00' END,
       CASE a.status WHEN 'PENDING' THEN now() - make_interval(days => a.seq % 9) + INTERVAL '7 days' END,
       CASE a.status WHEN 'PENDING' THEN NULL ELSE aw.award_date + INTERVAL '3 days' + TIME '12:00' END,
       CASE a.status WHEN 'REJECTED' THEN 'Немає підтверджувального документа' END
FROM awards aw
JOIN users u ON u.user_id = aw.user_id
JOIN seed_uni_awards a ON a.email_address = u.email_address AND a.title_uk = aw.title_uk
WHERE a.status <> 'DRAFT'
  AND NOT EXISTS (SELECT 1 FROM award_requests r WHERE r.award_id = aw.award_id);

CREATE TEMPORARY TABLE seed_uni_unit_awards ON COMMIT DROP AS
SELECT 'secretary.' || lower(f.code) || '@chnu.edu.ua' AS email_address, d.org_id AS recipient_org_id,
       k.title || ' ' || y.year AS title, k.title_uk || ' ' || y.year AS title_uk, k.category_id,
       COALESCE(k.awarding_organization, d.faculty_name_uk) AS awarding_organization,
       make_date(y.year, 4 + d.org_id::INT % 6, 15) AS award_date, (d.org_id + y.year) % 3 = 0 AS verified
FROM seed_uni_departments d
JOIN organizations f ON f.org_id = d.parent_org_id
CROSS JOIN generate_series(2022, 2026) AS y(year)
JOIN (VALUES
    (0, 'Department of the year of the faculty', 'Кафедра року факультету', 31, NULL),
    (1, 'University award for the curriculum', 'Нагорода університету за освітню програму', 21,
     'Чернівецький національний університет імені Юрія Федьковича'),
    (2, 'Ministry diploma for student olympiad results', 'Грамота міністерства за результати олімпіад', 13,
     'Міністерство освіти і науки України')
) AS k(kind, title, title_uk, category_id, awarding_organization) ON k.kind = (d.org_id + y.year) % 3
WHERE d.parent_org_id <> 9 AND (d.org_id + y.year) % 2 = 0
UNION ALL
SELECT 'dean.' || lower(f.code) || '@chnu.edu.ua', f.org_id,
       k.title || ' ' || y.year, k.title_uk || ' ' || y.year, k.category_id, k.awarding_organization,
       make_date(y.year, 9, 1 + f.org_id::INT), (f.org_id + y.year) % 2 = 0
FROM organizations f
CROSS JOIN generate_series(2023, 2026) AS y(year)
JOIN (VALUES
    (0, 'Regional recognition of the faculty', 'Обласне визнання факультету', 81,
     'Чернівецька обласна державна адміністрація'),
    (1, 'Ministry rating of the faculty', 'Рейтинг міністерства для факультету', 13,
     'Міністерство освіти і науки України')
) AS k(kind, title, title_uk, category_id, awarding_organization) ON k.kind = (f.org_id + y.year) % 2
WHERE f.org_id IN (2, 3, 5, 6, 8, 10, 12, 13);

INSERT INTO awards (user_id, organization_id, recipient_org_id, title, title_uk, status, category_id,
                    awarding_organization, award_date, verification_badge, visibility)
SELECT u.user_id, a.recipient_org_id, a.recipient_org_id, a.title, a.title_uk, 'APPROVED', a.category_id,
       a.awarding_organization, a.award_date, a.verified, 'PRIVATE'
FROM seed_uni_unit_awards a
JOIN users u ON u.email_address = a.email_address
WHERE NOT EXISTS (
    SELECT 1 FROM awards aw WHERE aw.recipient_org_id = a.recipient_org_id AND aw.title_uk = a.title_uk
);

INSERT INTO award_requests (award_id, submitter_id, status, current_level, submitted_at, completed_at)
SELECT aw.award_id, aw.user_id, 'APPROVED', 'FACULTY_SECRETARY', aw.award_date + TIME '10:00',
       aw.award_date + INTERVAL '3 days' + TIME '12:00'
FROM awards aw
JOIN seed_uni_unit_awards a ON a.recipient_org_id = aw.recipient_org_id AND a.title_uk = aw.title_uk
WHERE NOT EXISTS (SELECT 1 FROM award_requests r WHERE r.award_id = aw.award_id);

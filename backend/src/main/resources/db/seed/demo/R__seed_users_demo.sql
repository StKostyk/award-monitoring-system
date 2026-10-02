-- R__seed_users_demo.sql
-- Description: Fictional demo accounts for a deployed demo; created only when DEMO_PASSWORD_HASH is set
-- Author: Stefan Kostyk
-- Date: 2026-10-02

INSERT INTO users (email_address, first_name, last_name, password_hash, account_status, organization_id)
SELECT d.email_address, d.first_name, d.last_name, '${demo_password_hash}', 'ACTIVE', d.organization_id
FROM (VALUES
    ('admin@demo.example', 'Ігор', 'Адміненко', 1),
    ('rector@demo.example', 'Роман', 'Ректоренко', 1),
    ('dean.fmi@demo.example', 'Мартин', 'Мартинюк', 9),
    ('secretary.fmi@demo.example', 'Аліна', 'Секретаренко', 9),
    ('employee.fmi@demo.example', 'Анастасія', 'Працівник', 64)
) AS d(email_address, first_name, last_name, organization_id)
WHERE '${demo_password_hash}' <> ''
ON CONFLICT (email_address) DO NOTHING;

INSERT INTO user_roles (user_id, organization_id, role_type, valid_from)
SELECT u.user_id, r.organization_id, r.role_type, DATE '2026-09-01'
FROM (VALUES
    ('admin@demo.example', 1, 'SYSTEM_ADMIN'),
    ('rector@demo.example', 1, 'RECTOR'),
    ('dean.fmi@demo.example', 9, 'DEAN'),
    ('secretary.fmi@demo.example', 9, 'FACULTY_SECRETARY'),
    ('employee.fmi@demo.example', 64, 'EMPLOYEE')
) AS r(email_address, organization_id, role_type)
JOIN users u ON u.email_address = r.email_address
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles ur
    WHERE ur.user_id = u.user_id AND ur.role_type = r.role_type AND ur.organization_id = r.organization_id
);

-- R__seed_users_dev.sql
-- Description: Demo accounts for local and docker profiles only (password: Passw0rd-demo)
-- Author: Stefan Kostyk
-- Date: 2026-09-21

INSERT INTO users (email_address, first_name, last_name, password_hash, account_status, organization_id)
VALUES
    ('admin@chnu.edu.ua', 'Ігор', 'Адміненко', '$2a$12$aHfrRHPVY2s/0iSzOm5Jkua5t87xeHqTfXWPovs7uM/trJp2mgoTi', 'ACTIVE', 1),
    ('rector@chnu.edu.ua', 'Роман', 'Ректоренко', '$2a$12$aHfrRHPVY2s/0iSzOm5Jkua5t87xeHqTfXWPovs7uM/trJp2mgoTi', 'ACTIVE', 1),
    ('dean.fmi@chnu.edu.ua', 'Мартин', 'Мартинюк', '$2a$12$aHfrRHPVY2s/0iSzOm5Jkua5t87xeHqTfXWPovs7uM/trJp2mgoTi', 'ACTIVE', 9),
    ('secretary.fmi@chnu.edu.ua', 'Аліна', 'Секретаренко', '$2a$12$aHfrRHPVY2s/0iSzOm5Jkua5t87xeHqTfXWPovs7uM/trJp2mgoTi', 'ACTIVE', 9),
    ('employee.fmi@chnu.edu.ua', 'Анастасія', 'Працівник', '$2a$12$aHfrRHPVY2s/0iSzOm5Jkua5t87xeHqTfXWPovs7uM/trJp2mgoTi', 'ACTIVE', 64),
    ('pending@chnu.edu.ua', 'Петро', 'Новачок', '$2a$12$aHfrRHPVY2s/0iSzOm5Jkua5t87xeHqTfXWPovs7uM/trJp2mgoTi', 'PENDING', 64)
ON CONFLICT (email_address) DO NOTHING;

INSERT INTO user_roles (user_id, organization_id, role_type, valid_from)
SELECT u.user_id, r.organization_id, r.role_type, DATE '2026-09-01'
FROM (VALUES
    ('admin@chnu.edu.ua', 1, 'SYSTEM_ADMIN'),
    ('rector@chnu.edu.ua', 1, 'RECTOR'),
    ('dean.fmi@chnu.edu.ua', 9, 'DEAN'),
    ('secretary.fmi@chnu.edu.ua', 9, 'FACULTY_SECRETARY'),
    ('employee.fmi@chnu.edu.ua', 64, 'EMPLOYEE'),
    ('pending@chnu.edu.ua', 64, 'EMPLOYEE')
) AS r(email_address, organization_id, role_type)
JOIN users u ON u.email_address = r.email_address
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles ur
    WHERE ur.user_id = u.user_id AND ur.role_type = r.role_type AND ur.organization_id = r.organization_id
);

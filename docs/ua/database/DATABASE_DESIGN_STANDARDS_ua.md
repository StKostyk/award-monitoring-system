# Стандарти Проєктування Бази Даних
## Система Моніторингу та Відстеження Нагород

> **Результат Фази 9**: Архітектура Даних та Проєктування Бази Даних  
> **Версія Документа**: 1.0  
> **Останнє Оновлення**: Грудень 2025  
> **Автор**: Стефан Костик  
> **База Даних**: PostgreSQL 16  
> **Класифікація**: Внутрішній

---

## Резюме

Цей документ встановлює комплексні стандарти проєктування бази даних для Системи Моніторингу та Відстеження Нагород. Ці стандарти забезпечують узгодженість, підтримуваність та якість корпоративного рівня для всіх об'єктів бази даних. Усі конвенції іменування та шаблони проєктування оптимізовані для PostgreSQL 16 та узгоджені з інтеграцією Spring Boot/JPA.

### Ключові Принципи
- **Узгодженість**: Єдине іменування та структура для всіх об'єктів БД
- **Ясність**: Самодокументовані назви, що відображають бізнес-домен
- **Продуктивність**: Шаблони проєктування оптимізовані для можливостей PostgreSQL 16
- **Відповідність**: Готові до GDPR аудиторські сліди та захист даних
- **Підтримуваність**: Чіткі конвенції для еволюції схеми

---

## 1. Конвенції Іменування

### 1.1 Загальні Правила

| **Правило** | **Стандарт** | **Приклад** |
|-------------|--------------|-------------|
| Набір символів | Малі ASCII літери, цифри, підкреслення | `user_accounts`, `award_id` |
| Розділювач слів | Підкреслення (`_`) | `award_categories`, `created_at` |
| Максимальна довжина | 63 символи (обмеження PostgreSQL) | Тримайте назви стислими але описовими |
| Зарезервовані слова | Уникайте зарезервованих слів PostgreSQL | Використовуйте `user_status`, а не `status` |
| Скорочення | Мінімізуйте; використовуйте тільки стандартні | `org` для organization, `id` для identifier |
| Мова | Тільки англійська | Узгодженість по всій кодовій базі |

### 1.2 Конвенції Іменування Таблиць

| **Конвенція** | **Шаблон** | **Приклади** |
|---------------|------------|--------------|
| Формат | `множина_snake_case` | `users`, `award_categories` |
| Бізнес-домен | Відображає контекст домену | `award_requests`, `review_decisions` |
| Таблиці зв'язків | `сутність1_сутність2` (алфавітний порядок) | `user_roles`, `award_documents` |
| Таблиці аудиту | `сутність_audit` | `users_audit`, `awards_audit` |
| Архівні таблиці | `сутність_archive` | `awards_archive` |
| Тимчасові таблиці | `tmp_призначення` | `tmp_import_awards` |

```sql
-- ✅ ПРАВИЛЬНО: Приклади іменування таблиць
CREATE TABLE users (...);                    -- Сутність: User
CREATE TABLE award_categories (...);         -- Сутність: AwardCategory
CREATE TABLE review_decisions (...);         -- Сутність: ReviewDecision
CREATE TABLE user_roles (...);               -- Зв'язок: User ↔ Role
CREATE TABLE audit_logs (...);               -- Сутність аудиту
CREATE TABLE consent_records (...);          -- Сутність відповідності

-- ❌ НЕПРАВИЛЬНО: Антипатерни яких слід уникати
CREATE TABLE User (...);                     -- PascalCase
CREATE TABLE tblUsers (...);                 -- Угорська нотація
CREATE TABLE user (...);                     -- Однина (використовуйте множину)
CREATE TABLE UserAwardCategory (...);        -- CamelCase, без підкреслень
```

### 1.3 Конвенції Іменування Колонок

| **Конвенція** | **Шаблон** | **Приклади** |
|---------------|------------|--------------|
| Формат | `snake_case` | `first_name`, `email_address` |
| Первинний ключ | `таблиця_однина_id` | `user_id`, `award_id` |
| Зовнішній ключ | `посилана_таблиця_однина_id` | `organization_id`, `category_id` |
| Булеві | префікс `is_` або `has_` | `is_active`, `has_verified` |
| Мітки часу | суфікс `_at` | `created_at`, `updated_at`, `deleted_at` |
| Дати | суфікс `_date` або `_on` | `award_date`, `valid_from` |
| Статус | суфікс `_status` | `account_status`, `request_status` |
| Лічильники | суфікс `_count` | `approval_count`, `rejection_count` |
| JSON/JSONB | Описова назва | `parsed_metadata`, `old_values` |

```sql
-- ✅ ПРАВИЛЬНО: Приклади іменування колонок
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,           -- Первинний ключ
    organization_id BIGINT,                   -- Зовнішній ключ
    email_address VARCHAR(255) NOT NULL,      -- Описова назва
    first_name VARCHAR(100) NOT NULL,         -- Snake case
    last_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,      -- Назва з чітким призначенням
    is_active BOOLEAN DEFAULT TRUE,           -- Булевий з префіксом is_
    account_status VARCHAR(20),               -- Поле статусу
    last_login_at TIMESTAMP WITH TIME ZONE,   -- Мітка часу з _at
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT DEFAULT 1                  -- Оптимістичне блокування
);

-- ❌ НЕПРАВИЛЬНО: Антипатерни яких слід уникати
CREATE TABLE users (
    id BIGSERIAL,                             -- Занадто загально
    orgId BIGINT,                             -- CamelCase
    Email VARCHAR(255),                       -- PascalCase
    fname VARCHAR(100),                       -- Незрозуміле скорочення
    active BOOLEAN,                           -- Відсутній префікс is_
    status VARCHAR(20),                       -- Занадто загально
    lastLogin TIMESTAMP,                      -- CamelCase
    createdDate TIMESTAMP                     -- Непослідовний суфікс
);
```

### 1.4 Конвенції Іменування Обмежень

| **Тип Обмеження** | **Шаблон** | **Приклади** |
|-------------------|------------|--------------|
| Первинний ключ | `pk_таблиця` | `pk_users`, `pk_awards` |
| Зовнішній ключ | `fk_таблиця_посилання` | `fk_awards_users`, `fk_users_organizations` |
| Унікальний | `uk_таблиця_колонка(и)` | `uk_users_email`, `uk_users_username` |
| Перевірка | `ck_таблиця_колонка` | `ck_users_status`, `ck_awards_impact_score` |
| NOT NULL | (неявний, без назви) | Визначення колонки включає `NOT NULL` |
| DEFAULT | (неявний, без назви) | Визначення колонки включає `DEFAULT` |
| Виключення | `ex_таблиця_опис` | `ex_awards_date_overlap` |

```sql
-- ✅ ПРАВИЛЬНО: Приклади іменування обмежень
CREATE TABLE users (
    user_id BIGSERIAL,
    email_address VARCHAR(255) NOT NULL,
    username VARCHAR(50) NOT NULL,
    account_status VARCHAR(20) DEFAULT 'PENDING',
    
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uk_users_email UNIQUE (email_address),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT ck_users_status CHECK (
        account_status IN ('PENDING', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'RETIRED', 'MEMORIAL')
    )
);

ALTER TABLE awards
    ADD CONSTRAINT fk_awards_users 
    FOREIGN KEY (user_id) REFERENCES users(user_id);

ALTER TABLE awards
    ADD CONSTRAINT fk_awards_categories 
    FOREIGN KEY (category_id) REFERENCES award_categories(category_id);
```

### 1.5 Конвенції Іменування Індексів

| **Тип Індексу** | **Шаблон** | **Приклади** |
|-----------------|------------|--------------|
| Стандартний | `idx_таблиця_колонка(и)` | `idx_users_email`, `idx_awards_status` |
| Унікальний | `uidx_таблиця_колонка(и)` | `uidx_users_email` (якщо не UK обмеження) |
| Композитний | `idx_таблиця_кол1_кол2` | `idx_awards_user_date` |
| Частковий | `idx_таблиця_колонка_умова` | `idx_awards_status_pending` |
| GIN (JSONB) | `gin_таблиця_колонка` | `gin_documents_metadata` |
| GiST | `gist_таблиця_колонка` | `gist_locations_coordinates` |
| Повнотекстовий | `ftidx_таблиця_колонка` | `ftidx_awards_title` |
| Виразу | `idx_таблиця_вираз` | `idx_users_lower_email` |

```sql
-- ✅ ПРАВИЛЬНО: Приклади іменування та створення індексів

-- Стандартні індекси
CREATE INDEX idx_users_organization ON users(organization_id);
CREATE INDEX idx_awards_user ON awards(user_id);
CREATE INDEX idx_awards_status ON awards(status);
CREATE INDEX idx_awards_date ON awards(award_date);

-- Композитні індекси (порядок колонок важливий для оптимізації запитів)
CREATE INDEX idx_awards_user_status ON awards(user_id, status);
CREATE INDEX idx_awards_category_date ON awards(category_id, award_date DESC);

-- Часткові індекси (для часто запитуваних підмножин)
CREATE INDEX idx_awards_status_pending ON awards(status) 
    WHERE status = 'PENDING';
CREATE INDEX idx_requests_active ON award_requests(current_reviewer_id) 
    WHERE status IN ('SUBMITTED', 'IN_REVIEW');

-- GIN індекс для JSONB колонок
CREATE INDEX gin_documents_metadata ON documents USING GIN (parsed_metadata);

-- Повнотекстовий пошуковий індекс
CREATE INDEX ftidx_awards_title ON awards USING GIN (to_tsvector('english', title));

-- Індекс на основі виразу
CREATE INDEX idx_users_lower_email ON users(LOWER(email_address));
```

### 1.6 Іменування Послідовностей та Інших Об'єктів

| **Тип Об'єкта** | **Шаблон** | **Приклади** |
|-----------------|------------|--------------|
| Послідовність | `seq_таблиця_колонка` | `seq_users_user_id` (якщо ручна) |
| View | `vw_призначення` | `vw_active_awards`, `vw_pending_requests` |
| Матеріалізоване View | `mvw_призначення` | `mvw_award_statistics` |
| Функція | `fn_призначення` | `fn_calculate_impact_score` |
| Процедура | `sp_призначення` | `sp_process_expired_requests` |
| Тригер | `trg_таблиця_подія` | `trg_users_updated_at`, `trg_awards_audit` |
| Тип/Enum | `enum_домен` | `enum_user_status`, `enum_approval_level` |
| Домен | `dom_призначення` | `dom_email`, `dom_positive_integer` |

```sql
-- ✅ ПРАВИЛЬНО: Приклади іменування інших об'єктів

-- Views
CREATE VIEW vw_active_awards AS
    SELECT * FROM awards WHERE status = 'APPROVED';

CREATE VIEW vw_pending_requests AS
    SELECT * FROM award_requests WHERE status IN ('SUBMITTED', 'IN_REVIEW');

-- Матеріалізоване view для звітності
CREATE MATERIALIZED VIEW mvw_award_statistics AS
    SELECT 
        organization_id,
        COUNT(*) as total_awards,
        COUNT(*) FILTER (WHERE status = 'APPROVED') as approved_awards
    FROM awards
    GROUP BY organization_id;

-- Функції
CREATE FUNCTION fn_calculate_impact_score(award_id BIGINT) 
    RETURNS INTEGER AS $$ ... $$ LANGUAGE plpgsql;

-- Тригери
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

CREATE TRIGGER trg_awards_audit
    AFTER INSERT OR UPDATE OR DELETE ON awards
    FOR EACH ROW EXECUTE FUNCTION fn_audit_log();

-- Кастомні типи/enum
CREATE TYPE enum_user_status AS ENUM (
    'PENDING', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'RETIRED', 'MEMORIAL'
);

CREATE TYPE enum_approval_level AS ENUM (
    'FACULTY_SECRETARY', 'DEAN', 'RECTOR_SECRETARY', 'RECTOR'
);
```

---

## 2. Стандарти Типів Даних

### 2.1 Стандартні Відображення Типів Даних

| **Бізнес Тип** | **Тип PostgreSQL** | **Тип Java** | **Примітки** |
|----------------|-------------------|--------------|--------------|
| Ідентифікатори | `BIGSERIAL` / `BIGINT` | `Long` | Автоінкремент для PK |
| Короткий текст | `VARCHAR(n)` | `String` | Вказуйте макс. довжину |
| Довгий текст | `TEXT` | `String` | Необмежена довжина |
| Булевий | `BOOLEAN` | `Boolean` | TRUE/FALSE/NULL |
| Ціле число | `INTEGER` | `Integer` | діапазон -2B до +2B |
| Десятковий/Гроші | `NUMERIC(p,s)` | `BigDecimal` | Точна точність |
| Тільки дата | `DATE` | `LocalDate` | Без компонента часу |
| Мітка часу | `TIMESTAMP WITH TIME ZONE` | `Instant` | Завжди з часовим поясом |
| Тривалість | `INTERVAL` | `Duration` | Проміжки часу |
| JSON дані | `JSONB` | `JsonNode` | Бінарний JSON, індексований |
| UUID | `UUID` | `UUID` | Для зовнішніх ідентифікаторів |
| IP адреса | `INET` | `String` | Підтримка IPv4/IPv6 |
| Бінарні дані | `BYTEA` | `byte[]` | Малі бінарні; для великих використовуйте object storage |
| Enum | `VARCHAR(50)` або кастомний `ENUM` | `Enum` | Див. стратегію enum нижче |

### 2.2 Рекомендовані Розміри Колонок

| **Категорія Даних** | **Тип та Розмір** | **Обґрунтування** |
|--------------------|-------------------|-------------------|
| Адреси email | `VARCHAR(255)` | Максимум RFC 5321 |
| Імена (ім'я/прізвище) | `VARCHAR(100)` | Підтримка міжнародних імен |
| Повні імена | `VARCHAR(255)` | Об'єднані імена |
| Заголовки/Теми | `VARCHAR(500)` | Назви нагород можуть бути довгими |
| Описи | `TEXT` | Без довільного обмеження |
| URL | `VARCHAR(2048)` | Безпечний максимум браузера |
| Шляхи файлів | `VARCHAR(500)` | Розумна довжина шляху |
| Коди статусу | `VARCHAR(30)` | Значення enum |
| Хеш-значення | `VARCHAR(255)` | Вивід BCrypt/SHA |
| Телефонні номери | `VARCHAR(20)` | Міжнародний формат |
| Поштові індекси | `VARCHAR(20)` | Міжнародні формати |

### 2.3 Стратегія Enum

Для цього проєкту використовуємо **VARCHAR з CHECK обмеженнями** замість типів PostgreSQL ENUM:

**Обґрунтування:**
- Простіші міграції схеми (додавання значень не вимагає `ALTER TYPE`)
- Краща сумісність з JPA/Hibernate
- Чіткіші дані при експорті
- Дружня до Flyway еволюція

```sql
-- ✅ РЕКОМЕНДОВАНО: VARCHAR з CHECK обмеженням
CREATE TABLE users (
    account_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT ck_users_status CHECK (
        account_status IN ('PENDING', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'RETIRED', 'MEMORIAL')
    )
);

-- Додавання нового значення статусу - простий ALTER:
ALTER TABLE users DROP CONSTRAINT ck_users_status;
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (
    account_status IN ('PENDING', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'RETIRED', 'MEMORIAL', 'ARCHIVED')
);

-- ⚠️ АЛЬТЕРНАТИВА: PostgreSQL ENUM (використовуйте тільки якщо значення дійсно фіксовані)
CREATE TYPE enum_recognition_level AS ENUM (
    'DEPARTMENT', 'FACULTY', 'UNIVERSITY', 'NATIONAL', 'INTERNATIONAL'
);
```

---

## 3. Шаблони Проєктування Таблиць

### 3.1 Стандартна Структура Сутності

Усі бізнес-сутності дотримуються цієї стандартної структури:

```sql
-- Шаблон стандартної сутності
CREATE TABLE назва_сутності (
    -- Первинний Ключ
    назва_сутності_id BIGSERIAL,
    
    -- Бізнес Атрибути
    -- ... специфічні для домену колонки ...
    
    -- Зовнішні Ключі
    -- ... колонки посилань ...
    
    -- Аудиторські Колонки (обов'язкові для всіх сутностей)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,  -- Посилається на users.user_id
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,  -- Посилається на users.user_id
    
    -- Оптимістичне Блокування (для конкурентного доступу)
    version BIGINT NOT NULL DEFAULT 1,
    
    -- Підтримка М'якого Видалення (опційно, за сутністю)
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by BIGINT,
    
    -- Обмеження
    CONSTRAINT pk_назва_сутності PRIMARY KEY (назва_сутності_id)
);
```

### 3.2 Шаблон Аудиторського Сліду

Для відповідності GDPR та відстеження походження даних критичні сутності мають виділені таблиці аудиту:

```sql
-- Шаблон таблиці аудиту
CREATE TABLE awards_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    
    -- Посилання на оригінальний запис
    award_id BIGINT NOT NULL,
    
    -- Метадані аудиту
    operation VARCHAR(10) NOT NULL,  -- INSERT, UPDATE, DELETE
    operation_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operation_user_id BIGINT,
    
    -- Відстеження змін
    old_values JSONB,
    new_values JSONB,
    changed_fields TEXT[],
    
    -- Контекст
    ip_address INET,
    user_agent VARCHAR(500),
    correlation_id UUID,
    
    CONSTRAINT ck_awards_audit_operation CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE'))
);

-- Індекс для запитів історії аудиту
CREATE INDEX idx_awards_audit_award ON awards_audit(award_id);
CREATE INDEX idx_awards_audit_timestamp ON awards_audit(operation_timestamp);
CREATE INDEX idx_awards_audit_user ON awards_audit(operation_user_id);
```

### 3.3 Шаблон Ієрархічних Даних (Організації)

Для організаційної ієрархії (Університет → Факультет → Кафедра):

```sql
-- Шаблон самопосилання ієрархії
CREATE TABLE organizations (
    org_id BIGSERIAL,
    
    -- Ієрархія
    parent_org_id BIGINT,
    org_type VARCHAR(20) NOT NULL,
    hierarchy_path LTREE,  -- Розширення PostgreSQL ltree для ефективних запитів
    depth INTEGER NOT NULL DEFAULT 0,
    
    -- Бізнес атрибути
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50),
    
    -- Стандартні аудиторські колонки
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 1,
    
    CONSTRAINT pk_organizations PRIMARY KEY (org_id),
    CONSTRAINT fk_organizations_parent FOREIGN KEY (parent_org_id) 
        REFERENCES organizations(org_id),
    CONSTRAINT ck_organizations_type CHECK (org_type IN ('UNIVERSITY', 'FACULTY', 'DEPARTMENT'))
);

-- Увімкнення розширення ltree (виконується один раз)
CREATE EXTENSION IF NOT EXISTS ltree;

-- Індекс для ієрархічних запитів
CREATE INDEX idx_organizations_parent ON organizations(parent_org_id);
CREATE INDEX gist_organizations_path ON organizations USING GIST (hierarchy_path);
```

### 3.4 Шаблон Часових Даних (Дійсність Ролей)

Для обмежених у часі даних як призначення ролей:

```sql
-- Шаблон часової дійсності
CREATE TABLE user_roles (
    user_role_id BIGSERIAL,
    user_id BIGINT NOT NULL,
    role_type VARCHAR(30) NOT NULL,
    organization_id BIGINT NOT NULL,
    
    -- Часові межі
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,  -- NULL означає поточно дійсний
    
    -- Аудит
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    
    CONSTRAINT pk_user_roles PRIMARY KEY (user_role_id),
    CONSTRAINT fk_user_roles_users FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_user_roles_orgs FOREIGN KEY (organization_id) REFERENCES organizations(org_id),
    CONSTRAINT ck_user_roles_dates CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_user_roles_type CHECK (
        role_type IN ('EMPLOYEE', 'FACULTY_SECRETARY', 'DEAN', 'RECTOR_SECRETARY', 'RECTOR', 'SYSTEM_ADMIN', 'GDPR_OFFICER')
    )
);

-- Індекс для пошуку поточної ролі
CREATE INDEX idx_user_roles_user_current ON user_roles(user_id) 
    WHERE valid_to IS NULL OR valid_to >= CURRENT_DATE;
```

### 3.5 Шаблон Посилання Документа/Файлу

Для зберігання метаданих файлів (фактичні файли в object storage):

```sql
-- Шаблон посилання файлу (тільки метадані, файли в object storage)
CREATE TABLE documents (
    document_id BIGSERIAL,
    
    -- Батьківські посилання
    award_id BIGINT,
    request_id BIGINT,
    
    -- Метадані файлу
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    checksum_sha256 VARCHAR(64),
    
    -- Посилання на сховище (S3/Azure Blob path)
    storage_bucket VARCHAR(100) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    storage_url VARCHAR(2048),
    
    -- Результати AI обробки
    parsed_metadata JSONB,
    confidence_score NUMERIC(5,4),  -- 0.0000 до 1.0000
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed_at TIMESTAMP WITH TIME ZONE,
    
    -- Аудит
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by BIGINT NOT NULL,
    
    CONSTRAINT pk_documents PRIMARY KEY (document_id),
    CONSTRAINT fk_documents_awards FOREIGN KEY (award_id) REFERENCES awards(award_id),
    CONSTRAINT ck_documents_status CHECK (
        processing_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'MANUAL_REVIEW')
    )
);

-- Індекс для JSONB запитів
CREATE INDEX gin_documents_metadata ON documents USING GIN (parsed_metadata);
```

---

## 4. Стандарти Посилальної Цілісності

### 4.1 Політики Зовнішніх Ключів

| **Тип Зв'язку** | **ON DELETE** | **ON UPDATE** | **Випадок Використання** |
|-----------------|---------------|---------------|--------------------------|
| Сильне володіння | `CASCADE` | `CASCADE` | Дочірній не може існувати без батьківського |
| Слабке посилання | `SET NULL` | `CASCADE` | Посилання є опційним |
| Запобігання сиротам | `RESTRICT` | `CASCADE` | Вимагає явної обробки |
| Збереження аудиту | `NO ACTION` | `CASCADE` | Зберігати історію навіть якщо батьківський видалено |

```sql
-- Сильне володіння: Документи належать Нагородам
ALTER TABLE documents
    ADD CONSTRAINT fk_documents_awards 
    FOREIGN KEY (award_id) REFERENCES awards(award_id)
    ON DELETE CASCADE ON UPDATE CASCADE;

-- Слабке посилання: Поточний рецензент користувача може бути очищений
ALTER TABLE award_requests
    ADD CONSTRAINT fk_requests_reviewer 
    FOREIGN KEY (current_reviewer_id) REFERENCES users(user_id)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- Запобігання сиротам: Не можна видалити організацію з користувачами
ALTER TABLE users
    ADD CONSTRAINT fk_users_organizations 
    FOREIGN KEY (organization_id) REFERENCES organizations(org_id)
    ON DELETE RESTRICT ON UPDATE CASCADE;
```

### 4.2 Стандарти Обробки Null

| **Сценарій** | **Nullability** | **Обґрунтування** |
|--------------|-----------------|-------------------|
| Первинні ключі | `NOT NULL` | Завжди обов'язкові |
| Обов'язкові бізнес-дані | `NOT NULL` | Цілісність даних |
| Опційні бізнес-дані | Дозволити `NULL` | Справжня відсутність значення |
| Зовнішні ключі (обов'язкові) | `NOT NULL` | Зв'язок обов'язковий |
| Зовнішні ключі (опційні) | Дозволити `NULL` | Зв'язок опційний |
| Мітки часу (створення) | `NOT NULL` | Завжди відстежувати створення |
| Мітки часу (опційні події) | Дозволити `NULL` | Подія може не відбутися |
| Булеві | `NOT NULL DEFAULT` | Уникати тризначної логіки |

---

## 5. Шаблони Продуктивності Проєктування

### 5.1 Рекомендації Стратегії Індексування

| **Шаблон Запиту** | **Стратегія Індексу** | **Приклад** |
|-------------------|----------------------|-------------|
| Точний пошук | B-tree на колонці | `idx_users_email` |
| Діапазонні запити | B-tree на колонці | `idx_awards_date` |
| Кілька умов | Композитний індекс (спочатку селективніший) | `idx_awards_user_status` |
| Часта підмножина | Частковий індекс | `idx_awards_pending` |
| Запити JSON полів | GIN на JSONB колонці | `gin_documents_metadata` |
| Повнотекстовий пошук | GIN з tsvector | `ftidx_awards_title` |
| Регістронезалежний | Індекс виразу | `idx_users_lower_email` |
| Обхід ієрархії | GiST на ltree | `gist_orgs_path` |

### 5.2 Стратегія Партиціонування

Для таблиць великого об'єму (audit_logs, notifications) використовуйте партиціонування таблиць:

```sql
-- Партиціонування діапазону за датою для журналів аудиту
CREATE TABLE audit_logs (
    log_id BIGSERIAL,
    user_id BIGINT,
    action_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    old_values JSONB,
    new_values JSONB,
    ip_address INET,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_audit_logs PRIMARY KEY (log_id, created_at)
) PARTITION BY RANGE (created_at);

-- Місячні партиції (створюються через скрипти міграції під час розробки)
-- CREATE TABLE audit_logs_2025_01 PARTITION OF audit_logs
--     FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
```

### 5.3 Розмір Пулу З'єднань

Рекомендована конфігурація HikariCP для PostgreSQL:

```yaml
# Конфігурація застосунку (довідник для розробки)
spring:
  datasource:
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      idle-timeout: 300000        # 5 хвилин
      max-lifetime: 1200000       # 20 хвилин
      connection-timeout: 20000    # 20 секунд
      leak-detection-threshold: 60000  # 1 хвилина
```

---

## 6. Шаблони Відповідності GDPR

### 6.1 Класифікація Даних у Схемі

```sql
-- Коментарі колонок для класифікації даних
COMMENT ON COLUMN users.email_address IS 'GDPR: Персональні Дані - Контактна Інформація';
COMMENT ON COLUMN users.first_name IS 'GDPR: Персональні Дані - Ідентичність';
COMMENT ON COLUMN users.last_name IS 'GDPR: Персональні Дані - Ідентичність';
COMMENT ON COLUMN users.password_hash IS 'GDPR: Конфіденційно - Обліковий Запис Безпеки (хешований)';
COMMENT ON COLUMN audit_logs.ip_address IS 'GDPR: Персональні Дані - Технічний Ідентифікатор';
```

### 6.2 Підтримка Права на Видалення

Шаблон проєктування для підтримки Статті 17 GDPR (Право на Видалення):

```sql
-- Функція анонімізації (шаблон стратегії)
-- Нагороди анонімізуються, а не видаляються (цінність публічного запису)
-- Персональні дані видаляються зберігаючи запис досягнення

-- Приклад підходу анонімізації (реалізовано через рівень застосунку):
-- UPDATE users SET 
--     email_address = 'deleted_' || user_id || '@anonymized.local',
--     first_name = 'ВИДАЛЕНО',
--     last_name = 'КОРИСТУВАЧ',
--     password_hash = 'DELETED',
--     account_status = 'DELETED',
--     deleted_at = CURRENT_TIMESTAMP
-- WHERE user_id = ?;
```

### 6.3 Шаблон Відстеження Згоди

```sql
-- Гранулярне відстеження згоди
CREATE TABLE consent_records (
    consent_id BIGSERIAL,
    user_id BIGINT NOT NULL,
    
    -- Деталі згоди
    consent_type VARCHAR(50) NOT NULL,
    consent_version VARCHAR(20) NOT NULL,
    
    -- Відстеження стану
    is_granted BOOLEAN NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE,
    withdrawn_at TIMESTAMP WITH TIME ZONE,
    
    -- Аудит
    ip_address INET,
    user_agent VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_consent_records PRIMARY KEY (consent_id),
    CONSTRAINT fk_consent_users FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT ck_consent_type CHECK (
        consent_type IN ('DATA_PROCESSING', 'PUBLIC_VISIBILITY', 'EMAIL_NOTIFICATIONS', 'SMS_NOTIFICATIONS', 'ANALYTICS')
    )
);

-- Індекс для пошуку згоди користувача
CREATE INDEX idx_consent_user_type ON consent_records(user_id, consent_type);
```

---

## 7. Стандарти Документування Схеми

### 7.1 Обов'язкові Коментарі

Всі об'єкти бази даних повинні мати описові коментарі:

```sql
-- Коментарі таблиць
COMMENT ON TABLE users IS 'Користувачі системи включаючи співробітників та адміністраторів. Центральна сутність ідентичності для системи моніторингу нагород.';
COMMENT ON TABLE awards IS 'Записи нагород представляють професійні досягнення та визнання. Основна бізнес-сутність.';
COMMENT ON TABLE award_requests IS 'Запити робочого процесу для затвердження нагород. Відстежує багаторівневий процес затвердження.';

-- Коментарі колонок (особливо для неочевидних колонок)
COMMENT ON COLUMN awards.impact_score IS 'Розрахований бал (0-100) що представляє значущість нагороди на основі категорії, рівня та організації.';
COMMENT ON COLUMN documents.confidence_score IS 'Впевненість AI моделі (0.0-1.0) для точності витягу метаданих.';
COMMENT ON COLUMN award_requests.current_level IS 'Поточна позиція в робочому процесі затвердження: FACULTY_SECRETARY → DEAN → RECTOR_SECRETARY → RECTOR';

-- Коментарі індексів
COMMENT ON INDEX idx_awards_status_pending IS 'Частковий індекс для оптимізації запитів очікуючих нагород. Використовується панеллю рецензента.';
```

### 7.2 Відстеження Версії Схеми

```sql
-- Таблиця версії схеми (керується Flyway)
-- Таблиця flyway_schema_history створюється автоматично

-- Додаткова таблиця метаданих для кастомного відстеження
CREATE TABLE schema_metadata (
    metadata_id SERIAL PRIMARY KEY,
    schema_version VARCHAR(50) NOT NULL,
    description TEXT,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_by VARCHAR(100),
    checksum VARCHAR(64)
);
```

---

## 8. Міркування Специфічні для Середовища

### 8.1 Середовище Розробки

- Використовуйте Docker PostgreSQL 16 контейнер
- Seed дані через повторювані міграції Flyway
- Увімкніть `log_statement = 'all'` для дебагу запитів
- Використовуйте `pgAdmin` або `DBeaver` для візуалізації схеми

### 8.2 Тестове Середовище

- Використовуйте Testcontainers для інтеграційних тестів
- Свіжа база даних для кожного класу тестів/набору
- Вимикайте перевірку зовнішніх ключів тільки для unit тестів
- Використовуйте in-memory H2 тільки для простих тестів репозиторію

### 8.3 Продакшн Середовище

- Пул з'єднань через HikariCP
- SSL/TLS обов'язковий для всіх з'єднань
- Репліки для читання для звітних запитів
- Автоматизовані резервні копії з point-in-time recovery
- Моніторинг через pg_stat_statements

---

## 9. Контрольний Список Перевірки

Перед створенням будь-якого об'єкту бази даних перевірте:

- [ ] **Іменування**: Дотримується конвенцій Розділу 1
- [ ] **Типи Даних**: Використовує стандартні типи з Розділу 2
- [ ] **Обмеження**: Всі обмеження правильно названі
- [ ] **Індекси**: Відповідні індекси для шаблонів запитів
- [ ] **Аудиторські Колонки**: Включає created_at, updated_at, version
- [ ] **Коментарі**: Таблиця та ключові колонки задокументовані
- [ ] **GDPR**: Колонки персональних даних ідентифіковані
- [ ] **Зовнішні Ключі**: Правильні політики ON DELETE/UPDATE
- [ ] **Обробка Null**: Явне рішення NOT NULL або NULL

---

## Додаток A: Коротка Довідкова Картка

### Зведення Шаблонів Іменування

| Об'єкт | Шаблон | Приклад |
|--------|---------|---------|
| Таблиця | `множина_snake_case` | `award_categories` |
| Колонка | `snake_case` | `first_name` |
| Первинний Ключ | `table_id` | `user_id` |
| Зовнішній Ключ | `referenced_id` | `organization_id` |
| PK Обмеження | `pk_table` | `pk_users` |
| FK Обмеження | `fk_child_parent` | `fk_awards_users` |
| Унікальне Обмеження | `uk_table_column` | `uk_users_email` |
| Check Обмеження | `ck_table_column` | `ck_users_status` |
| Індекс | `idx_table_column` | `idx_awards_date` |
| Частковий Індекс | `idx_table_col_cond` | `idx_awards_pending` |
| View | `vw_purpose` | `vw_active_awards` |
| Функція | `fn_purpose` | `fn_calc_score` |
| Тригер | `trg_table_event` | `trg_users_audit` |

---

*Версія Документа: 1.0*  
*Класифікація: Внутрішній*  
*Фаза: 9 - Архітектура Даних та Проєктування Бази Даних*  
*Автор: Стефан Костик*

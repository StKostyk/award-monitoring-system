# Стратегія Продуктивності Бази Даних
## Система Моніторингу та Відстеження Нагород

> **Результат Фази 9**: Архітектура Даних та Проєктування Бази Даних  
> **Версія Документа**: 1.0  
> **Останнє Оновлення**: Грудень 2025  
> **Автор**: Стефан Костик  
> **База Даних**: PostgreSQL 16  
> **Класифікація**: Внутрішній

---

## Резюме

Цей документ визначає комплексну стратегію продуктивності бази даних для Системи Моніторингу та Відстеження Нагород. Він охоплює рекомендації щодо індексації, стратегії партиціонування, техніки оптимізації запитів, connection pooling та конфігурацію моніторингу. Ці стратегії забезпечують ефективні операції бази даних при масштабуванні системи для підтримки багатьох українських університетів та їх запитів на нагороди.

### Характеристики Продуктивності

| **Метрика** | **Цільове Значення** | **Критичний Поріг** |
|-------------|---------------------|---------------------|
| Час відповіді API (p95) | < 200ms | > 500ms |
| Затримка запитів БД (p95) | < 50ms | > 100ms |
| Connection utilization | < 70% | > 85% |
| Cache hit ratio | > 95% | < 90% |
| Одночасні з'єднання | < 100 | > 150 |

---

## 1. Стратегія Індексації

### 1.1 Матриця Типів Індексів

| **Тип Індексу** | **Випадок Використання** | **Приклад Колонок** |
|-----------------|-------------------------|---------------------|
| **B-tree (default)** | Порівняння рівності та діапазонів | `user_id`, `created_at`, `status` |
| **Hash** | Тільки порівняння рівності | `email_address` (тільки точний match) |
| **GIN** | JSONB, масиви, повнотекстовий пошук | `metadata`, `award_criteria` |
| **GiST** | Геометричні та діапазонні типи | Діапазони дат, геолокації |
| **BRIN** | Великі впорядковані таблиці | `audit_logs.created_at` |
| **Partial** | Підмножина рядків | `WHERE status = 'PENDING'` |
| **Covering** | Включає додаткові колонки | Index-only сканування |

### 1.2 Модель Індексації Таблиці

#### Users Table

```sql
-- Первинний ключ (автоматичний унікальний індекс)
-- PRIMARY KEY (user_id)

-- Пошук автентифікації (унікальний, часті lookup)
CREATE UNIQUE INDEX idx_users_email_unique 
    ON users (email_address);

-- Фільтрація організації (часті запити)
CREATE INDEX idx_users_organization 
    ON users (organization_id) 
    WHERE account_status = 'ACTIVE';

-- Рольові запити
CREATE INDEX idx_users_role_status 
    ON users (role, account_status);

-- Пошук імен (case-insensitive)
CREATE INDEX idx_users_name_search 
    ON users (LOWER(first_name), LOWER(last_name));
```

#### Awards Table

```sql
-- Первинний ключ (автоматичний)
-- PRIMARY KEY (award_id)

-- Lookup отримувача (часті запити)
CREATE INDEX idx_awards_user 
    ON awards (user_id) 
    INCLUDE (award_status, award_date);

-- Фільтрація статусу (часте фільтрування)
CREATE INDEX idx_awards_status 
    ON awards (award_status) 
    WHERE award_status IN ('PENDING', 'UNDER_REVIEW');

-- Фільтрація категорій
CREATE INDEX idx_awards_category 
    ON awards (category_id, award_date DESC);

-- Фільтрація організації
CREATE INDEX idx_awards_organization 
    ON awards (organization_id, award_date DESC);

-- Запити діапазону дат
CREATE INDEX idx_awards_date_range 
    ON awards (award_date DESC) 
    INCLUDE (award_status, user_id);

-- Повнотекстовий пошук
CREATE INDEX idx_awards_search 
    ON awards USING GIN (to_tsvector('english', description));
```

#### Award Requests Table

```sql
-- Первинний ключ (автоматичний)
-- PRIMARY KEY (request_id)

-- Запити подавача
CREATE INDEX idx_requests_user 
    ON award_requests (user_id, submitted_at DESC);

-- Workload рецензента
CREATE INDEX idx_requests_reviewer 
    ON award_requests (assigned_reviewer_id, request_status)
    WHERE request_status = 'UNDER_REVIEW';

-- Обробка черги статусів
CREATE INDEX idx_requests_status_queue 
    ON award_requests (request_status, priority, submitted_at)
    WHERE request_status IN ('PENDING', 'UNDER_REVIEW');

-- Запит на нагороду lookup
CREATE INDEX idx_requests_award 
    ON award_requests (award_id);
```

#### Audit Logs Table

```sql
-- BRIN індекс для впорядкованих за часом даних (ефективний для великих таблиць)
CREATE INDEX idx_audit_created_brin 
    ON audit_logs USING BRIN (created_at);

-- Пошук активності користувача
CREATE INDEX idx_audit_user_action 
    ON audit_logs (user_id, action_type, created_at DESC);

-- Пошук активності сутності
CREATE INDEX idx_audit_entity 
    ON audit_logs (entity_type, entity_id, created_at DESC);

-- Пошук типу дії
CREATE INDEX idx_audit_action_type 
    ON audit_logs (action_type, created_at DESC);
```

### 1.3 Візуалізація Стратегії Індексів

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         СТРАТЕГІЯ ІНДЕКСАЦІЇ                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ПАТЕРН ДОСТУПУ → РІШЕННЯ ІНДЕКСУ                                           │
│  ───────────────────────────────────                                         │
│                                                                              │
│  ┌────────────────────────┐      ┌────────────────────────────────────┐     │
│  │ Рівність (=)           │─────►│ B-tree (default)                   │     │
│  │ "WHERE user_id = 123"  │      │ Ефективний для точних matches      │     │
│  └────────────────────────┘      └────────────────────────────────────┘     │
│                                                                              │
│  ┌────────────────────────┐      ┌────────────────────────────────────┐     │
│  │ Діапазон (<, >, BETWEEN)│─────►│ B-tree                             │     │
│  │ "WHERE date > '2025'"  │      │ Ефективний для діапазонних сканів  │     │
│  └────────────────────────┘      └────────────────────────────────────┘     │
│                                                                              │
│  ┌────────────────────────┐      ┌────────────────────────────────────┐     │
│  │ JSONB запити           │─────►│ GIN                                │     │
│  │ "WHERE meta @> '{}''"  │      │ Ефективний для containment queries │     │
│  └────────────────────────┘      └────────────────────────────────────┘     │
│                                                                              │
│  ┌────────────────────────┐      ┌────────────────────────────────────┐     │
│  │ Повнотекстовий пошук   │─────►│ GIN + to_tsvector                  │     │
│  │ "WHERE ts @@ query"    │      │ Ефективний для текстового пошуку   │     │
│  └────────────────────────┘      └────────────────────────────────────┘     │
│                                                                              │
│  ┌────────────────────────┐      ┌────────────────────────────────────┐     │
│  │ Великі впорядковані    │─────►│ BRIN                               │     │
│  │ "audit_logs by date"   │      │ Мінімальне сховище, швидкі скани   │     │
│  └────────────────────────┘      └────────────────────────────────────┘     │
│                                                                              │
│  ┌────────────────────────┐      ┌────────────────────────────────────┐     │
│  │ Вибіркові запити       │─────►│ Partial Index                      │     │
│  │ "WHERE status='ACTIVE'"│      │ Менший індекс, швидші оновлення    │     │
│  └────────────────────────┘      └────────────────────────────────────┘     │
│                                                                              │
│  ┌────────────────────────┐      ┌────────────────────────────────────┐     │
│  │ Уникнути heap доступу  │─────►│ Covering Index (INCLUDE)           │     │
│  │ "SELECT a,b WHERE c"   │      │ Index-only скани                   │     │
│  └────────────────────────┘      └────────────────────────────────────┘     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.4 Рекомендації Обслуговування Індексів

| **Завдання** | **Частота** | **Команда** | **Призначення** |
|--------------|-------------|-------------|-----------------|
| Оновлення статистики | Щоденно | `ANALYZE table_name` | Точне планування запитів |
| Перевірка роздутості індексів | Щотижня | Кастомний запит | Виявлення деградації |
| Перебудова роздутих індексів | За потреби | `REINDEX CONCURRENTLY` | Відновлення продуктивності |
| Огляд невикористаних індексів | Щомісяця | `pg_stat_user_indexes` | Видалення overhead |
| Огляд відсутніх індексів | Щомісяця | `pg_stat_user_tables` | Виявлення seq сканів |

```sql
-- Знаходження невикористаних індексів
SELECT 
    schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
WHERE idx_scan = 0
ORDER BY pg_relation_size(indexrelid) DESC;

-- Знаходження таблиць з високими sequential сканами (можливо потрібні індекси)
SELECT 
    relname, seq_scan, seq_tup_read, 
    idx_scan, idx_tup_fetch,
    ROUND(100.0 * seq_scan / NULLIF(seq_scan + idx_scan, 0), 2) AS seq_pct
FROM pg_stat_user_tables
WHERE seq_scan > 100
ORDER BY seq_tup_read DESC;

-- Оцінка роздутості індексів
SELECT 
    indexrelname,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
    idx_scan AS scans,
    idx_tup_read AS tuples_read
FROM pg_stat_user_indexes
ORDER BY pg_relation_size(indexrelid) DESC;
```

---

## 2. Стратегія Партиціонування

### 2.1 Визначення Кандидатів на Партиціонування

| **Критерій** | **Поріг** | **Наші Кандидати** |
|--------------|-----------|-------------------|
| Розмір таблиці | > 10GB | `audit_logs` |
| Число рядків | > 10M | `audit_logs`, `notifications` |
| Патерн запитів на основі часу | Діапазонні запити | `audit_logs`, `notifications` |
| Вимоги архівування | Регулярне видалення | `audit_logs`, `notifications` |

### 2.2 Схема Партиціонування: Audit Logs

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   ПАРТИЦІОНУВАННЯ AUDIT_LOGS                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Стратегія: RANGE партиціонування за місяцями                               │
│  Причина: Запити на основі часу, вимоги зберігання, архівування             │
│                                                                              │
│  audit_logs (Батьківська таблиця - партиціонована)                          │
│  ├── audit_logs_2025_01 (Січень 2025)                                       │
│  ├── audit_logs_2025_02 (Лютий 2025)                                        │
│  ├── audit_logs_2025_03 (Березень 2025)                                     │
│  ├── audit_logs_2025_04 (Квітень 2025)                                      │
│  ├── ...                                                                    │
│  └── audit_logs_default (Fallback для несподіваних дат)                     │
│                                                                              │
│  Переваги:                                                                  │
│  ─────────                                                                   │
│  • Partition pruning для діапазонних запитів (планувальник сканує тільки    │
│    релевантні партиції)                                                     │
│  • Легке архівування (DROP старих партицій замість DELETE)                  │
│  • Паралельні сканування через партиції                                     │
│  • Меншe роздуття індексів на партицію                                      │
│                                                                              │
│  Обслуговування:                                                            │
│  ──────────────                                                              │
│  • Автоматизоване створення партицій (завдання створює наступний місяць)    │
│  • Автоматизоване архівування (завдання видаляє партиції старіші 7 років)   │
│  • Моніторинг розміру партицій                                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

```sql
-- Визначення партиціонованої таблиці (для довідки розробки)
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
    
    PRIMARY KEY (log_id, created_at)  -- Ключ партиціонування в PK
) PARTITION BY RANGE (created_at);

-- Створення партицій (автоматизовано в продакшні)
CREATE TABLE audit_logs_2025_01 PARTITION OF audit_logs
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
    
CREATE TABLE audit_logs_2025_02 PARTITION OF audit_logs
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');

-- Default партиція для fallback
CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;

-- Функція для автоматизації створення партицій
CREATE OR REPLACE FUNCTION fn_create_audit_partition()
RETURNS void AS $$
DECLARE
    partition_date DATE := DATE_TRUNC('month', CURRENT_DATE + INTERVAL '1 month');
    partition_name TEXT := 'audit_logs_' || TO_CHAR(partition_date, 'YYYY_MM');
    start_date TEXT := TO_CHAR(partition_date, 'YYYY-MM-DD');
    end_date TEXT := TO_CHAR(partition_date + INTERVAL '1 month', 'YYYY-MM-DD');
BEGIN
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF audit_logs FOR VALUES FROM (%L) TO (%L)',
        partition_name, start_date, end_date
    );
    RAISE NOTICE 'Створено партицію: %', partition_name;
END;
$$ LANGUAGE plpgsql;
```

### 2.3 Оптимізація Запитів до Партицій

```sql
-- Ефективний запит (partition pruning застосовується)
SELECT * FROM audit_logs
WHERE created_at >= '2025-03-01' AND created_at < '2025-04-01';
-- Сканує тільки audit_logs_2025_03

-- Неефективний запит (сканує всі партиції)
SELECT * FROM audit_logs
WHERE DATE_PART('month', created_at) = 3;
-- Функція запобігає partition pruning!

-- Краще: Явне обмеження діапазону
SELECT * FROM audit_logs
WHERE created_at >= '2025-03-01' AND created_at < '2025-04-01';
```

---

## 3. Оптимізація Запитів

### 3.1 Загальні Патерни та Оптимізації

| **Патерн** | **Проблема** | **Рішення** |
|------------|--------------|-------------|
| SELECT * | Надлишкові дані | Вибирати тільки потрібні колонки |
| N+1 запити | Багато round trips | Жадібне завантаження / JOIN / батчинг |
| Функції в WHERE | Індекс не використовується | Використовувати індексовані колонки безпосередньо |
| LIKE '%term%' | Повне сканування таблиці | Повнотекстовий пошук або тригерні індекси |
| Великий OFFSET | Повільна пагінація | Keyset пагінація |
| OR clauses | Неефективне використання індексу | UNION ALL замість цього |

### 3.2 Патерни Оптимізації Запитів

#### Keyset Пагінація (замість OFFSET)

```sql
-- ПОГАНО: Offset пагінація (повільно для великих offsets)
SELECT * FROM awards 
ORDER BY award_date DESC, award_id DESC 
LIMIT 20 OFFSET 10000;
-- Читає 10020 рядків, відкидає 10000

-- ДОБРЕ: Keyset пагінація (постійна продуктивність)
SELECT * FROM awards 
WHERE (award_date, award_id) < ('2025-03-15', 12345)
ORDER BY award_date DESC, award_id DESC 
LIMIT 20;
-- Переходить безпосередньо до позиції через індекс
```

#### Оптимізація Батчинга

```sql
-- ПОГАНО: N+1 запити
-- Застосунок виконує: SELECT * FROM awards WHERE user_id = ? 
-- ... для кожного з 100 користувачів

-- ДОБРЕ: Batch запит
SELECT * FROM awards 
WHERE user_id = ANY(ARRAY[1, 2, 3, ... 100])
ORDER BY user_id, award_date DESC;
```

#### Ефективні Підрахунки

```sql
-- ПОГАНО: Точний підрахунок для великих таблиць (повільно)
SELECT COUNT(*) FROM audit_logs;

-- ДОБРЕ: Приблизний підрахунок (майже миттєво)
SELECT reltuples::BIGINT AS estimate
FROM pg_class
WHERE relname = 'audit_logs';

-- ДОБРЕ: Точний підрахунок з умовою (якщо часто запитується, розгляньте materialized view)
SELECT COUNT(*) FROM audit_logs
WHERE created_at >= CURRENT_DATE - INTERVAL '7 days';
```

### 3.3 Оптимізація Специфічних Запитів

#### Dashboard Запит: Підсумки Нагород

```sql
-- До оптимізації: Множинні підзапити
SELECT 
    (SELECT COUNT(*) FROM awards WHERE award_status = 'APPROVED') AS approved,
    (SELECT COUNT(*) FROM awards WHERE award_status = 'PENDING') AS pending,
    (SELECT COUNT(*) FROM awards WHERE award_status = 'REJECTED') AS rejected;
-- 3 окремі сканування

-- Після оптимізації: Одне сканування з умовною агрегацією
SELECT 
    COUNT(*) FILTER (WHERE award_status = 'APPROVED') AS approved,
    COUNT(*) FILTER (WHERE award_status = 'PENDING') AS pending,
    COUNT(*) FILTER (WHERE award_status = 'REJECTED') AS rejected
FROM awards;
-- 1 сканування з filter clause
```

#### Dashboard Запит: Останні Нагороди з Деталями

```sql
-- Оптимізований запит з covering індексами
SELECT 
    a.award_id, a.award_title, a.award_date, a.award_status,
    u.first_name, u.last_name,
    c.category_name
FROM awards a
JOIN users u ON a.user_id = u.user_id
JOIN award_categories c ON a.category_id = c.category_id
WHERE a.organization_id = :org_id
ORDER BY a.award_date DESC
LIMIT 10;

-- Допоміжний індекс
CREATE INDEX idx_awards_org_date 
    ON awards (organization_id, award_date DESC)
    INCLUDE (award_id, award_title, award_status, user_id, category_id);
```

### 3.4 Аналіз Плану Виконання

```sql
-- Завжди аналізувати повільні запити з EXPLAIN ANALYZE
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM awards 
WHERE user_id = 123 AND award_status = 'APPROVED';

-- Ключові метрики для моніторингу:
-- - Actual time vs планований
-- - Rows vs кількість що планувались
-- - Buffers (shared hit vs read)
-- - Seq scan на великих таблицях (поганий знак)
-- - Nested loops з високим row множником

-- Приклад хорошого плану:
-- Index Scan using idx_awards_user on awards  (cost=0.43..8.45 rows=1 width=200)
--   Index Cond: (user_id = 123)
--   Filter: (award_status = 'APPROVED'::award_status)
--   Buffers: shared hit=4

-- Приклад поганого плану (потрібен індекс):
-- Seq Scan on awards  (cost=0.00..10000.00 rows=500 width=200)
--   Filter: ((user_id = 123) AND (award_status = 'APPROVED'))
--   Rows Removed by Filter: 99500
--   Buffers: shared read=5000
```

---

## 4. Connection Pooling

### 4.1 Конфігурація HikariCP

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      АРХІТЕКТУРА CONNECTION POOLING                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                           ЗАСТОСУНОК                                  │  │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐    │  │
│  │  │ Thread 1│  │ Thread 2│  │ Thread 3│  │ Thread 4│  │ Thread N│    │  │
│  │  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘    │  │
│  │       │            │            │            │            │          │  │
│  │       └────────────┴────────────┼────────────┴────────────┘          │  │
│  │                                 │                                     │  │
│  │  ┌──────────────────────────────▼──────────────────────────────────┐ │  │
│  │  │                      HIKARICP POOL                              │ │  │
│  │  │                                                                 │ │  │
│  │  │   Maximum Pool Size: 10 з'єднань                                │ │  │
│  │  │   Minimum Idle: 5 з'єднань                                      │ │  │
│  │  │   Connection Timeout: 30 сек                                    │ │  │
│  │  │   Idle Timeout: 10 хв                                           │ │  │
│  │  │   Max Lifetime: 30 хв                                           │ │  │
│  │  │                                                                 │ │  │
│  │  │   ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐            │ │  │
│  │  │   │Conn│ │Conn│ │Conn│ │Conn│ │Conn│ │Idle│ │Idle│            │ │  │
│  │  │   │ 1  │ │ 2  │ │ 3  │ │ 4  │ │ 5  │ │ 6  │ │ 7  │            │ │  │
│  │  │   └──┬─┘ └──┬─┘ └──┬─┘ └──┬─┘ └──┬─┘ └────┘ └────┘            │ │  │
│  │  │      │      │      │      │      │                              │ │  │
│  │  └──────┴──────┴──────┼──────┴──────┴─────────────────────────────┘ │  │
│  │                       │                                              │  │
│  └───────────────────────┼──────────────────────────────────────────────┘  │
│                          │                                                  │
│  ┌───────────────────────▼──────────────────────────────────────────────┐  │
│  │                         POSTGRESQL                                    │  │
│  │                   Max Connections: 100                                │  │
│  │                   (shared across all app instances)                   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Рекомендована Конфігурація

```yaml
# application.yml - Конфігурація HikariCP (для довідки розробки)
spring:
  datasource:
    hikari:
      # Sizing пулу
      maximum-pool-size: 10       # Ядра * 2 + ефективні spindles
      minimum-idle: 5             # Підтримувати готові з'єднання
      
      # Timeouts
      connection-timeout: 30000   # 30 сек - помилка якщо немає з'єднання
      idle-timeout: 600000        # 10 хв - закрити idle з'єднання
      max-lifetime: 1800000       # 30 хв - перециклювати з'єднання
      
      # Валідація
      validation-timeout: 5000    # 5 сек - перевірка валідності з'єднання
      leak-detection-threshold: 60000  # 60 сек - лог якщо утримується так довго
      
      # Оптимізації продуктивності
      auto-commit: false          # Управляти транзакціями вручну
      connection-test-query: SELECT 1
      
      # PostgreSQL специфічні
      data-source-properties:
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        useServerPrepStmts: true
        reWriteBatchedInserts: true
```

### 4.3 Рекомендації Розміру Пулу

| **Сценарій** | **Max Pool Size** | **Min Idle** | **Обґрунтування** |
|--------------|-------------------|--------------|-------------------|
| Розробка | 5 | 2 | Мінімальні ресурси |
| Staging | 10 | 5 | Помірне навантаження |
| Продакшн (1 pod) | 10 | 5 | Балансована продуктивність |
| Продакшн (N pods) | 10/pod | 5/pod | Max 100 total (PG limit) |

**Формула**: `pool_size = (core_count * 2) + effective_spindle_count`

Для сучасних SSD серверів з 4 ядрами: `(4 * 2) + 1 = 9 ≈ 10`

---

## 5. Стратегія Кешування

### 5.1 Архітектура Багаторівневого Кешу

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      АРХІТЕКТУРА БАГАТОРІВНЕВОГО КЕШУ                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  РІВЕНЬ 1: In-Memory (Caffeine/Guava)                                       │
│  ────────────────────────────────────                                        │
│  • Локальний для інстансу застосунку                                        │
│  • Надшвидкий доступ (наносекунди)                                          │
│  • Обмежений розмір (памʼять JVM)                                           │
│  • Випадки використання: Конфігурації, категорії нагород, дозволи ролей     │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Pod 1              Pod 2              Pod 3                        │    │
│  │  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐               │    │
│  │  │  L1 Cache   │   │  L1 Cache   │   │  L1 Cache   │               │    │
│  │  │ (10K items) │   │ (10K items) │   │ (10K items) │               │    │
│  │  └──────┬──────┘   └──────┬──────┘   └──────┬──────┘               │    │
│  │         │                 │                 │                       │    │
│  └─────────┼─────────────────┼─────────────────┼───────────────────────┘    │
│            │                 │                 │                             │
│  РІВЕНЬ 2: Розподілений Кеш (Redis)                                         │
│  ──────────────────────────────────                                          │
│  • Спільний для всіх інстансів застосунку                                   │
│  • Швидкий доступ (мілісекунди)                                             │
│  • Великий обʼєм (кластер Redis)                                            │
│  • Випадки використання: Сесії, часто доступні дані, обчислені результати   │
│            │                 │                 │                             │
│  ┌─────────▼─────────────────▼─────────────────▼───────────────────────┐    │
│  │                        REDIS CLUSTER                                │    │
│  │   ┌──────────┐  ┌──────────┐  ┌──────────┐                         │    │
│  │   │  Master  │  │  Master  │  │  Master  │                         │    │
│  │   │  Shard 1 │  │  Shard 2 │  │  Shard 3 │                         │    │
│  │   └──────────┘  └──────────┘  └──────────┘                         │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│  РІВЕНЬ 3: База Даних (PostgreSQL)                                          │
│  ─────────────────────────────────                                           │
│  • Джерело істини                                                           │
│  • Повільніший доступ (мілісекунди до секунд)                               │
│  • Повна узгодженість даних                                                 │
│  • Випадки використання: Всі персистентні дані                              │
│                                    │                                         │
│  ┌─────────────────────────────────▼───────────────────────────────────┐    │
│  │                         POSTGRESQL                                  │    │
│  │                    (Source of Truth)                                │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Стратегія Кешування за Сутністю

| **Сутність** | **Рівень Кешу** | **TTL** | **Стратегія Інвалідації** |
|--------------|-----------------|---------|---------------------------|
| Категорії Нагород | L1 + L2 | 24 год | TTL + ручна при оновленні |
| Дозволи Ролей | L1 + L2 | 1 год | Ручна при зміні ролі |
| Організації | L1 + L2 | 12 год | TTL + подія при оновленні |
| Користувачі | L2 тільки | 15 хв | Write-through |
| Нагороди | L2 тільки | 5 хв | Write-through |
| Dashboard Агрегати | L2 тільки | 1 хв | TTL |
| Аудит Логи | Без кешу | - | Завжди свіжі |

### 5.3 Стратегія Інвалідації Кешу

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   ПАТЕРНИ ІНВАЛІДАЦІЇ КЕШУ                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ПАТЕРН 1: Write-Through (Транзакційний)                                    │
│  ─────────────────────────────────────────                                   │
│                                                                              │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐                  │
│  │ Запит на│───►│ Оновити │───►│ Оновити │───►│  Успіх  │                  │
│  │ Запис   │    │   БД    │    │   Кеш   │    │         │                  │
│  └─────────┘    └─────────┘    └─────────┘    └─────────┘                  │
│                                                                              │
│  Використовувати для: Users, Awards (узгодженість критична)                 │
│                                                                              │
│  ПАТЕРН 2: Cache-Aside з TTL                                                │
│  ────────────────────────────                                                │
│                                                                              │
│  Читання:                                                                   │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐                                 │
│  │ Запит на│───►│Перевірка│─HIT─►│Повернути│                               │
│  │ Читання │    │   Кеш   │     │   Кеш   │                                │
│  └─────────┘    └────┬────┘     └─────────┘                                │
│                 MISS │                                                       │
│                      ▼                                                       │
│                ┌─────────┐    ┌─────────┐    ┌─────────┐                   │
│                │ Запит   │───►│ Заповнити│───►│Повернути│                  │
│                │   БД    │    │   Кеш    │    │  Дані   │                  │
│                └─────────┘    └─────────┘    └─────────┘                   │
│                                                                              │
│  Використовувати для: Категорії Нагород, Організації (TTL достатній)        │
│                                                                              │
│  ПАТЕРН 3: Pub/Sub Інвалідація                                              │
│  ──────────────────────────────                                              │
│                                                                              │
│  ┌─────────┐  Публікувати  ┌─────────┐                                     │
│  │ Запис у │──────────────►│  Redis  │                                     │
│  │   БД    │   подію       │ Pub/Sub │                                     │
│  └─────────┘               └────┬────┘                                     │
│                                 │                                            │
│          ┌──────────────────────┼──────────────────────┐                    │
│          │                      │                      │                     │
│          ▼                      ▼                      ▼                     │
│     ┌─────────┐            ┌─────────┐            ┌─────────┐              │
│     │Інвалід. │            │Інвалід. │            │Інвалід. │              │
│     │ Pod 1   │            │ Pod 2   │            │ Pod 3   │              │
│     └─────────┘            └─────────┘            └─────────┘              │
│                                                                              │
│  Використовувати для: Права доступу, критичні конфігураційні зміни          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.4 Приклад Реалізації Redis Кешування

```java
// Патерн кешування (документація для розробки)
@Service
public class AwardCategoryService {
    
    private static final String CACHE_PREFIX = "award_category:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    
    @Cacheable(value = "awardCategories", key = "#categoryId")
    public AwardCategory findById(Long categoryId) {
        return awardCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new NotFoundException("Категорію не знайдено"));
    }
    
    @CacheEvict(value = "awardCategories", key = "#category.categoryId")
    public AwardCategory update(AwardCategory category) {
        return awardCategoryRepository.save(category);
    }
    
    @CacheEvict(value = "awardCategories", allEntries = true)
    public void refreshAllCategories() {
        // Спровоковано адміністративною дією
    }
}

// Конфігурація Redis (application.yml)
spring:
  cache:
    type: redis
    redis:
      time-to-live: 3600000  # 1 година за замовчуванням
      cache-null-values: false
      use-key-prefix: true
      key-prefix: "awards:"
  redis:
    host: ${REDIS_HOST:localhost}
    port: 6379
    timeout: 2000
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 2
```

---

## 6. Налаштування Конфігурації PostgreSQL

### 6.1 Параметри Конфігурації Памʼяті

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   НАЛАШТУВАННЯ ПАМ'ЯТІ POSTGRESQL                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Для сервера з 16 GB RAM, виділеного PostgreSQL:                            │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                     РОЗПОДІЛ ПАМ'ЯТІ                                │    │
│  │                                                                     │    │
│  │   ┌─────────────────────────────────┐                              │    │
│  │   │        shared_buffers           │  4 GB (25% RAM)              │    │
│  │   │   (Кеш даних - найбільший вплив)│                              │    │
│  │   └─────────────────────────────────┘                              │    │
│  │                                                                     │    │
│  │   ┌─────────────────────────────────┐                              │    │
│  │   │      effective_cache_size       │  12 GB (75% RAM)             │    │
│  │   │   (Підказка планувальнику)      │                              │    │
│  │   └─────────────────────────────────┘                              │    │
│  │                                                                     │    │
│  │   ┌─────────────────┐  ┌─────────────────┐                         │    │
│  │   │   work_mem      │  │maintenance_work_│                         │    │
│  │   │  64 MB/операція │  │   mem: 512 MB   │                         │    │
│  │   │(сортування,hash)│  │(VACUUM,CREATE IX)│                        │    │
│  │   └─────────────────┘  └─────────────────┘                         │    │
│  │                                                                     │    │
│  │   ┌─────────────────┐  ┌─────────────────┐                         │    │
│  │   │  wal_buffers    │  │ temp_buffers    │                         │    │
│  │   │     64 MB       │  │     32 MB       │                         │    │
│  │   └─────────────────┘  └─────────────────┘                         │    │
│  │                                                                     │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Рекомендовані Параметри Конфігурації

```ini
# postgresql.conf - Рекомендована конфігурація продакшн
# Для сервера з 16 GB RAM, 4 ядра CPU, SSD сховище

# ===================
# НАЛАШТУВАННЯ ПАМ'ЯТІ
# ===================
shared_buffers = 4GB                    # 25% RAM
effective_cache_size = 12GB             # 75% RAM
work_mem = 64MB                         # Обережно з паралелізмом
maintenance_work_mem = 512MB            # Для VACUUM, CREATE INDEX
wal_buffers = 64MB                      # Достатньо для більшості навантажень
temp_buffers = 32MB                     # Для temp таблиць

# ===================
# НАЛАШТУВАННЯ ПЛАНУВАЛЬНИКА
# ===================
random_page_cost = 1.1                  # SSD (default 4.0 для HDD)
effective_io_concurrency = 200          # SSD (default 1 для HDD)
default_statistics_target = 100         # Точніші статистики

# ===================
# НАЛАШТУВАННЯ ПАРАЛЕЛІЗМУ
# ===================
max_parallel_workers_per_gather = 2     # Паралельні запити
max_parallel_workers = 4                # Загальний паралелізм
max_worker_processes = 8                # Background workers

# ===================
# НАЛАШТУВАННЯ WAL
# ===================
wal_level = replica                     # Для реплікації/PITR
max_wal_size = 4GB                      # Перед checkpoint
min_wal_size = 1GB                      # Зберегти принаймні стільки
checkpoint_completion_target = 0.9      # Розподілити I/O checkpoint

# ===================
# НАЛАШТУВАННЯ ЗʼЄДНАНЬ
# ===================
max_connections = 100                   # Обмежувати (використовувати pooling)
superuser_reserved_connections = 3      # Завжди дозволяти admin

# ===================
# НАЛАШТУВАННЯ ЛОГУВАННЯ
# ===================
log_min_duration_statement = 1000       # Логувати запити > 1 сек
log_checkpoints = on
log_connections = on
log_disconnections = on
log_lock_waits = on
log_temp_files = 0                      # Логувати всі temp файли

# ===================
# НАЛАШТУВАННЯ AUTOVACUUM
# ===================
autovacuum_max_workers = 3
autovacuum_naptime = 60                 # Перевіряти кожну хвилину
autovacuum_vacuum_scale_factor = 0.1    # Vacuum при 10% змін
autovacuum_analyze_scale_factor = 0.05  # Analyze при 5% змін
```

### 6.3 Оптимізації Для Конкретних Середовищ

| **Параметр** | **Розробка** | **Staging** | **Продакшн** |
|--------------|--------------|-------------|--------------|
| shared_buffers | 256MB | 1GB | 4GB |
| work_mem | 16MB | 32MB | 64MB |
| maintenance_work_mem | 128MB | 256MB | 512MB |
| max_connections | 20 | 50 | 100 |
| log_min_duration_statement | 100ms | 500ms | 1000ms |
| random_page_cost | 1.1 | 1.1 | 1.1 |

---

## 7. Моніторинг та Alerting

### 7.1 Ключові Метрики Продуктивності

| **Метрика** | **Джерело** | **Поріг Попередження** | **Поріг Критичний** |
|-------------|-------------|------------------------|---------------------|
| Query duration (p95) | pg_stat_statements | > 100ms | > 500ms |
| Active connections | pg_stat_activity | > 80% max | > 90% max |
| Cache hit ratio | pg_stat_database | < 95% | < 90% |
| Transaction rate | pg_stat_database | Різке падіння | N/A |
| Deadlocks | pg_stat_database | Будь-яке | N/A |
| Replication lag | pg_stat_replication | > 1MB | > 10MB |
| Table bloat | Кастомний запит | > 20% | > 50% |
| Index bloat | Кастомний запит | > 20% | > 50% |

### 7.2 Моніторинг Запитів

```sql
-- Увімкнути pg_stat_statements extension
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Топ 10 повільних запитів
SELECT 
    query,
    calls,
    ROUND(total_exec_time::numeric, 2) AS total_time_ms,
    ROUND(mean_exec_time::numeric, 2) AS avg_time_ms,
    ROUND((100 * total_exec_time / SUM(total_exec_time) OVER ())::numeric, 2) AS pct_total
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 10;

-- Запити з високим часом очікування
SELECT 
    query,
    calls,
    ROUND(blk_read_time::numeric, 2) AS read_time_ms,
    ROUND(blk_write_time::numeric, 2) AS write_time_ms
FROM pg_stat_statements
WHERE blk_read_time > 0 OR blk_write_time > 0
ORDER BY (blk_read_time + blk_write_time) DESC
LIMIT 10;

-- Найбільш часті запити
SELECT 
    query,
    calls,
    ROUND(mean_exec_time::numeric, 2) AS avg_time_ms,
    rows
FROM pg_stat_statements
ORDER BY calls DESC
LIMIT 10;
```

### 7.3 Dashboard Моніторингу

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DASHBOARD ПРОДУКТИВНОСТІ БД                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────┐  ┌─────────────────────────┐                   │
│  │    ЗДОРОВʼЯ ЗʼЄДНАНЬ    │  │  ПРОДУКТИВНІСТЬ ЗАПИТІВ │                   │
│  │                         │  │                         │                    │
│  │  Активні:    45/100 ✓   │  │  p50:    15ms    ✓      │                   │
│  │  Idle:       30         │  │  p95:    85ms    ✓      │                   │
│  │  Waiting:     2         │  │  p99:   250ms    ⚠      │                   │
│  │  Утилізація: 45%   ✓    │  │  Помилки: 0.01%  ✓      │                   │
│  └─────────────────────────┘  └─────────────────────────┘                   │
│                                                                              │
│  ┌─────────────────────────┐  ┌─────────────────────────┐                   │
│  │     ЕФЕКТИВНІСТЬ КЕШУ   │  │   СТАТУС РЕПЛІКАЦІЇ     │                   │
│  │                         │  │                         │                    │
│  │  Buffer Hit:   98.5% ✓  │  │  Lag:        256KB ✓    │                   │
│  │  Index Hit:    99.2% ✓  │  │  Статус:   streaming ✓  │                   │
│  │  Table Hit:    97.8% ✓  │  │  Останній: 2 сек тому ✓ │                   │
│  └─────────────────────────┘  └─────────────────────────┘                   │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                       ТОПОВІ ПОВІЛЬНІ ЗАПИТИ                          │  │
│  │                                                                       │  │
│  │  1. SELECT * FROM awards WHERE... (avg: 450ms, calls: 1.2K)   ⚠      │  │
│  │  2. UPDATE users SET last_login... (avg: 120ms, calls: 5K)    ✓      │  │
│  │  3. INSERT INTO audit_logs... (avg: 25ms, calls: 50K)         ✓      │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                      СТАТУС БЛОКУВАНЬ/LOCKS                           │  │
│  │                                                                       │  │
│  │  Deadlocks (24h):     0    ✓                                         │  │
│  │  Lock Waits (avg):    2ms  ✓                                         │  │
│  │  Long-running txns:   0    ✓                                         │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.4 Prometheus Метрики

```yaml
# postgres_exporter конфігурація (для Prometheus)
# Кастомні запити для бізнес-специфічних метрик

pg_awards_by_status:
  query: |
    SELECT 
      award_status,
      COUNT(*) as count
    FROM awards
    GROUP BY award_status
  metrics:
    - award_status:
        usage: "LABEL"
        description: "Статус нагороди"
    - count:
        usage: "GAUGE"
        description: "Кількість нагород за статусом"

pg_requests_queue_depth:
  query: |
    SELECT 
      COUNT(*) as queue_depth
    FROM award_requests
    WHERE request_status IN ('PENDING', 'UNDER_REVIEW')
  metrics:
    - queue_depth:
        usage: "GAUGE"
        description: "Кількість запитів в черзі"

pg_cache_hit_ratio:
  query: |
    SELECT 
      ROUND(
        100.0 * SUM(heap_blks_hit) / 
        NULLIF(SUM(heap_blks_hit) + SUM(heap_blks_read), 0), 
        2
      ) as ratio
    FROM pg_statio_user_tables
  metrics:
    - ratio:
        usage: "GAUGE"
        description: "Коефіцієнт влучань кешу в відсотках"
```

---

## 8. Чеклист Реалізації

### 8.1 Підготовка Фази Розробки

- [ ] Стратегія індексації задокументована
- [ ] Кандидати на партиціонування ідентифіковані
- [ ] Патерни оптимізації запитів визначені
- [ ] Конфігурація connection pool готова
- [ ] Стратегія кешування спланована
- [ ] Шаблон конфігурації PostgreSQL підготовлений
- [ ] Вимоги до моніторингу визначені

### 8.2 Production Readiness

- [ ] Індекси створені та протестовані
- [ ] Партиціонування налаштоване для великих таблиць
- [ ] Connection pooling налаштований
- [ ] Кешування Redis налаштоване
- [ ] PostgreSQL налаштований для продакшн
- [ ] Моніторинг та alerting активні
- [ ] Базовий рівень продуктивності встановлений
- [ ] Тести навантаження пройдені

---

*Версія Документа: 1.0*  
*Класифікація: Внутрішній*  
*Фаза: 9 - Архітектура Даних та Проєктування Бази Даних*  
*Автор: Стефан Костик*

# Runbook розгортання
## Система моніторингу та відстеження нагород

> **Артефакт Фази 17**: Документація та управління знаннями  
> **Версія документа**: 1.0  
> **Останнє оновлення**: Січень 2026  
> **Автор**: Стефан Костик  

---

## Огляд

Цей runbook містить покрокові процедури для розгортання системи моніторингу нагород у продакшн середовищах.

---

## Контрольний список перед розгортанням

| Крок | Завдання | Перевірено |
|------|----------|------------|
| 1 | Всі перевірки CI/CD пройдені | ☐ |
| 2 | Code review схвалено | ☐ |
| 3 | Покриття тестами ≥85% | ☐ |
| 4 | Сканування безпеки пройдено | ☐ |
| 5 | Міграції БД протестовані в staging | ☐ |
| 6 | План відкату задокументований | ☐ |
| 7 | Алерти моніторингу налаштовані | ☐ |
| 8 | Зацікавлені сторони повідомлені | ☐ |

---

## Процедури розгортання

### 1. Стандартне розгортання (Blue-Green)

**Тривалість**: ~15 хвилин  
**Рівень ризику**: Низький  
**Час відкату**: <1 хвилина

#### Кроки

```bash
# 1. Перевірте поточний статус розгортання
kubectl get deployments -n award-system

# 2. Розгорніть у неактивне (green) середовище
kubectl apply -f infra/k8s/deployment.yml

# 3. Зачекайте готовності pods
kubectl rollout status deployment/award-backend-green -n award-system

# 4. Запустіть smoke тести для green
curl -f https://green.award-system.edu.ua/actuator/health

# 5. Переключіть трафік на green
kubectl patch service award-backend \
  -p '{"spec":{"selector":{"deployment":"green"}}}'

# 6. Перевірте переключення трафіку
curl -f https://api.award-system.edu.ua/actuator/health

# 7. Моніторинг протягом 10 хвилин
# Перевірте Grafana dashboard на аномалії

# 8. Масштабуйте blue вниз (після перевірки)
kubectl scale deployment award-backend-blue --replicas=0
```

#### Відкат (якщо потрібно)

```bash
# Негайний відкат - переключення назад на blue
kubectl patch service award-backend \
  -p '{"spec":{"selector":{"deployment":"blue"}}}'

# Перевірте відкат
curl -f https://api.award-system.edu.ua/actuator/health
```

---

### 2. Розгортання з міграцією бази даних

**Тривалість**: ~30 хвилин  
**Рівень ризику**: Середній  
**Вимагає**: Резервне копіювання БД

#### Перед міграцією

```bash
# 1. Створіть резервну копію БД
pg_dump -h $DB_HOST -U $DB_USER award_system > backup_$(date +%Y%m%d_%H%M%S).sql

# 2. Перевірте резервну копію
pg_restore --list backup_*.sql | head -20

# 3. Тестування міграції в staging
flyway -url=$STAGING_DB_URL migrate
```

#### Кроки міграції

```bash
# 1. Увімкніть режим обслуговування
kubectl set env deployment/award-backend MAINTENANCE_MODE=true

# 2. Масштабуйте до одного екземпляра
kubectl scale deployment/award-backend --replicas=1

# 3. Запустіть Flyway міграцію
flyway -url=$PROD_DB_URL migrate

# 4. Перевірте міграцію
flyway -url=$PROD_DB_URL info

# 5. Розгорніть нову версію застосунку
kubectl apply -f infra/k8s/deployment.yml

# 6. Вимкніть режим обслуговування
kubectl set env deployment/award-backend MAINTENANCE_MODE=false

# 7. Масштабуйте назад
kubectl scale deployment/award-backend --replicas=3
```

---

## Перевірка після розгортання

### Health Checks

```bash
# API Health
curl -s https://api.award-system.edu.ua/actuator/health | jq .

# Підключення до БД
curl -s https://api.award-system.edu.ua/actuator/health/db | jq .

# Підключення до Redis  
curl -s https://api.award-system.edu.ua/actuator/health/redis | jq .
```

### Smoke тести

| Тест | Endpoint | Очікуваний результат |
|------|----------|----------------------|
| Health | GET /actuator/health | `{"status":"UP"}` |
| Auth | POST /auth/login | 200 з валідними credentials |
| Awards | GET /awards | 200 з пагінацією |
| Metrics | GET /actuator/prometheus | Дані метрик |

---

## Контакти

| Роль | Контакт | Ескалація |
|------|---------|-----------|
| Черговий інженер | Slack: #award-system-ops | PagerDuty |
| DBA | dba@university.edu.ua | Телефон |
| Команда безпеки | security@university.edu.ua | Терміновий Slack |

---

## Пов'язані документи

- [Стратегії розгортання](../../deployment/DEPLOYMENT_STRATEGIES_ua.md)
- [Просування середовищ](../../deployment/ENVIRONMENT_PROMOTION_ua.md)
- [Посібник з усунення несправностей](../TROUBLESHOOTING_ua.md)


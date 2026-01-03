# Налаштування середовища розробки
## Система моніторингу та відстеження нагород

> **Результат Фази 12**: Середовище розробки та інструменти  
> **Версія документа**: 1.0  
> **Останнє оновлення**: Січень 2026  
> **Автор**: Стефан Костик

---

## Короткий огляд

Цей документ описує налаштування середовища розробки для Системи моніторингу та відстеження нагород. Він охоплює попередні вимоги, локальні сервіси розробки, налаштування IDE та початок роботи з розробкою.

---

## 1. Попередні вимоги

### Необхідне програмне забезпечення

| **Програма** | **Мінімальна версія** | **Рекомендовано** | **Призначення** |
|--------------|----------------------|-------------------|-----------------|
| **Java JDK** | 21 | 21.0.2+ (Temurin) | Backend-розробка |
| **Maven** | 3.9.0 | 3.9.6+ | Автоматизація збірки |
| **Docker** | 24.0 | Остання | Сервіси розробки |
| **Git** | 2.40 | Остання | Контроль версій |
| **Node.js** | 20.0 | 22.x LTS | Frontend-розробка |
| **npm** | 10.0 | Остання | Управління пакетами |

### Додаткові інструменти

| **Інструмент** | **Призначення** |
|----------------|-----------------|
| IntelliJ IDEA Ultimate | Рекомендоване IDE для Java |
| VS Code | Альтернативне IDE / frontend |
| DBeaver | Управління базою даних |
| Postman | Тестування API |
| Docker Desktop | GUI для управління контейнерами |

---

## 2. Швидкий старт

### Windows (PowerShell)

```powershell
# Перейти до директорії проєкту
cd D:\Projects\AwardMonitoringApplication

# Запустити автоматичне налаштування
.\tools\dev-environment-setup.ps1
```

### Linux/macOS (Bash)

```bash
# Перейти до директорії проєкту
cd ~/projects/award-monitoring-system

# Зробити скрипт виконуваним
chmod +x tools/dev-environment-setup.sh

# Запустити автоматичне налаштування
./tools/dev-environment-setup.sh
```

---

## 3. Ручне налаштування

### 3.1 Java Development Kit

**Варіант A: SDKMAN (Рекомендовано для Linux/macOS)**
```bash
# Встановити SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Встановити Java 21
sdk install java 21.0.2-tem
sdk use java 21.0.2-tem
```

**Варіант B: Chocolatey (Windows)**
```powershell
choco install temurin21
```

**Варіант C: winget (Windows)**
```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

### 3.2 Maven

**SDKMAN:**
```bash
sdk install maven 3.9.6
```

**Chocolatey:**
```powershell
choco install maven
```

### 3.3 Docker-сервіси

Запустіть необхідні сервіси розробки:

```bash
# Створити мережу
docker network create award-network

# PostgreSQL 16
docker run -d \
    --name postgres-dev \
    --network award-network \
    -e POSTGRES_USER=award_dev \
    -e POSTGRES_PASSWORD=dev_password \
    -e POSTGRES_DB=award_monitoring \
    -p 5432:5432 \
    -v postgres-dev-data:/var/lib/postgresql/data \
    postgres:16-alpine

# Redis 7
docker run -d \
    --name redis-dev \
    --network award-network \
    -p 6379:6379 \
    redis:7-alpine --appendonly yes
```

### 3.4 Node.js та Angular

```bash
# Встановити Node.js 22 LTS
# Використовуйте nvm (Node Version Manager) для зручного управління версіями

# Встановити nvm (Linux/macOS)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash

# Встановити Node.js
nvm install 22
nvm use 22

# Встановити Angular CLI глобально
npm install -g @angular/cli@latest
```

---

## 4. Налаштування IDE

### 4.1 IntelliJ IDEA

#### Імпорт проєкту
1. `File > Open` → Виберіть кореневу директорію проєкту
2. Дочекайтеся синхронізації залежностей Maven
3. Позначте `src/main/java` як Sources Root
4. Позначте `src/test/java` як Test Sources Root

#### Стиль коду
1. `File > Settings > Editor > Code Style`
2. Натисніть значок шестерні → `Import Scheme > IntelliJ IDEA code style XML`
3. Виберіть `.idea/codeStyles/Project.xml`
4. Застосуйте до всього проєкту

#### Плагіни
Встановіть рекомендовані плагіни:
- **Lombok** - Обробка анотацій
- **Spring Boot Assistant** - Підтримка Spring
- **Checkstyle-IDEA** - Перевірка стилю коду
- **SonarLint** - Якість коду
- **PlantUML Integration** - Перегляд діаграм

#### Обробка анотацій
1. `File > Settings > Build, Execution, Deployment > Compiler > Annotation Processors`
2. Увімкніть `Enable annotation processing`

#### Конфігурації запуску
Створіть конфігурації запуску для:
- **Spring Boot Application**: Головний клас `ua.kostyk.award.AwardMonitoringApplication`
- **Maven Build**: `clean install -DskipTests`
- **Maven Tests**: `verify`

### 4.2 VS Code

#### Розширення
```json
// .vscode/extensions.json
{
    "recommendations": [
        "vscjava.vscode-java-pack",
        "vmware.vscode-spring-boot",
        "redhat.java",
        "vscjava.vscode-spring-initializr",
        "sonarsource.sonarlint-vscode",
        "shengchen.vscode-checkstyle",
        "angular.ng-template",
        "esbenp.prettier-vscode",
        "editorconfig.editorconfig"
    ]
}
```

---

## 5. Змінні середовища

### Профіль розробки

Створіть файл `.env` (не комітьте в git):

```properties
# База даних
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=award_monitoring
POSTGRES_USER=award_dev
POSTGRES_PASSWORD=dev_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Застосунок
SPRING_PROFILES_ACTIVE=dev
JWT_SECRET=dev-secret-key-change-in-production
```

### Конфігурація Spring Boot

Застосунок буде читати з `src/main/resources/application-dev.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:award_monitoring}
    username: ${POSTGRES_USER:award_dev}
    password: ${POSTGRES_PASSWORD:dev_password}
  
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

---

## 6. Запуск застосунку

### Backend (Spring Boot)

```bash
# З кореня проєкту
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Або з конкретним портом
mvn spring-boot:run -Dspring-boot.run.profiles=dev -Dserver.port=8080
```

### Frontend (Angular)

```bash
# Перейти до директорії frontend
cd frontend

# Встановити залежності
npm install

# Запустити сервер розробки
ng serve

# Доступ: http://localhost:4200
```

### Повний стек (Docker Compose)

```bash
# Запустити всі сервіси
docker-compose up -d

# Переглянути логи
docker-compose logs -f

# Зупинити всі сервіси
docker-compose down
```

---

## 7. Управління базою даних

### Підключення до PostgreSQL

```bash
# Через Docker
docker exec -it postgres-dev psql -U award_dev -d award_monitoring

# Через psql напряму
psql -h localhost -p 5432 -U award_dev -d award_monitoring
```

### Запуск міграцій Flyway

```bash
# Застосувати міграції
mvn flyway:migrate

# Перевірити статус міграцій
mvn flyway:info

# Очистити та перебудувати (УВАГА: видаляє дані)
mvn flyway:clean flyway:migrate
```

### GUI бази даних

Рекомендовані інструменти:
- **DBeaver** (безкоштовний): Підключення з драйвером PostgreSQL
- **pgAdmin**: Веб-адміністрування PostgreSQL
- **IntelliJ Database Tool**: Вбудований в Ultimate-версію

Параметри підключення:
- Хост: `localhost`
- Порт: `5432`
- База даних: `award_monitoring`
- Користувач: `award_dev`
- Пароль: `dev_password`

---

## 8. Типові задачі розробки

### Збірка проєкту

```bash
# Повна збірка з тестами
mvn clean install

# Пропустити тести (швидше)
mvn clean install -DskipTests

# Тільки пакування
mvn package -DskipTests
```

### Запуск тестів

```bash
# Всі тести
mvn test

# Конкретний тестовий клас
mvn test -Dtest=UserServiceTest

# З покриттям
mvn verify

# Тільки інтеграційні тести
mvn verify -Pit-tests
```

### Перевірки якості коду

```bash
# Всі перевірки якості
mvn checkstyle:check pmd:check spotbugs:check

# Генерація звітів
mvn site
# Звіти в: target/site/
```

### Документація API

```bash
# Генерація специфікації OpenAPI
mvn spring-boot:run

# Доступ до Swagger UI
# http://localhost:8080/swagger-ui.html

# OpenAPI JSON
# http://localhost:8080/v3/api-docs
```

---

## 9. Усунення неполадок

### Порт вже використовується

```bash
# Знайти процес, що використовує порт 8080
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Linux/macOS
lsof -i :8080
kill -9 <PID>
```

### Проблеми з Docker

```bash
# Скинути Docker-контейнери
docker-compose down -v
docker system prune -f

# Перезапустити Docker-сервіси
docker restart postgres-dev redis-dev
```

### Проблеми з Maven

```bash
# Очистити кеш Maven
mvn dependency:purge-local-repository

# Примусово оновити залежності
mvn clean install -U
```

### Проблеми з версією Java

```bash
# Перевірити версію Java
java -version

# Переконатися, що JAVA_HOME встановлено
echo $JAVA_HOME  # Linux/macOS
echo %JAVA_HOME%  # Windows
```

---

## 10. Робочий процес розробки

### Стратегія гілок

```
main           ─── Код, готовий до продакшену
  └── develop  ─── Інтеграційна гілка
       ├── feature/AMS-123-user-auth
       ├── feature/AMS-124-award-submission
       └── bugfix/AMS-125-login-error
```

### Формат повідомлень комітів

```
<тип>(<область>): <тема>

<тіло>

<підвал>
```

Типи: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Приклад:
```
feat(auth): реалізувати оновлення JWT-токена

- Додати endpoint оновлення токена
- Реалізувати ротацію токенів
- Додати чорний список токенів у Redis

Closes #123
```

### Чек-лист перед комітом

- [ ] Код компілюється без помилок
- [ ] Всі тести проходять локально
- [ ] Перевірки Checkstyle/PMD проходять
- [ ] Код правильно відформатований
- [ ] Новий код має unit-тести
- [ ] Документація оновлена за потреби

---

## Підсумок

| **Задача** | **Команда** |
|------------|-------------|
| Налаштування середовища | `.\tools\dev-environment-setup.ps1` |
| Запуск сервісів | `docker-compose up -d` |
| Збірка проєкту | `mvn clean install` |
| Запуск backend | `mvn spring-boot:run` |
| Запуск frontend | `cd frontend && ng serve` |
| Запуск тестів | `mvn verify` |
| Перевірка якості | `mvn checkstyle:check pmd:check` |

---

**Версія документа**: 1.0  
**Створено**: Січень 2026  
**Наступний перегляд**: Після першого спринту розробки


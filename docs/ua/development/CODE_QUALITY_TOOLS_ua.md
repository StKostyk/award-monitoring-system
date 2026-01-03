# Інтеграція інструментів якості коду
## Система моніторингу та відстеження нагород

> **Результат Фази 12**: Середовище розробки та інструменти  
> **Версія документа**: 1.0  
> **Останнє оновлення**: Січень 2026  
> **Автор**: Стефан Костик

---

## Короткий огляд

Цей документ описує інструменти контролю якості коду, інтегровані в Систему моніторингу та відстеження нагород. Ці інструменти забезпечують дотримання стандартів якості корпоративного рівня, гарантують узгодженість та автоматизують виявлення помилок, вразливостей безпеки та порушень стилю.

### Цільові показники якості

| **Метрика** | **Ціль** | **Інструмент** |
|-------------|----------|----------------|
| Покриття коду | ≥85% | JaCoCo + SonarQube |
| Дублювання | <3% | SonarQube |
| Коефіцієнт технічного боргу | <5% | SonarQube |
| Критичні вразливості | 0 | OWASP Dependency Check |
| Блокуючі/критичні помилки | 0 | SpotBugs + SonarQube |

---

## 1. Огляд інструментів

| **Інструмент** | **Призначення** | **Конфігурація** | **Команда запуску** |
|----------------|-----------------|------------------|---------------------|
| **Checkstyle** | Контроль стилю коду | `tools/quality/checkstyle.xml` | `mvn checkstyle:check` |
| **PMD** | Статичний аналіз коду | `tools/quality/pmd-ruleset.xml` | `mvn pmd:check` |
| **SpotBugs** | Виявлення патернів помилок | `tools/quality/spotbugs-excludes.xml` | `mvn spotbugs:check` |
| **SonarQube** | Контроль якості та панель | `sonar-project.properties` | `mvn sonar:sonar` |
| **OWASP DC** | Вразливості залежностей | Плагін Maven | `mvn dependency-check:check` |
| **JaCoCo** | Покриття коду | Плагін Maven | `mvn verify` |

---

## 2. Checkstyle

### Призначення
Забезпечує дотримання Google Java Style Guide з корпоративними налаштуваннями. Виявляє порушення стилю до перегляду коду.

### Розташування конфігурації
```
tools/quality/checkstyle.xml
tools/quality/checkstyle-suppressions.xml
```

### Основні правила

| **Категорія** | **Правило** | **Значення** |
|---------------|-------------|--------------|
| Довжина рядка | Максимум символів | 120 |
| Довжина методу | Максимум рядків | 50 |
| Цикломатична складність | Максимум | 10 |
| Довжина класу | Максимум рядків | 500 |
| Кількість параметрів | Максимум | 7 |
| Порядок імпортів | Групи | java → javax → jakarta → org → com → проєкт |

### Конфігурація Maven

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.3.1</version>
    <configuration>
        <configLocation>tools/quality/checkstyle.xml</configLocation>
        <suppressionsLocation>tools/quality/checkstyle-suppressions.xml</suppressionsLocation>
        <consoleOutput>true</consoleOutput>
        <failsOnError>true</failsOnError>
        <violationSeverity>warning</violationSeverity>
    </configuration>
    <executions>
        <execution>
            <phase>validate</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Використання

```bash
# Перевірка порушень
mvn checkstyle:check

# Генерація HTML-звіту
mvn checkstyle:checkstyle
# Звіт: target/site/checkstyle.html
```

---

## 3. PMD

### Призначення
Статичний аналіз для виявлення типових помилок програмування: невикористані змінні, порожні блоки catch, зайве створення об'єктів тощо.

### Розташування конфігурації
```
tools/quality/pmd-ruleset.xml
```

### Увімкнені категорії правил

| **Категорія** | **Фокус** |
|---------------|-----------|
| Найкращі практики | Стандарти кодування, невикористаний код |
| Стиль коду | Іменування, узгодженість форматування |
| Дизайн | Складність, зв'язність, згуртованість |
| Схильність до помилок | Перевірки null, обробка винятків |
| Продуктивність | Неефективні патерни |
| Безпека | Валідація вводу, SQL-ін'єкції |

### Використання

```bash
# Перевірка порушень
mvn pmd:check

# Генерація звіту з детектором копіювання
mvn pmd:pmd pmd:cpd
# Звіти: target/site/pmd.html, target/site/cpd.html
```

---

## 4. SpotBugs

### Призначення
Знаходить патерни помилок у Java-коді за допомогою статичного аналізу. Виявляє розіменування null-вказівників, нескінченні цикли, витоки ресурсів тощо.

### Розташування конфігурації
```
tools/quality/spotbugs-excludes.xml
```

### Виключені патерни

- Патерни ін'єкції Spring Framework
- Патерни сутностей JPA/Hibernate
- Код, згенерований Lombok
- Патерни незмінності DTO/Record
- Тестові класи (пом'якшені правила)

### Використання

```bash
# Перевірка на помилки
mvn spotbugs:check

# Генерація HTML-звіту
mvn spotbugs:spotbugs
# Звіт: target/site/spotbugs.html

# Запуск GUI SpotBugs
mvn spotbugs:gui
```

---

## 5. SonarQube

### Призначення
Централізована панель якості коду з контролем якості. Відстежує технічний борг, покриття коду, вразливості безпеки та проблеми коду з часом.

### Розташування конфігурації
```
sonar-project.properties
```

### Порогові значення контролю якості

| **Метрика** | **Поріг** | **Умова** |
|-------------|-----------|-----------|
| Покриття | ≥85% | На новому коді |
| Дубльовані рядки | <3% | Загалом |
| Рейтинг підтримуваності | A | Обов'язково |
| Рейтинг надійності | A | Обов'язково |
| Рейтинг безпеки | A | Обов'язково |
| Проблемні місця безпеки | 100% переглянуто | Обов'язково |

### Використання

```bash
# Запуск аналізу SonarQube (потрібен запущений сервер SonarQube)
mvn clean verify sonar:sonar \
    -Dsonar.host.url=http://localhost:9000 \
    -Dsonar.login=<ваш-токен>

# З SonarCloud
mvn clean verify sonar:sonar \
    -Dsonar.host.url=https://sonarcloud.io \
    -Dsonar.organization=<ваша-організація> \
    -Dsonar.login=<ваш-токен>
```

### Локальне налаштування SonarQube (Docker)

```bash
# Запуск SonarQube
docker run -d --name sonarqube \
    -p 9000:9000 \
    -v sonarqube_data:/opt/sonarqube/data \
    sonarqube:community

# Доступ: http://localhost:9000
# Облікові дані за замовчуванням: admin/admin
```

---

## 6. OWASP Dependency Check

### Призначення
Ідентифікує відомі вразливості в залежностях проєкту, використовуючи NVD (Національну базу даних вразливостей).

### Використання

```bash
# Перевірка залежностей на вразливості
mvn dependency-check:check

# Звіт: target/dependency-check-report.html
```

---

## 7. JaCoCo (Покриття коду)

### Призначення
Вимірює покриття коду під час виконання тестів. Генерує звіти для інтеграції з SonarQube.

### Виключення з покриття

```xml
<configuration>
    <excludes>
        <exclude>**/config/**</exclude>
        <exclude>**/dto/**</exclude>
        <exclude>**/entity/**</exclude>
        <exclude>**/*Application.class</exclude>
    </excludes>
</configuration>
```

### Використання

```bash
# Запуск тестів з покриттям
mvn clean verify

# Звіт: target/site/jacoco/index.html
```

---

## 8. Інтеграція з CI/CD

### Робочий процес GitHub Actions

```yaml
name: Якість коду

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  quality:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Налаштування JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Кешування пакетів Maven
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
    
    - name: Запуск Checkstyle
      run: mvn checkstyle:check
    
    - name: Запуск PMD
      run: mvn pmd:check
    
    - name: Запуск SpotBugs
      run: mvn spotbugs:check
    
    - name: Запуск тестів з покриттям
      run: mvn verify
    
    - name: Перевірка залежностей OWASP
      run: mvn dependency-check:check
    
    - name: Аналіз SonarQube
      env:
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      run: |
        mvn sonar:sonar \
          -Dsonar.host.url=https://sonarcloud.io \
          -Dsonar.organization=${{ secrets.SONAR_ORG }}
```

---

## 9. Інтеграція з IDE

### IntelliJ IDEA

1. **Плагін Checkstyle**
   - Встановлення: `Settings > Plugins > Checkstyle-IDEA`
   - Налаштування: `Settings > Tools > Checkstyle`
   - Додати файл конфігурації: `tools/quality/checkstyle.xml`

2. **Плагін SonarLint**
   - Встановлення: `Settings > Plugins > SonarLint`
   - Прив'язати до SonarQube/SonarCloud для узгоджених правил

3. **Стиль коду**
   - Імпорт: `Settings > Editor > Code Style > Import Scheme`
   - Вибрати: `.idea/codeStyles/Project.xml`

### VS Code

1. **Checkstyle for Java**
   - ID розширення: `shengchen.vscode-checkstyle`
   - Налаштувати шлях до `checkstyle.xml`

2. **SonarLint**
   - ID розширення: `SonarSource.sonarlint-vscode`

---

## 10. Швидка довідка

### Запуск усіх перевірок якості

```bash
# Повна перевірка якості (всі інструменти)
mvn clean verify checkstyle:check pmd:check spotbugs:check dependency-check:check

# Швидка перевірка
mvn checkstyle:check pmd:check
```

### Типові проблеми та рішення

| **Проблема** | **Інструмент** | **Рішення** |
|--------------|----------------|-------------|
| Занадто довгий рядок | Checkstyle | Розбити рядок на 120 символів |
| Невикористаний імпорт | PMD/Checkstyle | Видалити або використати організацію імпортів IDE |
| Ризик null-вказівника | SpotBugs | Додати перевірку null або використати Optional |
| Відсутній Javadoc | Checkstyle | Додати Javadoc до публічних методів |
| Висока складність | PMD | Виділити методи, спростити логіку |

---

## Підсумок

| **Інструмент** | **Коли** | **Що** |
|----------------|----------|--------|
| Checkstyle | Кожен коміт | Порушення стилю |
| PMD | Кожен коміт | Проблеми якості коду |
| SpotBugs | Кожна збірка | Патерни помилок |
| JaCoCo | Кожен запуск тестів | Метрики покриття |
| OWASP DC | Щотижня/Реліз | Вразливості безпеки |
| SonarQube | CI/CD pipeline | Загальна панель якості |

**Філософія**: Зміщення вліво - виявляти проблеми на ранніх етапах розробки, а не у продакшені.

---

**Версія документа**: 1.0  
**Створено**: Січень 2026  
**Наступний перегляд**: Після першого спринту розробки


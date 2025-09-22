# ADR-019: Вибір платформи CI/CD

**Статус**: Прийнято  
**Дата**: 2025-08-21  
**Автор**: Стефан Костик  
**Зацікавлені сторони**: Архітектор проекту, DevOps команда, Команда розробки

---

## Контекст

Система моніторингу та відстеження нагород потребує надійної платформи CI/CD для автоматизації pipeline збірки, тестування та розгортання. Рішення повинно добре інтегруватися з обраним технологічним стеком та забезпечувати корпоративні функції, залишаючись при цьому економічно ефективним для проекту соло розробника.

### Передумови
- Потреба в автоматизованому pipeline збірки, тестування та розгортання
- Інтеграція з Git репозиторієм та контролем версій
- Підтримка збірки Docker контейнерів та розгортання Kubernetes
- Вимоги щодо code quality gates та сканування безпеки
- Економічно ефективне рішення, підходяще для open-source портфоліо проекту

### Припущення
- Автоматизація CI/CD є важливою для сучасної розробки програмного забезпечення
- Інтеграція з GitHub репозиторієм забезпечує найкращий досвід розробника
- Хмарний CI/CD зменшує операційні накладні витрати
- Підхід pipeline-as-code дозволяє контроль версій процесів розгортання

---

## Рішення

**GitHub Actions** було обрано як платформа CI/CD для Системи моніторингу та відстеження нагород.

### Обраний підхід
- **Платформа CI/CD**: GitHub Actions з автоматизацією workflow
- **Визначення pipeline**: YAML-базовані workflow, збережені в репозиторії
- **Середовище збірки**: GitHub-hosted runners для збірки та тестування
- **Розгортання**: Автоматизоване розгортання в Kubernetes кластери

### Обґрунтування
- **Інтеграція з репозиторієм**: Безшовна інтеграція з GitHub хостингом репозиторіїв
- **Економічна ефективність**: Безкоштовно для публічних репозиторіїв, щедрий безкоштовний рівень для приватних
- **Легке налаштування**: Відсутність додаткової інфраструктури або конфігурації
- **Екосистема**: Багатий маркетплейс готових дій для поширених завдань
- **Нативні GitHub функції**: Інтеграція з issues, pull requests та releases

---

## Наслідки

### Позитивні наслідки
- **Нульові накладні витрати налаштування**: Відсутність додаткової інфраструктури для управління
- **Інтегрований досвід розробника**: Єдина платформа для коду та CI/CD
- **Економічна ефективність**: Безкоштовно для open-source проектів
- **Багата екосистема**: Великий маркетплейс дій спільноти
- **Контроль версій**: Визначення pipeline зберігаються з кодом

### Негативні наслідки
- **Залежність від постачальника**: Тісна прив'язка до платформи GitHub
- **Обмежена кастомізація**: Менша гнучкість порівняно з self-hosted рішеннями
- **Обмеження ресурсів**: Обмеження часу збірки та ресурсів виконання

### Нейтральні наслідки
- **Залежність від хмари**: Потребує інтернет-з'єднання для збірок
- **Екосистема GitHub**: Найкраще підходить для проектів, що вже використовують GitHub

---

## Розглянуті альтернативи

### Альтернатива 1: GitLab CI
- **Переваги**: Інтегрована платформа, потужні функції, опція self-hosted
- **Недоліки**: Додаткова вартість, потребує міграції GitLab, крива навчання
- **Причина відхилення**: Вже зобов'язані до екосистеми GitHub

### Альтернатива 2: Jenkins
- **Переваги**: Високо кастомізований, екосистема з великою кількістю плагінів, self-hosted контроль
- **Недоліки**: Накладні витрати інфраструктури, складне налаштування, тягар обслуговування
- **Причина відхилення**: Занадто багато операційних накладних витрат для соло розробника

### Альтернатива 3: Azure DevOps
- **Переваги**: Корпоративні функції, інтеграція екосистеми Microsoft
- **Недоліки**: Додаткова вартість, окрема платформа від GitHub, складність
- **Причина відхилення**: Перевага інтегрованого рішення GitHub

---

## Примітки з реалізації

### Технічні вимоги
- **GitHub репозиторій**: Публічний або приватний репозиторій з увімкненими Actions
- **Workflow файли**: YAML workflows в директорії `.github/workflows/`
- **Управління секретами**: GitHub repository secrets для чутливих даних
- **Container Registry**: GitHub Container Registry або інтеграція Docker Hub

### Основний Build Workflow
```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_PASSWORD: test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
        cache: maven
    
    - name: Run tests
      run: ./mvnw clean test
    
    - name: Generate test report
      uses: dorny/test-reporter@v1
      if: success() || failure()
      with:
        name: Maven Tests
        path: target/surefire-reports/*.xml
        reporter: java-junit

  security-scan:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    
    - name: Run Trivy vulnerability scanner
      uses: aquasecurity/trivy-action@master
      with:
        scan-type: 'fs'
        format: 'sarif'
        output: 'trivy-results.sarif'
    
    - name: Upload Trivy scan results
      uses: github/codeql-action/upload-sarif@v2
      with:
        sarif_file: 'trivy-results.sarif'

  build-and-deploy:
    needs: [test, security-scan]
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
        cache: maven
    
    - name: Build application
      run: ./mvnw clean package -DskipTests
    
    - name: Build Docker image
      run: |
        docker build -t ${{ github.repository }}:${{ github.sha }} .
        docker tag ${{ github.repository }}:${{ github.sha }} ${{ github.repository }}:latest
    
    - name: Login to Container Registry
      uses: docker/login-action@v3
      with:
        registry: ghcr.io
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}
    
    - name: Push Docker image
      run: |
        docker push ghcr.io/${{ github.repository }}:${{ github.sha }}
        docker push ghcr.io/${{ github.repository }}:latest
    
    - name: Deploy to Kubernetes
      uses: azure/k8s-deploy@v1
      with:
        manifests: |
          k8s/deployment.yaml
          k8s/service.yaml
        images: |
          ghcr.io/${{ github.repository }}:${{ github.sha }}
        kubeconfig: ${{ secrets.KUBE_CONFIG }}
```

### Конфігурація Quality Gates
```yaml
  quality-gate:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
      with:
        fetch-depth: 0
    
    - name: SonarQube Scan
      uses: sonarqube-quality-gate-action@master
      env:
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      with:
        scanMetadataReportFile: target/sonar/report-task.txt
    
    - name: Check test coverage
      run: |
        COVERAGE=$(./mvnw jacoco:report | grep -o "Total.*[0-9]*%" | grep -o "[0-9]*")
        if [ "$COVERAGE" -lt 85 ]; then
          echo "Test coverage $COVERAGE% is below threshold 85%"
          exit 1
        fi
```

---

## Відповідність та якість

### Наслідки для безпеки
- **Управління секретами**: GitHub secrets для чутливої конфігурації
- **Сканування контейнерів**: Автоматизоване сканування вразливостей Docker образів
- **SAST**: Інтеграція static analysis security testing
- **Сканування залежностей**: Автоматизовані перевірки вразливостей залежностей

### Вплив на продуктивність
- **Час збірки**: Паралельне виконання job для швидших збірок
- **Кешування**: Maven та Docker layer кешування для ефективності
- **Оптимізація ресурсів**: Ефективне використання GitHub runner ресурсів

### Супроводжуваність
- **Pipeline як код**: Всі workflows версіонуються з кодом додатка
- **Переважувальні workflows**: Спільні workflows для загальних паттернів
- **Документація**: Документація workflow та інтеграція README
- **Моніторинг**: Build status badges та сповіщення

---

## Метрики успіху

### Ключові показники ефективності
- **Успішність збірки**: > 95% успішних збірок
- **Середній час збірки**: < 10 хвилин для повного pipeline
- **Частота розгортання**: Щоденні розгортання в staging

### Моніторинг та сповіщення
- **Сповіщення збірки**: Slack/email сповіщення про невдалі збірки
- **Quality Gates**: Автоматизовані перевірки якості з сповіщеннями про невдачі
- **Статус розгортання**: Відстеження статусу розгортання в реальному часі

---

## Пов'язані документи

- **Технічний стек**: [Вибір технологічного стеку](../TECH_STACK_ua.md)
- **Інші ADR**: [ADR-017 Containerization](./ADR-017-Containerization-ua.md), [ADR-018 Orchestration](./ADR-018-Orchestration-ua.md)
- **Зовнішні ресурси**: [Документація GitHub Actions](https://docs.github.com/en/actions)

---

## Історія версій

| **Дата** | **Автор** | **Зміни** | **Причина** |
|----------|------------|-------------|------------|
| 2025-08-21 | Стефан Костик | Початкова версія | Створення документа |

---

**Статус документа**: Затверджено  
**Дата наступного огляду**: 2026-02-21  
**Категорія ADR**: Технологія 
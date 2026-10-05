# Впровадження Privacy by Design
## Система Моніторингу та Відстеження Нагород

> **Етап 10 Результат**: Архітектура Безпеки та Проектування Конфіденційності  
> **Версія Документа**: 1.0  
> **Останнє Оновлення**: Грудень 2025  
> **Автор**: Стефан Костик  
> **Фреймворк**: Privacy by Design (PbD) - 7 Основоположних Принципів  
> **Класифікація**: Внутрішній

---

## Резюме

Цей документ визначає стратегію впровадження Privacy by Design (PbD) для Системи Моніторингу та Відстеження Нагород. Дотримуючись семи основоположних принципів, встановлених Енн Кавукян, архітектура забезпечує вбудовування конфіденційності в кожен аспект проектування, розробки та експлуатації системи при збереженні відповідності GDPR.

### Цілі Впровадження PbD

| **Ціль** | **Опис** | **Метрика Успіху** |
|----------|----------|-------------------|
| **Вбудована Конфіденційність** | Контролі конфіденційності вбудовані в ядро архітектури | 100% полів PII захищено |
| **Контроль Користувача** | Користувачі мають повний контроль над своїми даними | Всі права GDPR впроваджено |
| **Мінімізація Даних** | Збір лише необхідних даних | Мінімальний слід даних на мету |
| **Прозорість** | Зрозумілі практики конфіденційності | Зрозумілі повідомлення про конфіденційність |
| **Безпека** | Сильний захист даних | Нуль порушень конфіденційності |

---

## 1. Сім Основоположних Принципів

### 1.1 Огляд Принципів

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  PRIVACY BY DESIGN - 7 ПРИНЦИПІВ                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  1. ПРОАКТИВНИЙ, НЕ РЕАКТИВНИЙ                                      │   │
│  │     Передбачення та запобігання проблемам конфіденційності           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  2. КОНФІДЕНЦІЙНІСТЬ ЗА ЗАМОВЧУВАННЯМ                                │   │
│  │     Максимальний захист без дій користувача                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  3. КОНФІДЕНЦІЙНІСТЬ ВБУДОВАНА В ДИЗАЙН                              │   │
│  │     Конфіденційність невід'ємна від архітектури, не додаток          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  4. ПОВНА ФУНКЦІОНАЛЬНІСТЬ (ПОЗИТИВНА СУМА)                          │   │
│  │     Конфіденційність І функціональність, не АБО                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  5. НАСКРІЗНА БЕЗПЕКА                                                │   │
│  │     Повний захист життєвого циклу від збору до видалення             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  6. ВИДИМІСТЬ ТА ПРОЗОРІСТЬ                                          │   │
│  │     Відкриті практики, підлягають перевірці                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  7. ПОВАГА ДО КОНФІДЕНЦІЙНОСТІ КОРИСТУВАЧА                           │   │
│  │     Дизайн, орієнтований на користувача, що надає повноваження       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Впровадження Мінімізації Даних

### 2.1 Принципи Збору Даних

| **Принцип** | **Впровадження** | **Технічний Контроль** |
|-------------|------------------|------------------------|
| **Збір Мінімуму** | Лише дані, необхідні для заявленої мети | Валідація схеми, лише обов'язкові поля |
| **Прив'язка до Мети** | Дані прив'язані до конкретної мети обробки | Поле мети в моделі даних |
| **Обмеження в Часі** | Визначені терміни зберігання для кожного типу даних | Автоматизовані завдання видалення |
| **Обмеження Доступу** | Дані доступні лише авторизованим ролям | RBAC + дозволи на рівні полів |

### 2.2 Матриця Збору Даних

| **Поле Даних** | **Мета** | **Правова Основа** | **Термін Зберіг.** | **Стратегія Мінімізації** |
|----------------|----------|--------------------|--------------------|---------------------------|
| `email_address` | Автентифікація, сповіщення | Договір | Час життя акаунту + 30 днів | Збір для однієї мети |
| `first_name`, `last_name` | Атрибуція нагород | Законний інтерес | 7 років після звільнення | Необхідно для прозорості |
| `phone_number` | Опціональний MFA | Згода | До відкликання згоди | Не обов'язково, вибір користувача |
| `award_title` | Основна функціональність | Договір | Безстроково (публічний запис) | Обов'язкове поле |
| `award_documents` | Верифікація | Згода | Визначається користувачем | Користувач контролює термін |

### 2.3 Технічне Впровадження

```java
// Сервіс Мінімізації Даних
@Service
public class DataMinimizationService {
    
    /**
     * Валідує, що збираються лише необхідні дані для даної мети
     */
    public <T> T minimizeForPurpose(T data, ProcessingPurpose purpose) {
        DataMinimizationRules rules = rulesRepository.getRulesForPurpose(purpose);
        
        // Отримання дозволених полів для цієї мети
        Set<String> allowedFields = rules.getAllowedFields();
        
        // Обнулення неосновних полів
        for (Field field : data.getClass().getDeclaredFields()) {
            if (!allowedFields.contains(field.getName())) {
                field.setAccessible(true);
                try {
                    if (!field.getType().isPrimitive()) {
                        field.set(data, null);
                    }
                } catch (IllegalAccessException e) {
                    log.warn("Не вдалося мінімізувати поле: {}", field.getName());
                }
            }
        }
        
        auditService.logDataMinimization(data.getClass().getSimpleName(), purpose);
        return data;
    }
    
    /**
     * Валідує, що вхідні дані містять лише дозволені поля
     */
    public void validateInputMinimization(Object input, ProcessingPurpose purpose) {
        DataMinimizationRules rules = rulesRepository.getRulesForPurpose(purpose);
        
        for (Field field : input.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(input);
                if (value != null && !rules.getAllowedFields().contains(field.getName())) {
                    throw new ExcessiveDataException(
                        "Поле '" + field.getName() + "' не потрібне для мети: " + purpose
                    );
                }
            } catch (IllegalAccessException e) {
                // Поле недоступне, пропускаємо
            }
        }
    }
}

// DTO для конкретних цілей забезпечують мінімізацію на етапі компіляції
public record AwardSubmissionRequest(
    @NotBlank String title,
    @NotNull LocalDate awardDate,
    @NotBlank String awardingOrganization,
    @NotNull Long categoryId
    // Примітка: Немає email, телефону, адреси - не потрібні для подання
) {}

public record UserRegistrationRequest(
    @Email @NotBlank String email,
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotBlank String password
    // Примітка: Телефон опціональний, збирається окремо за згодою
) {}
```

---

## 3. Забезпечення Обмеження Мети

### 3.1 Визначення Мети

| **ID Мети** | **Назва Мети** | **Опис** | **Категорії Даних** | **Термін Зберіг.** |
|-------------|----------------|----------|---------------------|-------------------|
| P001 | Управління Нагородами | Створення, відстеження та відображення нагород | Профіль користувача, Дані нагороди | 7 років |
| P002 | Автентифікація | Вхід та управління сесією | Облікові дані, Дані сесії | Сесія + 30 днів |
| P003 | Сповіщення | Email/SMS сповіщення про нагороди | Контактна інформація, Налаштування | Час життя акаунту |
| P004 | Аналітика | Використання системи та продуктивність | Анонімізовані дані використання | 3 роки |
| P005 | Відповідність | Аудит сліди та відповідність GDPR | Аудит логи, Записи згоди | 7 років |

### 3.2 Архітектура Прив'язки до Мети

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ЗАБЕЗПЕЧЕННЯ ОБМЕЖЕННЯ МЕТИ                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Збір Даних                                                                 │
│       │                                                                     │
│       ▼                                                                     │
│  ┌─────────────────┐                                                        │
│  │ Присвоєння      │  Кожен елемент даних тегується метою збору             │
│  │ Тегу Мети       │                                                        │
│  └────────┬────────┘                                                        │
│           │                                                                 │
│           ▼                                                                 │
│  ┌─────────────────┐                                                        │
│  │ Валідація       │  Перевірка відповідності мети дозволеним використ.     │
│  │ Мети            │                                                        │
│  └────────┬────────┘                                                        │
│           │                                                                 │
│       ┌───┴───┐                                                             │
│       │       │                                                             │
│       ▼       ▼                                                             │
│  ┌────────┐ ┌────────┐                                                      │
│  │Дозволити│ │Відхилити│                                                    │
│  │ Доступ │ │ + Лог  │                                                      │
│  └────────┘ └────────┘                                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 Технічне Впровадження

```java
// Аспект Обмеження Мети
@Aspect
@Component
public class PurposeLimitationAspect {
    
    @Around("@annotation(purposeRequired)")
    public Object enforcePurpose(ProceedingJoinPoint joinPoint, 
                                  PurposeRequired purposeRequired) throws Throwable {
        ProcessingPurpose requiredPurpose = purposeRequired.value();
        ProcessingContext context = ProcessingContext.current();
        
        // Валідація, що поточний контекст обробки має валідну мету
        if (context.getPurpose() == null) {
            throw new PurposeNotSpecifiedException(
                "Мета обробки повинна бути вказана для цієї операції"
            );
        }
        
        // Перевірка сумісності цілей
        if (!isPurposeCompatible(context.getPurpose(), requiredPurpose)) {
            auditService.logPurposeMismatch(
                context.getPurpose(), 
                requiredPurpose,
                joinPoint.getSignature().toShortString()
            );
            throw new PurposeMismatchException(
                "Дані зібрані для " + context.getPurpose() + 
                " не можуть використовуватися для " + requiredPurpose
            );
        }
        
        return joinPoint.proceed();
    }
    
    private boolean isPurposeCompatible(ProcessingPurpose original, 
                                        ProcessingPurpose requested) {
        // Перевірка чи запитана мета є тією самою або сумісною підметою
        return original == requested || 
               original.getCompatiblePurposes().contains(requested);
    }
}

// Приклад використання
@Service
public class NotificationService {
    
    @PurposeRequired(ProcessingPurpose.NOTIFICATION)
    public void sendAwardNotification(Long userId, AwardNotification notification) {
        // Цей метод може отримати доступ лише до даних зібраних для NOTIFICATION мети
        User user = userRepository.findByIdForPurpose(userId, ProcessingPurpose.NOTIFICATION);
        
        if (user.getNotificationConsent().isGranted()) {
            emailService.send(user.getEmail(), notification);
        }
    }
}
```

---

## 4. Система Управління Згодою

### 4.1 Архітектура Згоди

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      СИСТЕМА УПРАВЛІННЯ ЗГОДОЮ                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      ЗБІР ЗГОДИ                                      │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │   │
│  │  │ Гранулярні  │  │ Зрозуміла   │  │ Вільно      │                 │   │
│  │  │ Опції       │  │ Мова        │  │ Надана      │                 │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      ЗБЕРІГАННЯ ЗГОДИ                                │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │   │
│  │  │ Версіоновані│  │ З Часовими  │  │ Аудитовані  │                 │   │
│  │  │ Записи      │  │ Мітками     │  │ Сліди       │                 │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      ЗАБЕЗПЕЧЕННЯ ЗГОДИ                              │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │   │
│  │  │ Валідація   │  │ Шлюзи       │  │ Контроль    │                 │   │
│  │  │ в Реал. Часі│  │ Обробки     │  │ Доступу     │                 │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      ВІДКЛИКАННЯ ЗГОДИ                               │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │   │
│  │  │ Простий     │  │ Негайний    │  │ Каскадні    │                 │   │
│  │  │ Процес      │  │ Ефект       │  │ Оновлення   │                 │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Типи Згоди

| **Тип Згоди** | **Мета** | **Обов'язкова** | **Гранулярність** | **Вплив Відкликання** |
|---------------|----------|-----------------|-------------------|----------------------|
| `DATA_PROCESSING` | Базове використання системи | Так | Бінарна | Видалення акаунту |
| `PUBLIC_VISIBILITY` | Публічне відображення нагород | Так | Бінарна | Нагороди приховані |
| `EMAIL_NOTIFICATIONS` | Email сповіщення | Ні | За типом | Не надсилаються emails |
| `SMS_NOTIFICATIONS` | SMS сповіщення | Ні | Бінарна | Не надсилаються SMS |
| `DOCUMENT_AI_PROCESSING` | AI витяг метаданих | Ні | За документом | Лише ручне введення |
| `ANALYTICS` | Аналітика використання | Ні | Бінарна | Виключено з аналітики |

### 4.3 Технічне Впровадження

```java
// Сервіс Управління Згодою
@Service
@Transactional
public class ConsentManagementService {
    
    private final ConsentRepository consentRepository;
    private final ConsentEventPublisher eventPublisher;
    private final AuditService auditService;
    
    /**
     * Надання згоди з повним аудит слідом
     */
    public ConsentRecord grantConsent(ConsentRequest request) {
        validateConsentRequest(request);
        
        ConsentRecord consent = ConsentRecord.builder()
            .userId(request.getUserId())
            .consentType(request.getConsentType())
            .consentVersion(getCurrentPolicyVersion(request.getConsentType()))
            .isGranted(true)
            .grantedAt(Instant.now())
            .ipAddress(request.getIpAddress())
            .userAgent(request.getUserAgent())
            .build();
        
        ConsentRecord saved = consentRepository.save(consent);
        
        // Публікація події для downstream систем
        eventPublisher.publish(new ConsentGrantedEvent(saved));
        
        // Аудит лог
        auditService.logConsentChange(
            AuditAction.CONSENT_GRANTED,
            saved.getUserId(),
            saved.getConsentType(),
            request.getIpAddress()
        );
        
        return saved;
    }
    
    /**
     * Відкликання згоди з каскадними ефектами
     */
    public ConsentRecord withdrawConsent(Long userId, ConsentType consentType, 
                                          WithdrawalRequest request) {
        ConsentRecord current = consentRepository
            .findCurrentConsent(userId, consentType)
            .orElseThrow(() -> new ConsentNotFoundException("Активну згоду не знайдено"));
        
        // Оновлення запису згоди
        current.setIsGranted(false);
        current.setWithdrawnAt(Instant.now());
        ConsentRecord saved = consentRepository.save(current);
        
        // Публікація події для каскадних ефектів
        eventPublisher.publish(new ConsentWithdrawnEvent(saved));
        
        // Запуск негайної обробки даних на основі типу згоди
        handleConsentWithdrawal(userId, consentType);
        
        // Аудит лог
        auditService.logConsentChange(
            AuditAction.CONSENT_WITHDRAWN,
            userId,
            consentType,
            request.getIpAddress()
        );
        
        return saved;
    }
    
    /**
     * Перевірка чи користувач має валідну згоду для операції
     */
    public boolean hasValidConsent(Long userId, ConsentType consentType) {
        return consentRepository.findCurrentConsent(userId, consentType)
            .map(consent -> consent.getIsGranted() && 
                           consent.getConsentVersion().equals(getCurrentPolicyVersion(consentType)))
            .orElse(false);
    }
    
    /**
     * Обробка каскадних ефектів відкликання згоди
     */
    private void handleConsentWithdrawal(Long userId, ConsentType consentType) {
        switch (consentType) {
            case PUBLIC_VISIBILITY -> awardService.hideUserAwards(userId);
            case EMAIL_NOTIFICATIONS -> notificationService.disableEmail(userId);
            case SMS_NOTIFICATIONS -> notificationService.disableSms(userId);
            case DOCUMENT_AI_PROCESSING -> documentService.disableAiProcessing(userId);
            case ANALYTICS -> analyticsService.excludeUser(userId);
            case DATA_PROCESSING -> initiateAccountDeletion(userId);
        }
    }
}

// Анотація Забезпечення Згоди
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresConsent {
    ConsentType value();
    boolean failSilently() default false;
}

// Аспект Забезпечення Згоди
@Aspect
@Component
public class ConsentEnforcementAspect {
    
    @Around("@annotation(requiresConsent)")
    public Object enforceConsent(ProceedingJoinPoint joinPoint, 
                                  RequiresConsent requiresConsent) throws Throwable {
        Long userId = extractUserId(joinPoint);
        ConsentType requiredConsent = requiresConsent.value();
        
        if (!consentService.hasValidConsent(userId, requiredConsent)) {
            if (requiresConsent.failSilently()) {
                return null; // Тихе пропускання
            }
            throw new ConsentRequiredException(
                "Потрібна валідна згода для " + requiredConsent
            );
        }
        
        return joinPoint.proceed();
    }
}
```

---

## 5. Впровадження Права на Видалення

### 5.1 Потік Процесу Видалення

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ПРАВО НА ВИДАЛЕННЯ (СТАТТЯ 17)                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Запит Користувача                                                          │
│       │                                                                     │
│       ▼                                                                     │
│  ┌─────────────────┐                                                        │
│  │ Верифікація     │  Перевірка ідентичності перед обробкою                 │
│  │ Ідентичності    │                                                        │
│  └────────┬────────┘                                                        │
│           │                                                                 │
│           ▼                                                                 │
│  ┌─────────────────┐                                                        │
│  │ Оцінка          │  Перевірка вимог обов'язкового зберігання              │
│  │ Придатності     │                                                        │
│  └────────┬────────┘                                                        │
│           │                                                                 │
│       ┌───┴───┐                                                             │
│       │       │                                                             │
│       ▼       ▼                                                             │
│  ┌────────┐ ┌────────┐                                                      │
│  │ Повне  │ │Часткова│  Повне видалення або анонімізація                    │
│  │ Видал. │ │ Аноні. │                                                      │
│  └───┬────┘ └───┬────┘                                                      │
│      │          │                                                           │
│      └────┬─────┘                                                           │
│           │                                                                 │
│           ▼                                                                 │
│  ┌─────────────────┐                                                        │
│  │ Каскадне        │  Видалення з усіх систем та резервних копій            │
│  │ Видалення       │                                                        │
│  └────────┬────────┘                                                        │
│           │                                                                 │
│           ▼                                                                 │
│  ┌─────────────────┐                                                        │
│  │ Підтвердження   │  Сповіщення користувача та логування завершення        │
│  │ та Аудит        │                                                        │
│  └─────────────────┘                                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Категорії Видалення Даних

| **Категорія** | **Стратегія Видалення** | **Таймлайн** | **Винятки** |
|---------------|-------------------------|--------------|-------------|
| **Профіль Користувача** | Повне видалення | 30 днів | Юридичні утримання |
| **Нагороди** | Анонімізація | 30 днів | Збереження публічного запису |
| **Документи** | Повне видалення | 30 днів | Немає |
| **Аудит Логи** | Анонімізація | 30 днів | 7-річне зберігання для відповідності |
| **Резервні Копії** | Позначення для виключення | Наступний цикл резервування | Вимоги відповідності |
| **Аналітика** | Анонімізація | Негайно | Вже анонімізовано |

### 5.3 Технічне Впровадження

```java
// Сервіс Видалення Даних
@Service
@Transactional
public class DataErasureService {
    
    /**
     * Обробка запиту на право видалення
     */
    public ErasureResult processErasureRequest(ErasureRequest request) {
        // Крок 1: Верифікація ідентичності
        verifyUserIdentity(request.getUserId(), request.getVerificationToken());
        
        // Крок 2: Перевірка придатності
        ErasureEligibility eligibility = assessErasureEligibility(request.getUserId());
        
        if (!eligibility.isEligible()) {
            return ErasureResult.denied(eligibility.getReason());
        }
        
        // Крок 3: Виконання видалення на основі типу придатності
        ErasureResult result = switch (eligibility.getType()) {
            case FULL_DELETION -> executeFullDeletion(request.getUserId());
            case PARTIAL_ANONYMIZATION -> executePartialAnonymization(request.getUserId());
            case DEFERRED -> scheduleDeferredErasure(request.getUserId(), eligibility.getDeferralDate());
        };
        
        // Крок 4: Аудит та сповіщення
        auditService.logErasureRequest(request, result);
        notificationService.sendErasureConfirmation(request.getUserId(), result);
        
        return result;
    }
    
    /**
     * Повне видалення всіх даних користувача
     */
    private ErasureResult executeFullDeletion(Long userId) {
        List<DeletionTask> tasks = new ArrayList<>();
        
        // Видалення документів з об'єктного сховища
        tasks.add(documentStorageService.deleteAllUserDocuments(userId));
        
        // Видалення нагород (або анонімізація якщо публічний запис)
        tasks.add(awardService.deleteOrAnonymizeUserAwards(userId));
        
        // Видалення сповіщень
        tasks.add(notificationService.deleteUserNotifications(userId));
        
        // Видалення записів згоди (зберігаємо анонімізовано для доказу)
        tasks.add(consentService.anonymizeUserConsents(userId));
        
        // Анонімізація аудит логів (потрібно для відповідності)
        tasks.add(auditService.anonymizeUserLogs(userId));
        
        // Видалення профілю користувача (останній крок)
        tasks.add(userService.deleteUser(userId));
        
        // Запит на виключення з резервних копій
        backupService.markUserForExclusion(userId);
        
        // Агрегація результатів
        return ErasureResult.completed(tasks);
    }
    
    /**
     * Часткова анонімізація для записів з обов'язковим зберіганням
     */
    private ErasureResult executePartialAnonymization(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        // Анонімізація PII зі збереженням структури запису
        AnonymizedUser anonymized = AnonymizedUser.builder()
            .id(user.getId())
            .email(generateAnonymousEmail())
            .firstName("[ВИДАЛЕНО]")
            .lastName("[ВИДАЛЕНО]")
            .organizationId(user.getOrganizationId()) // Зберігаємо для статистики
            .anonymizedAt(Instant.now())
            .build();
        
        // Заміна запису користувача
        userRepository.save(anonymized);
        
        // Оновлення пов'язаних записів для вказівки на анонімізованого користувача
        awardService.anonymizeUserAttribution(userId);
        
        return ErasureResult.anonymized();
    }
}

// Утиліти Анонімізації
@Service
public class AnonymizationService {
    
    private static final String ANONYMIZED_MARKER = "[АНОНІМІЗОВАНО]";
    
    /**
     * Генерація стабільного псевдоніму для консистентності
     */
    public String generatePseudonym(Long userId) {
        // Стабільний хеш для збереження референційної цілісності
        return "USER_" + DigestUtils.sha256Hex(userId + SECRET_SALT).substring(0, 12);
    }
    
    /**
     * Анонімізація email адреси
     */
    public String anonymizeEmail(String email) {
        return "anonymized_" + UUID.randomUUID().toString().substring(0, 8) + "@deleted.local";
    }
    
    /**
     * K-анонімність для статистичних даних
     */
    public <T> List<T> applyKAnonymity(List<T> records, int k, List<String> quasiIdentifiers) {
        // Групування за квазі-ідентифікаторами
        Map<String, List<T>> groups = records.stream()
            .collect(Collectors.groupingBy(r -> extractQuasiIdentifiers(r, quasiIdentifiers)));
        
        // Узагальнення груп менших за k
        return groups.entrySet().stream()
            .flatMap(entry -> {
                if (entry.getValue().size() < k) {
                    return entry.getValue().stream().map(this::generalize);
                }
                return entry.getValue().stream();
            })
            .collect(Collectors.toList());
    }
}
```

---

## 6. Впровадження Портативності Даних

### 6.1 Специфікація Формату Експорту

| **Формат** | **Випадок Використання** | **Вміст** | **Стандарт** |
|------------|--------------------------|-----------|--------------|
| JSON | Машинно-читабельний експорт | Всі дані користувача | RFC 8259 |
| CSV | Імпорт в таблиці | Табличні дані | RFC 4180 |
| PDF | Людино-читабельний звіт | Форматоване резюме | PDF/A-3 |

### 6.2 Структура Даних Експорту

Для JSON реалізовано через `GET /api/v1/users/me/export` (функція 1.3, версія формату `1.1` з 2.1.8 (SCRUM-43): додано `award_versions`). Назви властивостей — у snake_case; кожен розділ-список присутній, можливо порожній. IP-адреси є власними ідентифікаторами особи (ст. 15 і 20) і експортуються без приховування; журнал активності зберігає адресу лише для дій, які особа виконує сама (вхід, вихід, підтвердження адреси, скидання пароля, відкликання «це був не я», зміни профілю й адреси, відхилені запити, експорти); події, які будь-хто може спричинити щодо облікового запису (невдалі входи, запити на скидання, блокування), і події, спричинені іншою особою (ролі, делегування), мають `null`, тож адреса стороннього чи адміністратора до файлу не потрапляє. Налаштування додаються до файлу з епіком 7.

```json
{
  "export_metadata": {
    "export_date": "2026-09-30T09:00:00Z",
    "user_id": 5,
    "format_version": "1.1",
    "gdpr_article": "Article 20 - Right to Data Portability"
  },
  "personal_data": {
    "profile": {
      "email": "employee.fmi@chnu.edu.ua",
      "first_name": "Анастасія",
      "last_name": "Петренко",
      "department": { "id": 64, "name": "Department of Algebra and Informatics", "name_uk": "Кафедра алгебри та інформатики" },
      "faculty": { "id": 9, "name": "Faculty of Mathematics and Informatics", "name_uk": "Факультет математики та інформатики" },
      "account_status": "ACTIVE",
      "created_at": "2026-09-01T08:00:00Z",
      "last_login_at": "2026-09-30T08:55:00Z"
    }
  },
  "roles": [
    { "role": "EMPLOYEE", "organization": { "id": 64, "name": "…", "name_uk": "…" },
      "valid_from": "2026-09-01", "valid_to": null, "current": true }
  ],
  "delegations": [
    { "direction": "RECEIVED", "other_party": "Петро Мартинюк", "role": "DEAN", "organization": { "id": 9, "name": "…", "name_uk": "…" },
      "valid_from": "2026-09-30", "valid_to": "2026-10-05", "state": "active", "reason": null,
      "created_at": "2026-09-29T10:00:00Z", "revoked_at": null }
  ],
  "awards": [
    { "award_id": 21, "title": "Letter of gratitude", "title_uk": "Подяка", "description": null, "description_uk": null,
      "category": { "id": 13, "name": "Ministry", "name_uk": "Міністерство" }, "awarding_organization": "МОН України",
      "award_date": "2025-05-01", "status": "DRAFT", "external_url": null, "organization": { "id": 64, "name": "…", "name_uk": "…" },
      "created_at": "2026-09-20T09:00:00Z", "updated_at": "2026-09-20T09:00:00Z" }
  ],
  "award_versions": [
    { "award_id": 21, "version": 1, "action": "CREATED", "changed_fields": [], "created_at": "2026-09-20T09:00:00Z",
      "snapshot": { "title": "Letter of gratitude", "title_uk": "Подяка", "description": null, "description_uk": null,
        "awarding_organization": "МОН України", "award_date": "2025-05-01", "category_id": 13, "status": "DRAFT",
        "impact_score": null, "external_url": null, "organization_id": 64 } }
  ],
  "documents": [
    { "document_id": 7, "award_id": 21, "file_name": "certificate.pdf", "file_type": "PDF", "mime_type": "application/pdf",
      "file_size": 184320, "uploaded_at": "2026-09-20T09:05:00Z", "api_path": "/api/v1/documents/7" }
  ],
  "consent_history": [
    { "consent_type": "DATA_PROCESSING", "consent_version": "1.0", "granted": true, "granted_at": "2026-09-01T08:00:00Z",
      "withdrawn_at": null, "ip_address": "10.0.0.7", "created_at": "2026-09-01T08:00:00Z" }
  ],
  "devices": [
    { "browser": "Firefox", "operating_system": "Windows", "last_ip_address": "10.0.0.7",
      "first_seen_at": "2026-09-01T08:01:00Z", "last_used_at": "2026-09-30T08:55:00Z" }
  ],
  "activity_log": [
    { "action": "LOGIN_SUCCESS", "timestamp": "2026-09-30T08:55:00Z", "ip_address": "10.0.0.7" },
    { "action": "ROLE_ASSIGNED", "timestamp": "2026-09-02T12:00:00Z", "ip_address": null }
  ]
}
```

Версії нагород не вказують, хто їх зберіг, бо пізніший рецензент — інша особа. Файл ніколи не містить хешу пароля, одноразових токенів чи їхніх хешів, записів сервера авторизації, знімків рядків тригерів аудиту, відбитків пристроїв, ключів сховища або даних інших людей, окрім імені другої сторони делегування.

### 6.3 Технічне Впровадження

У реалізації (функція 1.3) файл збирається синхронно з іменованих стовпців (`PersonalDataAssembler`), повертається як вкладення з `Cache-Control: no-store` і завантажується браузером як Blob, тож bearer-токен ніколи не з'являється в URL. Тимчасовий файл у сховищі та 24-годинне посилання для завантаження з ескізу нижче не використовуються: дані однієї особи займають десятки кілобайтів, а посилання без bearer-токена було б ще одним секретом для захисту. Експорт обмежено одним на хвилину, він фіксується в аудиті як `DATA_EXPORT` (`entity_type` `GDPR`, кількості записів у розділах) і супроводжується листом із часом, IP та браузером. CSV і PDF з'являться разом із механізмом звітів (епік 6).

```java
// Сервіс Портативності Даних
@Service
public class DataPortabilityService {
    
    /**
     * Генерація повного експорту даних для користувача
     */
    public DataExport generateExport(Long userId, ExportFormat format) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        // Збір всіх даних користувача
        UserDataBundle bundle = UserDataBundle.builder()
            .profile(extractUserProfile(user))
            .preferences(extractPreferences(userId))
            .awards(extractAwards(userId))
            .documents(extractDocuments(userId))
            .consentHistory(extractConsentHistory(userId))
            .activityLog(extractActivityLog(userId))
            .build();
        
        // Форматування експорту
        byte[] exportData = switch (format) {
            case JSON -> jsonExporter.export(bundle);
            case CSV -> csvExporter.export(bundle);
            case PDF -> pdfExporter.export(bundle);
        };
        
        // Генерація безпечного посилання для завантаження
        String downloadToken = generateSecureToken(userId);
        String downloadUrl = storeTempFile(exportData, downloadToken, format);
        
        // Аудит лог
        auditService.logDataExport(userId, format);
        
        return DataExport.builder()
            .userId(userId)
            .format(format)
            .downloadUrl(downloadUrl)
            .expiresAt(Instant.now().plus(Duration.ofHours(24)))
            .sizeBytes(exportData.length)
            .build();
    }
    
    /**
     * Витяг даних профілю користувача
     */
    private ProfileData extractUserProfile(User user) {
        return ProfileData.builder()
            .email(user.getEmailAddress())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .organization(user.getOrganization().getName())
            .registrationDate(user.getCreatedAt())
            .build();
    }
    
    /**
     * Витяг нагород з мінімальними метаданими
     */
    private List<AwardExport> extractAwards(Long userId) {
        return awardRepository.findByUserId(userId).stream()
            .map(award -> AwardExport.builder()
                .awardId(award.getId().toString())
                .title(award.getTitle())
                .date(award.getAwardDate())
                .category(award.getCategory().getName())
                .status(award.getStatus().name())
                .awardingOrganization(award.getAwardingOrganization())
                .build())
            .collect(Collectors.toList());
    }
}
```

---

## 7. Впровадження Сповіщення про Витік

### 7.1 Потік Виявлення та Реагування на Витік

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ПРОЦЕС СПОВІЩЕННЯ ПРО ВИТІК                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Виявлення                  Оцінка                      Сповіщення          │
│       │                       │                           │                 │
│       ▼                       ▼                           ▼                 │
│  ┌─────────┐           ┌─────────────┐             ┌─────────────┐         │
│  │ SIEM    │           │ Оцінка      │             │ DPA (72год) │         │
│  │ Сповіщ. │──────────▶│ Серйозності │────────────▶│ Сповіщення  │         │
│  └─────────┘           └─────────────┘             └─────────────┘         │
│       │                       │                           │                 │
│       │                       │                    ┌──────┴──────┐         │
│       │                       │                    │             │         │
│       ▼                       ▼                    ▼             ▼         │
│  ┌─────────┐           ┌─────────────┐       ┌─────────┐ ┌─────────┐       │
│  │ Виявл.  │           │ Інвентар    │       │ Сповіщ. │ │Внутрішні│       │
│  │ Аномалій│           │ Даних       │       │ Корист. │ │ Команди │       │
│  └─────────┘           └─────────────┘       └─────────┘ └─────────┘       │
│                                                                             │
│  Вимоги до Таймлайну:                                                       │
│  - Виявлення до Оцінки: < 4 години                                         │
│  - Оцінка до Сповіщення DPA: < 72 години                                   │
│  - Сповіщення Користувачів: "Без невиправданої затримки" якщо високий ризик│
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.2 Класифікація Серйозності Витоку

| **Серйозність** | **Критерії** | **Сповіщення DPA** | **Сповіщ. Користувачів** | **Таймлайн** |
|-----------------|--------------|--------------------|--------------------------| -------------|
| **Критична** | >10,000 записів АБО облікові дані | Обов'язково | Обов'язково | Негайно |
| **Висока** | 1,000-10,000 записів АБО особливі категорії | Обов'язково | Ймовірно | 24 години |
| **Середня** | 100-1,000 записів | Обов'язково | За обставинами | 48 годин |
| **Низька** | <100 записів, немає спеціальних даних | Лише документування | Не потрібно | 72 години |

### 7.3 Технічне Впровадження

```java
// Сервіс Виявлення та Сповіщення про Витік
@Service
public class BreachNotificationService {
    
    /**
     * Обробка виявленого витоку безпеки
     */
    @Async
    public void handleBreachDetection(SecurityBreachEvent event) {
        // Крок 1: Створення запису інциденту
        BreachIncident incident = BreachIncident.builder()
            .detectedAt(Instant.now())
            .source(event.getSource())
            .affectedSystem(event.getAffectedSystem())
            .status(BreachStatus.DETECTED)
            .build();
        
        incident = breachRepository.save(incident);
        
        // Крок 2: Оцінка серйозності
        BreachAssessment assessment = assessBreachSeverity(event);
        incident.setAssessment(assessment);
        incident.setStatus(BreachStatus.ASSESSED);
        breachRepository.save(incident);
        
        // Крок 3: Ідентифікація постраждалих користувачів
        List<Long> affectedUsers = identifyAffectedUsers(event);
        incident.setAffectedUserCount(affectedUsers.size());
        
        // Крок 4: Запуск сповіщень на основі серйозності
        if (assessment.requiresDpaNotification()) {
            scheduleDpaNotification(incident);
        }
        
        if (assessment.requiresUserNotification()) {
            scheduleUserNotifications(incident, affectedUsers);
        }
        
        // Крок 5: Внутрішня ескалація
        escalateToSecurityTeam(incident);
        
        // Крок 6: Аудит лог
        auditService.logBreachIncident(incident);
    }
    
    /**
     * Оцінка серйозності витоку на основі типів та обсягу даних
     */
    private BreachAssessment assessBreachSeverity(SecurityBreachEvent event) {
        int severityScore = 0;
        
        // Фактор обсягу
        int recordCount = event.getEstimatedRecordCount();
        if (recordCount > 10000) severityScore += 40;
        else if (recordCount > 1000) severityScore += 30;
        else if (recordCount > 100) severityScore += 20;
        else severityScore += 10;
        
        // Фактор чутливості даних
        if (event.containsCredentials()) severityScore += 30;
        if (event.containsSpecialCategories()) severityScore += 25;
        if (event.containsFinancialData()) severityScore += 20;
        if (event.containsPII()) severityScore += 15;
        
        // Фактор витонченості атаки
        if (event.isTargetedAttack()) severityScore += 10;
        
        return BreachAssessment.builder()
            .severityScore(severityScore)
            .severity(calculateSeverityLevel(severityScore))
            .requiresDpaNotification(severityScore >= 30)
            .requiresUserNotification(severityScore >= 50)
            .build();
    }
    
    /**
     * Генерація вмісту сповіщення DPA (GDPR Стаття 33)
     */
    private DpaNotification generateDpaNotification(BreachIncident incident) {
        return DpaNotification.builder()
            .incidentDate(incident.getDetectedAt())
            .notificationDate(Instant.now())
            .natureOfBreach(incident.getDescription())
            .categoriesOfData(incident.getDataCategories())
            .approximateRecords(incident.getAffectedUserCount())
            .likelyConsequences(incident.getAssessment().getConsequences())
            .measuresTaken(incident.getMitigationMeasures())
            .dpoContact(getDpoContactInfo())
            .build();
    }
}
```

---

## 8. Дашборд Контролів Конфіденційності

### 8.1 Функції Дашборду Конфіденційності Користувача

| **Функція** | **Опис** | **Стаття GDPR** |
|-------------|----------|-----------------|
| **Переглянути Мої Дані** | Відображення всіх зібраних персональних даних | Ст. 15 - Доступ |
| **Редагувати Мої Дані** | Виправлення неточної інформації | Ст. 16 - Виправлення |
| **Видалити Мої Дані** | Запит на видалення акаунту та даних | Ст. 17 - Видалення |
| **Експортувати Мої Дані** | Завантаження персональних даних в портативному форматі | Ст. 20 - Портативність |
| **Управління Згодою** | Надання/відкликання конкретних згод | Ст. 7 - Згода |
| **Налаштування Конфіденційності** | Конфігурація видимості та обміну | Ст. 18 - Обмеження |
| **Журнал Активності** | Перегляд всього доступу до даних та змін | Ст. 15 - Доступ |

### 8.2 Макет Дашборду

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      ДАШБОРД КОНФІДЕНЦІЙНОСТІ                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  МОЇ ДАНІ (РЕЗЮМЕ)                                                   │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐│   │
│  │  │ Профіль     │  │ Нагороди    │  │ Документи   │  │ Активність  ││   │
│  │  │ 8 полів     │  │ 12 записів  │  │ 23 файли    │  │ 156 подій   ││   │
│  │  │ [Переглянути] │ [Переглянути] │ [Переглянути] │ [Переглянути] ││   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘│   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  УПРАВЛІННЯ ЗГОДОЮ                                                   │   │
│  │                                                                      │   │
│  │  ☑ Обробка Даних (Обов'язково)      Надано: 15 Січ, 2024            │   │
│  │  ☑ Публічне Відображення Нагород    [Управління Видимістю]          │   │
│  │  ☑ Email Сповіщення                 [Налаштування]                  │   │
│  │  ☐ SMS Сповіщення                   Не увімкнено                    │   │
│  │  ☑ AI Обробка Документів            [Відкликати]                    │   │
│  │  ☐ Аналітика Використання           Відмовлено                      │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ДІЇ З ПРАВАМИ ДАНИХ                                                 │   │
│  │                                                                      │   │
│  │  [📥 Експортувати Дані] [✏️ Виправити Дані] [🗑️ Видалити Акаунт]   │   │
│  │                                                                      │   │
│  │  Останній експорт: 10 Груд, 2025   Запити на обробці: 0 очікують    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Контрольний Список Впровадження

### 9.1 Контрольний Список Відповідності Privacy by Design

| **Принцип** | **Впровадження** | **Статус** | **Валідація** |
|-------------|------------------|------------|---------------|
| **1. Проактивний** | Оцінка впливу на конфіденційність до розробки | ✅ | DPIA завершено |
| **2. За Замовчуванням** | Мінімальний збір даних, налаштування конфіденційності | ✅ | Огляд конфігурації |
| **3. Вбудований** | Контролі конфіденційності в архітектурі, не додаток | ✅ | Огляд архітектури |
| **4. Позитивна Сума** | Повна функціональність з конфіденційністю | ✅ | Тестування функцій |
| **5. Наскрізний** | Шифрування, зберігання, життєвий цикл видалення | ✅ | Аудит безпеки |
| **6. Прозорий** | Зрозуміла політика конфіденційності, контролі користувача | ✅ | Тестування користувачами |
| **7. Користувач-Центричний** | Контроль користувача, управління згодою | ✅ | Огляд UX |

### 9.2 Статус Впровадження Прав GDPR

| **Право** | **Стаття** | **Функція** | **Статус** |
|-----------|------------|-------------|------------|
| Доступ | Ст. 15 | Дашборд Переглянути Мої Дані | ✅ Спроектовано |
| Виправлення | Ст. 16 | Редагування профілю, запит виправлення даних | ✅ Спроектовано |
| Видалення | Ст. 17 | Видалення акаунту, анонімізація даних | ✅ Спроектовано |
| Портативність | Ст. 20 | Експорт JSON/CSV/PDF | ✅ JSON реалізовано (функція 1.3); CSV/PDF з епіком 6 |
| Обмеження | Ст. 18 | Налаштування конфіденційності, контролі видимості | ✅ Спроектовано |
| Заперечення | Ст. 21 | Відкликання згоди, відмова | ✅ Спроектовано |

---

## Пов'язані Документи

- **Етап 10**: [Архітектура Безпеки](./SECURITY_ARCHITECTURE_ua.md)
- **Етап 10**: [Модель Загроз](./THREAT_MODEL_ua.md)
- **Етап 10**: [Автентифікація та Авторизація](./AUTHENTICATION_AUTHORIZATION_ua.md)
- **Етап 6**: [Оцінка Впливу на Конфіденційність](../compliance/PRIVACY_IMPACT_ua.md)
- **Етап 6**: [Управління Даними](../compliance/DATA_GOVERNANCE_ua.md)

---

*Версія Документа: 1.0*  
*Класифікація: Внутрішній*  
*Етап: 10 - Архітектура Безпеки та Проектування Конфіденційності*  
*Автор: Стефан Костик*


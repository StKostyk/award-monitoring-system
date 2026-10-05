# Дизайн Автентифікації та Авторизації
## Система Моніторингу та Відстеження Нагород

> **Етап 10 Результат**: Архітектура Безпеки та Проектування Конфіденційності  
> **Версія Документа**: 1.1  
> **Останнє Оновлення**: Вересень 2026  
> **Автор**: Стефан Костик  
> **Модель Безпеки**: OAuth2 + JWT + RBAC  
> **Класифікація**: Внутрішній

---

## Резюме

Цей документ визначає комплексну архітектуру автентифікації та авторизації для Системи Моніторингу та Відстеження Нагород. Дизайн впроваджує OAuth2 з JWT токенами для stateless автентифікації та гібридну модель RBAC/ABAC для детального контролю доступу, забезпечуючи безпечний контроль доступу на всіх компонентах системи.

### Ключові Функції Безпеки

| **Функція** | **Технологія** | **Призначення** |
|-------------|----------------|-----------------|
| **Автентифікація** | OAuth2 + JWT | Безпечна stateless верифікація ідентичності |
| **Авторизація** | RBAC + ABAC | Детальний контроль доступу |
| **MFA** | TOTP + SMS + Email | Посилена безпека автентифікації |
| **Управління Сесіями** | Stateless JWT + Redis | Масштабоване управління сесіями |
| **Безпека Токенів** | RS256 підписання + ротація | Криптографічний захист токенів |

---

## 1. Архітектура Автентифікації

### 1.1 Огляд OAuth2 Потоку

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ПОТІК АВТЕНТИФІКАЦІЇ OAUTH2                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Користувач           Frontend            Auth Сервер          API Сервер  │
│   │                       │                   │                        │    │
│   │  1. Запит Входу       │                   │                        │    │
│   │──────────────────────▶│                   │                        │    │
│   │                       │                   │                        │    │
│   │                       │  2. Auth Запит    │                        │    │
│   │                       │──────────────────▶│                        │    │
│   │                       │                   │                        │    │
│   │                       │  3. Сторінка Входу│                        │    │
│   │◀─────────────────────────────────────────│                        │    │
│   │                       │                   │                        │    │
│   │  4. Облікові Дані     │                   │                        │    │
│   │──────────────────────────────────────────▶│                        │    │
│   │                       │                   │                        │    │
│   │                       │  5. MFA Виклик    │                        │    │
│   │◀─────────────────────────────────────────│                        │    │
│   │                       │                   │                        │    │
│   │  6. MFA Відповідь     │                   │                        │    │
│   │──────────────────────────────────────────▶│                        │    │
│   │                       │                   │                        │    │
│   │                       │  7. Токени (JWT)  │                        │    │
│   │◀─────────────────────────────────────────│                        │    │
│   │                       │                   │                        │    │
│   │                       │  8. API Запит+JWT │                        │    │
│   │                       │───────────────────────────────────────────▶│    │
│   │                       │                   │                        │    │
│   │                       │  9. Відповідь     │                        │    │
│   │◀──────────────────────────────────────────────────────────────────│    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Архітектура Токенів

| **Тип Токена** | **Призначення** | **Час Життя** | **Зберігання** | **Оновлення** |
|----------------|-----------------|---------------|----------------|---------------|
| **Access Token** | API автентифікація | 15 хвилин | Лише пам'ять | Через refresh токен |
| **Refresh Token** | Отримання нових access токенів | 7 днів | HttpOnly cookie | Через повторну автентифікацію |
| **ID Token** | Claims ідентичності користувача | 1 година | Лише пам'ять | Не оновлюється |

### 1.3 Структура JWT

> Ескіз проєкту. Реалізовану форму описано в [§9 Доповнення щодо реалізації](#9-доповнення-щодо-реалізації-вересень-2026).

```json
{
  "header": {
    "alg": "RS256",
    "typ": "JWT",
    "kid": "key-2025-001"
  },
  "payload": {
    "iss": "https://auth.award-system.ua",
    "sub": "user-123e4567-e89b",
    "aud": "award-api",
    "exp": 1702728000,
    "iat": 1702727100,
    "nbf": 1702727100,
    "jti": "unique-token-id-abc123",
    "roles": ["EMPLOYEE", "FACULTY_SECRETARY"],
    "permissions": ["award:read", "award:create", "award:approve:level1"],
    "org_id": "org-456",
    "org_type": "FACULTY",
    "mfa_verified": true,
    "session_id": "sess-789"
  },
  "signature": "RS256-signed-value"
}
```

### 1.4 Впровадження Автентифікації

> Ескіз проєкту. Реалізовану форму описано в [§9 Доповнення щодо реалізації](#9-доповнення-щодо-реалізації-вересень-2026).

```java
// Конфігурація OAuth2 Authorization Server
@Configuration
@EnableAuthorizationServer
public class AuthorizationServerConfig {
    
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient webClient = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("award-web-client")
            .clientSecret(passwordEncoder.encode(clientSecret))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://app.award-system.ua/callback")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope("awards:read")
            .scope("awards:write")
            .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(15))
                .refreshTokenTimeToLive(Duration.ofDays(7))
                .reuseRefreshTokens(false)
                .build())
            .build();
        
        return new InMemoryRegisteredClientRepository(webClient);
    }
    
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = generateRsaKey();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }
    
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            if (context.getTokenType().equals(OAuth2TokenType.ACCESS_TOKEN)) {
                UserDetails userDetails = (UserDetails) context.getPrincipal().getPrincipal();
                User user = userService.findByEmail(userDetails.getUsername());
                
                context.getClaims()
                    .claim("roles", user.getRoles().stream()
                        .map(UserRole::getRoleType)
                        .map(Enum::name)
                        .collect(Collectors.toList()))
                    .claim("permissions", permissionService.getPermissions(user))
                    .claim("org_id", user.getOrganization().getId())
                    .claim("org_type", user.getOrganization().getOrgType().name())
                    .claim("mfa_verified", context.get("mfa_verified"));
            }
        };
    }
}

// Конфігурація Resource Server
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class ResourceServerConfig {
    
    @Bean
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/health/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("SYSTEM_ADMIN")
                .requestMatchers("/api/awards/**").authenticated()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .build();
    }
    
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("permissions");
        grantedAuthoritiesConverter.setAuthorityPrefix("");
        
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            
            // Додавання повноважень на основі ролей
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(role -> authorities.add(
                    new SimpleGrantedAuthority("ROLE_" + role)));
            }
            
            // Додавання повноважень на основі дозволів
            List<String> permissions = jwt.getClaimAsStringList("permissions");
            if (permissions != null) {
                permissions.forEach(permission -> authorities.add(
                    new SimpleGrantedAuthority(permission)));
            }
            
            return authorities;
        });
        
        return converter;
    }
}
```

---

## 2. Багатофакторна Автентифікація (MFA)

### 2.1 Стратегія MFA

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        МАТРИЦЯ СТРАТЕГІЇ MFA                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Оцінка Рівня Ризику                       Вимога MFA                       │
│                                                                             │
│  ┌─────────────────────────────────────┐     ┌─────────────────────────┐   │
│  │ НИЗЬКИЙ РИЗИК                       │     │ Опціонально             │   │
│  │ - Відомий пристрій                  │────▶│ - Email верифікація     │   │
│  │ - Звичайна локація                  │     │   доступна              │   │
│  │ - Звичайний час                     │     └─────────────────────────┘   │
│  └─────────────────────────────────────┘                                   │
│                                                                             │
│  ┌─────────────────────────────────────┐     ┌─────────────────────────┐   │
│  │ СЕРЕДНІЙ РИЗИК                      │     │ Обов'язково (Вибір)     │   │
│  │ - Новий пристрій                    │────▶│ - TOTP                  │   │
│  │ - Інша локація                      │     │ - SMS                   │   │
│  │ - Незвичний час                     │     │ - Email                 │   │
│  └─────────────────────────────────────┘     └─────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────┐     ┌─────────────────────────┐   │
│  │ ВИСОКИЙ РИЗИК                       │     │ Обов'язково (Сильний)   │   │
│  │ - Адмін доступ                      │────▶│ - TOTP обов'язково      │   │
│  │ - Чутлива операція                  │     │ - Опція апаратний ключ  │   │
│  │ - Багато невдалих спроб             │     └─────────────────────────┘   │
│  └─────────────────────────────────────┘                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Методи MFA

| **Метод** | **Рівень Безпеки** | **UX** | **Відновлення** | **Випадок Використ.** |
|-----------|-------------------|--------|-----------------|----------------------|
| **TOTP** (Authenticator App) | Високий | Середній | Резервні коди | За замовчуванням для всіх |
| **SMS** | Середній | Високий | Верифікація телефону | Запасна опція |
| **Email** | Середній | Високий | Верифікація email | Сценарії низького ризику |
| **Апаратний Ключ** (WebAuthn) | Дуже Високий | Середній | Адмін відновлення | Адмін акаунти |

### 2.3 Впровадження MFA

```java
// Сервіс MFA
@Service
public class MfaService {
    
    private final TotpService totpService;
    private final SmsService smsService;
    private final EmailService emailService;
    private final WebAuthnService webAuthnService;
    private final RiskAssessmentService riskService;
    
    /**
     * Визначення чи потрібен MFA на основі оцінки ризику
     */
    public MfaRequirement assessMfaRequirement(User user, AuthenticationContext context) {
        RiskLevel risk = riskService.assessLoginRisk(user, context);
        
        // Адмін завжди вимагає MFA
        if (user.hasRole(RoleType.SYSTEM_ADMIN) || user.hasRole(RoleType.RECTOR)) {
            return MfaRequirement.required(MfaStrength.HIGH);
        }
        
        return switch (risk) {
            case LOW -> MfaRequirement.optional();
            case MEDIUM -> MfaRequirement.required(MfaStrength.STANDARD);
            case HIGH -> MfaRequirement.required(MfaStrength.HIGH);
            case CRITICAL -> MfaRequirement.blocked(); // Блокування та сповіщення
        };
    }
    
    /**
     * Ініціалізація MFA виклику на основі налаштувань користувача
     */
    public MfaChallenge initiateMfaChallenge(User user, MfaMethod preferredMethod) {
        return switch (preferredMethod) {
            case TOTP -> initiateTotpChallenge(user);
            case SMS -> initiateSmsChallenge(user);
            case EMAIL -> initiateEmailChallenge(user);
            case HARDWARE_KEY -> initiateWebAuthnChallenge(user);
        };
    }
    
    private MfaChallenge initiateTotpChallenge(User user) {
        if (!user.hasTotpEnabled()) {
            throw new MfaNotConfiguredException("TOTP не налаштовано для користувача");
        }
        
        return MfaChallenge.builder()
            .challengeId(UUID.randomUUID().toString())
            .userId(user.getId())
            .method(MfaMethod.TOTP)
            .expiresAt(Instant.now().plus(Duration.ofMinutes(5)))
            .build();
    }
    
    private MfaChallenge initiateSmsChallenge(User user) {
        String code = generateSecureCode(6);
        String maskedPhone = maskPhoneNumber(user.getPhoneNumber());
        
        // Збереження виклику
        MfaChallenge challenge = MfaChallenge.builder()
            .challengeId(UUID.randomUUID().toString())
            .userId(user.getId())
            .method(MfaMethod.SMS)
            .code(passwordEncoder.encode(code))
            .expiresAt(Instant.now().plus(Duration.ofMinutes(5)))
            .metadata(Map.of("masked_phone", maskedPhone))
            .build();
        
        challengeRepository.save(challenge);
        
        // Надсилання SMS
        smsService.sendVerificationCode(user.getPhoneNumber(), code);
        
        return challenge.withoutCode(); // Не розкриваємо код
    }
    
    /**
     * Верифікація MFA відповіді
     */
    public MfaVerificationResult verifyMfa(String challengeId, String response) {
        MfaChallenge challenge = challengeRepository.findById(challengeId)
            .orElseThrow(() -> new InvalidChallengeException("Виклик не знайдено"));
        
        if (challenge.isExpired()) {
            auditService.logMfaFailure(challenge.getUserId(), "Виклик закінчився");
            return MfaVerificationResult.expired();
        }
        
        boolean verified = switch (challenge.getMethod()) {
            case TOTP -> totpService.verify(
                getUserTotpSecret(challenge.getUserId()), response);
            case SMS, EMAIL -> passwordEncoder.matches(response, challenge.getCode());
            case HARDWARE_KEY -> webAuthnService.verifyAssertion(challenge, response);
        };
        
        if (verified) {
            auditService.logMfaSuccess(challenge.getUserId(), challenge.getMethod());
            challengeRepository.delete(challenge);
            return MfaVerificationResult.success();
        } else {
            auditService.logMfaFailure(challenge.getUserId(), "Невірний код");
            incrementFailedAttempts(challenge);
            return MfaVerificationResult.failed();
        }
    }
}

// Сервіс TOTP
@Service
public class TotpService {
    
    private static final int CODE_DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int ALLOWED_TIME_SKEW = 1; // Дозвіл 1 крок до/після
    
    public String generateSecret() {
        byte[] buffer = new byte[20];
        new SecureRandom().nextBytes(buffer);
        return Base32.encode(buffer);
    }
    
    public String generateQrCodeUri(String secret, String userEmail, String issuer) {
        return String.format(
            "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
            URLEncoder.encode(issuer, StandardCharsets.UTF_8),
            URLEncoder.encode(userEmail, StandardCharsets.UTF_8),
            secret,
            URLEncoder.encode(issuer, StandardCharsets.UTF_8),
            CODE_DIGITS,
            TIME_STEP_SECONDS
        );
    }
    
    public boolean verify(String secret, String code) {
        long currentTimeStep = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
        
        // Перевірка поточного та сусідніх кроків часу для зсуву годинника
        for (int i = -ALLOWED_TIME_SKEW; i <= ALLOWED_TIME_SKEW; i++) {
            String expectedCode = generateCode(secret, currentTimeStep + i);
            if (MessageDigest.isEqual(expectedCode.getBytes(), code.getBytes())) {
                return true;
            }
        }
        return false;
    }
    
    private String generateCode(String secret, long timeStep) {
        byte[] key = Base32.decode(secret);
        byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();
        
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) |
                        ((hash[offset + 1] & 0xFF) << 16) |
                        ((hash[offset + 2] & 0xFF) << 8) |
                        (hash[offset + 3] & 0xFF);
            
            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new RuntimeException("Генерація TOTP невдала", e);
        }
    }
}
```

---

## 3. Рольовий Контроль Доступу (RBAC)

### 3.1 Ієрархія Ролей

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ІЄРАРХІЯ РОЛЕЙ                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                        ┌─────────────────┐                                  │
│                        │  SYSTEM_ADMIN   │                                  │
│                        │  (Повний Доступ)│                                  │
│                        └────────┬────────┘                                  │
│                                 │                                           │
│                    ┌────────────┴────────────┐                              │
│                    │                         │                              │
│            ┌───────┴───────┐         ┌───────┴───────┐                     │
│            │    RECTOR     │         │  GDPR_OFFICER │                     │
│            │ (Університет) │         │ (Відповідність)│                    │
│            └───────┬───────┘         └───────────────┘                     │
│                    │                                                        │
│            ┌───────┴───────┐                                               │
│            │RECTOR_SECRETARY│                                               │
│            │(Офіс Ректора) │                                               │
│            └───────┬───────┘                                               │
│                    │                                                        │
│            ┌───────┴───────┐                                               │
│            │     DEAN      │                                               │
│            │  (Факультет)  │                                               │
│            └───────┬───────┘                                               │
│                    │                                                        │
│          ┌─────────┴─────────┐                                             │
│          │ FACULTY_SECRETARY │                                             │
│          │   (Кафедра)       │                                             │
│          └─────────┬─────────┘                                             │
│                    │                                                        │
│            ┌───────┴───────┐                                               │
│            │   EMPLOYEE    │                                               │
│            │   (Базовий)   │                                               │
│            └───────────────┘                                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Визначення Ролей

| **Роль** | **Область** | **Опис** | **Типові Користувачі** |
|----------|-------------|----------|------------------------|
| `EMPLOYEE` | Персональний | Базовий доступ до системи | Всі працівники |
| `FACULTY_SECRETARY` | Кафедра | Затвердження першого рівня | Адмін персонал |
| `DEAN` | Факультет | Затвердження на рівні факультету | Декани факультетів |
| `RECTOR_SECRETARY` | Університет | Обробка високого рівня | Офіс ректора |
| `RECTOR` | Університет | Фінальне затвердження | Ректор університету |
| `SYSTEM_ADMIN` | Система | Технічне адміністрування | IT адміністратори |
| `GDPR_OFFICER` | Відповідність | Нагляд за захистом даних | Роль DPO |

### 3.3 Матриця Дозволів

| **Дозвіл** | **EMPLOYEE** | **FAC_SEC** | **DEAN** | **RECT_SEC** | **RECTOR** | **ADMIN** | **GDPR** |
|------------|:------------:|:-----------:|:--------:|:------------:|:----------:|:---------:|:--------:|
| `award:read:own` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `award:read:department` | - | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `award:read:faculty` | - | - | ✅ | ✅ | ✅ | ✅ | ✅ |
| `award:read:all` | - | - | - | ✅ | ✅ | ✅ | ✅ |
| `award:create` | ✅ | ✅ | ✅ | ✅ | ✅ | - | - |
| `award:update:own` | ✅ | ✅ | ✅ | ✅ | ✅ | - | - |
| `award:approve:level1` | - | ✅ | ✅ | ✅ | ✅ | - | - |
| `award:approve:level2` | - | - | ✅ | ✅ | ✅ | - | - |
| `award:approve:level3` | - | - | - | ✅ | ✅ | - | - |
| `award:approve:final` | - | - | - | - | ✅ | - | - |
| `user:read:all` | - | - | - | - | - | ✅ | ✅ |
| `user:read:scope` | - | ✅ | ✅ | ✅ | ✅ | - | - |
| `user:manage` | - | - | - | - | - | ✅ | - |
| `user:manage:scope` | - | ✅ | ✅ | ✅ | ✅ | - | - |
| `system:configure` | - | - | - | - | - | ✅ | - |
| `data:export` | - | - | - | - | - | ✅ | ✅ |
| `consent:manage` | - | - | - | - | - | - | ✅ |
| `audit:read` | - | - | - | - | - | ✅ | ✅ |

### 3.4 Впровадження RBAC

> Ескіз проєкту. Реалізовану форму описано в [§9 Доповнення щодо реалізації](#9-доповнення-щодо-реалізації-вересень-2026).

```java
// Константи Дозволів
public final class Permissions {
    // Дозволи нагород
    public static final String AWARD_READ_OWN = "award:read:own";
    public static final String AWARD_READ_DEPARTMENT = "award:read:department";
    public static final String AWARD_READ_FACULTY = "award:read:faculty";
    public static final String AWARD_READ_ALL = "award:read:all";
    public static final String AWARD_CREATE = "award:create";
    public static final String AWARD_UPDATE_OWN = "award:update:own";
    public static final String AWARD_APPROVE_LEVEL1 = "award:approve:level1";
    public static final String AWARD_APPROVE_LEVEL2 = "award:approve:level2";
    public static final String AWARD_APPROVE_LEVEL3 = "award:approve:level3";
    public static final String AWARD_APPROVE_FINAL = "award:approve:final";
    
    // Дозволи користувачів
    public static final String USER_READ_ALL = "user:read:all";
    public static final String USER_MANAGE = "user:manage";
    
    // Системні дозволи
    public static final String SYSTEM_CONFIGURE = "system:configure";
    public static final String DATA_EXPORT = "data:export";
    public static final String CONSENT_MANAGE = "consent:manage";
    public static final String AUDIT_READ = "audit:read";
}

// Сервіс Відображення Роль-Дозвіл
@Service
public class RolePermissionService {
    
    private static final Map<RoleType, Set<String>> ROLE_PERMISSIONS = Map.of(
        RoleType.EMPLOYEE, Set.of(
            Permissions.AWARD_READ_OWN,
            Permissions.AWARD_CREATE,
            Permissions.AWARD_UPDATE_OWN
        ),
        RoleType.FACULTY_SECRETARY, Set.of(
            Permissions.AWARD_READ_OWN,
            Permissions.AWARD_READ_DEPARTMENT,
            Permissions.AWARD_CREATE,
            Permissions.AWARD_UPDATE_OWN,
            Permissions.AWARD_APPROVE_LEVEL1
        ),
        RoleType.DEAN, Set.of(
            Permissions.AWARD_READ_OWN,
            Permissions.AWARD_READ_DEPARTMENT,
            Permissions.AWARD_READ_FACULTY,
            Permissions.AWARD_CREATE,
            Permissions.AWARD_UPDATE_OWN,
            Permissions.AWARD_APPROVE_LEVEL1,
            Permissions.AWARD_APPROVE_LEVEL2
        ),
        // ... більше ролей
        RoleType.SYSTEM_ADMIN, Set.of(
            Permissions.AWARD_READ_ALL,
            Permissions.USER_READ_ALL,
            Permissions.USER_MANAGE,
            Permissions.SYSTEM_CONFIGURE,
            Permissions.DATA_EXPORT,
            Permissions.AUDIT_READ
        )
    );
    
    public Set<String> getPermissionsForRole(RoleType role) {
        return ROLE_PERMISSIONS.getOrDefault(role, Set.of());
    }
    
    public Set<String> getPermissionsForUser(User user) {
        return user.getRoles().stream()
            .filter(UserRole::isCurrentlyValid)
            .map(UserRole::getRoleType)
            .flatMap(role -> getPermissionsForRole(role).stream())
            .collect(Collectors.toSet());
    }
}

// Безпека Методів з RBAC
@Service
public class AwardService {
    
    @PreAuthorize("hasAuthority('award:create')")
    public Award createAward(AwardCreateRequest request) {
        User currentUser = getCurrentUser();
        
        Award award = Award.builder()
            .userId(currentUser.getId())
            .title(request.getTitle())
            .categoryId(request.getCategoryId())
            .status(AwardStatus.DRAFT)
            .build();
        
        return awardRepository.save(award);
    }
    
    @PreAuthorize("hasAuthority('award:approve:level1') or " +
                  "hasAuthority('award:approve:level2') or " +
                  "hasAuthority('award:approve:final')")
    @PostAuthorize("@awardSecurityService.canAccessAward(returnObject, authentication)")
    public Award getAwardForApproval(Long awardId) {
        return awardRepository.findById(awardId)
            .orElseThrow(() -> new NotFoundException("Нагороду не знайдено"));
    }
    
    @PreAuthorize("@awardSecurityService.canApproveAtLevel(#request.awardId, authentication)")
    public ApprovalResult approveAward(ApprovalRequest request) {
        // Логіка затвердження
    }
}

// Кастомний Сервіс Безпеки для Складної Авторизації
@Service
public class AwardSecurityService {
    
    public boolean canAccessAward(Award award, Authentication authentication) {
        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
        
        // Власник може завжди мати доступ
        if (award.getUserId().equals(principal.getUserId())) {
            return true;
        }
        
        // Перевірка організаційної області
        Set<String> authorities = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        
        if (authorities.contains(Permissions.AWARD_READ_ALL)) {
            return true;
        }
        
        if (authorities.contains(Permissions.AWARD_READ_FACULTY)) {
            return isInSameFaculty(award, principal);
        }
        
        if (authorities.contains(Permissions.AWARD_READ_DEPARTMENT)) {
            return isInSameDepartment(award, principal);
        }
        
        return false;
    }
    
    public boolean canApproveAtLevel(Long awardId, Authentication authentication) {
        Award award = awardRepository.findById(awardId).orElse(null);
        if (award == null) return false;
        
        AwardRequest request = award.getRequest();
        if (request == null) return false;
        
        ApprovalLevel requiredLevel = request.getCurrentLevel();
        Set<String> authorities = getAuthorities(authentication);
        
        return switch (requiredLevel) {
            case FACULTY_SECRETARY -> authorities.contains(Permissions.AWARD_APPROVE_LEVEL1);
            case DEAN -> authorities.contains(Permissions.AWARD_APPROVE_LEVEL2);
            case RECTOR_SECRETARY -> authorities.contains(Permissions.AWARD_APPROVE_LEVEL3);
            case RECTOR -> authorities.contains(Permissions.AWARD_APPROVE_FINAL);
        };
    }
}
```

---

## 4. Атрибутний Контроль Доступу (ABAC)

### 4.1 Модель Політик ABAC

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          МОДЕЛЬ ПОЛІТИК ABAC                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Атрибути Суб'єкта       Атрибути Ресурсу        Атрибути                  │
│  (Хто запитує)           (Що запитується)        Середовища                 │
│                                                  (Контекст)                 │
│  ┌─────────────────┐    ┌─────────────────┐     ┌─────────────────┐        │
│  │ - ID Корист.    │    │ - Тип Ресурсу   │     │ - Час Доби      │        │
│  │ - Ролі          │    │ - ID Власника   │     │ - IP Адреса     │        │
│  │ - Кафедра       │    │ - Кафедра       │     │ - Тип Пристрою  │        │
│  │ - Факультет     │    │ - Статус        │     │ - Локація       │        │
│  │ - Допуск        │    │ - Чутливість    │     │ - Рівень Ризику │        │
│  └────────┬────────┘    └────────┬────────┘     └────────┬────────┘        │
│           │                      │                       │                  │
│           └──────────────────────┼───────────────────────┘                  │
│                                  │                                          │
│                                  ▼                                          │
│                      ┌─────────────────────┐                                │
│                      │   ДВИЖОК ПОЛІТИК    │                                │
│                      │                     │                                │
│                      │  IF subject.role    │                                │
│                      │     IN [DEAN]       │                                │
│                      │  AND resource.dept  │                                │
│                      │     IN subject.depts│                                │
│                      │  AND env.risk       │                                │
│                      │     < HIGH          │                                │
│                      │  THEN PERMIT        │                                │
│                      │                     │                                │
│                      └──────────┬──────────┘                                │
│                                 │                                           │
│                                 ▼                                           │
│                      ┌─────────────────────┐                                │
│                      │  ДОЗВОЛИТИ / ВІДМОВИТИ│                              │
│                      └─────────────────────┘                                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Приклади Політик ABAC

| **Політика** | **Суб'єкт** | **Ресурс** | **Дія** | **Середовище** | **Ефект** |
|--------------|-------------|------------|---------|----------------|-----------|
| Доступ Власника Нагороди | user.id = resource.owner_id | Нагорода | READ, UPDATE | Будь-який | ДОЗВОЛИТИ |
| Перегляд Нагород Кафедри | user.dept ∈ resource.depts | Нагорода | READ | Будь-який | ДОЗВОЛИТИ |
| Затвердження Факультету | user.role = DEAN AND user.faculty = resource.faculty | Нагорода | APPROVE | risk < HIGH | ДОЗВОЛИТИ |
| Обмеження Позачасу | user.role ≠ ADMIN | Конф. Системи | * | time ∉ [9:00-18:00] | ВІДМОВИТИ |
| Блокування Високого Ризику | Будь-який | Чутливі Дані | * | risk = CRITICAL | ВІДМОВИТИ |

### 4.3 Впровадження ABAC

```java
// Движок Політик ABAC
@Service
public class AbacPolicyEngine {
    
    private final List<AbacPolicy> policies;
    
    /**
     * Оцінка запиту доступу проти всіх застосовних політик
     */
    public AccessDecision evaluate(AccessRequest request) {
        SubjectAttributes subject = extractSubjectAttributes(request);
        ResourceAttributes resource = extractResourceAttributes(request);
        EnvironmentAttributes environment = extractEnvironmentAttributes(request);
        
        // Пошук застосовних політик
        List<AbacPolicy> applicablePolicies = policies.stream()
            .filter(policy -> policy.appliesTo(subject, resource, request.getAction()))
            .collect(Collectors.toList());
        
        // Оцінка політик (алгоритм комбінування deny-overrides)
        for (AbacPolicy policy : applicablePolicies) {
            PolicyResult result = policy.evaluate(subject, resource, environment);
            
            if (result == PolicyResult.DENY) {
                auditService.logAccessDenied(request, policy.getName());
                return AccessDecision.deny(policy.getDenyReason());
            }
        }
        
        // Перевірка чи будь-яка політика дозволила
        boolean anyPermit = applicablePolicies.stream()
            .anyMatch(p -> p.evaluate(subject, resource, environment) == PolicyResult.PERMIT);
        
        if (anyPermit) {
            auditService.logAccessPermitted(request);
            return AccessDecision.permit();
        }
        
        // Заборона за замовчуванням
        return AccessDecision.deny("Немає застосовної політики дозволу");
    }
}

// Визначення Політики ABAC
public interface AbacPolicy {
    String getName();
    boolean appliesTo(SubjectAttributes subject, ResourceAttributes resource, String action);
    PolicyResult evaluate(SubjectAttributes subject, ResourceAttributes resource, 
                         EnvironmentAttributes environment);
    String getDenyReason();
}

// Приклад: Політика Організаційної Області
@Component
public class OrganizationalScopePolicy implements AbacPolicy {
    
    @Override
    public String getName() {
        return "ORGANIZATIONAL_SCOPE";
    }
    
    @Override
    public boolean appliesTo(SubjectAttributes subject, ResourceAttributes resource, String action) {
        return resource.getType().equals("AWARD") && action.equals("READ");
    }
    
    @Override
    public PolicyResult evaluate(SubjectAttributes subject, ResourceAttributes resource,
                                 EnvironmentAttributes environment) {
        // Системні адміни мають доступ до всього
        if (subject.hasRole("SYSTEM_ADMIN")) {
            return PolicyResult.PERMIT;
        }
        
        // Власник може завжди мати доступ
        if (subject.getUserId().equals(resource.getOwnerId())) {
            return PolicyResult.PERMIT;
        }
        
        // Перевірка організаційної ієрархії
        OrgHierarchy subjectOrg = subject.getOrganizationHierarchy();
        OrgHierarchy resourceOrg = resource.getOrganizationHierarchy();
        
        // Декан може мати доступ до нагород факультету
        if (subject.hasRole("DEAN") && subjectOrg.getFacultyId().equals(resourceOrg.getFacultyId())) {
            return PolicyResult.PERMIT;
        }
        
        // Секретар факультету може мати доступ до нагород кафедри
        if (subject.hasRole("FACULTY_SECRETARY") && 
            subjectOrg.getDepartmentId().equals(resourceOrg.getDepartmentId())) {
            return PolicyResult.PERMIT;
        }
        
        return PolicyResult.NOT_APPLICABLE;
    }
    
    @Override
    public String getDenyReason() {
        return "Користувач не має організаційного доступу до цього ресурсу";
    }
}

// Політика Доступу на Основі Ризику
@Component
public class RiskBasedAccessPolicy implements AbacPolicy {
    
    @Override
    public String getName() {
        return "RISK_BASED_ACCESS";
    }
    
    @Override
    public boolean appliesTo(SubjectAttributes subject, ResourceAttributes resource, String action) {
        return resource.getSensitivity() == Sensitivity.HIGH;
    }
    
    @Override
    public PolicyResult evaluate(SubjectAttributes subject, ResourceAttributes resource,
                                 EnvironmentAttributes environment) {
        // Блокування високоризикового доступу до чутливих ресурсів
        if (environment.getRiskLevel() == RiskLevel.CRITICAL) {
            return PolicyResult.DENY;
        }
        
        // Вимога верифікації MFA для високого ризику
        if (environment.getRiskLevel() == RiskLevel.HIGH && !subject.isMfaVerified()) {
            return PolicyResult.DENY;
        }
        
        return PolicyResult.PERMIT;
    }
    
    @Override
    public String getDenyReason() {
        return "Доступ відхилено через підвищений рівень ризику";
    }
}
```

---

## 5. Безпека та Управління Токенами

### 5.1 Життєвий Цикл Токена

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          ЖИТТЄВИЙ ЦИКЛ ТОКЕНА                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐  │
│  │ Видача  │───▶│ Активний│───▶│Оновлення│───▶│ Ротація │───▶│Відкликан│  │
│  └─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘  │
│                      │              │              │              │         │
│                      │              │              │              │         │
│                      ▼              ▼              ▼              ▼         │
│               ┌───────────────────────────────────────────────────────┐    │
│               │                    КЕШ REDIS                          │    │
│               │  - Активні токени                                     │    │
│               │  - Заблоковані токени                                 │    │
│               │  - Метадані сесій                                     │    │
│               └───────────────────────────────────────────────────────┘    │
│                                                                             │
│  Функції Безпеки Токенів:                                                   │
│  ├── RS256 асиметричне підписання                                          │
│  ├── Ротація ключів кожні 90 днів                                          │
│  ├── Прив'язка токена до сесії                                             │
│  ├── Ротація refresh токена при використанні                               │
│  └── Можливість негайного відкликання                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Впровадження Безпеки Токенів

```java
// Сервіс Управління Токенами
@Service
public class TokenManagementService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtEncoder jwtEncoder;
    
    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private static final String SESSION_PREFIX = "session:";
    
    /**
     * Видача нового access токена з прив'язкою до сесії
     */
    public TokenPair issueTokens(User user, String sessionId) {
        Instant now = Instant.now();
        
        // Access токен (короткоживучий)
        JwtClaimsSet accessClaims = JwtClaimsSet.builder()
            .issuer(issuerUri)
            .subject(user.getId().toString())
            .audience(List.of("award-api"))
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofMinutes(15)))
            .claim("session_id", sessionId)
            .claim("roles", getRoleNames(user))
            .claim("permissions", getPermissions(user))
            .build();
        
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(accessClaims)).getTokenValue();
        
        // Refresh токен (довгоживучий)
        String refreshToken = generateRefreshToken();
        
        // Зберігання refresh токена в Redis
        RefreshTokenData tokenData = RefreshTokenData.builder()
            .userId(user.getId())
            .sessionId(sessionId)
            .createdAt(now)
            .expiresAt(now.plus(Duration.ofDays(7)))
            .build();
        
        redisTemplate.opsForValue().set(
            "refresh:" + refreshToken,
            tokenData,
            Duration.ofDays(7)
        );
        
        return new TokenPair(accessToken, refreshToken);
    }
    
    /**
     * Оновлення access токена з ротацією
     */
    public TokenPair refreshTokens(String refreshToken) {
        String key = "refresh:" + refreshToken;
        RefreshTokenData tokenData = (RefreshTokenData) redisTemplate.opsForValue().get(key);
        
        if (tokenData == null || tokenData.isExpired()) {
            throw new InvalidTokenException("Невалідний або прострочений refresh токен");
        }
        
        User user = userRepository.findById(tokenData.getUserId())
            .orElseThrow(() -> new UserNotFoundException("Користувача не знайдено"));
        
        // Ротація refresh токена
        redisTemplate.delete(key);
        
        // Видача нової пари токенів
        return issueTokens(user, tokenData.getSessionId());
    }
    
    /**
     * Відкликання токена (додавання до чорного списку)
     */
    public void revokeToken(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            String jti = jwt.getId();
            Instant expiry = jwt.getExpiresAt();
            
            // Додавання до чорного списку до оригінального закінчення
            Duration ttl = Duration.between(Instant.now(), expiry);
            if (!ttl.isNegative()) {
                redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + jti,
                    "revoked",
                    ttl
                );
            }
            
            auditService.logTokenRevocation(jwt.getSubject(), jti);
        } catch (JwtException e) {
            log.warn("Не вдалося декодувати токен для відкликання", e);
        }
    }
    
    /**
     * Перевірка чи токен у чорному списку
     */
    public boolean isTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }
    
    /**
     * Відкликання всіх сесій користувача
     */
    public void revokeAllUserSessions(Long userId) {
        Set<String> sessionKeys = redisTemplate.keys(SESSION_PREFIX + userId + ":*");
        if (sessionKeys != null && !sessionKeys.isEmpty()) {
            redisTemplate.delete(sessionKeys);
        }
        
        auditService.logAllSessionsRevoked(userId);
    }
}

// Валідація JWT з Перевіркою Чорного Списку
@Component
public class JwtBlacklistValidator implements OAuth2TokenValidator<Jwt> {
    
    private final TokenManagementService tokenService;
    
    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String jti = jwt.getId();
        
        if (jti == null) {
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Відсутній ID токена", null));
        }
        
        if (tokenService.isTokenBlacklisted(jti)) {
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Токен відкликано", null));
        }
        
        return OAuth2TokenValidatorResult.success();
    }
}
```

---

## 6. Управління Сесіями

### 6.1 Безпека Сесій

| **Функція** | **Впровадження** | **Призначення** |
|-------------|------------------|-----------------|
| **Stateless Сесії** | На основі JWT, без стану сервера | Масштабованість |
| **Прив'язка Сесії** | Токен прив'язаний до ID сесії | Запобігання крадіжці токена |
| **Конкурентні Сесії** | Налаштовуваний ліміт (за замовч.: 3) | Зменшення поверхні атаки |
| **Таймаут Сесії** | 30 хв неактивності | Обмеження експозиції |
| **Примусовий Вихід** | Можливість адміна | Екстрене реагування |

### 6.2 Впровадження Сесій

```java
// Сервіс Управління Сесіями
@Service
public class SessionManagementService {
    
    private static final int MAX_CONCURRENT_SESSIONS = 3;
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);
    
    /**
     * Створення нової сесії з контролем конкурентних сесій
     */
    public Session createSession(User user, DeviceInfo deviceInfo) {
        // Перевірка конкурентних сесій
        List<Session> activeSessions = getActiveSessions(user.getId());
        
        if (activeSessions.size() >= MAX_CONCURRENT_SESSIONS) {
            // Видалення найстарішої сесії
            Session oldest = activeSessions.stream()
                .min(Comparator.comparing(Session::getCreatedAt))
                .orElseThrow();
            
            terminateSession(oldest.getId());
            auditService.logSessionTerminated(user.getId(), oldest.getId(), "Перевищено макс. сесій");
        }
        
        // Створення нової сесії
        Session session = Session.builder()
            .id(UUID.randomUUID().toString())
            .userId(user.getId())
            .deviceInfo(deviceInfo)
            .createdAt(Instant.now())
            .lastActivityAt(Instant.now())
            .expiresAt(Instant.now().plus(SESSION_TIMEOUT))
            .build();
        
        // Зберігання в Redis
        redisTemplate.opsForValue().set(
            sessionKey(session.getId()),
            session,
            SESSION_TIMEOUT
        );
        
        // Додавання до списку сесій користувача
        redisTemplate.opsForSet().add(userSessionsKey(user.getId()), session.getId());
        
        auditService.logSessionCreated(user.getId(), session.getId(), deviceInfo);
        
        return session;
    }
    
    /**
     * Валідація та оновлення сесії
     */
    public Session validateSession(String sessionId) {
        Session session = (Session) redisTemplate.opsForValue().get(sessionKey(sessionId));
        
        if (session == null) {
            throw new InvalidSessionException("Сесію не знайдено");
        }
        
        if (session.isExpired()) {
            terminateSession(sessionId);
            throw new SessionExpiredException("Сесія закінчилася");
        }
        
        // Оновлення таймауту сесії (sliding expiration)
        session.setLastActivityAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(SESSION_TIMEOUT));
        
        redisTemplate.opsForValue().set(
            sessionKey(sessionId),
            session,
            SESSION_TIMEOUT
        );
        
        return session;
    }
    
    /**
     * Завершення сесії
     */
    public void terminateSession(String sessionId) {
        Session session = (Session) redisTemplate.opsForValue().get(sessionKey(sessionId));
        
        if (session != null) {
            redisTemplate.delete(sessionKey(sessionId));
            redisTemplate.opsForSet().remove(userSessionsKey(session.getUserId()), sessionId);
            
            auditService.logSessionTerminated(session.getUserId(), sessionId, "Вихід користувача");
        }
    }
    
    /**
     * Отримання всіх активних сесій користувача
     */
    public List<Session> getActiveSessions(Long userId) {
        Set<Object> sessionIds = redisTemplate.opsForSet().members(userSessionsKey(userId));
        
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        
        return sessionIds.stream()
            .map(id -> (Session) redisTemplate.opsForValue().get(sessionKey((String) id)))
            .filter(Objects::nonNull)
            .filter(session -> !session.isExpired())
            .collect(Collectors.toList());
    }
    
    private String sessionKey(String sessionId) {
        return "session:" + sessionId;
    }
    
    private String userSessionsKey(Long userId) {
        return "user:sessions:" + userId;
    }
}
```

---

## 7. Моніторинг Безпеки та Аудит

### 7.1 Події Аудиту Автентифікації

| **Подія** | **Серйозність** | **Деталі Логу** | **Сповіщення** |
|-----------|-----------------|-----------------|----------------|
| `LOGIN_SUCCESS` | Info | ID користувача, IP, Пристрій, Час | Ні |
| `LOGIN_FAILED` | Warning | Ім'я користувача, IP, Причина | Після 5 невдач |
| `MFA_SUCCESS` | Info | ID користувача, Метод | Ні |
| `MFA_FAILED` | Warning | ID користувача, Метод, Причина | Після 3 невдач |
| `TOKEN_ISSUED` | Info | ID користувача, ID токена, Закінчення | Ні |
| `TOKEN_REVOKED` | Info | ID користувача, ID токена, Причина | Ні |
| `SESSION_CREATED` | Info | ID користувача, ID сесії, Пристрій | Ні |
| `SESSION_TERMINATED` | Info | ID користувача, ID сесії, Причина | Ні |
| `PERMISSION_DENIED` | Warning | ID користувача, Ресурс, Дія | Так (при повторенні) |
| `PRIVILEGE_ESCALATION` | Critical | ID користувача, З Ролі, До Ролі | Завжди |
| Зміни рядків (`INSERT`/`UPDATE`/`DELETE`, тригери таблиць) | Info | Виконавець = суб'єкт токена доступу, прив'язаний до кожної транзакції запису (`app.current_user_id`), ідентифікатор кореляції, старі й нові значення; доступні для читання за нагородою з `audit:read` (`GET /awards/{id}/audit-trail`, експорт у CSV `…/audit-trail/export` фіксується як `AUDIT_EXPORT`, функція 2.2) | Ні |

### 7.2 Впровадження Аудиту

```java
// Сервіс Аудиту Безпеки
@Service
public class SecurityAuditService {
    
    private final AuditLogRepository auditLogRepository;
    private final AlertService alertService;
    
    @Async
    public void logAuthenticationEvent(AuthenticationEvent event) {
        AuditLog log = AuditLog.builder()
            .userId(event.getUserId())
            .actionType(event.getEventType().name())
            .entityType("AUTHENTICATION")
            .ipAddress(event.getIpAddress())
            .userAgent(event.getUserAgent())
            .correlationId(event.getCorrelationId())
            .newValues(Map.of(
                "success", event.isSuccess(),
                "reason", event.getReason(),
                "method", event.getMethod()
            ))
            .createdAt(Instant.now())
            .build();
        
        auditLogRepository.save(log);
        
        // Перевірка умов для сповіщення
        checkAlertConditions(event);
    }
    
    private void checkAlertConditions(AuthenticationEvent event) {
        if (!event.isSuccess()) {
            // Підрахунок нещодавніх невдач
            long recentFailures = countRecentFailures(event.getIpAddress(), Duration.ofMinutes(5));
            
            if (recentFailures >= 5) {
                alertService.sendSecurityAlert(
                    AlertSeverity.HIGH,
                    "Множинні невдалі автентифікації з IP: " + event.getIpAddress(),
                    Map.of("ip", event.getIpAddress(), "failures", recentFailures)
                );
            }
        }
    }
    
    @Async
    public void logAccessDecision(AccessDecisionEvent event) {
        AuditLog log = AuditLog.builder()
            .userId(event.getUserId())
            .actionType(event.isPermitted() ? "ACCESS_GRANTED" : "ACCESS_DENIED")
            .entityType(event.getResourceType())
            .entityId(event.getResourceId())
            .newValues(Map.of(
                "action", event.getAction(),
                "decision", event.isPermitted() ? "PERMIT" : "DENY",
                "policy", event.getAppliedPolicy()
            ))
            .createdAt(Instant.now())
            .build();
        
        auditLogRepository.save(log);
        
        if (!event.isPermitted()) {
            checkAccessDenialPattern(event);
        }
    }
}
```

---

## 8. Контрольний Список Впровадження

### 8.1 Контрольний Список Автентифікації

| **Компонент** | **Статус** | **Примітки** |
|---------------|------------|--------------|
| OAuth2 Authorization Server | ✅ Спроектовано | Spring Authorization Server |
| Генерація JWT Токенів | ✅ Спроектовано | RS256 підписання |
| Ротація Refresh Токенів | ✅ Спроектовано | Бекенд Redis |
| Чорний Список Токенів | ✅ Спроектовано | На основі TTL Redis |
| TOTP MFA | ✅ Спроектовано | Сумісний з Google Authenticator |
| SMS MFA | ✅ Спроектовано | Запасна опція |
| WebAuthn/FIDO2 | ✅ Спроектовано | Підтримка апаратних ключів |
| Управління Сесіями | ✅ Спроектовано | Бекенд Redis, ліміти конкурентності |

### 8.2 Контрольний Список Авторизації

| **Компонент** | **Статус** | **Примітки** |
|---------------|------------|--------------|
| Впровадження RBAC | ✅ Спроектовано | 7 ролей визначено |
| Матриця Дозволів | ✅ Спроектовано | 15+ дозволів |
| Method Security | ✅ Спроектовано | @PreAuthorize |
| Движок Політик ABAC | ✅ Спроектовано | Організаційна область |
| Безпека на Рівні Ресурсів | ✅ Спроектовано | Перевірки власник/область |
| Журналювання Аудиту | ✅ Спроектовано | Всі auth події |

## 9. Доповнення щодо реалізації (вересень 2026)

Перший інкремент (функція 1.1) визначив такі пункти, у яких реалізація відрізняється від проєкту вище. Розділи
проєкту лишаються довідкою щодо обґрунтування; цей розділ фіксує те, що працює.

| **Тема** | **Проєкт вище** | **Реалізовано** | **Чому** |
|----------|-----------------|-----------------|----------|
| Клієнт браузера | Конфіденційний клієнт із `CLIENT_SECRET_BASIC` (§1.4) | Публічний клієнт `award-web`: код авторизації + PKCE (S256), без секрету, екран згоди вимкнено | Односторінковий застосунок не може зберігати секрет |
| Сторінка входу | Не визначено | Надається сервером авторизації за адресою `/login` (українською за замовчуванням, англійською через `?lang=en` або налаштування браузера, вибір зберігається в cookie); сторінки реєстрації, скидання пароля та профілю містяться в застосунку Angular | Потік коду авторизації вимагає надсилати облікові дані на сервер |
| Токен оновлення | HttpOnly cookie (§1.2) | Повертається у відповіді токена й зберігається SPA в session storage; одноразовий, з ротацією. Повторне пред'явлення вже ротованого токена відкликає всю авторизацію (`RefreshTokenReuseGuard`, хеші ротованих токенів зберігаються в Redis на час життя токена) | OAuth 2.1 дозволяє токени оновлення для публічних клієнтів, якщо вони одноразові; session storage переживає перезавантаження сторінки, не відкриваючи токен іншим вкладкам чи сайтам |
| Зберігання токенів | Redis для активних токенів і чорного списку (§5.1) | Авторизації, зареєстровані клієнти та згоди — у PostgreSQL (таблиці `oauth2_*`, служби JDBC); Redis містить лише короткоживучі лічильники й позначки ротованих токенів | Надійне стандартне зберігання бібліотеки; Redis — для того, що спливає |
| Claims токена доступу | §1.3 | `sub` (id користувача), `email`, `name`, `roles`, `permissions`, `org_id` (рядок), `org_type`; без `mfa_verified` і `session_id` | MFA відкладено; серверна сесія не розкривається API |
| Дозволи | Матриця §3.3 | Та сама матриця, що визначається з поточних призначень ролей у момент видачі токена (`RolePermissions`); не зберігається для кожного користувача | Ролі — джерело істини, дозволи випливають із них |
| Статус облікового запису під час входу | Не визначено | `PENDING`, `INACTIVE`, `SUSPENDED`, `MEMORIAL`, `DELETED` відхиляються до перевірки пароля з повідомленням відповідно до статусу; `RETIRED` може входити | Скінченний автомат облікового запису |
| Вихід | `/logout` | Вихід, ініційований стороною OpenID Connect, на `/connect/logout` з `id_token_hint` після відкликання токена оновлення на `/oauth2/revoke`. Бібліотека відхиляє `id_token_hint`, якщо вихід з усіх пристроїв видалив його авторизацію, і якщо його `sid` вказує на іншу сесію входу того самого користувача (видаючи токен, вона бере найновішу сесію користувача). При такій відмові браузер без сесії входу перенаправляється на `/login`, а браузер, користувач сесії якого є суб'єктом підказки (id-токен, підписаний цим сервером для вебклієнта, перевірений за підписом, видавцем, аудиторією та `email`), отримує завершення сесії й перенаправлення на `/login`. Будь-яка інша відмова — без підказки, чужа підказка, сторонній клієнт чи адреса перенаправлення — зберігає відповідь 400, тож підроблене посилання нікого не виводить із системи | Стандартні кінцеві точки бібліотеки |
| Ключ підпису | Ротація ключа кожні 90 днів (§5.1) | Один ключ RSA з конфігурації (`AUTH_JWK_*`); генерується під час запуску лише поза виробничим профілем, який без налаштованого ключа не запускається (профіль `local` зберігає згенерований ключ у `~/.award-monitoring/dev-jwk.json`, `AUTH_JWK_DEV_KEY_FILE`, тож перезапущений сервер розробки й далі приймає свої токени); застосунок у браузері відповідає на 401 одним оновленням і повтором запиту, перш ніж вийти; ротація — процедура розгортання, поки що не автоматизована | Достатньо для першого інкременту |
| Токен доступу й id-токен на API | Не визначено | Токени доступу містять `token_use=access`; сервер ресурсів приймає лише їх, перевіряє видавця й відхиляє id-токени, підписані тим самим ключем | Id-токен — доказ ідентичності для SPA, а не облікові дані для API |
| Оновлення при заблокованому обліковому записі | Не визначено | Видача за токеном оновлення повторно читає статус облікового запису; `INACTIVE`, `SUSPENDED`, `MEMORIAL`, `DELETED` або відсутній обліковий запис відкликає авторизацію й дає відповідь `invalid_grant`; уже видані токени доступу дійсні, доки не спливуть (≤ 15 хв) | Призупинення має завершувати сесію протягом одного терміну життя токена доступу |
| Повідомлення про статус під час входу | Не визначено | Статус облікового запису перевіряється лише після збігу пароля, тож хибний пароль для призупиненого чи непідтвердженого облікового запису дає загальне повідомлення; невідомі коди помилок показують загальне повідомлення | Без облікових даних статус не розкривається |
| Скидання пароля | §6 скасування сесій при зміні пароля | `POST /api/v1/auth/password-reset/request` відповідає 202 для кожної адреси й надсилає 1-годинне одноразове посилання лише обліковим записам `ACTIVE` (одне на хвилину на адресу); `confirm` повторно хешує пароль, видаляє всі рядки `oauth2_authorization` суб'єкта й завершує його сесії входу сервера авторизації (`SessionRegistry` у пам'яті; `ConcurrentSessionFilter`, розміщений перед кінцевою точкою авторизації, анулює завершену сесію й повторює запит, який потрапляє на сторінку входу), тож оновлення одразу припиняється, браузер із сесією не може отримати новий код без нового пароля, а токени доступу, видані до скидання, відхиляються негайно (див. «Вихід з усіх пристроїв»). Реєстр живе в процесі; розгортання з кількома екземплярами потребує Spring Session на Redis. Запити для відомих адрес виконують один додатковий `INSERT` до відповіді 202 — різниця в часі на рівні мілісекунд, поки що прийнята | Адреси не розкриваються; викрадена сесія не має переживати скидання |
| Зміна адреси для входу | Не визначено | `POST /api/v1/users/me/email-change` вимагає поточного пароля; хибний дає 403 і зараховується як невдалий вхід, а невдача, що блокує обліковий запис, також виводить його з усіх пристроїв (блокування й відкликання переживають відмову). Інституційна адреса, що відрізняється від поточної й не належить іншому обліковому запису, отримує 1-годинне посилання `EMAIL_CHANGE` на нову адресу (один запит на обліковий запис на хвилину; новий запит скасовує старіші посилання), а на поточну адресу надходить попередження з посиланням на скидання пароля. Скидання пароля, відкликання «це був не я» та блокування (з діалогу адреси чи з форми входу) скасовують усі незавершені зміни, тож власник, який відновлює обліковий запис, також зупиняє перенаправлення зловмисника. Публічна `POST /api/v1/auth/email-change/confirm` переносить обліковий запис (унікальний індекс на `LOWER(email_address)` захищає від гонки з новою реєстрацією: 409, адреса без змін), виводить користувача з усіх пристроїв під старим іменем суб'єкта, скасовує невикористані посилання скидання, прив'язує невикористані посилання «це був не я» до старої адреси й надсилає на стару адресу лист із новою разом з ще одним таким посиланням (24 години); використання прив'язаного посилання повертає обліковий запис на стару адресу, завершує сесії під обома іменами, надсилає посилання скидання на відновлену адресу й повідомляє замінену адресу; якщо стару адресу на той час має інший обліковий запис (зокрема нова реєстрація), нічого не відновлюється й посилання скидання нікуди не надсилається, бо поточна адреса може належати зловмиснику, а відмова записується в аудит для адміністратора; скидання пароля, повторна зміна чи посилання «це був не я» нового пристрою не скасовують прив'язаних посилань. Прийнятий ризик: протягом 24 годин після зміни той, хто читає стару скриньку, може повернути обліковий запис; замінену адресу повідомляють одразу, а адміністратор може скасувати повернення (2.1.8 (SCRUM-43)); поки поточна адреса заблокована, посилання дає 410, а новий запит — 423 `account-locked` без перевірки пароля. Відповідь називає id облікового запису, тож браузер, у якому виконано вхід від імені іншої особи, зберігає свою сесію. Ролі, делегування, нагороди й пристрої слідують за обліковим записом, бо посилаються на `user_id`. Фіксується в аудиті як `EMAIL_CHANGE_REQUESTED` і `EMAIL_CHANGED` (тип сутності `USER`) | Адреса є суб'єктом і каналом відновлення: повторна автентифікація не дає позиченій сесії перенаправити обліковий запис, нова скринька доводить володіння, а завершення всіх сесій прибирає токени зі старим іменем |
| Підтвердження адреси | Лише посилання | Сторінка підтвердження також вимагає пароля реєстрації; невідповідність дає 403, а токен лишається придатним | Не дає третій особі зареєструвати адресу колеги, щоб колега активував пароль зловмисника |
| Підбір пароля | §7.1 сповіщення після 5 невдач | Невдачі рахуються для кожної адреси в Redis (15-хвилинне вікно); п'ята блокує обліковий запис на 30 хвилин (`LockedException` до перевірки пароля, тож правильні облікові дані блокування не знімають), фіксує `ACCOUNT_LOCKED` в аудиті й надсилає лист кожному чинному `SYSTEM_ADMIN`. Невідомі адреси блокуються так само (без розкриття через повідомлення), але без запису аудиту й листа. Кінцеві точки автентифікації (`/oauth2/token`, `/login`, `/api/v1/auth/**`) допускають 20 запитів на хвилину з однієї клієнтської адреси (фіксоване вікно в Redis, попередні запити `OPTIONS` не враховуються, 429 з `Retry-After`); адреса — це співрозмовник сокета або клієнт із `X-Forwarded-For`, якщо співрозмовник є довіреним проксі (remote-IP valve Tomcat). Redis недоступний: команди одразу відхиляються, нічого не рахується й не обмежується, щохвилинні обмеження листів реєстрації та скидання пропускають запит, помилка записується в журнал | Блокування й вікна переживають перезапуски; вхід не має залежати від Redis |
| Новий пристрій | `SESSION_CREATED` з пристроєм (§7.1) | Кожен вхід через форму зіставляється з `user_devices` за відбитком SHA-256 родини браузера, родини операційної системи (`uap-java`) та `Accept-Language`; відомий пристрій лише оновлює `last_used_at` і `last_ip_address`. Невідомий зберігається, і про нього повідомляється листом (браузер, ОС, IP, час) з 24-годинним одноразовим посиланням `SECURITY_REVOKE` на `/security/not-me`. `POST /api/v1/auth/security/revoke` тоді видаляє рядки `oauth2_authorization` користувача, завершує сесії входу, скасовує інші невикористані посилання «це був не я» користувача (посилання нового пристрою та скидання пароля скасовують лише ті, що не прив'язані до попередньої адреси), забуває всі пристрої, замінює хеш пароля випадковим (старий пароль перестає працювати; обліковий запис лишається `ACTIVE`) і надсилає 1-годинне посилання для скидання; уже видані токени доступу відхиляються негайно (див. «Вихід з усіх пристроїв») | Версії змінюються з кожним оновленням браузера, тож родини тримають відбиток стабільним; токен не називає пристрій, а після підозри на компрометацію кожен браузер має знову себе оголосити. Облік пристроїв виконується у власній транзакції, і збій там записується в журнал, а не подається як невдалий вхід |
| Події аудиту | Каталог §7.1 | `LOGIN_SUCCESS`, `LOGIN_FAILED` (причина), `ACCOUNT_LOCKED`, `LOGOUT` (завершення сесії), `EMAIL_VERIFIED`, `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET`, `SECURITY_REVOKE` (авторизації та пристрої видалено) в `audit_logs` з id користувача, IP, user agent та ідентифікатором кореляції запиту (`X-Correlation-Id`); `MFA_*`, `TOKEN_*`, `SESSION_*` не записуються (MFA відкладено; токени — це рядки бібліотеки) | Те, що потрібно для розділу безпеки дипломної роботи |
| Значення токенів у сховищі | Чорний список у Redis (§5.1) | Бібліотека зберігає коди авторизації, значення токенів доступу, id-токенів і токенів оновлення відкритим текстом в `oauth2_authorization`; хешуються лише одноразові токени з листів. Хешування збережених токенів оновлення — пункт беклогу | Стандартне зберігання бібліотеки; база даних не відкрита назовні |
| Вихід з усіх пристроїв | Чорний список токенів доступу в Redis (§5.1) | Скидання пароля, відкликання «це був не я» та зміна адреси записують момент у мілісекундах епохи в Redis (`auth:nbf:<user id>`, зберігається на час життя токена доступу); токени доступу містять момент видачі в мілісекундах (`iat_ms`, бо `iat` має точність до секунди), а сервер ресурсів відхиляє будь-який токен, виданий не пізніше цього моменту (токен без `iat_ms` відхиляється протягом усієї секунди), тож вхід одразу після відкликання працює, а вже видані токени перестають діяти негайно, а не протягом 15 хвилин; ключ записується після фіксації транзакції відкликання й живе протягом часу життя токена плюс 60-секундний допуск `exp`. Відкликання ролей і делегувань (функція 1.2) записують той самий ключ. Без Redis перевірка пропускається й записується в журнал: відкликання все одно видаляє авторизації, тож новий токен не видається, але вже видані токени доступу придатні до завершення строку (щонайбільше 15 хвилин) — те саме вікно, що й для призупинення. В іншому токени без стану: чорного списку окремих токенів немає | Один `GET` на виклик API дешевий і стосується лише токенів самого користувача; чорний список окремих токенів не охопив би токени, які сервер не може перелічити. Резервний варіант у PostgreSQL додав би запит до кожного виклику, щоб закрити вікно, яке існує лише під час збою Redis |
| Політика паролів | §2 (довжина, список зламаних) | 10–72 байти, не в списку поширених паролів, і новий пароль має відрізнятися від поточного під час скидання (`password-same-as-current`); історія паролів не зберігається. Відхилене скидання залишає посилання придатним, тож власник посилання міг би перевіряти кандидатів на збіг із поточним паролем у межах обмеження запитів; прийняте посилання одноразове, короткоживуче й надсилається лише власникові облікового запису | Повторне використання щойно скинутого пароля знецінює скидання; таблиця історії зберігала б старі хеші заради малої користі |
| Обхідні шляхи сторінки входу | Не визначено | `/` і `/login`, відкриті браузером із виконаним входом, перенаправляють до застосунку; вхід без незавершеного запиту авторизації веде до застосунку; форма входу, надіслана із застарілим токеном CSRF (перезапуск сервера, завершення сесії), повертається до форми з повідомленням «сторінка застаріла»; 403 і помилки сервера показують фірмову сторінку з посиланням на застосунок | Сервер авторизації доступний напряму й ніколи не має заводити в глухий кут 404 чи стандартної сторінки помилки |
| Область підрозділу | Механізм політик ABAC з атрибутами кафедри/факультету (§4) | Кожне призначення ролі прив'язане до підрозділу; токен доступу містить `role_scopes` (`ROLE:orgId`), а сервер ресурсів тримає дерево підрозділів у пам'яті (перезавантажується за таймером і після змін). Вирази `@PreAuthorize` викликають `@access.require(permission…)`, `@access.inScope(orgId)`, `@access.below(role)` і `@access.canManage(role, orgId)`, а сервіси — `canReadAwards(orgId)`; лінія підпорядкування — `EMPLOYEE < FACULTY_SECRETARY < DEAN < RECTOR_SECRETARY < RECTOR`, `SYSTEM_ADMIN` стоїть над усім і один надає системні ролі, `GDPR_OFFICER` нічого не надає. `user:read:scope` і `user:manage:scope` доповнюють матрицю для чотирьох ролей затвердження; довідник обмежено областями ролей, що надають `user:read:scope`, тож роль `EMPLOYEE` в іншому підрозділі його не розширює. Календарні дні — київські (годинник застосунку) | До епіку 4 жодному споживачеві не потрібні умови ризику чи часу доби; один пошук у мапі на перевірку й невеликий токен кращі за механізм політик і запити на кожен виклик |
| Призначення ролей | Не визначено | `POST /api/v1/users/{id}/roles` і `DELETE /api/v1/users/{id}/roles/{roleId}` вимагають `user:manage` (будь-що, будь-де) або `user:manage:scope` (роль, яку надає власна роль виконавця, у підрозділі всередині піддерева цієї ролі: секретар факультету й секретар ректора надають `EMPLOYEE`, декан — `FACULTY_SECRETARY` і `EMPLOYEE`, ректор — `RECTOR_SECRETARY` і `DEAN`; 2.1.8 (SCRUM-43), `RoleLevels`); підрозділ має відповідати рівню ролі в дереві. Відкликання ніколи не видаляє: `valid_to` стає вчорашньою датою, а колишнього власника виводять з усіх пристроїв, тож втрачені повноваження перестають діяти негайно, тоді як надана роль потрапляє в токен під час наступного тихого оновлення. Ніхто не може забрати в себе останню роль вище `EMPLOYEE`. Обидві дії записують `ROLE_ASSIGNED`/`ROLE_REVOKED` в `audit_logs` (тип сутності `AUTHORIZATION`, у тій самій транзакції, що й зміна) і надсилають лист особі, повноваження якої змінилися | Правило узгоджує US-002, матрицю RBAC і §3.3 без процесу затвердження; збережена історія дає відповідь на питання «хто міг затвердити це в березні» в епіку 4 |
| Підтвердження членства | Не визначено | Самореєстрація не надає ролі: адреса `@chnu.edu.ua` доводить працевлаштування в університеті, а не належність до обраної кафедри, тож першу роль надає той, хто може керувати цією кафедрою. `GET /users/me` повідомляє `membershipConfirmed`; доки це не так, токен не містить ролей і дозволів, а застосунок показує банер. Відповідь дає стратегія `MembershipConfirmation`, єдина реалізація якої покладається на людину — точка розширення для майбутньої перевірки через LDAP чи кадрову систему. Той самий запит переводить користувача, який уже мав роль, до іншої кафедри лише тоді, коли називає кафедру, з якої його переводять (`fromOrganizationId`), тож застаріле підтвердження дає 409, а не скасовує виправлення; перехід забирає делегування, отримані для підрозділів, у яких користувач більше не має чинної ролі (правило, що робило їх допустимими). Кожен шлях, що завершує делегування, блокує їхні рядки, перш ніж когось виводити з системи, а створення делегування блокує рядок делегата, тож перехід, відкликання й нове делегування для однієї особи ніколи не перемежовуються. Видалені облікові записи, як і непідтверджені, не показуються в списку й не читаються за id. Обліковий запис `PENDING`, найновіше посилання підтвердження якого спливло, замінюється наступною реєстрацією на цю адресу | Без цього будь-хто з університетською адресою міг би діяти як член будь-якої кафедри |
| Делегування затвердження | Не визначено | Роль затвердження (`FACULTY_SECRETARY`, `DEAN`, `RECTOR_SECRETARY`, `RECTOR`) позичається колезі щонайбільше на 90 днів через `POST /api/v1/delegations`; `role_delegations` тримає її поза історією ролей. Токен доступу делегата отримує `delegations` (`ROLE:orgId:delegatorId`), відповідний запис `role_scopes` і лише дозволи ролі `award:read:*` та `award:approve:*`. Оскільки позичена роль не має давати влади над людьми, токен також містить `held_scopes` — області ролей, які особа має сама, — і `@access.canManage`, `@access.below` та область довідника читають саме їх, тоді як `@access.inScope` приймає й позичені; тож делегат може затверджувати всередині підрозділу, але не може ні керувати його користувачами, ні позичати роль далі, а отримання делегування ролі, яку особа вже має, не забирає власної. Делегування читається в токен лише доки делегант ще має позичену роль, тож воно ніколи не переживає повноважень, з яких виникло. Делегування спливає за календарною датою без жодного фонового завдання; `DELETE /api/v1/delegations/{id}` від делеганта, власника `user:manage` чи будь-кого, хто міг би забрати роль делеганта, встановлює `revoked_at` і виводить делегата з усіх пристроїв, тоді як будь-хто інший отримує 404, тож кінцева точка не розкриває ні делегування, ні його стану, а відкликання основної ролі забирає позичене нею в тій самій транзакції. Обидві дії записують `DELEGATION_CREATED`/`DELEGATION_REVOKED` в `audit_logs` і надсилають лист делегатові (і делегантові, якщо делегування завершив хтось інший) | US-002 вимагає тимчасових повноважень у межах діапазону дат; зберігання поза `user_roles` тримає історію ролей чесною й робить відстежуваною позначку «делеговано» епіку 4 |
| Доступ до нагород (епік 2) | `AwardSecurityService` з `@PostAuthorize` (§3.4) | Контролери перевіряють лише дозволи: `@access.require('award:create')`, `'award:update:own'`, `'award:read:own'` (має кожна роль) і `'audit:read'`. Сервіс вирішує, чи можна читати (`AwardOwnership.isReadable`): власник завжди читає нагороду, зокрема чернетки; будь-хто інший читає подану нагороду лише тоді, коли її `organization_id` охоплює роль із `award:read:department`, `award:read:faculty` чи `award:read:all` (власна чи делегована), або токен містить `award:read:all`. Недоступна нагорода дає 404, ніколи 403. Редагування, подання й видалення потребують власної чернетки: чужа нагорода дає 404, власна подана — 409 `award-not-editable`. Те саме правило діє для кінцевих точок статусу, версій і журналу аудиту | 404 не підтверджує чужі id; нагорода зберігає підрозділ свого подання, тож читання в межах області не залежить від пізнішого переведення власника |
| Відхилені запити | Не визначено | Кожна відмова автентифікованому виконавцю — правила запитів і безпека методів — публікується як подія авторизації й записується в `audit_logs` як `ACCESS_DENIED` (тип сутності `AUTHORIZATION`, власна транзакція, збій записується в журнал, а не подається назовні) з методом, шляхом, відсутнім дозволом, повноваженням чи областю, IP та ідентифікатором кореляції; тіло відповіді 403 — `urn:awards:problem:access-denied` з тими самими деталями. Пошук у довіднику поза областю виконавця дає 404, тож чужі id не підтверджуються | Один слухач охоплює кінцеві точки, додані пізніше; типізоване тіло дає застосунку в браузері пояснити відмову |

---

## Пов'язані Документи

- **Етап 10**: [Архітектура Безпеки](./SECURITY_ARCHITECTURE_ua.md)
- **Етап 10**: [Модель Загроз](./THREAT_MODEL_ua.md)
- **Етап 10**: [Privacy by Design](./PRIVACY_BY_DESIGN_ua.md)
- **Етап 7**: [ADR-009 Фреймворк Безпеки](../architecture/adr/ADR-009-Security-Framework-ua.md)
- **Етап 2**: [Матриця RBAC](../stakeholders/RBAC_matrix_ua.md)

---

*Версія Документа: 1.0*  
*Класифікація: Внутрішній*  
*Етап: 10 - Архітектура Безпеки та Проектування Конфіденційності*  
*Автор: Стефан Костик*


# Authentication & Authorization Design
## Award Monitoring & Tracking System

> **Phase 10 Deliverable**: Security Architecture & Privacy Design  
> **Document Version**: 1.0  
> **Last Updated**: December 2025  
> **Author**: Stefan Kostyk  
> **Security Model**: OAuth2 + JWT + RBAC  
> **Classification**: Internal

---

## Executive Summary

This document defines the comprehensive authentication and authorization architecture for the Award Monitoring & Tracking System. The design implements OAuth2 with JWT tokens for stateless authentication and a hybrid RBAC/ABAC model for fine-grained authorization, ensuring secure access control across all system components.

### Key Security Features

| **Feature** | **Technology** | **Purpose** |
|-------------|----------------|-------------|
| **Authentication** | OAuth2 + JWT | Secure, stateless identity verification |
| **Authorization** | RBAC + ABAC | Fine-grained access control |
| **MFA** | TOTP + SMS + Email | Enhanced authentication security |
| **Session Management** | Stateless JWT + Redis | Scalable session handling |
| **Token Security** | RS256 signing + rotation | Cryptographic token protection |

---

## 1. Authentication Architecture

### 1.1 OAuth2 Flow Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         OAUTH2 AUTHENTICATION FLOW                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  User                  Frontend              Auth Server         API Server │
│   │                       │                       │                    │    │
│   │  1. Login Request     │                       │                    │    │
│   │──────────────────────▶│                       │                    │    │
│   │                       │                       │                    │    │
│   │                       │  2. Auth Request      │                    │    │
│   │                       │──────────────────────▶│                    │    │
│   │                       │                       │                    │    │
│   │                       │  3. Login Page        │                    │    │
│   │◀─────────────────────────────────────────────│                    │    │
│   │                       │                       │                    │    │
│   │  4. Credentials       │                       │                    │    │
│   │──────────────────────────────────────────────▶│                    │    │
│   │                       │                       │                    │    │
│   │                       │  5. MFA Challenge     │                    │    │
│   │◀─────────────────────────────────────────────│                    │    │
│   │                       │                       │                    │    │
│   │  6. MFA Response      │                       │                    │    │
│   │──────────────────────────────────────────────▶│                    │    │
│   │                       │                       │                    │    │
│   │                       │  7. Tokens (JWT)      │                    │    │
│   │◀─────────────────────────────────────────────│                    │    │
│   │                       │                       │                    │    │
│   │                       │  8. API Request + JWT │                    │    │
│   │                       │───────────────────────────────────────────▶│    │
│   │                       │                       │                    │    │
│   │                       │  9. Response          │                    │    │
│   │◀──────────────────────────────────────────────────────────────────│    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Token Architecture

| **Token Type** | **Purpose** | **Lifetime** | **Storage** | **Refresh** |
|----------------|-------------|--------------|-------------|-------------|
| **Access Token** | API authentication | 15 minutes | Memory only | Via refresh token |
| **Refresh Token** | Obtain new access tokens | 7 days | HttpOnly cookie | Via re-authentication |
| **ID Token** | User identity claims | 1 hour | Memory only | Not refreshable |

### 1.3 JWT Structure

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

### 1.4 Authentication Implementation

```java
// OAuth2 Authorization Server Configuration
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

// Resource Server Configuration
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
            
            // Add role-based authorities
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(role -> authorities.add(
                    new SimpleGrantedAuthority("ROLE_" + role)));
            }
            
            // Add permission-based authorities
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

## 2. Multi-Factor Authentication (MFA)

### 2.1 MFA Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        MFA STRATEGY MATRIX                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Risk Level Assessment                        MFA Requirement               │
│                                                                             │
│  ┌─────────────────────────────────────┐     ┌─────────────────────────┐   │
│  │ LOW RISK                            │     │ Optional                │   │
│  │ - Known device                      │────▶│ - Email verification   │   │
│  │ - Normal location                   │     │   available             │   │
│  │ - Normal time                       │     └─────────────────────────┘   │
│  └─────────────────────────────────────┘                                   │
│                                                                             │
│  ┌─────────────────────────────────────┐     ┌─────────────────────────┐   │
│  │ MEDIUM RISK                         │     │ Required (Choice)       │   │
│  │ - New device                        │────▶│ - TOTP                  │   │
│  │ - Different location                │     │ - SMS                   │   │
│  │ - Unusual time                      │     │ - Email                 │   │
│  └─────────────────────────────────────┘     └─────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────┐     ┌─────────────────────────┐   │
│  │ HIGH RISK                           │     │ Required (Strong)       │   │
│  │ - Admin access                      │────▶│ - TOTP mandatory        │   │
│  │ - Sensitive operation               │     │ - Hardware key option   │   │
│  │ - Multiple failed attempts          │     └─────────────────────────┘   │
│  └─────────────────────────────────────┘                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 MFA Methods

| **Method** | **Security Level** | **User Experience** | **Recovery** | **Use Case** |
|------------|-------------------|---------------------|--------------|--------------|
| **TOTP** (Authenticator App) | High | Medium | Backup codes | Default for all users |
| **SMS** | Medium | High | Phone verification | Fallback option |
| **Email** | Medium | High | Email verification | Low-risk scenarios |
| **Hardware Key** (WebAuthn) | Very High | Medium | Admin recovery | Admin accounts |

### 2.3 MFA Implementation

```java
// MFA Service
@Service
public class MfaService {
    
    private final TotpService totpService;
    private final SmsService smsService;
    private final EmailService emailService;
    private final WebAuthnService webAuthnService;
    private final RiskAssessmentService riskService;
    
    /**
     * Determine if MFA is required based on risk assessment
     */
    public MfaRequirement assessMfaRequirement(User user, AuthenticationContext context) {
        RiskLevel risk = riskService.assessLoginRisk(user, context);
        
        // Admin always requires MFA
        if (user.hasRole(RoleType.SYSTEM_ADMIN) || user.hasRole(RoleType.RECTOR)) {
            return MfaRequirement.required(MfaStrength.HIGH);
        }
        
        return switch (risk) {
            case LOW -> MfaRequirement.optional();
            case MEDIUM -> MfaRequirement.required(MfaStrength.STANDARD);
            case HIGH -> MfaRequirement.required(MfaStrength.HIGH);
            case CRITICAL -> MfaRequirement.blocked(); // Block and notify
        };
    }
    
    /**
     * Initialize MFA challenge based on user preference
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
            throw new MfaNotConfiguredException("TOTP not configured for user");
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
        
        // Store challenge
        MfaChallenge challenge = MfaChallenge.builder()
            .challengeId(UUID.randomUUID().toString())
            .userId(user.getId())
            .method(MfaMethod.SMS)
            .code(passwordEncoder.encode(code))
            .expiresAt(Instant.now().plus(Duration.ofMinutes(5)))
            .metadata(Map.of("masked_phone", maskedPhone))
            .build();
        
        challengeRepository.save(challenge);
        
        // Send SMS
        smsService.sendVerificationCode(user.getPhoneNumber(), code);
        
        return challenge.withoutCode(); // Don't expose code
    }
    
    /**
     * Verify MFA response
     */
    public MfaVerificationResult verifyMfa(String challengeId, String response) {
        MfaChallenge challenge = challengeRepository.findById(challengeId)
            .orElseThrow(() -> new InvalidChallengeException("Challenge not found"));
        
        if (challenge.isExpired()) {
            auditService.logMfaFailure(challenge.getUserId(), "Challenge expired");
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
            auditService.logMfaFailure(challenge.getUserId(), "Invalid code");
            incrementFailedAttempts(challenge);
            return MfaVerificationResult.failed();
        }
    }
}

// TOTP Service
@Service
public class TotpService {
    
    private static final int CODE_DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int ALLOWED_TIME_SKEW = 1; // Allow 1 step before/after
    
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
        
        // Check current and adjacent time steps for clock skew
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
            throw new RuntimeException("TOTP generation failed", e);
        }
    }
}
```

---

## 3. Role-Based Access Control (RBAC)

### 3.1 Role Hierarchy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ROLE HIERARCHY                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                        ┌─────────────────┐                                  │
│                        │   SYSTEM_ADMIN  │                                  │
│                        │  (Full Access)  │                                  │
│                        └────────┬────────┘                                  │
│                                 │                                           │
│                    ┌────────────┴────────────┐                              │
│                    │                         │                              │
│            ┌───────┴───────┐         ┌───────┴───────┐                     │
│            │    RECTOR     │         │  GDPR_OFFICER │                     │
│            │ (University)  │         │ (Compliance)  │                     │
│            └───────┬───────┘         └───────────────┘                     │
│                    │                                                        │
│            ┌───────┴───────┐                                               │
│            │RECTOR_SECRETARY│                                               │
│            │(Rector Office) │                                               │
│            └───────┬───────┘                                               │
│                    │                                                        │
│            ┌───────┴───────┐                                               │
│            │     DEAN      │                                               │
│            │  (Faculty)    │                                               │
│            └───────┬───────┘                                               │
│                    │                                                        │
│          ┌─────────┴─────────┐                                             │
│          │ FACULTY_SECRETARY │                                             │
│          │   (Department)    │                                             │
│          └─────────┬─────────┘                                             │
│                    │                                                        │
│            ┌───────┴───────┐                                               │
│            │   EMPLOYEE    │                                               │
│            │   (Basic)     │                                               │
│            └───────────────┘                                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Role Definitions

| **Role** | **Scope** | **Description** | **Typical Users** |
|----------|-----------|-----------------|-------------------|
| `EMPLOYEE` | Personal | Basic system access | All employees |
| `FACULTY_SECRETARY` | Department | First-level approval | Administrative staff |
| `DEAN` | Faculty | Faculty-level approval | Faculty deans |
| `RECTOR_SECRETARY` | University | High-level processing | Rector's office |
| `RECTOR` | University | Final approval authority | University rector |
| `SYSTEM_ADMIN` | System | Technical administration | IT administrators |
| `GDPR_OFFICER` | Compliance | Data protection oversight | DPO role |

### 3.3 Permission Matrix

| **Permission** | **EMPLOYEE** | **FAC_SEC** | **DEAN** | **RECT_SEC** | **RECTOR** | **ADMIN** | **GDPR** |
|----------------|:------------:|:-----------:|:--------:|:------------:|:----------:|:---------:|:--------:|
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
| `user:manage` | - | - | - | - | - | ✅ | - |
| `system:configure` | - | - | - | - | - | ✅ | - |
| `data:export` | - | - | - | - | - | ✅ | ✅ |
| `consent:manage` | - | - | - | - | - | - | ✅ |
| `audit:read` | - | - | - | - | - | ✅ | ✅ |

### 3.4 RBAC Implementation

```java
// Permission Constants
public final class Permissions {
    // Award permissions
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
    
    // User permissions
    public static final String USER_READ_ALL = "user:read:all";
    public static final String USER_MANAGE = "user:manage";
    
    // System permissions
    public static final String SYSTEM_CONFIGURE = "system:configure";
    public static final String DATA_EXPORT = "data:export";
    public static final String CONSENT_MANAGE = "consent:manage";
    public static final String AUDIT_READ = "audit:read";
}

// Role-Permission Mapping Service
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
        // ... more roles
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

// Method Security with RBAC
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
            .orElseThrow(() -> new NotFoundException("Award not found"));
    }
    
    @PreAuthorize("@awardSecurityService.canApproveAtLevel(#request.awardId, authentication)")
    public ApprovalResult approveAward(ApprovalRequest request) {
        // Approval logic
    }
}

// Custom Security Service for Complex Authorization
@Service
public class AwardSecurityService {
    
    public boolean canAccessAward(Award award, Authentication authentication) {
        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
        
        // Owner can always access
        if (award.getUserId().equals(principal.getUserId())) {
            return true;
        }
        
        // Check organizational scope
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

## 4. Attribute-Based Access Control (ABAC)

### 4.1 ABAC Policy Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          ABAC POLICY MODEL                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Subject Attributes          Resource Attributes       Environmental        │
│  (Who is requesting)         (What is requested)       Attributes           │
│                                                        (Context)            │
│  ┌─────────────────┐        ┌─────────────────┐       ┌─────────────────┐  │
│  │ - User ID       │        │ - Resource Type │       │ - Time of Day   │  │
│  │ - Roles         │        │ - Owner ID      │       │ - IP Address    │  │
│  │ - Department    │        │ - Department    │       │ - Device Type   │  │
│  │ - Faculty       │        │ - Status        │       │ - Location      │  │
│  │ - Clearance     │        │ - Sensitivity   │       │ - Risk Level    │  │
│  └────────┬────────┘        └────────┬────────┘       └────────┬────────┘  │
│           │                          │                         │           │
│           └──────────────────────────┼─────────────────────────┘           │
│                                      │                                      │
│                                      ▼                                      │
│                          ┌─────────────────────┐                           │
│                          │   POLICY ENGINE     │                           │
│                          │                     │                           │
│                          │  IF subject.role    │                           │
│                          │     IN [DEAN]       │                           │
│                          │  AND resource.dept  │                           │
│                          │     IN subject.depts│                           │
│                          │  AND env.risk       │                           │
│                          │     < HIGH          │                           │
│                          │  THEN PERMIT        │                           │
│                          │                     │                           │
│                          └──────────┬──────────┘                           │
│                                     │                                      │
│                                     ▼                                      │
│                          ┌─────────────────────┐                           │
│                          │  PERMIT / DENY      │                           │
│                          └─────────────────────┘                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 ABAC Policy Examples

| **Policy** | **Subject** | **Resource** | **Action** | **Environment** | **Effect** |
|------------|-------------|--------------|------------|-----------------|------------|
| Award Owner Access | user.id = resource.owner_id | Award | READ, UPDATE | Any | PERMIT |
| Department Award View | user.dept ∈ resource.depts | Award | READ | Any | PERMIT |
| Faculty Approval | user.role = DEAN AND user.faculty = resource.faculty | Award | APPROVE | risk < HIGH | PERMIT |
| Off-Hours Restriction | user.role ≠ ADMIN | System Config | * | time ∉ [9:00-18:00] | DENY |
| High-Risk Block | Any | Sensitive Data | * | risk = CRITICAL | DENY |

### 4.3 ABAC Implementation

```java
// ABAC Policy Engine
@Service
public class AbacPolicyEngine {
    
    private final List<AbacPolicy> policies;
    
    /**
     * Evaluate access request against all applicable policies
     */
    public AccessDecision evaluate(AccessRequest request) {
        SubjectAttributes subject = extractSubjectAttributes(request);
        ResourceAttributes resource = extractResourceAttributes(request);
        EnvironmentAttributes environment = extractEnvironmentAttributes(request);
        
        // Find applicable policies
        List<AbacPolicy> applicablePolicies = policies.stream()
            .filter(policy -> policy.appliesTo(subject, resource, request.getAction()))
            .collect(Collectors.toList());
        
        // Evaluate policies (deny-overrides combining algorithm)
        for (AbacPolicy policy : applicablePolicies) {
            PolicyResult result = policy.evaluate(subject, resource, environment);
            
            if (result == PolicyResult.DENY) {
                auditService.logAccessDenied(request, policy.getName());
                return AccessDecision.deny(policy.getDenyReason());
            }
        }
        
        // Check if any policy permitted
        boolean anyPermit = applicablePolicies.stream()
            .anyMatch(p -> p.evaluate(subject, resource, environment) == PolicyResult.PERMIT);
        
        if (anyPermit) {
            auditService.logAccessPermitted(request);
            return AccessDecision.permit();
        }
        
        // Default deny
        return AccessDecision.deny("No applicable permit policy");
    }
}

// ABAC Policy Definition
public interface AbacPolicy {
    String getName();
    boolean appliesTo(SubjectAttributes subject, ResourceAttributes resource, String action);
    PolicyResult evaluate(SubjectAttributes subject, ResourceAttributes resource, 
                         EnvironmentAttributes environment);
    String getDenyReason();
}

// Example: Organizational Scope Policy
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
        // System admins can access all
        if (subject.hasRole("SYSTEM_ADMIN")) {
            return PolicyResult.PERMIT;
        }
        
        // Owner can always access
        if (subject.getUserId().equals(resource.getOwnerId())) {
            return PolicyResult.PERMIT;
        }
        
        // Check organizational hierarchy
        OrgHierarchy subjectOrg = subject.getOrganizationHierarchy();
        OrgHierarchy resourceOrg = resource.getOrganizationHierarchy();
        
        // Dean can access faculty awards
        if (subject.hasRole("DEAN") && subjectOrg.getFacultyId().equals(resourceOrg.getFacultyId())) {
            return PolicyResult.PERMIT;
        }
        
        // Faculty Secretary can access department awards
        if (subject.hasRole("FACULTY_SECRETARY") && 
            subjectOrg.getDepartmentId().equals(resourceOrg.getDepartmentId())) {
            return PolicyResult.PERMIT;
        }
        
        return PolicyResult.NOT_APPLICABLE;
    }
    
    @Override
    public String getDenyReason() {
        return "User does not have organizational access to this resource";
    }
}

// Risk-Based Access Policy
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
        // Block high-risk access to sensitive resources
        if (environment.getRiskLevel() == RiskLevel.CRITICAL) {
            return PolicyResult.DENY;
        }
        
        // Require MFA verification for high risk
        if (environment.getRiskLevel() == RiskLevel.HIGH && !subject.isMfaVerified()) {
            return PolicyResult.DENY;
        }
        
        return PolicyResult.PERMIT;
    }
    
    @Override
    public String getDenyReason() {
        return "Access denied due to elevated risk level";
    }
}
```

---

## 5. Token Security & Management

### 5.1 Token Lifecycle

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          TOKEN LIFECYCLE                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐  │
│  │ Issue   │───▶│ Active  │───▶│ Refresh │───▶│ Rotate  │───▶│ Revoke  │  │
│  └─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘  │
│                      │              │              │              │         │
│                      │              │              │              │         │
│                      ▼              ▼              ▼              ▼         │
│               ┌───────────────────────────────────────────────────────┐    │
│               │                    REDIS CACHE                         │    │
│               │  - Active tokens                                       │    │
│               │  - Blacklisted tokens                                  │    │
│               │  - Session metadata                                    │    │
│               └───────────────────────────────────────────────────────┘    │
│                                                                             │
│  Token Security Features:                                                   │
│  ├── RS256 asymmetric signing                                              │
│  ├── Key rotation every 90 days                                            │
│  ├── Token binding to session                                              │
│  ├── Refresh token rotation on use                                         │
│  └── Immediate revocation capability                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Token Security Implementation

```java
// Token Management Service
@Service
public class TokenManagementService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtEncoder jwtEncoder;
    
    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private static final String SESSION_PREFIX = "session:";
    
    /**
     * Issue new access token with session binding
     */
    public TokenPair issueTokens(User user, String sessionId) {
        Instant now = Instant.now();
        
        // Access token (short-lived)
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
        
        // Refresh token (long-lived)
        String refreshToken = generateRefreshToken();
        
        // Store refresh token in Redis
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
     * Refresh access token with rotation
     */
    public TokenPair refreshTokens(String refreshToken) {
        String key = "refresh:" + refreshToken;
        RefreshTokenData tokenData = (RefreshTokenData) redisTemplate.opsForValue().get(key);
        
        if (tokenData == null || tokenData.isExpired()) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }
        
        User user = userRepository.findById(tokenData.getUserId())
            .orElseThrow(() -> new UserNotFoundException("User not found"));
        
        // Rotate refresh token
        redisTemplate.delete(key);
        
        // Issue new token pair
        return issueTokens(user, tokenData.getSessionId());
    }
    
    /**
     * Revoke token (add to blacklist)
     */
    public void revokeToken(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            String jti = jwt.getId();
            Instant expiry = jwt.getExpiresAt();
            
            // Add to blacklist until original expiry
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
            log.warn("Failed to decode token for revocation", e);
        }
    }
    
    /**
     * Check if token is blacklisted
     */
    public boolean isTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }
    
    /**
     * Revoke all user sessions
     */
    public void revokeAllUserSessions(Long userId) {
        Set<String> sessionKeys = redisTemplate.keys(SESSION_PREFIX + userId + ":*");
        if (sessionKeys != null && !sessionKeys.isEmpty()) {
            redisTemplate.delete(sessionKeys);
        }
        
        auditService.logAllSessionsRevoked(userId);
    }
}

// JWT Validation with Blacklist Check
@Component
public class JwtBlacklistValidator implements OAuth2TokenValidator<Jwt> {
    
    private final TokenManagementService tokenService;
    
    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String jti = jwt.getId();
        
        if (jti == null) {
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Missing token ID", null));
        }
        
        if (tokenService.isTokenBlacklisted(jti)) {
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Token has been revoked", null));
        }
        
        return OAuth2TokenValidatorResult.success();
    }
}
```

---

## 6. Session Management

### 6.1 Session Security

| **Feature** | **Implementation** | **Purpose** |
|-------------|-------------------|-------------|
| **Stateless Sessions** | JWT-based, no server state | Scalability |
| **Session Binding** | Token bound to session ID | Prevent token theft |
| **Concurrent Sessions** | Configurable limit (default: 3) | Reduce attack surface |
| **Session Timeout** | 30 min inactivity | Limit exposure |
| **Forced Logout** | Admin capability | Emergency response |

### 6.2 Session Implementation

```java
// Session Management Service
@Service
public class SessionManagementService {
    
    private static final int MAX_CONCURRENT_SESSIONS = 3;
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);
    
    /**
     * Create new session with concurrent session control
     */
    public Session createSession(User user, DeviceInfo deviceInfo) {
        // Check concurrent sessions
        List<Session> activeSessions = getActiveSessions(user.getId());
        
        if (activeSessions.size() >= MAX_CONCURRENT_SESSIONS) {
            // Remove oldest session
            Session oldest = activeSessions.stream()
                .min(Comparator.comparing(Session::getCreatedAt))
                .orElseThrow();
            
            terminateSession(oldest.getId());
            auditService.logSessionTerminated(user.getId(), oldest.getId(), "Max sessions exceeded");
        }
        
        // Create new session
        Session session = Session.builder()
            .id(UUID.randomUUID().toString())
            .userId(user.getId())
            .deviceInfo(deviceInfo)
            .createdAt(Instant.now())
            .lastActivityAt(Instant.now())
            .expiresAt(Instant.now().plus(SESSION_TIMEOUT))
            .build();
        
        // Store in Redis
        redisTemplate.opsForValue().set(
            sessionKey(session.getId()),
            session,
            SESSION_TIMEOUT
        );
        
        // Add to user's session list
        redisTemplate.opsForSet().add(userSessionsKey(user.getId()), session.getId());
        
        auditService.logSessionCreated(user.getId(), session.getId(), deviceInfo);
        
        return session;
    }
    
    /**
     * Validate and refresh session
     */
    public Session validateSession(String sessionId) {
        Session session = (Session) redisTemplate.opsForValue().get(sessionKey(sessionId));
        
        if (session == null) {
            throw new InvalidSessionException("Session not found");
        }
        
        if (session.isExpired()) {
            terminateSession(sessionId);
            throw new SessionExpiredException("Session has expired");
        }
        
        // Refresh session timeout (sliding expiration)
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
     * Terminate session
     */
    public void terminateSession(String sessionId) {
        Session session = (Session) redisTemplate.opsForValue().get(sessionKey(sessionId));
        
        if (session != null) {
            redisTemplate.delete(sessionKey(sessionId));
            redisTemplate.opsForSet().remove(userSessionsKey(session.getUserId()), sessionId);
            
            auditService.logSessionTerminated(session.getUserId(), sessionId, "User logout");
        }
    }
    
    /**
     * Get all active sessions for user
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

## 7. Security Monitoring & Audit

### 7.1 Authentication Audit Events

| **Event** | **Severity** | **Details Logged** | **Alert** |
|-----------|--------------|-------------------|-----------|
| `LOGIN_SUCCESS` | Info | User ID, IP, Device, Time | No |
| `LOGIN_FAILED` | Warning | Username, IP, Reason | After 5 failures |
| `MFA_SUCCESS` | Info | User ID, Method | No |
| `MFA_FAILED` | Warning | User ID, Method, Reason | After 3 failures |
| `TOKEN_ISSUED` | Info | User ID, Token ID, Expiry | No |
| `TOKEN_REVOKED` | Info | User ID, Token ID, Reason | No |
| `SESSION_CREATED` | Info | User ID, Session ID, Device | No |
| `SESSION_TERMINATED` | Info | User ID, Session ID, Reason | No |
| `PERMISSION_DENIED` | Warning | User ID, Resource, Action | Yes (if repeated) |
| `PRIVILEGE_ESCALATION` | Critical | User ID, From Role, To Role | Always |

### 7.2 Audit Implementation

```java
// Security Audit Service
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
        
        // Check for alerting conditions
        checkAlertConditions(event);
    }
    
    private void checkAlertConditions(AuthenticationEvent event) {
        if (!event.isSuccess()) {
            // Count recent failures
            long recentFailures = countRecentFailures(event.getIpAddress(), Duration.ofMinutes(5));
            
            if (recentFailures >= 5) {
                alertService.sendSecurityAlert(
                    AlertSeverity.HIGH,
                    "Multiple authentication failures from IP: " + event.getIpAddress(),
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

## 8. Implementation Checklist

### 8.1 Authentication Checklist

| **Component** | **Status** | **Notes** |
|---------------|------------|-----------|
| OAuth2 Authorization Server | ✅ Designed | Spring Authorization Server |
| JWT Token Generation | ✅ Designed | RS256 signing |
| Refresh Token Rotation | ✅ Designed | Redis-backed |
| Token Blacklisting | ✅ Designed | Redis TTL-based |
| TOTP MFA | ✅ Designed | Google Authenticator compatible |
| SMS MFA | ✅ Designed | Fallback option |
| WebAuthn/FIDO2 | ✅ Designed | Hardware key support |
| Session Management | ✅ Designed | Redis-backed, concurrent limits |

### 8.2 Authorization Checklist

| **Component** | **Status** | **Notes** |
|---------------|------------|-----------|
| RBAC Implementation | ✅ Designed | 7 roles defined |
| Permission Matrix | ✅ Designed | 15+ permissions |
| Method Security | ✅ Designed | @PreAuthorize |
| ABAC Policy Engine | ✅ Designed | Organizational scope |
| Resource-Level Security | ✅ Designed | Owner/scope checks |
| Audit Logging | ✅ Designed | All auth events |

---

## Related Documents

- **Phase 10**: [Security Architecture](./SECURITY_ARCHITECTURE.md)
- **Phase 10**: [Threat Model](./THREAT_MODEL.md)
- **Phase 10**: [Privacy by Design](./PRIVACY_BY_DESIGN.md)
- **Phase 7**: [ADR-009 Security Framework](../architecture/adr/ADR-009-Security-Framework.md)
- **Phase 2**: [RBAC Matrix](../stakeholders/RBAC_matrix.md)

---

*Document Version: 1.0*  
*Classification: Internal*  
*Phase: 10 - Security Architecture & Privacy Design*  
*Author: Stefan Kostyk*


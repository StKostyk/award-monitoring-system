# Threat Model
## Award Monitoring & Tracking System - STRIDE Analysis

> **Phase 10 Deliverable**: Security Architecture & Privacy Design  
> **Document Version**: 1.0  
> **Last Updated**: December 2025  
> **Author**: Stefan Kostyk  
> **Methodology**: STRIDE Threat Modeling  
> **Classification**: Internal

---

## Executive Summary

This document presents a comprehensive threat model for the Award Monitoring & Tracking System using the STRIDE methodology. The analysis identifies potential threats across all system components, assesses their risk levels, and defines appropriate mitigations to ensure the security and integrity of the system.

### Threat Modeling Objectives

| **Objective** | **Description** | **Outcome** |
|---------------|-----------------|-------------|
| **Identify Assets** | Catalog all valuable system assets | Complete asset inventory |
| **Identify Threats** | Systematic threat identification using STRIDE | Comprehensive threat catalog |
| **Assess Risks** | Evaluate threat likelihood and impact | Risk-prioritized threat list |
| **Define Mitigations** | Document security controls | Control implementation plan |
| **Validate Security** | Ensure adequate protection | Security assurance |

---

## 1. System Assets Identification

### 1.1 Asset Categories

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SYSTEM ASSETS                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        DATA ASSETS                                   │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐│   │
│  │  │ User Data   │  │ Award Data  │  │ Documents   │  │ Audit Logs  ││   │
│  │  │ - PII       │  │ - Records   │  │ - Certs     │  │ - Actions   ││   │
│  │  │ - Creds     │  │ - Metadata  │  │ - Scans     │  │ - Access    ││   │
│  │  │ - Profiles  │  │ - History   │  │ - Proofs    │  │ - Changes   ││   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘│   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                       SYSTEM ASSETS                                  │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐│   │
│  │  │ Auth Tokens │  │ API Keys    │  │ Encryption  │  │ Certificates││   │
│  │  │ - JWT       │  │ - Service   │  │ Keys        │  │ - TLS       ││   │
│  │  │ - Refresh   │  │ - External  │  │ - DEK       │  │ - mTLS      ││   │
│  │  │ - Session   │  │ - Admin     │  │ - KEK       │  │ - Signing   ││   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘│   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    INFRASTRUCTURE ASSETS                             │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐│   │
│  │  │ Servers     │  │ Databases   │  │ Storage     │  │ Networks    ││   │
│  │  │ - App       │  │ - PostgreSQL│  │ - S3/Blob   │  │ - VPC       ││   │
│  │  │ - Cache     │  │ - Redis     │  │ - Backups   │  │ - Subnets   ││   │
│  │  │ - Message   │  │ - Search    │  │ - Logs      │  │ - Firewall  ││   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘│   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Asset Criticality Classification

| **Asset** | **Confidentiality** | **Integrity** | **Availability** | **Overall Criticality** |
|-----------|---------------------|---------------|------------------|------------------------|
| User Credentials | Critical | Critical | High | **Critical** |
| Personal Data (PII) | Critical | High | Medium | **Critical** |
| Award Records | Medium | Critical | High | **High** |
| Supporting Documents | High | High | Medium | **High** |
| Audit Logs | Medium | Critical | High | **High** |
| Authentication Tokens | Critical | Critical | High | **Critical** |
| Encryption Keys | Critical | Critical | Critical | **Critical** |
| System Configuration | High | Critical | High | **High** |
| API Endpoints | Low | High | Critical | **High** |
| Backup Data | Critical | High | Medium | **High** |

---

## 2. STRIDE Threat Analysis

### 2.1 STRIDE Methodology Overview

| **Category** | **Definition** | **Security Property Violated** | **Example Threat** |
|--------------|----------------|-------------------------------|-------------------|
| **S**poofing | Pretending to be someone/something else | Authentication | Stolen credentials |
| **T**ampering | Modifying data or code | Integrity | SQL injection |
| **R**epudiation | Denying actions performed | Non-repudiation | Missing audit logs |
| **I**nformation Disclosure | Exposing information | Confidentiality | Data breach |
| **D**enial of Service | Making system unavailable | Availability | DDoS attack |
| **E**levation of Privilege | Gaining unauthorized access | Authorization | Privilege escalation |

### 2.2 Component-Based Threat Analysis

#### 2.2.1 Web Application Layer

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WEB APPLICATION THREATS                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Component: Angular Frontend + API Gateway                                  │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  SPOOFING                                                            │   │
│  │  ├── Session hijacking via XSS                                      │   │
│  │  ├── Cookie theft                                                   │   │
│  │  └── Phishing attacks                                               │   │
│  │                                                                      │   │
│  │  TAMPERING                                                           │   │
│  │  ├── DOM manipulation                                               │   │
│  │  ├── Request parameter tampering                                    │   │
│  │  └── Client-side validation bypass                                  │   │
│  │                                                                      │   │
│  │  INFORMATION DISCLOSURE                                              │   │
│  │  ├── Sensitive data in browser storage                              │   │
│  │  ├── API response data leakage                                      │   │
│  │  └── Error message information disclosure                           │   │
│  │                                                                      │   │
│  │  DENIAL OF SERVICE                                                   │   │
│  │  ├── Resource exhaustion attacks                                    │   │
│  │  ├── Application-layer DDoS                                         │   │
│  │  └── Rate limiting bypass                                           │   │
│  │                                                                      │   │
│  │  ELEVATION OF PRIVILEGE                                              │   │
│  │  ├── CSRF attacks                                                   │   │
│  │  ├── Clickjacking                                                   │   │
│  │  └── Client-side role manipulation                                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 2.2.2 Authentication Service

| **Threat Category** | **Threat ID** | **Threat Description** | **Attack Vector** | **Impact** |
|---------------------|---------------|------------------------|-------------------|------------|
| **Spoofing** | S-AUTH-01 | Credential stuffing | Automated login attempts | Account takeover |
| **Spoofing** | S-AUTH-02 | Brute force attack | Password guessing | Unauthorized access |
| **Spoofing** | S-AUTH-03 | Token forgery | JWT manipulation | Identity spoofing |
| **Tampering** | T-AUTH-01 | Token replay attack | Captured token reuse | Session hijacking |
| **Tampering** | T-AUTH-02 | MFA bypass | MFA implementation flaws | Authentication bypass |
| **Repudiation** | R-AUTH-01 | Login action denial | Missing audit trail | Accountability loss |
| **Info Disclosure** | I-AUTH-01 | Password hash exposure | Database breach | Credential compromise |
| **Info Disclosure** | I-AUTH-02 | Token leakage in logs | Insufficient log sanitization | Session hijacking |
| **DoS** | D-AUTH-01 | Auth service flooding | Mass authentication requests | Service unavailability |
| **Elevation** | E-AUTH-01 | Role claim manipulation | JWT payload tampering | Privilege escalation |

#### 2.2.3 Award Service

| **Threat Category** | **Threat ID** | **Threat Description** | **Attack Vector** | **Impact** |
|---------------------|---------------|------------------------|-------------------|------------|
| **Spoofing** | S-AWARD-01 | Award submission spoofing | User impersonation | Fraudulent awards |
| **Tampering** | T-AWARD-01 | Award data manipulation | Direct database modification | Data integrity loss |
| **Tampering** | T-AWARD-02 | Approval workflow bypass | Logic manipulation | Unauthorized approvals |
| **Tampering** | T-AWARD-03 | Document tampering | File replacement | Evidence manipulation |
| **Repudiation** | R-AWARD-01 | Approval denial | Insufficient logging | Dispute resolution failure |
| **Info Disclosure** | I-AWARD-01 | Unauthorized award access | IDOR vulnerability | Privacy violation |
| **DoS** | D-AWARD-01 | Mass submission attack | Automated submissions | System overload |
| **Elevation** | E-AWARD-01 | Self-approval | Authorization bypass | Process integrity loss |

#### 2.2.4 Database Layer

| **Threat Category** | **Threat ID** | **Threat Description** | **Attack Vector** | **Impact** |
|---------------------|---------------|------------------------|-------------------|------------|
| **Spoofing** | S-DB-01 | Database user spoofing | Credential theft | Unauthorized access |
| **Tampering** | T-DB-01 | SQL injection | Malformed input | Data manipulation |
| **Tampering** | T-DB-02 | Direct record modification | Privileged access abuse | Data integrity loss |
| **Repudiation** | R-DB-01 | Transaction denial | Disabled logging | Audit failure |
| **Info Disclosure** | I-DB-01 | Database dump exposure | Backup theft | Mass data breach |
| **Info Disclosure** | I-DB-02 | Query result leakage | Error messages | Schema exposure |
| **DoS** | D-DB-01 | Connection pool exhaustion | Mass connections | Service disruption |
| **DoS** | D-DB-02 | Resource-intensive queries | Query injection | Performance degradation |
| **Elevation** | E-DB-01 | Privilege escalation | SQL injection | Full database access |

---

## 3. Detailed Threat Scenarios

### 3.1 Critical Threat Scenarios

#### Scenario 1: Account Takeover Attack

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    THREAT SCENARIO: ACCOUNT TAKEOVER                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Attack Chain:                                                              │
│                                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│  │ Phishing │───▶│ Credential│───▶│ Session  │───▶│ Account  │             │
│  │ Email    │    │ Harvest   │    │ Hijack   │    │ Takeover │             │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘             │
│                                                                             │
│  Threat Details:                                                            │
│  - STRIDE Category: Spoofing                                               │
│  - Threat ID: S-AUTH-04                                                    │
│  - Likelihood: Medium                                                       │
│  - Impact: Critical                                                         │
│  - Risk Score: 8/10                                                        │
│                                                                             │
│  Attack Steps:                                                              │
│  1. Attacker sends phishing email mimicking system notification            │
│  2. User clicks link and enters credentials on fake login page             │
│  3. Attacker uses stolen credentials to authenticate                       │
│  4. Attacker accesses user's account and data                              │
│  5. Attacker may escalate to other accounts or systems                     │
│                                                                             │
│  Mitigations:                                                               │
│  ├── MFA mandatory for all users                                           │
│  ├── Email domain verification (SPF, DKIM, DMARC)                          │
│  ├── User security awareness training                                       │
│  ├── Login anomaly detection                                               │
│  ├── Session fingerprinting                                                │
│  └── Password breach monitoring (HaveIBeenPwned)                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Scenario 2: Award Data Manipulation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  THREAT SCENARIO: AWARD DATA MANIPULATION                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Attack Chain:                                                              │
│                                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│  │ SQL      │───▶│ Auth     │───▶│ Data     │───▶│ Integrity│             │
│  │ Injection│    │ Bypass   │    │ Modify   │    │ Loss     │             │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘             │
│                                                                             │
│  Threat Details:                                                            │
│  - STRIDE Category: Tampering                                              │
│  - Threat ID: T-AWARD-01                                                   │
│  - Likelihood: Low (with proper controls)                                  │
│  - Impact: Critical                                                         │
│  - Risk Score: 7/10                                                        │
│                                                                             │
│  Attack Steps:                                                              │
│  1. Attacker identifies input field vulnerable to SQL injection            │
│  2. Attacker crafts malicious SQL payload                                  │
│  3. Application executes unintended SQL commands                           │
│  4. Attacker modifies award records (status, dates, recipients)            │
│  5. Fraudulent awards appear as legitimate                                 │
│                                                                             │
│  Mitigations:                                                               │
│  ├── Parameterized queries (JPA/Hibernate)                                 │
│  ├── Input validation and sanitization                                     │
│  ├── Database user with minimal privileges                                 │
│  ├── Web Application Firewall with SQL injection rules                     │
│  ├── Database activity monitoring                                          │
│  └── Award change audit trail with digital signatures                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Scenario 3: Data Breach via API

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    THREAT SCENARIO: API DATA BREACH                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Attack Chain:                                                              │
│                                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│  │ API      │───▶│ IDOR     │───▶│ Data     │───▶│ Mass     │             │
│  │ Enum     │    │ Exploit  │    │ Access   │    │ Exfil    │             │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘             │
│                                                                             │
│  Threat Details:                                                            │
│  - STRIDE Category: Information Disclosure                                 │
│  - Threat ID: I-API-01                                                     │
│  - Likelihood: Medium                                                       │
│  - Impact: Critical                                                         │
│  - Risk Score: 8/10                                                        │
│                                                                             │
│  Attack Steps:                                                              │
│  1. Attacker enumerates API endpoints through documentation/discovery      │
│  2. Attacker identifies IDOR vulnerability in /api/users/{id}             │
│  3. Attacker iterates through user IDs to access other profiles           │
│  4. Attacker extracts sensitive personal information                       │
│  5. Mass data exfiltration leads to GDPR breach                           │
│                                                                             │
│  Mitigations:                                                               │
│  ├── UUID instead of sequential IDs                                        │
│  ├── Object-level authorization checks                                     │
│  ├── Rate limiting per user/IP                                             │
│  ├── API response filtering based on permissions                           │
│  ├── Data access audit logging                                             │
│  └── Anomaly detection for unusual data access patterns                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Risk Assessment Matrix

### 4.1 Threat Risk Scoring

| **Threat ID** | **Category** | **Likelihood** | **Impact** | **Risk Score** | **Priority** |
|---------------|--------------|----------------|------------|----------------|--------------|
| S-AUTH-01 | Spoofing | High | Critical | 9 | **P1** |
| I-DB-01 | Info Disclosure | Low | Critical | 7 | **P1** |
| T-DB-01 | Tampering | Medium | Critical | 8 | **P1** |
| E-AUTH-01 | Elevation | Medium | Critical | 8 | **P1** |
| S-AUTH-04 | Spoofing | Medium | Critical | 8 | **P1** |
| T-AWARD-01 | Tampering | Low | Critical | 7 | **P2** |
| I-API-01 | Info Disclosure | Medium | High | 7 | **P2** |
| D-AUTH-01 | DoS | High | Medium | 6 | **P2** |
| T-AUTH-02 | Tampering | Low | High | 5 | **P3** |
| R-AUTH-01 | Repudiation | Low | Medium | 4 | **P3** |

### 4.2 Risk Heat Map

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          RISK HEAT MAP                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  IMPACT                                                                     │
│     ▲                                                                       │
│     │                                                                       │
│  Critical │  Medium     │  High       │  CRITICAL   │                      │
│           │  T-AUTH-02  │  I-DB-01    │  S-AUTH-01  │                      │
│           │             │  T-AWARD-01 │  T-DB-01    │                      │
│           │             │             │  E-AUTH-01  │                      │
│     ──────┼─────────────┼─────────────┼─────────────┤                      │
│  High     │  Low        │  Medium     │  High       │                      │
│           │  R-AWARD-01 │  I-API-01   │             │                      │
│           │             │             │             │                      │
│     ──────┼─────────────┼─────────────┼─────────────┤                      │
│  Medium   │  Low        │  Medium     │  Medium     │                      │
│           │  R-AUTH-01  │  D-DB-02    │  D-AUTH-01  │                      │
│           │             │             │             │                      │
│     ──────┼─────────────┼─────────────┼─────────────┤                      │
│  Low      │  Minimal    │  Low        │  Low        │                      │
│           │             │             │             │                      │
│           └─────────────┴─────────────┴─────────────┴────▶ LIKELIHOOD      │
│                Low          Medium         High                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Mitigation Controls

### 5.1 Control Categories

| **Control Type** | **Description** | **Examples** |
|------------------|-----------------|--------------|
| **Preventive** | Stop threats before they occur | Input validation, MFA, encryption |
| **Detective** | Identify threats when they occur | Logging, monitoring, IDS |
| **Corrective** | Respond to and fix threats | Incident response, patching |
| **Deterrent** | Discourage threat actors | Security warnings, legal notices |
| **Compensating** | Alternative controls when primary unavailable | Manual reviews, redundancy |

### 5.2 Mitigation Matrix

| **Threat ID** | **Primary Mitigation** | **Secondary Mitigation** | **Detection** | **Response** |
|---------------|------------------------|--------------------------|---------------|--------------|
| S-AUTH-01 | Rate limiting, Account lockout | CAPTCHA, IP blocking | Failed login monitoring | Account freeze |
| S-AUTH-02 | Strong password policy | MFA, Password breach check | Brute force detection | IP ban |
| S-AUTH-04 | MFA, Security training | Email authentication | Login anomaly detection | Session invalidation |
| T-DB-01 | Parameterized queries | WAF, Input validation | SQL injection detection | Query blocking |
| T-AWARD-01 | Audit logging, Checksums | Approval workflow | Change monitoring | Data restoration |
| I-DB-01 | Encryption at rest | Access controls | Data access logging | Breach notification |
| I-API-01 | Object authorization | UUID identifiers | Access pattern analysis | Rate limiting |
| D-AUTH-01 | Rate limiting | Auto-scaling | Traffic monitoring | Traffic filtering |
| E-AUTH-01 | Token signature verification | Role validation | Privilege monitoring | Token revocation |

### 5.3 Control Implementation Details

#### 5.3.1 Spoofing Mitigations

```java
// Multi-Factor Authentication Implementation
@Service
public class MfaService {
    
    public MfaChallenge initiateMfaChallenge(User user, AuthenticationContext context) {
        // Risk-based MFA determination
        RiskLevel risk = riskAssessmentService.assessLoginRisk(user, context);
        
        if (risk == RiskLevel.HIGH) {
            // Require hardware key for high-risk logins
            return createHardwareKeyChallenge(user);
        } else if (risk == RiskLevel.MEDIUM) {
            // TOTP for medium risk
            return createTotpChallenge(user);
        } else {
            // Email/SMS for low risk
            return createEmailChallenge(user);
        }
    }
    
    public boolean verifyMfa(String userId, String challengeId, String response) {
        MfaChallenge challenge = challengeRepository.findById(challengeId)
            .orElseThrow(() -> new InvalidChallengeException("Challenge not found"));
        
        if (challenge.isExpired()) {
            auditService.logFailedMfa(userId, "Challenge expired");
            throw new ExpiredChallengeException("MFA challenge has expired");
        }
        
        boolean verified = switch (challenge.getType()) {
            case TOTP -> totpValidator.validate(challenge.getSecret(), response);
            case EMAIL, SMS -> challenge.getCode().equals(response);
            case HARDWARE_KEY -> fido2Service.verifyAssertion(challenge, response);
        };
        
        if (!verified) {
            auditService.logFailedMfa(userId, "Invalid response");
        }
        
        return verified;
    }
}
```

#### 5.3.2 Tampering Mitigations

```java
// Data Integrity Verification
@Service
public class DataIntegrityService {
    
    @Transactional
    public Award saveAward(Award award) {
        // Calculate checksum before save
        String checksum = calculateChecksum(award);
        award.setIntegrityChecksum(checksum);
        
        // Save with optimistic locking
        Award saved = awardRepository.save(award);
        
        // Log change with checksum
        auditService.logDataChange(
            AuditAction.AWARD_MODIFIED,
            award.getId(),
            checksum,
            SecurityContextHolder.getContext().getAuthentication()
        );
        
        return saved;
    }
    
    public boolean verifyIntegrity(Award award) {
        String currentChecksum = calculateChecksum(award);
        return currentChecksum.equals(award.getIntegrityChecksum());
    }
    
    private String calculateChecksum(Award award) {
        String dataToHash = award.getTitle() + award.getDate() + 
                          award.getUserId() + award.getStatus();
        return DigestUtils.sha256Hex(dataToHash);
    }
}
```

#### 5.3.3 Information Disclosure Mitigations

```java
// Field-Level Encryption for PII
@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "email_address")
    @Convert(converter = EncryptedStringConverter.class)
    private String emailAddress;  // Encrypted at rest
    
    @Column(name = "phone_number")
    @Convert(converter = EncryptedStringConverter.class)
    private String phoneNumber;  // Encrypted at rest
    
    // Public fields - no encryption needed
    @Column(name = "display_name")
    private String displayName;
}

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    
    @Autowired
    private EncryptionService encryptionService;
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        return encryptionService.encrypt(attribute);
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return encryptionService.decrypt(dbData);
    }
}
```

#### 5.3.4 DoS Mitigations

```java
// Rate Limiting Configuration
@Configuration
public class RateLimitingConfig {
    
    @Bean
    public RateLimiter authenticationRateLimiter() {
        return RateLimiter.builder()
            .limitForPeriod(5)  // 5 requests
            .limitRefreshPeriod(Duration.ofMinutes(1))  // per minute
            .timeoutDuration(Duration.ofSeconds(1))
            .build();
    }
    
    @Bean
    public RateLimiter apiRateLimiter() {
        return RateLimiter.builder()
            .limitForPeriod(100)  // 100 requests
            .limitRefreshPeriod(Duration.ofMinutes(1))  // per minute
            .timeoutDuration(Duration.ofSeconds(5))
            .build();
    }
}

@Aspect
@Component
public class RateLimitingAspect {
    
    @Around("@annotation(RateLimited)")
    public Object rateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        String key = extractRateLimitKey(joinPoint);
        
        if (!rateLimiter.acquirePermission(key)) {
            auditService.logRateLimitExceeded(key);
            throw new TooManyRequestsException("Rate limit exceeded");
        }
        
        return joinPoint.proceed();
    }
}
```

#### 5.3.5 Elevation of Privilege Mitigations

```java
// Method-Level Authorization
@Service
public class AwardApprovalService {
    
    @PreAuthorize("hasRole('DEAN') or hasRole('RECTOR')")
    public void approveAward(Long awardId) {
        Award award = awardRepository.findById(awardId)
            .orElseThrow(() -> new NotFoundException("Award not found"));
        
        User currentUser = getCurrentUser();
        
        // Prevent self-approval
        if (award.getSubmitterId().equals(currentUser.getId())) {
            auditService.logSecurityViolation(
                "Self-approval attempt", 
                currentUser.getId(), 
                awardId
            );
            throw new SecurityException("Cannot approve own award submission");
        }
        
        // Verify authority level matches award category
        if (!hasAuthorityForCategory(currentUser, award.getCategory())) {
            throw new AccessDeniedException("Insufficient authority for this category");
        }
        
        // Process approval
        award.setStatus(AwardStatus.APPROVED);
        award.setApprovedBy(currentUser.getId());
        award.setApprovedAt(Instant.now());
        
        awardRepository.save(award);
        auditService.logApproval(award, currentUser);
    }
}
```

---

## 6. Threat Monitoring & Detection

### 6.1 Security Monitoring Rules

| **Rule ID** | **Threat Detected** | **Detection Logic** | **Alert Severity** |
|-------------|---------------------|---------------------|-------------------|
| SEC-001 | Brute force attack | >10 failed logins in 5 min from same IP | High |
| SEC-002 | Credential stuffing | >100 unique user login attempts from same IP | Critical |
| SEC-003 | Privilege escalation | Role change without proper approval | Critical |
| SEC-004 | Data exfiltration | >1000 records accessed in 1 hour by single user | High |
| SEC-005 | SQL injection attempt | SQL keywords in input parameters | Medium |
| SEC-006 | Token replay | Same token used from different IPs | High |
| SEC-007 | Unusual access pattern | Access outside normal hours/location | Medium |
| SEC-008 | Mass deletion attempt | >10 delete operations in 1 minute | Critical |

### 6.2 Detection Implementation

```yaml
# Elasticsearch Security Rules
security_rules:
  - name: "Brute Force Detection"
    type: "threshold"
    index: "audit-logs-*"
    query: "action:LOGIN_FAILED"
    group_by: "source_ip"
    threshold: 10
    time_window: "5m"
    severity: "high"
    actions:
      - type: "alert"
        channel: "security-team"
      - type: "block_ip"
        duration: "1h"
        
  - name: "Data Exfiltration Detection"
    type: "threshold"
    index: "audit-logs-*"
    query: "action:DATA_ACCESS AND entity_type:users"
    group_by: "user_id"
    threshold: 1000
    time_window: "1h"
    severity: "high"
    actions:
      - type: "alert"
        channel: "security-team"
      - type: "suspend_session"
```

---

## 7. Residual Risk Assessment

### 7.1 Post-Mitigation Risk Levels

| **Threat ID** | **Initial Risk** | **Mitigations Applied** | **Residual Risk** | **Acceptable** |
|---------------|------------------|-------------------------|-------------------|----------------|
| S-AUTH-01 | High (9) | MFA, Rate limiting, Account lockout | Low (3) | ✅ Yes |
| T-DB-01 | High (8) | Parameterized queries, WAF | Very Low (2) | ✅ Yes |
| I-DB-01 | High (7) | Encryption, Access controls | Low (3) | ✅ Yes |
| E-AUTH-01 | High (8) | Token signing, Role validation | Low (3) | ✅ Yes |
| I-API-01 | Medium (7) | Authorization, UUIDs | Low (4) | ✅ Yes |

### 7.2 Risk Acceptance Criteria

| **Risk Level** | **Score Range** | **Acceptance Authority** | **Review Frequency** |
|----------------|-----------------|-------------------------|---------------------|
| Very Low | 1-2 | Security Team | Annual |
| Low | 3-4 | Security Manager | Quarterly |
| Medium | 5-6 | CISO / Project Sponsor | Monthly |
| High | 7-8 | Executive Management | Immediate escalation |
| Critical | 9-10 | Not Acceptable | Must be mitigated |

---

## 8. Threat Model Maintenance

### 8.1 Review Schedule

| **Trigger** | **Review Type** | **Scope** | **Responsible** |
|-------------|-----------------|-----------|-----------------|
| New feature release | Incremental | Affected components | Development Team |
| Security incident | Targeted | Incident-related components | Security Team |
| Annual schedule | Full | Complete system | Security Team |
| Major architecture change | Full | Complete system | Architecture Board |
| New threat intelligence | Targeted | Relevant threats | Security Team |

### 8.2 Threat Model Update Process

1. **Identify Change** - Document system changes or new intelligence
2. **Update Assets** - Revise asset inventory if needed
3. **Analyze Threats** - Apply STRIDE to new/changed components
4. **Assess Risks** - Score new threats
5. **Define Mitigations** - Identify controls for new threats
6. **Update Documentation** - Revise this threat model
7. **Validate Controls** - Test mitigation effectiveness
8. **Communicate Changes** - Notify stakeholders

---

## Related Documents

- **Phase 10**: [Security Architecture](./SECURITY_ARCHITECTURE.md)
- **Phase 10**: [Authentication & Authorization](./AUTHENTICATION_AUTHORIZATION.md)
- **Phase 10**: [Privacy by Design](./PRIVACY_BY_DESIGN.md)
- **Phase 6**: [Security Framework](../compliance/SECURITY_FRAMEWORK.md)

---

*Document Version: 1.0*  
*Classification: Internal*  
*Phase: 10 - Security Architecture & Privacy Design*  
*Author: Stefan Kostyk*


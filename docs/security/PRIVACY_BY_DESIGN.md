# Privacy by Design Implementation
## Award Monitoring & Tracking System

> **Phase 10 Deliverable**: Security Architecture & Privacy Design  
> **Document Version**: 1.0  
> **Last Updated**: December 2025  
> **Author**: Stefan Kostyk  
> **Framework**: Privacy by Design (PbD) - 7 Foundational Principles  
> **Classification**: Internal

---

## Executive Summary

This document defines the Privacy by Design (PbD) implementation strategy for the Award Monitoring & Tracking System. Following the seven foundational principles established by Ann Cavoukian, the architecture ensures privacy is embedded into every aspect of system design, development, and operation while maintaining GDPR compliance.

### PbD Implementation Goals

| **Goal** | **Description** | **Success Metric** |
|----------|-----------------|-------------------|
| **Privacy Embedded** | Privacy controls built into core architecture | 100% of PII fields protected |
| **User Control** | Users have full control over their data | All GDPR rights implemented |
| **Data Minimization** | Collect only necessary data | Minimal data footprint per purpose |
| **Transparency** | Clear privacy practices | User-friendly privacy notices |
| **Security** | Strong data protection | Zero privacy breaches |

---

## 1. Seven Foundational Principles

### 1.1 Principles Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  PRIVACY BY DESIGN - 7 PRINCIPLES                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  1. PROACTIVE NOT REACTIVE                                          │   │
│  │     Anticipate and prevent privacy issues before they occur         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  2. PRIVACY AS DEFAULT                                               │   │
│  │     Maximum privacy protection without user action required          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  3. PRIVACY EMBEDDED INTO DESIGN                                     │   │
│  │     Privacy integral to system architecture, not an add-on           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  4. FULL FUNCTIONALITY (POSITIVE-SUM)                                │   │
│  │     Privacy AND functionality, not privacy OR functionality          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  5. END-TO-END SECURITY                                              │   │
│  │     Full lifecycle protection from collection to deletion            │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  6. VISIBILITY AND TRANSPARENCY                                      │   │
│  │     Open practices, subject to verification                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  7. RESPECT FOR USER PRIVACY                                         │   │
│  │     User-centric design, empowering individuals                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Data Minimization Implementation

### 2.1 Data Collection Principles

| **Principle** | **Implementation** | **Technical Control** |
|---------------|-------------------|----------------------|
| **Collect Minimum** | Only data essential for stated purpose | Schema validation, required fields only |
| **Purpose Binding** | Data linked to specific processing purpose | Purpose field in data model |
| **Time Limitation** | Retention periods defined per data type | Automated deletion jobs |
| **Access Limitation** | Data accessible only by authorized roles | RBAC + field-level permissions |

### 2.2 Data Collection Matrix

| **Data Field** | **Purpose** | **Legal Basis** | **Retention** | **Minimization Strategy** |
|----------------|-------------|-----------------|---------------|--------------------------|
| `email_address` | Authentication, notifications | Contract | Account lifetime + 30 days | Single purpose collection |
| `first_name`, `last_name` | Award attribution | Legitimate interest | 7 years post-employment | Required for transparency |
| `phone_number` | Optional MFA | Consent | Until consent withdrawn | Not required, user choice |
| `award_title` | Core functionality | Contract | Indefinite (public record) | Essential field |
| `award_documents` | Verification | Consent | User-defined retention | User controls retention |

### 2.3 Technical Implementation

```java
// Data Minimization Service
@Service
public class DataMinimizationService {
    
    /**
     * Validates that only necessary data is collected for a given purpose
     */
    public <T> T minimizeForPurpose(T data, ProcessingPurpose purpose) {
        DataMinimizationRules rules = rulesRepository.getRulesForPurpose(purpose);
        
        // Get allowed fields for this purpose
        Set<String> allowedFields = rules.getAllowedFields();
        
        // Null out non-essential fields
        for (Field field : data.getClass().getDeclaredFields()) {
            if (!allowedFields.contains(field.getName())) {
                field.setAccessible(true);
                try {
                    if (!field.getType().isPrimitive()) {
                        field.set(data, null);
                    }
                } catch (IllegalAccessException e) {
                    log.warn("Could not minimize field: {}", field.getName());
                }
            }
        }
        
        auditService.logDataMinimization(data.getClass().getSimpleName(), purpose);
        return data;
    }
    
    /**
     * Validates input data contains only allowed fields
     */
    public void validateInputMinimization(Object input, ProcessingPurpose purpose) {
        DataMinimizationRules rules = rulesRepository.getRulesForPurpose(purpose);
        
        for (Field field : input.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(input);
                if (value != null && !rules.getAllowedFields().contains(field.getName())) {
                    throw new ExcessiveDataException(
                        "Field '" + field.getName() + "' is not required for purpose: " + purpose
                    );
                }
            } catch (IllegalAccessException e) {
                // Field not accessible, skip
            }
        }
    }
}

// Purpose-specific DTOs enforce minimization at compile time
public record AwardSubmissionRequest(
    @NotBlank String title,
    @NotNull LocalDate awardDate,
    @NotBlank String awardingOrganization,
    @NotNull Long categoryId
    // Note: No email, no phone, no address - not needed for submission
) {}

public record UserRegistrationRequest(
    @Email @NotBlank String email,
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotBlank String password
    // Note: Phone is optional, collected separately with consent
) {}
```

---

## 3. Purpose Limitation Enforcement

### 3.1 Purpose Definition

| **Purpose ID** | **Purpose Name** | **Description** | **Data Categories** | **Retention** |
|----------------|------------------|-----------------|---------------------|---------------|
| P001 | Award Management | Creating, tracking, and displaying awards | User profile, Award data | 7 years |
| P002 | Authentication | User login and session management | Credentials, Session data | Session + 30 days |
| P003 | Notification | Email/SMS alerts about awards | Contact info, Preferences | Account lifetime |
| P004 | Analytics | System usage and performance | Anonymized usage data | 3 years |
| P005 | Compliance | Audit trails and GDPR compliance | Audit logs, Consent records | 7 years |

### 3.2 Purpose Binding Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PURPOSE LIMITATION ENFORCEMENT                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Data Collection                                                            │
│       │                                                                     │
│       ▼                                                                     │
│  ┌─────────────────┐                                                        │
│  │ Purpose Tag     │  Every data item tagged with collection purpose        │
│  │ Assignment      │                                                        │
│  └────────┬────────┘                                                        │
│           │                                                                 │
│           ▼                                                                 │
│  ┌─────────────────┐                                                        │
│  │ Purpose         │  Validates purpose matches allowed uses                │
│  │ Validation      │                                                        │
│  └────────┬────────┘                                                        │
│           │                                                                 │
│       ┌───┴───┐                                                             │
│       │       │                                                             │
│       ▼       ▼                                                             │
│  ┌────────┐ ┌────────┐                                                      │
│  │ Allow  │ │ Deny   │                                                      │
│  │ Access │ │ + Log  │                                                      │
│  └────────┘ └────────┘                                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 Technical Implementation

```java
// Purpose Limitation Aspect
@Aspect
@Component
public class PurposeLimitationAspect {
    
    @Around("@annotation(purposeRequired)")
    public Object enforcePurpose(ProceedingJoinPoint joinPoint, 
                                  PurposeRequired purposeRequired) throws Throwable {
        ProcessingPurpose requiredPurpose = purposeRequired.value();
        ProcessingContext context = ProcessingContext.current();
        
        // Validate current processing context has valid purpose
        if (context.getPurpose() == null) {
            throw new PurposeNotSpecifiedException(
                "Processing purpose must be specified for this operation"
            );
        }
        
        // Check if purposes are compatible
        if (!isPurposeCompatible(context.getPurpose(), requiredPurpose)) {
            auditService.logPurposeMismatch(
                context.getPurpose(), 
                requiredPurpose,
                joinPoint.getSignature().toShortString()
            );
            throw new PurposeMismatchException(
                "Data collected for " + context.getPurpose() + 
                " cannot be used for " + requiredPurpose
            );
        }
        
        return joinPoint.proceed();
    }
    
    private boolean isPurposeCompatible(ProcessingPurpose original, 
                                        ProcessingPurpose requested) {
        // Check if requested purpose is same or compatible sub-purpose
        return original == requested || 
               original.getCompatiblePurposes().contains(requested);
    }
}

// Usage example
@Service
public class NotificationService {
    
    @PurposeRequired(ProcessingPurpose.NOTIFICATION)
    public void sendAwardNotification(Long userId, AwardNotification notification) {
        // This method can only access data collected for NOTIFICATION purpose
        User user = userRepository.findByIdForPurpose(userId, ProcessingPurpose.NOTIFICATION);
        
        if (user.getNotificationConsent().isGranted()) {
            emailService.send(user.getEmail(), notification);
        }
    }
}
```

---

## 4. Consent Management System

### 4.1 Consent Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      CONSENT MANAGEMENT SYSTEM                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      CONSENT COLLECTION                              │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │   │
│  │  │ Granular    │  │ Clear       │  │ Freely      │                 │   │
│  │  │ Options     │  │ Language    │  │ Given       │                 │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      CONSENT STORAGE                                 │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │   │
│  │  │ Versioned   │  │ Timestamped │  │ Auditable   │                 │   │
│  │  │ Records     │  │ History     │  │ Trail       │                 │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      CONSENT ENFORCEMENT                             │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │   │
│  │  │ Real-time   │  │ Processing  │  │ Access      │                 │   │
│  │  │ Validation  │  │ Gates       │  │ Control     │                 │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      CONSENT WITHDRAWAL                              │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │   │
│  │  │ Easy        │  │ Immediate   │  │ Cascading   │                 │   │
│  │  │ Process     │  │ Effect      │  │ Updates     │                 │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Consent Types

| **Consent Type** | **Purpose** | **Required** | **Granularity** | **Withdrawal Impact** |
|------------------|-------------|--------------|-----------------|----------------------|
| `DATA_PROCESSING` | Basic system use | Yes | Binary | Account deletion |
| `PUBLIC_VISIBILITY` | Award public display | Yes | Binary | Awards hidden |
| `EMAIL_NOTIFICATIONS` | Email alerts | No | Per-type | No emails sent |
| `SMS_NOTIFICATIONS` | SMS alerts | No | Binary | No SMS sent |
| `DOCUMENT_AI_PROCESSING` | AI metadata extraction | No | Per-document | Manual entry only |
| `ANALYTICS` | Usage analytics | No | Binary | Excluded from analytics |

### 4.3 Technical Implementation

```java
// Consent Management Service
@Service
@Transactional
public class ConsentManagementService {
    
    private final ConsentRepository consentRepository;
    private final ConsentEventPublisher eventPublisher;
    private final AuditService auditService;
    
    /**
     * Grant consent with full audit trail
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
        
        // Publish event for downstream systems
        eventPublisher.publish(new ConsentGrantedEvent(saved));
        
        // Audit log
        auditService.logConsentChange(
            AuditAction.CONSENT_GRANTED,
            saved.getUserId(),
            saved.getConsentType(),
            request.getIpAddress()
        );
        
        return saved;
    }
    
    /**
     * Withdraw consent with cascading effects
     */
    public ConsentRecord withdrawConsent(Long userId, ConsentType consentType, 
                                          WithdrawalRequest request) {
        ConsentRecord current = consentRepository
            .findCurrentConsent(userId, consentType)
            .orElseThrow(() -> new ConsentNotFoundException("No active consent found"));
        
        // Update consent record
        current.setIsGranted(false);
        current.setWithdrawnAt(Instant.now());
        ConsentRecord saved = consentRepository.save(current);
        
        // Publish event for cascading effects
        eventPublisher.publish(new ConsentWithdrawnEvent(saved));
        
        // Trigger immediate data handling based on consent type
        handleConsentWithdrawal(userId, consentType);
        
        // Audit log
        auditService.logConsentChange(
            AuditAction.CONSENT_WITHDRAWN,
            userId,
            consentType,
            request.getIpAddress()
        );
        
        return saved;
    }
    
    /**
     * Check if user has valid consent for operation
     */
    public boolean hasValidConsent(Long userId, ConsentType consentType) {
        return consentRepository.findCurrentConsent(userId, consentType)
            .map(consent -> consent.getIsGranted() && 
                           consent.getConsentVersion().equals(getCurrentPolicyVersion(consentType)))
            .orElse(false);
    }
    
    /**
     * Handle cascading effects of consent withdrawal
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

// Consent Enforcement Annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresConsent {
    ConsentType value();
    boolean failSilently() default false;
}

// Consent Enforcement Aspect
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
                return null; // Silent skip
            }
            throw new ConsentRequiredException(
                "Valid consent for " + requiredConsent + " is required"
            );
        }
        
        return joinPoint.proceed();
    }
}
```

---

## 5. Right to Erasure Implementation

### 5.1 Erasure Process Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    RIGHT TO ERASURE (ARTICLE 17)                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  User Request                                                               │
│       │                                                                     │
│       ▼                                                                     │
│  ┌─────────────────┐                                                        │
│  │ Identity        │  Verify user identity before processing                │
│  │ Verification    │                                                        │
│  └────────┬────────┘                                                        │
│           │                                                                 │
│           ▼                                                                 │
│  ┌─────────────────┐                                                        │
│  │ Eligibility     │  Check for legal retention requirements                │
│  │ Assessment      │                                                        │
│  └────────┬────────┘                                                        │
│           │                                                                 │
│       ┌───┴───┐                                                             │
│       │       │                                                             │
│       ▼       ▼                                                             │
│  ┌────────┐ ┌────────┐                                                      │
│  │ Full   │ │ Partial│  Full deletion or anonymization                      │
│  │ Delete │ │ Anon.  │                                                      │
│  └───┬────┘ └───┬────┘                                                      │
│      │          │                                                           │
│      └────┬─────┘                                                           │
│           │                                                                 │
│           ▼                                                                 │
│  ┌─────────────────┐                                                        │
│  │ Cascading       │  Delete from all systems and backups                   │
│  │ Deletion        │                                                        │
│  └────────┬────────┘                                                        │
│           │                                                                 │
│           ▼                                                                 │
│  ┌─────────────────┐                                                        │
│  │ Confirmation    │  Notify user and log completion                        │
│  │ & Audit         │                                                        │
│  └─────────────────┘                                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Data Deletion Categories

| **Category** | **Deletion Strategy** | **Timeline** | **Exceptions** |
|--------------|----------------------|--------------|----------------|
| **User Profile** | Full deletion | 30 days | Legal holds |
| **Awards** | Anonymization | 30 days | Public record retention |
| **Documents** | Full deletion | 30 days | None |
| **Audit Logs** | Anonymization | 30 days | 7-year retention for compliance |
| **Backups** | Marked for exclusion | Next backup cycle | Compliance requirements |
| **Analytics** | Anonymization | Immediate | Already anonymized |

### 5.3 Technical Implementation

```java
// Data Erasure Service
@Service
@Transactional
public class DataErasureService {
    
    /**
     * Process right to erasure request
     */
    public ErasureResult processErasureRequest(ErasureRequest request) {
        // Step 1: Verify identity
        verifyUserIdentity(request.getUserId(), request.getVerificationToken());
        
        // Step 2: Check eligibility
        ErasureEligibility eligibility = assessErasureEligibility(request.getUserId());
        
        if (!eligibility.isEligible()) {
            return ErasureResult.denied(eligibility.getReason());
        }
        
        // Step 3: Execute erasure based on eligibility type
        ErasureResult result = switch (eligibility.getType()) {
            case FULL_DELETION -> executeFullDeletion(request.getUserId());
            case PARTIAL_ANONYMIZATION -> executePartialAnonymization(request.getUserId());
            case DEFERRED -> scheduleDeferredErasure(request.getUserId(), eligibility.getDeferralDate());
        };
        
        // Step 4: Audit and notify
        auditService.logErasureRequest(request, result);
        notificationService.sendErasureConfirmation(request.getUserId(), result);
        
        return result;
    }
    
    /**
     * Full deletion of all user data
     */
    private ErasureResult executeFullDeletion(Long userId) {
        List<DeletionTask> tasks = new ArrayList<>();
        
        // Delete documents from object storage
        tasks.add(documentStorageService.deleteAllUserDocuments(userId));
        
        // Delete user awards (or anonymize if public record)
        tasks.add(awardService.deleteOrAnonymizeUserAwards(userId));
        
        // Delete notifications
        tasks.add(notificationService.deleteUserNotifications(userId));
        
        // Delete consent records (keep anonymized for proof)
        tasks.add(consentService.anonymizeUserConsents(userId));
        
        // Anonymize audit logs (required for compliance)
        tasks.add(auditService.anonymizeUserLogs(userId));
        
        // Delete user profile (last step)
        tasks.add(userService.deleteUser(userId));
        
        // Request backup exclusion
        backupService.markUserForExclusion(userId);
        
        // Aggregate results
        return ErasureResult.completed(tasks);
    }
    
    /**
     * Partial anonymization for records with legal retention
     */
    private ErasureResult executePartialAnonymization(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        // Anonymize PII while keeping record structure
        AnonymizedUser anonymized = AnonymizedUser.builder()
            .id(user.getId())
            .email(generateAnonymousEmail())
            .firstName("[DELETED]")
            .lastName("[DELETED]")
            .organizationId(user.getOrganizationId()) // Keep for statistics
            .anonymizedAt(Instant.now())
            .build();
        
        // Replace user record
        userRepository.save(anonymized);
        
        // Update related records to point to anonymized user
        awardService.anonymizeUserAttribution(userId);
        
        return ErasureResult.anonymized();
    }
}

// Anonymization Utilities
@Service
public class AnonymizationService {
    
    private static final String ANONYMIZED_MARKER = "[ANONYMIZED]";
    
    /**
     * Generate stable pseudonym for consistency
     */
    public String generatePseudonym(Long userId) {
        // Stable hash to maintain referential integrity
        return "USER_" + DigestUtils.sha256Hex(userId + SECRET_SALT).substring(0, 12);
    }
    
    /**
     * Anonymize email address
     */
    public String anonymizeEmail(String email) {
        return "anonymized_" + UUID.randomUUID().toString().substring(0, 8) + "@deleted.local";
    }
    
    /**
     * K-anonymity for statistical data
     */
    public <T> List<T> applyKAnonymity(List<T> records, int k, List<String> quasiIdentifiers) {
        // Group by quasi-identifiers
        Map<String, List<T>> groups = records.stream()
            .collect(Collectors.groupingBy(r -> extractQuasiIdentifiers(r, quasiIdentifiers)));
        
        // Generalize groups smaller than k
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

## 6. Data Portability Implementation

### 6.1 Export Format Specification

| **Format** | **Use Case** | **Contents** | **Standard** |
|------------|--------------|--------------|--------------|
| JSON | Machine-readable export | All user data | RFC 8259 |
| CSV | Spreadsheet import | Tabular data | RFC 4180 |
| PDF | Human-readable report | Formatted summary | PDF/A-3 |

### 6.2 Export Data Structure

```json
{
  "export_metadata": {
    "export_date": "2025-12-16T10:30:00Z",
    "user_id": "USR-12345",
    "format_version": "1.0",
    "gdpr_article": "Article 20 - Right to Data Portability"
  },
  "personal_data": {
    "profile": {
      "email": "user@example.com",
      "first_name": "Ivan",
      "last_name": "Petrenko",
      "organization": "Faculty of Computer Science",
      "registration_date": "2024-01-15"
    },
    "preferences": {
      "notification_email": true,
      "notification_sms": false,
      "language": "uk"
    }
  },
  "awards": [
    {
      "award_id": "AWD-001",
      "title": "Best Research Paper 2024",
      "date": "2024-06-15",
      "category": "Academic Achievement",
      "status": "APPROVED",
      "awarding_organization": "Ministry of Education"
    }
  ],
  "documents": [
    {
      "document_id": "DOC-001",
      "filename": "certificate.pdf",
      "upload_date": "2024-06-16",
      "download_url": "/api/export/documents/DOC-001"
    }
  ],
  "consent_history": [
    {
      "consent_type": "DATA_PROCESSING",
      "granted_at": "2024-01-15T09:00:00Z",
      "version": "1.0"
    }
  ],
  "activity_log": [
    {
      "action": "LOGIN",
      "timestamp": "2024-12-15T08:30:00Z",
      "ip_address": "[REDACTED]"
    }
  ]
}
```

### 6.3 Technical Implementation

```java
// Data Portability Service
@Service
public class DataPortabilityService {
    
    /**
     * Generate complete data export for user
     */
    public DataExport generateExport(Long userId, ExportFormat format) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        // Collect all user data
        UserDataBundle bundle = UserDataBundle.builder()
            .profile(extractUserProfile(user))
            .preferences(extractPreferences(userId))
            .awards(extractAwards(userId))
            .documents(extractDocuments(userId))
            .consentHistory(extractConsentHistory(userId))
            .activityLog(extractActivityLog(userId))
            .build();
        
        // Format export
        byte[] exportData = switch (format) {
            case JSON -> jsonExporter.export(bundle);
            case CSV -> csvExporter.export(bundle);
            case PDF -> pdfExporter.export(bundle);
        };
        
        // Generate secure download link
        String downloadToken = generateSecureToken(userId);
        String downloadUrl = storeTempFile(exportData, downloadToken, format);
        
        // Audit log
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
     * Extract user profile data
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
     * Extract awards with minimal metadata
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

## 7. Breach Notification Implementation

### 7.1 Breach Detection & Response Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    BREACH NOTIFICATION PROCESS                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Detection                    Assessment                  Notification      │
│       │                           │                           │            │
│       ▼                           ▼                           ▼            │
│  ┌─────────┐               ┌─────────────┐             ┌─────────────┐     │
│  │ SIEM    │               │ Severity    │             │ DPA (72h)   │     │
│  │ Alert   │──────────────▶│ Assessment  │────────────▶│ Notification│     │
│  └─────────┘               └─────────────┘             └─────────────┘     │
│       │                           │                           │            │
│       │                           │                    ┌──────┴──────┐     │
│       │                           │                    │             │     │
│       ▼                           ▼                    ▼             ▼     │
│  ┌─────────┐               ┌─────────────┐       ┌─────────┐ ┌─────────┐  │
│  │ Anomaly │               │ Data        │       │ User    │ │ Internal│  │
│  │ Detect  │               │ Inventory   │       │ Notify  │ │ Teams   │  │
│  └─────────┘               └─────────────┘       └─────────┘ └─────────┘  │
│                                                                             │
│  Timeline Requirements:                                                     │
│  - Detection to Assessment: < 4 hours                                       │
│  - Assessment to DPA Notification: < 72 hours                              │
│  - User Notification: "Without undue delay" if high risk                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.2 Breach Severity Classification

| **Severity** | **Criteria** | **DPA Notification** | **User Notification** | **Timeline** |
|--------------|--------------|---------------------|----------------------|--------------|
| **Critical** | >10,000 records OR credentials | Required | Required | Immediate |
| **High** | 1,000-10,000 records OR special categories | Required | Likely | 24 hours |
| **Medium** | 100-1,000 records | Required | Case-by-case | 48 hours |
| **Low** | <100 records, no special data | Document only | Not required | 72 hours |

### 7.3 Technical Implementation

```java
// Breach Detection and Notification Service
@Service
public class BreachNotificationService {
    
    /**
     * Handle detected security breach
     */
    @Async
    public void handleBreachDetection(SecurityBreachEvent event) {
        // Step 1: Create incident record
        BreachIncident incident = BreachIncident.builder()
            .detectedAt(Instant.now())
            .source(event.getSource())
            .affectedSystem(event.getAffectedSystem())
            .status(BreachStatus.DETECTED)
            .build();
        
        incident = breachRepository.save(incident);
        
        // Step 2: Assess severity
        BreachAssessment assessment = assessBreachSeverity(event);
        incident.setAssessment(assessment);
        incident.setStatus(BreachStatus.ASSESSED);
        breachRepository.save(incident);
        
        // Step 3: Identify affected users
        List<Long> affectedUsers = identifyAffectedUsers(event);
        incident.setAffectedUserCount(affectedUsers.size());
        
        // Step 4: Trigger notifications based on severity
        if (assessment.requiresDpaNotification()) {
            scheduleDpaNotification(incident);
        }
        
        if (assessment.requiresUserNotification()) {
            scheduleUserNotifications(incident, affectedUsers);
        }
        
        // Step 5: Internal escalation
        escalateToSecurityTeam(incident);
        
        // Step 6: Audit log
        auditService.logBreachIncident(incident);
    }
    
    /**
     * Assess breach severity based on data types and volume
     */
    private BreachAssessment assessBreachSeverity(SecurityBreachEvent event) {
        int severityScore = 0;
        
        // Volume factor
        int recordCount = event.getEstimatedRecordCount();
        if (recordCount > 10000) severityScore += 40;
        else if (recordCount > 1000) severityScore += 30;
        else if (recordCount > 100) severityScore += 20;
        else severityScore += 10;
        
        // Data sensitivity factor
        if (event.containsCredentials()) severityScore += 30;
        if (event.containsSpecialCategories()) severityScore += 25;
        if (event.containsFinancialData()) severityScore += 20;
        if (event.containsPII()) severityScore += 15;
        
        // Attack sophistication factor
        if (event.isTargetedAttack()) severityScore += 10;
        
        return BreachAssessment.builder()
            .severityScore(severityScore)
            .severity(calculateSeverityLevel(severityScore))
            .requiresDpaNotification(severityScore >= 30)
            .requiresUserNotification(severityScore >= 50)
            .build();
    }
    
    /**
     * Generate DPA notification content (GDPR Article 33)
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

## 8. Privacy Controls Dashboard

### 8.1 User Privacy Dashboard Features

| **Feature** | **Description** | **GDPR Article** |
|-------------|-----------------|-----------------|
| **View My Data** | Display all collected personal data | Art. 15 - Access |
| **Edit My Data** | Correct inaccurate information | Art. 16 - Rectification |
| **Delete My Data** | Request account and data deletion | Art. 17 - Erasure |
| **Export My Data** | Download personal data in portable format | Art. 20 - Portability |
| **Manage Consent** | Grant/withdraw specific consents | Art. 7 - Consent |
| **Privacy Settings** | Configure visibility and sharing | Art. 18 - Restriction |
| **Activity Log** | View all data access and changes | Art. 15 - Access |

### 8.2 Dashboard Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PRIVACY DASHBOARD                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  MY DATA SUMMARY                                                     │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐│   │
│  │  │ Profile     │  │ Awards      │  │ Documents   │  │ Activity    ││   │
│  │  │ 8 fields    │  │ 12 items    │  │ 23 files    │  │ 156 events  ││   │
│  │  │ [View]      │  │ [View]      │  │ [View]      │  │ [View]      ││   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘│   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  CONSENT MANAGEMENT                                                  │   │
│  │                                                                      │   │
│  │  ☑ Data Processing (Required)         Granted: Jan 15, 2024        │   │
│  │  ☑ Public Award Display               [Manage Visibility]          │   │
│  │  ☑ Email Notifications                [Preferences]                │   │
│  │  ☐ SMS Notifications                  Not enabled                  │   │
│  │  ☑ AI Document Processing             [Withdraw]                   │   │
│  │  ☐ Usage Analytics                    Opted out                    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  DATA RIGHTS ACTIONS                                                 │   │
│  │                                                                      │   │
│  │  [📥 Export My Data]  [✏️ Correct Data]  [🗑️ Delete Account]       │   │
│  │                                                                      │   │
│  │  Last export: Dec 10, 2025    Processing requests: 0 pending        │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Implementation Checklist

### 9.1 Privacy by Design Compliance Checklist

| **Principle** | **Implementation** | **Status** | **Validation** |
|---------------|-------------------|------------|----------------|
| **1. Proactive** | Privacy impact assessment before development | ✅ | DPIA completed |
| **2. Default** | Minimal data collection, privacy-first settings | ✅ | Configuration review |
| **3. Embedded** | Privacy controls in architecture, not add-on | ✅ | Architecture review |
| **4. Positive-Sum** | Full functionality with privacy | ✅ | Feature testing |
| **5. End-to-End** | Encryption, retention, deletion lifecycle | ✅ | Security audit |
| **6. Transparent** | Clear privacy policy, user controls | ✅ | User testing |
| **7. User-Centric** | User control, consent management | ✅ | UX review |

### 9.2 GDPR Rights Implementation Status

| **Right** | **Article** | **Feature** | **Status** |
|-----------|-------------|-------------|------------|
| Access | Art. 15 | View My Data dashboard | ✅ Designed |
| Rectification | Art. 16 | Edit profile, data correction request | ✅ Designed |
| Erasure | Art. 17 | Account deletion, data anonymization | ✅ Designed |
| Portability | Art. 20 | JSON/CSV/PDF export | ✅ Designed |
| Restriction | Art. 18 | Privacy settings, visibility controls | ✅ Designed |
| Object | Art. 21 | Consent withdrawal, opt-out | ✅ Designed |

---

## Related Documents

- **Phase 10**: [Security Architecture](./SECURITY_ARCHITECTURE.md)
- **Phase 10**: [Threat Model](./THREAT_MODEL.md)
- **Phase 10**: [Authentication & Authorization](./AUTHENTICATION_AUTHORIZATION.md)
- **Phase 6**: [Privacy Impact Assessment](../compliance/PRIVACY_IMPACT.md)
- **Phase 6**: [Data Governance](../compliance/DATA_GOVERNANCE.md)

---

*Document Version: 1.0*  
*Classification: Internal*  
*Phase: 10 - Security Architecture & Privacy Design*  
*Author: Stefan Kostyk*


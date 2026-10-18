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
| P003 | Notification | E-mail and in-app notifications about awards, reviews, roles and delegations (no SMS) | E-mail address, notification payloads, delivery log, preferences | Notifications 1 year (`expires_at`), preferences for the account lifetime |
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

Processing of the data needed to run the system rests on legitimate interest (DATA_GOVERNANCE §2.1), so it is not asked as a consent: a consent that the system cannot be used without would not be freely given (Art. 7(4)). The person acknowledges the privacy notice instead, and the acknowledgement is recorded per notice version.

| **Record** | **Purpose** | **Legal basis** | **Required** | **Granularity** | **Withdrawal Impact** |
|------------|-------------|-----------------|--------------|-----------------|----------------------|
| `DATA_PROCESSING` | Acknowledgement of a privacy notice version, at registration and after each new version | Legitimate interest (records that the notice was read) | Yes | Per notice version | Cannot be withdrawn; erasure (§5) ends the processing |
| `ANALYTICS` | Naming the person in rankings and comparisons of unit reports (Epic 5) | Consent | No | Binary | Counted only in totals |
| Award visibility (`awards.visibility`) | Showing an approved personal award beyond the person's unit (4.3.1) | Consent | No | Per award | Award back to `PRIVATE` at once |
| Document shown to colleagues (`documents.shown_to_colleagues`) | A chosen document of a shared award opened by signed-in colleagues | Consent | No | Per document | Document hidden at once |
| E-mail per notification type (`notification_preferences`) | E-mail and in-app chosen per notification type (1.3.2); security and privacy mails are always sent | Legitimate interest | No | Per notification type | No e-mail of that type |

The former consent types `PUBLIC_VISIBILITY`, `EMAIL_NOTIFICATIONS` and `SMS_NOTIFICATIONS` were removed (V040): the first two are chosen per award and per notification type, and there is no SMS channel. Metadata extraction from documents (`DOCUMENT_AI_PROCESSING`) is not built; it would need its own consent type before it is.

Showing documents to colleagues (4.3.8) follows the award's visibility choice (4.3.1): every document starts hidden, including those uploaded before the choice existed, and only the owner of an approved personal award shared at `UNIVERSITY` or `PUBLIC` can show one. Colleagues see the shown documents on the read-only achievement view and download them while signed in; the public page and anonymous visitors never receive a document. Returning the award to `PRIVATE` clears every choice, so sharing it again does not reopen documents silently. Each effective change is a `DOCUMENT_SHARING_CHANGED` audit row (the record of consent), each colleague download a `DOCUMENT_DOWNLOAD` row marked `via: colleague`, and the export lists the choice per document.

### 4.3 Technical Implementation

- `consent_records` is append-only (V040, `trg_consent_records_guard`): rows are never deleted or changed, except that erasure may clear `ip_address` and `user_agent`. The newest row per type is the current state; DATA_DICTIONARY §4.2 has the columns.
- The notice texts are static pages of the web client per version and language (`/privacy/<version>/<lang>.html`); `app.privacy.notice-version` names the version in force and `GET /api/v1/public/privacy-notice` publishes it with the earlier versions.
- Registration requires the current version (`privacyNoticeVersion`, otherwise 422 `outdated` and no account) and writes the `DATA_PROCESSING` row in the registration transaction. `PrivacyNoticeVersions` (shared `common` module) checks the version and publishes `PrivacyNoticeAcknowledged`; `ConsentService` (`gdpr`) appends the row, so `auth` does not depend on `gdpr`.
- `GET /users/me/consents`, `POST /users/me/consents/notice-acknowledgement` and `PUT /users/me/consents/ANALYTICS` read and change the caller's own records. A change that does not alter the state appends nothing; concurrent changes of one person are serialised by a row lock on the account. The overview never shows IP addresses or user agents.
- After a new notice version, the web client sends a signed-in person to the acknowledgement page before any other screen; the API does not refuse requests of a person who has not acknowledged (PRD 6.1, decision 2).
- Each appended row is audited (`PRIVACY_NOTICE_ACKNOWLEDGED`, `CONSENT_GRANTED`, `CONSENT_WITHDRAWN`, entity type `GDPR`), and the personal data export lists every row under `consent_history`.

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

**Request and grace period (Feature 6.1, 6.1.4)**

1. In the privacy centre the person reads what is erased and what is kept (approved awards under a pseudonym, redacted audit records, consent records for seven years), ticks the confirmation and enters the current password (`POST /api/v1/users/me/erasure-request`). A wrong password counts as a failed sign-in; five lock the account.
2. The request is refused, with nothing written, while another request is pending (`erasure-pending`), while a legal hold is active (`legal-hold`; the reason is not disclosed) and for the only active system administrator (`last-administrator`).
3. An accepted request is stored as `PENDING` in `erasure_requests` with `scheduled_for` 30 days later (`app.erasure.grace-period`), audited as `ERASURE_REQUESTED` and confirmed by a privacy mail with the date and a link to cancel. The shell shows a banner with the date on every page.
4. Until the date the person can cancel (`POST /api/v1/users/me/erasure-request/cancel`): `CANCELLED`, `ERASURE_CANCELLED`, a confirmation mail.
5. The nightly job (02:30 Kyiv time) mails one reminder seven days before the date (`app.erasure.warning-before`).
6. At the date the job erases the account unless a legal hold postpones it (6.1.5). The GDPR officer sees the pending date next to the account's legal holds.

### 5.2 Data Deletion Categories

| **Category** | **Deletion Strategy** | **Timeline** | **Exceptions** |
|--------------|----------------------|--------------|----------------|
| **User Profile** | Scrub: pseudonym `Erased #<id>`, address `erased-<id>@erased.invalid`, status `DELETED`; sign-in material deleted | 30 days | Legal holds postpone |
| **Awards** | Approved: kept `PRIVATE` under the pseudonym; all others deleted with requests, decisions and versions | 30 days | Public record retention |
| **Documents** | Full deletion, stored objects after the commit | 30 days | None |
| **Notifications** | Own deleted; the person's names in related notifications of others replaced by the pseudonym | 30 days | None |
| **Consent Records** | Kept without IP address and user agent; deleted seven years after the erasure (`fn_purge_consent_records`, 6.2.3) | 30 days | 7-year retention (proof of acknowledgement) |
| **Audit Logs** | Guarded redaction of values (`fn_redact_audit_subject`, ADR-024): personal keys `"[erased]"`, snapshots of deleted records `{"erased": true}`, own IP address and user agent removed; rows, actions and times kept; `AUDIT_REDACTED` records the counts | 30 days | 7-year retention for compliance |
| **Backups** | Marked for exclusion | Next backup cycle | Compliance requirements |
| **Analytics** | Anonymization | Immediate | Already anonymized |

The same erasure runs without a request of the person when the retention job files one (`erasure_requests.origin` `RETENTION`, 6.2.3): an unverified registration 30 days after its last verification link, a retired or memorial account seven years after its status changed. Such a request is due at once and carried out at the next 02:30 run with the same holds, last-administrator check and audit redaction, but without any mail (an unverified address was never confirmed, a former employee's is usually gone); `ERASURE_COMPLETED` and `ERASURE_POSTPONED` carry `origin: retention`, and a request whose account was verified, renewed or returned to service meanwhile is cancelled under the row locks.

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

Implemented for JSON by `GET /api/v1/users/me/export` (Feature 1.3, format version `1.1` since 2.1.8 (SCRUM-43): `award_versions` added; `1.2` since 4.3.4 (SCRUM-70): `review_decisions` and `decisions_made` added; `1.3` since 4.3.8 (SCRUM-74): `shown_to_colleagues` per document; `1.4` since 7.2.3 (SCRUM-68): `notifications` and `notification_preferences` added). Property names are snake_case; every list section is present, possibly empty. IP addresses are the person's own identifiers (Art. 15 and 20) and are exported unredacted; the activity log keeps the address only for actions the person takes themselves (sign-in, sign-out, verification, password reset, "not me" revocation, profile and address changes, refused requests, exports); events anybody can cause against an account (failed sign-ins, reset requests, lockouts) and events another person caused (roles, delegations) carry `null`, so no stranger's or administrator's address reaches the file.

```json
{
  "export_metadata": {
    "export_date": "2026-09-30T09:00:00Z",
    "user_id": 5,
    "format_version": "1.4",
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
      "recipient_unit": null, "created_at": "2026-09-20T09:00:00Z", "updated_at": "2026-09-20T09:00:00Z" }
  ],
  "award_versions": [
    { "award_id": 21, "version": 1, "action": "CREATED", "changed_fields": [], "created_at": "2026-09-20T09:00:00Z",
      "snapshot": { "title": "Letter of gratitude", "title_uk": "Подяка", "description": null, "description_uk": null,
        "awarding_organization": "МОН України", "award_date": "2025-05-01", "category_id": 13, "status": "DRAFT",
        "impact_score": null, "external_url": null, "organization_id": 64 } }
  ],
  "documents": [
    { "document_id": 7, "award_id": 21, "file_name": "certificate.pdf", "file_type": "PDF", "mime_type": "application/pdf",
      "file_size": 184320, "uploaded_at": "2026-09-20T09:05:00Z", "api_path": "/api/v1/documents/7",
      "shown_to_colleagues": false }
  ],
  "review_decisions": [
    { "award_id": 21, "award_title": "Letter of gratitude", "award_title_uk": "Подяка", "decision": "RETURNED",
      "level": "FACULTY_SECRETARY", "decided_at": "2026-10-01T09:00:00Z", "comments": "Додайте наказ",
      "reviewer": "Оксана Коваль", "delegator": null }
  ],
  "decisions_made": [
    { "award_id": 40, "award_title": "Diploma", "award_title_uk": "Диплом", "decision": "APPROVED", "level": "DEAN",
      "decided_at": "2026-10-02T10:00:00Z", "comments": "Погоджено", "reviewer": "Анастасія Петренко",
      "delegator": "Петро Мартинюк" }
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
  ],
  "notifications": [
    { "type": "AWARD_RETURNED", "channel": "EMAIL", "payload": { "title": "Certificate", "titleUk": "Грамота", "reviewer": "Ірина Бойко", "level": "FACULTY_SECRETARY" },
      "read": false, "sent_at": "2026-09-30T08:40:00Z", "read_at": null, "delivered_at": "2026-09-30T08:40:02Z", "delivery_status": "SENT" },
    { "type": "AWARD_RETURNED", "channel": "IN_APP", "payload": { "title": "Certificate", "titleUk": "Грамота", "reviewer": "Ірина Бойко", "level": "FACULTY_SECRETARY" },
      "read": true, "sent_at": "2026-09-30T08:40:00Z", "read_at": "2026-09-30T08:50:00Z", "delivered_at": null, "delivery_status": null }
  ],
  "notification_preferences": [
    { "type": "ROLE_ASSIGNED", "email": false, "in_app": true }
  ]
}
```

Award versions leave out who saved them, since a later reviewer is another person. Review decisions, oldest first, appear twice: `review_decisions` holds the decisions on the person's own awards with the reviewer's comment and full name (and the delegator's, when the reviewer acted under a delegation); `decisions_made` holds the decisions the person made as a reviewer, and those a delegate made under a role the person lent, with the comment, the award titles and the reviewer's and delegator's names; of the award only its titles are given (free text that may name its owner), nothing else of the award or its owner. `notifications` lists every in-app notification and the delivery log of the mails of the notification types, newest first, with the payload the sentence is built from (never a reviewer comment or corrected values); `notification_preferences` lists only the types the person changed, since a type without a row is on for both channels. The file never contains the password hash, one-time tokens or their hashes, authorization-server records, the audit triggers' row snapshots, device fingerprints, storage keys, or other people's data beyond the names of a delegation's other party, reviewers and delegators.

### 6.3 Technical Implementation

As built (Feature 1.3): the file is assembled synchronously from named columns (`PersonalDataAssembler`), returned as an attachment with `Cache-Control: no-store` and downloaded by the browser as a Blob, so the bearer token never appears in a URL. The stored temporary file and 24-hour download link of the sketch below are not used: one person's data is tens of kilobytes, and a bearer-less link would be one more secret to protect. Exports are limited to one per minute, audited as `DATA_EXPORT` (`entity_type` `GDPR`, section counts) and announced by email with time, IP and browser. CSV and PDF follow with the reporting engine (Epic 6).

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
| Access | Art. 15 | Privacy centre with the log of one's own actions, JSON export | ✅ Implemented (Features 1.3, 6.1) |
| Rectification | Art. 16 | Edit profile | ✅ Implemented (Feature 1.3); no separate correction-request flow |
| Erasure | Art. 17 | Erasure request with a 30-day grace period, erasure across modules, legal hold | ✅ Implemented (Feature 6.1) |
| Portability | Art. 20 | JSON export | ✅ Implemented (Feature 1.3); CSV and PDF not built, the JSON file meets Art. 20 (Epic 6 deviation 6) |
| Restriction | Art. 18 | Award visibility, document sharing and notification choices in the privacy centre | ✅ Implemented as choices (Features 2.x, 6.1); no restriction-of-processing flow |
| Object | Art. 21 | Consent withdrawal | ✅ Implemented for the optional `ANALYTICS` consent (Feature 6.1) |
| Storage limitation | Art. 5(1)(e) | Nightly retention job with configurable policies | ✅ Implemented (Feature 6.2) |

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


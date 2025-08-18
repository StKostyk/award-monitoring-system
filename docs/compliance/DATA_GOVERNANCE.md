# Data Governance Framework: Award Monitoring & Tracking System

**Project**: Award Monitoring & Tracking System for Ukrainian Universities  
**Context**: Solo Developer Portfolio Project (Free/Open Source)  
**Framework Version**: 1.0  
**Effective Date**: August 2025  
**Data Protection Officer**: Stefan Kostyk (Solo Developer)  
**Regulatory Scope**: GDPR, Ukrainian Data Protection Laws

---

## Executive Summary

This Data Governance Framework establishes comprehensive policies and procedures for data management within the Award Monitoring & Tracking System. The framework ensures GDPR compliance, implements privacy-by-design principles, and provides clear guidelines for data classification, handling, retention, and access control.

**Key Framework Components**:
- **Data Classification**: 4-tier classification system aligned with university security requirements
- **Retention Policies**: GDPR-compliant automated data lifecycle management
- **Access Controls**: Role-based access with principle of least privilege
- **Privacy Controls**: Built-in consent management and data subject rights

---

## 1. Data Classification

### **1.1 Classification Levels**

| **Classification** | **Definition** | **Examples** | **Access Level** | **Retention** |
|-------------------|----------------|--------------|------------------|---------------|
| **Public** | Information approved for public disclosure | Published awards, public profiles, achievement records | Anyone who is registered | Permanent |
| **Internal** | Information for internal university use | Department structures, workflow states, system configurations | Authenticated users | 7 years |
| **Confidential** | Sensitive information requiring protection | Personal contact details, consent records, audit logs | Authorized roles only | User-controlled/3-7 years |
| **Restricted** | Highly sensitive requiring special handling | Scanned certificates, assessment documents, appeals | Specific permissions | User-defined (1-10 years) |

### **1.2 Data Category Mapping**

```yaml
# Data Classification Schema
award_data:
  classification: Public
  categories:
    - award_title: Public
    - recipient_name: Public  
    - institution: Public
    - award_date: Public
    - award_type: Public
    - verification_status: Public

user_data:
  classification: Internal/Confidential
  categories:
    - basic_profile: Internal
    - contact_information: Confidential
    - consent_records: Confidential
    - activity_logs: Confidential

document_data:
  classification: Restricted
  categories:
    - scanned_certificates: Restricted
    - supporting_documents: Restricted
    - assessment_notes: Restricted
    - appeal_documents: Restricted

system_data:
  classification: Internal/Confidential
  categories:
    - application_logs: Internal
    - security_logs: Confidential
    - backup_data: Confidential
    - configuration_data: Internal
```

### **1.3 Data Handling Procedures**

| **Classification** | **Storage Requirements** | **Transmission** | **Processing** | **Disposal** |
|-------------------|-------------------------|------------------|----------------|--------------|
| **Public** | Standard encryption at rest | HTTPS/TLS 1.3 | No restrictions | Safe deletion after retntion |
| **Internal** | AES-256 encryption | HTTPS + authentication | Authorized users only | Secure wiping |
| **Confidential** | AES-256 + access logging | Encrypted + audit trail | Role-based access | Certified destruction |
| **Restricted** | AES-256 + key management | End-to-end encryption | Explicit permission | Cryptographic erasure |

---

## 2. Retention Policies

### **2.1 Data Retention Matrix**

| **Data Category** | **Legal Basis** | **Retention Period** | **Auto-Delete** | **User Control** |
|-------------------|-----------------|---------------------|-----------------|------------------|
| **Award Records** | Legitimate Interest | Permanent (public record) | No | Read-only after employment |
| **Employee Profiles** | Legitimate Interest | 7 years post-employment | Yes | Profile visibility control |
| **Contact Information** | Consent | User-controlled | No | Full user control |
| **Scanned Documents** | Explicit Consent | User-defined (1-10 years) | Yes | User-defined periods |
| **Consent Records** | Legal Obligation | 7 years from withdrawal | Yes | View-only for users |
| **System Logs** | Legitimate Interest | 3 years | Yes | No user control |
| **Audit Trails** | Legal Obligation | 7 years | Yes | No user control |

### **2.2 Automated Retention Implementation**

```java
// Data Retention Service Implementation
@Service
@Scheduled
public class DataRetentionService {
    
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void processRetentionPolicies() {
        
        // Process user-defined document retention
        processDocumentRetention();
        
        // Process employment-based profile retention  
        processProfileRetention();
        
        // Process system log retention
        processLogRetention();
        
        // Process consent record retention
        processConsentRetention();
    }
    
    private void processDocumentRetention() {
        List<Document> expiredDocs = documentRepository
            .findDocumentsWithExpiredRetention(LocalDate.now());
            
        for (Document doc : expiredDocs) {
            if (doc.isEligibleForDeletion()) {
                securelyDeleteDocument(doc);
                auditService.logDataDeletion(doc);
            }
        }
    }
    
    private void processProfileRetention() {
        List<User> expiredProfiles = userRepository
            .findUsersWithExpiredEmployment(LocalDate.now().minusYears(7));
            
        for (User user : expiredProfiles) {
            if (!user.hasActiveAwards()) {
                anonymizeUserProfile(user);
                auditService.logProfileAnonymization(user);
            }
        }
    }
}
```

### **2.3 Data Deletion Procedures**

```sql
-- Data Deletion Procedures
-- 1. Soft Delete (Initial)
UPDATE users SET 
    status = 'DELETED',
    deletion_requested_at = CURRENT_TIMESTAMP,
    scheduled_deletion_date = CURRENT_TIMESTAMP + INTERVAL '30 days'
WHERE user_id = ?;

-- 2. Grace Period Warning
SELECT user_id, email, scheduled_deletion_date 
FROM users 
WHERE status = 'DELETED' 
    AND scheduled_deletion_date <= CURRENT_TIMESTAMP + INTERVAL '7 days'
    AND deletion_warning_sent = FALSE;

-- 3. Hard Delete (After Grace Period)
BEGIN;
    -- Anonymize awards (keep achievement records)
    UPDATE awards SET recipient_name = 'ANONYMIZED_USER_' || user_id 
    WHERE user_id = ?;
    
    -- Delete personal data
    DELETE FROM user_profiles WHERE user_id = ?;
    DELETE FROM contact_information WHERE user_id = ?;
    DELETE FROM consent_records WHERE user_id = ?;
    
    -- Update user record to anonymous
    UPDATE users SET 
        full_name = NULL,
        email = NULL,
        status = 'ANONYMIZED'
    WHERE user_id = ?;
COMMIT;
```

---

## 3. Access Controls

### **3.1 Role-Based Access Control (RBAC)**

| **Role** | **Data Access** | **Permissions** | **Classification Limits** |
|----------|-----------------|-----------------|---------------------------|
| **Authenticated User** | Own data + public | View/edit own profile, submit awards | Public + own Internal/Confidential |
| **Faculty Admin** | Faculty data | Approve faculty awards, manage users | Public + Internal + Confidential |
| **System Admin** | All system data | Full system management | All classifications |
| **GDPR Officer** | Compliance data | Data subject rights, audit access | All for compliance purposes |

### **3.2 Access Control Implementation**

```java
// Spring Security Configuration
@Configuration
@EnableMethodSecurity
public class DataGovernanceSecurityConfig {
    
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or (hasRole('USER') and #userId == authentication.principal.id)")
    public UserProfile getUserProfile(Long userId) {
        return userService.getProfile(userId);
    }
    
    @PreAuthorize("@dataClassificationService.canAccess(authentication, #documentId)")
    public Document getDocument(Long documentId) {
        return documentService.getDocument(documentId);
    }
    
    @PostAuthorize("@dataClassificationService.filterSensitiveData(returnObject, authentication)")
    public List<Award> getAwards(AwardSearchCriteria criteria) {
        return awardService.searchAwards(criteria);
    }
}

@Service
public class DataClassificationService {
    
    public boolean canAccess(Authentication auth, Long documentId) {
        Document doc = documentRepository.findById(documentId);
        DataClassification classification = doc.getClassification();
        
        return switch (classification) {
            case PUBLIC -> true;
            case INTERNAL -> auth.isAuthenticated();
            case CONFIDENTIAL -> hasConfidentialAccess(auth, doc);
            case RESTRICTED -> hasRestrictedAccess(auth, doc);
        };
    }
    
    private boolean hasConfidentialAccess(Authentication auth, Document doc) {
        // Check if user is owner, department admin, or has specific permission
        return isDocumentOwner(auth, doc) || 
               hasRole(auth, "DEPARTMENT_ADMIN") ||
               hasSpecificPermission(auth, doc);
    }
}
```

---

## 4. Privacy Impact Assessment Framework

### **4.1 Data Collection Justification**

| **Data Type** | **Collection Purpose** | **Legal Basis** | **Necessity Assessment** |
|---------------|------------------------|-----------------|--------------------------|
| **Employee Names** | Award recognition and transparency | Legitimate Interest | Necessary - core functionality |
| **Faculty Information** | Award categorization and approval workflow | Legitimate Interest | Necessary - business process |
| **Contact Details** | System notifications and communication | Consent | Optional - user preference |
| **Scanned Certificates** | Award verification and authenticity | Explicit Consent | Optional - enhanced verification |
| **Activity Logs** | Security monitoring and audit compliance | Legitimate Interest | Necessary - security/compliance |

### **4.2 Processing Lawfulness Matrix**

```yaml
# GDPR Article 6 Lawful Basis Assessment
lawful_basis_assessment:
  legitimate_interest:
    purposes:
      - Institutional transparency
      - Award verification
      - Academic achievement recognition
    balancing_test:
      interests: Institutional accountability and public trust
      necessity: High - core mission alignment
      impact_on_rights: Low - publicly available information
      safeguards: Consent for sensitive data, anonymization options
      
  consent:
    purposes:
      - Marketing communications
      - Enhanced document processing
      - Analytics and usage tracking
    requirements:
      - Freely given
      - Specific and informed
      - Withdrawable
      - Granular consent options
      
  legal_obligation:
    purposes:
      - Audit trail maintenance
      - Breach notification
      - Regulatory reporting
    sources:
      - GDPR compliance requirements
      - Ukrainian data protection laws
      - University audit policies
```

### **4.3 Data Subject Rights Implementation**

| **Right (GDPR Article)** | **Implementation** | **Response Time** | **Automation Level** |
|--------------------------|-------------------|-------------------|----------------------|
| **Information (13-14)** | Privacy policy, consent forms | Immediate | Fully automated |
| **Access (15)** | Self-service data export | 1 month | Fully automated |
| **Rectification (16)** | Profile editing interface | Immediate | Fully automated |
| **Erasure (17)** | Account deletion workflow | 1 month | Semi-automated |
| **Restrict Processing (18)** | Account suspension options | 1-3 days | Semi-automated |
| **Data Portability (20)** | JSON/CSV export functionality | 1 month | Fully automated |
| **Object (21)** | Opt-out mechanisms | Immediate | Fully automated |

### **4.4 Cross-Border Transfer Mechanisms**

```yaml
# International Data Transfer Framework
transfer_mechanisms:
  ukraine_to_eu:
    mechanism: Adequacy Decision (if available) or Standard Contractual Clauses
    assessment: Ukraine working toward GDPR adequacy
    safeguards: Encryption in transit, access logging
    
  cloud_providers:
    aws_eu:
      mechanism: Standard Contractual Clauses
      data_localization: EU regions only
      processor_certification: SOC 2, ISO 27001
      
    azure_eu:
      mechanism: Standard Contractual Clauses  
      data_localization: EU regions only
      processor_certification: SOC 2, ISO 27001
      
  data_minimization:
    principles:
      - Collect only necessary data
      - Process in jurisdiction of origin when possible
      - Implement data residency controls
      - Regular data mapping and flow analysis
```

---

## 5. Governance Implementation

### **5.1 Data Governance Roles & Responsibilities**

| **Role** | **Responsibilities** | **Authority** | **Reporting** |
|----------|---------------------|---------------|---------------|
| **Data Protection Officer** | GDPR compliance, privacy policies, breach response | Policy approval, audit authority | Rector, Legal Team |
| **Data Owner** | Data accuracy, access approval, retention decisions | Business rules, user access | Department Head |
| **Data Steward** | Daily data management, quality assurance | Operational procedures | Data Owner |
| **System Administrator** | Technical implementation, security controls | System configuration | IT Manager |

### **5.2 Compliance Monitoring & Reporting**

```java
// Compliance Monitoring Service
@Service
public class ComplianceMonitoringService {
    
    @Scheduled(cron = "0 0 1 * * ?") // Daily compliance check
    public void generateComplianceReport() {
        ComplianceReport report = ComplianceReport.builder()
            .reportDate(LocalDate.now())
            .dataClassificationAudit(auditDataClassification())
            .retentionComplianceCheck(checkRetentionCompliance())
            .accessControlAudit(auditAccessControls())
            .consentValidityCheck(validateConsents())
            .privacyRightsStatus(checkPrivacyRightsRequests())
            .build();
            
        if (report.hasViolations()) {
            alertComplianceOfficer(report);
        }
        
        complianceReportRepository.save(report);
    }
    
    private DataClassificationAudit auditDataClassification() {
        // Verify all data has proper classification
        List<UnclassifiedData> unclassified = dataRepository.findUnclassifiedData();
        
        return DataClassificationAudit.builder()
            .totalRecords(dataRepository.count())
            .classifiedRecords(dataRepository.countClassified())
            .unclassifiedRecords(unclassified.size())
            .compliancePercentage(calculateCompliancePercentage())
            .build();
    }
}
```

### **5.3 Data Governance Metrics**

| **Metric** | **Target** | **Measurement** | **Frequency** |
|------------|------------|-----------------|---------------|
| **Data Classification Coverage** | 100% | Classified records / Total records | Daily |
| **Retention Policy Compliance** | 95% | Compliant deletions / Required deletions | Weekly |
| **Access Control Violations** | 0 | Unauthorized access attempts | Real-time |
| **Privacy Rights Response Time** | <30 days | Average response time to requests | Monthly |
| **Consent Validity Rate** | 90% | Valid consents / Total consents | Monthly |

---

## 6. Implementation Timeline

### **6.1 Data Governance Implementation Phases**

| **Phase** | **Duration** | **Deliverables** | **Success Criteria** |
|-----------|--------------|------------------|----------------------|
| **Phase 1: Foundation** | Month 1 | Data classification, basic policies | 100% data classified |
| **Phase 2: Automation** | Month 2 | Retention automation, access controls | Automated retention processing |
| **Phase 3: Monitoring** | Month 3 | Compliance monitoring, reporting | Daily compliance reports |
| **Phase 4: Optimization** | Month 4 | Performance tuning, user training | >95% compliance metrics |

### **6.2 Training & Awareness**

```markdown
# Data Governance Training Plan

## Role-Based Training
- **All Users**: Data classification basics, privacy rights
- **Administrators**: Access control management, retention policies  
- **Compliance Officer**: GDPR requirements, audit procedures
- **Developers**: Privacy by design, secure coding practices

## Training Materials
- Interactive e-learning modules
- Quick reference guides
- Video tutorials
- Compliance checklists

## Assessment & Certification
- Knowledge assessments for each role
- Annual recertification requirements
- Compliance competency tracking
```

---

## 7. Review & Updates

### **7.1 Framework Maintenance**

- **Quarterly Review**: Policy updates, regulatory changes
- **Annual Assessment**: Complete framework evaluation
- **Incident-Driven Updates**: Response to breaches or violations
- **Regulatory Updates**: Adaptation to new legal requirements

### **7.2 Version Control**

| **Version** | **Date** | **Changes** | **Approver** |
|-------------|----------|-------------|--------------|
| 1.0 | August 2025 | Initial framework | Stefan Kostyk |
| 1.1 | TBD | Regulatory updates | TBD |

---

*Document Version: 1.0*  
*Classification: Internal*  
*Next Review: November 2025*  
*Data Protection Officer: Stefan Kostyk (Solo Developer)* 
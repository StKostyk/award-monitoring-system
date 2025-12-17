# Security Architecture
## Award Monitoring & Tracking System

> **Phase 10 Deliverable**: Security Architecture & Privacy Design  
> **Document Version**: 1.0  
> **Last Updated**: December 2025  
> **Author**: Stefan Kostyk  
> **Security Model**: Zero Trust Architecture  
> **Classification**: Internal

---

## Executive Summary

This document defines the comprehensive security architecture for the Award Monitoring & Tracking System, implementing a Zero Trust security model across all system layers. The architecture ensures protection of sensitive academic data while maintaining public transparency requirements and full GDPR compliance.

### Security Objectives

| **Objective** | **Description** | **Success Criteria** |
|---------------|-----------------|---------------------|
| **Confidentiality** | Protect sensitive personal and credential data | Zero unauthorized data access incidents |
| **Integrity** | Ensure award data accuracy and tamper-resistance | 100% data integrity verification |
| **Availability** | Maintain system uptime and responsiveness | 99.9% SLA compliance |
| **Non-repudiation** | Provide audit trails for all critical actions | Complete audit log coverage |
| **Compliance** | Meet GDPR and regulatory requirements | Zero compliance violations |

---

## 1. Zero Trust Architecture Model

### 1.1 Core Principles

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        ZERO TRUST SECURITY MODEL                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐            │
│  │ VERIFY IDENTITY │  │  LEAST PRIVILEGE│  │  ASSUME BREACH  │            │
│  │                 │  │                 │  │                 │            │
│  │ - MFA Required  │  │ - Minimal Access│  │ - Segment All   │            │
│  │ - Device Trust  │  │ - Time-Limited  │  │ - Monitor All   │            │
│  │ - Context-Aware │  │ - Just-In-Time  │  │ - Log Everything│            │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘            │
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐            │
│  │  ENCRYPT DATA   │  │ MICRO-SEGMENT   │  │ CONTINUOUS AUTH │            │
│  │                 │  │                 │  │                 │            │
│  │ - At Rest       │  │ - Network Zones │  │ - Session Valid │            │
│  │ - In Transit    │  │ - Service Mesh  │  │ - Token Refresh │            │
│  │ - Field-Level   │  │ - Pod Security  │  │ - Risk Scoring  │            │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Trust Boundaries

| **Boundary** | **Components** | **Trust Level** | **Access Controls** |
|--------------|----------------|-----------------|---------------------|
| **External** | Internet, Public APIs | Untrusted | WAF, Rate Limiting, DDoS Protection |
| **DMZ** | API Gateway, Load Balancer | Low Trust | TLS Termination, Authentication |
| **Application** | Microservices, Business Logic | Medium Trust | Service Mesh, mTLS |
| **Data** | Database, Cache, Storage | High Trust | Encryption, Access Policies |
| **Management** | Admin Tools, CI/CD | Highest Trust | MFA, Privileged Access |

### 1.3 Security Zones Architecture

```
                              INTERNET
                                 │
                    ┌────────────┴────────────┐
                    │      PERIMETER ZONE     │
                    │   - CloudFlare WAF      │
                    │   - DDoS Protection     │
                    │   - Rate Limiting       │
                    └────────────┬────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │         DMZ ZONE        │
                    │   - API Gateway         │
                    │   - Load Balancer       │
                    │   - TLS Termination     │
                    └────────────┬────────────┘
                                 │
    ┌────────────────────────────┼────────────────────────────┐
    │                  APPLICATION ZONE                        │
    │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐│
    │  │ Auth     │  │ Award    │  │ Workflow │  │ Document ││
    │  │ Service  │  │ Service  │  │ Service  │  │ Service  ││
    │  └──────────┘  └──────────┘  └──────────┘  └──────────┘│
    │                  Service Mesh (Istio mTLS)              │
    └────────────────────────────┬────────────────────────────┘
                                 │
    ┌────────────────────────────┼────────────────────────────┐
    │                     DATA ZONE                            │
    │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐│
    │  │PostgreSQL│  │  Redis   │  │  Kafka   │  │ S3/Blob  ││
    │  │(Encrypted│  │(Encrypted│  │(Encrypted│  │(Encrypted││
    │  └──────────┘  └──────────┘  └──────────┘  └──────────┘│
    └─────────────────────────────────────────────────────────┘
```

---

## 2. Security Layers Implementation

### 2.1 Identity & Access Management (IAM)

| **Component** | **Technology** | **Implementation** | **Compliance** |
|---------------|----------------|-------------------|----------------|
| **Identity Provider** | OAuth2 / OpenID Connect | Spring Authorization Server | NIST 800-63 |
| **Authentication** | JWT + Refresh Tokens | Spring Security 6+ | OWASP ASVS |
| **Authorization** | RBAC + ABAC Hybrid | Spring Security Method Security | ISO 27001 |
| **MFA** | TOTP, SMS, Email | Google Authenticator Compatible | NIST 800-63B |
| **Session Management** | Stateless JWT + Redis | Token Blacklisting | CIS Controls |

#### IAM Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    IDENTITY & ACCESS MANAGEMENT                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   User Request                                                              │
│        │                                                                    │
│        ▼                                                                    │
│   ┌─────────────────┐                                                       │
│   │  API Gateway    │                                                       │
│   │  (Rate Limit)   │                                                       │
│   └────────┬────────┘                                                       │
│            │                                                                │
│            ▼                                                                │
│   ┌─────────────────┐     ┌─────────────────┐                              │
│   │  Authentication │────▶│  OAuth2 Server  │                              │
│   │  Filter         │     │  - Token Issue  │                              │
│   └────────┬────────┘     │  - Token Valid  │                              │
│            │              │  - MFA Verify   │                              │
│            │              └─────────────────┘                              │
│            ▼                                                                │
│   ┌─────────────────┐     ┌─────────────────┐                              │
│   │  Authorization  │────▶│  Policy Engine  │                              │
│   │  Filter         │     │  - RBAC Rules   │                              │
│   └────────┬────────┘     │  - ABAC Rules   │                              │
│            │              │  - Context      │                              │
│            │              └─────────────────┘                              │
│            ▼                                                                │
│   ┌─────────────────┐                                                       │
│   │  Application    │                                                       │
│   │  Service        │                                                       │
│   └─────────────────┘                                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Network Security

| **Layer** | **Technology** | **Configuration** | **Compliance** |
|-----------|----------------|-------------------|----------------|
| **Transport** | TLS 1.3 | ECDHE-RSA-AES256-GCM-SHA384 | PCI-DSS |
| **Web Application Firewall** | CloudFlare/AWS WAF | OWASP CRS 3.x | OWASP |
| **DDoS Protection** | CloudFlare/AWS Shield | Rate Limiting + Geo-blocking | SOC 2 |
| **Service Mesh** | Istio | mTLS between services | Zero Trust |
| **Network Policies** | Kubernetes NetworkPolicy | Deny-by-default | CIS Benchmarks |

#### TLS Configuration

```yaml
# TLS 1.3 Configuration
tls_configuration:
  minimum_version: TLS1.3
  cipher_suites:
    - TLS_AES_256_GCM_SHA384
    - TLS_CHACHA20_POLY1305_SHA256
    - TLS_AES_128_GCM_SHA256
  certificate:
    algorithm: RSA-4096 or ECDSA P-384
    validity: 90 days (automated renewal)
    issuer: Let's Encrypt / AWS ACM
  hsts:
    enabled: true
    max_age: 31536000
    include_subdomains: true
    preload: true
```

### 2.3 Data Protection

| **Data State** | **Encryption** | **Key Management** | **Compliance** |
|----------------|----------------|-------------------|----------------|
| **At Rest** | AES-256-GCM | AWS KMS / HashiCorp Vault | GDPR Art. 32 |
| **In Transit** | TLS 1.3 | Certificate Manager | PCI-DSS |
| **In Processing** | Field-Level Encryption | Application Keys | HIPAA |
| **Backups** | AES-256-CBC | Separate Backup Keys | SOC 2 |

#### Encryption Key Hierarchy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       ENCRYPTION KEY HIERARCHY                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                    ┌─────────────────────────┐                              │
│                    │   MASTER KEY (KEK)      │                              │
│                    │   - HSM Protected       │                              │
│                    │   - Never Exported      │                              │
│                    └───────────┬─────────────┘                              │
│                                │                                            │
│           ┌────────────────────┼────────────────────┐                       │
│           │                    │                    │                       │
│           ▼                    ▼                    ▼                       │
│   ┌───────────────┐   ┌───────────────┐   ┌───────────────┐               │
│   │ Database DEK  │   │ Document DEK  │   │  Backup DEK   │               │
│   │ - Per-table   │   │ - Per-file    │   │ - Per-backup  │               │
│   │ - Rotated     │   │ - User-bound  │   │ - Rotated     │               │
│   └───────────────┘   └───────────────┘   └───────────────┘               │
│                                                                             │
│   Key Rotation Schedule:                                                    │
│   - Master Key: Annual (manual)                                             │
│   - Database DEK: Quarterly (automated)                                     │
│   - Document DEK: Monthly (automated)                                       │
│   - Backup DEK: Per backup (automated)                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.4 Application Security

| **Category** | **Controls** | **Implementation** | **Validation** |
|--------------|--------------|-------------------|----------------|
| **Input Validation** | Whitelist validation | Bean Validation (JSR-380) | Unit tests |
| **Output Encoding** | Context-aware encoding | OWASP Java Encoder | Integration tests |
| **SQL Injection** | Parameterized queries | JPA/Hibernate | SAST scanning |
| **XSS Prevention** | CSP + Sanitization | Spring Security headers | DAST scanning |
| **CSRF Protection** | Synchronizer tokens | Spring Security CSRF | Penetration tests |
| **Dependency Security** | Vulnerability scanning | OWASP Dependency Check | CI/CD gates |

#### Security Headers Configuration

```java
// Spring Security Headers Configuration
@Configuration
public class SecurityHeadersConfig {
    
    @Bean
    public SecurityFilterChain securityHeaders(HttpSecurity http) throws Exception {
        return http
            .headers(headers -> headers
                // Content Security Policy
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data: https:; " +
                        "font-src 'self'; " +
                        "frame-ancestors 'none'; " +
                        "form-action 'self'"
                    ))
                // HTTP Strict Transport Security
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
                    .preload(true))
                // X-Content-Type-Options
                .contentTypeOptions(Customizer.withDefaults())
                // X-Frame-Options
                .frameOptions(frame -> frame.deny())
                // Referrer Policy
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // Permissions Policy
                .permissionsPolicy(permissions -> permissions
                    .policy("geolocation=(), camera=(), microphone=()")))
            .build();
    }
}
```

### 2.5 Infrastructure Security

| **Component** | **Security Control** | **Implementation** | **Compliance** |
|---------------|---------------------|-------------------|----------------|
| **Containers** | Image scanning | Trivy, Clair | CIS Docker Benchmark |
| **Kubernetes** | Pod Security Standards | Restricted policy | CIS Kubernetes Benchmark |
| **Secrets** | External secrets | HashiCorp Vault / K8s Secrets | SOC 2 |
| **Logging** | Centralized audit logs | ELK Stack | GDPR Art. 30 |
| **Monitoring** | Security metrics | Prometheus + Grafana | ISO 27001 |

#### Kubernetes Security Configuration

```yaml
# Pod Security Policy
apiVersion: v1
kind: Pod
metadata:
  name: award-service
spec:
  securityContext:
    runAsNonRoot: true
    runAsUser: 1000
    fsGroup: 1000
    seccompProfile:
      type: RuntimeDefault
  containers:
  - name: app
    image: award-service:latest
    securityContext:
      allowPrivilegeEscalation: false
      readOnlyRootFilesystem: true
      capabilities:
        drop:
          - ALL
    resources:
      limits:
        memory: "1Gi"
        cpu: "500m"
      requests:
        memory: "512Mi"
        cpu: "250m"
```

---

## 3. Security Controls Matrix

### 3.1 OWASP Top 10 Mitigations

| **Vulnerability** | **Risk Level** | **Mitigation** | **Implementation** | **Validation** |
|-------------------|----------------|----------------|-------------------|----------------|
| **A01:2021 Broken Access Control** | Critical | RBAC + Method Security | Spring Security @PreAuthorize | Authorization tests |
| **A02:2021 Cryptographic Failures** | High | AES-256 + TLS 1.3 | JCE + Spring Security | Encryption audits |
| **A03:2021 Injection** | Critical | Parameterized queries | JPA + Validation | SAST + DAST |
| **A04:2021 Insecure Design** | High | Threat modeling | STRIDE analysis | Architecture reviews |
| **A05:2021 Security Misconfiguration** | Medium | Secure defaults | Spring profiles | Configuration audits |
| **A06:2021 Vulnerable Components** | High | Dependency scanning | OWASP DC + Snyk | CI/CD gates |
| **A07:2021 Auth Failures** | Critical | MFA + Rate limiting | Spring Security | Penetration tests |
| **A08:2021 Software & Data Integrity** | High | Code signing + SBOMs | Sigstore + CycloneDX | Supply chain audits |
| **A09:2021 Security Logging** | Medium | Centralized logging | ELK + Audit tables | Log analysis |
| **A10:2021 SSRF** | Medium | URL validation | Allowlisting | Security tests |

### 3.2 Defense in Depth Layers

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DEFENSE IN DEPTH                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Layer 7 (Application)                                                      │
│  ├── Input Validation                                                       │
│  ├── Output Encoding                                                        │
│  ├── Authentication & Authorization                                         │
│  └── Session Management                                                     │
│                                                                             │
│  Layer 6 (Service)                                                          │
│  ├── API Gateway Security                                                   │
│  ├── Rate Limiting                                                          │
│  ├── Service-to-Service mTLS                                               │
│  └── Circuit Breakers                                                       │
│                                                                             │
│  Layer 5 (Network)                                                          │
│  ├── Web Application Firewall                                               │
│  ├── Network Segmentation                                                   │
│  ├── DDoS Protection                                                        │
│  └── TLS/mTLS                                                              │
│                                                                             │
│  Layer 4 (Infrastructure)                                                   │
│  ├── Container Security                                                     │
│  ├── Kubernetes Security Policies                                           │
│  ├── Host Hardening                                                        │
│  └── Secret Management                                                      │
│                                                                             │
│  Layer 3 (Data)                                                             │
│  ├── Encryption at Rest                                                     │
│  ├── Field-Level Encryption                                                 │
│  ├── Database Access Controls                                               │
│  └── Backup Encryption                                                      │
│                                                                             │
│  Layer 2 (Physical)                                                         │
│  ├── Cloud Provider Security                                                │
│  ├── Data Center Controls                                                   │
│  └── Hardware Security Modules                                              │
│                                                                             │
│  Layer 1 (Governance)                                                       │
│  ├── Security Policies                                                      │
│  ├── Compliance Frameworks                                                  │
│  ├── Security Training                                                      │
│  └── Incident Response                                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Security Monitoring & Incident Response

### 4.1 Security Monitoring Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SECURITY MONITORING STACK                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐    │
│  │ Application │   │   Network   │   │Infrastructure│   │   Cloud     │    │
│  │   Logs      │   │    Logs     │   │    Logs     │   │   Logs      │    │
│  └──────┬──────┘   └──────┬──────┘   └──────┬──────┘   └──────┬──────┘    │
│         │                 │                 │                 │            │
│         └─────────────────┼─────────────────┼─────────────────┘            │
│                           │                 │                              │
│                           ▼                 ▼                              │
│                    ┌─────────────────────────────┐                         │
│                    │      LOG AGGREGATOR         │                         │
│                    │   (Elasticsearch / Loki)    │                         │
│                    └─────────────┬───────────────┘                         │
│                                  │                                         │
│                    ┌─────────────┴───────────────┐                         │
│                    │                             │                         │
│                    ▼                             ▼                         │
│           ┌───────────────┐            ┌───────────────┐                  │
│           │    SIEM       │            │   Alerting    │                  │
│           │ - Correlation │            │ - PagerDuty   │                  │
│           │ - Anomaly     │            │ - Slack       │                  │
│           │ - Threat Intel│            │ - Email       │                  │
│           └───────────────┘            └───────────────┘                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Security Events & Alerts

| **Event Category** | **Detection Method** | **Alert Severity** | **Response Time** |
|-------------------|---------------------|-------------------|------------------|
| **Authentication Failure (>5 attempts)** | Rate counting | High | 5 minutes |
| **Privilege Escalation Attempt** | Audit log analysis | Critical | Immediate |
| **Data Exfiltration Pattern** | Anomaly detection | Critical | Immediate |
| **SQL Injection Attempt** | WAF + App logs | High | 15 minutes |
| **Unauthorized API Access** | Access log analysis | Medium | 1 hour |
| **Configuration Change** | Change detection | Medium | 1 hour |
| **Certificate Expiry** | Monitoring | Low | 24 hours |

### 4.3 Incident Response Procedures

| **Phase** | **Actions** | **Responsible** | **Timeline** |
|-----------|-------------|-----------------|--------------|
| **Detection** | Log analysis, alert triage, initial assessment | Security Monitoring | < 5 min |
| **Containment** | Isolate systems, block IPs, revoke tokens | Incident Response | < 30 min |
| **Eradication** | Remove threat, patch vulnerabilities | Security Team | < 4 hours |
| **Recovery** | Restore services, validate security | Operations | < 8 hours |
| **Post-Incident** | Root cause analysis, documentation | All Teams | < 72 hours |

---

## 5. Compliance Mapping

### 5.1 Regulatory Compliance Matrix

| **Requirement** | **GDPR** | **ISO 27001** | **SOC 2** | **Implementation** |
|-----------------|----------|---------------|-----------|-------------------|
| **Access Control** | Art. 32 | A.9 | CC6.1 | RBAC + MFA |
| **Encryption** | Art. 32 | A.10 | CC6.1 | AES-256 + TLS 1.3 |
| **Audit Logging** | Art. 30 | A.12.4 | CC7.2 | Centralized logging |
| **Incident Response** | Art. 33 | A.16 | CC7.3 | IR procedures |
| **Data Protection** | Art. 25 | A.18 | CC6.6 | Privacy by design |
| **Vulnerability Management** | Art. 32 | A.12.6 | CC7.1 | Regular scanning |

### 5.2 Security Certifications Roadmap

| **Certification** | **Target Date** | **Status** | **Requirements** |
|-------------------|-----------------|------------|------------------|
| **SOC 2 Type I** | Q4 2025 | Planned | Security controls documentation |
| **SOC 2 Type II** | Q2 2026 | Planned | 6-month audit period |
| **ISO 27001** | Q4 2026 | Planned | Full ISMS implementation |

---

## 6. Security Metrics & KPIs

### 6.1 Security Performance Indicators

| **Metric** | **Target** | **Measurement** | **Frequency** |
|------------|------------|-----------------|---------------|
| **Mean Time to Detection (MTTD)** | < 5 minutes | Alert latency | Real-time |
| **Mean Time to Response (MTTR)** | < 30 minutes | Response time | Per incident |
| **Vulnerability Remediation** | < 7 days (critical) | Patch time | Weekly |
| **Security Test Coverage** | > 90% | Test reports | Per release |
| **False Positive Rate** | < 5% | Alert analysis | Monthly |
| **Security Training Completion** | 100% | Training records | Annual |

### 6.2 Security Dashboard Metrics

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      SECURITY DASHBOARD                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐         │
│  │ VULNERABILITIES  │  │  INCIDENTS (MTD) │  │ COMPLIANCE SCORE │         │
│  │                  │  │                  │  │                  │         │
│  │  Critical: 0     │  │  Total: 12       │  │      95%         │         │
│  │  High: 3         │  │  Resolved: 10    │  │                  │         │
│  │  Medium: 12      │  │  Open: 2         │  │  ████████████░   │         │
│  │  Low: 28         │  │  MTTR: 23 min    │  │                  │         │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘         │
│                                                                             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐         │
│  │  AUTH FAILURES   │  │   WAF BLOCKS     │  │  ENCRYPTION      │         │
│  │                  │  │                  │  │                  │         │
│  │  Last 24h: 156   │  │  SQL Injection:  │  │  At Rest: 100%   │         │
│  │  Blocked IPs: 8  │  │  47              │  │  In Transit:100% │         │
│  │  MFA Enabled:    │  │  XSS Attempts:   │  │  Field-Level:    │         │
│  │  98%             │  │  23              │  │  87%             │         │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Implementation Roadmap

### 7.1 Security Implementation Phases

| **Phase** | **Duration** | **Focus Areas** | **Deliverables** |
|-----------|--------------|-----------------|------------------|
| **Phase 1** | Month 1 | Authentication, Basic Authorization | OAuth2, JWT, RBAC |
| **Phase 2** | Month 2 | Encryption, Network Security | TLS, Data Encryption, WAF |
| **Phase 3** | Month 3 | Monitoring, Logging | SIEM, Audit Logs, Alerts |
| **Phase 4** | Month 4 | Hardening, Compliance | Security Scanning, Documentation |

### 7.2 Security Testing Schedule

| **Test Type** | **Frequency** | **Scope** | **Responsible** |
|---------------|---------------|-----------|-----------------|
| **SAST** | Every commit | Source code | CI/CD Pipeline |
| **DAST** | Weekly | Running application | Security Team |
| **Dependency Scan** | Daily | Dependencies | CI/CD Pipeline |
| **Penetration Test** | Quarterly | Full application | External Vendor |
| **Security Audit** | Annual | Full system | External Auditor |

---

## Related Documents

- **Phase 6**: [Security Framework (Compliance)](../compliance/SECURITY_FRAMEWORK.md)
- **Phase 6**: [Privacy Impact Assessment](../compliance/PRIVACY_IMPACT.md)
- **Phase 7**: [ADR-009 Security Framework](../architecture/adr/ADR-009-Security-Framework.md)
- **Phase 10**: [Threat Model](./THREAT_MODEL.md)
- **Phase 10**: [Authentication & Authorization](./AUTHENTICATION_AUTHORIZATION.md)
- **Phase 10**: [Privacy by Design](./PRIVACY_BY_DESIGN.md)

---

*Document Version: 1.0*  
*Classification: Internal*  
*Phase: 10 - Security Architecture & Privacy Design*  
*Author: Stefan Kostyk*


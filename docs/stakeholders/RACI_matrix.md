# RACI Matrix: Roles & Responsibilities

## Legend
- **R = Responsible:** Performs the work to complete the task
- **A = Accountable:** Ultimately answerable for completion and sign-off
- **C = Consulted:** Provides input and expertise (two-way communication)
- **I = Informed:** Kept up-to-date on progress (one-way communication)

## 1. Core Project Activities

| Task / Activity | Business Sponsor (Rector) | End Users (Employees) | Reviewers (Secretaries/Deans) | System Ops | Security (InfoSec) | Compliance (GDPR) | Development Team |
|-----------------|---------------------------|----------------------|-------------------------------|------------|-------------------|-------------------|------------------|
| **Define project scope & objectives** | A | I | C | I | C | C | R |
| **Approve project charter** | A | I | C | I | I | C | C |
| **Configure data retention policies** | C | I | I | I | C | A | R |
| **Design system architecture** | C | I | C | C | A | C | R |
| **Implement security controls** | I | I | I | C | A | C | R |
| **Deploy GDPR compliance features** | C | I | I | I | C | A | R |
| **Conduct user acceptance testing** | I | A | R | I | I | I | C |
| **Approve production deployment** | A | I | C | C | C | C | R |

## 2. Operational Activities

| Task / Activity | Business Sponsor (Rector) | End Users (Employees) | Reviewers (Secretaries/Deans) | System Ops | Security (InfoSec) | Compliance (GDPR) | Development Team |
|-----------------|---------------------------|----------------------|-------------------------------|------------|-------------------|-------------------|------------------|
| **Submit award requests** | I | R | I | I | I | I | I |
| **Review & approve award requests** | I | I | A/R | I | I | I | I |
| **Escalate approval decisions** | A | I | R | I | I | I | I |
| **Configure approval workflows** | C | C | R | I | A | C | C |
| **Manage user accounts & roles** | C | I | C | R | C | I | A |
| **Monitor system performance** | I | I | I | A/R | I | I | C |
| **Backup & disaster recovery** | I | I | I | A/R | C | I | C |
| **Handle security incidents** | I | I | I | C | A/R | C | C |

## 3. Development & Maintenance Activities

| Task / Activity | Business Sponsor (Rector) | End Users (Employees) | Reviewers (Secretaries/Deans) | System Ops | Security (InfoSec) | Compliance (GDPR) | Development Team |
|-----------------|---------------------------|----------------------|-------------------------------|------------|-------------------|-------------------|------------------|
| **Parser model training & tuning** | I | C | C | I | I | I | A/R |
| **Implement new features** | C | C | C | I | C | I | A/R |
| **Release new policy templates** | C | I | C | I | A | C | R |
| **Conduct security audits** | I | I | I | C | A/R | C | C |
| **Perform compliance audits** | I | I | I | I | C | A/R | C |
| **Apply system patches & updates** | I | I | I | A/R | C | I | C |
| **Manage technical debt** | I | I | I | I | I | I | A/R |

## 4. Decision-Making Authority Matrix

### Strategic Decisions (Rector Level)
| Decision Type | Primary Authority | Required Approvals | Escalation Path |
|---------------|-------------------|-------------------|-----------------|
| **Project Scope Changes** | Rector | Development Team consultation | N/A (Top Level) |
| **Budget Allocation** | Rector | N/A | N/A (Top Level) |
| **Policy Framework** | Rector | GDPR Officer, Dean consultation | N/A (Top Level) |
| **Go-Live Decision** | Rector | All stakeholders consultation | N/A (Top Level) |

### Tactical Decisions (Management Level)
| Decision Type | Primary Authority | Required Approvals | Escalation Path |
|---------------|-------------------|-------------------|-----------------|
| **Workflow Configuration** | Dean/Secretary | End User consultation | Rector (if conflict) |
| **User Role Assignments** | Dean | GDPR Officer consultation | Rector (for executives) |
| **Feature Prioritization** | Dean | Development Team input | Rector (if budget impact) |
| **Training Requirements** | Dean | N/A | Rector (if resource impact) |

### Technical Decisions (Implementation Level)
| Decision Type | Primary Authority | Required Approvals | Escalation Path |
|---------------|-------------------|-------------------|-----------------|
| **Architecture Changes** | Development Team | Dean consultation | Dean → Rector |
| **Security Implementation** | Development Team | Dean consultation | Dean → Rector |
| **Compliance Implementation** | Development Team | Dean + GDPR consultation | Dean → Rector |
| **Performance Optimization** | Development Team | System Ops consultation | Dean (if service impact) |

### Operational Decisions (Day-to-Day Level)
| Decision Type | Primary Authority | Required Approvals | Escalation Path |
|---------------|-------------------|-------------------|-----------------|
| **Award Approval** | Secretary/Dean | N/A | Next level reviewer |
| **User Support** | System Ops | N/A | Development Team |
| **Routine Maintenance** | System Ops | N/A | Development Team |
| **Incident Response** | InfoSec Team | System Ops coordination | Dean → Rector |

## 5. Escalation Paths & Procedures

### Technical Escalation Path
```
Level 1: Development Team
    ↓ (If unresolved in 24 hours)
Level 2: System Operations + InfoSec Team  
    ↓ (If unresolved in 48 hours)
Level 3: Dean (Prof. Martynyuk)
    ↓ (If unresolved in 72 hours)
Level 4: Rector (Prof. Biloskurskyi)
```

### Business Escalation Path
```
Level 1: Faculty Secretary
    ↓ (If policy conflict or unusual case)
Level 2: Dean (Prof. Martynyuk)
    ↓ (If institutional policy impact)
Level 3: Rector's Secretary (Natalia Yakubovska)
    ↓ (If executive decision required)
Level 4: Rector (Prof. Biloskurskyi)
```

### Compliance Escalation Path
```
Level 1: GDPR Officer
    ↓ (If legal interpretation needed)
Level 2: Dean + InfoSec Team
    ↓ (If institutional policy conflict)
Level 3: Rector (Prof. Biloskurskyi)
    ↓ (If external legal counsel needed)
Level 4: External Legal Counsel
```

## 6. Communication Requirements

### Decision Communication Matrix
| Decision Level | Communication Method | Stakeholders to Notify | Timeline |
|----------------|---------------------|------------------------|----------|
| **Strategic** | Formal email + meeting | All stakeholders | Within 24 hours |
| **Tactical** | Email notification | Affected stakeholders | Within 48 hours |
| **Technical** | Technical documentation | Technical stakeholders | Within 72 hours |
| **Operational** | System notification | End users | Real-time/next business day |

### Approval Workflows
- **Policy Changes:** GDPR Officer → Dean → Rector (sequential)
- **Technical Changes:** InfoSec Review → Development → Dean (parallel review)
- **Budget Changes:** Development → Dean → Rector (sequential)
- **Emergency Changes:** Parallel notification, retrospective approval
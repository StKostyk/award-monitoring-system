# Role-Based Access Control (RBAC) Matrix

## Overview
This matrix defines granular permissions for each role within the Award Monitoring & Tracking System, ensuring proper access control and security boundaries.

## Role Definitions

| Role | Description | Scope | Reporting Structure |
|------|-------------|-------|-------------------|
| **Employee** | End-user submitting award requests | Personal awards management, view all university awards | Reports to Faculty Secretary or Dean |
| **Faculty Secretary** | Faculty award reviewer and approver | Faculty-level awards management, view all university awards | Reports to Dean |
| **Dean** | Faculty-level leadership and oversight | Faculty-level awards management, policy decisions, view all university awards | Reports to Rector |
| **Rector's Secretary** | Executive administrative support | University-wide coordination | Reports to Rector |
| **Rector** | Executive authority and final approver | University-wide strategic decisions | Top-level authority |
| **System Ops** | Technical operations and maintenance | System infrastructure | Reports to IT Director |
| **GDPR Officer** | Data protection and compliance oversight | Data privacy and retention | Reports to Legal Counsel |
| **InfoSec Team** | Information security management | Security controls and monitoring | Reports to CISO |
| **Dev Team** | Development and technical implementation | Application development and maintenance | Reports to Technical Lead |

## 1. Core Permissions Matrix

| Permission / Role | Employee | Faculty Secretary | Dean | Rector's Secretary | Rector | System Ops | GDPR Officer | InfoSec Team | Dev Team |
|-------------------|:--------:|:----------------:|:----:|:-----------------:|:------:|:----------:|:------------:|:------------:|:--------:|
| **Award Management** |
| Submit Award Request | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Edit Own Award Request | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Upload Scanned Document | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| View All University Awards | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| View Own Awards | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Manage Personal Profile | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Manage Department Profile | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Manage Faculty Profile | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Manage University Profile | ❌ | ❌ | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |

| Permission / Role | Employee | Faculty Secretary | Dean | Rector's Secretary | Rector | System Ops | GDPR Officer | InfoSec Team | Dev Team |
|-------------------|:--------:|:----------------:|:----:|:-----------------:|:------:|:----------:|:------------:|:------------:|:--------:|
| **Approval Workflow** |
| Review Department Awards | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Approve Department Awards | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Review Faculty Awards | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Approve Faculty Awards | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Escalate to University Level | ❌ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Final University Approval | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Reject Award Request | ❌ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Request Additional Information | ❌ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |

## 2. Administrative Permissions Matrix

| Permission / Role | Employee | Faculty Secretary | Dean | Rector's Secretary | Rector | System Ops | GDPR Officer | InfoSec Team | Dev Team |
|-------------------|:--------:|:----------------:|:----:|:-----------------:|:------:|:----------:|:------------:|:------------:|:--------:|
| **User Management** |
| Manage Own Profile | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| View User Directory | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Manage Department Users | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Manage Faculty Users | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Manage All Users | ❌ | ❌ | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ✓ |
| Assign User Roles | ❌ | ❌ | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ✓ |
| Deactivate User Accounts | ❌ | ❌ | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ✓ |

| Permission / Role | Employee | Faculty Secretary | Dean | Rector's Secretary | Rector | System Ops | GDPR Officer | InfoSec Team | Dev Team |
|-------------------|:--------:|:----------------:|:----:|:-----------------:|:------:|:----------:|:------------:|:------------:|:--------:|
| **Configuration & Policies** |
| Configure Department Policies | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Configure Faculty Policies | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Configure University Policies | ❌ | ❌ | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Configure GDPR Policies | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ✓ |
| Configure Security Policies | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ✓ |
| Manage Parser Model | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ |
| Configure Workflows | ❌ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ✓ |
| Configure Notifications | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ✓ |

## 3. Technical & Compliance Permissions Matrix

| Permission / Role | Employee | Faculty Secretary | Dean | Rector's Secretary | Rector | System Ops | GDPR Officer | InfoSec Team | Dev Team |
|-------------------|:--------:|:----------------:|:----:|:-----------------:|:------:|:----------:|:------------:|:------------:|:--------:|
| **System Operations** |
| View System Health | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ✓ | ✓ |
| Monitor Performance | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ❌ | ✓ |
| System Maintenance | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ❌ | ✓ |
| Database Administration | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ |
| Backup Management | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ❌ | ✓ |
| Disaster Recovery | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ❌ | ✓ |
| Deploy Code Changes | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ |

| Permission / Role | Employee | Faculty Secretary | Dean | Rector's Secretary | Rector | System Ops | GDPR Officer | InfoSec Team | Dev Team |
|-------------------|:--------:|:----------------:|:----:|:-----------------:|:------:|:----------:|:------------:|:------------:|:--------:|
| **Audit & Compliance** |
| View Own Audit Logs | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| View Department Audit Logs | ❌ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| View Faculty Audit Logs | ❌ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| View All Audit Logs | ❌ | ❌ | ❌ | ❌ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Export Audit Reports | ❌ | ❌ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Configure Audit Rules | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ✓ |
| Manage Data Retention | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ✓ |
| Handle Data Requests | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ✓ |
| Process GDPR Requests | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ✓ |

## 4. Reporting & Analytics Permissions Matrix

| Permission / Role | Employee | Faculty Secretary | Dean | Rector's Secretary | Rector | System Ops | GDPR Officer | InfoSec Team | Dev Team |
|-------------------|:--------:|:----------------:|:----:|:-----------------:|:------:|:----------:|:------------:|:------------:|:--------:|
| **Reports & Analytics** |
| View Personal Analytics | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| View Department Analytics | ❌ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| View Faculty Analytics | ❌ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| View University Analytics | ❌ | ❌ | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Export Reports (CSV/PDF) | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Create Custom Reports | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Access Raw Data | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ |

| Permission / Role | Employee | Faculty Secretary | Dean | Rector's Secretary | Rector | System Ops | GDPR Officer | InfoSec Team | Dev Team |
|-------------------|:--------:|:----------------:|:----:|:-----------------:|:------:|:----------:|:------------:|:------------:|:--------:|
| **Security Management** |
| View Security Logs | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ✓ | ✓ |
| Configure Security Settings | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ✓ |
| Manage Encryption Keys | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ |
| Security Incident Response | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ✓ | ✓ |
| Vulnerability Management | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ✓ |
| Access Control Management | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ✓ |

## 5. Permission Inheritance & Delegation

### Hierarchical Inheritance
- **Faculty Secretary** inherits all Employee permissions
- **Dean** inherits all Faculty Secretary permissions for their faculty
- **Rector** inherits all Dean permissions for the entire university
- **Rector's Secretary** can act on behalf of Rector for administrative tasks (not approval decisions)

### Temporary Delegation Rules
| Delegating Role | Can Delegate To | Duration Limit | Approval Required |
|-----------------|----------------|----------------|-------------------|
| Faculty Secretary | Another Faculty Secretary | 30 days | Dean approval |
| Dean | Another Dean or Senior Faculty Secretary | 60 days | Rector approval |
| Rector | Dean (acting capacity) | 90 days | Board notification |

### Emergency Access Procedures
- **Technical Emergency:** Dev Team can temporarily escalate to System Ops level for critical issues
- **Business Emergency:** Rector's Secretary can approve awards if Rector unavailable (<24 hours)
- **Security Emergency:** InfoSec Team can temporarily restrict any user access

## 6. Compliance & Security Notes

### Data Access Restrictions
- Personal data access limited by GDPR principles
- Audit logs automatically recorded for all permission usage
- Failed access attempts trigger security alerts
- Cross-functional data access requires explicit justification

### Regular Review Requirements
- **Monthly:** Faculty Secretary and Dean permissions review
- **Quarterly:** Technical team permissions audit
- **Annually:** Complete RBAC matrix review and update
- **Ad-hoc:** Upon role changes, security incidents, or policy updates

# Role-Based Access Control (RBAC) Matrix

## Overview
This matrix defines granular permissions for each role within the Award Monitoring & Tracking System, ensuring proper access control and security boundaries.

## Role Definitions

| Role | Description | Scope | Reporting Structure |
|------|-------------|-------|-------------------|
| **Employee** | End-user submitting award requests | Personal awards management | Reports to Faculty Secretary or Dean |
| **Faculty Secretary** | Faculty award reviewer and approver | Department-level awards management | Reports to Dean |
| **Dean** | Faculty-level leadership and oversight | Faculty-level awards management, policy decisions | Reports to Rector |
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
| Submit Award Request¹ | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Edit Own Award Request¹ | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Upload Scanned Document | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| View Submitted Awards of Others⁵ | ❌ | Department | Faculty | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ |
| View Approved University Awards (public pages, planned after Epic 4) | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| View Own Awards⁵ | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| View Award Change History² | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ |
| View Award Review Status and Reviewer Comments⁴ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ |
| Manage Personal Profile | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Manage Department Profile | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Manage Faculty Profile | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Manage University Profile | ❌ | ❌ | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |

¹ Approvers receive awards too and submit their own like every employee (`award:create`, `award:update:own`; decision of 2026-09-28, Feature 2.1). A request always starts at the faculty secretary; that nobody reviews their own award is enforced in Epic 4. System administrators and GDPR officers read awards for oversight and never submit.

² The owner sees every saved version of their award; anyone who may read a submitted award (scope over its organisation, or `award:read:all`) sees its versions from the submission on (Feature 2.2, `GET /awards/{id}/versions`).

⁴ The review timeline of an award (approval path, deadlines, expected completion, delay reason) and the reviewer decisions with the reviewer's name and comment are shown to everybody who may read the award, by the same rule as footnote ² (Feature 2.3 D-6, `GET /awards/{id}/status`). Reviewers act in their official role; the owner needs the comment to correct the award.

⁵ As built in Epic 2 (`RolePermissions`, `AwardOwnership.isReadable`): the owner always reads their award, drafts included. Anyone else reads only submitted awards whose organisation is covered by a role granting `award:read:department` (faculty secretary), `award:read:faculty` (dean) or `award:read:all` (rector's secretary, rector, system administrator, GDPR officer), held or delegated; any other award answers 404. `award:read:own` is held by every role and gates the read endpoints; system administrators and GDPR officers own no awards. The original "every employee sees all university awards" row is kept as the public award pages, planned once approved awards exist (proposed in the Epic 2 documentation sync, 2026-10-01).

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
| View and Export Award Audit Trail³ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ✓ | ❌ | ❌ |
| Configure Audit Rules | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ✓ |
| Manage Data Retention | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ✓ |
| Handle Data Requests | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ✓ |
| Process GDPR Requests | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ✓ |

³ `audit:read` (system administrator, GDPR officer): the `audit_logs` rows about one award, also of a deleted draft (Feature 2.2 D-5), shown as the «Журнал аудиту» tab of the award page and downloadable as CSV (`GET /awards/{id}/audit-trail/export`, audited as `AUDIT_EXPORT`). The wider audit rows above describe the Epic 6 compliance dashboard.

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

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
| Submit an Award for a Faculty or Department⁹ | ❌ | Faculty + its departments | Faculty + its departments | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Edit Own Award Request¹ | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Withdraw Own Unclaimed Award¹² | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Upload Scanned Document (own draft)⁶ | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Delete Own Document (own draft)⁶ | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Download Award Documents⁶ | Own | Own + Department | Own + Faculty | ✓ | ✓ | ❌⁷ | ✓ | ❌ | ❌ |
| View Submitted Awards of Others⁵ | ❌ | Department | Faculty | ✓ | ✓ | ❌⁷ | ✓ | ❌ | ❌ |
| View Approved University Awards (public pages, planned after Epic 4) | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| View Own Awards⁵ | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| View Award Change History² | ✓ | ✓ | ✓ | ✓ | ✓ | ❌⁷ | ✓ | ❌ | ❌ |
| View Award Review Status and Reviewer Comments⁴ | ✓ | ✓ | ✓ | ✓ | ✓ | ❌⁷ | ✓ | ❌ | ❌ |
| Manage Personal Profile | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Manage Department Profile | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Manage Faculty Profile | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Manage University Profile | ❌ | ❌ | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |

¹ Approvers receive awards too and submit their own like every employee (`award:create`, `award:update:own`; decision of 2026-09-28, Feature 2.1). A request starts at the faculty secretary; since 4.1.0 a level whose only reviewer would be the submitter, or a delegate the submitter lent the role to, is passed over (the only secretary of a faculty submits their own award to the dean), and a vacant level is kept. GDPR officers read awards for oversight and never submit; system administrators neither submit nor read awards (note 7).

² The owner sees every saved version of their award; anyone who may read a submitted award (scope over its organisation, or `award:read:all`) sees its versions from the submission on (Feature 2.2, `GET /awards/{id}/versions`).

⁴ The review timeline of an award (approval path, deadlines, expected completion, delay reason) and the reviewer decisions with the reviewer's name and comment are shown to everybody who may read the award, by the same rule as footnote ² (Feature 2.3 D-6, `GET /awards/{id}/status`). Reviewers act in their official role; the owner needs the comment to correct the award.

⁵ As built in Epic 2 (`RolePermissions`, `AwardOwnership.isReadable`): the owner always reads their award, drafts included. Anyone else reads only submitted awards whose organisation is covered by a role granting `award:read:department` (faculty secretary), `award:read:faculty` (dean) or `award:read:all` (rector's secretary, rector, system administrator, GDPR officer), held or delegated; any other award answers 404. `award:read:own` is held by every role and gates the read endpoints; system administrators and GDPR officers own no awards. The original "every employee sees all university awards" row is kept as the public award pages, planned once approved awards exist (proposed in the Epic 2 documentation sync, 2026-10-01).

⁶ Feature 3.1 (`DocumentUpload`, `DocumentService`): uploads and deletions need `award:update:own` and only touch the caller's own draft; documents of a submitted award are frozen. Downloads and the document list follow the award read rule of note 5, so whoever reads the award reads its documents; every download is recorded as `DOCUMENT_DOWNLOAD`. Unknown or unreadable documents answer 404.

⁷ Decided in the design review of 2026-10-04, in force since 2.1.8 (SCRUM-43): the system administrator runs the system and keeps `audit:read` (the award audit trail, note 3), but loses `award:read:all`, so award content, documents, history and review status are no longer readable to that role. The GDPR officer keeps award reading for data-protection oversight.

⁹ Feature 4.1.0 (`RecipientUnits`, `GET /awards/recipient-units`): a faculty secretary or dean, by own or delegated role, enters and submits awards received by an active faculty or department inside the role's scope; no new permission. The entering person owns the award (draft rights, GDPR export) and the award belongs to the unit, so its review and the scoped reads of note 5 follow the unit. The scope is checked again at submission (`recipient-out-of-scope`).

¹⁰ Feature 4.1.1 (`ReviewerRule`, `ReviewAssignment`, `GET /reviews`, `PUT`/`DELETE /awards/{id}/reviewer`): any approval role, own or delegated (`award:approve:level1`), reviews requests at its level or below inside its scope, never an award its holder owns or submitted, nor one owned or submitted by the person who lent a delegated role. The queue shows the own level; a lower level is reached through the `level` filter. A claim makes the caller the only reviewer; a higher level, or a peer when the holder may no longer review, takes it over; the holder releases it or hands it to an eligible colleague. Every change is audited (`REVIEW_CLAIMED`, `REVIEW_RELEASED`, `REVIEW_HANDED_OVER`, `REVIEW_TAKEN_OVER`).

¹¹ Feature 4.1.2 (`ReviewDecisions`, `POST /awards/{id}/decisions`): whoever may review a request (note 10) approves, returns, rejects or escalates it; an unclaimed request is claimed by the decision, one held by a colleague answers 409 `request-claimed`. Return and reject need a comment. An approval below the category's minimum level (§ Final approval by recognition level) and an escalation move the request to the next level not passed over; the rector cannot escalate. A delegate's decision is stamped with the delegator (`review_decisions.delegator_id`); a reviewer who also holds the role in their own right decides under it. Every decision is audited (`REVIEW_DECISION`).

¹² Feature 4.1.3 (`AwardWithdrawal`, `POST /awards/{id}/withdraw`): the owner (`award:update:own`) takes a pending award back as a draft while no reviewer has claimed its request (`request-claimed` otherwise); the request becomes `WITHDRAWN` and leaves every queue. A returned or withdrawn draft is edited and resubmitted like any draft but cannot be deleted (`award-has-request`).

| Permission / Role | Employee | Faculty Secretary | Dean | Rector's Secretary | Rector | System Ops | GDPR Officer | InfoSec Team | Dev Team |
|-------------------|:--------:|:----------------:|:----:|:-----------------:|:------:|:----------:|:------------:|:------------:|:--------:|
| **Approval Workflow** |
| Review Department Awards | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Approve Department Awards | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Review Faculty Awards | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Approve Faculty Awards | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Escalate to University Level | ❌ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Claim, Release and Hand Over a Review¹⁰ | ❌ | Own level | Own level; take over lower | Own level; take over lower | Own level; take over lower | ❌ | ❌ | ❌ | ❌ |
| Decide at a Level (Own or Lower)¹¹ | ❌ | Own level | Own or lower | Own or lower | Own or lower | ❌ | ❌ | ❌ | ❌ |
| Final University Approval | ❌ | ❌ | ❌ | ❌ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Reject Award Request | ❌ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Request Additional Information | ❌ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |

### Final approval by recognition level

The category's recognition level sets the lowest role that may give the final approval and the impact base score; every role above that minimum in the same line may approve too, and a request climbs every level from the faculty secretary to the minimum. Decided in the design review of 2026-10-04 (LOCAL = city or community, REGIONAL = oblast), in force since 2.1.8 (SCRUM-43) (Feature 2.1 D-3 had college and faculty awards final at the dean, LOCAL 45, UNIVERSITY 60).

| Recognition level | Final approval from | Impact base score |
|-------------------|---------------------|:-----------------:|
| `SPECIALITY` | Faculty Secretary | 10 |
| `DEPARTMENT` | Faculty Secretary | 20 |
| `COLLEGE` | Faculty Secretary | 30 |
| `FACULTY` | Faculty Secretary | 40 |
| `UNIVERSITY` | Faculty Secretary | 50 |
| `LOCAL` | Faculty Secretary | 60 |
| `REGIONAL` | Faculty Secretary | 70 |
| `NATIONAL` | Rector's Secretary | 80 |
| `INTERNATIONAL` | Rector's Secretary | 100 |

No level requires the rector. The review period of each level is counted in working days (Monday to Friday, default 3, configurable), decided in the same review.

## 2. Administrative Permissions Matrix

| Permission / Role | Employee | Faculty Secretary | Dean | Rector's Secretary | Rector | System Ops | GDPR Officer | InfoSec Team | Dev Team |
|-------------------|:--------:|:----------------:|:----:|:-----------------:|:------:|:----------:|:------------:|:------------:|:--------:|
| **User Management** |
| Manage Own Profile | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| View User Directory | ✓ | ✓ | ✓ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ |
| Manage Department Users | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Manage Faculty Users | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Manage All Users | ❌ | ❌ | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ✓ |
| Assign User Roles⁸ | ❌ | Employee | Faculty Secretary, Employee | Employee | Rector's Secretary, Dean | All | ❌ | ❌ | ✓ |
| Deactivate User Accounts | ❌ | ❌ | ❌ | ✓ | ✓ | ❌ | ❌ | ❌ | ✓ |

⁸ Roles each role may assign, always inside its own organisation subtree (design review of 2026-10-04, in force since 2.1.8 (SCRUM-43)). The faculty secretary and the rector's secretary only confirm membership by assigning `EMPLOYEE`; the dean appoints faculty secretaries; the rector appoints the rector's secretary and deans; the system administrator assigns every role, including `SYSTEM_ADMIN` and `GDPR_OFFICER`. Feature 1.2 allowed any role strictly below the caller's own, university roles only by the rector.

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

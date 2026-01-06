# Administrator Guide
## Award Monitoring & Tracking System

> **Phase 17 Deliverable**: Documentation & Knowledge Management  
> **Document Version**: 1.0  
> **Last Updated**: January 2026  
> **Author**: Stefan Kostyk  

---

## Table of Contents

1. [Admin Roles Overview](#admin-roles-overview)
2. [User Management](#user-management)
3. [Award Category Management](#award-category-management)
4. [Workflow Configuration](#workflow-configuration)
5. [Reports & Analytics](#reports--analytics)
6. [System Configuration](#system-configuration)
7. [Audit & Compliance](#audit--compliance)
8. [Troubleshooting](#troubleshooting)

---

## Admin Roles Overview

### Role Hierarchy

| Role | Scope | Key Permissions |
|------|-------|-----------------|
| **System Admin** | Global | Full system access, user management, configuration |
| **GDPR Officer** | Global | Data access requests, deletion, audit logs |
| **Rector** | University | Final approval, all reports |
| **Dean** | Faculty | Faculty approvals, faculty reports |
| **Faculty Secretary** | Faculty | Award review, user assistance |
| **Department Secretary** | Department | Initial review, document verification |

### Accessing Admin Features

1. Login with admin-level account
2. Click **"Administration"** in navigation
3. Select desired admin section

---

## User Management

### Viewing Users

```
Administration → Users → User List
```

| Filter | Options |
|--------|---------|
| Role | All / Specific role |
| Organization | University / Faculty / Department |
| Status | Active / Inactive / Suspended |
| Search | Name, email |

### Creating Users

1. Go to **Administration → Users → Add User**
2. Fill required fields:
   - Email (university domain required)
   - First/Last name
   - Role
   - Organization assignment

3. User receives activation email

### Modifying User Roles

```
Users → Select User → Edit → Change Role → Save
```

**Role Change Rules:**
- Cannot self-assign higher privileges
- Role changes are logged in audit trail
- User notified of role changes

### Deactivating Users

1. Select user from list
2. Click **"Deactivate"**
3. Confirm action
4. User loses access immediately
5. Awards remain but assigned to successor

### Bulk User Operations

```
Administration → Users → Bulk Actions
```

- Import users via CSV
- Export user list
- Bulk status changes
- Bulk organization assignment

**CSV Import Format:**
```csv
email,firstName,lastName,role,organization
ivan@uni.edu.ua,Ivan,Petrenko,EMPLOYEE,CS Department
maria@uni.edu.ua,Maria,Koval,FACULTY_SECRETARY,IT Faculty
```

---

## Award Category Management

### Managing Categories

```
Administration → Awards → Categories
```

### Creating Categories

1. Click **"+ Add Category"**
2. Configure:
   - Name (Ukrainian and English)
   - Level (International/National/Regional/Local)
   - Description
   - Required documents
   - Approval workflow

### Category Settings

| Setting | Description |
|---------|-------------|
| **Active** | Available for new submissions |
| **Requires Verification** | Extra verification step |
| **Auto-Publish** | Publish immediately after final approval |
| **Notification Template** | Custom email template |

### Archiving Categories

- Archived categories cannot receive new awards
- Existing awards in archived categories remain visible
- Can be restored if needed

---

## Workflow Configuration

### Default Workflow

```
Employee Submits
     ↓
Department Review (Secretary)
     ↓
Faculty Review (Dean)
     ↓
University Review (Rector Office)
     ↓
Final Approval (Rector)
     ↓
Published
```

### Customizing Workflows

```
Administration → Workflows → Edit
```

**Configurable Options:**
- Skip levels for certain categories
- Parallel approvals
- Auto-approval for specific award types
- Deadline requirements per level

### Review Assignment Rules

| Rule Type | Description |
|-----------|-------------|
| Automatic | Assigned to role holder in organization |
| Manual | Admin assigns specific reviewer |
| Round-robin | Distributed among eligible reviewers |
| Load-balanced | Assigned to reviewer with fewest pending |

### Setting Deadlines

```yaml
Review Deadlines:
  Department: 3 business days
  Faculty: 5 business days
  University: 7 business days
  
Escalation:
  Warning: 1 day before deadline
  Escalation: On deadline
  Auto-forward: 2 days after deadline
```

---

## Reports & Analytics

### Available Reports

| Report | Audience | Content |
|--------|----------|---------|
| Awards Summary | All admins | Status distribution, trends |
| Processing Time | Managers | Average review times, bottlenecks |
| User Activity | System Admin | Login, submissions, reviews |
| Compliance | GDPR Officer | Data access, deletions |
| Faculty Report | Deans | Faculty-specific statistics |

### Generating Reports

1. Go to **Administration → Reports**
2. Select report type
3. Set parameters (date range, filters)
4. Choose format (PDF, Excel, CSV)
5. Generate and download

### Scheduled Reports

Configure automatic report generation:

```
Administration → Reports → Scheduled
```

| Setting | Options |
|---------|---------|
| Frequency | Daily / Weekly / Monthly |
| Recipients | Email addresses |
| Format | PDF / Excel |
| Time | Preferred delivery time |

### Dashboard Widgets

Customize admin dashboard:
- Drag widgets to rearrange
- Add/remove metrics
- Set refresh intervals
- Create custom queries (advanced)

---

## System Configuration

### General Settings

```
Administration → Settings → General
```

| Setting | Description |
|---------|-------------|
| System Name | Displayed in UI and emails |
| Support Email | User support contact |
| Session Timeout | Auto-logout time (minutes) |
| File Size Limit | Max upload size (MB) |
| Allowed File Types | PDF, JPEG, PNG |

### Email Configuration

```
Administration → Settings → Email
```

- SMTP server settings
- Email templates (per notification type)
- Sender name and address
- Test email functionality

### Security Settings

```
Administration → Settings → Security
```

| Setting | Recommended |
|---------|-------------|
| Password Policy | Min 8 chars, mixed case, number, symbol |
| MFA | Required for admins, optional for users |
| Session Duration | 8 hours |
| Failed Login Lockout | 5 attempts, 30 min lockout |
| IP Allowlist | Optional for admin access |

### Maintenance Mode

Enable during updates or emergencies:

```
Administration → Settings → Maintenance Mode
```

- Displays maintenance message to users
- Allows admin access only
- Queues incoming submissions
- Auto-disable after set time

---

## Audit & Compliance

### Audit Logs

```
Administration → Audit → Logs
```

**Logged Events:**
- User login/logout
- Award submissions and status changes
- Document uploads/deletions
- Admin actions
- Configuration changes
- Data exports

### Searching Audit Logs

| Filter | Example |
|--------|---------|
| User | `user:ivan@uni.edu.ua` |
| Action | `action:DELETE` |
| Resource | `resource:award` |
| Date | `date:2026-01-01..2026-01-31` |
| IP | `ip:192.168.1.*` |

### GDPR Compliance

#### Data Subject Requests

```
Administration → GDPR → Data Requests
```

**Request Types:**
- Access (export user data)
- Rectification (correct data)
- Erasure (delete data)
- Portability (export in standard format)

**Processing Requests:**
1. Verify requestor identity
2. Review request type
3. Generate report or perform action
4. Document completion
5. Notify user

#### Data Retention

```
Administration → GDPR → Retention
```

| Data Type | Retention | After Expiry |
|-----------|-----------|--------------|
| Active awards | Indefinite | N/A |
| Rejected awards | 3 years | Anonymize |
| Audit logs | 7 years | Delete |
| User accounts (inactive) | 2 years | Anonymize |

---

## Troubleshooting

### Common Admin Issues

**Problem: User can't log in**
1. Check user status (active/inactive)
2. Verify email is correct
3. Check for account lockout
4. Reset password if needed

**Problem: Workflow stuck**
1. Check current review level
2. Verify reviewer is assigned
3. Check for deadline exceeded
4. Manually assign reviewer if needed

**Problem: Reports not generating**
1. Check date range validity
2. Verify data exists for filters
3. Try smaller date range
4. Check server logs for errors

### Admin Tools

```
Administration → Tools
```

| Tool | Purpose |
|------|---------|
| Cache Clear | Clear system caches |
| Reindex Search | Rebuild search index |
| Health Check | System health status |
| Backup Status | Last backup information |

### Getting Technical Help

For issues beyond admin scope:
1. Check [Troubleshooting Guide](../operations/TROUBLESHOOTING.md)
2. Contact IT support: it-support@university.edu.ua
3. For urgent issues: See [Incident Response](../operations/runbooks/INCIDENT_RESPONSE_RUNBOOK.md)

---

## Best Practices

### User Management
- Regular audit of user roles and access
- Remove inactive users promptly
- Document role change reasons
- Review admin access quarterly

### Data Management
- Monitor storage usage
- Archive old categories periodically
- Test backup restoration annually
- Document data retention decisions

### Security
- Enable MFA for all admin accounts
- Review audit logs weekly
- Update admin passwords quarterly
- Test incident response procedures

---

## Quick Reference

### Admin Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+U` | User management |
| `Ctrl+R` | Reports |
| `Ctrl+A` | Audit logs |
| `Ctrl+S` | Settings |

### Emergency Contacts

| Role | Contact |
|------|---------|
| System Admin | sysadmin@university.edu.ua |
| GDPR Officer | gdpr@university.edu.ua |
| IT Support | it-support@university.edu.ua |

---

*Last updated: January 2026*


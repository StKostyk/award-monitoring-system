# Product Backlog
## Award Monitoring & Tracking System

> **Last Updated**: September 2026  
> **Total Story Points**: 154  
> **GitHub Issues**: [Project Board](https://github.com/users/StKostyk/projects/1/views/1)  
> **Jira**: project `SCRUM`, Epic 1 = SCRUM-5

Every story is tracked twice: a Jira issue for the sprint board and a GitHub issue that the pull request closes.

## Current Sprint: Sprint 2 (2026-09-21 – 2026-09-27) — Authentication core

| ID | Story | Points | Status | Jira | Issue |
|----|-------|--------|--------|------|-------|
| 1.1.0 | User domain entities and auth schema | 3 | ✅ Done | SCRUM-6 | [#42](https://github.com/StKostyk/award-monitoring-system/issues/42) |
| 1.1.1 | Authorization server, PKCE login and auth shell | 8 | ✅ Done | SCRUM-7 | [#36](https://github.com/StKostyk/award-monitoring-system/issues/36) |
| 1.1.2 | Employee registration and email verification (US-001) | 5 | 👀 In review | SCRUM-8 | [#29](https://github.com/StKostyk/award-monitoring-system/issues/29) |
| 1.1.3 | Password reset | 3 | ⏳ Sprint | SCRUM-9 | [#46](https://github.com/StKostyk/award-monitoring-system/issues/46) |
| 1.1.4 | Login rate limiting, lockout and auth audit | 3 | ⏳ Sprint | SCRUM-10 | [#31](https://github.com/StKostyk/award-monitoring-system/issues/31) |

Sprint Goal: a user can register, verify the address, log in through the authorization server and call a protected endpoint.  
Committed Points: 22

---

## Backlog (Prioritized)

### Epic 1 — remaining (Sprints 3–4)
| ID | Story | Points | Feature | Jira | Issue |
|----|-------|--------|---------|------|-------|
| 1.1.5 | New device login notification | 3 | 1.1 | SCRUM-11 | [#33](https://github.com/StKostyk/award-monitoring-system/issues/33) |
| 1.2.1 | Permission model and organisation-scoped access | 5 | 1.2 | SCRUM-12 | [#35](https://github.com/StKostyk/award-monitoring-system/issues/35) |
| 1.2.2 | Role assignment (US-002) | 8 | 1.2 | SCRUM-13 | [#32](https://github.com/StKostyk/award-monitoring-system/issues/32) |
| 1.2.3 | Approval authority delegation | 5 | 1.2 | SCRUM-14 | [#37](https://github.com/StKostyk/award-monitoring-system/issues/37) |
| 1.3.1 | Profile information update | 3 | 1.3 | SCRUM-15 | [#38](https://github.com/StKostyk/award-monitoring-system/issues/38) |
| 1.3.2 | Notification preferences | 3 | 1.3 | SCRUM-16 | [#39](https://github.com/StKostyk/award-monitoring-system/issues/39) |
| 1.3.3 | GDPR data portability (US-011, partial) | 5 | 1.3 | SCRUM-17 | [#40](https://github.com/StKostyk/award-monitoring-system/issues/40) |

### Epic 2 — next
| ID | Story | Points | Epic | Issue |
|----|-------|--------|------|-------|
| 2.0 | Award domain entities | 5 | Awards | [#43](https://github.com/StKostyk/award-monitoring-system/issues/43) |
| US-003 | Quick Award Submission | 8 | Awards | [#44](https://github.com/StKostyk/award-monitoring-system/issues/44) |
| 2.1.2 | Award Date Validation | - | Awards | |
| 2.1.3 | Smart Award Categorization | - | Awards | |
| 2.2.1 | Automatic Change Tracking | - | Awards | |
| 2.2.2 | Version History Display | - | Awards | |

### Later epics
| ID | Story | Points | Epic |
|----|-------|--------|------|
| 3.1.1 | Certificate Upload | - | Documents |
| US-006 | AI-Powered Document Parsing | - | Documents |
| US-007 | Confidence Score Display | - | Documents |
| US-004 | Multi-Level Approval Routing | - | Workflows |
| 4.2.1 | Automatic Escalation | - | Workflows |
| US-005 | Real-Time Status Updates | - | Awards |
| 2.4.1 | Award Error Correction | - | Awards |
| 2.4.2 | GDPR-Compliant Deletion | - | Awards |
| US-008 | Personal Dashboard | 13 | Analytics |
| US-010 | Executive Dashboard | 21 | Analytics |

---

## Completed

| Sprint | Stories | Points |
|--------|---------|--------|
| Sprint 1 | Setup (7 tasks) | 14 |

Total Completed: 14 / 154 points (9%)

---

## Icebox (Deferred)

| ID | Story | Reason | Issue |
|----|-------|--------|-------|
| 1.3.4 | MFA (TOTP, SMS, WebAuthn) | Not needed for the demo; design kept in the auth document | [#41](https://github.com/StKostyk/award-monitoring-system/issues/41) |
| — | HR validation endpoint | No HR system available; replaced by domain check and department selection at registration | [#47](https://github.com/StKostyk/award-monitoring-system/issues/47) |

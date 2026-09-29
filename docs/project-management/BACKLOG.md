# Product Backlog
## Award Monitoring & Tracking System

> **Last Updated**: September 2026  
> **Total Story Points**: 154  
> **GitHub Issues**: [Project Board](https://github.com/users/StKostyk/projects/1/views/1)  
> **Jira**: project `SCRUM`, Epic 1 = SCRUM-5, Epic 2 = SCRUM-20

Every story is tracked twice: a Jira issue for the sprint board and a GitHub issue that the pull request closes.

## Current Sprint: Sprint 2 (2026-09-21 – 2026-09-27) — Authentication core

| ID | Story | Points | Status | Jira | Issue |
|----|-------|--------|--------|------|-------|
| 1.1.0 | User domain entities and auth schema | 3 | ✅ Done | SCRUM-6 | [#42](https://github.com/StKostyk/award-monitoring-system/issues/42) |
| 1.1.1 | Authorization server, PKCE login and auth shell | 8 | ✅ Done | SCRUM-7 | [#36](https://github.com/StKostyk/award-monitoring-system/issues/36) |
| 1.1.2 | Employee registration and email verification (US-001) | 5 | ✅ Done | SCRUM-8 | [#29](https://github.com/StKostyk/award-monitoring-system/issues/29) |
| 1.1.3 | Password reset | 3 | ✅ Done | SCRUM-9 | [#46](https://github.com/StKostyk/award-monitoring-system/issues/46) |
| 1.1.4 | Login rate limiting, lockout and auth audit | 3 | ✅ Done | SCRUM-10 | [#31](https://github.com/StKostyk/award-monitoring-system/issues/31) |
| 1.1.5 | New device login notification | 3 | ✅ Done | SCRUM-11 | [#33](https://github.com/StKostyk/award-monitoring-system/issues/33) |
| 1.1.6 | Fixes from the Feature 1.1 manual run | 3 | ✅ Done | SCRUM-18 | [#64](https://github.com/StKostyk/award-monitoring-system/issues/64) |
| 1.2.1 | Permission model and organisation-scoped access | 5 | ✅ Done | SCRUM-12 | [#35](https://github.com/StKostyk/award-monitoring-system/issues/35) |
| 1.2.2 | Role assignment and membership confirmation | 8 | ✅ Done | SCRUM-13 | [#32](https://github.com/StKostyk/award-monitoring-system/issues/32) |
| 1.2.3 | Approval authority delegation | 5 | ✅ Done | SCRUM-14 | [#37](https://github.com/StKostyk/award-monitoring-system/issues/37) |
| 1.2.4 | Fixes from the Feature 1.2 validation | 5 | ✅ Done | SCRUM-19 | [#73](https://github.com/StKostyk/award-monitoring-system/issues/73) |

Sprint Goal: a user can register, verify the address, log in through the authorization server and call a protected endpoint.  
Committed Points: 46 (Feature 1.2 pulled forward: the sprint goal was met on day one)

---

## Backlog (Prioritized)

### Epic 2 — next (Jira epic SCRUM-20)
Feature 2.1 PRD approved 2026-09-28: [feature-2.1-award-creation-validation.md](../features/epic-02/feature-2.1-award-creation-validation.md).

| ID | Story | Points | Feature | Jira | Issue |
|----|-------|--------|---------|------|-------|
| 2.1.0 | Award domain model and category catalogue (done) | 3 | 2.1 | SCRUM-21 | [#43](https://github.com/StKostyk/award-monitoring-system/issues/43) |
| 2.1.1 | Award draft and submission (US-003) (done) | 8 | 2.1 | SCRUM-22 | [#44](https://github.com/StKostyk/award-monitoring-system/issues/44) |
| 2.1.2 | Award date and duplicate validation (done) | 3 | 2.1 | SCRUM-23 | [#76](https://github.com/StKostyk/award-monitoring-system/issues/76) |
| 2.1.3 | Award category suggestion (done) | 3 | 2.1 | SCRUM-24 | [#77](https://github.com/StKostyk/award-monitoring-system/issues/77) |
| 2.1.4 | Fixes from the Feature 2.1 validation (done) | 5 | 2.1 | SCRUM-28 | [#85](https://github.com/StKostyk/award-monitoring-system/issues/85) |
| 2.2.1 | Award version recording | 5 | 2.2 | SCRUM-25 | [#78](https://github.com/StKostyk/award-monitoring-system/issues/78) |
| 2.2.2 | Version history view and audit export | 5 | 2.2 | SCRUM-26 | [#79](https://github.com/StKostyk/award-monitoring-system/issues/79) |
| 2.3.1 | Award status tracking (US-005) | 5 | 2.3 | SCRUM-27 | [#80](https://github.com/StKostyk/award-monitoring-system/issues/80) |

### Epic 1 — remaining (after Feature 2.1)
| ID | Story | Points | Feature | Jira | Issue |
|----|-------|--------|---------|------|-------|
| 1.3.1 | Profile information update | 3 | 1.3 | SCRUM-15 | [#38](https://github.com/StKostyk/award-monitoring-system/issues/38) |
| 1.3.3 | GDPR data portability (US-011, partial) | 5 | 1.3 | SCRUM-17 | [#40](https://github.com/StKostyk/award-monitoring-system/issues/40) |

### Later epics
| ID | Story | Points | Epic |
|----|-------|--------|------|
| 3.1.1 | Certificate Upload | - | Documents |
| US-006 | AI-Powered Document Parsing | - | Documents |
| US-007 | Confidence Score Display | - | Documents |
| US-004 | Multi-Level Approval Routing | - | Workflows |
| 4.2.1 | Automatic Escalation | - | Workflows |
| 2.4.1 | Award correction by reviewers (end of Epic 4) | 5 | Workflows |
| 1.3.2 | Notification preferences (SCRUM-16, [#39](https://github.com/StKostyk/award-monitoring-system/issues/39)) | 3 | Notifications |
| 2.4.2 | GDPR-compliant award deletion | 5 | Compliance |
| US-008 | Personal Dashboard | 13 | Analytics |
| US-010 | Executive Dashboard | 21 | Analytics |

---

## Completed

| Sprint | Stories | Points |
|--------|---------|--------|
| Sprint 1 | Setup (7 tasks) | 14 |
| Sprint 2 | Feature 1.1 Core Authentication (1.1.0–1.1.6), Feature 1.2 RBAC (1.2.1–1.2.3) | 46 |

Total Completed: 39 / 154 points (25%)

---

## Icebox (Deferred)

| ID | Story | Reason | Issue |
|----|-------|--------|-------|
| 1.3.4 | MFA (TOTP, SMS, WebAuthn) | Not needed for the demo; design kept in the auth document | [#41](https://github.com/StKostyk/award-monitoring-system/issues/41) |
| — | HR validation endpoint | No HR system available; replaced by domain check and department selection at registration | [#47](https://github.com/StKostyk/award-monitoring-system/issues/47) |

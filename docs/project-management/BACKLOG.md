# Product Backlog
## Award Monitoring & Tracking System

> **Last Updated**: September 2026  
> **Total Story Points**: 156  
> **GitHub Issues**: [Project Board](https://github.com/users/StKostyk/projects/1/views/1)  
> **Jira**: project `SCRUM`, Epic 1 = SCRUM-5, Epic 2 = SCRUM-20

Every story is tracked twice: a Jira issue for the sprint board and a GitHub issue that the pull request closes.

## Current Sprint: Sprint 3 (2026-09-28 – 2026-10-04) — Award creation

| ID | Story | Points | Status | Jira | Issue |
|----|-------|--------|--------|------|-------|
| 1.2.4 | Fixes from the Feature 1.2 validation | 5 | ✅ Done | SCRUM-19 | [#73](https://github.com/StKostyk/award-monitoring-system/issues/73) |
| 2.1.0 | Award domain model and category catalogue | 3 | ✅ Done | SCRUM-21 | [#43](https://github.com/StKostyk/award-monitoring-system/issues/43) |
| 2.1.1 | Award draft and submission (US-003) | 8 | ✅ Done | SCRUM-22 | [#44](https://github.com/StKostyk/award-monitoring-system/issues/44) |
| 2.1.2 | Award date and duplicate validation | 3 | ✅ Done | SCRUM-23 | [#76](https://github.com/StKostyk/award-monitoring-system/issues/76) |
| 2.1.3 | Award category suggestion | 3 | ✅ Done | SCRUM-24 | [#77](https://github.com/StKostyk/award-monitoring-system/issues/77) |
| 2.1.4 | Fixes from the Feature 2.1 validation | 5 | ✅ Done | SCRUM-28 | [#85](https://github.com/StKostyk/award-monitoring-system/issues/85) |
| 1.3.1 | Profile information update | 5 | ✅ Done | SCRUM-15 | [#38](https://github.com/StKostyk/award-monitoring-system/issues/38) |
| 1.3.3 | GDPR data portability (US-011, partial) | 5 | ✅ Done | SCRUM-17 | [#40](https://github.com/StKostyk/award-monitoring-system/issues/40) |
| 1.3.4 | Fixes from the Feature 1.3 validation | 3 | ✅ Done | SCRUM-29 | [#92](https://github.com/StKostyk/award-monitoring-system/issues/92) |
| 2.2.1 | Award version recording | 5 | ✅ Done | SCRUM-25 | [#78](https://github.com/StKostyk/award-monitoring-system/issues/78) |
| 2.2.2 | Version history view and audit export | 5 | 🔍 In Review | SCRUM-26 | [#79](https://github.com/StKostyk/award-monitoring-system/issues/79) |

Sprint Goal: an employee creates, checks and submits an award (Feature 2.1).  
Completed Points: 45 (Feature 2.1 validated 2026-09-29, Feature 1.3 validated 2026-09-30, fixes 1.3.4 and 2.2.1 merged; the author's runs of PRD §9 pending)

---

## Backlog (Prioritized)

### Epic 2 — remaining (Jira epic SCRUM-20)
Feature 2.1 done (Sprint 3): [feature-2.1-award-creation-validation.md](../features/epic-02/feature-2.1-award-creation-validation.md). Feature 2.2 PRD approved 2026-09-30: [feature-2.2-award-version-history.md](../features/epic-02/feature-2.2-award-version-history.md).

| ID | Story | Points | Feature | Jira | Issue |
|----|-------|--------|---------|------|-------|
| 2.3.1 | Award status tracking (US-005) | 5 | 2.3 | SCRUM-27 | [#80](https://github.com/StKostyk/award-monitoring-system/issues/80) |

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
| Sprint 3 | Feature 1.2 fixes (1.2.4), Feature 2.1 Award Creation & Validation (2.1.0–2.1.4), Feature 1.3 User Profile Management (1.3.1, 1.3.3, 1.3.4) | 40 |

Total Completed: 100 / 159 points (63%)

---

## Icebox (Deferred)

| ID | Story | Reason | Issue |
|----|-------|--------|-------|
| 1.3.5 | MFA (TOTP, SMS, WebAuthn) | Not needed for the demo; design kept in the auth document | [#41](https://github.com/StKostyk/award-monitoring-system/issues/41) |
| — | HR validation endpoint | No HR system available; replaced by domain check and department selection at registration | [#47](https://github.com/StKostyk/award-monitoring-system/issues/47) |

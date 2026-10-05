# Product Backlog
## Award Monitoring & Tracking System

> **Last Updated**: October 2026  
> **Story Points**: 133 delivered, 57 estimated in the backlog (later-epic stories are sized at their epic kickoff)  
> **GitHub Issues**: [Project Board](https://github.com/users/StKostyk/projects/1/views/1)  
> **Jira**: project `SCRUM`, Epic 1 = SCRUM-5, Epic 2 = SCRUM-20, Epic 3 = SCRUM-34

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
| 2.2.2 | Version history view and audit export | 5 | ✅ Done | SCRUM-26 | [#79](https://github.com/StKostyk/award-monitoring-system/issues/79) |
| 2.2.3 | Fixes from the Feature 2.2 validation | 3 | ✅ Done | SCRUM-30 | [#97](https://github.com/StKostyk/award-monitoring-system/issues/97) |
| 2.3.1 | Award status tracking (US-005) | 5 | ✅ Done | SCRUM-27 | [#80](https://github.com/StKostyk/award-monitoring-system/issues/80) |
| 2.3.2 | Fixes from the Feature 2.3 validation | 2 | ✅ Done | SCRUM-31 | [#101](https://github.com/StKostyk/award-monitoring-system/issues/101) |
| 2.1.5 | Database views and functions follow the award model | 2 | ✅ Done | SCRUM-32 | [#103](https://github.com/StKostyk/award-monitoring-system/issues/103) |
| 2.1.6 | Fixes from the manual run of Feature 2.1 | 2 | ✅ Done | SCRUM-38 | [#117](https://github.com/StKostyk/award-monitoring-system/issues/117) |
| 2.1.7 | Fixes from the manual runs of Features 2.1 and 2.2 | 3 | 4 | SCRUM-42 | [#127](https://github.com/StKostyk/award-monitoring-system/issues/127) |
| 3.0.1 | Production configuration and local production run | 3 | ✅ Done | SCRUM-33 | [#109](https://github.com/StKostyk/award-monitoring-system/issues/109) |

Sprint Goal: an employee creates, checks, submits and tracks an award (Features 2.1–2.3).  
Completed Points: 65 (Feature 2.1 validated 2026-09-29, Features 1.3 and 2.2 validated 2026-09-30, fixes 2.2.3 merged, Feature 2.3 validated and fixes 2.3.2 merged 2026-10-01, database objects 2.1.5 merged 2026-10-01, production configuration 3.0.1 merged 2026-10-02; the manual runs of PRD §9 pending)

---

## Backlog (Prioritized)

### Epic 2 — done (Jira epic SCRUM-20)
Features 2.1, 2.2 and 2.3 done (Sprint 3): [feature-2.1-award-creation-validation.md](../features/epic-02/feature-2.1-award-creation-validation.md), [feature-2.2-award-version-history.md](../features/epic-02/feature-2.2-award-version-history.md), [feature-2.3-award-status-tracking.md](../features/epic-02/feature-2.3-award-status-tracking.md). Feature 2.4 stories are tracked with Epics 4 and 6. The database views and functions that disagreed with the award model (documentation sync of 2026-10-01) were fixed in 2.1.5 (Sprint 3). Epic 2 done 2026-10-01.

### Epic 3 — Document Processing (Jira epic SCRUM-34)
Tracker: [EPIC-03-DOCUMENT-PROCESSING-STATUS.md](../epics/EPIC-03-DOCUMENT-PROCESSING-STATUS.md). Upload only in this delivery; OCR and confidence scoring (US-006, US-007) are deferred until after Epic 8. Feature 3.1 PRD approved 2026-10-02: [feature-3.1-document-upload-storage.md](../features/epic-03/feature-3.1-document-upload-storage.md).

| ID | Story | Points | Sprint | Jira | Issue |
|----|-------|--------|--------|------|-------|
| 3.1.1 | Document storage and upload API | 8 | ✅ Done | SCRUM-35 | [#111](https://github.com/StKostyk/award-monitoring-system/issues/111) |
| 3.1.2 | Certificate upload in the award form and award page | 5 | ✅ Done | SCRUM-36 | [#112](https://github.com/StKostyk/award-monitoring-system/issues/112) |
| 3.1.3 | Malware scanning of uploads | 3 | 4 | SCRUM-37 | [#113](https://github.com/StKostyk/award-monitoring-system/issues/113) |
| 3.0.2 | Brand theming, dark mode and side-nav shell | 5 | ✅ Done | SCRUM-39 | [#121](https://github.com/StKostyk/award-monitoring-system/issues/121) |
| 3.0.3 | Login and error pages in the university brand | 3 | ✅ Done | SCRUM-40 | [#123](https://github.com/StKostyk/award-monitoring-system/issues/123) |
| 3.0.4 | Blocking lint and Playwright in CI | 3 | ✅ Done | SCRUM-41 | [#124](https://github.com/StKostyk/award-monitoring-system/issues/124) |

### Later epics
| ID | Story | Points | Epic |
|----|-------|--------|------|
| US-006 | AI-Powered Document Parsing (deferred) | 21 | Documents |
| US-007 | Confidence Score Display (deferred) | 8 | Documents |
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
| Sprint 3 | Feature 1.2 fixes (1.2.4), Feature 2.1 Award Creation & Validation (2.1.0–2.1.4), Feature 1.3 User Profile Management (1.3.1, 1.3.3, 1.3.4), Feature 2.2 Award Version History & Audit Trail (2.2.1–2.2.3), Feature 2.3 Award Status Tracking (2.3.1, 2.3.2), database objects (2.1.5), production configuration (3.0.1) | 65 |

Total Completed: 125 points (Epics 1 and 2 except the stories moved to later epics, and 3.0.1)

---

## Icebox (Deferred)

| ID | Story | Reason | Issue |
|----|-------|--------|-------|
| 1.3.5 | MFA (TOTP, SMS, WebAuthn) | Not needed for the demo; design kept in the auth document | [#41](https://github.com/StKostyk/award-monitoring-system/issues/41) |
| — | HR validation endpoint | No HR system available; replaced by domain check and department selection at registration | [#47](https://github.com/StKostyk/award-monitoring-system/issues/47) |

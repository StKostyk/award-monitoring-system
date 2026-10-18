# Product Backlog
## Award Monitoring & Tracking System

> **Last Updated**: October 2026  
> **Story Points**: 167 delivered, 133 estimated in the backlog (later-epic stories are sized at their epic kickoff)  
> **GitHub Issues**: [Project Board](https://github.com/users/StKostyk/projects/1/views/1)  
> **Jira**: project `SCRUM`, Epic 1 = SCRUM-5, Epic 2 = SCRUM-20, Epic 3 = SCRUM-34, Epic 4 = SCRUM-47, Epic 7 = SCRUM-63

Every story is tracked twice: a Jira issue for the sprint board and a GitHub issue that the pull request closes.

## Sprint 3 (2026-09-28 – 2026-10-04) — Award creation, closed ([summary](sprints/SPRINT-03.md))

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
| 2.1.7 | Fixes from the manual runs of Features 2.1 and 2.2 | 3 | ✅ Done (Sprint 4) | SCRUM-42 | [#127](https://github.com/StKostyk/award-monitoring-system/issues/127) |
| 2.1.8 | Review decisions: approval levels, working-day review period, role assignment, GDPR and audit fixes | 8 | ✅ Done (Sprint 4) | SCRUM-43 | [#130](https://github.com/StKostyk/award-monitoring-system/issues/130) |
| 3.0.1 | Production configuration and local production run | 3 | ✅ Done | SCRUM-33 | [#109](https://github.com/StKostyk/award-monitoring-system/issues/109) |

Sprint Goal: an employee creates, checks, submits and tracks an award (Features 2.1–2.3).  
Completed Points: 91 (these stories plus 3.0.2–3.0.4, 3.1.1 and 3.1.2 from Epic 3, listed under the epic below)

---

## Backlog (Prioritized)

### Epic 1 — User Management fixes (Jira epic SCRUM-5)
Features 1.1–1.3 done (Sprints 2–3); this table holds the fixes found in their later manual runs. Tracker: [EPIC-01-USER-MANAGEMENT-STATUS.md](../epics/EPIC-01-USER-MANAGEMENT-STATUS.md).

| ID | Story | Points | Sprint | Jira | Issue |
|----|-------|--------|--------|------|-------|
| 1.1.7 | Forgot-password 429 shows a raw translation key | 1 | ✅ Done | SCRUM-80 | [#207](https://github.com/StKostyk/award-monitoring-system/issues/207) |
| 1.2.5 | Repeated department move answers role-already-assigned | 1 | ✅ Done | SCRUM-81 | [#209](https://github.com/StKostyk/award-monitoring-system/issues/209) |

### Epic 2 — done (Jira epic SCRUM-20)
Features 2.1, 2.2 and 2.3 done (Sprint 3): [feature-2.1-award-creation-validation.md](../features/epic-02/feature-2.1-award-creation-validation.md), [feature-2.2-award-version-history.md](../features/epic-02/feature-2.2-award-version-history.md), [feature-2.3-award-status-tracking.md](../features/epic-02/feature-2.3-award-status-tracking.md). Feature 2.4 stories are tracked with Epics 4 and 6. The database views and functions that disagreed with the award model (documentation sync of 2026-10-01) were fixed in 2.1.5 (Sprint 3). Epic 2 done 2026-10-01.

### Epic 3 — Document Processing (Jira epic SCRUM-34)
Tracker: [EPIC-03-DOCUMENT-PROCESSING-STATUS.md](../epics/EPIC-03-DOCUMENT-PROCESSING-STATUS.md). Upload only in this delivery; OCR and confidence scoring (US-006, US-007) are deferred until after Epic 8. Feature 3.1 PRD approved 2026-10-02: [feature-3.1-document-upload-storage.md](../features/epic-03/feature-3.1-document-upload-storage.md). Feature 3.1 validated 2026-10-05 and its fixes merged (3.1.5); documentation synced with the code on 2026-10-05; the epic closes after the manual run of 2026-10-11.

| ID | Story | Points | Sprint | Jira | Issue |
|----|-------|--------|--------|------|-------|
| 3.1.1 | Document storage and upload API | 8 | ✅ Done | SCRUM-35 | [#111](https://github.com/StKostyk/award-monitoring-system/issues/111) |
| 3.1.2 | Certificate upload in the award form and award page | 5 | ✅ Done | SCRUM-36 | [#112](https://github.com/StKostyk/award-monitoring-system/issues/112) |
| 3.1.3 | Malware scanning of uploads and per-user upload limits | 3 | ✅ Done (Sprint 4) | SCRUM-37 | [#113](https://github.com/StKostyk/award-monitoring-system/issues/113) |
| 3.0.2 | Brand theming, dark mode and side-nav shell | 5 | ✅ Done | SCRUM-39 | [#121](https://github.com/StKostyk/award-monitoring-system/issues/121) |
| 3.0.3 | Login and error pages in the university brand | 3 | ✅ Done | SCRUM-40 | [#123](https://github.com/StKostyk/award-monitoring-system/issues/123) |
| 3.0.4 | Blocking lint and Playwright in CI | 3 | ✅ Done | SCRUM-41 | [#124](https://github.com/StKostyk/award-monitoring-system/issues/124) |
| 3.1.4 | CI fixes after SCRUM-37 | 1 | ✅ Done (Sprint 4) | SCRUM-44 | [#133](https://github.com/StKostyk/award-monitoring-system/issues/133) |
| 3.1.5 | Fixes and refactor sweep from the Feature 3.1 validation | 3 | ✅ Done (Sprint 4) | SCRUM-45 | [#135](https://github.com/StKostyk/award-monitoring-system/issues/135) |
| 3.1.6 | CI fixes: nginx proxy snippet and Kyiv dates | 1 | ✅ Done (Sprint 4) | SCRUM-46 | [#140](https://github.com/StKostyk/award-monitoring-system/issues/140) |

### Epic 4 — Approval Workflow Engine (Jira epic SCRUM-47)
Tracker: [EPIC-04-APPROVAL-WORKFLOW-STATUS.md](../epics/EPIC-04-APPROVAL-WORKFLOW-STATUS.md). Kickoff 2026-10-06; the organisational award model comes first, the achievement pages last. Story 2.4.1 from Epic 2 belongs here. Feature 4.1 PRD approved 2026-10-06: [feature-4.1-workflow-engine-core.md](../features/epic-04/feature-4.1-workflow-engine-core.md). Feature 4.2 PRD approved 2026-10-08: [feature-4.2-review-period-escalation.md](../features/epic-04/feature-4.2-review-period-escalation.md).

| ID | Story | Points | Sprint | Jira | Issue |
|----|-------|--------|--------|------|-------|
| 4.1.0 | Organisational awards: unit recipients and submitter | 5 | ✅ Done | SCRUM-48 | [#142](https://github.com/StKostyk/award-monitoring-system/issues/142) |
| 4.1.1 | Reviewer queue with claim, release and hand-over | 8 | ✅ Done | SCRUM-49 | [#143](https://github.com/StKostyk/award-monitoring-system/issues/143) |
| 4.1.2 | Review decisions: approve, reject, return, escalate to the dean | 8 | ✅ Done | SCRUM-50 | [#144](https://github.com/StKostyk/award-monitoring-system/issues/144) |
| 4.1.3 | Withdraw an unclaimed request and resubmit a returned award | 3 | ✅ Done | SCRUM-51 | [#145](https://github.com/StKostyk/award-monitoring-system/issues/145) |
| 4.1.4 | Batch review with template responses (US-004) | 5 | ✅ Done | SCRUM-52 | [#146](https://github.com/StKostyk/award-monitoring-system/issues/146) |
| 4.1.5 | Fixes from the Feature 4.1 validation | 3 | ✅ Done | SCRUM-58 | [#160](https://github.com/StKostyk/award-monitoring-system/issues/160) |
| 4.1.6 | Take over a request whose holder lost the role | 3 | ✅ Done | SCRUM-77 | [#200](https://github.com/StKostyk/award-monitoring-system/issues/200) |
| 4.1.7 | Hide delete on the edit form of a returned draft | 1 | ✅ Done | SCRUM-76 | [#199](https://github.com/StKostyk/award-monitoring-system/issues/199) |
| 4.2.1 | Per-faculty review period set by the dean | 3 | ✅ Done | SCRUM-53 | [#147](https://github.com/StKostyk/award-monitoring-system/issues/147) |
| 4.2.2 | Overdue detection, escalation notice and SLA metrics | 5 | ✅ Done | SCRUM-54 | [#148](https://github.com/StKostyk/award-monitoring-system/issues/148) |
| 2.4.1 | Award correction by reviewers | 5 | ✅ Done | SCRUM-55 | [#149](https://github.com/StKostyk/award-monitoring-system/issues/149) |
| 4.2.3 | Keep the decision dialog open until the answer | 2 | ✅ Done | SCRUM-59 | [#162](https://github.com/StKostyk/award-monitoring-system/issues/162) |
| 4.2.4 | Fixes from the Feature 4.2 walk | 2 | ✅ Done | SCRUM-60 | [#169](https://github.com/StKostyk/award-monitoring-system/issues/169) |
| 4.2.5 | Refactor from the Feature 4.2 sweep | 3 | ✅ Done | SCRUM-61 | [#171](https://github.com/StKostyk/award-monitoring-system/issues/171) |
| 4.2.6 | Review list: overdue notice chip clipped at 1280 px | 1 | ✅ Done | SCRUM-78 | [#202](https://github.com/StKostyk/award-monitoring-system/issues/202) |
| 4.3.1 | Colleague visibility and the achievements page | 5 | ✅ Done | SCRUM-56 | [#150](https://github.com/StKostyk/award-monitoring-system/issues/150) |
| 4.3.2 | Unit achievement pages and public achievements | 5 | ✅ Done | SCRUM-57 | [#151](https://github.com/StKostyk/award-monitoring-system/issues/151) |
| 4.3.3 | Fixes from the Feature 4.3 validation | 2 | ✅ Done | SCRUM-62 | [#176](https://github.com/StKostyk/award-monitoring-system/issues/176) |
| 4.3.6 | Public rate limit for the organisation list | 1 | ✅ Done | SCRUM-72 | [#192](https://github.com/StKostyk/award-monitoring-system/issues/192) |
| 4.3.7 | Peer take-over of overdue requests and no self-escalation for deans | 3 | ✅ Done | SCRUM-73 | [#193](https://github.com/StKostyk/award-monitoring-system/issues/193) |
| 4.3.9 | Achievement card names do not follow a language switch | 1 | ✅ Done | SCRUM-79 | [#204](https://github.com/StKostyk/award-monitoring-system/issues/204) |
| 4.3.4 | Review decisions in the personal data export | 3 | ✅ Done | SCRUM-70 | [#190](https://github.com/StKostyk/award-monitoring-system/issues/190) |
| 4.3.5 | Wire the business metrics to the award workflow | 2 | ✅ Done | SCRUM-71 | [#191](https://github.com/StKostyk/award-monitoring-system/issues/191) |
| 4.3.8 | Documents shown to colleagues on a shared award | 5 | ✅ Done | SCRUM-74 | [#194](https://github.com/StKostyk/award-monitoring-system/issues/194) |

### Epic 7 — Notification & Communication System (Jira epic SCRUM-63)
Tracker: [EPIC-07-NOTIFICATIONS-STATUS.md](../epics/EPIC-07-NOTIFICATIONS-STATUS.md). Kickoff 2026-10-09; Spring events on the Spring Modulith publication registry (no Kafka), e-mail and in-app channels (SMS out of scope, push with Epic 8). Story 1.3.2 from Epic 1 belongs here.

| ID | Story | Points | Sprint | Jira | Issue |
|----|-------|--------|--------|------|-------|
| 7.1.1 | Event publication registry for the after-commit mails | 5 | ✅ Done | SCRUM-64 | [#181](https://github.com/StKostyk/award-monitoring-system/issues/181) |
| 7.1.2 | Module boundaries checked by Spring Modulith | 8 | ✅ Done | SCRUM-65 | [#182](https://github.com/StKostyk/award-monitoring-system/issues/182) |
| 7.1.3 | Drop the duplicate async error log for undelivered mails | 1 | ✅ Done | SCRUM-75 | [#197](https://github.com/StKostyk/award-monitoring-system/issues/197) |
| 7.2.1 | In-app notifications from award and review events | 5 | ✅ Done | SCRUM-66 | [#183](https://github.com/StKostyk/award-monitoring-system/issues/183) |
| 7.2.2 | Notification centre in the toolbar | 5 | ✅ Done | SCRUM-67 | [#184](https://github.com/StKostyk/award-monitoring-system/issues/184) |
| 1.3.2 | Notification preferences | 3 | ✅ Done | SCRUM-16 | [#39](https://github.com/StKostyk/award-monitoring-system/issues/39) |
| 7.2.3 | Delivery log and notifications in the personal data export | 3 | ✅ Done | SCRUM-68 | [#185](https://github.com/StKostyk/award-monitoring-system/issues/185) |
| 7.2.4 | HTML e-mail templates in the university brand | 3 | ✅ Done | SCRUM-69 | [#186](https://github.com/StKostyk/award-monitoring-system/issues/186) |
| 7.2.5 | Name the target level in the passed-on notification text | 1 | ✅ Done | SCRUM-82 | [#239](https://github.com/StKostyk/award-monitoring-system/issues/239) |
| 7.2.6 | Save only the changed notification preferences | 1 | ✅ Done | SCRUM-83 | [#242](https://github.com/StKostyk/award-monitoring-system/issues/242) |
| 7.2.7 | Out-of-range notification page shows the empty state | 1 | ✅ Done | SCRUM-84 | [#245](https://github.com/StKostyk/award-monitoring-system/issues/245) |

### Epic 6 — Compliance & Audit System (Jira epic SCRUM-85)
Tracker: [EPIC-06-COMPLIANCE-AUDIT-STATUS.md](../epics/EPIC-06-COMPLIANCE-AUDIT-STATUS.md). Kickoff 2026-10-15; versioned privacy notice and consent records, privacy centre, legal hold, erasure after a 30-day grace period, nightly retention job. Story 6.1.5 replaces 2.4.2. Feature 6.1 PRD approved 2026-10-15: [feature-6.1-consent-data-subject-rights.md](../features/epic-06/feature-6.1-consent-data-subject-rights.md). Feature 6.2 PRD approved 2026-10-18: [feature-6.2-automated-data-retention.md](../features/epic-06/feature-6.2-automated-data-retention.md); 6.2.3 (SCRUM-95, #272) is added when it starts.

| ID | Story | Points | Sprint | Jira | Issue |
|----|-------|--------|--------|------|-------|
| 6.1.1 | Versioned privacy notice and consent records | 5 | ✅ Done | SCRUM-86 | [#252](https://github.com/StKostyk/award-monitoring-system/issues/252) |
| 6.1.2 | Privacy centre in the profile | 5 | ✅ Done | SCRUM-87 | [#253](https://github.com/StKostyk/award-monitoring-system/issues/253) |
| 6.1.3 | Legal hold on accounts | 3 | ✅ Done | SCRUM-88 | [#254](https://github.com/StKostyk/award-monitoring-system/issues/254) |
| 6.1.4 | Account erasure request with a 30-day grace period | 5 | ✅ Done | SCRUM-89 | [#255](https://github.com/StKostyk/award-monitoring-system/issues/255) |
| 6.1.5 | Erasure across modules with anonymised awards | 8 | ✅ Done | SCRUM-90 | [#256](https://github.com/StKostyk/award-monitoring-system/issues/256) |
| 6.1.6 | Mails without a reverse DNS lookup per message | 2 | ✅ Done | SCRUM-93 | [#266](https://github.com/StKostyk/award-monitoring-system/issues/266) |
| 6.1.7 | Shared erasure guard, postponement enum and consent append helper | 2 | ✅ Done | SCRUM-94 | [#268](https://github.com/StKostyk/award-monitoring-system/issues/268) |
| 6.2.1 | Nightly retention job with configurable policies | 5 | ✅ Done | SCRUM-91 | [#257](https://github.com/StKostyk/award-monitoring-system/issues/257) |
| 6.2.2 | Compliance page for retention runs, erasures and legal holds | 5 | ✅ Done | SCRUM-92 | [#258](https://github.com/StKostyk/award-monitoring-system/issues/258) |
| 6.2.3 | Retention of accounts, consent records and audit partitions | 5 | ✅ Done | SCRUM-95 | [#272](https://github.com/StKostyk/award-monitoring-system/issues/272) |
| 6.2.4 | Nightly erasure run outside the clock-change hour | 1 | 👀 In review | SCRUM-96 | [#277](https://github.com/StKostyk/award-monitoring-system/issues/277) |

### Later epics
| ID | Story | Points | Epic |
|----|-------|--------|------|
| US-006 | AI-Powered Document Parsing (deferred) | 21 | Documents |
| US-007 | Confidence Score Display (deferred) | 8 | Documents |
| US-008 | Personal Dashboard | 13 | Analytics |
| US-010 | Executive Dashboard | 21 | Analytics |
| — | Production hardening: backend-for-frontend with an HttpOnly cookie, refresh tokens hashed at rest, rate limits for a campus NAT, Spring Session on Redis (design review 2026-10-04) | - | Epic 9 |

---

## Completed

| Sprint | Stories | Points |
|--------|---------|--------|
| Sprint 1 | Setup (7 tasks) | 14 |
| Sprint 2 | Feature 1.1 Core Authentication (1.1.0–1.1.6), Feature 1.2 RBAC (1.2.1–1.2.3) | 46 |
| Sprint 3 | Feature 1.2 fixes (1.2.4), Feature 2.1 Award Creation & Validation (2.1.0–2.1.6), Feature 1.3 User Profile Management (1.3.1, 1.3.3, 1.3.4), Feature 2.2 Award Version History & Audit Trail (2.2.1–2.2.3), Feature 2.3 Award Status Tracking (2.3.1, 2.3.2), Feature 3.0 deployment preparation (3.0.1–3.0.4), document upload (3.1.1, 3.1.2) | 91 |

Total Completed: 167 points (Epics 1 and 2 except the stories moved to later epics, Feature 3.0, Feature 3.1); Sprint 4 so far: 2.1.7 (3), 2.1.8 (8), 3.1.3 (3), 3.1.4 (1), 3.1.5 (3), 3.1.6 (1), Feature 4.1 4.1.0–4.1.5 (32), Feature 4.2 4.2.1–4.2.3 and 2.4.1 (15), Feature 4.3 4.3.1–4.3.2 (10)

---

## Icebox (Deferred)

| ID | Story | Reason | Issue |
|----|-------|--------|-------|
| 1.3.5 | MFA (TOTP, SMS, WebAuthn) | Not needed for the demo; design kept in the auth document | [#41](https://github.com/StKostyk/award-monitoring-system/issues/41) |
| — | HR validation endpoint | No HR system available; replaced by domain check and department selection at registration | [#47](https://github.com/StKostyk/award-monitoring-system/issues/47) |

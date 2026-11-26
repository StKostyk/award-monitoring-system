# Sprint 3 Summary
## Award Creation, Tracking and the Start of Document Processing

**Sprint Duration**: September 28 – October 4, 2026  
**Sprint Goal**: An employee creates, checks, submits and tracks an award (Features 2.1–2.3)  
**Status**: ✅ Completed, goal met; Epic 2 closed on October 1 and Epic 3 started

---

## Committed and Delivered

| Scope | Stories | Points |
|-------|---------|--------|
| Committed: Feature 1.2 fixes, Feature 1.3, Features 2.1–2.3 | 1.2.4, 1.3.1, 1.3.3, 2.1.0–2.1.3, 2.2.1, 2.2.2, 2.3.1 | 47 |
| Validation and manual-run fixes | 1.3.4, 2.1.4, 2.2.3, 2.3.2, 2.1.6 | 15 |
| Database objects after the Epic 2 documentation sync | 2.1.5 | 2 |
| Pulled in from Epic 3 | 3.0.1–3.0.4, 3.1.1, 3.1.2 | 27 |
| **Delivered** | 22 stories, 35 pull requests merged | **91** |

Not started: 1.3.2 notification preferences (moved to the notifications epic), 3.1.3 malware scanning (Sprint 4).
Velocity: 91 (Sprint 2: 46). Fix stories, 1.2.4 included, were 22 % of the delivered points.

## Quality

| Metric | First count (Sept 29) | End of sprint |
|--------|-----------------|---------------|
| Backend unit and slice tests | 445 | 656 |
| Integration and functional tests | 124 | 223 |
| Line coverage (JaCoCo, gate ≥ 85 %) | 98.6 % | 98.4 % |
| Checkstyle / PMD / SpotBugs | 0 / 0 / 0 | 0 / 0 / 0 |
| Frontend unit tests (Vitest) | 207 | 393 |
| Playwright end-to-end tests | 30 | 54, now a blocking CI job |

CI on `develop`: 35 runs, 3 failed. Two were `DelegationFT`/`RoleAssignmentFT` failures that passed on the next run (Sept 28 and 30); one was the first CI run of the Playwright job (`f9` draft deletion, Oct 4). No reverts.

## What Went Well
- Every feature went through the same loop: PRD, stories with tests first, validation against the acceptance criteria, then a small fix story. Epic 2 closed in four days with its documentation reconciled to the code.
- The local gate (static checks first, then the full run) kept static analysis at zero across 35 merges.
- The design review on Sunday settled all open decisions except two that need answers from the university (student/staff address pattern, university servers).
- Running the manual scenarios on the container stack found defects the automated suites missed: the Swagger sign-in across origins, the date picker format, saving a draft after an incomplete submission.

## What to Improve
- **Flaky functional tests.** `DelegationFT` and `RoleAssignmentFT` failed twice on CI and passed on the next run. Action: find the shared state or clock dependency and fix it before Epic 4 adds more role-dependent tests.
- **Manual runs lag behind delivery.** Features 2.1–2.3 were validated by tests on Sept 29 – Oct 1 but only walked through manually on Oct 4–5, and Epic 1's runs move to Oct 11. Action: run each feature's manual scenario within two days of its validation, before the next feature starts.
- **One PRD step contradicted an acceptance criterion.** Feature 2.2 §9 step 15 asked for a draft's audit export from the page, which AC-1.8 of Feature 2.1 forbids. Action: when writing §9, check every step against the access rules of the earlier features.

## Decisions
- Review decisions of Oct 4–5 (level routing and impact scores, working-day review periods, role-assignment rules, «Відкликати» while unclaimed, organisational awards, Epic 9 production hardening) are recorded in the review list and move into the docs PR and a fix story in Sprint 4.
- Swagger UI is served through the application origin in the Compose stack (SCRUM-42).

## Risks
- Epic 4 grows with the review decisions (organisational awards, escalation, claim and hand-over); its PRD needs careful sizing.
- The deployment server is not provisioned (no spend until the defence date is known).

## Next Sprint (Sprint 4, October 5–11)
- Docs PR with the review decisions; fix story for the decided changes (~8 points)
- 3.1.3 malware scanning (3 points) and the Feature 3.1 validation; Epic 3 close
- Manual runs of Epic 1 and the theming checks on October 11
- Epic 4 kickoff and the first PRD

---

**Created**: October 5, 2026  
**Author**: Stefan Kostyk

# Sprint Retrospectives
## Award Monitoring & Tracking System

---

## Sprint 1: Development Environment Setup
**Date**: January 7-10, 2026  
**Sprint Goal**: Establish development infrastructure and CI/CD pipeline

### What Went Well ✅
- Docker setup was straightforward with existing scripts
- GitHub Actions pipeline configured in 2 hours
- SonarQube integration caught 3 potential issues early

### What Could Improve 🔄
- Underestimated PostgreSQL configuration time
- Need better local dev documentation
- Should have fully set up Redis earlier

### Action Items for Next Sprint 📋
- [ ] Create local dev troubleshooting guide
- [ ] Add Redis full caching configuration
- [ ] Time-box configuration tasks to 2 hours max

### Metrics
| Metric | Target | Actual |
|--------|--------|--------|
| Stories Completed | 3 | 3 |
| Story Points | 14 | 14 |
| Velocity | - | 14 |
| Bugs Found | 0 | 1 |
| Time Spent | 20h | 23h |

---

## Sprint 2: Authentication Core
**Date**: January 11 - February 15, 2026  
...

---

## Sprint 3: Award Creation and Tracking
**Date**: September 28 - October 4, 2026  
**Sprint Goal**: An employee creates, checks, submits and tracks an award (Features 2.1–2.3)  
**Summary**: [SPRINT-03.md](sprints/SPRINT-03.md)

### What Went Well ✅
- PRD, tests first, validation and a small fix story per feature; Epic 2 closed in four days
- Static analysis at zero across 35 merges thanks to the static-first local gate
- Manual scenarios on the container stack found defects the automated suites missed

### What Could Improve 🔄
- `DelegationFT` and `RoleAssignmentFT` failed twice on CI and passed on the next run
- Manual runs came days after each feature's validation
- One manual step contradicted an earlier feature's access rule

### Action Items for Next Sprint 📋
- [ ] Find and fix the shared state or clock dependency in `DelegationFT` and `RoleAssignmentFT`
- [ ] Run each feature's manual scenario within two days of its validation
- [ ] Check every manual step against the access rules of earlier features when writing a PRD

### Metrics
| Metric | Target | Actual |
|--------|--------|--------|
| Story Points | 47 | 91 |
| Velocity | - | 91 |
| Line Coverage | ≥ 85 % | 98.4 % |
| CI failures on `develop` | 0 | 3 of 35 runs |

# Agile Methodology Selection
## Award Monitoring & Tracking System

> **Phase 11 Deliverable**: Project Management & Agile Framework  
> **Document Version**: 1.0  
> **Last Updated**: December 2025  
> **Author**: Stefan Kostyk  
> **Selected Framework**: Solo Scrum (Scrum adapted for solo developer)

---

## Executive Summary

This document defines the agile methodology for the Award Monitoring & Tracking System development. Given the solo developer context and portfolio project nature, we adopt **Solo Scrum** - a lightweight adaptation of Scrum principles optimized for individual productivity while maintaining enterprise-grade discipline.

---

## 1. Framework Selection Analysis

### 1.1 Framework Comparison

| **Framework** | **Team Size** | **Complexity** | **Solo Developer Fit** | **Decision** |
|---------------|---------------|----------------|------------------------|--------------|
| **Scrum** | 3-9 members | Medium | Adaptable with modifications | ✅ Selected |
| **Kanban** | Any | Low | Good for maintenance | Partial adoption |
| **SAFe** | 50+ members | High | Over-engineered for solo | ❌ Rejected |
| **XP** | 2-12 members | High | Pair programming N/A | ❌ Rejected |

### 1.2 Selection Rationale: Solo Scrum

**Why Scrum (Adapted)?**
- **Time-boxed iterations** provide clear milestones for portfolio demonstration
- **Sprint planning** enforces realistic scope management
- **Retrospectives** enable continuous improvement even as solo developer
- **Backlog refinement** maintains prioritized work queue
- **Increment delivery** demonstrates working software regularly

**Adaptations for Solo Development:**
- Daily standups → Daily journaling/task review (5 min)
- Sprint planning → Self-facilitated planning session (1 hour)
- Sprint review → Demo recording and documentation update
- Retrospective → Written reflection and process improvement notes

---

## 2. Solo Scrum Framework

### 2.1 Sprint Structure

| **Element** | **Duration** | **Description** |
|-------------|--------------|-----------------|
| **Sprint Length** | 2 weeks | Balanced iteration for meaningful increments |
| **Planning** | 1 hour | Backlog selection and task breakdown |
| **Daily Review** | 15 min | Progress check, blocker identification |
| **Sprint Review** | 30 min | Demo preparation, increment documentation |
| **Retrospective** | 30 min | Process reflection and improvement |

### 2.2 Sprint Cadence

```
Week 1                          Week 2
┌─────────────────────────┐    ┌─────────────────────────┐
│ Mon: Sprint Planning    │    │ Mon: Development        │
│ Tue-Fri: Development    │    │ Tue-Thu: Development    │
│                         │    │ Fri: Review + Retro     │
└─────────────────────────┘    └─────────────────────────┘
```

### 2.3 Ceremonies (Adapted)

#### Sprint Planning (1 hour)
1. Review previous sprint outcomes
2. Select user stories from prioritized backlog
3. Break stories into technical tasks
4. Estimate tasks using Fibonacci points
5. Commit to sprint goal

#### Daily Task Review (15 min)
- Review yesterday's progress
- Plan today's focus
- Identify blockers and solutions
- Update task board

#### Sprint Review (30 min)
- Demo completed features (record video)
- Update documentation
- Prepare portfolio materials
- Tag release in Git

#### Sprint Retrospective (30 min)
Document in `RETROSPECTIVES.md`:
- What went well?
- What could improve?
- Action items for next sprint

---

## 3. Artifacts

### 3.1 Product Backlog

**Location**: GitHub Issues + `docs/project-management/BACKLOG.md`

**Structure**:
- Epics (large features) → User Stories → Tasks
- Prioritized using MoSCoW method (Must/Should/Could/Won't)
- Estimated in story points (Fibonacci: 1, 2, 3, 5, 8, 13, 21)

### 3.2 Sprint Backlog

**Location**: GitHub Project Board

**Columns**:
| Column | Description |
|--------|-------------|
| **Backlog** | Prioritized items for future sprints |
| **Sprint** | Current sprint committed items |
| **In Progress** | Actively working (WIP limit: 3) |
| **Review** | Code review/testing |
| **Done** | Meets Definition of Done |

### 3.3 Increment

Each sprint delivers:
- Working, tested code
- Updated documentation
- Release notes
- Portfolio-ready artifacts

---

## 4. Kanban Elements (Hybrid)

### 4.1 Work-in-Progress (WIP) Limits

| **Column** | **WIP Limit** | **Rationale** |
|------------|---------------|---------------|
| In Progress | 3 | Focus and context-switching reduction |
| Review | 2 | Ensure quality before completion |
| Blocked | 1 | Immediate resolution required |

### 4.2 Flow Metrics

Track weekly:
- **Lead Time**: Backlog → Done (target: <5 days for stories)
- **Cycle Time**: In Progress → Done (target: <3 days)
- **Throughput**: Stories completed per sprint

---

## 5. Tools & Implementation

### 5.1 Tool Stack

| **Purpose** | **Tool** | **Usage** |
|-------------|----------|-----------|
| **Backlog Management** | GitHub Issues | User stories, bugs, tasks |
| **Sprint Board** | GitHub Projects | Kanban board with automation |
| **Time Tracking** | Toggl/Manual | Velocity calculation |
| **Documentation** | Markdown files | Sprint notes, retrospectives |
| **Version Control** | Git + GitHub | Code, branching, releases |

### 5.2 GitHub Labels

```
Priority:    p0-critical, p1-high, p2-medium, p3-low
Type:        epic, story, task, bug, chore
Status:      blocked, needs-review, ready
Sprint:      sprint-1, sprint-2, ...
Epic:        epic-auth, epic-awards, epic-workflow, ...
```

### 5.3 Branch Strategy

```
main (production-ready)
  └── develop (integration)
        ├── feature/US-001-user-registration
        ├── feature/US-003-award-submission
        └── bugfix/fix-validation-error
```

---

## 6. Definition of Ready (DoR)

A user story is ready for sprint when:

- [ ] User story follows format: "As a [role], I want [goal], so that [benefit]"
- [ ] Acceptance criteria are clear and testable
- [ ] Dependencies identified and resolved
- [ ] Story estimated (≤13 points, else split)
- [ ] Technical approach understood
- [ ] No blockers preventing immediate start

---

## 7. Velocity & Capacity

### 7.1 Initial Velocity Estimate

| **Factor** | **Hours/Week** | **Notes** |
|------------|----------------|-----------|
| Available time | 20-30 | Part-time/portfolio project |
| Focus factor | 70% | Meetings, research, breaks |
| **Effective hours** | 14-21 | Actual coding time |

**Initial Velocity Target**: 15-20 story points per sprint

### 7.2 Velocity Tracking

After 3 sprints, calculate:
```
Average Velocity = (Sprint1 + Sprint2 + Sprint3) / 3
```

Adjust sprint commitments based on actual velocity.

---

## 8. Communication & Reporting

### 8.1 Sprint Reports

End of each sprint, create:
1. **Sprint Summary** in `docs/sprints/SPRINT-XX.md`
2. **Release Notes** in `CHANGELOG.md`
3. **Demo Video** (optional, for portfolio)

### 8.2 Stakeholder Updates

| **Stakeholder** | **Frequency** | **Format** |
|-----------------|---------------|------------|
| Self (retrospective) | Every sprint | Written reflection |
| Portfolio reviewers | Monthly | README + demo |
| Prof. Biloskurskyi | Monthly | Progress summary |

---

## Summary

**Selected Framework**: Solo Scrum with Kanban elements

**Key Practices**:
- 2-week sprints with clear ceremonies
- GitHub-based backlog and board management
- WIP limits for focus
- Continuous documentation and portfolio building
- Velocity-based planning after initial sprints

---

**Document Version**: 1.0  
**Created**: December 2025  
**Next Review**: After Sprint 3 (velocity calibration)


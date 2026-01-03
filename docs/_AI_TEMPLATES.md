# Template 1: New Phase Start Message

# Phase [NUMBER]: [PHASE NAME]

## Context
Award Monitoring System - Pre-development documentation project
Previous: Phase [N-1] complete (v0.[N-1].0)
Roadmap: `Enterprise_Pre-Development_Roadmap.md` lines [XXX-YYY]

## Deliverables for This Phase
1. [Deliverable 1 from roadmap]
2. [Deliverable 2 from roadmap]
3. [Deliverable 3 from roadmap]
4. Ukrainian translations

## References
@Enterprise_Pre-Development_Roadmap.md
@README.md
@CHANGELOG.md

## Task
Create Phase [N] deliverables following existing documentation quality and format.
Don't over-engineer - full context but concise.


# Template 2: End of Phase Message
# Phase [N] Complete - Finalize

## Completed Deliverables
- [List what was created]

## Tasks
1. **CHANGELOG.md**: Add v0.[N].0 entry with:
   - Added section with all deliverables
   - Deliverables completed checklist
   - Portfolio value section
   - Statistics

2. **README.md**: Update:
   - Project status section (current phase, next phase)
   - Phase table (mark complete, add next)
   - Add Phase [N] documentation section with links and key achievements

3. **Commit message**: Generate in this format:
    - feat: complete Phase [N] - [Phase Name]  
    DELIVERABLES COMPLETED:  
    [Bullet list of all deliverables created]


4. **.cursor/rules update instruction**:
   - Update "Current Status" section:


# 📋 Quick Reference: .cursor/rules Update
After each phase, update these lines in .cursor/rules:
## Current Status
**Version**: v0.[NEW_VERSION].0
**Completed Phases**: 1-[NEW_NUMBER] (Project Initiation → [Latest Phase Name])
**Next Phase**: [NEXT_NUMBER] ([Next Phase Name])
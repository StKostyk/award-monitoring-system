# Requirements Gathering Framework

## 1. Framework Overview

### 1.1 Purpose & Objectives
This framework provides a comprehensive methodology for gathering, validating, and documenting requirements for the Award Monitoring & Tracking System. The approach ensures complete stakeholder coverage while maintaining enterprise-grade documentation standards.

### 1.2 Requirements Categories
| Category | Description | Primary Stakeholders | Documentation Level |
|----------|-------------|---------------------|-------------------|
| **Functional Requirements** | What the system must do | End Users, Management | Detailed user stories with acceptance criteria |
| **Non-Functional Requirements** | How the system must perform | Technical Teams, Management | Quantified performance specifications |
| **Business Rules** | Institutional policies and constraints | Executive, Compliance | Formalized rule documentation |
| **Compliance Requirements** | Regulatory and legal mandates | GDPR Officer, InfoSec | Compliance mapping matrix |
| **Integration Requirements** | External system connections | Technical Teams, System Ops | Technical specifications |
| **Security Requirements** | Protection and access controls | InfoSec Team, GDPR Officer | Security control documentation |

### 1.3 Requirements Lifecycle
```
Discovery → Analysis → Documentation → Validation → Approval → Baseline → Change Management
```

## 2. Discovery Workshop Planning

### 2.1 Workshop Types & Objectives

#### **Executive Vision Workshop**
- **Duration:** 2 hours
- **Participants:** Rector, Dean, Department Chair, Project Team
- **Objectives:** Define strategic vision, success criteria, constraints
- **Deliverables:** Vision statement, success metrics, executive requirements

#### **Business Process Workshop**
- **Duration:** 3 hours (1 session)
- **Participants:** Faculty Secretaries, Dean, Department Chair
- **Objectives:** Map current processes, identify pain points, define future state
- **Deliverables:** Process maps, user journeys, workflow requirements

#### **Technical Requirements Workshop**
- **Duration:** 2 hours
- **Participants:** Development Team, Dean, Technical stakeholders
- **Objectives:** Define technical requirements, integration needs, constraints
- **Deliverables:** Technical requirements, architecture constraints, integration specs

#### **User Experience Workshop**
- **Duration:** 2 hours
- **Participants:** End Users, Development Team
- **Objectives:** Define user needs, interface requirements, accessibility needs
- **Deliverables:** User personas, UI/UX requirements, accessibility specifications

### 2.2 Workshop Planning Template

#### **Pre-Workshop Phase (1 week before)**
- [ ] **Objective Definition:** Clear workshop goals and success criteria
- [ ] **Participant Selection:** Right stakeholders with decision authority
- [ ] **Material Preparation:** Background documents, templates, tools
- [ ] **Logistics Setup:** Room booking, technology setup, catering
- [ ] **Pre-reading Distribution:** Background materials sent 48 hours prior

#### **Workshop Agenda Template**
```
1. Opening & Introductions (15 minutes)
   - Welcome and objectives
   - Participant introductions
   - Ground rules and expectations

2. Context Setting (30 minutes)
   - Project overview and vision
   - Current state analysis
   - Success criteria review

3. Requirements Discovery (2-3 hours)
   - Facilitated brainstorming
   - Process mapping exercises
   - Requirement prioritization
   - Pain point identification

4. Break (15 minutes)

5. Requirements Analysis (1-2 hours)
   - Requirement categorization
   - Feasibility discussion
   - Dependency mapping
   - Risk identification

6. Validation & Next Steps (30 minutes)
   - Requirement review and confirmation
   - Action items assignment
   - Next steps planning
   - Closing remarks
```

#### **Post-Workshop Phase (1 week after)**
- [ ] **Documentation:** Comprehensive meeting minutes and requirements capture
- [ ] **Validation:** Participant review and confirmation of captured requirements
- [ ] **Analysis:** Requirement categorization and priority assignment
- [ ] **Distribution:** Final documentation distributed to all participants
- [ ] **Follow-up:** Action items tracked and next steps initiated

### 2.3 Workshop Facilitation Guidelines

#### **Facilitator Responsibilities**
- Maintain focus on objectives
- Ensure balanced participation
- Manage time effectively
- Capture all inputs accurately
- Resolve conflicts constructively

#### **Facilitation Techniques**
- **Brainstorming:** Open idea generation
- **Affinity Mapping:** Grouping related concepts
- **Dot Voting:** Priority ranking
- **Process Mapping:** Visual workflow documentation
- **Root Cause Analysis:** Deep problem investigation

#### **Workshop Tools & Materials**
- Flipchart paper and markers
- Sticky notes (multiple colors)
- Digital collaboration tools (Miro, Mural)
- Laptops/tablets for documentation
- Audio recording equipment (with consent)

## 3. Interview Templates & Procedures

### 3.1 Stakeholder Interview Categories

#### **Executive Interview Template**
**Duration:** 45-60 minutes  
**Format:** Structured with open-ended questions

**Opening Questions (5 minutes)**
- What is your role in relation to this project?
- What are your primary expectations for the system?
- How does this project align with institutional strategic goals?

**Strategic Requirements (20 minutes)**
- What business outcomes must this system deliver?
- What are the key success metrics you'll use to evaluate the project?
- What constraints (budget, timeline, resources) should we be aware of?
- What risks are you most concerned about?
- How will you measure ROI for this investment?

**Functional Requirements (15 minutes)**
- What specific capabilities are absolutely essential?
- What current pain points must be eliminated?
- What integration requirements do you foresee?
- What reporting and analytics capabilities do you need?

**Closing Questions (5 minutes)**
- What haven't we discussed that's important for your success?
- Who else should we speak with to complete our understanding?
- What are your expectations for ongoing involvement in the project?

#### **Management Interview Template**
**Duration:** 60-90 minutes  
**Format:** Semi-structured with process deep-dives

**Role & Context (10 minutes)**
- Describe your current role and responsibilities
- Walk me through your typical workday related to award processing
- What systems and tools do you currently use?

**Current State Analysis (30 minutes)**
- Describe the current award processing workflow step-by-step
- What works well in the current process?
- What are the biggest pain points and frustrations?
- How much time do you spend on award-related tasks weekly?
- What errors or issues occur most frequently?

**Future State Requirements (30 minutes)**
- Describe your ideal award processing workflow
- What functionality would save you the most time?
- What information do you need to see in reports and dashboards?
- How should approval workflows be structured?
- What notifications and alerts would be helpful?

**Technical & Integration Needs (15 minutes)**
- What other systems need to integrate with the award system?
- What data do you need to import/export?
- What security and access controls are needed?
- What training and support will your team need?

**Wrap-up (5 minutes)**
- What's the most important thing for us to get right?
- What concerns do you have about implementing a new system?

#### **End User Interview Template**
**Duration:** 45 minutes  
**Format:** User-centered with task walkthroughs

**User Background (10 minutes)**
- Tell me about your role and how long you've been here
- How often do you currently submit award requests?
- What devices do you typically use for work tasks?

**Current Experience (20 minutes)**
- Walk me through how you currently submit an award request
- What documents do you typically need to provide?
- What's confusing or frustrating about the current process?
- How do you track the status of your requests?
- What questions do you frequently have during the process?

**Ideal Experience (10 minutes)**
- If you could design the perfect award submission system, what would it look like?
- What information would you want to see about your awards?
- How would you like to receive notifications about status changes?
- What would make you feel confident that your request was handled properly?

**Technical Preferences (5 minutes)**
- Do you prefer mobile or desktop interfaces?
- What features are important for accessibility?
- How comfortable are you with new technology?

#### **Technical Interview Template**
**Duration:** 90-120 minutes  
**Format:** Technical deep-dive with architecture focus

**Technical Environment (15 minutes)**
- Describe the current technical infrastructure
- What systems would need to integrate with the award system?
- What are the current security standards and requirements?
- What compliance frameworks do you follow?

**Technical Requirements (45 minutes)**
- What are the performance requirements (response time, throughput)?
- What are the availability and reliability requirements?
- What are the scalability requirements for users and data volume?
- What security controls must be implemented?
- What backup and disaster recovery requirements exist?

**Integration & Architecture (30 minutes)**
- What authentication systems should be used?
- What data sources need to be integrated?
- What external services or APIs are required?
- What monitoring and logging requirements exist?
- What deployment and infrastructure constraints apply?

**Compliance & Security (20 minutes)**
- What specific GDPR requirements apply?
- What data retention and archival policies exist?
- What audit and compliance reporting is needed?
- What security incident response procedures are required?

**Technical Support (10 minutes)**
- What ongoing maintenance and support capabilities are needed?
- What documentation standards must be followed?
- What training will technical staff need?

### 3.2 Interview Best Practices

#### **Preparation Guidelines**
- Research participant background and role
- Review relevant documentation beforehand
- Prepare specific questions based on stakeholder type
- Set clear expectations for time and outcomes
- Confirm logistics (location, technology, recording)

#### **Conducting Interviews**
- Start with rapport building and context setting
- Use open-ended questions to encourage detailed responses
- Follow up with clarifying questions
- Avoid leading questions that bias responses
- Take detailed notes even if recording
- Summarize key points to confirm understanding

#### **Post-Interview Process**
- Document findings within 24 hours
- Validate understanding with participant if needed
- Categorize requirements by type and priority
- Identify conflicts or gaps for follow-up
- Schedule additional sessions if needed

## 4. Survey Design & Implementation

### 4.1 Survey Types & Objectives

#### **Stakeholder Needs Assessment Survey**
**Target Audience:** All stakeholders  
**Timing:** Project initiation  
**Duration:** 10-15 minutes  
**Objective:** Baseline understanding of needs and expectations

**Sample Questions:**
1. How satisfied are you with the current award management process? (1-5 scale)
2. What are your top 3 pain points with the current system?
3. Which features would provide the most value to you? (rank order)
4. How critical is mobile access for your role? (1-5 scale)
5. What concerns do you have about implementing a new system?

#### **Functional Requirements Survey**
**Target Audience:** End users and management  
**Timing:** Requirements gathering phase  
**Duration:** 20-30 minutes  
**Objective:** Detailed functional requirement prioritization

**Sample Questions:**
1. Rate the importance of each feature (Critical/Important/Nice-to-have):
   - Automated document parsing
   - Real-time notifications
   - Advanced search capabilities
   - Mobile submission
   - Analytics dashboard
   
2. For each workflow step, indicate your preference:
   - Single-click approval vs. detailed review required
   - Automatic notifications vs. manual check-in
   - Bulk operations vs. individual processing

#### **Technical Requirements Survey**
**Target Audience:** Technical stakeholders  
**Timing:** Architecture planning phase  
**Duration:** 15-20 minutes  
**Objective:** Technical constraint and requirement identification

**Sample Questions:**
1. Rate the criticality of each non-functional requirement (1-5 scale):
   - Response time <200ms
   - 99.9% uptime
   - Support for 1000+ concurrent users
   - Real-time data synchronization
   
2. Select applicable compliance requirements:
   - GDPR data protection
   - Financial audit trails
   - Academic record retention
   - Security incident reporting

### 4.2 Survey Implementation Framework

#### **Survey Design Principles**
- **Clear Objectives:** Each question serves a specific requirement gathering purpose
- **Appropriate Length:** Balance comprehensiveness with response rate
- **Logical Flow:** Group related questions and use progressive disclosure
- **Response Options:** Provide meaningful scales and choices
- **Accessibility:** Ensure compatibility with assistive technologies

#### **Survey Distribution Strategy**
| Stakeholder Group | Distribution Method | Response Target | Reminder Schedule |
|------------------|-------------------|-----------------|-------------------|
| **Executive** | Personal email invitation | 100% | Day 3, Day 7 |
| **Management** | Email with management endorsement | 90% | Day 2, Day 5, Day 7 |
| **End Users** | Multiple channels (email, announcements) | 80% | Day 3, Day 6, Day 9 |
| **Technical** | Direct technical team communication | 100% | Day 2, Day 5 |

#### **Data Collection & Analysis**
- **Collection Period:** 10 business days with reminder schedule
- **Response Monitoring:** Daily tracking with targeted follow-up
- **Data Quality:** Validation checks for incomplete responses
- **Analysis Approach:** Quantitative analysis with qualitative coding
- **Reporting:** Stakeholder-specific result summaries

### 4.3 Survey Tools & Technologies

#### **Recommended Platforms**
- **Microsoft Forms:** Integration with enterprise Office 365
- **SurveyMonkey:** Advanced analytics and professional templates
- **Typeform:** User-friendly interface with high completion rates
- **Google Forms:** Simple surveys with real-time collaboration

#### **Survey Analytics Dashboard**
- Real-time response rate monitoring
- Completion time analysis
- Response quality indicators
- Stakeholder group comparison
- Requirement priority rankings

## 5. Requirements Documentation Standards

### 5.1 User Story Template

```
**Epic:** [High-level feature area]
**User Story ID:** [Unique identifier]
**Title:** [Descriptive name]

**As a** [user type]
**I want** [functionality]
**So that** [business value]

**Acceptance Criteria:**
- Given [precondition]
- When [action]
- Then [expected result]

**Priority:** [High/Medium/Low]
**Complexity:** [Story points]
**Dependencies:** [Other user stories or technical requirements]
**Notes:** [Additional context or constraints]

**Definition of Done:**
- [ ] Code developed and unit tested (>85% coverage)
- [ ] Integration tests pass
- [ ] Security review completed
- [ ] Performance benchmarks met
- [ ] Accessibility compliance verified (WCAG AA)
- [ ] Documentation updated
- [ ] Stakeholder acceptance obtained
```

### 5.2 Non-Functional Requirements Template

```
**NFR ID:** [Unique identifier]
**Category:** [Performance/Security/Usability/Compliance]
**Requirement:** [Specific, measurable requirement statement]

**Rationale:** [Why this requirement is needed]
**Measurement Criteria:** [How success will be measured]
**Target Value:** [Specific quantified target]
**Acceptance Threshold:** [Minimum acceptable value]

**Testing Approach:** [How requirement will be validated]
**Dependencies:** [Related systems or components]
**Risk Level:** [Impact if not met]
**Implementation Notes:** [Technical considerations]
```

### 5.3 Requirements Traceability Matrix

| Requirement ID | Category | Source | Priority | Design Element | Test Case | Status |
|----------------|----------|---------|----------|----------------|-----------|--------|
| REQ-001 | Functional | User Interview | High | User Management Module | TC-001 | Approved |
| REQ-002 | Security | GDPR Officer | Critical | Authentication Service | TC-002 | Approved |
| REQ-003 | Performance | Technical Workshop | High | Database Layer | TC-003 | Draft |

## 6. Requirements Validation & Approval

### 6.1 Validation Methods

#### **Stakeholder Review Cycles**
- **Draft Review:** Initial stakeholder feedback on documented requirements
- **Formal Review:** Structured review with sign-off process
- **Cross-functional Validation:** Review across stakeholder groups for conflicts
- **Technical Feasibility:** Architecture and development team validation

#### **Validation Techniques**
- **Walkthrough Sessions:** Step-by-step requirement review with stakeholders
- **Prototype Validation:** Mock-ups and prototypes for user interface requirements
- **Scenario Testing:** Use case walkthrough for workflow requirements
- **Compliance Review:** Legal and GDPR validation for compliance requirements

### 6.2 Approval Workflow

```
Requirements Documentation → Stakeholder Review → Conflict Resolution → 
Technical Validation → Management Approval → Executive Sign-off → Baseline
```

#### **Approval Criteria**
- **Completeness:** All identified requirements documented
- **Clarity:** Requirements are unambiguous and testable
- **Consistency:** No conflicts between requirements
- **Feasibility:** Technical and business feasibility confirmed
- **Traceability:** Source and rationale clearly documented

### 6.3 Change Management Process

#### **Change Request Template**
```
**Change Request ID:** [Unique identifier]
**Submitted By:** [Stakeholder name and role]
**Date Submitted:** [Date]
**Priority:** [Critical/High/Medium/Low]

**Current Requirement:** [What needs to change]
**Proposed Change:** [Detailed description of change]
**Rationale:** [Why change is needed]
**Impact Analysis:** [Effect on scope, timeline, resources]

**Stakeholder Review:**
- Business Impact: [Assessment]
- Technical Impact: [Assessment]
- Resource Impact: [Assessment]

**Decision:** [Approved/Rejected/Deferred]
**Approval Authority:** [Name and role]
**Implementation Date:** [When change will be implemented]
```

#### **Change Approval Matrix**
| Change Impact | Approval Authority | Review Process | Timeline |
|---------------|-------------------|----------------|----------|
| **Minor** | Project Manager | Peer review | 2 days |
| **Moderate** | Department Head | Stakeholder review | 1 week |
| **Major** | Executive Sponsor | Full committee review | 2 weeks |
| **Critical** | Steering Committee | Emergency review | 24 hours |

## 7. Quality Assurance & Continuous Improvement

### 7.1 Quality Metrics

| Metric | Target | Measurement Method | Review Frequency |
|---------|--------|-------------------|------------------|
| **Requirement Completeness** | 100% | Traceability matrix coverage | Weekly |
| **Stakeholder Satisfaction** | >4.5/5.0 | Post-session surveys | After each session |
| **Requirement Stability** | <5% change rate | Change request tracking | Monthly |
| **Validation Coverage** | 100% | Validation tracking matrix | Bi-weekly |

### 7.2 Process Improvement

#### **Retrospective Process**
- **After Each Workshop:** Immediate feedback and process adjustments
- **Monthly Review:** Comprehensive methodology assessment
- **Phase Completion:** Full framework evaluation and improvement

#### **Lessons Learned Documentation**
- **What Worked Well:** Successful techniques and approaches
- **Areas for Improvement:** Process gaps and enhancement opportunities
- **Stakeholder Feedback:** Input on methodology effectiveness
- **Recommendations:** Process improvements for future phases

---

**Document Version:** 1.0  
**Created:** July 2025  
**Author:** Project Management Office  
**Next Review:** After each major requirements gathering session  
**Approval Required:** Dean (Prof. Martynyuk), Development Team Lead 
# Technical Writing Standards
## Award Monitoring & Tracking System

> **Phase 17 Deliverable**: Documentation & Knowledge Management  
> **Document Version**: 1.0  
> **Last Updated**: January 2026  
> **Author**: Stefan Kostyk  

---

## Overview

This document defines technical writing standards for all project documentation to ensure consistency, clarity, and maintainability.

---

## Document Structure

### Standard Header

Every document must begin with:

```markdown
# [Document Title]
## Award Monitoring & Tracking System

> **Phase X Deliverable**: [Phase Name]  
> **Document Version**: X.X  
> **Last Updated**: [Month Year]  
> **Author**: Stefan Kostyk  

---

## Table of Contents (for documents >3 sections)
```

### Section Organization

1. **Executive Summary** - Brief overview (2-3 sentences)
2. **Main Content** - Organized by logical sections
3. **References** - Links to related documents
4. **Appendices** - Supplementary materials (if needed)

---

## Writing Style

### Principles

| Principle | Description |
|-----------|-------------|
| **Clarity** | Use simple, precise language |
| **Conciseness** | Remove unnecessary words |
| **Consistency** | Use same terms throughout |
| **Completeness** | Cover all scenarios |

### Voice and Tone

- Use **active voice** (preferred): "The system validates input"
- Avoid passive where possible: ~~"Input is validated by the system"~~
- Use **second person** for instructions: "Click the button"
- Use **present tense** for descriptions: "The API returns JSON"

### Sentence Structure

- Keep sentences under 25 words
- One idea per sentence
- Lead with the most important information
- Use bullet points for lists of 3+ items

---

## Formatting Standards

### Headings

```markdown
# Document Title (H1) - One per document
## Major Section (H2)
### Subsection (H3)
#### Detail Level (H4) - Use sparingly
```

### Code Blocks

Always specify language for syntax highlighting:

```markdown
    ```java
    public class Example {
        // Java code
    }
    ```
```

### Tables

Use tables for structured data:

```markdown
| Column 1 | Column 2 | Column 3 |
|----------|----------|----------|
| Data 1   | Data 2   | Data 3   |
```

### Lists

- Use **bullet points** for unordered items
- Use **numbered lists** for sequential steps
- Keep list items parallel in structure

### Emphasis

| Format | Usage |
|--------|-------|
| **Bold** | UI elements, important terms |
| *Italic* | First use of technical terms |
| `code` | Commands, filenames, variables |
| > Blockquote | Important notes, warnings |

---

## Terminology

### Consistent Terms

| Use | Don't Use |
|-----|-----------|
| user | customer, client |
| click | press, hit |
| select | choose, pick |
| enter | type, input |
| award | achievement, recognition |
| submit | send, upload |

### Technical Terms

- Define technical terms on first use
- Use acronyms only after defining: "Application Programming Interface (API)"
- Maintain glossary for project-specific terms

### Capitalization

- **Product names**: Award Monitoring System
- **Features**: Award Submission, Review Queue
- **Roles**: System Admin, GDPR Officer
- **Technologies**: Spring Boot, PostgreSQL

---

## Document Types

### API Documentation

```markdown
## Endpoint Name

**Method**: GET/POST/PUT/DELETE  
**URL**: `/api/v1/resource`  
**Auth**: Required/Optional

### Parameters
| Name | Type | Required | Description |

### Response
| Code | Description |

### Example
```

### User Guides

- Start with user goal
- Step-by-step numbered instructions
- Include screenshots for complex UI
- Add troubleshooting tips

### Runbooks

- Clear trigger conditions
- Step-by-step procedures
- Expected outcomes for each step
- Rollback instructions
- Contact information

---

## File Naming

### Conventions

| Type | Convention | Example |
|------|------------|---------|
| English docs | UPPER_SNAKE_CASE.md | `USER_GUIDE.md` |
| Ukrainian docs | filename_ua.md | `USER_GUIDE_ua.md` |
| Code examples | lowercase-kebab.ext | `example-service.java` |
| Diagrams | lowercase-kebab.puml | `sequence-login.puml` |

### Location

```
docs/
├── api/              # API documentation
├── operations/       # Runbooks, troubleshooting
├── user/             # End-user documentation
└── ua/               # Ukrainian translations
    ├── api/
    ├── operations/
    └── user/
```

---

## Bilingual Support

### English (Primary)

- All documents created in English first
- Located in `docs/` directory

### Ukrainian (Translation)

- Suffix `_ua` added to filename
- Located in `docs/ua/` mirror structure
- Translated by native speaker
- Technical terms may remain in English with Ukrainian explanation

### Translation Guidelines

- Maintain same structure as English
- Preserve code blocks unchanged
- Translate screenshots if possible
- Use standard Ukrainian IT terminology

---

## Review Process

### Before Publishing

- [ ] Spell check completed
- [ ] Links verified working
- [ ] Code examples tested
- [ ] Formatting consistent
- [ ] Ukrainian translation created (if applicable)

### Version Control

- Document version in header
- Significant changes increment version
- Update "Last Updated" date
- Note changes in CHANGELOG.md

---

## Quality Checklist

| Criterion | Check |
|-----------|-------|
| Accurate | Information is correct and current |
| Clear | Easy to understand |
| Complete | Covers all necessary information |
| Concise | No unnecessary content |
| Consistent | Follows standards throughout |
| Accessible | Readable by target audience |

---

## Tools

### Recommended

| Tool | Purpose |
|------|---------|
| VS Code + Markdownlint | Markdown editing |
| PlantUML | Diagram creation |
| Grammarly | Grammar checking |
| markdown-link-check | Link validation |

### Markdown Extensions

```json
{
  "markdownlint.config": {
    "MD013": false,
    "MD033": false
  }
}
```

---

## References

- [Google Developer Documentation Style Guide](https://developers.google.com/style)
- [Microsoft Writing Style Guide](https://docs.microsoft.com/en-us/style-guide/welcome/)
- [PlantUML Documentation](https://plantuml.com/)

---

*These standards ensure documentation quality and consistency across the Award Monitoring System project.*


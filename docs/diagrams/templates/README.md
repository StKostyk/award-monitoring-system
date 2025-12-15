# PlantUML Templates & Standards

> **Phase 8 Deliverable**: System Design & Modeling  
> **Purpose**: Provide reusable templates and standards for diagram consistency  
> **Author**: Stefan Kostyk

---

## Template Files

| Template | Purpose | Use Case |
|----------|---------|----------|
| `c4-template.puml` | C4 Architecture Diagrams | System context, containers, components |
| `sequence-template.puml` | Sequence Diagrams | User interactions, API flows |
| `state-machine-template.puml` | State Machine Diagrams | Entity lifecycle management |
| `class-template.puml` | Class Diagrams | Domain model documentation |
| `deployment-template.puml` | Deployment Diagrams | Infrastructure documentation |

---

## Standards & Conventions

### Color Coding

| Element Type | Color | Hex Code |
|-------------|-------|----------|
| Frontend/UI | Light Blue | `#LightBlue` |
| Backend Services | Light Green | `#LightGreen` |
| Databases | Light Yellow | `#LightYellow` |
| External Systems | Pink | `#Pink` |
| Security/Compliance | Light Coral | `#LightCoral` |
| Administrative | Light Gray | `#LightGray` |

### Naming Conventions

- **Files**: `{type}-{subject}.puml` (e.g., `sequence-award-submission.puml`)
- **Diagrams**: Title Case with System Name prefix
- **Components**: PascalCase for classes, snake_case for database entities
- **Stereotypes**: `<<service>>`, `<<entity>>`, `<<external>>`

### Documentation Requirements

Each diagram should include:
1. **Title**: Clear, descriptive title
2. **Legend**: Color/symbol explanation where needed
3. **Notes**: Contextual information for complex elements
4. **Version**: Embedded in file or accompanying documentation

---

## Usage Instructions

### Rendering Diagrams

**VS Code Extension**: Install "PlantUML" extension
```bash
# Command Palette: PlantUML: Preview Current Diagram
```

**Command Line (requires Java + PlantUML jar)**:
```bash
java -jar plantuml.jar diagram.puml
```

**Online**: Use [PlantUML Web Server](http://www.plantuml.com/plantuml)

### Including C4 Model Library

```plantuml
@startuml
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Context.puml
' Your diagram here
@enduml
```

---

## Quality Checklist

- [ ] Diagram renders without errors
- [ ] Title is descriptive and accurate
- [ ] Color coding follows standards
- [ ] Relationships are clearly labeled
- [ ] Notes explain complex logic
- [ ] File naming follows conventions




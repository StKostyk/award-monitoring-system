# ADR-012: Documentation Framework Selection

**Status**: Accepted  
**Date**: 2025-08-21  
**Author**: Stefan Kostyk  
**Stakeholders**: Project Architect, Development Team, Technical Writers

---

## Context

The Award Monitoring & Tracking System requires comprehensive documentation including API documentation, technical documentation, and architectural documentation. Documentation must be maintainable, version-controlled, and support multiple output formats.

### Background
- API-first development approach requiring comprehensive API documentation
- Technical documentation for system architecture and development
- Portfolio presentation requiring professional documentation formats
- Support for both English and Ukrainian content

---

## Decision

**OpenAPI 3.1 + Markdown** has been selected as the documentation framework for the Award Monitoring & Tracking System.

### Chosen Approach
- **API Documentation**: OpenAPI 3.1 specifications with Swagger UI
- **Technical Documentation**: Markdown for structured technical writing
- **Generation**: Automated documentation generation from code
- **Versioning**: Documentation as code with Git version control

### Rationale
- **API-First Development**: OpenAPI enables design-first API development
- **Professional Output**: Markdown supports PDF, HTML, and other formats
- **Automation**: Documentation generated from code annotations
- **Version Control**: Text-based formats work well with Git
- **Industry Standard**: OpenAPI is the standard for REST API documentation

---

## Consequences

### Positive Consequences
- **API Documentation**: Comprehensive, interactive API documentation
- **Automation**: Reduced manual effort with code-generated documentation
- **Multiple Formats**: Professional PDF and HTML output for portfolio
- **Version Control**: Documentation changes tracked alongside code

### Negative Consequences
- **Learning Curve**: Markdown syntax and documentation toolchain
- **Tooling Setup**: Additional build steps for documentation generation
- **Maintenance**: Need to keep documentation synchronized with code

---

## Alternatives Considered

### Alternative 1: Swagger 2.0
- **Pros**: Widely supported, simpler specification
- **Cons**: Older standard, less features than OpenAPI 3.1
- **Reason for Rejection**: OpenAPI 3.1 provides modern features

### Alternative 2: AsciiDoc
- **Pros**: Richer syntax, widespread adoption
- **Cons**: Harder formatting options, poor PDF output
- **Reason for Rejection**: Markdown easier for professional documentation

---

## Implementation Notes

### Technical Requirements
- **OpenAPI Version**: 3.1 for latest features and JSON Schema support
- **Documentation Tools**: Springdoc OpenAPI, plain markdown
- **Output Formats**: HTML, PDF, and interactive Swagger UI
- **Build Integration**: Maven plugins for documentation generation

### Documentation Structure
```
docs/
├── api/
│   ├── openapi.yml              # OpenAPI specification
│   └── api-guide.md             # API usage guide
├── technical/
│   ├── architecture.md          # System architecture
│   ├── deployment.md            # Deployment guide
│   └── development.md           # Development setup
└── user/
    ├── user-guide.md            # End-user documentation
    └── admin-guide.md           # Administrative guide
```

### API Documentation
```java
@RestController
@RequestMapping("/api/v1/awards")
@Tag(name = "Awards", description = "Award management operations")
public class AwardController {
    
    @Operation(
        summary = "Create new award",
        description = "Creates a new award entry in the system"
    )
    @ApiResponse(responseCode = "201", description = "Award created successfully")
    @PostMapping
    public ResponseEntity<Award> createAward(@RequestBody Award award) {
        // Implementation
    }
}
```

### Build Configuration
```xml
<plugin>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

## Success Metrics

- **API Coverage**: 100% of endpoints documented in OpenAPI
- **Documentation Freshness**: Documentation updated with every API change
- **User Satisfaction**: Positive feedback on documentation clarity

---

## Related Documents

- **Tech Stack**: [Technology Stack Selection](../TECH_STACK.md)
- **Other ADRs**: [ADR-001 Backend Framework](./ADR-001-Backend-Framework.md)
- **External Resources**: [OpenAPI Specification](https://spec.openapis.org/oas/v3.1.0)

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2025-08-21 | Stefan Kostyk | Initial version | Document creation |

---

**Document Status**: Approved  
**Next Review Date**: 2025-07-16  
**ADR Category**: Technology 
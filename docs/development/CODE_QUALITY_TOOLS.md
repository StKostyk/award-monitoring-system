# Code Quality Tools Integration
## Award Monitoring & Tracking System

> **Phase 12 Deliverable**: Development Environment & Toolchain  
> **Document Version**: 1.0  
> **Last Updated**: January 2026  
> **Author**: Stefan Kostyk

---

## Executive Summary

This document describes the code quality toolchain integrated into the Award Monitoring & Tracking System. The tools enforce enterprise-grade quality standards, ensure consistency, and automate the detection of bugs, security vulnerabilities, and style violations.

### Quality Targets

| **Metric** | **Target** | **Tool** |
|------------|------------|----------|
| Code Coverage | ≥85% | JaCoCo + SonarQube |
| Duplication | <3% | SonarQube |
| Technical Debt Ratio | <5% | SonarQube |
| Critical Vulnerabilities | 0 | OWASP Dependency Check |
| Blocker/Critical Bugs | 0 | SpotBugs + SonarQube |

---

## 1. Tool Overview

| **Tool** | **Purpose** | **Configuration** | **Run Command** |
|----------|-------------|-------------------|-----------------|
| **Checkstyle** | Code style enforcement | `tools/quality/checkstyle.xml` | `mvn checkstyle:check` |
| **PMD** | Static code analysis | `tools/quality/pmd-ruleset.xml` | `mvn pmd:check` |
| **SpotBugs** | Bug pattern detection | `tools/quality/spotbugs-excludes.xml` | `mvn spotbugs:check` |
| **SonarQube** | Quality gate & dashboard | `sonar-project.properties` | `mvn sonar:sonar` |
| **OWASP DC** | Dependency vulnerabilities | Maven plugin | `mvn dependency-check:check` |
| **JaCoCo** | Code coverage | Maven plugin | `mvn verify` |

---

## 2. Checkstyle

### Purpose
Enforces Google Java Style Guide with enterprise customizations. Catches style violations before code review.

### Configuration Location
```
tools/quality/checkstyle.xml
tools/quality/checkstyle-suppressions.xml
```

### Key Rules

| **Category** | **Rule** | **Value** |
|--------------|----------|-----------|
| Line Length | Maximum characters | 120 |
| Method Length | Maximum lines | 50 |
| Cyclomatic Complexity | Maximum | 10 |
| Class Length | Maximum lines | 500 |
| Parameter Count | Maximum | 7 |
| Import Order | Groups | java → javax → jakarta → org → com → project |

### Maven Configuration

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.3.1</version>
    <configuration>
        <configLocation>tools/quality/checkstyle.xml</configLocation>
        <suppressionsLocation>tools/quality/checkstyle-suppressions.xml</suppressionsLocation>
        <consoleOutput>true</consoleOutput>
        <failsOnError>true</failsOnError>
        <violationSeverity>warning</violationSeverity>
    </configuration>
    <executions>
        <execution>
            <phase>validate</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Usage

```bash
# Check for violations
mvn checkstyle:check

# Generate HTML report
mvn checkstyle:checkstyle
# Report: target/site/checkstyle.html
```

---

## 3. PMD

### Purpose
Static analysis for common programming flaws: unused variables, empty catch blocks, unnecessary object creation, and more.

### Configuration Location
```
tools/quality/pmd-ruleset.xml
```

### Enabled Rule Categories

| **Category** | **Focus** |
|--------------|-----------|
| Best Practices | Coding standards, unused code |
| Code Style | Naming, formatting consistency |
| Design | Complexity, coupling, cohesion |
| Error Prone | Null checks, exception handling |
| Performance | Inefficient patterns |
| Security | Input validation, SQL injection |

### Maven Configuration

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-pmd-plugin</artifactId>
    <version>3.21.2</version>
    <configuration>
        <rulesets>
            <ruleset>tools/quality/pmd-ruleset.xml</ruleset>
        </rulesets>
        <failOnViolation>true</failOnViolation>
        <printFailingErrors>true</printFailingErrors>
        <targetJdk>21</targetJdk>
    </configuration>
    <executions>
        <execution>
            <phase>validate</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Usage

```bash
# Check for violations
mvn pmd:check

# Generate report with Copy-Paste Detector
mvn pmd:pmd pmd:cpd
# Reports: target/site/pmd.html, target/site/cpd.html
```

---

## 4. SpotBugs

### Purpose
Finds bug patterns in Java code using static analysis. Detects null pointer dereferences, infinite loops, resource leaks, and more.

### Configuration Location
```
tools/quality/spotbugs-excludes.xml
```

### Excluded Patterns

- Spring Framework injection patterns
- JPA/Hibernate entity patterns
- Lombok-generated code
- DTO/Record immutability patterns
- Test classes (relaxed rules)

### Maven Configuration

```xml
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.8.3.1</version>
    <configuration>
        <effort>Max</effort>
        <threshold>Medium</threshold>
        <failOnError>true</failOnError>
        <excludeFilterFile>tools/quality/spotbugs-excludes.xml</excludeFilterFile>
        <plugins>
            <plugin>
                <groupId>com.h3xstream.findsecbugs</groupId>
                <artifactId>findsecbugs-plugin</artifactId>
                <version>1.13.0</version>
            </plugin>
        </plugins>
    </configuration>
    <executions>
        <execution>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Usage

```bash
# Check for bugs
mvn spotbugs:check

# Generate HTML report
mvn spotbugs:spotbugs
# Report: target/site/spotbugs.html

# Launch SpotBugs GUI
mvn spotbugs:gui
```

---

## 5. SonarQube

### Purpose
Centralized code quality dashboard with quality gates. Tracks technical debt, code coverage, security vulnerabilities, and code smells over time.

### Configuration Location
```
sonar-project.properties
```

### Quality Gate Thresholds

| **Metric** | **Threshold** | **Condition** |
|------------|---------------|---------------|
| Coverage | ≥85% | On new code |
| Duplicated Lines | <3% | Overall |
| Maintainability Rating | A | Required |
| Reliability Rating | A | Required |
| Security Rating | A | Required |
| Security Hotspots | 100% reviewed | Required |

### Maven Configuration

```xml
<plugin>
    <groupId>org.sonarsource.scanner.maven</groupId>
    <artifactId>sonar-maven-plugin</artifactId>
    <version>3.10.0.2594</version>
</plugin>
```

### Usage

```bash
# Run SonarQube analysis (requires running SonarQube server)
mvn clean verify sonar:sonar \
    -Dsonar.host.url=http://localhost:9000 \
    -Dsonar.login=<your-token>

# With SonarCloud
mvn clean verify sonar:sonar \
    -Dsonar.host.url=https://sonarcloud.io \
    -Dsonar.organization=<your-org> \
    -Dsonar.login=<your-token>
```

### Local SonarQube Setup (Docker)

```bash
# Start SonarQube
docker run -d --name sonarqube \
    -p 9000:9000 \
    -v sonarqube_data:/opt/sonarqube/data \
    sonarqube:community

# Access: http://localhost:9000
# Default credentials: admin/admin
```

---

## 6. OWASP Dependency Check

### Purpose
Identifies known vulnerabilities in project dependencies using NVD (National Vulnerability Database).

### Maven Configuration

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>9.0.8</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
        <formats>
            <format>HTML</format>
            <format>JSON</format>
        </formats>
        <suppressionFile>tools/quality/dependency-check-suppressions.xml</suppressionFile>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Usage

```bash
# Check dependencies for vulnerabilities
mvn dependency-check:check

# Report: target/dependency-check-report.html
```

---

## 7. JaCoCo (Code Coverage)

### Purpose
Measures code coverage during test execution. Generates reports for SonarQube integration.

### Maven Configuration

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.85</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Coverage Exclusions

```xml
<configuration>
    <excludes>
        <exclude>**/config/**</exclude>
        <exclude>**/dto/**</exclude>
        <exclude>**/entity/**</exclude>
        <exclude>**/*Application.class</exclude>
    </excludes>
</configuration>
```

### Usage

```bash
# Run tests with coverage
mvn clean verify

# Report: target/site/jacoco/index.html
```

---

## 8. CI/CD Integration

### GitHub Actions Workflow

```yaml
name: Code Quality

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  quality:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Cache Maven packages
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
    
    - name: Run Checkstyle
      run: mvn checkstyle:check
    
    - name: Run PMD
      run: mvn pmd:check
    
    - name: Run SpotBugs
      run: mvn spotbugs:check
    
    - name: Run Tests with Coverage
      run: mvn verify
    
    - name: OWASP Dependency Check
      run: mvn dependency-check:check
    
    - name: SonarQube Analysis
      env:
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      run: |
        mvn sonar:sonar \
          -Dsonar.host.url=https://sonarcloud.io \
          -Dsonar.organization=${{ secrets.SONAR_ORG }}
```

### Pre-Commit Hook

Create `.git/hooks/pre-commit`:

```bash
#!/bin/bash
echo "Running code quality checks..."

# Quick checks only (fast feedback)
mvn checkstyle:check pmd:check -q

if [ $? -ne 0 ]; then
    echo "Code quality checks failed. Please fix the issues before committing."
    exit 1
fi

echo "Code quality checks passed!"
```

---

## 9. IDE Integration

### IntelliJ IDEA

1. **Checkstyle Plugin**
   - Install: `Settings > Plugins > Checkstyle-IDEA`
   - Configure: `Settings > Tools > Checkstyle`
   - Add configuration file: `tools/quality/checkstyle.xml`

2. **SonarLint Plugin**
   - Install: `Settings > Plugins > SonarLint`
   - Bind to SonarQube/SonarCloud for consistent rules

3. **Code Style**
   - Import: `Settings > Editor > Code Style > Import Scheme`
   - Select: `.idea/codeStyles/Project.xml`

### VS Code

1. **Checkstyle for Java**
   - Extension ID: `shengchen.vscode-checkstyle`
   - Configure path to `checkstyle.xml`

2. **SonarLint**
   - Extension ID: `SonarSource.sonarlint-vscode`

---

## 10. Quick Reference

### Run All Quality Checks

```bash
# Full quality check (all tools)
mvn clean verify checkstyle:check pmd:check spotbugs:check dependency-check:check

# Quick check (fast)
mvn checkstyle:check pmd:check
```

### Common Issues and Fixes

| **Issue** | **Tool** | **Fix** |
|-----------|----------|---------|
| Line too long | Checkstyle | Break line at 120 characters |
| Unused import | PMD/Checkstyle | Remove or use IDE organize imports |
| Null pointer risk | SpotBugs | Add null check or use Optional |
| Missing Javadoc | Checkstyle | Add Javadoc to public methods |
| High complexity | PMD | Extract methods, simplify logic |

---

## Summary

| **Tool** | **When** | **What** |
|----------|----------|----------|
| Checkstyle | Every commit | Style violations |
| PMD | Every commit | Code quality issues |
| SpotBugs | Every build | Bug patterns |
| JaCoCo | Every test run | Coverage metrics |
| OWASP DC | Weekly/Release | Security vulnerabilities |
| SonarQube | CI/CD pipeline | Overall quality dashboard |

**Philosophy**: Shift left - catch issues early in development, not in production.

---

**Document Version**: 1.0  
**Created**: January 2026  
**Next Review**: After first development sprint


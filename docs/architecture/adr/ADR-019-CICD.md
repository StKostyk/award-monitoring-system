# ADR-019: CI/CD Platform Selection

**Status**: Accepted  
**Date**: 2025-01-16  
**Author**: Stefan Kostyk  
**Stakeholders**: Project Architect, DevOps Team, Development Team

---

## Context

The Award Monitoring & Tracking System requires a robust CI/CD platform to automate the build, test, and deployment pipeline. The solution must integrate well with the chosen technology stack and provide enterprise-grade features while being cost-effective for a solo developer project.

### Background
- Need for automated build, test, and deployment pipeline
- Integration with Git repository hosting and version control
- Support for Docker container builds and Kubernetes deployments
- Code quality gates and security scanning requirements
- Cost-effective solution suitable for open-source portfolio project

### Assumptions
- CI/CD automation is essential for modern software development
- Integration with GitHub repository provides the best developer experience
- Cloud-hosted CI/CD reduces operational overhead
- Pipeline-as-code approach enables version control of deployment processes

---

## Decision

**GitHub Actions** has been selected as the CI/CD platform for the Award Monitoring & Tracking System.

### Chosen Approach
- **CI/CD Platform**: GitHub Actions with workflow automation
- **Pipeline Definition**: YAML-based workflows stored in repository
- **Build Environment**: GitHub-hosted runners for build and test
- **Deployment**: Automated deployment to Kubernetes clusters

### Rationale
- **Integrated with Repository**: Seamless integration with GitHub repository hosting
- **Cost-Effective**: Free for public repositories, generous free tier for private
- **Easy Setup**: No additional infrastructure or configuration required
- **Ecosystem**: Rich marketplace of pre-built actions for common tasks
- **Native GitHub Features**: Integration with issues, pull requests, and releases

---

## Consequences

### Positive Consequences
- **Zero Setup Overhead**: No additional infrastructure to manage
- **Integrated Developer Experience**: Single platform for code and CI/CD
- **Cost Effective**: Free for open-source projects
- **Rich Ecosystem**: Large marketplace of community actions
- **Version Controlled**: Pipeline definitions stored with code

### Negative Consequences
- **Vendor Lock-in**: Tight coupling with GitHub platform
- **Limited Customization**: Less flexibility compared to self-hosted solutions
- **Resource Limits**: Build time and runner resource limitations

### Neutral Consequences
- **Cloud Dependency**: Requires internet connectivity for builds
- **GitHub Ecosystem**: Best suited for projects already using GitHub

---

## Alternatives Considered

### Alternative 1: GitLab CI
- **Pros**: Integrated platform, powerful features, self-hosted option
- **Cons**: Additional cost, requires GitLab migration, learning curve
- **Reason for Rejection**: Already committed to GitHub ecosystem

### Alternative 2: Jenkins
- **Pros**: Highly customizable, large plugin ecosystem, self-hosted control
- **Cons**: Infrastructure overhead, complex setup, maintenance burden
- **Reason for Rejection**: Too much operational overhead for solo developer

### Alternative 3: Azure DevOps
- **Pros**: Enterprise features, Microsoft ecosystem integration
- **Cons**: Additional cost, separate platform from GitHub, complexity
- **Reason for Rejection**: Prefer integrated GitHub solution

---

## Implementation Notes

### Technical Requirements
- **GitHub Repository**: Public or private repository with Actions enabled
- **Workflow Files**: YAML workflows in `.github/workflows/` directory
- **Secrets Management**: GitHub repository secrets for sensitive data
- **Container Registry**: GitHub Container Registry or Docker Hub integration

### Main Build Workflow
```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_PASSWORD: test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
        cache: maven
    
    - name: Run tests
      run: ./mvnw clean test
    
    - name: Generate test report
      uses: dorny/test-reporter@v1
      if: success() || failure()
      with:
        name: Maven Tests
        path: target/surefire-reports/*.xml
        reporter: java-junit

  security-scan:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    
    - name: Run Trivy vulnerability scanner
      uses: aquasecurity/trivy-action@master
      with:
        scan-type: 'fs'
        format: 'sarif'
        output: 'trivy-results.sarif'
    
    - name: Upload Trivy scan results
      uses: github/codeql-action/upload-sarif@v2
      with:
        sarif_file: 'trivy-results.sarif'

  build-and-deploy:
    needs: [test, security-scan]
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
        cache: maven
    
    - name: Build application
      run: ./mvnw clean package -DskipTests
    
    - name: Build Docker image
      run: |
        docker build -t ${{ github.repository }}:${{ github.sha }} .
        docker tag ${{ github.repository }}:${{ github.sha }} ${{ github.repository }}:latest
    
    - name: Login to Container Registry
      uses: docker/login-action@v3
      with:
        registry: ghcr.io
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}
    
    - name: Push Docker image
      run: |
        docker push ghcr.io/${{ github.repository }}:${{ github.sha }}
        docker push ghcr.io/${{ github.repository }}:latest
    
    - name: Deploy to Kubernetes
      uses: azure/k8s-deploy@v1
      with:
        manifests: |
          k8s/deployment.yaml
          k8s/service.yaml
        images: |
          ghcr.io/${{ github.repository }}:${{ github.sha }}
        kubeconfig: ${{ secrets.KUBE_CONFIG }}
```

### Quality Gates Configuration
```yaml
  quality-gate:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
      with:
        fetch-depth: 0
    
    - name: SonarQube Scan
      uses: sonarqube-quality-gate-action@master
      env:
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      with:
        scanMetadataReportFile: target/sonar/report-task.txt
    
    - name: Check test coverage
      run: |
        COVERAGE=$(./mvnw jacoco:report | grep -o "Total.*[0-9]*%" | grep -o "[0-9]*")
        if [ "$COVERAGE" -lt 85 ]; then
          echo "Test coverage $COVERAGE% is below threshold 85%"
          exit 1
        fi
```

---

## Compliance & Quality

### Security Implications
- **Secret Management**: GitHub secrets for sensitive configuration
- **Container Scanning**: Automated vulnerability scanning of Docker images
- **SAST**: Static analysis security testing integration
- **Dependency Scanning**: Automated dependency vulnerability checks

### Performance Impact
- **Build Time**: Parallel job execution for faster builds
- **Caching**: Maven and Docker layer caching for efficiency
- **Resource Optimization**: Efficient use of GitHub runner resources

### Maintainability
- **Pipeline as Code**: All workflows version controlled with application code
- **Reusable Workflows**: Shared workflows for common patterns
- **Documentation**: Workflow documentation and README integration
- **Monitoring**: Build status badges and notifications

---

## Success Metrics

### Key Performance Indicators
- **Build Success Rate**: > 95% successful builds
- **Average Build Time**: < 10 minutes for full pipeline
- **Deployment Frequency**: Daily deployments to staging

### Monitoring & Alerting
- **Build Notifications**: Slack/email notifications for build failures
- **Quality Gates**: Automated quality checks with failure notifications
- **Deployment Status**: Real-time deployment status tracking

---

## Related Documents

- **Technology Stack**: [Technology Stack Selection](../TECH_STACK.md)
- **Other ADRs**: [ADR-017 Containerization](./ADR-017-Containerization.md), [ADR-018 Orchestration](./ADR-018-Orchestration.md)
- **External Resources**: [GitHub Actions Documentation](https://docs.github.com/en/actions)

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2025-08-21 | Stefan Kostyk | Initial version | Document creation |

---

**Document Status**: Approved  
**Next Review Date**: 2026-02-21  
**ADR Category**: Technology 
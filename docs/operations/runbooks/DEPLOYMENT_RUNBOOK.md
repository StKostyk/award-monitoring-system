# Deployment Runbook
## Award Monitoring & Tracking System

> **Phase 17 Deliverable**: Documentation & Knowledge Management  
> **Document Version**: 1.0  
> **Last Updated**: January 2026  
> **Author**: Stefan Kostyk  

---

## Overview

This runbook provides step-by-step procedures for deploying the Award Monitoring System to production environments.

---

## Pre-Deployment Checklist

| Step | Task | Verified |
|------|------|----------|
| 1 | All CI/CD pipeline checks passed | ☐ |
| 2 | Code review approved | ☐ |
| 3 | Test coverage ≥85% | ☐ |
| 4 | Security scan passed (no critical vulnerabilities) | ☐ |
| 5 | Database migrations tested in staging | ☐ |
| 6 | Rollback plan documented | ☐ |
| 7 | Monitoring alerts configured | ☐ |
| 8 | Stakeholders notified | ☐ |

---

## Deployment Procedures

### 1. Standard Deployment (Blue-Green)

**Duration**: ~15 minutes  
**Risk Level**: Low  
**Rollback Time**: <1 minute

#### Steps

```bash
# 1. Verify current deployment status
kubectl get deployments -n award-system

# 2. Deploy to inactive (green) environment
kubectl apply -f infra/k8s/deployment.yml

# 3. Wait for pods to be ready
kubectl rollout status deployment/award-backend-green -n award-system

# 4. Run smoke tests against green
curl -f https://green.award-system.edu.ua/actuator/health

# 5. Switch traffic to green
kubectl patch service award-backend \
  -p '{"spec":{"selector":{"deployment":"green"}}}'

# 6. Verify traffic switch
curl -f https://api.award-system.edu.ua/actuator/health

# 7. Monitor for 10 minutes
# Check Grafana dashboard for anomalies

# 8. Scale down blue (after verification)
kubectl scale deployment award-backend-blue --replicas=0
```

#### Rollback (If Needed)

```bash
# Immediate rollback - switch back to blue
kubectl patch service award-backend \
  -p '{"spec":{"selector":{"deployment":"blue"}}}'

# Verify rollback
curl -f https://api.award-system.edu.ua/actuator/health
```

---

### 2. Database Migration Deployment

**Duration**: ~30 minutes  
**Risk Level**: Medium  
**Requires**: Database backup

#### Pre-Migration

```bash
# 1. Create database backup
pg_dump -h $DB_HOST -U $DB_USER award_system > backup_$(date +%Y%m%d_%H%M%S).sql

# 2. Verify backup
pg_restore --list backup_*.sql | head -20

# 3. Test migration in staging
flyway -url=$STAGING_DB_URL migrate

# 4. Verify staging data integrity
psql -h $STAGING_DB -c "SELECT COUNT(*) FROM awards;"
```

#### Migration Steps

```bash
# 1. Enable maintenance mode
kubectl set env deployment/award-backend MAINTENANCE_MODE=true

# 2. Scale to single instance
kubectl scale deployment/award-backend --replicas=1

# 3. Run Flyway migration
flyway -url=$PROD_DB_URL migrate

# 4. Verify migration
flyway -url=$PROD_DB_URL info

# 5. Deploy new application version
kubectl apply -f infra/k8s/deployment.yml

# 6. Disable maintenance mode
kubectl set env deployment/award-backend MAINTENANCE_MODE=false

# 7. Scale back up
kubectl scale deployment/award-backend --replicas=3
```

---

### 3. Emergency Hotfix Deployment

**Duration**: ~10 minutes  
**Risk Level**: Medium-High  
**Approval**: On-call manager

#### Steps

```bash
# 1. Create hotfix branch
git checkout -b hotfix/ISSUE-XXX main

# 2. Apply fix and run minimal tests
mvn clean test -Dtest=*CriticalTest

# 3. Build emergency image
docker build -t award-system:hotfix-XXX .

# 4. Deploy with --force
kubectl set image deployment/award-backend \
  app=award-system:hotfix-XXX --record

# 5. Monitor closely for 30 minutes
watch kubectl get pods -n award-system
```

---

## Post-Deployment Verification

### Health Checks

```bash
# API Health
curl -s https://api.award-system.edu.ua/actuator/health | jq .

# Database connectivity
curl -s https://api.award-system.edu.ua/actuator/health/db | jq .

# Redis connectivity  
curl -s https://api.award-system.edu.ua/actuator/health/redis | jq .
```

### Smoke Tests

| Test | Endpoint | Expected |
|------|----------|----------|
| Health | GET /actuator/health | `{"status":"UP"}` |
| Auth | POST /auth/login | 200 with valid creds |
| Awards | GET /awards | 200 with pagination |
| Metrics | GET /actuator/prometheus | Metrics data |

### Monitoring Checklist

| Metric | Threshold | Dashboard |
|--------|-----------|-----------|
| Error rate | <0.1% | Grafana: Application Overview |
| P99 latency | <200ms | Grafana: Application Overview |
| Memory usage | <80% | Grafana: JVM Metrics |
| Active connections | <80% pool | Grafana: Database |

---

## Troubleshooting During Deployment

### Pod Not Starting

```bash
# Check pod status
kubectl describe pod <pod-name> -n award-system

# Check logs
kubectl logs <pod-name> -n award-system --previous

# Common issues:
# - ImagePullBackOff: Check registry credentials
# - CrashLoopBackOff: Check application logs
# - Pending: Check resource quotas
```

### Database Connection Failure

```bash
# Test connectivity
kubectl run -it --rm debug --image=postgres:16 -- \
  psql -h $DB_HOST -U $DB_USER -d award_system -c "SELECT 1"

# Check secrets
kubectl get secret db-credentials -o yaml
```

### Memory Issues

```bash
# Check memory usage
kubectl top pods -n award-system

# Adjust limits if needed
kubectl patch deployment award-backend -p \
  '{"spec":{"template":{"spec":{"containers":[{"name":"app","resources":{"limits":{"memory":"2Gi"}}}]}}}}'
```

---

## Contacts

| Role | Contact | Escalation |
|------|---------|------------|
| On-call Engineer | Slack: #award-system-ops | PagerDuty |
| Database Admin | dba@university.edu.ua | Phone |
| Security Team | security@university.edu.ua | Urgent Slack |

---

## Related Documents

- [Deployment Strategies](../deployment/DEPLOYMENT_STRATEGIES.md)
- [Environment Promotion](../deployment/ENVIRONMENT_PROMOTION.md)
- [Troubleshooting Guide](./TROUBLESHOOTING.md)
- [Incident Response](./INCIDENT_RESPONSE_RUNBOOK.md)


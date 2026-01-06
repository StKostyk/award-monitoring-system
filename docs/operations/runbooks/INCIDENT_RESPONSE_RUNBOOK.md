# Incident Response Runbook
## Award Monitoring & Tracking System

> **Phase 17 Deliverable**: Documentation & Knowledge Management  
> **Document Version**: 1.0  
> **Last Updated**: January 2026  
> **Author**: Stefan Kostyk  

---

## Incident Severity Levels

| Severity | Impact | Response Time | Examples |
|----------|--------|---------------|----------|
| **P1 - Critical** | System down, data loss | 15 min | Complete outage, security breach |
| **P2 - High** | Major feature unavailable | 30 min | Auth failure, workflow blocked |
| **P3 - Medium** | Degraded performance | 2 hours | Slow responses, partial feature loss |
| **P4 - Low** | Minor issue | 8 hours | UI bug, non-critical error |

---

## Incident Response Workflow

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Detect    │───▶│   Triage    │───▶│   Resolve   │───▶│  Post-Mort  │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
     │                   │                   │                   │
  Alerts/           Assess             Fix &              Document
  Reports          Severity           Verify             Learnings
```

---

## P1: System Outage

### Symptoms
- Health endpoint returns 5xx
- No pods running in namespace
- Users cannot access application

### Immediate Actions

```bash
# 1. Verify outage
curl -I https://api.award-system.edu.ua/actuator/health

# 2. Check pod status
kubectl get pods -n award-system

# 3. Check recent events
kubectl get events -n award-system --sort-by='.lastTimestamp' | tail -20

# 4. Check node status
kubectl get nodes
kubectl describe node <problematic-node>
```

### Recovery Steps

```bash
# If pods are crashing
kubectl rollout undo deployment/award-backend -n award-system

# If no pods are running
kubectl scale deployment/award-backend --replicas=3 -n award-system

# If database is unreachable
kubectl delete pod -l app=award-backend -n award-system  # Force restart

# If cluster issue
# Contact infrastructure team immediately
```

### Communication Template

```
Subject: [P1] Award System Outage - Investigation in Progress

Status: INVESTIGATING
Start Time: [HH:MM UTC]
Impact: All users unable to access the system

Current Actions:
- Engineering team engaged
- Root cause investigation in progress
- ETA for next update: 15 minutes

Updates: [Status page URL]
```

---

## P2: Authentication Failure

### Symptoms
- Users cannot log in
- 401 errors on all authenticated endpoints
- JWT validation failures in logs

### Diagnosis

```bash
# Check auth service logs
kubectl logs -l app=award-backend -n award-system | grep -i "auth\|jwt\|token"

# Verify OAuth2 configuration
kubectl get secret jwt-signing-key -o yaml

# Check Redis (session store)
kubectl exec -it redis-0 -- redis-cli PING
```

### Resolution

```bash
# If JWT signing key corrupted
kubectl rollout restart deployment/award-backend

# If Redis connection issue
kubectl delete pod redis-0  # StatefulSet will recreate

# If OAuth2 provider unreachable
# Check external OAuth provider status
# Switch to backup authentication if available
```

---

## P2: Database Connection Pool Exhaustion

### Symptoms
- Timeout errors in API responses
- "Connection pool exhausted" in logs
- Queries timing out

### Diagnosis

```bash
# Check active connections
kubectl exec -it postgres-0 -- psql -U award_user -c \
  "SELECT count(*) FROM pg_stat_activity WHERE state = 'active';"

# Check waiting queries
kubectl exec -it postgres-0 -- psql -U award_user -c \
  "SELECT * FROM pg_stat_activity WHERE wait_event IS NOT NULL;"

# Check application pool metrics
curl -s https://api.award-system.edu.ua/actuator/metrics/hikaricp.connections.active
```

### Resolution

```bash
# Terminate long-running queries
kubectl exec -it postgres-0 -- psql -U award_user -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity 
   WHERE state = 'active' AND query_start < NOW() - INTERVAL '5 minutes';"

# Increase pool size (temporary)
kubectl set env deployment/award-backend \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=20

# Restart application to reset connections
kubectl rollout restart deployment/award-backend
```

---

## P3: High Response Latency

### Symptoms
- P99 latency > 500ms
- Users report slow page loads
- Prometheus alerts firing

### Diagnosis

```bash
# Check response times
curl -w "@curl-format.txt" -s -o /dev/null \
  https://api.award-system.edu.ua/awards

# Check pod resource usage
kubectl top pods -n award-system

# Check database slow queries
kubectl exec -it postgres-0 -- psql -U award_user -c \
  "SELECT query, mean_time, calls FROM pg_stat_statements 
   ORDER BY mean_time DESC LIMIT 10;"
```

### Resolution

```bash
# If CPU-bound
kubectl scale deployment/award-backend --replicas=5

# If memory-bound
kubectl set env deployment/award-backend JAVA_OPTS="-Xmx1g -Xms1g"

# If database-bound
# Add missing indexes (see slow query analysis)
# Consider read replicas for reporting queries

# If external service slow
# Enable circuit breaker / increase timeout
```

---

## P3: Disk Space Alert

### Symptoms
- Disk usage > 80% alert
- "No space left on device" errors
- Log files filling up

### Resolution

```bash
# Check disk usage
kubectl exec -it <pod> -- df -h

# Clean old logs
kubectl exec -it <pod> -- find /var/log -name "*.log" -mtime +7 -delete

# Clean Docker images (on nodes)
docker system prune -af

# For persistent volumes - increase PVC size
kubectl patch pvc postgres-data -p '{"spec":{"resources":{"requests":{"storage":"100Gi"}}}}'
```

---

## Post-Incident Checklist

| Step | Task | Owner | Completed |
|------|------|-------|-----------|
| 1 | Incident timeline documented | On-call | ☐ |
| 2 | Root cause identified | Engineering | ☐ |
| 3 | Customer communication sent | Support | ☐ |
| 4 | Monitoring gaps identified | SRE | ☐ |
| 5 | Action items created | Team lead | ☐ |
| 6 | Post-mortem meeting scheduled | Manager | ☐ |

---

## Post-Mortem Template

```markdown
# Incident Post-Mortem: [Title]

## Summary
- **Date**: YYYY-MM-DD
- **Duration**: X hours Y minutes
- **Severity**: P1/P2/P3
- **Impact**: X users affected, Y% error rate

## Timeline
- HH:MM - Incident detected
- HH:MM - Team engaged
- HH:MM - Root cause identified
- HH:MM - Fix deployed
- HH:MM - Incident resolved

## Root Cause
[Technical description of what went wrong]

## Resolution
[What was done to fix it]

## Action Items
| Action | Owner | Due Date | Status |
|--------|-------|----------|--------|
| [Action 1] | [Name] | [Date] | Open |

## Lessons Learned
- What went well
- What could be improved
- What will we do differently
```

---

## Emergency Contacts

| Role | Name | Contact | Availability |
|------|------|---------|--------------|
| Primary On-call | [Name] | [Phone] | 24/7 |
| Secondary On-call | [Name] | [Phone] | 24/7 |
| Database Admin | [Name] | [Phone] | Business hours |
| Security Lead | [Name] | [Phone] | 24/7 for P1 |
| Management | [Name] | [Phone] | P1 only |

---

## Related Documents

- [Monitoring & Observability](../monitoring/MONITORING_OBSERVABILITY.md)
- [Deployment Runbook](./DEPLOYMENT_RUNBOOK.md)
- [Troubleshooting Guide](./TROUBLESHOOTING.md)


# Troubleshooting Guide
## Award Monitoring & Tracking System

> **Phase 17 Deliverable**: Documentation & Knowledge Management  
> **Document Version**: 1.0  
> **Last Updated**: January 2026  
> **Author**: Stefan Kostyk  

---

## Quick Reference

| Symptom | Likely Cause | Quick Fix | Section |
|---------|--------------|-----------|---------|
| 401 Unauthorized | Token expired | Re-login or refresh token | [Auth Issues](#authentication-issues) |
| 500 Internal Error | Application error | Check logs, restart pod | [Server Errors](#server-errors) |
| Slow responses | DB/resource issue | Check metrics, scale up | [Performance](#performance-issues) |
| Connection refused | Service down | Check pod status | [Connectivity](#connectivity-issues) |
| Upload fails | File too large | Check file size limits | [Upload Issues](#file-upload-issues) |

---

## Authentication Issues

### Problem: "401 Unauthorized" on API Requests

**Causes:**
- Access token expired
- Invalid token format
- Token not included in request

**Solutions:**

```bash
# 1. Check token expiration
# Decode JWT at jwt.io or:
echo $TOKEN | cut -d. -f2 | base64 -d | jq .exp

# 2. Refresh token
curl -X POST https://api.award-system.edu.ua/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "your-refresh-token"}'

# 3. Re-login if refresh token also expired
curl -X POST https://api.award-system.edu.ua/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "password"}'
```

### Problem: "403 Forbidden" - Insufficient Permissions

**Causes:**
- User role doesn't have required permission
- Trying to access another user's resource
- Organization scope mismatch

**Solutions:**
1. Verify user role in profile (`GET /users/me`)
2. Check if resource belongs to user's organization
3. Contact administrator for role upgrade if needed

### Problem: Cannot Login After Password Reset

**Causes:**
- Browser caching old credentials
- Password complexity not met

**Solutions:**
1. Clear browser cache and cookies
2. Ensure password meets requirements:
   - Minimum 8 characters
   - At least one uppercase letter
   - At least one number
   - At least one special character

---

## Server Errors

### Problem: "500 Internal Server Error"

**Diagnosis:**

```bash
# Check application logs
kubectl logs -l app=award-backend -n award-system --tail=100

# Look for exceptions
kubectl logs -l app=award-backend | grep -i "exception\|error" | tail -20

# Check recent events
kubectl get events -n award-system --sort-by='.lastTimestamp' | tail -10
```

**Common Causes & Solutions:**

| Log Pattern | Cause | Solution |
|-------------|-------|----------|
| `NullPointerException` | Missing data | Check request payload |
| `DataIntegrityViolation` | DB constraint | Fix data or constraint |
| `OutOfMemoryError` | Memory exhaustion | Increase memory limit |
| `ConnectionRefused` | Dependent service down | Check service status |

### Problem: Pod CrashLoopBackOff

```bash
# Check crash reason
kubectl describe pod <pod-name> -n award-system

# Check previous container logs
kubectl logs <pod-name> -n award-system --previous

# Common fixes:
# 1. Memory issue - increase limits
kubectl patch deployment award-backend -p \
  '{"spec":{"template":{"spec":{"containers":[{"name":"app","resources":{"limits":{"memory":"1.5Gi"}}}]}}}}'

# 2. Startup issue - check environment variables
kubectl get configmap award-config -o yaml
kubectl get secret db-credentials -o yaml
```

---

## Performance Issues

### Problem: Slow API Response Times

**Diagnosis:**

```bash
# Check response times
curl -w "Time: %{time_total}s\n" -o /dev/null -s \
  https://api.award-system.edu.ua/awards

# Check application metrics
curl -s https://api.award-system.edu.ua/actuator/metrics/http.server.requests | jq

# Check database metrics
curl -s https://api.award-system.edu.ua/actuator/metrics/hikaricp.connections.active | jq
```

**Solutions by Cause:**

| Cause | Metric Indicator | Solution |
|-------|------------------|----------|
| High CPU | >80% CPU usage | Scale horizontally |
| Memory pressure | Frequent GC | Increase heap size |
| DB bottleneck | High connection wait | Optimize queries, add indexes |
| External service | Slow third-party calls | Add circuit breaker, timeout |

### Problem: Database Query Timeouts

```bash
# Find slow queries
kubectl exec -it postgres-0 -- psql -U award_user -c \
  "SELECT query, mean_time, calls 
   FROM pg_stat_statements 
   ORDER BY mean_time DESC LIMIT 5;"

# Check missing indexes
kubectl exec -it postgres-0 -- psql -U award_user -c \
  "SELECT schemaname, tablename, indexrelname, idx_scan
   FROM pg_stat_user_indexes 
   WHERE idx_scan = 0;"
```

**Quick Fixes:**
1. Add appropriate indexes for frequent queries
2. Use pagination for large result sets
3. Enable query caching for repeated queries

---

## Connectivity Issues

### Problem: "Connection Refused" to API

**Diagnosis:**

```bash
# Check service status
kubectl get svc -n award-system
kubectl get endpoints award-backend -n award-system

# Check ingress
kubectl get ingress -n award-system
kubectl describe ingress award-ingress -n award-system

# Test from within cluster
kubectl run -it --rm debug --image=curlimages/curl -- \
  curl http://award-backend:8080/actuator/health
```

### Problem: Database Connection Failures

```bash
# Test database connectivity
kubectl exec -it <app-pod> -- nc -zv postgres-service 5432

# Check database pod
kubectl get pods -l app=postgres -n award-system

# Verify credentials
kubectl get secret db-credentials -o jsonpath='{.data.password}' | base64 -d
```

### Problem: Redis Connection Issues

```bash
# Test Redis connectivity
kubectl exec -it redis-0 -- redis-cli PING

# Check Redis memory
kubectl exec -it redis-0 -- redis-cli INFO memory | grep used_memory_human

# Clear Redis cache if corrupted
kubectl exec -it redis-0 -- redis-cli FLUSHDB
```

---

## File Upload Issues

### Problem: "413 Request Entity Too Large"

**Cause:** File exceeds maximum upload size (10MB default)

**Solutions:**
1. Compress the file before uploading
2. Split large documents into multiple files
3. Contact admin if larger files are required

### Problem: "415 Unsupported Media Type"

**Cause:** File format not allowed

**Allowed formats:**
- Documents: PDF
- Images: JPEG, PNG
- Maximum size: 10MB

### Problem: Upload Succeeds but File Corrupted

```bash
# Check file in storage
kubectl exec -it <app-pod> -- ls -la /data/documents/

# Verify file checksum
kubectl exec -it <app-pod> -- md5sum /data/documents/<filename>
```

---

## Data Issues

### Problem: Award Stuck in Workflow

**Diagnosis:**
```bash
# Check award status
curl -s https://api.award-system.edu.ua/awards/{id} | jq '.status, .currentLevel'

# Check workflow history
curl -s https://api.award-system.edu.ua/awards/{id}/history | jq
```

**Solutions:**
1. Verify all required documents are attached
2. Check if reviewer has appropriate role
3. Contact admin to manually advance workflow if stuck

### Problem: Data Not Syncing Between Services

```bash
# Check Kafka consumer lag
kubectl exec -it kafka-0 -- kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group award-service

# Check for failed messages in dead-letter queue
kubectl exec -it kafka-0 -- kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic award-events-dlq \
  --from-beginning --max-messages 10
```

---

## Monitoring & Logs

### Accessing Logs

```bash
# Application logs
kubectl logs -l app=award-backend -n award-system -f

# Specific pod logs
kubectl logs <pod-name> -n award-system

# Previous container logs (after crash)
kubectl logs <pod-name> -n award-system --previous

# All containers in pod
kubectl logs <pod-name> -n award-system --all-containers
```

### Kibana Log Queries

```
# Find errors
level:ERROR AND kubernetes.namespace:award-system

# Find specific user activity
user_id:"uuid-here"

# Find slow requests
response_time:>1000

# Find specific trace
trace_id:"trace-id-here"
```

### Grafana Dashboards

| Dashboard | URL | Purpose |
|-----------|-----|---------|
| Application Overview | /d/app-overview | Health, latency, errors |
| JVM Metrics | /d/jvm-metrics | Memory, GC, threads |
| Database | /d/database | Connections, queries |
| Infrastructure | /d/infra | Nodes, pods, network |

---

## Common Error Codes

| Code | Meaning | Action |
|------|---------|--------|
| 400 | Bad Request | Check request format/validation |
| 401 | Unauthorized | Re-authenticate |
| 403 | Forbidden | Check permissions |
| 404 | Not Found | Verify resource exists |
| 409 | Conflict | Concurrent modification - retry |
| 413 | Payload Too Large | Reduce file size |
| 429 | Too Many Requests | Wait and retry |
| 500 | Server Error | Check logs, report bug |
| 502 | Bad Gateway | Check upstream service |
| 503 | Service Unavailable | Service starting/overloaded |
| 504 | Gateway Timeout | Increase timeout or optimize |

---

## Getting Help

1. **Self-Service**: Check this guide and [FAQ](#common-error-codes)
2. **Documentation**: See [User Guide](../user/USER_GUIDE.md)
3. **Support**: Contact support@award-system.edu.ua
4. **Urgent Issues**: See [Incident Response Runbook](./runbooks/INCIDENT_RESPONSE_RUNBOOK.md)

---

## Related Documents

- [User Guide](../user/USER_GUIDE.md)
- [Admin Guide](../user/ADMIN_GUIDE.md)
- [Deployment Runbook](./runbooks/DEPLOYMENT_RUNBOOK.md)
- [Monitoring & Observability](../monitoring/MONITORING_OBSERVABILITY.md)


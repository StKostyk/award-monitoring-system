# Monitoring & Observability Strategy

> Award Monitoring & Tracking System - Phase 15 Documentation

## Overview

This document defines the monitoring and observability strategy for the Award Monitoring System, covering metrics collection, distributed tracing, log aggregation, and alerting.

### Observability Pillars

| Pillar | Technology | Purpose |
|--------|-----------|---------|
| **Metrics** | Prometheus + Micrometer | Performance monitoring, SLA tracking |
| **Tracing** | Jaeger + OpenTelemetry | Distributed request tracing |
| **Logging** | ELK Stack + Logstash | Centralized log aggregation |
| **Alerting** | Alertmanager | Incident notification |
| **Visualization** | Grafana | Dashboards and analysis |

---

## 1. Observability Stack Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Observability Stack                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌─────────────┐     ┌─────────────┐     ┌─────────────┐         │
│   │   Backend   │────▶│  Prometheus │────▶│   Grafana   │         │
│   │  (Metrics)  │     │   :9090     │     │   :3000     │         │
│   └─────────────┘     └─────────────┘     └─────────────┘         │
│          │                   │                                     │
│          │            ┌──────┴──────┐                              │
│          │            │ Alertmanager│                              │
│          │            │    :9093    │                              │
│          │            └─────────────┘                              │
│          │                                                         │
│          ▼                                                         │
│   ┌─────────────┐     ┌─────────────┐                              │
│   │   Backend   │────▶│   Jaeger    │                              │
│   │  (Traces)   │     │   :16686    │                              │
│   └─────────────┘     └─────────────┘                              │
│          │                                                         │
│          ▼                                                         │
│   ┌─────────────┐     ┌─────────────┐     ┌─────────────┐         │
│   │   Backend   │────▶│  Logstash   │────▶│Elasticsearch│         │
│   │   (Logs)    │     │   :5000     │     │   :9200     │         │
│   └─────────────┘     └─────────────┘     └─────────────┘         │
│                                                  │                 │
│                                           ┌──────┴──────┐          │
│                                           │   Kibana    │          │
│                                           │   :5601     │          │
│                                           └─────────────┘          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Quick Start

```bash
# Start observability stack
cd infra
docker-compose -f docker-compose.monitoring.yml up -d

# Access dashboards
# Grafana:      http://localhost:3000 (admin/admin)
# Prometheus:   http://localhost:9090
# Jaeger:       http://localhost:16686
# Kibana:       http://localhost:5601
# Alertmanager: http://localhost:9093
```

---

## 2. Metrics Configuration

### 2.1 Micrometer Integration

The application uses Micrometer with Prometheus registry for metrics collection.

**Configuration Class** (`MetricsConfiguration.java`):

```java
@Configuration
public class MetricsConfiguration {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags("application", "award-monitoring-system")
            .commonTags("environment", "${spring.profiles.active}");
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
```

### 2.2 Business Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `award.submissions.total` | Counter | Total award submissions by status |
| `award.approvals.total` | Counter | Approvals by level and decision |
| `award.requests.pending.total` | Gauge | Current pending requests |
| `document.processing.time` | Timer | Document processing duration |
| `document.processing.failures.total` | Counter | Processing failures |
| `user.registrations.total` | Counter | User registrations |
| `user.sessions.active` | Gauge | Active user sessions |

**Usage Example**:

```java
@Service
public class AwardService {
    private final BusinessMetricsService metrics;

    public void submitAward(AwardRequest request) {
        try {
            // Process submission
            metrics.recordAwardSubmissionSuccess();
            metrics.incrementPendingAwardRequests();
        } catch (Exception e) {
            metrics.recordAwardSubmissionFailure();
            throw e;
        }
    }
}
```

### 2.3 HTTP Metrics

Spring Boot Actuator automatically provides:

| Metric | Description |
|--------|-------------|
| `http.server.requests` | HTTP request latency histogram |
| `http.server.requests.count` | Request count by status/uri |
| `jvm.memory.used` | JVM memory usage |
| `jvm.gc.pause` | GC pause duration |
| `hikaricp.connections.*` | Database connection pool |

### 2.4 Prometheus Endpoint

```yaml
# application.yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  prometheus:
    metrics:
      export:
        enabled: true
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 50ms,100ms,200ms,500ms,1s
```

---

## 3. Distributed Tracing

### 3.1 OpenTelemetry Integration

Traces are exported to Jaeger via OTLP protocol.

**Configuration**:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% sampling for development
otel:
  exporter:
    otlp:
      endpoint: http://localhost:4318
```

### 3.2 Using @Observed Annotation

```java
@Service
public class AwardWorkflowService {

    @Observed(name = "award.workflow.process",
              contextualName = "process-award-workflow")
    public void processWorkflow(Long awardId) {
        // Method automatically traced
    }
}
```

### 3.3 Manual Span Creation

```java
@Service
public class DocumentService {
    private final Tracer tracer;

    public void processDocument(Document doc) {
        Span span = tracer.nextSpan().name("process-document").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("document.id", doc.getId().toString());
            // Processing logic
        } finally {
            span.end();
        }
    }
}
```

---

## 4. Structured Logging

### 4.1 Logback Configuration

**Development** (human-readable):
```
2024-01-15 10:30:45.123 INFO  [main] c.e.a.AwardService - Award submitted: ID=123
```

**Production** (JSON for ELK):
```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger_name": "c.e.a.AwardService",
  "message": "Award submitted: ID=123",
  "trace_id": "abc123",
  "span_id": "def456",
  "user_id": "user@example.com",
  "application": "award-monitoring-system",
  "environment": "production"
}
```

### 4.2 MDC Context

```java
@Component
public class LoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) {
        try {
            MDC.put("request_id", UUID.randomUUID().toString());
            MDC.put("user_id", getCurrentUserId());
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

### 4.3 Log Levels by Environment

| Environment | Root Level | Application Level |
|-------------|------------|-------------------|
| `local` | DEBUG | DEBUG |
| `dev` | INFO | DEBUG |
| `staging` | INFO | INFO |
| `production` | WARN | INFO |

---

## 5. Alerting & SLA Monitoring

### 5.1 SLA Targets

| Metric | Target | Alert Threshold |
|--------|--------|-----------------|
| **Uptime** | 99.9% | Service down > 1min |
| **Response Time P99** | < 200ms | > 200ms for 5min |
| **Error Rate** | < 0.1% | > 0.1% for 5min |
| **JVM Memory** | < 85% | > 85% for 5min |

### 5.2 Alert Categories

**Critical** (immediate action):
- Service down
- Error rate > 0.1%
- Response time > 1s
- JVM memory > 95%
- Database connection pool exhausted

**Warning** (investigation needed):
- Response time > 200ms
- JVM memory > 85%
- High GC pause time
- Low database connections

### 5.3 Alert Rules Example

```yaml
# prometheus/alert-rules.yml
groups:
  - name: application_health
    rules:
      - alert: HighErrorRate
        expr: |
          (sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
          / sum(rate(http_server_requests_seconds_count[5m]))) > 0.001
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High HTTP error rate detected"
          description: "Error rate is {{ $value | humanizePercentage }}"
```

### 5.4 Notification Channels

| Channel | Use Case | Configuration |
|---------|----------|---------------|
| Webhook | Development | Default receiver |
| Slack | Team alerts | `#alerts-critical` |
| Email | Business stakeholders | On-call rotation |
| PagerDuty | Production critical | Incident management |

---

## 6. Grafana Dashboards

### 6.1 Application Overview Dashboard

**Panels included**:
- Service status (up/down)
- Request rate (req/s)
- P99 latency (ms)
- Error rate (%)
- Request rate by endpoint
- Response time percentiles
- JVM heap memory
- Database connection pool

### 6.2 Creating Custom Dashboards

Access Grafana at `http://localhost:3000` and:

1. Click **+ Create** → **Dashboard**
2. Add panels with Prometheus queries
3. Save and organize in folders

**Useful PromQL Queries**:

```promql
# Request rate
sum(rate(http_server_requests_seconds_count[5m]))

# P99 latency
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))

# Error rate
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) 
/ sum(rate(http_server_requests_seconds_count[5m])) * 100

# JVM memory usage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100
```

---

## 7. Kibana Log Analysis

### 7.1 Index Pattern Setup

1. Access Kibana at `http://localhost:5601`
2. Go to **Stack Management** → **Index Patterns**
3. Create pattern: `award-logs-*`
4. Set time field: `@timestamp`

### 7.2 Useful KQL Queries

```
# Errors in last hour
log_level:ERROR AND @timestamp >= now-1h

# Specific user activity
user_id:"user@example.com"

# Trace investigation
trace_id:"abc123"

# Award submission logs
log_message:*award* AND log_message:*submit*
```

---

## 8. Health Checks

### 8.1 Kubernetes Probes

```yaml
# Configured in application.yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
```

**Endpoints**:
- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`

### 8.2 Custom Health Indicators

```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        if (isDatabaseAccessible()) {
            return Health.up().withDetail("database", "PostgreSQL").build();
        }
        return Health.down().withDetail("error", "Cannot connect").build();
    }
}
```

---

## 9. Production Recommendations

### 9.1 Sampling Strategy

| Environment | Trace Sampling | Log Level |
|-------------|----------------|-----------|
| Development | 100% | DEBUG |
| Staging | 50% | INFO |
| Production | 10% | WARN (INFO for app) |

### 9.2 Retention Policies

| Data Type | Retention | Storage |
|-----------|-----------|---------|
| Metrics (Prometheus) | 15 days | ~1GB |
| Traces (Jaeger) | 7 days | ~5GB |
| Logs (Elasticsearch) | 30 days | ~10GB |

### 9.3 Resource Requirements

| Service | Memory | CPU |
|---------|--------|-----|
| Prometheus | 512MB | 0.5 |
| Grafana | 256MB | 0.25 |
| Jaeger | 256MB | 0.25 |
| Elasticsearch | 1GB | 1.0 |
| Kibana | 512MB | 0.5 |
| Logstash | 512MB | 0.5 |

---

## 10. Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| Metrics not appearing | Check `/actuator/prometheus` endpoint |
| Traces missing | Verify OTLP endpoint connectivity |
| Logs not in Kibana | Check Logstash pipeline and ES connectivity |
| High memory usage | Reduce retention, enable sampling |

### Debug Commands

```bash
# Check Prometheus targets
curl http://localhost:9090/api/v1/targets

# Test Elasticsearch
curl http://localhost:9200/_cat/indices

# View Logstash logs
docker logs award-logstash

# Check application metrics
curl http://localhost:8080/actuator/prometheus | grep award
```

---

## References

- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Documentation](https://micrometer.io/docs)
- [Prometheus Alerting](https://prometheus.io/docs/alerting/latest/overview/)
- [OpenTelemetry Java](https://opentelemetry.io/docs/instrumentation/java/)
- [ELK Stack Guide](https://www.elastic.co/guide/index.html)


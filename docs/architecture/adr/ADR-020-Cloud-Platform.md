# ADR-020: Cloud Platform Selection

**Status**: Accepted  
**Date**: 2025-08-21  
**Author**: Stefan Kostyk  
**Stakeholders**: Project Architect, DevOps Team, Operations Team

---

## Context

The Award Monitoring & Tracking System requires a cloud platform for production deployment that provides enterprise-grade services, scalability, and reliability. The platform must support Kubernetes orchestration, managed databases, and comprehensive monitoring while being cost-effective for a portfolio project.

### Background
- Production deployment requirements for high availability and scalability
- Need for managed services to reduce operational overhead
- Enterprise-grade security and compliance features
- Global availability requirements for Ukrainian and international users
- Cost optimization for portfolio project economics

### Assumptions
- Cloud deployment provides better scalability than on-premises
- Managed services reduce operational complexity for solo developer
- Multi-cloud strategy may be beneficial for risk mitigation
- Ukraine-specific requirements may favor certain cloud providers

---

## Decision

**AWS (Primary) / Azure (Secondary)** has been selected as the cloud platform for the Award Monitoring & Tracking System.

### Chosen Approach
- **Primary Cloud**: Amazon Web Services (AWS) for production deployment
- **Secondary Cloud**: Microsoft Azure as alternative/backup option
- **Managed Services**: Leverage cloud-native managed services where possible
- **Multi-Region**: Deploy across multiple regions for high availability

### Rationale
- **Enterprise Adoption**: AWS and Azure are the leading enterprise cloud platforms
- **Comprehensive Services**: Full suite of managed services for all application needs
- **Ukraine Support**: Both platforms have presence and support in Ukraine
- **Kubernetes Support**: Excellent managed Kubernetes services (EKS/AKS)
- **Portfolio Value**: Demonstrates cloud-native architecture expertise

---

## Consequences

### Positive Consequences
- **Scalability**: Virtually unlimited scaling capabilities
- **Managed Services**: Reduced operational overhead with managed databases and services
- **Global Reach**: Multiple regions for international accessibility
- **Enterprise Features**: Advanced security, compliance, and monitoring capabilities
- **Innovation**: Access to latest cloud-native technologies and AI services

### Negative Consequences
- **Cost Complexity**: Variable costs that can escalate without proper management
- **Vendor Lock-in**: Some degree of platform-specific dependencies
- **Learning Curve**: Requires expertise in cloud platform services and best practices

### Neutral Consequences
- **Multi-Cloud Complexity**: Managing two cloud platforms adds operational overhead
- **Service Selection**: Many service options require careful evaluation and selection

---

## Alternatives Considered

### Alternative 1: Google Cloud Platform
- **Pros**: Excellent Kubernetes heritage, competitive pricing, innovative services
- **Cons**: Smaller enterprise adoption, limited Ukraine presence
- **Reason for Rejection**: Lower enterprise adoption and regional presence

### Alternative 2: DigitalOcean
- **Pros**: Simple pricing, developer-friendly, good performance
- **Cons**: Limited enterprise services, smaller global presence
- **Reason for Rejection**: Insufficient enterprise-grade services

### Alternative 3: On-Premises
- **Pros**: Complete control, no cloud costs, data sovereignty
- **Cons**: High operational overhead, limited scalability, upfront costs
- **Reason for Rejection**: Not practical for solo developer portfolio project

---

## Implementation Notes

### AWS Primary Services
- **Compute**: Amazon EKS (Kubernetes) with EC2 worker nodes
- **Database**: Amazon RDS for PostgreSQL with Multi-AZ deployment
- **Caching**: Amazon ElastiCache for Redis
- **Storage**: Amazon S3 for object storage and backups
- **CDN**: Amazon CloudFront for global content delivery
- **Monitoring**: Amazon CloudWatch and X-Ray for observability
- **Security**: AWS IAM, Security Groups, and WAF

### Azure Secondary Services
- **Compute**: Azure Kubernetes Service (AKS)
- **Database**: Azure Database for PostgreSQL
- **Caching**: Azure Cache for Redis
- **Storage**: Azure Blob Storage
- **CDN**: Azure CDN
- **Monitoring**: Azure Monitor and Application Insights
- **Security**: Azure Active Directory and Network Security Groups

### Infrastructure as Code
```hcl
# Terraform configuration for AWS
provider "aws" {
  region = var.aws_region
}

resource "aws_eks_cluster" "award_system" {
  name     = "award-system-cluster"
  role_arn = aws_iam_role.cluster.arn
  version  = "1.28"

  vpc_config {
    subnet_ids = [
      aws_subnet.private_a.id,
      aws_subnet.private_b.id,
      aws_subnet.public_a.id,
      aws_subnet.public_b.id,
    ]
    endpoint_private_access = true
    endpoint_public_access  = true
  }

  depends_on = [
    aws_iam_role_policy_attachment.cluster_AmazonEKSClusterPolicy,
  ]
}

resource "aws_rds_instance" "award_system_db" {
  identifier     = "award-system-db"
  engine         = "postgres"
  engine_version = "16"
  instance_class = "db.t3.micro"
  
  allocated_storage     = 20
  max_allocated_storage = 100
  storage_encrypted     = true
  
  db_name  = "award_system"
  username = "award_user"
  password = random_password.db_password.result
  
  multi_az               = true
  backup_retention_period = 7
  backup_window          = "03:00-04:00"
  maintenance_window     = "sun:04:00-sun:05:00"
  
  skip_final_snapshot = false
  deletion_protection = true
  
  tags = {
    Name = "Award System Database"
    Environment = "production"
  }
}
```

### Cost Optimization Strategy
- **Reserved Instances**: Use reserved instances for predictable workloads
- **Spot Instances**: Use spot instances for non-critical workloads
- **Auto Scaling**: Implement auto-scaling to match demand
- **Storage Optimization**: Use appropriate storage classes and lifecycle policies
- **Resource Tagging**: Comprehensive tagging for cost allocation and optimization

---

## Compliance & Quality

### Security Implications
- **Data Encryption**: Encryption at rest and in transit for all data
- **Network Security**: VPC, security groups, and network ACLs
- **Identity Management**: IAM roles and policies for least privilege access
- **Compliance**: GDPR compliance features and data residency controls

### Performance Impact
- **Global CDN**: CloudFront/Azure CDN for improved global performance
- **Multi-AZ Deployment**: High availability across availability zones
- **Auto Scaling**: Automatic scaling based on demand
- **Managed Services**: Optimized performance with managed database services

### Maintainability
- **Infrastructure as Code**: Terraform for reproducible infrastructure
- **Monitoring**: Comprehensive monitoring and alerting
- **Backup Strategy**: Automated backups and disaster recovery
- **Documentation**: Cloud architecture documentation and runbooks

---

## Success Metrics

### Key Performance Indicators
- **Availability**: 99.9% uptime SLA
- **Performance**: < 200ms response time globally
- **Cost Efficiency**: < $500/month for production environment

### Monitoring & Alerting
- **Infrastructure Monitoring**: CloudWatch/Azure Monitor dashboards
- **Application Performance**: APM tools for application metrics
- **Cost Monitoring**: Budget alerts and cost optimization recommendations
- **Security Monitoring**: Security events and compliance monitoring

---

## Related Documents

- **Technology Stack**: [Technology Stack Selection](../TECH_STACK.md)
- **Other ADRs**: [ADR-018 Orchestration](./ADR-018-Orchestration.md), [ADR-019 CI/CD](./ADR-019-CICD.md)
- **External Resources**: [AWS Documentation](https://docs.aws.amazon.com/), [Azure Documentation](https://docs.microsoft.com/en-us/azure/)

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2025-08-21 | Stefan Kostyk | Initial version | Document creation |

---

**Document Status**: Approved  
**Next Review Date**: 2026-02-21  
**ADR Category**: Technology 
#!/bin/bash
# =============================================================================
# Award Monitoring & Tracking System - Development Environment Setup
# =============================================================================
# Purpose: Automated setup of the development environment for Unix/Linux/macOS
# Author: Stefan Kostyk
# Version: 1.0
# Last Updated: January 2026
# =============================================================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Print banner
echo "============================================="
echo "Award Monitoring System - Dev Environment"
echo "============================================="
echo ""

# =============================================================================
# 1. Prerequisites Check
# =============================================================================
log_info "Checking prerequisites..."

# Check for required commands
check_command() {
    if command -v "$1" &> /dev/null; then
        log_success "$1 is installed"
        return 0
    else
        log_warning "$1 is not installed"
        return 1
    fi
}

MISSING_TOOLS=()

check_command "java" || MISSING_TOOLS+=("java")
check_command "mvn" || MISSING_TOOLS+=("maven")
check_command "docker" || MISSING_TOOLS+=("docker")
check_command "git" || MISSING_TOOLS+=("git")
check_command "node" || MISSING_TOOLS+=("nodejs")

if [ ${#MISSING_TOOLS[@]} -gt 0 ]; then
    log_error "Missing required tools: ${MISSING_TOOLS[*]}"
    log_info "Please install the missing tools before proceeding."
    exit 1
fi

# =============================================================================
# 2. Java Development Kit
# =============================================================================
log_info "Checking Java version..."

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -ge 21 ]; then
    log_success "Java $JAVA_VERSION detected (required: 21+)"
else
    log_warning "Java 21+ is required. Current version: $JAVA_VERSION"
    
    if command -v sdk &> /dev/null; then
        log_info "SDKMAN detected. Installing Java 21..."
        sdk install java 21.0.2-tem
        sdk use java 21.0.2-tem
    else
        log_info "Consider installing SDKMAN for easier Java management:"
        log_info "  curl -s 'https://get.sdkman.io' | bash"
        log_info "Then run: sdk install java 21.0.2-tem"
    fi
fi

# =============================================================================
# 3. Maven Configuration
# =============================================================================
log_info "Checking Maven version..."

MVN_VERSION=$(mvn -version 2>&1 | head -1 | awk '{print $3}')
log_success "Maven $MVN_VERSION detected"

# =============================================================================
# 4. Docker Services Setup
# =============================================================================
log_info "Setting up Docker development services..."

# Check if Docker is running
if ! docker info &> /dev/null; then
    log_error "Docker daemon is not running. Please start Docker first."
    exit 1
fi

log_success "Docker daemon is running"

# Create Docker network if not exists
if ! docker network inspect award-network &> /dev/null; then
    log_info "Creating Docker network: award-network"
    docker network create award-network
    log_success "Docker network created"
else
    log_success "Docker network already exists"
fi

# PostgreSQL 16
log_info "Setting up PostgreSQL 16..."
if docker ps -a --format '{{.Names}}' | grep -q '^postgres-dev$'; then
    if docker ps --format '{{.Names}}' | grep -q '^postgres-dev$'; then
        log_success "PostgreSQL container already running"
    else
        docker start postgres-dev
        log_success "PostgreSQL container started"
    fi
else
    docker run -d \
        --name postgres-dev \
        --network award-network \
        -e POSTGRES_USER=award_dev \
        -e POSTGRES_PASSWORD=dev_password \
        -e POSTGRES_DB=award_monitoring \
        -p 5432:5432 \
        -v postgres-dev-data:/var/lib/postgresql/data \
        postgres:16-alpine
    log_success "PostgreSQL container created and started"
fi

# Redis 7
log_info "Setting up Redis 7..."
if docker ps -a --format '{{.Names}}' | grep -q '^redis-dev$'; then
    if docker ps --format '{{.Names}}' | grep -q '^redis-dev$'; then
        log_success "Redis container already running"
    else
        docker start redis-dev
        log_success "Redis container started"
    fi
else
    docker run -d \
        --name redis-dev \
        --network award-network \
        -p 6379:6379 \
        redis:7-alpine --appendonly yes
    log_success "Redis container created and started"
fi

# =============================================================================
# 5. Optional Services (Kafka, Elasticsearch)
# =============================================================================
read -p "Do you want to set up Kafka and Elasticsearch? (y/N): " SETUP_OPTIONAL

if [[ "$SETUP_OPTIONAL" =~ ^[Yy]$ ]]; then
    # Kafka with Zookeeper (using Confluent images)
    log_info "Setting up Apache Kafka..."
    
    # Zookeeper
    if ! docker ps -a --format '{{.Names}}' | grep -q '^zookeeper-dev$'; then
        docker run -d \
            --name zookeeper-dev \
            --network award-network \
            -e ZOOKEEPER_CLIENT_PORT=2181 \
            -p 2181:2181 \
            confluentinc/cp-zookeeper:7.5.0
        log_success "Zookeeper container created"
    fi
    
    # Wait for Zookeeper
    sleep 5
    
    # Kafka
    if ! docker ps -a --format '{{.Names}}' | grep -q '^kafka-dev$'; then
        docker run -d \
            --name kafka-dev \
            --network award-network \
            -e KAFKA_BROKER_ID=1 \
            -e KAFKA_ZOOKEEPER_CONNECT=zookeeper-dev:2181 \
            -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
            -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
            -p 9092:9092 \
            confluentinc/cp-kafka:7.5.0
        log_success "Kafka container created"
    fi

    # Elasticsearch
    log_info "Setting up Elasticsearch 8..."
    if ! docker ps -a --format '{{.Names}}' | grep -q '^elasticsearch-dev$'; then
        docker run -d \
            --name elasticsearch-dev \
            --network award-network \
            -e discovery.type=single-node \
            -e xpack.security.enabled=false \
            -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
            -p 9200:9200 \
            -v elasticsearch-dev-data:/usr/share/elasticsearch/data \
            docker.elastic.co/elasticsearch/elasticsearch:8.11.0
        log_success "Elasticsearch container created"
    fi
fi

# =============================================================================
# 6. Node.js & Angular (Frontend)
# =============================================================================
log_info "Checking Node.js setup..."

NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -ge 20 ]; then
    log_success "Node.js v$NODE_VERSION detected (required: 20+)"
else
    log_warning "Node.js 20+ recommended for Angular 20. Current: v$NODE_VERSION"
fi

# Check for Angular CLI
if command -v ng &> /dev/null; then
    NG_VERSION=$(ng version 2>&1 | grep "Angular CLI" | awk '{print $3}')
    log_success "Angular CLI $NG_VERSION detected"
else
    log_info "Installing Angular CLI globally..."
    npm install -g @angular/cli
    log_success "Angular CLI installed"
fi

# =============================================================================
# 7. IDE Configuration
# =============================================================================
log_info "IDE Configuration Notes:"
echo ""
echo "  IntelliJ IDEA:"
echo "    - Import project as Maven project"
echo "    - Code style: File > Settings > Editor > Code Style > Import from .idea/codeStyles/Project.xml"
echo "    - Enable annotation processing: Settings > Build > Compiler > Annotation Processors"
echo ""
echo "  VS Code:"
echo "    - Install 'Extension Pack for Java'"
echo "    - Install 'Spring Boot Extension Pack'"
echo "    - Install 'Angular Language Service'"
echo ""

# =============================================================================
# 8. Final Verification
# =============================================================================
echo ""
log_info "Running verification..."
echo ""
echo "============================================="
echo "Development Environment Status"
echo "============================================="
echo ""
echo "Core Services:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "(postgres|redis|kafka|zookeeper|elastic|NAMES)"
echo ""
echo "Versions:"
echo "  Java:   $(java -version 2>&1 | head -1)"
echo "  Maven:  $(mvn -version | head -1)"
echo "  Node:   $(node -v)"
echo "  Docker: $(docker -v)"
echo ""
log_success "Development environment setup complete!"
echo ""
log_info "Next steps:"
echo "  1. Run 'mvn clean install' to build the backend"
echo "  2. Run 'cd frontend && npm install' for frontend dependencies"
echo "  3. Run 'mvn spring-boot:run' to start the backend"
echo "  4. Run 'ng serve' in frontend directory to start Angular"
echo ""
echo "============================================="


# =============================================================================
# Award Monitoring & Tracking System - Development Environment Setup (Windows)
# =============================================================================
# Purpose: Automated setup of the development environment for Windows
# Author: Stefan Kostyk
# Version: 1.0
# Last Updated: January 2026
# Requires: PowerShell 7+ recommended
# =============================================================================

#Requires -Version 5.1

$ErrorActionPreference = "Stop"

# Colors for output
function Write-Info { Write-Host "[INFO] $args" -ForegroundColor Blue }
function Write-Success { Write-Host "[SUCCESS] $args" -ForegroundColor Green }
function Write-Warn { Write-Host "[WARNING] $args" -ForegroundColor Yellow }
function Write-Err { Write-Host "[ERROR] $args" -ForegroundColor Red }

# Banner
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "Award Monitoring System - Dev Environment" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# =============================================================================
# 1. Prerequisites Check
# =============================================================================
Write-Info "Checking prerequisites..."

function Test-Command {
    param($Command)
    try {
        if (Get-Command $Command -ErrorAction SilentlyContinue) {
            Write-Success "$Command is installed"
            return $true
        }
    } catch {
        Write-Warn "$Command is not installed"
        return $false
    }
}

$MissingTools = @()

if (-not (Test-Command "java")) { $MissingTools += "java" }
if (-not (Test-Command "mvn")) { $MissingTools += "maven" }
if (-not (Test-Command "docker")) { $MissingTools += "docker" }
if (-not (Test-Command "git")) { $MissingTools += "git" }
if (-not (Test-Command "node")) { $MissingTools += "nodejs" }

if ($MissingTools.Count -gt 0) {
    Write-Err "Missing required tools: $($MissingTools -join ', ')"
    Write-Info "Please install the missing tools before proceeding."
    Write-Info ""
    Write-Info "Recommended installation methods:"
    Write-Info "  - Chocolatey: choco install openjdk maven docker-desktop git nodejs"
    Write-Info "  - Scoop: scoop install openjdk maven docker git nodejs"
    Write-Info "  - winget: winget install Oracle.JDK.21 Apache.Maven Docker.DockerDesktop Git.Git OpenJS.NodeJS"
    exit 1
}

# =============================================================================
# 2. Java Development Kit
# =============================================================================
Write-Info "Checking Java version..."

$JavaVersionOutput = java -version 2>&1 | Select-String "version"
$JavaVersion = [regex]::Match($JavaVersionOutput, '"(\d+)').Groups[1].Value

if ([int]$JavaVersion -ge 21) {
    Write-Success "Java $JavaVersion detected (required: 21+)"
} else {
    Write-Warn "Java 21+ is required. Current version: $JavaVersion"
    Write-Info "Install Java 21 using:"
    Write-Info "  winget install Oracle.JDK.21"
    Write-Info "  or"
    Write-Info "  choco install openjdk21"
}

# =============================================================================
# 3. Maven Configuration
# =============================================================================
Write-Info "Checking Maven version..."

$MvnVersion = (mvn -version 2>&1 | Select-Object -First 1) -replace "Apache Maven ", ""
Write-Success "Maven $MvnVersion detected"

# =============================================================================
# 4. Docker Services Setup
# =============================================================================
Write-Info "Setting up Docker development services..."

# Check if Docker is running
try {
    docker info 2>&1 | Out-Null
    Write-Success "Docker daemon is running"
} catch {
    Write-Err "Docker daemon is not running. Please start Docker Desktop first."
    exit 1
}

# Create Docker network if not exists
$NetworkExists = docker network ls --format "{{.Name}}" | Where-Object { $_ -eq "award-network" }
if (-not $NetworkExists) {
    Write-Info "Creating Docker network: award-network"
    docker network create award-network
    Write-Success "Docker network created"
} else {
    Write-Success "Docker network already exists"
}

# PostgreSQL 16
Write-Info "Setting up PostgreSQL 16..."
$PostgresExists = docker ps -a --format "{{.Names}}" | Where-Object { $_ -eq "postgres-dev" }
$PostgresRunning = docker ps --format "{{.Names}}" | Where-Object { $_ -eq "postgres-dev" }

if ($PostgresExists) {
    if ($PostgresRunning) {
        Write-Success "PostgreSQL container already running"
    } else {
        docker start postgres-dev
        Write-Success "PostgreSQL container started"
    }
} else {
    docker run -d `
        --name postgres-dev `
        --network award-network `
        -e POSTGRES_USER=award_dev `
        -e POSTGRES_PASSWORD=dev_password `
        -e POSTGRES_DB=award_monitoring `
        -p 5432:5432 `
        -v postgres-dev-data:/var/lib/postgresql/data `
        postgres:16-alpine
    Write-Success "PostgreSQL container created and started"
}

# Redis 7
Write-Info "Setting up Redis 7..."
$RedisExists = docker ps -a --format "{{.Names}}" | Where-Object { $_ -eq "redis-dev" }
$RedisRunning = docker ps --format "{{.Names}}" | Where-Object { $_ -eq "redis-dev" }

if ($RedisExists) {
    if ($RedisRunning) {
        Write-Success "Redis container already running"
    } else {
        docker start redis-dev
        Write-Success "Redis container started"
    }
} else {
    docker run -d `
        --name redis-dev `
        --network award-network `
        -p 6379:6379 `
        redis:7-alpine --appendonly yes
    Write-Success "Redis container created and started"
}

# =============================================================================
# 5. Optional Services (Kafka, Elasticsearch)
# =============================================================================
$SetupOptional = Read-Host "Do you want to set up Kafka and Elasticsearch? (y/N)"

if ($SetupOptional -match "^[Yy]$") {
    # Zookeeper
    Write-Info "Setting up Zookeeper..."
    $ZookeeperExists = docker ps -a --format "{{.Names}}" | Where-Object { $_ -eq "zookeeper-dev" }
    if (-not $ZookeeperExists) {
        docker run -d `
            --name zookeeper-dev `
            --network award-network `
            -e ZOOKEEPER_CLIENT_PORT=2181 `
            -p 2181:2181 `
            confluentinc/cp-zookeeper:7.5.0
        Write-Success "Zookeeper container created"
    }
    
    Start-Sleep -Seconds 5
    
    # Kafka
    Write-Info "Setting up Apache Kafka..."
    $KafkaExists = docker ps -a --format "{{.Names}}" | Where-Object { $_ -eq "kafka-dev" }
    if (-not $KafkaExists) {
        docker run -d `
            --name kafka-dev `
            --network award-network `
            -e KAFKA_BROKER_ID=1 `
            -e KAFKA_ZOOKEEPER_CONNECT=zookeeper-dev:2181 `
            -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 `
            -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 `
            -p 9092:9092 `
            confluentinc/cp-kafka:7.5.0
        Write-Success "Kafka container created"
    }

    # Elasticsearch
    Write-Info "Setting up Elasticsearch 8..."
    $ElasticExists = docker ps -a --format "{{.Names}}" | Where-Object { $_ -eq "elasticsearch-dev" }
    if (-not $ElasticExists) {
        docker run -d `
            --name elasticsearch-dev `
            --network award-network `
            -e discovery.type=single-node `
            -e xpack.security.enabled=false `
            -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" `
            -p 9200:9200 `
            -v elasticsearch-dev-data:/usr/share/elasticsearch/data `
            docker.elastic.co/elasticsearch/elasticsearch:8.11.0
        Write-Success "Elasticsearch container created"
    }
}

# =============================================================================
# 6. Node.js & Angular (Frontend)
# =============================================================================
Write-Info "Checking Node.js setup..."

$NodeVersion = (node -v) -replace "v", "" -split "\." | Select-Object -First 1
if ([int]$NodeVersion -ge 20) {
    Write-Success "Node.js v$NodeVersion detected (required: 20+)"
} else {
    Write-Warn "Node.js 20+ recommended for Angular 20. Current: v$NodeVersion"
}

# Check for Angular CLI
try {
    $NgVersion = (ng version 2>&1 | Select-String "Angular CLI") -replace "Angular CLI: ", ""
    Write-Success "Angular CLI $NgVersion detected"
} catch {
    Write-Info "Installing Angular CLI globally..."
    npm install -g @angular/cli
    Write-Success "Angular CLI installed"
}

# =============================================================================
# 7. IDE Configuration
# =============================================================================
Write-Info "IDE Configuration Notes:"
Write-Host ""
Write-Host "  IntelliJ IDEA:" -ForegroundColor Cyan
Write-Host "    - Import project as Maven project"
Write-Host "    - Code style: File > Settings > Editor > Code Style > Import Scheme > IntelliJ IDEA code style XML"
Write-Host "    - Select: .idea\codeStyles\Project.xml"
Write-Host "    - Enable annotation processing: Settings > Build > Compiler > Annotation Processors"
Write-Host ""
Write-Host "  VS Code:" -ForegroundColor Cyan
Write-Host "    - Install 'Extension Pack for Java'"
Write-Host "    - Install 'Spring Boot Extension Pack'"
Write-Host "    - Install 'Angular Language Service'"
Write-Host ""

# =============================================================================
# 8. Final Verification
# =============================================================================
Write-Host ""
Write-Info "Running verification..."
Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "Development Environment Status" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Core Services:" -ForegroundColor Yellow
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
Write-Host ""
Write-Host "Versions:" -ForegroundColor Yellow
Write-Host "  Java:   $(java -version 2>&1 | Select-Object -First 1)"
Write-Host "  Maven:  $(mvn -version | Select-Object -First 1)"
Write-Host "  Node:   $(node -v)"
Write-Host "  Docker: $(docker -v)"
Write-Host ""
Write-Success "Development environment setup complete!"
Write-Host ""
Write-Info "Next steps:"
Write-Host "  1. Run 'mvn clean install' to build the backend"
Write-Host "  2. Run 'cd frontend; npm install' for frontend dependencies"
Write-Host "  3. Run 'mvn spring-boot:run' to start the backend"
Write-Host "  4. Run 'ng serve' in frontend directory to start Angular"
Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan


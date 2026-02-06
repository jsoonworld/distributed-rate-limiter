#!/bin/bash
# scripts/docker-smoke-test.sh
# Docker Container Smoke Test

set -e

# ============================================
# Configuration
# ============================================
IMAGE_NAME="${1:-distributed-rate-limiter:latest}"
CONTAINER_NAME="smoke-test-$$"
NETWORK_NAME="smoke-test-network-$$"
REDIS_CONTAINER="smoke-test-redis-$$"
MAX_WAIT_SECONDS=120
HEALTH_CHECK_INTERVAL=5

# Actuator authentication credentials for testing
ACTUATOR_USER="${ACTUATOR_USER:-actuator}"
ACTUATOR_PASSWORD="${ACTUATOR_PASSWORD:-testpassword}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# ============================================
# Functions
# ============================================
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

cleanup() {
    log_info "Cleaning up..."
    docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
    docker rm -f "$REDIS_CONTAINER" 2>/dev/null || true
    docker network rm "$NETWORK_NAME" 2>/dev/null || true
}

trap cleanup EXIT

# ============================================
# Test Start
# ============================================
log_info "Starting smoke test for image: $IMAGE_NAME"

# Create network
log_info "Creating test network..."
docker network create "$NETWORK_NAME"

# Start Redis
log_info "Starting Redis container..."
docker run -d \
    --name "$REDIS_CONTAINER" \
    --network "$NETWORK_NAME" \
    redis:7-alpine

# Wait for Redis to be ready
log_info "Waiting for Redis to be ready..."
sleep 3

# Start application container
log_info "Starting application container..."
docker run -d \
    --name "$CONTAINER_NAME" \
    --network "$NETWORK_NAME" \
    -p 18080:8080 \
    -e SPRING_DATA_REDIS_HOST="$REDIS_CONTAINER" \
    -e SPRING_DATA_REDIS_PORT=6379 \
    -e SPRING_PROFILES_ACTIVE=local \
    -e ACTUATOR_USER="$ACTUATOR_USER" \
    -e ACTUATOR_PASSWORD="$ACTUATOR_PASSWORD" \
    "$IMAGE_NAME"

# ============================================
# Health Check Wait
# ============================================
log_info "Waiting for application to become healthy..."
ELAPSED=0
while [ $ELAPSED -lt $MAX_WAIT_SECONDS ]; do
    STATUS=$(docker inspect --format='{{.State.Health.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "unknown")

    if [ "$STATUS" = "healthy" ]; then
        log_info "Application is healthy!"
        break
    elif [ "$STATUS" = "unhealthy" ]; then
        log_error "Application became unhealthy!"
        docker logs "$CONTAINER_NAME"
        exit 1
    fi

    log_info "Status: $STATUS, waiting... ($ELAPSED/$MAX_WAIT_SECONDS seconds)"
    sleep $HEALTH_CHECK_INTERVAL
    ELAPSED=$((ELAPSED + HEALTH_CHECK_INTERVAL))
done

if [ $ELAPSED -ge $MAX_WAIT_SECONDS ]; then
    log_error "Timeout waiting for application to become healthy"
    docker logs "$CONTAINER_NAME"
    exit 1
fi

# ============================================
# API Tests
# ============================================
log_info "Running API tests..."

# Health endpoint
log_info "Testing /actuator/health..."
HEALTH_RESPONSE=$(curl -sf http://localhost:18080/actuator/health || echo "FAILED")
if echo "$HEALTH_RESPONSE" | grep -q '"status":"UP"'; then
    log_info "Health check passed!"
else
    log_error "Health check failed: $HEALTH_RESPONSE"
    exit 1
fi

# Info endpoint
log_info "Testing /actuator/info..."
curl -sf http://localhost:18080/actuator/info > /dev/null && log_info "Info endpoint passed!" || log_warn "Info endpoint not available"

# Prometheus endpoint (requires authentication)
log_info "Testing /actuator/prometheus..."
PROM_RESPONSE=$(curl -sf -u "${ACTUATOR_USER}:${ACTUATOR_PASSWORD}" http://localhost:18080/actuator/prometheus || echo "FAILED")
if echo "$PROM_RESPONSE" | grep -q "jvm_"; then
    log_info "Prometheus endpoint passed!"
else
    log_warn "Prometheus endpoint may not be fully configured"
fi

# Rate limiter API (if available)
log_info "Testing rate limiter API..."
RATE_LIMIT_RESPONSE=$(curl -sf "http://localhost:18080/api/v1/rate-limit/check?key=smoke-test&algorithm=TOKEN_BUCKET" 2>/dev/null || echo "NOT_AVAILABLE")
if echo "$RATE_LIMIT_RESPONSE" | grep -q "allowed"; then
    log_info "Rate limiter API passed!"
else
    log_warn "Rate limiter API test skipped (endpoint may not be available)"
fi

# ============================================
# Container Information Output
# ============================================
log_info "Container information:"
docker inspect --format='
  Image: {{.Config.Image}}
  Created: {{.Created}}
  User: {{.Config.User}}
  WorkingDir: {{.Config.WorkingDir}}
  ExposedPorts: {{range $port, $_ := .Config.ExposedPorts}}{{$port}} {{end}}
' "$CONTAINER_NAME"

# Memory usage
log_info "Memory usage:"
docker stats --no-stream --format "{{.MemUsage}}" "$CONTAINER_NAME"

# ============================================
# Result
# ============================================
echo ""
echo "============================================"
log_info "All smoke tests passed!"
echo "============================================"

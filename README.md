# Distributed Rate Limiter

A Redis-based rate limiter for distributed environments, supporting Token Bucket and Sliding Window algorithms.

## Features

- **Multiple Algorithms**: Token Bucket (burst-friendly) and Sliding Window Log (precise)
- **Distributed**: Redis-based state sharing with atomic Lua script operations
- **Fail Open**: Maintains service availability during Redis failures
- **Observable**: Prometheus metrics and Grafana dashboard ready

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 1.9 |
| Framework | Spring Boot 3.2 + WebFlux |
| Async | Kotlin Coroutines |
| Storage | Redis 7.x (Lettuce) |
| Monitoring | Micrometer + Prometheus |
| Testing | JUnit 5 + Testcontainers |

## Quick Start

```bash
# Start Redis
docker-compose -f docker/docker-compose.yml up -d

# Run application
./gradlew bootRun

# Test API
curl http://localhost:8080/api/v1/rate-limit/check
```

## API

### Check Rate Limit

```bash
GET /api/v1/rate-limit/check?algorithm=TOKEN_BUCKET&key=user:123
```

**Response:**
```json
{
  "allowed": true,
  "remainingTokens": 95,
  "resetTimeSeconds": 10
}
```

**Headers:**
- `X-RateLimit-Remaining`: Remaining requests
- `X-RateLimit-Reset`: Seconds until reset
- `Retry-After`: Seconds to wait (429 only)

### Other Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/rate-limit/remaining` | Get remaining limit |
| DELETE | `/api/v1/rate-limit/reset?key={key}` | Reset rate limit |

## Algorithms

### Token Bucket

- Allows burst traffic up to bucket capacity
- Refills tokens at a constant rate
- Best for: General API rate limiting

### Sliding Window Log

- Precise request counting within time window
- No boundary issues
- Best for: Strict rate limiting requirements

| Criteria | Token Bucket | Sliding Window |
|----------|--------------|----------------|
| Burst | Allowed | Not allowed |
| Memory | O(1) | O(N) |
| Accuracy | High | Very High |

## Configuration

```yaml
rate-limiter:
  default:
    algorithm: TOKEN_BUCKET
    capacity: 100
    refill-rate: 10
    window-size: 60
```

## Monitoring

```bash
# Health check
curl http://localhost:8080/actuator/health

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

**Key Metrics:**
- `rate_limiter_requests_total{algorithm, allowed}`
- `rate_limiter_check_seconds`

## License

MIT License

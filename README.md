# Distributed Rate Limiter

A Redis-based rate limiter designed for distributed environments, supporting Token Bucket and Sliding Window algorithms.

## Features

- **Multiple Algorithm Support**
  - Token Bucket: Allows burst traffic, refills tokens at a constant rate
  - Sliding Window Log: Precise rate limiting without boundary issues
  - (Planned) Fixed Window Counter
  - (Planned) Sliding Window Counter
  - (Planned) Leaky Bucket

- **Distributed Environment Support**
  - Redis-based distributed state storage
  - Atomic operations via Lua scripts
  - Shared rate limits across multiple instances

- **Monitoring**
  - Prometheus metrics integration
  - Grafana dashboard support
  - Spring Actuator endpoints

## Tech Stack

- Kotlin 1.9
- Spring Boot 3.2
- Spring Data Redis (Reactive)
- Kotlin Coroutines
- Testcontainers
- Docker

## Quick Start

### 1. Start Redis

```bash
cd docker
docker-compose up -d redis
```

### 2. Run Application

```bash
./gradlew bootRun
```

### 3. Test API

```bash
# Check rate limit (Token Bucket)
curl http://localhost:8080/api/v1/rate-limit/check

# Check rate limit (Sliding Window)
curl "http://localhost:8080/api/v1/rate-limit/check?algorithm=SLIDING_WINDOW_LOG"

# Get remaining limit
curl http://localhost:8080/api/v1/rate-limit/remaining

# Reset rate limit
curl -X DELETE "http://localhost:8080/api/v1/rate-limit/reset?key=127.0.0.1"
```

## API Reference

### Check Rate Limit

```
GET /api/v1/rate-limit/check
```

**Parameters:**
- `algorithm` (optional): `TOKEN_BUCKET` | `SLIDING_WINDOW_LOG` (default: `TOKEN_BUCKET`)
- `key` (optional): Client identifier (default: request IP)

**Response:**
```json
{
  "allowed": true,
  "key": "user:123",
  "remainingTokens": 95,
  "resetTimeSeconds": 10,
  "message": "Request allowed"
}
```

**Response Headers:**
- `X-RateLimit-Remaining`: Remaining request count
- `X-RateLimit-Reset`: Time until reset (seconds)
- `Retry-After`: Wait time for retry (seconds, only on 429 response)

### Get Remaining Limit

```
GET /api/v1/rate-limit/remaining
```

### Reset Rate Limit

```
DELETE /api/v1/rate-limit/reset?key={key}
```

## Algorithm Details

### Token Bucket

```
┌─────────────────────────────────────┐
│  Bucket (capacity: 100)             │
│  ┌───┬───┬───┬───┬───┬───┬───┬───┐ │
│  │ ● │ ● │ ● │ ● │ ● │   │   │   │ │  ← Tokens
│  └───┴───┴───┴───┴───┴───┴───┴───┘ │
│         ↑                           │
│    Refill: 10 tokens/sec            │
└─────────────────────────────────────┘
```

- Store up to `capacity` tokens in the bucket
- Consume 1 token per request
- Refill `refill_rate` tokens per second
- Allow burst traffic when bucket is full

### Sliding Window Log

```
     Window (60 seconds)
├────────────────────────────────────┤
│  ●  ●     ●  ● ●    ●   ●  ●  ●   │  ← Request timestamps
├────────────────────────────────────┤
                                    now
```

- Store each request timestamp in Redis Sorted Set
- Accurately count requests within the window
- Precise rate limiting without boundary issues

### Algorithm Comparison

| Criteria | Token Bucket | Sliding Window Log |
|----------|--------------|-------------------|
| Accuracy | High | Very High |
| Burst Allowed | Yes | No |
| Memory Usage | Low (2 fields) | High (per request) |
| Complexity | O(1) | O(N) |
| Best For | General API Rate Limiting | Precise limiting required |

## Configuration

```yaml
rate-limiter:
  default:
    algorithm: TOKEN_BUCKET
    capacity: 100          # Max tokens/requests
    refill-rate: 10        # Tokens refilled per second
    window-size: 60        # Window size (seconds)
```

## Monitoring

### Prometheus Metrics

- `rate_limiter_requests_total`: Total request count
- `rate_limiter_check_seconds`: Check duration

### Endpoints

- `/actuator/health`: Health check
- `/actuator/prometheus`: Prometheus metrics
- `/actuator/metrics`: Spring metrics

## Project Structure

```
src/main/kotlin/com/jsoonworld/ratelimiter/
├── algorithm/
│   ├── RateLimiter.kt              # Interface
│   ├── TokenBucketRateLimiter.kt   # Token Bucket implementation
│   └── SlidingWindowRateLimiter.kt # Sliding Window implementation
├── config/
│   └── RedisConfig.kt              # Redis configuration
├── controller/
│   └── RateLimitController.kt      # REST API
├── model/
│   ├── RateLimitAlgorithm.kt       # Algorithm enum
│   └── RateLimitResult.kt          # Result DTO
├── service/
│   └── RateLimitService.kt         # Business logic
└── RateLimiterApplication.kt       # Main class
```

## Documentation

- [1-Pager](docs/1-pager.md): Project overview and goals
- [Technical Specification](docs/tech-spec.md): Detailed design documentation

## Roadmap

- [ ] Fixed Window Counter algorithm
- [ ] Sliding Window Counter algorithm (hybrid)
- [ ] Leaky Bucket algorithm
- [ ] Dynamic rate limits based on configuration
- [ ] Custom limits per client
- [ ] Redis Cluster support
- [ ] Spring AOP-based annotations
- [ ] SDK/Library distribution

## License

MIT License

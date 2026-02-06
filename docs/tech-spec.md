# Distributed Rate Limiter Technical Specification

> **Version**: 1.0
> **Last Updated**: 2026-02-04
> **Status**: Draft

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [System Architecture](#2-system-architecture)
3. [Algorithm Specifications](#3-algorithm-specifications)
4. [Data Architecture](#4-data-architecture)
5. [API Design](#5-api-design)
6. [Resilience Patterns](#6-resilience-patterns)
7. [Observability](#7-observability)
8. [Non-Functional Requirements](#8-non-functional-requirements)

---

## 1. Executive Summary

### 1.1 Vision

Distributed Rate Limiter는 **분산 환경에서 일관된 요청 제한을 제공하는 인프라 컴포넌트**로, 다양한 Rate Limiting 알고리즘을 지원하며 Redis를 통해 다중 서버 간 상태를 동기화한다.

### 1.2 Key Characteristics

| Characteristic | Description |
|----------------|-------------|
| **Distributed** | Redis 기반 다중 인스턴스 간 상태 공유 |
| **Atomic Operations** | Lua Script를 통한 원자적 연산 보장 |
| **Algorithm Pluggable** | 전략 패턴으로 알고리즘 런타임 선택 |
| **Fail Open** | Redis 장애 시 요청 허용 (가용성 우선) |
| **Observable** | Micrometer/Prometheus 통합 메트릭 |

### 1.3 Goals

| Goal | Success Metric |
|------|----------------|
| Accuracy | 분산 환경에서 한도 오차 < 1% |
| Low Latency | Rate Limit 체크 p99 < 10ms |
| High Availability | Redis 장애 시에도 서비스 정상 동작 |
| Scalability | 수평 확장 시 설정 변경 없이 동작 |

### 1.4 Technology Stack

| Layer | Technology | Selection Rationale |
|-------|------------|---------------------|
| Language | Kotlin 1.9 | Null Safety, Coroutines, 간결한 문법 |
| Framework | Spring Boot 3.2 + WebFlux | Reactive, 성숙한 생태계 |
| Async | Kotlin Coroutines | 비동기 코드의 가독성 |
| Cache/Store | Redis 7.x | Lua Script, 고성능, 클러스터 지원 |
| Redis Client | Lettuce | Reactive 지원, 커넥션 풀링 |
| Monitoring | Micrometer + Prometheus | Spring Boot 표준, 풍부한 메트릭 |
| Testing | JUnit 5 + Testcontainers | 실제 Redis 환경 통합 테스트 |

---

## 2. System Architecture

### 2.1 Layer Architecture

```text
┌─────────────────────────────────────────────────────────┐
│                    Controller Layer                      │
│  RateLimitController: REST API 엔드포인트               │
│  - /check: Rate Limit 확인 및 토큰 소비                 │
│  - /remaining: 남은 한도 조회                           │
│  - /reset: Rate Limit 초기화                            │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                    Service Layer                         │
│  RateLimitService: 비즈니스 로직, 메트릭 수집           │
│  - 알고리즘 선택 (Strategy Pattern)                     │
│  - 메트릭 기록 (Micrometer)                             │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                   Algorithm Layer                        │
│  RateLimiter Interface                                   │
│  ├── TokenBucketRateLimiter                             │
│  └── SlidingWindowRateLimiter                           │
│                                                         │
│  Lua Script: 원자적 연산 보장                           │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                Infrastructure Layer                      │
│  Redis: 분산 상태 저장소                                │
│  - ReactiveStringRedisTemplate                          │
│  - Lettuce Connection Pool                              │
└─────────────────────────────────────────────────────────┘
```

### 2.2 Package Structure

```text
com.jsoonworld.ratelimiter
├── algorithm/           # Rate Limiting 알고리즘 구현체
│   ├── RateLimiter.kt          # 인터페이스
│   ├── TokenBucketRateLimiter.kt
│   └── SlidingWindowRateLimiter.kt
├── config/              # 설정 클래스
│   └── RedisConfig.kt
├── controller/          # REST API 엔드포인트
│   └── RateLimitController.kt
├── model/               # 데이터 모델
│   ├── RateLimitAlgorithm.kt   # Enum
│   └── RateLimitResult.kt      # 결과 DTO
└── service/             # 비즈니스 로직
    └── RateLimitService.kt
```

### 2.3 Core Interface

```kotlin
interface RateLimiter {
    suspend fun tryAcquire(key: String, permits: Long = 1): RateLimitResult
    suspend fun getRemainingLimit(key: String): Long
    suspend fun reset(key: String)
}
```

**설계 원칙**:
- `suspend` 함수로 비동기 처리
- `permits` 파라미터로 다중 토큰 소비 지원
- 모든 구현체가 동일한 인터페이스 준수

---

## 3. Algorithm Specifications

### 3.1 Token Bucket Algorithm

**개요**: 버킷에 일정 용량의 토큰을 저장하고, 요청마다 토큰을 소비하며, 일정 속도로 토큰을 리필한다.

**특징**:
- 버스트 트래픽 허용 (버킷이 가득 차 있을 때)
- 장기적으로 평균 처리율 제한
- 구현 복잡도: 중간

**파라미터**:

| Parameter | Default | Description |
|-----------|---------|-------------|
| capacity | 100 | 버킷 최대 용량 |
| refill_rate | 10/sec | 초당 토큰 리필 속도 |

**Lua Script**:

```lua
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

-- Initialize if not exists
if tokens == nil then
    tokens = capacity
    last_refill = now
end

-- Calculate tokens to add based on time passed
local time_passed = now - last_refill
local tokens_to_add = time_passed * refill_rate
tokens = math.min(capacity, tokens + tokens_to_add)

local allowed = 0
local remaining = tokens

if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
    remaining = tokens
end

-- Save state
redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
redis.call('EXPIRE', key, math.ceil(capacity / refill_rate) + 1)

return {allowed, math.floor(remaining), math.ceil((capacity - remaining) / refill_rate)}
```

**Redis Data Structure**:
```text
Key: rate_limiter:token_bucket:{client_key}
Type: Hash
Fields:
  - tokens: 현재 토큰 수
  - last_refill: 마지막 리필 타임스탬프
TTL: (capacity / refill_rate) + 1 seconds
```

### 3.2 Sliding Window Log Algorithm

**개요**: 각 요청의 타임스탬프를 저장하고, 윈도우 내 요청 수를 정확히 계산한다.

**특징**:
- 정확한 Rate Limiting (경계 문제 없음)
- 메모리 사용량이 높을 수 있음
- 구현 복잡도: 낮음

**파라미터**:

| Parameter | Default | Description |
|-----------|---------|-------------|
| window_size | 60 sec | 윈도우 크기 |
| max_requests | 100 | 윈도우 내 최대 요청 수 |

**Lua Script**:

```lua
local key = KEYS[1]
local window_size = tonumber(ARGV[1])
local max_requests = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

-- Remove expired entries
local window_start = now - window_size
redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

-- Count current requests in window
local current_count = redis.call('ZCARD', key)

local allowed = 0
local remaining = max_requests - current_count

if current_count + requested <= max_requests then
    -- Add new request(s)
    for i = 1, requested do
        redis.call('ZADD', key, now, now .. ':' .. i .. ':' .. math.random())
    end
    allowed = 1
    remaining = max_requests - current_count - requested
end

-- Set expiration
redis.call('EXPIRE', key, window_size + 1)

-- Calculate reset time based on oldest entry in the window
local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
local reset_time = window_size
if oldest and oldest[2] then
    reset_time = math.ceil(tonumber(oldest[2]) + window_size - now)
end

return {allowed, math.max(0, remaining), math.max(0, reset_time)}
```

**Redis Data Structure**:
```text
Key: rate_limiter:sliding_window:{client_key}
Type: Sorted Set
Score: 요청 타임스탬프
Member: {timestamp}:{index}:{random}
TTL: window_size + 1 seconds
```

### 3.3 Algorithm Comparison

| 기준 | Token Bucket | Sliding Window Log |
|------|--------------|-------------------|
| 정확도 | 높음 | 매우 높음 |
| 버스트 허용 | O | X |
| 메모리 사용 | 낮음 (2 fields) | 높음 (요청 수만큼) |
| 연산 복잡도 | O(1) | O(N) |
| 적합 상황 | 일반 API Rate Limiting | 정밀 제한 필요 시 |

---

## 4. Data Architecture

### 4.1 Redis Key Naming Convention

```text
rate_limiter:{algorithm}:{client_key}
```

**Examples**:
```text
rate_limiter:token_bucket:user:12345
rate_limiter:token_bucket:ip:192.168.1.1
rate_limiter:sliding_window:api_key:abc123
```

### 4.2 TTL Strategy

모든 키에 적절한 TTL을 설정하여 메모리 누수 방지:

| Algorithm | TTL Calculation |
|-----------|-----------------|
| Token Bucket | `(capacity / refill_rate) + 1` |
| Sliding Window | `window_size + 1` |

### 4.3 Data Models

```kotlin
data class RateLimitResult(
    val allowed: Boolean,
    val remaining: Long,
    val resetAfterSeconds: Long,
    val retryAfterSeconds: Long
)

enum class RateLimitAlgorithm {
    TOKEN_BUCKET,
    SLIDING_WINDOW,
    SLIDING_WINDOW_COUNTER,  // Not yet implemented
    FIXED_WINDOW,            // Not yet implemented
    LEAKY_BUCKET             // Not yet implemented
}
```

---

## 5. API Design

### 5.1 Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/rate-limit/check` | Rate Limit 확인 및 토큰 소비 |
| GET | `/api/v1/rate-limit/remaining` | 남은 한도 조회 (소비 없음) |
| DELETE | `/api/v1/rate-limit/reset` | Rate Limit 초기화 |

### 5.2 Request/Response

**Rate Limit Check**:

```bash
GET /api/v1/rate-limit/check?algorithm=TOKEN_BUCKET&key=user:123
```

**Success Response (200 OK)**:
```json
{
  "allowed": true,
  "key": "user:123",
  "algorithm": "TOKEN_BUCKET",
  "remaining": 95,
  "resetAfterSeconds": 10,
  "retryAfterSeconds": 0,
  "message": "Request allowed"
}
```

**Rate Limited Response (429 Too Many Requests)**:
```json
{
  "allowed": false,
  "key": "user:123",
  "algorithm": "TOKEN_BUCKET",
  "remaining": 0,
  "resetAfterSeconds": 5,
  "retryAfterSeconds": 5,
  "message": "Rate limit exceeded"
}
```

### 5.3 Response Headers

| Header | Description |
|--------|-------------|
| `X-RateLimit-Remaining` | 남은 요청 가능 횟수 |
| `X-RateLimit-Reset` | Unix epoch timestamp when limit resets |
| `Retry-After` | 재시도까지 대기 시간 (초, 429일 때만) |

### 5.4 Client Key Extraction

클라이언트 식별 우선순위:
1. `key` 쿼리 파라미터 (명시적 지정)
2. `X-Forwarded-For` 헤더 (프록시/로드밸런서 환경)
3. `X-Real-IP` 헤더
4. `remoteAddr` (직접 연결)

---

## 6. Resilience Patterns

### 6.1 Fail Open Policy

Redis 장애 시 요청을 허용하여 서비스 가용성 유지:

```kotlin
try {
    // Redis 연산
} catch (e: Exception) {
    logger.error("Error executing rate limit check for key: $key", e)
    // Fail open - allow request if Redis is unavailable
    RateLimitResult.allowed(DEFAULT_CAPACITY, 0)
}
```

**Rationale**:
- Rate Limiter 장애로 전체 서비스가 다운되면 안 됨
- 일시적 Redis 장애 시 서비스 연속성 보장
- 장애 상황은 메트릭/로그로 감지하여 대응

### 6.2 Atomic Operations

Lua Script를 사용하여 Race Condition 방지:

```text
문제: 분산 환경에서 동시 요청 시 한도 초과
해결: Redis Lua Script로 읽기-계산-쓰기를 원자적으로 처리
```

### 6.3 Connection Management

Lettuce 커넥션 풀을 통한 효율적인 Redis 연결 관리:

```kotlin
@Configuration
class RedisConfig {
    @Bean
    fun reactiveRedisConnectionFactory(): ReactiveRedisConnectionFactory {
        val config = RedisStandaloneConfiguration(host, port)
        return LettuceConnectionFactory(config)
    }
}
```

---

## 7. Observability

### 7.1 Metrics

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `rate_limiter.check` | Timer | algorithm, allowed | Rate Limit 체크 소요 시간 |
| `rate_limiter.requests` | Counter | algorithm, allowed | 요청 수 (허용/거부) |

### 7.2 Prometheus Integration

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: prometheus, health, info
  metrics:
    tags:
      application: distributed-rate-limiter
```

**Endpoint**: `GET /actuator/prometheus`

### 7.3 Sample Queries

```promql
# 분당 거부된 요청 수
sum(rate(rate_limiter_requests_total{allowed="false"}[1m])) by (algorithm)

# Rate Limit 체크 p99 응답 시간
histogram_quantile(0.99, rate(rate_limiter_check_seconds_bucket[5m]))

# 알고리즘별 요청 비율
sum(rate(rate_limiter_requests_total[5m])) by (algorithm)
```

### 7.4 Logging

```kotlin
private val logger = LoggerFactory.getLogger(javaClass)

// Debug: 정상 흐름
logger.debug("Request allowed for key: $key, remaining: $remaining")

// Warn: 대체 동작
logger.warn("Algorithm $algorithm not implemented, falling back to TOKEN_BUCKET")

// Error: 예외 발생
logger.error("Error executing rate limit check for key: $key", e)
```

---

## 8. Non-Functional Requirements

### 8.1 Performance

| Metric | Target | Measurement |
|--------|--------|-------------|
| Rate Limit Check Latency | p99 < 10ms | Prometheus histogram |
| Redis Round-Trip | < 5ms | Network monitoring |
| Throughput | 10,000+ checks/sec/instance | Load testing |

### 8.2 Scalability

- **Horizontal Scaling**: 서버 인스턴스 추가 시 설정 변경 없이 동작
- **Redis Cluster**: 대규모 트래픽 시 Redis Cluster로 확장 가능
- **Key Distribution**: 클라이언트 키 기반 자연스러운 샤딩

### 8.3 Availability

| Scenario | Behavior |
|----------|----------|
| Redis 일시 장애 | Fail Open - 요청 허용 |
| Redis 영구 장애 | 로그/알림 후 요청 허용 |
| Rate Limiter 서버 장애 | 로드밸런서가 다른 인스턴스로 라우팅 |

### 8.4 Security Considerations

- **Key Injection**: 클라이언트가 임의의 키를 지정하지 못하도록 서버에서 검증
- **Redis 접근 제어**: 네트워크 격리, 인증 설정
- **Rate Limit Bypass**: X-Forwarded-For 스푸핑 방지 (신뢰할 수 있는 프록시만 허용)

---

*Last Updated: 2026-02-04*

# Distributed Rate Limiter

Redis 기반 분산 환경에서 동작하는 Rate Limiter 구현체입니다.

## Features

- **다양한 알고리즘 지원**
  - Token Bucket: 버스트 트래픽 허용, 일정 속도로 토큰 리필
  - Sliding Window Log: 정확한 rate limiting, 경계 문제 없음
  - (예정) Fixed Window Counter
  - (예정) Sliding Window Counter
  - (예정) Leaky Bucket

- **분산 환경 지원**
  - Redis를 사용한 분산 상태 저장
  - Lua 스크립트를 통한 원자적 연산
  - 여러 인스턴스에서 동일한 rate limit 공유

- **모니터링**
  - Prometheus 메트릭 연동
  - Grafana 대시보드 지원
  - Spring Actuator 엔드포인트

## Tech Stack

- Kotlin 1.9
- Spring Boot 3.2
- Spring Data Redis (Reactive)
- Coroutines
- Testcontainers
- Docker

## Quick Start

### 1. Redis 실행

```bash
cd docker
docker-compose up -d redis
```

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 3. API 테스트

```bash
# Rate limit 체크 (Token Bucket)
curl http://localhost:8080/api/v1/rate-limit/check

# Rate limit 체크 (Sliding Window)
curl "http://localhost:8080/api/v1/rate-limit/check?algorithm=SLIDING_WINDOW_LOG"

# 남은 한도 확인
curl http://localhost:8080/api/v1/rate-limit/remaining

# Rate limit 리셋
curl -X DELETE "http://localhost:8080/api/v1/rate-limit/reset?key=127.0.0.1"
```

## API Reference

### Check Rate Limit

```
GET /api/v1/rate-limit/check
```

**Parameters:**
- `algorithm` (optional): `TOKEN_BUCKET` | `SLIDING_WINDOW_LOG` (default: `TOKEN_BUCKET`)
- `key` (optional): 클라이언트 식별자 (기본값: 요청 IP)

**Response Headers:**
- `X-RateLimit-Remaining`: 남은 요청 수
- `X-RateLimit-Reset`: 리셋까지 남은 시간(초)
- `Retry-After`: 재시도까지 대기 시간(초) - 429 응답 시

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
│  │ ● │ ● │ ● │ ● │ ● │   │   │   │ │  ← 토큰
│  └───┴───┴───┴───┴───┴───┴───┴───┘ │
│         ↑                           │
│    Refill: 10 tokens/sec            │
└─────────────────────────────────────┘
```

- 버킷에 최대 `capacity`개의 토큰 저장
- 요청마다 토큰 1개 소비
- 초당 `refill_rate`개의 토큰 보충
- 버킷이 가득 차면 버스트 트래픽 허용

### Sliding Window Log

```
     Window (60 seconds)
├────────────────────────────────────┤
│  ●  ●     ●  ● ●    ●   ●  ●  ●   │  ← 요청 타임스탬프
├────────────────────────────────────┤
                                    now
```

- 각 요청의 타임스탬프를 Redis Sorted Set에 저장
- 윈도우 내 요청 수를 정확하게 계산
- 경계 문제 없이 정확한 rate limiting

## Configuration

```yaml
rate-limiter:
  default:
    algorithm: TOKEN_BUCKET
    capacity: 100          # 최대 토큰/요청 수
    refill-rate: 10        # 초당 리필 토큰 수
    window-size: 60        # 윈도우 크기 (초)
```

## Monitoring

### Prometheus Metrics

- `rate_limiter_requests_total`: 총 요청 수
- `rate_limiter_check_seconds`: 체크 소요 시간

### Endpoints

- `/actuator/health`: 헬스 체크
- `/actuator/prometheus`: Prometheus 메트릭
- `/actuator/metrics`: Spring 메트릭

## Project Structure

```
src/main/kotlin/com/jsoonworld/ratelimiter/
├── algorithm/
│   ├── RateLimiter.kt              # 인터페이스
│   ├── TokenBucketRateLimiter.kt   # Token Bucket 구현
│   └── SlidingWindowRateLimiter.kt # Sliding Window 구현
├── config/
│   └── RedisConfig.kt              # Redis 설정
├── controller/
│   └── RateLimitController.kt      # REST API
├── model/
│   ├── RateLimitAlgorithm.kt       # 알고리즘 enum
│   └── RateLimitResult.kt          # 결과 DTO
├── service/
│   └── RateLimitService.kt         # 비즈니스 로직
└── RateLimiterApplication.kt       # 메인 클래스
```

## Roadmap

- [ ] Fixed Window Counter 알고리즘
- [ ] Sliding Window Counter 알고리즘 (하이브리드)
- [ ] Leaky Bucket 알고리즘
- [ ] 설정 기반 동적 rate limit
- [ ] 클라이언트별 커스텀 한도
- [ ] Redis Cluster 지원
- [ ] Spring AOP 기반 어노테이션
- [ ] SDK/라이브러리 형태로 배포

## License

MIT License

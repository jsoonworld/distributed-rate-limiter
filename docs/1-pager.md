# Distributed Rate Limiter - Executive Summary

> **Version**: 1.0 | **Status**: Draft | **Last Updated**: 2026-02-04
>
> 상세 기술 명세는 [Technical Specification](./tech-spec.md) 참조

---

## 한 줄 요약

**분산 환경에서 안정적으로 동작하는 Redis 기반 Rate Limiter**

---

## 해결하려는 문제

1. **단일 서버 Rate Limiter의 한계** - 서버 확장 시 각 인스턴스가 독립적으로 요청 수를 관리하여 전체 한도 초과
2. **트래픽 폭주로 인한 서비스 장애** - API 남용, DDoS, 버그로 인한 과다 요청으로 서비스 다운
3. **Rate Limiting 구현 복잡성** - 알고리즘 선택, 분산 동기화, 장애 대응 등 고려사항 과다

---

## 목표 사용자

| 사용자 | 니즈 |
|--------|------|
| 백엔드 개발자 | 간단한 API 호출로 Rate Limiting 적용 |
| DevOps 엔지니어 | 실시간 메트릭으로 트래픽 모니터링 |
| 시스템 아키텍트 | 용도에 맞는 알고리즘 선택 |

---

## 핵심 기능

| 기능 | 설명 |
|------|------|
| **Token Bucket** | 버스트 트래픽 허용, 평균 처리율 제어 |
| **Sliding Window Log** | 정확한 요청 수 제한, 경계 문제 없음 |
| **분산 동기화** | Redis 기반 다중 서버 간 상태 공유 |
| **Fail Open** | Redis 장애 시 서비스 가용성 유지 |
| **Prometheus 메트릭** | 실시간 모니터링, 알림 연동 |

---

## 성공 기준

| 지표 | 목표 |
|------|------|
| 정확도 | 분산 환경에서 요청 한도 오차 < 1% |
| 응답 시간 | p99 < 10ms (Rate Limit 체크) |
| 가용성 | Redis 장애 시에도 서비스 정상 동작 |
| 확장성 | 수평 확장 시 설정 변경 없이 동작 |

---

## 지원 알고리즘

| 알고리즘 | 특징 | 적합한 상황 |
|----------|------|-------------|
| **Token Bucket** | 버스트 허용, 평균 속도 제한 | API Rate Limiting, 일반적 사용 |
| **Sliding Window Log** | 정확한 제한, 높은 메모리 사용 | 정밀한 제한이 필요한 경우 |
| Fixed Window | 구현 단순, 경계 문제 존재 | 예정 |
| Leaky Bucket | 일정 속도 출력, 버스트 불허 | 예정 |

---

## 범위 제외

사용자별 동적 한도 설정, 멀티 Redis 클러스터, 분산 트랜잭션은 현재 범위 외 (향후 확장 예정)

---

## 연동 방식

```
[클라이언트 서비스]
       │
       │ REST API / Library
       ▼
┌─────────────────────────┐
│  Distributed Rate       │
│  Limiter                │ ──Lua Script──▶ [Redis Cluster]
│  (Spring Boot)          │
└─────────────────────────┘
       │
       │ Prometheus Metrics
       ▼
[Grafana Dashboard]
```

- **요청**: REST API 또는 라이브러리 직접 호출
- **응답**: 허용 여부, 남은 한도, 재시도 시간
- **모니터링**: `/actuator/prometheus` 엔드포인트

### API 예시

```bash
GET /api/v1/rate-limit/check?algorithm=TOKEN_BUCKET&key=user:123
```

```json
{
  "allowed": true,
  "key": "user:123",
  "remainingTokens": 95,
  "resetTimeSeconds": 10,
  "message": "Request allowed"
}
```

---

*Last Updated: 2026-02-04*

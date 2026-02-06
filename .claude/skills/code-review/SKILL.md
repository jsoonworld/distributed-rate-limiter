---
name: code-review
description: Process PR review comments from any reviewer - analyze, decide, fix, and reply politely
allowed-tools: Bash, Read, Edit, Write, Grep, Glob, Task
---

# Code Review Processing Skill

## Overview
PR 리뷰 코멘트를 처리합니다. 리뷰어가 누구든 (사람, CodeRabbit, 기타 봇) 동일하게 적용됩니다.

각 코멘트에 대해:
1. 기술적 근거를 바탕으로 분석
2. 수용/거절 결정 (명확한 근거 필수)
3. 수용 시: 병렬 에이전트로 수정 (독립적인 경우)
4. 각 코멘트에 개별적으로 **공손하고 친절하게** 답변

## Tone Guidelines (말투 가이드)

**핵심 원칙: 존중, 감사, 겸손**

- 항상 리뷰어의 시간과 노력에 감사 표현
- 거절 시에도 공손하고 건설적으로
- "틀렸다"가 아닌 "다른 관점"으로 표현
- 이모지는 사용하지 않음 (프로페셔널 유지)

## Workflow

### Step 1: Fetch PR Information

```bash
PR_NUMBER=$ARGUMENTS
if [ -z "$PR_NUMBER" ]; then
  PR_NUMBER=$(gh pr view --json number -q '.number' 2>/dev/null)
fi
```

### Step 2: Fetch All Review Comments

```bash
# 모든 리뷰 코멘트 가져오기
gh api repos/{owner}/{repo}/pulls/{pr_number}/comments --paginate

# 또는 리뷰 자체를 가져오기
gh api repos/{owner}/{repo}/pulls/{pr_number}/reviews --paginate
```

### Step 3: Analyze Each Comment

#### 3.1 Analysis Phase
각 코멘트 분석:
- **파일 & 라인**: 어떤 코드가 대상인가?
- **카테고리**: 버그, 성능, 보안, 스타일, 베스트 프랙티스?
- **심각도**: Critical, Major, Minor, Suggestion?
- **현재 코드**: 현재 구현의 의도는?
- **제안 내용**: 리뷰어가 원하는 변경은?
- **영향 평가**: 수용/거절 시 영향은?

#### 3.2 Decision Phase

**수용 (ACCEPT) 기준:**
- 실제 버그나 보안 이슈 수정
- 과도한 엔지니어링 없이 품질 향상
- 프로젝트 컨벤션과 일치 (CLAUDE.md 확인)
- 측정 가능한 성능 개선
- 복잡도 감소 또는 가독성 향상

**거절 (REJECT) 기준:**
- 실질적 이점 없는 과도한 엔지니어링
- CLAUDE.md 규칙과 충돌
- 근거 없는 조기 최적화
- 기존 기능 손상 가능성
- 기술적 근거 없는 스타일 선호
- 이미 다른 곳에서 처리됨

#### 3.3 Implementation Phase (if ACCEPT) - 병렬 실행 필수

**독립적인 수정은 반드시 병렬로 실행:**

1. 모든 ACCEPT 결정 수집
2. 파일별 그룹화 (같은 파일 → 순차, 다른 파일 → 병렬)
3. 하나의 메시지에서 모든 병렬 에이전트 실행

```text
예시: 4개 파일 수정 필요

실행 방식:
- Task Agent A: TokenBucketRateLimiter.kt 수정
- Task Agent B: SlidingWindowRateLimiter.kt 수정
- Task Agent C: RateLimiterService.kt 수정
- Task Agent D: RateLimitController.kt 수정
(모두 동시에 실행)
```

#### 3.4 Reply Phase

각 코멘트에 개별 답변:

```bash
gh api repos/{owner}/{repo}/pulls/{pr_number}/comments/{comment_id}/replies \
  -X POST \
  -f body="REPLY_CONTENT"
```

---

## Reply Templates (공손한 말투)

### ACCEPT Template
```markdown
리뷰 감사합니다! 좋은 지적이라고 생각합니다.

**수용합니다**

말씀해주신 부분이 맞습니다. [기술적 이유 설명]

**변경 사항:**
- `path/to/file.kt` (line X-Y): [구체적인 변경 내용]

**검증:**
- 관련 테스트 통과 확인했습니다.

피드백 감사드립니다!
```

### REJECT Template (공손하게)
```markdown
리뷰 감사합니다! 의견 주신 부분 신중하게 검토했습니다.

**현재 구현을 유지하려고 합니다**

말씀하신 부분 충분히 이해하지만, 현재 구현을 유지하는 것이 좋을 것 같습니다.

**이유:**
- [기술적 근거 1]
- [기술적 근거 2]

**참고:**
- 프로젝트 컨벤션에서 [관련 규칙] 을 따르고 있습니다.
- [추가 맥락이나 코드 예시]

혹시 제가 놓친 부분이 있다면 말씀해 주세요. 다시 검토하겠습니다!
```

### PARTIAL ACCEPT Template
```markdown
리뷰 감사합니다! 좋은 의견들이 많았습니다.

**일부 수용합니다**

**수용한 부분:**
- [수용 내용]: 좋은 지적이라 반영했습니다.

**유지한 부분:**
- [유지 내용]: [유지 이유 설명]

**변경 사항:**
- `path/to/file.kt`: [구체적인 변경]

추가 의견 있으시면 편하게 말씀해 주세요!
```

### QUESTION Template (추가 정보 필요 시)
```markdown
리뷰 감사합니다!

말씀하신 부분에 대해 한 가지 여쭤봐도 될까요?

[구체적인 질문]

의도하신 바를 정확히 이해하고 반영하고 싶어서 여쭤봅니다.
답변 주시면 바로 검토하겠습니다!
```

### ERROR Template (수정 실패 시)
```markdown
리뷰 감사합니다! 지적하신 부분 동의합니다.

**수용하려 했으나 적용 중 문제가 발생했습니다**

**문제:**
- [발생한 에러 설명]

**상황:**
- [시도한 내용]

수동 확인이 필요할 것 같습니다. 확인 후 다시 업데이트하겠습니다.
불편을 드려 죄송합니다!
```

---

## Execution Rules

1. **개별 답변** - 각 코멘트에 하나씩 답변 (배치 X)
2. **근거 필수** - 코드, 테스트, 문서 참조
3. **CLAUDE.md 우선** - 프로젝트 컨벤션이 일반 제안보다 우선
4. **테스트 필수** - 수정 후 반드시 테스트 실행
5. **병렬 실행** - 독립적 수정은 동시에
6. **공손한 말투** - 항상 감사와 존중 표현

## Project-Specific Rules (Kotlin/Redis Rate Limiter)

- **Coroutines**: suspend 함수 패턴 유지, runBlocking은 테스트에서만 사용
- **Redis Lua Script**: 원자적 연산을 위한 Lua 스크립트 패턴 유지
- **Fail Open**: Redis 장애 시 요청 허용 정책 준수
- **키 네이밍**: `rate_limiter:{algorithm}:{key}` 형식 유지

## Command Usage

```bash
# PR 번호로 실행
/code-review 42

# 현재 브랜치 PR 자동 감지
/code-review
```

## Example Session

```text
Processing PR #42: "feat: Add leaky bucket rate limiter"

Found 5 review comments:

[1/5] @reviewer1 on TokenBucketRateLimiter.kt:45
  "awaitSingleOrNull 사용을 권장합니다"
  → ACCEPT: null 안전성 향상
  → Reply: "리뷰 감사합니다! 좋은 지적이라고 생각합니다..."

[2/5] @coderabbitai on RateLimitController.kt:89
  "@Validated 추가 필요"
  → REJECT: Service 레이어에서 이미 검증
  → Reply: "리뷰 감사합니다! 의견 주신 부분 신중하게 검토했습니다..."

[3/5] @reviewer2 on SlidingWindowRateLimiter.kt:23
  "TTL 설정 누락"
  → ACCEPT: Redis 메모리 관리 필수
  → Reply: "리뷰 감사합니다! 좋은 지적이라고 생각합니다..."

Parallel execution for ACCEPT items (1, 3):
- Agent A: TokenBucketRateLimiter.kt fix
- Agent B: SlidingWindowRateLimiter.kt fix

Summary:
- Accepted: 2
- Rejected: 1
- All replies posted individually
- Tests: PASSED
```

## Important Notes

- **TDD 준수**: 새로운 동작이 필요하면 테스트 먼저
- **금지 패턴**: `.block()`, 빈 catch 블록, 예외 무시 절대 금지
- **Reactive 유지**: WebFlux 코드 수정 시 reactive 패턴 유지
- **중복 방지**: 이미 답변한 코멘트는 건너뛰기

---
name: coderabbit-review
description: Process CodeRabbit PR review comments - analyze, decide, fix, and reply individually
disable-model-invocation: true
allowed-tools: Bash, Read, Edit, Write, Grep, Glob, Task
---

# CodeRabbit Review Processing Skill

## Overview
This skill processes CodeRabbit review comments on a PR. For each comment:
1. Analyze the suggestion with technical reasoning
2. Decide: ACCEPT or REJECT with justification
3. If ACCEPT: Fix the issue (use parallel agents for independent fixes)
4. Reply to each comment individually with the decision and rationale

## Workflow

### Step 1: Fetch PR Information
```bash
# Get PR number from current branch or argument
PR_NUMBER=$ARGUMENTS

# If no argument, get from current branch
if [ -z "$PR_NUMBER" ]; then
  PR_NUMBER=$(gh pr view --json number -q '.number' 2>/dev/null)
fi
```

### Step 2: Fetch All Review Comments
Use `gh api` to get all review comments:
```bash
gh api repos/{owner}/{repo}/pulls/{pr_number}/comments --paginate
```

Filter for CodeRabbit comments (author: `coderabbitai[bot]` or contains CodeRabbit signature).

### Step 3: For Each Comment, Execute This Process

#### 3.1 Analysis Phase
For each comment, analyze:
- **File & Line**: Which file and code section is affected?
- **Category**: Bug fix, Performance, Security, Style, Best Practice, Refactoring?
- **Severity**: Critical, Major, Minor, Suggestion?
- **Current Code**: What does the current implementation do?
- **Suggested Change**: What is CodeRabbit suggesting?
- **Impact Assessment**: What are the implications of accepting/rejecting?

#### 3.2 Decision Phase
Make a decision with clear reasoning:

**ACCEPT if:**
- Fixes a genuine bug or security issue
- Improves code quality without over-engineering
- Aligns with project conventions (check CLAUDE.md)
- Performance improvement with measurable benefit
- Reduces complexity or improves readability

**REJECT if:**
- Over-engineering for no practical benefit
- Contradicts project conventions or CLAUDE.md rules
- Premature optimization without evidence
- Would break existing functionality
- Stylistic preference without technical merit
- Already handled elsewhere in the codebase

#### 3.3 Implementation Phase (if ACCEPT) - PARALLEL EXECUTION MANDATORY

**CRITICAL: All independent fixes MUST be executed in parallel using Task agents.**

**Step 1: Analyze all ACCEPT decisions**

```text
ACCEPT comments:
- Comment #1: TokenBucketRateLimiter.kt:45 (null check)
- Comment #3: RateLimitController.kt:89 (validation)
- Comment #5: SlidingWindowRateLimiter.kt:23 (error handling)
```

**Step 2: Group by independence**
- Same file? → Sequential
- Different files, no dependency? → PARALLEL

**Step 3: Launch ALL parallel agents in ONE message**

```text
Launch simultaneously:
- Task Agent A: Fix TokenBucketRateLimiter.kt (Comment #1)
- Task Agent B: Fix RateLimitController.kt (Comment #3)
- Task Agent C: Fix SlidingWindowRateLimiter.kt (Comment #5)
```

**Implementation:**
- Use `subagent_type: general-purpose` for each fix
- Each agent: Read file → Apply fix → Run relevant test
- ALL Task tool calls in SINGLE response (not sequential)
- Wait for ALL agents to complete before proceeding to replies

**Example: 5 ACCEPT comments on different files**

```text
WRONG (Sequential - FORBIDDEN):
  Fix #1 → Wait → Fix #2 → Wait → Fix #3 → Wait → Fix #4 → Wait → Fix #5
  Total time: 5x

CORRECT (Parallel - REQUIRED):
  Fix #1, #2, #3, #4, #5 simultaneously (5 Task calls in ONE message)
  Total time: 1x
```

#### 3.4 Reply Phase

Reply to EACH comment individually using:

```bash
gh api repos/{owner}/{repo}/pulls/{pr_number}/comments/{comment_id}/replies \
  -method POST \
  -f body="REPLY_CONTENT"
```

### Reply Templates (공손한 말투)

#### ACCEPT Template
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

#### REJECT Template
```markdown
리뷰 감사합니다! 의견 주신 부분 신중하게 검토했습니다.

**현재 구현을 유지하려고 합니다**

말씀하신 부분 충분히 이해하지만, 현재 구현을 유지하는 것이 좋을 것 같습니다.

**이유:**
- [기술적 근거 1]
- [기술적 근거 2]

**참고:**
- 프로젝트 컨벤션에서 [관련 규칙]을 따르고 있습니다.

혹시 제가 놓친 부분이 있다면 말씀해 주세요. 다시 검토하겠습니다!
```

#### PARTIAL ACCEPT Template
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

## Project-Specific Rules (Kotlin/Redis Rate Limiter)

- **Coroutines**: suspend 함수 패턴 유지, awaitSingle/awaitSingleOrNull 사용
- **Redis Lua Script**: 원자적 연산을 위한 Lua 스크립트 패턴 유지
- **Fail Open**: Redis 장애 시 요청 허용 정책 준수
- **키 네이밍**: `rate_limiter:{algorithm}:{key}` 형식 유지
- **로깅**: LoggerFactory.getLogger(javaClass) 패턴 사용

## Execution Rules

1. **Never batch replies** - Reply to each comment individually after processing
2. **Always provide evidence** - Reference specific code, tests, or documentation
3. **Check CLAUDE.md first** - Project conventions override generic suggestions
4. **Run tests after each fix** - Never commit broken code
5. **Use parallel agents** - For independent fixes across different files
6. **Track progress** - Report which comments are processed

## Command Usage

```bash
# Process PR by number
/coderabbit-review 42

# Process current branch's PR
/coderabbit-review
```

## Example Session

```text
Processing PR #42: "feat: Add leaky bucket rate limiter"

Found 5 CodeRabbit comments:

[1/5] Comment #123456 on TokenBucketRateLimiter.kt:45
  Suggestion: "Consider using awaitSingleOrNull instead of awaitSingle"
  Decision: ACCEPT
  Reason: Improves null safety, aligns with Kotlin patterns
  Action: Fixing...
  Reply: Posted

[2/5] Comment #123457 on RateLimitController.kt:89
  Suggestion: "Add @Validated annotation"
  Decision: REJECT
  Reason: Already validated at service layer per CLAUDE.md architecture
  Reply: Posted

[3/5] Comment #123458 on SlidingWindowRateLimiter.kt:23
  Suggestion: "Add TTL to Redis key"
  Decision: ACCEPT
  Reason: Memory management for Redis keys
  Action: Fixing...
  Reply: Posted

[4/5] Comment #123459 on RateLimiterTest.kt:67
  Suggestion: "Use shouldBe instead of assertEquals"
  Decision: ACCEPT
  Reason: Kotest provides better error messages
  Action: Fixing...
  Reply: Posted

[5/5] Comment #123460 on Application.kt:12
  Suggestion: "Consider adding startup logging"
  Decision: REJECT
  Reason: Over-engineering, Spring Boot already logs startup info
  Reply: Posted

Summary:
- Accepted: 3
- Rejected: 2
- All replies posted individually
- Tests: PASSED
```

## Important Notes

- **TDD Compliance**: If a fix requires new behavior, write test first (Red-Green-Refactor)
- **Forbidden Patterns**: Never introduce `.block()`, empty catch blocks, or swallowed exceptions
- **Reactive Code**: Maintain reactive patterns when modifying WebFlux code
- **Idempotency**: Each comment reply should be idempotent (check if already replied)

## Error Handling

If a fix fails:
1. Do NOT reply with success
2. Report the error in the reply
3. Request human review
4. Continue processing other comments

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

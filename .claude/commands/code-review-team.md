---
description: Parallel code review with agent team (Security, Performance, Testing)
---

# Code Review Agent Team

Create an agent team to review the recent PR changes.
Spawn three specialized reviewers:

- **Security reviewer**: Focus on security vulnerabilities, authentication, authorization, input validation, data exposure risks. For this project, pay special attention to Redis injection, Lua script safety, and rate limiter bypass attacks.
- **Performance reviewer**: Check for optimization opportunities, memory leaks, inefficient algorithms, Redis call counts, TTL settings, and unnecessary memory allocations.
- **Test coverage reviewer**: Validate test coverage meets 80% minimum (90%+ for algorithm layer), edge cases, error handling, Fail Open policy testing, and test quality.

Each reviewer should:
1. Work independently and thoroughly
2. Report findings with severity levels (Critical / Major / Minor / Info)
3. Share findings with other team members
4. Discuss and reach consensus on critical issues
5. Verify against the Code Review Checklist in CLAUDE.md

Require plan approval before implementation starts.

Output format:
```
## Review Summary
### Security: [PASS/WARN/FAIL]
### Performance: [PASS/WARN/FAIL]
### Test Coverage: [PASS/WARN/FAIL]

### Critical Findings
- ...

### Recommendations
- ...
```

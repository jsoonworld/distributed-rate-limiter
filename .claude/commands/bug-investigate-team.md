---
description: Bug investigation with competing hypotheses using agent team
---

# Bug Investigation Agent Team

$ARGUMENTS

Spawn 5 agent teammates to investigate different root cause hypotheses:

- **Teammate 1 (Network/Connection)**: Investigate Redis connection issues, timeouts, connection pool exhaustion, network latency
- **Teammate 2 (State Management)**: Investigate race conditions in rate limiter state, Lua script atomicity issues, key expiration timing
- **Teammate 3 (Configuration)**: Investigate misconfiguration in Spring Boot properties, Redis settings, algorithm parameters
- **Teammate 4 (Timing/Race Condition)**: Investigate coroutine concurrency issues, suspend function race conditions, token bucket timing edge cases
- **Teammate 5 (Devil's Advocate)**: Challenge other theories, look for unexpected root causes, check recent code changes

Instructions:
1. Each teammate independently investigates their hypothesis
2. Teammates should actively try to disprove each other's theories
3. Share findings and evidence with the group
4. Reach consensus on the most likely root cause
5. Propose a fix with test case

Output format:
```
## Investigation Results
### Most Likely Root Cause: [description]
### Evidence: [supporting data]
### Proposed Fix: [code changes]
### Test Case: [failing test that reproduces the bug]
```

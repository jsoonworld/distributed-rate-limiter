---
description: Research a topic using subagent (lightweight, single task)
---

# Research Subagent

$ARGUMENTS

Use a subagent (NOT an agent team) to research the given topic.
This is a lightweight, focused task suitable for:

- Library/framework comparison and recommendation
- API documentation review and summary
- Architecture pattern research
- Performance benchmark analysis
- Best practice research for rate limiting, Redis, Kotlin coroutines

The subagent should:
1. Research the topic thoroughly
2. Provide a structured comparison if applicable
3. Give a clear recommendation with rationale
4. Consider how findings apply to this project (Redis-based distributed rate limiter)

Output format:
```
## Research: [Topic]
### Summary
[Brief overview]

### Findings
[Detailed analysis / comparison table]

### Recommendation
[Clear recommendation with rationale]

### Applicability to This Project
[How to apply findings to the rate limiter project]
```

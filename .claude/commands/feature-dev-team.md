---
description: Parallel feature development with agent team
---

# Feature Development Agent Team

$ARGUMENTS

Create an agent team with 4 teammates, each owning different layers in parallel:

- **Algorithm Developer**: Implement the core rate limiting algorithm
  - Create `RateLimiter` interface implementation
  - Write Lua scripts for atomic Redis operations
  - Follow Token Bucket / Sliding Window patterns
  - Ensure Fail Open policy on Redis failure

- **API Developer**: Implement controller and service layer
  - Create REST controller endpoints
  - Implement service layer with metrics collection (Micrometer)
  - Use suspend functions for all async operations
  - Add proper logging at each level

- **Testing Engineer**: Write comprehensive test coverage
  - Follow TDD (Red-Green-Refactor cycle)
  - Unit tests with MockK
  - Integration tests with Testcontainers (Redis)
  - Target 80%+ overall, 90%+ algorithm layer coverage

- **DevOps Engineer**: Handle configuration and infrastructure
  - Update Docker Compose if needed
  - Add Prometheus metrics configuration
  - Update application.yml properties
  - Update CI/CD pipeline if needed

Requirements:
1. Each teammate owns their module with clear boundaries (no overlapping files)
2. Use the shared task list to coordinate work
3. All code must follow Kotlin coding conventions in CLAUDE.md
4. Require plan approval for major architectural decisions
5. Use Sonnet model for all teammates for cost efficiency

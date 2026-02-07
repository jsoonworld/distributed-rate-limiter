---
description: Parallel refactoring across modules with agent team
---

# Parallel Refactoring Agent Team

$ARGUMENTS

Create a team with teammates to refactor modules in parallel.
Assign one module per teammate to avoid file conflicts.

For this project, typical module boundaries are:
- `algorithm/` - Rate limiter algorithm implementations
- `config/` - Spring configuration classes
- `controller/` - REST API controllers
- `service/` - Business logic services
- `model/` - Data classes and enums
- `repository/` - Redis repository layer

Each teammate should:
1. Analyze their assigned module for code smells and improvement opportunities
2. Create a detailed refactoring plan (requires approval)
3. Implement refactoring with backward compatibility
4. Ensure all existing tests still pass
5. Add/update tests if behavior changes
6. Document changes and rationale

Constraints:
- Follow Kotlin coding conventions from CLAUDE.md
- Use suspend functions, no `.block()` in production code
- Maintain test coverage >= 80%
- Run `./gradlew test` after each refactoring step
- Prefer `val` over `var` for immutability

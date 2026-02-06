---
description: TDD Refactor Phase - Improve code while keeping tests green
---

# REFACTOR Phase - Clean Up

You are in the REFACTOR phase of TDD. Your task:

1. **Improve the code** without changing behavior:
   - Remove duplication
   - Improve naming
   - Extract methods/classes if needed
   - Apply design patterns where appropriate
   - Follow SOLID principles

2. **Run tests after each change**:
```bash
./gradlew test
```

3. **Tests MUST stay green** - If any test fails, revert the change

Refactoring checklist:
- [ ] Is the code readable?
- [ ] Are names descriptive?
- [ ] Is there any duplication?
- [ ] Are methods/classes small and focused?
- [ ] Does it follow clean architecture?
- [ ] Is error handling consistent?

After refactoring, the cycle is complete. Start a new cycle with /red for the next feature.

---
description: TDD Green Phase - Write minimal code to pass the test
---

# GREEN Phase - Make Test Pass

You are in the GREEN phase of TDD. Your task:

1. **Write the MINIMUM code** to make the failing test pass
   - Don't over-engineer
   - Don't add extra features
   - Don't optimize prematurely
   - Just make it work

2. **Run the test**:
```bash
./gradlew test --tests "*[TestClassName]*"
```

3. **The test MUST pass** - If it still fails, fix the implementation

4. **Run all tests** to ensure nothing broke:
```bash
./gradlew test
```

Remember:
- Reactive code: Use Mono/Flux properly
- No .block() in production code
- Follow existing patterns in the codebase

After all tests pass, proceed to REFACTOR phase with /refactor command.

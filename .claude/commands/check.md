---
description: Run all quality checks before commit/push
---

# Quality Check

Run comprehensive quality checks:

```bash
./gradlew clean build test jacocoTestReport
```

Verify:
1. **Build succeeds** - No compilation errors
2. **All tests pass** - Zero failures
3. **Coverage >= 80%** - Check build/reports/jacoco/test/html/index.html

If any check fails, fix before committing.

After all checks pass, you're ready to commit.

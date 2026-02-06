---
description: Run tests with coverage report
---

Run tests with JaCoCo coverage:

```bash
./gradlew test jacocoTestReport
```

After running, check the coverage report at:
- HTML Report: build/reports/jacoco/test/html/index.html
- XML Report: build/reports/jacoco/test/jacocoTestReport.xml

Report the coverage percentages for:
- Line coverage
- Branch coverage
- Class coverage

Target: Minimum 80% line coverage

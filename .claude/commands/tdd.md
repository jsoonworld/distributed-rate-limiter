---
description: TDD cycle helper - guides through Red-Green-Refactor
---

# TDD Cycle Guide

Follow the Red-Green-Refactor cycle:

1. **RED Phase**: Write a failing test first
   - Create test file if not exists
   - Write the test that describes expected behavior
   - Run `./gradlew test` to confirm it fails

2. **GREEN Phase**: Write minimal code to pass
   - Implement just enough to make the test pass
   - Run `./gradlew test` to confirm it passes

3. **REFACTOR Phase**: Improve the code
   - Clean up duplication
   - Improve naming
   - Run `./gradlew test` to ensure tests still pass

Ask the user which phase they want to work on.

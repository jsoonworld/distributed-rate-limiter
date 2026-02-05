# CLAUDE.md

This file provides guidelines for Claude Code when working on this project.

## Language Settings

**IMPORTANT: Use English for all outputs including:**
- Commit messages
- PR titles and descriptions
- Code comments
- Documentation
- README files
- Log messages

## Project Context

A Redis-based distributed Rate Limiter supporting Token Bucket and Sliding Window algorithms.

## Development Standards

### Kotlin Coding Conventions

- **Naming**: Use camelCase, constants in SCREAMING_SNAKE_CASE (`DEFAULT_CAPACITY`, `KEY_PREFIX`)
- **Class structure**: Place companion object at the bottom, use for private constants and script definitions
- **Package structure**: Separate by feature (`algorithm`, `config`, `controller`, `model`, `service`, `repository`)
- **data class**: Use for immutable data models (`RateLimitResult`, `RateLimitResponse`)
- **enum class**: Use for type-safe constant groups (`RateLimitAlgorithm`)
- **Extension functions**: Actively use Kotlin standard library extensions (`.trimIndent()`, `.flatten()`)
- **Null safety**: Use `?.`, `?:`, `!!` operators appropriately, prefer non-null types
- **String Template**: Use `$variable` and `${expression}` for string interpolation

### Coroutines Usage Rules

- **suspend functions**: Declare all async operations as `suspend` functions
- **awaitSingle / awaitSingleOrNull**: Use when converting Reactor's Mono to coroutines
- **runBlocking**: Only use in tests, prohibited in production code
- **import**: Use `kotlinx.coroutines.reactor.awaitSingle`, `kotlinx.coroutines.reactor.awaitSingleOrNull`

### Dependency Injection

- **Constructor injection**: Use primary constructor injection
- **Annotations**: Use `@Component`, `@Service`, `@Configuration`, `@RestController`
- **Interface separation**: Separate `RateLimiter` interface from implementations

### Logging

- **Logger declaration**: Use `private val logger = LoggerFactory.getLogger(javaClass)` pattern
- **Log levels**: debug (normal flow), info (state changes), warn (fallback actions), error (exceptions)
- **String Template**: Use Kotlin string templates in log messages

### Redis Patterns

- **Lua Script**: Use Lua scripts for atomic operations
- **Key naming**: `rate_limiter:{algorithm}:{key}` format
- **TTL setting**: Set appropriate expiration time for all keys
- **Fail Open**: Allow requests on Redis failure (prioritize availability)

## Testing Guidelines

### TDD Enforcement (NON-NEGOTIABLE)

The Red-Green-Refactor cycle is **mandatory** for this project:

1. **RED**: Write a failing test first (`/red` command)
2. **GREEN**: Write minimal code to pass (`/green` command)
3. **REFACTOR**: Clean up while keeping tests green (`/refactor` command)

**Coverage Requirements**:
- Minimum overall coverage: **80%**
- Domain/algorithm layer: **90%+** recommended
- Coverage must not decrease per PR

### Testing Principles

- **TDD**: Write tests first, follow Red-Green-Refactor cycle
- **Test isolation**: Each test must be independently executable
- **Clear naming**: Use backtick function names to express test intent (`` `should allow request when bucket has tokens` ``)

### Testing Tools

- **JUnit 5**: Use `@Test`, `@BeforeEach`, Assertions
- **Testcontainers**: Integration tests with actual Redis container
- **MockK**: Kotlin-friendly mocking library (`io.mockk:mockk`)
- **SpringBootTest**: Use `@SpringBootTest`, `@DynamicPropertySource`

### Test Structure

```kotlin
@SpringBootTest
@Testcontainers
class SomeTest {
    companion object {
        @Container
        val redis = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    @Test
    fun `should do something`() = runBlocking {
        // Given
        // When
        // Then
    }
}
```

## Architecture Principles

### Layer Structure

```
Controller (API endpoints)
    ↓
Service (Business logic, metrics collection)
    ↓
Algorithm (Rate limiting algorithm implementations)
    ↓
Redis (Distributed state store)
```

### Core Design Principles

1. **Interface-based design**: Abstract algorithms with `RateLimiter` interface
2. **Single Responsibility Principle**: Each class handles one responsibility
3. **Strategy Pattern**: Algorithms selectable at runtime
4. **Fail Open Policy**: Maintain service availability on Redis failure

### Supported Algorithms

- **Token Bucket**: Allows burst traffic, token refill mechanism
- **Sliding Window Log**: Accurate rate limiting, higher memory usage
- (Planned) Fixed Window, Sliding Window Counter, Leaky Bucket

### Monitoring

- **Micrometer**: Metrics collection (`rate_limiter.check`, `rate_limiter.requests`)
- **Prometheus**: Metrics exposure (`/actuator/prometheus`)
- **Tags**: Categorize by algorithm, allowed, etc.

## Build and Run Commands

### Build

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Clean build
./gradlew clean build
```

### Run

```bash
# Run application (requires Redis)
./gradlew bootRun

# Run with Redis using Docker Compose
docker-compose -f docker/docker-compose.yml up -d
```

### Code Quality

```bash
# Check dependencies
./gradlew dependencies

# Test coverage
./gradlew jacocoTestReport
```

### Git Hooks Setup (Required)

After cloning the repository, run the setup script to enable TDD enforcement:

```bash
./.githooks/setup.sh
```

This installs the following hooks:

| Hook | Trigger | Checks |
|------|---------|--------|
| `pre-commit` | `git commit` | Compilation, `.block()` usage, sensitive files |
| `pre-push` | `git push` | Full test suite, coverage gate (80% minimum) |

To bypass in emergencies: `git commit --no-verify` or `git push --no-verify`

## Code Review Checklist

### Required Checks

- [ ] Are suspend functions used appropriately?
- [ ] Are Redis operations atomic using Lua scripts?
- [ ] Are exception handling and Fail Open policies applied?
- [ ] Is appropriate logging added?
- [ ] Are tests written? (unit/integration)
- [ ] Is metrics collection added?

### Code Style

- [ ] Does it follow Kotlin coding conventions?
- [ ] Is immutability (val) preferred?
- [ ] Is null safety guaranteed?
- [ ] Are meaningful variable/function names used?

### Performance Considerations

- [ ] Is the number of Redis calls minimized?
- [ ] Are appropriate TTLs set?
- [ ] Are there no unnecessary memory allocations?

## Git Workflow

### Branch Strategy

- `main`: Production releases
- `develop`: Development integration branch
- `feature/*`: Feature development
- `fix/*`: Bug fixes
- `refactor/*`: Refactoring

### Commit Message Rules

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Type**: feat, fix, docs, style, refactor, test, chore

**Example**:
```
feat(algorithm): add leaky bucket rate limiter

- Implement LeakyBucketRateLimiter class
- Add Lua script for atomic queue operations
- Include unit and integration tests
```

### PR Rules

1. Work on feature branches
2. Ensure tests pass
3. Request code review
4. Use squash merge

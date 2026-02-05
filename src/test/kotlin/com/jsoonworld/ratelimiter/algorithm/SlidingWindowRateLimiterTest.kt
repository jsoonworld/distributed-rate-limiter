package com.jsoonworld.ratelimiter.algorithm

import com.jsoonworld.ratelimiter.config.RateLimiterProperties
import com.jsoonworld.ratelimiter.model.RateLimitAlgorithm
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest
@Testcontainers
class SlidingWindowRateLimiterTest {

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

    @Autowired
    lateinit var redisTemplate: ReactiveStringRedisTemplate

    private lateinit var rateLimiter: SlidingWindowRateLimiter

    @BeforeEach
    fun setUp() {
        // Window size: 60 seconds, Max requests: 100
        val properties = RateLimiterProperties(
            algorithm = RateLimitAlgorithm.SLIDING_WINDOW_LOG,
            capacity = 100,
            refillRate = 10,
            windowSize = 60
        )
        rateLimiter = SlidingWindowRateLimiter(redisTemplate, properties)
    }

    @Test
    fun `should allow request within window limit`(): Unit = runBlocking {
        // Given
        val key = "sliding-window-test-${UUID.randomUUID()}"

        // When
        val result = rateLimiter.tryAcquire(key)

        // Then
        assertThat(result.allowed).isTrue()
        assertThat(result.remainingTokens).isEqualTo(99)
        Unit
    }

    @Test
    fun `should deny request when window limit exceeded`(): Unit = runBlocking {
        // Given
        val key = "sliding-window-deny-${UUID.randomUUID()}"

        // Exhaust all requests within the window
        repeat(100) {
            rateLimiter.tryAcquire(key)
        }

        // When
        val result = rateLimiter.tryAcquire(key)

        // Then
        assertThat(result.allowed).isFalse()
        assertThat(result.remainingTokens).isEqualTo(0)
        assertThat(result.retryAfterSeconds).isNotNull()
        Unit
    }

    @Test
    fun `should correctly calculate remaining`(): Unit = runBlocking {
        // Given
        val key = "sliding-window-remaining-${UUID.randomUUID()}"

        // Consume 50 requests
        repeat(50) {
            rateLimiter.tryAcquire(key)
        }

        // When
        val remaining: Long = rateLimiter.getRemainingLimit(key)

        // Then
        assertThat(remaining).isEqualTo(50L)
        Unit
    }

    @Test
    fun `should reset rate limit`(): Unit = runBlocking {
        // Given
        val key = "sliding-window-reset-${UUID.randomUUID()}"

        // Exhaust all requests
        repeat(100) {
            rateLimiter.tryAcquire(key)
        }

        // Verify rate limit is exhausted
        val beforeReset = rateLimiter.tryAcquire(key)
        assertThat(beforeReset.allowed).isFalse()

        // When
        rateLimiter.reset(key)

        // Then
        val afterReset = rateLimiter.tryAcquire(key)
        assertThat(afterReset.allowed).isTrue()
        assertThat(afterReset.remainingTokens).isEqualTo(99)
        Unit
    }

    @Test
    fun `should handle multiple permits`(): Unit = runBlocking {
        // Given
        val key = "sliding-window-multi-permit-${UUID.randomUUID()}"

        // When - request 10 permits at once
        val result = rateLimiter.tryAcquire(key, permits = 10)

        // Then
        assertThat(result.allowed).isTrue()
        assertThat(result.remainingTokens).isEqualTo(90)

        // Verify remaining is correct
        val remaining: Long = rateLimiter.getRemainingLimit(key)
        assertThat(remaining).isEqualTo(90L)
        Unit
    }

    @Test
    fun `should allow requests after window expires`(): Unit = runBlocking {
        // Given - create a rate limiter with a very short window (2 seconds)
        val shortWindowProperties = RateLimiterProperties(
            algorithm = RateLimitAlgorithm.SLIDING_WINDOW_LOG,
            capacity = 5,
            refillRate = 10,
            windowSize = 2
        )
        val shortWindowLimiter = SlidingWindowRateLimiter(redisTemplate, shortWindowProperties)
        val key = "sliding-window-expiry-${UUID.randomUUID()}"

        // Exhaust all requests within the short window
        repeat(5) {
            shortWindowLimiter.tryAcquire(key)
        }

        // Verify rate limit is exhausted
        val beforeExpiry = shortWindowLimiter.tryAcquire(key)
        assertThat(beforeExpiry.allowed).isFalse()

        // When - wait for the window to expire
        delay(2500)

        // Then - requests should be allowed again after window expires
        val afterExpiry = shortWindowLimiter.tryAcquire(key)
        assertThat(afterExpiry.allowed).isTrue()
        assertThat(afterExpiry.remainingTokens).isEqualTo(4)
        Unit
    }

    @Test
    fun `should gradually allow more requests as old ones expire from window`(): Unit = runBlocking {
        // Given - create a rate limiter with a very short window (2 seconds)
        val shortWindowProperties = RateLimiterProperties(
            algorithm = RateLimitAlgorithm.SLIDING_WINDOW_LOG,
            capacity = 3,
            refillRate = 10,
            windowSize = 2
        )
        val shortWindowLimiter = SlidingWindowRateLimiter(redisTemplate, shortWindowProperties)
        val key = "sliding-window-gradual-${UUID.randomUUID()}"

        // Make 3 requests (exhaust the limit)
        repeat(3) {
            shortWindowLimiter.tryAcquire(key)
        }

        // Verify rate limit is exhausted
        val exhausted = shortWindowLimiter.tryAcquire(key)
        assertThat(exhausted.allowed).isFalse()

        // When - wait for window to expire
        delay(2500)

        // Then - should be able to make requests again
        val result1 = shortWindowLimiter.tryAcquire(key)
        assertThat(result1.allowed).isTrue()

        val result2 = shortWindowLimiter.tryAcquire(key)
        assertThat(result2.allowed).isTrue()

        val result3 = shortWindowLimiter.tryAcquire(key)
        assertThat(result3.allowed).isTrue()

        // Now should be exhausted again
        val result4 = shortWindowLimiter.tryAcquire(key)
        assertThat(result4.allowed).isFalse()
        Unit
    }
}

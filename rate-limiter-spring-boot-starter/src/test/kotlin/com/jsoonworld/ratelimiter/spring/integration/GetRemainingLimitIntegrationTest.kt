package com.jsoonworld.ratelimiter.spring.integration

import com.jsoonworld.ratelimiter.core.algorithm.SlidingWindowRateLimiter
import com.jsoonworld.ratelimiter.core.algorithm.TokenBucketRateLimiter
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Integration tests for getRemainingLimit method.
 *
 * These tests verify that the getRemainingLimit method correctly returns
 * a Long value (not a List<Long>) and accurately reflects the remaining
 * capacity/requests after token consumption.
 */
@SpringBootTest(classes = [GetRemainingLimitIntegrationTest.TestConfig::class])
@Testcontainers
class GetRemainingLimitIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    class TestConfig

    companion object {
        private const val DEFAULT_CAPACITY = 100L
        private const val DEFAULT_MAX_REQUESTS = 100L

        @Container
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    @Autowired
    lateinit var tokenBucketRateLimiter: TokenBucketRateLimiter

    @Autowired
    lateinit var slidingWindowRateLimiter: SlidingWindowRateLimiter

    @Autowired
    lateinit var redisTemplate: ReactiveStringRedisTemplate

    @BeforeEach
    fun setUp() = runBlocking {
        // Clean up any existing keys before each test
        val keys = redisTemplate.keys("rate_limiter:*").collectList().block()
        keys?.forEach { key ->
            redisTemplate.delete(key).block()
        }
    }

    // ===== Token Bucket Rate Limiter Tests =====

    @Test
    fun `TokenBucket - should return full capacity for new key`() = runBlocking {
        // Given
        val key = "new-key-${UUID.randomUUID()}"

        // When
        val remaining = tokenBucketRateLimiter.getRemainingLimit(key)

        // Then
        assertThat(remaining).isEqualTo(DEFAULT_CAPACITY)
        // Verify the result is actually a Long, not a List
        assertThat(remaining).isInstanceOf(Long::class.java)
    }

    @Test
    fun `TokenBucket - should return correct remaining after single consumption`() = runBlocking {
        // Given
        val key = "single-consume-${UUID.randomUUID()}"
        val permitsToConsume = 1L

        // When
        val result = tokenBucketRateLimiter.tryAcquire(key, permitsToConsume)
        val remaining = tokenBucketRateLimiter.getRemainingLimit(key)

        // Then
        assertThat(result.allowed).isTrue()
        assertThat(remaining).isEqualTo(DEFAULT_CAPACITY - permitsToConsume)
        assertThat(remaining).isInstanceOf(Long::class.java)
    }

    @Test
    fun `TokenBucket - should return correct remaining after multiple consumptions`() = runBlocking {
        // Given
        val key = "multi-consume-${UUID.randomUUID()}"
        val permitsPerRequest = 5L
        val numberOfRequests = 3

        // When
        repeat(numberOfRequests) {
            tokenBucketRateLimiter.tryAcquire(key, permitsPerRequest)
        }
        val remaining = tokenBucketRateLimiter.getRemainingLimit(key)

        // Then
        val totalConsumed = permitsPerRequest * numberOfRequests
        assertThat(remaining).isEqualTo(DEFAULT_CAPACITY - totalConsumed)
        assertThat(remaining).isInstanceOf(Long::class.java)
    }

    @Test
    fun `TokenBucket - should return zero when fully consumed`() = runBlocking {
        // Given
        val key = "fully-consumed-${UUID.randomUUID()}"

        // When - consume all tokens
        tokenBucketRateLimiter.tryAcquire(key, DEFAULT_CAPACITY)
        val remaining = tokenBucketRateLimiter.getRemainingLimit(key)

        // Then
        assertThat(remaining).isEqualTo(0L)
        assertThat(remaining).isInstanceOf(Long::class.java)
    }

    @Test
    fun `TokenBucket - should return capacity after reset`() = runBlocking {
        // Given
        val key = "reset-key-${UUID.randomUUID()}"

        // Consume some tokens first
        tokenBucketRateLimiter.tryAcquire(key, 50L)
        assertThat(tokenBucketRateLimiter.getRemainingLimit(key)).isEqualTo(50L)

        // When
        tokenBucketRateLimiter.reset(key)
        val remaining = tokenBucketRateLimiter.getRemainingLimit(key)

        // Then
        assertThat(remaining).isEqualTo(DEFAULT_CAPACITY)
        assertThat(remaining).isInstanceOf(Long::class.java)
    }

    // ===== Sliding Window Rate Limiter Tests =====

    @Test
    fun `SlidingWindow - should return max requests for new key`() = runBlocking {
        // Given
        val key = "new-key-${UUID.randomUUID()}"

        // When
        val remaining = slidingWindowRateLimiter.getRemainingLimit(key)

        // Then
        assertThat(remaining).isEqualTo(DEFAULT_MAX_REQUESTS)
        // Verify the result is actually a Long, not a List
        assertThat(remaining).isInstanceOf(Long::class.java)
    }

    @Test
    fun `SlidingWindow - should return correct remaining after single request`() = runBlocking {
        // Given
        val key = "single-request-${UUID.randomUUID()}"
        val permitsToConsume = 1L

        // When
        val result = slidingWindowRateLimiter.tryAcquire(key, permitsToConsume)
        val remaining = slidingWindowRateLimiter.getRemainingLimit(key)

        // Then
        assertThat(result.allowed).isTrue()
        assertThat(remaining).isEqualTo(DEFAULT_MAX_REQUESTS - permitsToConsume)
        assertThat(remaining).isInstanceOf(Long::class.java)
    }

    @Test
    fun `SlidingWindow - should return correct remaining after multiple requests`() = runBlocking {
        // Given
        val key = "multi-request-${UUID.randomUUID()}"
        val permitsPerRequest = 5L
        val numberOfRequests = 3

        // When
        repeat(numberOfRequests) {
            slidingWindowRateLimiter.tryAcquire(key, permitsPerRequest)
        }
        val remaining = slidingWindowRateLimiter.getRemainingLimit(key)

        // Then
        val totalConsumed = permitsPerRequest * numberOfRequests
        assertThat(remaining).isEqualTo(DEFAULT_MAX_REQUESTS - totalConsumed)
        assertThat(remaining).isInstanceOf(Long::class.java)
    }

    @Test
    fun `SlidingWindow - should return zero when limit reached`() = runBlocking {
        // Given
        val key = "limit-reached-${UUID.randomUUID()}"

        // When - consume all requests
        slidingWindowRateLimiter.tryAcquire(key, DEFAULT_MAX_REQUESTS)
        val remaining = slidingWindowRateLimiter.getRemainingLimit(key)

        // Then
        assertThat(remaining).isEqualTo(0L)
        assertThat(remaining).isInstanceOf(Long::class.java)
    }

    @Test
    fun `SlidingWindow - should return max requests after reset`() = runBlocking {
        // Given
        val key = "reset-key-${UUID.randomUUID()}"

        // Consume some requests first
        slidingWindowRateLimiter.tryAcquire(key, 50L)
        assertThat(slidingWindowRateLimiter.getRemainingLimit(key)).isEqualTo(50L)

        // When
        slidingWindowRateLimiter.reset(key)
        val remaining = slidingWindowRateLimiter.getRemainingLimit(key)

        // Then
        assertThat(remaining).isEqualTo(DEFAULT_MAX_REQUESTS)
        assertThat(remaining).isInstanceOf(Long::class.java)
    }

    // ===== Type Safety Tests =====

    @Test
    fun `TokenBucket - getRemainingLimit should return Long not List`() = runBlocking {
        // Given
        val key = "type-check-${UUID.randomUUID()}"

        // When
        val remaining = tokenBucketRateLimiter.getRemainingLimit(key)

        // Then - This test explicitly verifies the return type is Long, not List<Long>
        // If the type casting bug exists, this assertion will fail
        assertThat(remaining).isInstanceOf(Long::class.java)
        assertThat(remaining).isNotInstanceOf(List::class.java)

        // Also verify it's a valid numeric value
        assertThat(remaining).isGreaterThanOrEqualTo(0L)
        assertThat(remaining).isLessThanOrEqualTo(DEFAULT_CAPACITY)
    }

    @Test
    fun `SlidingWindow - getRemainingLimit should return Long not List`() = runBlocking {
        // Given
        val key = "type-check-${UUID.randomUUID()}"

        // When
        val remaining = slidingWindowRateLimiter.getRemainingLimit(key)

        // Then - This test explicitly verifies the return type is Long, not List<Long>
        // If the type casting bug exists, this assertion will fail
        assertThat(remaining).isInstanceOf(Long::class.java)
        assertThat(remaining).isNotInstanceOf(List::class.java)

        // Also verify it's a valid numeric value
        assertThat(remaining).isGreaterThanOrEqualTo(0L)
        assertThat(remaining).isLessThanOrEqualTo(DEFAULT_MAX_REQUESTS)
    }
}

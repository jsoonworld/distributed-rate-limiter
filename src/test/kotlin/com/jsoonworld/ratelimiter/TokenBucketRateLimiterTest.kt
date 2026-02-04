package com.jsoonworld.ratelimiter

import com.jsoonworld.ratelimiter.algorithm.TokenBucketRateLimiter
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
class TokenBucketRateLimiterTest {

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
    private lateinit var tokenBucketRateLimiter: TokenBucketRateLimiter

    private val testKey = "test-client-${System.currentTimeMillis()}"

    @BeforeEach
    fun setup() = runBlocking {
        tokenBucketRateLimiter.reset(testKey)
    }

    @Test
    fun `should allow request when bucket has tokens`() = runBlocking {
        val result = tokenBucketRateLimiter.tryAcquire(testKey)

        assertTrue(result.allowed)
        assertTrue(result.remainingTokens >= 0)
    }

    @Test
    fun `should return remaining tokens after request`() = runBlocking {
        val initialRemaining = tokenBucketRateLimiter.getRemainingLimit(testKey)
        tokenBucketRateLimiter.tryAcquire(testKey)
        val afterRemaining = tokenBucketRateLimiter.getRemainingLimit(testKey)

        assertTrue(afterRemaining < initialRemaining || afterRemaining == initialRemaining - 1)
    }

    @Test
    fun `should reset bucket correctly`() = runBlocking {
        // Consume some tokens
        repeat(5) {
            tokenBucketRateLimiter.tryAcquire(testKey)
        }

        // Reset
        tokenBucketRateLimiter.reset(testKey)

        // Should have full capacity again
        val remaining = tokenBucketRateLimiter.getRemainingLimit(testKey)
        assertEquals(100L, remaining) // DEFAULT_CAPACITY
    }
}

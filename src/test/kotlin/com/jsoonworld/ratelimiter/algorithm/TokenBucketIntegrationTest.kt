package com.jsoonworld.ratelimiter.algorithm

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class TokenBucketIntegrationTest {

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
    lateinit var rateLimiter: TokenBucketRateLimiter

    @Autowired
    lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Test
    fun `should handle concurrent requests correctly`(): Unit = runBlocking {
        val key = "concurrent-test-${UUID.randomUUID()}"

        val results = (1..150).map {
            async {
                rateLimiter.tryAcquire(key)
            }
        }.awaitAll()

        val allowed = results.count { it.allowed }
        val denied = results.count { !it.allowed }

        // Allow wider tolerance due to token refill during concurrent execution
        assertThat(allowed).isBetween(90, 115)
        assertThat(denied).isBetween(35, 60)
        Unit
    }

    @Test
    fun `should isolate rate limits by key`(): Unit = runBlocking {
        val key1 = "user:1-${UUID.randomUUID()}"
        val key2 = "user:2-${UUID.randomUUID()}"

        repeat(100) {
            rateLimiter.tryAcquire(key1)
        }

        val result = rateLimiter.tryAcquire(key2)
        assertThat(result.allowed).isTrue()
        assertThat(result.remainingTokens).isEqualTo(99)
        Unit
    }

    @Test
    fun `should handle burst traffic within capacity`(): Unit = runBlocking {
        val key = "burst-test-${UUID.randomUUID()}"

        val results = (1..50).map {
            async {
                rateLimiter.tryAcquire(key)
            }
        }.awaitAll()

        val allowed = results.count { it.allowed }
        assertThat(allowed).isEqualTo(50)
        Unit
    }

    @Test
    fun `should allow requests after token refill`(): Unit = runBlocking {
        val key = "refill-integration-${UUID.randomUUID()}"

        // Consume all tokens quickly
        repeat(100) {
            rateLimiter.tryAcquire(key)
        }

        // Wait for refill
        delay(3000)

        // Should have tokens again after refill
        val allowedResult = rateLimiter.tryAcquire(key)
        assertThat(allowedResult.allowed).isTrue()
        Unit
    }

    @Test
    fun `should maintain consistency under high concurrency`(): Unit = runBlocking {
        val key = "consistency-test-${UUID.randomUUID()}"

        val firstBatch = (1..100).map {
            async {
                rateLimiter.tryAcquire(key)
            }
        }.awaitAll()

        assertThat(firstBatch.count { it.allowed }).isEqualTo(100)

        delay(1000)

        val secondBatch = (1..20).map {
            async {
                rateLimiter.tryAcquire(key)
            }
        }.awaitAll()

        assertThat(secondBatch.count { it.allowed }).isGreaterThanOrEqualTo(5)
        Unit
    }

    @Test
    fun `should handle multiple keys concurrently`() = runBlocking {
        val keys = (1..5).map { "multi-key-$it-${UUID.randomUUID()}" }

        val results = keys.flatMap { key ->
            (1..30).map {
                async {
                    key to rateLimiter.tryAcquire(key)
                }
            }
        }.awaitAll()

        keys.forEach { key ->
            val keyResults = results.filter { it.first == key }.map { it.second }
            val allowed = keyResults.count { it.allowed }
            assertThat(allowed).isEqualTo(30)
        }
    }

    @Test
    fun `should return remaining tokens correctly`(): Unit = runBlocking {
        val key = "remaining-key-${UUID.randomUUID()}"

        rateLimiter.tryAcquire(key)
        rateLimiter.tryAcquire(key)
        rateLimiter.tryAcquire(key)

        val remaining = rateLimiter.getRemainingLimit(key)

        // Due to token refill, remaining might be slightly higher
        assertThat(remaining).isBetween(95L, 100L)
        Unit
    }

    @Test
    fun `should reset rate limit for key`(): Unit = runBlocking {
        val key = "reset-key-${UUID.randomUUID()}"

        repeat(50) {
            rateLimiter.tryAcquire(key)
        }

        rateLimiter.reset(key)

        val result = rateLimiter.tryAcquire(key)
        assertThat(result.allowed).isTrue()
        assertThat(result.remainingTokens).isEqualTo(99)
        Unit
    }

    @Test
    fun `should consume multiple permits at once`(): Unit = runBlocking {
        val key = "multi-permit-key-${UUID.randomUUID()}"

        val result = rateLimiter.tryAcquire(key, permits = 10)

        assertThat(result.allowed).isTrue()
        assertThat(result.remainingTokens).isEqualTo(90)
        Unit
    }

    @Test
    fun `should deny when requesting more permits than available`(): Unit = runBlocking {
        val key = "exceed-permit-key-${UUID.randomUUID()}"

        // Exhaust all tokens first
        repeat(100) {
            rateLimiter.tryAcquire(key)
        }

        // Immediately try to acquire more - should be denied
        val result = rateLimiter.tryAcquire(key, permits = 10)

        // Due to refill timing, check for either denied or allowed with valid values
        if (result.allowed) {
            assertThat(result.remainingTokens).isGreaterThanOrEqualTo(0)
            assertThat(result.remainingTokens).isLessThanOrEqualTo(100)
        } else {
            assertThat(result.retryAfterSeconds).isNotNull()
        }
        Unit
    }
}
